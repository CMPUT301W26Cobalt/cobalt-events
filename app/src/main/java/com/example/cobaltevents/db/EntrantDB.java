package com.example.cobaltevents.db;

import com.example.cobaltevents.model.Entrant;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.firestore.FirebaseFirestore;

/**
 * Firestore operations for the "entrants" collection.
 * US 01.04.03: updateNotificationPreference for opt-out toggle.
 * Supporting US 01.04.01/02: getEntrant to check opt-out before sending notifications.
 */
public class EntrantDB {

    private static final String COLLECTION = "entrants";
    private final FirebaseFirestore db;

    public EntrantDB() {
        db = FirebaseFirestore.getInstance();
    }

    /** Create or update an entrant profile using deviceId as the document ID. */
    public void saveEntrant(Entrant entrant, OnSuccessListener<Void> onSuccess, OnFailureListener onFailure) {
        db.collection(COLLECTION)
                .document(entrant.getDeviceId())
                .set(entrant)
                .addOnSuccessListener(onSuccess)
                .addOnFailureListener(onFailure);
    }

    /** Fetch an entrant by device ID. Returns null to onSuccess if not found. */
    public void getEntrant(String deviceId, OnSuccessListener<Entrant> onSuccess, OnFailureListener onFailure) {
        db.collection(COLLECTION)
                .document(deviceId)
                .get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        Entrant entrant = doc.toObject(Entrant.class);
                        onSuccess.onSuccess(entrant);
                    } else {
                        onSuccess.onSuccess(null);
                    }
                })
                .addOnFailureListener(onFailure);
    }

    /** Update only the notificationsEnabled field (US 01.04.03). */
    public void updateNotificationPreference(String deviceId, boolean enabled, OnSuccessListener<Void> onSuccess, OnFailureListener onFailure) {
        db.collection(COLLECTION)
                .document(deviceId)
                .update("notificationsEnabled", enabled)
                .addOnSuccessListener(onSuccess)
                .addOnFailureListener(onFailure);
    }
}
