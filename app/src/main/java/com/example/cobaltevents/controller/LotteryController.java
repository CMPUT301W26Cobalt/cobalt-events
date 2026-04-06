package com.example.cobaltevents.controller;

import com.example.cobaltevents.db.WaitingListDB;
import com.example.cobaltevents.model.DeclineSelectionInviteOutcome;
import com.example.cobaltevents.model.LotteryErrorCodes;
import com.example.cobaltevents.model.WaitingList;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;

public class LotteryController {

    public static final String ERR_INVITATION_NOT_ACTIVE = LotteryErrorCodes.INVITATION_NOT_ACTIVE;

    public static final String ERR_ALREADY_ENROLLED = LotteryErrorCodes.INVITATION_ALREADY_ENROLLED;

    private final WaitingListDB waitingListDB;
    private final NotificationController notificationController;

    public LotteryController() {
        this.waitingListDB = new WaitingListDB();
        this.notificationController = new NotificationController();
    }

    /**
     * Runs a lottery draw: randomly selects up to {@code requestedCount} entrants from
     * {@link WaitingList#STATUS_PENDING} and {@link WaitingList#STATUS_NOT_SELECTED}, bounded by remaining event capacity, sets their status to
     * {@link WaitingList#STATUS_SELECTED}, and sends {@link Notification#TYPE_SELECTED} in-app notifications.
     * Remaining draw-eligible entrants are set to {@link WaitingList#STATUS_NOT_SELECTED} and receive
     * {@link Notification#TYPE_NOT_SELECTED}.
     * <p>
     * All waitlist updates run in a single Firestore transaction so concurrent draws cannot both
     * succeed on the same pending pool; one commits first, the other fails if too few entrants remain.
     */
    public void runLotteryDraw(String eventId,
                               int requestedCount,
                               OnSuccessListener<Integer> onSuccess,
                               OnFailureListener onFailure) {
        if (requestedCount <= 0) {
            onFailure.onFailure(new IllegalArgumentException("requestedCount must be positive"));
            return;
        }
        waitingListDB.runLotteryDrawTransactional(eventId, requestedCount,
                outcome -> {
                    String eventName = outcome.eventName;
                    for (String deviceId : outcome.winnerDeviceIds) {
                        notificationController.sendSelectedNotification(deviceId, eventId, eventName);
                    }
                    for (String deviceId : outcome.loserDeviceIds) {
                        notificationController.sendNotSelectedNotification(deviceId, eventId, eventName);
                    }
                    onSuccess.onSuccess(outcome.winnerCount);
                },
                onFailure);
    }

    /**
     * Accepts an invitation to an event.
     */
    public void acceptInvitation(String deviceId, String eventId,
                                 OnSuccessListener<Void> onSuccess,
                                 OnFailureListener onFailure) {
        waitingListDB.getRegistrationForEventAnyStatus(eventId, deviceId, reg -> {
            if (reg == null) {
                onFailure.onFailure(new Exception("No active registration found"));
                return;
            }
            if (WaitingList.STATUS_ENROLLED.equals(reg.getStatus())) {
                onSuccess.onSuccess(null);
                return;
            }
            String entryDocId = reg.getId();
            if (entryDocId == null || entryDocId.isEmpty()) {
                entryDocId = reg.getDeviceId();
            }
            if (entryDocId == null || entryDocId.isEmpty()) {
                onFailure.onFailure(new Exception("No active registration found"));
                return;
            }
            waitingListDB.acceptSelectedInvitationTransactional(eventId, entryDocId, deviceId,
                    onSuccess, onFailure);
        }, onFailure);
    }

    /**
     * Declines an invitation ({@code selected} → {@code declined}) in one transaction, or reports
     * {@link DeclineSelectionInviteOutcome#ALREADY_DECLINED} / {@link DeclineSelectionInviteOutcome#ALREADY_ENROLLED} / etc.
     */
    public void declineInvitation(String deviceId, String eventId,
                                  OnSuccessListener<DeclineSelectionInviteOutcome> onSuccess,
                                  OnFailureListener onFailure) {
        waitingListDB.getRegistrationForEventAnyStatus(eventId, deviceId, reg -> {
            if (reg == null) {
                onFailure.onFailure(new Exception("No active registration found"));
                return;
            }
            if (WaitingList.STATUS_DECLINED.equals(reg.getStatus())
                    || WaitingList.STATUS_DECLINED_FOUND_REPLACEMENT.equals(reg.getStatus())) {
                onSuccess.onSuccess(DeclineSelectionInviteOutcome.ALREADY_DECLINED);
                return;
            }
            String entryDocId = reg.getId();
            if (entryDocId == null || entryDocId.isEmpty()) {
                entryDocId = reg.getDeviceId();
            }
            if (entryDocId == null || entryDocId.isEmpty()) {
                onFailure.onFailure(new Exception("No active registration found"));
                return;
            }
            waitingListDB.declineSelectedInvitationTransactional(eventId, entryDocId, onSuccess, onFailure);
        }, onFailure);
    }

    /**
     * Batch replacement lottery (same transactional + capacity rules as {@link #runLotteryDraw}):
     * random declined entrants → {@link WaitingList#STATUS_DECLINED_FOUND_REPLACEMENT}; replacement picks from
     * {@link WaitingList#STATUS_PENDING}, {@link WaitingList#STATUS_NOT_SELECTED}, and {@link WaitingList#STATUS_SELECTED}
     * respect remaining invite capacity when the event has a finite waitlist cap.
     */
    public void runReplacementLottery(String eventId,
                                      int requestedCount,
                                      OnSuccessListener<Integer> onSuccess,
                                      OnFailureListener onFailure) {
        waitingListDB.runReplacementLotteryTransactional(eventId, requestedCount,
                outcome -> {
                    String eventName = outcome.eventName;
                    for (String replacementId : outcome.selectedReplacementDeviceIds) {
                        notificationController.sendGotOffWaitlistNotification(replacementId, eventId, eventName);
                    }
                    onSuccess.onSuccess(outcome.replacementCount);
                },
                onFailure);
    }
}
