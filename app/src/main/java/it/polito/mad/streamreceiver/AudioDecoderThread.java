package it.polito.mad.streamreceiver;

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.util.Log;
import android.view.Surface;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Created by luigi on 21/01/16.
 */
@SuppressWarnings("deprecation")
public class AudioDecoderThread implements Runnable {

    public static abstract class Listener {
        void onRawFrameAvailable(byte[] frame){}
    }

    private static final String TAG = "AUDIODECODER";
    private static final boolean VERBOSE = true;

    private static final int TIMEOUT_US = 10000;
    private static final String MIME_TYPE = "audio/mp4a-latm";
    private static final int SAMPLE_RATE = 44100;
    private static final int CHANNEL_COUNT = 2;

    private Listener mListener;
    private ByteBuffer mConfigBuffer;
    private VideoChunks mEncodedFrames = new VideoChunks();

    private Thread mWorkerThread;

    public void startThread(){
        if (mWorkerThread != null){
            return;
        }
        mWorkerThread = new Thread(this);
        mWorkerThread.start();
    }

    public void requestStop(){
        if (mWorkerThread == null){
            return;
        }
        mWorkerThread.interrupt();
    }

    public boolean waitForTermination(){
        if (mWorkerThread == null){
            return true;
        }
        boolean result = true;
        try{
            mWorkerThread.join();
        } catch(InterruptedException e){
            result = false;
        }
        mWorkerThread = null;
        return result;
    }

    public boolean isRunning(){
        return mWorkerThread != null;
    }

    public AudioDecoderThread(Listener listener){
        mListener = listener;
    }

    public void drain(){
        mEncodedFrames.clear();
    }

    public void setListener(Listener mListener) {
        this.mListener = mListener;
    }

    public synchronized void setConfigurationBuffer(ByteBuffer csd0){
        mConfigBuffer = csd0;
        notifyAll();
    }

    public void submitEncodedData(VideoChunks.Chunk chunk){
        mEncodedFrames.addChunk(chunk);
    }


    @Override
    public void run() {
        MediaCodec decoder = null;

        MediaFormat format = MediaFormat.createAudioFormat(MIME_TYPE, SAMPLE_RATE, CHANNEL_COUNT);
        format.setInteger(MediaFormat.KEY_BIT_RATE, 64*1024);
        format.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectHE);

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

            format.setByteBuffer("csd-0", mConfigBuffer);

            decoder.configure(format, null,  null, 0);
            decoder.start();
        }
        catch(InterruptedException e){
            Log.d(TAG, "Cancel requested");
            decoder.release();
            Log.i(TAG, "Decoder Released!! Closing");
            return;
        }
        catch(IOException e){
            Log.e(TAG, "Unable to create an appropriate codec for " + MIME_TYPE);
            return;
        }
        catch(Throwable t){
            Log.e(TAG, t.toString());
            return;
        }

        ByteBuffer[] decoderInputBuffers = decoder.getInputBuffers();
        ByteBuffer[] decoderOutputBuffers = decoder.getOutputBuffers();
        long counter = 0L;

        int inputStatus = -1, outputStatus = -1;

        if (VERBOSE) Log.d(TAG, "Decoder starts...");

        while (!Thread.interrupted()){
            if (VERBOSE) Log.i(TAG, "Waiting for input buffer");
            inputStatus = decoder.dequeueInputBuffer(TIMEOUT_US);
            if (inputStatus >= 0) {
                if (VERBOSE) Log.d(TAG, "released InBuf["+inputStatus+"]");
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
                counter++;

                decoder.queueInputBuffer(inputStatus, 0, chunk.data.length,
                        chunk.presentationTimestampUs, chunk.flags);
                if (VERBOSE) Log.d(TAG, "queued array # " + counter + ": "
                        + chunk.data.length + " bytes to decoder");
            }

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
                    if (VERBOSE) Log.d(TAG, "DECODER OUTPUT AVAILABLE!!!");
                    ByteBuffer outputFrame = decoderOutputBuffers[outputStatus];
                    if (outputFrame != null) {
                        outputFrame.position(info.offset);
                        outputFrame.limit(info.offset + info.size);
                    }
                    byte[] decodedArray = new byte[outputFrame.remaining()];
                    outputFrame.get(decodedArray);
                    if (mListener != null) mListener.onRawFrameAvailable(decodedArray);

                    decoder.releaseOutputBuffer(outputStatus, false /*render*/);
                    //if (VERBOSE) Log.d(TAG, "released OutBuf["+outputStatus+"]");
                    break;
            }
        }
        decoder.stop();
        decoder.release();
        Log.i(TAG, "Decoder Released!! Closing");
    }

}
