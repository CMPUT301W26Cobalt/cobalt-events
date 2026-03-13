package com.example.cobaltevents.controller;

import com.example.cobaltevents.db.EventDB;
import com.example.cobaltevents.db.NotificationDB;
import com.example.cobaltevents.db.WaitingListDB;
import com.example.cobaltevents.model.Event;
import com.example.cobaltevents.model.Notification;
import com.example.cobaltevents.model.WaitingList;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class LotteryController {

    private final EventDB eventDB;
    private final WaitingListDB waitingListDB;
    private final NotificationDB notificationDB;

    public LotteryController() {
        this.eventDB = new EventDB();
        this.waitingListDB = new WaitingListDB();
        this.notificationDB = new NotificationDB();
    }

    /**
     * Accepts an invitation to an event.
     */
    public void acceptInvitation(String deviceId, String eventId,
                                 OnSuccessListener<Void> onSuccess,
                                 OnFailureListener onFailure) {
        waitingListDB.getActiveRegistrationForEvent(eventId, deviceId, reg -> {
            if (reg == null) {
                onFailure.onFailure(new Exception("No active registration found"));
                return;
            }
            if (WaitingList.STATUS_ENROLLED.equals(reg.getStatus())) {
                onFailure.onFailure(new Exception("Already enrolled"));
                return;
            }
            
            waitingListDB.updateStatus(eventId, reg.getDeviceId(), WaitingList.STATUS_ENROLLED, v -> {
                eventDB.getEvent(eventId, event -> {
                    if (event != null) {
                        List<String> confirmed = event.getConfirmedAttendeeIds();
                        if (confirmed == null) confirmed = new ArrayList<>();
                        if (!confirmed.contains(deviceId)) {
                            confirmed.add(deviceId);
                            event.setConfirmedAttendeeIds(confirmed);
                            eventDB.updateEvent(event, onSuccess, onFailure);
                        } else {
                            onSuccess.onSuccess(null);
                        }
                    } else {
                        onSuccess.onSuccess(null);
                    }
                }, onFailure);
            }, onFailure);
        }, onFailure);
    }

    /**
     * Declines an invitation to an event and triggers replacement logic.
     */
    public void declineInvitation(String deviceId, String eventId,
                                  OnSuccessListener<Void> onSuccess,
                                  OnFailureListener onFailure) {
        waitingListDB.getActiveRegistrationForEvent(eventId, deviceId, reg -> {
            if (reg == null) {
                onFailure.onFailure(new Exception("No active registration found"));
                return;
            }
            waitingListDB.updateStatus(eventId, reg.getDeviceId(), WaitingList.STATUS_DECLINED, v -> {
                // Trigger replacement logic
                drawReplacement(eventId, onSuccess, onFailure);
            }, onFailure);
        }, onFailure);
    }

    private void drawReplacement(String eventId, OnSuccessListener<Void> onSuccess, OnFailureListener onFailure) {
        waitingListDB.getEntrantsForEvent(eventId, allRegs -> {
            List<WaitingList> pending = allRegs.stream()
                    .filter(r -> WaitingStatusPending(r))
                    .collect(Collectors.toList());
            
            if (pending.isEmpty()) {
                onSuccess.onSuccess(null);
                return;
            }

            Collections.shuffle(pending);
            WaitingList replacement = pending.get(0);
            
            waitingListDB.updateStatus(eventId, replacement.getDeviceId(), WaitingList.STATUS_SELECTED, v -> {
                eventDB.getEvent(eventId, event -> {
                    // Event-specific notifications removed; simply complete successfully.
                    onSuccess.onSuccess(null);
                }, onFailure);
            }, onFailure);
        }, onFailure);
    }

    private boolean WaitingStatusPending(WaitingList r) {
        return WaitingList.STATUS_PENDING.equals(r.getStatus());
    }
}
