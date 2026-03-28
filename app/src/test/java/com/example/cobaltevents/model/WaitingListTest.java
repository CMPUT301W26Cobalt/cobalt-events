package com.example.cobaltevents.model;

import static org.junit.Assert.*;

import org.junit.Test;

/**
 * Unit tests for the WaitingList model class.
 * Tests constructors, status constants, notification-method selection logic,
 * and all getters/setters.
 */
public class WaitingListTest {

    // ── Status constants ─────────────────────────────────────────────────────

    @Test
    public void statusConstants_haveExpectedValues() {
        assertEquals("pending",                    WaitingList.STATUS_PENDING);
        assertEquals("selected",                   WaitingList.STATUS_SELECTED);
        assertEquals("not_selected",               WaitingList.STATUS_NOT_SELECTED);
        assertEquals("enrolled",                   WaitingList.STATUS_ENROLLED);
        assertEquals("declined",                   WaitingList.STATUS_DECLINED);
        assertEquals("declined_found_replacement", WaitingList.STATUS_DECLINED_FOUND_REPLACEMENT);
    }

    @Test
    public void notifyConstants_haveExpectedValues() {
        assertEquals("email", WaitingList.NOTIFY_EMAIL);
        assertEquals("phone", WaitingList.NOTIFY_PHONE);
        assertEquals("push",  WaitingList.NOTIFY_PUSH);
    }

    // ── No-arg constructor ───────────────────────────────────────────────────

    @Test
    public void noArgConstructor_createsInstance() {
        WaitingList wl = new WaitingList();
        assertNotNull(wl);
    }

    // ── Three-arg constructor ────────────────────────────────────────────────

    @Test
    public void threeArgConstructor_setsEventIdDeviceIdStatus() {
        WaitingList wl = new WaitingList("event-1", "device-1", WaitingList.STATUS_PENDING);
        assertEquals("event-1",               wl.getEventId());
        assertEquals("device-1",              wl.getDeviceId());
        assertEquals(WaitingList.STATUS_PENDING, wl.getStatus());
    }

    @Test
    public void threeArgConstructor_defaultsNumParticipantsToOne() {
        WaitingList wl = new WaitingList("e", "d", WaitingList.STATUS_PENDING);
        assertEquals(1, wl.getNumParticipants());
    }

    @Test
    public void threeArgConstructor_notificationsAllowedByDefault() {
        WaitingList wl = new WaitingList("e", "d", WaitingList.STATUS_PENDING);
        assertTrue(wl.isNotificationsAllowed());
    }

    @Test
    public void threeArgConstructor_registeredAtIsSet() {
        WaitingList wl = new WaitingList("e", "d", WaitingList.STATUS_PENDING);
        assertNotNull(wl.getRegisteredAt());
    }

    // ── Full constructor ─────────────────────────────────────────────────────

    @Test
    public void fullConstructor_withPhoneNumber_setsNotifyPhone() {
        WaitingList wl = new WaitingList("event-2", "device-2", 2,
                "Alice", "alice@x.com", "7801234567", WaitingList.NOTIFY_EMAIL);
        assertEquals(WaitingList.NOTIFY_PHONE, wl.getNotificationMethod());
        assertEquals("7801234567", wl.getPhone());
    }

    @Test
    public void fullConstructor_emptyPhone_usesProvidedMethod() {
        WaitingList wl = new WaitingList("event-3", "device-3", 1,
                "Bob", "bob@x.com", "", WaitingList.NOTIFY_EMAIL);
        assertEquals(WaitingList.NOTIFY_EMAIL, wl.getNotificationMethod());
        assertNull(wl.getPhone());
    }

    @Test
    public void fullConstructor_nullPhone_usesProvidedMethod() {
        WaitingList wl = new WaitingList("event-4", "device-4", 1,
                "Carol", "carol@x.com", null, WaitingList.NOTIFY_PUSH);
        assertEquals(WaitingList.NOTIFY_PUSH, wl.getNotificationMethod());
        assertNull(wl.getPhone());
    }

    @Test
    public void fullConstructor_nullMethodAndNoPhone_defaultsToEmail() {
        WaitingList wl = new WaitingList("event-5", "device-5", 1,
                "Dan", "dan@x.com", null, null);
        assertEquals(WaitingList.NOTIFY_EMAIL, wl.getNotificationMethod());
    }

    @Test
    public void fullConstructor_defaultsStatusToPending() {
        WaitingList wl = new WaitingList("event-6", "device-6", 1,
                "Eve", "eve@x.com", "", WaitingList.NOTIFY_EMAIL);
        assertEquals(WaitingList.STATUS_PENDING, wl.getStatus());
    }

    @Test
    public void fullConstructor_setsName() {
        WaitingList wl = new WaitingList("e", "d", 1,
                "Frank", "frank@x.com", "", WaitingList.NOTIFY_EMAIL);
        assertEquals("Frank", wl.getName());
    }

    // ── Setters / getters ────────────────────────────────────────────────────

    @Test
    public void setId_updatesValue() {
        WaitingList wl = new WaitingList();
        wl.setId("wl-abc");
        assertEquals("wl-abc", wl.getId());
    }

    @Test
    public void setStatus_updatesValue() {
        WaitingList wl = new WaitingList("e", "d", WaitingList.STATUS_PENDING);
        wl.setStatus(WaitingList.STATUS_SELECTED);
        assertEquals(WaitingList.STATUS_SELECTED, wl.getStatus());
    }

    @Test
    public void setNote_updatesValue() {
        WaitingList wl = new WaitingList();
        wl.setNote("Bringing +1");
        assertEquals("Bringing +1", wl.getNote());
    }

    @Test
    public void setNumParticipants_updatesValue() {
        WaitingList wl = new WaitingList();
        wl.setNumParticipants(3);
        assertEquals(3, wl.getNumParticipants());
    }

    @Test
    public void setNotificationsAllowed_toggles() {
        WaitingList wl = new WaitingList("e", "d", WaitingList.STATUS_PENDING);
        wl.setNotificationsAllowed(false);
        assertFalse(wl.isNotificationsAllowed());
        wl.setNotificationsAllowed(true);
        assertTrue(wl.isNotificationsAllowed());
    }

    @Test
    public void setLatitudeLongitude_updatesValues() {
        WaitingList wl = new WaitingList();
        wl.setLatitude(53.5461);
        wl.setLongitude(-113.4938);
        assertEquals(53.5461, wl.getLatitude(), 0.0001);
        assertEquals(-113.4938, wl.getLongitude(), 0.0001);
    }

    @Test
    public void latitudeLongitude_defaultNull() {
        WaitingList wl = new WaitingList();
        assertNull(wl.getLatitude());
        assertNull(wl.getLongitude());
    }
}
