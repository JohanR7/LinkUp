package com.linkup.app.ui.main;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListView;
import android.widget.SimpleCursorAdapter;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.linkup.app.R;
import com.linkup.app.ui.common.BaseFragment;
import com.linkup.app.ui.common.Dialogs;
import com.linkup.app.ui.common.Refreshable;

/**
 * Safety guidance, an emergency call button, and the list of people I blocked.
 * The guidance sits in a ListView header so the whole screen scrolls together.
 */
public class SafetyFragment extends BaseFragment implements Refreshable {

    private SimpleCursorAdapter adapter;
    private View noneBlocked;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_safety, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        ListView list = view.findViewById(R.id.blocked_list);
        View header = getLayoutInflater().inflate(R.layout.header_safety, list, false);
        list.addHeaderView(header, null, false);
        noneBlocked = header.findViewById(R.id.safety_none_blocked);

        // Implicit intent: opens the dialer with the number filled in (no call permission needed)
        header.findViewById(R.id.safety_emergency).setOnClickListener(v -> {
            try {
                startActivity(new Intent(Intent.ACTION_DIAL, Uri.parse("tel:112")));
            } catch (ActivityNotFoundException e) {
                Dialogs.info(requireContext(), getString(R.string.safety_emergency), getString(R.string.error_no_dialer));
            }
        });
        header.findViewById(R.id.safety_tips).setOnClickListener(v -> Dialogs.safetyTips(requireContext(), null));

        adapter = new SimpleCursorAdapter(requireContext(), R.layout.item_blocked, null,
                new String[]{"name"}, new int[]{R.id.blocked_name}, 0);
        list.setAdapter(adapter);
        list.setOnItemClickListener((parent, row, position, id) -> {
            // position includes the header row, so read the row through the list
            Cursor c = (Cursor) parent.getItemAtPosition(position);
            String uuid = c.getString(c.getColumnIndexOrThrow("blocked_uuid"));
            String name = c.getString(c.getColumnIndexOrThrow("name"));
            Dialogs.confirm(requireContext(), getString(R.string.unblock_title, name), getString(R.string.unblock_message),
                    getString(R.string.action_unblock), () -> {
                        repo().unblock(uuid);
                        refresh();
                    });
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        refresh();
    }

    @Override
    public void refresh() {
        if (adapter == null) return;
        adapter.changeCursor(repo().getBlockedCursor());
        noneBlocked.setVisibility(adapter.getCount() == 0 ? View.VISIBLE : View.GONE);
    }

    @Override
    public void onDestroyView() {
        if (adapter != null) adapter.changeCursor(null);
        super.onDestroyView();
    }
}
