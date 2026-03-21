package com.example.cobaltevents.model;

import java.util.ArrayList;
import java.util.List;

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
    private int likes;
    /** Millis since epoch; maps to Firestore Timestamp / created_at. */
    private long createdAtMillis;
    /** UI-only: whether current viewer has liked this comment. */
    private boolean likedByCurrentUser;
    private List<Reply> replies;

    public Comment() {
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

    public int getLikes() { return likes; }
    public void setLikes(int likes) { this.likes = likes; }

    public long getCreatedAtMillis() { return createdAtMillis; }
    public void setCreatedAtMillis(long createdAtMillis) { this.createdAtMillis = createdAtMillis; }

    public boolean isLikedByCurrentUser() { return likedByCurrentUser; }
    public void setLikedByCurrentUser(boolean likedByCurrentUser) { this.likedByCurrentUser = likedByCurrentUser; }

    public List<Reply> getReplies() { return replies != null ? replies : new ArrayList<>(); }
    public void setReplies(List<Reply> replies) { this.replies = replies != null ? replies : new ArrayList<>(); }
}
