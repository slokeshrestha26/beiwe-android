@file:Suppress("LocalVariableName")

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
import org.beiwe.app.networking.NetworkUtility
import org.beiwe.app.networking.PostRequest
import org.beiwe.app.networking.SurveyDownloader
import org.beiwe.app.storage.PersistentData
import org.beiwe.app.storage.TextFileManager
import org.beiwe.app.survey.SurveyScheduler
import org.beiwe.app.ui.user.LoginActivity
import org.beiwe.app.ui.utils.SurveyNotifications.displaySurveyNotification
import java.util.*


// notification channel constants (to be moved elsewhere)
const val NOTIFICATION_CHANNEL_ID = "_service_channel"
const val NOTIFICATION_CHANNEL_NAME = "Beiwe Data Collection" // user facing name, seen if they hold press the notification

// timer constants all internal values require milliseconds
const val FCM_TIMER = 1000L * 60 * 120 // 2 hours between sending fcm updates
const val FOREGROUND_SERVICE_TIMER = 1000L * 60 * 60 * 6 // 6 hours for a foreground service notification
const val FOREGROUND_SERVICE_RESTART_PERIODICITY = 1000L * 30 // but try every 2 minutes because we are very greedy about this.

const val BLLUETOOTH_MESSAGE_1 = "bluetooth Failure, device should not have gotten to this line of code"
const val BLLUETOOTH_MESSAGE_2 = "Device does not support bluetooth LE, bluetooth features disabled."


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

    /*#############################################################################
	#########################         Starters              #######################
	#############################################################################*/

    /** Initializes the Bluetooth listener
     * Bluetooth has several checks to make sure that it actually exists on the device with the
     * capabilities we need. Checking for Bluetooth LE is necessary because it is an optional
     * extension to Bluetooth 4.0. */
    fun startBluetooth() {
        // Note: the Bluetooth listener is a BroadcastReceiver, which means it must have a 0-argument
        // constructor in order for android to instantiate it on broadcast receipts. The following
        // check must be made, but it requires a Context that we cannot pass into the
        // BluetoothListener, so we do the check in the BackgroundService.
        if (applicationContext.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)
                && PersistentData.getBluetoothEnabled()) {
            bluetoothListener = BluetoothListener()
            if (bluetoothListener!!.isBluetoothEnabled) {
                val intent_filter = IntentFilter("android.bluetooth.adapter.action.STATE_CHANGED")
                registerReceiver(bluetoothListener, intent_filter)
            } else {
                // TODO: Track down why this error occurs, cleanup.  The above check should be for
                //  the (new) doesBluetoothCapabilityExist function instead of isBluetoothEnabled
                Log.e("Main Service", BLLUETOOTH_MESSAGE_1)
                TextFileManager.getDebugLogFile().writeEncrypted(BLLUETOOTH_MESSAGE_1)
            }
        } else {
            if (PersistentData.getBluetoothEnabled()) {
                TextFileManager.writeDebugLogStatement(BLLUETOOTH_MESSAGE_2)
                Log.w("MainS bluetooth init", BLLUETOOTH_MESSAGE_2)
            }
            bluetoothListener = null
        }
    }

    /** Initializes the sms logger.  */
    fun startSmsSentLogger() {
        val smsSentLogger = SmsSentLogger(Handler(), appContext)
        this.contentResolver.registerContentObserver(
                Uri.parse("content://sms/"), true, smsSentLogger)
    }

    fun startMmsSentLogger() {
        val mmsMonitor = MMSSentLogger(Handler(), appContext)
        this.contentResolver.registerContentObserver(
                Uri.parse("content://mms/"), true, mmsMonitor)
    }

    /** Initializes the call logger.  */
    private fun startCallLogger() {
        val callLogger = CallLogger(Handler(), appContext)
        this.contentResolver.registerContentObserver(
                Uri.parse("content://call_log/calls/"), true, callLogger)
    }

    /** Initializes the PowerStateListener.
     *  The PowerStateListener requires the ACTION_SCREEN_OFF and ACTION_SCREEN_ON intents be
     *  registered programatically. They do not work if registered in the app's manifest. Same for
     *  the ACTION_POWER_SAVE_MODE_CHANGED and ACTION_DEVICE_IDLE_MODE_CHANGED filters, though they
     *  are for monitoring deeper power state changes in 5.0 and 6.0, respectively.  */
    @SuppressLint("InlinedApi")
    private fun startPowerStateListener() {
        if (powerStateListener == null) {
            val filter = IntentFilter()
            filter.addAction(Intent.ACTION_SCREEN_ON)
            filter.addAction(Intent.ACTION_SCREEN_OFF)
            if (Build.VERSION.SDK_INT >= 21)
                filter.addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
            if (Build.VERSION.SDK_INT >= 23)
                filter.addAction(PowerManager.ACTION_DEVICE_IDLE_MODE_CHANGED)
            powerStateListener = PowerStateListener()
            registerReceiver(powerStateListener, filter)
            PowerStateListener.start(appContext)
        }
    }

    /** Gets, sets, and pushes the FCM token to the backend.  */
    fun initializeFireBaseIDToken() {
        val errorMessage = "Unable to get FCM token, will not be able to receive push notifications."

        // Set up the oncomplete listener for the FCM getter code, then wait until registered to
        // actually push it to the server or else the post request will error.

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
            val outerNotificationBlockerThread = Thread(Runnable {
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
            }, "outerNotificationBlockerThread")
            outerNotificationBlockerThread.start()
        })
    }

    /* Sends the FCM token to the backend. Automatically sets an alarm to run perioditaccally.
	   based on the FCM_TIMER variable. */
    // fun sendFcmToken() {
    //     val fcm_token = PersistentData.getFCMInstanceID()
    //     if (fcm_token != null)
    //         PostRequest.sendFCMInstanceID(fcm_token)
    //     timer!!.setupExactSingleAlarm(FCM_TIMER.toLong(), Timer.sendCurrentFCMTokenIntent)
    // }

    /*#############################################################################
	####################            Timer Logic             #######################
	#############################################################################*/

    fun startTimers() {
        Log.i("BackgroundService", "running startTimer logic.")
        val now = run_all_app_logic()

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
                FOREGROUND_SERVICE_RESTART_PERIODICITY,
                repeatingRestartServicePendingIntent
        )
    }

    /**The timerReceiver is an Android BroadcastReceiver that listens for our timer events to trigger,
     * and then runs the appropriate code for that trigger.
     * Note: every condition has a return statement at the end; this is because the trigger survey
     * notification action requires a fairly expensive dive into PersistantData JSON unpacking. */
    private val timerReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(appContext: Context, intent: Intent) {
            Log.e("BackgroundService", "Received broadcast: $intent")
            // Don't change this log to have a "correct" comma.
            TextFileManager.getDebugLogFile().writeEncrypted(
                    System.currentTimeMillis().toString() + " Received Broadcast: " + intent.toString())

            val broadcastAction = intent.action

            run_all_app_logic()

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

            // I don't know if we pull this one out
            // Signs out the user. (does not set up a timer, that is handled in activity and sign-in logic) 
            if (broadcastAction == appContext.getString(R.string.signout_intent)) {
                //FIXME: does this need to run on the main thread in do_signout_check?
                PersistentData.logout()
                val loginPage = Intent(appContext, LoginActivity::class.java) // yup that is still java
                loginPage.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                appContext.startActivity(loginPage)
                return
            }

            // leave the SMS/MMS/calls logic as-is, it is like this to ensure they are never
            // enabled until the particiant presses the accept button.
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

            // TODO: convert to state-based check logic
            // checks if the action is the id of a survey (expensive), if so pop up the notification
            // for that survey, schedule the next alarm.
            if (PersistentData.getSurveyIds().contains(broadcastAction)) {
				// Log.i("MAIN SERVICE", "new notification: " + broadcastAction);
                displaySurveyNotification(appContext, broadcastAction!!)
                SurveyScheduler.scheduleSurvey(broadcastAction)
                return
            }

            // these are special actions that will only run if the app device is in debug mode.
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

    /*##############################################################################
    ########################## Application State Logic #############################
    ##############################################################################*/

    /* Abstract function, checks the time, runs the action, sets the next time. */
    fun do_an_event_session_check(
            now: Long,
            identifier_string: String,
            periodicity_in_milliseconds: Long,
            do_action: () -> Unit,
    ) {
        val t1 = System.currentTimeMillis()
        val most_recent_event_time = PersistentData.getMostRecentAlarmTime(identifier_string)
        if (now - most_recent_event_time > periodicity_in_milliseconds) {
            printe("'$identifier_string' - time to trigger")
            do_action()  // TODO: stick this on a queue
            PersistentData.setMostRecentAlarmTime(identifier_string, System.currentTimeMillis())
            printv("'$identifier_string - trigger - ${System.currentTimeMillis() - t1}")
        } else {
            printi("'$identifier_string' - not yet time to trigger")
            printv("'$identifier_string - not trigger - ${System.currentTimeMillis() - t1}")
        }
    }

    /* abstracted timing logic for sessions that have a duration to their recording session */
    fun do_an_on_off_session_check(
            now: Long,
            is_running: Boolean,
            should_turn_off_at: Long,
            should_turn_on_again_at: Long,
            identifier_string: String,
            on_action: () -> Unit,
            off_action: () -> Unit
    ) {
        val t1 = System.currentTimeMillis()
        if (is_running && now <= should_turn_off_at) {
            printw("'$identifier_string' is running, not time to turn of")
            printv("'$identifier_string - is running - ${System.currentTimeMillis() - t1}")
            return
        }

        // running, should be off, off is in the past
        if (is_running && should_turn_off_at < now) {  // THIS LINE SHOULD HAVE AN IDE WARNING -- it disappeared?
            printi("'$identifier_string' time to turn off")
            off_action()  // e.g. accelerometerListener.turn_off()
            printv("'$identifier_string - turn off - ${System.currentTimeMillis() - t1}")
        }

        // not_running,  should turn on is still in the future, do nothing
        if (!is_running && should_turn_on_again_at >= now) {
            printw("'$identifier_string' correctly off")
            printv("'$identifier_string - correctly off - ${System.currentTimeMillis() - t1}")
            return
        }

        // not running, should be on, (on is in the past)
        if (!is_running && should_turn_on_again_at < now) {  // THIS LINE SHOULD HAVE AN IDE WARNING
            // always get the current time, the now value could be stale - unlikely but possible
            // we care that we get data, not that data be rigidly accurate to a clock.
            PersistentData.setMostRecentAlarmTime(identifier_string, System.currentTimeMillis())
            printe("'$identifier_string' turn it on!")
            on_action()  //TODO: stick this on a queue
            printv("'$identifier_string - on action - ${System.currentTimeMillis() - t1}")
        }
    }

    /* If there is something with app state logic that should be idempotently checked, stick it
     * here. Returns the value used for the current time.  @Synchronized because this is the core
     * logic loop.  Provided potentially-expensive operations, like upload logic, run on anynchronously
     * and with reasonably widely separated real-time values, */
    @Synchronized
    fun run_all_app_logic(): Long {
        val now = System.currentTimeMillis()

        // These are currently syncronous (block) unless they say otherwise, profiling was done
        // on a Pixel 6. No-action always measures 0-1ms.
        val t1 = System.currentTimeMillis()
        do_new_files_check(now)  // always 10s of ms (30-70ms)
        accelerometer_logic(now)
        gyro_logic(now)  // on action ~20-50ms, off action 10-20ms
        gps_logic(now)  // on acction <10-20ms, off action ~2ms (yes two)
        ambient_audio_logic(now)  // asynchronous when stopping because it has to encrypt
        do_fcm_upload_logic_check(now)  // asynchronous, runs network request on a thread.
        do_wifi_logic_check(now)  // on action <10-40ms
        do_upload_logic_check(now)  // asynchronous, runs network request on a threa, single digit ms.
        do_new_surveys_check(now)  // asynchronous, runs network request on a thread, single digit ms.
        do_survey_notifications_check(now)  // 1 survey notification <10-30ms.
        // highest total time was 159ms, but insufficient data points to be confident.
        printv("run_all_app_logic - ${System.currentTimeMillis() - t1}")
        return now
    }
    // TODO: if we make this use rtc time that will solve time-reset issues.  Could also run a sanity check.
    // TODO: test default (should be zero? out of PersistentData) cases.

    fun accelerometer_logic(now: Long) {
        // accelerometer may not exist, or be disabled for the study
        if (!PersistentData.getAccelerometerEnabled() || accelerometerListener!!.exists)
            return

        // assemble all the variables we need for on-off with duration
        val on_string = getString(R.string.turn_accelerometer_on)
        val most_recent_on = PersistentData.getMostRecentAlarmTime(on_string)
        val should_turn_off_at = most_recent_on + PersistentData.getAccelerometerOnDuration()
        val should_turn_on_again_at = should_turn_off_at + PersistentData.getAccelerometerOffDuration()
        do_an_on_off_session_check(
                now,
                accelerometerListener!!.running,
                should_turn_off_at,
                should_turn_on_again_at,
                on_string,
                accelerometerListener!!.accelerometer_on_action,
                accelerometerListener!!.accelerometer_off_action
        )
    }

    fun gyro_logic(now: Long) {
        // gyro may not exist, or be disabled for the study
        if (!PersistentData.getGyroscopeEnabled() || !gyroscopeListener!!.exists)
            return

        // assemble all the variables we need for on-off with duration
        val on_string = getString(R.string.turn_gyroscope_on)
        val most_recent_on = PersistentData.getMostRecentAlarmTime(on_string)
        val should_turn_off_at = most_recent_on + PersistentData.getGyroscopeOnDuration()
        val should_turn_on_again_at = should_turn_off_at + PersistentData.getGyroscopeOffDuration()
        do_an_on_off_session_check(
                now,
                gyroscopeListener!!.running,
                should_turn_off_at,
                should_turn_on_again_at,
                on_string,
                gyroscopeListener!!.gyro_on_action,
                gyroscopeListener!!.gyro_off_action
        )
    }

    fun gps_logic(now: Long) {
        // GPS (location service) always _exists to a degree_, but may not be enabled on a study
        if (!PersistentData.getGpsEnabled())
            return

        // assemble all the variables we need for on-off with duration
        val on_string = getString(R.string.turn_gps_on)
        val most_recent_on = PersistentData.getMostRecentAlarmTime(on_string)
        val should_turn_off_at = most_recent_on + PersistentData.getGpsOnDuration()
        val should_turn_on_again_at = should_turn_off_at + PersistentData.getGpsOffDuration()
        do_an_on_off_session_check(
                now,
                gpsListener!!.running,
                should_turn_off_at,
                should_turn_on_again_at,
                on_string,
                gpsListener!!.gps_on_action,
                gpsListener!!.gps_off_action
        )
    }

    fun ambient_audio_logic(now: Long) {
        //  there is a Lot of safety around enabling/disabling, and it looks like permissions might
        //  not be implemented at all but it .. works??
        //FIXME: permissions don't seem to be implemented for microphone access?
        if (!PersistentData.getAmbientAudioEnabled())
            return

        val on_string = getString(R.string.turn_ambient_audio_on)
        val most_recent_on = PersistentData.getMostRecentAlarmTime(on_string)
        val should_turn_off_at = most_recent_on + PersistentData.getAmbientAudioOnDuration()
        val should_turn_on_again_at = should_turn_off_at + PersistentData.getAmbientAudioOffDuration()
        // ambiant audio needs the app context at runtime (we write very consistent code)
        val ambient_audio_on = {
            AmbientAudioListener.startRecording(applicationContext)
        }
        do_an_on_off_session_check(
                now,
                AmbientAudioListener.isCurrentlyRunning,
                should_turn_off_at,
                should_turn_on_again_at,
                on_string,
                ambient_audio_on,
                AmbientAudioListener.ambient_audio_off_action
        )
    }

    fun do_wifi_logic_check(now: Long) {
        // wifi has permissions and may be disabled on the study
        if (!PersistentData.getWifiEnabled())
            return
        if (!checkWifiPermissions(applicationContext)) {
            TextFileManager.writeDebugLogStatement("user has not provided permission for Wifi.")
            return
        }
        val event_string = getString(R.string.run_wifi_log)
        val event_frequency = PersistentData.getWifiLogFrequency()
        val wifi_do_action = {  // wifi will need some attention to convert to kotlin...
            WifiListener.scanWifi()
        }
        do_an_event_session_check(now, event_string, event_frequency, wifi_do_action)
    }

    fun do_upload_logic_check(now: Long) {
        // upload waits until registration  --  probably remove this
        if (!PersistentData.getIsRegistered())
            return

        val upload_string = applicationContext.getString(R.string.upload_data_files_intent)
        val periodicity = PersistentData.getUploadDataFilesFrequency()
        val do_uploads_action = {
            PostRequest.uploadAllFiles()
        }
        do_an_event_session_check(now, upload_string, periodicity, do_uploads_action)
    }

    fun do_fcm_upload_logic_check(now: Long) {
        // only run after registration
        if (!PersistentData.getIsRegistered())
            return
        // we can just literally hardcode this one, sendFcmToken is this plus a timer
        val event_string = applicationContext.getString(R.string.fcm_upload)
        val send_fcm_action = {
            val fcm_token = PersistentData.getFCMInstanceID()
            if (fcm_token != null)
                PostRequest.sendFCMInstanceID(fcm_token)
        }
        do_an_event_session_check(now, event_string, FCM_TIMER, send_fcm_action)
    }

    fun do_new_files_check(now: Long) {
        // only run after registration
        if (!PersistentData.getIsRegistered())
            return
        val event_string = getString(R.string.create_new_data_files_intent)
        val periodicity = PersistentData.getCreateNewDataFilesFrequency()
        val new_files_action = {
            TextFileManager.makeNewFilesForEverything()
        }
        do_an_event_session_check(now, event_string, periodicity, new_files_action)
    }

    fun do_new_surveys_check(now: Long) {
        // only run after registration
        if (!PersistentData.getIsRegistered())
            return
        val event_string = getString(R.string.check_for_new_surveys_intent)
        val periodicity = PersistentData.getCheckForNewSurveysFrequency()
        val dowwnload_surveys_action = {
            SurveyDownloader.downloadSurveys(applicationContext, null)
        }
        do_an_event_session_check(now, event_string, periodicity, dowwnload_surveys_action)
    }

    fun do_survey_notifications_check(now: Long) {
        // checks for the current expected state for survey notifications, and the app state for
        // scheduled alarms.
        val t1 = System.currentTimeMillis()
        var counter = 0
        printe("survey notification check!")
        for (surveyId in PersistentData.getSurveyIds()) {
            var app_state_says_on = PersistentData.getSurveyNotificationState(surveyId)
            var alarm_in_past = PersistentData.getMostRecentSurveyAlarmTime(surveyId) < now

            // we don't currently have logic to determine if a notification is actually visible,
            // the behavior is that it ... replaces all the notifications.  we can make this better.
            if (app_state_says_on || alarm_in_past) {
                // this calls PersistentData.setSurveyNotificationState
                displaySurveyNotification(appContext!!, surveyId)
                counter++
            }

            //TODO: fix this naming mismatch.
            // Never call this:
            //   timer!!.cancelAlarm(Intent(surveyId))  // BAD!
            // setMostRecentSurveyAlarmTime is called in Timer.setupSurveyAlarm (when the alarm
            // is set in the scheduler logic), e.g. the application state is updated to say that
            // the _next_ survey alarm time is at X o'clock - there is a naming mismatch.

            // if there is no alarm set for this survey, set it.
            if (!timer!!.alarmIsSet(Intent(surveyId)))
                SurveyScheduler.scheduleSurvey(surveyId)
        }
        printv("$counter survey notifications took ${System.currentTimeMillis() - t1} ms")
    }

    // fun do_signout_check(now: Long) {
    //     // only run after registration
    //     if (!PersistentData.getIsRegistered())
    //         return
    //     val event_string = getString(R.string.signout_intent)
    //     val periodicity = PersistentData.getCheckForNewSurveysFrequency()
    //
    //     val signout_check = {
    //         PersistentData.logout()
    //         // FIXME: this operation may crash if it is not on the main thread, need to test it
    //         val loginPage = Intent(appContext, LoginActivity::class.java)  // yup that is still java
    //         loginPage.flags = Intent.FLAG_ACTIVITY_NEW_TASK
    //         startActivity(loginPage)
    //     }
    //     // nnnnoope this one uses a per-activity mechanism.... hmm
    //     // do_an_event_session_check(now, event_string, periodicity, dowwnload_surveys_action)
    // }

    /*##########################################################################################
	############## code related to onStartCommand and binding to an activity ###################
	##########################################################################################*/

    override fun onBind(arg0: Intent): IBinder? {
        return BackgroundServiceBinder()
    }

    /**A public "Binder" class for Activities to access.
     *  Provides a (safe) handle to the Main Service using the onStartCommand code used in every
     *  RunningBackgroundServiceActivity  */
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

        // onStartCommand is called every 30 seconds due to repeating high-priority-or-whatever
        // alarms, so we will stick a core logic check here.
        run_all_app_logic()

        // We want this service to continue running until it is explicitly stopped, so return sticky.
        return START_STICKY
        // in testing out this restarting behavior for the service it is entirely unclear if changing
        // this return will have any observable effect despite the documentation's claim that it does.
        //return START_REDELIVER_INTENT;
    }

    // the rest of these are ~identical
    override fun onTaskRemoved(rootIntent: Intent) {
        //Log.d("BackroundService onTaskRemoved", "onTaskRemoved called with intent: " + rootIntent.toString() );
        TextFileManager.writeDebugLogStatement("onTaskRemoved called with intent: $rootIntent")
        restartService()
    }

    override fun onUnbind(intent: Intent): Boolean {
        //Log.d("BackroundService onUnbind", "onUnbind called with intent: " + intent.toString() );
        TextFileManager.writeDebugLogStatement("onUnbind called with intent: $intent")
        restartService()
        return super.onUnbind(intent)
    }

    override fun onDestroy() { //Log.w("BackgroundService", "BackgroundService was destroyed.");
        //note: this does not run when the service is killed in a task manager, OR when the stopService() function is called from debugActivity.
        TextFileManager.writeDebugLogStatement("BackgroundService was destroyed.")
        restartService()
        super.onDestroy()
    }

    override fun onLowMemory() { //Log.w("BackroundService onLowMemory", "Low memory conditions encountered");
        TextFileManager.writeDebugLogStatement("onLowMemory called.")
        restartService()
    }

    /** Stops the BackgroundService instance, development tool  */
    fun stop() {
        if (BuildConfig.APP_IS_BETA)
            this.stopSelf()
    }

    /** Sets a timer that starts the service if it is not running after a half second.  */
    private fun restartService() {
        val restartServiceIntent = Intent(applicationContext, this.javaClass)
        restartServiceIntent.setPackage(packageName)
        val restartServicePendingIntent = PendingIntent.getService(
                applicationContext,
                1,
                restartServiceIntent,
                pending_intent_flag_fix(PendingIntent.FLAG_ONE_SHOT)
        )
        //  kotlin port action turned this into a very weird setter syntax using [] access...
        (applicationContext.getSystemService(ALARM_SERVICE) as AlarmManager).set(
                AlarmManager.ELAPSED_REALTIME,
                SystemClock.elapsedRealtime() + 500,
                restartServicePendingIntent
        )
    }

    // static assets
    companion object {
        // I guess we need access to this one in static contexts...
        public var timer: Timer? = null

        // localHandle is how static scopes access the currently instantiated main service.
        // It is to be used ONLY to register new surveys with the running main service, because
        // that code needs to be able to update the IntentFilters associated with timerReceiver.
        // This is Really Hacky and terrible style, but it is okay because the scheduling code can only ever
        // begin to run with an already fully instantiated main service.
        var localHandle: MainService? = null

        private var foregroundServiceLastStarted = 0L

        //FIXME: in order to make this non-static we probably need to port PostReqeuest to kotlin
        /** create timers that will trigger events throughout the program, and
         * register the custom Intents with the controlMessageReceiver.  */
        @JvmStatic
        fun registerTimers(appContext: Context) {
            timer = Timer(localHandle!!)
            val filter = IntentFilter()
            // filter.addAction(appContext.getString(R.string.turn_accelerometer_off))
            // filter.addAction(appContext.getString(R.string.turn_accelerometer_on))
            // filter.addAction(appContext.getString(R.string.turn_ambient_audio_off))
            // filter.addAction(appContext.getString(R.string.turn_ambient_audio_on))
            // filter.addAction(appContext.getString(R.string.turn_gyroscope_on))
            // filter.addAction(appContext.getString(R.string.turn_gyroscope_off))
            filter.addAction(appContext.getString(R.string.turn_bluetooth_on))
            filter.addAction(appContext.getString(R.string.turn_bluetooth_off))
            // filter.addAction(appContext.getString(R.string.turn_gps_on))
            // filter.addAction(appContext.getString(R.string.turn_gps_off))
            filter.addAction(appContext.getString(R.string.signout_intent))
            filter.addAction(appContext.getString(R.string.voice_recording))
            // filter.addAction(appContext.getString(R.string.run_wifi_log))
            // filter.addAction(appContext.getString(R.string.upload_data_files_intent))
            // filter.addAction(appContext.getString(R.string.create_new_data_files_intent))
            filter.addAction(appContext.getString(R.string.check_for_new_surveys_intent))
            filter.addAction(appContext.getString(R.string.check_for_sms_enabled))
            filter.addAction(appContext.getString(R.string.check_for_calls_enabled))
            // filter.addAction(appContext.getString(R.string.check_if_ambient_audio_recording_is_enabled))
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