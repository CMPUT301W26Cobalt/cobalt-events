package com.example.cobaltevents.controller;
import android.net.Uri;

import com.example.cobaltevents.db.EventDB;
import com.example.cobaltevents.db.ImageDB;
import com.example.cobaltevents.model.Event;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;


/**
 * Handles Firebase Storage operations for event poster images.
 * Used by ImageController to upload posters for events.
 */
public class ImageController {
    private final ImageDB imageDB;
    private final EventDB eventDB;


    public ImageController() {
        imageDB = new ImageDB();
        eventDB = new EventDB();
    }

    public void uploadPoster(Uri imageUri, Event event,
                             OnSuccessListener<Void> onSuccess,
                             OnFailureListener onFailure) {

        imageDB.uploadPoster(imageUri, event.getEventId(),
                url -> {
                    event.setPosterImageUrl(url);

                    eventDB.updateEvent(event,
                            onSuccess,
                            onFailure);
                },
                onFailure
        );
    }
}
