package com.linkup.app.sync;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.linkup.app.LinkUpApp;

/** Runs a background sync when the SyncScheduler alarm fires. */
public class SyncAlarmReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        // goAsync keeps the receiver alive while the network call runs on another thread
        PendingResult result = goAsync();
        Context app = context.getApplicationContext();
        LinkUpApp.runInBackground(() -> {
            try {
                SyncManager.syncNow(app);
            } catch (ApiException e) {
                Log.d("LinkUpSync", "Background sync skipped: " + e.getMessage());
            } finally {
                result.finish();
            }
        });
    }
}
