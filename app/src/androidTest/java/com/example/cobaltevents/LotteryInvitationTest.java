package com.example.cobaltevents;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;

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

@RunWith(AndroidJUnit4.class)
@LargeTest
public class LotteryInvitationTest {

    private String deviceId;
    private final EventDB eventDB = new EventDB();
    private final WaitingListDB waitingListDB = new WaitingListDB();
    private final NotificationDB notificationDB = new NotificationDB();
    private final List<String> notificationIdsToDelete = new ArrayList<>();

    @Rule
    public ActivityScenarioRule<NotificationsActivity> activityRule =
            new ActivityScenarioRule<>(NotificationsActivity.class);

    @Before
    public void setUp() throws InterruptedException {
        Context context = ApplicationProvider.getApplicationContext();
        deviceId = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ANDROID_ID);
        notificationIdsToDelete.clear();
        
        // Clean slate for the test device
        clearOldNotifications();
    }

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
            new Thread(() -> {
                try {
                    delLatch.await(10, TimeUnit.SECONDS);
                } catch (InterruptedException ignored) {}
                latch.countDown();
            }).start();
        }, e -> latch.countDown());
        latch.await(15, TimeUnit.SECONDS);
        Thread.sleep(1000);
    }

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
        Thread.sleep(2000);
        activityRule.getScenario().onActivity(NotificationsActivity::recreate);
        Thread.sleep(3000);
    }

    @After
    public void tearDown() throws InterruptedException {
        if (notificationIdsToDelete.isEmpty()) return;
        CountDownLatch latch = new CountDownLatch(notificationIdsToDelete.size());
        for (String id : notificationIdsToDelete) {
            notificationDB.deleteNotification(id, v -> latch.countDown(), e -> latch.countDown());
        }
        latch.await(10, TimeUnit.SECONDS);
    }

    @Test
    public void testAcceptInvitation() throws InterruptedException {
        String testTitle = "Accept Test " + UUID.randomUUID().toString().substring(0, 8);
        prepareTestData(testTitle);

        onView(withText(testTitle)).check(matches(isDisplayed()));
        
        // Scope the button click to the specific CardView containing our unique title
        onView(allOf(
                withId(R.id.btn_accept),
                isDescendantOfA(allOf(
                        hasDescendant(withText(testTitle)),
                        withClassName(containsString("CardView"))
                )),
                isDisplayed()
        )).perform(click());

        Thread.sleep(3000);
        
        // Verify accepted badge inside the same specific CardView
        onView(allOf(
                withText("Accepted"),
                isDescendantOfA(allOf(
                        hasDescendant(withText(testTitle)),
                        withClassName(containsString("CardView"))
                )),
                isDisplayed()
        )).check(matches(isDisplayed()));
    }

    @Test
    public void testDeclineInvitation() throws InterruptedException {
        String testTitle = "Decline Test " + UUID.randomUUID().toString().substring(0, 8);
        prepareTestData(testTitle);

        onView(withText(testTitle)).check(matches(isDisplayed()));

        // Scope the button click to the specific CardView containing our unique title
        onView(allOf(
                withId(R.id.btn_decline),
                isDescendantOfA(allOf(
                        hasDescendant(withText(testTitle)),
                        withClassName(containsString("CardView"))
                )),
                isDisplayed()
        )).perform(click());

        Thread.sleep(3000);

        // Verify declined badge inside the same specific CardView
        onView(allOf(
                withText("Declined"),
                isDescendantOfA(allOf(
                        hasDescendant(withText(testTitle)),
                        withClassName(containsString("CardView"))
                )),
                isDisplayed()
        )).check(matches(isDisplayed()));
    }
}
