package org.beiwe.app.storage

import org.json.JSONException
import org.json.JSONObject

object SetDeviceSettings {
    @JvmStatic
    @Throws(JSONException::class)
    fun writeDeviceSettings(deviceSettings: JSONObject) {
        // Write data stream booleans
        PersistentData.setAccelerometerEnabled(deviceSettings.getBoolean("accelerometer"))
        PersistentData.setGyroscopeEnabled(deviceSettings.getBoolean("gyro"))
        PersistentData.setGpsEnabled(deviceSettings.getBoolean("gps"))
        PersistentData.setCallsEnabled(deviceSettings.getBoolean("calls"))
        PersistentData.setTextsEnabled(deviceSettings.getBoolean("texts"))
        PersistentData.setWifiEnabled(deviceSettings.getBoolean("wifi"))
        PersistentData.setBluetoothEnabled(deviceSettings.getBoolean("bluetooth"))
        PersistentData.setPowerStateEnabled(deviceSettings.getBoolean("power_state"))

        // any sections in try-catch blocks were added after go-live, so must be caught in case the
        // app is newer than the server backend.
        try {
            PersistentData.setAmbientAudioCollectionIsEnabled(deviceSettings.getBoolean("ambient_audio"))
            PersistentData.setAmbientAudioOffDuration(deviceSettings.getLong("ambient_audio_off_duration_seconds"))
            PersistentData.setAmbientAudioOnDuration(deviceSettings.getLong("ambient_audio_on_duration_seconds"))
            PersistentData.setAmbientAudioSampleRate(deviceSettings.getLong("ambient_audio_sampling_rate"))
            PersistentData.setAmbientAudioBitrate(deviceSettings.getLong("ambient_audio_bitrate"))
        } catch (e: JSONException) {
            e.printStackTrace()
        }

        try {
            PersistentData.setAllowUploadOverCellularData(deviceSettings.getBoolean("allow_upload_over_cellular_data"))
        } catch (e: JSONException) {
            PersistentData.setAllowUploadOverCellularData(false)
        }

        // Write timer settings
        PersistentData.setAccelerometerOffDuration(deviceSettings.getLong("accelerometer_off_duration_seconds"))
        PersistentData.setAccelerometerOnDuration(deviceSettings.getLong("accelerometer_on_duration_seconds"))
        PersistentData.setAccelerometerFrequency(deviceSettings.getLong("accelerometer_frequency"))
        PersistentData.setGyroscopeOffDuration(deviceSettings.getLong("gyro_off_duration_seconds"))
        PersistentData.setGyroscopeOnDuration(deviceSettings.getLong("gyro_on_duration_seconds"))
        PersistentData.setGyroscopeFrequency(deviceSettings.getLong("gyro_frequency"))
        PersistentData.setBluetoothOnDuration(deviceSettings.getLong("bluetooth_on_duration_seconds"))
        PersistentData.setBluetoothTotalDuration(deviceSettings.getLong("bluetooth_total_duration_seconds"))
        PersistentData.setBluetoothGlobalOffset(deviceSettings.getLong("bluetooth_global_offset_seconds"))
        PersistentData.setCheckForNewSurveysFrequency(deviceSettings.getLong("check_for_new_surveys_frequency_seconds"))
        PersistentData.setCreateNewDataFilesFrequency(deviceSettings.getLong("create_new_data_files_frequency_seconds"))
        PersistentData.setGpsOffDuration(deviceSettings.getLong("gps_off_duration_seconds"))
        PersistentData.setGpsOnDuration(deviceSettings.getLong("gps_on_duration_seconds"))
        PersistentData.setTimeBeforeAutoLogout(deviceSettings.getLong("seconds_before_auto_logout"))
        PersistentData.setUploadDataFilesFrequency(deviceSettings.getLong("upload_data_files_frequency_seconds"))
        PersistentData.setVoiceRecordingMaxTimeLength(deviceSettings.getLong("voice_recording_max_time_length_seconds"))

        // wifi periodicity needs to have a minimum because it creates a new file every week
        var wifiLogFrequencySeconds = deviceSettings.getLong("wifi_log_frequency_seconds")
        if (wifiLogFrequencySeconds < 10) {
            wifiLogFrequencySeconds = 10
        }
        PersistentData.setWifiLogFrequency(wifiLogFrequencySeconds)

        // Write text strings
        PersistentData.setAboutPageText(deviceSettings.getString("about_page_text"))
        PersistentData.setCallClinicianButtonText(deviceSettings.getString("call_clinician_button_text"))
        PersistentData.setConsentFormText(deviceSettings.getString("consent_form_text"))
        PersistentData.setSurveySubmitSuccessToastText(deviceSettings.getString("survey_submit_success_toast_text"))

        // Anonymized hashing
        try {
            PersistentData.setUseAnonymizedHashing(deviceSettings.getBoolean("use_anonymized_hashing"))
        } catch (e: JSONException) {
            PersistentData.setUseAnonymizedHashing(false)
        }

        // Use GPS Fuzzing
        try {
            PersistentData.setUseGpsFuzzing(deviceSettings.getBoolean("use_gps_fuzzing"))
        } catch (e: JSONException) {
            PersistentData.setUseGpsFuzzing(false)
        }

        // Call button toggles
        try {
            PersistentData.setCallClinicianButtonEnabled(deviceSettings.getBoolean("call_clinician_button_enabled"))
        } catch (e: JSONException) {
            PersistentData.setCallClinicianButtonEnabled(true)
        }
        try {
            PersistentData.setCallResearchAssistantButtonEnabled(deviceSettings.getBoolean("call_research_assistant_button_enabled"))
        } catch (e: JSONException) {
            PersistentData.setCallResearchAssistantButtonEnabled(true)
        }
    }
}