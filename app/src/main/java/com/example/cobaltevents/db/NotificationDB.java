package com.example.cobaltevents.db;

import com.example.cobaltevents.model.Notification;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.firestore.DocumentChange;
import com.google.firebase.firestore.EventListener;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.WriteBatch;

import java.util.ArrayList;
import java.util.List;

/**
 * Firestore operations for the "notifications" collection.
 * US 01.04.01: save "selected" notifications and listen for new ones.
 * US 01.04.02: save "not_selected" notifications.
 */
public class NotificationDB {

    private static final String COLLECTION = "notifications";
    private final FirebaseFirestore db;

    public NotificationDB() {
        db = FirebaseFirestore.getInstance();
    }

    /** Save a single notification document. */
    public void saveNotification(Notification notification, OnSuccessListener<Void> onSuccess, OnFailureListener onFailure) {
        db.collection(COLLECTION)
                .add(notification)
                .addOnSuccessListener(docRef -> {
                    notification.setId(docRef.getId());
                    onSuccess.onSuccess(null);
                })
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

    /** Fetch all notifications for a recipient, ordered by timestamp descending. */
    public void getNotificationsForRecipient(String deviceId, OnSuccessListener<List<Notification>> onSuccess, OnFailureListener onFailure) {
        db.collection(COLLECTION)
                .whereEqualTo("recipientId", deviceId)
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    List<Notification> notifications = new ArrayList<>();
                    for (var doc : querySnapshot.getDocuments()) {
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

    /** Mark a notification as read. */
    public void markAsRead(String notificationId, OnSuccessListener<Void> onSuccess, OnFailureListener onFailure) {
        db.collection(COLLECTION)
                .document(notificationId)
                .update("read", true)
                .addOnSuccessListener(onSuccess)
                .addOnFailureListener(onFailure);
    }

    /** Real-time listener for new notifications. Returns ListenerRegistration to stop listening. */
    public ListenerRegistration listenForNotifications(String deviceId, EventListener<QuerySnapshot> listener) {
        return db.collection(COLLECTION)
                .whereEqualTo("recipientId", deviceId)
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .addSnapshotListener(listener);
    }
}
