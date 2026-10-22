package com.linkup.app.ui.main;

import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.os.Bundle;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.ListView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.SearchView;
import androidx.appcompat.widget.SwitchCompat;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.linkup.app.LinkUpApp;
import com.linkup.app.R;
import com.linkup.app.model.Categories;
import com.linkup.app.model.Plan;
import com.linkup.app.model.User;
import com.linkup.app.ui.adapters.PlanListAdapter;
import com.linkup.app.ui.common.BaseFragment;
import com.linkup.app.ui.common.PlanActions;
import com.linkup.app.ui.common.Refreshable;
import com.linkup.app.ui.plan.PlanDetailsActivity;
import com.linkup.app.ui.settings.SettingsActivity;
import com.linkup.app.util.LocationHelper;
import com.linkup.app.util.SessionManager;
import com.linkup.app.util.TimeUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * The activity feed: upcoming plans nearby in a ListView, with search (action bar SearchView),
 * a filter dialog, a sort sub-menu and a long-press context menu.
 */
public class DiscoverFragment extends BaseFragment implements Refreshable {

    private static final String ARG_CATEGORY = "category";

    private static final int SORT_SOONEST = 0;
    private static final int SORT_NEAREST = 1;
    private static final int SORT_SPOTS = 2;

    private static final int TIME_ANY = 0;
    private static final int TIME_NEXT_3_HOURS = 1;
    private static final int TIME_TODAY = 2;
    private static final int TIME_TOMORROW = 3;
    private static final int TIME_THIS_WEEK = 4;

    // Filter state (kept across rotation in onSaveInstanceState)
    private String category;
    private int maxDistanceKm;
    private int timeFilter = TIME_ANY;
    private boolean matchInterests;
    private boolean showFull = true;
    private String query = "";
    private int sortMode = SORT_SOONEST;

    private PlanListAdapter adapter;
    private boolean askedForFix;
    private TextView summaryView;
    private TextView locationHint;

    public static DiscoverFragment newInstance(@Nullable String category) {
        DiscoverFragment fragment = new DiscoverFragment();
        Bundle args = new Bundle();
        args.putString(ARG_CATEGORY, category);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
        if (savedInstanceState != null) {
            category = savedInstanceState.getString("category");
            maxDistanceKm = savedInstanceState.getInt("distance");
            timeFilter = savedInstanceState.getInt("time");
            matchInterests = savedInstanceState.getBoolean("interests");
            showFull = savedInstanceState.getBoolean("full", true);
            query = savedInstanceState.getString("query", "");
            sortMode = savedInstanceState.getInt("sort");
        } else {
            category = getArguments() != null ? getArguments().getString(ARG_CATEGORY) : null;
            maxDistanceKm = new SessionManager(requireContext()).getRadiusKm();
        }
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle out) {
        super.onSaveInstanceState(out);
        out.putString("category", category);
        out.putInt("distance", maxDistanceKm);
        out.putInt("time", timeFilter);
        out.putBoolean("interests", matchInterests);
        out.putBoolean("full", showFull);
        out.putString("query", query);
        out.putInt("sort", sortMode);
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_discover, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        ListView list = view.findViewById(R.id.discover_list);
        summaryView = view.findViewById(R.id.discover_summary);
        locationHint = view.findViewById(R.id.discover_location_hint);

        adapter = new PlanListAdapter(repo().myId());
        list.setAdapter(adapter);
        list.setEmptyView(view.findViewById(R.id.discover_empty));
        list.setOnItemClickListener((parent, row, position, id) ->
                startActivity(PlanDetailsActivity.intent(requireContext(), adapter.getItem(position).uuid)));
        registerForContextMenu(list);

        summaryView.setOnClickListener(v -> showFilterDialog());
        locationHint.setOnClickListener(v -> startActivity(new Intent(requireContext(), SettingsActivity.class)));
    }

    @Override
    public void onResume() {
        super.onResume();
        refresh();
    }

    // ------------------------------------------------------------------ loading

    @Override
    public void refresh() {
        if (!isAdded()) return;
        Context context = requireContext().getApplicationContext();
        // Copy the filter values so the background thread sees one consistent set
        Filters f = new Filters();
        String me = repo().myId();

        LinkUpApp.runInBackground(() -> {
            List<Plan> plans = repo().getDiscoverPlans();
            Location here = LocationHelper.getReferenceLocation(context);
            User myself = repo().getUser(me);
            List<Plan> shown = f.apply(plans, here, myself, me);
            LinkUpApp.runOnMain(() -> {
                if (!isAdded() || getView() == null) return;
                adapter.setPlans(shown);
                updateSummary(shown.size(), here != null);
                if (here == null) requestLocationOnce();
            });
        });
    }

