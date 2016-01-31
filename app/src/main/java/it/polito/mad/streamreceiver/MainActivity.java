package it.polito.mad.streamreceiver;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.widget.EditText;
import android.widget.Toast;

import java.nio.ByteBuffer;

import it.polito.mad.websocket.AsyncClientImpl;

public class MainActivity extends Activity {

    private AsyncClientImpl mClient = new AsyncClientImpl(new AsyncClientImpl.Listener() {
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
        public void onConfigParamsReceived(byte[] configParams, int width, int height) {
            Log.d("ACT", "config bytes: "+new String(configParams)+" ; " +
                    "resolution: "+width+"x"+height);
            mWidth = width;
            mHeight = height;
            stopDecoder();
            startDecoder();
            mDecoderTask.setConfigurationBuffer(ByteBuffer.wrap(configParams));
        }

        @Override
        public void onStreamChunkReceived(byte[] chunk, int flags, long timestamp) {
            Log.d("ACT", "stream["+chunk.length+"]");
            mDecoderTask.submitEncodedData(new VideoChunks.Chunk(chunk, flags, timestamp));
        }
    });

    private int mWidth = 320, mHeight = 240;
    private EditText mIp;
    private Surface mSurface;
    private DecoderThread mDecoderTask;
    //private DecoderTask mDecoderTask;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mIp = (EditText) findViewById(R.id.ip);

        final SurfaceView outputView = (SurfaceView) findViewById(R.id.output_view);
        outputView.getHolder().addCallback(new SurfaceHolder.Callback() {
            @Override
            public void surfaceCreated(SurfaceHolder holder) {

            }

            @Override
            public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {

                mSurface = holder.getSurface();

                mClient.connect(mIp.getText().toString(), 8080, 2000);
            }

            @Override
            public void surfaceDestroyed(SurfaceHolder holder) {

            }
        });
    }


    @Override
    protected void onPause() {
        /*if (mDecoderTask != null){
            mDecoderTask.interrupt();
            try{
                mDecoderTask.join();
            }catch(InterruptedException e){

            }
        }
        */
        stopDecoder();
        if (mClient.getSocket() != null){
            mClient.closeConnection();
        }
        super.onPause();
    }

    private void startDecoder(){
        if (mDecoderTask == null){
            mDecoderTask = new DecoderThread(mWidth, mHeight, null);
            mDecoderTask.setSurface(mSurface);
            mDecoderTask.start();
        }
    }

    private void stopDecoder(){
        if (mDecoderTask != null){
            mDecoderTask.interrupt();
            try{
                mDecoderTask.join();
            }catch(InterruptedException e){}
            mDecoderTask = null;
        }
    }

}
