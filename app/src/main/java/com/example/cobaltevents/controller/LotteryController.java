package com.example.cobaltevents.controller;

import android.content.Context;
import android.util.Log;

import com.example.cobaltevents.db.EventDB;
import com.example.cobaltevents.db.WaitingListDB;
import com.example.cobaltevents.model.Event;
import com.example.cobaltevents.model.WaitingList;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Runs the lottery draw for an event. Randomly selects entrants from the waiting list,
 * updates their statuses, then triggers notifications via NotificationController.
 * US 01.04.01: Triggers "selected" notifications for lottery winners.
 * US 01.04.02: Triggers "not_selected" notifications for non-winners.
 */
public class LotteryController {

    private static final String TAG = "LotteryController";

    private final WaitingListDB waitingListDB;
    private final EventDB eventDB;
    private final NotificationController notificationController;

    public LotteryController(Context context) {
        this.waitingListDB = new WaitingListDB();
        this.eventDB = new EventDB();
        this.notificationController = new NotificationController(context);
    }

    /**
     * Draws the lottery for an event:
     * 1. Fetch all "waiting" entries
     * 2. Shuffle randomly
     * 3. First N (maxEntrants) → "selected"
     * 4. Remainder → "not_selected"
     * 5. Batch update statuses in Firestore
     * 6. Send notifications to all participants
     * 7. Mark event.lotteryDrawn = true
     */
    public void drawLottery(Event event, OnLotteryCompleteListener listener) {
        waitingListDB.getWaitingListByEvent(event.getEventId(), entries -> {
            // Filter to only "waiting" entries
            List<WaitingList> waitingEntries = new ArrayList<>();
            for (WaitingList entry : entries) {
                if (WaitingList.STATUS_WAITING.equals(entry.getStatus())) {
                    waitingEntries.add(entry);
                }
            }

            if (waitingEntries.isEmpty()) {
                listener.onError(new Exception("No entrants in the waiting list"));
                return;
            }

            // Randomly shuffle
            Collections.shuffle(waitingEntries);

            // Split into selected and not selected
            int selectCount = Math.min(waitingEntries.size(), event.getMaxEntrants());
            List<String> selectedIds = new ArrayList<>();
            List<String> notSelectedIds = new ArrayList<>();
            Map<String, String> statusUpdates = new HashMap<>();

            for (int i = 0; i < waitingEntries.size(); i++) {
                WaitingList entry = waitingEntries.get(i);
                if (i < selectCount) {
                    statusUpdates.put(entry.getId(), WaitingList.STATUS_SELECTED);
                    selectedIds.add(entry.getEntrantId());
                } else {
                    statusUpdates.put(entry.getId(), WaitingList.STATUS_NOT_SELECTED);
                    notSelectedIds.add(entry.getEntrantId());
                }
            }

            // Batch update statuses in Firestore
            waitingListDB.batchUpdateStatuses(statusUpdates,
                    unused -> {
                        // Mark event as drawn
                        eventDB.markLotteryDrawn(event.getEventId(),
                                unused2 -> Log.d(TAG, "Event marked as lottery drawn"),
                                e -> Log.e(TAG, "Failed to mark lottery drawn", e)
                        );

                        // Send notifications (checks opt-out per entrant)
                        notificationController.sendLotteryNotifications(selectedIds, notSelectedIds, event);

                        listener.onComplete(selectedIds, notSelectedIds);
                    },
                    e -> {
                        Log.e(TAG, "Failed to update waiting list statuses", e);
                        listener.onError(e);
                    }
            );
        }, e -> {
            Log.e(TAG, "Failed to fetch waiting list", e);
            listener.onError(e);
        });
    }

    public interface OnLotteryCompleteListener {
        void onComplete(List<String> selectedIds, List<String> notSelectedIds);
        void onError(Exception e);
    }
}
