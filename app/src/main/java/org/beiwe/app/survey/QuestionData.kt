package org.beiwe.app.survey

import org.beiwe.app.printe

/** class for containing the answer to any type of question. Has data and logic to convert any type
 * of question to an appropriate answer string.  */
class QuestionData(id: String?, type: QuestionType.Type, text: String?, options: String?) {
    // todo: make more of these non optional.
    var id: String? = null
    var type: QuestionType.Type
    var text: String? = null
    var options: String? = null
    public var answerString: String? = null
    var answerInteger: Int? = null
    var answerDouble: Double? = null

    var time_hour: Int? = null
    var time_minute: Int? = null
    var date_string: String? = null

    fun pprint() {
        // print everything that has data in the object
        var x = "QuestionData - "
        x += if (this.id == null) "" else "id: $id, "
        x += if (this.type == null) "" else "type: $type, "
        x += if (this.text == null) "" else "text: $text, "
        x += if (this.options == null) "" else "options: $options, "
        x += if (this.answerString == null) "" else "answerString: $answerString, "
        x += if (this.answerInteger == null) "" else "answerInteger: $answerInteger, "
        x += if (this.answerDouble == null) "" else "answerDouble: $answerDouble, "
        x += if (this.time_hour == null) "" else "time_hour: $time_hour, "
        x += if (this.time_minute == null) "" else "time_minute: $time_minute, "
        x += if (this.date_string == null) "" else "date_string: $date_string, "
        printe(x)
    }

    init {
        this.id = id
        this.type = type
        this.text = text
        this.options = options
    }

    fun coerceAnswer(){
        when (this.type) {
            QuestionType.Type.FREE_RESPONSE -> this.setAnswerFreeResponse()
            QuestionType.Type.SLIDER -> this.setAnswerSlider()
            QuestionType.Type.RADIO_BUTTON -> answer_no_op()
            QuestionType.Type.INFO_TEXT_BOX -> answer_no_op()
            QuestionType.Type.CHECKBOX -> this.answer_no_op()
            QuestionType.Type.DATE -> this.setAnswerDate()
            QuestionType.Type.DATE_TIME -> this.setAnswerDateTime()
            QuestionType.Type.TIME -> this.setAnswerTime()
        }
    }

    /** @return False if the answerString is null, true if an answer exists. */
    fun questionIsAnswered(): Boolean {
        return this.answerString != null
    }

    // some answers are fully contained in their answerString.
    fun answer_no_op() {}

    /* Numeric free response questions will get converted to floats in addition to their string
     * representation. */
    fun setAnswerFreeResponse() {
        if (this.answerString != null) {
            try {
                this.answerDouble = this.answerString!!.toDouble()
            } catch (e: NumberFormatException) {
                this.answerDouble = null
            }
        } else
            // make sure to set double to null if string is null
            this.answerDouble = null
    }

    /** Sliders comes in as integers, convert to float, convert to string... why float?
     * make sure to clear out when null. anyway */
    fun setAnswerSlider() {
        if (this.answerInteger != null)
            this.answerDouble = java.lang.Double.valueOf(this.answerInteger!!.toDouble())
        else
            this.answerDouble = null

        // and then this next line depends on the double being set... why?
        if (this.answerDouble != null)
            this.answerString = "" + this.answerInteger
        else
            this.answerString = null
    }

    /** Take the time numbers and convert to strings */
    fun setAnswerTime() {
        // pad values under 10 with a 0
        if (this.time_hour != null && this.time_minute != null)
            this.answerString = this.timeToString()
        else
            this.answerString = null
    }

    /** factorable logic for converting time numbers to a string. */
    fun timeToString(): String {
        val hourString = if (time_hour!! < 10) "0" + time_hour else "" + time_hour
        val minuteString = if (time_minute!! < 10) "0" + time_minute else "" + time_minute
        return "$hourString:$minuteString"
    }

    /** Essentially a passthrough, we need a separate field for datetime questions */
    fun setAnswerDate() {
        if (this.date_string != null)
            this.answerString = this.date_string
        else
            this.answerString = null
    }

    /** date time questions need to concatenate their date and answer fields into one. We don't
     * handle special cases because loading back partially populated answers gets too complex, and
     * the date and time pickers select the current date or time by default and can't be cleared. */
    fun setAnswerDateTime() {
        if (this.date_string != null && this.time_hour != null && this.time_minute != null)
            this.answerString = this.date_string + " " + this.timeToString()
        else
            this.answerString = null
    }
}