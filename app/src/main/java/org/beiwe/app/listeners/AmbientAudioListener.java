package org.beiwe.app.listeners;

import android.content.Context;
import android.media.MediaRecorder;
import android.os.AsyncTask;
import android.util.Log;

import org.beiwe.app.storage.AudioFileManager;

import java.io.IOException;

/**
 * AmbientAudioListener is a Singleton class: it should only be started and instantiated once.
 */
public class AmbientAudioListener {
    private static AmbientAudioListener ambientAudioListenerInstance = null;
    private static Context appContext;
    private static MediaRecorder mRecorder;
    private static String filenameExtension = ".mp4";
    public static final String unencryptedAudioFilename = "tempUnencryptedAmbientAudioFile";

    private AmbientAudioListener() {};

    private static String getUnencryptedAudioFilepath() {
        return appContext.getFilesDir().getAbsolutePath() + "/" + unencryptedAudioFilename;
    }

    public static synchronized void startRecording(Context applicationContext) {
        // Only instantiate the AmbientAudioRecorder if it has not yet been instantiated!
        if (ambientAudioListenerInstance == null) {
            Log.e("ambient", "startRecording!");
            // Instantiate the AmbientAudioListener itself
            ambientAudioListenerInstance = new AmbientAudioListener();
            appContext = applicationContext;
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
        }
    }


    public static synchronized void encryptAmbientAudioFile() {
        Log.e("ambient", "called encryptAmbientAudioFile");
        // First, check if the audio recorder exists
        if (ambientAudioListenerInstance != null) {
            Log.e("ambient", "preparing to halt ambient audio collection");
            // TODO: check if mRecorder is currently recording
            mRecorder.stop();
            mRecorder.reset();
            mRecorder.release();
            mRecorder = null; // TODO: is this necessary? Or can we just resume it later?
            new EncryptAmbientAudioFileTask().execute();
        }
    }


    // TODO: is there a problem with EncryptAmbientAudioFileTask being static?
    private static class EncryptAmbientAudioFileTask extends AsyncTask<Void, Void, Void> {
        @Override
        protected void onPreExecute() {
            Log.e("ambient", "encrypt audio file, onPreExecute()");
            // TODO: set the filename, and blacklist it from being uploaded
        }

        @Override
        protected Void doInBackground(Void... params) {
            Log.e("ambient", "encrypt audio file, doing in background...");
            AudioFileManager.encryptAudioFile(getUnencryptedAudioFilepath(), filenameExtension, null, appContext);
            return null;
        }

        @Override
        protected void onPostExecute(Void arg) {
            Log.e("ambient", "encrypt audio file, onPostExecute()");
            // TODO: delete the unencrypted audio file
            // TODO: remove the filename from the blacklist, and allow it to be uploaded
            // TODO: resume recording, to a new unencrypted audio file
        }
    }
}
