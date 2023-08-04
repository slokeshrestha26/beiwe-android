package org.beiwe.app.survey;

import static org.beiwe.app.UtilsKt.print;
import static org.beiwe.app.UtilsKt.printe;

import android.app.FragmentTransaction;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import org.beiwe.app.JSONUtils;
import org.beiwe.app.R;
import org.beiwe.app.session.SessionActivity;
import org.beiwe.app.storage.PersistentData;
import org.beiwe.app.storage.TextFileManager;
import org.beiwe.app.ui.user.MainMenuActivity;
import org.beiwe.app.ui.utils.SurveyNotifications;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**The SurveyActivity displays to the user the survey that has been pushed to the device.
 * Layout in this activity is rendered, not static.
 * @author Josh Zagorsky, Eli Jones */

public class SurveyActivity extends SessionActivity implements
	QuestionFragment.OnGoToNextQuestionListener,
	SurveySubmitFragment.OnSubmitButtonClickedListener {
	private String surveyId;
	private JsonSkipLogic surveyLogic;
	private boolean hasLoadedBefore = false;
	private long initialViewMoment;
	private QuestionFragment questionFragment;
	
	@Override
	protected void onCreate (Bundle savedInstanceState) {
		PersistentData.setTakingSurvey();
		super.onCreate(savedInstanceState);
		initialViewMoment = System.currentTimeMillis();
		setContentView(R.layout.activity_survey);
		Intent triggerIntent = getIntent();
		surveyId = triggerIntent.getStringExtra("surveyId");
	}
	
	@Override
	protected void onDestroy () {
		super.onDestroy();
		PersistentData.setNotTakingSurvey();
	}
	
	@Override
	protected void doBackgroundDependentTasks () {
		super.doBackgroundDependentTasks();
		if (!hasLoadedBefore) {
			setUpQuestions(surveyId);
			// Run the logic as if we had just pressed next without answering a hypothetical question -1
			goToNextQuestion(null);
			// Record the time that the survey was first visible to the user
			SurveyTimingsRecorder.recordSurveyFirstDisplayed(surveyId);
			// Onnela lab requested this line in the debug log
			TextFileManager.writeDebugLogStatement(initialViewMoment + " opened survey " + surveyId + ".");
			hasLoadedBefore = true;
		}
	}
	
	
	@Override
	public void goToNextQuestion (QuestionData dataFromOldQuestion) {
		// store the answer from the previous question
		surveyLogic.setAnswer(dataFromOldQuestion);
		
		Boolean isRequired = surveyLogic.getCurrentQuentionRequired();
		// printe("goToNextQuestion - getCurrentQuentionRequired:" + isRequired);
		// printe("goToNextQuestion - dataFromOldQuestion: " + dataFromOldQuestion + " - " + (dataFromOldQuestion == null) );
		// if (dataFromOldQuestion != null)
		// 	dataFromOldQuestion.pprint();
		// else printe("goToNextQuestion - dataFromOldQuestion is null");
		
		if (isRequired != null && isRequired && (dataFromOldQuestion == null || !dataFromOldQuestion.questionIsAnswered())) {
			Toast.makeText(this, "This question is required.", Toast.LENGTH_SHORT).show();
			return;
		}
		
		JSONObject nextQuestion = surveyLogic.getNextQuestion();
		
		// If you've run out of questions, display the Submit button
		if (nextQuestion == null) {
			displaySurveySubmitFragment();
		} else {
			displaySurveyQuestionFragment(nextQuestion, surveyLogic.onFirstQuestion());
		}
	}
	
	
	@Override
	public void onBackPressed () {
		super.onBackPressed();
		// In order oto do that we need to execute the fragment's getAnswer function.
		// surveyLogic.setAnswer( questionFragment.getAnswer(...) );
		surveyLogic.goBackOneQuestion();
	}
	
	
	private void displaySurveyQuestionFragment (JSONObject jsonQuestion, Boolean isFirstQuestion) {
		// Create a question fragment with the attributes of the question
		questionFragment = new QuestionFragment();
		questionFragment.setArguments(QuestionJSONParser.getQuestionArgsFromJSONString(jsonQuestion));
		
		// Put the fragment into the view
		FragmentTransaction fragmentTransaction = getFragmentManager().beginTransaction();
		if (isFirstQuestion) {
			fragmentTransaction.add(R.id.questionFragmentGoesHere, questionFragment);
		} else {
			fragmentTransaction.replace(R.id.questionFragmentGoesHere, questionFragment);
			fragmentTransaction.addToBackStack(null);
		}
		fragmentTransaction.commit();
	}
	
	
	private void displaySurveySubmitFragment () {
		Bundle args = new Bundle();
		args.putStringArrayList("unansweredQuestions", surveyLogic.getUnansweredQuestions());
		
		SurveySubmitFragment submitFragment = new SurveySubmitFragment();
		submitFragment.setArguments(args);
		
		FragmentTransaction fragmentTransaction = getFragmentManager().beginTransaction();
		fragmentTransaction.replace(R.id.questionFragmentGoesHere, submitFragment);
		fragmentTransaction.addToBackStack(null);
		fragmentTransaction.commit();
	}
	
	
	private void setUpQuestions (String surveyId) {
		// Get survey settings
		Boolean randomizeWithMemory = false;
		Boolean randomize = false;
		int numberQuestions = 0;
		
		try {
			JSONObject surveySettings = new JSONObject(PersistentData.getSurveySettings(surveyId));
			randomizeWithMemory = surveySettings.optBoolean(getString(R.string.randomizeWithMemory), false);
			randomize = surveySettings.optBoolean(getString(R.string.randomize), false);
			numberQuestions = surveySettings.optInt(getString(R.string.numberQuestions), 0);
		} catch (JSONException e) {
			Log.e("Survey Activity", "There was an error parsing survey settings");
			e.printStackTrace();
		}
		
		try { // Get survey content as an array of questions; each question is a JSON object
			JSONArray jsonQuestions = new JSONArray(PersistentData.getSurveyContent(surveyId));
			// If randomizing the question order, reshuffle the questions in the JSONArray
			if (randomize && !randomizeWithMemory) {
				jsonQuestions = JSONUtils.shuffleJSONArray(jsonQuestions, numberQuestions);
			}
			if (randomize && randomizeWithMemory) {
				jsonQuestions = JSONUtils.shuffleJSONArrayWithMemory(jsonQuestions, numberQuestions, surveyId);
			}
			//construct the survey's skip logic.
			//(param 2: If randomization is enabled do not run the skip logic for the survey.)
			surveyLogic = new JsonSkipLogic(jsonQuestions, !randomize, getApplicationContext());
		} catch (JSONException e) {
			e.printStackTrace();
		}
	}
	
	public QuestionData getCurrentQuestionData () {
		return surveyLogic.getCurrentQuestionData();
	}
	
	/**Called when the user presses "Submit" at the end of the survey,
	 * saves the answers, and takes the user back to the main page. */
	@Override
	public void submitButtonClicked () {
		SurveyTimingsRecorder.recordSubmit(getApplicationContext());
		
		// Write the data to a SurveyAnswers file
		SurveyAnswersRecorder answersRecorder = new SurveyAnswersRecorder();
		// Show a Toast telling the user either "Thanks, success!" or "Oops, there was an error"
		String toastMsg = null;
		if (answersRecorder.writeLinesToFile(surveyId, surveyLogic.getQuestionsForSerialization())) {
			toastMsg = PersistentData.getSurveySubmitSuccessToastText();
		} else {
			toastMsg = getApplicationContext().getResources().getString(R.string.survey_submit_error_message);
		}
		Toast.makeText(getApplicationContext(), toastMsg, Toast.LENGTH_LONG).show();
		
		// Close the Activity
		startActivity(new Intent(getApplicationContext(), MainMenuActivity.class));
		SurveyNotifications.dismissNotification(getApplicationContext(), surveyId);
		finish();
	}
}
