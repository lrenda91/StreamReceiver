package it.polito.mad.streamreceiver;

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaFormat;
import android.os.AsyncTask;
import android.util.Log;
import android.view.Surface;

import java.io.IOException;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.Semaphore;

/**
 * Created by luigi on 21/01/16.
 */
@SuppressWarnings("deprecation")
public class DecoderTask extends AsyncTask<Void, VideoChunks.Chunk, Void> {

    public static abstract class Listener {
        void onEncodedDataAvailable(byte[] data){}
    }

    private static final String TAG = "DECODER";
    private static final boolean VERBOSE = true;

    private static final int TIMEOUT_US = 10000;
    private static final String MIME_TYPE = "video/avc";
    private static final int FRAME_RATE = 20;
    private static final int I_FRAME_INTERVAL = 1;
    private static final int BIT_RATE_BPS = 500000;
    private static final long NUM_FRAMES = 100;

    private Listener mListener;
    //private Semaphore mConfigDataReceived = new Semaphore(0);
    private ByteBuffer mConfigBuffer;
    private VideoChunks mEncodedFrames = new VideoChunks();
    private int mWidth = 320, mHeight = 240;
    private Surface mOutputSurface;

    public void setListener(Listener mListener) {
        this.mListener = mListener;
    }

    public synchronized void setConfigurationBuffer(ByteBuffer csd0){
        Log.d(TAG, csd0.order().toString());
        mConfigBuffer = csd0;
        notifyAll();
    }

    public void submitEncodedData(VideoChunks.Chunk chunk){
        mEncodedFrames.addChunk(chunk);
    }

    public void setSurface(Surface s){
        mOutputSurface = s;
    }

    @Override
    protected Void doInBackground(Void... params) {
        MediaCodec decoder = null;
        MediaFormat format = MediaFormat.createVideoFormat(MIME_TYPE, mWidth, mHeight);

        try{
            decoder = MediaCodec.createDecoderByType(MIME_TYPE);
            //now, we must wait for csd-0 configuration bytes by the encoder
            //that is, until setConfigurationBuffer() will be called from another thread
            if (VERBOSE) Log.d(TAG, "Waiting for configuration buffer from the encoder...");
            synchronized (this){
                while (mConfigBuffer == null){
                    wait();
                }
            }
            byte[] array = new byte[mConfigBuffer.remaining()];
            mConfigBuffer.get(array);
            int spsIdx = -1, spsSize = 0, ppsIdx = -1, ppsSize = 0;
            for(int i=0; i <= array.length-4; i++){
                boolean delimiterFound =
                        (array[i]==0 && array[i+1]==0 && array[i+2]==0 && array[i+3]==1);
                if (spsIdx < 0 && delimiterFound){
                    spsIdx = i;
                }
                else if (ppsIdx < 0 && delimiterFound){
                    spsSize = i - spsIdx;
                    ppsIdx = i;
                    ppsSize = array.length - ppsIdx;
                }
            }
            byte[] spsArray = new byte[spsSize], ppsArray = new byte[ppsSize];
            mConfigBuffer.position(0);
            mConfigBuffer.get(spsArray, 0, spsSize);
            mConfigBuffer.get(ppsArray, 0, ppsSize);
            ByteBuffer sps = ByteBuffer.wrap(spsArray);
            ByteBuffer pps = ByteBuffer.wrap(ppsArray);
            //format.setByteBuffer("csd-0", mConfigBuffer);  //SPS + PPS
            format.setByteBuffer("csd-0", sps);
            format.setByteBuffer("csd-1", pps);
            byte[] b = new byte[mConfigBuffer.remaining()];
            mConfigBuffer.get(b);

            Log.d(TAG, "Configured csd-0 buffer: "+mConfigBuffer.toString());
            decoder.configure(format, mOutputSurface,  null, 0);
            decoder.start();
        }
        catch(InterruptedException e){
            Log.d(TAG, "Cancel requested");
            decoder.release();
            Log.i(TAG, "Decoder Released!! Closing");
            return null;
        }
        catch(IOException e){
            Log.e(TAG, "Unable to create an appropriate codec for " + MIME_TYPE);
            return null;
        }
        catch(Throwable t){
            Log.e(TAG, t.toString());
            return null;
        }

        ByteBuffer[] decoderInputBuffers = decoder.getInputBuffers();
        ByteBuffer[] decoderOutputBuffers = decoder.getOutputBuffers();
        long counter = 0;

        boolean EOS_Received = false;
        //boolean mEnd = true;
        int inputStatus = -1, outputStatus = -1;

        if (VERBOSE) Log.d(TAG, "Decoder starts...");

        while (!Thread.interrupted()){
            //if (!EOS_Received) {
                if (VERBOSE) Log.i(TAG, "Waiting for input buffer");
                inputStatus = decoder.dequeueInputBuffer(-1);
                if (inputStatus >= 0) {
                    ByteBuffer inputBuf = decoderInputBuffers[inputStatus];
                    inputBuf.clear();

                    //if (VERBOSE) Log.d(TAG, "Waiting for new encoded chunk from encoder...");
                    VideoChunks.Chunk chunk = mEncodedFrames.getNextChunk();
                    if (chunk == null){
                        if (VERBOSE) Log.d(TAG, "Cancelling thread...");
                        break;
                    }
                    if (VERBOSE) Log.d(TAG, "Received byte["+chunk.data.length+"] from server");

                    inputBuf.put(chunk.data);
                    //EOS_Received = (chunk.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0;
                    counter++;

                    decoder.queueInputBuffer(inputStatus, 0, chunk.data.length,
                            chunk.presentationTimestampUs, chunk.flags);
                    if (VERBOSE) Log.d(TAG, "queued array # " + counter + ": "
                            + chunk.data.length + " bytes to decoder");
                }
            //}

            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            outputStatus = decoder.dequeueOutputBuffer(info, TIMEOUT_US);
            switch (outputStatus){
                case MediaCodec.INFO_TRY_AGAIN_LATER:
                    //if (VERBOSE) Log.d(TAG, "no output from decoder available");
                    break;
                case MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED:
                    if (VERBOSE) Log.d(TAG, "decoder output buffers changed");
                    decoderOutputBuffers = decoder.getOutputBuffers();
                    break;
                case MediaCodec.INFO_OUTPUT_FORMAT_CHANGED:
                    format = decoder.getOutputFormat();
                    if (VERBOSE) Log.d(TAG, "decoder output format changed: " + format);
                    break;
                default:
                    if (outputStatus < 0)
                        break;
                    Log.d(TAG, "DECODER OUTPUT AVAILABLE!!!");
                    ByteBuffer outputFrame = decoderOutputBuffers[outputStatus];
                    if (outputFrame != null) {
                        outputFrame.position(info.offset);
                        outputFrame.limit(info.offset + info.size);
                    }
                    /*byte[] decodedArray = new byte[outputFrame.remaining()];
                    outputFrame.get(decodedArray);
                    VideoChunks.Chunk c =
                                new VideoChunks.Chunk(decodedArray, info.flags, info.presentationTimeUs);
                    publishProgress(c);
                    */
                    decoder.releaseOutputBuffer(outputStatus, true /*render*/);
                    Log.d(TAG, "released with render=true");
                    break;
            }
        }
        decoder.stop();
        decoder.release();
        Log.i(TAG, "Decoder Released!! Closing");
        return null;
    }

}
