package com.linkup.app.ui.main;

import android.content.Intent;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.linkup.app.R;
import com.linkup.app.model.User;
import com.linkup.app.ui.common.BaseFragment;
import com.linkup.app.ui.common.Refreshable;
import com.linkup.app.ui.profile.EditProfileActivity;
import com.linkup.app.util.SessionManager;

import java.io.File;

/** My own profile as other people see it, with an Edit button. */
public class ProfileFragment extends BaseFragment implements Refreshable {

    private View root;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_profile, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        root = view;
        view.findViewById(R.id.profile_edit).setOnClickListener(v ->
                startActivity(new Intent(requireContext(), EditProfileActivity.class)));
    }

    @Override
    public void onResume() {
        super.onResume();
        refresh();
    }

    @Override
    public void refresh() {
        if (root == null) return;
        String me = repo().myId();
        User user = repo().getUser(me);
        if (user == null) return;

        ((TextView) root.findViewById(R.id.profile_name)).setText(user.name);
        ((TextView) root.findViewById(R.id.profile_email)).setText(new SessionManager(requireContext()).getEmail());
        root.findViewById(R.id.profile_verified).setVisibility(user.verified ? View.VISIBLE : View.GONE);
        root.findViewById(R.id.profile_unverified).setVisibility(user.verified ? View.GONE : View.VISIBLE);

        TextView bio = root.findViewById(R.id.profile_bio);
        bio.setText(user.bio == null || user.bio.isEmpty() ? getString(R.string.profile_no_bio) : user.bio);

        ((TextView) root.findViewById(R.id.profile_hosted)).setText(String.valueOf(repo().countHosted(me)));
        ((TextView) root.findViewById(R.id.profile_joined)).setText(String.valueOf(repo().countJoined(me)));

        // One Chip per interest, created in code
        ChipGroup chips = root.findViewById(R.id.profile_interests);
        chips.removeAllViews();
        for (String interest : user.interestList()) {
            Chip chip = new Chip(requireContext());
            chip.setText(interest);
            chip.setClickable(false);
            chips.addView(chip);
        }
        root.findViewById(R.id.profile_no_interests).setVisibility(chips.getChildCount() == 0 ? View.VISIBLE : View.GONE);

        TextView initials = root.findViewById(R.id.profile_initials);
        ImageView photo = root.findViewById(R.id.profile_photo);
        initials.setText(User.initials(user.name));
        if (user.photoPath != null && new File(user.photoPath).exists()) {
            photo.setImageBitmap(BitmapFactory.decodeFile(user.photoPath));
            photo.setVisibility(View.VISIBLE);
        } else {
            photo.setVisibility(View.GONE);
        }
    }
}
