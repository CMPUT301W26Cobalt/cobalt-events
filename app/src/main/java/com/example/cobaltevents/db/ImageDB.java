package com.example.cobaltevents.db;

import android.net.Uri;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.util.UUID;

/**
 * Handles Firestore and Firebase Storage operations for images.
 */
public class ImageDB {

    private final FirebaseStorage storage;
    private static final String PROFILE_IMAGES_PATH = "profile_images/";

    public ImageDB() {
        this.storage = FirebaseStorage.getInstance();
    }

    public void uploadProfileImage(Uri imageUri, OnSuccessListener<String> onSuccess, OnFailureListener onFailure) {
        String fileName = UUID.randomUUID().toString();
        StorageReference ref = storage.getReference().child(PROFILE_IMAGES_PATH + fileName);

        ref.putFile(imageUri)
                .addOnSuccessListener(taskSnapshot -> ref.getDownloadUrl()
                        .addOnSuccessListener(uri -> onSuccess.onSuccess(uri.toString()))
                        .addOnFailureListener(onFailure))
                .addOnFailureListener(onFailure);
    }

    public void uploadPoster(Uri imageUri, String eventId, OnSuccessListener<String> onSuccess, OnFailureListener onFailure) {
        String fileName = "poster_" + eventId + "_" + UUID.randomUUID().toString();
        StorageReference ref = storage.getReference().child("event_posters/" + fileName);

        ref.putFile(imageUri)
                .addOnSuccessListener(taskSnapshot -> ref.getDownloadUrl()
                        .addOnSuccessListener(uri -> onSuccess.onSuccess(uri.toString()))
                        .addOnFailureListener(onFailure))
                .addOnFailureListener(onFailure);
    }

    public void deleteImageByUrl(String imageUrl, OnSuccessListener<Void> onSuccess, OnFailureListener onFailure) {
        if (imageUrl == null || imageUrl.isEmpty()) {
            onSuccess.onSuccess(null);
            return;
        }
        try {
            StorageReference ref = storage.getReferenceFromUrl(imageUrl);
            ref.delete().addOnSuccessListener(onSuccess).addOnFailureListener(onFailure);
        } catch (Exception e) {
            onFailure.onFailure(e);
        }
    }
}
