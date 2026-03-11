package com.example.cobaltevents.model;

import android.util.Patterns;

import com.google.firebase.firestore.Exclude;
import com.google.firebase.firestore.IgnoreExtraProperties;

/**
 * Data model for an entrant (user who enters event lotteries).
 *
 * @IgnoreExtraProperties stops Firestore from logging warnings about
 * computed fields like getInitials() that have no matching setter.
 */
@IgnoreExtraProperties
public class Entrant {

    private String deviceId;
    private String name;
    private String email;
    private String phone;
    private String profilePictureUrl;

    public Entrant() {} // Required for Firestore

    public Entrant(String name, String email, String phone) {
        this(null, name, email, phone, null);
    }

    public Entrant(String name, String email, String phone, String profilePictureUrl) {
        this(null, name, email, phone, profilePictureUrl);
    }

    public Entrant(String deviceId, String name, String email, String phone, String profilePictureUrl) {
        this.deviceId = deviceId;
        this.name = name;
        this.email = email;
        this.phone = phone;
        this.profilePictureUrl = profilePictureUrl;
    }

    @Exclude
    public boolean isValidName() {
        return name != null && !name.trim().isEmpty();
    }

    @Exclude
    public boolean isValidEmail() {
        return email != null &&
                !email.trim().isEmpty() &&
                Patterns.EMAIL_ADDRESS.matcher(email).matches();
    }

    @Exclude
    public boolean isValidPhone() {
        if (phone == null || phone.trim().isEmpty()) return true;
        return phone.matches("^[0-9+()\\-\\s]{7,20}$");
    }

    @Exclude
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

    public String getProfilePictureUrl() { return profilePictureUrl; }
    public void setProfilePictureUrl(String url) { this.profilePictureUrl = url; }

    @Exclude
    public String getInitials() {
        if (name == null || name.isEmpty()) return "??";
        String[] parts = name.trim().split("\\s+");
        if (parts.length >= 2) {
            return (parts[0].substring(0, 1) + parts[1].substring(0, 1)).toUpperCase();
        }
        return parts[0].substring(0, Math.min(2, parts[0].length())).toUpperCase();
    }
}