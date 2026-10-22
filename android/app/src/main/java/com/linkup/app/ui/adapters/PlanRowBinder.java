package com.linkup.app.ui.adapters;

import android.content.res.ColorStateList;
import android.view.View;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.linkup.app.R;
import com.linkup.app.model.Categories;
import com.linkup.app.model.Participant;
import com.linkup.app.model.Plan;
import com.linkup.app.util.LocationHelper;
import com.linkup.app.util.TimeUtils;

/**
 * Fills one item_plan row. Shared by the BaseAdapter (Discover) and the CursorAdapter
 * (My activities) so both lists look the same. Uses the ViewHolder pattern so
 * findViewById runs once per row view, not on every scroll.
 */
public final class PlanRowBinder {

    private static class ViewHolder {
        TextView emoji, title, status, when, place, spots, host;
    }

    private PlanRowBinder() {
    }

    public static void bind(View row, Plan plan, String me) {
        ViewHolder h = (ViewHolder) row.getTag();
        if (h == null) {
            h = new ViewHolder();
            h.emoji = row.findViewById(R.id.plan_emoji);
            h.title = row.findViewById(R.id.plan_title);
            h.status = row.findViewById(R.id.plan_status);
            h.when = row.findViewById(R.id.plan_when);
            h.place = row.findViewById(R.id.plan_place);
            h.spots = row.findViewById(R.id.plan_spots);
            h.host = row.findViewById(R.id.plan_host);
            row.setTag(h);
        }

        h.emoji.setText(Categories.emojiFor(plan.category));
        h.title.setText(plan.title);
        h.when.setText(TimeUtils.formatWhen(plan.startTime) + "  ·  " + TimeUtils.relative(plan.startTime));

        String place = plan.placeName;
        if (plan.distanceKm >= 0) place += "  ·  " + LocationHelper.formatDistance(plan.distanceKm);
        h.place.setText(place);

        h.spots.setText(plan.isFull()
                ? row.getContext().getString(R.string.spots_full, plan.approvedCount)
                : row.getContext().getString(R.string.spots_left, plan.spotsLeft(), plan.maxSpots));

        boolean mine = plan.isHostedBy(me);
        h.host.setText(mine ? row.getContext().getString(R.string.hosted_by_you)
                : row.getContext().getString(R.string.hosted_by, plan.hostName));
        h.host.setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0,
                plan.hostVerified && !mine ? R.drawable.ic_verified_small : 0, 0);

        applyStatusBadge(h.status, plan, me);
    }

    /** Coloured pill: Hosting / Going / Requested / Full / Open / Cancelled ... */
    public static void applyStatusBadge(TextView badge, Plan plan, String me) {
        int label;
        int color;
        if (plan.isCancelled()) {
            label = R.string.status_cancelled;
            color = R.color.status_cancelled;
        } else if (Plan.STATUS_HIDDEN.equals(plan.status)) {
            label = R.string.status_hidden;
            color = R.color.status_full;
        } else if (plan.isOver()) {
            label = R.string.status_ended;
            color = R.color.status_full;
        } else if (plan.isHostedBy(me)) {
            label = R.string.status_hosting;
            color = R.color.primary_dark;
        } else if (Participant.APPROVED.equals(plan.myStatus)) {
            label = R.string.status_going;
            color = R.color.status_joined;
        } else if (Participant.PENDING.equals(plan.myStatus)) {
            label = R.string.status_requested;
            color = R.color.status_pending;
        } else if (Participant.REJECTED.equals(plan.myStatus)) {
            label = R.string.status_declined;
            color = R.color.status_full;
        } else if (plan.isFull()) {
            label = R.string.status_full;
            color = R.color.status_full;
        } else if (plan.needsApproval()) {
            label = R.string.status_request;
            color = R.color.status_request;
        } else {
            label = R.string.status_open;
            color = R.color.status_open;
        }
        badge.setText(label);
        badge.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(badge.getContext(), color)));
    }
}
