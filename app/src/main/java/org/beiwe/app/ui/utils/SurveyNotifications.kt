package org.beiwe.app.ui.utils

import android.annotation.SuppressLint
import android.app.Notification
import org.beiwe.app.printe
import org.beiwe.app.JSONUtils
import org.beiwe.app.storage.PersistentData
import org.beiwe.app.ui.utils.SurveyNotifications
import org.beiwe.app.storage.TextFileManager
import android.os.Build
import android.content.Intent
import org.beiwe.app.R
import org.beiwe.app.survey.SurveyActivity
import android.graphics.BitmapFactory
import android.app.PendingIntent
import android.app.NotificationManager
import androidx.core.app.NotificationCompat
import android.app.NotificationChannel
import android.content.Context
import android.graphics.Color
import android.service.notification.StatusBarNotification
import android.util.Log
import org.beiwe.app.pending_intent_flag_fix
import org.json.JSONObject
import org.beiwe.app.survey.AudioRecorderEnhancedActivity
import org.json.JSONException
import org.beiwe.app.survey.AudioRecorderActivity

/**The purpose of this class is to deal with all that has to do with Survey Notifications.
 * This is a STATIC method, and is called from the main service.
 * @author Eli Jones */
object SurveyNotifications {
    private const val CHANNEL_ID = "survey_notification_channel"

    /**Show notifications for each survey in surveyIds, as long as that survey exists in PersistentData. */
    @JvmStatic
    fun showSurveyNotifications(appContext: Context, surveyIds: List<String>?) {
        // return early if empty
        if (surveyIds == null || surveyIds.isEmpty())  // ah, || short circuits, keep, don't use 'or'
            return

        val idsOfStoredSurveys = JSONUtils.jsonArrayToStringList(PersistentData.getSurveyIdsJsonArray())
        for (surveyId in surveyIds) {
            if (idsOfStoredSurveys.contains(surveyId)) {
                displaySurveyNotification(appContext, surveyId)
            } else {
                val errorMsg = "Tried to show notification for survey ID $surveyId but didn't have" +
                        " that survey stored in PersistentData."
                printe(errorMsg)
                TextFileManager.writeDebugLogStatement(errorMsg)
            }
        }
    }

