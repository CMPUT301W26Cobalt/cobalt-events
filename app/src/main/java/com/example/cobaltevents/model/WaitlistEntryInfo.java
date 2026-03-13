package com.example.cobaltevents.model;

/**
 * Snapshot of a waitlist entry used when merging with notifications
 * (status + whether notifications are enabled for that event).
 */
public class WaitlistEntryInfo {
    private final String status;
    private final boolean notificationsAllowed;

    public WaitlistEntryInfo(String status, boolean notificationsAllowed) {
        this.status = status != null ? status : WaitingList.STATUS_PENDING;
        this.notificationsAllowed = notificationsAllowed;
    }

    public String getStatus() { return status; }
    public boolean isNotificationsAllowed() { return notificationsAllowed; }
}
