package com.linkup.app.ui.main;

import android.content.Context;
import android.location.Location;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.linkup.app.LinkUpApp;
import com.linkup.app.R;
import com.linkup.app.model.Categories;
import com.linkup.app.model.Plan;
import com.linkup.app.ui.adapters.PlanRowBinder;
import com.linkup.app.ui.common.BaseFragment;
import com.linkup.app.ui.common.Refreshable;
import com.linkup.app.ui.plan.PlanDetailsActivity;
import com.linkup.app.util.LocationHelper;
import com.linkup.app.util.TimeUtils;

import org.osmdroid.events.MapEventsReceiver;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.CustomZoomButtonsController;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.MapEventsOverlay;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider;
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay;

import java.util.ArrayList;
import java.util.List;

/**
 * OpenStreetMap view (osmdroid) with a pin for every upcoming activity.
 * Tapping a pin shows a summary card; tapping the map hides it.
 * The blue "my location" dot is drawn only on this phone and is never shared.
 */
public class PlanMapFragment extends BaseFragment implements Refreshable {

    private MapView map;
    private MyLocationNewOverlay myLocationOverlay;
    private final List<Marker> markers = new ArrayList<>();

    private View card;
    private TextView cardEmoji, cardTitle, cardWhen, cardPlace, cardStatus;
    private Plan selected;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_map, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        Context context = requireContext();

        map = view.findViewById(R.id.map);
        map.setTileSource(TileSourceFactory.MAPNIK);
        map.setMultiTouchControls(true);
        map.getZoomController().setVisibility(CustomZoomButtonsController.Visibility.SHOW_AND_FADEOUT);
        map.setMinZoomLevel(4.0);
        map.getController().setZoom(15.0);

        // Added first so it sits under the markers: a tap on empty map closes the card
        map.getOverlays().add(new MapEventsOverlay(new MapEventsReceiver() {
            @Override
            public boolean singleTapConfirmedHelper(GeoPoint p) {
                hideCard();
                return true;
            }

            @Override
            public boolean longPressHelper(GeoPoint p) {
                return false;
            }
        }));

        if (LocationHelper.hasPermission(context)) {
            myLocationOverlay = new MyLocationNewOverlay(new GpsMyLocationProvider(context), map);
            map.getOverlays().add(myLocationOverlay);
        }

        card = view.findViewById(R.id.map_card);
        cardEmoji = view.findViewById(R.id.map_card_emoji);
        cardTitle = view.findViewById(R.id.map_card_title);
        cardWhen = view.findViewById(R.id.map_card_when);
        cardPlace = view.findViewById(R.id.map_card_place);
        cardStatus = view.findViewById(R.id.map_card_status);
        card.setOnClickListener(v -> openSelected());
        view.findViewById(R.id.map_card_open).setOnClickListener(v -> openSelected());
        view.findViewById(R.id.map_recenter).setOnClickListener(v -> centerOnMe(true));

        centerOnMe(false);
    }

    /** osmdroid's MapView must be told about the fragment's lifecycle. */
    @Override
    public void onResume() {
        super.onResume();
        map.onResume();
        if (myLocationOverlay != null) myLocationOverlay.enableMyLocation();
        refresh();
    }

    @Override
    public void onPause() {
        if (myLocationOverlay != null) myLocationOverlay.disableMyLocation();
        map.onPause();
        super.onPause();
    }

    @Override
    public void onDestroyView() {
        map.onDetach();
        super.onDestroyView();
    }

    @Override
    public void refresh() {
        if (!isAdded()) return;
        LinkUpApp.runInBackground(() -> {
            List<Plan> plans = repo().getDiscoverPlans();
            LinkUpApp.runOnMain(() -> {
                if (isAdded() && getView() != null) showMarkers(plans);
            });
        });
    }

    private void showMarkers(List<Plan> plans) {
        map.getOverlays().removeAll(markers);
        markers.clear();
        for (Plan plan : plans) {
            Marker marker = new Marker(map);
            marker.setPosition(new GeoPoint(plan.lat, plan.lng));
            marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
            marker.setIcon(ContextCompat.getDrawable(requireContext(), R.drawable.ic_map_pin));
            marker.setTitle(plan.title);
            marker.setOnMarkerClickListener((m, mapView) -> {
                showCard(plan);
                return true;
            });
            markers.add(marker);
        }
        map.getOverlays().addAll(markers);
        map.invalidate();
    }

    private void showCard(Plan plan) {
        selected = plan;
        cardEmoji.setText(Categories.emojiFor(plan.category));
        cardTitle.setText(plan.title);
        cardWhen.setText(TimeUtils.formatWhen(plan.startTime) + "  ·  " + TimeUtils.relative(plan.startTime));
        String place = plan.placeName;
        Location here = LocationHelper.getReferenceLocation(requireContext());
        if (here != null) {
            place += "  ·  " + LocationHelper.formatDistance(
                    LocationHelper.distanceKm(here.getLatitude(), here.getLongitude(), plan.lat, plan.lng));
        }
        cardPlace.setText(place);
        PlanRowBinder.applyStatusBadge(cardStatus, plan, repo().myId());
        card.setVisibility(View.VISIBLE);
        map.getController().animateTo(new GeoPoint(plan.lat, plan.lng));
    }

    private void hideCard() {
        selected = null;
        card.setVisibility(View.GONE);
    }

    private void openSelected() {
        if (selected != null) startActivity(PlanDetailsActivity.intent(requireContext(), selected.uuid));
    }

    private void centerOnMe(boolean animate) {
        GeoPoint target = null;
        if (myLocationOverlay != null && myLocationOverlay.getMyLocation() != null) {
            target = myLocationOverlay.getMyLocation();
        } else {
            Location here = LocationHelper.getReferenceLocation(requireContext());
            if (here != null) target = new GeoPoint(here.getLatitude(), here.getLongitude());
        }
        if (target == null) target = new GeoPoint(LocationHelper.DEFAULT_LAT, LocationHelper.DEFAULT_LNG);
        if (animate) map.getController().animateTo(target);
        else map.getController().setCenter(target);
    }
}
