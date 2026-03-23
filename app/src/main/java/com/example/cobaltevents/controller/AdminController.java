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
 * AdminController handles all Firestore operations for the Admin dashboard.
 *
 * Covers all Admin user stories:
 *   US 03.01.01 — Remove events
 *   US 03.02.01 — Remove profiles
 *   US 03.03.01 — Remove images (clears posterImageUrl on the event document)
 *   US 03.04.01 — Browse events
 *   US 03.05.01 — Browse profiles
 *   US 03.06.01 — Browse images
 *   US 03.07.01 — Remove organizers (also deletes their events)
 *   US 03.08.01 — Review notification logs (read-only)
 *
 * Performance notes:
 *   - Static in-memory caches for events and profiles survive tab switches
 *     and activity re-creation, so Firestore is only hit once per session.
 *   - The Images and Organizers tabs reuse the event/profile caches —
 *     zero extra Firestore fetches after the first load.
 *   - Deletes use Firestore WriteBatch (single round trip for bulk deletes).
 *   - Cache is invalidated after every mutation so the next fetch is fresh.
 */
public class AdminController {

    // ── Database helpers ──────────────────────────────────────────────────────

    private final EventDB eventDB;
    private final ProfileDB profileDB;
    private final FirebaseFirestore db;

    // ── Static caches — survive tab switches and activity re-creation ─────────
    // These are static so a new AdminController instance reuses the same data.
    // They are set to null whenever a mutation (delete/update) occurs so the
    // next read always fetches fresh data from Firestore.

    private static List<Event>   cachedEvents   = null;
    private static List<Entrant> cachedProfiles = null;

    // ── Constructor ───────────────────────────────────────────────────────────

    public AdminController() {
        this.eventDB   = new EventDB();
        this.profileDB = new ProfileDB();
        this.db        = FirebaseFirestore.getInstance();
    }

    // ── Cache invalidation helpers ────────────────────────────────────────────

    /** Clears the event cache so the next getAllEvents() hits Firestore. */
    private void invalidateEvents()   { cachedEvents   = null; }

    /** Clears the profile cache so the next getAllProfiles() hits Firestore. */
    private void invalidateProfiles() { cachedProfiles = null; }

    /** Clears both caches — used after deletes that affect both collections. */
    public static void invalidateAll() {
        cachedEvents   = null;
        cachedProfiles = null;
    }

    // =========================================================================
    // US 03.04.01 — Browse events
    // Returns all events from Firestore (cached after first fetch).
    // =========================================================================

    public void getAllEvents(OnSuccessListener<List<Event>> onSuccess,
                             OnFailureListener onFailure) {
        getAllEvents(onSuccess, onFailure, null);
    }

    /**
     * @param onUsedInMemoryCache if non-null, invoked when the static session cache is used
     *                            instead of fetching from Firestore.
     */
    public void getAllEvents(OnSuccessListener<List<Event>> onSuccess,
                             OnFailureListener onFailure,
                             Runnable onUsedInMemoryCache) {
        // Return cached list immediately if available (avoids Firestore round trip)
        if (cachedEvents != null) {
            if (onUsedInMemoryCache != null) onUsedInMemoryCache.run();
            onSuccess.onSuccess(cachedEvents);
            return;
        }

        // First load: fetch all events from Firestore
        db.collection("events")
                .get()
                .addOnSuccessListener(snapshot -> {
                    List<Event> events = new ArrayList<>();
                    for (DocumentSnapshot doc : snapshot.getDocuments()) {
                        Event e = doc.toObject(Event.class);
                        if (e != null) {
                            // Firestore sometimes doesn't map the document ID into the object —
                            // fall back to the document ID if eventId is missing
                            if (e.getEventId() == null || e.getEventId().trim().isEmpty())
                                e.setEventId(doc.getId());
                            events.add(e);
                        }
                    }
                    cachedEvents = events; // Store in cache for future tab switches
                    onSuccess.onSuccess(events);
                })
                .addOnFailureListener(onFailure);
    }

    // =========================================================================
    // US 03.01.01 — Remove events
    // Deletes the event document from Firestore and invalidates the cache.
    // =========================================================================

    public void removeEvent(String eventId,
                            OnSuccessListener<Void> onSuccess,
                            OnFailureListener onFailure) {
        eventDB.deleteEvent(eventId, v -> {
            invalidateEvents(); // Force fresh fetch on next browse
            onSuccess.onSuccess(v);
        }, onFailure);
    }

