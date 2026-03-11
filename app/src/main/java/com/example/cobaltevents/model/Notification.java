package com.example.cobaltevents.model;

import java.util.Date;

/**
 * Data model for the Firestore "notifications" collection.
 * Used for US 01.04.01 — Notification When Selected.
 *
 * Each document represents a notification sent to an entrant
 * (e.g., when they are selected from the waiting list).
 */
public class Notification {

    public static final String TYPE_SELECTED = "selected";

    private String id;
    private String recipientId;
    private String eventId;
    private String title;
    private String message;
    private String type;
    private boolean read;
    private Date timestamp;

    /** No-arg constructor required by Firestore deserialization. */
    public Notification() {}

    /**
     * Creates a notification with all fields.
     *
     * @param recipientId  device ID of the entrant receiving this notification
     * @param eventId      the event this notification is about
     * @param title        notification title (shown in system notification)
     * @param message      notification body text
     * @param type         notification type (e.g., TYPE_SELECTED)
     */
    public Notification(String recipientId, String eventId, String title, String message, String type) {
        this.recipientId = recipientId;
        this.eventId = eventId;
        this.title = title;
        this.message = message;
        this.type = type;
        this.read = false;
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

    public boolean isRead() { return read; }
    public void setRead(boolean read) { this.read = read; }

    public Date getTimestamp() { return timestamp; }
    public void setTimestamp(Date timestamp) { this.timestamp = timestamp; }
}
