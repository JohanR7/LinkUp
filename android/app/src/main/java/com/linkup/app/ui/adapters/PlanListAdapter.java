package com.linkup.app.ui.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;

import com.linkup.app.R;
import com.linkup.app.model.Plan;

import java.util.ArrayList;
import java.util.List;

/** BaseAdapter for the Discover feed. The list is filtered and sorted in Java before it is set. */
public class PlanListAdapter extends BaseAdapter {

    private final List<Plan> plans = new ArrayList<>();
    private final String me;

    public PlanListAdapter(String me) {
        this.me = me;
    }

    public void setPlans(List<Plan> newPlans) {
        plans.clear();
        plans.addAll(newPlans);
        notifyDataSetChanged();
    }

    @Override
    public int getCount() {
        return plans.size();
    }

    @Override
    public Plan getItem(int position) {
        return plans.get(position);
    }

    @Override
    public long getItemId(int position) {
        return plans.get(position).uuid.hashCode();
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        View row = convertView;
        if (row == null) {   // reuse rows that scrolled off screen
            row = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_plan, parent, false);
        }
        PlanRowBinder.bind(row, getItem(position), me);
        return row;
    }
}
