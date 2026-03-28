package com.example.cobaltevents.ui.adapter;

import static org.junit.Assert.*;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.example.cobaltevents.model.Notification;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.lang.reflect.Method;

/**
 * Tests the private helper methods of {@link NotificationListAdapter} via reflection.
 *
 * The adapter extends {@code RecyclerView.Adapter}, so these run as instrumented tests.
 * The three helpers under test are pure string/type logic with no Android context dependency.
 *
 * Methods covered:
 *  - {@code normalizeResponse(String)}    – lowercase + null-on-blank
 *  - {@code isPrivateEventType(String)}   – case-insensitive private-event detection
 *  - {@code isActionableType(Notification)} – determines if Accept/Decline buttons appear
 */
@RunWith(AndroidJUnit4.class)
public class NotificationListAdapterLogicTest {

    private NotificationListAdapter adapter;
    private Method normalizeResponse;
    private Method isPrivateEventType;
    private Method isActionableType;

    @Before
    public void setUp() throws Exception {
        adapter = new NotificationListAdapter();

        normalizeResponse = NotificationListAdapter.class
                .getDeclaredMethod("normalizeResponse", String.class);
        normalizeResponse.setAccessible(true);

        isPrivateEventType = NotificationListAdapter.class
                .getDeclaredMethod("isPrivateEventType", String.class);
        isPrivateEventType.setAccessible(true);

        isActionableType = NotificationListAdapter.class
                .getDeclaredMethod("isActionableType", Notification.class);
        isActionableType.setAccessible(true);
    }

    // ── normalizeResponse ────────────────────────────────────────────────────

    @Test
    public void normalizeResponse_null_returnsNull() throws Exception {
        assertNull(normalizeResponse.invoke(adapter, (Object) null));
    }

    @Test
    public void normalizeResponse_emptyString_returnsNull() throws Exception {
        assertNull(normalizeResponse.invoke(adapter, ""));
    }

    @Test
    public void normalizeResponse_blankString_returnsNull() throws Exception {
        assertNull(normalizeResponse.invoke(adapter, "   "));
    }

    @Test
    public void normalizeResponse_uppercasePending_returnsLowercase() throws Exception {
        assertEquals("pending", normalizeResponse.invoke(adapter, "PENDING"));
    }

    @Test
    public void normalizeResponse_mixedCase_returnsLowercase() throws Exception {
        assertEquals("accepted", normalizeResponse.invoke(adapter, "Accepted"));
    }

    @Test
    public void normalizeResponse_alreadyLowercase_unchanged() throws Exception {
        assertEquals("declined", normalizeResponse.invoke(adapter, "declined"));
    }

    // ── isPrivateEventType ────────────────────────────────────────────────────

    @Test
    public void isPrivateEventType_null_returnsFalse() throws Exception {
        assertFalse((boolean) isPrivateEventType.invoke(adapter, (Object) null));
    }

    @Test
    public void isPrivateEventType_exactConstant_returnsTrue() throws Exception {
        assertTrue((boolean) isPrivateEventType.invoke(adapter, Notification.TYPE_PRIVATE_EVENT));
    }

    @Test
    public void isPrivateEventType_uppercase_returnsTrue() throws Exception {
        assertTrue((boolean) isPrivateEventType.invoke(adapter, "PRIVATE-EVENT"));
    }

    @Test
    public void isPrivateEventType_shortVariant_returnsTrue() throws Exception {
        assertTrue((boolean) isPrivateEventType.invoke(adapter, "private"));
    }

    @Test
    public void isPrivateEventType_underscoreVariant_returnsTrue() throws Exception {
        assertTrue((boolean) isPrivateEventType.invoke(adapter, "private_event"));
    }

    @Test
    public void isPrivateEventType_unrelatedType_returnsFalse() throws Exception {
        assertFalse((boolean) isPrivateEventType.invoke(adapter, Notification.TYPE_SELECTED));
    }

    @Test
    public void isPrivateEventType_emptyString_returnsFalse() throws Exception {
        assertFalse((boolean) isPrivateEventType.invoke(adapter, ""));
    }

    // ── isActionableType ─────────────────────────────────────────────────────

    @Test
    public void isActionableType_null_returnsFalse() throws Exception {
        assertFalse((boolean) isActionableType.invoke(adapter, (Object) null));
    }

    @Test
    public void isActionableType_nullType_returnsFalse() throws Exception {
        Notification n = new Notification();
        // type is null
        assertFalse((boolean) isActionableType.invoke(adapter, n));
    }

    @Test
    public void isActionableType_selectedType_returnsTrue() throws Exception {
        Notification n = new Notification("r", "e", "t", "m", Notification.TYPE_SELECTED);
        assertTrue((boolean) isActionableType.invoke(adapter, n));
    }

    @Test
    public void isActionableType_gotOffWaitlistType_returnsTrue() throws Exception {
        Notification n = new Notification("r", "e", "t", "m", Notification.TYPE_GOT_OFF_WAITLIST);
        assertTrue((boolean) isActionableType.invoke(adapter, n));
    }

    @Test
    public void isActionableType_privateEventType_returnsTrue() throws Exception {
        Notification n = new Notification("r", "e", "t", "m", Notification.TYPE_PRIVATE_EVENT);
        assertTrue((boolean) isActionableType.invoke(adapter, n));
    }

    @Test
    public void isActionableType_coOrganizerInOrganizerMode_returnsTrue() throws Exception {
        Notification n = new Notification("r", "e", "t", "m", Notification.TYPE_CO_ORGANIZER);
        n.setRecipientMode(Notification.RECIPIENT_MODE_ORGANIZER);
        assertTrue((boolean) isActionableType.invoke(adapter, n));
    }

    @Test
    public void isActionableType_coOrganizerInUserMode_returnsFalse() throws Exception {
        Notification n = new Notification("r", "e", "t", "m", Notification.TYPE_CO_ORGANIZER);
        n.setRecipientMode(Notification.RECIPIENT_MODE_USER);
        assertFalse((boolean) isActionableType.invoke(adapter, n));
    }

    @Test
    public void isActionableType_notSelectedType_returnsFalse() throws Exception {
        Notification n = new Notification("r", "e", "t", "m", Notification.TYPE_NOT_SELECTED);
        assertFalse((boolean) isActionableType.invoke(adapter, n));
    }

    @Test
    public void isActionableType_eventAlertType_returnsFalse() throws Exception {
        Notification n = new Notification("r", "e", "t", "m", Notification.TYPE_EVENT_ALERT);
        assertFalse((boolean) isActionableType.invoke(adapter, n));
    }
}
