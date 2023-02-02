package org.beiwe.app

import android.annotation.SuppressLint
import android.app.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.ConnectivityManager
import android.net.NetworkInfo
import android.net.Uri
import android.os.*
import android.util.Log
import com.google.android.gms.tasks.OnCompleteListener
import com.google.android.gms.tasks.Task
import com.google.firebase.iid.FirebaseInstanceId
import com.google.firebase.iid.InstanceIdResult
import io.sentry.Sentry
import io.sentry.android.AndroidSentryClientFactory
import io.sentry.dsn.InvalidDsnException
import org.beiwe.app.PermissionHandler.checkBluetoothPermissions
import org.beiwe.app.PermissionHandler.checkGpsPermissions
import org.beiwe.app.PermissionHandler.checkWifiPermissions
import org.beiwe.app.PermissionHandler.confirmBluetooth
import org.beiwe.app.PermissionHandler.confirmCalls
import org.beiwe.app.PermissionHandler.confirmTexts
import org.beiwe.app.listeners.*
import org.beiwe.app.networking.PostRequest
import org.beiwe.app.networking.SurveyDownloader
import org.beiwe.app.storage.PersistentData
import org.beiwe.app.storage.TextFileManager
import org.beiwe.app.survey.SurveyScheduler
import org.beiwe.app.ui.user.LoginActivity
import org.beiwe.app.ui.utils.SurveyNotifications.displaySurveyNotification
import java.util.*


class MainService : Service() {
    // unfortunately this variable cannot be made non-null, it is not available at init.
    private var appContext: Context? = null

    // the various listeners for sensor data
    var gpsListener: GPSListener? = null
    var powerStateListener: PowerStateListener? = null
    var accelerometerListener: AccelerometerListener? = null
    var gyroscopeListener: GyroscopeListener? = null
    var bluetoothListener: BluetoothListener? = null

    /*##############################################################################################
    ##############################           App Core Setup              ###########################
    ##############################################################################################*/

    /** onCreate is essentially the constructor for the service, initialize variables here.  */
    override fun onCreate() {
        appContext = this.applicationContext!!
        localHandle = this //yes yes, gross, I know. must instantiate before  registerTimers()

        try {
            val sentryDsn = BuildConfig.SENTRY_DSN
            Sentry.init(sentryDsn, AndroidSentryClientFactory(appContext))
        } catch (ie: InvalidDsnException) {
            Sentry.init(AndroidSentryClientFactory(appContext))
        }

        // report errors from the service to sentry only when this is not the development build
        if (!BuildConfig.APP_IS_DEV)
            Thread.setDefaultUncaughtExceptionHandler(CrashHandler(appContext!!))

        // Initialize everything that is necessary for the app!
        PersistentData.initialize(appContext)
        initializeFireBaseIDToken()
        TextFileManager.initialize(appContext)
        PostRequest.initialize(appContext)
        registerTimers(appContext!!)
        createNotificationChannel()
        doSetup()
    }

    fun doSetup() {
        //Accelerometer, gyro, power state, and wifi don't need permissions or they are checked in
        // the broadcastreceiver logic
        startPowerStateListener()
        gpsListener = GPSListener(appContext!!)
        WifiListener.initialize(appContext)

        if (PersistentData.getAccelerometerEnabled())
            accelerometerListener = AccelerometerListener(appContext!!)
        if (PersistentData.getGyroscopeEnabled())
            gyroscopeListener = GyroscopeListener(appContext!!)

        //Bluetooth, wifi, gps, calls, and texts need permissions
        if (confirmBluetooth(appContext!!))
            startBluetooth()

        if (confirmTexts(appContext!!)) {
            startSmsSentLogger()
            startMmsSentLogger()
        } else if (PersistentData.getTextsEnabled()) {
            sendBroadcast(Timer.checkForSMSEnabledIntent)
        }
        if (confirmCalls(appContext!!))
            startCallLogger()
        else if (PersistentData.getCallsEnabled())
            sendBroadcast(Timer.checkForCallsEnabledIntent)

        //Only do the following if the device is registered
        if (PersistentData.getIsRegistered()) {
            DeviceInfo.initialize(appContext) //if at registration this has already been initialized. (we don't care.)
            startTimers()
        }
    }

