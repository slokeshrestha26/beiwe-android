package org.beiwe.app.survey

import android.content.Context
import android.util.Log
import org.beiwe.app.CrashHandler.Companion.writeCrashlog
import org.beiwe.app.printe
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.util.Collections
import java.util.Locale

/** Created by elijones on 12/1/16.  */

/**@param jsonQuestions The content of the "content" key in a survey
 * @param runDisplayLogic A boolean value for whether skip Logic should be run on this survey.
 * @throws JSONException thrown if there are any questions without question ids. */
class JsonSkipLogic(jsonQuestions: JSONArray, runDisplayLogic: Boolean, private val appContext: Context) {
    val questionAnswers: HashMap<String?, QuestionData>
    val questionSkipLogic: HashMap<String, JSONObject>
    val questionsById: HashMap<String, JSONObject>
    val questionOrder: ArrayList<String>
    val questionsRequired: HashMap<String, Boolean>
    var currentQuestion: Int
    val displayLogicEnabled: Boolean

    init {
        val MAX_SIZE = jsonQuestions.length()
        var questionId: String
        var requiredQuestion: Boolean
        var question: JSONObject
        var displayLogic: JSONObject?

        // construct the various question id collections
        questionAnswers = HashMap(MAX_SIZE)
        questionSkipLogic = HashMap(MAX_SIZE)
        questionsById = HashMap(MAX_SIZE)
        questionOrder = ArrayList(MAX_SIZE)
        questionsRequired = HashMap(MAX_SIZE)

        // go over the questions and store their data
        for (i in 0 until MAX_SIZE) { // uhg, you can't iterate over a JSONArray.
            question = jsonQuestions.optJSONObject(i)
            questionId = question.getString("question_id")
            requiredQuestion = question.optBoolean("required", false)
            questionsById[questionId] = question // store questions by id
            questionsRequired[questionId] = requiredQuestion // setup required questions
            questionOrder.add(questionId) // setup question order

            // setup question logic
            if (question.has("display_if")) { // skip item if it has no display_if item
                Log.v("debugging json content", " $question")
                displayLogic = question.optJSONObject("display_if")
                if (displayLogic != null) { // skip if display logic exists but is null
                    questionSkipLogic[questionId] = displayLogic
                }
            }
        }

        // attribute init
        this.displayLogicEnabled = runDisplayLogic
        currentQuestion = -1 // set the current question to -1, makes getNextQuestionID less annoying.
    }

    fun pprint() {
        if (currentQuestionJson == null) {
            printe("can't print skiplogic current question, zeroth question.")
        } else {
            printe("current question json logic:")
            printe(currentQuestionJson)
        }
    }

    val currentQuestionJson: JSONObject?
        get() {
            if (currentQuestion < questionOrder.size && currentQuestion >= 0)
                return questionsById[questionOrder[currentQuestion]]
            return null
        }


    /** @return the QuestionData object for the current question, null otherwise. */
    val currentQuestionData: QuestionData?
        get() {
            if (currentQuestion < questionOrder.size)
                return questionAnswers[questionOrder[currentQuestion]]
            return null
        }

    /** @return the question id string for the current question, null otherwise. */
    val currentQuestionRequired: Boolean?
        get() {
            if (currentQuestion < questionOrder.size && currentQuestion >= 0)
                return questionsRequired[questionOrder[currentQuestion]]
            return null
        }

    /** Determines question should be displayed next.
     * @return a question id string, null if there is no next item.*/
    private fun getQuestion(goForward: Boolean): JSONObject? {
        if (goForward) currentQuestion++ else currentQuestion--

        // if currentQuestion is set to anything less than zero, reset to zero (shouldn't occur)
        if (currentQuestion < 0)
            currentQuestion = 0

        // if it is the first question it should invariably display.
        if (currentQuestion == 0) {
            // Log.i("json logic", "skipping logic and displaying first question");
            if (questionOrder.size == 0)
                return null
            else
                return questionsById[questionOrder[0]]
        }

        // if we would overflow the list (>= size) we are done, return null.
        // Log.w("json logic", "overflowed...");
        if (currentQuestion >= questionOrder.size)
            return null

        // if display logic has been disabled we skip logic processing and return the next question
        if (!displayLogicEnabled) {
            // Log.d("json logic", "runDisplayLogic set to true! doing all questions!");
            return questionsById[questionOrder[currentQuestion]]
        }

        val questionId = questionOrder[currentQuestion]
        // Log.v("json logic", "starting question " + QuestionOrder.indexOf(questionId) + " (" + questionId + "))");
        // if questionId does not have skip logic we display it.
        if (!questionSkipLogic.containsKey(questionId)) {
            // Log.d("json logic", "Question " + QuestionOrder.indexOf(questionId) + " (" + questionId + ") has no skip logic, done.");
            return questionsById[questionId]
        }

        if (shouldQuestionDisplay(questionId)) {
            // Log.d("json logic", "Question " + QuestionOrder.indexOf(questionId) + " (" + questionId + ") evaluated as true, done.");
            return questionsById[questionId]
        }

        // Log.d("json logic", "Question " + QuestionOrder.indexOf(questionId) + " (" + questionId + ") did not evaluate as true, proceeding to next question...");
        /* If it didn't meet any of the above conditions (and didn't display a question), call
        this function recursively, and keep doing that until you reach a question that should
        display. */
        return getQuestion(goForward)
    }

