package org.beiwe.app.listeners

import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import org.beiwe.app.DeviceInfo
import org.beiwe.app.storage.PersistentData
import org.beiwe.app.storage.TextFileManager

class GyroscopeListener(private val appContext: Context) : SensorEventListener {
    companion object {
        @JvmField
        var header = "timestamp,accuracy,x,y,z"
    }

    private var gyroSensorManager: SensorManager? = null
    private var gyroSensor: Sensor? = null
    private var enabled: Boolean = false
    private var accuracy ="unknown"

    @JvmField
    var exists: Boolean = appContext.packageManager.hasSystemFeature(PackageManager.FEATURE_SENSOR_GYROSCOPE)

    /**Listens for gyroscope updates.  NOT activated on instantiation. Use the turn_on() function
     * to log any gyroscope updates to the gyroscope log. */
    init {
        if (exists) {
            // logic tests two layers of nullability
            gyroSensorManager = appContext.getSystemService(Context.SENSOR_SERVICE) as SensorManager
            if (gyroSensorManager == null) {
                Log.e("Gyroscope Problems", "gyroSensorManager does not exist? (1)")
                TextFileManager.getDebugLogFile().writeEncrypted("gyroSensorManager does not exist? (1)")
                exists = false
            }

            gyroSensor = gyroSensorManager?.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
            if (gyroSensor == null) {
                Log.e("Gyroscope Problems", "gyroSensor does not exist? (2)")
                TextFileManager.getDebugLogFile().writeEncrypted("gyroSensor does not exist? (2)")
                exists = false
            }
        }
    }

    @Synchronized
    fun turn_on() {
        if (gyroSensorManager == null)
            return

        // 1 / frequency equals the period in seconds, times one million gets period in microseconds
        val delay_period_microseconds = ((1.0 / PersistentData.getGyroscopeFrequency().toDouble()) * 1000000).toInt()
//        print("starting gyro with delay of $delay_period_microseconds")
//        print("gyro frequency was " + PersistentData.getGyroscopeFrequency())

        if (!gyroSensorManager!!.registerListener(this, gyroSensor, delay_period_microseconds)) {
            Log.e("Gyroscope", "Gyroscope is broken")
            TextFileManager.getDebugLogFile().writeEncrypted("Trying to start gyroscope session, device cannot find gyroscope.")
        } else
            enabled = true
    }

    @Synchronized
    fun turn_off() {
        gyroSensorManager?.unregisterListener(this)
        enabled = false
    }

    /** Update the accuracy, synchronized so very closely timed trigger events do not overlap.
     * (only triggered by the system.)  */
    @Synchronized
    override fun onAccuracyChanged(arg0: Sensor, arg1: Int) {
        accuracy = arg1.toString()
    }

    // private var prior_timecode: Long = 0
    /** On receipt of a sensor change, record it.  Include accuracy.
     * (only ever triggered by the system.)  */
    @Synchronized
    override fun onSensorChanged(arg0: SensorEvent) {
        // we record the system boot time once and use that as a reference.
        val javaTimeCode = DeviceInfo.boot_time + (arg0.timestamp / 1000000)
        // print("gyro milliseconds since prior: ${javaTimeCode-prior_timecode})")
        // prior_timecode = javaTimeCode
        val values = arg0.values
        val value0 = String.format("%.16f", values[0])
        val value1 = String.format("%.16f", values[1])
        val value2 = String.format("%.16f", values[2])
        TextFileManager.getGyroFile().writeEncrypted("$javaTimeCode,$accuracy,$value0,$value1,$value2")
    }
}