    /** No cached position yet (e.g. right after boot): ask GPS for one fix, then reload. */
    private void requestLocationOnce() {
        Context context = getContext();
        if (askedForFix || context == null || !LocationHelper.hasPermission(context)
                || !new SessionManager(context).useDeviceLocation()) {
            return;
        }
        askedForFix = true;
        LocationHelper.requestFreshLocation(context, location -> {
            if (location != null && isAdded()) refresh();
        });
    }

    /** A snapshot of the current filters, applied to the plan list in Java. */
    private class Filters {
        final String category = DiscoverFragment.this.category;
        final int maxKm = maxDistanceKm;
        final int time = timeFilter;
        final boolean interests = matchInterests;
        final boolean full = showFull;
        final String q = query.trim().toLowerCase(Locale.getDefault());
        final int sort = sortMode;

        List<Plan> apply(List<Plan> plans, Location here, User myself, String me) {
            List<String> myInterests = new ArrayList<>();
            if (myself != null) {
                for (String s : myself.interestList()) myInterests.add(s.toLowerCase(Locale.getDefault()));
            }
            List<Plan> out = new ArrayList<>();
            for (Plan p : plans) {
                if (here != null) {
                    p.distanceKm = LocationHelper.distanceKm(here.getLatitude(), here.getLongitude(), p.lat, p.lng);
                }
                if (category != null && !category.equals(p.category)) continue;
                if (maxKm > 0 && p.distanceKm > maxKm) continue;
                if (!matchesTime(p.startTime)) continue;
                if (!full && p.isFull() && !p.amGoing() && !p.isHostedBy(me)) continue;
                String text = (p.title + " " + p.description + " " + p.placeName + " " + p.category)
                        .toLowerCase(Locale.getDefault());
                if (!q.isEmpty() && !text.contains(q)) continue;
                if (interests && !matchesAny(text, myInterests)) continue;
                out.add(p);
            }

            Comparator<Plan> order;
            if (sort == SORT_NEAREST) {
                // unknown distances (-1) go last
                order = Comparator.comparingDouble(p -> p.distanceKm < 0 ? Double.MAX_VALUE : p.distanceKm);
            } else if (sort == SORT_SPOTS) {
                order = (a, b) -> Integer.compare(b.spotsLeft(), a.spotsLeft());
            } else {
                order = Comparator.comparingLong(p -> p.startTime);
            }
            Collections.sort(out, order);
            return out;
        }

        boolean matchesTime(long start) {
            long untilStart = start - System.currentTimeMillis();
            switch (time) {
                case TIME_NEXT_3_HOURS:
                    return untilStart <= 3 * 60 * 60 * 1000L;
                case TIME_TODAY:
                    return TimeUtils.daysFromToday(start) <= 0;
                case TIME_TOMORROW:
                    return TimeUtils.daysFromToday(start) == 1;
                case TIME_THIS_WEEK:
                    return untilStart <= 7 * 24 * 60 * 60 * 1000L;
                default:
                    return true;
            }
        }

        boolean matchesAny(String text, List<String> words) {
            for (String word : words) {
                if (text.contains(word)) return true;
            }
            return false;
        }
    }

    private void updateSummary(int count, boolean locationKnown) {
        List<String> parts = new ArrayList<>();
        parts.add(getResources().getQuantityString(R.plurals.activities_count, count, count));
        if (category != null) parts.add(Categories.emojiFor(category) + " " + category);
        if (maxDistanceKm > 0 && locationKnown) parts.add(getString(R.string.filter_within, maxDistanceKm));
        if (timeFilter != TIME_ANY) parts.add(getResources().getStringArray(R.array.time_filters)[timeFilter]);
        if (matchInterests) parts.add(getString(R.string.filter_my_interests));
        if (!query.isEmpty()) parts.add("\"" + query + "\"");
        summaryView.setText(android.text.TextUtils.join("  ·  ", parts));
        locationHint.setVisibility(locationKnown ? View.GONE : View.VISIBLE);
    }

    // ------------------------------------------------------------------ options menu

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        inflater.inflate(R.menu.menu_discover, menu);

