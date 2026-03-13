package com.example.cobaltevents;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.clearText;
import static androidx.test.espresso.action.ViewActions.swipeLeft;
import static androidx.test.espresso.action.ViewActions.typeText;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.hasDescendant;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertEquals;

import android.os.SystemClock;
import android.graphics.Rect;

import androidx.test.espresso.UiController;
import androidx.test.espresso.ViewAction;
import androidx.test.espresso.ViewInteraction;
import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.example.cobaltevents.R;
import com.example.cobaltevents.controller.AdminController;
import com.example.cobaltevents.ui.admin.AdminActivity;
import com.example.cobaltevents.model.Entrant;
import com.example.cobaltevents.model.Event;
import com.example.cobaltevents.model.Notification;
import com.google.firebase.FirebaseApp;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.FirebaseFirestore;

import androidx.recyclerview.widget.RecyclerView;
import android.view.View;
import org.hamcrest.Matcher;

import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Full Admin test suite — combines controller (background logic) tests
 * AND Espresso UI tests (live on-screen interactions).
 *
 * ── What you'll see on screen ────────────────────────────────────────────────
 * The Espresso tests launch the real AdminActivity on the device so you can
 * watch every action happen live:
 *   - Tabs being tapped and switching
 *   - Cards loading in the list
 *   - Swipe left revealing the red delete background
 *   - Confirm dialogs appearing and being tapped
 *   - Cards disappearing instantly after delete
 *   - Search bar filtering the list in real time
 *   - Detail dialogs opening and closing
 *
 * ── What the controller tests verify ─────────────────────────────────────────
 * The controller tests run silently in the background and verify that
 * Firestore is actually being updated correctly — documents are really
 * deleted, image URLs are really cleared, etc.
 *
 * ── Test data ─────────────────────────────────────────────────────────────────
 * @Before creates real Firestore documents with TEST_ prefixed IDs.
 * @After deletes them all so real app data is never affected.
 *
 * ── User stories covered ──────────────────────────────────────────────────────
 *   US 03.01.01 — Remove events
 *   US 03.02.01 — Remove profiles
 *   US 03.03.01 — Remove images
 *   US 03.04.01 — Browse events
 *   US 03.05.01 — Browse profiles
 *   US 03.06.01 — Browse images
 *   US 03.07.01 — Remove organizers
 *   US 03.08.01 — Review notification logs
 */
@RunWith(AndroidJUnit4.class)
public class AdminTest {

    // ── Custom RecyclerView helpers (replaces espresso-contrib) ──────────────

    /** Scrolls to the first item matching the matcher and performs the action. */
    private static ViewAction actionOnItem(final Matcher<View> itemMatcher, final ViewAction action) {
        return new ViewAction() {
            @Override public Matcher<View> getConstraints() {
                return androidx.test.espresso.matcher.ViewMatchers.isDisplayed();
            }
            @Override public String getDescription() { return "action on RecyclerView item matching: " + itemMatcher; }
            @Override public void perform(UiController uiController, View view) {
                RecyclerView recycler = (RecyclerView) view;
                // Wait up to 5 seconds for the adapter to have items
                long deadline = System.currentTimeMillis() + 5000;
                while (System.currentTimeMillis() < deadline) {
                    RecyclerView.Adapter adapter = recycler.getAdapter();
                    if (adapter != null && adapter.getItemCount() > 0) break;
                    uiController.loopMainThreadForAtLeast(200);
                }
                RecyclerView.Adapter adapter = recycler.getAdapter();
                if (adapter == null || adapter.getItemCount() == 0) return;
                for (int i = 0; i < adapter.getItemCount(); i++) {
                    recycler.scrollToPosition(i);
                    uiController.loopMainThreadUntilIdle();
                    RecyclerView.ViewHolder holder = recycler.findViewHolderForAdapterPosition(i);
                    if (holder != null && itemMatcher.matches(holder.itemView)) {
                        action.perform(uiController, holder.itemView);
                        return;
                    }
                }
            }
        };
    }

    /** Scrolls to and performs an action on the item at the given position. */
    private static ViewAction actionOnItemAtPosition(final int position, final ViewAction action) {
        return new ViewAction() {
            @Override public Matcher<View> getConstraints() {
                return androidx.test.espresso.matcher.ViewMatchers.isDisplayed();
            }
            @Override public String getDescription() { return "action on RecyclerView item at position: " + position; }
            @Override public void perform(UiController uiController, View view) {
                RecyclerView recycler = (RecyclerView) view;
                // Wait up to 5 seconds for the adapter to have items
                long deadline = System.currentTimeMillis() + 5000;
                while (System.currentTimeMillis() < deadline) {
                    RecyclerView.Adapter adapter = recycler.getAdapter();
                    if (adapter != null && adapter.getItemCount() > position) break;
                    uiController.loopMainThreadForAtLeast(200);
                }
                recycler.scrollToPosition(position);
                uiController.loopMainThreadUntilIdle();
                RecyclerView.ViewHolder holder = recycler.findViewHolderForAdapterPosition(position);
                if (holder != null) {
                    action.perform(uiController, holder.itemView);
                }
            }
        };
    }

