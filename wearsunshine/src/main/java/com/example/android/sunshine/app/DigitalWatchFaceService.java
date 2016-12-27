package com.example.android.sunshine.app;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.content.ContextCompat;
import android.support.wearable.watchface.CanvasWatchFaceService;
import android.support.wearable.watchface.WatchFaceStyle;
import android.text.format.DateFormat;
import android.text.format.DateUtils;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.WindowInsets;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.wearable.Asset;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataItem;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.PutDataMapRequest;
import com.google.android.gms.wearable.PutDataRequest;
import com.google.android.gms.wearable.Wearable;

import java.io.InputStream;
import java.util.Calendar;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

/**
 * Created by Nicola on 2016-12-17.
 */

public class DigitalWatchFaceService extends CanvasWatchFaceService {
    private static final String TAG = "DigitalWatchFaceService";
    @Override
    public Engine onCreateEngine() {
        /* provide your watch face implementation */
        return new Engine();
    }

    /* implement service callback methods */
    private class Engine extends CanvasWatchFaceService.Engine implements DataApi.DataListener,
            GoogleApiClient.ConnectionCallbacks,GoogleApiClient.OnConnectionFailedListener {

        public static final String WEATHER_UPDATE_REQUEST_PATH = "/weather-request";
        public static final String WEATHER_UPDATE_REQUEST_KEY = "weather_request";
        public static final int WEATHER_UPDATE_REQUEST_VALUE = 0;
        public static final String WEATHER_ICON_KEY = "weather_icon";
        public static final String WEATHER_MIN_TEMPERATURE_KEY = "weather_min_temperature";
        public static final String WEATHER_MAX_TEMPERATURE_KEY = "weather_max_temperature";
        public static final String WEATHER_UPDATE_PATH = "/weatherupdate";
        private static final String DATA_ITEM_RECEIVED_PATH = "/data-item-received";

        private GoogleApiClient mGoogleApiClient;
        Context mContext;

        static final int MSG_UPDATE_TIME = 0;
        static final int INTERACTIVE_UPDATE_RATE_MS = 1000;
        static final String COLON_STRING = ":";

        boolean mIsRound;

        String mMinTemp = "";
        String mMaxTemp = "";
        Asset mWeatherAsset;
        Bitmap mWeatherBitmap;

        Calendar mCalendar;

        private float mColonWidth;

        boolean mLowBitAmbient;

        // graphic objects
        Paint mTimePaint;
        Paint mDatePaint;
        int mBitmapSize;
        Paint mWeatherPaint1;
        Paint mWeatherPaint2;

        // handler to update the time once a second in interactive mode
        final Handler mUpdateTimeHandler = new Handler() {
            @Override
            public void handleMessage(Message message) {
                switch (message.what) {
                    case MSG_UPDATE_TIME:
                        invalidate();
                        if (shouldTimerBeRunning()) {
                            long timeMs = System.currentTimeMillis();
                            long delayMs = INTERACTIVE_UPDATE_RATE_MS - (timeMs % INTERACTIVE_UPDATE_RATE_MS);
                            mUpdateTimeHandler
                                    .sendEmptyMessageDelayed(MSG_UPDATE_TIME, delayMs);
                        }
                        break;
                }
            }
        };

        // receiver to update the time zone
        final BroadcastReceiver mTimeZoneReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                mCalendar.setTimeZone(TimeZone.getDefault());
                invalidate();
            }
        };

        @Override
        public void onApplyWindowInsets(WindowInsets insets) {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "onApplyWindowInsets: " + (insets.isRound() ? "round" : "square"));
            }
            super.onApplyWindowInsets(insets);

            // Load resources that have alternate values for round watches.
            mIsRound = insets.isRound();
            float textSize =  getResources().getDimension(mIsRound
                    ? R.dimen.digital_text_size_round : R.dimen.digital_text_size);

            mTimePaint = new Paint();
            mTimePaint.setTextSize(textSize);
            mTimePaint.setARGB(255,255,255,255);

            mDatePaint = new Paint();
            int dateColor = ContextCompat.getColor(getBaseContext(),R.color.primary_light);
            mDatePaint.setARGB(Color.alpha(dateColor),Color.red(dateColor),Color.green(dateColor),Color.blue(dateColor));
            mDatePaint.setTextSize(getResources().getDimension(R.dimen.digital_date_text_size));
            mColonWidth = mTimePaint.measureText(COLON_STRING);

            mBitmapSize = Math.round(getResources().getDimension(mIsRound ? R.dimen.bitmap_size_round : R.dimen.bitmap_size));
            float weatherTextSize = getResources().getDimension(mIsRound ? R.dimen.weather_text_size_round : R.dimen.weather_text_size );
            mWeatherPaint1 = new Paint();
            mWeatherPaint1.setTextSize(weatherTextSize);
            mWeatherPaint1.setARGB(255,255,255,255);

            mWeatherPaint2 = new Paint();
            mWeatherPaint2.setTextSize(weatherTextSize);
            mWeatherPaint2.setARGB(Color.alpha(dateColor),Color.red(dateColor),Color.green(dateColor),Color.blue(dateColor));
        }

        @Override
        public void onCreate(SurfaceHolder holder) {
            super.onCreate(holder);
            mContext = getBaseContext();
            Log.v(TAG,"onCreate");
            /* init google api client */
            mGoogleApiClient = new GoogleApiClient.Builder(DigitalWatchFaceService.this)
                    .addApi(Wearable.API)
                    .addConnectionCallbacks(this)
                    .addOnConnectionFailedListener(this)
                    .build();

            mGoogleApiClient.connect();

            /* initialize your watch face */
            setWatchFaceStyle(new WatchFaceStyle.Builder(DigitalWatchFaceService.this)
                    .setCardPeekMode(WatchFaceStyle.PEEK_MODE_SHORT)
                    .setBackgroundVisibility(WatchFaceStyle
                            .BACKGROUND_VISIBILITY_INTERRUPTIVE)
                    .setShowSystemUiTime(false)
                    .build());
            // allocate a Calendar to calculate local time using the UTC time and time zone
            mCalendar = Calendar.getInstance();
        }

        @Override
        public void onConnected(@Nullable Bundle bundle) {
            Log.v(TAG,"onConnected");
            Wearable.DataApi.addListener(mGoogleApiClient, this);
            requestWeatherInfoToSunshine();
        }

        @Override
        public void onConnectionSuspended(int i) {
            Log.v(TAG,"onConnectionSuspended");
        }

        @Override
        public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
            Log.v(TAG,"onConnectionFailed: " + connectionResult.toString());
            if(connectionResult.getErrorCode() == ConnectionResult.SERVICE_VERSION_UPDATE_REQUIRED){

            }
        }

        @Override
        public void onDataChanged(DataEventBuffer dataEvents) {
            Log.d(TAG, "onDataChanged: " + dataEvents);
            // Loop through the events and send a message back to the node that created the data item.
            for (DataEvent event : dataEvents) {
                if (event.getType() == DataEvent.TYPE_CHANGED) {
                    // DataItem changed
                    DataItem item = event.getDataItem();
                    if (item.getUri().getPath().compareTo(WEATHER_UPDATE_PATH) == 0) {
                        DataMap dataMap = DataMapItem.fromDataItem(item).getDataMap();
                        notifyUpdate(dataMap);
                        sendResponseToNotifier(item);
                    }
                } else if (event.getType() == DataEvent.TYPE_DELETED) {
                    // DataItem deleted
                }
            }
        }

        @Override
        public void onPropertiesChanged(Bundle properties) {
            super.onPropertiesChanged(properties);
            /* get device features (burn-in, low-bit ambient) */
        }

        @Override
        public void onTimeTick() {
            super.onTimeTick();
            /* the time changed */
            invalidate();
            //Log.v(TAG,"onTimeTick");
        }

        @Override
        public void onAmbientModeChanged(boolean inAmbientMode) {
            super.onAmbientModeChanged(inAmbientMode);
            if (mLowBitAmbient) {
                boolean antiAlias = !inAmbientMode;
                mTimePaint.setAntiAlias(antiAlias);
                mDatePaint.setAntiAlias(antiAlias);
                mWeatherPaint1.setAntiAlias(antiAlias);
                mWeatherPaint2.setAntiAlias(antiAlias);
            }
            invalidate();
            updateTimer();
            Log.v(TAG,"Ambient mode changed");
        }

        private boolean checkPlayServices() {
            GoogleApiAvailability googleAPI = GoogleApiAvailability.getInstance();
            int result = googleAPI.isGooglePlayServicesAvailable(getBaseContext());
            if(result != ConnectionResult.SUCCESS) {
                if(googleAPI.isUserResolvableError(result)) {
                    Toast.makeText(getBaseContext(),getString(R.string.play_services_error),Toast.LENGTH_SHORT).show();
                }

                return false;
            }

            return true;
        }

        @Override
        public void onDraw(Canvas canvas, Rect bounds) {
            //Log.v(TAG,"onDraw");
            long now = System.currentTimeMillis();
            int screenWidth = bounds.width();
            int screenHeight = bounds.height();

            float centerX = screenWidth / 2f;
            float centerY = screenHeight / 2f;

            mCalendar.setTimeInMillis(now);

            // Draws the background.
            canvas.drawColor(ContextCompat.getColor(DigitalWatchFaceService.this,R.color.primary));

            // Time
            String hourString = formatTwoDigitNumber(mCalendar.get(Calendar.HOUR_OF_DAY));
            String minuteString = formatTwoDigitNumber(mCalendar.get(Calendar.MINUTE));
            String timeString = hourString + COLON_STRING + minuteString;
            float timeXPosition = (screenWidth - mTimePaint.measureText(timeString))/2;
            float timeYPosition = getResources().getDimension(mIsRound ? R.dimen.digital_y_offset_round : R.dimen.digital_y_offset);
            Log.v(TAG,"iS ROUND: " +mIsRound);

            Log.v(TAG,"Time y pos: " + timeYPosition);
            canvas.drawText(timeString, timeXPosition, timeYPosition, mTimePaint);

            // Date
            String dateString = Utility.getFriendlyDayString(now);
            float dateXPosition = getStartCenteredXPositionForView(bounds,mDatePaint.measureText(dateString));
            float dateYPosition = timeYPosition + mTimePaint.getTextSize()/2 + getResources().getDimension(R.dimen.digital_line_height);
            canvas.drawText(dateString, dateXPosition, dateYPosition, mDatePaint);

            // Line
            int lineWidth = 96;
            float lineXStartPosition = getStartCenteredXPositionForView(bounds,lineWidth);
            float lineXEndPosition = centerX+lineWidth/2;
            canvas.drawLine(lineXStartPosition,dateYPosition + getResources().getDimension(R.dimen.digital_line_height),
                    lineXEndPosition,dateYPosition + getResources().getDimension(R.dimen.digital_line_height),mTimePaint);
            // Weather

            if (mWeatherBitmap!=null){
                String maxTempString = mMaxTemp + " ";

                Bitmap scaled = Bitmap.createScaledBitmap(mWeatherBitmap,mBitmapSize,mBitmapSize, true);
                float weatherRowYPosition = dateYPosition + getResources().getDimension(R.dimen.digital_line_height);
                float bitmapXPosition = getStartCenteredXPositionForView(bounds,(float) scaled.getWidth()
                        + mWeatherPaint1.measureText(maxTempString) + mWeatherPaint2.measureText(mMinTemp) );
                canvas.drawBitmap(scaled,bitmapXPosition,weatherRowYPosition + mBitmapSize/2,mWeatherPaint1);
                float tempStringXPosition = bitmapXPosition + scaled.getWidth() + mWeatherPaint1.measureText(" ");
                float tempStringYPosition = weatherRowYPosition+mWeatherPaint1.getTextSize() + mBitmapSize/2;
                canvas.drawText(maxTempString,tempStringXPosition, tempStringYPosition, mWeatherPaint1);
                canvas.drawText(mMinTemp,tempStringXPosition + mWeatherPaint1.measureText(maxTempString), tempStringYPosition, mWeatherPaint2);
            }
        }


        public float getStartCenteredXPositionForView(Rect bounds, float viewWidth){
            int width = bounds.width();
            int height = bounds.height();
            float centerX = (width - viewWidth) / 2f;
            return centerX;
        }

        private String formatTwoDigitNumber(int hour) {
            return String.format("%02d", hour);
        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            super.onVisibilityChanged(visible);
            Log.v(TAG,"onVisibilityChanged");
            /* the watch face became visible or invisible */
            if (visible) {
                // time zone changed receiver
                IntentFilter filter = new IntentFilter(Intent.ACTION_TIMEZONE_CHANGED);
                registerReceiver(mTimeZoneReceiver,filter);
                // Update time zone in case it changed while we weren't visible.
                mCalendar.setTimeZone(TimeZone.getDefault());
            } else {
                unregisterReceiver(mTimeZoneReceiver);
            }

            // Whether the timer should be running depends on whether we're visible and
            // whether we're in ambient mode, so we may need to start or stop the timer
            updateTimer();
        }

        private void updateTimer() {
            Log.v(TAG,"updateTimer");
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            if (shouldTimerBeRunning()) {
                mUpdateTimeHandler.sendEmptyMessage(MSG_UPDATE_TIME);
            }
        }

        @Override
        public void onSurfaceChanged(
                SurfaceHolder holder, int format, int width, int height) {
            super.onSurfaceChanged(holder, format, width, height);
        }

        private boolean shouldTimerBeRunning() {
            return isVisible() && !isInAmbientMode();
        }


        public void notifyUpdate(DataMap map){
            mMinTemp = map.getString(WEATHER_MIN_TEMPERATURE_KEY);
            mMaxTemp = map.getString(WEATHER_MAX_TEMPERATURE_KEY);
            mWeatherAsset = map.getAsset(WEATHER_ICON_KEY);
            new WeatherCreateBitmapFromAssetTask().execute(mWeatherAsset);
            //loadBitmapFromAsset(mWeatherAsset);
            Log.v(TAG, "min temp: " + mMinTemp);
            Log.v(TAG, "max temp: " + mMaxTemp);
        }

        public void sendResponseToNotifier(DataItem item){
            // Send the RPC
            Uri uri = item.getUri();
            String nodeId = uri.getHost();
            byte[] payload = uri.toString().getBytes();
            Wearable.MessageApi.sendMessage(mGoogleApiClient, nodeId, DATA_ITEM_RECEIVED_PATH, payload);
        }

        public Bitmap loadBitmapFromAsset(Asset asset) {
            if (asset == null) {
                throw new IllegalArgumentException("Asset must be non-null");
            }
            // convert asset into a file descriptor and block until it's ready
            InputStream assetInputStream = Wearable.DataApi.getFdForAsset(mGoogleApiClient, asset).await().getInputStream();

            if (assetInputStream == null) {
                Log.w(TAG, "Requested an unknown Asset.");
                return null;
            }
            // decode the stream into a bitmap
            return BitmapFactory.decodeStream(assetInputStream);
        }

        class WeatherCreateBitmapFromAssetTask extends AsyncTask<Asset,Void,Bitmap>{
            private Asset mAsset;
            @Override
            protected Bitmap doInBackground(Asset... assets) {
                mAsset = assets[0];
                Bitmap bitmap = loadBitmapFromAsset(mAsset);
                return bitmap;
            }

            protected void onPostExecute(Bitmap weatherImage) {
                mWeatherBitmap = weatherImage;
                invalidate();
            }
        }

        public void requestWeatherInfoToSunshine(){
            PutDataMapRequest putDataMapRequest = PutDataMapRequest.create(WEATHER_UPDATE_REQUEST_PATH);
            putDataMapRequest.getDataMap().putLong(WEATHER_UPDATE_REQUEST_KEY, System.currentTimeMillis());
            Log.v(TAG,"request info to sunshine");
            PutDataRequest putDataRequest = putDataMapRequest.asPutDataRequest();
            putDataRequest.setUrgent();
            Wearable.DataApi.putDataItem(mGoogleApiClient, putDataRequest);
            Log.d(TAG,String.valueOf(mGoogleApiClient.isConnected()));
        }
    }
}
