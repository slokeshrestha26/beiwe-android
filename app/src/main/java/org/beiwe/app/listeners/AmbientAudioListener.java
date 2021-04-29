package org.beiwe.app.listeners;

import android.content.Context;
import android.media.MediaRecorder;
import android.util.Log;

import java.io.IOException;

/**
 * AmbientAudioListener is a Singleton class: it should only be started and instantiated once.
 */
public class AmbientAudioListener {
    private static AmbientAudioListener ambientAudioListenerInstance = null;
    protected static MediaRecorder mRecorder;
    private static Context appContext;
    public static final String unencryptedAudioFilename = "tempUnencryptedAmbientAudioFile";

    private AmbientAudioListener() {};

    private static String getUnencryptedAudioFilepath() {
        return appContext.getFilesDir().getAbsolutePath() + "/" + unencryptedAudioFilename;
    }

    public static synchronized AmbientAudioListener startRecording(Context applicationContext) {
        if (ambientAudioListenerInstance == null) {
            Log.i("ambient", "AmbientAudioListener is being instantiated");
            appContext = applicationContext;
            // Instantiate the AmbientAudioListener itself
            ambientAudioListenerInstance = new AmbientAudioListener();
            // Start the Media Recorder
            mRecorder = new MediaRecorder();
            mRecorder.reset();
            mRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            mRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            mRecorder.setOutputFile(getUnencryptedAudioFilepath());
            mRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            mRecorder.setAudioChannels(1);
            mRecorder.setAudioSamplingRate(44100);
            mRecorder.setAudioEncodingBitRate(64000);
            try {
                mRecorder.prepare();
            } catch (IOException e) {
                Log.e("ambient", "MediaRecorder.prepare() failed");
                // TODO: print a line to the app log file
            }
            mRecorder.start();
        } else {
            Log.i("ambient", "AmbientAudioListener already existed");
        }
        Log.i("ambient", "starting recording now!");
        return ambientAudioListenerInstance;  // TODO: maybe we don't need to return anything
    }
}
