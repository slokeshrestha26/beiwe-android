package org.beiwe.app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import org.beiwe.app.DeviceInfo
import org.beiwe.app.storage.PersistentData
import org.json.JSONObject
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

        // access background location and post notifications are only available on newer versions of
        // android. Check must be identical to the one in their respective check function or the app will crash.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            permissionMessages[Manifest.permission.ACCESS_BACKGROUND_LOCATION] = R.string.permission_access_background_location
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            permissionMessages[Manifest.permission.POST_NOTIFICATIONS] = R.string.permission_notifications

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissionMessages[Manifest.permission.BLUETOOTH_CONNECT] = R.string.permission_bluetooth
            permissionMessages[Manifest.permission.BLUETOOTH_SCAN] = R.string.permission_bluetooth_admin
        }
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
	 *  Bluetooth Scan and Connect - Android 12+
	 *  PostNotifications - Android 12+ (10+?)
	 *  Background Location - Android 12+
	 *
	 * We check for microphone recording as a special condition on the audio recording screen,
	 *   or when background audio is enabled */

    /* Simple permission checks */

    @JvmStatic
    fun checkAccessCoarseLocation(context: Context): Boolean {
        return context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PERMISSION_GRANTED
    }

    @JvmStatic
    fun checkAccessFineLocation(context: Context): Boolean {
        return context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PERMISSION_GRANTED
    }

    @JvmStatic
    fun checkAccessBackgroundLocation(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            context.checkSelfPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PERMISSION_GRANTED
        else
            return true
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

    fun checkAccessBluetoothConnect(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        return context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PERMISSION_GRANTED
    }

    fun checkAccessBluetoothScan(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        return context.checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) == PERMISSION_GRANTED
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

    fun checkAccessNotifications(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PERMISSION_GRANTED
        else
            true
    }

    /* Complex permission checks */

    @JvmStatic
    fun checkGpsPermissions(context: Context): Boolean {
        return checkAccessFineLocation(context) // we don't want coarse, fine means GPS.
    }

    fun checkCallsPermissions(context: Context): Boolean {
        return checkAccessReadPhoneState(context) && checkAccessCallPhone(context) && checkAccessReadCallLog(context)
    }

    fun checkTextsPermissions(context: Context): Boolean {
        return checkAccessReadContacts(context) && checkAccessReadSms(context) && checkAccessReceiveMms(context) && checkAccessReceiveSms(context)
    }

    @JvmStatic
    fun checkWifiPermissions(context: Context): Boolean {
        return checkAccessWifiState(context) && checkAccessNetworkState(context)
    }

    @JvmStatic
    fun checkBluetoothPermissions(context: Context): Boolean {
        // android versions below 12 use permission.BLUETOOTH and permission.BLUETOOTH_ADMIN,
        // 12+ uses permission.BLUETOOTH_CONNECT and permission.BLUETOOTH_SCAN
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return checkAccessBluetoothConnect(context) && checkAccessBluetoothScan(context)
        }
        return checkAccessBluetooth(context) && checkAccessBluetoothAdmin(context)
    }

    @JvmStatic
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

    @JvmStatic
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
        // os version check handled inside checkAccessNotifications
        if (!checkAccessNotifications(context)) return Manifest.permission.POST_NOTIFICATIONS

        if (PersistentData.getGpsEnabled()) {
            if (!checkAccessFineLocation(context)) return Manifest.permission.ACCESS_FINE_LOCATION
            // os version check handled inside checkAccessBackgroundLocation
            if (!checkAccessBackgroundLocation(context)) return Manifest.permission.ACCESS_BACKGROUND_LOCATION
        }
        if (PersistentData.getWifiEnabled()) {
            if (!checkAccessWifiState(context)) return Manifest.permission.ACCESS_WIFI_STATE
            if (!checkAccessNetworkState(context)) return Manifest.permission.ACCESS_NETWORK_STATE
            if (!checkAccessCoarseLocation(context)) return Manifest.permission.ACCESS_COARSE_LOCATION
            if (!checkAccessFineLocation(context)) return Manifest.permission.ACCESS_FINE_LOCATION
        }
        if (PersistentData.getBluetoothEnabled()) {
            // android versions below 12 use permission.BLUETOOTH and permission.BLUETOOTH_ADMIN,
            // 12+ uses permission.BLUETOOTH_CONNECT and permission.BLUETOOTH_SCAN
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (!checkAccessBluetoothConnect(context)) return Manifest.permission.BLUETOOTH_CONNECT
                if (!checkAccessBluetoothScan(context)) return Manifest.permission.BLUETOOTH_SCAN
            } else {
                if (!checkAccessBluetooth(context)) return Manifest.permission.BLUETOOTH
                if (!checkAccessBluetoothAdmin(context)) return Manifest.permission.BLUETOOTH_ADMIN
            }
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

        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        if (!pm.isIgnoringBatteryOptimizations(context.packageName)) {
            return POWER_EXCEPTION_PERMISSION
        }

        return null
    }

    // https://stackoverflow.com/questions/65479363/android-adaptive-battery-setting-detection
    fun isAdaptiveBatteryEnabled(ctx: Context): Boolean {
        val intValue = Settings.Global.getInt(
                ctx.contentResolver,
                "adaptive_battery_management_enabled",
                -1
        )
        return intValue == 1
    }


    // This function is probably sitting in the wrong file, but its so broad, and we need to have a
    // it as a json object for serialization, that there isn't really anywhere good to stick it.
    @JvmStatic
    fun getDeviceStatusReport(context: Context): String {
        val permissions = JSONObject()

        // get the time, convert to calendar object, get local time, insert timezone.
        val time_int = System.currentTimeMillis()
        val time_locale = Date(System.currentTimeMillis()).toLocaleString()
        permissions.put("time_int", time_int)
        permissions.put("time_locale", time_locale + " " + DeviceInfo.timeZoneInfo())

        // the normal permissions
        permissions.put("permission_access_background_location", checkAccessBackgroundLocation(context))
        permissions.put("permission_access_coarse_location", checkAccessCoarseLocation(context))
        permissions.put("permission_access_fine_location", checkAccessFineLocation(context))
        permissions.put("permission_access_network_state", checkAccessNetworkState(context))
        permissions.put("permission_access_wifi_state", checkAccessWifiState(context))
        permissions.put("permission_bluetooth", checkAccessBluetooth(context))
        permissions.put("permission_bluetooth_admin", checkAccessBluetoothAdmin(context))
        permissions.put("permission_bluetooth_connect", checkAccessBluetoothConnect(context))
        permissions.put("permission_bluetooth_scan", checkAccessBluetoothScan(context))
        permissions.put("permission_call_phone", checkAccessCallPhone(context))
        permissions.put("permission_post_notifications", checkAccessNotifications(context))
        permissions.put("permission_read_call_log", checkAccessReadCallLog(context))
        permissions.put("permission_read_contacts", checkAccessReadContacts(context))
        permissions.put("permission_read_phone_state (receive_boot_completed?)", checkAccessReadPhoneState(context))
        permissions.put("permission_read_sms", checkAccessReadSms(context))
        permissions.put("permission_receive_boot_completed", checkAccessReadPhoneState(context))
        permissions.put("permission_receive_mms", checkAccessReceiveMms(context))
        permissions.put("permission_receive_sms", checkAccessReceiveSms(context))
        permissions.put("permission_record_audio", checkAccessRecordAudio(context))

        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        permissions.put("power_battery_discharge_prediction", pm.batteryDischargePrediction)
        permissions.put("power_current_thermal_status", pm.currentThermalStatus)
        permissions.put("power_is_adaptive_battery_management_enabled", isAdaptiveBatteryEnabled(context))
        permissions.put("power_is_battery_discharge_prediction_personalized", pm.isBatteryDischargePredictionPersonalized)
        permissions.put("power_is_device_idle_mode", pm.isDeviceIdleMode)
        permissions.put("power_is_ignoring_battery_optimizations", pm.isIgnoringBatteryOptimizations(context.packageName))
        permissions.put("power_is_interactive", pm.isInteractive)
        permissions.put("power_is_power_save_mode", pm.isPowerSaveMode)
        permissions.put("power_is_sustained_performance_mode_supported", pm.isSustainedPerformanceModeSupported)
        permissions.put("power_location_power_save_mode", pm.locationPowerSaveMode)

        // storage details
        permissions.put("storage_free_space", DeviceInfo.freeSpace())
        return permissions.toString()

    }
}