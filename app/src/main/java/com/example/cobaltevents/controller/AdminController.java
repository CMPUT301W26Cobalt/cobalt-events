package com.example.cobaltevents.controller;

import com.example.cobaltevents.db.EventDB;
import com.example.cobaltevents.db.ProfileDB;
import com.example.cobaltevents.model.Entrant;
import com.example.cobaltevents.model.Event;
import com.example.cobaltevents.model.Notification;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.WriteBatch;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Controller for all Admin operations.
 *
 * Performance optimizations:
 *  - In-memory cache for events and profiles (invalidated on mutation)
 *  - Images reuse the event cache instead of a second Firestore fetch
 *  - Organizers reuse both caches — zero extra Firestore fetches
 *  - Targeted whereEqualTo queries instead of full collection scans
 *  - Batch deletes use Firestore WriteBatch (one round trip)
 *
 * US 03.01.01 — Remove events
 * US 03.02.01 — Remove profiles
 * US 03.03.01 — Remove images
 * US 03.04.01 — Browse events
 * US 03.05.01 — Browse profiles
 * US 03.06.01 — Browse images
 * US 03.07.01 — Remove organizers
 * US 03.08.01 — Review notification logs
 */
public class AdminController {

    private final EventDB eventDB;
    private final ProfileDB profileDB;
    private final FirebaseFirestore db;
    private List<Event>   cachedEvents   = null;
    private List<Entrant> cachedProfiles = null;

    public AdminController() {
        this.eventDB     = new EventDB();
        this.profileDB   = new ProfileDB();
        this.db          = FirebaseFirestore.getInstance();
    }


    private void invalidateEvents()   { cachedEvents   = null; }
    private void invalidateProfiles() { cachedProfiles = null; }

    // US 03.04.01 — Browse events

    public void getAllEvents(OnSuccessListener<List<Event>> onSuccess,
                             OnFailureListener onFailure) {
        if (cachedEvents != null) {
            onSuccess.onSuccess(cachedEvents);
            return;
        }
        db.collection("events")
                .get()
                .addOnSuccessListener(snapshot -> {
                    List<Event> events = new ArrayList<>();
                    for (DocumentSnapshot doc : snapshot.getDocuments()) {
                        Event e = doc.toObject(Event.class);
                        if (e != null) {
                            if (e.getEventId() == null || e.getEventId().trim().isEmpty())
                                e.setEventId(doc.getId());
                            events.add(e);
                        }
                    }
                    cachedEvents = events;
                    onSuccess.onSuccess(events);
                })
                .addOnFailureListener(onFailure);
    }

    // US 03.01.01 — Remove events

    public void removeEvent(String eventId,
                            OnSuccessListener<Void> onSuccess,
                            OnFailureListener onFailure) {
        eventDB.deleteEvent(eventId, v -> {
            invalidateEvents();
            onSuccess.onSuccess(v);
        }, onFailure);
    }

    //  US 03.05.01 — Browse profiles

    public void getAllProfiles(OnSuccessListener<List<Entrant>> onSuccess,
                               OnFailureListener onFailure) {
        if (cachedProfiles != null) {
            onSuccess.onSuccess(cachedProfiles);
            return;
        }
        db.collection("profiles")
                .get()
                .addOnSuccessListener(snapshot -> {
                    List<Entrant> profiles = new ArrayList<>();
                    for (DocumentSnapshot doc : snapshot.getDocuments()) {
                        Entrant p = doc.toObject(Entrant.class);
                        if (p != null) {
                            if (p.getDeviceId() == null || p.getDeviceId().trim().isEmpty())
                                p.setDeviceId(doc.getId());
                            profiles.add(p);
                        }
                    }
                    cachedProfiles = profiles;
                    onSuccess.onSuccess(profiles);
                })
                .addOnFailureListener(onFailure);
    }

    // US 03.02.01 — Remove profiles
    // Also deletes any events this profile created as an organizer.

