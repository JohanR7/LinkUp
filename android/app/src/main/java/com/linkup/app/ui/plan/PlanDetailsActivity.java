package com.linkup.app.ui.plan;

import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.linkup.app.R;
import com.linkup.app.model.Categories;
import com.linkup.app.model.Participant;
import com.linkup.app.model.Plan;
import com.linkup.app.notify.ReminderScheduler;
import com.linkup.app.sync.SyncManager;
import com.linkup.app.ui.adapters.PlanRowBinder;
import com.linkup.app.ui.common.BaseActivity;
import com.linkup.app.ui.common.Dialogs;
import com.linkup.app.ui.common.PlanActions;
import com.linkup.app.ui.profile.UserProfileActivity;
import com.linkup.app.util.LocationHelper;
import com.linkup.app.util.TimeUtils;

import java.util.List;

/**
 * Everything about one activity: when, where, who is going, and the join flow
 * (join / request / cancel request / leave). The host also approves or declines requests here.
 */
public class PlanDetailsActivity extends BaseActivity {

    public static final String EXTRA_PLAN_UUID = "plan_uuid";

    private enum Action { JOIN, REQUEST, CANCEL_REQUEST, LEAVE, EDIT, NONE }

    private String planUuid;
    private String me;
    private Plan plan;
    private Action primaryAction = Action.NONE;

    private TextView emojiView, titleView, categoryView, statusView, whenView, relativeView,
            placeView, spotsView, modeView, hostView, descriptionView, noteView, goingTitle;
    private Button primaryButton, chatButton;
    private LinearLayout goingContainer, requestsContainer;
    private View requestsSection;

    /** Explicit intent with the plan id as an extra. Used by lists, the map and notifications. */
    public static Intent intent(Context context, String planUuid) {
        return new Intent(context, PlanDetailsActivity.class).putExtra(EXTRA_PLAN_UUID, planUuid);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (redirectIfLoggedOut()) return;
        setContentView(R.layout.activity_plan_details);
        setupToolbar(true);
        setTitle("");

        planUuid = getIntent().getStringExtra(EXTRA_PLAN_UUID);
        if (planUuid == null) {
            finish();
            return;
        }
        me = repo().myId();

        emojiView = findViewById(R.id.details_emoji);
        titleView = findViewById(R.id.details_title);
        categoryView = findViewById(R.id.details_category);
        statusView = findViewById(R.id.details_status);
        whenView = findViewById(R.id.details_when);
        relativeView = findViewById(R.id.details_relative);
        placeView = findViewById(R.id.details_place);
        spotsView = findViewById(R.id.details_spots);
        modeView = findViewById(R.id.details_mode);
        hostView = findViewById(R.id.details_host);
        descriptionView = findViewById(R.id.details_description);
        noteView = findViewById(R.id.details_note);
        goingTitle = findViewById(R.id.details_going_title);
        primaryButton = findViewById(R.id.details_primary);
        chatButton = findViewById(R.id.details_chat);
        goingContainer = findViewById(R.id.details_going);
        requestsContainer = findViewById(R.id.details_requests);
        requestsSection = findViewById(R.id.details_requests_section);

        primaryButton.setOnClickListener(v -> onPrimaryAction());
        chatButton.setOnClickListener(v -> startActivity(ChatActivity.intent(this, planUuid)));
        findViewById(R.id.details_open_map).setOnClickListener(v -> PlanActions.openInMaps(this, plan));
        findViewById(R.id.details_calendar).setOnClickListener(v -> PlanActions.addToCalendar(this, plan));
        findViewById(R.id.details_host_row).setOnClickListener(v -> openProfile(plan.hostUuid));
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (isFinishing()) return;
        load();
        SyncManager.syncAsync(this, error -> {
            if (!isFinishing()) load();
        });
    }

    // ------------------------------------------------------------------ display

    private void load() {
        plan = repo().getPlan(planUuid);
        boolean isHost = plan != null && plan.isHostedBy(me);
        if (plan == null || (Plan.STATUS_HIDDEN.equals(plan.status) && !isHost)) {
            toast(R.string.error_plan_missing);
            finish();
            return;
        }

        emojiView.setText(Categories.emojiFor(plan.category));
        titleView.setText(plan.title);
        categoryView.setText(plan.category);
        PlanRowBinder.applyStatusBadge(statusView, plan, me);
        whenView.setText(TimeUtils.formatFull(plan.startTime));
        relativeView.setText(TimeUtils.relative(plan.startTime));

        String place = plan.placeName;
        Location here = LocationHelper.getReferenceLocation(this);
        if (here != null) {
            place += "\n" + getString(R.string.details_away, LocationHelper.formatDistance(
                    LocationHelper.distanceKm(here.getLatitude(), here.getLongitude(), plan.lat, plan.lng)));
        }
        placeView.setText(place);

        spotsView.setText(plan.isFull()
                ? getString(R.string.details_spots_full, plan.approvedCount)
                : getString(R.string.details_spots, plan.approvedCount, plan.spotsLeft()));
        modeView.setText(plan.needsApproval() ? R.string.details_mode_approval : R.string.details_mode_open);
        hostView.setText(isHost ? getString(R.string.details_host_you) : plan.hostName);
        hostView.setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, plan.hostVerified ? R.drawable.ic_verified_small : 0, 0);

