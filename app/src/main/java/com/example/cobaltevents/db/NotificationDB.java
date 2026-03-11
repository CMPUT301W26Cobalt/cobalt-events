package com.example.cobaltevents.db;

import com.example.cobaltevents.model.Notification;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.ArrayList;
import java.util.List;

public class NotificationDB {

    private static final String COLLECTION = "notifications";
    private final FirebaseFirestore db;

    public NotificationDB() {
        this.db = FirebaseFirestore.getInstance();
    }

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

    public void updateReadStatus(String notificationId, String readStatus,
                                 OnSuccessListener<Void> onSuccess,
                                 OnFailureListener onFailure) {
        db.collection(COLLECTION).document(notificationId)
                .update("read", readStatus)
                .addOnSuccessListener(onSuccess)
                .addOnFailureListener(onFailure);
    }

    public ListenerRegistration listenForNotifications(String recipientId,
                                                       OnNotificationListener listener) {
        return db.collection(COLLECTION)
                .whereEqualTo("recipientId", recipientId)
                .whereEqualTo("read", "pending")
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
