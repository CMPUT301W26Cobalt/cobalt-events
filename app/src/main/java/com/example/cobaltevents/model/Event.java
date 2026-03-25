package com.example.cobaltevents.model;

import com.google.firebase.Timestamp;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Represents an event in the Cobalt Events application.
 */
public class Event {

    private String eventId;
    private String name;
    private String description;
    private String location;
    /** Optional: from Google Places when organizer picks a venue; used for geolocation join distance. */
    private Double locationLatitude;
    private Double locationLongitude;
    private String price;
    private Timestamp eventDate;
    private Timestamp registrationOpen;
    private Timestamp registrationClose;
    /**
     * Device IDs of all organizers (creator + co-organizers). Stored in Firestore as {@code organizers}.
     */
    private List<String> organizers;
    private String waitingListId;
    private String posterImageUrl;
    private boolean geolocationRequired;
    private int waitingListCapacity;   // 0 = unlimited
    private String qrCodeData;
    private List<String> confirmedAttendeeIds;
    private Object category;
    private String ageGroup;
    private String criteria;
    /** If true, event is not shown in public browse (organizer-only); UI shows a Private tag. */
    private boolean isPrivate;

    public Event() {
        this.confirmedAttendeeIds = new ArrayList<>();
        this.geolocationRequired = false;
        this.waitingListCapacity = 0;
        this.isPrivate = false;
    }

    public Event(String name, String description, String location,
                 Timestamp eventDate, Timestamp registrationOpen,
                 Timestamp registrationClose) {
        this.name = name;
        this.description = description;
        this.location = location;
        this.eventDate = eventDate;
        this.registrationOpen = registrationOpen;
        this.registrationClose = registrationClose;
        this.confirmedAttendeeIds = new ArrayList<>();
        this.geolocationRequired = false;
        this.waitingListCapacity = 0;
        this.isPrivate = false;
    }

