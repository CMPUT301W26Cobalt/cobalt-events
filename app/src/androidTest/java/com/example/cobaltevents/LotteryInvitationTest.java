package com.example.cobaltevents;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static org.hamcrest.Matchers.allOf;

import android.content.Context;
import android.provider.Settings;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;

import com.example.cobaltevents.db.EventDB;
import com.example.cobaltevents.db.NotificationDB;
import com.example.cobaltevents.db.WaitingListDB;
import com.example.cobaltevents.model.Event;
import com.example.cobaltevents.model.Notification;
import com.example.cobaltevents.model.WaitingList;
import com.example.cobaltevents.ui.NotificationsActivity;
import com.google.firebase.Timestamp;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * UI tests for the Lottery Invitation system.
 * This class tests the functionality of accepting and declining event invitations
 * within the Notifications tab of the Cobalt Events application.
 *
 * US 01.04.01: As an entrant, I want to receive a notification when I am chosen from the waiting list.
 * US 01.05.01: As an entrant, I want to accept my invitation to register.
 * US 01.05.02: As an entrant, I want to decline an invitation.
 */
@RunWith(AndroidJUnit4.class)
@LargeTest
public class LotteryInvitationTest {

    private String deviceId;
    private final EventDB eventDB = new EventDB();
    private final WaitingListDB waitingListDB = new WaitingListDB();
    private final NotificationDB notificationDB = new NotificationDB();
    private final List<String> notificationIdsToDelete = new ArrayList<>();

    /**
     * Rule to launch the NotificationsActivity for each test.
     */
    @Rule
    public ActivityScenarioRule<NotificationsActivity> activityRule =
            new ActivityScenarioRule<>(NotificationsActivity.class);

    /**
     * Sets up the test environment by initializing the device ID and clearing old notifications.
     * @throws InterruptedException if the thread is interrupted during setup.
     */
    @Before
    public void setUp() throws InterruptedException {
        Context context = ApplicationProvider.getApplicationContext();
        deviceId = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ANDROID_ID);
        notificationIdsToDelete.clear();
        
        // Clean start: clear any pre-existing notifications for this device to avoid AmbiguousViewMatcherException
        clearOldNotifications();
    }

    /**
     * Helper method to clear all notifications for the current device from Firestore.
     * @throws InterruptedException if the thread is interrupted while waiting for deletion.
     */
    private void clearOldNotifications() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        notificationDB.getNotificationsForRecipient(deviceId, list -> {
            if (list == null || list.isEmpty()) {
                latch.countDown();
                return;
            }
            CountDownLatch delLatch = new CountDownLatch(list.size());
            for (Notification n : list) {
                notificationDB.deleteNotification(n.getId(), v -> delLatch.countDown(), e -> delLatch.countDown());
            }
            // Use a separate thread to wait for deletion so we don't block the UI thread if called from there
            new Thread(() -> {
                try {
                    delLatch.await(10, TimeUnit.SECONDS);
                } catch (InterruptedException ignored) {}
                latch.countDown();
            }).start();
        }, e -> latch.countDown());
        latch.await(15, TimeUnit.SECONDS);
        Thread.sleep(1000); // Buffer for Firestore consistency
    }

    /**
     * Prepares test data in Firestore, including an event, a waiting list entry, and a notification.
     * @param title The unique title for the test notification.
     * @throws InterruptedException if the thread is interrupted while waiting for Firestore operations.
     */
    private void prepareTestData(String title) throws InterruptedException {
        String eventId = UUID.randomUUID().toString();
        Event event = new Event(title, "Description", "Location",
                new Timestamp(new Date()), null, null, "organizer-id");
        event.setEventId(eventId);
        
        CountDownLatch latch = new CountDownLatch(3);
        
        eventDB.updateEvent(event, v -> latch.countDown(), e -> latch.countDown());
        WaitingList wl = new WaitingList(eventId, deviceId, WaitingList.STATUS_SELECTED);
        waitingListDB.addRegistration(wl, id -> latch.countDown(), e -> latch.countDown());

        Notification notification = new Notification(deviceId, eventId, 
                title, "You have been selected.", Notification.TYPE_SELECTED);
        notificationDB.saveNotification(notification, id -> {
            notificationIdsToDelete.add(id);
            latch.countDown();
        }, e -> latch.countDown());

        latch.await(15, TimeUnit.SECONDS);
        
        // Ensure Firestore has indexed the new data
        Thread.sleep(2000);
        
        // Recreate activity to fetch fresh notifications
        activityRule.getScenario().onActivity(NotificationsActivity::recreate);
        
        // Give the UI time to render the RecyclerView items
        Thread.sleep(3000);
    }

    /**
     * Cleans up test data by deleting notifications created during the test.
     * @throws InterruptedException if the thread is interrupted during cleanup.
     */
    @After
    public void tearDown() throws InterruptedException {
        if (notificationIdsToDelete.isEmpty()) return;
        CountDownLatch latch = new CountDownLatch(notificationIdsToDelete.size());
        for (String id : notificationIdsToDelete) {
            notificationDB.deleteNotification(id, v -> latch.countDown(), e -> latch.countDown());
        }
        latch.await(10, TimeUnit.SECONDS);
    }

    /**
     * Tests the "Accept" invitation flow.
     * Verifies that clicking the Accept button updates the UI to show the "Accepted" badge.
     * @throws InterruptedException if the thread is interrupted during the test.
     */
    @Test
    public void testAcceptInvitation() throws InterruptedException {
        String testTitle = "Accept Test " + UUID.randomUUID().toString().substring(0, 8);
        prepareTestData(testTitle);

        // Verify the unique notification is displayed
        onView(withText(testTitle)).check(matches(isDisplayed()));
        
        // Target the Accept button specifically inside the card containing our testTitle
        // We use withId(R.id.notification_card_root) which is the root CardView of the item layout
        onView(allOf(
                withId(R.id.btn_accept),
                isDescendantOfA(allOf(
                        withId(R.id.notification_card_root),
                        hasDescendant(withText(testTitle))
                )),
                isDisplayed()
        )).perform(click());

        // Wait for the status update to be processed and badge to show
        Thread.sleep(3000);
        
        // Verify the "Accepted" badge appears in the correct card
        onView(allOf(
                withText("Accepted"),
                isDescendantOfA(allOf(
                        withId(R.id.notification_card_root),
                        hasDescendant(withText(testTitle))
                )),
                isDisplayed()
        )).check(matches(isDisplayed()));
    }

    /**
     * Tests the "Decline" invitation flow.
     * Verifies that clicking the Decline button updates the UI to show the "Declined" badge.
     * @throws InterruptedException if the thread is interrupted during the test.
     */
    @Test
    public void testDeclineInvitation() throws InterruptedException {
        String testTitle = "Decline Test " + UUID.randomUUID().toString().substring(0, 8);
        prepareTestData(testTitle);

        onView(withText(testTitle)).check(matches(isDisplayed()));

        // Target the Decline button specifically inside the card containing our testTitle
        onView(allOf(
                withId(R.id.btn_decline),
                isDescendantOfA(allOf(
                        withId(R.id.notification_card_root),
                        hasDescendant(withText(testTitle))
                )),
                isDisplayed()
        )).perform(click());

        // Wait for the status update to be processed and badge to show
        Thread.sleep(3000);

        // Verify the "Declined" badge appears in the correct card
        onView(allOf(
                withText("Declined"),
                isDescendantOfA(allOf(
                        withId(R.id.notification_card_root),
                        hasDescendant(withText(testTitle))
                )),
                isDisplayed()
        )).check(matches(isDisplayed()));
    }
}
