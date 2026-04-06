package com.example.cobaltevents.model;

import com.google.firebase.firestore.ServerTimestamp;

import java.util.Date;

/**
 * Model class for notifications sent to entrants.
 * Notifications represent messages about event lottery selection, waiting list status, etc.
 */
public class Notification {

    /**
     * Supported notification {@code type} strings:
     * {@link #TYPE_SELECTED} (trophy), {@link #TYPE_GOT_OFF_WAITLIST} (star),
     * {@link #TYPE_NOT_SELECTED} (X), {@link #TYPE_PRIVATE_EVENT} (lock),
     * {@link #TYPE_CO_ORGANIZER} (medal icon; informational, no Accept/Decline in the list),
     * {@link #TYPE_EVENT_ALERT} (amber exclamation; informational — organizer “notify waitlisted”
     * and admin event deletion broadcasts).
     */
    public static final String TYPE_SELECTED = "selected";
    public static final String TYPE_GOT_OFF_WAITLIST = "got-off-waitlist";
    public static final String TYPE_NOT_SELECTED = "not-selected";
    public static final String TYPE_PRIVATE_EVENT = "private-event";
    public static final String TYPE_CO_ORGANIZER = "co-organizer";
    public static final String TYPE_EVENT_ALERT = "event-alert";
    public static final String RECIPIENT_MODE_USER = "user";
    public static final String RECIPIENT_MODE_ORGANIZER = "organizer";
    public static final String RESPONSE_PENDING = "pending";
    public static final String RESPONSE_ACCEPTED = "accepted";
    public static final String RESPONSE_DECLINED = "declined";

    private String id;
    private String recipientId;
    private String eventId;
    private String title;
    private String message;
    private String type;
    private String recipient_mode;
    private String response;
    @ServerTimestamp
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
        this.recipient_mode = RECIPIENT_MODE_USER;
        if (TYPE_NOT_SELECTED.equals(type)) {
            this.response = null;
        } else {
            this.response = RESPONSE_PENDING;
        }
        // timestamp left null so @ServerTimestamp fills it with accurate server time on write
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
    public void setType(String type) {
        this.type = type;
        if (TYPE_NOT_SELECTED.equals(type)) {
            this.response = null;
        } else if (this.response == null || this.response.trim().isEmpty()) {
            this.response = RESPONSE_PENDING;
        }
    }

    /** @return Notification recipient mode ({@code user} or {@code organizer}). */
    public String getRecipientMode() {
        if (RECIPIENT_MODE_ORGANIZER.equals(recipient_mode)) {
            return RECIPIENT_MODE_ORGANIZER;
        }
        return RECIPIENT_MODE_USER;
    }
    /** @param recipientMode Recipient mode to set. */
    public void setRecipientMode(String recipientMode) {
        if (RECIPIENT_MODE_ORGANIZER.equals(recipientMode)) {
            this.recipient_mode = RECIPIENT_MODE_ORGANIZER;
            return;
        }
        this.recipient_mode = RECIPIENT_MODE_USER;
    }

    /** @return Notification response state (pending/accepted/declined). */
    public String getResponse() { return response; }
    /** @param response Response state to set. */
    public void setResponse(String response) { this.response = response; }

    /** @return The timestamp when the notification was created. */
    public Date getTimestamp() { return timestamp; }
    /** @param timestamp Notification timestamp to set. */
    public void setTimestamp(Date timestamp) { this.timestamp = timestamp; }
}
