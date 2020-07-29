package org.beiwe.app.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import kotlinx.android.synthetic.main.activity_debug_interface.*
import org.beiwe.app.*
import org.beiwe.app.Timer
import org.beiwe.app.networking.PostRequest
import org.beiwe.app.networking.SurveyDownloader
import org.beiwe.app.session.SessionActivity
import org.beiwe.app.storage.EncryptionEngine
import org.beiwe.app.storage.PersistentData
import org.beiwe.app.storage.TextFileManager
import org.beiwe.app.survey.JsonSkipLogic
import org.beiwe.app.ui.user.MainMenuActivity
import org.beiwe.app.ui.utils.SurveyNotifications
import org.json.JSONArray
import org.json.JSONException
import java.security.spec.InvalidKeySpecException
import java.util.*

class DebugInterfaceActivity : SessionActivity() {
    //extends a session activity.
    var appContext: Context? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_debug_interface)
        appContext = this.applicationContext
        if (BuildConfig.APP_IS_DEV) {   debugtexttwenty.visibility = View.VISIBLE
            button.visibility = View.VISIBLE
            buttonPrintSurveys.visibility = View.VISIBLE
            buttonClearInternalLog.visibility = View.VISIBLE
            buttonDeleteEverything.visibility = View.VISIBLE
            buttonListFiles.visibility = View.VISIBLE
            buttonStartTimer.visibility = View.VISIBLE
            buttonLogDataToggles.visibility = View.VISIBLE
            buttonAlarmStates.visibility = View.VISIBLE
            buttonFeaturesEnabled.visibility = View.VISIBLE
            buttonFeaturesPermissable.visibility = View.VISIBLE
            sendTestNotification.visibility = View.VISIBLE
            sendSurveyNotification.visibility = View.VISIBLE
            // These are specialized and only need to be enabled when testing app resurrection.
            // buttonCrashUi.visibility = View.VISIBLE
            // buttonCrashBackground.visibility = View.VISIBLE
            // buttonCrashBackgroundInFive.visibility = View.VISIBLE
            // buttonTestManualErrorReport.visibility = View.VISIBLE
            // stopBackgroundService.visibility = View.VISIBLE
            // buttonEnterANRUI.visibility = View.VISIBLE
            // buttonEnterANRBackground.visibility = View.VISIBLE

            // These are seriously old and don't need to be visible under almost all circumstances.
            // testEncryption.visibility View.VISIBLE
            // buttonPrintInternalLog.visibility View.VISIBLE
            // buttonGetKeyFile.visibility View.VISIBLE
        }
    }

    //Intent triggers caught in BackgroundService
    fun accelerometerOn(view: View?) {
        appContext!!.sendBroadcast(Timer.accelerometerOnIntent)
    }

    fun accelerometerOff(view: View?) {
        appContext!!.sendBroadcast(Timer.accelerometerOffIntent)
    }

    fun gyroscopeOn(view: View?) {
        appContext!!.sendBroadcast(Timer.gyroscopeOnIntent)
    }

    fun gyroscopeOff(view: View?) {
        appContext!!.sendBroadcast(Timer.gyroscopeOffIntent)
    }

    fun gpsOn(view: View?) {
        appContext!!.sendBroadcast(Timer.gpsOnIntent)
    }

    fun gpsOff(view: View?) {
        appContext!!.sendBroadcast(Timer.gpsOffIntent)
    }

    fun scanWifi(view: View?) {
        appContext!!.sendBroadcast(Timer.wifiLogIntent)
    }

    fun bluetoothButtonStart(view: View?) {
        appContext!!.sendBroadcast(Timer.bluetoothOnIntent)
    }

    fun bluetoothButtonStop(view: View?) {
        appContext!!.sendBroadcast(Timer.bluetoothOffIntent)
    }

    //raw debugging info
    fun printInternalLog(view: View?) {
        // Log.i("print log button pressed", "press.");
        val log = TextFileManager.getDebugLogFile().read()
        for (line in log.split("\n".toRegex()).toTypedArray()) {
            Log.i("log file...", line)
        }
        //		Log.i("log file encrypted", EncryptionEngine.encryptAES(log) );
    }

    fun printSurveys(view: View?) {
        val surveyIDs = PersistentData.getSurveyIds()
        Log.i("debug", surveyIDs.toString())
        for (survey_id in surveyIDs) {
            Log.i("survey", survey_id)
            Log.i("survey", PersistentData.getSurveyContent(survey_id))
        }
    }

    fun testEncrypt(view: View?) {
        Log.i("Debug..", TextFileManager.getKeyFile().read())
        val data = TextFileManager.getKeyFile().read()
        Log.i("reading keyFile:", data)

        try {
            EncryptionEngine.readKey()
        } catch (e: InvalidKeySpecException) {
            Log.e("DebugInterfaceActivity", "this is only partially implemented, unknown behavior")
            e.printStackTrace()
            throw NullPointerException("some form of encryption error, type 1")
        }

        val encrypted = try {
            EncryptionEngine.encryptRSA("ThIs Is a TeSt".toByteArray()).toString()
        } catch (e: InvalidKeySpecException) {
            Log.e("DebugInterfaceActivity", "this is only partially implemented, unknown behavior")
            e.printStackTrace()
            throw NullPointerException("some form of encryption error, type 2")
        }

        Log.i("test encrypt - length:", "" + encrypted.length)
        Log.i("test encrypt - output:", encrypted)
        Log.i("test hash:", EncryptionEngine.safeHash(encrypted))
        Log.i("test hash:", EncryptionEngine.hashMAC(encrypted))
    }

    fun logDataToggles(view: View?) {
        Log.i("Debug.DataToggles", "Accelerometer: " + PersistentData.getAccelerometerEnabled().toString() )
        Log.i("Debug.DataToggles", "Gyroscope: " + PersistentData.getGyroscopeEnabled().toString() )
        Log.i("Debug.DataToggles", "GPS: " + PersistentData.getGpsEnabled().toString() )
        Log.i("Debug.DataToggles", "Calls: " + PersistentData.getCallsEnabled().toString() )
        Log.i("Debug.DataToggles", "Texts: " + PersistentData.getTextsEnabled().toString() )
        Log.i("Debug.DataToggles", "WiFi: " + PersistentData.getWifiEnabled().toString() )
        Log.i("Debug.DataToggles", "Bluetooth: " + PersistentData.getBluetoothEnabled().toString() )
        Log.i("Debug.DataToggles", "Power State: " + PersistentData.getPowerStateEnabled().toString() )
    }

    fun getAlarmStates(view: View?) {
        val ids = PersistentData.getSurveyIds()
        for (surveyId in ids) {
            Log.i("most recent alarm state", "survey id: " + surveyId + ", " + PersistentData.getMostRecentSurveyAlarmTime(surveyId) + ", " + PersistentData.getSurveyNotificationState(surveyId))
        }
    }

    fun getEnabledFeatures(view: View?) {
        if (PersistentData.getAccelerometerEnabled())
            Log.i("features", "Accelerometer Enabled.")
        else
            Log.e("features", "Accelerometer Disabled.")

        if (PersistentData.getGyroscopeEnabled())
            Log.i("features", "Gyroscope Enabled.")
        else
            Log.e("features", "Gyroscope Disabled.")

        if (PersistentData.getGpsEnabled())
            Log.i("features", "Gps Enabled.")
        else
            Log.e("features", "Gps Disabled.")

        if (PersistentData.getCallsEnabled())
            Log.i("features", "Calls Enabled.")
        else
            Log.e("features", "Calls Disabled.")

        if (PersistentData.getTextsEnabled())
            Log.i("features", "Texts Enabled.")
        else
            Log.e("features", "Texts Disabled.")

        if (PersistentData.getWifiEnabled())
            Log.i("features", "Wifi Enabled.")
        else
            Log.e("features", "Wifi Disabled.")

        if (PersistentData.getBluetoothEnabled())
            Log.i("features", "Bluetooth Enabled.")
        else
            Log.e("features", "Bluetooth Disabled.")

        if (PersistentData.getPowerStateEnabled())
            Log.i("features", "PowerState Enabled.")
        else
            Log.e("features", "PowerState Disabled.")

    }

    fun getPermissableFeatures(view: View?) {
        if (PermissionHandler.checkAccessFineLocation(applicationContext))
            Log.i("permissions", "AccessFineLocation enabled.")
        else
            Log.e("permissions", "AccessFineLocation disabled.")

        if (PermissionHandler.checkAccessNetworkState(applicationContext))
            Log.i("permissions", "AccessNetworkState enabled.")
        else
            Log.e("permissions", "AccessNetworkState disabled.")

        if (PermissionHandler.checkAccessWifiState(applicationContext))
            Log.i("permissions", "AccessWifiState enabled.")
        else
            Log.e("permissions", "AccessWifiState disabled.")

        if (PermissionHandler.checkAccessBluetooth(applicationContext))
            Log.i("permissions", "Bluetooth enabled.")
        else
            Log.e("permissions", "Bluetooth disabled.")

        if (PermissionHandler.checkAccessBluetoothAdmin(applicationContext))
            Log.i("permissions", "BluetoothAdmin enabled.")
        else
            Log.e("permissions", "BluetoothAdmin disabled.")

        if (PermissionHandler.checkAccessCallPhone(applicationContext))
            Log.i("permissions", "CallPhone enabled.")
        else
            Log.e("permissions", "CallPhone disabled.")

        if (PermissionHandler.checkAccessReadCallLog(applicationContext))
            Log.i("permissions", "ReadCallLog enabled.")
        else
            Log.e("permissions", "ReadCallLog disabled.")

        if (PermissionHandler.checkAccessReadContacts(applicationContext))
            Log.i("permissions", "ReadContacts enabled.")
        else
            Log.e("permissions", "ReadContacts disabled.")

        if (PermissionHandler.checkAccessReadPhoneState(applicationContext))
            Log.i("permissions", "ReadPhoneState enabled.")
        else
            Log.e("permissions", "ReadPhoneState disabled.")

        if (PermissionHandler.checkAccessReadSms(applicationContext))
            Log.i("permissions", "ReadSms enabled.")
        else
            Log.e("permissions", "ReadSms disabled.")

        if (PermissionHandler.checkAccessReceiveMms(applicationContext))
            Log.i("permissions", "ReceiveMms enabled.")
        else
            Log.e("permissions", "ReceiveMms disabled.")

        if (PermissionHandler.checkAccessReceiveSms(applicationContext))
            Log.i("permissions", "ReceiveSms enabled.")
        else
            Log.e("permissions", "ReceiveSms disabled.")

        if (PermissionHandler.checkAccessRecordAudio(applicationContext))
            Log.i("permissions", "RecordAudio enabled.")
        else
            Log.e("permissions", "RecordAudio disabled.")
    }

    fun clearInternalLog(view: View?) {
        TextFileManager.getDebugLogFile().deleteSafely()
    }

    fun getKeyFile(view: View?) {
        Log.i("DEBUG", "key file data: " + TextFileManager.getKeyFile().read())
    }

    //network operations
    fun uploadDataFiles(view: View?) {
        PostRequest.uploadAllFiles()
    }

    fun runSurveyDownload(view: View?) {
        SurveyDownloader.downloadSurveys(applicationContext)
    }

    fun buttonStartTimer(view: View?) {
        backgroundService.startTimers()
    }

    //file operations
    fun makeNewFiles(view: View?) {
        TextFileManager.makeNewFilesForEverything()
    }

    fun deleteEverything(view: View?) {
        Log.i("Delete Everything button pressed", "poke.")
        val files = TextFileManager.getAllFiles()
        Arrays.sort(files)
        for (file in files) {
            Log.i("files...", file)
        }
        TextFileManager.deleteEverything()
    }

    fun listFiles(view: View?) {
        Log.w("files...", "UPLOADABLE FILES")
        var files = TextFileManager.getAllUploadableFiles()
        Arrays.sort(files)

        for (file in files)
            Log.i("files...", file)

        Log.w("files...", "ALL FILES")
        files = TextFileManager.getAllFiles()
        Arrays.sort(files)

        for (file in files)
            Log.i("files...", file)
    }

    //ui operations
    fun loadMainMenu(view: View?) {
        startActivity(Intent(appContext, MainMenuActivity::class.java))
    }

    fun popSurveyNotifications(view: View?) {
        for (surveyId in PersistentData.getSurveyIds()) {
            SurveyNotifications.displaySurveyNotification(appContext, surveyId)
        }
    }

    //crash operations (No, really, we actually need this.)
    fun crashUi(view: View?) {
        throw NullPointerException("oops, you bwoke it.")
    }

    fun crashBackground(view: View?) {
        BackgroundService.timer.setupExactSingleAlarm(0.toLong(), Intent("crashBeiwe"))
    }

    fun crashBackgroundInFive(view: View?) {
        BackgroundService.timer.setupExactSingleAlarm(5000.toLong(), Intent("crashBeiwe"))
    }

    fun enterANRUI(view: View?) {
        try {
            Thread.sleep(100000)
        } catch (ie: InterruptedException) {
            ie.printStackTrace()
        }
    }

    fun enterANRBackground(view: View?) {
        BackgroundService.timer.setupExactSingleAlarm(0.toLong(), Intent("enterANR"))
    }

    fun stopBackgroundService(view: View?) {
        backgroundService.stop()
    }

    fun testManualErrorReport(view: View?) {
        try {
            throw NullPointerException("this is a test null pointer exception from the debug interface")
        } catch (e: Exception) {
            CrashHandler.writeCrashlog(e, applicationContext)
        }
    }

    //runs tests on the json logic parser
    fun testJsonLogicParser(view: View?) {
        val JsonQuestionsListString = "[{\"question_text\": \"In the last 7 days, how OFTEN did you EAT BROCCOLI?\", \"question_type\": \"radio_button\", \"answers\": [{\"text\": \"Never\"}, {\"text\": \"Rarely\"}, {\"text\": \"Occasionally\"}, {\"text\": \"Frequently\"}, {\"text\": \"Almost Constantly\"}], \"question_id\": \"6695d6c4-916b-4225-8688-89b6089a24d1\"}, {\"display_if\": {\">\": [\"6695d6c4-916b-4225-8688-89b6089a24d1\", 0]}, \"question_text\": \"In the last 7 days, what was the SEVERITY of your CRAVING FOR BROCCOLI?\", \"question_type\": \"radio_button\", \"answers\": [{\"text\": \"None\"}, {\"text\": \"Mild\"}, {\"text\": \"Moderate\"}, {\"text\": \"Severe\"}, {\"text\": \"Very Severe\"}], \"question_id\": \"41d54793-dc4d-48d9-f370-4329a7bc6960\"}, {\"display_if\": {\"and\": [{\">\": [\"6695d6c4-916b-4225-8688-89b6089a24d1\", 0]}, {\">\": [\"41d54793-dc4d-48d9-f370-4329a7bc6960\", 0]}]}, \"question_text\": \"In the last 7 days, how much did your CRAVING FOR BROCCOLI INTERFERE with your usual or daily activities, (e.g. eating cauliflower)?\", \"question_type\": \"radio_button\", \"answers\": [{\"text\": \"Not at all\"}, {\"text\": \"A little bit\"}, {\"text\": \"Somewhat\"}, {\"text\": \"Quite a bit\"}, {\"text\": \"Very much\"}], \"question_id\": \"5cfa06ad-d907-4ba7-a66a-d68ea3c89fba\"}, {\"display_if\": {\"or\": [{\"and\": [{\"<=\": [\"6695d6c4-916b-4225-8688-89b6089a24d1\", 3]}, {\"==\": [\"41d54793-dc4d-48d9-f370-4329a7bc6960\", 2]}, {\"<\": [\"5cfa06ad-d907-4ba7-a66a-d68ea3c89fba\", 3]}]}, {\"and\": [{\"<=\": [\"6695d6c4-916b-4225-8688-89b6089a24d1\", 3]}, {\"<\": [\"41d54793-dc4d-48d9-f370-4329a7bc6960\", 3]}, {\"==\": [\"5cfa06ad-d907-4ba7-a66a-d68ea3c89fba\", 2]}]}, {\"and\": [{\"==\": [\"6695d6c4-916b-4225-8688-89b6089a24d1\", 4]}, {\"<=\": [\"41d54793-dc4d-48d9-f370-4329a7bc6960\", 1]}, {\"<=\": [\"5cfa06ad-d907-4ba7-a66a-d68ea3c89fba\", 1]}]}]}, \"question_text\": \"While broccoli is a nutritious and healthful food, it's important to recognize that craving too much broccoli can have adverse consequences on your health.  If in a single day you find yourself eating broccoli steamed, stir-fried, and raw with a 'vegetable dip', you may be a broccoli addict.  This is an additional paragraph (following a double newline) warning you about the dangers of broccoli consumption.\", \"question_type\": \"info_text_box\", \"question_id\": \"9d7f737d-ef55-4231-e901-b3b68ca74190\"}, {\"display_if\": {\"or\": [{\"and\": [{\"==\": [\"6695d6c4-916b-4225-8688-89b6089a24d1\", 4]}, {\"or\": [{\">=\": [\"41d54793-dc4d-48d9-f370-4329a7bc6960\", 2]}, {\">=\": [\"5cfa06ad-d907-4ba7-a66a-d68ea3c89fba\", 2]}]}]}, {\"or\": [{\">=\": [\"41d54793-dc4d-48d9-f370-4329a7bc6960\", 3]}, {\">=\": [\"5cfa06ad-d907-4ba7-a66a-d68ea3c89fba\", 3]}]}]}, \"question_text\": \"OK, it sounds like your broccoli habit is getting out of hand.  Please call your clinician immediately.\", \"question_type\": \"info_text_box\", \"question_id\": \"59f05c45-df67-40ed-a299-8796118ad173\"}, {\"question_text\": \"How many pounds of broccoli per day could a woodchuck chuck if a woodchuck could chuck broccoli?\", \"text_field_type\": \"NUMERIC\", \"question_type\": \"free_response\", \"question_id\": \"9745551b-a0f8-4eec-9205-9e0154637513\"}, {\"display_if\": {\"<\": [\"9745551b-a0f8-4eec-9205-9e0154637513\", 10]}, \"question_text\": \"That seems a little low.\", \"question_type\": \"info_text_box\", \"question_id\": \"cedef218-e1ec-46d3-d8be-e30cb0b2d3aa\"}, {\"display_if\": {\"==\": [\"9745551b-a0f8-4eec-9205-9e0154637513\", 10]}, \"question_text\": \"That sounds about right.\", \"question_type\": \"info_text_box\", \"question_id\": \"64a2a19b-c3d0-4d6e-9c0d-06089fd00424\"}, {\"display_if\": {\">\": [\"9745551b-a0f8-4eec-9205-9e0154637513\", 10]}, \"question_text\": \"What?! No way- that's way too high!\", \"question_type\": \"info_text_box\", \"question_id\": \"166d74ea-af32-487c-96d6-da8d63cfd368\"}, {\"max\": \"5\", \"question_id\": \"059e2f4a-562a-498e-d5f3-f59a2b2a5a5b\", \"question_text\": \"On a scale of 1 (awful) to 5 (delicious) stars, how would you rate your dinner at Chez Broccoli Restaurant?\", \"question_type\": \"slider\", \"min\": \"1\"}, {\"display_if\": {\">=\": [\"059e2f4a-562a-498e-d5f3-f59a2b2a5a5b\", 4]}, \"question_text\": \"Wow, you are a true broccoli fan.\", \"question_type\": \"info_text_box\", \"question_id\": \"6dd9b20b-9dfc-4ec9-cd29-1b82b330b463\"}, {\"question_text\": \"THE END. This survey is over.\", \"question_type\": \"info_text_box\", \"question_id\": \"ec0173c9-ac8d-449d-d11d-1d8e596b4ec9\"}]"
        val steve: JsonSkipLogic
        val questions: JSONArray
        val runDisplayLogic = true
        try {
            questions = JSONArray(JsonQuestionsListString)
            steve = JsonSkipLogic(questions, runDisplayLogic, applicationContext)
        } catch (e: JSONException) {
            Log.e("Debug", "it dun gon wronge.")
            e.printStackTrace()
            throw NullPointerException("it done gon wronge")
        }
        var i = 0
        Log.v("debug", "" + i)
        i++
        steve.nextQuestion
        Log.v("debug", "" + i)
        i++
        steve.nextQuestion
        Log.v("debug", "" + i)
        i++
        steve.nextQuestion
        Log.v("debug", "" + i)
        i++
        steve.nextQuestion
        Log.v("debug", "" + i)
        i++
        steve.nextQuestion
        Log.v("debug", "" + i)
        i++
        steve.nextQuestion
        Log.v("debug", "" + i)
        i++
        steve.nextQuestion
        Log.v("debug", "" + i)
        i++
        steve.nextQuestion
        Log.v("debug", "" + i)
        i++
        steve.nextQuestion
        Log.v("debug", "" + i)
        i++
        steve.nextQuestion
        Log.v("debug", "" + i)
        i++
        steve.nextQuestion
        Log.v("debug", "" + i)
        i++
        steve.nextQuestion
        Log.v("debug", "" + i)
        i++
        steve.nextQuestion
        Log.v("debug", "" + i)
        i++
        steve.nextQuestion
        Log.v("debug", "" + i)
        i++
        steve.nextQuestion
        Log.v("debug", "" + i)
        i++
        steve.nextQuestion
        Log.v("debug", "" + i)
        i++
        steve.nextQuestion
        Log.v("debug", "" + i)
        i++
        steve.nextQuestion
        Log.v("debug", "" + i)
        i++
        steve.nextQuestion
        Log.v("debug", "" + i)
        i++
        steve.nextQuestion
        Log.v("debug", "" + i)
        i++
        steve.nextQuestion
        Log.v("debug", "" + i)
        i++
        steve.nextQuestion
        Log.v("debug", "" + i)
        i++
        steve.nextQuestion
        Log.v("debug", "" + i)
    }

    fun sendTestNotification(view: View?) {
        PostRequest.sendTestNotification()
    }

    fun sendSurveyNotification(view: View?) {
        PostRequest.sendSurveyNotification()
    }

    fun clearNotifications(view: View?) {
        for (surveyId in PersistentData.getSurveyIds())
            SurveyNotifications.dismissNotification(appContext, surveyId)
    }
}