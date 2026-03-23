package com.example.cobaltevents.ui;

/**
 * Extras for {@link EditLocationActivity} (Places + map preview, same behavior as create event).
 */
public final class EditLocationContract {

    public static final String EXTRA_INITIAL_LOCATION = "initial_location";
    /** Optional; existing venue coordinates when editing so save works without re-picking from Places. */
    public static final String EXTRA_INITIAL_LATITUDE = "initial_latitude";
    public static final String EXTRA_INITIAL_LONGITUDE = "initial_longitude";
    public static final String RESULT_LOCATION = "result_location";
    /** Optional; from Places {@link com.google.android.libraries.places.api.model.Place#getLatLng()}. */
    public static final String RESULT_LATITUDE = "result_latitude";
    public static final String RESULT_LONGITUDE = "result_longitude";

    private EditLocationContract() {}
}
