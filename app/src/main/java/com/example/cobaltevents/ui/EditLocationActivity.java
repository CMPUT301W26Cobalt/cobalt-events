package com.example.cobaltevents.ui;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.ArrayAdapter;
import android.widget.Filter;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatAutoCompleteTextView;

import com.example.cobaltevents.R;
import com.google.android.libraries.places.api.Places;
import com.google.android.libraries.places.api.model.AutocompletePrediction;
import com.google.android.libraries.places.api.model.AutocompleteSessionToken;
import com.google.android.libraries.places.api.model.Place;
import com.google.android.libraries.places.api.net.FetchPlaceRequest;
import com.google.android.libraries.places.api.net.FindAutocompletePredictionsRequest;
import com.google.android.libraries.places.api.net.PlacesClient;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Location editor using Google Places autocomplete (same approach as {@link EventCreateActivity})
 * plus optional map preview.
 */
public class EditLocationActivity extends AppCompatActivity {

    private static final int PLACES_DEBOUNCE_MS = 350;
    private static final int PLACES_MIN_QUERY_LEN = 2;

    private boolean placesInitialized;
    private PlacesClient placesClient;
    private AutocompleteSessionToken placesSessionToken;
    private final Handler placesHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService validationExecutor = Executors.newSingleThreadExecutor();
    private final Runnable placesDebouncedQuery = this::runDebouncedPlaceQuery;
    private final List<AutocompletePrediction> placesPredictions = new ArrayList<>();
    private final List<String> placesSuggestionLines = new ArrayList<>();
    private ArrayAdapter<String> placesAdapter;
    private boolean locationTextProgrammatic;
    private Double lastPickedLatitude;
    private Double lastPickedLongitude;
    private String lockedLocationText;
    /** True when coordinates were passed in with the initial address (unchanged save skips geocoder). */
    private boolean initialCoordsFromEvent;

