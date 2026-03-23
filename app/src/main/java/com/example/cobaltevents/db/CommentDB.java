package com.example.cobaltevents.db;

import com.example.cobaltevents.model.Comment;
import com.example.cobaltevents.model.Reply;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QuerySnapshot;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Firestore-backed comments and replies for events.
 * <p>
 * Schema (mirrors the SQL design, mapped to Firestore):
 * <ul>
 *   <li>{@code events/{eventId}/comments/{commentId}} — userId, userName, text, likes, createdAt</li>
 *   <li>{@code events/{eventId}/comments/{commentId}/replies/{replyId}} — same fields (no eventId on doc)</li>
 * </ul>
 */
public class CommentDB {

    private static final String COLLECTION_EVENTS = "events";
    private static final String SUB_COMMENTS = "comments";
    private static final String SUB_REPLIES = "replies";

    private final FirebaseFirestore db;

    public CommentDB() {
        this.db = EventDBConnector.getInstance().getFirestore();
    }

    /**
     * Loads all comments for an event (newest first) and nests replies (oldest first).
     */
    public void loadCommentsForEvent(String eventId,
                                     String viewerUserId,
                                     OnSuccessListener<List<Comment>> onSuccess,
                                     OnFailureListener onFailure) {
        if (eventId == null || eventId.isEmpty()) {
            onSuccess.onSuccess(new ArrayList<>());
            return;
        }
        db.collection(COLLECTION_EVENTS)
                .document(eventId)
                .collection(SUB_COMMENTS)
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .get()
                .addOnSuccessListener(snapshot -> {
                    if (snapshot == null || snapshot.isEmpty()) {
                        onSuccess.onSuccess(new ArrayList<>());
                        return;
                    }
                    List<DocumentSnapshot> commentDocs = snapshot.getDocuments();
                    List<Task<QuerySnapshot>> replyTasks = new ArrayList<>();
                    for (DocumentSnapshot d : commentDocs) {
                        replyTasks.add(d.getReference().collection(SUB_REPLIES)
                                .orderBy("createdAt", Query.Direction.ASCENDING)
                                .get());
                    }
                    Tasks.whenAllComplete(replyTasks)
                            .addOnSuccessListener(unused -> {
                                try {
                                    List<Comment> out = new ArrayList<>();
                                    for (int i = 0; i < commentDocs.size(); i++) {
                                        DocumentSnapshot d = commentDocs.get(i);
                                        Comment c = commentFromDoc(d, eventId, viewerUserId);
                                        Task<QuerySnapshot> rt = replyTasks.get(i);
                                        if (rt.isSuccessful() && rt.getResult() != null) {
                                            for (DocumentSnapshot rd : rt.getResult().getDocuments()) {
                                                c.getReplies().add(replyFromDoc(rd, c.getId(), viewerUserId));
                                            }
                                        }
                                        out.add(c);
                                    }
                                    onSuccess.onSuccess(out);
                                } catch (Exception e) {
                                    if (onFailure != null) {
                                        onFailure.onFailure(e);
                                    }
                                }
                            })
                            .addOnFailureListener(e -> {
                                if (onFailure != null) {
                                    onFailure.onFailure(e);
                                }
                            });
                })
                .addOnFailureListener(onFailure);
    }

    public void addComment(String eventId,
                           String userId,
                           String userName,
                           String text,
                           OnSuccessListener<String> onSuccess,
                           OnFailureListener onFailure) {
        if (eventId == null || eventId.isEmpty() || text == null || text.trim().isEmpty()) {
            if (onFailure != null) {
                onFailure.onFailure(new IllegalArgumentException("eventId and text required"));
            }
            return;
        }
        DocumentReference ref = db.collection(COLLECTION_EVENTS)
                .document(eventId)
                .collection(SUB_COMMENTS)
                .document();
        Map<String, Object> data = new HashMap<>();
        data.put("userId", userId != null ? userId : "");
        data.put("userName", userName != null && !userName.isEmpty() ? userName : "You");
        data.put("text", text.trim());
        data.put("likes", 0L);
        data.put("likedByIds", new ArrayList<String>());
        data.put("createdAt", FieldValue.serverTimestamp());
        ref.set(data)
                .addOnSuccessListener(v -> onSuccess.onSuccess(ref.getId()))
                .addOnFailureListener(onFailure);
    }

    public void addReply(String eventId,
                         String commentId,
                         String userId,
                         String userName,
                         String text,
                         OnSuccessListener<String> onSuccess,
                         OnFailureListener onFailure) {
        if (eventId == null || commentId == null || text == null || text.trim().isEmpty()) {
            if (onFailure != null) {
                onFailure.onFailure(new IllegalArgumentException("eventId, commentId, text required"));
            }
            return;
        }
        DocumentReference ref = db.collection(COLLECTION_EVENTS)
                .document(eventId)
                .collection(SUB_COMMENTS)
                .document(commentId)
                .collection(SUB_REPLIES)
                .document();
        Map<String, Object> data = new HashMap<>();
        data.put("userId", userId != null ? userId : "");
        data.put("userName", userName != null && !userName.isEmpty() ? userName : "You");
        data.put("text", text.trim());
        data.put("likes", 0L);
        data.put("likedByIds", new ArrayList<String>());
        data.put("createdAt", FieldValue.serverTimestamp());
        ref.set(data)
                .addOnSuccessListener(v -> onSuccess.onSuccess(ref.getId()))
                .addOnFailureListener(onFailure);
    }

