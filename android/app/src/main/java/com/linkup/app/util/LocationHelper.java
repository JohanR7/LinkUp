package com.linkup.app.util;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import java.util.Locale;
import java.util.function.Consumer;

/**
 * Location through Android's own LocationManager. The position is only used on the phone
 * to measure distances; it is never sent to the server or shown to other people.
 */
public final class LocationHelper {

    /** Used to centre the map when nothing better is known (Bengaluru). */
    public static final double DEFAULT_LAT = 12.9716;
    public static final double DEFAULT_LNG = 77.5946;

    private static final long FIX_TIMEOUT_MS = 10_000;

    private LocationHelper() {
    }

    public static boolean hasPermission(Context context) {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    /**
     * Where distances are measured from: the newest known device location (if allowed in
     * Settings and permitted), otherwise the home area from Settings. Null if neither is known.
     */
    @SuppressLint("MissingPermission")
    public static Location getReferenceLocation(Context context) {
        SessionManager session = new SessionManager(context);
        if (session.useDeviceLocation() && hasPermission(context)) {
            LocationManager manager = context.getSystemService(LocationManager.class);
            Location best = null;
            for (String provider : manager.getProviders(true)) {
                Location location = manager.getLastKnownLocation(provider);
                if (location != null && (best == null || location.getTime() > best.getTime())) best = location;
            }
            if (best != null) return best;
        }
        if (session.hasHomeLocation()) {
            Location home = new Location("home");
            home.setLatitude(session.getHomeLat());
            home.setLongitude(session.getHomeLng());
            return home;
        }
        return null;
    }

    /** Asks GPS (or the network) for one fresh position. The callback gets null after 10 s without a fix. */
    @SuppressLint("MissingPermission")
    public static void requestFreshLocation(Context context, Consumer<Location> callback) {
        if (!hasPermission(context)) {
            callback.accept(null);
            return;
        }
        LocationManager manager = context.getSystemService(LocationManager.class);
        String provider = manager.isProviderEnabled(LocationManager.GPS_PROVIDER) ? LocationManager.GPS_PROVIDER
                : manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) ? LocationManager.NETWORK_PROVIDER : null;
        if (provider == null) {
            callback.accept(null);
            return;
        }

        boolean[] done = {false};
        LocationListener listener = new LocationListener() {
            @Override
            public void onLocationChanged(@NonNull Location location) {
                if (done[0]) return;
                done[0] = true;
                manager.removeUpdates(this);
                callback.accept(location);
            }

            // Older Android versions require these three methods to exist
            @Override
            public void onStatusChanged(String provider, int status, Bundle extras) {
            }

            @Override
            public void onProviderEnabled(@NonNull String provider) {
            }

            @Override
            public void onProviderDisabled(@NonNull String provider) {
            }
        };
        manager.requestLocationUpdates(provider, 0, 0, listener, Looper.getMainLooper());
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            if (done[0]) return;
            done[0] = true;
            manager.removeUpdates(listener);
            callback.accept(null);
        }, FIX_TIMEOUT_MS);
    }

    public static double distanceKm(double lat1, double lng1, double lat2, double lng2) {
        float[] result = new float[1];
        Location.distanceBetween(lat1, lng1, lat2, lng2, result);
        return result[0] / 1000.0;
    }

    /** "450 m" or "3.2 km"; empty when the distance is unknown. */
    public static String formatDistance(double km) {
        if (km < 0) return "";
        if (km < 1) return Math.max(10, Math.round(km * 100) * 10) + " m";
        return String.format(Locale.getDefault(), km < 10 ? "%.1f km" : "%.0f km", km);
    }
}
