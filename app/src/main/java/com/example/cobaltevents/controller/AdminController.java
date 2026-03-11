package com.example.cobaltevents.controller;

import com.example.cobaltevents.db.EventDB;
import com.example.cobaltevents.db.NotificationDB;
import com.example.cobaltevents.db.ProfileDB;
import com.example.cobaltevents.model.Entrant;
import com.example.cobaltevents.model.Event;
import com.example.cobaltevents.model.Notification;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Controller for all Admin operations.
 *
 * US 03.01.01 — Remove events
 * US 03.02.01 — Remove profiles
 * US 03.03.01 — Remove images (clears posterImageUrl on event)
 * US 03.04.01 — Browse events
 * US 03.05.01 — Browse profiles
 * US 03.06.01 — Browse images
 * US 03.07.01 — Remove organizers
 * US 03.08.01 — Review notification logs
 */
public class AdminController {

    private final EventDB eventDB;
    private final ProfileDB profileDB;
    private final NotificationDB notificationDB;
    private final FirebaseFirestore db;

    public AdminController() {
        this.eventDB = new EventDB();
        this.profileDB = new ProfileDB();
        this.notificationDB = new NotificationDB();
        this.db = FirebaseFirestore.getInstance();
    }

    // ── US 03.04.01 — Browse events ──────────────────────────────────────────

    public void getAllEvents(OnSuccessListener<List<Event>> onSuccess,
                             OnFailureListener onFailure) {
        db.collection("events")
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    List<Event> events = new ArrayList<>();
                    for (com.google.firebase.firestore.DocumentSnapshot doc : querySnapshot.getDocuments()) {
                        Event e = doc.toObject(Event.class);
                        if (e != null) {
                            // Always use document ID as fallback so nothing gets skipped
                            if (e.getEventId() == null || e.getEventId().trim().isEmpty()) {
                                e.setEventId(doc.getId());
                            }
                            events.add(e);
                        }
                    }
                    onSuccess.onSuccess(events);
                })
                .addOnFailureListener(onFailure);
    }

    // ── US 03.01.01 — Remove events ──────────────────────────────────────────

    public void removeEvent(String eventId,
                            OnSuccessListener<Void> onSuccess,
                            OnFailureListener onFailure) {
        eventDB.deleteEvent(eventId, onSuccess, onFailure);
    }

    // ── US 03.05.01 — Browse profiles ────────────────────────────────────────

    public void getAllProfiles(OnSuccessListener<List<Entrant>> onSuccess,
                               OnFailureListener onFailure) {
        db.collection("profiles")
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    List<Entrant> profiles = new ArrayList<>();
                    for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
                        Entrant p = doc.toObject(Entrant.class);
                        if (p != null) {
                            if (p.getDeviceId() == null || p.getDeviceId().trim().isEmpty()) {
                                p.setDeviceId(doc.getId());
                            }
                            profiles.add(p);
                        }
                    }
                    onSuccess.onSuccess(profiles);
                })
                .addOnFailureListener(onFailure);
    }

    // ── US 03.02.01 — Remove profiles ────────────────────────────────────────

    public void removeProfile(String deviceId,
                              OnSuccessListener<Void> onSuccess,
                              OnFailureListener onFailure) {
        profileDB.deleteProfile(deviceId, onSuccess, onFailure);
    }

    // ── US 03.06.01 — Browse images ──────────────────────────────────────────

    public void getAllImagesFromEvents(OnSuccessListener<List<Event>> onSuccess,
                                       OnFailureListener onFailure) {
        eventDB.getAllEvents(events -> {
            List<Event> withImages = new ArrayList<>();
            if (events != null) {
                for (Event e : events) {
                    String url = e.getPosterImageUrl();
                    if (url != null && !url.trim().isEmpty()) {
                        withImages.add(e);
                    }
                }
            }
            onSuccess.onSuccess(withImages);
        }, onFailure);
    }

    // ── US 03.03.01 — Remove images ──────────────────────────────────────────

    public void removeEventImage(String eventId,
                                 OnSuccessListener<Void> onSuccess,
                                 OnFailureListener onFailure) {
        db.collection("events")
                .document(eventId)
                .update("posterImageUrl", null)
                .addOnSuccessListener(onSuccess)
                .addOnFailureListener(onFailure);
    }

    // ── US 03.07.01 — Browse organizers ──────────────────────────────────────
    // Organizers are profiles whose deviceId appears as organizerDeviceId
    // on at least one event.

    public void getAllOrganizers(OnSuccessListener<List<Entrant>> onSuccess,
                                 OnFailureListener onFailure) {
        db.collection("events")
                .get()
                .addOnSuccessListener(eventSnapshot -> {
                    Set<String> organizerIds = new HashSet<>();
                    for (DocumentSnapshot doc : eventSnapshot.getDocuments()) {
                        Event e = doc.toObject(Event.class);
                        if (e != null && e.getOrganizerDeviceId() != null
                                && !e.getOrganizerDeviceId().trim().isEmpty()) {
                            organizerIds.add(e.getOrganizerDeviceId());
                        }
                    }

                    if (organizerIds.isEmpty()) {
                        onSuccess.onSuccess(new ArrayList<>());
                        return;
                    }

                    db.collection("profiles")
                            .get()
                            .addOnSuccessListener(profileSnapshot -> {
                                List<Entrant> organizers = new ArrayList<>();
                                for (DocumentSnapshot doc : profileSnapshot.getDocuments()) {
                                    Entrant p = doc.toObject(Entrant.class);
                                    if (p != null) {
                                        if (p.getDeviceId() == null || p.getDeviceId().trim().isEmpty()) {
                                            p.setDeviceId(doc.getId());
                                        }
                                        if (organizerIds.contains(p.getDeviceId())) {
                                            organizers.add(p);
                                        }
                                    }
                                }
                                onSuccess.onSuccess(organizers);
                            })
                            .addOnFailureListener(onFailure);
                })
                .addOnFailureListener(onFailure);
    }

    // ── US 03.07.01 — Remove organizer ───────────────────────────────────────
    // Deletes the organizer's profile AND all events they created.

    public void removeOrganizer(String organizerDeviceId,
                                OnSuccessListener<Void> onSuccess,
                                OnFailureListener onFailure) {
        profileDB.deleteProfile(organizerDeviceId, unused -> {
            eventDB.getAllEvents(events -> {
                if (events == null || events.isEmpty()) {
                    onSuccess.onSuccess(null);
                    return;
                }

                List<Event> theirEvents = new ArrayList<>();
                for (Event e : events) {
                    if (organizerDeviceId.equals(e.getOrganizerDeviceId())) {
                        theirEvents.add(e);
                    }
                }

                if (theirEvents.isEmpty()) {
                    onSuccess.onSuccess(null);
                    return;
                }

                final int[] remaining = {theirEvents.size()};
                for (Event e : theirEvents) {
                    eventDB.deleteEvent(e.getEventId(), v -> {
                        remaining[0]--;
                        if (remaining[0] == 0) onSuccess.onSuccess(null);
                    }, onFailure);
                }
            }, onFailure);
        }, onFailure);
    }

    // ── US 03.08.01 — Review notification logs ───────────────────────────────

    public void getAllNotifications(OnSuccessListener<List<Notification>> onSuccess,
                                    OnFailureListener onFailure) {
        notificationDB.getAllNotifications(onSuccess, onFailure);
    }
}