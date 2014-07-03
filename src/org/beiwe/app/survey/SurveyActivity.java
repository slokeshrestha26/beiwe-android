package org.beiwe.app.survey;

import org.beiwe.app.R;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

public class SurveyActivity extends Activity {
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_survey);
		
		renderSurvey();		
	}
	
	
	private void renderSurvey() {
		String jsonSurveyString = null;
		
		QuestionsDownloader downloader = new QuestionsDownloader(getApplicationContext());
		try {
			jsonSurveyString = downloader.getSurveyQuestionsFromServer();
		}
		catch (Exception e) {
			jsonSurveyString = downloader.getSurveyQuestionsFromAppResources();
		}		

		LinearLayout surveyLayout = (LinearLayout) getLayoutInflater().inflate(R.layout.survey_layout, null);
		LinearLayout surveyQuestionsLayout = (LinearLayout) surveyLayout.findViewById(R.id.surveyQuestionsLayout);
		
		JsonParser jsonParser = new JsonParser(getApplicationContext());
		jsonParser.renderSurveyFromJSON(surveyQuestionsLayout, jsonSurveyString);

		ViewGroup page = (ViewGroup) findViewById(R.id.scrollViewMain);
		page.addView(surveyLayout);
		
		//View loadingSpinner = getLayoutInflater().inflate(R.layout.survey_loading_spinner, null);
		//page.addView(loadingSpinner);
		
		AnswerRecorder.recordSurveyFirstDisplayed();
	}
	
	
	public void submitButtonPressed(View v) {
		AnswerRecorder.recordSubmit(getApplicationContext());
		finish();
	}

}
