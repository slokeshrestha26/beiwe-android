package org.beiwe.app.ui.user

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import kotlinx.android.synthetic.main.activity_main_menu.main_menu_call_clinician
import org.beiwe.app.R
import org.beiwe.app.session.SessionActivity
import org.beiwe.app.storage.PersistentData
import org.beiwe.app.survey.SurveyActivity
import org.beiwe.app.ui.utils.SurveyNotifications
import org.json.JSONException
import org.json.JSONObject

/**The main menu activity of the app. Currently displays 4 buttons - Audio Recording, Graph, Call Clinician, and Sign out.
 * @author Dor Samet
 */
class MainMenuActivity : SessionActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main_menu)

        if (PersistentData.getCallClinicianButtonEnabled())
            main_menu_call_clinician.text = PersistentData.getCallClinicianButtonText()
        else
            main_menu_call_clinician.visibility = View.GONE
    }

    fun setupSurveyList() {
        // get the active and always available surveys
        val surveyIds = ArrayList<String>()
        for (surveyId in PersistentData.getSurveyIds())
            try {
                val surveySettings = JSONObject(PersistentData.getSurveySettings(surveyId))
                val is_active = SurveyNotifications.isNotificationActive(applicationContext, surveyId)
                if (surveySettings.getBoolean("always_available") || is_active)
                    surveyIds.add(surveyId)
            } catch (e: JSONException) {
                e.printStackTrace()
            }

        var button_count = 0
        for (i in surveyIds.indices) {
            button_count = i
            var surveyName = PersistentData.getSurveyName(surveyIds[i])
            val surveyType = PersistentData.getSurveyType(surveyIds[i])
            val button = findViewById<View>(resources.getIdentifier(
                    "permSurvey$i", "id", this.packageName)) as Button

            // set textAllCaps and surveyName if there is no survey name
            if (surveyName == "") {
                button.isAllCaps = true
                surveyName = if (surveyType == "audio_survey")
                    getString(R.string.permaaudiosurvey)
                else
                    getString(R.string.perm_survey)
            } else
                button.isAllCaps = false

            // add emoji to survey name if it is an audio survey
            if (surveyType == "audio_survey")
                surveyName = "$surveyName 🎙"

            // button.text = "$surveyName ($survey_tag)"
            button.text = surveyName
            button.setTag(R.string.permasurvey, surveyIds[i])
            button.visibility = View.VISIBLE

            // there are 16 buttons, so we only need to iterate 0 to 16
            if (i >= 16)
                break
        }

        // iterate over every unused button, disable button and GONE it to fix scroll bugs
        for (i in button_count + 1..16) {
            val button = findViewById<View>(resources.getIdentifier(
                    "permSurvey$i", "id", this.packageName)) as Button
            button.visibility = View.GONE
        }
    }

    // run an task every 5 seconds to refresh the available survey list
    override fun onResume() {
        super.onResume()
        setupSurveyList()
    }


    /*#########################################################################
	############################## Buttons ####################################
	#########################################################################*/
    fun displaySurvey(view: View) {
        val activityIntent: Intent
        val surveyId = view.getTag(R.string.permasurvey) as String
        activityIntent = if (PersistentData.getSurveyType(surveyId) == "audio_survey") {
            Intent(applicationContext, SurveyNotifications.getAudioSurveyClass(surveyId))
        } else {
            Intent(applicationContext, SurveyActivity::class.java)
        }
        activityIntent.action = applicationContext.getString(R.string.start_tracking_survey)
        activityIntent.putExtra("surveyId", surveyId)
        activityIntent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
        startActivity(activityIntent)
    }
}