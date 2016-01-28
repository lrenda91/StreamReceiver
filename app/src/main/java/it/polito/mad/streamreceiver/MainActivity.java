package it.polito.mad.streamreceiver;

import android.app.Activity;
import android.os.AsyncTask;
import android.os.Environment;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Base64;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.widget.Toast;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
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
        public void onConfigParamsReceived(byte[] configParams) {
            mDecoderTask.setConfigurationBuffer(ByteBuffer.wrap(configParams));
        }

        @Override
        public void onStreamChunkReceived(byte[] chunk, int flags, long timestamp) {
            mDecoderTask.submitEncodedData(new VideoChunks.Chunk(chunk, flags, timestamp));
        }
    });

    private DecoderTask mDecoderTask;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        final SurfaceView outputView = (SurfaceView) findViewById(R.id.output_view);
        outputView.getHolder().addCallback(new SurfaceHolder.Callback() {
            @Override
            public void surfaceCreated(SurfaceHolder holder) {
                Surface s = holder.getSurface();
                mDecoderTask = new DecoderTask();
                mDecoderTask.setSurface(outputView.getHolder().getSurface());
                mDecoderTask.execute();

                mClient.connect("192.168.1.31", 8080, 2000);
            }

            @Override
            public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {

            }

            @Override
            public void surfaceDestroyed(SurfaceHolder holder) {

            }
        });
    }


    @Override
    protected void onPause() {
        if (mDecoderTask != null){
            mDecoderTask.cancel(true);
        }
        if (mClient.getSocket() != null){
            mClient.closeConnection();
        }
        super.onPause();
    }
}
