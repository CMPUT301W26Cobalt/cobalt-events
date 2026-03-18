package com.example.cobaltevents.controller;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.core.app.ActivityCompat;

import com.example.cobaltevents.db.WaitingListDB;
import com.example.cobaltevents.model.Event;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class GeolocationController {

    public static final float MAX_JOIN_DISTANCE_METERS = 30_000f;
    private static final long LOCATION_TIMEOUT_MS = 10_000L;
    private static final String TAG = "GeolocationController";

    // Fetched once on app startup — reused for all geo checks
    private static Location appLocation = null;

    private final WaitingListDB waitingListDB;
    private final ExecutorService geoExecutor = Executors.newSingleThreadExecutor();
    private LocationListener activeLocationListener;

    public interface OnLocationResult { void onResult(Location location); }
    public interface GeoJoinCallback {
        void onAllowed(Location userLocation);
        void onBlocked(float distanceMeters);
        void onError(String message);
    }

    public GeolocationController() {
        this.waitingListDB = new WaitingListDB();
    }

    public static Location getAppLocation() { return appLocation; }

    public boolean hasLocationPermission(Context context) {
        return ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED
                || ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    // -------------------------------------------------------------------------
    // Called once on app startup — fetches and stores location for entire session
    // -------------------------------------------------------------------------
    public void fetchLocationOnStartup(Context context) {
        if (!hasLocationPermission(context)) return;
        getCurrentDeviceLocation(context, location -> {
            if (location != null) {
                appLocation = location;
                Log.d(TAG, "Startup location stored: " + location.getLatitude() + "," + location.getLongitude());
            }
        });
    }

    // -------------------------------------------------------------------------
    // Main geo-check: uses startup location (instant) + geocodes event address
    // -------------------------------------------------------------------------
    public void checkDistanceForEvent(Context context, Event event, GeoJoinCallback callback) {
        Location userLoc = appLocation;

        if (userLoc == null) {
            // Startup location not ready yet — fetch now as fallback
            getCurrentDeviceLocation(context, freshLoc -> {
                if (freshLoc == null) {
                    callback.onError("Could not get your location. Make sure location is enabled.");
                    return;
                }
                appLocation = freshLoc;
                geocodeAndCompare(context, event, freshLoc, callback);
            });
            return;
        }

        geocodeAndCompare(context, event, userLoc, callback);
    }

    private void geocodeAndCompare(Context context, Event event,
                                    Location userLoc, GeoJoinCallback callback) {
        String address = event.getLocation();
        if (address == null || address.isEmpty() || address.equals("TBD")) {
            callback.onAllowed(userLoc);
            return;
        }

        geoExecutor.execute(() -> {
            try {
                Geocoder geocoder = new Geocoder(context, Locale.getDefault());
                List<Address> results = geocoder.getFromLocationName(address, 1);
                if (results == null || results.isEmpty()) {
                    new Handler(Looper.getMainLooper()).post(() -> callback.onAllowed(userLoc));
                    return;
                }
                double eventLat = results.get(0).getLatitude();
                double eventLng = results.get(0).getLongitude();
                float[] out = new float[1];
                Location.distanceBetween(
                        userLoc.getLatitude(), userLoc.getLongitude(),
                        eventLat, eventLng, out);
                float distMeters = out[0];
                Log.d(TAG, "Distance to event: " + distMeters + "m");
                new Handler(Looper.getMainLooper()).post(() -> {
                    if (distMeters > MAX_JOIN_DISTANCE_METERS) {
                        callback.onBlocked(distMeters);
                    } else {
                        callback.onAllowed(userLoc);
                    }
                });
            } catch (IOException | IllegalArgumentException e) {
                new Handler(Looper.getMainLooper()).post(() -> callback.onAllowed(userLoc));
            }
        });
    }

    // -------------------------------------------------------------------------
    // Location fetch via LocationManager — works on all devices and emulators
    // -------------------------------------------------------------------------
    @SuppressWarnings("MissingPermission")
    public void getCurrentDeviceLocation(Context context, OnLocationResult cb) {
        if (!hasLocationPermission(context)) { cb.onResult(null); return; }

        LocationManager lm = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        if (lm == null) { cb.onResult(null); return; }

        // Try last known first (instant)
        Location best = null;
        for (String p : Arrays.asList(
                LocationManager.GPS_PROVIDER,
                LocationManager.NETWORK_PROVIDER,
                LocationManager.PASSIVE_PROVIDER)) {
            try {
                Location loc = lm.getLastKnownLocation(p);
                if (loc != null && (best == null || loc.getTime() > best.getTime())) best = loc;
            } catch (Exception ignored) {}
        }
        if (best != null) { cb.onResult(best); return; }

        // Request live fix from all providers
        Handler handler = new Handler(Looper.getMainLooper());
        final boolean[] delivered = {false};

        activeLocationListener = location -> {
            if (delivered[0]) return;
            delivered[0] = true;
            handler.removeCallbacksAndMessages(null);
            try { lm.removeUpdates(activeLocationListener); } catch (Exception ignored) {}
            activeLocationListener = null;
            cb.onResult(location);
        };

        int registered = 0;
        for (String p : Arrays.asList(
                LocationManager.GPS_PROVIDER,
                LocationManager.NETWORK_PROVIDER,
                LocationManager.PASSIVE_PROVIDER)) {
            try {
                lm.requestLocationUpdates(p, 0, 0, activeLocationListener, Looper.getMainLooper());
                registered++;
            } catch (Exception e) {
                Log.w(TAG, "Could not register " + p + ": " + e.getMessage());
            }
        }

        if (registered == 0) { cb.onResult(null); return; }

        handler.postDelayed(() -> {
            if (delivered[0]) return;
            delivered[0] = true;
            try { lm.removeUpdates(activeLocationListener); } catch (Exception ignored) {}
            activeLocationListener = null;
            Log.w(TAG, "Location timed out");
            cb.onResult(null);
        }, LOCATION_TIMEOUT_MS);
    }

    // -------------------------------------------------------------------------
    // Save user lat/lng to the waitlist entry in Firestore
    // -------------------------------------------------------------------------
    public void recordLocationForEvent(Context context, String deviceId, String eventId,
                                       Location userLocation,
                                       OnSuccessListener<Void> onSuccess,
                                       OnFailureListener onFailure) {
        if (userLocation == null) return;
        waitingListDB.saveLocation(eventId, deviceId,
                userLocation.getLatitude(), userLocation.getLongitude(),
                onSuccess, onFailure);
    }
}
