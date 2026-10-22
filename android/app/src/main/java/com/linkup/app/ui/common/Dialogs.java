package com.linkup.app.ui.common;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.linkup.app.R;

/** Reusable dialogs: progress, confirmation, information and the safety tips. */
public final class Dialogs {

    private Dialogs() {
    }

    /**
     * A non-cancelable "please wait" dialog (spinner + message).
     * Android's old ProgressDialog class is deprecated; an AlertDialog with a
     * ProgressBar layout is the recommended replacement.
     */
    public static AlertDialog progress(Context context, String message) {
        View view = LayoutInflater.from(context).inflate(R.layout.dialog_progress, null);
        ((TextView) view.findViewById(R.id.progress_message)).setText(message);
        AlertDialog dialog = new MaterialAlertDialogBuilder(context)
                .setView(view)
                .setCancelable(false)
                .create();
        dialog.show();
        return dialog;
    }

    public static void confirm(Context context, String title, String message, String yesLabel, Runnable onYes) {
        new MaterialAlertDialogBuilder(context)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton(yesLabel, (d, which) -> onYes.run())
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    public static void info(Context context, String title, String message) {
        new MaterialAlertDialogBuilder(context)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton(R.string.action_ok, null)
                .show();
    }

    /** Shown before someone's first join request, and from the Safety screen. */
    public static void safetyTips(Context context, Runnable onContinue) {
        new MaterialAlertDialogBuilder(context)
                .setIcon(R.drawable.ic_shield)
                .setTitle(R.string.safety_title)
                .setMessage(R.string.safety_tips_short)
                .setCancelable(false)
                .setPositiveButton(R.string.safety_understand, (d, which) -> {
                    if (onContinue != null) onContinue.run();
                })
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }
}
