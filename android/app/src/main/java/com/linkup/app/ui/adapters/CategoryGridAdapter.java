package com.linkup.app.ui.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import com.linkup.app.R;
import com.linkup.app.model.Categories;

import java.util.HashMap;
import java.util.Map;

/** BaseAdapter for the category GridView: emoji, name and how many activities are coming up. */
public class CategoryGridAdapter extends BaseAdapter {

    private final Map<String, Integer> counts = new HashMap<>();

    public void setCounts(Map<String, Integer> newCounts) {
        counts.clear();
        counts.putAll(newCounts);
        notifyDataSetChanged();
    }

    @Override
    public int getCount() {
        return Categories.NAMES.length;
    }

    @Override
    public String getItem(int position) {
        return Categories.NAMES[position];
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        View cell = convertView != null ? convertView
                : LayoutInflater.from(parent.getContext()).inflate(R.layout.item_category, parent, false);
        String name = getItem(position);
        int count = counts.containsKey(name) ? counts.get(name) : 0;

        ((TextView) cell.findViewById(R.id.category_emoji)).setText(Categories.emojiFor(name));
        ((TextView) cell.findViewById(R.id.category_name)).setText(name);
        ((TextView) cell.findViewById(R.id.category_count)).setText(
                parent.getContext().getResources().getQuantityString(R.plurals.activities_count, count, count));
        return cell;
    }
}
