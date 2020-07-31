package org.beiwe.app.ui.user

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import kotlinx.android.synthetic.main.activity_main_menu.*
import org.beiwe.app.R
import org.beiwe.app.session.SessionActivity
import org.beiwe.app.storage.PersistentData
import org.beiwe.app.survey.SurveyActivity
import org.beiwe.app.ui.utils.SurveyNotifications
import org.json.JSONException
import org.json.JSONObject
import java.util.*

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

        val permSurveyIds = ArrayList<String>()

        for (surveyId in PersistentData.getSurveyIds())
            try {
                val surveySettings = JSONObject(PersistentData.getSurveySettings(surveyId))
                if (surveySettings.getBoolean("always_available"))
                    permSurveyIds.add(surveyId)
            } catch (e: JSONException) {
                e.printStackTrace()
            }

        if (permSurveyIds.size != 0) {
            for (i in permSurveyIds.indices) {
                val button = findViewById<View>(resources.getIdentifier("permSurvey$i", "id", this.packageName)) as Button
                if (PersistentData.getSurveyType(permSurveyIds[i]) == "audio_survey")
                    button.setText(R.string.permaaudiosurvey)
                button.setTag(R.string.permasurvey, permSurveyIds[i])
                button.visibility = View.VISIBLE
            }
        }
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