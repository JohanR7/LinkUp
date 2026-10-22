package com.linkup.app.ui.auth;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Patterns;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.linkup.app.R;
import com.linkup.app.ui.common.BaseActivity;

import org.json.JSONException;
import org.json.JSONObject;

public class RegisterActivity extends BaseActivity {

    private EditText nameInput, emailInput, passwordInput, confirmInput;
    private TextView passwordHint, emailHint;
    private CheckBox agreeCheck;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);
        setupToolbar(true);

        nameInput = findViewById(R.id.register_name);
        emailInput = findViewById(R.id.register_email);
        passwordInput = findViewById(R.id.register_password);
        confirmInput = findViewById(R.id.register_confirm);
        passwordHint = findViewById(R.id.register_password_hint);
        emailHint = findViewById(R.id.register_email_hint);
        agreeCheck = findViewById(R.id.register_agree);

        // Input events: live feedback while typing
        passwordInput.addTextChangedListener(new SimpleWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                updatePasswordHint(s.toString());
            }
        });
        emailInput.addTextChangedListener(new SimpleWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                String email = s.toString().trim().toLowerCase();
                boolean campus = email.endsWith(".edu") || email.endsWith(".edu.in") || email.endsWith(".ac.in");
                emailHint.setText(campus ? R.string.register_email_campus : R.string.register_email_hint);
            }
        });

        findViewById(R.id.register_button).setOnClickListener(v -> attemptRegister());
    }

    private void updatePasswordHint(String password) {
        int text, color;
        if (password.isEmpty()) {
            text = R.string.password_rule;
            color = R.color.ink_muted;
        } else if (password.length() < 6) {
            text = R.string.password_short;
            color = R.color.status_cancelled;
        } else if (password.length() < 10 || password.chars().allMatch(Character::isLetter)) {
            text = R.string.password_ok;
            color = R.color.status_pending;
        } else {
            text = R.string.password_strong;
            color = R.color.status_joined;
        }
        passwordHint.setText(text);
        passwordHint.setTextColor(ContextCompat.getColor(this, color));
    }

    private void attemptRegister() {
        String name = nameInput.getText().toString().trim();
        String email = emailInput.getText().toString().trim();
        String password = passwordInput.getText().toString();

        if (name.isEmpty()) {
            nameInput.setError(getString(R.string.error_name));
            nameInput.requestFocus();
            return;
        }
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            emailInput.setError(getString(R.string.error_email));
            emailInput.requestFocus();
            return;
        }
        if (password.length() < 6) {
            passwordInput.setError(getString(R.string.password_short));
            passwordInput.requestFocus();
            return;
        }
        if (!password.equals(confirmInput.getText().toString())) {
            confirmInput.setError(getString(R.string.error_password_match));
            confirmInput.requestFocus();
            return;
        }
        if (!agreeCheck.isChecked()) {
            toast(R.string.error_agree);
            return;
        }

        JSONObject body = new JSONObject();
        try {
            body.put("name", name).put("email", email).put("password", password);
        } catch (JSONException ignored) {
        }
        LoginActivity.authenticate(this, "/auth/register", body, getString(R.string.progress_register));
    }

    /** TextWatcher with empty defaults so callers only override what they need. */
    abstract static class SimpleWatcher implements TextWatcher {
        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
        }
    }
}