    /**Creates a survey notification that transfers the user to the survey activity.
     * Note: the notification can only be dismissed through submitting the survey
     * @param appContext */
    @JvmStatic
    fun displaySurveyNotification(appContext: Context, surveyId: String) {
        //activityIntent contains information on the action triggered by tapping the notification.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ensureSurveyNotificationChannelExists(appContext)
            displaySurveyNotificationNew(appContext, surveyId)
        } else 
            displaySurveyNotificationOld(appContext, surveyId)
    }

    /** Survey notification function for phones running api versions O or newer
     * Calls PersistentData.setSurveyNotificationState
     * Uses Notification.Builder
     * @param appContext
     * @param surveyId */
    private fun displaySurveyNotificationNew(appContext: Context, surveyId: String) {
        val notificationBuilder = Notification.Builder(appContext, CHANNEL_ID)
        notificationBuilder.setContentTitle(appContext.getString(R.string.survey_notification_app_name))
        notificationBuilder.setShowWhen(true) // As of API 24 this no longer defaults to true and must be set explicitly
        var survey_name = PersistentData.getSurveyName(surveyId)?: ""
        if (survey_name != "")
            survey_name = " \"$survey_name\""

        // build the activity intent based on the type of survey
        val activityIntent: Intent
        if (PersistentData.getSurveyType(surveyId) == "tracking_survey") {
            activityIntent = Intent(appContext, SurveyActivity::class.java)
            activityIntent.action = appContext.getString(R.string.start_tracking_survey)
            notificationBuilder.setTicker(appContext.resources.getString(R.string.new_android_survey_notification_ticker))
            notificationBuilder.setContentText(appContext.resources.getString(R.string.new_android_survey_notification_details) + survey_name)
            notificationBuilder.setSmallIcon(R.drawable.survey_icon)
            notificationBuilder.setLargeIcon(BitmapFactory.decodeResource(appContext.resources, R.drawable.survey_icon))
            notificationBuilder.setGroup(surveyId)
        } else if (PersistentData.getSurveyType(surveyId) == "audio_survey") {
            activityIntent = Intent(appContext, getAudioSurveyClass(surveyId))
            activityIntent.action = appContext.getString(R.string.start_audio_survey)
            notificationBuilder.setTicker(appContext.resources.getString(R.string.new_audio_survey_notification_ticker))
            notificationBuilder.setContentText(appContext.resources.getString(R.string.new_audio_survey_notification_details) + survey_name)
            notificationBuilder.setSmallIcon(R.drawable.voice_recording_icon)
            notificationBuilder.setLargeIcon(BitmapFactory.decodeResource(appContext.resources, R.drawable.voice_recording_icon))
            notificationBuilder.setGroup(surveyId)
        } else {
            TextFileManager.writeDebugLogStatement("encountered unknown survey type: " + PersistentData.getSurveyType(surveyId) + ", cannot schedule survey.")
            return
        }
        
        activityIntent.putExtra("surveyId", surveyId)
        activityIntent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP //modifies behavior when the user is already in the app.

        //This value is used inside the notification (and the pending intent) as the unique Identifier of that notification, this value must be an int.
        //note: recommendations about not using the .hashCode function in java are in usually regards to Object.hashCode(),
        // or are about the fact that the specific hash algorithm is not necessarily consistent between versions of the JVM.
        // If you look at the source of the String class hashCode function you will see that it operates on the value of the string, this is all we need.
        val surveyIdHash = surveyId.hashCode()

        /* The pending intent defines properties of the notification itself.
		 * BUG. Cannot use FLAG_UPDATE_CURRENT, which handles conflicts of multiple notification with the same id,
		 * so that the new notification replaces the old one.  if you use FLAG_UPDATE_CURRENT the notification will
		 * not launch the provided activity on android api 19.
		 * Solution: use FLAG_CANCEL_CURRENT, it provides the same functionality for our purposes.
		 * (or add android:exported="true" to the activity's permissions in the Manifest.)
		 * http://stackoverflow.com/questions/21250364/notification-click-not-launch-the-given-activity-on-nexus-phones */
        // We manually cancel the notification anyway, so this is likely moot.
        // UPDATE: when targetting api version 31 and above we have to set FLAG_IMMUTABLE (or mutable)

        val intent_flag = pending_intent_flag_fix(PendingIntent.FLAG_CANCEL_CURRENT)
        val pendingActivityIntent = PendingIntent.getActivity(appContext, surveyIdHash, activityIntent, intent_flag)
        notificationBuilder.setContentIntent(pendingActivityIntent)
        val surveyNotification = notificationBuilder.build()
        surveyNotification.flags = Notification.FLAG_ONGOING_EVENT
        val notificationManager = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(surveyIdHash) //cancel any current notification with this id hash
        notificationManager.notify(surveyIdHash, surveyNotification)  // If another notification with the same ID pops up, this notification will be updated/cancelled.

        //And, finally, set the notification state for zombie alarms.
        PersistentData.setSurveyNotificationState(surveyId, true)

        // Check if notifications have been disabled
        if (!notificationManager.areNotificationsEnabled()) {
            TextFileManager.writeDebugLogStatement("Participant has blocked notifications (1)")
            Log.e("SurveyNotifications", "Participant has blocked notifications (1)")
        }
    }

    /**Survey notification function for phones running api versions older than O
     * Uses NotificationCompat.Builder
     * @param appContext
     * @param surveyId */
    @SuppressLint("UnspecifiedImmutableFlag")  // this is the compat version of the function
    private fun displaySurveyNotificationOld(appContext: Context, surveyId: String) {
        //activityIntent contains information on the action triggered by tapping the notification.
        val notificationBuilder = NotificationCompat.Builder(appContext)
        notificationBuilder.setContentTitle(appContext.getString(R.string.survey_notification_app_name))
        var survey_name = PersistentData.getSurveyName(surveyId)?: ""
        if (survey_name != "")
            survey_name = " \"$survey_name\""
        val activityIntent: Intent
        if (PersistentData.getSurveyType(surveyId) == "tracking_survey") {
            activityIntent = Intent(appContext, SurveyActivity::class.java)
            activityIntent.action = appContext.getString(R.string.start_tracking_survey)
            notificationBuilder.setTicker(appContext.resources.getString(R.string.new_android_survey_notification_ticker))
            notificationBuilder.setContentText(appContext.resources.getString(R.string.new_android_survey_notification_details) + survey_name)
            notificationBuilder.setSmallIcon(R.drawable.survey_icon)
            notificationBuilder.setLargeIcon(BitmapFactory.decodeResource(appContext.resources, R.drawable.survey_icon))
            notificationBuilder.setGroup(surveyId)
        } else if (PersistentData.getSurveyType(surveyId) == "audio_survey") {
            activityIntent = Intent(appContext, getAudioSurveyClass(surveyId))
            activityIntent.action = appContext.getString(R.string.start_audio_survey)
            notificationBuilder.setTicker(appContext.resources.getString(R.string.new_audio_survey_notification_ticker))
            notificationBuilder.setContentText(appContext.resources.getString(R.string.new_audio_survey_notification_details) + survey_name)
            notificationBuilder.setSmallIcon(R.drawable.voice_recording_icon)
            notificationBuilder.setLargeIcon(BitmapFactory.decodeResource(appContext.resources, R.drawable.voice_recording_icon))
            notificationBuilder.setGroup(surveyId)
        } else {
            TextFileManager.writeDebugLogStatement("encountered unknown survey type: " + PersistentData.getSurveyType(surveyId) + ", cannot schedule survey.")
            return
        }
        activityIntent.putExtra("surveyId", surveyId)
        activityIntent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP //modifies behavior when the user is already in the app.

        //This value is used inside the notification (and the pending intent) as the unique Identifier of that notification, this value must be an int.
        //note: recommendations about not using the .hashCode function in java are in usually regards to Object.hashCode(),
        // or are about the fact that the specific hash algorithm is not necessarily consistent between versions of the JVM.
        // If you look at the source of the String class hashCode function you will see that it operates on the value of the string, this is all we need.
        val surveyIdHash = surveyId.hashCode()

        /* The pending intent defines properties of the notification itself.
		 * BUG. Cannot use FLAG_UPDATE_CURRENT, which handles conflicts of multiple notification with the same id,
		 * so that the new notification replaces the old one.  if you use FLAG_UPDATE_CURRENT the notification will
		 * not launch the provided activity on android api 19.
		 * Solution: use FLAG_CANCEL_CURRENT, it provides the same functionality for our purposes.
		 * (or add android:exported="true" to the activity's permissions in the Manifest.)
		 * http://stackoverflow.com/questions/21250364/notification-click-not-launch-the-given-activity-on-nexus-phones */
        //we manually cancel the notification anyway, so this is likely moot.
        val pendingActivityIntent = PendingIntent.getActivity(appContext, surveyIdHash, activityIntent, PendingIntent.FLAG_CANCEL_CURRENT)
        notificationBuilder.setContentIntent(pendingActivityIntent)
        val surveyNotification = notificationBuilder.build()
        surveyNotification.flags = Notification.FLAG_ONGOING_EVENT
        val notificationManager = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(surveyIdHash) //cancel any current notification with this id hash
        notificationManager.notify(surveyIdHash,  // If another notification with the same ID pops up, this notification will be updated/cancelled.
                surveyNotification)

        //And, finally, set the notification state for zombie alarms.
        PersistentData.setSurveyNotificationState(surveyId, true)

        // Check if notifications have been disabled
        if (!notificationManager.areNotificationsEnabled()) {
            TextFileManager.writeDebugLogStatement("Participant has blocked notifications (2)")
            Log.e("SurveyNotifications", "Participant has blocked notifications (2)")
        }
    }

    // Apps targeting api 26 or later need a notification channel to display notifications
    private fun ensureSurveyNotificationChannelExists(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // if it already exists return early
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (notificationManager.getNotificationChannel(CHANNEL_ID) != null)
                return

            // Originally copied these from an example, these values could change but its fine?
            val notificationChannel = NotificationChannel(
                    CHANNEL_ID, "Survey Notification", NotificationManager.IMPORTANCE_LOW)
            notificationChannel.description = "The Beiwe App notification channel"
            notificationChannel.enableLights(true)
            notificationChannel.lightColor = Color.RED
            notificationChannel.vibrationPattern = longArrayOf(0, 1000, 500, 1000)
            notificationChannel.enableVibration(true)
            notificationManager.createNotificationChannel(notificationChannel)
        }
    }

    /**Use to dismiss the notification corresponding the surveyIdInt.
     * @param appContext
     * @param surveyId */
    @JvmStatic
    fun dismissNotification(appContext: Context, surveyId: String) {
        val notificationManager = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(surveyId.hashCode())
        PersistentData.setSurveyNotificationState(surveyId, false)
    }

    fun isNotificationActive(appContext: Context, surveyId: String): Boolean {
        val notificationManager = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        // loop over notifications and check if any of them are the surveyId passed in
        for (notification: StatusBarNotification in notificationManager.activeNotifications) {
            if (notification.id == surveyId.hashCode()) {
                return true
            }
        }
        return false
    }

    /**Tries to determine the type of audio survey.  If it is an Enhanced audio survey AudioRecorderEnhancedActivity.class is returned,
     * any other outcome (including an inability to determine type) returns AudioRecorderActivity.class instead.  */
    fun getAudioSurveyClass(surveyId: String): Class<*> {
        try {
            val surveySettings = JSONObject(PersistentData.getSurveySettings(surveyId))
            if (surveySettings.getString("audio_survey_type") == "raw")
                return AudioRecorderEnhancedActivity::class.java
        } catch (e: JSONException) {
            e.printStackTrace()
        }
        return AudioRecorderActivity::class.java
    }
}