    private fun createNotificationChannel() {
        // setup the notification channel so the service can run in the foreground
        val chan = NotificationChannel(NOTIFICATION_CHANNEL_ID, NOTIFICATION_CHANNEL_NAME, NotificationManager.IMPORTANCE_DEFAULT)
        chan.lightColor = Color.BLUE
        chan.lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        chan.setSound(null, null)
        val manager = (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
        manager.createNotificationChannel(chan)
    }

    /** Stops the BackgroundService instance, development tool  */
    fun stop() {
        if (BuildConfig.APP_IS_BETA) this.stopSelf()
    }


    /*#############################################################################
	#########################         Starters              #######################
	#############################################################################*/

    /** Initializes the Bluetooth listener
     * Note: Bluetooth has several checks to make sure that it actually exists on the device with the capabilities we need.
     * Checking for Bluetooth LE is necessary because it is an optional extension to Bluetooth 4.0.  */
    fun startBluetooth() {
        //Note: the Bluetooth listener is a BroadcastReceiver, which means it must have a 0-argument constructor in order for android can instantiate it on broadcast receipts.
        //The following check must be made, but it requires a Context that we cannot pass into the BluetoothListener, so we do the check in the BackgroundService.
        if (appContext!!.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE) && PersistentData.getBluetoothEnabled()) {
            bluetoothListener = BluetoothListener()
            if (bluetoothListener!!.isBluetoothEnabled) {
//				Log.i("Main Service", "success, actually doing bluetooth things.");
                registerReceiver(bluetoothListener, IntentFilter("android.bluetooth.adapter.action.STATE_CHANGED"))
            } else {
                //TODO: Low priority. Eli. Track down why this error log pops up, cleanup.  -- the above check should be for the (new) doesBluetoothCapabilityExist function instead of isBluetoothEnabled
                Log.e("Main Service", "bluetooth Failure. Should not have gotten this far.")
                TextFileManager.getDebugLogFile().writeEncrypted("bluetooth Failure, device should not have gotten to this line of code")
            }
        } else {
            if (PersistentData.getBluetoothEnabled()) {
                TextFileManager.getDebugLogFile().writeEncrypted("Device does not support bluetooth LE, bluetooth features disabled.")
                Log.w("MainS bluetooth init", "Device does not support bluetooth LE, bluetooth features disabled.")
            }
            // else { Log.d("BackgroundService bluetooth init", "Bluetooth not enabled for study."); }
            bluetoothListener = null
        }
    }

    /** Initializes the sms logger.  */
    fun startSmsSentLogger() {
        val smsSentLogger = SmsSentLogger(Handler(), appContext)
        this.contentResolver.registerContentObserver(Uri.parse("content://sms/"), true, smsSentLogger)
    }

    fun startMmsSentLogger() {
        val mmsMonitor = MMSSentLogger(Handler(), appContext)
        this.contentResolver.registerContentObserver(Uri.parse("content://mms/"), true, mmsMonitor)
    }

    /** Initializes the call logger.  */
    private fun startCallLogger() {
        val callLogger = CallLogger(Handler(), appContext)
        this.contentResolver.registerContentObserver(Uri.parse("content://call_log/calls/"), true, callLogger)
    }

    /** Initializes the PowerStateListener.
     * The PowerStateListener requires the ACTION_SCREEN_OFF and ACTION_SCREEN_ON intents
     * be registered programatically. They do not work if registered in the app's manifest.
     * Same for the ACTION_POWER_SAVE_MODE_CHANGED and ACTION_DEVICE_IDLE_MODE_CHANGED filters,
     * though they are for monitoring deeper power state changes in 5.0 and 6.0, respectively.  */
    @SuppressLint("InlinedApi")
    private fun startPowerStateListener() {
        if (powerStateListener == null) {
            val filter = IntentFilter()
            filter.addAction(Intent.ACTION_SCREEN_ON)
            filter.addAction(Intent.ACTION_SCREEN_OFF)
            if (Build.VERSION.SDK_INT >= 21) {
                filter.addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
            }
            if (Build.VERSION.SDK_INT >= 23) {
                filter.addAction(PowerManager.ACTION_DEVICE_IDLE_MODE_CHANGED)
            }
            powerStateListener = PowerStateListener()
            registerReceiver(powerStateListener, filter)
            PowerStateListener.start(appContext)
        }
    }

    /** Gets, sets, and pushes the FCM token to the backend.  */
    fun initializeFireBaseIDToken() {
        val errorMessage = "Unable to get FCM token, will not be able to receive push notifications."

        // Set up the oncomplete listener for the FCM getter code, then wait until registered
        // to actually push it to the server or else the post request will error.

        // This inline function was autoconverted from Java Runnable() syntax, note that the thread
        // name used is waaaay at the end
        FirebaseInstanceId.getInstance().instanceId.addOnCompleteListener(OnCompleteListener {
            task: Task<InstanceIdResult> ->

            // If the task failed, log the error and return, we will catch in the firebase
            // token-changed code, or the FCM_TIMER periodic event.
            if (!task.isSuccessful) {
                Log.e("FCM", errorMessage, task.exception)
                TextFileManager.writeDebugLogStatement("$errorMessage(1)")
                return@OnCompleteListener
            }

            // Get new Instance ID token
            val taskResult = task.result
            if (taskResult == null) {
                TextFileManager.writeDebugLogStatement("$errorMessage(2)")
                return@OnCompleteListener
            }

            //We need to wait until the participant is registered to send the fcm token.
            val token = taskResult.token
            val outerNotifcationBlockerThread = Thread(Runnable {
                while (!PersistentData.getIsRegistered()) {
                    try {
                        Thread.sleep(1000)
                    } catch (ignored: InterruptedException) {
                        TextFileManager.writeDebugLogStatement("$errorMessage(3)")
                        return@Runnable
                    }
                }
                PersistentData.setFCMInstanceID(token)
                PostRequest.sendFCMInstanceID(token)
            }, "outerNotifcationBlockerThread")
            outerNotifcationBlockerThread.start()
        })
    }

