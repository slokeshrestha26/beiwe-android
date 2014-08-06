package org.beiwe.app.survey;

import java.util.Arrays;

import org.beiwe.app.R;

import android.content.Context;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnTouchListener;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.SeekBar;
import android.widget.TextView;

public class QuestionRenderer {

	private Context appContext;
	private LayoutInflater inflater;
	private InputListener inputRecorder;
	
	private int viewID;
	
	public QuestionRenderer(Context applicationContext) {
		
		appContext = applicationContext;
		inflater = (LayoutInflater) appContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		inputRecorder = new InputListener(appContext);
		
		viewID = 100;  // Start from an arbitrary number, in case other Views have low, sequential ID #s
		
		/* XML views inflated by an Activity render with the app's default
		 * style (set in the Manifest.XML), but for some reason, XML views
		 * inflated by this class don't render with the app's default style,
		 * unless we set it manually: */
		appContext.setTheme(R.style.AppTheme);
	}

	
	/**
	 * Creates an informational text view that does not have an answer type
	 * @param infoText The informational text
	 * @return TextView (to be displayed as question text)
	 */
	public TextView createInfoTextbox(String questionID, String infoText) {
		
		TextView infoTextbox = (TextView) inflater.inflate(R.layout.survey_info_textbox, null);
		
		// Clean inputs
		if (infoText == null) {
			infoText = appContext.getResources().getString(R.string.question_error_text);
		}
		
		// Set the question text
		infoTextbox.setText(infoText);
		
		return infoTextbox;
	}


	/**
	 * Creates a slider with a range of discrete values
	 * @param questionText The text of the question to be asked
	 * @param numberOfValues A range of "0-4" has 5 values
	 * @param defaultValue Starts at 0; can be as high as (numberOfValues - 1)
	 * @return LinearLayout A slider bar
	 */
	public LinearLayout createSliderQuestion(String questionID, 
			String questionText, int numberOfValues, int defaultValue) {
		
		LinearLayout question = (LinearLayout) inflater.inflate(R.layout.survey_slider_question, null);
		SeekBar slider = (SeekBar) question.findViewById(R.id.slider);
		
		// Set the text of the question itself
		TextView questionTextView = (TextView) question.findViewById(R.id.questionText);
		if (questionText != null) {
			questionTextView.setText(questionText);
		}
		
		// Clean inputs/force them to be usable numbers
		if (numberOfValues < 2) { numberOfValues = 2; }
		if (numberOfValues > 100) {	numberOfValues = 100; }
		if (defaultValue < 0) {	defaultValue = 0; }
		if (defaultValue > numberOfValues - 1) { defaultValue = numberOfValues - 1; }
		
		// Set the slider's range and default/starting value
		slider.setMax(numberOfValues - 1);
		slider.setProgress(defaultValue);

		setViewId(slider);
		
		// Add a label above the slider with numbers that mark points on a scale
		addNumbersLabelingToSlider(question, 0, numberOfValues);
		
		// Create text strings that represent the question and its answer choices
		String options = "From 0 to " + numberOfValues + "; default " + defaultValue;
		QuestionDescription questionDescription = 
				new QuestionDescription(questionID, "Slider Question", questionText, options);
		
		// Set the slider to listen for and record user input
		slider.setOnSeekBarChangeListener(inputRecorder.new SliderListener(questionDescription));
		
		// Make the slider invisible until it's touched (so there's effectively no default value)
		makeSliderInvisibleUntilTouched(slider);
		
		return question;
	}
	
	
	/**
	 * Creates a group of radio buttons
	 * @param questionText The text of the question
	 * @param answers An array of strings that are options matched with radio buttons
	 * @return RadioGroup A vertical set of radio buttons 
	 */
	public LinearLayout createRadioButtonQuestion(String questionID, String questionText, String[] answers) {
		
		LinearLayout question = (LinearLayout) inflater.inflate(R.layout.survey_radio_button_question, null);
		RadioGroup radioGroup = (RadioGroup) question.findViewById(R.id.radioGroup);
		
		// Set the text of the question itself
		TextView questionTextView = (TextView) question.findViewById(R.id.questionText);
		if (questionText != null) {
			questionTextView.setText(questionText);
		}
		
		// If the array of answers is null or too short, replace it with an error message
		if ((answers == null) || (answers.length < 2)) {
			String replacementAnswer = appContext.getResources().getString(R.string.question_error_text);
			String[] replacementAnswers = {replacementAnswer, replacementAnswer};
			answers = replacementAnswers;
		}
		
		// Loop through the answer strings, and make each one a radio button option
		for (int i = 0; i < answers.length; i++) {
			RadioButton radioButton = (RadioButton) inflater.inflate(R.layout.survey_radio_button, null);
			if (answers[i] != null) {
				radioButton.setText(answers[i]);
			}			
			setViewId(radioButton);
			radioGroup.addView(radioButton);
		}
		
		// Create text strings that represent the question and its answer choices
		QuestionDescription questionDescription = 
				new QuestionDescription(questionID, "Radio Button Question", questionText, Arrays.toString(answers));

		// Set the group of radio buttons to listen for and record user input
		radioGroup.setOnCheckedChangeListener(inputRecorder.new RadioButtonListener(questionDescription));
		
		return question;
	}
	

