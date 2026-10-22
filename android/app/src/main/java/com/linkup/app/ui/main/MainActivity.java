package com.linkup.app.ui.main;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.Fragment;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.navigation.NavigationView;
import com.google.android.material.snackbar.Snackbar;
import com.linkup.app.LinkUpApp;
import com.linkup.app.R;
import com.linkup.app.model.User;
import com.linkup.app.notify.ReminderScheduler;
import com.linkup.app.sync.ApiException;
import com.linkup.app.sync.SyncManager;
import com.linkup.app.ui.auth.LoginActivity;
import com.linkup.app.ui.common.BaseActivity;
import com.linkup.app.ui.common.Dialogs;
import com.linkup.app.ui.common.Refreshable;
import com.linkup.app.ui.plan.PlanFormActivity;
import com.linkup.app.ui.settings.SettingsActivity;
import com.linkup.app.util.LocationHelper;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Home screen: a Navigation Drawer on the left switches between fragments shown
 * in one container (Discover, Map, Categories, My activities, Inbox, Profile, Safety).
 */
public class MainActivity extends BaseActivity implements NavigationView.OnNavigationItemSelectedListener {

    private static final String STATE_SECTION = "section";
    private static final String PREF_ASKED_PERMISSIONS = "asked_permissions";

    private DrawerLayout drawer;
    private NavigationView navigation;
    private FloatingActionButton createButton;
    private int currentSection = R.id.nav_discover;
    private boolean offlineShown;

