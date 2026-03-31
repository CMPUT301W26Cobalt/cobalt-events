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
import com.google.firebase.firestore.WriteBatch;

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
    public static final String ERR_COMMENT_DELETED = "COMMENT_DELETED";

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
        data.put("reactions", new HashMap<String, Object>());
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
        DocumentReference commentRef = db.collection(COLLECTION_EVENTS)
                .document(eventId)
                .collection(SUB_COMMENTS)
                .document(commentId);
        DocumentReference ref = commentRef
                .collection(SUB_REPLIES)
                .document();
        Map<String, Object> data = new HashMap<>();
        data.put("userId", userId != null ? userId : "");
        data.put("userName", userName != null && !userName.isEmpty() ? userName : "You");
        data.put("text", text.trim());
        data.put("reactions", new HashMap<String, Object>());
        data.put("createdAt", FieldValue.serverTimestamp());
        db.runTransaction(trx -> {
            DocumentSnapshot commentSnap = trx.get(commentRef);
            if (commentSnap == null || !commentSnap.exists()) {
                throw new IllegalStateException(ERR_COMMENT_DELETED);
            }
            trx.set(ref, data);
            return ref.getId();
        })
                .addOnSuccessListener(onSuccess)
                .addOnFailureListener(onFailure);
    }

    /**
     * Toggles an emoji reaction on a comment. If the user already reacted with this emoji it is
     * removed; otherwise it is added. Removes the emoji key entirely when the last user un-reacts.
     *
     * @param onSuccess called with the full updated reactions map after the transaction commits.
     */
    public void toggleCommentReaction(String eventId,
                                      String commentId,
                                      String userId,
                                      String emoji,
                                      OnSuccessListener<Map<String, List<String>>> onSuccess,
                                      OnFailureListener onFailure) {
        if (eventId == null || commentId == null || userId == null || userId.trim().isEmpty()
                || emoji == null || emoji.isEmpty()) {
            if (onFailure != null) {
                onFailure.onFailure(new IllegalArgumentException("eventId, commentId, userId, emoji required"));
            }
            return;
        }
        DocumentReference ref = db.collection(COLLECTION_EVENTS)
                .document(eventId)
                .collection(SUB_COMMENTS)
                .document(commentId);
        db.runTransaction(trx -> {
            DocumentSnapshot snap = trx.get(ref);
            Map<String, List<String>> reactions = parseReactions(snap.get("reactions"));
            toggleUserInReaction(reactions, emoji, userId);
            trx.update(ref, "reactions", reactions);
            return reactions;
        }).addOnSuccessListener(onSuccess)
                .addOnFailureListener(onFailure);
    }

    /**
     * Toggles an emoji reaction on a reply.
     *
     * @param onSuccess called with the full updated reactions map after the transaction commits.
     */
    public void toggleReplyReaction(String eventId,
                                    String commentId,
                                    String replyId,
                                    String userId,
                                    String emoji,
                                    OnSuccessListener<Map<String, List<String>>> onSuccess,
                                    OnFailureListener onFailure) {
        if (eventId == null || commentId == null || replyId == null
                || userId == null || userId.trim().isEmpty()
                || emoji == null || emoji.isEmpty()) {
            if (onFailure != null) {
                onFailure.onFailure(new IllegalArgumentException(
                        "eventId, commentId, replyId, userId, emoji required"));
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
            Map<String, List<String>> reactions = parseReactions(snap.get("reactions"));
            toggleUserInReaction(reactions, emoji, userId);
            trx.update(ref, "reactions", reactions);
            return reactions;
        }).addOnSuccessListener(onSuccess)
                .addOnFailureListener(onFailure);
    }

    /** Adds or removes {@code userId} from the emoji bucket; removes empty buckets. */
    private static void toggleUserInReaction(Map<String, List<String>> reactions,
                                             String emoji, String userId) {
        List<String> users = reactions.containsKey(emoji)
                ? new ArrayList<>(reactions.get(emoji)) : new ArrayList<>();
        if (users.contains(userId)) {
            users.remove(userId);
        } else {
            users.add(userId);
        }
        if (users.isEmpty()) {
            reactions.remove(emoji);
        } else {
            reactions.put(emoji, users);
        }
    }

    public void deleteCommentWithReplies(String eventId,
                                         String commentId,
                                         OnSuccessListener<Void> onSuccess,
                                         OnFailureListener onFailure) {
        if (eventId == null || eventId.isEmpty() || commentId == null || commentId.isEmpty()) {
            if (onFailure != null) {
                onFailure.onFailure(new IllegalArgumentException("eventId and commentId required"));
            }
            return;
        }
        DocumentReference commentRef = db.collection(COLLECTION_EVENTS)
                .document(eventId)
                .collection(SUB_COMMENTS)
                .document(commentId);
        commentRef.collection(SUB_REPLIES)
                .get()
                .addOnSuccessListener(replySnap -> {
                    WriteBatch batch = db.batch();
                    if (replySnap != null) {
                        for (DocumentSnapshot replyDoc : replySnap.getDocuments()) {
                            batch.delete(replyDoc.getReference());
                        }
                    }
                    batch.delete(commentRef);
                    batch.commit()
                            .addOnSuccessListener(v -> {
                                if (onSuccess != null) {
                                    onSuccess.onSuccess(null);
                                }
                            })
                            .addOnFailureListener(onFailure);
                })
                .addOnFailureListener(onFailure);
    }

    private static final int BATCH_DELETE_MAX = 500;

    /**
     * Deletes all replies under each comment, then all comment documents, for {@code events/{eventId}/comments}.
     */
    public void deleteAllCommentsAndRepliesForEvent(String eventId,
                                                    OnSuccessListener<Void> onSuccess,
                                                    OnFailureListener onFailure) {
        if (eventId == null || eventId.isEmpty()) {
            if (onSuccess != null) {
                onSuccess.onSuccess(null);
            }
            return;
        }
        db.collection(COLLECTION_EVENTS)
                .document(eventId)
                .collection(SUB_COMMENTS)
                .get()
                .addOnSuccessListener(snap -> {
                    if (snap == null || snap.isEmpty()) {
                        if (onSuccess != null) {
                            onSuccess.onSuccess(null);
                        }
                        return;
                    }
                    deleteRepliesThenCommentDocs(new ArrayList<>(snap.getDocuments()), 0, onSuccess, onFailure);
                })
                .addOnFailureListener(onFailure);
    }

    private void deleteRepliesThenCommentDocs(List<DocumentSnapshot> commentDocs,
                                              int index,
                                              OnSuccessListener<Void> onSuccess,
                                              OnFailureListener onFailure) {
        if (index >= commentDocs.size()) {
            List<DocumentReference> crefList = new ArrayList<>();
            for (DocumentSnapshot c : commentDocs) {
                crefList.add(c.getReference());
            }
            deleteDocumentRefsInBatches(crefList, 0, onSuccess, onFailure);
            return;
        }
        DocumentSnapshot c = commentDocs.get(index);
        c.getReference()
                .collection(SUB_REPLIES)
                .get()
                .addOnSuccessListener(rs -> {
                    List<DocumentReference> rrefs = new ArrayList<>();
                    if (rs != null) {
                        for (DocumentSnapshot r : rs.getDocuments()) {
                            rrefs.add(r.getReference());
                        }
                    }
                    deleteDocumentRefsInBatches(rrefs, 0,
                            unused -> deleteRepliesThenCommentDocs(commentDocs, index + 1, onSuccess, onFailure),
                            onFailure);
                })
                .addOnFailureListener(onFailure);
    }

    private void deleteDocumentRefsInBatches(List<DocumentReference> refs,
                                             int start,
                                             OnSuccessListener<Void> onSuccess,
                                             OnFailureListener onFailure) {
        if (start >= refs.size()) {
            if (onSuccess != null) {
                onSuccess.onSuccess(null);
            }
            return;
        }
        int end = Math.min(start + BATCH_DELETE_MAX, refs.size());
        WriteBatch batch = db.batch();
        for (int i = start; i < end; i++) {
            batch.delete(refs.get(i));
        }
        batch.commit()
                .addOnSuccessListener(v -> deleteDocumentRefsInBatches(refs, end, onSuccess, onFailure))
                .addOnFailureListener(onFailure);
    }

    private static Comment commentFromDoc(DocumentSnapshot doc, String eventId, String viewerUserId) {
        Comment c = new Comment();
        c.setId(doc.getId());
        c.setEventId(eventId);
        c.setUserId(doc.getString("userId"));
        c.setUserName(doc.getString("userName"));
        c.setText(doc.getString("text"));
        c.setReactions(parseReactions(doc.get("reactions")));
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
        r.setReactions(parseReactions(doc.get("reactions")));
        Timestamp ts = doc.getTimestamp("createdAt");
        r.setCreatedAtMillis(ts != null ? ts.toDate().getTime() : 0L);
        return r;
    }

    /** Parses the Firestore reactions map into {@code Map<emoji, List<userId>>}. */
    @SuppressWarnings("unchecked")
    static Map<String, List<String>> parseReactions(Object raw) {
        Map<String, List<String>> out = new HashMap<>();
        if (!(raw instanceof Map)) return out;
        for (Map.Entry<?, ?> entry : ((Map<?, ?>) raw).entrySet()) {
            if (!(entry.getKey() instanceof String)) continue;
            out.put((String) entry.getKey(), asStringList(entry.getValue()));
        }
        return out;
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
