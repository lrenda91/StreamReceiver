package it.polito.mad.websocket;

import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.util.Base64;
import android.util.Log;

import com.neovisionaries.ws.client.WebSocket;
import com.neovisionaries.ws.client.WebSocketFactory;

import org.json.JSONException;
import org.json.JSONObject;

import it.polito.mad.JSONMessageFactory;

/**
 * Manages a {@link WebSocket} inside a background thread
 * Created by luigi on 02/12/15.
 */
public class WSClient extends AbstractWSClient {

    private static final boolean VERBOSE = true;
    private static final String TAG = "WebSocketClient";

    private HandlerThread mBackgroundThread;
    private Handler mBackgroundHandler, mMainHandler;

    public interface Listener {
        void onConnectionEstablished();
        void onServerUnreachable(Exception e);
        void onVideoConfigParamsReceived(byte[] configParams, int w, int h);
        void onAudioConfigParamsReceived(byte[] configParams);
        void onStreamChunkReceived(boolean audio, byte[] chunk, int flags, long timestamp);
    }

    private Listener mListener;

    public WSClient(Listener listener){
        mMainHandler = new Handler(Looper.getMainLooper());
        mListener = listener;
    }

    @Override
    public void connect(final String serverIP, final int port, final int timeout) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    String uri = "ws://" + serverIP + ":" + port;
                    mWebSocket = new WebSocketFactory().createSocket(uri, timeout);
                    mWebSocket.addListener(WSClient.this);
                    mWebSocket.connect();
                    if (VERBOSE) Log.d(TAG, "Successfully connected to " + uri);
                } catch (final Exception e) {
                    mMainHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            if (mListener != null) mListener.onServerUnreachable(e);
                        }
                    });
                    return;
                }
                mMainHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (mListener != null) mListener.onConnectionEstablished();
                    }
                });

            }
        }).start();
    }

    @Override
    public void closeConnection() {
        mWebSocket.sendClose();
    }

    public void requestConfigParams(){
        try {
            JSONObject configMsg = JSONMessageFactory.createConfigRequestMessage();
            mWebSocket.sendText(configMsg.toString());
        } catch (JSONException e) {

        }
    }

    @Override
    public void onTextMessage(WebSocket websocket, String text) throws Exception {
        JSONObject obj = null;
        try{
            obj = new JSONObject(text);
            if (obj.has("type")){
                Object type = obj.get("type");
                if (type.equals("config-video")) {
                    String sParams = obj.getString("configArray");
                    int width = obj.getInt("width");
                    int height = obj.getInt("height");
                    final byte[] params = Base64.decode(sParams, Base64.DEFAULT);
                    if (mListener != null) mListener.onVideoConfigParamsReceived(params, width, height);
                }
                else if (type.equals("config-audio")) {
                    String sParams = obj.getString("audioConfig");
                    final byte[] params = Base64.decode(sParams, Base64.DEFAULT);
                    if (mListener != null) mListener.onAudioConfigParamsReceived(params);
                }
                else if (type.equals("video")){
                    String sChunk = obj.getString("data");
                    final byte[] chunk = Base64.decode(sChunk, Base64.DEFAULT);
                    final int flags = obj.getInt("flags");
                    final long timestamp = obj.getLong("ts");
                    if (mListener != null) mListener.onStreamChunkReceived(false, chunk, flags, timestamp);
                }
                else if (type.equals("audio")){
                    String sChunk = obj.getString("data");
                    final byte[] chunk = Base64.decode(sChunk, Base64.DEFAULT);
                    final int flags = obj.getInt("flags");
                    final long timestamp = obj.getLong("ts");
                    if (mListener != null) mListener.onStreamChunkReceived(true, chunk, flags, timestamp);
                }
            }
        }
        catch(JSONException e){
            Log.e(TAG, "Can't parse JSON from text: "+text);
        }

    }
}
