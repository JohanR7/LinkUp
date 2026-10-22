package com.linkup.app.ui.settings;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.SwitchCompat;

import com.linkup.app.LinkUpApp;
import com.linkup.app.R;
import com.linkup.app.notify.ReminderScheduler;
import com.linkup.app.sync.ApiClient;
import com.linkup.app.sync.ApiException;
import com.linkup.app.sync.SyncManager;
import com.linkup.app.ui.common.BaseActivity;
import com.linkup.app.ui.common.Dialogs;
import com.linkup.app.ui.plan.LocationPickerActivity;
import com.linkup.app.util.SessionManager;

import java.util.Locale;

/**
 * Server address (reachable before login), privacy and notification switches,
 * the default search radius, the home area, and database/sync tools.
 */
public class SettingsActivity extends BaseActivity {

    private EditText serverInput;
    private TextView radiusLabel, homeLabel;

    private final ActivityResultLauncher<Intent> pickHome = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    session().setHomeLocation(result.getData().getDoubleExtra(LocationPickerActivity.EXTRA_LAT, 0),
                            result.getData().getDoubleExtra(LocationPickerActivity.EXTRA_LNG, 0));
                    updateHomeLabel();
                    toast(R.string.settings_home_saved);
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        setupToolbar(true);
        SessionManager s = session();

        // --- server
        serverInput = findViewById(R.id.settings_server);
        if (savedInstanceState == null) serverInput.setText(s.getServerUrl());
        findViewById(R.id.settings_server_save).setOnClickListener(v -> saveServer());
        findViewById(R.id.settings_server_test).setOnClickListener(v -> testServer());
        findViewById(R.id.settings_server_reset).setOnClickListener(v -> serverInput.setText(SessionManager.DEFAULT_SERVER));

        // --- switches
        SwitchCompat reminders = findViewById(R.id.settings_reminders);
        reminders.setChecked(s.remindersEnabled());
        reminders.setOnCheckedChangeListener((button, checked) -> {
            s.setRemindersEnabled(checked);
            ReminderScheduler.refresh(this);
        });

        SwitchCompat useLocation = findViewById(R.id.settings_use_location);
        useLocation.setChecked(s.useDeviceLocation());
        useLocation.setOnCheckedChangeListener((button, checked) -> s.setUseDeviceLocation(checked));

        // --- radius
        radiusLabel = findViewById(R.id.settings_radius_label);
        SeekBar radius = findViewById(R.id.settings_radius);
        radius.setProgress(s.getRadiusKm());
        updateRadiusLabel(s.getRadiusKm());
        radius.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                updateRadiusLabel(Math.max(1, progress));
            }

            @Override
            public void onStartTrackingTouch(SeekBar bar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar bar) {
                s.setRadiusKm(Math.max(1, bar.getProgress()));
            }
        });

        // --- home area
        homeLabel = findViewById(R.id.settings_home_label);
        updateHomeLabel();
        findViewById(R.id.settings_home_pick).setOnClickListener(v -> {
            Intent intent = new Intent(this, LocationPickerActivity.class)
                    .putExtra(LocationPickerActivity.EXTRA_TITLE, getString(R.string.settings_home_title));
            if (s.hasHomeLocation()) {
                intent.putExtra(LocationPickerActivity.EXTRA_LAT, s.getHomeLat());
                intent.putExtra(LocationPickerActivity.EXTRA_LNG, s.getHomeLng());
            }
            pickHome.launch(intent);
        });

        // --- data tools (only meaningful when logged in)
        View dataSection = findViewById(R.id.settings_data_section);
        dataSection.setVisibility(s.isLoggedIn() ? View.VISIBLE : View.GONE);
        findViewById(R.id.settings_storage).setOnClickListener(v ->
                Dialogs.info(this, getString(R.string.settings_storage), repo().describeStorage()));
        findViewById(R.id.settings_sync).setOnClickListener(v -> {
            AlertDialog progress = Dialogs.progress(this, getString(R.string.progress_sync));
            SyncManager.syncAsync(this, error -> {
                progress.dismiss();
                toast(error == null ? getString(R.string.sync_done) : error.getMessage());
            });
        });

        ((TextView) findViewById(R.id.settings_version)).setText(getString(R.string.settings_version, getVersionName()));
    }

    private String getVersionName() {
        try {
            return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception e) {
            return "?";
        }
    }

    private void updateRadiusLabel(int km) {
        radiusLabel.setText(getString(R.string.settings_radius_value, km));
    }

    private void updateHomeLabel() {
        homeLabel.setText(session().hasHomeLocation()
                ? String.format(Locale.US, "📍 %.4f, %.4f", session().getHomeLat(), session().getHomeLng())
                : getString(R.string.settings_home_none));
    }

    private String cleanServerUrl() {
        String url = serverInput.getText().toString().trim();
        if (!url.startsWith("http://") && !url.startsWith("https://")) url = "http://" + url;
        while (url.endsWith("/")) url = url.substring(0, url.length() - 1);
        return url;
    }

    private void saveServer() {
        String url = cleanServerUrl();
        serverInput.setText(url);
        session().setServerUrl(url);
        toast(R.string.settings_server_saved);
    }

    /** Calls GET /health on a background thread and reports the result in a dialog. */
    private void testServer() {
        String url = cleanServerUrl();
        AlertDialog progress = Dialogs.progress(this, getString(R.string.progress_test));
        LinkUpApp.runInBackground(() -> {
            String message;
            boolean ok;
            try {
                new ApiClient(url, null).get("/health");
                ok = true;
                message = getString(R.string.settings_test_ok, url);
            } catch (ApiException e) {
                ok = false;
                message = e.getMessage() + "\n\n" + getString(R.string.settings_test_help);
            }
            boolean success = ok;
            String text = message;
            runOnUiThread(() -> {
                progress.dismiss();
                Dialogs.info(this, getString(success ? R.string.settings_test_ok_title : R.string.settings_test_fail_title), text);
            });
        });
    }
}
