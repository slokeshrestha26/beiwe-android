package org.beiwe.app.survey

/**
 * Created by Josh Zagorsky on 12/11/16.
 */
class QuestionType {
    enum class Type(val stringName: String) {
        INFO_TEXT_BOX("Info Text Box"),

        SLIDER("Slider Question"),

        RADIO_BUTTON("Radio Button Question"),

        CHECKBOX("Checkbox Question"),

        FREE_RESPONSE("Open Response Question"),

        DATE("Date Question"),

        DATE_TIME("Date and Time Question"),

        TIME("Time Question");
    }
}