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
                        Event e = snapshot.toObject(Event.class);
                        if (e != null) {
                            e.setEventId(snapshot.getId());
                        }
                        onSuccess.onSuccess(e);
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
                        if (e != null) {
                            e.setEventId(doc.getId());
                            events.add(e);
                        }
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
                        if (e != null) {
                            e.setEventId(doc.getId());
                            events.add(e);
                        }
                    }
                    onSuccess.onSuccess(events);
                })
                .addOnFailureListener(onFailure);
    }

    /**
     * Fetch a single event by its qrCodeData value.
     * Returns the first matching event or null if none found.
     */
    public void getEventByQrCode(String qrCodeData,
                                 OnSuccessListener<Event> onSuccess,
                                 OnFailureListener onFailure) {
        db.collection(COLLECTION)
                .whereEqualTo("qrCodeData", qrCodeData)
                .limit(1)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    if (querySnapshot.isEmpty()) {
                        onSuccess.onSuccess(null);
                        return;
                    }
                    com.google.firebase.firestore.DocumentSnapshot doc = querySnapshot.getDocuments().get(0);
                    Event e = doc.toObject(Event.class);
                    if (e != null) e.setEventId(doc.getId());
                    onSuccess.onSuccess(e);
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
