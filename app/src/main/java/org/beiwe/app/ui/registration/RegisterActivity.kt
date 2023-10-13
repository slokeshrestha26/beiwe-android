package org.beiwe.app.ui.registration

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.telephony.TelephonyManager
import android.view.View
import android.widget.EditText
import android.widget.TextView
import org.beiwe.app.BuildConfig
import org.beiwe.app.DeviceInfo.androidVersion
import org.beiwe.app.DeviceInfo.beiweVersion
import org.beiwe.app.DeviceInfo.brand
import org.beiwe.app.DeviceInfo.getAndroidID
import org.beiwe.app.DeviceInfo.hardwareId
import org.beiwe.app.DeviceInfo.initialize
import org.beiwe.app.DeviceInfo.manufacturer
import org.beiwe.app.DeviceInfo.model
import org.beiwe.app.DeviceInfo.product
import org.beiwe.app.PermissionHandler
import org.beiwe.app.PermissionHandler.checkAccessReadPhoneNumbers
import org.beiwe.app.R
import org.beiwe.app.RunningBackgroundServiceActivity
import org.beiwe.app.networking.HTTPUIAsync
import org.beiwe.app.networking.PostRequest
import org.beiwe.app.printe
import org.beiwe.app.storage.EncryptionEngine
import org.beiwe.app.storage.PersistentData
import org.beiwe.app.survey.TextFieldKeyboard
import org.beiwe.app.ui.utils.AlertsManager

const val PHONE_REQUIRED = BuildConfig.READ_SMS_AND_PHONE_CALL_STATS
val READ_PHONE_NUMBERS_PERMISSION = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
    Manifest.permission.READ_PHONE_NUMBERS
} else {
    Manifest.permission.READ_PHONE_STATE
}

/**Activity used to log a user in to the application for the first time. This activity should only
 * be called on ONCE, as once the user is logged in, data is saved on the phone.
 * @author Dor Samet, Eli Jones, Josh Zagorsky */

@SuppressLint("ShowToast")
class RegisterActivity : RunningBackgroundServiceActivity() {
    private var serverUrlInput: EditText? = null
    private var userIdInput: EditText? = null
    private var tempPasswordInput: EditText? = null
    private var newPasswordInput: EditText? = null
    private var confirmNewPasswordInput: EditText? = null
    var handler: Handler? = null
    var self: RegisterActivity? = null  // what on earth is this doing

