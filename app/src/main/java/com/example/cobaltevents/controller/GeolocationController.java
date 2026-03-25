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
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.gms.tasks.CancellationTokenSource;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class GeolocationController {

    public static final float MAX_JOIN_DISTANCE_METERS = 30_000f;
    private static final long LEGACY_LOCATION_TIMEOUT_MS = 25_000L;
    /** Ignore {@link LocationManager} last-known fixes older than this (often wrong city on emulators). */
    private static final long MAX_LAST_KNOWN_AGE_MS = 120_000L;
    private static final String TAG = "GeolocationController";

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

    public void fetchLocationOnStartup(Context context) {
        if (!hasLocationPermission(context)) return;
        getCurrentDeviceLocation(context, location -> {
            if (location != null) {
                appLocation = location;
                Log.d(TAG, "Startup location stored: " + location.getLatitude() + "," + location.getLongitude());
            }
        });
    }

    private void runOnMainThread(Runnable r) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            r.run();
        } else {
            new Handler(Looper.getMainLooper()).post(r);
        }
    }

    /** Same distance rule as the join flow when event lat/lng are already known. */
    private void checkDistanceWithKnownAnchor(Context context, double eventLat, double eventLng,
                                            GeoJoinCallback callback) {
        getCurrentDeviceLocation(context, freshLoc -> runOnMainThread(() -> {
            if (freshLoc == null) {
                callback.onError("Could not get your location. Make sure location is enabled.");
                return;
            }
            appLocation = freshLoc;
            float[] out = new float[1];
            Location.distanceBetween(
                    freshLoc.getLatitude(), freshLoc.getLongitude(),
                    eventLat, eventLng, out);
            float distMeters = out[0];
            Log.d(TAG, "Distance check — user: " + freshLoc.getLatitude() + "," + freshLoc.getLongitude()
                    + " event anchor: " + eventLat + "," + eventLng + " → " + distMeters + "m");
            if (distMeters > MAX_JOIN_DISTANCE_METERS) {
                callback.onBlocked(distMeters);
            } else {
                callback.onAllowed(freshLoc);
            }
        }));
    }

    /**
     * Always obtains a fix for the join attempt (no stale {@link #appLocation} shortcut).
     * Uses Play Services fused location first so Android Emulator mock locations are honored.
     * <p>
     * When the event needs a geocoded anchor (no stored lat/lng), device location and address geocoding
     * run in parallel to reduce wall-clock time before the same distance check as before.
     */
    public void checkDistanceForEvent(Context context, Event event, GeoJoinCallback callback) {
        String address = event.getLocation();
        if (address == null || address.isEmpty() || address.equals("TBD")) {
            getRealtimeDeviceLocationForJoin(context, freshLoc -> runOnMainThread(() -> {
                if (freshLoc == null) {
                    callback.onError("Could not get your location. Make sure location is enabled.");
                    return;
                }
                appLocation = freshLoc;
                callback.onAllowed(freshLoc);
            }));
            return;
        }

        Double storedLat = event.getLocationLatitude();
        Double storedLng = event.getLocationLongitude();
        if (storedLat != null && storedLng != null) {
            checkDistanceWithKnownAnchor(context, storedLat, storedLng, callback);
            return;
        }

        final Location[] userLocHolder = new Location[1];
        final double[] eventAnchorLat = new double[1];
        final double[] eventAnchorLng = new double[1];
        final boolean[] geocodeResolved = new boolean[1];
        AtomicInteger pending = new AtomicInteger(2);

        Runnable mergeOnMain = () -> {
            if (pending.decrementAndGet() != 0) {
                return;
            }
            runOnMainThread(() -> {
                Location userLoc = userLocHolder[0];
                if (userLoc == null) {
                    callback.onError("Could not get your location. Make sure location is enabled.");
                    return;
                }
                appLocation = userLoc;
                if (!geocodeResolved[0]) {
                    callback.onAllowed(userLoc);
                    return;
                }
                float[] out = new float[1];
                Location.distanceBetween(
                        userLoc.getLatitude(), userLoc.getLongitude(),
                        eventAnchorLat[0], eventAnchorLng[0], out);
                float distMeters = out[0];
                Log.d(TAG, "Distance check — user: " + userLoc.getLatitude() + "," + userLoc.getLongitude()
                        + " event anchor: " + eventAnchorLat[0] + "," + eventAnchorLng[0] + " → " + distMeters + "m");
                if (distMeters > MAX_JOIN_DISTANCE_METERS) {
                    callback.onBlocked(distMeters);
                } else {
                    callback.onAllowed(userLoc);
                }
            });
        };

        getRealtimeDeviceLocationForJoin(context, loc -> {
            userLocHolder[0] = loc;
            mergeOnMain.run();
        });

        geoExecutor.execute(() -> {
            try {
                Geocoder geocoder = new Geocoder(context, Locale.getDefault());
                List<Address> results = geocoder.getFromLocationName(address, 1);
                if (results != null && !results.isEmpty()) {
                    eventAnchorLat[0] = results.get(0).getLatitude();
                    eventAnchorLng[0] = results.get(0).getLongitude();
                    geocodeResolved[0] = true;
                }
            } catch (IOException | IllegalArgumentException ignored) {
            } finally {
                mergeOnMain.run();
            }
        });
    }

    /**
     * For join / geo verification only: race {@link FusedLocationProviderClient#getCurrentLocation} against
     * fresh {@link LocationManager#requestLocationUpdates} (started immediately). First real-time fix wins.
     * No fused {@link FusedLocationProviderClient#getLastLocation} and no {@link LocationManager#getLastKnownLocation}.
     */
    @SuppressWarnings("MissingPermission")
    private void getRealtimeDeviceLocationForJoin(Context context, OnLocationResult cb) {
        if (!hasLocationPermission(context)) {
            cb.onResult(null);
            return;
        }

        final AtomicBoolean delivered = new AtomicBoolean(false);
        final Handler handler = new Handler(Looper.getMainLooper());
        final CancellationTokenSource cts = new CancellationTokenSource();
        final LocationManager[] lmRef = new LocationManager[1];
        final LocationListener[] listenerRef = new LocationListener[1];
        final Runnable[] timeoutRef = new Runnable[1];

        Runnable finish = () -> {
            LocationManager lm = lmRef[0];
            LocationListener lis = listenerRef[0];
            if (timeoutRef[0] != null) {
                handler.removeCallbacks(timeoutRef[0]);
                timeoutRef[0] = null;
            }
            if (lis != null && lm != null) {
                try {
                    lm.removeUpdates(lis);
                } catch (Exception ignored) {
                }
            }
            listenerRef[0] = null;
            lmRef[0] = null;
            try {
                cts.cancel();
            } catch (Exception ignored) {
            }
        };

        timeoutRef[0] = () -> {
            if (!delivered.compareAndSet(false, true)) {
                return;
            }
            finish.run();
            Log.w(TAG, "Join: location race timed out");
            cb.onResult(null);
        };

        FusedLocationProviderClient fused = LocationServices.getFusedLocationProviderClient(context);
        Task<Location> current = fused.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cts.getToken());
        current.addOnSuccessListener(loc -> {
            if (loc == null) {
                return;
            }
            if (!delivered.compareAndSet(false, true)) {
                return;
            }
            finish.run();
            Log.d(TAG, "Join: Fused getCurrentLocation (race): " + loc.getLatitude() + "," + loc.getLongitude());
            cb.onResult(loc);
        }).addOnFailureListener(e -> Log.w(TAG, "Join getCurrentLocation failed: " + e.getMessage()));

        LocationManager lm = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        lmRef[0] = lm;
        if (lm != null) {
            listenerRef[0] = location -> {
                if (!delivered.compareAndSet(false, true)) {
                    return;
                }
                finish.run();
                Log.d(TAG, "Join: Fresh update (race): " + location.getLatitude() + "," + location.getLongitude());
                cb.onResult(location);
            };
            int registered = 0;
            for (String p : Arrays.asList(
                    LocationManager.GPS_PROVIDER,
                    LocationManager.NETWORK_PROVIDER,
                    LocationManager.PASSIVE_PROVIDER)) {
                try {
                    lm.requestLocationUpdates(p, 0, 0, listenerRef[0], Looper.getMainLooper());
                    registered++;
                } catch (Exception e) {
                    Log.w(TAG, "Could not register " + p + ": " + e.getMessage());
                }
            }
            if (registered == 0) {
                listenerRef[0] = null;
                lmRef[0] = null;
            }
        }

        handler.postDelayed(timeoutRef[0], LEGACY_LOCATION_TIMEOUT_MS);
    }

    /**
     * Prefer {@link FusedLocationProviderClient} so emulator “Extended controls → Location” mock
     * is applied. Falls back to legacy {@link LocationManager} only if needed.
     */
    @SuppressWarnings("MissingPermission")
    public void getCurrentDeviceLocation(Context context, OnLocationResult cb) {
        if (!hasLocationPermission(context)) { cb.onResult(null); return; }

        FusedLocationProviderClient fused = LocationServices.getFusedLocationProviderClient(context);
        CancellationTokenSource cts = new CancellationTokenSource();

        Task<Location> current = fused.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cts.getToken());
        current.addOnSuccessListener(loc -> {
            if (loc != null) {
                Log.d(TAG, "Fused getCurrentLocation: " + loc.getLatitude() + "," + loc.getLongitude());
                cb.onResult(loc);
                return;
            }
            tryFusedLastLocationThenLegacy(context, fused, cb);
        }).addOnFailureListener(e -> {
            Log.w(TAG, "getCurrentLocation failed: " + e.getMessage());
            tryFusedLastLocationThenLegacy(context, fused, cb);
        });
    }

    private void tryFusedLastLocationThenLegacy(Context context, FusedLocationProviderClient fused,
                                                OnLocationResult cb) {
        fused.getLastLocation().addOnSuccessListener(loc -> {
            if (loc != null) {
                Log.d(TAG, "Fused getLastLocation: " + loc.getLatitude() + "," + loc.getLongitude());
                cb.onResult(loc);
            } else {
                getCurrentDeviceLocationLegacy(context, cb);
            }
        }).addOnFailureListener(e -> getCurrentDeviceLocationLegacy(context, cb));
    }

    @SuppressWarnings("MissingPermission")
    private void getCurrentDeviceLocationLegacy(Context context, OnLocationResult cb) {
        LocationManager lm = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        if (lm == null) {
            cb.onResult(null);
            return;
        }

        Location best = null;
        for (String p : Arrays.asList(
                LocationManager.GPS_PROVIDER,
                LocationManager.NETWORK_PROVIDER,
                LocationManager.PASSIVE_PROVIDER)) {
            try {
                Location loc = lm.getLastKnownLocation(p);
                if (loc == null) continue;
                long age = System.currentTimeMillis() - loc.getTime();
                if (age > MAX_LAST_KNOWN_AGE_MS) {
                    continue;
                }
                if (best == null || loc.getTime() > best.getTime()) {
                    best = loc;
                }
            } catch (Exception ignored) {}
        }
        if (best != null) {
            Log.d(TAG, "Legacy lastKnown (fresh): " + best.getLatitude() + "," + best.getLongitude());
            cb.onResult(best);
            return;
        }

        requestFreshLocationUpdatesOnly(context, cb);
    }

    /**
     * New fixes from {@link LocationManager} only (no last-known reads).
     */
    @SuppressWarnings("MissingPermission")
    private void requestFreshLocationUpdatesOnly(Context context, OnLocationResult cb) {
        LocationManager lm = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        if (lm == null) {
            cb.onResult(null);
            return;
        }

        Handler handler = new Handler(Looper.getMainLooper());
        final AtomicBoolean delivered = new AtomicBoolean(false);

        activeLocationListener = location -> {
            if (!delivered.compareAndSet(false, true)) return;
            handler.removeCallbacksAndMessages(null);
            try {
                lm.removeUpdates(activeLocationListener);
            } catch (Exception ignored) {}
            activeLocationListener = null;
            Log.d(TAG, "Fresh location update: " + location.getLatitude() + "," + location.getLongitude());
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

        if (registered == 0) {
            cb.onResult(null);
            return;
        }

        handler.postDelayed(() -> {
            if (!delivered.compareAndSet(false, true)) return;
            try {
                lm.removeUpdates(activeLocationListener);
            } catch (Exception ignored) {}
            activeLocationListener = null;
            Log.w(TAG, "Location timed out (fresh updates)");
            cb.onResult(null);
        }, LEGACY_LOCATION_TIMEOUT_MS);
    }

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
