package com.example.cobaltevents.db;

import com.example.cobaltevents.model.Event;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.List;

/**
 * Handles all Firestore database operations for Event objects.
 * Documents are stored in the "events" collection.
 */
public class EventDB {

    private static final String COLLECTION = "events";
    private final FirebaseFirestore db;

    public EventDB() {
        this.db = EventDBConnector.getInstance().getFirestore();
    }

    public void createEvent(Event event,
                            OnSuccessListener<String> onSuccess,
                            OnFailureListener onFailure) {
        DocumentReference ref = db.collection(COLLECTION).document();
        event.setEventId(ref.getId());
        ref.set(event)
                .addOnSuccessListener(unused -> onSuccess.onSuccess(ref.getId()))
                .addOnFailureListener(onFailure);
    }

    public void getEvent(String eventId,
                         OnSuccessListener<Event> onSuccess,
                         OnFailureListener onFailure) {
        db.collection(COLLECTION)
                .document(eventId)
                .get()
                .addOnSuccessListener(snapshot -> {
                    if (snapshot.exists()) {
                        onSuccess.onSuccess(snapshot.toObject(Event.class));
                    } else {
                        onSuccess.onSuccess(null);
                    }
                })
                .addOnFailureListener(onFailure);
    }

    public void getAllEvents(OnSuccessListener<List<Event>> onSuccess,
                             OnFailureListener onFailure) {
        db.collection(COLLECTION)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    List<Event> events = new ArrayList<>();
                    for (com.google.firebase.firestore.DocumentSnapshot doc : querySnapshot.getDocuments()) {
                        Event e = doc.toObject(Event.class);
                        if (e != null) events.add(e);
                    }
                    onSuccess.onSuccess(events);
                })
                .addOnFailureListener(onFailure);
    }

    public void getEventsByOrganizer(String organizerDeviceId,
                                     OnSuccessListener<List<Event>> onSuccess,
                                     OnFailureListener onFailure) {
        db.collection(COLLECTION)
                .whereEqualTo("organizerDeviceId", organizerDeviceId)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    List<Event> events = new ArrayList<>();
                    for (com.google.firebase.firestore.DocumentSnapshot doc : querySnapshot.getDocuments()) {
                        Event e = doc.toObject(Event.class);
                        if (e != null) events.add(e);
                    }
                    onSuccess.onSuccess(events);
                })
                .addOnFailureListener(onFailure);
    }

    public void updateEvent(Event event,
                            OnSuccessListener<Void> onSuccess,
                            OnFailureListener onFailure) {
        db.collection(COLLECTION)
                .document(event.getEventId())
                .set(event)
                .addOnSuccessListener(onSuccess)
                .addOnFailureListener(onFailure);
    }

    public void deleteEvent(String eventId,
                            OnSuccessListener<Void> onSuccess,
                            OnFailureListener onFailure) {
        db.collection(COLLECTION)
                .document(eventId)
                .delete()
                .addOnSuccessListener(onSuccess)
                .addOnFailureListener(onFailure);
    }
}
