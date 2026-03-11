package com.example.cobaltevents.db;

import com.example.cobaltevents.model.WaitingList;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;


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
}
