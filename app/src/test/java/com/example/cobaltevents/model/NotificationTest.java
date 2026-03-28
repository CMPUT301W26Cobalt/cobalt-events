package com.example.cobaltevents.model;

import static org.junit.Assert.*;

import org.junit.Test;

public class NotificationTest {

    @Test
    public void constructor_setsCoreFields() {
        Notification n = new Notification("r", "e", "t", "m", Notification.TYPE_SELECTED);
        assertEquals("r", n.getRecipientId());
        assertEquals("e", n.getEventId());
        assertEquals("t", n.getTitle());
        assertEquals("m", n.getMessage());
        assertEquals(Notification.TYPE_SELECTED, n.getType());
        assertEquals(Notification.RECIPIENT_MODE_USER, n.getRecipientMode());
    }

    @Test
    public void serverTimestamp_isNotSetLocally() {
        Notification n = new Notification("r", "e", "t", "m", Notification.TYPE_SELECTED);
        assertNull(n.getTimestamp());
    }

    @Test
    public void recipientMode_acceptsOrganizerOrDefaultsUser() {
        Notification n = new Notification("r", "e", "t", "m", Notification.TYPE_SELECTED);
        n.setRecipientMode(Notification.RECIPIENT_MODE_ORGANIZER);
        assertEquals(Notification.RECIPIENT_MODE_ORGANIZER, n.getRecipientMode());

        n.setRecipientMode("unknown");
        assertEquals(Notification.RECIPIENT_MODE_USER, n.getRecipientMode());
    }

    // ── Type constants ───────────────────────────────────────────────────────

    @Test
    public void typeConstants_haveExpectedValues() {
        assertEquals("selected",        Notification.TYPE_SELECTED);
        assertEquals("got-off-waitlist",Notification.TYPE_GOT_OFF_WAITLIST);
        assertEquals("not-selected",    Notification.TYPE_NOT_SELECTED);
        assertEquals("private-event",   Notification.TYPE_PRIVATE_EVENT);
        assertEquals("co-organizer",    Notification.TYPE_CO_ORGANIZER);
        assertEquals("event-alert",     Notification.TYPE_EVENT_ALERT);
    }

    @Test
    public void responseConstants_haveExpectedValues() {
        assertEquals("pending",  Notification.RESPONSE_PENDING);
        assertEquals("accepted", Notification.RESPONSE_ACCEPTED);
        assertEquals("declined", Notification.RESPONSE_DECLINED);
    }

    // ── Response state set by constructor ────────────────────────────────────

    @Test
    public void constructor_selectedType_responseIsPending() {
        Notification n = new Notification("r", "e", "t", "m", Notification.TYPE_SELECTED);
        assertEquals(Notification.RESPONSE_PENDING, n.getResponse());
    }

    @Test
    public void constructor_gotOffWaitlistType_responseIsPending() {
        Notification n = new Notification("r", "e", "t", "m", Notification.TYPE_GOT_OFF_WAITLIST);
        assertEquals(Notification.RESPONSE_PENDING, n.getResponse());
    }

    @Test
    public void constructor_notSelectedType_responseIsNull() {
        Notification n = new Notification("r", "e", "t", "m", Notification.TYPE_NOT_SELECTED);
        assertNull(n.getResponse());
    }

    @Test
    public void constructor_privateEventType_responseIsPending() {
        Notification n = new Notification("r", "e", "t", "m", Notification.TYPE_PRIVATE_EVENT);
        assertEquals(Notification.RESPONSE_PENDING, n.getResponse());
    }

    // ── setType response side-effects ────────────────────────────────────────

    @Test
    public void setType_toNotSelected_clearsResponse() {
        Notification n = new Notification("r", "e", "t", "m", Notification.TYPE_SELECTED);
        n.setType(Notification.TYPE_NOT_SELECTED);
        assertNull(n.getResponse());
    }

    @Test
    public void setType_fromNotSelectedToSelected_restoresPendingResponse() {
        Notification n = new Notification("r", "e", "t", "m", Notification.TYPE_NOT_SELECTED);
        assertNull(n.getResponse());
        n.setType(Notification.TYPE_SELECTED);
        assertEquals(Notification.RESPONSE_PENDING, n.getResponse());
    }

    @Test
    public void setType_whenResponseAlreadySet_doesNotOverwrite() {
        Notification n = new Notification("r", "e", "t", "m", Notification.TYPE_SELECTED);
        n.setResponse(Notification.RESPONSE_ACCEPTED);
        n.setType(Notification.TYPE_CO_ORGANIZER);
        // response was non-null/non-empty so it should NOT be overwritten
        assertEquals(Notification.RESPONSE_ACCEPTED, n.getResponse());
    }

    // ── Setters ──────────────────────────────────────────────────────────────

    @Test
    public void setId_updatesValue() {
        Notification n = new Notification();
        n.setId("notif-001");
        assertEquals("notif-001", n.getId());
    }

    @Test
    public void setRecipientId_updatesValue() {
        Notification n = new Notification();
        n.setRecipientId("device-xyz");
        assertEquals("device-xyz", n.getRecipientId());
    }

    @Test
    public void setEventId_updatesValue() {
        Notification n = new Notification();
        n.setEventId("event-123");
        assertEquals("event-123", n.getEventId());
    }

    @Test
    public void setTitle_updatesValue() {
        Notification n = new Notification();
        n.setTitle("You're in!");
        assertEquals("You're in!", n.getTitle());
    }

    @Test
    public void setMessage_updatesValue() {
        Notification n = new Notification();
        n.setMessage("Congratulations, you were selected.");
        assertEquals("Congratulations, you were selected.", n.getMessage());
    }

    @Test
    public void setResponse_updatesValue() {
        Notification n = new Notification("r", "e", "t", "m", Notification.TYPE_SELECTED);
        n.setResponse(Notification.RESPONSE_ACCEPTED);
        assertEquals(Notification.RESPONSE_ACCEPTED, n.getResponse());
    }

    @Test
    public void noArgConstructor_allFieldsNull() {
        Notification n = new Notification();
        assertNull(n.getId());
        assertNull(n.getRecipientId());
        assertNull(n.getEventId());
        assertNull(n.getTitle());
        assertNull(n.getMessage());
        assertNull(n.getType());
        assertNull(n.getTimestamp());
    }
}
