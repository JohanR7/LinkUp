package com.linkup.app.ui.adapters;

import android.content.Context;
import android.database.Cursor;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CursorAdapter;

import com.linkup.app.R;
import com.linkup.app.data.Repository;

/**
 * CursorAdapter for "My activities": rows are read straight from the SQLite cursor
 * as the list scrolls, without copying everything into a List first.
 * The cursor must contain an _id column.
 */
public class PlanCursorAdapter extends CursorAdapter {

    private final String me;

    public PlanCursorAdapter(Context context, String me) {
        super(context, null, 0);
        this.me = me;
    }

    @Override
    public View newView(Context context, Cursor cursor, ViewGroup parent) {
        return LayoutInflater.from(context).inflate(R.layout.item_plan, parent, false);
    }

    @Override
    public void bindView(View view, Context context, Cursor cursor) {
        PlanRowBinder.bind(view, Repository.planFromCursor(cursor), me);
    }

    /** uuid of the plan in the given row. */
    public String getPlanUuid(int position) {
        Cursor cursor = (Cursor) getItem(position);
        return cursor.getString(cursor.getColumnIndexOrThrow("uuid"));
    }
}
