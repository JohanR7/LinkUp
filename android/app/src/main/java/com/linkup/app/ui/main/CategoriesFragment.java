package com.linkup.app.ui.main;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.GridView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.linkup.app.R;
import com.linkup.app.ui.adapters.CategoryGridAdapter;
import com.linkup.app.ui.common.BaseFragment;
import com.linkup.app.ui.common.Refreshable;

/** GridView of categories; tapping one opens Discover filtered to it. */
public class CategoriesFragment extends BaseFragment implements Refreshable {

    private CategoryGridAdapter adapter;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_categories, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        GridView grid = view.findViewById(R.id.category_grid);
        adapter = new CategoryGridAdapter();
        grid.setAdapter(adapter);
        grid.setOnItemClickListener((parent, cell, position, id) ->
                ((MainActivity) requireActivity()).openDiscoverWithCategory(adapter.getItem(position)));
    }

    @Override
    public void onResume() {
        super.onResume();
        refresh();
    }

    @Override
    public void refresh() {
        if (adapter != null) adapter.setCounts(repo().countPlansByCategory());
    }
}