    private AppCompatAutoCompleteTextView etLocation;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_location);

        String initial = getIntent().getStringExtra(EditLocationContract.EXTRA_INITIAL_LOCATION);
        if (initial == null) {
            initial = "";
        }

        etLocation = findViewById(R.id.edit_location_et);
        etLocation.setText(initial);
        etLocation.setSelection(etLocation.getText() != null ? etLocation.getText().length() : 0);
        if (!initial.isEmpty()) {
            lockedLocationText = initial.trim();
        }

        if (getIntent().hasExtra(EditLocationContract.EXTRA_INITIAL_LATITUDE)
                && getIntent().hasExtra(EditLocationContract.EXTRA_INITIAL_LONGITUDE)) {
            double lat = getIntent().getDoubleExtra(EditLocationContract.EXTRA_INITIAL_LATITUDE, Double.NaN);
            double lng = getIntent().getDoubleExtra(EditLocationContract.EXTRA_INITIAL_LONGITUDE, Double.NaN);
            if (!Double.isNaN(lat) && !Double.isNaN(lng) && isFinitePlausibleLatLng(lat, lng)) {
                lastPickedLatitude = lat;
                lastPickedLongitude = lng;
                initialCoordsFromEvent = true;
            }
        }

        ImageButton btnClose = findViewById(R.id.edit_location_btn_close);
        ImageButton btnSave = findViewById(R.id.edit_location_btn_save);
        TextView btnPreviewMap = findViewById(R.id.edit_location_btn_preview_map);

        initPlacesIfPossible();
        setupLocationAutocomplete();

        btnClose.setOnClickListener(v -> finish());
        btnSave.setOnClickListener(v -> save());
        btnPreviewMap.setOnClickListener(v -> openMapPreview());
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        placesHandler.removeCallbacksAndMessages(null);
        validationExecutor.shutdown();
    }

    private String getMapsApiKey() {
        try {
            android.content.pm.ApplicationInfo ai = getPackageManager()
                    .getApplicationInfo(getPackageName(), PackageManager.GET_META_DATA);
            if (ai.metaData != null) {
                return ai.metaData.getString("com.google.android.geo.API_KEY");
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private void initPlacesIfPossible() {
        String key = getMapsApiKey();
        if (key == null || key.isEmpty()) {
            return;
        }
        try {
            if (!Places.isInitialized()) {
                Places.initialize(getApplicationContext(), key);
            }
            placesClient = Places.createClient(this);
            placesInitialized = true;
        } catch (Exception e) {
            placesInitialized = false;
            placesClient = null;
        }
    }

    private void setupLocationAutocomplete() {
        if (etLocation == null) {
            return;
        }
        etLocation.setThreshold(PLACES_MIN_QUERY_LEN);
        placesAdapter = new ArrayAdapter<String>(this,
                android.R.layout.simple_dropdown_item_1line,
                new ArrayList<String>()) {
            @Override
            public Filter getFilter() {
                return new Filter() {
                    @Override
                    protected FilterResults performFiltering(CharSequence constraint) {
                        FilterResults results = new FilterResults();
                        synchronized (placesSuggestionLines) {
                            results.values = new ArrayList<>(placesSuggestionLines);
                            results.count = placesSuggestionLines.size();
                        }
                        return results;
                    }

                    @Override
                    protected void publishResults(CharSequence constraint, FilterResults results) {
                        clear();
                        if (results != null && results.count > 0) {
                            @SuppressWarnings("unchecked")
                            List<String> list = (List<String>) results.values;
                            addAll(list);
                        }
                        notifyDataSetChanged();
                    }
                };
            }
        };
        etLocation.setAdapter(placesAdapter);
        etLocation.setOnItemClickListener((parent, view, position, id) -> {
            placesHandler.removeCallbacks(placesDebouncedQuery);
            initialCoordsFromEvent = false;
            if (position < 0 || position >= placesPredictions.size()) {
                return;
            }
            AutocompletePrediction pred = placesPredictions.get(position);
            String preview = pred.getFullText(null).toString();
            lockedLocationText = preview;
            locationTextProgrammatic = true;
            etLocation.setText(preview);
            etLocation.setSelection(preview.length());
            locationTextProgrammatic = false;
            clearPlaceSuggestions();
            etLocation.dismissDropDown();
            fetchPlaceDetails(pred.getPlaceId());
        });
        etLocation.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus && lockedLocationText != null && etLocation.getText() != null
                    && lockedLocationText.equals(etLocation.getText().toString().trim())) {
                etLocation.dismissDropDown();
            }
        });
        etLocation.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (locationTextProgrammatic) {
                    return;
                }
                String str = s != null ? s.toString() : "";
                String trimmed = str.trim();
                if (lockedLocationText != null && lockedLocationText.equals(trimmed)) {
                    placesHandler.removeCallbacks(placesDebouncedQuery);
                    clearPlaceSuggestions();
                    etLocation.dismissDropDown();
                    return;
                }
                if (lockedLocationText != null && !lockedLocationText.equals(trimmed)) {
                    lockedLocationText = null;
                    lastPickedLatitude = null;
                    lastPickedLongitude = null;
                }
                placesHandler.removeCallbacks(placesDebouncedQuery);
                placesHandler.postDelayed(placesDebouncedQuery, PLACES_DEBOUNCE_MS);
            }
        });
        if (placesInitialized) {
            placesSessionToken = AutocompleteSessionToken.newInstance();
        }
    }

    private void runDebouncedPlaceQuery() {
        if (etLocation == null || !placesInitialized || placesClient == null) {
            return;
        }
        String q = etLocation.getText() != null ? etLocation.getText().toString().trim() : "";
        if (lockedLocationText != null && lockedLocationText.equals(q)) {
            clearPlaceSuggestions();
            etLocation.dismissDropDown();
            return;
        }
        if (q.length() < PLACES_MIN_QUERY_LEN) {
            clearPlaceSuggestions();
            return;
        }
        if (placesSessionToken == null) {
            placesSessionToken = AutocompleteSessionToken.newInstance();
        }
        FindAutocompletePredictionsRequest request = FindAutocompletePredictionsRequest.builder()
                .setSessionToken(placesSessionToken)
                .setQuery(q)
                .build();
        placesClient.findAutocompletePredictions(request)
                .addOnSuccessListener(response -> {
                    placesPredictions.clear();
                    synchronized (placesSuggestionLines) {
                        placesSuggestionLines.clear();
                        for (AutocompletePrediction p : response.getAutocompletePredictions()) {
                            placesPredictions.add(p);
                            placesSuggestionLines.add(p.getFullText(null).toString());
                        }
                    }
                    placesAdapter.getFilter().filter(etLocation.getText());
                    if (!placesPredictions.isEmpty() && etLocation.hasFocus()) {
                        etLocation.showDropDown();
                    }
                })
                .addOnFailureListener(e -> { /* plain text still works */ });
    }

    private void clearPlaceSuggestions() {
        placesPredictions.clear();
        synchronized (placesSuggestionLines) {
            placesSuggestionLines.clear();
        }
        if (placesAdapter != null) {
            placesAdapter.getFilter().filter(etLocation != null ? etLocation.getText() : "");
        }
    }

    private void fetchPlaceDetails(String placeId) {
        if (!placesInitialized || placesClient == null || placeId == null || etLocation == null) {
            return;
        }
        placesHandler.removeCallbacks(placesDebouncedQuery);
        List<Place.Field> fields = Arrays.asList(
                Place.Field.ADDRESS, Place.Field.NAME, Place.Field.LAT_LNG);
        FetchPlaceRequest request = FetchPlaceRequest.newInstance(placeId, fields);
        placesClient.fetchPlace(request)
                .addOnSuccessListener(response -> {
                    Place place = response.getPlace();
                    if (place == null) {
                        lockedLocationText = null;
                        lastPickedLatitude = null;
                        lastPickedLongitude = null;
                        return;
                    }
                    com.google.android.gms.maps.model.LatLng ll = place.getLatLng();
                    if (ll != null) {
                        lastPickedLatitude = ll.latitude;
                        lastPickedLongitude = ll.longitude;
                    } else {
                        lastPickedLatitude = null;
                        lastPickedLongitude = null;
                    }
                    String line = place.getAddress();
                    if (line == null || line.isEmpty()) {
                        line = place.getName();
                    }
                    if (line != null && !line.isEmpty()) {
                        locationTextProgrammatic = true;
                        etLocation.setText(line);
                        etLocation.setSelection(line.length());
                        locationTextProgrammatic = false;
                        lockedLocationText = line;
                    }
                    placesSessionToken = AutocompleteSessionToken.newInstance();
                    clearPlaceSuggestions();
                    etLocation.dismissDropDown();
                    etLocation.clearFocus();
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this, R.string.event_create_places_unavailable, Toast.LENGTH_SHORT).show());
    }

    private void openMapPreview() {
        String loc = etLocation.getText() != null ? etLocation.getText().toString().trim() : "";
        if (loc.isEmpty()) {
            Toast.makeText(this, R.string.edit_location_need_address_for_map, Toast.LENGTH_SHORT).show();
            return;
        }
        Intent i = new Intent(this, MapPreviewActivity.class);
        i.putExtra(MapPreviewActivity.EXTRA_LOCATION, loc);
        i.putExtra(MapPreviewActivity.EXTRA_EVENT_NAME, getString(R.string.edit_location_title));
        startActivity(i);
    }

    private void save() {
        String loc = etLocation.getText() != null ? etLocation.getText().toString().trim() : "";
        if (lastPickedLatitude != null && lastPickedLongitude != null
                && lockedLocationText != null && lockedLocationText.trim().equals(loc)) {
            if (!isFinitePlausibleLatLng(lastPickedLatitude, lastPickedLongitude)) {
                Toast.makeText(this, R.string.edit_location_verify_failed, Toast.LENGTH_LONG).show();
                return;
            }
            if (initialCoordsFromEvent) {
                finishWithLocationResult(loc, lastPickedLatitude, lastPickedLongitude);
            } else {
                verifyAddressWithGeocoderThenFinish(loc, lastPickedLatitude, lastPickedLongitude);
            }
            return;
        }
        if (loc.isEmpty() || "TBD".equalsIgnoreCase(loc)) {
            finishWithLocationResult(loc, null, null);
            return;
        }
        Toast.makeText(this, R.string.event_select_place_from_suggestions, Toast.LENGTH_LONG).show();
    }

    /**
     * After a successful Places pick, require Android Geocoder to resolve the same address string
     * before returning coordinates to the edit-event flow.
     */
    private void verifyAddressWithGeocoderThenFinish(String loc, double placeLat, double placeLng) {
        if (!Geocoder.isPresent()) {
            Toast.makeText(this, R.string.edit_location_verify_failed, Toast.LENGTH_LONG).show();
            return;
        }
        final ImageButton btnSave = findViewById(R.id.edit_location_btn_save);
        if (btnSave != null) {
            btnSave.setEnabled(false);
        }
        validationExecutor.execute(() -> {
            boolean ok = false;
            try {
                Geocoder g = new Geocoder(getApplicationContext(), Locale.getDefault());
                List<Address> r = g.getFromLocationName(loc, 1);
                ok = r != null && !r.isEmpty();
            } catch (IOException ignored) {
            }
            final boolean pass = ok;
            runOnUiThread(() -> {
                if (isFinishing()) {
                    return;
                }
                if (btnSave != null) {
                    btnSave.setEnabled(true);
                }
                if (pass) {
                    finishWithLocationResult(loc, placeLat, placeLng);
                } else {
                    Toast.makeText(EditLocationActivity.this, R.string.edit_location_verify_failed,
                            Toast.LENGTH_LONG).show();
                }
            });
        });
    }

    private static boolean isFinitePlausibleLatLng(double lat, double lng) {
        return !Double.isNaN(lat) && !Double.isNaN(lng)
                && Math.abs(lat) <= 90.0 && Math.abs(lng) <= 180.0;
    }

    private void finishWithLocationResult(String loc, Double latitude, Double longitude) {
        Intent data = new Intent();
        data.putExtra(EditResultKinds.EXTRA_KIND, EditResultKinds.KIND_LOCATION);
        data.putExtra(EditLocationContract.RESULT_LOCATION, loc);
        if (latitude != null && longitude != null) {
            data.putExtra(EditLocationContract.RESULT_LATITUDE, latitude);
            data.putExtra(EditLocationContract.RESULT_LONGITUDE, longitude);
        }
        setResult(RESULT_OK, data);
        finish();
    }
}
