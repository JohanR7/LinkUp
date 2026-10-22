package com.linkup.app.ui.common;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.provider.CalendarContract;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.linkup.app.R;
import com.linkup.app.data.Repository;
import com.linkup.app.model.Categories;
import com.linkup.app.model.Plan;
import com.linkup.app.sync.SyncManager;
import com.linkup.app.util.TimeUtils;

/**
 * Actions offered from several screens (feed context menu, map, details):
 * implicit intents to other apps, plus report and block.
 */
public final class PlanActions {

    private static final long DEFAULT_DURATION_MS = 2 * 60 * 60 * 1000L;

    private PlanActions() {
    }

    /** Implicit intent: any maps app that understands geo: URIs. Falls back to the OpenStreetMap website. */
    public static void openInMaps(Context context, Plan plan) {
        Uri geo = Uri.parse("geo:" + plan.lat + "," + plan.lng + "?q=" + plan.lat + "," + plan.lng
                + "(" + Uri.encode(plan.placeName) + ")");
        try {
            context.startActivity(new Intent(Intent.ACTION_VIEW, geo));
        } catch (ActivityNotFoundException e) {
            Uri web = Uri.parse("https://www.openstreetmap.org/?mlat=" + plan.lat + "&mlon=" + plan.lng
                    + "#map=17/" + plan.lat + "/" + plan.lng);
            try {
                context.startActivity(new Intent(Intent.ACTION_VIEW, web));
            } catch (ActivityNotFoundException e2) {
                Toast.makeText(context, R.string.error_no_app, Toast.LENGTH_SHORT).show();
            }
        }
    }

    /** Implicit intent: the system share sheet (WhatsApp, Messages, ...). */
    public static void share(Context context, Plan plan) {
        String text = Categories.emojiFor(plan.category) + " " + plan.title + "\n"
                + "🕒 " + TimeUtils.formatFull(plan.startTime) + "\n"
                + "📍 " + plan.placeName + "\n"
                + "Join me on LinkUp!";
        Intent send = new Intent(Intent.ACTION_SEND)
                .setType("text/plain")
                .putExtra(Intent.EXTRA_SUBJECT, plan.title)
                .putExtra(Intent.EXTRA_TEXT, text);
        context.startActivity(Intent.createChooser(send, context.getString(R.string.action_share)));
    }

    /** Implicit intent: the calendar app's "new event" screen, pre-filled. */
    public static void addToCalendar(Context context, Plan plan) {
        Intent insert = new Intent(Intent.ACTION_INSERT)
                .setData(CalendarContract.Events.CONTENT_URI)
                .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, plan.startTime)
                .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, plan.startTime + DEFAULT_DURATION_MS)
                .putExtra(CalendarContract.Events.TITLE, plan.title)
                .putExtra(CalendarContract.Events.EVENT_LOCATION, plan.placeName)
                .putExtra(CalendarContract.Events.DESCRIPTION, plan.description);
        try {
            context.startActivity(insert);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(context, R.string.error_no_calendar, Toast.LENGTH_SHORT).show();
        }
    }

    /** Report dialog: pick a reason (radio buttons built from an array resource) and add details. */
    public static void report(Context context, String userUuid, @Nullable String planUuid, String what,
                              @Nullable Runnable onReported) {
        View view = LayoutInflater.from(context).inflate(R.layout.dialog_report, null);
        RadioGroup reasons = view.findViewById(R.id.report_reasons);
        EditText details = view.findViewById(R.id.report_details);
        for (String reason : context.getResources().getStringArray(R.array.report_reasons)) {
            RadioButton button = new RadioButton(context);
            button.setId(View.generateViewId());
            button.setText(reason);
            reasons.addView(button);
        }

        AlertDialog dialog = new MaterialAlertDialogBuilder(context)
                .setTitle(context.getString(R.string.report_title, what))
                .setView(view)
                .setPositiveButton(R.string.report_send, null)
                .setNegativeButton(R.string.action_cancel, null)
                .create();
        // Override the button so the dialog stays open until a reason is picked
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            int checked = reasons.getCheckedRadioButtonId();
            if (checked == View.NO_ID) {
                Toast.makeText(context, R.string.report_pick_reason, Toast.LENGTH_SHORT).show();
                return;
            }
            String reason = ((RadioButton) reasons.findViewById(checked)).getText().toString();
            new Repository(context).addReport(userUuid, planUuid, reason, details.getText().toString().trim());
            SyncManager.syncAsync(context, null);
            dialog.dismiss();
            Toast.makeText(context, R.string.report_thanks, Toast.LENGTH_LONG).show();
            if (onReported != null) onReported.run();
        }));
        dialog.show();
    }

    public static void block(Context context, String userUuid, String name, @Nullable Runnable onBlocked) {
        Dialogs.confirm(context,
                context.getString(R.string.block_title, name),
                context.getString(R.string.block_message),
                context.getString(R.string.action_block),
                () -> {
                    new Repository(context).block(userUuid);
                    Toast.makeText(context, context.getString(R.string.block_done, name), Toast.LENGTH_SHORT).show();
                    if (onBlocked != null) onBlocked.run();
                });
    }
}
