package com.linkup.app.ui.common;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.linkup.app.R;
import com.linkup.app.data.Repository;
import com.linkup.app.ui.auth.LoginActivity;
import com.linkup.app.util.SessionManager;

/**
 * Parent of every screen. Logs each lifecycle callback to Logcat (filter by tag "Lifecycle")
 * so the Activity lifecycle can be watched while using the app, and sets up the toolbar.
 */
public abstract class BaseActivity extends AppCompatActivity {

    protected static final String LIFECYCLE = "Lifecycle";

    private SessionManager session;
    private Repository repository;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(LIFECYCLE, getClass().getSimpleName() + ".onCreate (restored state: " + (savedInstanceState != null) + ")");
    }

    @Override
    protected void onStart() {
        super.onStart();
        Log.d(LIFECYCLE, getClass().getSimpleName() + ".onStart");
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.d(LIFECYCLE, getClass().getSimpleName() + ".onResume");
    }

    @Override
    protected void onPause() {
        Log.d(LIFECYCLE, getClass().getSimpleName() + ".onPause");
        super.onPause();
    }

    @Override
    protected void onStop() {
        Log.d(LIFECYCLE, getClass().getSimpleName() + ".onStop");
        super.onStop();
    }

    @Override
    protected void onRestart() {
        super.onRestart();
        Log.d(LIFECYCLE, getClass().getSimpleName() + ".onRestart");
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        Log.d(LIFECYCLE, getClass().getSimpleName() + ".onSaveInstanceState");
    }

    @Override
    protected void onDestroy() {
        Log.d(LIFECYCLE, getClass().getSimpleName() + ".onDestroy");
        super.onDestroy();
    }

    /**
     * Screens opened from a notification can start after the user logged out.
     * Returns true (and goes to the login screen) when nobody is logged in.
     */
    protected boolean redirectIfLoggedOut() {
        if (session().isLoggedIn()) return false;
        Intent intent = new Intent(this, LoginActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
        return true;
    }

    /** Uses the layout's Toolbar as the action bar, optionally with a back arrow. */
    protected Toolbar setupToolbar(boolean showBack) {
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (showBack && getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        return toolbar;
    }

    @Override
    public boolean onSupportNavigateUp() {
        getOnBackPressedDispatcher().onBackPressed();
        return true;
    }

    protected SessionManager session() {
        if (session == null) session = new SessionManager(this);
        return session;
    }

    /** Created lazily because the logged-in user can change (login screen). */
    protected Repository repo() {
        if (repository == null) repository = new Repository(this);
        return repository;
    }

    protected void resetRepo() {
        repository = null;
    }

    protected void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    protected void toast(int resId) {
        Toast.makeText(this, resId, Toast.LENGTH_SHORT).show();
    }
}
