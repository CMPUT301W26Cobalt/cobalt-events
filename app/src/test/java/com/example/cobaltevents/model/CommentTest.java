package com.example.cobaltevents.model;

import static org.junit.Assert.*;

import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

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
    public void setLikes_updatesValue() {
        comment.setLikes(42);
        assertEquals(42, comment.getLikes());
    }

    @Test
    public void setCreatedAtMillis_updatesValue() {
        long now = System.currentTimeMillis();
        comment.setCreatedAtMillis(now);
        assertEquals(now, comment.getCreatedAtMillis());
    }

    @Test
    public void setLikedByCurrentUser_toggles() {
        comment.setLikedByCurrentUser(true);
        assertTrue(comment.isLikedByCurrentUser());
        comment.setLikedByCurrentUser(false);
        assertFalse(comment.isLikedByCurrentUser());
    }

    // ── Replies management ───────────────────────────────────────────────────

    @Test
    public void setReplies_replacesRepliesList() {
        Reply r1 = new Reply();
        r1.setId("reply-1");
        Reply r2 = new Reply();
        r2.setId("reply-2");

        comment.setReplies(Arrays.asList(r1, r2));
        assertEquals(2, comment.getReplies().size());
        assertEquals("reply-1", comment.getReplies().get(0).getId());
        assertEquals("reply-2", comment.getReplies().get(1).getId());
    }

    @Test
    public void setReplies_nullInput_returnsEmptyList() {
        comment.setReplies(null);
        assertNotNull(comment.getReplies());
        assertTrue(comment.getReplies().isEmpty());
    }

    @Test
    public void getReplies_afterSetNull_returnsEmptyList() {
        comment.setReplies(null);
        List<Reply> replies = comment.getReplies();
        assertNotNull(replies);
        assertTrue(replies.isEmpty());
    }

    // ── Defaults ─────────────────────────────────────────────────────────────

    @Test
    public void defaults_likesIsZero() {
        assertEquals(0, comment.getLikes());
    }

    @Test
    public void defaults_likedByCurrentUserIsFalse() {
        assertFalse(comment.isLikedByCurrentUser());
    }

    @Test
    public void defaults_idIsNull() {
        assertNull(comment.getId());
    }
}
