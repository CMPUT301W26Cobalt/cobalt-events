package com.example.cobaltevents.model;

/**
 * Result of {@link com.example.cobaltevents.db.WaitingListDB#rescindSelectionInviteIfStillSelected}.
 */
public enum RescindSelectionInviteOutcome {
    /** Waitlist was {@code selected} and is now {@code pending}. */
    APPLIED,
    /** No longer invited (e.g. pending, not_selected, declined). */
    NOT_INVITED_ANYMORE,
    /** Entrant already accepted — waitlist is {@code enrolled}; do not rescind. */
    ALREADY_ENROLLED
}
