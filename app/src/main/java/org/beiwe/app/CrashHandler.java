package org.beiwe.app;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;
import android.util.Log;

import org.beiwe.app.networking.PostRequest;
import org.beiwe.app.storage.PersistentData;

import io.sentry.Sentry;
import io.sentry.event.BreadcrumbBuilder;

public class CrashHandler implements java.lang.Thread.UncaughtExceptionHandler{
	private final Context errorHandlerContext;
	private int millisecondsUntilRestart = 500;
	public CrashHandler(Context context) { this.errorHandlerContext = context; }

	/** This function is where any errors that occur in any Activity that inherits RunningBackgroundServiceActivity
	 * will dump its errors.  We roll it up, stick it in a file, and try to restart the app after exiting it.
	 * (using a new alarm like we do in the BackgroundService). */
	public void uncaughtException(Thread thread, Throwable exception){
		
		Log.w("CrashHandler Raw","start original stacktrace");
		exception.printStackTrace();
		Log.w("CrashHandler Raw","end original stacktrace");
		
		//Write that log file
		Sentry.getContext().recordBreadcrumb(
				new BreadcrumbBuilder().setMessage("Attempting application restart").build()
		);
		writeCrashlog(exception, errorHandlerContext);
//		Log.i("inside crashlog", "does this line happen");  //keep this line for debugging crashes in the crash handler (yup.)
		//setup to restart service
		Intent restartServiceIntent = new Intent( errorHandlerContext, BackgroundService.class );
		restartServiceIntent.setPackage( errorHandlerContext.getPackageName() );
		PendingIntent restartServicePendingIntent = PendingIntent.getService( errorHandlerContext, 1, restartServiceIntent, PendingIntent.FLAG_ONE_SHOT );
		AlarmManager alarmService = (AlarmManager) errorHandlerContext.getSystemService( Context.ALARM_SERVICE );
		alarmService.set(AlarmManager.ELAPSED_REALTIME, SystemClock.elapsedRealtime() + millisecondsUntilRestart, restartServicePendingIntent);
		//exit beiwe
		android.os.Process.killProcess(android.os.Process.myPid());
		System.exit(10);
	}
	
	
	/**Creates a crash log file that will be uploaded at the next upload event.
	 * Also writes error to the error log so that it is visible in logcat.
	 * @param exception A Throwable (probably your error).
	 * @param context An android Context */
	public static void writeCrashlog(Throwable exception, Context context) {
		Sentry.getContext().addTag("user_id", PersistentData.getPatientID());
		Sentry.getContext().addTag("server_url", PostRequest.addWebsitePrefix(""));
		Sentry.capture(exception);
	}
}