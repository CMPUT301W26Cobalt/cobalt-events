package com.example.cobaltevents.db;

import com.example.cobaltevents.model.WaitingList;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.WriteBatch;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Firestore operations for the "waitingList" collection.
 * Supporting US 01.04.01/02: lottery draw reads waiting list entries and updates statuses,
 * which determines whether "selected" or "not_selected" notifications are sent.
 */
public class WaitingListDB {

    private static final String COLLECTION = "waitingList";
    private final FirebaseFirestore db;

    public WaitingListDB() {
        db = FirebaseFirestore.getInstance();
    }

    /** Fetch all waiting list entries for a specific event. */
    public void getWaitingListByEvent(String eventId, OnSuccessListener<List<WaitingList>> onSuccess, OnFailureListener onFailure) {
        db.collection(COLLECTION)
                .whereEqualTo("eventId", eventId)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    List<WaitingList> entries = new ArrayList<>();
                    for (var doc : querySnapshot.getDocuments()) {
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

    /** Add an entrant to a waiting list. */
    public void addToWaitingList(WaitingList entry, OnSuccessListener<Void> onSuccess, OnFailureListener onFailure) {
        db.collection(COLLECTION)
                .add(entry)
                .addOnSuccessListener(docRef -> {
                    entry.setId(docRef.getId());
                    onSuccess.onSuccess(null);
                })
                .addOnFailureListener(onFailure);
    }

    /** Batch update statuses for multiple waiting list entries (used after lottery draw). */
    public void batchUpdateStatuses(Map<String, String> docIdToStatus, OnSuccessListener<Void> onSuccess, OnFailureListener onFailure) {
        WriteBatch batch = db.batch();
        for (Map.Entry<String, String> entry : docIdToStatus.entrySet()) {
            batch.update(db.collection(COLLECTION).document(entry.getKey()), "status", entry.getValue());
        }
        batch.commit()
                .addOnSuccessListener(onSuccess)
                .addOnFailureListener(onFailure);
    }
}
