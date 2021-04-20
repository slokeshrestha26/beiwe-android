package org.beiwe.app.listeners;


import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.util.Log;

import androidx.appcompat.app.AppCompatActivity;

import org.beiwe.app.storage.AudioFileManager;

import java.io.IOException;
import java.util.Date;

public class BackgroundAudioListener extends AppCompatActivity {

    static String unencryptedFileName = "currentlyRecordingFile";
    static int format = MediaRecorder.OutputFormat.MPEG_4;
    static BackgroundAudioListener instance;

    MediaRecorder recorder;
    AudioFileManager fileManager = new AudioFileManager();
    int BIT_RATE = 6400;
    static long fileRefreshInterval = AlarmManager.INTERVAL_FIFTEEN_MINUTES;

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        instance = this;
        startRecording();
        Log.e("background audio", "inside on create function");
        // begins a process to create a new recording file every time interval
        initializeFileRefreshAlarms();
    }

    //ensure that if the activity ends, any recording is ended and encrypted
    @Override
    public void onDestroy() {
        if (isFinishing()) { // If the activity is being finished()...
            if (recorder != null) { endRecording(); }
        }
        super.onDestroy();
    }

    private void startRecording() {
        recorder = new MediaRecorder();
        recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        recorder.setOutputFormat(format);
        recorder.setOutputFile(unencryptedFileName);
        recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
        recorder.setAudioChannels(1);
        recorder.setAudioSamplingRate(44100);
        recorder.setAudioEncodingBitRate(BIT_RATE);
        // may require experimentation
        //recorder.setWakeMode()

        try {
            recorder.prepare();
        } catch (IOException e) {
            Log.e("Background Audio Record", "prepare() failed");
        }

        recorder.start();
    }

    private void endRecording() {
        recorder.stop();
        recorder.release();
        recorder = null;

        fileManager.encryptAudioFile(unencryptedFileName, ".mp3", getApplicationContext());
    }

    void startNewRecordingFile(){
        startRecording();
        endRecording();
    }

    void initializeFileRefreshAlarms(){
        Date when = new Date(System.currentTimeMillis());
        Context context = getApplicationContext();
        try {
            Intent someIntent = new Intent(getApplicationContext(), audioFileRefreshTimer.class); // intent to be launched

            PendingIntent pendingIntent = PendingIntent.getBroadcast(
                    context,
                    0, // id (optional)
                    someIntent, // intent to launch
                    PendingIntent.FLAG_CANCEL_CURRENT // PendingIntent flag
            );

            AlarmManager alarms = (AlarmManager) context.getSystemService(
                    context.ALARM_SERVICE
            );

            alarms.setRepeating(
                    AlarmManager.RTC_WAKEUP,
                    when.getTime(),
                    fileRefreshInterval,
                    pendingIntent
            );
        } catch(Exception e) {
            e.printStackTrace();
        }
    }
}
