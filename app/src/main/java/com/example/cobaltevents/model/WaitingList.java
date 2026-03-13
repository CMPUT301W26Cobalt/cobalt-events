package com.example.cobaltevents.model;

import com.google.firebase.Timestamp;

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
    private String name;
    private String email;
    private String phone;
    private String notificationMethod;
    private boolean notificationsAllowed = true;

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
        this.notificationsAllowed = true;
    }

    public WaitingList(String eventId, String deviceId, int numParticipants,
                       String name, String email, String phone, String notificationMethod) {
        this.eventId = eventId;
        this.deviceId = deviceId;
        this.status = STATUS_PENDING;
        this.registeredAt = Timestamp.now();
        this.note = note;
        this.numParticipants = numParticipants;
        this.name = name;
        this.email = email;
        this.phone = (phone != null && !phone.isEmpty()) ? phone : null;
        this.notificationMethod = (this.phone != null) ? NOTIFY_PHONE : (notificationMethod != null ? notificationMethod : NOTIFY_EMAIL);
        this.notificationsAllowed = true;
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

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

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

    public boolean isNotificationsAllowed() { return notificationsAllowed; }
    public void setNotificationsAllowed(boolean notificationsAllowed) { this.notificationsAllowed = notificationsAllowed; }
}
