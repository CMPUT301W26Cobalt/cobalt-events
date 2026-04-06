package com.example.cobaltevents.db;

import com.example.cobaltevents.model.Notification;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.firestore.AggregateSource;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.Source;
import com.google.firebase.firestore.WriteBatch;

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
            if (notification.getRecipientMode() == null || notification.getRecipientMode().trim().isEmpty()) {
                notification.setRecipientMode(Notification.RECIPIENT_MODE_USER);
            }
            if (Notification.TYPE_NOT_SELECTED.equals(notification.getType())) {
                notification.setResponse(null);
            } else if (Notification.TYPE_EVENT_ALERT.equals(notification.getType())) {
                notification.setResponse(null);
            } else if (Notification.TYPE_CO_ORGANIZER.equals(notification.getType())
                    && Notification.RECIPIENT_MODE_USER.equals(notification.getRecipientMode())) {
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

    /**
     * Removes in-app lottery / replacement invite notifications for an entrant and event so the
     * notification log no longer shows an active selection invite after the organizer rescinds it.
     * Deletes {@link Notification#TYPE_SELECTED} and {@link Notification#TYPE_GOT_OFF_WAITLIST}
     * with {@link Notification#RECIPIENT_MODE_USER}.
     *
     * @param recipientId entrant device id
     * @param eventId     event the invite referred to
     */
    public void deleteLotteryInviteNotifications(String recipientId,
                                                 String eventId,
                                                 OnSuccessListener<Void> onSuccess,
                                                 OnFailureListener onFailure) {
        if (recipientId == null || recipientId.isEmpty() || eventId == null || eventId.isEmpty()) {
            if (onFailure != null) {
                onFailure.onFailure(new IllegalArgumentException("recipientId and eventId required"));
            }
            return;
        }
        getNotificationsForRecipientAndEvent(recipientId, eventId, notifications -> {
            WriteBatch batch = db.batch();
            int n = 0;
            for (Notification notif : notifications) {
                if (notif == null || notif.getId() == null) continue;
                if (!Notification.RECIPIENT_MODE_USER.equals(notif.getRecipientMode())) continue;
                String t = notif.getType();
                if (Notification.TYPE_SELECTED.equals(t) || Notification.TYPE_GOT_OFF_WAITLIST.equals(t)) {
                    batch.delete(db.collection(COLLECTION).document(notif.getId()));
                    n++;
                }
            }
            if (n == 0) {
                if (onSuccess != null) onSuccess.onSuccess(null);
                return;
            }
            batch.commit()
                    .addOnSuccessListener(v -> {
                        if (onSuccess != null) onSuccess.onSuccess(null);
                    })
                    .addOnFailureListener(e -> {
                        if (onFailure != null) onFailure.onFailure(e);
                    });
        }, onFailure);
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

    /**
     * Count notifications for an event with a given {@code response} (e.g. pending invites).
     * Uses the server only.
     */
    public void countNotificationsForEventAndResponse(String eventId,
                                                      String response,
                                                      OnSuccessListener<Integer> onSuccess,
                                                      OnFailureListener onFailure) {
        if (eventId == null || eventId.isEmpty() || response == null) {
            if (onFailure != null) {
                onFailure.onFailure(new IllegalArgumentException("eventId and response required"));
            }
            return;
        }
        db.collection(COLLECTION)
                .whereEqualTo("eventId", eventId)
                .whereEqualTo("response", response)
                .count()
                .get(AggregateSource.SERVER)
                .addOnSuccessListener(snapshot -> onSuccess.onSuccess((int) snapshot.getCount()))
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

    public void getNotificationsForEventTypeAndMode(String eventId,
                                                    String type,
                                                    String recipientMode,
                                                    OnSuccessListener<List<Notification>> onSuccess,
                                                    OnFailureListener onFailure) {
        db.collection(COLLECTION)
                .whereEqualTo("eventId", eventId)
                .whereEqualTo("type", type)
                .get(Source.SERVER)
                .addOnSuccessListener(querySnapshot -> {
                    List<Notification> notifications = new ArrayList<>();
                    for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
                        Notification n = doc.toObject(Notification.class);
                        if (n != null) {
                            n.setId(doc.getId());
                            if (recipientMode == null || recipientMode.equals(n.getRecipientMode())) {
                                notifications.add(n);
                            }
                        }
                    }
                    onSuccess.onSuccess(notifications);
                })
                .addOnFailureListener(onFailure);
    }

    /**
     * Listen for all notifications for a recipient. Status (pending/accepted/rejected) is
     * sourced from waitlist entry status.
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
