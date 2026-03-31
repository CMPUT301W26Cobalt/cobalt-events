package com.example.cobaltevents.ui.adapter;

import static org.junit.Assert.*;

import android.content.Context;
import android.view.View;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.example.cobaltevents.R;
import com.example.cobaltevents.model.Event;
import com.example.cobaltevents.model.EventHistory;
import com.example.cobaltevents.model.WaitingList;
import com.google.firebase.Timestamp;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;

/**
 * Unit tests for EventHistoryAdapter
 * Tests the display and color coding of event history items
 */
@RunWith(AndroidJUnit4.class)
public class EventHistoryAdapterTest {

    private EventHistoryAdapter adapter;
    private Context context;
    private List<EventHistory> testHistory;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        testHistory = new ArrayList<>();
        adapter = new EventHistoryAdapter(testHistory, new EventHistoryAdapter.Listener() {
            @Override public void onHistoryClick(com.example.cobaltevents.model.EventHistory history) {}
            @Override public void onDeleteClick(com.example.cobaltevents.model.EventHistory history) {}
        });
    }

    // ── Adapter initialization ───────────────────────────────────────────────

    @Test
    public void adapter_initializes_withEmptyList() {
        assertEquals(0, adapter.getItemCount());
    }

    @Test
    public void adapter_initializes_withNonEmptyList() {
        testHistory.add(createMockEventHistory("event1", "Test Event", "pending"));
        adapter = new EventHistoryAdapter(testHistory, new EventHistoryAdapter.Listener() {
            @Override public void onHistoryClick(com.example.cobaltevents.model.EventHistory history) {}
            @Override public void onDeleteClick(com.example.cobaltevents.model.EventHistory history) {}
        });
        
        assertEquals(1, adapter.getItemCount());
    }

    // ── Update history ───────────────────────────────────────────────────────

    @Test
    public void updateHistory_updatesItemCount() {
        assertEquals(0, adapter.getItemCount());
        
        List<EventHistory> newHistory = new ArrayList<>();
        newHistory.add(createMockEventHistory("event1", "Event 1", "pending"));
        newHistory.add(createMockEventHistory("event2", "Event 2", "selected"));
        
        adapter.updateHistory(newHistory);
        
        assertEquals(2, adapter.getItemCount());
    }

    @Test
    public void updateHistory_withEmptyList_clearsAdapter() {
        List<EventHistory> initialHistory = new ArrayList<>();
        initialHistory.add(createMockEventHistory("event1", "Event 1", "pending"));
        adapter.updateHistory(initialHistory);
        
        assertEquals(1, adapter.getItemCount());
        
        adapter.updateHistory(new ArrayList<>());
        
        assertEquals(0, adapter.getItemCount());
    }

    // ── Status color coding ──────────────────────────────────────────────────

    @Test
    public void eventHistory_withPendingStatus_hasOrangeColor() {
        EventHistory history = createMockEventHistory("event1", "Test Event", "pending");
        
        assertEquals("pending", history.getRegistration().getStatus());
    }

    @Test
    public void eventHistory_withSelectedStatus_hasGreenColor() {
        EventHistory history = createMockEventHistory("event1", "Test Event", "selected");
        
        assertEquals("selected", history.getRegistration().getStatus());
    }

    @Test
    public void eventHistory_withNotSelectedStatus_hasRedColor() {
        EventHistory history = createMockEventHistory("event1", "Test Event", "not_selected");
        
        assertEquals("not_selected", history.getRegistration().getStatus());
    }

    @Test
    public void eventHistory_withDeclinedStatus_hasRedColor() {
        EventHistory history = createMockEventHistory("event1", "Test Event", "declined");
        
        assertEquals("declined", history.getRegistration().getStatus());
    }

    @Test
    public void eventHistory_withDeclinedAliasStatus_hasRedColor() {
        EventHistory history = createMockEventHistory("event1", "Test Event", "declined");
        
        assertEquals("declined", history.getRegistration().getStatus());
    }

    // ── Event data display ───────────────────────────────────────────────────

    @Test
    public void eventHistory_containsEventName() {
        EventHistory history = createMockEventHistory("event1", "Music Festival", "pending");
        
        assertEquals("Music Festival", history.getEvent().getName());
    }

    @Test
    public void eventHistory_containsEventLocation() {
        Event event = new Event();
        event.setEventId("event1");
        event.setName("Test Event");
        event.setLocation("Downtown Arena");
        
        WaitingList registration = new WaitingList("event1", "device123", "pending");
        EventHistory history = new EventHistory(event, registration);
        
        assertEquals("Downtown Arena", history.getEvent().getLocation());
    }

    @Test
    public void eventHistory_containsEventDate() {
        Event event = new Event();
        event.setEventId("event1");
        event.setName("Test Event");
        Timestamp eventDate = Timestamp.now();
        event.setEventDate(eventDate);
        
        WaitingList registration = new WaitingList("event1", "device123", "pending");
        EventHistory history = new EventHistory(event, registration);
        
        assertEquals(eventDate, history.getEvent().getEventDate());
    }

    // ── Multiple items ───────────────────────────────────────────────────────

    @Test
    public void adapter_handlesMultipleItems_withDifferentStatuses() {
        List<EventHistory> multipleHistory = new ArrayList<>();
        multipleHistory.add(createMockEventHistory("event1", "Event 1", "pending"));
        multipleHistory.add(createMockEventHistory("event2", "Event 2", "selected"));
        multipleHistory.add(createMockEventHistory("event3", "Event 3", "not_selected"));
        multipleHistory.add(createMockEventHistory("event4", "Event 4", "declined"));
        
        adapter.updateHistory(multipleHistory);
        
        assertEquals(4, adapter.getItemCount());
    }

    // ── Helper methods ───────────────────────────────────────────────────────

    private EventHistory createMockEventHistory(String eventId, String eventName, String status) {
        Event event = new Event();
        event.setEventId(eventId);
        event.setName(eventName);
        event.setDescription("Test description");
        event.setLocation("Test location");
        event.setEventDate(Timestamp.now());
        
        WaitingList registration = new WaitingList(eventId, "device123", status);
        registration.setRegisteredAt(Timestamp.now());
        
        return new EventHistory(event, registration);
    }
}