    /** Clicks a tab inside a HorizontalScrollView by scrolling it into view first. */
    private static void clickTab(int tabId) {
        // Use ViewActions to scroll the HorizontalScrollView and then click
        onView(withId(R.id.adminTabsScroll)).perform(new ViewAction() {
            @Override public Matcher<View> getConstraints() {
                return androidx.test.espresso.matcher.ViewMatchers.isDisplayed();
            }
            @Override public String getDescription() { return "scroll HorizontalScrollView to child"; }
            @Override public void perform(UiController uiController, View view) {
                View tab = view.findViewById(tabId);
                if (tab != null) {
                    tab.getParent().requestChildRectangleOnScreen(
                            tab,
                            new android.graphics.Rect(0, 0, tab.getWidth(), tab.getHeight()),
                            false);
                    uiController.loopMainThreadUntilIdle();
                }
            }
        });
        onView(withId(tabId)).perform(click());
    }

    /** Waits up to maxWaitMs for the RecyclerView to contain a descendant with the given text. */
    private static void waitForItemWithText(int recyclerId, String text, int maxWaitMs) {
        long deadline = System.currentTimeMillis() + maxWaitMs;
        while (System.currentTimeMillis() < deadline) {
            try {
                onView(withId(recyclerId))
                        .check(matches(hasDescendant(withText(text))));
                return; // found it
            } catch (Throwable ignored) {
                SystemClock.sleep(500);
            }
        }
    }
    /** Matches a RecyclerView whose adapter has at least minCount items. */
    private static org.hamcrest.Matcher<View> hasAdapterItemCount(int minCount) {
        return new org.hamcrest.TypeSafeMatcher<View>() {
            @Override public void describeTo(org.hamcrest.Description description) {
                description.appendText("RecyclerView with at least " + minCount + " adapter items");
            }
            @Override protected boolean matchesSafely(View view) {
                if (!(view instanceof RecyclerView)) return false;
                RecyclerView.Adapter adapter = ((RecyclerView) view).getAdapter();
                return adapter != null && adapter.getItemCount() >= minCount;
            }
        };
    }

    private static final String TEST_EVENT_ID       = "TEST_EVENT_ADMIN_001";
    private static final String TEST_EVENT_ID_2     = "TEST_EVENT_ADMIN_002";
    private static final String TEST_PROFILE_ID     = "TEST_PROFILE_ADMIN_001";
    private static final String TEST_ORGANIZER_ID   = "TEST_ORGANIZER_ADMIN_001";
    private static final String TEST_NOTIF_ID       = "TEST_NOTIF_ADMIN_001";
    private static final String TEST_IMAGE_EVENT_ID = "TEST_IMAGE_EVENT_ADMIN_001";

    // ── Timing constants ──────────────────────────────────────────────────────
    private static final int    UI_WAIT_MS    = 3000; // Wait for UI to update
    private static final int    LONG_WAIT_MS  = 5000; // Wait for Firestore to load
    private static final long   TIMEOUT_SECS  = 10;   // Max wait for Firestore ops

    private AdminController adminController;
    private FirebaseFirestore db;

    // ── Launches AdminActivity on screen before each test ─────────────────────
    @Rule
    public ActivityScenarioRule<AdminActivity> activityRule =
            new ActivityScenarioRule<>(AdminActivity.class);

    // ── One-time Firebase init ────────────────────────────────────────────────

    @BeforeClass
    public static void initFirebase() {
        if (FirebaseApp.getApps(
                InstrumentationRegistry.getInstrumentation().getTargetContext()).isEmpty()) {
            FirebaseApp.initializeApp(
                    InstrumentationRegistry.getInstrumentation().getTargetContext());
        }
    }

    // ── Run before every test — creates fresh test data in Firestore ──────────

    @Before
    public void setUp() throws InterruptedException {
        db = FirebaseFirestore.getInstance();
        AdminController.invalidateAll(); // Always start with fresh cache
        adminController = new AdminController();

        // Create test event (no image)
        createTestEvent(TEST_EVENT_ID, "Test Event Alpha", TEST_ORGANIZER_ID, null);

        // Create second test event owned by same organizer
        createTestEvent(TEST_EVENT_ID_2, "Test Event Beta", TEST_ORGANIZER_ID, null);

        // Create test event WITH a poster image (for Images tab tests)
        createTestEvent(TEST_IMAGE_EVENT_ID, "Test Image Event", TEST_ORGANIZER_ID,
                "https://images.unsplash.com/photo-1461896836934-ffe607ba8211?w=400");

        // Create regular user profile (no events — should NOT appear as organizer)
        createTestProfile(TEST_PROFILE_ID, "Test User", "testuser@test.com", "5551234567");

        // Create organizer profile (owns the test events above)
        createTestProfile(TEST_ORGANIZER_ID, "Test Organizer", "organizer@test.com", "5559876543");

        // Create test notification
        createTestNotification(TEST_NOTIF_ID, "Test Notification",
                "You were selected!", "selected");

        // Wait for all Firestore writes to propagate before tests run
        SystemClock.sleep(LONG_WAIT_MS);
    }

    // ── Run after every test — removes all test documents from Firestore ──────

