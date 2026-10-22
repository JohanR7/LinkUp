package com.linkup.app.ui.profile;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.linkup.app.LinkUpApp;
import com.linkup.app.R;
import com.linkup.app.model.User;
import com.linkup.app.sync.SyncManager;
import com.linkup.app.ui.common.BaseActivity;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Edit name, bio, interests (multi-choice dialog) and photo.
 * The photo is chosen with an implicit intent (system picker) and stays on this phone.
 */
public class EditProfileActivity extends BaseActivity {

    private static final String STATE_INTERESTS = "interests";
    private static final int BIO_MAX = 300;
    private static final int PHOTO_SIZE = 512;

    private EditText nameInput, bioInput;
    private TextView bioCounter, interestsView, initialsView;
    private ImageView photoView;
    private ArrayList<String> interests = new ArrayList<>();
    private String photoPath;

    /** ACTION_GET_CONTENT for images, wrapped by the Activity Result API. */
    private final ActivityResultLauncher<String> pickImage = registerForActivityResult(
            new ActivityResultContracts.GetContent(), uri -> {
                if (uri != null) savePhoto(uri);
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (redirectIfLoggedOut()) return;
        setContentView(R.layout.activity_edit_profile);
        setupToolbar(true);
        setTitle(R.string.title_edit_profile);

        nameInput = findViewById(R.id.edit_name);
        bioInput = findViewById(R.id.edit_bio);
        bioCounter = findViewById(R.id.edit_bio_counter);
        interestsView = findViewById(R.id.edit_interests);
        photoView = findViewById(R.id.edit_photo);
        initialsView = findViewById(R.id.edit_initials);

        User me = repo().getUser(repo().myId());
        if (me == null) {
            finish();
            return;
        }
        photoPath = me.photoPath;
        if (savedInstanceState == null) {
            nameInput.setText(me.name);
            bioInput.setText(me.bio);
            interests = new ArrayList<>(me.interestList());
        } else {
            interests = savedInstanceState.getStringArrayList(STATE_INTERESTS);
        }

        bioInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                bioCounter.setText(getString(R.string.edit_bio_counter, s.length(), BIO_MAX));
            }
        });
        bioCounter.setText(getString(R.string.edit_bio_counter, bioInput.length(), BIO_MAX));

        View.OnClickListener choosePhoto = v -> pickImage.launch("image/*");
        photoView.setOnClickListener(choosePhoto);
        initialsView.setOnClickListener(choosePhoto);
        findViewById(R.id.edit_change_photo).setOnClickListener(choosePhoto);
        findViewById(R.id.edit_remove_photo).setOnClickListener(v -> {
            photoPath = null;
            repo().setMyPhoto(null);
            showPhoto();
        });
        findViewById(R.id.edit_choose_interests).setOnClickListener(v -> chooseInterests());
        findViewById(R.id.edit_save).setOnClickListener(v -> save());

        showPhoto();
        showInterests();
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putStringArrayList(STATE_INTERESTS, interests);
    }

    /** Multi-choice AlertDialog; interests typed elsewhere (not in the list) are kept. */
    private void chooseInterests() {
        String[] options = getResources().getStringArray(R.array.interest_options);
        boolean[] checked = new boolean[options.length];
        for (int i = 0; i < options.length; i++) {
            for (String s : interests) {
                if (s.equalsIgnoreCase(options[i])) checked[i] = true;
            }
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.edit_interests_title)
                .setMultiChoiceItems(options, checked, (d, which, isChecked) -> checked[which] = isChecked)
                .setPositiveButton(R.string.action_done, (d, w) -> {
                    List<String> custom = new ArrayList<>();
                    for (String s : interests) {
                        boolean inList = false;
                        for (String o : options) if (o.equalsIgnoreCase(s)) inList = true;
                        if (!inList) custom.add(s);
                    }
                    interests = new ArrayList<>(custom);
                    for (int i = 0; i < options.length; i++) if (checked[i]) interests.add(options[i]);
                    showInterests();
                })
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    private void showInterests() {
        interestsView.setText(interests.isEmpty() ? getString(R.string.edit_no_interests) : TextUtils.join(", ", interests));
    }

    private void showPhoto() {
        boolean has = photoPath != null && new File(photoPath).exists();
        initialsView.setText(User.initials(nameInput.getText().toString()));
        if (has) photoView.setImageBitmap(BitmapFactory.decodeFile(photoPath));
        photoView.setVisibility(has ? View.VISIBLE : View.GONE);
        initialsView.setVisibility(has ? View.GONE : View.VISIBLE);
        findViewById(R.id.edit_remove_photo).setVisibility(has ? View.VISIBLE : View.GONE);
    }

    /** Scales the picked image down and saves it as a JPEG in the app's private files. */
    private void savePhoto(Uri uri) {
        File out = new File(getFilesDir(), "profile_" + repo().myId() + ".jpg");
        LinkUpApp.runInBackground(() -> {
            try {
                BitmapFactory.Options bounds = new BitmapFactory.Options();
                bounds.inJustDecodeBounds = true;
                try (InputStream in = getContentResolver().openInputStream(uri)) {
                    BitmapFactory.decodeStream(in, null, bounds);
                }
                BitmapFactory.Options opts = new BitmapFactory.Options();
                opts.inSampleSize = 1;
                while (Math.max(bounds.outWidth, bounds.outHeight) / (opts.inSampleSize * 2) >= PHOTO_SIZE) {
                    opts.inSampleSize *= 2;
                }
                Bitmap bitmap;
                try (InputStream in = getContentResolver().openInputStream(uri)) {
                    bitmap = BitmapFactory.decodeStream(in, null, opts);
                }
                if (bitmap == null) throw new IOException("not an image");
                try (FileOutputStream fos = new FileOutputStream(out)) {
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 85, fos);
                }
                runOnUiThread(() -> {
                    photoPath = out.getAbsolutePath();
                    repo().setMyPhoto(photoPath);
                    showPhoto();
                });
            } catch (IOException | SecurityException e) {
                runOnUiThread(() -> toast(R.string.error_photo));
            }
        });
    }

    private void save() {
        String name = nameInput.getText().toString().trim();
        if (name.isEmpty()) {
            nameInput.setError(getString(R.string.error_name));
            return;
        }
        String bio = bioInput.getText().toString().trim();
        if (bio.length() > BIO_MAX) bio = bio.substring(0, BIO_MAX);

        repo().updateMyProfile(name, bio, TextUtils.join(", ", interests));
        session().setName(name);
        SyncManager.syncAsync(this, null);
        toast(R.string.edit_saved);
        finish();
    }
}
