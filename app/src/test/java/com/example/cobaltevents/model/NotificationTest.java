package com.example.cobaltevents.model;

import static org.junit.Assert.*;

import org.junit.Test;

public class NotificationTest {

    @Test
    public void isPending_true_whenReadNullOrEmptyOrPending() {
        Notification n1 = new Notification("r","e","t","m", Notification.TYPE_SELECTED);
        n1.setRead(null);
        assertTrue(n1.isPending());

        Notification n2 = new Notification("r","e","t","m", Notification.TYPE_SELECTED);
        n2.setRead("");
        assertTrue(n2.isPending());

        Notification n3 = new Notification("r","e","t","m", Notification.TYPE_SELECTED);
        n3.setRead(Notification.READ_PENDING);
        assertTrue(n3.isPending());
    }

    @Test
    public void isPending_false_whenAcceptedOrRejected() {
        Notification n1 = new Notification("r","e","t","m", Notification.TYPE_SELECTED);
        n1.setRead(Notification.READ_ACCEPTED);
        assertFalse(n1.isPending());

        Notification n2 = new Notification("r","e","t","m", Notification.TYPE_SELECTED);
        n2.setRead(Notification.READ_REJECTED);
        assertFalse(n2.isPending());
    }
}

