package com.example.cobaltevents.notifications;

import static org.junit.Assert.*;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.example.cobaltevents.db.NotificationDB;
import com.example.cobaltevents.model.Notification;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Instrumented tests for NotificationDB:
 * - saveNotification
 * - getNotificationsForRecipient
 *
 * Note: Requires Firebase emulator or test project to run online.
 */
@RunWith(AndroidJUnit4.class)
public class NotificationDBInstrumentedTest {

    private NotificationDB notificationDB;
    private static final int TIMEOUT_SECONDS = 10;
    private String recipientId;

    @Before
    public void setUp() {
        notificationDB = new NotificationDB();
        recipientId = "test_recipient_" + UUID.randomUUID();
    }

    @Test
    public void save_and_fetch_notifications_succeeds() throws InterruptedException {
        CountDownLatch saveLatch = new CountDownLatch(1);
        AtomicReference<String> savedId = new AtomicReference<>();

        Notification n = new Notification(
                recipientId,
                "test_event",
                "Test Title",
                "Test Message",
                Notification.TYPE_SELECTED
        );

        notificationDB.saveNotification(n,
                id -> {
                    savedId.set(id);
                    saveLatch.countDown();
                },
                e -> saveLatch.countDown());

        assertTrue(saveLatch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS));
        assertNotNull(savedId.get());

        CountDownLatch listLatch = new CountDownLatch(1);
        AtomicReference<List<Notification>> listRef = new AtomicReference<>();

        notificationDB.getNotificationsForRecipient(recipientId,
                list -> {
                    listRef.set(list);
                    listLatch.countDown();
                },
                e -> listLatch.countDown());

        assertTrue(listLatch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS));
        assertNotNull(listRef.get());
        assertTrue(listRef.get().stream().anyMatch(nn -> savedId.get().equals(nn.getId())));
    }

}

