package com.linkup.app.ui.profile;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.linkup.app.R;
import com.linkup.app.model.User;
import com.linkup.app.ui.common.BaseActivity;
import com.linkup.app.ui.common.PlanActions;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Another person's public profile, with shared interests highlighted, and report/block options. */
public class UserProfileActivity extends BaseActivity {

    public static final String EXTRA_USER_UUID = "user_uuid";

    private String userUuid;
    private User user;

    public static Intent intent(Context context, String userUuid) {
        return new Intent(context, UserProfileActivity.class).putExtra(EXTRA_USER_UUID, userUuid);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (redirectIfLoggedOut()) return;
        setContentView(R.layout.activity_user_profile);
        setupToolbar(true);
        setTitle(R.string.title_profile);

        userUuid = getIntent().getStringExtra(EXTRA_USER_UUID);
        user = userUuid == null ? null : repo().getUser(userUuid);
        if (user == null) {
            toast(R.string.error_user_missing);
            finish();
            return;
        }
        bind();
    }

    private void bind() {
        ((TextView) findViewById(R.id.user_initials)).setText(User.initials(user.name));
        ((TextView) findViewById(R.id.user_name)).setText(user.name);
        findViewById(R.id.user_verified).setVisibility(user.verified ? View.VISIBLE : View.GONE);
        ((TextView) findViewById(R.id.user_bio)).setText(user.bio == null || user.bio.isEmpty()
                ? getString(R.string.profile_no_bio_other) : user.bio);
        ((TextView) findViewById(R.id.user_hosted)).setText(String.valueOf(repo().countHosted(userUuid)));
        ((TextView) findViewById(R.id.user_joined)).setText(String.valueOf(repo().countJoined(userUuid)));

        // Interests we share are shown as checked chips
        User me = repo().getUser(repo().myId());
        List<String> mine = new ArrayList<>();
        if (me != null) {
            for (String s : me.interestList()) mine.add(s.toLowerCase(Locale.getDefault()));
        }
        List<String> shared = new ArrayList<>();
        ChipGroup chips = findViewById(R.id.user_interests);
        for (String interest : user.interestList()) {
            Chip chip = new Chip(this);
            chip.setText(interest);
            boolean common = mine.contains(interest.toLowerCase(Locale.getDefault()));
            chip.setCheckable(true);
            chip.setChecked(common);
            chip.setEnabled(false);
            chips.addView(chip);
            if (common) shared.add(interest);
        }
        TextView sharedView = findViewById(R.id.user_shared);
        if (!shared.isEmpty() && !userUuid.equals(repo().myId())) {
            sharedView.setText(getString(R.string.profile_shared, android.text.TextUtils.join(", ", shared)));
            sharedView.setVisibility(View.VISIBLE);
        }
        findViewById(R.id.user_no_interests).setVisibility(chips.getChildCount() == 0 ? View.VISIBLE : View.GONE);
        findViewById(R.id.user_blocked_note).setVisibility(repo().isBlocked(userUuid) ? View.VISIBLE : View.GONE);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        if (userUuid != null && !userUuid.equals(repo().myId())) {
            getMenuInflater().inflate(R.menu.menu_user_profile, menu);
        }
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        MenuItem block = menu.findItem(R.id.action_block_user);
        if (block != null) block.setTitle(repo().isBlocked(userUuid) ? R.string.action_unblock : R.string.action_block);
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_report_user) {
            PlanActions.report(this, userUuid, null, user.name, null);
            return true;
        } else if (item.getItemId() == R.id.action_block_user) {
            if (repo().isBlocked(userUuid)) {
                repo().unblock(userUuid);
                toast(getString(R.string.unblock_done, user.name));
                bind();
            } else {
                PlanActions.block(this, userUuid, user.name, this::finish);
            }
            invalidateOptionsMenu();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