    public void toggleCommentLike(String eventId,
                                  String commentId,
                                  String userId,
                                  OnSuccessListener<Boolean> onSuccess,
                                  OnFailureListener onFailure) {
        if (eventId == null || commentId == null || userId == null || userId.trim().isEmpty()) {
            if (onFailure != null) {
                onFailure.onFailure(new IllegalArgumentException("eventId, commentId, userId required"));
            }
            return;
        }
        DocumentReference ref = db.collection(COLLECTION_EVENTS)
                .document(eventId)
                .collection(SUB_COMMENTS)
                .document(commentId);
        db.runTransaction(trx -> {
            DocumentSnapshot snap = trx.get(ref);
            List<String> likedBy = asStringList(snap.get("likedByIds"));
            boolean nowLiked;
            if (likedBy.contains(userId)) {
                likedBy.remove(userId);
                nowLiked = false;
            } else {
                likedBy.add(userId);
                nowLiked = true;
            }
            trx.update(ref, "likedByIds", likedBy, "likes", likedBy.size());
            return nowLiked;
        }).addOnSuccessListener(onSuccess)
                .addOnFailureListener(onFailure);
    }

    public void toggleReplyLike(String eventId,
                                String commentId,
                                String replyId,
                                String userId,
                                OnSuccessListener<Boolean> onSuccess,
                                OnFailureListener onFailure) {
        if (eventId == null || commentId == null || replyId == null || userId == null || userId.trim().isEmpty()) {
            if (onFailure != null) {
                onFailure.onFailure(new IllegalArgumentException("eventId, commentId, replyId, userId required"));
            }
            return;
        }
        DocumentReference ref = db.collection(COLLECTION_EVENTS)
                .document(eventId)
                .collection(SUB_COMMENTS)
                .document(commentId)
                .collection(SUB_REPLIES)
                .document(replyId);
        db.runTransaction(trx -> {
            DocumentSnapshot snap = trx.get(ref);
            List<String> likedBy = asStringList(snap.get("likedByIds"));
            boolean nowLiked;
            if (likedBy.contains(userId)) {
                likedBy.remove(userId);
                nowLiked = false;
            } else {
                likedBy.add(userId);
                nowLiked = true;
            }
            trx.update(ref, "likedByIds", likedBy, "likes", likedBy.size());
            return nowLiked;
        }).addOnSuccessListener(onSuccess)
                .addOnFailureListener(onFailure);
    }

    private static Comment commentFromDoc(DocumentSnapshot doc, String eventId, String viewerUserId) {
        Comment c = new Comment();
        c.setId(doc.getId());
        c.setEventId(eventId);
        c.setUserId(doc.getString("userId"));
        c.setUserName(doc.getString("userName"));
        c.setText(doc.getString("text"));
        Long likes = doc.getLong("likes");
        c.setLikes(likes != null ? likes.intValue() : 0);
        c.setLikedByCurrentUser(containsUserId(doc.get("likedByIds"), viewerUserId));
        Timestamp ts = doc.getTimestamp("createdAt");
        c.setCreatedAtMillis(ts != null ? ts.toDate().getTime() : 0L);
        c.setReplies(new ArrayList<>());
        return c;
    }

    private static Reply replyFromDoc(DocumentSnapshot doc, String commentId, String viewerUserId) {
        Reply r = new Reply();
        r.setId(doc.getId());
        r.setCommentId(commentId);
        r.setUserId(doc.getString("userId"));
        r.setUserName(doc.getString("userName"));
        r.setText(doc.getString("text"));
        Long likes = doc.getLong("likes");
        r.setLikes(likes != null ? likes.intValue() : 0);
        r.setLikedByCurrentUser(containsUserId(doc.get("likedByIds"), viewerUserId));
        Timestamp ts = doc.getTimestamp("createdAt");
        r.setCreatedAtMillis(ts != null ? ts.toDate().getTime() : 0L);
        return r;
    }

    private static boolean containsUserId(Object raw, String userId) {
        if (userId == null || userId.trim().isEmpty()) {
            return false;
        }
        return asStringList(raw).contains(userId);
    }

    @SuppressWarnings("unchecked")
    private static List<String> asStringList(Object raw) {
        if (!(raw instanceof List)) {
            return new ArrayList<>();
        }
        List<?> values = (List<?>) raw;
        if (values.isEmpty()) {
            return new ArrayList<>();
        }
        List<String> out = new ArrayList<>(values.size());
        for (Object v : values) {
            if (v instanceof String) {
                out.add((String) v);
            }
        }
        return out.isEmpty() ? new ArrayList<>() : out;
    }
}
