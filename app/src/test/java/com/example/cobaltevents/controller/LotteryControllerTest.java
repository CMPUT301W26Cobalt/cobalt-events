package com.example.cobaltevents.controller;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.example.cobaltevents.db.EventDB;
import com.example.cobaltevents.db.NotificationDB;
import com.example.cobaltevents.db.WaitingListDB;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedConstruction;
import org.mockito.Mockito;

/**
 * Unit tests for {@link LotteryController}.
 *
 * All DB dependencies (EventDB, WaitingListDB, NotificationDB) call Firebase in their
 * constructors; they are intercepted with {@code Mockito.mockConstruction} so that no
 * network or Android runtime is needed.
 *
 * Only the pure-Java validation branches (no DB calls) are exercised here.
 * Firestore-dependent paths are covered by instrumented / integration tests.
 */
public class LotteryControllerTest {

    private MockedConstruction<EventDB>            mockedEventDB;
    private MockedConstruction<WaitingListDB>      mockedWaitingListDB;
    private MockedConstruction<NotificationDB>     mockedNotificationDB;

    private LotteryController controller;

    @Before
    public void setUp() {
        mockedEventDB        = Mockito.mockConstruction(EventDB.class);
        mockedWaitingListDB  = Mockito.mockConstruction(WaitingListDB.class);
        mockedNotificationDB = Mockito.mockConstruction(NotificationDB.class);

        controller = new LotteryController();
    }

    @After
    public void tearDown() {
        mockedEventDB.close();
        mockedWaitingListDB.close();
        mockedNotificationDB.close();
    }

    // ── runLotteryDraw — input validation ─────────────────────────────────────

    @Test
    public void runLotteryDraw_zeroCount_callsOnFailure() {
        Exception[] caught = {null};
        controller.runLotteryDraw("event1", 0,
                ignored -> {},
                e -> caught[0] = e);

        assertNotNull("onFailure should be called for requestedCount = 0", caught[0]);
        assertTrue(caught[0] instanceof IllegalArgumentException);
    }

    @Test
    public void runLotteryDraw_negativeCount_callsOnFailure() {
        Exception[] caught = {null};
        controller.runLotteryDraw("event1", -5,
                ignored -> {},
                e -> caught[0] = e);

        assertNotNull("onFailure should be called for requestedCount < 0", caught[0]);
        assertTrue(caught[0] instanceof IllegalArgumentException);
    }

    @Test
    public void runLotteryDraw_negativeCount_errorMessageMentionsPositive() {
        Exception[] caught = {null};
        controller.runLotteryDraw("event1", -1,
                ignored -> {},
                e -> caught[0] = e);

        assertNotNull(caught[0]);
        assertTrue(caught[0].getMessage().toLowerCase().contains("positive"));
    }

    @Test
    public void runLotteryDraw_zeroCount_doesNotCallOnSuccess() {
        boolean[] successCalled = {false};
        controller.runLotteryDraw("event1", 0,
                ignored -> successCalled[0] = true,
                e -> {});

        assertTrue("onSuccess must NOT be called when requestedCount is invalid",
                !successCalled[0]);
    }
}
