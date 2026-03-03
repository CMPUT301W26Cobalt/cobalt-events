package com.example.cobaltevents.db;

import com.example.cobaltevents.model.Notification;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.WriteBatch;

import java.util.ArrayList;
import java.util.List;

/**
 * Handles Firestore operations for the "notifications" collection.
 * US 01.04.01 — stores "selected" notifications.
 * US 01.04.02 — stores "not_selected" notifications.
 */
public class NotificationDB {

    private static final String COLLECTION = "notifications";
    private final FirebaseFirestore db;

    public NotificationDB() {
        this.db = FirebaseFirestore.getInstance();
    }

    /**
     * Saves a notification document to Firestore.
     */
    public void saveNotification(Notification notification,
                                 OnSuccessListener<Void> onSuccess,
                                 OnFailureListener onFailure) {
        String docId = db.collection(COLLECTION).document().getId();
        notification.setId(docId);
        db.collection(COLLECTION).document(docId)
                .set(notification)
                .addOnSuccessListener(onSuccess)
                .addOnFailureListener(onFailure);
    }

    /** Batch write multiple notifications (used after lottery draw). */
    public void saveNotifications(List<Notification> notifications, OnSuccessListener<Void> onSuccess, OnFailureListener onFailure) {
        WriteBatch batch = db.batch();
        for (Notification n : notifications) {
            batch.set(db.collection(COLLECTION).document(), n);
        }
        batch.commit()
                .addOnSuccessListener(onSuccess)
                .addOnFailureListener(onFailure);
    }

    /**
     * Fetches all notifications for a given recipient, ordered by timestamp descending.
     */
    public void getNotificationsForRecipient(String recipientId,
                                             OnSuccessListener<List<Notification>> onSuccess,
                                             OnFailureListener onFailure) {
        db.collection(COLLECTION)
                .whereEqualTo("recipientId", recipientId)
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    List<Notification> notifications = new ArrayList<>();
                    for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
                        Notification n = doc.toObject(Notification.class);
                        if (n != null) {
                            n.setId(doc.getId());
                            notifications.add(n);
                        }
                    }
                    onSuccess.onSuccess(notifications);
                })
                .addOnFailureListener(onFailure);
    }

    /**
     * Marks a notification as read.
     */
    public void markAsRead(String notificationId,
                           OnSuccessListener<Void> onSuccess,
                           OnFailureListener onFailure) {
        db.collection(COLLECTION).document(notificationId)
                .update("read", true)
                .addOnSuccessListener(onSuccess)
                .addOnFailureListener(onFailure);
    }

    /**
     * Starts a real-time listener for new unread notifications for a recipient.
     */
    public ListenerRegistration listenForNotifications(String recipientId,
                                                       OnNotificationListener listener) {
        return db.collection(COLLECTION)
                .whereEqualTo("recipientId", recipientId)
                .whereEqualTo("read", false)
                .addSnapshotListener((snapshots, error) -> {
                    if (error != null) {
                        listener.onError(error);
                        return;
                    }
                    if (snapshots != null) {
                        List<Notification> notifications = new ArrayList<>();
                        for (DocumentSnapshot doc : snapshots.getDocuments()) {
                            Notification n = doc.toObject(Notification.class);
                            if (n != null) {
                                n.setId(doc.getId());
                                notifications.add(n);
                            }
                        }
                        listener.onNotifications(notifications);
                    }
                });
    }

    public interface OnNotificationListener {
        void onNotifications(List<Notification> notifications);
        void onError(Exception e);
    }
}
