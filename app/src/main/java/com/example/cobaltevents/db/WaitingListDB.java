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

/**
 * Handles Firestore operations for waiting list registrations.
 */
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

    /** Fetch all waiting list entries for a specific event. */
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

    public void updateStatus(String registrationId, String status, OnSuccessListener<Void> onSuccess, OnFailureListener onFailure) {
        db.collection(COLLECTION_NAME)
            .document(registrationId)
            .update("status", status)
            .addOnSuccessListener(onSuccess)
            .addOnFailureListener(onFailure);
    }

    /** Batch update statuses for multiple waiting list entries (used after lottery draw). */
    public void batchUpdateStatuses(Map<String, String> docIdToStatus, OnSuccessListener<Void> onSuccess, OnFailureListener onFailure) {
        WriteBatch batch = db.batch();
        for (Map.Entry<String, String> entry : docIdToStatus.entrySet()) {
            batch.update(db.collection(COLLECTION_NAME).document(entry.getKey()), "status", entry.getValue());
        }
        batch.commit()
                .addOnSuccessListener(onSuccess)
                .addOnFailureListener(onFailure);
    }
}
