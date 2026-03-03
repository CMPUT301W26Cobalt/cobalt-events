package com.example.cobaltevents.controller;

import android.content.Context;
import android.provider.Settings;

import com.example.cobaltevents.db.EntrantDB;
import com.example.cobaltevents.model.Entrant;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;

/**
 * Manages entrant profiles and notification preferences.
 * US 01.04.03: setNotificationsEnabled toggles the opt-out preference.
 * Supporting all stories: getOrCreateEntrant provides device-based identification.
 */
public class EntrantController {

    private final EntrantDB entrantDB;

    public EntrantController() {
        this.entrantDB = new EntrantDB();
    }

    /** Gets the device ID used for identifying the entrant (no username/password). */
    public static String getDeviceId(Context context) {
        return Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ANDROID_ID);
    }

    /**
     * Loads or creates the entrant profile based on device ID.
     * If no profile exists, creates a default one with notifications enabled.
     */
    public void getOrCreateEntrant(Context context, OnEntrantReadyListener listener) {
        String deviceId = getDeviceId(context);
        entrantDB.getEntrant(deviceId, entrant -> {
            if (entrant != null) {
                listener.onEntrantReady(entrant);
            } else {
                Entrant newEntrant = new Entrant(deviceId, "Entrant", "");
                entrantDB.saveEntrant(newEntrant,
                        unused -> listener.onEntrantReady(newEntrant),
                        e -> listener.onError(e)
                );
            }
        }, e -> listener.onError(e));
    }

    /**
     * Updates the notification opt-out preference.
     * US 01.04.03: Entrant can opt out of receiving notifications.
     */
    public void setNotificationsEnabled(String deviceId, boolean enabled, OnSuccessListener<Void> onSuccess, OnFailureListener onFailure) {
        entrantDB.updateNotificationPreference(deviceId, enabled, onSuccess, onFailure);
    }

    /** Reads the current notification preference for an entrant. */
    public void getEntrant(String deviceId, OnSuccessListener<Entrant> onSuccess, OnFailureListener onFailure) {
        entrantDB.getEntrant(deviceId, onSuccess, onFailure);
    }

    public interface OnEntrantReadyListener {
        void onEntrantReady(Entrant entrant);
        void onError(Exception e);
    }
}
