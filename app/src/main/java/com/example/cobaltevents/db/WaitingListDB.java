package com.example.cobaltevents.db;

import com.example.cobaltevents.model.WaitingList;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.WriteBatch;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public class WaitingListDB {

    private final FirebaseFirestore db;
    private static final String COLLECTION_NAME = "waiting_lists";

    public WaitingListDB() {
        this.db = FirebaseFirestore.getInstance();
    }

    public void getEntrantHistory(String deviceId, OnSuccessListener<List<WaitingList>> onSuccess, OnFailureListener onFailure) {
        db.collection(COLLECTION_NAME)
            .whereEqualTo("deviceId", deviceId)
            .get()
            .addOnSuccessListener(querySnapshot -> {
                List<WaitingList> registrations = new ArrayList<>();
                for (QueryDocumentSnapshot doc : querySnapshot) {
                    WaitingList registration = doc.toObject(WaitingList.class);
                    registration.setId(doc.getId());
                    registrations.add(registration);
                }
                onSuccess.onSuccess(registrations);
            })
            .addOnFailureListener(onFailure);
    }

    public void getWaitingListByEvent(String eventId, OnSuccessListener<List<WaitingList>> onSuccess, OnFailureListener onFailure) {
        db.collection(COLLECTION_NAME)
                .whereEqualTo("eventId", eventId)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    List<WaitingList> entries = new ArrayList<>();
                    for (QueryDocumentSnapshot doc : querySnapshot) {
                        WaitingList entry = doc.toObject(WaitingList.class);
                        if (entry != null) {
                            entry.setId(doc.getId());
                            entries.add(entry);
                        }
                    }
                    onSuccess.onSuccess(entries);
                })
                .addOnFailureListener(onFailure);
    }

    public void addRegistration(WaitingList registration, OnSuccessListener<String> onSuccess, OnFailureListener onFailure) {
        db.collection(COLLECTION_NAME)
            .add(registration)
            .addOnSuccessListener(docRef -> onSuccess.onSuccess(docRef.getId()))
            .addOnFailureListener(onFailure);
    }

    public void getActiveRegistrationForEvent(String eventId,
                                              String deviceId,
                                              OnSuccessListener<WaitingList> onSuccess,
                                              OnFailureListener onFailure) {
        db.collection(COLLECTION_NAME)
                .whereEqualTo("eventId", eventId)
                .whereEqualTo("deviceId", deviceId)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    WaitingList active = null;
                    for (QueryDocumentSnapshot doc : querySnapshot) {
                        WaitingList reg = doc.toObject(WaitingList.class);
                        if (reg == null) continue;
                        reg.setId(doc.getId());
                        String status = reg.getStatus();
                        boolean isActive = status == null
                                || (!WaitingList.STATUS_WITHDRAWN.equals(status)
                                && !WaitingList.STATUS_CANCELLED.equals(status));
                        if (isActive) {
                            active = reg;
                            break;
                        }
                    }
                    onSuccess.onSuccess(active);
                })
                .addOnFailureListener(onFailure);
    }

    public void updateStatus(String registrationId, String status, OnSuccessListener<Void> onSuccess, OnFailureListener onFailure) {
        db.collection(COLLECTION_NAME)
            .document(registrationId)
            .update("status", status)
            .addOnSuccessListener(onSuccess)
            .addOnFailureListener(onFailure);
    }

    public void batchUpdateStatuses(Map<String, String> docIdToStatus, OnSuccessListener<Void> onSuccess, OnFailureListener onFailure) {
        WriteBatch batch = db.batch();
        for (Map.Entry<String, String> entry : docIdToStatus.entrySet()) {
            batch.update(db.collection(COLLECTION_NAME).document(entry.getKey()), "status", entry.getValue());
        }
        batch.commit()
                .addOnSuccessListener(onSuccess)
                .addOnFailureListener(onFailure);
    }

    public void getActiveCountForEvent(String eventId,
                                       OnSuccessListener<Integer> onSuccess,
                                       OnFailureListener onFailure) {
        db.collection(COLLECTION_NAME)
                .whereEqualTo("eventId", eventId)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    AtomicInteger count = new AtomicInteger(0);
                    for (QueryDocumentSnapshot doc : querySnapshot) {
                        WaitingList reg = doc.toObject(WaitingList.class);
                        if (reg == null) continue;
                        String status = reg.getStatus();
                        boolean isActive = status == null
                                || (!WaitingList.STATUS_WITHDRAWN.equals(status)
                                && !WaitingList.STATUS_CANCELLED.equals(status));
                        if (isActive) count.incrementAndGet();
                    }
                    onSuccess.onSuccess(count.get());
                })
                .addOnFailureListener(onFailure);
    }

    public void getEntrantsForEvent(String eventId,
                                     OnSuccessListener<List<WaitingList>> onSuccess,
                                     OnFailureListener onFailure) {
        db.collection(COLLECTION_NAME)
                .whereEqualTo("eventId", eventId)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    List<WaitingList> list = new ArrayList<>();
                    for (QueryDocumentSnapshot doc : querySnapshot) {
                        WaitingList wl = doc.toObject(WaitingList.class);
                        wl.setId(doc.getId());
                        list.add(wl);
                    }
                    onSuccess.onSuccess(list);
                })
                .addOnFailureListener(onFailure);
    }

    public void removeUserFromAllWaitlists(String deviceId, OnSuccessListener<Void> onSuccess, OnFailureListener onFailure) {
        db.collection(COLLECTION_NAME)
            .whereEqualTo("deviceId", deviceId)
            .get()
            .addOnSuccessListener(querySnapshot -> {
                if (querySnapshot.isEmpty()) {
                    onSuccess.onSuccess(null);
                    return;
                }
                AtomicInteger pending = new AtomicInteger(querySnapshot.size());
                AtomicReference<Exception> firstError = new AtomicReference<>(null);
                for (QueryDocumentSnapshot doc : querySnapshot) {
                    doc.getReference().delete()
                        .addOnSuccessListener(v -> {
                            if (pending.decrementAndGet() == 0) {
                                if (firstError.get() != null) {
                                    onFailure.onFailure(firstError.get());
                                } else {
                                    onSuccess.onSuccess(null);
                                }
                            }
                        })
                        .addOnFailureListener(e -> {
                            firstError.compareAndSet(null, e);
                            if (pending.decrementAndGet() == 0) {
                                onFailure.onFailure(firstError.get() != null ? firstError.get() : e);
                            }
                        });
                }
            })
            .addOnFailureListener(onFailure);
    }
}