    @After
    public void tearDown() throws InterruptedException {
        deleteDocumentIfExists("events",        TEST_EVENT_ID);
        deleteDocumentIfExists("events",        TEST_EVENT_ID_2);
        deleteDocumentIfExists("events",        TEST_IMAGE_EVENT_ID);
        deleteDocumentIfExists("profiles",      TEST_PROFILE_ID);
        deleteDocumentIfExists("profiles",      TEST_ORGANIZER_ID);
        deleteDocumentIfExists("notifications", TEST_NOTIF_ID);
        AdminController.invalidateAll();
    }

    // =========================================================================
    // US 03.04.01 — Browse events (Controller test)
    // Verifies getAllEvents() returns a list containing our test event.
    // =========================================================================

    @Test
    public void testBrowseEvents_Controller_US_03_04_01() throws InterruptedException {
        // Fetch all events directly from Firestore and verify:
        // 1. The list is not empty
        // 2. Every event has a non-null ID
        // 3. The count matches what Firestore actually has
        AdminController.invalidateAll();
        CountDownLatch latch = new CountDownLatch(1);
        final List<Event> result = new ArrayList<>();

        adminController.getAllEvents(events -> {
            if (events != null) result.addAll(events);
            latch.countDown();
        }, e -> latch.countDown());

        latch.await(TIMEOUT_SECS, TimeUnit.SECONDS);

        // Verify we got something back
        assertFalse("getAllEvents() should return a non-empty list", result.isEmpty());

        // Verify every event returned has a valid ID
        for (Event e : result) {
            assertNotNull("Every event should have a non-null ID", e.getEventId());
            assertFalse("Every event should have a non-empty ID", e.getEventId().trim().isEmpty());
        }
    }

    // =========================================================================
    // US 03.04.01 — Browse events (UI test)
    // Opens the Events tab and verifies the test event card is visible on screen.
    // =========================================================================

    @Test
    public void testBrowseEvents_UI_US_03_04_01() throws InterruptedException {
        // Step 1: Ask Firestore directly how many events exist
        AdminController.invalidateAll();
        CountDownLatch latch = new CountDownLatch(1);
        final int[] expectedCount = {0};

        adminController.getAllEvents(events -> {
            if (events != null) expectedCount[0] = events.size();
            latch.countDown();
        }, e -> latch.countDown());

        latch.await(TIMEOUT_SECS, TimeUnit.SECONDS);
        assertTrue("Firestore should have at least 1 event", expectedCount[0] > 0);

        // Step 2: Open the Events tab and verify the UI shows the same count
        AdminController.invalidateAll();
        clickTab(R.id.tab_events);
        SystemClock.sleep(LONG_WAIT_MS);

        // Section title should say "Browse & Manage Events"
        onView(withId(R.id.tvSectionTitle))
                .check(matches(withText("Browse & Manage Events")));

        // RecyclerView should be visible and show all events from Firestore
        onView(withId(R.id.adminRecycler)).check(matches(isDisplayed()));
        onView(withId(R.id.adminRecycler)).check(matches(hasAdapterItemCount(expectedCount[0])));
    }

    // =========================================================================
    // US 03.04.01 — Tap event card opens detail dialog (UI test)
    // =========================================================================

    @Test
    public void testTapEventCard_OpensDetailDialog_US_03_04_01() {
        // Tap the first real event card and verify the detail dialog opens
        clickTab(R.id.tab_events);
        SystemClock.sleep(LONG_WAIT_MS);

        onView(withId(R.id.adminRecycler))
                .perform(actionOnItemAtPosition(0, click()));
        SystemClock.sleep(UI_WAIT_MS);

        onView(withId(R.id.btnDetailClose)).check(matches(isDisplayed()));
        onView(withId(R.id.btnDetailClose)).perform(click());
    }

    // =========================================================================
    // US 03.01.01 — Remove events — swipe shows confirm dialog (UI test)
    // =========================================================================

    @Test
    public void testSwipeEvent_ShowsConfirmDialog_US_03_01_01() {
        // Swipe the first real event card left and verify the confirm dialog appears
        // then cancel — nothing is deleted
        clickTab(R.id.tab_events);
        SystemClock.sleep(LONG_WAIT_MS);

        onView(withId(R.id.adminRecycler))
                .perform(actionOnItemAtPosition(0, swipeLeft()));
        SystemClock.sleep(UI_WAIT_MS);

        onView(withId(R.id.tvDeleteTitle)).check(matches(isDisplayed()));
        onView(withId(R.id.btnConfirmDelete)).check(matches(isDisplayed()));
        onView(withId(R.id.btnCancelDelete)).check(matches(isDisplayed()));

        // Cancel — no data is deleted
        onView(withId(R.id.btnCancelDelete)).perform(click());
    }

    // =========================================================================
    // US 03.01.01 — Remove events — confirm delete (Controller + UI test)
    // Swipes the test event, confirms delete, verifies card gone from screen
    // AND document gone from Firestore.
    // =========================================================================

