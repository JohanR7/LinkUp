package com.linkup.app.ui.main;

import android.database.Cursor;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListView;
import android.widget.SimpleCursorAdapter;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.linkup.app.R;
import com.linkup.app.ui.common.BaseFragment;
import com.linkup.app.ui.common.Dialogs;
import com.linkup.app.ui.common.Refreshable;
import com.linkup.app.ui.plan.PlanDetailsActivity;
import com.linkup.app.util.TimeUtils;

/**
 * History of every notification (join requests, approvals, changes, reminders), stored in SQLite
 * and shown with a SimpleCursorAdapter. A ViewBinder formats the time and the unread dot.
 */
public class InboxFragment extends BaseFragment implements Refreshable {

    private SimpleCursorAdapter adapter;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_inbox, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        ListView list = view.findViewById(R.id.inbox_list);

        String[] from = {"title", "body", "created_at", "is_read"};
        int[] to = {R.id.inbox_title, R.id.inbox_body, R.id.inbox_time, R.id.inbox_dot};
        adapter = new SimpleCursorAdapter(requireContext(), R.layout.item_inbox, null, from, to, 0);
        adapter.setViewBinder((target, cursor, column) -> {
            String name = cursor.getColumnName(column);
            boolean unread = cursor.getInt(cursor.getColumnIndexOrThrow("is_read")) == 0;
            switch (name) {
                case "created_at":
                    ((TextView) target).setText(TimeUtils.ago(cursor.getLong(column)));
                    return true;
                case "is_read":
                    target.setVisibility(unread ? View.VISIBLE : View.INVISIBLE);
                    return true;
                case "title":
                    ((TextView) target).setText(cursor.getString(column));
                    ((TextView) target).setTypeface(null, unread ? Typeface.BOLD : Typeface.NORMAL);
                    return true;
                default:
                    return false;   // let the adapter bind it normally
            }
        });
        list.setAdapter(adapter);
        list.setEmptyView(view.findViewById(R.id.inbox_empty));

        list.setOnItemClickListener((parent, row, position, id) -> {
            Cursor c = (Cursor) adapter.getItem(position);
            String planUuid = c.getString(c.getColumnIndexOrThrow("plan_uuid"));
            repo().markRead(id);
            if (planUuid != null) startActivity(PlanDetailsActivity.intent(requireContext(), planUuid));
            else refresh();
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
        adapter.changeCursor(repo().getNotificationsCursor());
        if (getActivity() instanceof MainActivity) ((MainActivity) getActivity()).updateInboxCount();
    }

    @Override
    public void onDestroyView() {
        if (adapter != null) adapter.changeCursor(null);
        super.onDestroyView();
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        inflater.inflate(R.menu.menu_inbox, menu);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.action_mark_read) {
            repo().markAllRead();
            refresh();
            return true;
        } else if (item.getItemId() == R.id.action_clear) {
            Dialogs.confirm(requireContext(), getString(R.string.inbox_clear_title), getString(R.string.inbox_clear_message),
                    getString(R.string.action_clear), () -> {
                        repo().clearNotifications();
                        refresh();
                    });
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
