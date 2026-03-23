package com.example.cobaltevents.db;

import com.example.cobaltevents.model.WaitingList;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.AggregateSource;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.Source;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public class WaitingListDB {

    /** Failure reason when {@link #addRegistrationWithJoinChecks} rejects (at capacity). */
    public static final String REASON_WAITLIST_FULL = "WAITLIST_FULL";
    /** Failure reason when registration close time has passed. */
    public static final String REASON_REGISTRATION_CLOSED = "REGISTRATION_CLOSED";

    private final FirebaseFirestore db;
    private static final String COLLECTION_WAITLISTS = "waitlists";
    private static final String SUBCOLLECTION_ENTRIES = "entries";

    public WaitingListDB() {
        this.db = FirebaseFirestore.getInstance();
    }

    public void getEntrantsForEvent(String eventId,
                                    OnSuccessListener<List<WaitingList>> onSuccess,
                                    OnFailureListener onFailure) {
        if (eventId == null || eventId.isEmpty()) {
            if (onFailure != null) onFailure.onFailure(new IllegalArgumentException("eventId required"));
            return;
        }
        db.collection(COLLECTION_WAITLISTS)
                .document(eventId)
                .collection(SUBCOLLECTION_ENTRIES)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    List<WaitingList> list = new ArrayList<>();
                    for (QueryDocumentSnapshot doc : querySnapshot) {
                        WaitingList reg = doc.toObject(WaitingList.class);
                        if (reg != null) {
                            reg.setEventId(eventId);
                            list.add(reg);
                        }
                    }
                    onSuccess.onSuccess(list);
                })
                .addOnFailureListener(onFailure);
    }

    /**
     * All waitlist entry docs for this device across every event (one Firestore round trip).
     * Paths are waitlists/{eventId}/entries/{deviceId}; eventId is the waitlist document id.
     */
    public void getEntrantHistory(String deviceId, OnSuccessListener<List<WaitingList>> onSuccess, OnFailureListener onFailure) {
        if (deviceId == null || deviceId.isEmpty()) {
            onSuccess.onSuccess(new ArrayList<>());
            return;
        }
        db.collectionGroup(SUBCOLLECTION_ENTRIES)
                .whereEqualTo("deviceId", deviceId)
                .get(Source.SERVER)
                .addOnSuccessListener(querySnapshot -> {
                    List<WaitingList> registrations = new ArrayList<>();
                    for (QueryDocumentSnapshot doc : querySnapshot) {
                        WaitingList reg = doc.toObject(WaitingList.class);
                        if (reg == null) continue;
                        String pathEventId = null;
                        CollectionReference entriesCollection = doc.getReference().getParent();
                        if (entriesCollection != null && entriesCollection.getParent() != null) {
                            pathEventId = entriesCollection.getParent().getId();
                        }
                        // Prefer eventId stored on the document (matches Event list / join flows).
                        // Parent doc id can differ in some schemas; overwriting broke event-card matching.
                        String eventId = reg.getEventId();
                        if (eventId == null || eventId.isEmpty()) {
                            eventId = doc.getString("eventId");
                            if (eventId != null) {
                                reg.setEventId(eventId);
                            }
                        }
                        if (eventId == null || eventId.isEmpty()) {
                            eventId = pathEventId;
                            if (eventId != null) {
                                reg.setEventId(eventId);
                            }
                        }
                        if (eventId == null || eventId.isEmpty()) {
                            continue;
                        }
                        registrations.add(reg);
                    }
                    onSuccess.onSuccess(registrations);
                })
                .addOnFailureListener(err -> {
                    getEntrantHistoryByEventScan(deviceId, onSuccess, onFailure);
                });
    }

    private void getEntrantHistoryByEventScan(String deviceId,
                                              OnSuccessListener<List<WaitingList>> onSuccess,
                                              OnFailureListener onFailure) {
        db.collection("events").get(Source.SERVER)
                .addOnSuccessListener(eventSnapshot -> {
                    if (eventSnapshot == null || eventSnapshot.isEmpty()) {
                        onSuccess.onSuccess(new ArrayList<>());
                        return;
                    }
                    List<String> eventIds = new ArrayList<>();
                    for (QueryDocumentSnapshot doc : eventSnapshot) {
                        eventIds.add(doc.getId());
                    }
                    if (eventIds.isEmpty()) {
                        onSuccess.onSuccess(new ArrayList<>());
                        return;
                    }

                    List<WaitingList> registrations = new ArrayList<>();
                    AtomicInteger pending = new AtomicInteger(eventIds.size());

                    for (String eventId : eventIds) {
                        db.collection(COLLECTION_WAITLISTS)
                                .document(eventId)
                                .collection(SUBCOLLECTION_ENTRIES)
                                .document(deviceId)
                                .get(Source.SERVER)
                                .addOnSuccessListener(docSnapshot -> {
                                    if (docSnapshot != null && docSnapshot.exists()) {
                                        WaitingList reg = docSnapshot.toObject(WaitingList.class);
                                        if (reg != null) {
                                            if (reg.getEventId() == null || reg.getEventId().isEmpty()) {
                                                reg.setEventId(eventId);
                                            }
                                            registrations.add(reg);
                                        }
                                    }
                                    if (pending.decrementAndGet() == 0) {
                                        onSuccess.onSuccess(registrations);
                                    }
                                })
                                .addOnFailureListener(e -> {
                                    if (pending.decrementAndGet() == 0) {
                                        onSuccess.onSuccess(registrations);
                                    }
                                });
                    }
                })
                .addOnFailureListener(onFailure);
    }

    public void addRegistration(WaitingList registration, OnSuccessListener<String> onSuccess, OnFailureListener onFailure) {
        String eventId = registration.getEventId();
        String deviceId = registration.getDeviceId();
        if (eventId == null || eventId.isEmpty() || deviceId == null || deviceId.isEmpty()) {
            if (onFailure != null) onFailure.onFailure(new IllegalArgumentException("eventId and deviceId required"));
            return;
        }
        db.collection(COLLECTION_WAITLISTS)
                .document(eventId)
                .collection(SUBCOLLECTION_ENTRIES)
                .document(deviceId)
                .set(registration)
                .addOnSuccessListener(aVoid -> onSuccess.onSuccess(deviceId))
                .addOnFailureListener(onFailure);
    }

    /**
     * Writes a waitlist entry only if registration is still open and the event is not at capacity.
     * <ul>
     *   <li>{@code registrationClose} non-null and before {@link Timestamp#now()} → rejected (stale UI).</li>
     *   <li>{@code waitingListCapacity} &gt; 0 and active count &gt;= capacity → rejected (server count).</li>
     * </ul>
     * Unlimited capacity: {@code waitingListCapacity} &lt;= 0 skips count check.
     */
    public void addRegistrationWithJoinChecks(WaitingList registration, int waitingListCapacity,
                                              Timestamp registrationClose,
                                              OnSuccessListener<String> onSuccess,
                                              OnFailureListener onFailure) {
        String eventId = registration.getEventId();
        String deviceId = registration.getDeviceId();
        if (eventId == null || eventId.isEmpty() || deviceId == null || deviceId.isEmpty()) {
            if (onFailure != null) {
                onFailure.onFailure(new IllegalArgumentException("eventId and deviceId required"));
            }
            return;
        }
        if (registrationClose != null) {
            Timestamp now = Timestamp.now();
            if (registrationClose.compareTo(now) < 0) {
                if (onFailure != null) {
                    onFailure.onFailure(new IllegalStateException(REASON_REGISTRATION_CLOSED));
                }
                return;
            }
        }
        if (waitingListCapacity <= 0) {
            addRegistration(registration, onSuccess, onFailure);
            return;
        }
        getActiveCountForEvent(eventId,
                count -> {
                    if (count >= waitingListCapacity) {
                        if (onFailure != null) {
                            onFailure.onFailure(new IllegalStateException(REASON_WAITLIST_FULL));
                        }
                        return;
                    }
                    addRegistration(registration, onSuccess, onFailure);
                },
                onFailure);
    }

    public void getActiveRegistrationForEvent(String eventId,
                                              String deviceId,
                                              OnSuccessListener<WaitingList> onSuccess,
                                              OnFailureListener onFailure) {
        db.collection(COLLECTION_WAITLISTS)
                .document(eventId)
                .collection(SUBCOLLECTION_ENTRIES)
                .document(deviceId)
                .get()
                .addOnSuccessListener(docSnapshot -> {
                    if (docSnapshot == null || !docSnapshot.exists()) {
                        onSuccess.onSuccess(null);
                        return;
                    }
                    WaitingList reg = docSnapshot.toObject(WaitingList.class);
                    if (reg == null) {
                        onSuccess.onSuccess(null);
                        return;
                    }
                    reg.setEventId(eventId);
                    String status = reg.getStatus();
                    boolean isActive = isEntryActive(status);
                    onSuccess.onSuccess(isActive ? reg : null);
                })
                .addOnFailureListener(onFailure);
    }

    /**
     * Fetch a registration document for the given event/device regardless of status.
     * Returns the WaitingList object if the document exists, otherwise null.
     */
    public void getRegistrationForEventAnyStatus(String eventId,
                                                 String deviceId,
                                                 OnSuccessListener<WaitingList> onSuccess,
                                                 OnFailureListener onFailure) {
        db.collection(COLLECTION_WAITLISTS)
                .document(eventId)
                .collection(SUBCOLLECTION_ENTRIES)
                .document(deviceId)
                .get(Source.SERVER)
                .addOnSuccessListener(docSnapshot -> {
                    if (docSnapshot == null || !docSnapshot.exists()) {
                        onSuccess.onSuccess(null);
                        return;
                    }
                    WaitingList reg = docSnapshot.toObject(WaitingList.class);
                    if (reg != null) reg.setEventId(eventId);
                    onSuccess.onSuccess(reg);
                })
                .addOnFailureListener(onFailure);
    }

    public void updateStatus(String eventId, String registrationId, String status, OnSuccessListener<Void> onSuccess, OnFailureListener onFailure) {
        db.collection(COLLECTION_WAITLISTS)
                .document(eventId)
                .collection(SUBCOLLECTION_ENTRIES)
                .document(registrationId)
                .update("status", status)
                .addOnSuccessListener(onSuccess)
                .addOnFailureListener(onFailure);
    }

    public void deleteRegistration(String eventId, String deviceId, OnSuccessListener<Void> onSuccess, OnFailureListener onFailure) {
        db.collection(COLLECTION_WAITLISTS)
                .document(eventId)
                .collection(SUBCOLLECTION_ENTRIES)
                .document(deviceId)
                .delete()
                .addOnSuccessListener(onSuccess)
                .addOnFailureListener(onFailure);
    }

    /**
     * Server-side count of active waitlist entries (no document downloads).
     * Uses Firestore's {@code whereIn} + {@code count()} aggregation so only
     * a single number crosses the wire instead of every entry document.
     */
    public void getActiveCountForEvent(String eventId,
                                       OnSuccessListener<Integer> onSuccess,
                                       OnFailureListener onFailure) {
        List<String> activeStatuses = java.util.Arrays.asList(
                WaitingList.STATUS_PENDING,
                WaitingList.STATUS_SELECTED,
                WaitingList.STATUS_ENROLLED,
                WaitingList.STATUS_NOT_SELECTED
        );
        Query activeQuery = db.collection(COLLECTION_WAITLISTS)
                .document(eventId)
                .collection(SUBCOLLECTION_ENTRIES)
                .whereIn("status", activeStatuses);
        activeQuery.count()
                .get(AggregateSource.SERVER)
                .addOnSuccessListener(snapshot ->
                        onSuccess.onSuccess((int) snapshot.getCount()))
                .addOnFailureListener(onFailure);
    }

    /**
     * Removes the user's waitlist entry from every event by scanning all events
     * and deleting the per-event entry doc directly.
     * This avoids the collectionGroup index requirement.
     */
    public void removeUserFromAllWaitlists(String deviceId,
                                           OnSuccessListener<Void> onSuccess,
                                           OnFailureListener onFailure) {
        db.collection("events").get()
                .addOnSuccessListener(eventSnapshot -> {
                    if (eventSnapshot.isEmpty()) {
                        onSuccess.onSuccess(null);
                        return;
                    }
                    List<String> eventIds = new ArrayList<>();
                    for (QueryDocumentSnapshot doc : eventSnapshot) {
                        eventIds.add(doc.getId());
                    }
                    AtomicInteger pending = new AtomicInteger(eventIds.size());
                    AtomicReference<Exception> firstError = new AtomicReference<>(null);

                    for (String eventId : eventIds) {
                        db.collection(COLLECTION_WAITLISTS)
                                .document(eventId)
                                .collection(SUBCOLLECTION_ENTRIES)
                                .document(deviceId)
                                .delete()
                                .addOnSuccessListener(v -> {
                                    if (pending.decrementAndGet() == 0) {
                                        if (firstError.get() != null) {
                                            onFailure.onFailure(firstError.get());
                                        } else {
                                            onSuccess.onSuccess(null);
                                        }
                                    }
                                })
                                .addOnFailureListener(e -> {
                                    firstError.compareAndSet(null, e);
                                    if (pending.decrementAndGet() == 0) {
                                        onFailure.onFailure(firstError.get());
                                    }
                                });
                    }
                })
                .addOnFailureListener(onFailure);
    }

    public void saveLocation(String eventId, String deviceId,
                             double latitude, double longitude,
                             OnSuccessListener<Void> onSuccess,
                             OnFailureListener onFailure) {
        java.util.Map<String, Object> update = new java.util.HashMap<>();
        update.put("latitude", latitude);
        update.put("longitude", longitude);
        db.collection(COLLECTION_WAITLISTS)
                .document(eventId)
                .collection(SUBCOLLECTION_ENTRIES)
                .document(deviceId)
                .update(update)
                .addOnSuccessListener(onSuccess)
                .addOnFailureListener(onFailure);
    }

    private static boolean isEntryActive(String status) {
        if (status == null) return true;
        return WaitingList.STATUS_PENDING.equals(status)
                || WaitingList.STATUS_SELECTED.equals(status)
                || WaitingList.STATUS_ENROLLED.equals(status)
                // X / "not-selected" means the user remains on the waitlist.
                || WaitingList.STATUS_NOT_SELECTED.equals(status);
    }
}
