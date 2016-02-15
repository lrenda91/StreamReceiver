package it.polito.mad.streamreceiver;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.util.Log;

import java.io.ByteArrayInputStream;
import java.nio.ByteBuffer;

/**
 * Created by luigi on 13/02/16.
 */
public class AudioPlayerThread {

    private static final String TAG = "AUDIO_PLAYBACK";

    private AudioTrack mAudioTrack;
    private byte[] mBuffer;

    public AudioPlayerThread(){
        int channelsOut = AudioFormat.CHANNEL_OUT_STEREO;
        int sampleRate = 44100;
        int audioFormat = AudioFormat.ENCODING_PCM_16BIT;
        int trackMinBufSize = AudioTrack.getMinBufferSize(sampleRate, channelsOut, audioFormat);
        Log.d(TAG, "audio track buf size: " + trackMinBufSize);
        mAudioTrack = new AudioTrack(
                AudioManager.STREAM_MUSIC,
                sampleRate,
                channelsOut,
                audioFormat,
                trackMinBufSize,
                AudioTrack.MODE_STREAM);
        mBuffer = new byte[trackMinBufSize];
    }

    private AudioDecoderThread mDecoder = new AudioDecoderThread(new AudioDecoderThread.Listener() {
        @Override
        void onRawFrameAvailable(byte[] frame) {
            Log.d(TAG, "sample available["+frame.length+"]");
            mAudioTrack.write(frame, 0, frame.length);
        }
    });

    public void setAudioConfig(byte[] data){
        mDecoder.setConfigurationBuffer(ByteBuffer.wrap(data));
    }

    public void publishEncodedSample(VideoChunks.Chunk chunk){
        mDecoder.submitEncodedData(chunk);
    }

    public void start() {
        mAudioTrack.play();
        mDecoder.startThread();
    }

    public void stop(){
        mDecoder.requestStop();
        mDecoder.waitForTermination();
        mAudioTrack.stop();
    }
}
