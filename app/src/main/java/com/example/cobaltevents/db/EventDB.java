package com.example.cobaltevents.db;

import com.example.cobaltevents.model.Event;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Source;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import androidx.annotation.Nullable;

/**
 * Handles all Firestore database operations for Event objects.
 * Documents are stored in the "events" collection.
 */
public class EventDB {

    private static final String COLLECTION = "events";
    public static final String ERR_LAST_ORGANIZER = "LAST_ORGANIZER";
    private final FirebaseFirestore db;

    public EventDB() {
        this.db = EventDBConnector.getInstance().getFirestore();
    }

    public void createEvent(Event event,
                            OnSuccessListener<String> onSuccess,
                            OnFailureListener onFailure) {
        String presetId = event.getEventId();
        DocumentReference ref;
        if (presetId != null && !presetId.trim().isEmpty()) {
            ref = db.collection(COLLECTION).document(presetId.trim());
        } else {
            ref = db.collection(COLLECTION).document();
            event.setEventId(ref.getId());
        }
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

    /** Latest event document from the server (join / geo validation — avoid stale cache). */
    public void getEventFromServer(String eventId,
                                   OnSuccessListener<Event> onSuccess,
                                   OnFailureListener onFailure) {
        if (eventId == null || eventId.isEmpty()) {
            if (onFailure != null) {
                onFailure.onFailure(new IllegalArgumentException("eventId required"));
            }
            return;
        }
        db.collection(COLLECTION)
                .document(eventId)
                .get(Source.SERVER)
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

    /**
     * Loads the full event catalog from the server so organizer edits
     * (name, private flag, poster, etc.) show up reliably. Falls back to the local
     * Firestore cache only when the server request fails (e.g. offline).
     *
     * @param onUsedLocalCacheFallback optional runnable invoked when serving from cache after a failed server read
     */
    public void getAllEvents(OnSuccessListener<List<Event>> onSuccess,
                             OnFailureListener onFailure) {
        getAllEvents(onSuccess, onFailure, null);
    }

    public void getAllEvents(OnSuccessListener<List<Event>> onSuccess,
                             OnFailureListener onFailure,
                             Runnable onUsedLocalCacheFallback) {
        getAllEventsFromServer(
                onSuccess,
                serverErr -> db.collection(COLLECTION)
                        .get(Source.CACHE)
                        .addOnSuccessListener(cacheSnapshot -> {
                            if (cacheSnapshot != null && !cacheSnapshot.isEmpty()) {
                                if (onUsedLocalCacheFallback != null) {
                                    onUsedLocalCacheFallback.run();
                                }
                                onSuccess.onSuccess(parseEvents(cacheSnapshot));
                            } else if (onFailure != null) {
                                onFailure.onFailure(serverErr);
                            }
                        })
                        .addOnFailureListener(cacheErr -> {
                            if (onFailure != null) {
                                onFailure.onFailure(serverErr);
                            }
                        }));
    }

    private void getAllEventsFromServer(OnSuccessListener<List<Event>> onSuccess,
                                        OnFailureListener onFailure) {
        db.collection(COLLECTION)
                .get(com.google.firebase.firestore.Source.SERVER)
                .addOnSuccessListener(querySnapshot -> onSuccess.onSuccess(parseEvents(querySnapshot)))
                .addOnFailureListener(onFailure);
    }

    private List<Event> parseEvents(com.google.firebase.firestore.QuerySnapshot querySnapshot) {
        List<Event> events = new ArrayList<>();
        for (com.google.firebase.firestore.DocumentSnapshot doc : querySnapshot.getDocuments()) {
            Event e = doc.toObject(Event.class);
            if (e != null) {
                e.setEventId(doc.getId());
                events.add(e);
            }
        }
        return events;
    }

    public void getEventsByOrganizer(String organizerDeviceId,
                                     OnSuccessListener<List<Event>> onSuccess,
                                     OnFailureListener onFailure) {
        db.collection(COLLECTION)
                .whereArrayContains("organizers", organizerDeviceId)
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
     * Events where {@code deviceId} appears in the {@code organizers} array.
     */
    public void getEventsForOrganizerParticipation(String deviceId,
                                                   OnSuccessListener<List<Event>> onSuccess,
                                                   OnFailureListener onFailure) {
        getEventsByOrganizer(deviceId, onSuccess, onFailure);
    }

    /**
     * Fetch a single event by its qrCodeData value.
     * Returns the first matching event or null if none found.
     *
     * @param onUsedLocalCacheFallback optional runnable when serving from cache after a failed server read
     */
    public void getEventByQrCode(String qrCodeData,
                                 OnSuccessListener<Event> onSuccess,
                                 OnFailureListener onFailure) {
        getEventByQrCode(qrCodeData, onSuccess, onFailure, null);
    }

    public void getEventByQrCode(String qrCodeData,
                                 OnSuccessListener<Event> onSuccess,
                                 OnFailureListener onFailure,
                                 Runnable onUsedLocalCacheFallback) {
        com.google.firebase.firestore.Query query = db.collection(COLLECTION)
                .whereEqualTo("qrCodeData", qrCodeData)
                .limit(1);
        query.get(Source.SERVER)
                .addOnSuccessListener(querySnapshot -> emitFirstEventFromQuery(querySnapshot, onSuccess))
                .addOnFailureListener(serverErr -> query.get(Source.CACHE)
                        .addOnSuccessListener(cacheSnap -> {
                            if (onUsedLocalCacheFallback != null
                                    && cacheSnap != null
                                    && !cacheSnap.isEmpty()) {
                                onUsedLocalCacheFallback.run();
                            }
                            emitFirstEventFromQuery(cacheSnap, onSuccess);
                        })
                        .addOnFailureListener(onFailure));
    }

    private static void emitFirstEventFromQuery(com.google.firebase.firestore.QuerySnapshot querySnapshot,
                                               OnSuccessListener<Event> onSuccess) {
        if (querySnapshot == null || querySnapshot.isEmpty()) {
            onSuccess.onSuccess(null);
            return;
        }
        com.google.firebase.firestore.DocumentSnapshot doc = querySnapshot.getDocuments().get(0);
        Event e = doc.toObject(Event.class);
        if (e != null) {
            e.setEventId(doc.getId());
        }
        onSuccess.onSuccess(e);
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

    /**
     * Removes one organizer from {@code organizers} while guaranteeing at least one organizer remains.
     * Transactional to protect against concurrent organizer removals.
     */
    public void removeOrganizerEnsuringAtLeastOne(String eventId,
                                                  String organizerDeviceId,
                                                  OnSuccessListener<Void> onSuccess,
                                                  OnFailureListener onFailure) {
        if (eventId == null || eventId.trim().isEmpty()
                || organizerDeviceId == null || organizerDeviceId.trim().isEmpty()) {
            if (onFailure != null) {
                onFailure.onFailure(new IllegalArgumentException("eventId and organizerDeviceId required"));
            }
            return;
        }
        DocumentReference ref = db.collection(COLLECTION).document(eventId.trim());
        db.runTransaction(trx -> {
            com.google.firebase.firestore.DocumentSnapshot snap = trx.get(ref);
            Object raw = snap.get("organizers");
            List<String> organizers = new ArrayList<>();
            if (raw instanceof List<?>) {
                for (Object item : (List<?>) raw) {
                    if (item instanceof String) {
                        String id = ((String) item).trim();
                        if (!id.isEmpty() && !organizers.contains(id)) {
                            organizers.add(id);
                        }
                    }
                }
            }
            organizers.remove(organizerDeviceId.trim());
            if (organizers.size() < 1) {
                throw new IllegalStateException(ERR_LAST_ORGANIZER);
            }
            trx.update(ref, "organizers", organizers);
            return null;
        }).addOnSuccessListener(unused -> {
                    if (onSuccess != null) {
                        onSuccess.onSuccess(null);
                    }
                })
                .addOnFailureListener(onFailure);
    }
}
