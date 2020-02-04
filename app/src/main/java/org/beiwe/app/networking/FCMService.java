package org.beiwe.app.networking;

import android.util.Log;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import org.beiwe.app.storage.PersistentData;

public class FCMService extends FirebaseMessagingService {

    /**
     * Called if InstanceID token is updated. This may occur if the security of
     * the previous token had been compromised. Note that this is called when the InstanceID token
     * is initially generated so this is where you would retrieve the token.
     */
    @Override
    public void onNewToken(String token) {
        Log.i("FCM", "Refreshed token: " + token);

        // If you want to send messages to this application instance or
        // manage this apps subscriptions on the server side, send the
        // Instance ID token to your app server.

        PersistentData.setFCMInstanceID(token);
        PostRequest.setFCMInstanceID(token);
    }

    @Override
    public void onMessageReceived(RemoteMessage remoteMessage) {
        Log.i("FCM", "From: " + remoteMessage.getFrom());

        // Check if message contains a data payload.
        if (remoteMessage.getData().size() > 0) {
            Log.i("FCM", "Message data payload: " + remoteMessage.getData());

        }

        if (remoteMessage.getNotification() != null) {
            Log.i("FCM", "Message Notification Body: " + remoteMessage.getNotification().getBody());
        }
    }
}
