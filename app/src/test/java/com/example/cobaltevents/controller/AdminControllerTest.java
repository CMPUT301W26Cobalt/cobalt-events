package com.example.cobaltevents.controller;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.example.cobaltevents.db.EventDB;
import com.example.cobaltevents.db.ProfileDB;
import com.example.cobaltevents.model.Entrant;
import com.example.cobaltevents.model.Event;
import com.google.firebase.firestore.FirebaseFirestore;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;

/**
 * Unit tests for {@link AdminController}.
 *
 * The constructor calls {@code new EventDB()}, {@code new ProfileDB()}, and
 * {@code FirebaseFirestore.getInstance()} — all Firebase-dependent. These are
 * intercepted with Mockito so that no network or Android runtime is needed.
 *
 * Tests focus on:
 *  - {@code invalidateAll()} — public static cache-clear method
 *  - Cache-hit paths in {@code getAllEvents} / {@code getAllProfiles} /
 *    {@code getAllImagesFromEvents} (the branches that never touch Firestore)
 */
public class AdminControllerTest {

    private MockedConstruction<EventDB>       mockedEventDB;
    private MockedConstruction<ProfileDB>     mockedProfileDB;
    private MockedStatic<FirebaseFirestore>   mockedFirestore;

    @Before
    public void setUp() throws Exception {
        mockedEventDB    = Mockito.mockConstruction(EventDB.class);
        mockedProfileDB  = Mockito.mockConstruction(ProfileDB.class);

        FirebaseFirestore firestoreMock = Mockito.mock(FirebaseFirestore.class);
        mockedFirestore  = Mockito.mockStatic(FirebaseFirestore.class);
        mockedFirestore.when(FirebaseFirestore::getInstance).thenReturn(firestoreMock);

        // Ensure caches start empty before each test
        setCachedEvents(null);
        setCachedProfiles(null);
    }

    @After
    public void tearDown() throws Exception {
        mockedEventDB.close();
        mockedProfileDB.close();
        mockedFirestore.close();

        // Clean up static state so other tests aren't affected
        setCachedEvents(null);
        setCachedProfiles(null);
    }

    // ── invalidateAll ─────────────────────────────────────────────────────────

    @Test
    public void invalidateAll_clearsBothCaches() throws Exception {
        // Pre-populate caches via reflection
        setCachedEvents(Arrays.asList(new Event()));
        setCachedProfiles(Arrays.asList(new Entrant()));

        AdminController.invalidateAll();

        assertNull("cachedEvents should be null after invalidateAll()",
                getCachedEvents());
        assertNull("cachedProfiles should be null after invalidateAll()",
                getCachedProfiles());
    }

    @Test
    public void invalidateAll_isIdempotent() {
        // Calling twice on already-null caches must not throw
        AdminController.invalidateAll();
        AdminController.invalidateAll();
        assertNull(getCachedEvents());
        assertNull(getCachedProfiles());
    }

    // ── getAllEvents — cache-hit path ─────────────────────────────────────────

    @Test
    public void getAllEvents_withCachedEvents_returnsCache_withoutFirestore() throws Exception {
        List<Event> cached = Arrays.asList(new Event(), new Event());
        setCachedEvents(cached);

        AdminController controller = new AdminController();
        List<Event>[] result = new List[]{null};
        controller.getAllEvents(r -> result[0] = r, e -> {});

        assertNotNull(result[0]);
        assertSame("Should return the exact cached list instance", cached, result[0]);
    }

    @Test
    public void getAllEvents_withCachedEvents_invokesOnUsedInMemoryCache() throws Exception {
        setCachedEvents(Arrays.asList(new Event()));

        AdminController controller = new AdminController();
        boolean[] cacheCalled = {false};
        controller.getAllEvents(r -> {}, e -> {}, () -> cacheCalled[0] = true);

        assertTrue("onUsedInMemoryCache runnable should be invoked on a cache hit",
                cacheCalled[0]);
    }

    @Test
    public void getAllEvents_withNullCacheCallback_doesNotThrow() throws Exception {
        setCachedEvents(Arrays.asList(new Event()));

        AdminController controller = new AdminController();
        // Passing null for onUsedInMemoryCache must not throw NullPointerException
        controller.getAllEvents(r -> {}, e -> {}, null);
    }

