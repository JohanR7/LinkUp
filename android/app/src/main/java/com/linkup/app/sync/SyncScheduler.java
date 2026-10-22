package com.linkup.app.sync;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;

/**
 * Wakes the app roughly every 15 minutes to sync, so join requests and approvals
 * arrive as notifications even when the app is closed. This is how LinkUp gets
 * "push"-style notifications without a push service.
 */
public final class SyncScheduler {

    private SyncScheduler() {
    }

    public static void schedule(Context context) {
        AlarmManager alarms = context.getSystemService(AlarmManager.class);
        Intent intent = new Intent(context, SyncAlarmReceiver.class);
        PendingIntent pending = PendingIntent.getBroadcast(context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        alarms.setInexactRepeating(AlarmManager.ELAPSED_REALTIME,
                SystemClock.elapsedRealtime() + AlarmManager.INTERVAL_FIFTEEN_MINUTES,
                AlarmManager.INTERVAL_FIFTEEN_MINUTES, pending);
    }
}