    // =========================================================================
    // US 03.05.01 — Browse profiles
    // Returns all user profiles from Firestore (cached after first fetch).
    // =========================================================================

    public void getAllProfiles(OnSuccessListener<List<Entrant>> onSuccess,
                               OnFailureListener onFailure) {
        getAllProfiles(onSuccess, onFailure, null);
    }

    /**
     * @param onUsedInMemoryCache if non-null, invoked when the static session cache is used.
     */
    public void getAllProfiles(OnSuccessListener<List<Entrant>> onSuccess,
                               OnFailureListener onFailure,
                               Runnable onUsedInMemoryCache) {
        // Return cached list immediately if available
        if (cachedProfiles != null) {
            if (onUsedInMemoryCache != null) onUsedInMemoryCache.run();
            onSuccess.onSuccess(cachedProfiles);
            return;
        }

        // First load: fetch all profiles from Firestore
        db.collection("profiles")
                .get()
                .addOnSuccessListener(snapshot -> {
                    List<Entrant> profiles = new ArrayList<>();
                    for (DocumentSnapshot doc : snapshot.getDocuments()) {
                        Entrant p = doc.toObject(Entrant.class);
                        if (p != null) {
                            // Fall back to document ID if deviceId field is missing
                            if (p.getDeviceId() == null || p.getDeviceId().trim().isEmpty())
                                p.setDeviceId(doc.getId());
                            profiles.add(p);
                        }
                    }
                    cachedProfiles = profiles; // Store in cache
                    onSuccess.onSuccess(profiles);
                })
                .addOnFailureListener(onFailure);
    }

    // =========================================================================
    // US 03.02.01 — Remove profiles
    // Deletes the profile document AND all events created by this organizer
    // (using a targeted whereEqualTo query + WriteBatch for efficiency).
    // =========================================================================

    public void removeProfile(String deviceId,
                              OnSuccessListener<Void> onSuccess,
                              OnFailureListener onFailure) {
        // Step 1: Delete the profile document from the profiles collection
        profileDB.deleteProfile(deviceId, unused -> {
            invalidateProfiles(); // Profile cache is now stale

            // Step 2: Find and delete all events this user organised
            // We use whereEqualTo instead of scanning all events — much faster
            db.collection("events")
                    .whereEqualTo("organizerDeviceId", deviceId)
                    .get()
                    .addOnSuccessListener(snapshot -> {
                        if (snapshot.isEmpty()) {
                            // No events to delete — we're done
                            onSuccess.onSuccess(null);
                            return;
                        }

                        // Batch delete all found events in a single Firestore round trip
                        WriteBatch batch = db.batch();
                        for (DocumentSnapshot doc : snapshot.getDocuments()) {
                            batch.delete(doc.getReference());
                        }
                        batch.commit()
                                .addOnSuccessListener(v -> {
                                    invalidateEvents(); // Event cache is now stale
                                    onSuccess.onSuccess(null);
                                })
                                .addOnFailureListener(onFailure);
                    })
                    .addOnFailureListener(onFailure);
        }, onFailure);
    }

    // =========================================================================
    // US 03.06.01 — Browse images
    // Reuses the event cache — filters to only events that have a poster image.
    // No extra Firestore fetch needed after events are loaded.
    // =========================================================================

    public void getAllImagesFromEvents(OnSuccessListener<List<Event>> onSuccess,
                                       OnFailureListener onFailure) {
        getAllImagesFromEvents(onSuccess, onFailure, null);
    }

    /**
     * @param onUsedInMemoryCache forwarded to {@link #getAllEvents} when the event cache is used.
     */
    public void getAllImagesFromEvents(OnSuccessListener<List<Event>> onSuccess,
                                       OnFailureListener onFailure,
                                       Runnable onUsedInMemoryCache) {
        getAllEvents(events -> {
            // Filter: only include events that have a non-empty poster image URL
            List<Event> withImages = new ArrayList<>();
            for (Event e : events) {
                String url = e.getPosterImageUrl();
                if (url != null && !url.trim().isEmpty())
                    withImages.add(e);
            }
            onSuccess.onSuccess(withImages);
        }, onFailure, onUsedInMemoryCache);
    }

    // =========================================================================
    // US 03.03.01 — Remove images
    // Clears the posterImageUrl field on the event document (does NOT delete
    // the event itself — admin chooses that separately in the Images dialog).
    // =========================================================================

