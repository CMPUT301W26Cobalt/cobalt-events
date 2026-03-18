package com.example.cobaltevents.ui;

import android.location.Address;
import android.location.Geocoder;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.cobaltevents.R;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

public class MapPreviewActivity extends AppCompatActivity implements OnMapReadyCallback {

    public static final String EXTRA_LOCATION = "location";
    public static final String EXTRA_EVENT_NAME = "event_name";

    private String locationString;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_map_preview);

        locationString = getIntent().getStringExtra(EXTRA_LOCATION);
        String eventName = getIntent().getStringExtra(EXTRA_EVENT_NAME);

        TextView tvTitle = findViewById(R.id.tv_location_title);
        if (tvTitle != null) {
            tvTitle.setText(locationString != null ? locationString : "Location");
        }

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());

        SupportMapFragment mapFragment = (SupportMapFragment)
                getSupportFragmentManager().findFragmentById(R.id.map);
        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        }
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        if (locationString == null || locationString.isEmpty() || locationString.equals("TBD")) {
            Toast.makeText(this, "No location available", Toast.LENGTH_SHORT).show();
            return;
        }

        // Geocode the address string to lat/lng on a background thread
        new Thread(() -> {
            try {
                Geocoder geocoder = new Geocoder(this, Locale.getDefault());
                List<Address> addresses = geocoder.getFromLocationName(locationString, 1);
                if (addresses != null && !addresses.isEmpty()) {
                    Address address = addresses.get(0);
                    LatLng latLng = new LatLng(address.getLatitude(), address.getLongitude());
                    runOnUiThread(() -> {
                        googleMap.addMarker(new MarkerOptions()
                                .position(latLng)
                                .title(locationString));
                        googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(latLng, 15f));
                    });
                } else {
                    runOnUiThread(() ->
                            Toast.makeText(this, "Could not find location on map", Toast.LENGTH_SHORT).show());
                }
            } catch (IOException e) {
                runOnUiThread(() ->
                        Toast.makeText(this, "Could not find location on map", Toast.LENGTH_SHORT).show());
            }
        }).start();
    }
}
