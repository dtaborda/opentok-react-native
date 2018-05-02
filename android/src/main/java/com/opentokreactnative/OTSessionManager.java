package com.opentokreactnative;

/**
 * Created by manik on 1/29/18.
 */

import android.util.Log;
import android.widget.FrameLayout;
import android.support.annotation.Nullable;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.Callback;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.UiThreadUtil;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.modules.core.DeviceEventManagerModule;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.ReadableArray;

import com.opentok.android.Session;
import com.opentok.android.Connection;
import com.opentok.android.Publisher;
import com.opentok.android.PublisherKit;
import com.opentok.android.Stream;
import com.opentok.android.OpentokError;
import com.opentok.android.Subscriber;
import com.opentok.android.SubscriberKit;

import java.util.concurrent.ConcurrentHashMap;
import java.util.ArrayList;

public class OTSessionManager extends ReactContextBaseJavaModule
        implements Session.SessionListener,
        PublisherKit.PublisherListener,
        PublisherKit.AudioLevelListener,
        SubscriberKit.SubscriberListener,
        Session.SignalListener,
        Session.ConnectionListener,
        Session.ReconnectionListener,
        Session.ArchiveListener,
        SubscriberKit.AudioLevelListener,
        SubscriberKit.AudioStatsListener,
        SubscriberKit.VideoStatsListener,
        SubscriberKit.VideoListener{

    private Callback connectCallback;
    private Callback disconnectCallback;
    private ArrayList<String> jsEvents = new ArrayList<String>();
    private ArrayList<String> componentEvents = new ArrayList<String>();
    private static final String TAG = "OTRN";
    private final String sessionPreface = "session:";
    private final String publisherPreface = "publisher:";
    private final String subscriberPreface = "subscriber:";
    private boolean isPublishing = false;
    public OTRN sharedState;

    public OTSessionManager(ReactApplicationContext reactContext) {

        super(reactContext);
        sharedState = OTRN.getSharedState();

    }

    @ReactMethod
    public void initSession(String apiKey, String sessionId) {

        Session mSession = new Session.Builder(this.getReactApplicationContext(), apiKey, sessionId).build();
        mSession.setSessionListener(this);
        mSession.setSignalListener(this);
        mSession.setConnectionListener(this);
        mSession.setReconnectionListener(this);
        mSession.setArchiveListener(this);
        sharedState.setSession(mSession);
    }

    @ReactMethod
    public void connect(String token, Callback callback) {

        Session mSession = sharedState.getSession();
        mSession.connect(token);
        connectCallback = callback;
    }

    @ReactMethod
    public void initPublisher(ReadableMap properties) {

        String name = properties.getString("name");
        Boolean videoTrack = properties.getBoolean("videoTrack");
        Boolean audioTrack = properties.getBoolean("audioTrack");
        String cameraPosition = properties.getString("cameraPosition");
        Boolean audioFallbackEnabled = properties.getBoolean("audioFallbackEnabled");
        int audioBitrate = properties.getInt("audioBitrate");
        String frameRate = "FPS_" + properties.getInt("frameRate");
        String resolution = properties.getString("resolution");
        Boolean publishAudio = properties.getBoolean("publishAudio");
        Boolean publishVideo = properties.getBoolean("publishVideo");

        Publisher mPublisher = new Publisher.Builder(this.getReactApplicationContext())
                                    .audioTrack(audioTrack)
                                    .videoTrack(videoTrack)
                                    .name(name)
                                    .audioBitrate(audioBitrate)
                                    .resolution(Publisher.CameraCaptureResolution.valueOf(resolution))
                                    .frameRate(Publisher.CameraCaptureFrameRate.valueOf(frameRate))
                                    .build();
        mPublisher.setPublisherListener(this);
        mPublisher.setAudioLevelListener(this);
        mPublisher.setAudioFallbackEnabled(audioFallbackEnabled);
        mPublisher.setPublishVideo(publishVideo);
        mPublisher.setPublishAudio(publishAudio);
        if (cameraPosition.equals("back")) {
            mPublisher.cycleCamera();
        }
        sharedState.setPublisher(mPublisher);
    }

    @ReactMethod
    public void publish(Callback callback) {

        Session mSession = sharedState.getSession();
        Publisher mPublisher = sharedState.getPublisher();
        mSession.publish(mPublisher);
        callback.invoke();

    }

    @ReactMethod
    public void subscribeToStream(String streamId, ReadableMap properties, Callback callback) {

        ConcurrentHashMap<String, Stream> mSubscriberStreams = sharedState.getSubscriberStreams();
        ConcurrentHashMap<String, Subscriber> mSubscribers = sharedState.getSubscribers();
        Session mSession = sharedState.getSession();
        Stream stream = mSubscriberStreams.get(streamId);
        Subscriber mSubscriber = new Subscriber.Builder(getReactApplicationContext(), stream).build();
        mSubscriber.setSubscriberListener(this);
        mSubscriber.setAudioLevelListener(this);
        mSubscriber.setAudioStatsListener(this);
        mSubscriber.setVideoStatsListener(this);
        mSubscriber.setVideoListener(this);
        mSubscriber.setSubscribeToAudio(properties.getBoolean("subscribeToAudio"));
        mSubscriber.setSubscribeToVideo(properties.getBoolean("subscribeToVideo"));
        mSubscribers.put(streamId, mSubscriber);
        mSession.subscribe(mSubscriber);
        callback.invoke();

    }

    @ReactMethod
    public void removeSubscriber(final String streamId, final Callback callback) {

        UiThreadUtil.runOnUiThread(new Runnable() {
       @Override
       public void run() {

           String mStreamId = streamId;
           Callback mCallback = callback;
           ConcurrentHashMap<String, Subscriber> mSubscribers = sharedState.getSubscribers();
           ConcurrentHashMap<String, Stream> mSubscriberStreams = sharedState.getSubscriberStreams();
           ConcurrentHashMap<String, FrameLayout> mSubscriberViewContainers = sharedState.getSubscriberViewContainers();
           Subscriber mSubscriber = mSubscribers.get(mStreamId);
           mSubscriberViewContainers.get(mStreamId).removeAllViews();
           mSubscriberViewContainers.remove(mStreamId);
           mSubscriber.destroy();
           mSubscribers.remove(mStreamId);
           mSubscriberStreams.remove(mStreamId);
           mCallback.invoke();

       }
     });
    }

    @ReactMethod
    public void disconnectSession(Callback callback) {

        Session mSession = sharedState.getSession();
        mSession.disconnect();
        sharedState.setSession(null);
        disconnectCallback = callback;
    }

    @ReactMethod
    public void publishAudio(Boolean publishAudio) {

        Publisher mPublisher = sharedState.getPublisher();
        mPublisher.setPublishAudio(publishAudio);
    }

    @ReactMethod
    public void publishVideo(Boolean publishVideo) {

        Publisher mPublisher = sharedState.getPublisher();
        mPublisher.setPublishVideo(publishVideo);
    }

    @ReactMethod
    public void changeCameraPosition(String cameraPosition) {

        Publisher mPublisher = sharedState.getPublisher();
        mPublisher.cycleCamera();
        Log.i(TAG, "Changing camera to " + cameraPosition);
    }

    @ReactMethod
    public void setNativeEvents(ReadableArray events) {

        for (int i = 0; i < events.size(); i++) {
            jsEvents.add(events.getString(i));
        }
    }

    @ReactMethod
    public void removeNativeEvents(ReadableArray events) {

        for (int i = 0; i < events.size(); i++) {
            jsEvents.remove(events.getString(i));
        }
    }

    @ReactMethod
    public void setJSComponentEvents(ReadableArray events) {

        for (int i = 0; i < events.size(); i++) {
            componentEvents.add(events.getString(i));
        }
    }

    @ReactMethod
    public void removeJSComponentEvents(ReadableArray events) {

        for (int i = 0; i < events.size(); i++) {
            componentEvents.remove(events.getString(i));
        }
    }

    @ReactMethod
    public void sendSignal(ReadableMap signal, Callback callback) {

        Session mSession = sharedState.getSession();
        mSession.sendSignal(signal.getString("type"), signal.getString("data"));
        callback.invoke();
    }

    @ReactMethod
    public void destroyPublisher(final Callback callback) {

        UiThreadUtil.runOnUiThread(new Runnable() {
            @Override
            public void run() {

                Callback mCallback = callback;
                FrameLayout mPublisherViewContainer = sharedState.getPublisherViewContainer();
                Publisher mPublisher = sharedState.getPublisher();
                Session mSession = sharedState.getSession();
                if (mSession != null && isPublishing) {
                    mSession.unpublish(mPublisher);
                }
                mPublisher.destroy();
                mPublisherViewContainer.removeAllViews();
                isPublishing = false;
                sharedState.setPublisherViewContainer(null);             
                sharedState.setPublisher(null);
                mCallback.invoke();

            }
        });
    }

    @ReactMethod
    public void getSessionInfo(Callback callback) {

        Session mSession = sharedState.getSession();
        WritableMap sessionInfo = Arguments.createMap();
        sessionInfo.putString("sessionId", mSession.getSessionId());
        sessionInfo.putMap("connection", prepareConnectionMap(mSession.getConnection()));
        callback.invoke(sessionInfo);
    }

    private boolean contains(ArrayList array, String value) {

        for (int i = 0; i < array.size(); i++) {
            if (array.get(i).equals(value)) {
                return true;
            }
        }
        return false;
    }

    private WritableMap prepareStreamMap(Stream stream) {

        WritableMap streamInfo = Arguments.createMap();
        streamInfo.putString("streamId", stream.getStreamId());
        streamInfo.putInt("height", stream.getVideoHeight());
        streamInfo.putInt("width", stream.getVideoWidth());
        streamInfo.putString("creationTime", stream.getCreationTime().toString());
        streamInfo.putString("connectionId", stream.getConnection().getConnectionId());
        streamInfo.putString("name", stream.getName());
        streamInfo.putBoolean("audio", stream.hasAudio());
        streamInfo.putBoolean("video", stream.hasVideo());
        return streamInfo;
    }

    private WritableMap prepareErrorMap(OpentokError error) {

        WritableMap errorInfo = Arguments.createMap();
        errorInfo.putString("message", error.getMessage());
        errorInfo.putString("code", error.getErrorCode().toString());
        return errorInfo;
    }

    private WritableMap prepareConnectionMap(Connection connection) {

        WritableMap connectionInfo = Arguments.createMap();
        connectionInfo.putString("connectionId", connection.getConnectionId());
        connectionInfo.putString("creationTime", connection.getCreationTime().toString());
        connectionInfo.putString("data", connection.getData());
        return connectionInfo;
    }

    private void sendEvent(ReactContext reactContext, String eventName, String eventData) {

        reactContext
                .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
                .emit(eventName, eventData);
    }

    private void sendEventMap(ReactContext reactContext, String eventName, @Nullable WritableMap eventData) {

        reactContext
                .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
                .emit(eventName, eventData);
    }

    @Override
    public String getName() {

        return this.getClass().getSimpleName();
    }

    @Override
    public void onError(Session session, OpentokError opentokError) {

        if (contains(jsEvents, sessionPreface + "onError")) {
            WritableMap errorInfo = prepareErrorMap(opentokError);
            sendEventMap(this.getReactApplicationContext(), sessionPreface + "onError", errorInfo);
        }
        Log.i(TAG, "There was an error");
    }

    @Override
    public void onDisconnected(Session session) {
        if (disconnectCallback != null) {
            disconnectCallback.invoke();
        }

        if (contains(jsEvents, sessionPreface + "onDisconnected")) {
            sendEvent(this.getReactApplicationContext(), sessionPreface + "onDisconnected", null);

        }
        Log.i(TAG, "onDisconnected: Disconnected from session: " + session.getSessionId());

    }

    @Override
    public void onStreamReceived(Session session, Stream stream) {

        if (contains(jsEvents, sessionPreface + "onStreamReceived") || contains(componentEvents, sessionPreface + "onStreamReceived")) {
            ConcurrentHashMap<String, Stream> mSubscriberStreams = sharedState.getSubscriberStreams();
            mSubscriberStreams.put(stream.getStreamId(), stream);
            WritableMap streamInfo = prepareStreamMap(stream);
            sendEventMap(this.getReactApplicationContext(), sessionPreface + "onStreamReceived", streamInfo);
        }
        Log.i(TAG, "onStreamReceived: New Stream Received " + stream.getStreamId() + " in session: " + session.getSessionId());

    }

    @Override
    public void onConnected(Session session) {

        connectCallback.invoke();
        if (contains(jsEvents, sessionPreface + "onConnected") || contains(componentEvents, sessionPreface + "onConnected")) {
            sendEvent(this.getReactApplicationContext(), sessionPreface + "onConnected", null);
        }
        Log.i(TAG, "onConnected: Connected to session: "+session.getSessionId());
        connectCallback = null;
    }

    @Override
    public void onReconnected(Session session) {

        if (contains(jsEvents, sessionPreface + "onReconnected")) {
            sendEvent(this.getReactApplicationContext(), sessionPreface + "onReconnected", null);
        }
        Log.i(TAG, "Reconnected");
    }

    @Override
    public void onReconnecting(Session session) {

        if (contains(jsEvents, sessionPreface + "onReconnecting")) {
            sendEvent(this.getReactApplicationContext(), sessionPreface + "onReconnecting", null);
        }
        Log.i(TAG, "Reconnecting");
    }

    @Override
    public void onArchiveStarted(Session session, String id, String name) {

        if (contains(jsEvents, sessionPreface + "onArchiveStarted")) {
            WritableMap archiveInfo = Arguments.createMap();
            archiveInfo.putString("archiveId", id);
            archiveInfo.putString("name", name);
            sendEventMap(this.getReactApplicationContext(), sessionPreface + "onArchiveStarted", archiveInfo);
        }
        Log.i(TAG, "Archive Started: " + id);
    }

    @Override
    public void onArchiveStopped(Session session, String id) {

        if (contains(jsEvents, sessionPreface + "onArchiveStopped")) {
            sendEvent(this.getReactApplicationContext(), sessionPreface + "onArchiveStopped", id);
        }
        Log.i(TAG, "Archive Stopped: " + id);
    }
    @Override
    public void onConnectionCreated(Session session, Connection connection) {

        if (contains(jsEvents, sessionPreface + "onConnectionCreated")) {
            WritableMap connectionInfo = prepareConnectionMap(connection);
            sendEventMap(this.getReactApplicationContext(), sessionPreface + "onConnectionCreated", connectionInfo);
        }
        Log.i(TAG, "onConnectionCreated: Connection Created: "+connection.getConnectionId());
   }

    @Override
    public void onConnectionDestroyed(Session session, Connection connection) {

        if (contains(jsEvents, sessionPreface + "onConnectionDestroyed")) {
            WritableMap connectionInfo = prepareConnectionMap(connection);
            sendEventMap(this.getReactApplicationContext(), sessionPreface + "onConnectionDestroyed", connectionInfo);
        }
        Log.i(TAG, "onConnectionDestroyed: Connection Destroyed: "+connection.getConnectionId());
    }
    @Override
    public void onStreamDropped(Session session, Stream stream) {

        if (contains(jsEvents, sessionPreface + "onStreamDropped") || contains(componentEvents, sessionPreface + "onStreamDropped")) {
            WritableMap streamInfo = prepareStreamMap(stream);
            sendEventMap(this.getReactApplicationContext(), sessionPreface + "onStreamDropped", streamInfo);
        }
        Log.i(TAG, "onStreamDropped: Stream Dropped: "+stream.getStreamId() +" in session: "+session.getSessionId());
    }

    @Override
    public void onStreamCreated(PublisherKit publisherKit, Stream stream) {

        if (contains(jsEvents, publisherPreface + "onStreamCreated")) {
            WritableMap streamInfo = prepareStreamMap(stream);
            sendEventMap(this.getReactApplicationContext(), publisherPreface +  "onStreamCreated", streamInfo);
        }
        isPublishing = true;
        Log.i(TAG, "onStreamCreated: Publisher Stream Created. Own stream "+stream.getStreamId());

    }

    @Override
    public void onStreamDestroyed(PublisherKit publisherKit, Stream stream) {

        if (contains(jsEvents, publisherPreface +  "onStreamDestroyed")) {
            WritableMap streamInfo = prepareStreamMap(stream);
            sendEventMap(this.getReactApplicationContext(), publisherPreface +  "onStreamDestroyed", streamInfo);
        }
        isPublishing = false;
        Log.i(TAG, "onStreamDestroyed: Publisher Stream Destroyed. Own stream "+stream.getStreamId());
    }

    @Override
    public void onError(PublisherKit publisherKit, OpentokError opentokError) {

        if (contains(jsEvents, publisherPreface +  "onError")) {
            WritableMap errorInfo = prepareErrorMap(opentokError);
            sendEventMap(this.getReactApplicationContext(), publisherPreface +  "onError", errorInfo);
        }
        Log.i(TAG, "onError: "+opentokError.getErrorDomain() + " : " +
                opentokError.getErrorCode() +  " - "+opentokError.getMessage());
    }

    @Override
    public void onAudioLevelUpdated(PublisherKit publisher, float audioLevel) {

        if(contains(jsEvents, publisherPreface + "onAudioLevelUpdated")) {
            sendEvent(this.getReactApplicationContext(), publisherPreface + "onAudioLevelUpdated", String.valueOf(audioLevel));
        }
    }

    @Override
    public void onConnected(SubscriberKit subscriberKit) {

        if (contains(jsEvents, subscriberPreface +  "onConnected")) {
            sendEvent(this.getReactApplicationContext(), subscriberPreface +  "onConnected", null);
        }
        Log.d(TAG, "onConnected: Subscriber connected. Stream: "+subscriberKit.getStream().getStreamId());
    }

    @Override
    public void onDisconnected(SubscriberKit subscriberKit) {

        if (contains(jsEvents, subscriberPreface +  "onDisconnected")) {
            sendEvent(this.getReactApplicationContext(), subscriberPreface +  "onDisconnected", null);
        }
        Log.d(TAG, "onDisconnected: Subscriber disconnected. Stream: "+subscriberKit.getStream().getStreamId());
    }

    @Override
    public void onError(SubscriberKit subscriberKit, OpentokError opentokError) {

        if (contains(jsEvents, subscriberPreface +  "onError")) {
            WritableMap errorInfo = prepareErrorMap(opentokError);
            sendEventMap(this.getReactApplicationContext(), subscriberPreface +  "onError", errorInfo);
        }
        Log.e(TAG, "onError: "+opentokError.getErrorDomain() + " : " +
                opentokError.getErrorCode() +  " - "+opentokError.getMessage());

    }

    @Override
    public void onSignalReceived(Session session, String type, String data, Connection connection) {

        if (contains(jsEvents, sessionPreface + "onSignalReceived")) {
            WritableMap signalInfo = Arguments.createMap();
            signalInfo.putString("type", type);
            signalInfo.putString("data", data);
            signalInfo.putString("connectionId", connection.getConnectionId());
            sendEventMap(this.getReactApplicationContext(), sessionPreface + "onSignalReceived", signalInfo);
        }
        Log.d(TAG, "onSignalReceived: Data: " + data + " Type: " + type);
    }

    @Override
    public void onAudioStats(SubscriberKit subscriber, SubscriberKit.SubscriberAudioStats stats) {

        if (contains(jsEvents, subscriberPreface + "onAudioStats")) {
            WritableMap audioStats = Arguments.createMap();
            audioStats.putInt("audioPacketsLost", stats.audioPacketsLost);
            audioStats.putInt("audioBytesReceived", stats.audioBytesReceived);
            audioStats.putInt("audioPacketsReceived", stats.audioPacketsReceived);
            sendEventMap(this.getReactApplicationContext(), subscriberPreface + "onAudioStats", audioStats);
        }
    }

    @Override
    public void onVideoStats(SubscriberKit subscriber, SubscriberKit.SubscriberVideoStats stats) {

        if (contains(jsEvents, subscriberPreface + "onVideoStats")) {
            WritableMap audioStats = Arguments.createMap();
            audioStats.putInt("videoPacketsLost", stats.videoPacketsLost);
            audioStats.putInt("videoBytesReceived", stats.videoBytesReceived);
            audioStats.putInt("videoPacketsReceived", stats.videoPacketsReceived);
            sendEventMap(this.getReactApplicationContext(), subscriberPreface + "onVideoStats", audioStats);
        }
    }

    @Override
    public void onAudioLevelUpdated(SubscriberKit subscriber, float audioLevel) {

        if (contains(jsEvents, subscriberPreface + "onAudioLevelUpdated")) {
            sendEvent(this.getReactApplicationContext(), subscriberPreface + "onAudioLevelUpdated", String.valueOf(audioLevel));
        }
    }

    @Override
    public void onVideoDisabled(SubscriberKit subscriber, String reason) {

        if (contains(jsEvents, subscriberPreface + "onVideoDisabled")) {
            sendEvent(this.getReactApplicationContext(), subscriberPreface + "onVideoDisabled", reason);
        }
    }

    @Override
    public void onVideoEnabled(SubscriberKit subscriber, String reason) {

        if (contains(jsEvents, subscriberPreface + "onVideoEnabled")) {
            sendEvent(this.getReactApplicationContext(), subscriberPreface + "onVideoEnabled", reason);
        }
    }

    @Override
    public void onVideoDisableWarning(SubscriberKit subscriber) {

        if (contains(jsEvents, subscriberPreface + "onVideoDisableWarning")) {
            sendEvent(this.getReactApplicationContext(), subscriberPreface + "onVideoDisableWarning", null);
        }
    }

    @Override
    public void onVideoDisableWarningLifted(SubscriberKit subscriber) {

        if (contains(jsEvents, subscriberPreface + "onVideoDisableWarningLifted")) {
            sendEvent(this.getReactApplicationContext(), subscriberPreface + "onVideoDisableWarningLifted", null);
        }
    }

    @Override
    public void onVideoDataReceived(SubscriberKit subscriber) {

        if (contains(jsEvents, subscriberPreface + "onVideoDataReceived")) {
            sendEvent(this.getReactApplicationContext(), subscriberPreface + "onVideoDataReceived", null);
        }
    }
}
