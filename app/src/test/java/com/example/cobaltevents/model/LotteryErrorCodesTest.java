package com.example.cobaltevents.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import org.junit.Test;

/**
 * Verifies that each {@link LotteryErrorCodes} constant holds its expected string value.
 * These strings are mapped to user-facing messages in EventManageActivity.
 */
public class LotteryErrorCodesTest {

    @Test
    public void noPendingEntrants_constantValue() {
        assertEquals("LOTTERY_NO_PENDING_ENTRANTS", LotteryErrorCodes.NO_PENDING_ENTRANTS);
    }

    @Test
    public void requestExceedsPending_constantValue() {
        assertEquals("LOTTERY_REQUEST_EXCEEDS_PENDING", LotteryErrorCodes.REQUEST_EXCEEDS_PENDING);
    }

    @Test
    public void requestExceedsReplacementCapacity_constantValue() {
        assertEquals("LOTTERY_REQUEST_EXCEEDS_REPLACEMENT_CAPACITY",
                LotteryErrorCodes.REQUEST_EXCEEDS_REPLACEMENT_CAPACITY);
    }

    @Test
    public void noCapacity_constantValue() {
        assertEquals("LOTTERY_NO_CAPACITY", LotteryErrorCodes.NO_CAPACITY);
    }

    @Test
    public void tooManyWaits_constantValue() {
        assertEquals("LOTTERY_TOO_MANY_WAITS", LotteryErrorCodes.TOO_MANY_WAITS);
    }

    @Test
    public void allConstants_areNonNull() {
        assertNotNull(LotteryErrorCodes.NO_PENDING_ENTRANTS);
        assertNotNull(LotteryErrorCodes.REQUEST_EXCEEDS_PENDING);
        assertNotNull(LotteryErrorCodes.REQUEST_EXCEEDS_REPLACEMENT_CAPACITY);
        assertNotNull(LotteryErrorCodes.NO_CAPACITY);
        assertNotNull(LotteryErrorCodes.TOO_MANY_WAITS);
    }

    @Test
    public void allConstants_areDistinct() {
        // Each error code must uniquely identify a failure mode
        String[] codes = {
                LotteryErrorCodes.NO_PENDING_ENTRANTS,
                LotteryErrorCodes.REQUEST_EXCEEDS_PENDING,
                LotteryErrorCodes.REQUEST_EXCEEDS_REPLACEMENT_CAPACITY,
                LotteryErrorCodes.NO_CAPACITY,
                LotteryErrorCodes.TOO_MANY_WAITS
        };
        for (int i = 0; i < codes.length; i++) {
            for (int j = i + 1; j < codes.length; j++) {
                assertNotNull("Code at index " + i + " is null", codes[i]);
                assertNotNull("Code at index " + j + " is null", codes[j]);
                assert !codes[i].equals(codes[j])
                        : "Duplicate code: " + codes[i];
            }
        }
    }
}
