package com.example.cobaltevents.model;

import static org.junit.Assert.*;

import org.junit.Before;
import org.junit.Test;

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
    public void setLikes_updatesValue() {
        reply.setLikes(7);
        assertEquals(7, reply.getLikes());
    }

    @Test
    public void setCreatedAtMillis_updatesValue() {
        long ts = 1_700_000_000_000L;
        reply.setCreatedAtMillis(ts);
        assertEquals(ts, reply.getCreatedAtMillis());
    }

    @Test
    public void setLikedByCurrentUser_toggles() {
        reply.setLikedByCurrentUser(true);
        assertTrue(reply.isLikedByCurrentUser());
        reply.setLikedByCurrentUser(false);
        assertFalse(reply.isLikedByCurrentUser());
    }

    // ── Defaults ─────────────────────────────────────────────────────────────

    @Test
    public void defaults_likesIsZero() {
        assertEquals(0, reply.getLikes());
    }

    @Test
    public void defaults_likedByCurrentUserIsFalse() {
        assertFalse(reply.isLikedByCurrentUser());
    }

    @Test
    public void defaults_allFieldsNullOrZero() {
        assertNull(reply.getId());
        assertNull(reply.getCommentId());
        assertNull(reply.getUserId());
        assertNull(reply.getUserName());
        assertNull(reply.getText());
        assertEquals(0, reply.getLikes());
        assertEquals(0L, reply.getCreatedAtMillis());
    }
}
