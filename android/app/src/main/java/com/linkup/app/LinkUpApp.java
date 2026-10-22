package com.linkup.app;

import android.app.Application;
import android.os.Handler;
import android.os.Looper;

import com.linkup.app.notify.NotificationHelper;
import com.linkup.app.sync.SyncScheduler;

import org.osmdroid.config.Configuration;
import org.osmdroid.config.IConfigurationProvider;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Application class: runs once when the app process starts, before any screen.
 * Sets up notification channels, the osmdroid map library and background sync,
 * and offers a small thread pool so database/network work stays off the UI thread.
 */
public class LinkUpApp extends Application {

    private static final ExecutorService BACKGROUND = Executors.newFixedThreadPool(3);
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    public static void runInBackground(Runnable task) {
        BACKGROUND.execute(task);
    }

    public static void runOnMain(Runnable task) {
        MAIN.post(task);
    }

    @Override
    public void onCreate() {
        super.onCreate();

        NotificationHelper.createChannels(this);

        // OpenStreetMap asks every app to identify itself; tiles are cached in app storage
        IConfigurationProvider osm = Configuration.getInstance();
        osm.setUserAgentValue(getPackageName());
        osm.setOsmdroidBasePath(new File(getCacheDir(), "osmdroid"));
        osm.setOsmdroidTileCache(new File(getCacheDir(), "osmdroid/tiles"));

        SyncScheduler.schedule(this);
    }
}