    fun nextQuestion(): JSONObject? {
        return getQuestion(true)
    }

    fun previousQuestion(): JSONObject? {
        return getQuestion(false)
    }

    /** @return whether the current logic is on question 1 */
    fun onFirstQuestion(): Boolean {
        return currentQuestion < 1
    }

    /** This function wraps the logic processing code.  If the logic processing encounters an error
     * due to json parsing the behavior is to invariably return true.
     * @param questionId
     * @return Boolean result of the logic */
    private fun shouldQuestionDisplay(questionId: String): Boolean {
        try {
            // If the survey display logic object is null or empty, display
            val question = questionSkipLogic[questionId]
            return if (question == null || question.length() == 0)
                true
            else
                parseLogicTree(questionId, question)
        } catch (e: JSONException) {
            Log.w("json exception while doing a logic parse", "=============================================================================================================================================")
            e.printStackTrace()
            writeCrashlog(e, appContext)
        }
        return true
    }

    @Throws(JSONException::class)
    private fun parseLogicTree(questionId: String, logic: JSONObject): Boolean {
        // extract the comparator, force it as lower case.
        val comparator = logic.keys().next().lowercase(Locale.getDefault())

        // logic.getXXX(comparator_as_key) is the only way to grab the value due to strong typing.
        // This object has many uses, so the following logic has been written for explicit clarity,
        // rather than optimized code length or performance.

        // We'll get the NOT out of the way first.
        if (comparator == "not") {
            // we need to pass in the Json _Object_ of the next layer in
            // Log.d("json logic", "evaluating as NOT (invert)");
            return !parseLogicTree(questionId, logic.getJSONObject(comparator))
        }
        if (COMPARATORS.contains(comparator)) {
            // in this case logic.getString(comparator) contains a json list/array with the first
            // element being the referencing question ID, and the second being a value to compare to.
            return runNumericLogic(comparator, logic.getJSONArray(comparator))
        }
        if (BOOLEAN_OPERATORS.contains(comparator)) {
            // get array of logic operations
            val manyLogics = logic.getJSONArray(comparator)
            val results: MutableList<Boolean> = ArrayList(manyLogics.length())
            // Log.v("json logic", "evaluating as boolean, " + manyLogics.length() + " things to process...");

            // iterate over array, get the booleans into a list
            for (i in 0 until manyLogics.length()) { // jsonArrays are not iterable...
                results.add(parseLogicTree(questionId, manyLogics.getJSONObject(i)))
            } // results now contains the boolean evaluation of all nested logics.

            // Log.v("json logic", "returning inside of " + QuestionOrder.indexOf(questionId) + " (" + questionId + ") after processing logic for boolean.");

            // And. if anything is false, return false. If those all pass, return true.
            if (comparator == "and") {
                Log.d("logic meanderings", "$questionId AND bools: $results")
                for (bool in results)
                    if (!bool)
                        return false
                return true
            }
            // Or. if anything is true, return true. If those all pass, return false.
            if (comparator == "or") {
                Log.d("logic meanderings", "$questionId OR bools: $results")
                for (bool in results)
                    if (bool)
                        return true
                return false
            }
        }
        throw NullPointerException("received invalid comparator: $comparator")
    }

