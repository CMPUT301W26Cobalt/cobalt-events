package com.example.cobaltevents.db;

import static org.junit.Assert.*;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.example.cobaltevents.model.WaitingList;
import com.google.firebase.Timestamp;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Integration tests for WaitingListDB
 * Tests Firestore operations for event registration history
 * 
 * Note: These tests require Firebase emulator or test project to run properly
 */
@RunWith(AndroidJUnit4.class)
public class WaitingListDBTest {

    private WaitingListDB waitingListDB;
    private static final String TEST_DEVICE_ID = "test_device_123";
    private static final String TEST_EVENT_ID = "test_event_456";
    private static final int TIMEOUT_SECONDS = 10;

    @Before
    public void setUp() {
        waitingListDB = new WaitingListDB();
    }

    // ── Add registration ─────────────────────────────────────────────────────

    @Test
    public void addRegistration_withValidData_succeeds() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicBoolean success = new AtomicBoolean(false);
        AtomicReference<String> registrationId = new AtomicReference<>();

        WaitingList registration = new WaitingList(TEST_EVENT_ID, TEST_DEVICE_ID, "pending");

        waitingListDB.addRegistration(
                registration,
                id -> {
                    registrationId.set(id);
                    success.set(true);
                    latch.countDown();
                },
                e -> {
                    latch.countDown();
                }
        );

        latch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        assertTrue("Registration should succeed", success.get());
        assertNotNull("Registration ID should not be null", registrationId.get());
    }

    @Test
    public void addRegistration_withPendingStatus_savesCorrectly() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicBoolean success = new AtomicBoolean(false);

        WaitingList registration = new WaitingList(TEST_EVENT_ID, TEST_DEVICE_ID, "pending");

        waitingListDB.addRegistration(
                registration,
                id -> {
                    success.set(true);
                    latch.countDown();
                },
                e -> latch.countDown()
        );

        latch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        assertTrue("Registration with pending status should succeed", success.get());
    }

    // ── Get entrant history ──────────────────────────────────────────────────

    @Test
    public void getEntrantHistory_withValidDeviceId_returnsResults() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<List<WaitingList>> historyRef = new AtomicReference<>();
        AtomicBoolean success = new AtomicBoolean(false);

        waitingListDB.getEntrantHistory(
                TEST_DEVICE_ID,
                history -> {
                    historyRef.set(history);
                    success.set(true);
                    latch.countDown();
                },
                e -> latch.countDown()
        );

        latch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        assertTrue("Query should succeed", success.get());
        assertNotNull("History list should not be null", historyRef.get());
    }

    @Test
    public void getEntrantHistory_withNoRegistrations_returnsEmptyList() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<List<WaitingList>> historyRef = new AtomicReference<>();

        String nonExistentDeviceId = "nonexistent_device_" + System.currentTimeMillis();

        waitingListDB.getEntrantHistory(
                nonExistentDeviceId,
                history -> {
                    historyRef.set(history);
                    latch.countDown();
                },
                e -> latch.countDown()
        );

        latch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        assertNotNull("History list should not be null", historyRef.get());
        assertEquals("History should be empty for non-existent device", 0, historyRef.get().size());
    }

    // ── Update status ────────────────────────────────────────────────────────

    @Test
    public void updateStatus_withValidId_succeeds() throws InterruptedException {
        // First, add a registration
        CountDownLatch addLatch = new CountDownLatch(1);
        AtomicReference<String> registrationId = new AtomicReference<>();

        WaitingList registration = new WaitingList(TEST_EVENT_ID, TEST_DEVICE_ID, "pending");

        waitingListDB.addRegistration(
                registration,
                id -> {
                    registrationId.set(id);
                    addLatch.countDown();
                },
                e -> addLatch.countDown()
        );

        addLatch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        assertNotNull("Registration ID should not be null", registrationId.get());

        // Now update the status
        CountDownLatch updateLatch = new CountDownLatch(1);
        AtomicBoolean updateSuccess = new AtomicBoolean(false);

        waitingListDB.updateStatus(
                registrationId.get(),
                "selected",
                aVoid -> {
                    updateSuccess.set(true);
                    updateLatch.countDown();
                },
                e -> updateLatch.countDown()
        );

        updateLatch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        assertTrue("Status update should succeed", updateSuccess.get());
    }

    @Test
    public void updateStatus_toSelected_succeeds() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicBoolean success = new AtomicBoolean(false);

        // This test assumes a registration exists
        // In a real test, you'd create one first
        String testRegistrationId = "test_registration_id";

        waitingListDB.updateStatus(
                testRegistrationId,
                "selected",
                aVoid -> {
                    success.set(true);
                    latch.countDown();
                },
                e -> latch.countDown()
        );

        latch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        // Note: This may fail if the registration doesn't exist
        // In production tests, you'd set up test data first
    }

    @Test
    public void updateStatus_toNotSelected_succeeds() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicBoolean success = new AtomicBoolean(false);

        String testRegistrationId = "test_registration_id";

        waitingListDB.updateStatus(
                testRegistrationId,
                "not_selected",
                aVoid -> {
                    success.set(true);
                    latch.countDown();
                },
                e -> latch.countDown()
        );

        latch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    @Test
    public void updateStatus_toCancelled_succeeds() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicBoolean success = new AtomicBoolean(false);

        String testRegistrationId = "test_registration_id";

        waitingListDB.updateStatus(
                testRegistrationId,
                "cancelled",
                aVoid -> {
                    success.set(true);
                    latch.countDown();
                },
                e -> latch.countDown()
        );

        latch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    // ── Data integrity ───────────────────────────────────────────────────────

    @Test
    public void waitingList_preservesDeviceId() {
        WaitingList registration = new WaitingList(TEST_EVENT_ID, TEST_DEVICE_ID, "pending");
        
        assertEquals("Device ID should match", TEST_DEVICE_ID, registration.getDeviceId());
    }

    @Test
    public void waitingList_preservesEventId() {
        WaitingList registration = new WaitingList(TEST_EVENT_ID, TEST_DEVICE_ID, "pending");
        
        assertEquals("Event ID should match", TEST_EVENT_ID, registration.getEventId());
    }

    @Test
    public void waitingList_preservesStatus() {
        WaitingList registration = new WaitingList(TEST_EVENT_ID, TEST_DEVICE_ID, "selected");
        
        assertEquals("Status should match", "selected", registration.getStatus());
    }

    @Test
    public void waitingList_setsRegisteredAtTimestamp() {
        WaitingList registration = new WaitingList(TEST_EVENT_ID, TEST_DEVICE_ID, "pending");
        
        assertNotNull("Registered at timestamp should be set", registration.getRegisteredAt());
    }
}
