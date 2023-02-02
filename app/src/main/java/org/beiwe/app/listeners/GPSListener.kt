package org.beiwe.app.listeners

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.util.Log
import org.beiwe.app.PermissionHandler.checkAccessCoarseLocation
import org.beiwe.app.PermissionHandler.checkAccessFineLocation
import org.beiwe.app.storage.PersistentData
import org.beiwe.app.storage.TextFileManager

/* Notes/observation on Location Services:
 * We are passing in "0" as the minimum time for location updates to be pushed to us, this results in about
 * 1 update every second.  This is based on logs made using a nexus 7 tablet.
 * This makes sense, GPSs on phones do not Have that kind of granularity/resolution.
 * However, we need the resolution in milliseconds for the line-by-line encryption scheme.
 * So, we grab the system time instead.  This may add a fraction of a second to the timestamp.
 * 
 * We are NOT recording which location provider provided the update, or which location providers
 * are available on a given device. */
class GPSListener(private val appContext: Context) : LocationListener {
    companion object {
        const val header = "timestamp, latitude, longitude, altitude, accuracy"
    }

    private val pkgManager: PackageManager
    private var locationManager: LocationManager
    private var enabled = false

    /** Listens for GPS updates from the network GPS location provider and/or the true
     * GPS provider, both if possible.  It is NOT activated upon instantiation.  Requires an
     * application Context object be passed in in order to interface with location services.
     * When activated using the turn_on() function it will log any location updates to the GPS log.
     * @param appContext A Context provided an Activity or Service. */
    init {
        pkgManager = appContext.packageManager
        locationManager = appContext.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        Log.d("initializing GPS...", "initializing GPS...")
    }

    /** Turns on GPS providers, provided they are accessible. Handles permission errors appropriately
     * Tests many permissions */
    @SuppressLint("MissingPermission")
    @Synchronized
    fun turn_on() {
        val coarsePermissible = checkAccessCoarseLocation(appContext)
        val finePermissible = checkAccessFineLocation(appContext)

        if (!coarsePermissible)
            makeDebugLogStatement("Beiwe has not been granted permissions for coarse location updates.")
        if (!finePermissible)
            makeDebugLogStatement("Beiwe has not been granted permissions for fine location updates.")

        val fineExists = pkgManager.hasSystemFeature(PackageManager.FEATURE_LOCATION_GPS)
        val coarseExists = pkgManager.hasSystemFeature(PackageManager.FEATURE_LOCATION_NETWORK)
        if (!fineExists)
            makeDebugLogStatement("Fine location updates are unsupported on this device.")
        if (!coarseExists)
            makeDebugLogStatement("Coarse location updates are unsupported on this device.")

        if (!fineExists and !coarseExists)
            return

        // if already enabled return true.  We want the above logging, do not refactor to earlier in the logic.
        if (enabled)
            return

        //Instantiate a new location manager (looks like the fine and coarse available variables get confused if we use an old one.)
        locationManager = appContext.getSystemService(Context.LOCATION_SERVICE) as LocationManager

        //If the feature exists, request locations from it. (enable if their boolean flag is true.)
        if (fineExists && finePermissible && coarsePermissible) {
            //AndroidStudio insists that both of these require the same location permissions, which seems to be correct
            // since there is only one toggle in userland anyway, yes or no to location permissions.
            // parameters: provider, minTime, minDistance, listener);
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0f, this)
        }

        if (coarseExists && finePermissible && coarsePermissible) {
            // parameters: provider, minTime, minDistance, listener);
            locationManager.requestLocationUpdates(
                    LocationManager.NETWORK_PROVIDER, 0, 0f, this)
        }

        //Verbose statements on the quality of GPS data streams.
        val fineAvailable = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
        val coarseAvailable = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)

        if (!fineAvailable)
            makeDebugLogStatement("GPS data stream warning: fine location updates are currently disabled.")
        if (!coarseAvailable)
            makeDebugLogStatement("GPS data stream warning: coarse location updates are currently disabled.")

        enabled = true
    }

    /** Disable all location updates  */
    @Synchronized
    fun turn_off() {
        // pretty confident this cannot fail.
        locationManager.removeUpdates(this)
        enabled = false
    }

    /** pushes an update to us whenever there is a location update.  */
    override fun onLocationChanged(location: Location) {
        val javaTimeCode = System.currentTimeMillis()
        //order: time, latitude, longitude, altitude, horizontal_accuracy\n

        // Latitude and longitude offset should be 0 unless GPS fuzzing is enabled
        val latitude = location.latitude + PersistentData.getLatitudeOffset()
        val longitude = (location.longitude + PersistentData.getLongitudeOffset() + 180.0) % 360 - 180.0
        val data = (javaTimeCode.toString() + TextFileManager.DELIMITER
                + latitude + TextFileManager.DELIMITER
                + longitude + TextFileManager.DELIMITER
                + location.altitude + TextFileManager.DELIMITER
                + location.accuracy)
        //note, altitude is notoriously inaccurate, getAccuracy only applies to latitude/longitude
        TextFileManager.getGPSFile().writeEncrypted(data)
    }

    /*  We do not actually need to implement any of the following overrides.
	 *  When a provider has a changed we do not need to record it, and we have
	 *  not encountered any corner cases where these are relevant. */
    //  arg0 for Provider Enabled/Disabled is a string saying "network" or "gps".
    override fun onProviderDisabled(arg0: String) {} // Log.d("A location provider was disabled.", arg0); }
    override fun onProviderEnabled(arg0: String) {} //Log.d("A location provider was enabled.", arg0); }

    private fun makeDebugLogStatement(message: String) {
        TextFileManager.getDebugLogFile().writeEncrypted(System.currentTimeMillis().toString() + " " + message)
        Log.w("GPS recording warning", message)
    }
}