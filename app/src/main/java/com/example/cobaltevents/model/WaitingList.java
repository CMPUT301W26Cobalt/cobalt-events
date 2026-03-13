package com.example.cobaltevents.model;

import com.google.firebase.Timestamp;

/**
 * Model class for registrations on an event's waiting list.
 * This class tracks the status of an entrant's entry into the event lottery.
 */
public class WaitingList {

    public static final String STATUS_PENDING = "pending";
    public static final String STATUS_SELECTED = "selected";
    public static final String STATUS_NOT_SELECTED = "not_selected";
    public static final String STATUS_CANCELLED = "cancelled";
    public static final String STATUS_WITHDRAWN = "withdrawn";
    public static final String STATUS_ENROLLED = "enrolled";
    public static final String STATUS_DECLINED = "declined";

    public static final String NOTIFY_EMAIL = "email";
    public static final String NOTIFY_PHONE = "phone";
    public static final String NOTIFY_PUSH = "push";

    private String id;
    private String eventId;
    private String deviceId;
    private String status;
    private Timestamp registeredAt;
    private String note;
    private int numParticipants;
    private String email;
    private String phone;
    private String notificationMethod;

    /**
     * Default constructor for WaitingList. Required for Firestore.
     */
    public WaitingList() {}

    /**
     * Constructs a WaitingList entry with basic status.
     * @param eventId ID of the event.
     * @param deviceId ID of the entrant's device.
     * @param status Initial status of the entry.
     */
    public WaitingList(String eventId, String deviceId, String status) {
        this.eventId = eventId;
        this.deviceId = deviceId;
        this.status = status;
        this.registeredAt = Timestamp.now();
        this.numParticipants = 1;
    }

    /**
     * Constructs a WaitingList entry with full registration details.
     * @param eventId ID of the event.
     * @param deviceId ID of the entrant's device.
     * @param note Registration note.
     * @param numParticipants Number of participants registered.
     * @param email Email for contact.
     * @param phone Phone for contact.
     * @param notificationMethod Preferred notification method.
     */
    public WaitingList(String eventId, String deviceId, String note, int numParticipants,
                       String email, String phone, String notificationMethod) {
        this.eventId = eventId;
        this.deviceId = deviceId;
        this.status = STATUS_PENDING;
        this.registeredAt = Timestamp.now();
        this.note = note;
        this.numParticipants = numParticipants;
        this.email = email;
        this.phone = phone;
        this.notificationMethod = notificationMethod;
    }
    
    /** @return The unique identifier of this registration. */
    public String getId() { return id; }
    /** @param id Unique identifier to set. */
    public void setId(String id) { this.id = id; }
    
    /** @return The ID of the associated event. */
    public String getEventId() { return eventId; }
    /** @param eventId Event ID to set. */
    public void setEventId(String eventId) { this.eventId = eventId; }
    
    /** @return The ID of the registrant's device. */
    public String getDeviceId() { return deviceId; }
    /** @param deviceId Device ID to set. */
    public void setDeviceId(String deviceId) { this.deviceId = deviceId; }
    
    /** @return The current status (e.g., "pending", "selected"). */
    public String getStatus() { return status; }
    /** @param status Status to set. */
    public void setStatus(String status) { this.status = status; }
    
    /** @return The timestamp when the entry was registered. */
    public Timestamp getRegisteredAt() { return registeredAt; }
    /** @param registeredAt Registration timestamp to set. */
    public void setRegisteredAt(Timestamp registeredAt) { this.registeredAt = registeredAt; }

    /** @return The registration note. */
    public String getNote() { return note; }
    /** @param note Note content to set. */
    public void setNote(String note) { this.note = note; }

    /** @return The number of participants in this entry. */
    public int getNumParticipants() { return numParticipants; }
    /** @param numParticipants Number of participants to set. */
    public void setNumParticipants(int numParticipants) { this.numParticipants = numParticipants; }

    /** @return The contact email for this registration. */
    public String getEmail() { return email; }
    /** @param email Email to set. */
    public void setEmail(String email) { this.email = email; }

    /** @return The contact phone number for this registration. */
    public String getPhone() { return phone; }
    /** @param phone Phone number to set. */
    public void setPhone(String phone) { this.phone = phone; }

    /** @return The preferred notification method. */
    public String getNotificationMethod() { return notificationMethod; }
    /** @param notificationMethod Notification method to set. */
    public void setNotificationMethod(String notificationMethod) { this.notificationMethod = notificationMethod; }
}
