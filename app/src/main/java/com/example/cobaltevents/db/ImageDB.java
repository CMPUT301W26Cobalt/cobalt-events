package com.example.cobaltevents.db;


/**
 * Handles Firebase Storage operations for event poster images.
 * Used by ImageController to upload posters for events.
 */

import android.net.Uri;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
public class ImageDB {

    //Firestore instance
    private final FirebaseStorage storage;
    public ImageDB() {
        storage = FirebaseStorage.getInstance();
    }


    public void uploadPoster(Uri imageUri, String eventId,
                             OnSuccessListener<String> onSuccess,
                             OnFailureListener onFailure) {

        StorageReference ref = storage
                .getReference()
                .child("event_posters/" + eventId + ".jpg");

        ref.putFile(imageUri)
                .continueWithTask(task -> ref.getDownloadUrl())
                .addOnSuccessListener(uri -> onSuccess.onSuccess(uri.toString()))
                .addOnFailureListener(onFailure);
    }
}


