package org.beiwe.app.listeners;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class audioFileRefreshTimer extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent)
    {
        Log.e("audio file refresh", "recived new file signal");
        BackgroundAudioListener.instance.startNewRecordingFile();
    }
}