        MenuItem searchItem = menu.findItem(R.id.action_search);
        SearchView searchView = (SearchView) searchItem.getActionView();
        searchView.setQueryHint(getString(R.string.search_hint));
        if (!query.isEmpty()) {
            searchItem.expandActionView();
            searchView.setQuery(query, false);
        }
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String text) {
                searchView.clearFocus();
                return true;
            }

            @Override
            public boolean onQueryTextChange(String text) {
                query = text;
                refresh();
                return true;
            }
        });

        int checked = sortMode == SORT_NEAREST ? R.id.sort_nearest : sortMode == SORT_SPOTS ? R.id.sort_spots : R.id.sort_soonest;
        menu.findItem(checked).setChecked(true);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_refresh) {
            ((MainActivity) requireActivity()).syncNow(true);
            return true;
        } else if (id == R.id.action_filter) {
            showFilterDialog();
            return true;
        } else if (id == R.id.sort_soonest || id == R.id.sort_nearest || id == R.id.sort_spots) {
            sortMode = id == R.id.sort_nearest ? SORT_NEAREST : id == R.id.sort_spots ? SORT_SPOTS : SORT_SOONEST;
            item.setChecked(true);
            refresh();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    // ------------------------------------------------------------------ filter dialog

    private void showFilterDialog() {
        View view = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_filters, null);
        Spinner categorySpinner = view.findViewById(R.id.filter_category);
        Spinner timeSpinner = view.findViewById(R.id.filter_time);
        SeekBar distanceBar = view.findViewById(R.id.filter_distance);
        TextView distanceLabel = view.findViewById(R.id.filter_distance_label);
        CheckBox interestsCheck = view.findViewById(R.id.filter_interests);
        SwitchCompat fullSwitch = view.findViewById(R.id.filter_show_full);

        // Spinner from a String[] built in code: "All categories" + every category
        List<String> categoryOptions = new ArrayList<>();
        categoryOptions.add(getString(R.string.filter_all_categories));
        Collections.addAll(categoryOptions, Categories.labels());
        ArrayAdapter<String> categoryAdapter = new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_spinner_item, categoryOptions);
        categoryAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        categorySpinner.setAdapter(categoryAdapter);
        categorySpinner.setSelection(category == null ? 0 : Categories.indexOf(category) + 1);

        // Spinner from a string-array resource
        ArrayAdapter<CharSequence> timeAdapter = ArrayAdapter.createFromResource(requireContext(),
                R.array.time_filters, android.R.layout.simple_spinner_item);
        timeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        timeSpinner.setAdapter(timeAdapter);
        timeSpinner.setSelection(timeFilter);

        // SeekBar 0..50 km, where 0 means "any distance"
        distanceBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                distanceLabel.setText(progress == 0 ? getString(R.string.filter_any_distance)
                        : getString(R.string.filter_within, progress));
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });
        distanceBar.setProgress(maxDistanceKm);
        distanceLabel.setText(maxDistanceKm == 0 ? getString(R.string.filter_any_distance)
                : getString(R.string.filter_within, maxDistanceKm));

        interestsCheck.setChecked(matchInterests);
        fullSwitch.setChecked(showFull);

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.filter_title)
                .setView(view)
                .setPositiveButton(R.string.action_apply, (d, which) -> {
                    int pos = categorySpinner.getSelectedItemPosition();
                    category = pos == 0 ? null : Categories.NAMES[pos - 1];
                    timeFilter = timeSpinner.getSelectedItemPosition();
                    maxDistanceKm = distanceBar.getProgress();
                    matchInterests = interestsCheck.isChecked();
                    showFull = fullSwitch.isChecked();
                    refresh();
                })
                .setNeutralButton(R.string.action_reset, (d, which) -> {
                    category = null;
                    timeFilter = TIME_ANY;
                    maxDistanceKm = new SessionManager(requireContext()).getRadiusKm();
                    matchInterests = false;
                    showFull = true;
                    refresh();
                })
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    // ------------------------------------------------------------------ context menu

    @Override
    public void onCreateContextMenu(@NonNull ContextMenu menu, @NonNull View v, @Nullable ContextMenu.ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);
        Plan plan = adapter.getItem(((AdapterView.AdapterContextMenuInfo) menuInfo).position);
        requireActivity().getMenuInflater().inflate(R.menu.menu_plan_context, menu);
        menu.setHeaderTitle(Categories.emojiFor(plan.category) + "  " + plan.title);
        if (plan.isHostedBy(repo().myId())) {
            menu.removeItem(R.id.ctx_report);
            menu.removeItem(R.id.ctx_block);
        }
    }

    @Override
    public boolean onContextItemSelected(@NonNull MenuItem item) {
        if (!(item.getMenuInfo() instanceof AdapterView.AdapterContextMenuInfo)) return super.onContextItemSelected(item);
        Plan plan = adapter.getItem(((AdapterView.AdapterContextMenuInfo) item.getMenuInfo()).position);
        int id = item.getItemId();
        if (id == R.id.ctx_open) {
            startActivity(PlanDetailsActivity.intent(requireContext(), plan.uuid));
        } else if (id == R.id.ctx_map) {
            PlanActions.openInMaps(requireContext(), plan);
        } else if (id == R.id.ctx_share) {
            PlanActions.share(requireContext(), plan);
        } else if (id == R.id.ctx_calendar) {
            PlanActions.addToCalendar(requireContext(), plan);
        } else if (id == R.id.ctx_report) {
            PlanActions.report(requireContext(), plan.hostUuid, plan.uuid, getString(R.string.this_activity), null);
        } else if (id == R.id.ctx_block) {
            PlanActions.block(requireContext(), plan.hostUuid, plan.hostName, this::refresh);
        } else {
            return super.onContextItemSelected(item);
        }
        return true;
    }
}
