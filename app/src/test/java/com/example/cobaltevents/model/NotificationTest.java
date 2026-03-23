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
}
