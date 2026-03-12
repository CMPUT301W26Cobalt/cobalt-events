package com.example.cobaltevents.db;

import com.example.cobaltevents.model.Entrant;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
        Map<String, Object> data = new HashMap<>();
        data.put("deviceId", entrant.getDeviceId());
        data.put("name", entrant.getName());
        data.put("email", entrant.getEmail());
        data.put("phone", entrant.getPhone());
        data.put("profilePictureUrl", entrant.getProfilePictureUrl());
        if (entrant.getOrganizerEnabled() != null) {
            data.put("organizerEnabled", entrant.getOrganizerEnabled());
        }
        db.collection(COLLECTION)
                .document(entrant.getDeviceId())
                .set(data, SetOptions.merge())
                .addOnSuccessListener(onSuccess)
                .addOnFailureListener(onFailure);
    }

    public void setOrganizerEnabled(String deviceId, boolean enabled,
                                    OnSuccessListener<Void> onSuccess,
                                    OnFailureListener onFailure) {
        Map<String, Object> update = new HashMap<>();
        update.put("organizerEnabled", enabled);
        db.collection(COLLECTION)
                .document(deviceId)
                .update(update)
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
