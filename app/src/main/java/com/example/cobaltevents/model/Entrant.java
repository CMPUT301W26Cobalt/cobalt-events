package com.example.cobaltevents.model;

import android.util.Patterns;

/**
 * Data model for an app user/participant stored in the Firestore "entrants" collection.
 * Merges main's profile fields (phone, validation) with lx's notification opt-out (US 01.04.03).
 */
public class Entrant {

    private String deviceId;
    private String name;
    private String email;
    private String phone; // optional
    private boolean notificationsEnabled;

    /** No-arg constructor required by Firestore deserialization. */
    public Entrant() {
        this.notificationsEnabled = true;
    }

    public Entrant(String deviceId, String name, String email) {
        this.deviceId = deviceId;
        this.name = name;
        this.email = email;
        this.notificationsEnabled = true;
    }

    public Entrant(String name, String email, String phone) {
        this.name = name;
        this.email = email;
        this.phone = phone;
        this.notificationsEnabled = true;
    }

    // --- Validation methods (from main) ---

    public boolean isValidName() {
        return name != null && !name.trim().isEmpty();
    }

    public boolean isValidEmail() {
        return email != null &&
                !email.trim().isEmpty() &&
                Patterns.EMAIL_ADDRESS.matcher(email).matches();
    }

    public boolean isValidPhone() {
        if (phone == null || phone.trim().isEmpty()) return true; // optional
        return phone.matches("^[0-9+()\\-\\s]{7,20}$");
    }

    public boolean isValid() {
        return isValidName() && isValidEmail() && isValidPhone();
    }

    // --- Getters and setters ---

    public String getDeviceId() { return deviceId; }
    public void setDeviceId(String deviceId) { this.deviceId = deviceId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public boolean isNotificationsEnabled() { return notificationsEnabled; }
    public void setNotificationsEnabled(boolean notificationsEnabled) { this.notificationsEnabled = notificationsEnabled; }
}
