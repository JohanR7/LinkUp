package com.linkup.app.ui.plan;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.os.Bundle;
import android.text.format.DateFormat;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.NumberPicker;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;

import com.linkup.app.R;
import com.linkup.app.model.Categories;
import com.linkup.app.model.Plan;
import com.linkup.app.notify.ReminderScheduler;
import com.linkup.app.sync.SyncManager;
import com.linkup.app.ui.common.BaseActivity;
import com.linkup.app.ui.common.Dialogs;
import com.linkup.app.util.LocationHelper;
import com.linkup.app.util.TimeUtils;

import java.util.Calendar;
import java.util.Locale;

/**
 * Create a new activity, or edit one I host (started with EXTRA_PLAN_UUID).
 * Uses a Spinner, Date/Time picker dialogs, a NumberPicker, a RadioGroup, a CheckBox and
 * an explicit intent for a result (the map location picker).
 */
public class PlanFormActivity extends BaseActivity {

    public static final String EXTRA_PLAN_UUID = "plan_uuid";

    private static final String STATE_START = "start";
    private static final String STATE_HAS_LOCATION = "has_location";
    private static final String STATE_LAT = "lat";
    private static final String STATE_LNG = "lng";

    private EditText titleInput, descriptionInput, placeInput;
    private Spinner categorySpinner;
    private Button dateButton, timeButton;
    private TextView locationLabel;
    private NumberPicker spotsPicker;
    private RadioGroup joinModeGroup;
    private CheckBox publicPlaceCheck;

    private final Calendar start = Calendar.getInstance();
    private boolean hasLocation;
    private double lat, lng;
    private Plan editing;