    /** Users will go into this activity first to register information on the phone and on the server. */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)
        handler = Handler(Looper.getMainLooper())
        self = this
        if (!BuildConfig.CUSTOMIZABLE_SERVER_URL) {
            val serverUrlCaption = findViewById<View>(R.id.serverUrlCaption) as TextView
            val serverUrlInput = findViewById<View>(R.id.serverUrlInput) as EditText
            serverUrlCaption.visibility = View.GONE
            serverUrlInput.visibility = View.GONE
        }
        serverUrlInput = findViewById<View>(R.id.serverUrlInput) as EditText
        userIdInput = findViewById<View>(R.id.registerUserIdInput) as EditText
        tempPasswordInput = findViewById<View>(R.id.registerTempPasswordInput) as EditText
        newPasswordInput = findViewById<View>(R.id.registerNewPasswordInput) as EditText
        confirmNewPasswordInput = findViewById<View>(R.id.registerConfirmNewPasswordInput) as EditText

        val textFieldKeyboard = TextFieldKeyboard(applicationContext)
        textFieldKeyboard.makeKeyboardBehave(serverUrlInput)
        textFieldKeyboard.makeKeyboardBehave(userIdInput)
        textFieldKeyboard.makeKeyboardBehave(tempPasswordInput)
        textFieldKeyboard.makeKeyboardBehave(newPasswordInput)
        textFieldKeyboard.makeKeyboardBehave(confirmNewPasswordInput)
        newPasswordInput!!.hint = String.format(
            getString(R.string.registration_replacement_password_hint), PersistentData.minPasswordLength()
        )
        confirmNewPasswordInput!!.hint = String.format(
            getString(R.string.registration_replacement_password_hint), PersistentData.minPasswordLength()
        )
    }

    /** Registration sequence begins here, called when the submit button is pressed.  */
    @Synchronized
    fun registerButtonPressed(view: View?) {
        val serverUrl = serverUrlInput!!.text.toString()
        val userID = userIdInput!!.text.toString().replace("\\s+".toRegex(), "")
        val tempPassword = tempPasswordInput!!.text.toString()
        val newPassword = newPasswordInput!!.text.toString()
        val confirmNewPassword = confirmNewPasswordInput!!.text.toString()
        if (serverUrl.length == 0 && BuildConfig.CUSTOMIZABLE_SERVER_URL) {
            // If the study URL is empty, alert the user
            AlertsManager.showAlert(getString(
                R.string.url_too_short), getString(R.string.couldnt_register), this)
        } else if (userID.length == 0) {
            // If the user id length is too short, alert the user
            AlertsManager.showAlert(getString(
                R.string.invalid_user_id), getString(R.string.couldnt_register), this)
        } else if (tempPassword.length < 1) {
            // If the temporary registration password isn't filled in
            AlertsManager.showAlert(getString(
                R.string.empty_temp_password), getString(R.string.couldnt_register), this)
        } else if (!PersistentData.passwordMeetsRequirements(newPassword)) {
            // If the new password has too few characters
            val alertMessage = String.format(getString(
                R.string.password_too_short), PersistentData.minPasswordLength())
            AlertsManager.showAlert(alertMessage, getString(R.string.couldnt_register), this)
        } else if (newPassword != confirmNewPassword) {
            // If the new password doesn't match the confirm new password
            AlertsManager.showAlert(getString(
                R.string.password_mismatch), getString(R.string.couldnt_register), this)
        } else {
            if (BuildConfig.CUSTOMIZABLE_SERVER_URL)
                PersistentData.setServerUrl(serverUrl)
            PersistentData.setLoginCredentials(userID, tempPassword)

            // Log.d("RegisterActivity", "trying \"" + LoginManager.getPatientID() +
            //       "\" with password \"" + LoginManager.getPassword() + "\"" );
            tryToRegisterWithTheServer(
                this,
                PostRequest.addWebsitePrefix(applicationContext.getString(R.string.register_url)),
                newPassword
            )
        }
    }

    /**This is the fuction that requires phone permissions.  We need to supply a (unique) identifier
     * for phone numbers to the registration arguments.*/
    private val phoneNumber: String
        get() {
            val phoneNumber = try {
                // If the participant accepts the phone permission checks, then this command will succeed
                (this.getSystemService(TELEPHONY_SERVICE) as TelephonyManager).line1Number
            } catch (e: SecurityException) {
                printe("RegisterActivity", "SecurityException in phoneNumber getter")
                ""
            }
            // apparently we have a null case to handle, kotlin disagrees, please keep this if statement anyway
            return if (phoneNumber == null)
                EncryptionEngine.hashPhoneNumber("")
            else
                EncryptionEngine.hashPhoneNumber(phoneNumber)
        }

    // duplicate of the function from RunningBackgroundServiceActivity (this isn't a subclass)
    private fun goToSettings() {
        // Log.i("reg", "goToSettings");
        val myAppSettings = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")
        )
        myAppSettings.addCategory(Intent.CATEGORY_DEFAULT)
        myAppSettings.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        startActivityForResult(myAppSettings, REQUEST_PERMISSIONS_IDENTIFIER)
    }

    override fun onResume() {
        // Log.i("reg", "onResume");
        super.onResume()
        activityNotVisible = false

        // This used to be in an else block, its idempotent and we appear to have been having
        // problems with it not having been run.
        initialize(applicationContext)
        if (aboutToResetFalseActivityReturn) {
            aboutToResetFalseActivityReturn = false
            thisResumeCausedByFalseActivityReturn = false
            return
        }

        // We ~need access to the phone number, there might be a cleaner way than asking for this
        // permission but we don't know it.
        if (PHONE_REQUIRED && !checkAccessReadPhoneNumbers(applicationContext) && !thisResumeCausedByFalseActivityReturn) {
            if (shouldShowRequestPermissionRationale(Manifest.permission.READ_PHONE_NUMBERS)) {
                if (!prePromptActive && !postPromptActive)
                    showPostPermissionAlert(this)
            } else if (!prePromptActive && !postPromptActive)
                showPrePermissionAlert(this)
        }
    }

    override fun onPause() {
        super.onPause()
        activityNotVisible = true
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        // printi"onActivityResult. requestCode: " + requestCode + ", resultCode: " + resultCode )
        aboutToResetFalseActivityReturn = true
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        // Log.i("reg", "onRequestPermissionResult");
        // this is identical logical progression to the way it works in SessionActivity.
        if (activityNotVisible)
            return

        for (i in grantResults.indices) {
            if (permissions[i] == Manifest.permission.READ_PHONE_NUMBERS) {
                // Log.i("permiss", "permission return: " + permissions[i]);
                if (grantResults[i] == PermissionHandler.PERMISSION_GRANTED)
                    break
                // shouldShow... "This method returns true if the app has requested this permission
                // previously and the user denied the request.")
                if (shouldShowRequestPermissionRationale(permissions[i]))
                    showPostPermissionAlert(this)
            }
        }
    }

    companion object {
        private const val PERMISSION_CALLBACK = 0 // This callback value can be anything, we are not really using it
        private const val REQUEST_PERMISSIONS_IDENTIFIER = 1500

        /**Implements the server request logic for user, device registration.
         * @param url the URL for device registration */
        @JvmStatic
        private fun tryToRegisterWithTheServer(currentActivity: Activity, url: String, newPassword: String) {
            object : HTTPUIAsync(url, currentActivity) {
                override fun doInBackground(vararg arg0: Void?): Void? {
                    initialize(currentActivity.applicationContext)
                    // Always use anonymized hashing when first registering the phone.
                    parameters = PostRequest.makeParameter("new_password", newPassword) +
                            PostRequest.makeParameter("phone_number", (activity as RegisterActivity).phoneNumber) +
                            PostRequest.makeParameter("device_id", getAndroidID()) +
                            PostRequest.makeParameter("device_os", "Android") +
                            PostRequest.makeParameter("os_version", androidVersion) +
                            PostRequest.makeParameter("hardware_id", hardwareId) +
                            PostRequest.makeParameter("brand", brand) +
                            PostRequest.makeParameter("manufacturer", manufacturer) +
                            PostRequest.makeParameter("model", model) +
                            PostRequest.makeParameter("product", product) + PostRequest.makeParameter("beiwe_version", beiweVersion)
                    responseCode = PostRequest.httpRegister(parameters, url)

                    // If we are not using anonymized hashing, resubmit the phone identifying information
                    // (This short circuits so if the initial register fails, it won't try here)
                    if (responseCode == 200 && !PersistentData.getUseAnonymizedHashing()) {
                        try {
                            // Sleep for one second to fix bug htat happens when backend does not
                            // receive information with overlapping timestamps.... haaax...
                            Thread.sleep(1000)
                        } catch (e: InterruptedException) {
                            e.printStackTrace()
                        }
                        parameters = PostRequest.makeParameter("new_password", newPassword) +
                                PostRequest.makeParameter("phone_number", (activity as RegisterActivity).phoneNumber) +
                                PostRequest.makeParameter("device_id", getAndroidID()) +
                                PostRequest.makeParameter("device_os", "Android") +
                                PostRequest.makeParameter("os_version", androidVersion) +
                                PostRequest.makeParameter("hardware_id", hardwareId) +
                                PostRequest.makeParameter("brand", brand) +
                                PostRequest.makeParameter("manufacturer", manufacturer) +
                                PostRequest.makeParameter("model", model) +
                                PostRequest.makeParameter("product", product) +
                                PostRequest.makeParameter("beiwe_version", beiweVersion)
                        PostRequest.httpRegisterAgain(parameters, url)
                    }
                    return null
                }

                override fun onPostExecute(arg: Void?) {
                    super.onPostExecute(arg)
                    if (responseCode == 200) {
                        PersistentData.setPassword(newPassword)
                        if (PersistentData.getCallClinicianButtonEnabled() || PersistentData.getCallResearchAssistantButtonEnabled())
                            activity.startActivity(Intent(activity.applicationContext, PhoneNumberEntryActivity::class.java))
                        else
                            activity.startActivity(Intent(activity.applicationContext, ConsentFormActivity::class.java))
                        activity.finish()
                    } else
                        AlertsManager.showAlert(responseCode, currentActivity.getString(R.string.couldnt_register), currentActivity)
                }
            }
        }

        /*####################################################################
	    ###################### Permission Prompting ##########################
	    ####################################################################*/
        private var prePromptActive = false
        private var postPromptActive = false
        private var thisResumeCausedByFalseActivityReturn = false
        private var aboutToResetFalseActivityReturn = false
        private var activityNotVisible = false

        /* Message Popping */
        fun showPrePermissionAlert(activity: Activity) {
            // Log.i("reg", "showPreAlert");
            if (prePromptActive) {
                return
            }
            prePromptActive = true
            val builder = AlertDialog.Builder(activity)
            builder.setTitle(activity.getString(R.string.permissions_alert_title))
            builder.setMessage(R.string.permission_registration_read_sms_alert)
            builder.setOnDismissListener {
                activity.requestPermissions(arrayOf(READ_PHONE_NUMBERS_PERMISSION), PERMISSION_CALLBACK)
                prePromptActive = false
            }
            builder.setPositiveButton(activity.getString(R.string.alert_ok_button_text)) { arg0, arg1 -> } // Okay button
            builder.create().show()
        }

        fun showPostPermissionAlert(activity: RegisterActivity) {
            // Log.i("reg", "showPostAlert");
            if (postPromptActive) {
                return
            }
            postPromptActive = true
            val builder = AlertDialog.Builder(activity)
            builder.setTitle(R.string.permissions_alert_title)
            builder.setMessage(R.string.permission_registration_actually_need_sms_alert)
            builder.setOnDismissListener {
                thisResumeCausedByFalseActivityReturn = true
                activity.goToSettings()
                postPromptActive = false
            }
            builder.setPositiveButton(activity.getString(R.string.alert_ok_button_text)) { arg0, arg1 -> } // Okay button
            builder.create().show()
        }
    }
}