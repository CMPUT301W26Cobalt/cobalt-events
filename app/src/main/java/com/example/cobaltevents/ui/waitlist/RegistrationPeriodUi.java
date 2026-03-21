package com.example.cobaltevents.ui.waitlist;

import com.example.cobaltevents.model.Event;
import com.google.firebase.Timestamp;

/**
 * Registration window checks aligned with {@link com.example.cobaltevents.db.WaitingListDB#addRegistrationWithJoinChecks}
 * and list/QR button logic (registration open/close vs {@link Timestamp#now()}).
 */
public final class RegistrationPeriodUi {

    private RegistrationPeriodUi() {}

    /**
     * Whether the current time is within the event's registration window (inclusive).
     * {@code null} registration open or close means no bound on that side.
     */
    public static boolean isNowWithinRegistrationWindow(Event event) {
        if (event == null) {
            return false;
        }
        Timestamp now = Timestamp.now();
        if (event.getRegistrationOpen() != null && now.compareTo(event.getRegistrationOpen()) < 0) {
            return false;
        }
        if (event.getRegistrationClose() != null && event.getRegistrationClose().compareTo(now) < 0) {
            return false;
        }
        return true;
    }
}
