package com.example.android.sunshine.app;

import android.content.Context;
import android.text.format.Time;

import java.text.SimpleDateFormat;

/**
 * Created by Nicola on 2016-12-23.
 */

public class Utility {
    public static final String DATE_FORMAT = "yyyyMMdd";

    public static String getFriendlyDayString(long dateInMillis) {
        // is "Today, June 24"
            // Otherwise, use the form "Mon Jun 3"
        SimpleDateFormat shortenedDateFormat = new SimpleDateFormat("EEE, MMM dd yyyy");
        return shortenedDateFormat.format(dateInMillis).toUpperCase();

    }
}