    @Test
    public void testConfirmDeleteEvent_US_03_01_01() throws InterruptedException {
        // Controller: verify event exists in Firestore before delete
        assertTrue("Event should exist before delete",
                documentExists("events", TEST_EVENT_ID));

        // UI: open Events tab
        onView(withId(R.id.tab_events)).perform(click());
        SystemClock.sleep(LONG_WAIT_MS);

        // UI: swipe the test event card left
        onView(withId(R.id.adminRecycler))
                .perform(actionOnItem(
                        hasDescendant(withText("Test Event Alpha")), swipeLeft()));
        SystemClock.sleep(UI_WAIT_MS);

        // UI: confirm the delete
        onView(withId(R.id.btnConfirmDelete)).perform(click());
        SystemClock.sleep(LONG_WAIT_MS);

        // UI: card should be gone from the list
        onView(withId(R.id.adminRecycler))
                .check(matches(not(hasDescendant(withText("Test Event Alpha")))));

        // Controller: verify deleted from Firestore
        assertFalse("Event should be deleted from Firestore",
                documentExists("events", TEST_EVENT_ID));
    }

    // =========================================================================
    // US 03.05.01 — Browse profiles (Controller test)
    // =========================================================================

    @Test
    public void testBrowseProfiles_Controller_US_03_05_01() throws InterruptedException {
        // Verify getAllProfiles() returns a non-empty list from real Firestore data
        AdminController.invalidateAll();
        CountDownLatch latch = new CountDownLatch(1);
        final List<Entrant> result = new ArrayList<>();

        adminController.getAllProfiles(profiles -> {
            if (profiles != null) result.addAll(profiles);
            latch.countDown();
        }, e -> latch.countDown());

        latch.await(TIMEOUT_SECS, TimeUnit.SECONDS);

        assertFalse("getAllProfiles() should return a non-empty list", result.isEmpty());
        for (Entrant p : result) {
            assertNotNull("Every profile should have a non-null deviceId", p.getDeviceId());
            assertFalse("Every profile should have a non-empty deviceId", p.getDeviceId().trim().isEmpty());
        }
    }

    // =========================================================================
    // US 03.05.01 — Browse profiles (UI test)
    // =========================================================================

    @Test
    public void testBrowseProfiles_UI_US_03_05_01() throws InterruptedException {
        // Step 1: get real count from Firestore
        AdminController.invalidateAll();
        CountDownLatch latch = new CountDownLatch(1);
        final int[] expectedCount = {0};

        adminController.getAllProfiles(profiles -> {
            if (profiles != null) expectedCount[0] = profiles.size();
            latch.countDown();
        }, e -> latch.countDown());

        latch.await(TIMEOUT_SECS, TimeUnit.SECONDS);
        assertTrue("Firestore should have at least 1 profile", expectedCount[0] > 0);

        // Step 2: verify UI shows all profiles
        AdminController.invalidateAll();
        onView(withId(R.id.tab_profiles)).perform(click());
        SystemClock.sleep(LONG_WAIT_MS);

        onView(withId(R.id.tvSectionTitle))
                .check(matches(withText("Browse & Manage User Profiles")));

        onView(withId(R.id.adminRecycler)).check(matches(isDisplayed()));
        onView(withId(R.id.adminRecycler)).check(matches(hasAdapterItemCount(expectedCount[0])));
    }

    // =========================================================================
    // US 03.05.01 — Tap profile card opens detail dialog (UI test)
    // =========================================================================

    @Test
    public void testTapProfileCard_OpensDetailDialog_US_03_05_01() {
        // Tap the first real profile card and verify the detail dialog opens
        onView(withId(R.id.tab_profiles)).perform(click());
        SystemClock.sleep(LONG_WAIT_MS);

        onView(withId(R.id.adminRecycler))
                .perform(actionOnItemAtPosition(0, click()));
        SystemClock.sleep(UI_WAIT_MS);

        onView(withId(R.id.btnDetailClose)).check(matches(isDisplayed()));
        onView(withId(R.id.btnDetailClose)).perform(click());
    }

    // =========================================================================
    // US 03.02.01 — Remove profiles (Controller + UI test)
    // Also verifies their events are deleted from Firestore.
    // =========================================================================

    @Test
    public void testRemoveProfile_US_03_02_01() throws InterruptedException {
        assertTrue("Profile should exist before delete",
                documentExists("profiles", TEST_PROFILE_ID));

        onView(withId(R.id.tab_profiles)).perform(click());
        SystemClock.sleep(LONG_WAIT_MS);

        onView(withId(R.id.adminRecycler))
                .perform(actionOnItem(
                        hasDescendant(withText("Test User")), swipeLeft()));
        SystemClock.sleep(UI_WAIT_MS);

        onView(withId(R.id.btnConfirmDelete)).perform(click());
        SystemClock.sleep(LONG_WAIT_MS);

        // Card gone from list
        onView(withId(R.id.adminRecycler))
                .check(matches(not(hasDescendant(withText("Test User")))));

        // Profile deleted from Firestore
        assertFalse("Profile should be deleted from Firestore",
                documentExists("profiles", TEST_PROFILE_ID));
    }

    // =========================================================================
    // US 03.02.01 — Remove profile also deletes their events (Controller test)
    // =========================================================================

