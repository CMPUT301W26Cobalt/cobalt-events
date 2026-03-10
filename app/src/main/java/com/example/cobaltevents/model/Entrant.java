package com.example.cobaltevents.model;

import android.util.Patterns;

/**
 * Data model for an entrant (user who enters event lotteries).
 *
 * Fields:
 * - deviceId: unique device identifier (Settings.Secure.ANDROID_ID), used as
 *   the Firestore document ID and as the recipientId for notifications.
 * - name, email, phone: profile information.
 * - Validation methods for profile fields (from main branch).
 *
 * Modified for US 01.04.01: added deviceId so we can match notifications
 * to the correct device.
 */
public class Entrant {

    private String deviceId;
    private String name;
    private String email;
    private String phone; // this is optional so hint feature in XML is utilized

    /** No-arg constructor required by Firestore deserialization. */
    public Entrant() {}

    public Entrant(String name, String email, String phone) {
        this.name = name;
        this.email = email;
        this.phone = phone;
    }

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

    public String getDeviceId() { return deviceId; }
    public void setDeviceId(String deviceId) { this.deviceId = deviceId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
}
