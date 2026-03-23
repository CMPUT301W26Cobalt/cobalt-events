package com.example.cobaltevents.model;

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
    private int likes;
    /** Millis since epoch; maps to Firestore Timestamp. */
    private long createdAtMillis;
    /** UI-only: whether current viewer has liked this reply. */
    private boolean likedByCurrentUser;

    public Reply() {}

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

    public int getLikes() { return likes; }
    public void setLikes(int likes) { this.likes = likes; }

    public long getCreatedAtMillis() { return createdAtMillis; }
    public void setCreatedAtMillis(long createdAtMillis) { this.createdAtMillis = createdAtMillis; }

    public boolean isLikedByCurrentUser() { return likedByCurrentUser; }
    public void setLikedByCurrentUser(boolean likedByCurrentUser) { this.likedByCurrentUser = likedByCurrentUser; }
}
