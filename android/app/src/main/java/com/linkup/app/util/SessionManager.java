package com.linkup.app.util;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Small key-value settings stored in SharedPreferences: who is logged in, the server
 * address, the sync position and the user's app settings.
 */
public class SessionManager {

    public static final String DEFAULT_SERVER = "http://10.0.2.2:7070";

    private static final String PREFS = "linkup_session";
    private static final String KEY_TOKEN = "token";
    private static final String KEY_USER_ID = "user_id";
    private static final String KEY_NAME = "name";
    private static final String KEY_EMAIL = "email";
    private static final String KEY_SERVER = "server_url";
    private static final String KEY_LAST_REV = "last_rev";
    private static final String KEY_REMINDERS = "reminders";
    private static final String KEY_USE_LOCATION = "use_location";
    private static final String KEY_RADIUS = "radius_km";
    private static final String KEY_HOME_LAT = "home_lat";
    private static final String KEY_HOME_LNG = "home_lng";
    private static final String KEY_SAFETY_SEEN = "safety_seen";

    private final SharedPreferences prefs;

    public SessionManager(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    // ---- login

    public boolean isLoggedIn() {
        return getToken() != null && getUserId() != null;
    }

    public void saveLogin(String token, String userId, String name, String email) {
        prefs.edit()
                .putString(KEY_TOKEN, token)
                .putString(KEY_USER_ID, userId)
                .putString(KEY_NAME, name)
                .putString(KEY_EMAIL, email)
                .putLong(KEY_LAST_REV, 0)   // a fresh login downloads everything again
                .apply();
    }

    public void logout() {
        prefs.edit()
                .remove(KEY_TOKEN)
                .remove(KEY_USER_ID)
                .remove(KEY_NAME)
                .remove(KEY_EMAIL)
                .remove(KEY_LAST_REV)
                .apply();
    }

    public String getToken() {
        return prefs.getString(KEY_TOKEN, null);
    }

    public String getUserId() {
        return prefs.getString(KEY_USER_ID, null);
    }

    public String getName() {
        return prefs.getString(KEY_NAME, "");
    }

    public void setName(String name) {
        prefs.edit().putString(KEY_NAME, name).apply();
    }

    public String getEmail() {
        return prefs.getString(KEY_EMAIL, "");
    }

    // ---- server and sync

    public String getServerUrl() {
        return prefs.getString(KEY_SERVER, DEFAULT_SERVER);
    }

    public void setServerUrl(String url) {
        prefs.edit().putString(KEY_SERVER, url).apply();
    }

    public long getLastRev() {
        return prefs.getLong(KEY_LAST_REV, 0);
    }

    public void setLastRev(long rev) {
        prefs.edit().putLong(KEY_LAST_REV, rev).apply();
    }

    // ---- user settings

    public boolean remindersEnabled() {
        return prefs.getBoolean(KEY_REMINDERS, true);
    }

    public void setRemindersEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_REMINDERS, enabled).apply();
    }

    /** Privacy setting: when off, distances are measured from the home area instead of GPS. */
    public boolean useDeviceLocation() {
        return prefs.getBoolean(KEY_USE_LOCATION, true);
    }

    public void setUseDeviceLocation(boolean use) {
        prefs.edit().putBoolean(KEY_USE_LOCATION, use).apply();
    }

    public int getRadiusKm() {
        return prefs.getInt(KEY_RADIUS, 10);
    }

    public void setRadiusKm(int km) {
        prefs.edit().putInt(KEY_RADIUS, km).apply();
    }

    public boolean hasHomeLocation() {
        return prefs.contains(KEY_HOME_LAT);
    }

    public double getHomeLat() {
        return Double.longBitsToDouble(prefs.getLong(KEY_HOME_LAT, Double.doubleToLongBits(0)));
    }

    public double getHomeLng() {
        return Double.longBitsToDouble(prefs.getLong(KEY_HOME_LNG, Double.doubleToLongBits(0)));
    }

    public void setHomeLocation(double lat, double lng) {
        prefs.edit()
                .putLong(KEY_HOME_LAT, Double.doubleToLongBits(lat))
                .putLong(KEY_HOME_LNG, Double.doubleToLongBits(lng))
                .apply();
    }

    /** The safety tips dialog is shown before the first join request. */
    public boolean hasSeenSafetyTips() {
        return prefs.getBoolean(KEY_SAFETY_SEEN, false);
    }

    public void setSeenSafetyTips() {
        prefs.edit().putBoolean(KEY_SAFETY_SEEN, true).apply();
    }
}
