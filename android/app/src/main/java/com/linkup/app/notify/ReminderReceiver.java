package com.linkup.app.notify;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.linkup.app.data.Repository;
import com.linkup.app.model.Plan;
import com.linkup.app.util.SessionManager;
import com.linkup.app.util.TimeUtils;

/** Fired by AlarmManager shortly before an activity starts. */
public class ReminderReceiver extends BroadcastReceiver {

    public static final String EXTRA_PLAN_UUID = "plan_uuid";

    @Override
    public void onReceive(Context context, Intent intent) {
        String planUuid = intent.getStringExtra(EXTRA_PLAN_UUID);
        if (planUuid == null || !new SessionManager(context).isLoggedIn()) return;

        Repository repo = new Repository(context);
        Plan plan = repo.getPlan(planUuid);
        // The plan may have been cancelled, or I left, since the alarm was set
        if (plan == null || !Plan.STATUS_ACTIVE.equals(plan.status)
                || !(plan.isHostedBy(repo.myId()) || plan.amGoing())) {
            return;
        }

        String title = "Starting soon: " + plan.title;
        String body = TimeUtils.formatTime(plan.startTime) + " at " + plan.placeName
                + ". Meet in the public spot and check the chat for updates.";
        long rowId = repo.addNotification(NotificationHelper.TYPE_REMINDER, title, body, plan.uuid);
        NotificationHelper.showPlanNotification(context, (int) rowId, NotificationHelper.CHANNEL_REMINDERS,
                title, body, plan.uuid, false);
    }
}