    /* Sends the FCM token to the backend. Automatically sets an alarm to run perioditaccally.
	   based on the FCM_TIMER variable. */
    fun sendFcmToken() {
        val fcm_token = PersistentData.getFCMInstanceID()
        if (fcm_token != null)
            PostRequest.sendFCMInstanceID(fcm_token)
        timer!!.setupExactSingleAlarm(FCM_TIMER.toLong(), Timer.sendCurrentFCMTokenIntent)
    }

    /*#############################################################################
	####################            Timer Logic             #######################
	#############################################################################*/
    fun startTimers() {
        val now = System.currentTimeMillis()
        Log.i("BackgroundService", "running startTimer logic.")

        // The accelerometer logic is the one I'm documenting, but the others are similar/identical.
        if (PersistentData.getAccelerometerEnabled()) {  //if accelerometer data recording is enabled and...
            val accelMostRecentAlarmTime = PersistentData.getMostRecentAlarmTime(getString(R.string.turn_accelerometer_on))
            val accelMostRecentOffTime = accelMostRecentAlarmTime - PersistentData.getAccelerometerOffDuration() + 1000
            if (accelMostRecentAlarmTime < now ||  //the most recent accelerometer alarm time is in the past, or...
                    !timer!!.alarmIsSet(Timer.accelerometerOnIntent)) { //there is no scheduled accelerometer-on timer.
                sendBroadcast(Timer.accelerometerOnIntent) // start accelerometer timers (immediately runs accelerometer recording session).
            } else if (timer!!.alarmIsSet(Timer.accelerometerOffIntent) && accelMostRecentOffTime > now) {
                //note: when there is no accelerometer-off timer that means we are in-between scans.  This state is fine, so we don't check for it.
                accelerometerListener!!.turn_on()
            }
        }

        // logic identical to accelerometer
        if (PersistentData.getGyroscopeEnabled()) {
            val gyroMostRecentAlarmTime = PersistentData.getMostRecentAlarmTime(getString(R.string.turn_gyroscope_on))
            val gyroMostRecentOffTime = gyroMostRecentAlarmTime - PersistentData.getGyroscopeOffDuration() + 1000
            if (gyroMostRecentAlarmTime < now || !timer!!.alarmIsSet(Timer.gyroscopeOnIntent))
                sendBroadcast(Timer.gyroscopeOnIntent)
            else if (timer!!.alarmIsSet(Timer.gyroscopeOffIntent) && gyroMostRecentOffTime > now)
                gyroscopeListener!!.turn_on()
        }

        // logic identical to accelerometer
        if (PersistentData.getAmbientAudioEnabled()) {
            val ambientAudioMostRecentAlarmTime = PersistentData.getMostRecentAlarmTime(getString(R.string.turn_ambient_audio_on))
            val ambientAudioMostRecentOffTime = ambientAudioMostRecentAlarmTime - PersistentData.getAmbientAudioOffDuration() + 1000
            if (ambientAudioMostRecentAlarmTime < now || !timer!!.alarmIsSet(Timer.ambientAudioOnIntent))
                sendBroadcast(Timer.ambientAudioOnIntent)
            else if (timer!!.alarmIsSet(Timer.ambientAudioOffIntent) && ambientAudioMostRecentOffTime > now)
                AmbientAudioListener.startRecording(appContext)
        }
        // logic identical to accelerometer
        if (PersistentData.getGpsEnabled()) {
            val gpsMostRecentAlarmTime = PersistentData.getMostRecentAlarmTime(getString(R.string.turn_gps_on))
            val gpsMostRecentOffTime = gpsMostRecentAlarmTime - PersistentData.getGpsOffDuration() + 1000
            if (gpsMostRecentAlarmTime < now || !timer!!.alarmIsSet(Timer.gpsOnIntent))
                sendBroadcast(Timer.gpsOnIntent)
            else if (timer!!.alarmIsSet(Timer.gpsOffIntent) && gpsMostRecentOffTime > now)
                gpsListener!!.turn_on()
        }

        // wifi has a one-time timer
        if (PersistentData.getWifiEnabled()) {
            // the most recent wifi log time is in the past or no timer is set
            val mostRecentWifiScan = PersistentData.getMostRecentAlarmTime(getString(R.string.run_wifi_log))
            if (mostRecentWifiScan < now || !timer!!.alarmIsSet(Timer.wifiLogIntent))
                sendBroadcast(Timer.wifiLogIntent)
        }

        // if Bluetooth recording is enabled and there is no scheduled next-bluetooth-enable event,
        // set up the next Bluetooth-on alarm. (Bluetooth needs to run at absolute points in time,
        // it should not be started if a scheduled event is missed.)
        if (PersistentData.getBluetoothEnabled()) {
            if (confirmBluetooth(appContext!!) && !timer!!.alarmIsSet(Timer.bluetoothOnIntent))
                timer!!.setupExactSingleAbsoluteTimeAlarm(
                        PersistentData.getBluetoothTotalDuration(),
                        PersistentData.getBluetoothGlobalOffset(),
                        Timer.bluetoothOnIntent
                )
        }

        // Functionality timers. We don't need aggressive checking for if these timers have been missed, as long as they run eventually it is fine.
        if (!timer!!.alarmIsSet(Timer.uploadDatafilesIntent))
            timer!!.setupExactSingleAlarm(PersistentData.getUploadDataFilesFrequency(), Timer.uploadDatafilesIntent)
        if (!timer!!.alarmIsSet(Timer.createNewDataFilesIntent))
            timer!!.setupExactSingleAlarm(PersistentData.getCreateNewDataFilesFrequency(), Timer.createNewDataFilesIntent)
        if (!timer!!.alarmIsSet(Timer.checkForNewSurveysIntent))
            timer!!.setupExactSingleAlarm(PersistentData.getCheckForNewSurveysFrequency(), Timer.checkForNewSurveysIntent)

        //checks for the current expected state for survey notifications,
        for (surveyId in PersistentData.getSurveyIds()) {
            if (PersistentData.getSurveyNotificationState(surveyId) || PersistentData.getMostRecentSurveyAlarmTime(surveyId) < now)
                //if survey notification should be active or the most recent alarm time is in the past, trigger the notification.
                displaySurveyNotification(appContext!!, surveyId!!)
        }

        // checks that surveys are actually scheduled, if a survey is not scheduled, schedule it!
        for (surveyId in PersistentData.getSurveyIds()) {
            if (!timer!!.alarmIsSet(Intent(surveyId)))
                SurveyScheduler.scheduleSurvey(surveyId)
        }

        // a repeating alarm to send an updated fcm tokens to the server, periodicity is 2 hours.
        if (!timer!!.alarmIsSet(Timer.sendCurrentFCMTokenIntent)) {
            sendFcmToken()
        }

        // this is a repeating alarm that ensures the service is running, it starts the service if it isn't.
        // Periodicity is FOREGROUND_SERVICE_RESTART_PERIODICITY.
        // This is a special intent, it has a  construction that targets the MainService's onStartCommand method.
        val alarmService = applicationContext.getSystemService(ALARM_SERVICE) as AlarmManager
        val restartServiceIntent = Intent(applicationContext, MainService::class.java)
        restartServiceIntent.setPackage(packageName)
        val flags = pending_intent_flag_fix(PendingIntent.FLAG_UPDATE_CURRENT)
        // no clue what this requestcode means, it is 0 on normal pending intents
        val repeatingRestartServicePendingIntent = PendingIntent.getService(
                applicationContext, 1, restartServiceIntent, flags
        )
        alarmService.setRepeating(
                AlarmManager.RTC_WAKEUP,
                System.currentTimeMillis() + FOREGROUND_SERVICE_RESTART_PERIODICITY,
                FOREGROUND_SERVICE_RESTART_PERIODICITY.toLong(),
                repeatingRestartServicePendingIntent
        )
    }

