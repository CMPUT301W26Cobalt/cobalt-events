package com.example.cobaltevents.model;

import android.util.Patterns;
import com.google.firebase.firestore.Exclude;

/**
 * Data model for an entrant (user who enters event lotteries).
 * This class stores profile information and device identification for notifications.
 */
public class Entrant {

    private String deviceId;
    private String name;
    private String email;
    private String phone;
    private String profilePictureUrl;
    private Boolean organizerEnabled;

    /**
     * Default constructor for Entrant. Required for Firestore.
     */
    public Entrant() {}

    /**
     * Constructs an Entrant with basic profile info.
     * @param name Name of the entrant.
     * @param email Email of the entrant.
     * @param phone Phone number of the entrant.
     */
    public Entrant(String name, String email, String phone) {
        this(null, name, email, phone, null);
    }

    /**
     * Constructs an Entrant with profile info and a picture.
     * @param name Name of the entrant.
     * @param email Email of the entrant.
     * @param phone Phone number of the entrant.
     * @param profilePictureUrl URL of the profile picture.
     */
    public Entrant(String name, String email, String phone, String profilePictureUrl) {
        this(null, name, email, phone, profilePictureUrl);
    }

    /**
     * Constructs an Entrant with full details.
     * @param deviceId Unique device identifier.
     * @param name Name of the entrant.
     * @param email Email of the entrant.
     * @param phone Phone number of the entrant.
     * @param profilePictureUrl URL of the profile picture.
     */
    public Entrant(String deviceId, String name, String email, String phone, String profilePictureUrl) {
        this.deviceId = deviceId;
        this.name = name;
        this.email = email;
        this.phone = phone;
        this.profilePictureUrl = profilePictureUrl;
    }

    /** @return True if the name is not null or empty. */
    @Exclude
    public boolean isValidName() {
        return name != null && !name.trim().isEmpty();
    }

    /** @return True if the email is in a valid format. */
    @Exclude
    public boolean isValidEmail() {
        return email != null &&
                !email.trim().isEmpty() &&
                Patterns.EMAIL_ADDRESS.matcher(email).matches();
    }

    /** @return True if the phone number is valid or empty. */
    @Exclude
    public boolean isValidPhone() {
        if (phone == null || phone.trim().isEmpty()) return true;
        return phone.matches("^[0-9+()\\-\\s]{7,20}$");
    }

    /** @return True if all profile fields are valid. */
    @Exclude
    public boolean isValid() {
        return isValidName() && isValidEmail() && isValidPhone();
    }

    /** @return Unique device identifier. */
    public String getDeviceId() { return deviceId; }
    /** @param deviceId Unique device identifier to set. */
    public void setDeviceId(String deviceId) { this.deviceId = deviceId; }

    /** @return Name of the entrant. */
    public String getName() { return name; }
    /** @param name Name to set. */
    public void setName(String name) { this.name = name; }

    /** @return Email of the entrant. */
    public String getEmail() { return email; }
    /** @param email Email to set. */
    public void setEmail(String email) { this.email = email; }

    /** @return Phone number of the entrant. */
    public String getPhone() { return phone; }
    /** @param phone Phone number to set. */
    public void setPhone(String phone) { this.phone = phone; }

    /** @return URL of the profile picture. */
    public String getProfilePictureUrl() { return profilePictureUrl; }
    /** @param profilePictureUrl URL to set. */
    public void setProfilePictureUrl(String profilePictureUrl) { this.profilePictureUrl = profilePictureUrl; }

    /** @return Whether the entrant has organizer features enabled. */
    public Boolean getOrganizerEnabled() { return organizerEnabled; }
    /** @param organizerEnabled Whether to enable organizer features. */
    public void setOrganizerEnabled(Boolean organizerEnabled) { this.organizerEnabled = organizerEnabled; }

    /** @return Initial characters of the entrant's name. */
    public String getInitials() {
        if (name == null || name.trim().isEmpty()) {
            return "?";
        }
        String[] parts = name.trim().split("\\s+");
        if (parts.length == 0) return "?";

        StringBuilder initials = new StringBuilder();
        initials.append(parts[0].substring(0, 1).toUpperCase());
        if (parts.length > 1) {
            initials.append(parts[parts.length - 1].substring(0, 1).toUpperCase());
        }
        return initials.toString();
    }
}
