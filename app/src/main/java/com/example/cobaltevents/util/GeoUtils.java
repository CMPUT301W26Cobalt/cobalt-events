package com.example.cobaltevents.util;

import android.content.Context;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.os.Handler;
import android.os.Looper;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

public class GeoUtils {

    private static final float MAX_DISTANCE_KM = 30f;
    private static final long TIMEOUT_MS = 10_000L;

    // Static reference prevents garbage collection of the callback
    private static LocationCallback activeCallback = null;
    private static FusedLocationProviderClient activeClient = null;

    public interface GeoCheckCallback {
        void onResult(boolean withinRange);
        void onError(String message);
    }

    @SuppressWarnings("MissingPermission")
    public static void fetchLocationAndCheck(Context context,
                                             String eventAddress,
                                             GeoCheckCallback callback) {
        if (eventAddress == null || eventAddress.isEmpty() || eventAddress.equals("TBD")) {
            callback.onResult(true);
            return;
        }

        FusedLocationProviderClient client = LocationServices.getFusedLocationProviderClient(context);
        activeClient = client;

        // Cancel any in-flight request
        cancelPending();

        Handler timeoutHandler = new Handler(Looper.getMainLooper());

        activeCallback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult result) {
                timeoutHandler.removeCallbacksAndMessages(null);
                cancelPending();
                Location location = result.getLastLocation();
                if (location != null) {
                    checkDistance(context, location, eventAddress, callback);
                } else {
                    callback.onError("Could not get your location. Please try again.");
                }
            }
        };

        LocationRequest request = new LocationRequest.Builder(Priority.PRIORITY_BALANCED_POWER_ACCURACY, 2000)
                .setMinUpdateIntervalMillis(0)
                .setMaxUpdates(1)
                .build();

        client.requestLocationUpdates(request, activeCallback, Looper.getMainLooper());

        // If no location arrives within 10 seconds, fall back to last known
        timeoutHandler.postDelayed(() -> {
            cancelPending();
            client.getLastLocation().addOnSuccessListener(lastLocation -> {
                if (lastLocation != null) {
                    long ageMs = System.currentTimeMillis() - lastLocation.getTime();
                    if (ageMs < 5 * 60 * 1000L) {
                        checkDistance(context, lastLocation, eventAddress, callback);
                    } else {
                        callback.onError("Location is unavailable. Please step outside or open Google Maps to refresh your location, then try again.");
                    }
                } else {
                    callback.onError("Location is unavailable. Please open Google Maps once to warm up location, then try again.");
                }
            }).addOnFailureListener(e ->
                    callback.onError("Could not get your location. Please try again."));
        }, TIMEOUT_MS);
    }

    private static void cancelPending() {
        if (activeCallback != null && activeClient != null) {
            activeClient.removeLocationUpdates(activeCallback);
            activeCallback = null;
        }
    }

    private static void checkDistance(Context context,
                                      Location userLocation,
                                      String eventAddress,
                                      GeoCheckCallback callback) {
        new Thread(() -> {
            try {
                Geocoder geocoder = new Geocoder(context, Locale.getDefault());
                List<Address> addresses = geocoder.getFromLocationName(eventAddress, 1);
                if (addresses == null || addresses.isEmpty()) {
                    runOnMain(() -> callback.onResult(true));
                    return;
                }
                Address eventAddr = addresses.get(0);
                float[] result = new float[1];
                Location.distanceBetween(
                        userLocation.getLatitude(), userLocation.getLongitude(),
                        eventAddr.getLatitude(), eventAddr.getLongitude(),
                        result);
                float distanceKm = result[0] / 1000f;
                runOnMain(() -> callback.onResult(distanceKm <= MAX_DISTANCE_KM));
            } catch (IOException e) {
                runOnMain(() -> callback.onResult(true));
            }
        }).start();
    }

    private static void runOnMain(Runnable r) {
        new Handler(Looper.getMainLooper()).post(r);
    }
}