	/**
	 * Creates a question with an array of checkboxes
	 * @param questionText The text of the question
	 * @param options Each string in options[] will caption one checkbox
	 * @return LinearLayout a question with a list of checkboxes
	 */
	public LinearLayout createCheckboxQuestion(String questionID, String questionText, String[] options) {
		
		LinearLayout question = (LinearLayout) inflater.inflate(R.layout.survey_checkbox_question, null);
		LinearLayout checkboxesList = (LinearLayout) question.findViewById(R.id.checkboxesList);
		
		// Set the text of the question itself
		TextView questionTextView = (TextView) question.findViewById(R.id.questionText);
		if (questionText != null) {
			questionTextView.setText(questionText);
		}
		
		// Create text strings that represent the question and its answer choices
		QuestionDescription questionDescription = 
				new QuestionDescription(questionID, "Checkbox Question", questionText, Arrays.toString(options));

		// Loop through the options strings, and make each one a checkbox option
		if (options != null) {
			for (int i = 0; i < options.length; i++) {
				
				// Inflate the checkbox from an XML layout file
				CheckBox checkbox = (CheckBox) inflater.inflate(R.layout.survey_checkbox, null);
				
				// Set the text if it's provided; otherwise leave text as default error message
				if (options[i] != null) {
					checkbox.setText(options[i]);
				}
				
				setViewId(checkbox);
				
				// Make the checkbox listen for and record user input
				checkbox.setOnClickListener(inputRecorder.new CheckboxListener(questionDescription));
				
				// Add the checkbox to the list of checkboxes
				checkboxesList.addView(checkbox);
			}			
		}		
		
		return question;		
	}
	
	
	/**
	 * Creates a question with an open-response, text-input field
	 * @param questionText The text of the question
	 * @param inputTextType The type of answer (number, text, etc.)
	 * @return LinearLayout question and answer
	 */
	public LinearLayout createFreeResponseQuestion(String questionID, 
			String questionText, TextFieldType.Type inputTextType) {
		
		LinearLayout question = (LinearLayout) inflater.inflate(R.layout.survey_open_response_question, null);

		// Set the text of the question itself
		TextView questionTextView = (TextView) question.findViewById(R.id.questionText);
		if (questionText != null) {
			questionTextView.setText(questionText);
		}
		
		EditText editText = null;
		switch (inputTextType) {
		case NUMERIC:
			editText = (EditText) inflater.inflate(R.layout.survey_free_number_input, null);
			break;
			
		case SINGLE_LINE_TEXT:
			editText = (EditText) inflater.inflate(R.layout.survey_free_text_input, null);			
			break;
			
		case MULTI_LINE_TEXT:
			editText = (EditText) inflater.inflate(R.layout.survey_multiline_text_input, null);			
			break;

		default:
			editText = (EditText) inflater.inflate(R.layout.survey_free_text_input, null);			
			break;
		}

		setViewId(editText);
		
		// Create text strings that represent the question and its answer choices
		String options = "Text-field input type = " + inputTextType.toString();
		QuestionDescription questionDescription = 
				new QuestionDescription(questionID, "Open Response Question", questionText, options);

		// Set the text field to listen for and record user input
		editText.setOnFocusChangeListener(inputRecorder.new OpenResponseListener(questionDescription));
		
		LinearLayout textFieldContainer = (LinearLayout) question.findViewById(R.id.textFieldContainer);
		textFieldContainer.addView(editText);
		
		// TODO: on press carriage return, move to next question
		// TODO: add date and time pickers as input types: http://stackoverflow.com/a/14933515
		
		return question;
	}
	
	
	/**
	 * Set the view's ID so that its data are automatically saved when the screen rotates
	 * @param view
	 */
	private synchronized void setViewId(View view) {
		view.setId(viewID);
		viewID++;
	}

	
	/**
	 * Adds a numeric scale above a Slider Question
	 * @param question the Slider Question that needs a number scale
	 * @param min the lowest number on the scale
	 * @param max the highest number on the scale
	 */
	private void addNumbersLabelingToSlider(LinearLayout question, int min, int max) {
		// TODO: implement the ability to use MIN and MAX so that not every scale starts at zero
		
		// Replace the numbers label placeholder view (based on http://stackoverflow.com/a/3760027)
		View numbersLabel = (View) question.findViewById(R.id.numbersPlaceholder);
		int index = question.indexOfChild(numbersLabel);
		question.removeView(numbersLabel);
		numbersLabel = inflater.inflate(R.layout.survey_slider_numbers_label, question, false);
		LinearLayout label = (LinearLayout) numbersLabel.findViewById(R.id.linearLayoutNumbers);
		
		/* Decide whether to put 2, 3, 4, or 5 number labels. Pick the highest number of labels
		 * that can be achieved with each label above an integer value, and even spacing between
		 * all labels. */		
		int numberOfLabels = 0;
		int range = max - min;
		if ((range - 1) % 4 == 0) {
			numberOfLabels = 5;
		}
		else if ((range - 1) % 3 == 0) {
			numberOfLabels = 4;
		}
		else if ((range - 1) % 2 == 0) {
			numberOfLabels = 3;
		}
		else {
			numberOfLabels = 2;
		}
		
		// Create labels and spacers
		int numberResourceID = R.layout.survey_slider_single_number_label;
		for (int i = 0; i < numberOfLabels - 1; i++) {
			TextView number = (TextView) inflater.inflate(numberResourceID, label, false);
			label.addView(number);
			number.setText("" + (i * (range - 1) / (numberOfLabels - 1) + min));
			
			View spacer = (View) inflater.inflate(R.layout.horizontal_spacer, label, false);
			label.addView(spacer);			
		}
		// Create one last label (the rightmost one) without a spacer to its right
		TextView number = (TextView) inflater.inflate(numberResourceID, label, false);
		label.addView(number);		
		number.setText("" + (max - 1));
		
		// Add the set of numeric labels to the question
		question.addView(numbersLabel, index);		
	}
	
	
	/**
	 * Make the "thumb" (the round circle/progress knob) of a Slider almost
	 * invisible until the user touches it.  This way the user is forced to
	 * answer every slider question; otherwise, we would not be able to tell
	 * the difference between a user ignoring a slider and a user choosing to
	 * leave a slider at the default value.  This makes it like there is no
	 * default value. 
	 * @param slider
	 */
	private void makeSliderInvisibleUntilTouched(SeekBar slider) {
		// Before the user has touched the slider, make the "thumb" transparent/ almost invisible
		slider.getThumb().mutate().setAlpha(50);
		
		slider.setOnTouchListener(new OnTouchListener() {	
			@Override
			public boolean onTouch(View v, MotionEvent event) {
				// When the user touches the slider, make the "thumb" opaque and fully visible
				SeekBar slider = (SeekBar) v;
				slider.getThumb().mutate().setAlpha(255);
				return false;
			}
		});
	}
		
}
