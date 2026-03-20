package com.example.cobaltevents;

import com.example.cobaltevents.model.Event;
import com.google.firebase.Timestamp;

import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Unit tests for the Event model class.
 * Tests field storage, defaults, setters, and attendee list management.
 */
public class EventTest {

    private Event event;
    private Timestamp now;
    private Timestamp future;
    private Timestamp past;

    @Before
    public void setUp() {
        now = Timestamp.now();
        future = new Timestamp(now.getSeconds() + 86400, 0);  // +1 day
        past   = new Timestamp(now.getSeconds() - 86400, 0);  // -1 day
        event = new Event("Tech Conference", "Annual tech event",
                "Edmonton Convention Centre", future, now, future, "organizer-device-001");
    }

    // ── Constructor ──────────────────────────────────────────────────────────

    @Test
    public void testConstructor_setsName() {
        assertEquals("Tech Conference", event.getName());
    }

    @Test
    public void testConstructor_setsDescription() {
        assertEquals("Annual tech event", event.getDescription());
    }

    @Test
    public void testConstructor_setsLocation() {
        assertEquals("Edmonton Convention Centre", event.getLocation());
    }

    @Test
    public void testConstructor_setsOrganizerDeviceId() {
        assertEquals("organizer-device-001", event.getOrganizerDeviceId());
    }

    @Test
    public void testConstructor_setsEventDate() {
        assertEquals(future, event.getEventDate());
    }

    @Test
    public void testConstructor_setsRegistrationOpen() {
        assertEquals(now, event.getRegistrationOpen());
    }

    @Test
    public void testConstructor_setsRegistrationClose() {
        assertEquals(future, event.getRegistrationClose());
    }

    // ── Defaults ─────────────────────────────────────────────────────────────

    @Test
    public void testDefault_geolocationRequiredIsFalse() {
        assertFalse(event.isGeolocationRequired());
    }

    @Test
    public void testDefault_waitingListCapacityIsZero() {
        assertEquals(0, event.getWaitingListCapacity());
    }

    @Test
    public void testDefault_confirmedAttendeesIsEmptyList() {
        assertNotNull(event.getConfirmedAttendeeIds());
        assertTrue(event.getConfirmedAttendeeIds().isEmpty());
    }

    @Test
    public void testDefault_eventIdIsNull() {
        assertNull(event.getEventId());
    }

    @Test
    public void testDefault_posterImageUrlIsNull() {
        assertNull(event.getPosterImageUrl());
    }

    @Test
    public void testDefault_qrCodeDataIsNull() {
        assertNull(event.getQrCodeData());
    }

    @Test
    public void testDefault_isPrivateIsFalse() {
        assertFalse(event.isPrivate());
    }

    @Test
    public void testSetIsPrivate_updatesValue() {
        event.setIsPrivate(true);
        assertTrue(event.isPrivate());
        event.setIsPrivate(false);
        assertFalse(event.isPrivate());
    }

    // ── No-arg constructor ───────────────────────────────────────────────────

    @Test
    public void testEmptyConstructor_initializesDefaults() {
        Event empty = new Event();
        assertNotNull(empty.getConfirmedAttendeeIds());
        assertFalse(empty.isGeolocationRequired());
        assertEquals(0, empty.getWaitingListCapacity());
        assertFalse(empty.isPrivate());
    }

    // ── Setters ──────────────────────────────────────────────────────────────

    @Test
    public void testSetEventId_storesId() {
        event.setEventId("abc-123");
        assertEquals("abc-123", event.getEventId());
    }

    @Test
    public void testSetName_updatesName() {
        event.setName("Science Fair");
        assertEquals("Science Fair", event.getName());
    }

    @Test
    public void testSetDescription_updatesDescription() {
        event.setDescription("New description");
        assertEquals("New description", event.getDescription());
    }

    @Test
    public void testSetLocation_updatesLocation() {
        event.setLocation("University of Alberta");
        assertEquals("University of Alberta", event.getLocation());
    }

    @Test
    public void testSetGeolocationRequired_setsTrue() {
        event.setGeolocationRequired(true);
        assertTrue(event.isGeolocationRequired());
    }

    @Test
    public void testSetGeolocationRequired_setsFalse() {
        event.setGeolocationRequired(true);
        event.setGeolocationRequired(false);
        assertFalse(event.isGeolocationRequired());
    }

    @Test
    public void testSetWaitingListCapacity_updatesCapacity() {
        event.setWaitingListCapacity(50);
        assertEquals(50, event.getWaitingListCapacity());
    }

    @Test
    public void testSetWaitingListCapacity_zeroMeansUnlimited() {
        event.setWaitingListCapacity(100);
        event.setWaitingListCapacity(0);
        assertEquals(0, event.getWaitingListCapacity());
    }

    @Test
    public void testSetPosterImageUrl_storesUrl() {
        event.setPosterImageUrl("https://example.com/poster.jpg");
        assertEquals("https://example.com/poster.jpg", event.getPosterImageUrl());
    }

    @Test
    public void testSetQrCodeData_storesData() {
        event.setQrCodeData("qr-payload-xyz");
        assertEquals("qr-payload-xyz", event.getQrCodeData());
    }

    @Test
    public void testSetWaitingListId_storesId() {
        event.setWaitingListId("wl-999");
        assertEquals("wl-999", event.getWaitingListId());
    }

    @Test
    public void testSetOrganizerDeviceId_updatesId() {
        event.setOrganizerDeviceId("new-organizer-id");
        assertEquals("new-organizer-id", event.getOrganizerDeviceId());
    }

    @Test
    public void testSetEventDate_updatesDate() {
        event.setEventDate(past);
        assertEquals(past, event.getEventDate());
    }

    @Test
    public void testSetRegistrationOpen_updatesDate() {
        event.setRegistrationOpen(past);
        assertEquals(past, event.getRegistrationOpen());
    }

    @Test
    public void testSetRegistrationClose_updatesDate() {
        event.setRegistrationClose(past);
        assertEquals(past, event.getRegistrationClose());
    }

    // ── Confirmed attendees list ─────────────────────────────────────────────

    @Test
    public void testSetConfirmedAttendeeIds_replacesEntireList() {
        List<String> attendees = Arrays.asList("device-a", "device-b", "device-c");
        event.setConfirmedAttendeeIds(attendees);
        assertEquals(3, event.getConfirmedAttendeeIds().size());
        assertTrue(event.getConfirmedAttendeeIds().contains("device-a"));
        assertTrue(event.getConfirmedAttendeeIds().contains("device-b"));
        assertTrue(event.getConfirmedAttendeeIds().contains("device-c"));
    }

    @Test
    public void testSetConfirmedAttendeeIds_emptyList_clears() {
        event.setConfirmedAttendeeIds(Arrays.asList("device-a"));
        event.setConfirmedAttendeeIds(Arrays.asList());
        assertTrue(event.getConfirmedAttendeeIds().isEmpty());
    }
}
