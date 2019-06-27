package org.beiwe.app.ui.user;

import org.beiwe.app.R;
import org.beiwe.app.session.SessionActivity;
import org.beiwe.app.storage.PersistentData;
import org.beiwe.app.survey.SurveyActivity;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;

/**The main menu activity of the app. Currently displays 4 buttons - Audio Recording, Graph, Call Clinician, and Sign out.
 * @author Dor Samet */
public class MainMenuActivity extends SessionActivity {
	//extends a SessionActivity
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main_menu);

		Button callClinicianButton = (Button) findViewById(R.id.main_menu_call_clinician);
		if(PersistentData.getCallClinicianButtonEnabled()) {
			callClinicianButton.setText(PersistentData.getCallClinicianButtonText());
		}
		else {
			callClinicianButton.setVisibility(View.GONE);
		}

		((Button) findViewById(R.id.permSurvey)).setVisibility(View.VISIBLE);
	}
	
	/*#########################################################################
	############################## Buttons ####################################
	#########################################################################*/
	public void displaySurvey(View View) {
		Log.e("survey button", "button");
		Intent activityIntent = new Intent(getApplicationContext(), SurveyActivity.class);
		activityIntent.setAction( getApplicationContext().getString(R.string.start_tracking_survey) );
		activityIntent.putExtra( "surveyId", "LF8XyDFIGCMJaeElvFQKtpXS" );
		activityIntent.setFlags( Intent.FLAG_ACTIVITY_CLEAR_TOP );
		startActivity(activityIntent);
	}

//	public void graphResults (View v) { startActivity( new Intent(getApplicationContext(), GraphActivity.class) ); }
}