    public void removeProfile(String deviceId,
                              OnSuccessListener<Void> onSuccess,
                              OnFailureListener onFailure) {
        profileDB.deleteProfile(deviceId, unused -> {
            invalidateProfiles();
            db.collection("events")
                    .whereEqualTo("organizerDeviceId", deviceId)
                    .get()
                    .addOnSuccessListener(snapshot -> {
                        if (snapshot.isEmpty()) {
                            onSuccess.onSuccess(null);
                            return;
                        }
                        WriteBatch batch = db.batch();
                        for (DocumentSnapshot doc : snapshot.getDocuments()) {
                            batch.delete(doc.getReference());
                        }
                        batch.commit()
                                .addOnSuccessListener(v -> {
                                    invalidateEvents();
                                    onSuccess.onSuccess(null);
                                })
                                .addOnFailureListener(onFailure);
                    })
                    .addOnFailureListener(onFailure);
        }, onFailure);
    }

    // ── US 03.06.01 — Browse images ──────────────────────────────────────────
    // Reuses event cache — no extra Firestore fetch.

    public void getAllImagesFromEvents(OnSuccessListener<List<Event>> onSuccess,
                                       OnFailureListener onFailure) {
        getAllEvents(events -> {
            List<Event> withImages = new ArrayList<>();
            for (Event e : events) {
                String url = e.getPosterImageUrl();
                if (url != null && !url.trim().isEmpty())
                    withImages.add(e);
            }
            onSuccess.onSuccess(withImages);
        }, onFailure);
    }

    //  US 03.03.01 — Remove images

    public void removeEventImage(String eventId,
                                 OnSuccessListener<Void> onSuccess,
                                 OnFailureListener onFailure) {
        db.collection("events")
                .document(eventId)
                .update("posterImageUrl", null)
                .addOnSuccessListener(v -> {
                    invalidateEvents();
                    onSuccess.onSuccess(v);
                })
                .addOnFailureListener(onFailure);
    }

    // US 03.07.01 — Browse organizers
    // Reuses both caches — no Firestore fetch if data already loaded.

    public void getAllOrganizers(OnSuccessListener<List<Entrant>> onSuccess,
                                 OnFailureListener onFailure) {
        getAllEvents(events -> {
            Set<String> organizerIds = new HashSet<>();
            for (Event e : events) {
                if (e.getOrganizerDeviceId() != null && !e.getOrganizerDeviceId().trim().isEmpty())
                    organizerIds.add(e.getOrganizerDeviceId());
            }
            if (organizerIds.isEmpty()) {
                onSuccess.onSuccess(new ArrayList<>());
                return;
            }
            getAllProfiles(profiles -> {
                List<Entrant> organizers = new ArrayList<>();
                for (Entrant p : profiles) {
                    if (organizerIds.contains(p.getDeviceId()))
                        organizers.add(p);
                }
                onSuccess.onSuccess(organizers);
            }, onFailure);
        }, onFailure);
    }

    //  US 03.07.01  Remove organizer

    public void removeOrganizer(String organizerDeviceId,
                                OnSuccessListener<Void> onSuccess,
                                OnFailureListener onFailure) {
        profileDB.deleteProfile(organizerDeviceId, unused -> {
            invalidateProfiles();
            db.collection("events")
                    .whereEqualTo("organizerDeviceId", organizerDeviceId)
                    .get()
                    .addOnSuccessListener(snapshot -> {
                        if (snapshot.isEmpty()) {
                            onSuccess.onSuccess(null);
                            return;
                        }
                        WriteBatch batch = db.batch();
                        for (DocumentSnapshot doc : snapshot.getDocuments()) {
                            batch.delete(doc.getReference());
                        }
                        batch.commit()
                                .addOnSuccessListener(v -> {
                                    invalidateEvents();
                                    onSuccess.onSuccess(null);
                                })
                                .addOnFailureListener(onFailure);
                    })
                    .addOnFailureListener(onFailure);
        }, onFailure);
    }

    //  US 03.08.01 — Review notification logs

    public void getAllNotifications(OnSuccessListener<List<Notification>> onSuccess,
                                    OnFailureListener onFailure) {
        db.collection("notifications")
                .get()
                .addOnSuccessListener(snapshot -> {
                    List<Notification> list = new ArrayList<>();
                    for (DocumentSnapshot doc : snapshot.getDocuments()) {
                        Notification n = doc.toObject(Notification.class);
                        if (n != null) {
                            if (n.getId() == null || n.getId().trim().isEmpty())
                                n.setId(doc.getId());
                            list.add(n);
                        }
                    }
                    onSuccess.onSuccess(list);
                })
                .addOnFailureListener(onFailure);
    }
}