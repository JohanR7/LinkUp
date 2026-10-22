package com.linkup.app.notify;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import com.linkup.app.data.Repository;
import com.linkup.app.model.Plan;
import com.linkup.app.util.SessionManager;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Sets an AlarmManager alarm 30 minutes before each activity I host or was approved for.
 * refresh() is called after every sync and after joining/leaving, so alarms always match the data.
 */
public final class ReminderScheduler {

    public static final long REMIND_BEFORE_MS = 30 * 60 * 1000L;

    private static final String PREFS = "reminders";
    private static final String KEY_SCHEDULED = "scheduled";

    private ReminderScheduler() {
    }

    public static void refresh(Context context) {
        AlarmManager alarms = context.getSystemService(AlarmManager.class);
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        Set<String> previous = new HashSet<>(prefs.getStringSet(KEY_SCHEDULED, Collections.emptySet()));
        Set<String> scheduled = new HashSet<>();

        SessionManager session = new SessionManager(context);
        if (session.isLoggedIn() && session.remindersEnabled()) {
            long now = System.currentTimeMillis();
            for (Plan plan : new Repository(context).getMyUpcomingPlans()) {
                long at = plan.startTime - REMIND_BEFORE_MS;
                if (at <= now) continue;
                // Inexact but battery friendly; exact alarms need an extra special permission
                alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, reminderIntent(context, plan.uuid));
                scheduled.add(plan.uuid);
            }
        }
        for (String uuid : previous) {
            if (!scheduled.contains(uuid)) alarms.cancel(reminderIntent(context, uuid));
        }
        prefs.edit().putStringSet(KEY_SCHEDULED, scheduled).apply();
    }

    private static PendingIntent reminderIntent(Context context, String planUuid) {
        Intent intent = new Intent(context, ReminderReceiver.class);
        intent.putExtra(ReminderReceiver.EXTRA_PLAN_UUID, planUuid);
        return PendingIntent.getBroadcast(context, planUuid.hashCode(), intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
}
