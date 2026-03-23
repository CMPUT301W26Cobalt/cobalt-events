package com.example.cobaltevents.ui.waitlist;

import com.example.cobaltevents.model.Notification;
import com.example.cobaltevents.model.WaitingList;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Shared waitlist + notification status rules for {@link com.example.cobaltevents.ui.EventListActivity}
 * / {@link com.example.cobaltevents.ui.adapter.EventAdapter} and {@link com.example.cobaltevents.ui.QRScanActivity}.
 */
public final class WaitlistStatusUi {

    private WaitlistStatusUi() {}

    /**
     * Derives a single override status from one notification (same rules as event list bootstrap).
     */
    public static String getOverrideStatusFromNotification(Notification n) {
        if (n == null || n.getType() == null) {
            return null;
        }
        String type = n.getType();
        String response = n.getResponse();
        if (Notification.TYPE_CO_ORGANIZER.equals(type)) {
            return null;
        }
        if (Notification.TYPE_NOT_SELECTED.equals(type)) {
            return WaitingList.STATUS_NOT_SELECTED;
        }
        if (Notification.TYPE_PRIVATE_EVENT.equals(type)) {
            return null;
        }
        if (Notification.TYPE_SELECTED.equals(type) || Notification.TYPE_GOT_OFF_WAITLIST.equals(type)) {
            if (Notification.RESPONSE_ACCEPTED.equals(response)) {
                return WaitingList.STATUS_ENROLLED;
            }
            if (Notification.RESPONSE_DECLINED.equals(response)) {
                return WaitingList.STATUS_DECLINED;
            }
            return null;
        }
        return null;
    }

    /**
     * Builds the per-event effective map from the full notification list (order = query order).
     */
    public static Map<String, String> effectiveStatusByEventIdFromNotifications(List<Notification> notifications) {
        Map<String, String> effective = new HashMap<>();
        if (notifications == null) {
            return effective;
        }
        Set<String> processedEventIds = new HashSet<>();
        for (Notification n : notifications) {
            if (n == null || n.getEventId() == null || n.getType() == null) {
                continue;
            }
            String eventId = n.getEventId();
            if (processedEventIds.contains(eventId)) {
                continue;
            }
            String effectiveStatus = getOverrideStatusFromNotification(n);
            if (effectiveStatus == null) {
                continue;
            }
            processedEventIds.add(eventId);
            effective.put(eventId, effectiveStatus);
        }
        return effective;
    }

    /**
     * First override for {@code eventId} when walking {@code notifications} in list order (matches
     * {@link #effectiveStatusByEventIdFromNotifications} for a single event).
     */
    public static String firstEffectiveOverrideForEvent(List<Notification> notifications, String eventId) {
        if (notifications == null || eventId == null) {
            return null;
        }
        for (Notification n : notifications) {
            if (n == null || !eventId.equals(n.getEventId())) {
                continue;
            }
            String s = getOverrideStatusFromNotification(n);
            if (s != null) {
                return s;
            }
        }
        return null;
    }

    /**
     * Merges DB registration status with notification-derived override — same rules as
     * {@link com.example.cobaltevents.ui.adapter.EventAdapter}.
     * If there is no DB registration ({@code dbStatus == null}), returns {@code null} (ignore overrides).
     */
    public static String mergeDbStatusWithEffectiveOverride(String dbStatus, String effectiveOverride) {
        if (dbStatus == null) {
            return null;
        }
        if (effectiveOverride == null) {
            return dbStatus;
        }
        if (WaitingList.STATUS_NOT_SELECTED.equals(effectiveOverride)
                && (WaitingList.STATUS_DECLINED.equals(dbStatus)
                || WaitingList.STATUS_DECLINED_FOUND_REPLACEMENT.equals(dbStatus)
                || "declined".equalsIgnoreCase(dbStatus))) {
            return dbStatus;
        }
        if (WaitingList.STATUS_NOT_SELECTED.equals(effectiveOverride)
                && (WaitingList.STATUS_ENROLLED.equals(dbStatus) || WaitingList.STATUS_SELECTED.equals(dbStatus))) {
            return dbStatus;
        }
        return effectiveOverride;
    }
}