    public String getEventId() { return eventId; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public String getLocation() { return location; }
    /** @return Latitude from Places when set; otherwise null (client may geocode {@link #getLocation()}). */
    public Double getLocationLatitude() { return locationLatitude; }
    public void setLocationLatitude(Double locationLatitude) { this.locationLatitude = locationLatitude; }
    /** @return Longitude from Places when set; otherwise null. */
    public Double getLocationLongitude() { return locationLongitude; }
    public void setLocationLongitude(Double locationLongitude) { this.locationLongitude = locationLongitude; }
    public String getPrice() { return price; }
    public Timestamp getEventDate() { return eventDate; }
    public Timestamp getRegistrationOpen() { return registrationOpen; }
    public Timestamp getRegistrationClose() { return registrationClose; }
    public List<String> getOrganizers() {
        return organizers != null ? organizers : Collections.emptyList();
    }
    public String getWaitingListId() { return waitingListId; }
    public String getPosterImageUrl() { return posterImageUrl; }
    public boolean isGeolocationRequired() { return geolocationRequired; }
    public int getWaitingListCapacity() { return waitingListCapacity; }
    public String getQrCodeData() { return qrCodeData; }
    public List<String> getConfirmedAttendeeIds() { return confirmedAttendeeIds; }
    public List<String> getCategory() {
        if (category == null) return Collections.emptyList();
        if (category instanceof List<?>) {
            List<String> result = new ArrayList<>();
            for (Object item : (List<?>) category) {
                if (item instanceof String) {
                    String value = ((String) item).trim();
                    if (!value.isEmpty()) result.add(value);
                }
            }
            return result;
        }
        if (category instanceof String) {
            String value = ((String) category).trim();
            if (value.isEmpty()) return Collections.emptyList();
            List<String> single = new ArrayList<>();
            single.add(value);
            return single;
        }
        return Collections.emptyList();
    }
    public String getPrimaryCategory() {
        List<String> categories = getCategory();
        return categories.isEmpty() ? null : categories.get(0);
    }
    public String getCategoryDisplayText() {
        List<String> categories = getCategory();
        return categories.isEmpty() ? "" : String.join(", ", categories);
    }
    public String getAgeGroup() { return ageGroup; }
    public String getCriteria() { return criteria; }
    public boolean getIsPrivate() { return isPrivate; }
    public boolean isPrivate() { return isPrivate; }

    /** Whether {@code deviceId} is listed in {@code organizers}. */
    public boolean isDeviceAnOrganizer(String deviceId) {
        if (deviceId == null || deviceId.isEmpty()) {
            return false;
        }
        if (organizers != null) {
            for (String id : organizers) {
                if (id != null && deviceId.equals(id)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Deduplicated organizer device IDs from {@code organizers} (stable order).
     * Used for account deletion and co-organizer checks.
     */
    public List<String> getMergedOrganizerDeviceIds() {
        LinkedHashSet<String> set = new LinkedHashSet<>();
        if (organizers != null) {
            for (String id : organizers) {
                if (id != null && !id.trim().isEmpty()) {
                    set.add(id.trim());
                }
            }
        }
        return new ArrayList<>(set);
    }

    /** True if this device is the only organizer on the merged organizer list. */
    public boolean isSoleOrganizer(String deviceId) {
        if (deviceId == null || deviceId.isEmpty()) {
            return false;
        }
        List<String> merged = getMergedOrganizerDeviceIds();
        return merged.size() == 1 && deviceId.equals(merged.get(0));
    }

    public void setEventId(String eventId) { this.eventId = eventId; }
    public void setName(String name) { this.name = name; }
    public void setDescription(String description) { this.description = description; }
    public void setLocation(String location) { this.location = location; }
    public void setPrice(String price) { this.price = price; }
    public void setEventDate(Timestamp eventDate) { this.eventDate = eventDate; }
    public void setRegistrationOpen(Timestamp registrationOpen) { this.registrationOpen = registrationOpen; }
    public void setRegistrationClose(Timestamp registrationClose) { this.registrationClose = registrationClose; }
    public void setOrganizers(List<String> organizers) { this.organizers = organizers; }
    public void setWaitingListId(String waitingListId) { this.waitingListId = waitingListId; }
    public void setPosterImageUrl(String posterImageUrl) { this.posterImageUrl = posterImageUrl; }
    public void setGeolocationRequired(boolean geolocationRequired) { this.geolocationRequired = geolocationRequired; }
    public void setWaitingListCapacity(int waitingListCapacity) { this.waitingListCapacity = waitingListCapacity; }
    public void setQrCodeData(String qrCodeData) { this.qrCodeData = qrCodeData; }
    public void setConfirmedAttendeeIds(List<String> confirmedAttendeeIds) { this.confirmedAttendeeIds = confirmedAttendeeIds; }
    public void setCategory(Object category) {
        if (category == null) {
            this.category = null;
            return;
        }
        if (category instanceof List<?>) {
            List<String> normalized = new ArrayList<>();
            for (Object item : (List<?>) category) {
                if (item instanceof String) {
                    String trimmed = ((String) item).trim();
                    if (!trimmed.isEmpty()) normalized.add(trimmed);
                }
            }
            this.category = normalized;
            return;
        }
        if (category instanceof String) {
            String trimmed = ((String) category).trim();
            if (trimmed.isEmpty()) {
                this.category = null;
            } else {
                List<String> single = new ArrayList<>();
                single.add(trimmed);
                this.category = single;
            }
            return;
        }
        this.category = null;
    }
    public void setAgeGroup(String ageGroup) { this.ageGroup = ageGroup; }
    public void setCriteria(String criteria) { this.criteria = criteria; }
    public void setIsPrivate(boolean isPrivate) { this.isPrivate = isPrivate; }
    public void setPrivate(boolean isPrivate) { this.isPrivate = isPrivate; }
}
