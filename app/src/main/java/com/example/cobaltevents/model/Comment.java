package com.example.cobaltevents.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Event discussion comment. Stored in Firestore at
 * {@code events/{eventId}/comments/{commentId}} (see {@link com.example.cobaltevents.db.CommentDB}).
 */
public class Comment {

    private String id;
    private String eventId;
    private String userId;
    private String userName;
    private String text;
    /** Millis since epoch; maps to Firestore Timestamp / created_at. */
    private long createdAtMillis;
    /**
     * Emoji reactions: emoji → list of userIds who have reacted with that emoji.
     * Stored in Firestore as a nested map. UI-only state is derived at display time.
     */
    private Map<String, List<String>> reactions;
    private List<Reply> replies;

    public Comment() {
        this.reactions = new HashMap<>();
        this.replies = new ArrayList<>();
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getEventId() { return eventId; }
    public void setEventId(String eventId) { this.eventId = eventId; }

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

    public List<Reply> getReplies() { return replies != null ? replies : new ArrayList<>(); }
    public void setReplies(List<Reply> replies) { this.replies = replies != null ? replies : new ArrayList<>(); }

    /** Total number of reactions across all emojis. */
    public int getTotalReactionCount() {
        int total = 0;
        for (List<String> users : getReactions().values()) {
            if (users != null) total += users.size();
        }
        return total;
    }

    /** Whether the given user has reacted with the given emoji. */
    public boolean hasReacted(String emoji, String userId) {
        if (emoji == null || userId == null) return false;
        List<String> users = getReactions().get(emoji);
        return users != null && users.contains(userId);
    }
}
