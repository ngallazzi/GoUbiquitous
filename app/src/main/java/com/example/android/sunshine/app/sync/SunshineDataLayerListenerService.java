package com.example.android.sunshine.app.sync;

import android.content.ContentValues;
import android.content.Intent;
import android.util.Log;

import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.WearableListenerService;

import java.util.Timer;
import java.util.TimerTask;


/**
 * Created by Nicola on 2016-12-24.
 */

public class SunshineDataLayerListenerService extends WearableListenerService {
    private final String TAG = SunshineDataLayerListenerService.class.getSimpleName();
    @Override
    public void onDataChanged(DataEventBuffer dataEvents) {
        Log.d(TAG, "onDataChanged: " + dataEvents);
        try{
            ContentValues values = SunshineSyncAdapter.getLastWeatherUpdateForWearable(this);
            WearableUpdatesNotifier notifier = new WearableUpdatesNotifier(this);
            notifier.notifyDataUpdateToWearable(values);
        }catch (Exception e){
            Log.d(TAG,e.getMessage());
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.v(TAG,"on Start command");
        return START_STICKY;
    }
}
