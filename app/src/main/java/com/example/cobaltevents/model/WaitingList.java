package com.example.cobaltevents.model;

import com.google.firebase.Timestamp;

public class WaitingList {

    public static final String STATUS_PENDING = "pending";
    public static final String STATUS_ACCEPTED = "accepted";
    public static final String STATUS_REJECTED = "rejected";
    public static final String STATUS_SELECTED = "selected";
    public static final String STATUS_NOT_SELECTED = "not_selected";
    public static final String STATUS_CANCELLED = "cancelled";
    public static final String STATUS_WITHDRAWN = "withdrawn";
    public static final String STATUS_ENROLLED = "enrolled";
    public static final String STATUS_DECLINED = "declined";

    public static final String NOTIFY_EMAIL = "email";
    public static final String NOTIFY_PHONE = "phone";
    public static final String NOTIFY_PUSH = "push";

    private String eventId;
    private String deviceId;
    private String status;
    private Timestamp registeredAt;
    private int numParticipants;
    private String name;
    private String email;
    private String phone;
    private String notificationMethod;
    private boolean notificationsAllowed = true;

    public WaitingList() {}

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
        this.numParticipants = numParticipants;
        this.name = name;
        this.email = email;
        this.phone = (phone != null && !phone.isEmpty()) ? phone : null;
        this.notificationMethod = (this.phone != null) ? NOTIFY_PHONE : (notificationMethod != null ? notificationMethod : NOTIFY_EMAIL);
        this.notificationsAllowed = true;
    }

    public String getEventId() { return eventId; }
    public void setEventId(String eventId) { this.eventId = eventId; }

    public String getDeviceId() { return deviceId; }
    public void setDeviceId(String deviceId) { this.deviceId = deviceId; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Timestamp getRegisteredAt() { return registeredAt; }
    public void setRegisteredAt(Timestamp registeredAt) { this.registeredAt = registeredAt; }

    public int getNumParticipants() { return numParticipants; }
    public void setNumParticipants(int numParticipants) { this.numParticipants = numParticipants; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getNotificationMethod() { return notificationMethod; }
    public void setNotificationMethod(String notificationMethod) { this.notificationMethod = notificationMethod; }

    public boolean isNotificationsAllowed() { return notificationsAllowed; }
    public void setNotificationsAllowed(boolean notificationsAllowed) { this.notificationsAllowed = notificationsAllowed; }
}
