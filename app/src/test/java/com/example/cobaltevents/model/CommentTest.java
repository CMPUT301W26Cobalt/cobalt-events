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
 * Unit tests for the Comment model class.
 */
public class CommentTest {

    private Comment comment;

    @Before
    public void setUp() {
        comment = new Comment();
    }

    // ── Constructor ──────────────────────────────────────────────────────────

    @Test
    public void noArgConstructor_initializesRepliesAsEmptyList() {
        assertNotNull(comment.getReplies());
        assertTrue(comment.getReplies().isEmpty());
    }

    @Test
    public void noArgConstructor_initializesReactionsAsEmptyMap() {
        assertNotNull(comment.getReactions());
        assertTrue(comment.getReactions().isEmpty());
    }

    // ── Setters / getters ────────────────────────────────────────────────────

    @Test
    public void setId_updatesValue() {
        comment.setId("comment-001");
        assertEquals("comment-001", comment.getId());
    }

    @Test
    public void setEventId_updatesValue() {
        comment.setEventId("event-xyz");
        assertEquals("event-xyz", comment.getEventId());
    }

    @Test
    public void setUserId_updatesValue() {
        comment.setUserId("user-123");
        assertEquals("user-123", comment.getUserId());
    }

    @Test
    public void setUserName_updatesValue() {
        comment.setUserName("Alice");
        assertEquals("Alice", comment.getUserName());
    }

    @Test
    public void setText_updatesValue() {
        comment.setText("Great event!");
        assertEquals("Great event!", comment.getText());
    }

    @Test
    public void setCreatedAtMillis_updatesValue() {
        long now = System.currentTimeMillis();
        comment.setCreatedAtMillis(now);
        assertEquals(now, comment.getCreatedAtMillis());
    }

    // ── Reactions ────────────────────────────────────────────────────────────

    @Test
    public void setReactions_updatesMap() {
        Map<String, List<String>> r = new HashMap<>();
        r.put("👍", Arrays.asList("user1", "user2"));
        comment.setReactions(r);
        assertEquals(2, comment.getReactions().get("👍").size());
    }

    @Test
    public void setReactions_null_returnsEmptyMap() {
        comment.setReactions(null);
        assertNotNull(comment.getReactions());
        assertTrue(comment.getReactions().isEmpty());
    }

    @Test
    public void hasReacted_userInList_returnsTrue() {
        Map<String, List<String>> r = new HashMap<>();
        r.put("❤️", Arrays.asList("user1", "user2"));
        comment.setReactions(r);
        assertTrue(comment.hasReacted("❤️", "user1"));
    }

    @Test
    public void hasReacted_userNotInList_returnsFalse() {
        Map<String, List<String>> r = new HashMap<>();
        r.put("❤️", Collections.singletonList("user1"));
        comment.setReactions(r);
        assertFalse(comment.hasReacted("❤️", "user99"));
    }

    @Test
    public void hasReacted_unknownEmoji_returnsFalse() {
        comment.setReactions(new HashMap<>());
        assertFalse(comment.hasReacted("🔥", "user1"));
    }

    @Test
    public void hasReacted_nullEmoji_returnsFalse() {
        assertFalse(comment.hasReacted(null, "user1"));
    }

    @Test
    public void hasReacted_nullUserId_returnsFalse() {
        assertFalse(comment.hasReacted("👍", null));
    }

    @Test
    public void getTotalReactionCount_multipleEmojis() {
        Map<String, List<String>> r = new HashMap<>();
        r.put("👍", Arrays.asList("u1", "u2"));
        r.put("😂", Collections.singletonList("u3"));
        comment.setReactions(r);
        assertEquals(3, comment.getTotalReactionCount());
    }

    @Test
    public void getTotalReactionCount_emptyReactions_returnsZero() {
        comment.setReactions(new HashMap<>());
        assertEquals(0, comment.getTotalReactionCount());
    }

    // ── Replies management ───────────────────────────────────────────────────

    @Test
    public void setReplies_replacesRepliesList() {
        Reply r1 = new Reply(); r1.setId("reply-1");
        Reply r2 = new Reply(); r2.setId("reply-2");
        comment.setReplies(Arrays.asList(r1, r2));
        assertEquals(2, comment.getReplies().size());
        assertEquals("reply-1", comment.getReplies().get(0).getId());
    }

    @Test
    public void setReplies_nullInput_returnsEmptyList() {
        comment.setReplies(null);
        assertNotNull(comment.getReplies());
        assertTrue(comment.getReplies().isEmpty());
    }

    // ── Defaults ─────────────────────────────────────────────────────────────

    @Test
    public void defaults_idIsNull() {
        assertNull(comment.getId());
    }

    @Test
    public void defaults_createdAtIsZero() {
        assertEquals(0L, comment.getCreatedAtMillis());
    }
}
