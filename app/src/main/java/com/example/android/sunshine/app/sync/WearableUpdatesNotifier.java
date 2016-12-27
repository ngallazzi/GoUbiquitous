package com.example.android.sunshine.app.sync;

import android.content.ContentValues;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.support.annotation.NonNull;
import android.util.Log;

import com.example.android.sunshine.app.Utility;
import com.example.android.sunshine.app.data.WeatherContract;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.wearable.Asset;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.PutDataMapRequest;
import com.google.android.gms.wearable.PutDataRequest;
import com.google.android.gms.wearable.Wearable;

/**
 * Created by Nicola on 2016-12-24.
 */

public class WearableUpdatesNotifier implements GoogleApiClient.OnConnectionFailedListener {
    private final String TAG = WearableUpdatesNotifier.class.getSimpleName();

    private static final String WEATHER_ICON_KEY = "weather_icon";
    private static final String WEATHER_MIN_TEMPERATURE_KEY = "weather_min_temperature";
    private static final String WEATHER_MAX_TEMPERATURE_KEY = "weather_max_temperature";
    private static final String WEATHER_UPDATE_PATH = "/weatherupdate";
    private Context mContext;
    private GoogleApiClient mGoogleApiClient;

    public WearableUpdatesNotifier(Context context) {
        mContext = context;
        mGoogleApiClient = new GoogleApiClient.Builder(context)
                .addApi(Wearable.API)
                .addOnConnectionFailedListener(this)
                .build();
        mGoogleApiClient.connect();
    }

    public void notifyDataUpdateToWearable(ContentValues values) {
        try{
            PutDataMapRequest putDataMapReq = PutDataMapRequest.create(WEATHER_UPDATE_PATH);
            int weatherId = values.getAsInteger(WeatherContract.WeatherEntry.COLUMN_WEATHER_ID);
            String maxTemp = values.getAsString(WeatherContract.WeatherEntry.COLUMN_MAX_TEMP);
            String minTemp = values.getAsString(WeatherContract.WeatherEntry.COLUMN_MIN_TEMP);
            int resource = Utility.getArtResourceForWeatherCondition(weatherId);
            Bitmap bitmap = BitmapFactory.decodeResource(mContext.getResources(), resource);
            Asset asset = Utility.createAssetFromBitmap(bitmap);
            putDataMapReq.getDataMap().putAsset(WEATHER_ICON_KEY, asset);
            putDataMapReq.getDataMap().putString(WEATHER_MIN_TEMPERATURE_KEY, minTemp);
            putDataMapReq.getDataMap().putString(WEATHER_MAX_TEMPERATURE_KEY, maxTemp);
            putDataMapReq.getDataMap().putLong("Time",System.currentTimeMillis());

            PutDataRequest putDataReq = putDataMapReq.asPutDataRequest();
            putDataReq.setUrgent();
            PendingResult<DataApi.DataItemResult> pendingResult = Wearable.DataApi.putDataItem(mGoogleApiClient, putDataReq);
            Log.v(TAG,"Update notified: ");
            pendingResult.setResultCallback(new ResultCallback<DataApi.DataItemResult>() {
                @Override
                public void onResult(final DataApi.DataItemResult result) {
                    if(result.getStatus().isSuccess()) {
                        Log.d(TAG, "Data item set: " + result.getDataItem().getUri());
                    }
                }
            });
        }catch (Exception e){
            Log.d(TAG,e.getMessage());
        }
    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {

    }
}