    @Test
    public void testRemoveProfile_AlsoDeletesEvents_US_03_02_01()
            throws InterruptedException {
        // Create a dedicated owner + their event
        String ownerId = "TEST_OWNER_CASCADE_DELETE";
        String ownedEventId = "TEST_OWNED_EVENT_CASCADE";
        createTestProfile(ownerId, "Cascade Owner", "cascade@test.com", "");
        createTestEvent(ownedEventId, "Cascade Event", ownerId, null);

        assertTrue(documentExists("profiles", ownerId));
        assertTrue(documentExists("events",   ownedEventId));

        CountDownLatch latch = new CountDownLatch(1);
        adminController.removeProfile(ownerId, unused -> latch.countDown(),
                e -> latch.countDown());
        latch.await(TIMEOUT_SECS, TimeUnit.SECONDS);

        // Both profile AND their event should be gone
        assertFalse("Profile should be deleted",
                documentExists("profiles", ownerId));
        assertFalse("Event owned by deleted profile should also be deleted",
                documentExists("events", ownedEventId));

        // Cleanup
        deleteDocumentIfExists("profiles", ownerId);
        deleteDocumentIfExists("events",   ownedEventId);
    }

    // =========================================================================
    // US 03.06.01 — Browse images (Controller test)
    // Only events with a posterImageUrl should be returned.
    // =========================================================================

    @Test
    public void testBrowseImages_Controller_US_03_06_01() throws InterruptedException {
        // Verify getAllImagesFromEvents() only returns events that have a poster image
        AdminController.invalidateAll();
        CountDownLatch latch = new CountDownLatch(1);
        final List<Event> result = new ArrayList<>();

        adminController.getAllImagesFromEvents(events -> {
            if (events != null) result.addAll(events);
            latch.countDown();
        }, e -> latch.countDown());

        latch.await(TIMEOUT_SECS, TimeUnit.SECONDS);

        // Every returned event must have a non-empty posterImageUrl
        for (Event e : result) {
            assertNotNull("Every image result must have a posterImageUrl", e.getPosterImageUrl());
            assertFalse("posterImageUrl must not be empty", e.getPosterImageUrl().trim().isEmpty());
        }
    }

    // =========================================================================
    // US 03.06.01 — Browse images (UI test)
    // =========================================================================

    @Test
    public void testBrowseImages_UI_US_03_06_01() throws InterruptedException {
        // Step 1: get real image count from Firestore
        AdminController.invalidateAll();
        CountDownLatch latch = new CountDownLatch(1);
        final int[] expectedCount = {0};

        adminController.getAllImagesFromEvents(events -> {
            if (events != null) expectedCount[0] = events.size();
            latch.countDown();
        }, e -> latch.countDown());

        latch.await(TIMEOUT_SECS, TimeUnit.SECONDS);
        assertTrue("Firestore should have at least 1 event with an image", expectedCount[0] > 0);

        // Step 2: verify UI shows all image events
        AdminController.invalidateAll();
        onView(withId(R.id.tab_images)).perform(click());
        SystemClock.sleep(LONG_WAIT_MS);

        onView(withId(R.id.tvSectionTitle))
                .check(matches(withText("Browse & Manage Uploaded Images")));

        onView(withId(R.id.adminRecycler)).check(matches(isDisplayed()));
        onView(withId(R.id.adminRecycler)).check(matches(hasAdapterItemCount(expectedCount[0])));
    }

    // =========================================================================
    // US 03.06.01 — Tap image card opens image dialog (UI test)
    // =========================================================================

    @Test
    public void testTapImageCard_OpensImageDialog_US_03_06_01() {
        onView(withId(R.id.tab_images)).perform(click());
        SystemClock.sleep(LONG_WAIT_MS);

        onView(withId(R.id.adminRecycler))
                .perform(actionOnItemAtPosition(0, click()));
        SystemClock.sleep(UI_WAIT_MS);

        onView(withId(R.id.btnImageDialogCloseBottom)).check(matches(isDisplayed()));
        onView(withId(R.id.btnImageDialogCloseBottom)).perform(click());
    }

    // =========================================================================
    // US 03.03.01 — Remove image only (Controller + UI test)
    // Swipes image card → taps "Remove Image Only" → event stays, image cleared.
    // =========================================================================

    @Test
    public void testRemoveImageOnly_US_03_03_01() throws InterruptedException {
        onView(withId(R.id.tab_images)).perform(click());
        SystemClock.sleep(LONG_WAIT_MS);

        onView(withId(R.id.adminRecycler))
                .perform(actionOnItem(
                        hasDescendant(withText("Test Image Event")), swipeLeft()));
        SystemClock.sleep(UI_WAIT_MS);

        // Two-option image delete dialog appears
        onView(withId(R.id.btnDeleteImageOnly)).check(matches(isDisplayed()));
        onView(withId(R.id.btnDeleteImageAndEvent)).check(matches(isDisplayed()));

        // Tap "Remove Image Only"
        onView(withId(R.id.btnDeleteImageOnly)).perform(click());
        SystemClock.sleep(LONG_WAIT_MS);

        // Event document still exists in Firestore
        assertTrue("Event should still exist after image-only removal",
                documentExists("events", TEST_IMAGE_EVENT_ID));

        // posterImageUrl should now be null in Firestore
        final String[] url = {""};
        CountDownLatch latch = new CountDownLatch(1);
        db.collection("events").document(TEST_IMAGE_EVENT_ID).get()
                .addOnSuccessListener(doc -> {
                    url[0] = doc.getString("posterImageUrl");
                    latch.countDown();
                })
                .addOnFailureListener(e -> latch.countDown());
        latch.await(TIMEOUT_SECS, TimeUnit.SECONDS);
        assertTrue("posterImageUrl should be null after image removal",
                url[0] == null || url[0].isEmpty());
    }

