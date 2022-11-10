package org.beiwe.app.ui;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;

import androidx.core.content.ContextCompat;

import org.beiwe.app.MainService;
import org.beiwe.app.MainService.BackgroundServiceBinder;
import org.beiwe.app.BuildConfig;
import org.beiwe.app.R;
import org.beiwe.app.RunningBackgroundServiceActivity;
import org.beiwe.app.storage.EncryptionEngine;
import org.beiwe.app.storage.PersistentData;
import org.beiwe.app.ui.registration.RegisterActivity;
import org.beiwe.app.ui.user.MainMenuActivity;
import org.beiwe.app.ui.utils.AlertsManager;

import java.io.UnsupportedEncodingException;
import java.security.NoSuchAlgorithmException;

import io.sentry.Sentry;
import io.sentry.android.AndroidSentryClientFactory;
import io.sentry.dsn.InvalidDsnException;

/**The LoadingActivity is a temporary RunningBackgroundServiceActivity (Not a SessionActivity,
 * check out those classes if you are confused) that pops up when the user opens the app.
 * This activity runs some simple checks to make sure that the device can actually run the app,
 * and then bumps the user to the correct screen (Register or MainMenu).
 *
 * note: this cannot be a SessionActvity, doing so would cause it to instantiate itself infinitely when a user is logged out. 
 * @author Eli Jones, Dor Samet */

public class LoadingActivity extends RunningBackgroundServiceActivity {
	
	/** for some reason we have to override the serviceconnection in order to call finish()
	 * (inside loadingSequence()). otherwise we can't unbind the background service.
	 * IllegalArgumentException: Service not registered: org.beiwe.app.RunningBackgroundServiceActivit...
	 * using the mainServiceConnection variable is the least messy way that works. */
	protected ServiceConnection mainServiceConnection = new ServiceConnection() {
		@Override
		public void onServiceConnected (ComponentName name, IBinder binder) {
			// Log.d("loading ServiceConnection", "Main Service Connected");
			BackgroundServiceBinder some_binder = (BackgroundServiceBinder) binder;
			mainService = some_binder.getService();
			loadingSequence();
		}
		
		@Override
		public void onServiceDisconnected (ComponentName name) {
			// Log.d("loading ServiceConnection", "Main Service Disconnected");
			mainService = null;
		}
	};
	
	/**onCreate - right now it just calls on checkLogin() in SessionManager, and moves the activity
	 * to the appropriate page. In the future it could hold a splash screen before redirecting activity. */
	@Override
	protected void onCreate (Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		try {
			String sentryDsn = BuildConfig.SENTRY_DSN;
			Sentry.init(sentryDsn, new AndroidSentryClientFactory(getApplicationContext()));
		} catch (InvalidDsnException ie) {
			Sentry.init(new AndroidSentryClientFactory(getApplicationContext()));
		}
		
		setContentView(R.layout.activity_loading);
		
		if (testHashing()) {
			Intent startingIntent = new Intent(this.getApplicationContext(), MainService.class);
			startingIntent.addFlags(Intent.FLAG_FROM_BACKGROUND);
			// ContextCompat correctly handles old and new android APIs
			ContextCompat.startForegroundService(getApplicationContext(), startingIntent);
			bindService(startingIntent, mainServiceConnection, Context.BIND_AUTO_CREATE);
		} else {
			failureExit();
		}
	}
	
	
	/**CHecks whether device is registered, sends user to the correct screen. */
	private void loadingSequence () {
		//if the device is not registered, push the user to the register activity
		if (!PersistentData.getIsRegistered()) {
			startActivity(new Intent(this, RegisterActivity.class));
		}
		//if device is registered push user to the main menu.
		else {
			if (BuildConfig.APP_IS_BETA) {
				startActivity(new Intent(this, DebugInterfaceActivity.class));
			} else {
				startActivity(new Intent(this, MainMenuActivity.class));
			}
		}
		unbindService(mainServiceConnection);
		finish(); //destroy the loading screen
	}
	
	
	/*##################################################################################
	############################### Testing Function ###################################
	##################################################################################*/
	
	/**Tests whether the device can run the hash algorithm the app requires
	 * @return boolean of whether hashing works */
	private Boolean testHashing () {
		// Runs the unsafe hashing function and catches errors, if it catches errors.
		// The hashMAC function does not need to be tested here because it should not actually blow up.
		// The source indicates that it should not blow up.
		try {
			EncryptionEngine.unsafeHash("input");
		} catch (NoSuchAlgorithmException noSuchAlgorithm) {
			return false;
		} catch (UnsupportedEncodingException unSupportedEncoding) {
			return false;
		}
		return true;
	}
	
	/**Displays error, then exit.*/
	private void failureExit () {
		AlertsManager.showErrorAlert(getString(R.string.invalid_device), this, 1);
	}
}