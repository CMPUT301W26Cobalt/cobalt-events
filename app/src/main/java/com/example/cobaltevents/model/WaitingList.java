package com.example.cobaltevents.model;

import com.google.firebase.Timestamp;

/**
 * Represents an entrant's registration for an event.
 */
public class WaitingList {

    public static final String STATUS_WAITING = "pending";
    public static final String STATUS_SELECTED = "selected";
    public static final String STATUS_NOT_SELECTED = "not_selected";

    private String id;
    private String eventId;
    private String deviceId;
    private String status; // "pending", "selected", "not_selected", "cancelled", "withdrawn"
    private Timestamp registeredAt;

    public WaitingList() {}

    public WaitingList(String eventId, String deviceId, String status) {
        this.eventId = eventId;
        this.deviceId = deviceId;
        this.status = status;
        this.registeredAt = Timestamp.now();
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getEventId() { return eventId; }
    public void setEventId(String eventId) { this.eventId = eventId; }

    public String getDeviceId() { return deviceId; }
    public void setDeviceId(String deviceId) { this.deviceId = deviceId; }

    /** Alias for getDeviceId() for compatibility with notification code. */
    public String getEntrantId() { return deviceId; }
    public void setEntrantId(String entrantId) { this.deviceId = entrantId; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Timestamp getRegisteredAt() { return registeredAt; }
    public void setRegisteredAt(Timestamp registeredAt) { this.registeredAt = registeredAt; }
}
