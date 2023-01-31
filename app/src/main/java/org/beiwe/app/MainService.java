package org.beiwe.app;

import static org.beiwe.app.UtilsKt.*;

import android.annotation.SuppressLint;
import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.SystemClock;
import android.util.Log;

import androidx.annotation.NonNull;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.iid.FirebaseInstanceId;
import com.google.firebase.iid.InstanceIdResult;

import org.beiwe.app.listeners.*;
import org.beiwe.app.networking.PostRequest;
import org.beiwe.app.networking.SurveyDownloader;
import org.beiwe.app.storage.PersistentData;
import org.beiwe.app.storage.TextFileManager;
import org.beiwe.app.survey.SurveyScheduler;
import org.beiwe.app.ui.user.LoginActivity;
import org.beiwe.app.ui.utils.SurveyNotifications;

import java.util.Calendar;
import java.util.List;

import io.sentry.Sentry;
import io.sentry.android.AndroidSentryClientFactory;
import io.sentry.dsn.InvalidDsnException;

import static java.lang.Thread.sleep;

public class MainService extends Service {
	private Context appContext;
	public GPSListener gpsListener;
	public PowerStateListener powerStateListener;
	public AccelerometerListener accelerometerListener;
	public GyroscopeListener gyroscopeListener;
	public BluetoothListener bluetoothListener;
	public String notificationChannelId = "_service_channel";
	String channelName = "Beiwe Data Collection"; // user facing name, seen if they hold press the notification
	public static Timer timer;
	
	//localHandle is how static functions access the currently instantiated main service.
	//It is to be used ONLY to register new surveys with the running main service, because
	//that code needs to be able to update the IntentFilters associated with timerReceiver.
	//This is Really Hacky and terrible style, but it is okay because the scheduling code can only ever
	//begin to run with an already fully instantiated main service.
	private static MainService localHandle;
	private static boolean foregroundServiceStarted = false;
	
	/** onCreate is essentially the constructor for the service, initialize variables here. */
	@Override
	public void onCreate () {
		appContext = this.getApplicationContext();
		try {
			String sentryDsn = BuildConfig.SENTRY_DSN;
			Sentry.init(sentryDsn, new AndroidSentryClientFactory(appContext));
		} catch (InvalidDsnException ie) {
			Sentry.init(new AndroidSentryClientFactory(appContext));
		}
		
		if (!BuildConfig.APP_IS_DEV) {
			Thread.setDefaultUncaughtExceptionHandler(new CrashHandler(appContext));
		}
		
		PersistentData.initialize(appContext);
		initializeFireBaseIDToken();
		TextFileManager.initialize(appContext);
		PostRequest.initialize(appContext);
		localHandle = this;  //yes yes, hacky, I know. This line needs to run before registerTimers()
		registerTimers(appContext);
		
		createNotificationChannel();
		doSetup();
	}
	
	public void doSetup () {
		//Accelerometer and power state don't need permissons
		startPowerStateListener();
		gpsListener = new GPSListener(appContext); // Permissions are checked in the broadcast receiver
		WifiListener.initialize(appContext);
		
		if (PersistentData.getAccelerometerEnabled())
			accelerometerListener = new AccelerometerListener(appContext);
		
		if (PersistentData.getGyroscopeEnabled())
			gyroscopeListener = new GyroscopeListener(appContext);
		
		//Bluetooth, wifi, gps, calls, and texts need permissions
		if (PermissionHandler.confirmBluetooth(appContext))
			startBluetooth();
		
		//		if ( PermissionHandler.confirmWifi(appContext) ) { WifiListener.initialize( appContext ); }
		
		if (PermissionHandler.confirmTexts(appContext)) {
			startSmsSentLogger();
			startMmsSentLogger();
		} else if (PersistentData.getTextsEnabled()) {
			sendBroadcast(Timer.checkForSMSEnabled);
		}
		
		if (PermissionHandler.confirmCalls(appContext))
			startCallLogger();
		else if (PersistentData.getCallsEnabled())
			sendBroadcast(Timer.checkForCallsEnabled);
		
		//Only do the following if the device is registered
		if (PersistentData.getIsRegistered()) {
			DeviceInfo.initialize(appContext); //if at registration this has already been initialized. (we don't care.)
			startTimers();
		}
	}
	
	private void createNotificationChannel () {
		// setup the notification channel so the service can run in the foreground
		NotificationChannel chan = new NotificationChannel(notificationChannelId, channelName, NotificationManager.IMPORTANCE_DEFAULT);
		chan.setLightColor(Color.BLUE);
		chan.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
		chan.setSound(null, null);
		NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
		assert manager != null;
		manager.createNotificationChannel(chan);
	}
	
