package com.example.cobaltevents.db;

import com.example.cobaltevents.model.Entrant;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.List;

/**
 * Handles all Firestore database operations for Entrant (Profile) objects.
 * Documents are stored in the "profiles" collection using deviceId as the document ID.
 */
public class ProfileDB {

    private static final String COLLECTION = "profiles";
    private final FirebaseFirestore db;

    public ProfileDB() {
        this.db = EventDBConnector.getInstance().getFirestore();
    }

    public void saveProfile(Entrant entrant,
                            OnSuccessListener<Void> onSuccess,
                            OnFailureListener onFailure) {
        if (entrant.getDeviceId() == null) {
            onFailure.onFailure(new Exception("Device ID is null"));
            return;
        }
        db.collection(COLLECTION)
                .document(entrant.getDeviceId())
                .set(entrant)
                .addOnSuccessListener(onSuccess)
                .addOnFailureListener(onFailure);
    }

    public void getProfile(String deviceId,
                           OnSuccessListener<Entrant> onSuccess,
                           OnFailureListener onFailure) {
        db.collection(COLLECTION)
                .document(deviceId)
                .get()
                .addOnSuccessListener(snapshot -> {
                    if (snapshot.exists()) {
                        onSuccess.onSuccess(snapshot.toObject(Entrant.class));
                    } else {
                        onSuccess.onSuccess(null);
                    }
                })
                .addOnFailureListener(onFailure);
    }

    public void getAllProfiles(OnSuccessListener<List<Entrant>> onSuccess,
                               OnFailureListener onFailure) {
        db.collection(COLLECTION)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    List<Entrant> profiles = new ArrayList<>();
                    for (com.google.firebase.firestore.DocumentSnapshot doc : querySnapshot.getDocuments()) {
                        Entrant p = doc.toObject(Entrant.class);
                        if (p != null) profiles.add(p);
                    }
                    onSuccess.onSuccess(profiles);
                })
                .addOnFailureListener(onFailure);
    }

    public void deleteProfile(String deviceId,
                              OnSuccessListener<Void> onSuccess,
                              OnFailureListener onFailure) {
        db.collection(COLLECTION)
                .document(deviceId)
                .delete()
                .addOnSuccessListener(onSuccess)
                .addOnFailureListener(onFailure);
    }
}
