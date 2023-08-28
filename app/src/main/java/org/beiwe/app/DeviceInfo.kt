package org.beiwe.app

import android.annotation.SuppressLint
import android.app.usage.StorageStatsManager
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import org.beiwe.app.storage.EncryptionEngine
import java.util.TimeZone

/**This is a class that NEEDS to be instantiated in the main service. In order to get the Android ID, the class needs
 * Context. Once instantiated, the class assigns two variables for AndroidID and BluetoothMAC. Once they are instantiated,
 * they can be called from different classes to be used. They are hashed when they are called.
 *
 * The class is used to grab unique ID data, and pass it to the server. The data is used while authenticating users
 *
 * @author Dor Samet, Eli Jones */

object DeviceInfo {
    // this is to be used in an increasing number of places where we hae system elapsed time for sensor events
    var boot_time_milli = System.currentTimeMillis() - SystemClock.elapsedRealtime()
    var boot_time_nano = System.nanoTime() - SystemClock.elapsedRealtimeNanos()
    private var androidID: String? = null
    private var bluetoothMAC: String? = null

    /** grab the Android ID and the Bluetooth's MAC address  */
    @JvmStatic
    @SuppressLint("HardwareIds")
    fun initialize(appContext: Context) {
        androidID = Settings.Secure.getString(appContext.contentResolver, Settings.Secure.ANDROID_ID) // android ID appears to be a 64 bit string

        /* If the BluetoothAdapter is null, or if the BluetoothAdapter.getAddress() returns null,
		 * record an empty string for the Bluetooth MAC Address.
		 * The Bluetooth MAC Address is always empty in Android 8.0 and above, because the app needs
		 * the LOCAL_MAC_ADDRESS permission, which is a system permission that it's not allowed to
		 * have:
		 * https://android-developers.googleblog.com/2017/04/changes-to-device-identifiers-in.html
		 * The Bluetooth MAC Address is also sometimes empty on Android 7 and lower. */

        // This will not work on all devices: http://stackoverflow.com/questions/33377982/get-bluetooth-local-mac-address-in-marshmallow
        if (Build.VERSION.SDK_INT >= 23) {
            var bluetoothAddress = Settings.Secure.getString(appContext.contentResolver, "bluetooth_address")
            if (bluetoothAddress == null) {
                bluetoothAddress = ""
            }

            bluetoothMAC = EncryptionEngine.hashMAC(bluetoothAddress)
        } else { // Android before version 6
            val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
            if (bluetoothAdapter == null || bluetoothAdapter.address == null) {
                bluetoothMAC = ""
            } else {
                bluetoothMAC = bluetoothAdapter.address
            }
        }
    }

    @JvmStatic
    val beiweVersion: String
        get() = BuildConfig.FLAVOR + "-" + BuildConfig.VERSION_NAME

    @JvmStatic
    val androidVersion: String
        get() = Build.VERSION.RELEASE

    @JvmStatic
    val product: String
        get() = Build.PRODUCT

    @JvmStatic
    val brand: String
        get() = Build.BRAND

    @JvmStatic
    val hardwareId: String
        get() = Build.HARDWARE

    @JvmStatic
    val manufacturer: String
        get() = Build.MANUFACTURER

    @JvmStatic
    val model: String
        get() = Build.MODEL

    @JvmStatic
    fun getAndroidID(): String {
        return EncryptionEngine.safeHash(androidID)
    }

    @JvmStatic
    fun getBluetoothMAC(): String {
        return EncryptionEngine.hashMAC(bluetoothMAC)
    }

    @JvmStatic
    fun timeZoneInfo(): String {
        return  TimeZone.getDefault().id  // this is a string like "America/New_York"
    }

    @JvmStatic
    fun freeSpace(): String {
        // profiled on a Pixel 6, it takes less than a millisecond.
        return android.os.StatFs("/data").availableBytes.toString()
    }
}