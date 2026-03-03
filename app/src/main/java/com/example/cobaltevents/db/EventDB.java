package com.example.cobaltevents.db;

import com.example.cobaltevents.model.Event;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.List;

/**
 * Firestore operations for the "events" collection.
 * Supporting US 01.04.01: getEvent loads event details when a notification is tapped.
 */
public class EventDB {

    private static final String COLLECTION = "events";
    private final FirebaseFirestore db;

    public EventDB() {
        db = FirebaseFirestore.getInstance();
    }

    /** Fetch a single event by ID. */
    public void getEvent(String eventId, OnSuccessListener<Event> onSuccess, OnFailureListener onFailure) {
        db.collection(COLLECTION)
                .document(eventId)
                .get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        Event event = doc.toObject(Event.class);
                        if (event != null) {
                            event.setEventId(doc.getId());
                        }
                        onSuccess.onSuccess(event);
                    } else {
                        onSuccess.onSuccess(null);
                    }
                })
                .addOnFailureListener(onFailure);
    }

    /** Fetch all events. */
    public void getAllEvents(OnSuccessListener<List<Event>> onSuccess, OnFailureListener onFailure) {
        db.collection(COLLECTION)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    List<Event> events = new ArrayList<>();
                    for (var doc : querySnapshot.getDocuments()) {
                        Event event = doc.toObject(Event.class);
                        if (event != null) {
                            event.setEventId(doc.getId());
                            events.add(event);
                        }
                    }
                    onSuccess.onSuccess(events);
                })
                .addOnFailureListener(onFailure);
    }

    /** Save or update an event. If eventId is null, a new document is created. */
    public void saveEvent(Event event, OnSuccessListener<Void> onSuccess, OnFailureListener onFailure) {
        if (event.getEventId() != null) {
            db.collection(COLLECTION)
                    .document(event.getEventId())
                    .set(event)
                    .addOnSuccessListener(onSuccess)
                    .addOnFailureListener(onFailure);
        } else {
            db.collection(COLLECTION)
                    .add(event)
                    .addOnSuccessListener(docRef -> {
                        event.setEventId(docRef.getId());
                        onSuccess.onSuccess(null);
                    })
                    .addOnFailureListener(onFailure);
        }
    }

    /** Mark an event's lottery as drawn. */
    public void markLotteryDrawn(String eventId, OnSuccessListener<Void> onSuccess, OnFailureListener onFailure) {
        db.collection(COLLECTION)
                .document(eventId)
                .update("lotteryDrawn", true)
                .addOnSuccessListener(onSuccess)
                .addOnFailureListener(onFailure);
    }
}
