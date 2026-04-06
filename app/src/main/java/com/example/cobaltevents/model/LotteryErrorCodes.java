package com.example.cobaltevents.model;

/**
 * Messages carried by exceptions from lottery flows; mapped to user strings in
 * {@link com.example.cobaltevents.ui.EventManageActivity}.
 */
public final class LotteryErrorCodes {

    private LotteryErrorCodes() {}

    public static final String NO_PENDING_ENTRANTS = "LOTTERY_NO_PENDING_ENTRANTS";
    public static final String REQUEST_EXCEEDS_PENDING = "LOTTERY_REQUEST_EXCEEDS_PENDING";
    public static final String REQUEST_EXCEEDS_REPLACEMENT_CAPACITY = "LOTTERY_REQUEST_EXCEEDS_REPLACEMENT_CAPACITY";
    public static final String NO_CAPACITY = "LOTTERY_NO_CAPACITY";
    /** Waitlist has more documents than Firestore allows reading in one transaction. */
    public static final String TOO_MANY_WAITS = "LOTTERY_TOO_MANY_WAITS";

    /** Rescind invite: waitlist entry document missing during transactional read. */
    public static final String WAITLIST_ENTRY_NOT_FOUND = "WAITLIST_ENTRY_NOT_FOUND";

    /** Accept invitation: waitlist is not {@code selected}. */
    public static final String INVITATION_NOT_ACTIVE = "Invitation is no longer active";

    /** Accept invitation: waitlist already {@code enrolled}. */
    public static final String INVITATION_ALREADY_ENROLLED = "Already enrolled";
}
