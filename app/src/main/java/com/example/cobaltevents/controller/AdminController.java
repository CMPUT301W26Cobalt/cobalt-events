package com.example.cobaltevents.controller;

import android.content.Context;

import com.example.cobaltevents.R;
import com.example.cobaltevents.db.CommentDB;
import com.example.cobaltevents.db.EventDB;
import com.example.cobaltevents.db.NotificationDB;
import com.example.cobaltevents.db.ProfileDB;
import com.example.cobaltevents.db.WaitingListDB;
import com.example.cobaltevents.model.Comment;
import com.example.cobaltevents.model.Entrant;
import com.example.cobaltevents.model.Event;
import com.example.cobaltevents.model.Notification;
import com.example.cobaltevents.model.WaitingList;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.Source;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Controller that mediates between admin UI ({@link com.example.cobaltevents.ui.admin.AdminActivity}
 * and related screens) and Firestore for system administration.
 *
 * <p>User stories (admin role):
 * <ul>
 *   <li>US 03.01.01 – Remove events (alerts organizers and waitlisted users via
 *       {@link Notification#TYPE_EVENT_ALERT}, then deletes the document)</li>
 *   <li>US 03.02.01 – Remove profiles (same organizer/event cleanup as account self-delete, then
 *       {@link WaitingListDB#removeUserFromAllWaitlists} and {@link ProfileDB#deleteProfile})</li>
 *   <li>US 03.03.01 – Remove images (clears {@code posterImageUrl} only if it still matches the URL
 *       the admin saw)</li>
 *   <li>US 03.04.01 – Browse events</li>
 *   <li>US 03.05.01 – Browse profiles</li>
 *   <li>US 03.06.01 – Browse images</li>
 *   <li>US 03.07.01 – Browse organizers; remove organizer (event-side cleanup only—no profile delete
 *       or waitlist removal for that user)</li>
 *   <li>US 03.08.01 – Review notification logs</li>
 *   <li>US 03.10.01 – Browse and remove comments (via {@link #getAllCommentsGroupedByEvent},
 *       {@link #removeComment}, {@link #removeReply})</li>
 * </ul>
 *
 * <p>Events and profiles are cached in memory for the session so tab switches avoid extra Firestore
 * reads; {@link #invalidateAll()} clears both. Co-organizer updates use per-document writes so a
 * missing document does not abort the whole chain.
 */
public class AdminController {

    /**
     * {@link #removeEventImage} reports this via {@code onFailure} when the server poster URL no longer
     * matches the URL shown in the admin Images list ({@link #isPosterUrlMismatchFailure}).
     */
    public static final String ERR_POSTER_URL_MISMATCH = "POSTER_URL_MISMATCH";

    // ── Database helpers ──────────────────────────────────────────────────────

    private final EventDB eventDB;
    private final ProfileDB profileDB;
    private final FirebaseFirestore db;
    private final Context appContext;
    private final WaitingListDB waitingListDB;
    private final NotificationDB notificationDB;

    // ── Static caches — survive tab switches and activity re-creation ─────────
    // These are static so a new AdminController instance reuses the same data.
    // They are set to null whenever a mutation (delete/update) occurs so the
    // next read always fetches fresh data from Firestore.

    private static List<Event>   cachedEvents   = null;
    private static List<Entrant> cachedProfiles = null;

    // ── Constructor ───────────────────────────────────────────────────────────

    private final CommentDB commentDB;

    public AdminController() {
        this(null);
    }

    /**
     * @param appContext application context for notification strings; if {@code null}, English fallbacks are used
     */
    public AdminController(Context appContext) {
        this.appContext = appContext != null ? appContext.getApplicationContext() : null;
        this.eventDB         = new EventDB();
        this.profileDB       = new ProfileDB();
        this.db              = FirebaseFirestore.getInstance();
        this.commentDB       = new CommentDB();
        this.waitingListDB   = new WaitingListDB();
        this.notificationDB  = new NotificationDB();
    }

    // ── Cache invalidation helpers ────────────────────────────────────────────

    /** Clears the event cache so the next getAllEvents() hits Firestore. */
    private void invalidateEvents()   { cachedEvents   = null; }

    /** Clears the profile cache so the next getAllProfiles() hits Firestore. */
    private void invalidateProfiles() { cachedProfiles = null; }

    /** Clears event and profile session caches; next browse calls hit Firestore again. */
    public static void invalidateAll() {
        cachedEvents   = null;
        cachedProfiles = null;
    }

    /** US 03.04.01: Load all events for admin browse (cached after the first fetch in the session). */
    public void getAllEvents(OnSuccessListener<List<Event>> onSuccess,
                             OnFailureListener onFailure) {
        getAllEvents(onSuccess, onFailure, null);
    }

    /**
     * US 03.04.01: Same as {@link #getAllEvents(OnSuccessListener, OnFailureListener)} with an optional
     * callback when the in-memory cache is used instead of Firestore.
     *
     * @param onUsedInMemoryCache if non-null, invoked when the session cache is served
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

    /**
     * US 03.01.01: Delete an event after collecting organizers and waitlisted device IDs; sends
     * {@link Notification#TYPE_EVENT_ALERT} best-effort (delete is not blocked by a failed notification write).
     */
    public void removeEvent(String eventId,
                            OnSuccessListener<Void> onSuccess,
                            OnFailureListener onFailure) {
        if (eventId == null || eventId.trim().isEmpty()) {
            if (onFailure != null) {
                onFailure.onFailure(new IllegalArgumentException("eventId required"));
            }
            return;
        }
        final String id = eventId.trim();

        eventDB.getEvent(id, event -> {
            final String displayName = resolveEventDisplayName(event);
            final Set<String> organizerIds = new HashSet<>();
            if (event != null && event.getOrganizers() != null) {
                for (String o : event.getOrganizers()) {
                    if (o != null && !o.trim().isEmpty()) {
                        organizerIds.add(o.trim());
                    }
                }
            }

            waitingListDB.getEntrantsForEvent(id, Source.SERVER, registrations -> {
                LinkedHashSet<String> recipientIds = new LinkedHashSet<>();
                if (registrations != null) {
                    for (WaitingList reg : registrations) {
                        if (reg == null) continue;
                        String did = reg.getDeviceId();
                        if (did != null && !did.trim().isEmpty()) {
                            recipientIds.add(did.trim());
                        }
                    }
                }
                recipientIds.addAll(organizerIds);
                deleteEventThenSendDeletionAlerts(id, displayName, recipientIds, organizerIds,
                        onSuccess, onFailure);
            }, e -> {
                LinkedHashSet<String> recipientIds = new LinkedHashSet<>(organizerIds);
                deleteEventThenSendDeletionAlerts(id, displayName, recipientIds, organizerIds,
                        onSuccess, onFailure);
            });
        }, e -> finishAdminEventDelete(id, onSuccess, onFailure));
    }

    private void deleteEventThenSendDeletionAlerts(String eventId,
                                                   String displayName,
                                                   LinkedHashSet<String> recipientIds,
                                                   Set<String> organizerDeviceIds,
                                                   OnSuccessListener<Void> onSuccess,
                                                   OnFailureListener onFailure) {
        finishAdminEventDelete(eventId, v -> {
            if (onSuccess != null) onSuccess.onSuccess(v);
            if (recipientIds == null || recipientIds.isEmpty()) return;
            String title = resolveEventAlertTitle();
            String message = resolveAdminEventDeletedMessage(displayName);
            List<String> ordered = new ArrayList<>(recipientIds);
            sendAdminDeletionEventAlerts(ordered, 0, eventId, title, message, organizerDeviceIds);
        }, onFailure);
    }

    private void finishAdminEventDelete(String eventId,
                                        OnSuccessListener<Void> onSuccess,
                                        OnFailureListener onFailure) {
        eventDB.deleteEvent(eventId, v -> {
            invalidateEvents();
            if (onSuccess != null) onSuccess.onSuccess(v);
        }, onFailure);
    }

    private static String resolveEventDisplayName(Event event) {
        if (event == null) return "Untitled Event";
        String n = event.getName();
        if (n != null && !n.trim().isEmpty()) return n.trim();
        return "Untitled Event";
    }

    private String resolveEventAlertTitle() {
        if (appContext != null) {
            return appContext.getString(R.string.notification_event_alert_title);
        }
        return "Event Alert";
    }

    private String resolveAdminEventDeletedMessage(String displayName) {
        if (appContext != null) {
            return appContext.getString(R.string.notification_admin_event_deleted_message, displayName);
        }
        return "The event \"" + displayName + "\" has been permanently deleted.";
    }

    /** Best-effort: continue after failures so one bad write does not abort the chain. */
    private void sendAdminDeletionEventAlerts(List<String> recipientIds,
                                              int index,
                                              String eventId,
                                              String title,
                                              String message,
                                              Set<String> organizerDeviceIds) {
        if (index >= recipientIds.size()) return;
        String recipientId = recipientIds.get(index);
        Notification n = new Notification(
                recipientId,
                eventId,
                title,
                message,
                Notification.TYPE_EVENT_ALERT);
        if (organizerDeviceIds.contains(recipientId)) {
            n.setRecipientMode(Notification.RECIPIENT_MODE_ORGANIZER);
        } else {
            n.setRecipientMode(Notification.RECIPIENT_MODE_USER);
        }
        n.setResponse(null);
        notificationDB.saveNotification(n,
                unused -> sendAdminDeletionEventAlerts(recipientIds, index + 1, eventId, title, message,
                        organizerDeviceIds),
                e -> sendAdminDeletionEventAlerts(recipientIds, index + 1, eventId, title, message,
                        organizerDeviceIds));
    }

    /** US 03.05.01: Load all user profiles for admin browse (cached after the first fetch). */
    public void getAllProfiles(OnSuccessListener<List<Entrant>> onSuccess,
                               OnFailureListener onFailure) {
        getAllProfiles(onSuccess, onFailure, null);
    }

    /**
     * US 03.05.01: Same as {@link #getAllProfiles(OnSuccessListener, OnFailureListener)} with an optional
     * cache-hit callback.
     *
     * @param onUsedInMemoryCache if non-null, invoked when the session cache is served
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

    private static boolean isFirestoreNotFound(Throwable t) {
        while (t != null) {
            if (t instanceof FirebaseFirestoreException) {
                return ((FirebaseFirestoreException) t).getCode() == FirebaseFirestoreException.Code.NOT_FOUND;
            }
            t = t.getCause();
        }
        return false;
    }

    /**
     * Co-organizer-only writes (sole-organizer deletes use {@link #deleteEventCascadeForRemovedOrganizer}).
     */
    private static final class OrganizerEventMutation {
        final DocumentReference ref;
        /** Delete the whole event document. */
        final boolean deleteEvent;
        /** Replace {@code organizers} with this list (non-null when not deleting and not using arrayRemove). */
        final List<String> newOrganizers;
        /** When we could not rebuild the list but the query matched, use {@link FieldValue#arrayRemove(Object)}. */
        final boolean arrayRemoveFallback;

        OrganizerEventMutation(DocumentReference ref, boolean deleteEvent,
                               List<String> newOrganizers, boolean arrayRemoveFallback) {
            this.ref = ref;
            this.deleteEvent = deleteEvent;
            this.newOrganizers = newOrganizers;
            this.arrayRemoveFallback = arrayRemoveFallback;
        }
    }

    private static List<String> readOrganizerStringsFromDoc(DocumentSnapshot doc) {
        List<String> out = new ArrayList<>();
        Object raw = doc.get("organizers");
        if (!(raw instanceof List<?>)) {
            return out;
        }
        for (Object item : (List<?>) raw) {
            if (item instanceof String) {
                out.add((String) item);
            }
        }
        return out;
    }

    /**
     * Builds a delete or {@code organizers} update for one event; {@code removedDeviceId} is trimmed.
     */
    private static OrganizerEventMutation buildOrganizerRemovalMutation(DocumentSnapshot doc,
                                                                        String removedDeviceId) {
        DocumentReference ref = doc.getReference();
        List<String> raw = readOrganizerStringsFromDoc(doc);
        List<String> next = new ArrayList<>();
        boolean removedAny = false;
        for (String o : raw) {
            if (o == null) {
                continue;
            }
            if (o.trim().equals(removedDeviceId)) {
                removedAny = true;
                continue;
            }
            next.add(o);
        }
        if (removedAny) {
            if (next.isEmpty()) {
                return new OrganizerEventMutation(ref, true, null, false);
            }
            return new OrganizerEventMutation(ref, false, next, false);
        }
        return new OrganizerEventMutation(ref, false, null, true);
    }

    /**
     * Applies co-organizer updates one document at a time so a missing event (e.g. user already
     * deleted their account) does not fail the whole admin delete.
     */
    private void applyCoOrganizerMutationsSequentially(List<OrganizerEventMutation> mutations,
                                                       String removedDeviceId,
                                                       int index,
                                                       Runnable onDone,
                                                       OnFailureListener onFailure) {
        if (index >= mutations.size()) {
            onDone.run();
            return;
        }
        OrganizerEventMutation m = mutations.get(index);
        com.google.android.gms.tasks.Task<Void> task;
        if (m.arrayRemoveFallback) {
            task = m.ref.update("organizers", FieldValue.arrayRemove(removedDeviceId));
        } else if (m.newOrganizers != null) {
            task = m.ref.update("organizers", m.newOrganizers);
        } else {
            applyCoOrganizerMutationsSequentially(mutations, removedDeviceId, index + 1, onDone, onFailure);
            return;
        }
        task.addOnSuccessListener(v -> applyCoOrganizerMutationsSequentially(
                        mutations, removedDeviceId, index + 1, onDone, onFailure))
                .addOnFailureListener(e -> {
                    if (isFirestoreNotFound(e)) {
                        applyCoOrganizerMutationsSequentially(
                                mutations, removedDeviceId, index + 1, onDone, onFailure);
                    } else if (onFailure != null) {
                        onFailure.onFailure(e);
                    }
                });
    }

    /** Same ordering as {@link com.example.cobaltevents.ui.AccountSettingsActivity#performAccountDeletion}. */
    private void deleteEventCascadeForRemovedOrganizer(String eventId,
                                                       Runnable onSuccess,
                                                       OnFailureListener onFailure) {
        commentDB.deleteAllCommentsAndRepliesForEvent(eventId,
                unused -> waitingListDB.deleteAllWaitlistDataForEvent(eventId,
                        unused2 -> eventDB.deleteEvent(eventId,
                                unused3 -> {
                                    if (onSuccess != null) {
                                        onSuccess.run();
                                    }
                                },
                                e -> {
                                    if (isFirestoreNotFound(e)) {
                                        if (onSuccess != null) {
                                            onSuccess.run();
                                        }
                                    } else if (onFailure != null) {
                                        onFailure.onFailure(e);
                                    }
                                }),
                        onFailure),
                onFailure);
    }

    private void runCascadeDeletesThenCoOrganizerUpdates(List<String> soleOrganizerEventIds,
                                                         int cascadeIndex,
                                                         List<OrganizerEventMutation> coOrgMutations,
                                                         String removedDeviceId,
                                                         Runnable onDone,
                                                         OnFailureListener onFailure) {
        if (cascadeIndex < soleOrganizerEventIds.size()) {
            deleteEventCascadeForRemovedOrganizer(soleOrganizerEventIds.get(cascadeIndex),
                    () -> runCascadeDeletesThenCoOrganizerUpdates(
                            soleOrganizerEventIds, cascadeIndex + 1, coOrgMutations,
                            removedDeviceId, onDone, onFailure),
                    onFailure);
            return;
        }
        if (coOrgMutations.isEmpty()) {
            onDone.run();
            return;
        }
        applyCoOrganizerMutationsSequentially(coOrgMutations, removedDeviceId, 0, onDone, onFailure);
    }

    /**
     * For every event that lists {@code deviceId} in {@code organizers}: sole organizer → cascade
     * delete (comments, waitlists, event); else → update {@code organizers}. Then {@code onDone}.
     */
    private void runOrganizerRemovalPhase(String deviceId,
                                          Runnable onDone,
                                          OnFailureListener onFailure) {
        db.collection("events")
                .whereArrayContains("organizers", deviceId)
                .get()
                .addOnSuccessListener(snapshot -> {
                    List<DocumentSnapshot> docs = snapshot.getDocuments();
                    if (docs.isEmpty()) {
                        onDone.run();
                        return;
                    }
                    List<String> soleOrganizerEventIds = new ArrayList<>();
                    List<OrganizerEventMutation> coOrgMutations = new ArrayList<>();
                    for (DocumentSnapshot doc : docs) {
                        OrganizerEventMutation m = buildOrganizerRemovalMutation(doc, deviceId);
                        if (m.deleteEvent) {
                            soleOrganizerEventIds.add(doc.getId());
                        } else {
                            coOrgMutations.add(m);
                        }
                    }
                    runCascadeDeletesThenCoOrganizerUpdates(
                            soleOrganizerEventIds, 0, coOrgMutations, deviceId, onDone, onFailure);
                })
                .addOnFailureListener(onFailure);
    }

    /**
     * US 03.02.01: Remove a user profile—same organizer/event pass as account self-delete (cascade sole-org
     * events or drop from {@code organizers}), then {@link WaitingListDB#removeUserFromAllWaitlists}
     * and {@link ProfileDB#deleteProfile}.
     */
    public void removeProfile(String deviceId,
                              OnSuccessListener<Void> onSuccess,
                              OnFailureListener onFailure) {
        removeEntrantAndFixOrganizedEvents(deviceId, onSuccess, onFailure);
    }

    /**
     * US 03.06.01: Events that have a non-empty {@code posterImageUrl} (reuses {@link #getAllEvents} cache).
     */
    public void getAllImagesFromEvents(OnSuccessListener<List<Event>> onSuccess,
                                       OnFailureListener onFailure) {
        getAllImagesFromEvents(onSuccess, onFailure, null);
    }

    /**
     * US 03.06.01: Same as {@link #getAllImagesFromEvents(OnSuccessListener, OnFailureListener)}; forwards
     * {@code onUsedInMemoryCache} to {@link #getAllEvents}.
     *
     * @param onUsedInMemoryCache forwarded to {@link #getAllEvents} when the event cache is used
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

    /**
     * US 03.03.01: Clear {@code posterImageUrl} on the event only if it still equals {@code posterUrlWhenListed}
     * (avoids wiping a poster an organizer changed concurrently). Does not delete the event document.
     *
     * @param posterUrlWhenListed poster URL from the admin Images row; required for the transactional check
     */
    public void removeEventImage(String eventId,
                                 String posterUrlWhenListed,
                                 OnSuccessListener<Void> onSuccess,
                                 OnFailureListener onFailure) {
        if (eventId == null || eventId.trim().isEmpty()) {
            if (onFailure != null) {
                onFailure.onFailure(new IllegalArgumentException("eventId required"));
            }
            return;
        }
        if (posterUrlWhenListed == null || posterUrlWhenListed.trim().isEmpty()) {
            if (onFailure != null) {
                onFailure.onFailure(new IllegalArgumentException("posterUrlWhenListed required"));
            }
            return;
        }
        final String expected = posterUrlWhenListed.trim();
        DocumentReference docRef = db.collection("events").document(eventId.trim());
        db.runTransaction(transaction -> {
            DocumentSnapshot snap = transaction.get(docRef);
            if (!snap.exists()) {
                throw new IllegalStateException("EVENT_NOT_FOUND");
            }
            String current = snap.getString("posterImageUrl");
            if (!posterImageUrlsMatch(current, expected)) {
                return false;
            }
            transaction.update(docRef, "posterImageUrl", null);
            return true;
        }).addOnSuccessListener(cleared -> {
            if (Boolean.TRUE.equals(cleared)) {
                invalidateEvents();
                if (onSuccess != null) {
                    onSuccess.onSuccess(null);
                }
            } else {
                invalidateEvents();
                if (onFailure != null) {
                    onFailure.onFailure(new IllegalStateException(ERR_POSTER_URL_MISMATCH));
                }
            }
        }).addOnFailureListener(e -> {
            invalidateEvents();
            if (onFailure != null) {
                onFailure.onFailure(e);
            }
        });
    }

    private static String normalizePosterImageUrl(String url) {
        return url == null ? "" : url.trim();
    }

    private static boolean posterImageUrlsMatch(String firestoreUrl, String expectedUrl) {
        return normalizePosterImageUrl(firestoreUrl).equals(normalizePosterImageUrl(expectedUrl));
    }

    /** Whether {@code e} (or a cause) indicates {@link #ERR_POSTER_URL_MISMATCH} from {@link #removeEventImage}. */
    public static boolean isPosterUrlMismatchFailure(Exception e) {
        Throwable t = e;
        while (t != null) {
            if (t instanceof IllegalStateException
                    && ERR_POSTER_URL_MISMATCH.equals(t.getMessage())) {
                return true;
            }
            t = t.getCause();
        }
        return false;
    }

    /**
     * US 03.07.01: Profiles that appear as an organizer on at least one event (joins cached events + profiles).
     */
    public void getAllOrganizers(OnSuccessListener<List<Entrant>> onSuccess,
                                 OnFailureListener onFailure) {
        getAllOrganizers(onSuccess, onFailure, null);
    }

    /**
     * US 03.07.01: Same as {@link #getAllOrganizers(OnSuccessListener, OnFailureListener)} with cache-hit
     * callbacks forwarded to {@link #getAllEvents} and {@link #getAllProfiles} (may run twice per load).
     *
     * @param onUsedInMemoryCache forwarded when either cache is used
     */
    public void getAllOrganizers(OnSuccessListener<List<Entrant>> onSuccess,
                                 OnFailureListener onFailure,
                                 Runnable onUsedInMemoryCache) {
        // Step 1: Get all events to extract unique organizer device IDs
        getAllEvents(events -> {
            Set<String> organizerIds = new HashSet<>();
            for (Event e : events) {
                if (e.getOrganizers() != null) {
                    for (String id : e.getOrganizers()) {
                        if (id != null && !id.trim().isEmpty()) {
                            organizerIds.add(id.trim());
                        }
                    }
                }
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

    /**
     * US 03.07.01: Strip this device from organizer duties—same event-side logic as the organizer pass in
     * account self-delete / {@link #removeProfile} (cascade-delete sole-org events; else remove from
     * {@code organizers}). Does not remove waitlists or delete the profile (unlike {@link #removeProfile}).
     */
    public void removeOrganizer(String organizerDeviceId,
                                OnSuccessListener<Void> onSuccess,
                                OnFailureListener onFailure) {
        if (organizerDeviceId == null || organizerDeviceId.trim().isEmpty()) {
            if (onFailure != null) {
                onFailure.onFailure(new IllegalArgumentException("organizerDeviceId required"));
            }
            return;
        }
        final String id = organizerDeviceId.trim();
        runOrganizerRemovalPhase(id,
                () -> {
                    invalidateEvents();
                    if (onSuccess != null) {
                        onSuccess.onSuccess(null);
                    }
                },
                onFailure);
    }

    private void removeEntrantAndFixOrganizedEvents(String deviceId,
                                                    OnSuccessListener<Void> onSuccess,
                                                    OnFailureListener onFailure) {
        if (deviceId == null || deviceId.trim().isEmpty()) {
            if (onFailure != null) {
                onFailure.onFailure(new IllegalArgumentException("deviceId required"));
            }
            return;
        }
        final String id = deviceId.trim();
        runOrganizerRemovalPhase(id,
                () -> {
                    invalidateEvents();
                    waitingListDB.removeUserFromAllWaitlists(id,
                            v -> profileDB.deleteProfile(id,
                                    unused -> {
                                        invalidateProfiles();
                                        if (onSuccess != null) {
                                            onSuccess.onSuccess(null);
                                        }
                                    },
                                    e -> {
                                        if (isFirestoreNotFound(e)) {
                                            invalidateProfiles();
                                            if (onSuccess != null) {
                                                onSuccess.onSuccess(null);
                                            }
                                        } else if (onFailure != null) {
                                            onFailure.onFailure(e);
                                        }
                                    }),
                            onFailure);
                },
                onFailure);
    }

    /**
     * US 03.08.01: Load all notification documents for read-only review (not session-cached).
     */
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

    /**
     * US 03.10.01: Map each event to its top-level comments (for admin Comments tab); events with no comments are omitted.
     */
    public void getAllCommentsGroupedByEvent(
            OnSuccessListener<java.util.Map<Event, List<Comment>>> onSuccess,
            OnFailureListener onFailure) {
        getAllEvents(events -> {
            if (events == null || events.isEmpty()) {
                onSuccess.onSuccess(new java.util.LinkedHashMap<>());
                return;
            }
            java.util.Map<Event, List<Comment>> result = new java.util.LinkedHashMap<>();
            int[] remaining = {events.size()};
            for (Event event : events) {
                String eventId = event.getEventId();
                if (eventId == null || eventId.trim().isEmpty()) {
                    if (--remaining[0] == 0) onSuccess.onSuccess(result);
                    continue;
                }
                db.collection("events")
                        .document(eventId)
                        .collection("comments")
                        .get()
                        .addOnSuccessListener(snapshot -> {
                            List<Comment> comments = new java.util.ArrayList<>();
                            for (com.google.firebase.firestore.DocumentSnapshot doc : snapshot.getDocuments()) {
                                Comment c = doc.toObject(Comment.class);
                                if (c != null) {
                                    if (c.getId() == null) c.setId(doc.getId());
                                    if (c.getEventId() == null) c.setEventId(eventId);
                                    comments.add(c);
                                }
                            }
                            if (!comments.isEmpty()) result.put(event, comments);
                            if (--remaining[0] == 0) onSuccess.onSuccess(result);
                        })
                        .addOnFailureListener(e -> {
                            if (--remaining[0] == 0) onSuccess.onSuccess(result);
                        });
            }
        }, onFailure);
    }

    /** US 03.10.01: Delete a comment document under an event. */
    public void removeComment(String eventId, String commentId,
                              OnSuccessListener<Void> onSuccess,
                              OnFailureListener onFailure) {
        db.collection("events")
                .document(eventId)
                .collection("comments")
                .document(commentId)
                .delete()
                .addOnSuccessListener(onSuccess)
                .addOnFailureListener(onFailure);
    }

    /** US 03.10.01: Delete one reply document under a comment. */
    public void removeReply(String eventId, String commentId, String replyId,
                            OnSuccessListener<Void> onSuccess,
                            OnFailureListener onFailure) {
        db.collection("events")
                .document(eventId)
                .collection("comments")
                .document(commentId)
                .collection("replies")
                .document(replyId)
                .delete()
                .addOnSuccessListener(onSuccess)
                .addOnFailureListener(onFailure);
    }
}