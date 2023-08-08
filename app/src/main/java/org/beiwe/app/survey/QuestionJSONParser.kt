package org.beiwe.app.survey

import android.os.Bundle
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

object QuestionJSONParser {
    fun getQuestionArgsFromJSONString(jsonQuestion: JSONObject): Bundle {
        val args = Bundle()
        val questionType = getStringFromJSONObject(jsonQuestion, "question_type")
        args.putString("question_type", questionType)
        if (questionType == "info_text_box")
            return getArgsForInfoTextBox(jsonQuestion, args)
        else if (questionType == "slider")
            return getArgsForSliderQuestion(jsonQuestion, args)
        else if (questionType == "radio_button")
            return getArgsForRadioButtonQuestion(jsonQuestion, args)
        else if (questionType == "checkbox")
            return getArgsForCheckboxQuestion(jsonQuestion, args)
        else if (questionType == "free_response")
            return getArgsForFreeResponseQuestion(jsonQuestion, args)
        else if (questionType == "date_time" || questionType == "time" || questionType == "date")
            return getArgsForDateTimeQuestion(jsonQuestion, args)
        throw RuntimeException("Invalid question type: $questionType")
    }

    // Gets and cleans the parameters necessary to create an Info Text Box
    private fun getArgsForInfoTextBox(jsonQuestion: JSONObject, args: Bundle): Bundle {
        args.putString("question_id", getStringFromJSONObject(jsonQuestion, "question_id"))
        args.putString("question_text", getStringFromJSONObject(jsonQuestion, "question_text"))
        return args
    }

    // Gets and cleans the parameters necessary to create a Slider Question
    private fun getArgsForSliderQuestion(jsonQuestion: JSONObject, args: Bundle): Bundle {
        args.putString("question_id", getStringFromJSONObject(jsonQuestion, "question_id"))
        args.putString("question_text", getStringFromJSONObject(jsonQuestion, "question_text"))
        args.putInt("min", getIntFromJSONObject(jsonQuestion, "min"))
        args.putInt("max", getIntFromJSONObject(jsonQuestion, "max"))
        return args
    }

    // Gets and cleans the parameters necessary to create a Radio Button Question
    private fun getArgsForRadioButtonQuestion(jsonQuestion: JSONObject, args: Bundle): Bundle {
        args.putString("question_id", getStringFromJSONObject(jsonQuestion, "question_id"))
        args.putString("question_text", getStringFromJSONObject(jsonQuestion, "question_text"))
        args.putStringArray("answers", getStringArrayFromJSONObject(jsonQuestion, "answers"))
        return args
    }

    // Gets and cleans the parameters necessary to create a Checkbox Question
    private fun getArgsForCheckboxQuestion(jsonQuestion: JSONObject, args: Bundle): Bundle {
        args.putString("question_id", getStringFromJSONObject(jsonQuestion, "question_id"))
        args.putString("question_text", getStringFromJSONObject(jsonQuestion, "question_text"))
        args.putStringArray("answers", getStringArrayFromJSONObject(jsonQuestion, "answers"))
        return args
    }

    // Gets and cleans the parameters necessary to create a Free-Response Question
    private fun getArgsForFreeResponseQuestion(jsonQuestion: JSONObject, args: Bundle): Bundle {
        args.putString("question_id", getStringFromJSONObject(jsonQuestion, "question_id"))
        args.putString("question_text", getStringFromJSONObject(jsonQuestion, "question_text"))
        args.putInt("text_field_type", getTextFieldTypeAsIntFromJSONObject(jsonQuestion, "text_field_type"))
        return args
    }

    private fun getArgsForDateTimeQuestion(jsonQuestion: JSONObject, args: Bundle): Bundle {
        args.putString("question_id", getStringFromJSONObject(jsonQuestion, "question_id"))
        args.putString("question_text", getStringFromJSONObject(jsonQuestion, "question_text"))
        return args
    }

    /**Get a String from a JSONObject key
     * @param obj a generic JSONObject
     * @param key the JSON key
     * @return return an empty String instead of throwing a JSONException */
    private fun getStringFromJSONObject(obj: JSONObject, key: String): String {
        return try {
            obj.getString(key)
        } catch (e: JSONException) {
            ""
        }
    }

    /**Get an int from a JSONObject key
     * @param obj a generic JSONObject
     * @param key the JSON key
     * @return return -1 instead of throwing a JSONException */
    private fun getIntFromJSONObject(obj: JSONObject, key: String): Int {
        return try {
            obj.getInt(key)
        } catch (e: JSONException) {
            -1
        }
    }

    /**Get an array of Strings from a JSONObject key
     * @param obj a generic JSONObject
     * @param key the JSON key
     * @return return a one-String array instead of throwing a JSONException */
    private fun getStringArrayFromJSONObject(obj: JSONObject, key: String): Array<String?> {
        val jsonArray: JSONArray
        jsonArray = try {
            obj.getJSONArray(key)
        } catch (e1: JSONException) {
            return arrayOf("")
        }
        val strings = arrayOfNulls<String>(jsonArray.length())
        for (i in 0 until jsonArray.length()) {
            try {
                strings[i] = jsonArray.getJSONObject(i).getString("text")
            } catch (e: JSONException) {
                strings[i] = ""
            }
        }
        return strings
    }

    /**Get an Enum TextFieldType from a JSONObject key
     * @param obj a generic JSONObject
     * @param key the JSON key
     * @return return SINGLE_LINE_TEXT as the default instead of throwing a JSONException */
    private fun getTextFieldTypeAsIntFromJSONObject(obj: JSONObject, key: String): Int {
        return try {
            TextFieldType.Type.valueOf(obj.getString(key)).ordinal
        } catch (e: JSONException) {
            TextFieldType.Type.SINGLE_LINE_TEXT.ordinal
        }
    }
}