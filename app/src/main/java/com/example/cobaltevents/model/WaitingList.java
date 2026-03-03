package com.example.cobaltevents.model;

/**
 * Data model for a waiting list entry stored in the Firestore "waitingList" collection.
 * Links an entrant to an event. The status field determines which notification type to send
 * (US 01.04.01 for "selected", US 01.04.02 for "not_selected").
 */
public class WaitingList {

    public static final String STATUS_WAITING = "waiting";
    public static final String STATUS_SELECTED = "selected";
    public static final String STATUS_NOT_SELECTED = "not_selected";

    private String id;
    private String eventId;
    private String entrantId;
    private String status;

    /** No-arg constructor required by Firestore deserialization. */
    public WaitingList() {}

    public WaitingList(String eventId, String entrantId, String status) {
        this.eventId = eventId;
        this.entrantId = entrantId;
        this.status = status;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getEventId() { return eventId; }
    public void setEventId(String eventId) { this.eventId = eventId; }

    public String getEntrantId() { return entrantId; }
    public void setEntrantId(String entrantId) { this.entrantId = entrantId; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
