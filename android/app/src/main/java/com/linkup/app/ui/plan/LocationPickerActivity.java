package com.linkup.app.ui.plan;

import android.content.Intent;
import android.location.Location;
import android.os.Bundle;
import android.widget.TextView;

import com.linkup.app.R;
import com.linkup.app.ui.common.BaseActivity;
import com.linkup.app.util.LocationHelper;

import org.osmdroid.api.IGeoPoint;
import org.osmdroid.events.MapListener;
import org.osmdroid.events.ScrollEvent;
import org.osmdroid.events.ZoomEvent;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;

import java.util.Locale;

/**
 * Full-screen map with a fixed pin in the middle: move the map under the pin, then confirm.
 * Returns the chosen point with setResult() to whichever screen started it.
 */
public class LocationPickerActivity extends BaseActivity {

    public static final String EXTRA_LAT = "lat";
    public static final String EXTRA_LNG = "lng";
    public static final String EXTRA_TITLE = "title";

    private MapView map;
    private TextView coordinates;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_location_picker);
        setupToolbar(true);
        String title = getIntent().getStringExtra(EXTRA_TITLE);
        if (title != null) setTitle(title);

        map = findViewById(R.id.picker_map);
        coordinates = findViewById(R.id.picker_coordinates);
        map.setTileSource(TileSourceFactory.MAPNIK);
        map.setMultiTouchControls(true);
        map.getController().setZoom(17.0);

        GeoPoint startPoint;
        if (getIntent().hasExtra(EXTRA_LAT)) {
            startPoint = new GeoPoint(getIntent().getDoubleExtra(EXTRA_LAT, 0), getIntent().getDoubleExtra(EXTRA_LNG, 0));
        } else {
            Location here = LocationHelper.getReferenceLocation(this);
            startPoint = here != null ? new GeoPoint(here.getLatitude(), here.getLongitude())
                    : new GeoPoint(LocationHelper.DEFAULT_LAT, LocationHelper.DEFAULT_LNG);
        }
        map.getController().setCenter(startPoint);

        map.addMapListener(new MapListener() {
            @Override
            public boolean onScroll(ScrollEvent event) {
                updateCoordinates();
                return false;
            }

            @Override
            public boolean onZoom(ZoomEvent event) {
                updateCoordinates();
                return false;
            }
        });

        findViewById(R.id.picker_my_location).setOnClickListener(v -> {
            toast(R.string.progress_location);
            LocationHelper.requestFreshLocation(this, location -> {
                if (location == null) {
                    toast(R.string.error_location_unknown);
                } else {
                    map.getController().animateTo(new GeoPoint(location.getLatitude(), location.getLongitude()));
                }
            });
        });

        findViewById(R.id.picker_confirm).setOnClickListener(v -> {
            IGeoPoint center = map.getMapCenter();
            Intent result = new Intent()
                    .putExtra(EXTRA_LAT, center.getLatitude())
                    .putExtra(EXTRA_LNG, center.getLongitude());
            setResult(RESULT_OK, result);
            finish();
        });
        updateCoordinates();
    }

    private void updateCoordinates() {
        IGeoPoint center = map.getMapCenter();
        coordinates.setText(String.format(Locale.US, "%.5f, %.5f", center.getLatitude(), center.getLongitude()));
    }

    @Override
    protected void onResume() {
        super.onResume();
        map.onResume();
    }

    @Override
    protected void onPause() {
        map.onPause();
        super.onPause();
    }
}
