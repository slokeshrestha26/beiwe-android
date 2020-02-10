package org.beiwe.app.networking;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.BitmapFactory;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import org.beiwe.app.R;
import org.beiwe.app.storage.PersistentData;
import org.beiwe.app.ui.user.MainMenuActivity;
import org.beiwe.app.ui.utils.SurveyNotifications;


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

        Log.i("FCM", "Content: " + remoteMessage.getData().get("content"));

        Context appContext = getApplicationContext();
        // Using NotificationCompat.Builder for devices < API 26. For devices running >=26, create
        // a Notification Channel and use Notification.Builder
        NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(appContext);
        Intent intent;
        if (remoteMessage.getData().get("type").equals("survey")) {
            String surveyId = remoteMessage.getData().get("survey_id");
            SurveyDownloader.downloadSingleSurvey(appContext, surveyId);
            SurveyNotifications.displaySurveyNotification(appContext, surveyId);
        } else {
            intent = new Intent(appContext, MainMenuActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            PendingIntent pendingIntent = PendingIntent.getActivity(appContext, 0, intent, PendingIntent.FLAG_CANCEL_CURRENT);
            notificationBuilder.setSmallIcon(R.drawable.survey_icon);
            notificationBuilder.setLargeIcon( BitmapFactory.decodeResource(appContext.getResources(), R.drawable.survey_icon ) );
            notificationBuilder.setContentTitle("Hello There!");
            notificationBuilder.setContentText("General Kenobi, you are a bold one");
            notificationBuilder.setContentIntent(pendingIntent);
            notificationBuilder.setAutoCancel(true);
            NotificationManager notificationManager = (NotificationManager) appContext.getSystemService(Context.NOTIFICATION_SERVICE);
            notificationManager.notify(0, notificationBuilder.build());
        }

    }
}
