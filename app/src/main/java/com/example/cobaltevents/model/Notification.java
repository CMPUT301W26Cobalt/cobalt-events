package com.example.cobaltevents.model;

import java.util.Date;

public class Notification {

    public static final String TYPE_SELECTED = "selected";
    public static final String TYPE_SELECT = "select";
    public static final String TYPE_GOT_OFF_WAITLIST = "got-off-waitlist";
    public static final String TYPE_NOT_SELECTED = "not-selected";
    /** Status values aligned with waitlist entry status (used for UI only). */
    public static final String STATUS_PENDING = "pending";
    public static final String STATUS_ACCEPTED = "accepted";
    public static final String STATUS_REJECTED = "rejected";

    private String id;
    private String recipientId;
    private String eventId;
    private String title;
    private String message;
    private String type;
    private Date timestamp;

    public Notification() {}

    public Notification(String recipientId, String eventId, String title, String message, String type) {
        this.recipientId = recipientId;
        this.eventId = eventId;
        this.title = title;
        this.message = message;
        this.type = type;
        this.timestamp = new Date();
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getRecipientId() { return recipientId; }
    public void setRecipientId(String recipientId) { this.recipientId = recipientId; }

    public String getEventId() { return eventId; }
    public void setEventId(String eventId) { this.eventId = eventId; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public Date getTimestamp() { return timestamp; }
    public void setTimestamp(Date timestamp) { this.timestamp = timestamp; }
}
