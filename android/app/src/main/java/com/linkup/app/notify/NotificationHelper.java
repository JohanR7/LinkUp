package com.linkup.app.notify;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.app.TaskStackBuilder;
import androidx.core.content.ContextCompat;

import com.linkup.app.R;
import com.linkup.app.ui.main.MainActivity;
import com.linkup.app.ui.plan.ChatActivity;
import com.linkup.app.ui.plan.PlanDetailsActivity;

/** Creates the notification channels and posts notifications through NotificationManager. */
public final class NotificationHelper {

    public static final String CHANNEL_REQUESTS = "requests";
    public static final String CHANNEL_UPDATES = "updates";
    public static final String CHANNEL_CHAT = "chat";
    public static final String CHANNEL_REMINDERS = "reminders";

    public static final String TYPE_REQUEST = "request";
    public static final String TYPE_JOIN = "join";
    public static final String TYPE_APPROVAL = "approval";
    public static final String TYPE_UPDATE = "update";
    public static final String TYPE_CHAT = "chat";
    public static final String TYPE_REMINDER = "reminder";

    private NotificationHelper() {
    }

    /** Android 8+ needs channels before notifying; users can mute each one separately. */
    public static void createChannels(Context context) {
        NotificationManager manager = context.getSystemService(NotificationManager.class);

        NotificationChannel requests = new NotificationChannel(CHANNEL_REQUESTS,
                "Join requests & approvals", NotificationManager.IMPORTANCE_HIGH);
        requests.setDescription("When someone asks to join your activity, or a host answers your request");

        NotificationChannel updates = new NotificationChannel(CHANNEL_UPDATES,
                "Activity changes", NotificationManager.IMPORTANCE_DEFAULT);
        updates.setDescription("Time or place changes and cancellations");

        NotificationChannel chat = new NotificationChannel(CHANNEL_CHAT,
                "Chat messages", NotificationManager.IMPORTANCE_DEFAULT);

        NotificationChannel reminders = new NotificationChannel(CHANNEL_REMINDERS,
                "Reminders", NotificationManager.IMPORTANCE_HIGH);
        reminders.setDescription("30 minutes before an activity you're going to");

        manager.createNotificationChannel(requests);
        manager.createNotificationChannel(updates);
        manager.createNotificationChannel(chat);
        manager.createNotificationChannel(reminders);
    }

    public static String channelFor(String type) {
        switch (type) {
            case TYPE_REQUEST:
            case TYPE_JOIN:
            case TYPE_APPROVAL:
                return CHANNEL_REQUESTS;
            case TYPE_CHAT:
                return CHANNEL_CHAT;
            case TYPE_REMINDER:
                return CHANNEL_REMINDERS;
            default:
                return CHANNEL_UPDATES;
        }
    }

    /** Android 13+ requires the POST_NOTIFICATIONS runtime permission. */
    public static boolean canNotify(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            return false;
        }
        return NotificationManagerCompat.from(context).areNotificationsEnabled();
    }

    @SuppressLint("MissingPermission")   // checked in canNotify
    public static void showPlanNotification(Context context, int id, String channel, String title, String body,
                                            String planUuid, boolean openChat) {
        if (!canNotify(context)) return;
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, channel)
                .setSmallIcon(R.drawable.ic_notification)
                .setColor(ContextCompat.getColor(context, R.color.primary))
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(body))
                .setPriority(CHANNEL_CHAT.equals(channel) ? NotificationCompat.PRIORITY_DEFAULT : NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(contentIntent(context, planUuid, openChat, id));
        NotificationManagerCompat.from(context).notify(id, builder.build());
    }

    /**
     * Tapping a notification opens the plan (or its chat) with Main underneath,
     * so Back goes through the app's normal screens.
     */
    private static PendingIntent contentIntent(Context context, String planUuid, boolean openChat, int requestCode) {
        TaskStackBuilder stack = TaskStackBuilder.create(context);
        stack.addNextIntent(new Intent(context, MainActivity.class));
        if (planUuid != null) {
            stack.addNextIntent(PlanDetailsActivity.intent(context, planUuid));
            if (openChat) stack.addNextIntent(ChatActivity.intent(context, planUuid));
        }
        return stack.getPendingIntent(requestCode, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    public static int chatNotificationId(String planUuid) {
        return ("chat:" + planUuid).hashCode();
    }

    public static void cancel(Context context, int id) {
        NotificationManagerCompat.from(context).cancel(id);
    }
}
