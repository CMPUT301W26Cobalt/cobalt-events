package com.example.cobaltevents.model;

import java.util.Date;

/**
 * Model class for notifications sent to entrants.
 * Notifications represent messages about event lottery selection, waiting list status, etc.
 */
public class Notification {

    public static final String TYPE_SELECTED = "selected";
    public static final String TYPE_SELECT = "select";
    public static final String TYPE_GOT_OFF_WAITLIST = "got-off-waitlist";
    public static final String TYPE_NOT_SELECTED = "not-selected";
    public static final String READ_PENDING = "pending";
    public static final String READ_ACCEPTED = "accepted";
    public static final String READ_REJECTED = "rejected";

    private String id;
    private String recipientId;
    private String eventId;
    private String title;
    private String message;
    private String type;
    private String read;
    private Date timestamp;

    /**
     * Default constructor for Notification. Required for Firestore.
     */
    public Notification() {}

    /**
     * Constructs a Notification with its core details.
     * @param recipientId Unique identifier for the notification recipient.
     * @param eventId Unique identifier for the associated event.
     * @param title Title of the notification.
     * @param message Message body of the notification.
     * @param type Type of notification (e.g., "selected").
     */
    public Notification(String recipientId, String eventId, String title, String message, String type) {
        this.recipientId = recipientId;
        this.eventId = eventId;
        this.title = title;
        this.message = message;
        this.type = type;
        this.read = READ_PENDING;
        this.timestamp = new Date();
    }

    /** @return The unique identifier of the notification. */
    public String getId() { return id; }
    /** @param id Unique identifier to set. */
    public void setId(String id) { this.id = id; }

    /** @return The recipient's ID. */
    public String getRecipientId() { return recipientId; }
    /** @param recipientId Recipient ID to set. */
    public void setRecipientId(String recipientId) { this.recipientId = recipientId; }

    /** @return The associated event's ID. */
    public String getEventId() { return eventId; }
    /** @param eventId Event ID to set. */
    public void setEventId(String eventId) { this.eventId = eventId; }

    /** @return The title of the notification. */
    public String getTitle() { return title; }
    /** @param title Notification title to set. */
    public void setTitle(String title) { this.title = title; }

    /** @return The notification message. */
    public String getMessage() { return message; }
    /** @param message Message content to set. */
    public void setMessage(String message) { this.message = message; }

    /** @return The notification type. */
    public String getType() { return type; }
    /** @param type Notification type to set. */
    public void setType(String type) { this.type = type; }

    /** @return The read status (e.g., "pending", "accepted"). */
    public String getRead() { return read; }
    /** @param read Read status to set. */
    public void setRead(String read) { this.read = read; }
    
    /** @return True if the notification is still pending action. */
    public boolean isPending() { return read == null || read.isEmpty() || READ_PENDING.equals(read); }
    /** @param pending Placeholder setter for Firestore. */
    public void setPending(boolean pending) { }

    /** @return The timestamp when the notification was created. */
    public Date getTimestamp() { return timestamp; }
    /** @param timestamp Notification timestamp to set. */
    public void setTimestamp(Date timestamp) { this.timestamp = timestamp; }
}
