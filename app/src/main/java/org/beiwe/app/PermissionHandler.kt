package org.beiwe.app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import org.beiwe.app.storage.PersistentData
import java.util.*

object PermissionHandler {
    const val PERMISSION_GRANTED = PackageManager.PERMISSION_GRANTED

    @JvmField
    var POWER_EXCEPTION_PERMISSION = "POWER_EXCEPTION_PERMISSION"
    @JvmField
    var permissionMessages: MutableMap<String, Int> = HashMap()

    init {
        permissionMessages[Manifest.permission.ACCESS_FINE_LOCATION] = R.string.permission_access_fine_location
        permissionMessages[Manifest.permission.ACCESS_NETWORK_STATE] = R.string.permission_access_network_state
        permissionMessages[Manifest.permission.ACCESS_WIFI_STATE] = R.string.permission_access_wifi_state
        permissionMessages[Manifest.permission.READ_SMS] = R.string.permission_read_sms
        permissionMessages[Manifest.permission.BLUETOOTH] = R.string.permission_bluetooth
        permissionMessages[Manifest.permission.BLUETOOTH_ADMIN] = R.string.permission_bluetooth_admin
        permissionMessages[Manifest.permission.CALL_PHONE] = R.string.permission_call_phone
        permissionMessages[Manifest.permission.INTERNET] = R.string.permission_internet
        permissionMessages[Manifest.permission.READ_CALL_LOG] = R.string.permission_read_call_log
        permissionMessages[Manifest.permission.READ_CONTACTS] = R.string.permission_read_contacts
        permissionMessages[Manifest.permission.READ_PHONE_STATE] = R.string.permission_read_phone_state
        permissionMessages[Manifest.permission.RECEIVE_BOOT_COMPLETED] = R.string.permission_receive_boot_completed
        permissionMessages[Manifest.permission.RECORD_AUDIO] = R.string.permission_record_audio
        permissionMessages[Manifest.permission.ACCESS_COARSE_LOCATION] = R.string.permission_access_coarse_location
        permissionMessages[Manifest.permission.RECEIVE_MMS] = R.string.permission_receive_mms
        permissionMessages[Manifest.permission.RECEIVE_SMS] = R.string.permission_receive_sms
        permissionMessages[Manifest.permission.ACCESS_BACKGROUND_LOCATION] = R.string.permission_access_background_location
        permissionMessages = Collections.unmodifiableMap(permissionMessages)
    }

    @JvmStatic
    fun getNormalPermissionMessage(permission: String, appContext: Context): String {
        return String.format(appContext.getString(R.string.permission_normal_request_template),
                appContext.getString(permissionMessages[permission]!!))
    }

    @JvmStatic
    fun getBumpingPermissionMessage(permission: String, appContext: Context): String {
        return String.format(appContext.getString(R.string.permission_bumping_request_template),
                appContext.getString(permissionMessages[permission]!!))
    }

    /* The following are enabled by default.
	 *  AccessNetworkState
	 *  AccessWifiState
	 *  Bluetooth
	 *  BluetoothAdmin
	 *  Internet
	 *  ReceiveBootCompleted
	 *
	 * the following are enabled on the registration screen
	 *  ReadSms
	 *  ReceiveMms
	 *  ReceiveSms
	 *
	 * This leaves the following to be prompted for at session activity start
	 *  AccessFineLocation - GPS
	 *  CallPhone - Calls
	 *  ReadCallLog - Calls
	 *  ReadContacts - Calls and SMS
	 *  ReadPhoneState - Calls
	 *  WriteCallLog - Calls
	 *
	 *  We will check for microphone recording as a special condition on the audio recording screen. */
    @JvmStatic
    fun checkAccessCoarseLocation(context: Context): Boolean {
        return context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PERMISSION_GRANTED
    }

    @JvmStatic
    fun checkAccessFineLocation(context: Context): Boolean {
        return context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PERMISSION_GRANTED
    }

    fun checkAccessNetworkState(context: Context): Boolean {
        return context.checkSelfPermission(Manifest.permission.ACCESS_NETWORK_STATE) == PERMISSION_GRANTED
    }

    fun checkAccessWifiState(context: Context): Boolean {
        return context.checkSelfPermission(Manifest.permission.ACCESS_WIFI_STATE) == PERMISSION_GRANTED
    }

    fun checkAccessBluetooth(context: Context): Boolean {
        return context.checkSelfPermission(Manifest.permission.BLUETOOTH) == PERMISSION_GRANTED
    }

    fun checkAccessBluetoothAdmin(context: Context): Boolean {
        return context.checkSelfPermission(Manifest.permission.BLUETOOTH_ADMIN) == PERMISSION_GRANTED
    }

    fun checkAccessCallPhone(context: Context): Boolean {
        return context.checkSelfPermission(Manifest.permission.CALL_PHONE) == PERMISSION_GRANTED
    }

    fun checkAccessReadCallLog(context: Context): Boolean {
        return context.checkSelfPermission(Manifest.permission.READ_CALL_LOG) == PERMISSION_GRANTED
    }

    fun checkAccessReadContacts(context: Context): Boolean {
        return context.checkSelfPermission(Manifest.permission.READ_CONTACTS) == PERMISSION_GRANTED
    }

    fun checkAccessReadPhoneState(context: Context): Boolean {
        return context.checkSelfPermission(Manifest.permission.READ_PHONE_STATE) == PERMISSION_GRANTED
    }

    @JvmStatic
    fun checkAccessReadSms(context: Context): Boolean {
        return context.checkSelfPermission(Manifest.permission.READ_SMS) == PERMISSION_GRANTED
    }

