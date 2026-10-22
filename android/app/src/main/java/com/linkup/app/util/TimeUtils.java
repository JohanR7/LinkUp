package com.linkup.app.util;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/** Friendly date/time text: "Today", "in 2 h", "5 min ago"... */
public final class TimeUtils {

    private static final long MINUTE = 60_000L;
    private static final long HOUR = 60 * MINUTE;
    private static final long DAY = 24 * HOUR;

    private TimeUtils() {
    }

    private static String format(String pattern, long millis) {
        return new SimpleDateFormat(pattern, Locale.getDefault()).format(new Date(millis));
    }

    public static String formatTime(long millis) {
        return format("h:mm a", millis);
    }

    public static String formatDate(long millis) {
        return format("EEE, d MMM yyyy", millis);
    }

    /** "Today", "Tomorrow" or "Sat, 26 Oct" */
    public static String formatDay(long millis) {
        int diff = daysFromToday(millis);
        if (diff == 0) return "Today";
        if (diff == 1) return "Tomorrow";
        if (diff == -1) return "Yesterday";
        return format("EEE, d MMM", millis);
    }

    /** "Today, 20 Oct" / "Tomorrow, 21 Oct" / "Sat, 24 Oct" (short enough for a button) */
    public static String formatDayWithDate(long millis) {
        int diff = daysFromToday(millis);
        return diff == 0 || diff == 1 ? formatDay(millis) + ", " + format("d MMM", millis) : formatDay(millis);
    }

    /** "Today · 6:30 PM" */
    public static String formatWhen(long millis) {
        return formatDay(millis) + " · " + formatTime(millis);
    }

    /** "Saturday, 26 October · 6:30 PM" */
    public static String formatFull(long millis) {
        return format("EEEE, d MMMM", millis) + " · " + formatTime(millis);
    }

    /** "in 25 min", "in 3 h", "in 2 days", "started" */
    public static String relative(long millis) {
        long diff = millis - System.currentTimeMillis();
        if (diff < 0) return diff > -2 * HOUR ? "happening now" : "ended";
        if (diff < HOUR) return "in " + Math.max(1, diff / MINUTE) + " min";
        if (diff < DAY) return "in " + Math.round(diff / (double) HOUR) + " h";
        long days = Math.round(diff / (double) DAY);
        return "in " + days + (days == 1 ? " day" : " days");
    }

    /** "just now", "5 min ago", "3 h ago", "26 Oct" */
    public static String ago(long millis) {
        long diff = System.currentTimeMillis() - millis;
        if (diff < MINUTE) return "just now";
        if (diff < HOUR) return diff / MINUTE + " min ago";
        if (diff < DAY) return diff / HOUR + " h ago";
        return format("d MMM", millis);
    }

    /** Chat timestamps: time today, otherwise date and time. */
    public static String chatTime(long millis) {
        return daysFromToday(millis) == 0 ? formatTime(millis) : format("d MMM, h:mm a", millis);
    }

    public static int daysFromToday(long millis) {
        Calendar today = startOfDay(System.currentTimeMillis());
        Calendar other = startOfDay(millis);
        return (int) Math.round((other.getTimeInMillis() - today.getTimeInMillis()) / (double) DAY);
    }

    public static Calendar startOfDay(long millis) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(millis);
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c;
    }
}
