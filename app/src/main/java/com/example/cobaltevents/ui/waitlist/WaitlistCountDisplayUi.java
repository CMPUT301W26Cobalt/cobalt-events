package com.example.cobaltevents.ui.waitlist;

import android.content.Context;

import androidx.annotation.NonNull;

import com.example.cobaltevents.R;

/**
 * Shared waitlist count line for event cards (browse list, QR popup, My Events read-only).
 */
public final class WaitlistCountDisplayUi {

    private WaitlistCountDisplayUi() {}

    /**
     * @param activeCount server active waitlist count; null → empty string
     * @param capacity    {@link com.example.cobaltevents.model.Event#getWaitingListCapacity()}; ≤0 → unlimited
     */
    @NonNull
    public static String formatLine(@NonNull Context context, Integer activeCount, int capacity) {
        if (activeCount == null) {
            return "";
        }
        if (capacity > 0) {
            return context.getString(R.string.waitlist_summary_with_capacity, activeCount, capacity);
        }
        return context.getString(R.string.waitlist_summary_unlimited, activeCount);
    }
}
