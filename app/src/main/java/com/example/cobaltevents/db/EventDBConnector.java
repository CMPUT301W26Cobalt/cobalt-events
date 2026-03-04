package com.example.cobaltevents.db;

import com.google.firebase.firestore.FirebaseFirestore;

/**
 * Singleton connector that provides access to the Firebase Firestore database.
 * Used by EventDB to obtain the shared Firestore instance.
 */
public class EventDBConnector {

    private static EventDBConnector instance;
    private final FirebaseFirestore firestore;

    private EventDBConnector() {
        this.firestore = FirebaseFirestore.getInstance();
    }

    public static synchronized EventDBConnector getInstance() {
        if (instance == null) {
            instance = new EventDBConnector();
        }
        return instance;
    }

    public FirebaseFirestore getFirestore() {
        return firestore;
    }
}