    private final ActivityResultLauncher<String[]> permissionLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestMultiplePermissions(), granted -> refreshCurrentSection());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!session().isLoggedIn()) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }
        setContentView(R.layout.activity_main);
        Toolbar toolbar = setupToolbar(false);

        drawer = findViewById(R.id.drawer_layout);
        navigation = findViewById(R.id.nav_view);
        createButton = findViewById(R.id.fab_create);

        // Hamburger icon in the toolbar that opens/closes the drawer
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(this, drawer, toolbar,
                R.string.drawer_open, R.string.drawer_close);
        drawer.addDrawerListener(toggle);
        toggle.syncState();
        navigation.setNavigationItemSelectedListener(this);

        createButton.setOnClickListener(v -> startActivity(new Intent(this, PlanFormActivity.class)));

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (drawer.isDrawerOpen(GravityCompat.START)) {
                    drawer.closeDrawer(GravityCompat.START);
                } else if (currentSection != R.id.nav_discover) {
                    showSection(R.id.nav_discover, null);
                } else {
                    setEnabled(false);
                    getOnBackPressedDispatcher().onBackPressed();
                }
            }
        });

        if (savedInstanceState == null) {
            showSection(R.id.nav_discover, null);
        } else {
            // The FragmentManager restores the fragment itself after rotation
            currentSection = savedInstanceState.getInt(STATE_SECTION, R.id.nav_discover);
            updateChrome();
        }
        askPermissionsOnce();
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(STATE_SECTION, currentSection);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!session().isLoggedIn()) return;
        updateHeader();
        updateInboxCount();
        SyncManager.syncAsync(this, this::onSyncFinished);
    }

    /** Public so fragments can ask for a sync (e.g. the Refresh menu item). */
    public void syncNow(boolean showResult) {
        SyncManager.syncAsync(this, error -> {
            onSyncFinished(error);
            if (showResult && error == null) toast(R.string.sync_done);
            else if (showResult) toast(error.getMessage());
        });
    }

    private void onSyncFinished(ApiException error) {
        if (isFinishing()) return;
        if (error != null && error.isUnauthorized()) {
            toast(R.string.error_session_expired);
            finishLogout();
            return;
        }
        if (error != null && error.isOffline() && !offlineShown) {
            offlineShown = true;
            Snackbar.make(findViewById(R.id.fragment_container), R.string.offline_notice, Snackbar.LENGTH_LONG)
                    .setAction(R.string.title_settings, v -> startActivity(new Intent(this, SettingsActivity.class)))
                    .show();
        }
        refreshCurrentSection();
        updateInboxCount();
        updateHeader();
    }

    // ------------------------------------------------------------------ navigation

    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        drawer.closeDrawer(GravityCompat.START);
        if (id == R.id.nav_settings) {
            startActivity(new Intent(this, SettingsActivity.class));
            return false;
        }
        if (id == R.id.nav_logout) {
            confirmLogout();
            return false;
        }
        showSection(id, null);
        return true;
    }

    /** Used by the category grid: opens Discover filtered to one category. */
    public void openDiscoverWithCategory(String category) {
        showSection(R.id.nav_discover, category);
    }

    private void showSection(int id, String category) {
        Fragment fragment;
        if (id == R.id.nav_map) fragment = new PlanMapFragment();
        else if (id == R.id.nav_categories) fragment = new CategoriesFragment();
        else if (id == R.id.nav_my_plans) fragment = new MyPlansFragment();
        else if (id == R.id.nav_inbox) fragment = new InboxFragment();
        else if (id == R.id.nav_profile) fragment = new ProfileFragment();
        else if (id == R.id.nav_safety) fragment = new SafetyFragment();
        else {
            id = R.id.nav_discover;
            fragment = DiscoverFragment.newInstance(category);
        }
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.fragment_container, fragment)
                .commit();
        currentSection = id;
        updateChrome();
    }

    /** Title, checked drawer item and the "+" button follow the current section. */
    private void updateChrome() {
        navigation.setCheckedItem(currentSection);
        MenuItem item = navigation.getMenu().findItem(currentSection);
        if (getSupportActionBar() != null && item != null) {
            getSupportActionBar().setTitle(currentSection == R.id.nav_discover ? getString(R.string.title_discover) : item.getTitle());
        }
        boolean showCreate = currentSection == R.id.nav_discover || currentSection == R.id.nav_map
                || currentSection == R.id.nav_categories || currentSection == R.id.nav_my_plans;
        if (showCreate) createButton.show();
        else createButton.hide();
    }

    private void refreshCurrentSection() {
        Fragment fragment = getSupportFragmentManager().findFragmentById(R.id.fragment_container);
        if (fragment instanceof Refreshable && fragment.isAdded()) ((Refreshable) fragment).refresh();
    }

    public void updateInboxCount() {
        int unread = repo().countUnread();
        MenuItem inbox = navigation.getMenu().findItem(R.id.nav_inbox);
        inbox.setTitle(unread > 0 ? getString(R.string.nav_inbox_count, unread) : getString(R.string.nav_inbox));
    }

    private void updateHeader() {
        View header = navigation.getHeaderView(0);
        User me = repo().getUser(repo().myId());
        String name = me != null ? me.name : session().getName();

        ((TextView) header.findViewById(R.id.header_name)).setText(name);
        ((TextView) header.findViewById(R.id.header_email)).setText(session().getEmail());
        header.findViewById(R.id.header_verified).setVisibility(me != null && me.verified ? View.VISIBLE : View.GONE);

        TextView initials = header.findViewById(R.id.header_initials);
        ImageView photo = header.findViewById(R.id.header_photo);
        initials.setText(User.initials(name));
        if (me != null && me.photoPath != null && new File(me.photoPath).exists()) {
            photo.setImageBitmap(BitmapFactory.decodeFile(me.photoPath));
            photo.setVisibility(View.VISIBLE);
        } else {
            photo.setVisibility(View.GONE);
        }
        header.setOnClickListener(v -> {
            showSection(R.id.nav_profile, null);
            drawer.closeDrawer(GravityCompat.START);
        });
    }

    // ------------------------------------------------------------------ permissions

    /** Location (for distances) and, on Android 13+, notifications. Asked once after the first login. */
    private void askPermissionsOnce() {
        SharedPreferences prefs = getSharedPreferences("ui", Context.MODE_PRIVATE);
        if (prefs.getBoolean(PREF_ASKED_PERMISSIONS, false)) return;
        prefs.edit().putBoolean(PREF_ASKED_PERMISSIONS, true).apply();

        List<String> needed = new ArrayList<>();
        if (!LocationHelper.hasPermission(this)) {
            needed.add(Manifest.permission.ACCESS_FINE_LOCATION);
            needed.add(Manifest.permission.ACCESS_COARSE_LOCATION);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            needed.add(Manifest.permission.POST_NOTIFICATIONS);
        }
        if (needed.isEmpty()) return;

        new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle(R.string.permissions_title)
                .setMessage(R.string.permissions_message)
                .setPositiveButton(R.string.action_continue,
                        (d, w) -> permissionLauncher.launch(needed.toArray(new String[0])))
                .setNegativeButton(R.string.action_not_now, null)
                .show();
    }

    // ------------------------------------------------------------------ logout

    private void confirmLogout() {
        Dialogs.confirm(this, getString(R.string.logout_title), getString(R.string.logout_message),
                getString(R.string.nav_logout), () -> {
                    AlertDialog progress = Dialogs.progress(this, getString(R.string.progress_logout));
                    Context app = getApplicationContext();
                    LinkUpApp.runInBackground(() -> {
                        try {
                            SyncManager.syncNow(app);   // send anything still waiting before leaving
                        } catch (ApiException ignored) {
                        }
                        runOnUiThread(() -> {
                            progress.dismiss();
                            finishLogout();
                        });
                    });
                });
    }

    private void finishLogout() {
        session().logout();
        ReminderScheduler.refresh(this);   // no user any more, so this cancels every reminder
        NotificationManagerCompat.from(this).cancelAll();
        Intent intent = new Intent(this, LoginActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}
