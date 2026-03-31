package com.example.cobaltevents.model;

import static org.junit.Assert.*;

import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Unit tests for the Reply model class.
 */
public class ReplyTest {

    private Reply reply;

    @Before
    public void setUp() {
        reply = new Reply();
    }

    // ── Constructor ──────────────────────────────────────────────────────────

    @Test
    public void noArgConstructor_createsInstance() {
        assertNotNull(reply);
    }

    @Test
    public void noArgConstructor_initializesReactionsAsEmptyMap() {
        assertNotNull(reply.getReactions());
        assertTrue(reply.getReactions().isEmpty());
    }

    // ── Setters / getters ────────────────────────────────────────────────────

    @Test
    public void setId_updatesValue() {
        reply.setId("reply-001");
        assertEquals("reply-001", reply.getId());
    }

    @Test
    public void setCommentId_updatesValue() {
        reply.setCommentId("comment-abc");
        assertEquals("comment-abc", reply.getCommentId());
    }

    @Test
    public void setUserId_updatesValue() {
        reply.setUserId("user-999");
        assertEquals("user-999", reply.getUserId());
    }

    @Test
    public void setUserName_updatesValue() {
        reply.setUserName("Bob");
        assertEquals("Bob", reply.getUserName());
    }

    @Test
    public void setText_updatesValue() {
        reply.setText("Thanks for the info!");
        assertEquals("Thanks for the info!", reply.getText());
    }

    @Test
    public void setCreatedAtMillis_updatesValue() {
        long ts = 1_700_000_000_000L;
        reply.setCreatedAtMillis(ts);
        assertEquals(ts, reply.getCreatedAtMillis());
    }

    // ── Reactions ────────────────────────────────────────────────────────────

    @Test
    public void setReactions_updatesMap() {
        Map<String, List<String>> r = new HashMap<>();
        r.put("🔥", Arrays.asList("u1", "u2", "u3"));
        reply.setReactions(r);
        assertEquals(3, reply.getReactions().get("🔥").size());
    }

    @Test
    public void setReactions_null_returnsEmptyMap() {
        reply.setReactions(null);
        assertNotNull(reply.getReactions());
        assertTrue(reply.getReactions().isEmpty());
    }

    @Test
    public void hasReacted_userInList_returnsTrue() {
        Map<String, List<String>> r = new HashMap<>();
        r.put("👍", Collections.singletonList("userA"));
        reply.setReactions(r);
        assertTrue(reply.hasReacted("👍", "userA"));
    }

    @Test
    public void hasReacted_userNotInList_returnsFalse() {
        Map<String, List<String>> r = new HashMap<>();
        r.put("👍", Collections.singletonList("userA"));
        reply.setReactions(r);
        assertFalse(reply.hasReacted("👍", "userB"));
    }

    @Test
    public void hasReacted_nullEmoji_returnsFalse() {
        assertFalse(reply.hasReacted(null, "userA"));
    }

    @Test
    public void hasReacted_nullUserId_returnsFalse() {
        assertFalse(reply.hasReacted("👍", null));
    }

    @Test
    public void hasReacted_missingEmoji_returnsFalse() {
        assertFalse(reply.hasReacted("😂", "userA"));
    }

    // ── Defaults ─────────────────────────────────────────────────────────────

    @Test
    public void defaults_allFieldsNullOrZero() {
        assertNull(reply.getId());
        assertNull(reply.getCommentId());
        assertNull(reply.getUserId());
        assertNull(reply.getUserName());
        assertNull(reply.getText());
        assertEquals(0L, reply.getCreatedAtMillis());
    }
}
