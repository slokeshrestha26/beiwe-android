package org.beiwe.app.survey

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Fragment
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.View.OnFocusChangeListener
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.SeekBar.OnSeekBarChangeListener
import android.widget.TextView
import org.beiwe.app.R
import java.util.Arrays

/**
 * Created by Josh Zagorsky on 11/30/16.
 */
class QuestionFragment : Fragment() {
    var goToNextQuestionListener: OnGoToNextQuestionListener? = null
    var questionData: QuestionData? = null
    var questionType: QuestionType.Type? = null
    var the_questionLayout: View? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        /* XML views inflated by an Activity render with the app's default style (set in the
         * Manifest.XML), but for some reason, XML views inflated by this class don't render with
         * the app's default style, unless we set it manually: */

        // Render the question and inflate the layout for this fragment
        val fragmentQuestionLayout = inflater.inflate(R.layout.fragment_question, null) as ScrollView
        val questionContainer = fragmentQuestionLayout.findViewById<View>(R.id.questionContainer) as FrameLayout
        val questionLayout = createQuestion(inflater, arguments)
        this.the_questionLayout = questionLayout // the view is mutable and can change??
        questionContainer.addView(questionLayout)

        // Set an onClickListener for the next and back button (need to cast)
        val nextButton = fragmentQuestionLayout.findViewById<View>(R.id.nextButton) as Button
        val backButton = fragmentQuestionLayout.findViewById<View>(R.id.backButton) as Button
        nextButton.setOnClickListener {
            goToNextQuestionListener!!.goToNextQuestion(getAnswer(questionLayout, questionType!!))
        }
        backButton.setOnClickListener {
            activity.onBackPressed() // very easy
        }
        return fragmentQuestionLayout
    }

    /* The following dual declaration is due to a change/deprecation in the Android Fragment
	 * _handling_ code.  It is difficult to determine exactly which API version this change occurs
	 * in, but the linked stack overflow article assumes API 23 (6.0). The difference in the
	 * declarations is that one takes an activity, and one takes a context.  Starting in 6 the OS
	 * guarantees a call to the one that takes an activity, previous versions called the one that
	 * takes an activity.
	 * ...
	 * If one of these is missing then goToNextQuestionListener fails to instantiate, causing a
	 * crash inside the onClick function for the next button.
	 * ...
	 * http://stackoverflow.com/questions/32604552/onattach-not-called-in-fragment */
    /** This function will get called on NEW versions of Android (6+). ... and now that's been deprecated tooooo!  */
    override fun onAttach(context: Context) {
        super.onAttach(context)
        goToNextQuestionListener = context as OnGoToNextQuestionListener
    }

    /** This function will get called on OLD versions of Android (<6).  */
    override fun onAttach(activity: Activity) {
        super.onAttach(activity)
        goToNextQuestionListener = activity as OnGoToNextQuestionListener
    }

    private fun getAnswer(questionLayout: View, questionType: QuestionType.Type): QuestionData? {
        val answerString = SurveyAnswersRecorder.getAnswerString(questionLayout, questionType)
        if (answerString != null) {
            questionData!!.answerString = answerString
        }
        val answerIntegerValue = SurveyAnswersRecorder.getAnswerIntegerValue(questionLayout, questionType)
        if (answerIntegerValue != null) {
            questionData!!.answerInteger = answerIntegerValue
        }
        return questionData
    }

    private fun createQuestion(inflater: LayoutInflater, args: Bundle): View {
        val questionID = args.getString("question_id")
        val questionType = args.getString("question_type")
        val questionText = args.getString("question_text")
        if (questionType == "info_text_box") {
            this.questionType = QuestionType.Type.INFO_TEXT_BOX
            return createInfoTextbox(inflater, questionID, questionText)
        } else if (questionType == "slider") {
            this.questionType = QuestionType.Type.SLIDER
            val min = args.getInt("min")
            val max = args.getInt("max")
            return createSliderQuestion(inflater, questionID, questionText, min, max)
        } else if (questionType == "radio_button") {
            this.questionType = QuestionType.Type.RADIO_BUTTON
            val answers = args.getStringArray("answers")
            return createRadioButtonQuestion(inflater, questionID, questionText, answers)
        } else if (questionType == "checkbox") {
            this.questionType = QuestionType.Type.CHECKBOX
            val answers = args.getStringArray("answers")
            return createCheckboxQuestion(inflater, questionID, questionText, answers)
        } else if (questionType == "free_response") {
            this.questionType = QuestionType.Type.FREE_RESPONSE
            val textFieldTypeInt = args.getInt("text_field_type")
            val textFieldType = TextFieldType.Type.values()[textFieldTypeInt]
            return createFreeResponseQuestion(inflater, questionID, questionText, textFieldType)
        }
        // Default: return an error message
        return inflater.inflate(R.layout.survey_info_textbox, null)
    }

    private fun prepareExistingAnswers() {
        /* We need to check whether questionData already has an object assigned to it, which occurs
        when the back button gets pressed and pops the backstack.  When the next button is pressed
        we pull any answer that has been saved by the activity.
        This operation may do nothing (re-set value to null) if there is no answer, that is fine. */
        if (questionData == null) {
            print("prepareExistingAnswers - questionData is null")
            questionData = (activity as SurveyActivity).currentQuestionData
        } else {
            print("prepareExistingAnswers - questionData is NOT null")
        }
    }

    /**
     * Creates an informational text view that does not have an answer type
     * @param infoText The informational text
     * @return TextView (to be displayed as question text)
     */
    private fun createInfoTextbox(inflater: LayoutInflater, questionID: String?, infoText: String?): TextView {
        var infoText = infoText
        val infoTextbox = inflater.inflate(R.layout.survey_info_textbox, null) as MarkDownTextView

        // Clean inputs
        if (infoText == null) {
            // Set the question text to the error string
            infoText = try {
                context.resources.getString(R.string.question_error_text)
            } catch (e: NoSuchMethodError) {
                activity.resources.getString(R.string.question_error_text)
            }
        }
        infoTextbox.setText(infoText)
        return infoTextbox
    }

    /**
     * Creates a slider with a range of discrete values
     * @param questionText The text of the question to be asked
     * @return LinearLayout A slider bar
     */
    private fun createSliderQuestion(
            inflater: LayoutInflater, questionID: String?,
            questionText: String?, min: Int, max: Int,
    ): LinearLayout {
        var min = min
        var max = max
        val question = inflater.inflate(R.layout.survey_slider_question, null) as LinearLayout
        val slider = question.findViewById<View>(R.id.slider) as SeekBarEditableThumb

        // Set the text of the question itself
        val questionTextView = question.findViewById<View>(R.id.questionText) as MarkDownTextView
        if (questionText != null) {
            questionTextView.setText(questionText)
        }

        // The min must be greater than the max, and the range must be at most 100.
        // If the min and max don't fit that, reset min to 0 and max to 100.
        if (min > max - 1 || max - min > 100) {
            min = 0
            max = 100
        }

        // Set the slider's range and default/starting value
        slider.max = max - min
        slider.min = min
        prepareExistingAnswers()
        if (questionData != null && questionData!!.answerInteger != null) {
            slider.progress = questionData!!.answerInteger!!
            slider.markAsTouched()
        } else {
            // Make the slider invisible until it's touched (so there's effectively no default value)
            slider.progress = 0
            makeSliderInvisibleUntilTouched(slider)
            // Create text strings that represent the question and its answer choices
            val options = "min = $min; max = $max"
            questionData = QuestionData(questionID, QuestionType.Type.SLIDER, questionText, options)
        }

        // Add a label above the slider with numbers that mark points on a scale
        addNumbersLabelingToSlider(inflater, question, min, max)

        // Set the slider to listen for and record user input
        slider.setOnSeekBarChangeListener(SliderListener(questionData!!))
        return question
    }

    /**
     * Creates a group of radio buttons
     * @param questionText The text of the question
     * @param answers An array of strings that are options matched with radio buttons
     * @return RadioGroup A vertical set of radio buttons
     */
    private fun createRadioButtonQuestion(inflater: LayoutInflater, questionID: String?, questionText: String?, answers: Array<String?>?): LinearLayout {
        var answers = answers
        val question = inflater.inflate(R.layout.survey_radio_button_question, null) as LinearLayout
        val radioGroup = question.findViewById<View>(R.id.radioGroup) as RadioGroup

        // Set the text of the question itself
        val questionTextView = question.findViewById<View>(R.id.questionText) as MarkDownTextView
        if (questionText != null) {
            questionTextView.setText(questionText)
        }

        // If the array of answers is null or too short, replace it with an error message
        if (answers == null || answers.size < 2) {
            val replacementAnswer: String
            replacementAnswer = try {
                context.resources.getString(R.string.question_error_text)
            } catch (e: NoSuchMethodError) {
                activity.resources.getString(R.string.question_error_text)
            }
            val replacementAnswers = arrayOf<String?>(replacementAnswer, replacementAnswer)
            answers = replacementAnswers
        }

        // Loop through the answer strings, and make each one a radio button option
        for (i in answers.indices) {
            val radioButton = inflater.inflate(R.layout.survey_radio_button, null) as RadioButton
            if (answers.get(i) != null) {
                radioButton.text = answers.get(i)
            }
            radioGroup.addView(radioButton)
        }
        prepareExistingAnswers()
        if (questionData != null && questionData!!.answerInteger != null) {
            radioGroup.check(radioGroup.getChildAt(questionData!!.answerInteger!!).id)
        } else {
            // Create text strings that represent the question and its answer choices
            questionData = QuestionData(questionID, QuestionType.Type.RADIO_BUTTON, questionText, Arrays.toString(answers))
        }

        // Set the group of radio buttons to listen for and record user input
        radioGroup.setOnCheckedChangeListener(RadioButtonListener(questionData!!))
        return question
    }

    /**Creates a question with an array of checkboxes
     * @param questionText The text of the question
     * @param options Each string in options[] will caption one checkbox
     * @return LinearLayout a question with a list of checkboxes */
    private fun createCheckboxQuestion(inflater: LayoutInflater, questionID: String?, questionText: String?, options: Array<String?>?): LinearLayout {
        val question = inflater.inflate(R.layout.survey_checkbox_question, null) as LinearLayout
        val checkboxesList = question.findViewById<View>(R.id.checkboxesList) as LinearLayout

        // Set the text of the question itself
        val questionTextView = question.findViewById<View>(R.id.questionText) as MarkDownTextView
        if (questionText != null) {
            questionTextView.setText(questionText)
        }
        var checkedAnswers: Array<String?>? = null
        prepareExistingAnswers()
        if (questionData != null && questionData!!.answerString != null) {
            val answerString = questionData!!.answerString
            if (answerString!!.length > 2) {
                checkedAnswers = answerString.substring(1, answerString.length - 1).split(", ".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
            }
        } else {
            // Create text strings that represent the question and its answer choices
            questionData = QuestionData(questionID, QuestionType.Type.CHECKBOX, questionText, Arrays.toString(options))
        }

        // Loop through the options strings, and make each one a checkbox option
        if (options != null) {
            for (i in options.indices) {

                // Inflate the checkbox from an XML layout file
                val checkbox = inflater.inflate(R.layout.survey_checkbox, null) as CheckBox

                // Set the text if it's provided; otherwise leave text as default error message
                if (options[i] != null) {
                    checkbox.text = options[i]
                    // If it should be checked, check it
                    if (checkedAnswers != null && Arrays.asList<String?>(*checkedAnswers).contains(options[i])) {
                        checkbox.isChecked = true
                    }
                }

                // Make the checkbox listen for and record user input
                checkbox.setOnClickListener(CheckboxListener(questionData!!))

                // Add the checkbox to the list of checkboxes
                checkboxesList.addView(checkbox)
            }
        }
        return question
    }

    /**Creates a question with an open-response, text-input field
     * @param questionText The text of the question
     * @param inputTextType The type of answer (number, text, etc.)
     * @return LinearLayout question and answer */
    private fun createFreeResponseQuestion(
            inflater: LayoutInflater, questionID: String?,
            questionText: String?, inputTextType: TextFieldType.Type,
    ): LinearLayout {
        // TODO: Give open response questions autofocus and make the keyboard appear
        val question = inflater.inflate(R.layout.survey_open_response_question, null) as LinearLayout

        // Set the text of the question itself
        val questionTextView = question.findViewById<View>(R.id.questionText) as MarkDownTextView

        if (questionText != null) {
            questionTextView.setText(questionText)
        }

        val editText: EditText = when (inputTextType) {
            TextFieldType.Type.NUMERIC -> inflater.inflate(R.layout.survey_free_number_input, null) as EditText
            TextFieldType.Type.SINGLE_LINE_TEXT -> inflater.inflate(R.layout.survey_free_text_input, null) as EditText
            TextFieldType.Type.MULTI_LINE_TEXT -> inflater.inflate(R.layout.survey_multiline_text_input, null) as EditText
            // else -> inflater.inflate(R.layout.survey_free_text_input, null) as EditText
        }

        /* Improvement idea: if you want to add date and time pickers as input
		 * types, here's a start: http://stackoverflow.com/a/14933515 */

        /* Improvement idea: when the user presses Enter, jump to the next input field */
        prepareExistingAnswers()
        if (questionData != null && questionData!!.answerString != null) {
            editText.setText(questionData!!.answerString)
        } else {
            // Create text strings that represent the question and its answer choices
            val options = "Text-field input type = $inputTextType"
            questionData = QuestionData(questionID, QuestionType.Type.FREE_RESPONSE, questionText, options)
        }

        // Set the text field to listen for and record user input
        editText.onFocusChangeListener = OpenResponseListener(questionData!!)
        val textFieldContainer = question.findViewById<View>(R.id.textFieldContainer) as LinearLayout
        textFieldContainer.addView(editText)
        return question
    }

    /**
     * Adds a numeric scale above a Slider Question
     * @param question the Slider Question that needs a number scale
     * @param min the lowest number on the scale
     * @param max the highest number on the scale */
    private fun addNumbersLabelingToSlider(inflater: LayoutInflater, question: LinearLayout, min: Int, max: Int) {
        // Replace the numbers label placeholder view (based on http://stackoverflow.com/a/3760027)
        var numbersLabel = question.findViewById(R.id.numbersPlaceholder) as View
        val index = question.indexOfChild(numbersLabel)
        question.removeView(numbersLabel)
        numbersLabel = inflater.inflate(R.layout.survey_slider_numbers_label, question, false)
        val label = numbersLabel.findViewById<View>(R.id.linearLayoutNumbers) as LinearLayout

        /* Decide whether to put 2, 3, 4, or 5 number labels. Pick the highest number of labels
		 * that can be achieved with each label above an integer value, and even spacing between
		 * all labels. */
        var numberOfLabels = 0
        val range = max - min
        numberOfLabels = if (range % 4 == 0) {
            5
        } else if (range % 3 == 0) {
            4
        } else if (range % 2 == 0) {
            3
        } else {
            2
        }

        // Create labels and spacers
        val numberResourceID = R.layout.survey_slider_single_number_label
        for (i in 0 until numberOfLabels - 1) {
            val number = inflater.inflate(numberResourceID, label, false) as TextView
            label.addView(number)
            number.text = "" + (min + i * range / (numberOfLabels - 1))
            val spacer = inflater.inflate(R.layout.horizontal_spacer, label, false) as View
            label.addView(spacer)
        }
        // Create one last label (the rightmost one) without a spacer to its right
        val number = inflater.inflate(numberResourceID, label, false) as TextView
        label.addView(number)
        number.text = "" + max

        // Add the set of numeric labels to the question
        question.addView(numbersLabel, index)
    }

    /** Make the "thumb" (the round circle/progress knob) of a Slider almost invisible until the user
     * touches it.  This way the user is forced to answer every slider question; otherwise, we would
     * not be able to tell the difference between a user ignoring a slider and a user choosing to
     * leave a slider at the default value.  This makes it like there is no default value.
     * @param slider */
    @SuppressLint("ClickableViewAccessibility")
    private fun makeSliderInvisibleUntilTouched(slider: SeekBarEditableThumb) {
        // Before the user has touched the slider, make the "thumb" transparent/ almost invisible
        /* Note: this works well on Android 4; there's a weird bug on Android 2 in which the first
		 * slider question in the survey sometimes appears with a black thumb (once you touch it,
		 * it turns into a white thumb). */
        slider.markAsUntouched()

        slider.setOnTouchListener { v, event ->
            // When the user touches the slider, make the "thumb" opaque and fully visible
            val slider = v as SeekBarEditableThumb
            slider.markAsTouched()
            false
        }
    }

    /**************************** ANSWER LISTENERS ********************************************* */

    /** Listens for a touch/answer to a Slider Question, and records the answer  */
    private inner class SliderListener(var questionDescription: QuestionData) : OnSeekBarChangeListener {
        override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {}
        override fun onStartTrackingTouch(seekBar: SeekBar) {}

        override fun onStopTrackingTouch(seekBar: SeekBar) {
            var answer = ""
            answer += if (seekBar is SeekBarEditableThumb) {
                val slider = seekBar
                slider.progress + slider.min
            } else {
                seekBar.progress
            }
            SurveyTimingsRecorder.recordAnswer(answer, questionDescription)
            (activity as SurveyActivity).surveyLogic!!.setAnswer(getAnswer(the_questionLayout!!, questionType!!))
        }
    }

    /** Listens for a touch/answer to a Radio Button Question, and records the answer  */
    private inner class RadioButtonListener(var questionDescription: QuestionData) : RadioGroup.OnCheckedChangeListener {
        override fun onCheckedChanged(group: RadioGroup, checkedId: Int) {
            val selectedButton = group.findViewById<View>(checkedId) as RadioButton
            if (selectedButton.isChecked) {
                SurveyTimingsRecorder.recordAnswer(selectedButton.text.toString(), questionDescription)
                (activity as SurveyActivity).surveyLogic!!.setAnswer(getAnswer(the_questionLayout!!, questionType!!))
            } else {
                /* It should not be possible to un-check a radio button, but if
				 * that happens, record the answer as an empty string */
                SurveyTimingsRecorder.recordAnswer("", questionDescription)
            }
        }
    }

    /** Listens for a touch/answer to a Checkbox Question, and records the answer  */
    private inner class CheckboxListener(var questionDescription: QuestionData) : View.OnClickListener {
        override fun onClick(view: View) {
            // If it's a CheckBox and its parent is a LinearLayout...
            if (view is CheckBox && view.getParent() is LinearLayout) {
                val checkboxesList = view.getParent() as LinearLayout
                val answersList = SurveyAnswersRecorder.getSelectedCheckboxes(checkboxesList)
                SurveyTimingsRecorder.recordAnswer(answersList, questionDescription)
                // if (the_questionLayout != null) {
                (activity as SurveyActivity).surveyLogic!!.setAnswer(getAnswer(the_questionLayout!!, questionType!!))
                // }
            }
        }
    }

    /** Listens for an input/answer to an Open/Free Response Question, and records the answer  */
    private inner class OpenResponseListener(var questionDescription: QuestionData) : OnFocusChangeListener {
        // TODO: replace this with a listener on the Next button; that'd probably make more sense
        override fun onFocusChange(v: View, hasFocus: Boolean) {
            if (hasFocus) {
                // The user just selected the input box

                /* Improvement idea: record when the user first touched the
				 * input field; right now it only records when the user
				 * selected away from the input field. */

                // Set the EditText so that if the user taps outside, the keyboard disappears
                if (v is EditText) {
                    val keyboard: TextFieldKeyboard
                    keyboard = try {
                        TextFieldKeyboard(context)
                    } catch (e: NoSuchMethodError) {
                        TextFieldKeyboard(activity)
                    }
                    keyboard.makeKeyboardBehave(v)
                }
            } else {
                // The user just selected away from the input box
                if (v is EditText) {
                    val answer = v.text.toString()
                    SurveyTimingsRecorder.recordAnswer(answer, questionDescription)
                    (activity as SurveyActivity).surveyLogic!!.setAnswer(getAnswer(the_questionLayout!!, questionType!!))
                }
            }
        }
    }

    // Interface for the "Next" button to signal the Activity
    interface OnGoToNextQuestionListener {
        fun goToNextQuestion(dataFromCurrentQuestion: QuestionData?)
    }
}