package com.example.cobaltevents.model;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Reply to an event comment. Firestore path:
 * {@code events/{eventId}/comments/{commentId}/replies/{replyId}}.
 */
public class Reply {

    private String id;
    private String commentId;
    private String userId;
    private String userName;
    private String text;
    /** Millis since epoch; maps to Firestore Timestamp. */
    private long createdAtMillis;
    /**
     * Emoji reactions: emoji → list of userIds who reacted.
     * Mirrors the same field on {@link Comment}.
     */
    private Map<String, List<String>> reactions;

    public Reply() {
        this.reactions = new HashMap<>();
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getCommentId() { return commentId; }
    public void setCommentId(String commentId) { this.commentId = commentId; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getUserName() { return userName; }
    public void setUserName(String userName) { this.userName = userName; }

    public String getText() { return text; }
    public void setText(String text) { this.text = text; }

    public long getCreatedAtMillis() { return createdAtMillis; }
    public void setCreatedAtMillis(long createdAtMillis) { this.createdAtMillis = createdAtMillis; }

    public Map<String, List<String>> getReactions() {
        return reactions != null ? reactions : new HashMap<>();
    }
    public void setReactions(Map<String, List<String>> reactions) {
        this.reactions = reactions != null ? reactions : new HashMap<>();
    }

    /** Whether the given user has reacted with the given emoji. */
    public boolean hasReacted(String emoji, String userId) {
        if (emoji == null || userId == null) return false;
        List<String> users = getReactions().get(emoji);
        return users != null && users.contains(userId);
    }
}
