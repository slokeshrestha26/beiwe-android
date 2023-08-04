package org.beiwe.app.survey

import org.beiwe.app.print

/**
 * This class makes it easier to pass around the description of a question so
 * that it can be recorded in Survey Answers and Survey Timings files.
 * That way, instead of just recording "user answered '7' to question #5", the
 * Survey Answers and Survey Timings files record something along the lines of
 * "user answered '7' to question #5, which asked 'how many hours of sleep did
 * you get last night' and had a numeric input field, with options..."
 */
class QuestionData(id: String?, type: QuestionType.Type, text: String?, options: String?) {
    var id: String? = null
    var type: QuestionType.Type
    var text: String? = null
    var options: String? = null
    var answerString: String? = null
    var answerInteger: Int? = null
    var answerDouble: Double? = null

    fun pprint() {
        print(
                "QuestionData - " +
                "id: " + id +
                ", type: " + type +
                ", text: " + text +
                ", options: " + options +
                ", answerString: " + answerString +
                ", answerInteger: " + answerInteger +
                ", answerDouble: " + answerDouble
        )
    }

    init {
        this.id = id
        this.type = type
        this.text = text
        this.options = options
    }

    /**  @return False if the answer is null, true if an answer exists. */
    fun questionIsAnswered(): Boolean {
        return answerString != null
    }
}