	/** Stops the BackgroundService instance. */
	public void stop () {
		if (BuildConfig.APP_IS_BETA) {
			this.stopSelf();
		}
	}
	
	/*#############################################################################
	#########################         Starters              #######################
	#############################################################################*/
	
	/** Initializes the Bluetooth listener 
	 * Note: Bluetooth has several checks to make sure that it actually exists on the device with the capabilities we need.
	 * Checking for Bluetooth LE is necessary because it is an optional extension to Bluetooth 4.0. */
	public void startBluetooth () {
		//Note: the Bluetooth listener is a BroadcastReceiver, which means it must have a 0-argument constructor in order for android can instantiate it on broadcast receipts.
		//The following check must be made, but it requires a Context that we cannot pass into the BluetoothListener, so we do the check in the BackgroundService.
		if (appContext.getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE) && PersistentData.getBluetoothEnabled()) {
			this.bluetoothListener = new BluetoothListener();
			if (this.bluetoothListener.isBluetoothEnabled()) {
//				Log.i("Main Service", "success, actually doing bluetooth things.");
				registerReceiver(this.bluetoothListener, new IntentFilter("android.bluetooth.adapter.action.STATE_CHANGED"));
			} else {
				//TODO: Low priority. Eli. Track down why this error log pops up, cleanup.  -- the above check should be for the (new) doesBluetoothCapabilityExist function instead of isBluetoothEnabled
				Log.e("Main Service", "bluetooth Failure. Should not have gotten this far.");
				TextFileManager.getDebugLogFile().writeEncrypted("bluetooth Failure, device should not have gotten to this line of code");
			}
		} else {
			if (PersistentData.getBluetoothEnabled()) {
				TextFileManager.getDebugLogFile().writeEncrypted("Device does not support bluetooth LE, bluetooth features disabled.");
				Log.w("MainS bluetooth init", "Device does not support bluetooth LE, bluetooth features disabled.");
			}
			// else { Log.d("BackgroundService bluetooth init", "Bluetooth not enabled for study."); }
			this.bluetoothListener = null;
		}
	}
	
	/** Initializes the sms logger. */
	public void startSmsSentLogger () {
		SmsSentLogger smsSentLogger = new SmsSentLogger(new Handler(), appContext);
		this.getContentResolver().registerContentObserver(Uri.parse("content://sms/"), true, smsSentLogger);
	}
	
	public void startMmsSentLogger () {
		MMSSentLogger mmsMonitor = new MMSSentLogger(new Handler(), appContext);
		this.getContentResolver().registerContentObserver(Uri.parse("content://mms/"), true, mmsMonitor);
	}
	
	/** Initializes the call logger. */
	private void startCallLogger () {
		CallLogger callLogger = new CallLogger(new Handler(), appContext);
		this.getContentResolver().registerContentObserver(Uri.parse("content://call_log/calls/"), true, callLogger);
	}
	
	/** Initializes the PowerStateListener. 
	 * The PowerStateListener requires the ACTION_SCREEN_OFF and ACTION_SCREEN_ON intents
	 * be registered programatically. They do not work if registered in the app's manifest.
	 * Same for the ACTION_POWER_SAVE_MODE_CHANGED and ACTION_DEVICE_IDLE_MODE_CHANGED filters,
	 * though they are for monitoring deeper power state changes in 5.0 and 6.0, respectively. */
	@SuppressLint ("InlinedApi")
	private void startPowerStateListener () {
		if (powerStateListener == null) {
			IntentFilter filter = new IntentFilter();
			filter.addAction(Intent.ACTION_SCREEN_ON);
			filter.addAction(Intent.ACTION_SCREEN_OFF);
			if (android.os.Build.VERSION.SDK_INT >= 21) {
				filter.addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED);
			}
			if (android.os.Build.VERSION.SDK_INT >= 23) {
				filter.addAction(PowerManager.ACTION_DEVICE_IDLE_MODE_CHANGED);
			}
			powerStateListener = new PowerStateListener();
			registerReceiver(powerStateListener, filter);
			PowerStateListener.start(appContext);
		}
	}
	
	
	/** create timers that will trigger events throughout the program, and
	 * register the custom Intents with the controlMessageReceiver. */
	@SuppressWarnings ("static-access")
	public static void registerTimers (Context appContext) {
		localHandle.timer = new Timer(localHandle);
		IntentFilter filter = new IntentFilter();
		filter.addAction(appContext.getString(R.string.turn_accelerometer_off));
		filter.addAction(appContext.getString(R.string.turn_accelerometer_on));
		filter.addAction(appContext.getString(R.string.turn_ambient_audio_off));
		filter.addAction(appContext.getString(R.string.turn_ambient_audio_on));
		filter.addAction(appContext.getString(R.string.turn_gyroscope_on));
		filter.addAction(appContext.getString(R.string.turn_gyroscope_off));
		filter.addAction(appContext.getString(R.string.turn_bluetooth_on));
		filter.addAction(appContext.getString(R.string.turn_bluetooth_off));
		filter.addAction(appContext.getString(R.string.turn_gps_on));
		filter.addAction(appContext.getString(R.string.turn_gps_off));
		filter.addAction(appContext.getString(R.string.signout_intent));
		filter.addAction(appContext.getString(R.string.voice_recording));
		filter.addAction(appContext.getString(R.string.run_wifi_log));
		filter.addAction(appContext.getString(R.string.upload_data_files_intent));
		filter.addAction(appContext.getString(R.string.create_new_data_files_intent));
		filter.addAction(appContext.getString(R.string.check_for_new_surveys_intent));
		filter.addAction(appContext.getString(R.string.check_for_sms_enabled));
		filter.addAction(appContext.getString(R.string.check_for_calls_enabled));
		filter.addAction(appContext.getString(R.string.check_if_ambient_audio_recording_is_enabled));
		filter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
		filter.addAction("crashBeiwe");
		filter.addAction("enterANR");
		List<String> surveyIds = PersistentData.getSurveyIds();
		for (String surveyId: surveyIds) {
			filter.addAction(surveyId);
		}
		appContext.registerReceiver(localHandle.timerReceiver, filter);
	}
	
	/** Gets, sets, and pushes the FCM token to the backend. */
	public void initializeFireBaseIDToken () {
		final String errorMessage =
			"Unable to get FCM token, will not be able to receive push notifications.";
		
		// Set up the oncomplete listener for the FCM getter code, then wait until registered
		// to actually push it to the server or else the post request will error.
		FirebaseInstanceId.getInstance().getInstanceId()
			.addOnCompleteListener(new OnCompleteListener<InstanceIdResult>() {
				@Override
				public void onComplete (@NonNull Task<InstanceIdResult> task) {
					
					if (!task.isSuccessful()) {
						Log.e("FCM", errorMessage, task.getException());
						TextFileManager.writeDebugLogStatement(errorMessage + "(1)");
						return;
					}
					
					// Get new Instance ID token
					InstanceIdResult taskResult = task.getResult();
					if (taskResult == null) {
						TextFileManager.writeDebugLogStatement(errorMessage + "(2)");
						return;
					}
					
					//We need to wait until the participant is registered to send the fcm token.
					final String token = taskResult.getToken();
					Thread outerNotifcationBlockerThread = new Thread(new Runnable() {
						@Override
						public void run () {
							while (!PersistentData.getIsRegistered()) {
								try {
									Thread.sleep(1000);
								} catch (InterruptedException ignored) {
									TextFileManager.writeDebugLogStatement(errorMessage + "(3)");
									return;
								}
							}
							PersistentData.setFCMInstanceID(token);
							PostRequest.setFCMInstanceID(token);
						}
					}, "outerNotifcationBlockerThread");
					outerNotifcationBlockerThread.start();
				}
			});
	}
	
	/*#############################################################################
	####################            Timer Logic             #######################
	#############################################################################*/
	
	public void startTimers () {
		Long now = System.currentTimeMillis();
		Log.i("BackgroundService", "running startTimer logic.");
		
		if (PersistentData.getAccelerometerEnabled()) {  //if accelerometer data recording is enabled and...
			long accelMostRecentAlarmTime = PersistentData.getMostRecentAlarmTime(getString(R.string.turn_accelerometer_on));
			long accelMostRecentOffTime = accelMostRecentAlarmTime - PersistentData.getAccelerometerOffDuration() + 1000;
			if (accelMostRecentAlarmTime < now || //the most recent accelerometer alarm time is in the past, or...
				!timer.alarmIsSet(Timer.accelerometerOnIntent)) { //there is no scheduled accelerometer-on timer.
				sendBroadcast(Timer.accelerometerOnIntent); // start accelerometer timers (immediately runs accelerometer recording session).
			} else if (timer.alarmIsSet(Timer.accelerometerOffIntent) && accelMostRecentOffTime > now) {
				//note: when there is no accelerometer-off timer that means we are in-between scans.  This state is fine, so we don't check for it.
				accelerometerListener.turn_on();
			}
		}
		
		// logic identical to accelerometer
		if (PersistentData.getGyroscopeEnabled()) {
			long gyroMostRecentAlarmTime = PersistentData.getMostRecentAlarmTime(getString(R.string.turn_gyroscope_on));
			long gyroMostRecentOffTime = gyroMostRecentAlarmTime - PersistentData.getGyroscopeOffDuration() + 1000;
			if (gyroMostRecentAlarmTime < now || !timer.alarmIsSet(Timer.gyroscopeOnIntent)) {
				sendBroadcast(Timer.gyroscopeOnIntent);
			} else if (timer.alarmIsSet(Timer.gyroscopeOffIntent) && gyroMostRecentOffTime > now) {
				gyroscopeListener.turn_on();
			}
		}
		
		// logic identical to accelerometer
		if (PersistentData.getAmbientAudioEnabled()) {
			long ambientAudioMostRecentAlarmTime = PersistentData.getMostRecentAlarmTime(getString(R.string.turn_ambient_audio_on));
			long ambientAudioMostRecentOffTime = ambientAudioMostRecentAlarmTime - PersistentData.getAmbientAudioOffDuration() + 1000;
			if (ambientAudioMostRecentAlarmTime < now || !timer.alarmIsSet(Timer.ambientAudioOnIntent)) {
				sendBroadcast(Timer.ambientAudioOnIntent);
			} else if (timer.alarmIsSet(Timer.ambientAudioOffIntent) && ambientAudioMostRecentOffTime > now) {
				AmbientAudioListener.startRecording(appContext);
			}
		}
		// logic identical to accelerometer
		if (PersistentData.getGpsEnabled()) {
			long gpsMostRecentAlarmTime = PersistentData.getMostRecentAlarmTime(getString(R.string.turn_gps_on));
			long gpsMostRecentOffTime = gpsMostRecentAlarmTime - PersistentData.getGpsOffDuration() + 1000;
			if (gpsMostRecentAlarmTime < now || !timer.alarmIsSet(Timer.gpsOnIntent)) {
				sendBroadcast(Timer.gpsOnIntent);
			} else if (timer.alarmIsSet(Timer.gpsOffIntent) && gpsMostRecentOffTime > now) {
				gpsListener.turn_on();
			}
		}
		
		// wifi has a one-time timer
		if (PersistentData.getWifiEnabled()) {
			// the most recent wifi log time is in the past or no timer is set
			long mostRecentWifiScan = PersistentData.getMostRecentAlarmTime(getString(R.string.run_wifi_log));
			if (mostRecentWifiScan < now || !timer.alarmIsSet(Timer.wifiLogIntent)) {
				sendBroadcast(Timer.wifiLogIntent);
			}
		}
		
		//if Bluetooth recording is enabled and there is no scheduled next-bluetooth-enable event, set up the next Bluetooth-on alarm.
		//(Bluetooth needs to run at absolute points in time, it should not be started if a scheduled event is missed.)
		if (PersistentData.getBluetoothEnabled()) {
			if (PermissionHandler.confirmBluetooth(appContext) && !timer.alarmIsSet(Timer.bluetoothOnIntent)) {
				timer.setupExactSingleAbsoluteTimeAlarm(
					PersistentData.getBluetoothTotalDuration(), PersistentData.getBluetoothGlobalOffset(), Timer.bluetoothOnIntent
				);
			}
		}
		
		// Functionality timers. We don't need aggressive checking for if these timers have been missed, as long as they run eventually it is fine.
		if (!timer.alarmIsSet(Timer.uploadDatafilesIntent)) {
			timer.setupExactSingleAlarm(PersistentData.getUploadDataFilesFrequency(), Timer.uploadDatafilesIntent);
		}
		if (!timer.alarmIsSet(Timer.createNewDataFilesIntent)) {
			timer.setupExactSingleAlarm(PersistentData.getCreateNewDataFilesFrequency(), Timer.createNewDataFilesIntent);
		}
		if (!timer.alarmIsSet(Timer.checkForNewSurveysIntent)) {
			timer.setupExactSingleAlarm(PersistentData.getCheckForNewSurveysFrequency(), Timer.checkForNewSurveysIntent);
		}
		
		//checks for the current expected state for survey notifications,
		for (String surveyId: PersistentData.getSurveyIds()) {
			if (PersistentData.getSurveyNotificationState(surveyId) || PersistentData.getMostRecentSurveyAlarmTime(surveyId) < now) {
				//if survey notification should be active or the most recent alarm time is in the past, trigger the notification.
				SurveyNotifications.displaySurveyNotification(appContext, surveyId);
			}
		}
		
		//checks that surveys are actually scheduled, if a survey is not scheduled, schedule it!
		for (String surveyId: PersistentData.getSurveyIds()) {
			if (!timer.alarmIsSet(new Intent(surveyId))) {
				SurveyScheduler.scheduleSurvey(surveyId);
			}
		}
		
		// this is a repeating alarm that ensures the service is running, it starts the service if it isn't.
		Intent restartServiceIntent = new Intent(getApplicationContext(), MainService.class);
		restartServiceIntent.setPackage(getPackageName());
		int flags = pending_intent_flag_fix(PendingIntent.FLAG_UPDATE_CURRENT);
		PendingIntent repeatingRestartServicePendingIntent = PendingIntent.getService(
			getApplicationContext(), 1, restartServiceIntent, flags);
		AlarmManager alarmService = (AlarmManager) getApplicationContext().getSystemService(Context.ALARM_SERVICE);
		alarmService.setRepeating(AlarmManager.RTC_WAKEUP,
			System.currentTimeMillis() + 1000 * 60 * 2,
			1000 * 60 * 2,
			repeatingRestartServicePendingIntent
		);
	}
	
	/**Refreshes the logout timer.
	 * This function has a THEORETICAL race condition, where the BackgroundService is not fully instantiated by a session activity,
	 * in this case we log an error to the debug log, print the error, and then wait for it to crash.  In testing on a (much) older
	 * version of the app we would occasionally see the error message, but we have never (august 10 2015) actually seen the app crash
	 * inside this code. */
	public static void startAutomaticLogoutCountdownTimer () {
		if (timer == null) {
			Log.e("bacgroundService", "timer is null, BackgroundService may be about to crash, the Timer was null when the BackgroundService was supposed to be fully instantiated.");
			TextFileManager.getDebugLogFile().writeEncrypted("our not-quite-race-condition encountered, Timer was null when the BackgroundService was supposed to be fully instantiated");
		}
		timer.setupExactSingleAlarm(PersistentData.getTimeBeforeAutoLogout(), Timer.signoutIntent);
		PersistentData.loginOrRefreshLogin();
	}
	
	/** cancels the signout timer */
	public static void clearAutomaticLogoutCountdownTimer () {
		timer.cancelAlarm(Timer.signoutIntent);
	}
	
	/** The Timer requires the BackgroundService in order to create alarms, hook into that functionality here. */
	public static void setSurveyAlarm (String surveyId, Calendar alarmTime) {
		timer.startSurveyAlarm(surveyId, alarmTime);
	}
	
	public static void cancelSurveyAlarm (String surveyId) {
		timer.cancelAlarm(new Intent(surveyId));
	}
	
	/**The timerReceiver is an Android BroadcastReceiver that listens for our timer events to trigger,
	 * and then runs the appropriate code for that trigger. 
	 * Note: every condition has a return statement at the end; this is because the trigger survey notification
	 * action requires a fairly expensive dive into PersistantData JSON unpacking.*/
	private BroadcastReceiver timerReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive (Context appContext, Intent intent) {
			Log.d("BackgroundService", "Received broadcast: " + intent.toString());
			TextFileManager.getDebugLogFile().writeEncrypted(System.currentTimeMillis() + " Received Broadcast: " + intent.toString());
			String broadcastAction = intent.getAction();
			
			/* For GPS and Accelerometer the failure modes are:
			 * 1. If a recording event is triggered and followed by Doze being enabled then Beiwe will record until the Doze period ends.
			 * 2. If, after Doze ends, the timers trigger out of order Beiwe ceases to record and triggers a new recording event in the future. */
			
			/* Disable active sensor */
			if (broadcastAction.equals(appContext.getString(R.string.turn_accelerometer_off))) {
				accelerometerListener.turn_off();
				return;
			}
			if (broadcastAction.equals(appContext.getString(R.string.turn_gyroscope_off))) {
				gyroscopeListener.turn_off();
				return;
			}
			if (broadcastAction.equals(appContext.getString(R.string.turn_gps_off))) {
				if (PermissionHandler.checkGpsPermissions(appContext)) {
					gpsListener.turn_off();
				}
				return;
			}
			if (broadcastAction.equals(appContext.getString(R.string.turn_ambient_audio_off))) {
				AmbientAudioListener.encryptAmbientAudioFile();
				return;
			}
			
			
			// Enable active sensors, reset timers.
			//Accelerometer. We automatically have permissions required for accelerometer.
			if (broadcastAction.equals(appContext.getString(R.string.turn_accelerometer_on))) {
				if (!accelerometerListener.exists) {
					return;
				}
				accelerometerListener.turn_on();
				//start both the sensor-off-action timer, and the next sensor-on-timer.
				timer.setupExactSingleAlarm(PersistentData.getAccelerometerOnDuration(), Timer.accelerometerOffIntent);
				long alarmTime = timer.setupExactSingleAlarm(
					PersistentData.getAccelerometerOffDuration() + PersistentData.getAccelerometerOnDuration(),
					Timer.accelerometerOnIntent
				);
				//record the system time that the next alarm is supposed to go off at, so that we can recover in the event of a reboot or crash. 
				PersistentData.setMostRecentAlarmTime(getString(R.string.turn_accelerometer_on), alarmTime);
				return;
			}
			
			//Gyroscope. Almost identical logic to accelerometer above.
			if (broadcastAction.equals(appContext.getString(R.string.turn_gyroscope_on))) {
				if (!gyroscopeListener.exists) {
					return;
				}
				gyroscopeListener.turn_on();
				//start both the sensor-off-action timer, and the next sensor-on-timer.
				timer.setupExactSingleAlarm(PersistentData.getGyroscopeOnDuration(), Timer.gyroscopeOffIntent);
				long alarmTime = timer.setupExactSingleAlarm(
					PersistentData.getGyroscopeOffDuration() + PersistentData.getGyroscopeOnDuration(),
					Timer.gyroscopeOnIntent
				);
				//record the system time that the next alarm is supposed to go off at, so that we can recover in the event of a reboot or crash.
				PersistentData.setMostRecentAlarmTime(getString(R.string.turn_gyroscope_on), alarmTime);
				return;
			}
			
			//GPS. Almost identical logic to accelerometer above.
			if (broadcastAction.equals(appContext.getString(R.string.turn_gps_on))) {
				gpsListener.turn_on();
				timer.setupExactSingleAlarm(PersistentData.getGpsOnDuration(), Timer.gpsOffIntent);
				long alarmTime = timer.setupExactSingleAlarm(
					PersistentData.getGpsOnDuration() + PersistentData.getGpsOffDuration(),
					Timer.gpsOnIntent
				);
				PersistentData.setMostRecentAlarmTime(getString(R.string.turn_gps_on), alarmTime);
				return;
			}
			//run a wifi scan.  Most similar to GPS, but without an off-timer.
			if (broadcastAction.equals(appContext.getString(R.string.run_wifi_log))) {
				if (PermissionHandler.checkWifiPermissions(appContext)) {
					WifiListener.scanWifi();
				} else {
					TextFileManager.getDebugLogFile().writeEncrypted(System.currentTimeMillis() + " user has not provided permission for Wifi.");
				}
				long alarmTime = timer.setupExactSingleAlarm(PersistentData.getWifiLogFrequency(), Timer.wifiLogIntent);
				PersistentData.setMostRecentAlarmTime(getString(R.string.run_wifi_log), alarmTime);
				return;
			}
			
			/* Bluetooth timers are unlike GPS and Accelerometer because it uses an absolute-point-in-time as a trigger, and therefore we don't need to store most-recent-timer state.
			 * The Bluetooth-on action sets the corresponding Bluetooth-off timer, the Bluetooth-off action sets the next Bluetooth-on timer.*/
			if (broadcastAction.equals(appContext.getString(R.string.turn_bluetooth_on))) {
				if (!PersistentData.getBluetoothEnabled()) {
					Log.e("BackgroundService", "invalid Bluetooth on received");
					return;
				}
				if (PermissionHandler.checkBluetoothPermissions(appContext)) {
					if (bluetoothListener != null) bluetoothListener.enableBLEScan();
				} else {
					TextFileManager.getDebugLogFile().writeEncrypted(System.currentTimeMillis() + " user has not provided permission for Bluetooth.");
				}
				timer.setupExactSingleAlarm(PersistentData.getBluetoothOnDuration(), Timer.bluetoothOffIntent);
				return;
			}

			if (broadcastAction.equals(appContext.getString(R.string.turn_bluetooth_off))) {
				if (PermissionHandler.checkBluetoothPermissions(appContext)) {
					if (bluetoothListener != null) bluetoothListener.disableBLEScan();
				}
				timer.setupExactSingleAbsoluteTimeAlarm(PersistentData.getBluetoothTotalDuration(), PersistentData.getBluetoothGlobalOffset(), Timer.bluetoothOnIntent);
				return;
			}
			
			//starts a data upload attempt.
			if (broadcastAction.equals(appContext.getString(R.string.upload_data_files_intent))) {
				PostRequest.uploadAllFiles();
				timer.setupExactSingleAlarm(PersistentData.getUploadDataFilesFrequency(), Timer.uploadDatafilesIntent);
				return;
			}
			
			//creates new data files
			if (broadcastAction.equals(appContext.getString(R.string.create_new_data_files_intent))) {
				TextFileManager.makeNewFilesForEverything();
				timer.setupExactSingleAlarm(PersistentData.getCreateNewDataFilesFrequency(), Timer.createNewDataFilesIntent);
				PostRequest.uploadAllFiles();
				return;
			}
			
			//Downloads the most recent survey questions and schedules the surveys.
			if (broadcastAction.equals(appContext.getString(R.string.check_for_new_surveys_intent))) {
				SurveyDownloader.downloadSurveys(getApplicationContext(), null);
				timer.setupExactSingleAlarm(PersistentData.getCheckForNewSurveysFrequency(), Timer.checkForNewSurveysIntent);
				return;
			}
			
			// Signs out the user. (does not set up a timer, that is handled in activity and sign-in logic) 
			if (broadcastAction.equals(appContext.getString(R.string.signout_intent))) {
				PersistentData.logout();
				Intent loginPage = new Intent(appContext, LoginActivity.class);
				loginPage.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
				appContext.startActivity(loginPage);
				return;
			}
			
			// logic for the SMS/MMS message logger
			if (broadcastAction.equals(appContext.getString(R.string.check_for_sms_enabled))) {
				if (PermissionHandler.confirmTexts(appContext)) {
					startSmsSentLogger();
					startMmsSentLogger();
				} else if (PersistentData.getTextsEnabled()) {
					timer.setupExactSingleAlarm(30000L, Timer.checkForSMSEnabled);
				}
			}
			
			// logic for the call (metadata) logger
			if (broadcastAction.equals(appContext.getString(R.string.check_for_calls_enabled))) {
				if (PermissionHandler.confirmCalls(appContext)) {
					startCallLogger();
				} else if (PersistentData.getCallsEnabled()) {
					timer.setupExactSingleAlarm(30000L, Timer.checkForCallsEnabled);
				}
			}
			
			// ambient audio logic
			if (broadcastAction.equals(appContext.getString(R.string.turn_ambient_audio_on))) {
				AmbientAudioListener.startRecording(appContext);
				timer.setupExactSingleAlarm(PersistentData.getAmbientAudioOnDuration(), Timer.ambientAudioOffIntent);
				long alarmTime = timer.setupExactSingleAlarm(PersistentData.getAmbientAudioOffDuration() + PersistentData.getAmbientAudioOnDuration(), Timer.ambientAudioOnIntent);
				PersistentData.setMostRecentAlarmTime(getString(R.string.turn_ambient_audio_on), alarmTime);
				return;
			}
			
			//checks if the action is the id of a survey (expensive), if so pop up the notification for that survey, schedule the next alarm
			if (PersistentData.getSurveyIds().contains(broadcastAction)) {
//				Log.i("MAIN SERVICE", "new notification: " + broadcastAction);
				SurveyNotifications.displaySurveyNotification(appContext, broadcastAction);
				SurveyScheduler.scheduleSurvey(broadcastAction);
				return;
			}
			
			// logic for data upload (note only runs after registration)
			if (PersistentData.getIsRegistered() && broadcastAction.equals(ConnectivityManager.CONNECTIVITY_ACTION)) {
				NetworkInfo networkInfo = intent.getParcelableExtra(ConnectivityManager.EXTRA_NETWORK_INFO);
				if (networkInfo.getType() == ConnectivityManager.TYPE_WIFI) {
					PostRequest.uploadAllFiles();
					return;
				}
			}
			
			//these are  a special actions that will only run if the app device is in debug mode.
			if (broadcastAction.equals("crashBeiwe") && BuildConfig.APP_IS_BETA) {
				throw new NullPointerException("beeeeeoooop.");
			}
			if (broadcastAction.equals("enterANR") && BuildConfig.APP_IS_BETA) {
				try {
					sleep(100000);
				} catch (InterruptedException ie) {
					ie.printStackTrace();
				}
			}
		}
	};
	
	/*##########################################################################################
	############## code related to onStartCommand and binding to an activity ###################
	##########################################################################################*/
	@Override
	public IBinder onBind (Intent arg0) {
		return new BackgroundServiceBinder();
	}
	
	/**A public "Binder" class for Activities to access.
	 * Provides a (safe) handle to the Main Service using the onStartCommand code
	 * used in every RunningBackgroundServiceActivity */
	public class BackgroundServiceBinder extends Binder {
		public MainService getService () {
			return MainService.this;
		}
	}
	
	/*##############################################################################
	########################## Android Service Lifecycle ###########################
	##############################################################################*/
	
	/** The BackgroundService is meant to be all the time, so we return START_STICKY */
	@Override
	public int onStartCommand (Intent intent, int flags, int startId) {
		//Log.d("BackgroundService onStartCommand", "started with flag " + flags );
		TextFileManager.getDebugLogFile().writeEncrypted(System.currentTimeMillis() + " " + "started with flag " + flags);
		
		if (!foregroundServiceStarted) {
			Intent intent_to_start_foreground_service = new Intent(getApplicationContext(), MainService.class);
			int intent_flags = pending_intent_flag_fix(0);  // no flags
			PendingIntent onStartCommandPendingIntent = PendingIntent.getService(
				getApplicationContext(), 0, intent_to_start_foreground_service, intent_flags
			);
			
			Notification notification =
				new Notification.Builder(getApplicationContext(), notificationChannelId)
					.setContentTitle("Beiwe App")
					.setContentText("Beiwe data collection running")
					.setSmallIcon(R.mipmap.ic_launcher)
					.setContentIntent(onStartCommandPendingIntent)
					.setTicker("Beiwe data collection running in the background, no action required")
					.build();
			
			// multiple sources recommend an ID of 1 because it works. documentation is very spotty about this
			startForeground(1, notification);
			foregroundServiceStarted = true;
		}
		
		// We want this service to continue running until it is explicitly stopped, so return sticky.
		return START_STICKY;
		// in testing out this restarting behavior for the service it is entirely unclear if changing
		// this return will have any observable effect despite the documentation's claim that it does.
		//return START_REDELIVER_INTENT;
	}
	
	// the rest of these are ~identical
	@Override
	public void onTaskRemoved (Intent rootIntent) {
		//Log.d("BackroundService onTaskRemoved", "onTaskRemoved called with intent: " + rootIntent.toString() );
		TextFileManager.getDebugLogFile().writeEncrypted(System.currentTimeMillis() + " " + "onTaskRemoved called with intent: " + rootIntent.toString());
		restartService();
	}
	
	@Override
	public boolean onUnbind (Intent intent) {
		//Log.d("BackroundService onUnbind", "onUnbind called with intent: " + intent.toString() );
		TextFileManager.getDebugLogFile().writeEncrypted(System.currentTimeMillis() + " " + "onUnbind called with intent: " + intent.toString());
		restartService();
		return super.onUnbind(intent);
	}
	
	@Override
	public void onDestroy () { //Log.w("BackgroundService", "BackgroundService was destroyed.");
		//note: this does not run when the service is killed in a task manager, OR when the stopService() function is called from debugActivity.
		TextFileManager.getDebugLogFile().writeEncrypted(System.currentTimeMillis() + " " + "BackgroundService was destroyed.");
		restartService();
		super.onDestroy();
	}
	
	@Override
	public void onLowMemory () { //Log.w("BackroundService onLowMemory", "Low memory conditions encountered");
		TextFileManager.getDebugLogFile().writeEncrypted(System.currentTimeMillis() + " " + "onLowMemory called.");
		restartService();
	}
	
	/** Sets a timer that starts the service if it is not running in ten seconds. */
	private void restartService () {
		//how does this even...  Whatever, 10 seconds later the main service will start.
		Intent restartServiceIntent = new Intent(getApplicationContext(), this.getClass());
		restartServiceIntent.setPackage(getPackageName());
		
		int flags = pending_intent_flag_fix(PendingIntent.FLAG_ONE_SHOT);
		PendingIntent restartServicePendingIntent = PendingIntent.getService(
			getApplicationContext(), 1, restartServiceIntent, flags);
		
		AlarmManager alarmService = (AlarmManager) getApplicationContext().getSystemService(Context.ALARM_SERVICE);
		alarmService.set(AlarmManager.ELAPSED_REALTIME, SystemClock.elapsedRealtime() + 500, restartServicePendingIntent);
	}
}