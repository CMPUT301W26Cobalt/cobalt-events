package com.example.cobaltevents.model;

import static org.junit.Assert.*;

import com.google.firebase.Timestamp;

import org.junit.Before;
import org.junit.Test;

/**
 * Unit tests for the EventHistory model class.
 */
public class EventHistoryTest {

    private Event event;
    private WaitingList registration;

    @Before
    public void setUp() {
        Timestamp now = Timestamp.now();
        Timestamp future = new Timestamp(now.getSeconds() + 86400, 0);
        event = new Event("Art Fair", "Local art showcase", "City Hall", future, now, future);

        registration = new WaitingList("event-art", "device-a", WaitingList.STATUS_ENROLLED);
    }

    // ── Constructor / getters ────────────────────────────────────────────────

    @Test
    public void constructor_storesEvent() {
        EventHistory eh = new EventHistory(event, registration);
        assertSame(event, eh.getEvent());
    }

    @Test
    public void constructor_storesRegistration() {
        EventHistory eh = new EventHistory(event, registration);
        assertSame(registration, eh.getRegistration());
    }

    // ── getStatus ────────────────────────────────────────────────────────────

    @Test
    public void getStatus_returnsRegistrationStatus() {
        EventHistory eh = new EventHistory(event, registration);
        assertEquals(WaitingList.STATUS_ENROLLED, eh.getStatus());
    }

    @Test
    public void getStatus_pendingRegistration_returnsPending() {
        WaitingList pending = new WaitingList("e", "d", WaitingList.STATUS_PENDING);
        EventHistory eh = new EventHistory(event, pending);
        assertEquals(WaitingList.STATUS_PENDING, eh.getStatus());
    }

    @Test
    public void getStatus_selectedRegistration_returnsSelected() {
        WaitingList selected = new WaitingList("e", "d", WaitingList.STATUS_SELECTED);
        EventHistory eh = new EventHistory(event, selected);
        assertEquals(WaitingList.STATUS_SELECTED, eh.getStatus());
    }

    @Test
    public void getStatus_declinedRegistration_returnsDeclined() {
        WaitingList declined = new WaitingList("e", "d", WaitingList.STATUS_DECLINED);
        EventHistory eh = new EventHistory(event, declined);
        assertEquals(WaitingList.STATUS_DECLINED, eh.getStatus());
    }

    @Test
    public void getStatus_nullRegistration_returnsUnknown() {
        EventHistory eh = new EventHistory(event, null);
        assertEquals("unknown", eh.getStatus());
    }

    @Test
    public void nullEvent_doesNotThrow() {
        EventHistory eh = new EventHistory(null, registration);
        assertNull(eh.getEvent());
        assertEquals(WaitingList.STATUS_ENROLLED, eh.getStatus());
    }
}