    /** Processes the logical operation implemented of a comparator.
     * If there has been no answer for the question a logic operation references this function returns false.
     * @param comparator a string that is in the COMPARATORS constant.
     * @param parameters json array 2 elements in length.  The first element is a target question ID to pull an answer from, the second is the survey's value to compare to.
     * @return Boolean result of the operation, or false if the referenced question has no answer.
     * @throws JSONException */
    @Throws(JSONException::class)
    private fun runNumericLogic(comparator: String, parameters: JSONArray): Boolean {
        // Log.d("json logic", "inside numeric logic: " + comparator + ", " + parameters.toString());
        val targetQuestionId = parameters.getString(0)
        if (!questionAnswers.containsKey(targetQuestionId)) {
            return false
        } // false if DNE
        val userAnswer = questionAnswers[targetQuestionId]!!.answerDouble
        val surveyValue = parameters.getDouble(1)

//		Log.d("logic...", "evaluating useranswer " + userAnswer + comparator + surveyValue);

        // If we encounter an unanswered question, that evaluates as false. (defined in the spec.)
        if (userAnswer == null)
            return false
        if (comparator == "<")
            return userAnswer < surveyValue && !isEqual(userAnswer, surveyValue)
        if (comparator == ">")
            return userAnswer > surveyValue && !isEqual(userAnswer, surveyValue)
        if (comparator == "<=")
            return userAnswer <= surveyValue || isEqual(userAnswer, surveyValue) // the <= is slightly redundant, its fine.
        if (comparator == ">=")
            return userAnswer >= surveyValue || isEqual(userAnswer, surveyValue) // the >= is slightly redundant, its fine.
        if (comparator == "==")
            return isEqual(userAnswer, surveyValue)
        if (comparator == "!=")
            return !isEqual(userAnswer, surveyValue)
        throw NullPointerException("numeric logic fail")
    }

    // coerce values to numerics for all non-checkbox questions (they have a json list)

    fun setAnswer(questionData: QuestionData) {
        questionData.coerceAnswer() // ideally we don't need to run this safety function, but it's here just in case.
        questionAnswers[questionData.id] = questionData
    }

    /** @return a list of QuestionData objects for serialization to the answers file. */
    fun questionsForSerialization(): List<QuestionData> {
        val answers: MutableList<QuestionData> = ArrayList(questionOrder.size)
        // iterate over the question order, assemble QuestionAnswer objects. Skip INFO_TEXT_BOX
        // questions wherever they show up.
        for (questionId in questionOrder) {
            // if the question logic state indicates that a question should not have displayed then
            // we force the answer to NOT_PRESENTED.
            if (!shouldQuestionDisplay(questionId)) {
                val default_question_data = getCorrectlyPopulatedQuestionAnswer(questionId)
                default_question_data.answerString = "NOT_PRESENTED"
                if (default_question_data.type != QuestionType.Type.INFO_TEXT_BOX) // info text box...
                    answers.add(default_question_data)
                // It is possible for a user to go back and change answers with conditional logic,
                // if so then they may have answers in questionAnswers. We want to skip those.
                continue
            }

            var question_answer: QuestionData? = null
            // if the question was answered, add it to the list of answers.
            if (questionAnswers.containsKey(questionId))
                question_answer = questionAnswers[questionId]
            // if the question was not answered, get a default QuestionAnswer - will be coerced to
            // NO_ANSWER_SELECTED. (handles both null object present and no object present.)
            if (question_answer == null)
                question_answer = getCorrectlyPopulatedQuestionAnswer(questionId)
            // add as long as its not an info text box...
            if (question_answer.type != QuestionType.Type.INFO_TEXT_BOX)
                answers.add(question_answer)

        }
        return answers
    }

    /** This is the single function call to correctly instantiate a correct, unanswered QuestionData
     * object. Takes a question id, gets the json, passes it to QuestionType.Type logic, which is
     * slightly weirdly over in QuestionJSONParser. */
    fun getCorrectlyPopulatedQuestionAnswer(question_id: String): QuestionData {
        val json_object = questionsById[question_id]
        if (json_object != null)
            return QuestionJSONParser.getQuestionDataFromJSONString(json_object)
        throw NullPointerException("question_id $question_id not found in questionsById")
    }

    // If would not display not, skip it without incrementing.
    // If question is actually unanswered construct a display string.
    // If would display, increment question number.//A user may have viewed questions along a Display Logic Path A, but failed to answer
    // certain questions, then reversed and changed some answers. If so they may have created
    // a logic path B that no longer displays a previously answered question.
    // If that occurred then we have a a QuestionData object in QuestionAnswers that
    // effectively should not have displayed (returns false on evaluation of
    // shouldQuestionDisplay), so we need to catch that case.
    // The only way to catch that case is to run shouldQuestionDisplay on every QuestionData
    // object in QuestionAnswers.
    // There is one exception: INFO_TEXT_BOX questions are always ignored.

