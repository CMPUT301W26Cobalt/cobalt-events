package com.example.cobaltevents.model;

/**
 * Result of {@link com.example.cobaltevents.db.WaitingListDB#declineSelectedInvitationTransactional}.
 */
public enum DeclineSelectionInviteOutcome {
    /** Waitlist was {@code selected} and is now {@code declined}. */
    APPLIED,
    /** No longer on an active invite (e.g. pending, rescinded, not_selected). */
    NOT_INVITED_ANYMORE,
    /** Waitlist already {@code declined} or {@code declined_found_replacement}. */
    ALREADY_DECLINED,
    /** Already {@code enrolled} — cannot decline. */
    ALREADY_ENROLLED
}