    // ── getAllProfiles — cache-hit path ───────────────────────────────────────

    @Test
    public void getAllProfiles_withCachedProfiles_returnsCache() throws Exception {
        Entrant e1 = new Entrant(); e1.setDeviceId("d1");
        List<Entrant> cached = Arrays.asList(e1);
        setCachedProfiles(cached);

        AdminController controller = new AdminController();
        List<Entrant>[] result = new List[]{null};
        controller.getAllProfiles(r -> result[0] = r, ex -> {});

        assertNotNull(result[0]);
        assertSame(cached, result[0]);
    }

    @Test
    public void getAllProfiles_withCachedProfiles_invokesOnUsedInMemoryCache() throws Exception {
        setCachedProfiles(Arrays.asList(new Entrant()));

        AdminController controller = new AdminController();
        boolean[] cacheCalled = {false};
        controller.getAllProfiles(r -> {}, e -> {}, () -> cacheCalled[0] = true);

        assertTrue(cacheCalled[0]);
    }

    // ── getAllImagesFromEvents — cache-hit path / filter logic ─────────────────

    @Test
    public void getAllImagesFromEvents_filtersEventsWithoutImages() throws Exception {
        Event withImage    = new Event(); withImage.setPosterImageUrl("https://example.com/img.png");
        Event withoutImage = new Event(); withoutImage.setPosterImageUrl(null);
        Event emptyUrl     = new Event(); emptyUrl.setPosterImageUrl("   ");

        setCachedEvents(Arrays.asList(withImage, withoutImage, emptyUrl));

        AdminController controller = new AdminController();
        List<Event>[] result = new List[]{null};
        controller.getAllImagesFromEvents(r -> result[0] = r, e -> {});

        assertNotNull(result[0]);
        assertTrue("Only events with non-blank poster URLs should be included",
                result[0].size() == 1);
        assertSame(withImage, result[0].get(0));
    }

    @Test
    public void getAllImagesFromEvents_allEventsHaveImages_returnsAll() throws Exception {
        Event e1 = new Event(); e1.setPosterImageUrl("https://example.com/1.png");
        Event e2 = new Event(); e2.setPosterImageUrl("https://example.com/2.png");
        setCachedEvents(Arrays.asList(e1, e2));

        AdminController controller = new AdminController();
        List<Event>[] result = new List[]{null};
        controller.getAllImagesFromEvents(r -> result[0] = r, ex -> {});

        assertTrue(result[0].size() == 2);
    }

    @Test
    public void getAllImagesFromEvents_noEventsHaveImages_returnsEmpty() throws Exception {
        Event e1 = new Event(); e1.setPosterImageUrl(null);
        Event e2 = new Event(); e2.setPosterImageUrl("");
        setCachedEvents(Arrays.asList(e1, e2));

        AdminController controller = new AdminController();
        List<Event>[] result = new List[]{null};
        controller.getAllImagesFromEvents(r -> result[0] = r, ex -> {});

        assertNotNull(result[0]);
        assertTrue(result[0].isEmpty());
    }

    // ── Reflection helpers ────────────────────────────────────────────────────

    private static void setCachedEvents(List<Event> value) throws Exception {
        Field f = AdminController.class.getDeclaredField("cachedEvents");
        f.setAccessible(true);
        f.set(null, value);
    }

    private static void setCachedProfiles(List<Entrant> value) throws Exception {
        Field f = AdminController.class.getDeclaredField("cachedProfiles");
        f.setAccessible(true);
        f.set(null, value);
    }

    @SuppressWarnings("unchecked")
    private static List<Event> getCachedEvents() {
        try {
            Field f = AdminController.class.getDeclaredField("cachedEvents");
            f.setAccessible(true);
            return (List<Event>) f.get(null);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @SuppressWarnings("unchecked")
    private static List<Entrant> getCachedProfiles() {
        try {
            Field f = AdminController.class.getDeclaredField("cachedProfiles");
            f.setAccessible(true);
            return (List<Entrant>) f.get(null);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
