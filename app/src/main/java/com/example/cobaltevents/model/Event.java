package com.example.cobaltevents.model;

import com.google.firebase.Timestamp;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents an event in the Cobalt Events application.
 */
public class Event {

    private String eventId;
    private String name;
    private String description;
    private String location;
    private Timestamp eventDate;
    private Timestamp registrationOpen;
    private Timestamp registrationClose;
    private String organizerDeviceId;
    private String waitingListId;
    private String posterImageUrl;
    private boolean geolocationRequired;
    private int waitingListCapacity;   // 0 = unlimited
    private String qrCodeData;
    private List<String> confirmedAttendeeIds;
    private String category;

    public Event() {
        this.confirmedAttendeeIds = new ArrayList<>();
        this.geolocationRequired = false;
        this.waitingListCapacity = 0;
    }

    public Event(String name, String description, String location,
                 Timestamp eventDate, Timestamp registrationOpen,
                 Timestamp registrationClose, String organizerDeviceId) {
        this.name = name;
        this.description = description;
        this.location = location;
        this.eventDate = eventDate;
        this.registrationOpen = registrationOpen;
        this.registrationClose = registrationClose;
        this.organizerDeviceId = organizerDeviceId;
        this.confirmedAttendeeIds = new ArrayList<>();
        this.geolocationRequired = false;
        this.waitingListCapacity = 0;
    }

    public String getEventId() { return eventId; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public String getLocation() { return location; }
    public Timestamp getEventDate() { return eventDate; }
    public Timestamp getRegistrationOpen() { return registrationOpen; }
    public Timestamp getRegistrationClose() { return registrationClose; }
    public String getOrganizerDeviceId() { return organizerDeviceId; }
    public String getWaitingListId() { return waitingListId; }
    public String getPosterImageUrl() { return posterImageUrl; }
    public boolean isGeolocationRequired() { return geolocationRequired; }
    public int getWaitingListCapacity() { return waitingListCapacity; }
    public String getQrCodeData() { return qrCodeData; }
    public List<String> getConfirmedAttendeeIds() { return confirmedAttendeeIds; }
    public String getCategory() { return category; }

    public void setEventId(String eventId) { this.eventId = eventId; }
    public void setName(String name) { this.name = name; }
    public void setDescription(String description) { this.description = description; }
    public void setLocation(String location) { this.location = location; }
    public void setEventDate(Timestamp eventDate) { this.eventDate = eventDate; }
    public void setRegistrationOpen(Timestamp registrationOpen) { this.registrationOpen = registrationOpen; }
    public void setRegistrationClose(Timestamp registrationClose) { this.registrationClose = registrationClose; }
    public void setOrganizerDeviceId(String organizerDeviceId) { this.organizerDeviceId = organizerDeviceId; }
    public void setWaitingListId(String waitingListId) { this.waitingListId = waitingListId; }
    public void setPosterImageUrl(String posterImageUrl) { this.posterImageUrl = posterImageUrl; }
    public void setGeolocationRequired(boolean geolocationRequired) { this.geolocationRequired = geolocationRequired; }
    public void setWaitingListCapacity(int waitingListCapacity) { this.waitingListCapacity = waitingListCapacity; }
    public void setQrCodeData(String qrCodeData) { this.qrCodeData = qrCodeData; }
    public void setConfirmedAttendeeIds(List<String> confirmedAttendeeIds) { this.confirmedAttendeeIds = confirmedAttendeeIds; }
    public void setCategory(String category) { this.category = category; }
}
