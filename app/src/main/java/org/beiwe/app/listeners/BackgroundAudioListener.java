package org.beiwe.app.listeners;


import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.util.Log;

import androidx.appcompat.app.AppCompatActivity;

import org.beiwe.app.CrashHandler;
import org.beiwe.app.storage.AudioFileManager;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Date;

import javax.xml.transform.ErrorListener;

public class BackgroundAudioListener{

    static String unencryptedFileName = "currentlyRecordingFile.mp3";
    private int BUFFER_SIZE = 0; //constant set in onCreate
    String unencryptedRawAudioFilePath;
    static int format = MediaRecorder.OutputFormat.MPEG_4;
    static BackgroundAudioListener instance;

    MediaRecorder recorder = null;
    //AudioFileManager fileManager = new AudioFileManager();
    int BIT_RATE = 6400;
    static long fileRefreshInterval = AlarmManager.INTERVAL_FIFTEEN_MINUTES;
    Context context = null;

    public BackgroundAudioListener(Context c){
        context = c;
        instance = this;
        Log.e("previous path", context.getFilesDir().getAbsolutePath() + "/" + unencryptedFileName);
        File outputFile = new File(context.getFilesDir().getAbsolutePath() + "/" + unencryptedFileName);
        unencryptedRawAudioFilePath = outputFile.getAbsolutePath();
        Log.e("new file path", unencryptedRawAudioFilePath);

        startRecording();
        Log.e("background audio", "inside constructor, recording started");
        // begins a process to create a new recording file every time interval
        initializeFileRefreshAlarms();
    }


    //ensure that if the activity ends, any recording is ended and encrypted
//    public void onDestroy() {
//        if (isFinishing()) { // If the activity is being finished()...
//            if (recorder != null) { endRecording(); }
//        }
//        super.onDestroy();
//    }

    private void startRecording() {
        recorder = new MediaRecorder();
        recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        recorder.setOutputFormat(format);
        recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
        recorder.setOutputFile(unencryptedRawAudioFilePath);
//        recorder.setAudioSamplingRate(44100);
//        recorder.setAudioEncodingBitRate(BIT_RATE);
        // may require experimentation
        //recorder.setWakeMode()
        // timeout handler?

        recorder.setOnErrorListener(new MediaRecorder.OnErrorListener() {
            @Override
            public void onError(MediaRecorder mr, int errCode, int extra) {
                Log.e("media recorder error", String.valueOf(errCode));
                Log.e("media recorder extra", String.valueOf(extra));
            }
        });

        recorder.setOnInfoListener(new MediaRecorder.OnInfoListener() {
            @Override
            public void onInfo(MediaRecorder mr, int what, int extra) {
                Log.e("media recorder info", String.valueOf(what));
                Log.e("media recorder extra", String.valueOf(extra));
            }
        });

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
        AudioFileManager.encryptAudioFile(unencryptedRawAudioFilePath, ".mp3", null, context);
    }

    public void startNewRecordingFile(){
        endRecording();
        startRecording();
    }

    void initializeFileRefreshAlarms(){
        Date when = new Date(System.currentTimeMillis());
        try {
            Intent someIntent = new Intent(context, audioFileRefreshTimer.class); // intent to be launched

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

    // only for audio recorder
//    private void writeAudioDataToFile() {
//        int recordingStatus = 0;
//        byte data[] = new byte[BUFFER_SIZE];
//        FileOutputStream rawAudioFile = null;
//        //setup file.
//        try { rawAudioFile = new FileOutputStream( unencryptedRawAudioFilePath ); }
//        catch (FileNotFoundException e) { CrashHandler.writeCrashlog(e, context ); return; }
//        //while recording get audio data chunks.
//        while ( currentlyRecording ) {
//            recordingStatus = recorder.read(data, 0, BUFFER_SIZE);
//            if ( recordingStatus != AudioRecord.ERROR_INVALID_OPERATION ) {
//                try { rawAudioFile.write(data); }
//                catch (IOException e) { e.printStackTrace(); } //swallow error.
//            }
//        }
//        try { rawAudioFile.close(); }
//        catch (IOException e) { e.printStackTrace(); }
//    }
}