    /**The timerReceiver is an Android BroadcastReceiver that listens for our timer events to trigger,
     * and then runs the appropriate code for that trigger.
     * Note: every condition has a return statement at the end; this is because the trigger survey notification
     * action requires a fairly expensive dive into PersistantData JSON unpacking. */
    private val timerReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(appContext: Context, intent: Intent) {
            Log.e("BackgroundService", "Received broadcast: $intent")
            // Don't change this log to have a "correct" comma.
            TextFileManager.getDebugLogFile().writeEncrypted(
                    System.currentTimeMillis().toString() + " Received Broadcast: " + intent.toString())

            val broadcastAction = intent.action

            /* For GPS and Accelerometer the failure modes are:
			 * 1. If a recording event is triggered and followed by Doze being enabled then Beiwe will record until the Doze period ends.
			 * 2. If, after Doze ends, the timers trigger out of order Beiwe ceases to record and triggers a new recording event in the future. */

            // Disable active sensors commands
            if (broadcastAction == appContext.getString(R.string.turn_accelerometer_off)) {
                accelerometerListener!!.turn_off()
                return
            }
            if (broadcastAction == appContext.getString(R.string.turn_gyroscope_off)) {
                gyroscopeListener!!.turn_off()
                return
            }
            if (broadcastAction == appContext.getString(R.string.turn_gps_off)) {
                if (checkGpsPermissions(appContext)) {
                    gpsListener!!.turn_off()
                }
                return
            }
            if (broadcastAction == appContext.getString(R.string.turn_ambient_audio_off)) {
                AmbientAudioListener.encryptAmbientAudioFile()
                return
            }

            // send the current fcm token to the server.
            if (broadcastAction == appContext.getString(R.string.fcm_upload)) {
                sendFcmToken()
                return
            }

            // Enable active sensors, reset timers.
            //Accelerometer. We automatically have permissions required for accelerometer.
            if (broadcastAction == appContext.getString(R.string.turn_accelerometer_on)) {
                if (!accelerometerListener!!.exists) {
                    return
                }
                accelerometerListener!!.turn_on()
                //start both the sensor-off-action timer, and the next sensor-on-timer.
                timer!!.setupExactSingleAlarm(PersistentData.getAccelerometerOnDuration(), Timer.accelerometerOffIntent)
                val alarmTime = timer!!.setupExactSingleAlarm(
                        PersistentData.getAccelerometerOffDuration() + PersistentData.getAccelerometerOnDuration(),
                        Timer.accelerometerOnIntent
                )
                //record the system time that the next alarm is supposed to go off at, so that we can recover in the event of a reboot or crash. 
                PersistentData.setMostRecentAlarmTime(getString(R.string.turn_accelerometer_on), alarmTime)
                return
            }

            //Gyroscope. Almost identical logic to accelerometer above.
            if (broadcastAction == appContext.getString(R.string.turn_gyroscope_on)) {
                if (!gyroscopeListener!!.exists) {
                    return
                }
                gyroscopeListener!!.turn_on()
                //start both the sensor-off-action timer, and the next sensor-on-timer.
                timer!!.setupExactSingleAlarm(PersistentData.getGyroscopeOnDuration(), Timer.gyroscopeOffIntent)
                val alarmTime = timer!!.setupExactSingleAlarm(
                        PersistentData.getGyroscopeOffDuration() + PersistentData.getGyroscopeOnDuration(),
                        Timer.gyroscopeOnIntent
                )
                //record the system time that the next alarm is supposed to go off at, so that we can recover in the event of a reboot or crash.
                PersistentData.setMostRecentAlarmTime(getString(R.string.turn_gyroscope_on), alarmTime)
                return
            }

            //GPS. Almost identical logic to accelerometer above.
            if (broadcastAction == appContext.getString(R.string.turn_gps_on)) {
                gpsListener!!.turn_on()
                timer!!.setupExactSingleAlarm(PersistentData.getGpsOnDuration(), Timer.gpsOffIntent)
                val alarmTime = timer!!.setupExactSingleAlarm(
                        PersistentData.getGpsOnDuration() + PersistentData.getGpsOffDuration(),
                        Timer.gpsOnIntent
                )
                PersistentData.setMostRecentAlarmTime(getString(R.string.turn_gps_on), alarmTime)
                return
            }
            //run a wifi scan.  Most similar to GPS, but without an off-timer.
            if (broadcastAction == appContext.getString(R.string.run_wifi_log)) {
                if (checkWifiPermissions(appContext)) {
                    WifiListener.scanWifi()
                } else {
                    TextFileManager.getDebugLogFile().writeEncrypted(
                            System.currentTimeMillis().toString() + " user has not provided permission for Wifi.")
                }
                val alarmTime = timer!!.setupExactSingleAlarm(PersistentData.getWifiLogFrequency(), Timer.wifiLogIntent)
                PersistentData.setMostRecentAlarmTime(getString(R.string.run_wifi_log), alarmTime)
                return
            }

            /* Bluetooth timers are unlike GPS and Accelerometer because it uses an absolute-point-in-time
             * as a trigger, and therefore we don't need to store most-recent-timer state.
			 * The Bluetooth-on action sets the corresponding Bluetooth-off timer, the
			 * Bluetooth-off action sets the next Bluetooth-on timer.*/if (broadcastAction == appContext.getString(R.string.turn_bluetooth_on)) {
            if (!PersistentData.getBluetoothEnabled()) {
                    Log.e("BackgroundService", "invalid Bluetooth on received")
                    return
                }
                if (checkBluetoothPermissions(appContext)) {
                    if (bluetoothListener != null) bluetoothListener!!.enableBLEScan()
                } else {
                    TextFileManager.getDebugLogFile().writeEncrypted(
                            System.currentTimeMillis().toString() + " user has not provided permission for Bluetooth.")
                }
                timer!!.setupExactSingleAlarm(PersistentData.getBluetoothOnDuration(), Timer.bluetoothOffIntent)
                return
            }

            if (broadcastAction == appContext.getString(R.string.turn_bluetooth_off)) {
                if (checkBluetoothPermissions(appContext) && bluetoothListener != null)
                    bluetoothListener!!.disableBLEScan()
                timer!!.setupExactSingleAbsoluteTimeAlarm(
                        PersistentData.getBluetoothTotalDuration(),
                        PersistentData.getBluetoothGlobalOffset(),
                        Timer.bluetoothOnIntent
                )
                return
            }

            //starts a data upload attempt.
            if (broadcastAction == appContext.getString(R.string.upload_data_files_intent)) {
                PostRequest.uploadAllFiles()
                timer!!.setupExactSingleAlarm(PersistentData.getUploadDataFilesFrequency(), Timer.uploadDatafilesIntent)
                return
            }

            //creates new data files
            if (broadcastAction == appContext.getString(R.string.create_new_data_files_intent)) {
                TextFileManager.makeNewFilesForEverything()
                timer!!.setupExactSingleAlarm(PersistentData.getCreateNewDataFilesFrequency(), Timer.createNewDataFilesIntent)
                PostRequest.uploadAllFiles()
                return
            }

            //Downloads the most recent survey questions and schedules the surveys.
            if (broadcastAction == appContext.getString(R.string.check_for_new_surveys_intent)) {
                SurveyDownloader.downloadSurveys(applicationContext, null)
                timer!!.setupExactSingleAlarm(PersistentData.getCheckForNewSurveysFrequency(), Timer.checkForNewSurveysIntent)
                return
            }

            // Signs out the user. (does not set up a timer, that is handled in activity and sign-in logic) 
            if (broadcastAction == appContext.getString(R.string.signout_intent)) {
                PersistentData.logout()
                val loginPage = Intent(appContext, LoginActivity::class.java)
                loginPage.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                appContext.startActivity(loginPage)
                return
            }

            // logic for the SMS/MMS message logger
            if (broadcastAction == appContext.getString(R.string.check_for_sms_enabled)) {
                if (confirmTexts(appContext)) {
                    startSmsSentLogger()
                    startMmsSentLogger()
                } else if (PersistentData.getTextsEnabled())
                    timer!!.setupExactSingleAlarm(30000L, Timer.checkForSMSEnabledIntent)
            }

            // logic for the call (metadata) logger
            if (broadcastAction == appContext.getString(R.string.check_for_calls_enabled)) {
                if (confirmCalls(appContext)) {
                    startCallLogger()
                } else if (PersistentData.getCallsEnabled())
                    timer!!.setupExactSingleAlarm(30000L, Timer.checkForCallsEnabledIntent)
            }

            // ambient audio logic
            if (broadcastAction == appContext.getString(R.string.turn_ambient_audio_on)) {
                AmbientAudioListener.startRecording(appContext)
                timer!!.setupExactSingleAlarm(PersistentData.getAmbientAudioOnDuration(), Timer.ambientAudioOffIntent)
                val alarmTime = timer!!.setupExactSingleAlarm(PersistentData.getAmbientAudioOffDuration() + PersistentData.getAmbientAudioOnDuration(), Timer.ambientAudioOnIntent)
                PersistentData.setMostRecentAlarmTime(getString(R.string.turn_ambient_audio_on), alarmTime)
                return
            }

            //checks if the action is the id of a survey (expensive), if so pop up the notification for that survey, schedule the next alarm
            if (PersistentData.getSurveyIds().contains(broadcastAction)) {
//				Log.i("MAIN SERVICE", "new notification: " + broadcastAction);
                displaySurveyNotification(appContext, broadcastAction!!)
                SurveyScheduler.scheduleSurvey(broadcastAction)
                return
            }

            // logic for data upload (note only runs after registration)
            if (PersistentData.getIsRegistered() && broadcastAction == ConnectivityManager.CONNECTIVITY_ACTION) {
                val networkInfo = intent.getParcelableExtra<NetworkInfo>(ConnectivityManager.EXTRA_NETWORK_INFO)
                if (networkInfo!!.type == ConnectivityManager.TYPE_WIFI) {
                    PostRequest.uploadAllFiles()
                    return
                }
            }

            //these are  a special actions that will only run if the app device is in debug mode.
            if (broadcastAction == "crashBeiwe" && BuildConfig.APP_IS_BETA) {
                throw NullPointerException("beeeeeoooop.")
            }
            if (broadcastAction == "enterANR" && BuildConfig.APP_IS_BETA) {
                try {
                    Thread.sleep(100000)
                } catch (ie: InterruptedException) {
                    ie.printStackTrace()
                }
            }
        }
    }

    /*##########################################################################################
	############## code related to onStartCommand and binding to an activity ###################
	##########################################################################################*/

    override fun onBind(arg0: Intent): IBinder? {
        return BackgroundServiceBinder()
    }

    /**A public "Binder" class for Activities to access.
     * Provides a (safe) handle to the Main Service using the onStartCommand code
     * used in every RunningBackgroundServiceActivity  */
    inner class BackgroundServiceBinder : Binder() {
        val service: MainService
            get() = this@MainService
    }

    /*##############################################################################
	########################## Android Service Lifecycle ###########################
	##############################################################################*/

    /** The BackgroundService is meant to be all the time, so we return START_STICKY  */
    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        //Log.d("BackgroundService onStartCommand", "started with flag " + flags );
        TextFileManager.getDebugLogFile().writeEncrypted(
                System.currentTimeMillis().toString() + " started with flag " + flags)
        val now = System.currentTimeMillis()
        val millisecondsSincePrevious = now - foregroundServiceLastStarted

        // if it has been FOREGROUND_SERVICE_TIMER or longer since the last time we started the
        // foreground service notification, start it again.
        if (foregroundServiceLastStarted == 0L || millisecondsSincePrevious > FOREGROUND_SERVICE_TIMER) {
            val intent_to_start_foreground_service = Intent(applicationContext, MainService::class.java)
            val intent_flags = pending_intent_flag_fix(PendingIntent.FLAG_UPDATE_CURRENT) // no flags
            val onStartCommandPendingIntent = PendingIntent.getService(
                    applicationContext, 0, intent_to_start_foreground_service, intent_flags
            )
            val notification = Notification.Builder(applicationContext, NOTIFICATION_CHANNEL_ID)
                    .setContentTitle("Beiwe App")
                    .setContentText("Beiwe data collection running")
                    .setSmallIcon(R.mipmap.ic_launcher)
                    .setContentIntent(onStartCommandPendingIntent)
                    .setTicker("Beiwe data collection running in the background, no action required")
                    .build()

            // multiple sources recommend an ID of 1 because it works. documentation is very spotty about this
            startForeground(1, notification)
            foregroundServiceLastStarted = now
        }

        // We want this service to continue running until it is explicitly stopped, so return sticky.
        return START_STICKY
        // in testing out this restarting behavior for the service it is entirely unclear if changing
        // this return will have any observable effect despite the documentation's claim that it does.
        //return START_REDELIVER_INTENT;
    }

    // the rest of these are ~identical
    override fun onTaskRemoved(rootIntent: Intent) {
        //Log.d("BackroundService onTaskRemoved", "onTaskRemoved called with intent: " + rootIntent.toString() );
        TextFileManager.getDebugLogFile().writeEncrypted(
                System.currentTimeMillis().toString() + " " + "onTaskRemoved called with intent: " + rootIntent.toString())
        restartService()
    }

    override fun onUnbind(intent: Intent): Boolean {
        //Log.d("BackroundService onUnbind", "onUnbind called with intent: " + intent.toString() );
        TextFileManager.getDebugLogFile().writeEncrypted(
                System.currentTimeMillis().toString() + " " + "onUnbind called with intent: " + intent.toString())
        restartService()
        return super.onUnbind(intent)
    }

    override fun onDestroy() { //Log.w("BackgroundService", "BackgroundService was destroyed.");
        //note: this does not run when the service is killed in a task manager, OR when the stopService() function is called from debugActivity.
        TextFileManager.getDebugLogFile().writeEncrypted(
                System.currentTimeMillis().toString() + " " + "BackgroundService was destroyed.")
        restartService()
        super.onDestroy()
    }

    override fun onLowMemory() { //Log.w("BackroundService onLowMemory", "Low memory conditions encountered");
        TextFileManager.getDebugLogFile().writeEncrypted(
                System.currentTimeMillis().toString() + " " + "onLowMemory called.")
        restartService()
    }

    /** Sets a timer that starts the service if it is not running in ten seconds.  */
    private fun restartService() {
        val alarmService = applicationContext.getSystemService(ALARM_SERVICE) as AlarmManager
        //how does this even...  Whatever, a half second later the main service will start.
        val restartServiceIntent = Intent(applicationContext, this.javaClass)
        restartServiceIntent.setPackage(packageName)
        val restartServicePendingIntent = PendingIntent.getService(
                applicationContext,
                1,
                restartServiceIntent,
                pending_intent_flag_fix(PendingIntent.FLAG_ONE_SHOT)
        )
        // careful, kotlin refactor turnedd this into a very weird setter syntax using [] access...
        alarmService.set(
                AlarmManager.ELAPSED_REALTIME,
                SystemClock.elapsedRealtime() + 500,
                restartServicePendingIntent
        )
    }

    companion object {
        // I guess we need access to this one in static contexts...
        var timer: Timer? = null

        // localHandle is how static scopes access the currently instantiated main service.
        // It is to be used ONLY to register new surveys with the running main service, because
        // that code needs to be able to update the IntentFilters associated with timerReceiver.
        // This is Really Hacky and terrible style, but it is okay because the scheduling code can only ever
        // begin to run with an already fully instantiated main service.
        var localHandle: MainService? = null

        private var foregroundServiceLastStarted = 0L

        // notification channel constants (to be moved elsewhere)
        const val NOTIFICATION_CHANNEL_ID = "_service_channel"
        const val NOTIFICATION_CHANNEL_NAME = "Beiwe Data Collection" // user facing name, seen if they hold press the notification

        // timer constants (to be moved elsewhere)
        const val FCM_TIMER = 1000 * 60 * 120 // 2 hours between sending fcm updates
        const val FOREGROUND_SERVICE_TIMER = 1000 * 60 * 60 * 6 // 6 hours for a foreground service notification
        const val FOREGROUND_SERVICE_RESTART_PERIODICITY = 1000 * 60 * 2 // but try every 2 minutes because we are very greedy about this.

        //FIXME: in order to make this non-static we probably needd to make postreqeuest kotlin,
        //  so that we have access to useful optional tools
        /** create timers that will trigger events throughout the program, and
         * register the custom Intents with the controlMessageReceiver.  */
        @JvmStatic
        fun registerTimers(appContext: Context) {
            timer = Timer(localHandle!!)
            val filter = IntentFilter()
            filter.addAction(appContext.getString(R.string.turn_accelerometer_off))
            filter.addAction(appContext.getString(R.string.turn_accelerometer_on))
            filter.addAction(appContext.getString(R.string.turn_ambient_audio_off))
            filter.addAction(appContext.getString(R.string.turn_ambient_audio_on))
            filter.addAction(appContext.getString(R.string.turn_gyroscope_on))
            filter.addAction(appContext.getString(R.string.turn_gyroscope_off))
            filter.addAction(appContext.getString(R.string.turn_bluetooth_on))
            filter.addAction(appContext.getString(R.string.turn_bluetooth_off))
            filter.addAction(appContext.getString(R.string.turn_gps_on))
            filter.addAction(appContext.getString(R.string.turn_gps_off))
            filter.addAction(appContext.getString(R.string.signout_intent))
            filter.addAction(appContext.getString(R.string.voice_recording))
            filter.addAction(appContext.getString(R.string.run_wifi_log))
            filter.addAction(appContext.getString(R.string.upload_data_files_intent))
            filter.addAction(appContext.getString(R.string.create_new_data_files_intent))
            filter.addAction(appContext.getString(R.string.check_for_new_surveys_intent))
            filter.addAction(appContext.getString(R.string.check_for_sms_enabled))
            filter.addAction(appContext.getString(R.string.check_for_calls_enabled))
            filter.addAction(appContext.getString(R.string.check_if_ambient_audio_recording_is_enabled))
            filter.addAction(appContext.getString(R.string.fcm_upload))
            filter.addAction(ConnectivityManager.CONNECTIVITY_ACTION)
            filter.addAction("crashBeiwe")
            filter.addAction("enterANR")

            for (surveyId in PersistentData.getSurveyIds()) {
                filter.addAction(surveyId)
            }
            appContext.registerReceiver(localHandle!!.timerReceiver, filter)
        }

        /**Refreshes the logout timer.
         * This function has a THEORETICAL race condition, where the BackgroundService is not fully instantiated by a session activity,
         * in this case we log an error to the debug log, print the error, and then wait for it to crash.  In testing on a (much) older
         * version of the app we would occasionally see the error message, but we have never (august 10 2015) actually seen the app crash
         * inside this code.  */
        fun startAutomaticLogoutCountdownTimer() {
            if (timer == null) {
                Log.e("bacgroundService", "timer is null, BackgroundService may be about to crash, the Timer was null when the BackgroundService was supposed to be fully instantiated.")
                TextFileManager.getDebugLogFile().writeEncrypted("our not-quite-race-condition encountered, Timer was null when the BackgroundService was supposed to be fully instantiated")
            }
            timer!!.setupExactSingleAlarm(PersistentData.getTimeBeforeAutoLogout(), Timer.signoutIntent)
            PersistentData.loginOrRefreshLogin()
        }

        /** cancels the signout timer  */
        fun clearAutomaticLogoutCountdownTimer() {
            timer!!.cancelAlarm(Timer.signoutIntent)
        }

        /** The Timer requires the BackgroundService in order to create alarms, hook into that functionality here.  */
        @JvmStatic
        fun setSurveyAlarm(surveyId: String?, alarmTime: Calendar?) {
            timer!!.startSurveyAlarm(surveyId!!, alarmTime!!)
        }

        @JvmStatic
        fun cancelSurveyAlarm(surveyId: String?) {
            timer!!.cancelAlarm(Intent(surveyId))
        }
    }
}