package com.example.cobaltevents.model;

/**
 * Result of {@link com.example.cobaltevents.db.WaitingListDB#rescindSelectionInviteIfStillSelected}.
 * <p>
 * Used when an organizer withdraws a lottery or replacement invite from the Invited tab: the entrant
 * returns to {@link com.example.cobaltevents.model.WaitingList#STATUS_PENDING} unless they already
 * enrolled or the invite is no longer {@code selected}.
 */
public enum RescindSelectionInviteOutcome {
    /** Waitlist was {@code selected} and is now {@code pending}. */
    APPLIED,
    /** No longer invited (e.g. pending, not_selected, declined). */
    NOT_INVITED_ANYMORE,
    /** Entrant already accepted — waitlist is {@code enrolled}; do not rescind. */
    ALREADY_ENROLLED
}