    public void removeEventImage(String eventId,
                                 OnSuccessListener<Void> onSuccess,
                                 OnFailureListener onFailure) {
        // Only nullify the image field — leave the rest of the event intact
        db.collection("events")
                .document(eventId)
                .update("posterImageUrl", null)
                .addOnSuccessListener(v -> {
                    invalidateEvents(); // Event cache is stale (posterImageUrl changed)
                    onSuccess.onSuccess(v);
                })
                .addOnFailureListener(onFailure);
    }

    // =========================================================================
    // US 03.07.01 — Browse organizers
    // Reuses BOTH caches: gets organizer IDs from events, then matches them
    // against profiles. Zero extra Firestore fetches after initial load.
    // =========================================================================

    public void getAllOrganizers(OnSuccessListener<List<Entrant>> onSuccess,
                                 OnFailureListener onFailure) {
        getAllOrganizers(onSuccess, onFailure, null);
    }

    /**
     * @param onUsedInMemoryCache forwarded to both {@link #getAllEvents} and {@link #getAllProfiles}
     *                            when either session cache is used (may run twice — UI may dedupe).
     */
    public void getAllOrganizers(OnSuccessListener<List<Entrant>> onSuccess,
                                 OnFailureListener onFailure,
                                 Runnable onUsedInMemoryCache) {
        // Step 1: Get all events to extract unique organizer device IDs
        getAllEvents(events -> {
            Set<String> organizerIds = new HashSet<>();
            for (Event e : events) {
                if (e.getOrganizerDeviceId() != null && !e.getOrganizerDeviceId().trim().isEmpty())
                    organizerIds.add(e.getOrganizerDeviceId());
            }

            if (organizerIds.isEmpty()) {
                onSuccess.onSuccess(new ArrayList<>()); // No organizers found
                return;
            }

            // Step 2: Get all profiles and filter to only the organizer IDs we found
            getAllProfiles(profiles -> {
                List<Entrant> organizers = new ArrayList<>();
                for (Entrant p : profiles) {
                    if (organizerIds.contains(p.getDeviceId()))
                        organizers.add(p);
                }
                onSuccess.onSuccess(organizers);
            }, onFailure, onUsedInMemoryCache);
        }, onFailure, onUsedInMemoryCache);
    }

    // =========================================================================
    // US 03.07.01 — Remove organizer
    // Deletes the organizer's profile AND all events they created.
    // Same pattern as removeProfile but semantically for organizer removal.
    // =========================================================================

    public void removeOrganizer(String organizerDeviceId,
                                OnSuccessListener<Void> onSuccess,
                                OnFailureListener onFailure) {
        // Step 1: Delete the organizer's profile document
        profileDB.deleteProfile(organizerDeviceId, unused -> {
            invalidateProfiles(); // Profile cache is now stale

            // Step 2: Batch delete all events this organizer created
            db.collection("events")
                    .whereEqualTo("organizerDeviceId", organizerDeviceId)
                    .get()
                    .addOnSuccessListener(snapshot -> {
                        if (snapshot.isEmpty()) {
                            // Organizer had no events — done
                            onSuccess.onSuccess(null);
                            return;
                        }

                        // Delete all their events in one Firestore round trip
                        WriteBatch batch = db.batch();
                        for (DocumentSnapshot doc : snapshot.getDocuments()) {
                            batch.delete(doc.getReference());
                        }
                        batch.commit()
                                .addOnSuccessListener(v -> {
                                    invalidateEvents(); // Event cache is now stale
                                    onSuccess.onSuccess(null);
                                })
                                .addOnFailureListener(onFailure);
                    })
                    .addOnFailureListener(onFailure);
        }, onFailure);
    }

    // =========================================================================
    // US 03.08.01 — Review notification logs (read-only)
    // Fetches all notification documents. Not cached because notifications
    // are high-frequency and admins expect to see the latest logs each time.
    // =========================================================================

    public void getAllNotifications(OnSuccessListener<List<Notification>> onSuccess,
                                    OnFailureListener onFailure) {
        db.collection("notifications")
                .get()
                .addOnSuccessListener(snapshot -> {
                    List<Notification> list = new ArrayList<>();
                    for (DocumentSnapshot doc : snapshot.getDocuments()) {
                        Notification n = doc.toObject(Notification.class);
                        if (n != null) {
                            // Fall back to document ID if id field is missing
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