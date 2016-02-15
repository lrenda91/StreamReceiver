package it.polito.mad.streamreceiver;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import java.nio.ByteBuffer;

import it.polito.mad.websocket.WSClient;

public class MainActivity extends Activity {

    private WSClient mClient = new WSClient(new WSClient.Listener() {
        @Override
        public void onConnectionEstablished() {
            Toast.makeText(MainActivity.this, "Connected", Toast.LENGTH_LONG).show();
            mClient.requestConfigParams();
        }

        @Override
        public void onServerUnreachable(Exception e) {
            Toast.makeText(MainActivity.this, "Can't connect to server: "
                    + e.getClass().getSimpleName()+": "+e.getMessage(), Toast.LENGTH_LONG).show();

        }

        @Override
        public void onVideoConfigParamsReceived(byte[] configParams, int width, int height) {
            Log.d("ACT", "config bytes: "+new String(configParams)+" ; " +
                    "resolution: "+width+"x"+height);
            mWidth = width;
            mHeight = height;
            stopDecoder();
            startDecoder();
            mDecoderThread.setConfigurationBuffer(ByteBuffer.wrap(configParams));
        }

        @Override
        public void onAudioConfigParamsReceived(byte[] configParams) {
            mPlayer.stop();
            mPlayer.start();
            mPlayer.setAudioConfig(configParams);
        }

        @Override
        public void onStreamChunkReceived(boolean audio, byte[] chunk, int flags, long timestamp) {
            Log.d("ACT", "stream[" + chunk.length + "]");
            if (audio) {
                mPlayer.publishEncodedSample(new VideoChunks.Chunk(chunk, flags, timestamp));
            } else {
                mDecoderThread.submitEncodedData(new VideoChunks.Chunk(chunk, flags, timestamp));
            }
        }
    });

    private int mWidth = 320, mHeight = 240;
    private EditText mIp;
    private Surface mSurface;
    private VideoDecoderThread mDecoderThread;
    private AudioPlayerThread mPlayer = new AudioPlayerThread();

    private int mRotation = 0;
    private Button mRotate;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mRotate = (Button) findViewById(R.id.rotate);
        mIp = (EditText) findViewById(R.id.ip);

        final SurfaceView outputView = (SurfaceView) findViewById(R.id.output_view);
        outputView.getHolder().addCallback(new SurfaceHolder.Callback() {
            @Override
            public void surfaceCreated(SurfaceHolder holder) {

            }

            @Override
            public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
                mSurface = holder.getSurface();
            }

            @Override
            public void surfaceDestroyed(SurfaceHolder holder) {

            }
        });

        mRotate.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mClient.connect(mIp.getText().toString(), 8080, 2000);
                /*mRotation += 90;
                mRotation %= 360;
                Log.d("ROTATION",""+mRotation);
                mDecoderThread.requestStop();
                mDecoderThread.waitForTermination();
                mDecoderThread.startThread(mWidth, mHeight, mRotation);
                if (mClient.getSocket().isOpen()){
                    mClient.requestConfigParams();
                }
                */
            }
        });
    }


    @Override
    protected void onPause() {
        stopDecoder();
        if (mClient.getSocket() != null){
            mClient.closeConnection();
        }
        super.onPause();
    }

    private void startDecoder(){
        if (mDecoderThread == null){

            mDecoderThread = new VideoDecoderThread(null);
            mDecoderThread.setSurface(mSurface);
            mDecoderThread.startThread(mWidth, mHeight, mRotation);
        }
    }

    private void stopDecoder(){
        if (mDecoderThread != null){
            mDecoderThread.requestStop();
            mDecoderThread.waitForTermination();
            mDecoderThread = null;
        }
    }

}
