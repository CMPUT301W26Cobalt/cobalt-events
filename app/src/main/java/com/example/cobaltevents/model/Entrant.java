package com.example.cobaltevents.model;

/**
 * Data model for an app user/participant stored in the Firestore "entrants" collection.
 * The notificationsEnabled field supports US 01.04.03 (opt out of notifications).
 */
public class Entrant {

    private String deviceId;
    private String name;
    private String email;
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

    public String getDeviceId() { return deviceId; }
    public void setDeviceId(String deviceId) { this.deviceId = deviceId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public boolean isNotificationsEnabled() { return notificationsEnabled; }
    public void setNotificationsEnabled(boolean notificationsEnabled) { this.notificationsEnabled = notificationsEnabled; }
}
