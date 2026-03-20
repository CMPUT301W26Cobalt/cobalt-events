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
    }

    @Test
    public void serverTimestamp_isNotSetLocally() {
        Notification n = new Notification("r", "e", "t", "m", Notification.TYPE_SELECTED);
        assertNull(n.getTimestamp());
    }
}