    // INFO_TEXT_BOX - skip it.
    // check if should display, store value
    // QuestionData objects are put in the QuestionAnswers dictionary if they are ever
    // displayed. (The user must also proceed to the next question, but that has no effect.)
    // No QuestionAnswer object means question did not display, which means we can skip it.
    // Guarantee: the questions in QuestionAnswer will consist of all _displayed_ questions.
    /**@return a list of QuestionData objects A) should have displayed, B) will be accessible by
     * paging back, C) don't have answers. */
    val unansweredQuestions: ArrayList<String>
        get() {
            val unanswered = ArrayList<String>(questionOrder.size)
            var questionDisplayNumber = 0
            var questionWouldDisplay: Boolean

            // Guarantee: the questions in QuestionAnswer will consist of all _displayed_ questions.
            for (questionId in questionOrder) {

                // QuestionData objects are put in the QuestionAnswers dictionary if they are ever
                // displayed. (The user must also proceed to the next question, but that has no effect.)
                // No QuestionAnswer object means question did not display, which means we can skip it.
                if (questionAnswers.containsKey(questionId)) {
                    // A user may have viewed questions along a Display Logic Path A, but failed to answer
                    // certain questions, then reversed and changed some answers. If so they may have created
                    // a logic path B that no longer displays a previously answered question.
                    // If that occurred then we have a a QuestionData object in QuestionAnswers that
                    // effectively should not have displayed (returns false on evaluation of
                    // shouldQuestionDisplay), so we need to catch that case.
                    // The only way to catch that case is to run shouldQuestionDisplay on every QuestionData
                    // object in QuestionAnswers.
                    // There is one exception: INFO_TEXT_BOX questions are always ignored.
                    val questionData: QuestionData? = questionAnswers[questionId]

                    // INFO_TEXT_BOX - skip it.
                    if (questionData!!.type == QuestionType.Type.INFO_TEXT_BOX)
                        continue

                    // check if should display, store value
                    questionWouldDisplay = shouldQuestionDisplay(questionId)
                    if (questionWouldDisplay) // If would display, increment question number.
                        questionDisplayNumber++
                    else // If would not display not, skip it without incrementing.
                        continue

                    // If question is actually unanswered construct a display string.
                    if (!questionData.questionIsAnswered())
                        unanswered.add("Question " + questionDisplayNumber + ": " + questionData.text)
                } else {
                    questionWouldDisplay = shouldQuestionDisplay(questionId)
                    if (questionWouldDisplay) {
                        questionDisplayNumber++
                        // need to get the question json and then get the question text
                        val questionJson: JSONObject? = questionsById[questionId]
                        val questionText = questionJson!!.optString("question_text") ?: ""
                        unanswered.add("Question " + questionDisplayNumber + ": " + questionText)
                    }
                }
            }
            return unanswered
        }


    /*############################# Assets ###################################*/
    companion object {
        // Comparator sets
        private var COMPARATORS: MutableSet<String> = HashSet(4)
        private var BOOLEAN_OPERATORS: MutableSet<String> = HashSet(2)

        init {
            COMPARATORS.add("<") // Setup numerics
            COMPARATORS.add(">")
            COMPARATORS.add("<=")
            COMPARATORS.add(">=")
            COMPARATORS.add("==") // Setup == / !=
            COMPARATORS.add("!=")
            BOOLEAN_OPERATORS.add("and") // Setup boolean operators
            BOOLEAN_OPERATORS.add("or")
            BOOLEAN_OPERATORS = Collections.unmodifiableSet(BOOLEAN_OPERATORS) // Block modification of all the sets.
            COMPARATORS = Collections.unmodifiableSet(COMPARATORS)
        }

        // For checking equality between Doubles.
        // http://stackoverflow.com/questions/25160375/comparing-double-values-for-equality-in-java
        const val ANSWER_COMPARISON_EQUALITY_DELTA = 0.00001

        private fun isEqual(d1: Double, d2: Double): Boolean {
            return d1 == d2 || isRelativelyEqual(d1, d2) // this short circuit just makes it faster
        }

        /** checks if the numbers are separated by a predefined absolute difference. */
        private fun isRelativelyEqual(d1: Double, d2: Double): Boolean {
            return ANSWER_COMPARISON_EQUALITY_DELTA > Math.abs(d1 - d2) / Math.max(Math.abs(d1), Math.abs(d2))
        }
    }
}