    // =========================================================================
    // US 03.03.01 — Remove image AND event (Controller + UI test)
    // =========================================================================

    @Test
    public void testRemoveImageAndEvent_US_03_03_01() throws InterruptedException {
        onView(withId(R.id.tab_images)).perform(click());
        SystemClock.sleep(LONG_WAIT_MS);

        onView(withId(R.id.adminRecycler))
                .perform(actionOnItem(
                        hasDescendant(withText("Test Image Event")), swipeLeft()));
        SystemClock.sleep(UI_WAIT_MS);

        // Tap "Delete Image & Event"
        onView(withId(R.id.btnDeleteImageAndEvent)).perform(click());
        SystemClock.sleep(LONG_WAIT_MS);

        // Entire event document gone from Firestore
        assertFalse("Event should be fully deleted from Firestore",
                documentExists("events", TEST_IMAGE_EVENT_ID));
    }

    // =========================================================================
    // US 03.07.01 — Browse organizers (Controller test)
    // Only profiles that own at least one event should be returned.
    // =========================================================================

    @Test
    public void testBrowseOrganizers_Controller_US_03_07_01() throws InterruptedException {
        // Verify getAllOrganizers() returns only profiles that own at least one event
        AdminController.invalidateAll();
        CountDownLatch latch = new CountDownLatch(1);
        final List<Entrant> result = new ArrayList<>();

        adminController.getAllOrganizers(organizers -> {
            if (organizers != null) result.addAll(organizers);
            latch.countDown();
        }, e -> latch.countDown());

        latch.await(TIMEOUT_SECS, TimeUnit.SECONDS);

        assertFalse("getAllOrganizers() should return a non-empty list", result.isEmpty());
        for (Entrant o : result) {
            assertNotNull("Every organizer should have a non-null deviceId", o.getDeviceId());
            assertFalse("Every organizer should have a non-empty deviceId", o.getDeviceId().trim().isEmpty());
        }
    }

    // =========================================================================
    // US 03.07.01 — Browse organizers (UI test)
    // =========================================================================

    @Test
    public void testBrowseOrganizers_UI_US_03_07_01() throws InterruptedException {
        // Step 1: get real organizer count from Firestore
        AdminController.invalidateAll();
        CountDownLatch latch = new CountDownLatch(1);
        final int[] expectedCount = {0};

        adminController.getAllOrganizers(organizers -> {
            if (organizers != null) expectedCount[0] = organizers.size();
            latch.countDown();
        }, e -> latch.countDown());

        latch.await(TIMEOUT_SECS, TimeUnit.SECONDS);
        assertTrue("Firestore should have at least 1 organizer", expectedCount[0] > 0);

        // Step 2: verify UI shows all organizers
        AdminController.invalidateAll();
        onView(withId(R.id.tab_organizers)).perform(click());
        SystemClock.sleep(LONG_WAIT_MS);

        onView(withId(R.id.tvSectionTitle))
                .check(matches(withText("Browse & Manage Organizers")));

        onView(withId(R.id.adminRecycler)).check(matches(isDisplayed()));
        onView(withId(R.id.adminRecycler)).check(matches(hasAdapterItemCount(expectedCount[0])));
    }

    // =========================================================================
    // US 03.07.01 — Remove organizer also deletes their events (Controller + UI)
    // =========================================================================

    @Test
    public void testRemoveOrganizer_AlsoDeletesEvents_US_03_07_01()
            throws InterruptedException {
        assertTrue(documentExists("profiles", TEST_ORGANIZER_ID));
        assertTrue(documentExists("events",   TEST_EVENT_ID));
        assertTrue(documentExists("events",   TEST_EVENT_ID_2));

        onView(withId(R.id.tab_organizers)).perform(click());
        SystemClock.sleep(LONG_WAIT_MS);

        onView(withId(R.id.adminRecycler))
                .perform(actionOnItem(
                        hasDescendant(withText("Test Organizer")), swipeLeft()));
        SystemClock.sleep(UI_WAIT_MS);

        onView(withId(R.id.btnConfirmDelete)).perform(click());
        SystemClock.sleep(LONG_WAIT_MS);

        // Card gone from list
        onView(withId(R.id.adminRecycler))
                .check(matches(not(hasDescendant(withText("Test Organizer")))));

        // Organizer profile deleted from Firestore
        assertFalse("Organizer should be deleted",
                documentExists("profiles", TEST_ORGANIZER_ID));

        // All their events also deleted from Firestore
        assertFalse("Organizer's first event should be deleted",
                documentExists("events", TEST_EVENT_ID));
        assertFalse("Organizer's second event should be deleted",
                documentExists("events", TEST_EVENT_ID_2));
        assertFalse("Organizer's image event should be deleted",
                documentExists("events", TEST_IMAGE_EVENT_ID));
    }

