package com.example.cobaltevents.db;

import com.example.cobaltevents.model.Notification;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.Source;

import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.Map;

public class NotificationDB {

    private static final String COLLECTION = "notifications";
    private final FirebaseFirestore db;

    public NotificationDB() {
        this.db = FirebaseFirestore.getInstance();
    }

    public void saveNotification(Notification notification,
                                 OnSuccessListener<String> onSuccess,
                                 OnFailureListener onFailure) {
        if (notification != null) {
            if (Notification.TYPE_NOT_SELECTED.equals(notification.getType())) {
                notification.setResponse(null);
            } else if (notification.getResponse() == null || notification.getResponse().trim().isEmpty()) {
                notification.setResponse(Notification.RESPONSE_PENDING);
            }
        }
        String docId = db.collection(COLLECTION).document().getId();
        notification.setId(docId);
        db.collection(COLLECTION).document(docId)
                .set(notification)
                .addOnSuccessListener(v -> onSuccess.onSuccess(docId))
                .addOnFailureListener(onFailure);
    }

    public void deleteNotification(String notificationId,
                                   OnSuccessListener<Void> onSuccess,
                                   OnFailureListener onFailure) {
        db.collection(COLLECTION).document(notificationId)
                .delete()
                .addOnSuccessListener(onSuccess)
                .addOnFailureListener(onFailure);
    }

    public void updateResponse(String notificationId,
                               String response,
                               OnSuccessListener<Void> onSuccess,
                               OnFailureListener onFailure) {
        if (notificationId == null || notificationId.trim().isEmpty()) {
            onFailure.onFailure(new IllegalArgumentException("notificationId is required"));
            return;
        }
        Map<String, Object> updates = new HashMap<>();
        updates.put("response", response);
        db.collection(COLLECTION).document(notificationId)
                .update(updates)
                .addOnSuccessListener(onSuccess)
                .addOnFailureListener(onFailure);
    }

    public void getNotificationsForRecipient(String recipientId,
                                             OnSuccessListener<List<Notification>> onSuccess,
                                             OnFailureListener onFailure) {
        db.collection(COLLECTION)
                .whereEqualTo("recipientId", recipientId)
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .get(Source.SERVER)
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
     * Fetch notifications for a specific recipient+event pair.
     */
    public void getNotificationsForRecipientAndEvent(String recipientId,
                                                     String eventId,
                                                     OnSuccessListener<List<Notification>> onSuccess,
                                                     OnFailureListener onFailure) {
        db.collection(COLLECTION)
                .whereEqualTo("recipientId", recipientId)
                .whereEqualTo("eventId", eventId)
                .get(Source.SERVER)
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
     * Listen for all notifications for a recipient. Status (pending/accepted/rejected) is
     * sourced from waitlist entry status; merge with WaitingListDB.getWaitlistStatusesForDevice when displaying.
     */
    public ListenerRegistration listenForNotifications(String recipientId,
                                                       OnNotificationListener listener) {
        return db.collection(COLLECTION)
                .whereEqualTo("recipientId", recipientId)
                .orderBy("timestamp", Query.Direction.DESCENDING)
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
