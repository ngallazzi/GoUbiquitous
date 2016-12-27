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
    public void onCreate() {
        super.onCreate();
        Timer timer = new Timer();
//timer.schedule(task, delay, period)
//timer.schedule( new performClass(), 1000, 30000 );
// or you can write in another way
//timer.scheduleAtFixedRate(task, delay, period);
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                Log.v(TAG,"I'm alive");
            }
        }, 1000, 5000);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.v(TAG,"on Start command");
        return START_STICKY;
    }
}
