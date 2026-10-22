package com.linkup.app.ui.auth;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Patterns;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import com.linkup.app.LinkUpApp;
import com.linkup.app.R;
import com.linkup.app.data.Repository;
import com.linkup.app.sync.ApiClient;
import com.linkup.app.sync.ApiException;
import com.linkup.app.sync.SyncManager;
import com.linkup.app.ui.common.BaseActivity;
import com.linkup.app.ui.common.Dialogs;
import com.linkup.app.ui.main.MainActivity;
import com.linkup.app.ui.settings.SettingsActivity;
import com.linkup.app.util.SessionManager;

import org.json.JSONException;
import org.json.JSONObject;

/** Launcher screen. Skips straight to the main screen when already logged in. */
public class LoginActivity extends BaseActivity {

    private EditText emailInput;
    private EditText passwordInput;
    private TextView serverLabel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (session().isLoggedIn()) {
            openMain(this);
            finish();
            return;
        }
        setContentView(R.layout.activity_login);

        emailInput = findViewById(R.id.login_email);
        passwordInput = findViewById(R.id.login_password);
        serverLabel = findViewById(R.id.login_server);

        findViewById(R.id.login_button).setOnClickListener(v -> attemptLogin());
        // Pressing "Done" on the keyboard also logs in
        passwordInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                attemptLogin();
                return true;
            }
            return false;
        });
        // Explicit intents to other screens of this app
        findViewById(R.id.login_register).setOnClickListener(v -> startActivity(new Intent(this, RegisterActivity.class)));
        serverLabel.setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (serverLabel != null) serverLabel.setText(getString(R.string.login_server, session().getServerUrl()));
    }

    private void attemptLogin() {
        String email = emailInput.getText().toString().trim();
        String password = passwordInput.getText().toString();

        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            emailInput.setError(getString(R.string.error_email));
            emailInput.requestFocus();
            return;
        }
        if (TextUtils.isEmpty(password)) {
            passwordInput.setError(getString(R.string.error_password_empty));
            passwordInput.requestFocus();
            return;
        }

        JSONObject body = new JSONObject();
        try {
            body.put("email", email).put("password", password);
        } catch (JSONException ignored) {
        }
        authenticate(this, "/auth/login", body, getString(R.string.progress_login));
    }

    /**
     * Shared by login and registration: calls the server on a background thread,
     * saves the session, downloads the data and opens the main screen.
     */
    static void authenticate(BaseActivity activity, String path, JSONObject body, String progressText) {
        AlertDialog progress = Dialogs.progress(activity, progressText);
        String server = new SessionManager(activity).getServerUrl();
        Context app = activity.getApplicationContext();

        LinkUpApp.runInBackground(() -> {
            try {
                JSONObject response = new ApiClient(server, null).post(path, body);
                saveSession(app, response);
                try {
                    SyncManager.syncNow(app);   // first download of activities nearby
                } catch (ApiException ignored) {
                    // Logged in anyway; the data arrives with the next sync
                }
                activity.runOnUiThread(() -> {
                    if (activity.isFinishing()) return;
                    progress.dismiss();
                    openMain(activity);
                    activity.finishAffinity();
                });
            } catch (ApiException e) {
                activity.runOnUiThread(() -> {
                    if (activity.isFinishing()) return;
                    progress.dismiss();
                    Dialogs.info(activity, activity.getString(R.string.error_auth_title), e.getMessage());
                });
            }
        });
    }

    private static void saveSession(Context context, JSONObject response) throws ApiException {
        try {
            JSONObject user = response.getJSONObject("user");
            new SessionManager(context).saveLogin(response.getString("token"), user.getString("id"),
                    user.getString("name"), user.getString("email"));
            new Repository(context).saveOwnUser(user.getString("id"), user.getString("name"),
                    user.optString("bio", ""), user.optString("interests", ""), user.optInt("verified", 0) == 1);
        } catch (JSONException e) {
            throw new ApiException(0, "The server sent an unexpected response");
        }
    }

    private static void openMain(Context context) {
        context.startActivity(new Intent(context, MainActivity.class));
    }
}