        descriptionView.setText(plan.description);
        descriptionView.setVisibility(plan.description == null || plan.description.isEmpty() ? View.GONE : View.VISIBLE);

        bindPeople(repo().getParticipants(planUuid), isHost);
        bindPrimaryAction(isHost);
        chatButton.setEnabled(isHost || plan.amGoing());
        invalidateOptionsMenu();
    }

    /** Builds the "Who's going" and "Requests" rows by inflating a layout per person. */
    private void bindPeople(List<Participant> people, boolean isHost) {
        LayoutInflater inflater = getLayoutInflater();
        goingContainer.removeAllViews();
        requestsContainer.removeAllViews();

        addPersonRow(inflater, plan.hostUuid, plan.hostName, plan.hostVerified, getString(R.string.role_host));
        int going = 0, pending = 0;
        for (Participant p : people) {
            if (Participant.APPROVED.equals(p.status)) {
                going++;
                addPersonRow(inflater, p.userUuid, p.userName, p.verified,
                        p.userUuid.equals(me) ? getString(R.string.role_you) : getString(R.string.status_going));
            } else if (Participant.PENDING.equals(p.status) && isHost) {
                pending++;
                View row = inflater.inflate(R.layout.item_request, requestsContainer, false);
                ((TextView) row.findViewById(R.id.request_name)).setText(p.userName);
                row.findViewById(R.id.request_verified).setVisibility(p.verified ? View.VISIBLE : View.GONE);
                row.findViewById(R.id.request_name).setOnClickListener(v -> openProfile(p.userUuid));
                row.findViewById(R.id.request_approve).setOnClickListener(v -> decide(p, Participant.APPROVED));
                row.findViewById(R.id.request_decline).setOnClickListener(v -> decide(p, Participant.REJECTED));
                requestsContainer.addView(row);
            }
        }
        goingTitle.setText(getString(R.string.details_going_title, going + 1));
        requestsSection.setVisibility(pending > 0 ? View.VISIBLE : View.GONE);
    }

    private void addPersonRow(LayoutInflater inflater, String uuid, String name, boolean verified, String role) {
        View row = inflater.inflate(R.layout.item_person, goingContainer, false);
        ((TextView) row.findViewById(R.id.person_initials)).setText(com.linkup.app.model.User.initials(name));
        ((TextView) row.findViewById(R.id.person_name)).setText(name);
        ((TextView) row.findViewById(R.id.person_role)).setText(role);
        row.findViewById(R.id.person_verified).setVisibility(verified ? View.VISIBLE : View.GONE);
        row.setOnClickListener(v -> openProfile(uuid));
        goingContainer.addView(row);
    }

    /** The big button changes with my relation to the plan. */
    private void bindPrimaryAction(boolean isHost) {
        int label;
        String note = null;
        boolean enabled = true;

        if (plan.isCancelled()) {
            primaryAction = Action.NONE;
            label = R.string.details_cancelled;
            enabled = false;
            note = getString(R.string.note_cancelled);
        } else if (plan.isOver()) {
            primaryAction = Action.NONE;
            label = R.string.details_ended;
            enabled = false;
        } else if (isHost) {
            primaryAction = Action.EDIT;
            label = R.string.details_edit;
            note = plan.pendingCount > 0 ? getResources().getQuantityString(R.plurals.note_requests, plan.pendingCount, plan.pendingCount)
                    : getString(R.string.note_host);
        } else if (Participant.APPROVED.equals(plan.myStatus)) {
            primaryAction = Action.LEAVE;
            label = R.string.details_leave;
            note = getString(R.string.note_going, plan.placeName);
        } else if (Participant.PENDING.equals(plan.myStatus)) {
            primaryAction = Action.CANCEL_REQUEST;
            label = R.string.details_cancel_request;
            note = getString(R.string.note_pending, plan.hostName);
        } else if (Participant.REJECTED.equals(plan.myStatus)) {
            primaryAction = Action.NONE;
            label = R.string.status_declined;
            enabled = false;
        } else if (plan.isFull()) {
            primaryAction = Action.NONE;
            label = R.string.status_full;
            enabled = false;
        } else if (plan.needsApproval()) {
            primaryAction = Action.REQUEST;
            label = R.string.details_request;
        } else {
            primaryAction = Action.JOIN;
            label = R.string.details_join;
        }
        primaryButton.setText(label);
        primaryButton.setEnabled(enabled);
        noteView.setText(note);
        noteView.setVisibility(note == null ? View.GONE : View.VISIBLE);
    }

    // ------------------------------------------------------------------ actions

    private void onPrimaryAction() {
        switch (primaryAction) {
            case JOIN:
            case REQUEST:
                if (session().hasSeenSafetyTips()) {
                    join();
                } else {
                    Dialogs.safetyTips(this, () -> {
                        session().setSeenSafetyTips();
                        join();
                    });
                }
                break;
            case CANCEL_REQUEST:
                repo().setParticipation(planUuid, me, Participant.LEFT);
                load();
                syncWithProgress(getString(R.string.progress_saving), getString(R.string.toast_request_cancelled));
                break;
            case LEAVE:
                Dialogs.confirm(this, getString(R.string.leave_title), getString(R.string.leave_message),
                        getString(R.string.details_leave), () -> {
                            repo().setParticipation(planUuid, me, Participant.LEFT);
                            load();
                            syncWithProgress(getString(R.string.progress_saving), getString(R.string.toast_left));
                        });
                break;
            case EDIT:
                startActivity(PlanFormActivity.editIntent(this, planUuid));
                break;
            default:
                break;
        }
    }

    private void join() {
        // Open plans: show "Going" straight away; the server confirms on sync
        boolean approval = plan.needsApproval();
        repo().setParticipation(planUuid, me, approval ? Participant.PENDING : Participant.APPROVED);
        load();
        syncWithProgress(getString(approval ? R.string.progress_request : R.string.progress_join),
                getString(approval ? R.string.toast_requested : R.string.toast_joined));
    }

    /** Host decision on a join request. */
    private void decide(Participant person, String status) {
        if (Participant.APPROVED.equals(status) && plan.isFull()) {
            Dialogs.info(this, getString(R.string.status_full), getString(R.string.error_full_approve));
            return;
        }
        repo().setParticipation(planUuid, person.userUuid, status);
        load();
        syncWithProgress(getString(R.string.progress_saving), getString(Participant.APPROVED.equals(status)
                ? R.string.toast_approved : R.string.toast_declined, person.userName));
    }

    /** Sends the change right away, with a progress dialog; offline changes wait for the next sync. */
    private void syncWithProgress(String message, String successText) {
        AlertDialog progress = Dialogs.progress(this, message);
        SyncManager.syncAsync(this, error -> {
            progress.dismiss();
            if (isFinishing()) return;
            ReminderScheduler.refresh(this);
            load();
            if (error == null) toast(successText);
            else if (error.isOffline()) toast(R.string.toast_saved_offline);
            else toast(error.getMessage());
        });
    }

    private void openProfile(String userUuid) {
        startActivity(UserProfileActivity.intent(this, userUuid));
    }

    // ------------------------------------------------------------------ options menu

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_plan_details, menu);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        boolean isHost = plan != null && plan.isHostedBy(me);
        boolean active = plan != null && !plan.isCancelled() && !plan.isOver();
        menu.findItem(R.id.action_edit).setVisible(isHost && active);
        menu.findItem(R.id.action_cancel_plan).setVisible(isHost && active);
        menu.findItem(R.id.action_report).setVisible(!isHost);
        menu.findItem(R.id.action_block).setVisible(!isHost);
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (plan == null) return super.onOptionsItemSelected(item);
        int id = item.getItemId();
        if (id == R.id.action_share) {
            PlanActions.share(this, plan);
        } else if (id == R.id.action_calendar) {
            PlanActions.addToCalendar(this, plan);
        } else if (id == R.id.action_edit) {
            startActivity(PlanFormActivity.editIntent(this, planUuid));
        } else if (id == R.id.action_cancel_plan) {
            new MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.cancel_plan_title)
                    .setMessage(R.string.cancel_plan_message)
                    .setPositiveButton(R.string.cancel_plan_yes, (d, w) -> {
                        repo().cancelPlan(planUuid);
                        load();
                        syncWithProgress(getString(R.string.progress_saving), getString(R.string.toast_plan_cancelled));
                    })
                    .setNegativeButton(R.string.cancel_plan_keep, null)
                    .show();
        } else if (id == R.id.action_report) {
            PlanActions.report(this, plan.hostUuid, planUuid, getString(R.string.this_activity), null);
        } else if (id == R.id.action_block) {
            PlanActions.block(this, plan.hostUuid, plan.hostName, this::finish);
        } else {
            return super.onOptionsItemSelected(item);
        }
        return true;
    }
}
