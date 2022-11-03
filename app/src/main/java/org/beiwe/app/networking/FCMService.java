package org.beiwe.app.networking;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import org.beiwe.app.JSONUtils;
import org.beiwe.app.storage.PersistentData;
import org.beiwe.app.storage.TextFileManager;
import org.json.JSONArray;
import org.json.JSONException;

import java.util.List;
import java.util.Map;

import static org.beiwe.app.UtilsKt.printe;
import static org.beiwe.app.UtilsKt.printi;


public class FCMService extends FirebaseMessagingService {
    /**
     * Called if InstanceID token is updated. This may occur if the security of
     * the previous token had been compromised. Note that this is called when the InstanceID token
     * is initially generated so this is where you would retrieve the token. */
    @Override
    public void onNewToken (String token) {
        // If you want to send messages to this application instance or
        // manage this apps subscriptions on the server side, send the
        // Instance ID token to your app server.
        final String errorMessage = "Unable to get FCM token, will not be able to receive push notifications.";
        final String final_token = token;
        Thread fcmBlockerThread = new Thread(new Runnable() {
            @Override
            public void run () {
                while (!PersistentData.getIsRegistered()) {
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException ignored) {
                        printe(errorMessage + "(3)");
                        TextFileManager.writeDebugLogStatement(errorMessage + "(3)");
                        return;
                    }
                }
                PersistentData.setFCMInstanceID(final_token);
                PostRequest.setFCMInstanceID(final_token);
            }
        }, "fcmBlockerThread");
        fcmBlockerThread.start();
    }

    @Override
    public void onMessageReceived(RemoteMessage remoteMessage) {
        printi("FCM", "From: " + remoteMessage.getFrom());
        Map<String, String> data = remoteMessage.getData();
        // Check if message contains a data payload.
//        if (remoteMessage.getData().size() > 0)
//            printi("FCM", "Message data payload: " + remoteMessage.getData());
//        if (remoteMessage.getNotification() != null)
//            printi("FCM", "Message Notification Body: " + remoteMessage.getNotification().getBody());
//        printi("FCM", "Content: " + data.get("content"));
        
        if (data.get("type").equals("survey")) {
            // check for surveys first.  This can fail (gracefully), so handle the case where the
            // device does not have a particular survey.

            // Get the list of survey_ids from the push notification's JSON data
            List<String> notificationSurveyIds;
            try {
                notificationSurveyIds = JSONUtils.jsonArrayToStringList(new JSONArray(data.get("survey_ids")));
            } catch (JSONException e) {
                e.printStackTrace();
                String errorMsg = "received unparsable push notification for new surveys";
                printe(errorMsg);
                TextFileManager.writeDebugLogStatement(errorMsg);
                return;
            }

            SurveyDownloader.downloadSurveys(getApplicationContext(), notificationSurveyIds);
        }
    }
}