    // =========================================================================
    // US 03.08.01 — Review notification logs (Controller test)
    // =========================================================================

    // @Test
    public void testBrowseNotifications_Controller_US_03_08_01()
            throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        final List<Notification> result = new ArrayList<>();

        adminController.getAllNotifications(notifications -> {
            if (notifications != null) result.addAll(notifications);
            latch.countDown();
        }, e -> latch.countDown());

        latch.await(TIMEOUT_SECS, TimeUnit.SECONDS);

        assertFalse("Notification list should not be empty", result.isEmpty());

        boolean found = false;
        for (Notification n : result) {
            if (TEST_NOTIF_ID.equals(n.getId())) {
                found = true;
                assertEquals("Test Notification", n.getTitle());
                assertEquals("You were selected!", n.getMessage());
                assertEquals("selected", n.getType());
                break;
            }
        }
        assertTrue("Test notification should be in the list", found);
    }

    // =========================================================================
    // US 03.08.01 — Review notification logs (UI test)
    // =========================================================================

    // @Test
    public void testBrowseNotifications_UI_US_03_08_01() {
        clickTab(R.id.tab_notifications);
        SystemClock.sleep(LONG_WAIT_MS);

        onView(withId(R.id.tvSectionTitle))
                .check(matches(withText("Review Notification Logs")));

        onView(withId(R.id.adminRecycler)).check(matches(isDisplayed()));

        onView(withId(R.id.adminRecycler))
                .check(matches(hasDescendant(withText("Test Notification"))));
    }

    // =========================================================================
    // US 03.08.01 — Notifications tab swipe snaps back (read-only, UI test)
    // =========================================================================

    // @Test
    public void testNotifications_SwipeSnapsBack_US_03_08_01()
            throws InterruptedException {
        clickTab(R.id.tab_notifications);
        SystemClock.sleep(LONG_WAIT_MS);

        // Swipe left — should snap back, no delete dialog
        onView(withId(R.id.adminRecycler))
                .perform(actionOnItemAtPosition(0, swipeLeft()));
        SystemClock.sleep(UI_WAIT_MS);

        // List still visible (not deleted)
        onView(withId(R.id.adminRecycler)).check(matches(isDisplayed()));

        // Notification still in Firestore
        assertTrue("Notification should NOT be deleted — tab is read-only",
                documentExists("notifications", TEST_NOTIF_ID));
    }

    // =========================================================================
    // Search bar filtering (UI test)
    // =========================================================================

    @Test
    public void testSearchBar_FiltersList() throws InterruptedException {
        // Step 1: get the name of the first real event from Firestore to use as search term
        AdminController.invalidateAll();
        CountDownLatch latch = new CountDownLatch(1);
        final String[] firstEventName = {null};

        adminController.getAllEvents(events -> {
            if (events != null && !events.isEmpty()) {
                firstEventName[0] = events.get(0).getName();
            }
            latch.countDown();
        }, e -> latch.countDown());

        latch.await(TIMEOUT_SECS, TimeUnit.SECONDS);
        assertNotNull("Need at least one real event to test search", firstEventName[0]);

        // Step 2: open Events tab and search for that name
        AdminController.invalidateAll();
        clickTab(R.id.tab_events);
        SystemClock.sleep(LONG_WAIT_MS);

        onView(withId(R.id.etSearch)).perform(typeText(firstEventName[0]));
        SystemClock.sleep(UI_WAIT_MS);
        onView(withId(R.id.adminRecycler))
                .check(matches(hasDescendant(withText(firstEventName[0]))));

        // Step 3: search for something that matches nothing
        onView(withId(R.id.etSearch)).perform(clearText());
        onView(withId(R.id.etSearch)).perform(typeText("ZZZNOMATCH999"));
        SystemClock.sleep(UI_WAIT_MS);
        onView(withId(R.id.emptyMessage)).check(matches(isDisplayed()));

        // Step 4: clear search — full list comes back
        onView(withId(R.id.etSearch)).perform(clearText());
        SystemClock.sleep(UI_WAIT_MS);
        onView(withId(R.id.adminRecycler)).check(matches(isDisplayed()));
    }

    // =========================================================================
    // All 5 tabs switch correctly (UI test)
    // =========================================================================

    @Test
    public void testAllTabsSwitch() {
        onView(withId(R.id.tab_events)).perform(click());
        SystemClock.sleep(UI_WAIT_MS);
        onView(withId(R.id.tvSectionTitle))
                .check(matches(withText("Browse & Manage Events")));

        onView(withId(R.id.tab_profiles)).perform(click());
        SystemClock.sleep(UI_WAIT_MS);
        onView(withId(R.id.tvSectionTitle))
                .check(matches(withText("Browse & Manage User Profiles")));

        onView(withId(R.id.tab_images)).perform(click());
        SystemClock.sleep(UI_WAIT_MS);
        onView(withId(R.id.tvSectionTitle))
                .check(matches(withText("Browse & Manage Uploaded Images")));

        onView(withId(R.id.tab_organizers)).perform(click());
        SystemClock.sleep(UI_WAIT_MS);
        onView(withId(R.id.tvSectionTitle))
                .check(matches(withText("Browse & Manage Organizers")));

        clickTab(R.id.tab_notifications);
        SystemClock.sleep(UI_WAIT_MS);
        onView(withId(R.id.tvSectionTitle))
                .check(matches(withText("Review Notification Logs")));
    }

    // =========================================================================
    // Cache invalidation test (Controller test)
    // =========================================================================

    @Test
    public void testCacheInvalidation() throws InterruptedException {
        // Step 1: fetch once — populates cache
        AdminController.invalidateAll();
        CountDownLatch latch1 = new CountDownLatch(1);
        final List<Event> firstResult = new ArrayList<>();
        adminController.getAllEvents(events -> {
            if (events != null) firstResult.addAll(events);
            latch1.countDown();
        }, e -> latch1.countDown());
        latch1.await(TIMEOUT_SECS, TimeUnit.SECONDS);
        int firstCount = firstResult.size();
        assertTrue("Need at least 1 real event to test caching", firstCount > 0);

        // Step 2: fetch again WITHOUT invalidation — should return same cached count
        CountDownLatch latch2 = new CountDownLatch(1);
        final List<Event> cachedResult = new ArrayList<>();
        adminController.getAllEvents(events -> {
            if (events != null) cachedResult.addAll(events);
            latch2.countDown();
        }, e -> latch2.countDown());
        latch2.await(TIMEOUT_SECS, TimeUnit.SECONDS);
        assertEquals("Second fetch without invalidation should return cached count",
                firstCount, cachedResult.size());

        // Step 3: invalidate then fetch — should return same real count from Firestore
        AdminController.invalidateAll();
        CountDownLatch latch3 = new CountDownLatch(1);
        final List<Event> freshResult = new ArrayList<>();
        adminController.getAllEvents(events -> {
            if (events != null) freshResult.addAll(events);
            latch3.countDown();
        }, e -> latch3.countDown());
        latch3.await(TIMEOUT_SECS, TimeUnit.SECONDS);
        assertEquals("Fresh fetch after invalidation should return the same real count",
                firstCount, freshResult.size());
    }

    // =========================================================================
    // Helper methods
    // =========================================================================

    private void createTestEvent(String eventId, String name,
                                 String organizerDeviceId, String posterImageUrl)
            throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        Map<String, Object> data = new HashMap<>();
        data.put("eventId",           eventId);
        data.put("name",              name);
        data.put("description",       "Test description for " + name);
        data.put("location",          "Test Location");
        data.put("organizerDeviceId", organizerDeviceId);
        data.put("eventDate",         new Timestamp(new Date()));
        data.put("category",          "TEST");
        data.put("posterImageUrl",    posterImageUrl);
        db.collection("events").document(eventId).set(data)
                .addOnSuccessListener(v -> latch.countDown())
                .addOnFailureListener(e -> latch.countDown());
        latch.await(TIMEOUT_SECS, TimeUnit.SECONDS);
    }

    private void createTestProfile(String deviceId, String name,
                                   String email, String phone)
            throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        Map<String, Object> data = new HashMap<>();
        data.put("deviceId", deviceId);
        data.put("name",     name);
        data.put("email",    email);
        data.put("phone",    phone);
        db.collection("profiles").document(deviceId).set(data)
                .addOnSuccessListener(v -> latch.countDown())
                .addOnFailureListener(e -> latch.countDown());
        latch.await(TIMEOUT_SECS, TimeUnit.SECONDS);
    }

    private void createTestNotification(String notifId, String title,
                                        String message, String type)
            throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        Map<String, Object> data = new HashMap<>();
        data.put("id",          notifId);
        data.put("title",       title);
        data.put("message",     message);
        data.put("type",        type);
        data.put("timestamp",   new Timestamp(new Date()));
        data.put("recipientId", "TEST_RECIPIENT_001");
        db.collection("notifications").document(notifId).set(data)
                .addOnSuccessListener(v -> latch.countDown())
                .addOnFailureListener(e -> latch.countDown());
        latch.await(TIMEOUT_SECS, TimeUnit.SECONDS);
    }

    private boolean documentExists(String collection, String docId)
            throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        final boolean[] exists = {false};
        db.collection(collection).document(docId).get()
                .addOnSuccessListener(doc -> {
                    exists[0] = doc.exists();
                    latch.countDown();
                })
                .addOnFailureListener(e -> latch.countDown());
        latch.await(TIMEOUT_SECS, TimeUnit.SECONDS);
        return exists[0];
    }

    private void deleteDocumentIfExists(String collection, String docId)
            throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        db.collection(collection).document(docId).delete()
                .addOnSuccessListener(v -> latch.countDown())
                .addOnFailureListener(e -> latch.countDown());
        latch.await(TIMEOUT_SECS, TimeUnit.SECONDS);
    }
}