    /** Opens the map picker and receives the chosen point back. */
    private final ActivityResultLauncher<Intent> pickLocation = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    setLocation(result.getData().getDoubleExtra(LocationPickerActivity.EXTRA_LAT, 0),
                            result.getData().getDoubleExtra(LocationPickerActivity.EXTRA_LNG, 0));
                }
            });

    public static Intent editIntent(Context context, String planUuid) {
        return new Intent(context, PlanFormActivity.class).putExtra(EXTRA_PLAN_UUID, planUuid);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (redirectIfLoggedOut()) return;
        setContentView(R.layout.activity_plan_form);
        setupToolbar(true);

        titleInput = findViewById(R.id.form_title);
        descriptionInput = findViewById(R.id.form_description);
        placeInput = findViewById(R.id.form_place);
        categorySpinner = findViewById(R.id.form_category);
        dateButton = findViewById(R.id.form_date);
        timeButton = findViewById(R.id.form_time);
        locationLabel = findViewById(R.id.form_location_label);
        spotsPicker = findViewById(R.id.form_spots);
        joinModeGroup = findViewById(R.id.form_join_mode);
        publicPlaceCheck = findViewById(R.id.form_public_place);

        ArrayAdapter<String> categories = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, Categories.labels());
        categories.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        categorySpinner.setAdapter(categories);
        // Changing the category changes the example shown in the title box
        String[] examples = getResources().getStringArray(R.array.category_examples);
        categorySpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                titleInput.setHint(examples[position]);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        spotsPicker.setMinValue(1);
        spotsPicker.setMaxValue(20);
        spotsPicker.setValue(4);
        spotsPicker.setWrapSelectorWheel(false);

        String planUuid = getIntent().getStringExtra(EXTRA_PLAN_UUID);
        if (planUuid != null) {
            editing = repo().getPlan(planUuid);
            if (editing == null || !editing.isHostedBy(repo().myId())) {
                toast(R.string.error_plan_missing);
                finish();
                return;
            }
            setTitle(R.string.title_edit_plan);
            ((Button) findViewById(R.id.form_save)).setText(R.string.form_save_changes);
            if (savedInstanceState == null) fillFrom(editing);
        } else {
            setTitle(R.string.title_new_plan);
            if (savedInstanceState == null) {
                // Default: one hour from now, rounded up to the next quarter hour
                start.add(Calendar.HOUR_OF_DAY, 1);
                start.set(Calendar.MINUTE, ((start.get(Calendar.MINUTE) + 14) / 15) * 15);
                start.set(Calendar.SECOND, 0);
                start.set(Calendar.MILLISECOND, 0);
            }
        }

        if (savedInstanceState != null) {
            start.setTimeInMillis(savedInstanceState.getLong(STATE_START));
            hasLocation = savedInstanceState.getBoolean(STATE_HAS_LOCATION);
            lat = savedInstanceState.getDouble(STATE_LAT);
            lng = savedInstanceState.getDouble(STATE_LNG);
        }
        updateDateTimeLabels();
        updateLocationLabel();

        dateButton.setOnClickListener(v -> showDatePicker());
        timeButton.setOnClickListener(v -> showTimePicker());
        findViewById(R.id.form_pick_map).setOnClickListener(v -> {
            Intent intent = new Intent(this, LocationPickerActivity.class);
            if (hasLocation) {
                intent.putExtra(LocationPickerActivity.EXTRA_LAT, lat);
                intent.putExtra(LocationPickerActivity.EXTRA_LNG, lng);
            }
            pickLocation.launch(intent);
        });
        findViewById(R.id.form_use_current).setOnClickListener(v -> useCurrentLocation());
        findViewById(R.id.form_save).setOnClickListener(v -> save());
    }

    /** EditTexts restore themselves; the picked date and location are saved here. */
    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putLong(STATE_START, start.getTimeInMillis());
        outState.putBoolean(STATE_HAS_LOCATION, hasLocation);
        outState.putDouble(STATE_LAT, lat);
        outState.putDouble(STATE_LNG, lng);
    }

    private void fillFrom(Plan plan) {
        titleInput.setText(plan.title);
        descriptionInput.setText(plan.description);
        placeInput.setText(plan.placeName);
        categorySpinner.setSelection(Categories.indexOf(plan.category));
        start.setTimeInMillis(plan.startTime);
        spotsPicker.setValue(Math.min(20, Math.max(1, plan.maxSpots)));
        joinModeGroup.check(plan.needsApproval() ? R.id.form_mode_approval : R.id.form_mode_open);
        publicPlaceCheck.setChecked(true);
        hasLocation = true;
        lat = plan.lat;
        lng = plan.lng;
    }

    // ------------------------------------------------------------------ pickers

    private void showDatePicker() {
        DatePickerDialog dialog = new DatePickerDialog(this, (view, year, month, day) -> {
            start.set(year, month, day);
            updateDateTimeLabels();
        }, start.get(Calendar.YEAR), start.get(Calendar.MONTH), start.get(Calendar.DAY_OF_MONTH));
        dialog.getDatePicker().setMinDate(System.currentTimeMillis() - 1000);
        dialog.show();
    }

    private void showTimePicker() {
        new TimePickerDialog(this, (view, hour, minute) -> {
            start.set(Calendar.HOUR_OF_DAY, hour);
            start.set(Calendar.MINUTE, minute);
            updateDateTimeLabels();
        }, start.get(Calendar.HOUR_OF_DAY), start.get(Calendar.MINUTE), DateFormat.is24HourFormat(this)).show();
    }

    private void updateDateTimeLabels() {
        dateButton.setText(TimeUtils.formatDayWithDate(start.getTimeInMillis()));
        timeButton.setText(TimeUtils.formatTime(start.getTimeInMillis()));
    }

    // ------------------------------------------------------------------ location

    private void useCurrentLocation() {
        if (!LocationHelper.hasPermission(this)) {
            toast(R.string.error_location_permission);
            return;
        }
        AlertDialog progress = Dialogs.progress(this, getString(R.string.progress_location));
        LocationHelper.requestFreshLocation(this, location -> {
            progress.dismiss();
            Location found = location != null ? location : LocationHelper.getReferenceLocation(this);
            if (found == null) {
                toast(R.string.error_location_unknown);
            } else {
                setLocation(found.getLatitude(), found.getLongitude());
            }
        });
    }

    private void setLocation(double newLat, double newLng) {
        hasLocation = true;
        lat = newLat;
        lng = newLng;
        updateLocationLabel();
    }

    private void updateLocationLabel() {
        locationLabel.setText(hasLocation
                ? String.format(Locale.US, "📍 %.5f, %.5f", lat, lng)
                : getString(R.string.form_no_location));
    }

    // ------------------------------------------------------------------ save

    private void save() {
        String title = titleInput.getText().toString().trim();
        String place = placeInput.getText().toString().trim();

        if (title.isEmpty()) {
            titleInput.setError(getString(R.string.error_title));
            titleInput.requestFocus();
            return;
        }
        if (place.isEmpty()) {
            placeInput.setError(getString(R.string.error_place));
            placeInput.requestFocus();
            return;
        }
        if (!hasLocation) {
            toast(R.string.error_no_location);
            return;
        }
        if (start.getTimeInMillis() < System.currentTimeMillis()) {
            toast(R.string.error_time_past);
            return;
        }
        if (!publicPlaceCheck.isChecked()) {
            toast(R.string.error_public_place);
            return;
        }

        Plan plan = editing != null ? editing : new Plan();
        plan.title = title;
        plan.category = Categories.NAMES[categorySpinner.getSelectedItemPosition()];
        plan.description = descriptionInput.getText().toString().trim();
        plan.startTime = start.getTimeInMillis();
        plan.placeName = place;
        plan.lat = lat;
        plan.lng = lng;
        plan.maxSpots = spotsPicker.getValue();
        plan.joinMode = joinModeGroup.getCheckedRadioButtonId() == R.id.form_mode_approval ? Plan.MODE_APPROVAL : Plan.MODE_OPEN;
        plan.status = Plan.STATUS_ACTIVE;

        repo().savePlan(plan);
        ReminderScheduler.refresh(this);
        SyncManager.syncAsync(this, null);

        if (editing != null) {
            toast(R.string.form_saved);
        } else {
            toast(R.string.form_posted);
            startActivity(PlanDetailsActivity.intent(this, plan.uuid));
        }
        finish();
    }
}
