package com.example.cobaltevents.model;

import com.google.firebase.Timestamp;

public class WaitingList {

    public static final String STATUS_PENDING = "pending";
    public static final String STATUS_WAITING = "pending";
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

    public WaitingList() {}

    public WaitingList(String eventId, String deviceId, String status) {
        this.eventId = eventId;
        this.deviceId = deviceId;
        this.status = status;
        this.registeredAt = Timestamp.now();
        this.numParticipants = 1;
    }

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

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getEventId() { return eventId; }
    public void setEventId(String eventId) { this.eventId = eventId; }

    public String getDeviceId() { return deviceId; }
    public void setDeviceId(String deviceId) { this.deviceId = deviceId; }

    public String getEntrantId() { return deviceId; }
    public void setEntrantId(String entrantId) { this.deviceId = entrantId; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Timestamp getRegisteredAt() { return registeredAt; }
    public void setRegisteredAt(Timestamp registeredAt) { this.registeredAt = registeredAt; }

    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }

    public int getNumParticipants() { return numParticipants; }
    public void setNumParticipants(int numParticipants) { this.numParticipants = numParticipants; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getNotificationMethod() { return notificationMethod; }
    public void setNotificationMethod(String notificationMethod) { this.notificationMethod = notificationMethod; }
}
