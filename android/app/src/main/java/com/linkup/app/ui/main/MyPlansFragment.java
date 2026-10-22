package com.linkup.app.ui.main;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListView;
import android.widget.RadioGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.linkup.app.R;
import com.linkup.app.ui.adapters.PlanCursorAdapter;
import com.linkup.app.ui.common.BaseFragment;
import com.linkup.app.ui.common.Refreshable;
import com.linkup.app.ui.plan.PlanDetailsActivity;

/** "Hosting" and "Joined" lists, read from SQLite through a CursorAdapter. */
public class MyPlansFragment extends BaseFragment implements Refreshable {

    private static final String STATE_HOSTING = "hosting";

    private PlanCursorAdapter adapter;
    private TextView emptyView;
    private boolean hosting = true;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_my_plans, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        if (savedInstanceState != null) hosting = savedInstanceState.getBoolean(STATE_HOSTING, true);

        ListView list = view.findViewById(R.id.my_plans_list);
        emptyView = view.findViewById(R.id.my_plans_empty);
        adapter = new PlanCursorAdapter(requireContext(), repo().myId());
        list.setAdapter(adapter);
        list.setEmptyView(emptyView);
        list.setOnItemClickListener((parent, row, position, id) ->
                startActivity(PlanDetailsActivity.intent(requireContext(), adapter.getPlanUuid(position))));

        RadioGroup tabs = view.findViewById(R.id.my_plans_tabs);
        tabs.check(hosting ? R.id.tab_hosting : R.id.tab_joined);
        tabs.setOnCheckedChangeListener((group, checkedId) -> {
            hosting = checkedId == R.id.tab_hosting;
            refresh();
        });
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(STATE_HOSTING, hosting);
    }

    @Override
    public void onResume() {
        super.onResume();
        refresh();
    }

    @Override
    public void refresh() {
        if (adapter == null) return;
        emptyView.setText(hosting ? R.string.my_plans_empty_hosting : R.string.my_plans_empty_joined);
        // changeCursor closes the previous cursor for us
        adapter.changeCursor(repo().getMyPlansCursor(hosting));
    }

    @Override
    public void onDestroyView() {
        if (adapter != null) adapter.changeCursor(null);   // close the cursor
        super.onDestroyView();
    }
}
