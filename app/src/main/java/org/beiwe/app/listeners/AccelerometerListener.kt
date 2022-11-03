package org.beiwe.app.listeners

import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import org.beiwe.app.DeviceInfo
import org.beiwe.app.print
import org.beiwe.app.storage.PersistentData
import org.beiwe.app.storage.TextFileManager

class AccelerometerListener(private val appContext: Context) : SensorEventListener {
    companion object {
        @JvmField
        var header = "timestamp,accuracy,x,y,z"
    }

    private var accelSensorManager: SensorManager? = null
    private var accelSensor: Sensor? = null
    private var enabled: Boolean = false
    private var accuracy ="unknown"


    @JvmField
    var exists: Boolean = appContext.packageManager.hasSystemFeature(PackageManager.FEATURE_SENSOR_ACCELEROMETER)

    /**Listens for accelerometer updates.  NOT activated on instantiation. Use the turn_on()
     * function to log any accelerometer updates to the accelerometer log. */
    init {
        if (exists) {
            accelSensorManager = appContext.getSystemService(Context.SENSOR_SERVICE) as SensorManager
            if (accelSensorManager == null) {
                Log.e("Accelerometer Problems", "accelSensorManager does not exist? (1)")
                TextFileManager.getDebugLogFile().writeEncrypted("accelSensorManager does not exist? (1)")
                exists = false
            }

            accelSensor = accelSensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
            if (accelSensor == null) {
                Log.e("Accelerometer Problems", "accelSensor does not exist? (2)")
                TextFileManager.getDebugLogFile().writeEncrypted("accelSensor does not exist? (2)")
                exists = false
            }
        }
    }

    @Synchronized
    fun turn_on() {
        if (accelSensorManager == null)
            return

        // 1 / frequency equals the period in seconds, times one million gets period in microseconds
        val delay_period_microseconds = (1.0 / PersistentData.getAccelerometerFrequency().toDouble() * 1000000).toInt()
//        print("starting accelerometer with delay of $delay_period_microseconds")
//        print("accelerometer frequency was " + PersistentData.getAccelerometerFrequency())

        if (!accelSensorManager!!.registerListener(this, accelSensor, delay_period_microseconds)) {
            Log.e("Accelerometer", "Accelerometer is broken")
            TextFileManager.getDebugLogFile().writeEncrypted("Trying to start Accelerometer session, device cannot find accelerometer.")
        } else {
            enabled = true
        }
    }

    @Synchronized
    fun turn_off() {
        accelSensorManager?.unregisterListener(this)
        enabled = false
    }

    /** Update the accuracy, synchronized so very closely timed trigger events do not overlap.
     * (only triggered by the system.)  */
    @Synchronized
    override fun onAccuracyChanged(arg0: Sensor, arg1: Int) {
        accuracy = arg1.toString()
    }

    /** On receipt of a sensor change, record it.  Include accuracy.
     * (only ever triggered by the system.)  */
    @Synchronized
    override fun onSensorChanged(arg0: SensorEvent) {
        // we record the system boot time once and use that as a reference.
        val javaTimeCode = DeviceInfo.boot_time + (arg0.timestamp / 1000000)
        val values = arg0.values
        val value0 = String.format("%.16f", values[0])
        val value1 = String.format("%.16f", values[1])
        val value2 = String.format("%.16f", values[2])
        TextFileManager.getAccelFile().writeEncrypted("$javaTimeCode,$accuracy,$value0,$value1,$value2")
    }
}