package com.linkup.app.sync;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.linkup.app.notify.ReminderScheduler;

/** Alarms are lost when the phone restarts, so set them up again. */
public class BootReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            SyncScheduler.schedule(context);
            ReminderScheduler.refresh(context);
        }
    }
}
