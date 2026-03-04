package com.example.cobaltevents.controller;

import android.content.Context;
import android.provider.Settings;

import com.example.cobaltevents.db.EntrantDB;
import com.example.cobaltevents.model.Entrant;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;

/**
 * Manages entrant profiles, validation, and notification preferences.
 * Merges main's validation with lx's Firestore async + notification opt-out (US 01.04.03).
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

    // --- Validation methods (from main) ---

    public String validateName(String name) {
        if (name == null || name.trim().isEmpty())
            return "Name is required.";
        return null;
    }

    public String validateEmail(String email) {
        if (email == null || email.trim().isEmpty())
            return "Email is required.";
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches())
            return "Invalid email format.";
        return null;
    }

    public String validatePhone(String phone) {
        if (phone == null || phone.trim().isEmpty())
            return null; // optional
        if (!phone.matches("^[0-9+()\\-\\s]{7,20}$"))
            return "Invalid phone number.";
        return null;
    }

    // --- Firestore async methods (from lx) ---

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

    /** Saves entrant profile to Firestore. */
    public void saveEntrant(Entrant entrant, OnSuccessListener<Void> onSuccess, OnFailureListener onFailure) {
        entrantDB.saveEntrant(entrant, onSuccess, onFailure);
    }

    /**
     * Updates the notification opt-out preference.
     * US 01.04.03: Entrant can opt out of receiving notifications.
     */
    public void setNotificationsEnabled(String deviceId, boolean enabled, OnSuccessListener<Void> onSuccess, OnFailureListener onFailure) {
        entrantDB.updateNotificationPreference(deviceId, enabled, onSuccess, onFailure);
    }

    /** Reads the current entrant profile. */
    public void getEntrant(String deviceId, OnSuccessListener<Entrant> onSuccess, OnFailureListener onFailure) {
        entrantDB.getEntrant(deviceId, onSuccess, onFailure);
    }

    public interface OnEntrantReadyListener {
        void onEntrantReady(Entrant entrant);
        void onError(Exception e);
    }
}