    fun checkAccessReceiveMms(context: Context): Boolean {
        return context.checkSelfPermission(Manifest.permission.RECEIVE_MMS) == PERMISSION_GRANTED
    }

    fun checkAccessReceiveSms(context: Context): Boolean {
        return context.checkSelfPermission(Manifest.permission.RECEIVE_SMS) == PERMISSION_GRANTED
    }

    fun checkAccessRecordAudio(context: Context): Boolean {
        return context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PERMISSION_GRANTED
    }

    @JvmStatic
    fun checkGpsPermissions(context: Context): Boolean {
        return checkAccessFineLocation(context)
    }

    fun checkCallsPermissions(context: Context): Boolean {
        return checkAccessReadPhoneState(context) && checkAccessCallPhone(context) && checkAccessReadCallLog(context)
    }

    fun checkTextsPermissions(context: Context): Boolean {
        return checkAccessReadContacts(context) && checkAccessReadSms(context) && checkAccessReceiveMms(context) && checkAccessReceiveSms(context)
    }

    // TODO: for unknown reasons, at some point in the past, the checkwifipermissions function
    //  included checkAccessCoarseLocation. This has been removed and tested on chris' android 6.0
    //  phone and the nexus 7 tablet and does not appear to be necessary.
    // TODO: We may need to re-enable this function because course location is required for wifi on
    //  Google Pixel phone as of Android 8.1.0

// old function call, probably can be cleared out.
//     public static boolean checkWifiPermissions( Context context ) { return ( checkAccessWifiState(context) && checkAccessNetworkState(context) && checkAccessCoarseLocation(context) ) ; }
    @JvmStatic
    fun checkWifiPermissions(context: Context): Boolean {
        return checkAccessWifiState(context) && checkAccessNetworkState(context)
    }

    @JvmStatic
    fun checkBluetoothPermissions(context: Context): Boolean {
        return checkAccessBluetooth(context) && checkAccessBluetoothAdmin(context)
    }

    fun confirmGps(context: Context): Boolean {
        return PersistentData.getGpsEnabled() && checkGpsPermissions(context)
    }

    @JvmStatic
    fun confirmCalls(context: Context): Boolean {
        return PersistentData.getCallsEnabled() && checkCallsPermissions(context)
    }

    @JvmStatic
    fun confirmTexts(context: Context): Boolean {
        return PersistentData.getTextsEnabled() && checkTextsPermissions(context)
    }

    fun confirmWifi(context: Context): Boolean {
        return PersistentData.getWifiEnabled() && checkWifiPermissions(context) && checkAccessFineLocation(context) && checkAccessCoarseLocation(context)
    }

    @JvmStatic
    fun confirmBluetooth(context: Context): Boolean {
        return PersistentData.getBluetoothEnabled() && checkBluetoothPermissions(context)
    }

    @JvmStatic
    fun confirmAmbientAudioCollection(context: Context): Boolean {
        return PersistentData.getAmbientAudioEnabled() && checkAccessRecordAudio(context)
    }

    @JvmStatic
    fun getNextPermission(context: Context, includeRecording: Boolean): String? {
        if (PersistentData.getGpsEnabled()) {
            if (!checkAccessFineLocation(context)) return Manifest.permission.ACCESS_FINE_LOCATION
        }
        if (PersistentData.getWifiEnabled()) {
            if (!checkAccessWifiState(context)) return Manifest.permission.ACCESS_WIFI_STATE
            if (!checkAccessNetworkState(context)) return Manifest.permission.ACCESS_NETWORK_STATE
            if (!checkAccessCoarseLocation(context)) return Manifest.permission.ACCESS_COARSE_LOCATION
            if (!checkAccessFineLocation(context)) return Manifest.permission.ACCESS_FINE_LOCATION
        }
        if (PersistentData.getBluetoothEnabled()) {
            if (!checkAccessBluetooth(context)) return Manifest.permission.BLUETOOTH
            if (!checkAccessBluetoothAdmin(context)) return Manifest.permission.BLUETOOTH_ADMIN
        }
        if (PersistentData.getCallsEnabled() && BuildConfig.READ_SMS_AND_PHONE_CALL_STATS) {
            if (!checkAccessReadPhoneState(context)) return Manifest.permission.READ_PHONE_STATE
            if (!checkAccessReadCallLog(context)) return Manifest.permission.READ_CALL_LOG
        }
        if (PersistentData.getTextsEnabled() && BuildConfig.READ_SMS_AND_PHONE_CALL_STATS) {
            if (!checkAccessReadContacts(context)) return Manifest.permission.READ_CONTACTS
            if (!checkAccessReadSms(context)) return Manifest.permission.READ_SMS
            if (!checkAccessReceiveMms(context)) return Manifest.permission.RECEIVE_MMS
            if (!checkAccessReceiveSms(context)) return Manifest.permission.RECEIVE_SMS
        }
        if (includeRecording || PersistentData.getAmbientAudioEnabled()) {
            if (!checkAccessRecordAudio(context)) return Manifest.permission.RECORD_AUDIO
        }

        //The phone call permission is invariant, it is required for all studies in order for the
        // call clinician functionality to work
        if (!checkAccessCallPhone(context)) return Manifest.permission.CALL_PHONE
        if (Build.VERSION.SDK_INT >= 23) {
            val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            if (!pm.isIgnoringBatteryOptimizations(context.packageName)) {
                return POWER_EXCEPTION_PERMISSION
            }
        }
        return null
    }
}