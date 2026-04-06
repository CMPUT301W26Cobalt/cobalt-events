package com.example.cobaltevents.db;

import com.example.cobaltevents.model.Event;
import com.example.cobaltevents.model.LotteryErrorCodes;
import com.example.cobaltevents.model.DeclineSelectionInviteOutcome;
import com.example.cobaltevents.model.RescindSelectionInviteOutcome;
import com.example.cobaltevents.model.WaitingList;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.AggregateSource;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.firestore.Transaction;
import com.google.firebase.firestore.Source;
import com.google.firebase.firestore.WriteBatch;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public class WaitingListDB {

    /** Failure reason when {@link #addRegistrationWithJoinChecks} rejects (at capacity). */
    public static final String REASON_WAITLIST_FULL = "WAITLIST_FULL";
    /** Failure reason when registration close time has passed. */
    public static final String REASON_REGISTRATION_CLOSED = "REGISTRATION_CLOSED";
    /** Failure reason when the entrant is an organizer of this event (cannot join own event). */
    public static final String REASON_ORGANIZER_CANNOT_JOIN = "ORGANIZER_CANNOT_JOIN";
    /** Event document is missing (e.g. deleted); join must not proceed. */
    public static final String REASON_EVENT_DELETED = "EVENT_DELETED";

    private final FirebaseFirestore db;
    private static final String COLLECTION_EVENTS = "events";
    private static final String COLLECTION_WAITLISTS = "waitlists";
    private static final String SUBCOLLECTION_ENTRIES = "entries";
    /** Firestore caps document reads per transaction (~500); we prefetch IDs then {@link Transaction#get(DocumentReference)}. */
    private static final int MAX_FIRESTORE_TRANSACTION_READS = 500;

    /**
     * Subcollection entry document id for reads/updates. Prefer {@link WaitingList#getId()} (Firestore
     * doc id) so writes match {@code entries/{docId}} even if the stored {@code deviceId} field is wrong.
     */
    private static String entryDocId(WaitingList w) {
        if (w == null) return null;
        String id = w.getId();
        if (id != null && !id.isEmpty()) return id;
        String d = w.getDeviceId();
        if (d != null && !d.isEmpty()) return d;
        return null;
    }

    private static List<String> uniqueEntryDocIds(List<WaitingList> allRegs) {
        LinkedHashSet<String> ids = new LinkedHashSet<>();
        if (allRegs != null) {
            for (WaitingList r : allRegs) {
                String id = entryDocId(r);
                if (id != null && !id.isEmpty()) {
                    ids.add(id);
                }
            }
        }
        return new ArrayList<>(ids);
    }

    /**
     * Picks {@code requestedCount} replacement entrants, respecting invite capacity like
     * {@link #runLotteryDrawTransactional}: each pending/not_selected pick consumes one spot;
     * re-picking someone already {@link WaitingList#STATUS_SELECTED} consumes none.
     */
    private static List<WaitingList> pickReplacementAssigneesWithCapacity(
            List<WaitingList> replacementCandidates,
            int requestedCount,
            int spotsAvailable,
            int capacity) {
        if (requestedCount <= 0 || replacementCandidates.isEmpty()) {
            return new ArrayList<>();
        }
        if (capacity <= 0) {
            Collections.shuffle(replacementCandidates);
            return new ArrayList<>(replacementCandidates.subList(0, requestedCount));
        }
        List<WaitingList> needSlot = new ArrayList<>();
        List<WaitingList> alreadySel = new ArrayList<>();
        for (WaitingList w : replacementCandidates) {
            String st = w.getStatus();
            if (WaitingList.STATUS_PENDING.equals(st) || WaitingList.STATUS_NOT_SELECTED.equals(st)) {
                needSlot.add(w);
            } else if (WaitingList.STATUS_SELECTED.equals(st)) {
                alreadySel.add(w);
            }
        }
        int minA = Math.max(0, requestedCount - alreadySel.size());
        int maxA = Math.min(Math.min(requestedCount, needSlot.size()), spotsAvailable);
        if (minA > maxA) {
            throw new IllegalArgumentException(LotteryErrorCodes.NO_CAPACITY);
        }
        int a = minA + ThreadLocalRandom.current().nextInt(maxA - minA + 1);
        Collections.shuffle(needSlot);
        Collections.shuffle(alreadySel);
        List<WaitingList> out = new ArrayList<>(needSlot.subList(0, a));
        out.addAll(alreadySel.subList(0, requestedCount - a));
        return out;
    }

    public WaitingListDB() {
        this.db = FirebaseFirestore.getInstance();
    }

    /**
     * Ensures {@code waitlists/{eventId}} exists as the parent for the {@code entries} subcollection.
     * The waitlist document id is the same as the event id; store it in {@link com.example.cobaltevents.model.Event#setWaitingListId}.
     */
    public void createWaitlistContainerForEvent(String eventId,
                                                OnSuccessListener<Void> onSuccess,
                                                OnFailureListener onFailure) {
        if (eventId == null || eventId.isEmpty()) {
            if (onFailure != null) {
                onFailure.onFailure(new IllegalArgumentException("eventId required"));
            }
            return;
        }
        Map<String, Object> data = new HashMap<>();
        data.put("eventId", eventId);
        data.put("createdAt", FieldValue.serverTimestamp());
        db.collection(COLLECTION_WAITLISTS)
                .document(eventId)
                .set(data, SetOptions.merge())
                .addOnSuccessListener(unused -> {
                    if (onSuccess != null) {
                        onSuccess.onSuccess(null);
                    }
                })
                .addOnFailureListener(onFailure);
    }

    public void getEntrantsForEvent(String eventId,
                                    OnSuccessListener<List<WaitingList>> onSuccess,
                                    OnFailureListener onFailure) {
        getEntrantsForEvent(eventId, Source.DEFAULT, onSuccess, onFailure);
    }

    /**
     * Loads all waitlist entry documents for an event. Pass {@link Source#SERVER} to bypass cache.
     */
    public void getEntrantsForEvent(String eventId,
                                    Source source,
                                    OnSuccessListener<List<WaitingList>> onSuccess,
                                    OnFailureListener onFailure) {
        if (eventId == null || eventId.isEmpty()) {
            if (onFailure != null) onFailure.onFailure(new IllegalArgumentException("eventId required"));
            return;
        }
        db.collection(COLLECTION_WAITLISTS)
                .document(eventId)
                .collection(SUBCOLLECTION_ENTRIES)
                .get(source)
                .addOnSuccessListener(querySnapshot -> {
                    List<WaitingList> list = new ArrayList<>();
                    for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
                        WaitingList reg = doc.toObject(WaitingList.class);
                        if (reg != null) {
                            reg.setEventId(eventId);
                            reg.setId(doc.getId());
                            if (reg.getDeviceId() == null || reg.getDeviceId().isEmpty()) {
                                reg.setDeviceId(doc.getId());
                            }
                            list.add(reg);
                        }
                    }
                    onSuccess.onSuccess(list);
                })
                .addOnFailureListener(onFailure);
    }

    /**
     * Server-side count of entries with a single {@code status} value (e.g. pending, enrolled).
     */
    public void countEntriesWithStatus(String eventId,
                                       String status,
                                       OnSuccessListener<Integer> onSuccess,
                                       OnFailureListener onFailure) {
        if (eventId == null || eventId.isEmpty() || status == null) {
            if (onFailure != null) {
                onFailure.onFailure(new IllegalArgumentException("eventId and status required"));
            }
            return;
        }
        db.collection(COLLECTION_WAITLISTS)
                .document(eventId)
                .collection(SUBCOLLECTION_ENTRIES)
                .whereEqualTo("status", status)
                .count()
                .get(AggregateSource.SERVER)
                .addOnSuccessListener(snapshot -> onSuccess.onSuccess((int) snapshot.getCount()))
                .addOnFailureListener(onFailure);
    }

    /**
     * Server-side count of entrants eligible for a lottery draw:
     * {@link WaitingList#STATUS_PENDING} + {@link WaitingList#STATUS_NOT_SELECTED}.
     */
    public void countLotteryEligibleEntries(String eventId,
                                            OnSuccessListener<Integer> onSuccess,
                                            OnFailureListener onFailure) {
        if (eventId == null || eventId.isEmpty()) {
            if (onFailure != null) {
                onFailure.onFailure(new IllegalArgumentException("eventId required"));
            }
            return;
        }
        db.collection(COLLECTION_WAITLISTS)
                .document(eventId)
                .collection(SUBCOLLECTION_ENTRIES)
                .whereIn("status", java.util.Arrays.asList(
                        WaitingList.STATUS_PENDING,
                        WaitingList.STATUS_NOT_SELECTED))
                .count()
                .get(AggregateSource.SERVER)
                .addOnSuccessListener(snapshot -> onSuccess.onSuccess((int) snapshot.getCount()))
                .addOnFailureListener(onFailure);
    }

    /** Server-side count of declined entrants who still need replacements. */
    public void countDeclinedNeedReplacementEntries(String eventId,
                                                    OnSuccessListener<Integer> onSuccess,
                                                    OnFailureListener onFailure) {
        countEntriesWithStatus(eventId, WaitingList.STATUS_DECLINED, onSuccess, onFailure);
    }

    /** Server-side count of declined entrants for whom a replacement has already been found. */
    public void countDeclinedFoundReplacementEntries(String eventId,
                                                     OnSuccessListener<Integer> onSuccess,
                                                     OnFailureListener onFailure) {
        countEntriesWithStatus(eventId, WaitingList.STATUS_DECLINED_FOUND_REPLACEMENT, onSuccess, onFailure);
    }

    /** Server-side count of entrants who can be chosen as replacements (pending, not selected, or selected). */
    public void countReplacementPoolEntries(String eventId,
                                            OnSuccessListener<Integer> onSuccess,
                                            OnFailureListener onFailure) {
        if (eventId == null || eventId.isEmpty()) {
            if (onFailure != null) {
                onFailure.onFailure(new IllegalArgumentException("eventId required"));
            }
            return;
        }
        db.collection(COLLECTION_WAITLISTS)
                .document(eventId)
                .collection(SUBCOLLECTION_ENTRIES)
                .whereIn("status", java.util.Arrays.asList(
                        WaitingList.STATUS_PENDING,
                        WaitingList.STATUS_NOT_SELECTED,
                        WaitingList.STATUS_SELECTED))
                .count()
                .get(AggregateSource.SERVER)
                .addOnSuccessListener(snapshot -> onSuccess.onSuccess((int) snapshot.getCount()))
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
                    for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
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
                    for (DocumentSnapshot doc : eventSnapshot.getDocuments()) {
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
        db.collection(COLLECTION_EVENTS)
                .document(eventId)
                .get(Source.SERVER)
                .addOnSuccessListener(snapshot -> {
                    Event event = null;
                    if (snapshot != null && snapshot.exists()) {
                        event = snapshot.toObject(Event.class);
                        if (event != null) {
                            event.setEventId(snapshot.getId());
                        }
                    }
                    if (snapshot == null || !snapshot.exists() || event == null) {
                        if (onFailure != null) {
                            onFailure.onFailure(new IllegalStateException(REASON_EVENT_DELETED));
                        }
                        return;
                    }
                    if (event.isDeviceAnOrganizer(deviceId)) {
                        if (onFailure != null) {
                            onFailure.onFailure(new IllegalStateException(REASON_ORGANIZER_CANNOT_JOIN));
                        }
                        return;
                    }
                    addRegistrationWithCapacityChecks(registration, waitingListCapacity, onSuccess, onFailure);
                })
                .addOnFailureListener(onFailure);
    }

    private void addRegistrationWithCapacityChecks(WaitingList registration,
                                                   int waitingListCapacity,
                                                   OnSuccessListener<String> onSuccess,
                                                   OnFailureListener onFailure) {
        String eventId = registration.getEventId();
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
                    if (reg != null) {
                        reg.setEventId(eventId);
                        reg.setId(docSnapshot.getId());
                    }
                    onSuccess.onSuccess(reg);
                })
                .addOnFailureListener(onFailure);
    }

    /**
     * Reads waitlist entry documents by id using only {@link Transaction#get(DocumentReference)} so the
     * code compiles on Firestore Android versions that do not expose {@code Transaction#get(Query)}.
     */
    private List<WaitingList> readWaitlistEntriesInTransaction(
            Transaction transaction,
            CollectionReference entriesCol,
            String eventId,
            List<String> entryDocIds) throws FirebaseFirestoreException {
        List<WaitingList> allRegs = new ArrayList<>();
        for (String id : entryDocIds) {
            DocumentSnapshot doc = transaction.get(entriesCol.document(id));
            if (!doc.exists()) {
                continue;
            }
            WaitingList w = doc.toObject(WaitingList.class);
            if (w == null) {
                continue;
            }
            w.setId(doc.getId());
            if (w.getDeviceId() == null || w.getDeviceId().isEmpty()) {
                w.setDeviceId(doc.getId());
            }
            w.setEventId(eventId);
            allRegs.add(w);
        }
        return allRegs;
    }

    /**
     * Runs a lottery draw in a single Firestore transaction: reads pending entries and event, applies
     * winner/loser status updates atomically. Concurrent draws serialize; the second sees updated data
     * and fails if not enough pending entrants remain.
     * <p>
     * Entry ids are prefetched from the server, then re-read inside the transaction so concurrent
     * updates are visible and Firestore’s document-read API matches all supported SDKs.
     */
    public void runLotteryDrawTransactional(String eventId,
                                          int requestedCount,
                                          OnSuccessListener<LotteryDrawOutcome> onSuccess,
                                          OnFailureListener onFailure) {
        if (eventId == null || eventId.isEmpty()) {
            if (onFailure != null) {
                onFailure.onFailure(new IllegalArgumentException("eventId required"));
            }
            return;
        }
        if (requestedCount <= 0) {
            if (onFailure != null) {
                onFailure.onFailure(new IllegalArgumentException("requestedCount must be positive"));
            }
            return;
        }

        getEntrantsForEvent(eventId, Source.SERVER, allRegs -> {
            List<String> entryDocIds = uniqueEntryDocIds(allRegs);
            if (entryDocIds.size() > MAX_FIRESTORE_TRANSACTION_READS) {
                if (onFailure != null) {
                    onFailure.onFailure(new IllegalStateException(LotteryErrorCodes.TOO_MANY_WAITS));
                }
                return;
            }

            db.runTransaction(transaction -> {
                DocumentSnapshot eventSnap = transaction.get(
                        db.collection(COLLECTION_EVENTS).document(eventId));
                if (!eventSnap.exists()) {
                    throw new IllegalStateException("Event not found");
                }
                Event event = eventSnap.toObject(Event.class);
                if (event != null) {
                    event.setEventId(eventId);
                }

                CollectionReference entriesCol = db.collection(COLLECTION_WAITLISTS)
                        .document(eventId)
                        .collection(SUBCOLLECTION_ENTRIES);
                List<WaitingList> regs = readWaitlistEntriesInTransaction(
                        transaction, entriesCol, eventId, entryDocIds);

                List<WaitingList> drawEligible = regs.stream()
                        .filter(r -> WaitingList.STATUS_PENDING.equals(r.getStatus())
                                || WaitingList.STATUS_NOT_SELECTED.equals(r.getStatus()))
                        .collect(Collectors.toList());

                if (drawEligible.isEmpty()) {
                    throw new IllegalStateException(LotteryErrorCodes.NO_PENDING_ENTRANTS);
                }

                int alreadySelected = 0;
                for (WaitingList reg : regs) {
                    if (WaitingList.STATUS_SELECTED.equals(reg.getStatus())) {
                        alreadySelected++;
                    }
                }

                List<String> confirmed = event != null ? event.getConfirmedAttendeeIds() : null;
                int confirmedCount = confirmed == null ? 0 : confirmed.size();
                int capacity = event != null ? event.getWaitingListCapacity() : 0;
                int spotsAvailable;
                if (capacity <= 0) {
                    spotsAvailable = Integer.MAX_VALUE / 4;
                } else {
                    spotsAvailable = capacity - confirmedCount - alreadySelected;
                }

                if (spotsAvailable <= 0) {
                    throw new IllegalArgumentException(LotteryErrorCodes.NO_CAPACITY);
                }

                if (requestedCount > drawEligible.size()) {
                    throw new IllegalArgumentException(LotteryErrorCodes.REQUEST_EXCEEDS_PENDING);
                }
                if (capacity > 0 && requestedCount > spotsAvailable) {
                    throw new IllegalArgumentException(LotteryErrorCodes.NO_CAPACITY);
                }

                Collections.shuffle(drawEligible);
                List<WaitingList> winners = new ArrayList<>(drawEligible.subList(0, requestedCount));
                Set<String> winnerIds = winners.stream()
                        .map(WaitingList::getDeviceId)
                        .collect(Collectors.toCollection(HashSet::new));
                List<WaitingList> losers = drawEligible.stream()
                        .filter(w -> !winnerIds.contains(w.getDeviceId()))
                        .collect(Collectors.toList());

                String eventName = event != null && event.getName() != null ? event.getName() : "the event";

                for (WaitingList w : winners) {
                    transaction.update(entriesCol.document(entryDocId(w)),
                            "status", WaitingList.STATUS_SELECTED);
                }
                for (WaitingList l : losers) {
                    transaction.update(entriesCol.document(entryDocId(l)),
                            "status", WaitingList.STATUS_NOT_SELECTED);
                }

                List<String> winnerDeviceIds = new ArrayList<>();
                for (WaitingList w : winners) {
                    winnerDeviceIds.add(w.getDeviceId());
                }
                List<String> loserDeviceIds = new ArrayList<>();
                for (WaitingList l : losers) {
                    loserDeviceIds.add(l.getDeviceId());
                }

                return new LotteryDrawOutcome(winners.size(), winnerDeviceIds, loserDeviceIds, eventName);
            }).addOnSuccessListener(onSuccess).addOnFailureListener(onFailure);
        }, onFailure);
    }

    /**
     * Picks one random pending entrant and sets them to {@link WaitingList#STATUS_SELECTED}, or does nothing
     * if no one is pending (success with {@link ReplacementDrawOutcome#selectedDeviceId} null).
     * Uses a single transaction so concurrent replacement draws serialize correctly.
     */
    public void runReplacementDrawTransactional(String eventId,
                                                OnSuccessListener<ReplacementDrawOutcome> onSuccess,
                                                OnFailureListener onFailure) {
        if (eventId == null || eventId.isEmpty()) {
            if (onFailure != null) {
                onFailure.onFailure(new IllegalArgumentException("eventId required"));
            }
            return;
        }

        getEntrantsForEvent(eventId, Source.SERVER, allRegs -> {
            List<String> entryDocIds = uniqueEntryDocIds(allRegs);
            if (entryDocIds.size() > MAX_FIRESTORE_TRANSACTION_READS) {
                if (onFailure != null) {
                    onFailure.onFailure(new IllegalStateException(LotteryErrorCodes.TOO_MANY_WAITS));
                }
                return;
            }

            db.runTransaction(transaction -> {
                DocumentSnapshot eventSnap = transaction.get(
                        db.collection(COLLECTION_EVENTS).document(eventId));
                if (!eventSnap.exists()) {
                    throw new IllegalStateException("Event not found");
                }
                Event event = eventSnap.toObject(Event.class);
                if (event != null) {
                    event.setEventId(eventId);
                }
                String eventName = event != null && event.getName() != null ? event.getName() : "the event";

                CollectionReference entriesCol = db.collection(COLLECTION_WAITLISTS)
                        .document(eventId)
                        .collection(SUBCOLLECTION_ENTRIES);
                List<WaitingList> regs = readWaitlistEntriesInTransaction(
                        transaction, entriesCol, eventId, entryDocIds);

                List<WaitingList> drawEligible = regs.stream()
                        .filter(r -> WaitingList.STATUS_PENDING.equals(r.getStatus())
                                || WaitingList.STATUS_NOT_SELECTED.equals(r.getStatus()))
                        .collect(Collectors.toList());

                if (drawEligible.isEmpty()) {
                    return new ReplacementDrawOutcome(null, eventName);
                }

                int alreadySelected = 0;
                for (WaitingList reg : regs) {
                    if (WaitingList.STATUS_SELECTED.equals(reg.getStatus())) {
                        alreadySelected++;
                    }
                }

                List<String> confirmed = event != null ? event.getConfirmedAttendeeIds() : null;
                int confirmedCount = confirmed == null ? 0 : confirmed.size();
                int capacity = event != null ? event.getWaitingListCapacity() : 0;
                int spotsAvailable;
                if (capacity <= 0) {
                    spotsAvailable = Integer.MAX_VALUE / 4;
                } else {
                    spotsAvailable = capacity - confirmedCount - alreadySelected;
                }

                if (spotsAvailable <= 0) {
                    throw new IllegalArgumentException(LotteryErrorCodes.NO_CAPACITY);
                }

                Collections.shuffle(drawEligible);
                WaitingList replacement = drawEligible.get(0);

                transaction.update(entriesCol.document(entryDocId(replacement)),
                        "status", WaitingList.STATUS_SELECTED);

                return new ReplacementDrawOutcome(replacement.getDeviceId(), eventName);
            }).addOnSuccessListener(onSuccess).addOnFailureListener(onFailure);
        }, onFailure);
    }

    /** Result of {@link #runReplacementDrawTransactional}. */
    public static final class ReplacementDrawOutcome {
        /** Null when no pending entrant was available (still a successful no-op). */
        public final String selectedDeviceId;
        public final String eventName;

        public ReplacementDrawOutcome(String selectedDeviceId, String eventName) {
            this.selectedDeviceId = selectedDeviceId;
            this.eventName = eventName;
        }
    }

    /** Result for batch replacement lottery runs. */
    public static final class ReplacementLotteryOutcome {
        public final int replacementCount;
        public final List<String> selectedReplacementDeviceIds;
        public final List<String> declinedNowResolvedDeviceIds;
        public final String eventName;

        public ReplacementLotteryOutcome(int replacementCount,
                                         List<String> selectedReplacementDeviceIds,
                                         List<String> declinedNowResolvedDeviceIds,
                                         String eventName) {
            this.replacementCount = replacementCount;
            this.selectedReplacementDeviceIds = selectedReplacementDeviceIds;
            this.declinedNowResolvedDeviceIds = declinedNowResolvedDeviceIds;
            this.eventName = eventName;
        }
    }

    /**
     * Replacement lottery in one Firestore transaction (same concurrency model as {@link #runLotteryDrawTransactional}):
     * prefetch entry ids from the server, re-read every entry inside the transaction so concurrent runs serialize;
     * capacity / free invite slots match the main lottery ({@code alreadySelected}, confirmed attendees, {@code waitingListCapacity}).
     * Randomly picks N entrants from DECLINED → {@link WaitingList#STATUS_DECLINED_FOUND_REPLACEMENT}, and N from the
     * replacement pool (PENDING + NOT_SELECTED + SELECTED) → SELECTED. Picks respect remaining invite slots when
     * capacity is finite. Chosen replacements never reuse the same entry document as a chosen declined entrant.
     */
    public void runReplacementLotteryTransactional(String eventId,
                                                   int requestedCount,
                                                   OnSuccessListener<ReplacementLotteryOutcome> onSuccess,
                                                   OnFailureListener onFailure) {
        if (eventId == null || eventId.isEmpty()) {
            if (onFailure != null) onFailure.onFailure(new IllegalArgumentException("eventId required"));
            return;
        }
        if (requestedCount <= 0) {
            if (onFailure != null) onFailure.onFailure(new IllegalArgumentException("requestedCount must be positive"));
            return;
        }

        getEntrantsForEvent(eventId, Source.SERVER, allRegs -> {
            List<String> entryDocIds = uniqueEntryDocIds(allRegs);
            if (entryDocIds.size() > MAX_FIRESTORE_TRANSACTION_READS) {
                if (onFailure != null) {
                    onFailure.onFailure(new IllegalStateException(LotteryErrorCodes.TOO_MANY_WAITS));
                }
                return;
            }

            db.runTransaction(transaction -> {
                DocumentSnapshot eventSnap = transaction.get(
                        db.collection(COLLECTION_EVENTS).document(eventId));
                if (!eventSnap.exists()) {
                    throw new IllegalStateException("Event not found");
                }
                Event event = eventSnap.toObject(Event.class);
                if (event != null) event.setEventId(eventId);

                CollectionReference entriesCol = db.collection(COLLECTION_WAITLISTS)
                        .document(eventId)
                        .collection(SUBCOLLECTION_ENTRIES);
                List<WaitingList> regs = readWaitlistEntriesInTransaction(transaction, entriesCol, eventId, entryDocIds);

                List<WaitingList> declinedNeedReplacement = regs.stream()
                        .filter(r -> WaitingList.STATUS_DECLINED.equals(r.getStatus()))
                        .collect(Collectors.toList());
                Set<String> declinedEntryIds = new HashSet<>();
                for (WaitingList w : declinedNeedReplacement) {
                    String eid = entryDocId(w);
                    if (eid != null) {
                        declinedEntryIds.add(eid);
                    }
                }
                List<WaitingList> replacementPool = regs.stream()
                        .filter(r -> WaitingList.STATUS_PENDING.equals(r.getStatus())
                                || WaitingList.STATUS_NOT_SELECTED.equals(r.getStatus())
                                || WaitingList.STATUS_SELECTED.equals(r.getStatus()))
                        .filter(r -> {
                            String eid = entryDocId(r);
                            return eid == null || !declinedEntryIds.contains(eid);
                        })
                        .collect(Collectors.toList());

                int alreadySelected = 0;
                for (WaitingList reg : regs) {
                    if (WaitingList.STATUS_SELECTED.equals(reg.getStatus())) {
                        alreadySelected++;
                    }
                }
                List<String> confirmed = event != null ? event.getConfirmedAttendeeIds() : null;
                int confirmedCount = confirmed == null ? 0 : confirmed.size();
                int capacity = event != null ? event.getWaitingListCapacity() : 0;
                int spotsAvailable;
                if (capacity <= 0) {
                    spotsAvailable = Integer.MAX_VALUE / 4;
                } else {
                    spotsAvailable = capacity - confirmedCount - alreadySelected;
                }

                int maxReplaceable = Math.min(declinedNeedReplacement.size(), replacementPool.size());
                if (maxReplaceable <= 0) {
                    throw new IllegalStateException(LotteryErrorCodes.REQUEST_EXCEEDS_REPLACEMENT_CAPACITY);
                }
                if (requestedCount > maxReplaceable) {
                    throw new IllegalArgumentException(LotteryErrorCodes.REQUEST_EXCEEDS_REPLACEMENT_CAPACITY);
                }

                Collections.shuffle(declinedNeedReplacement);

                List<WaitingList> chosenDeclined = new ArrayList<>(
                        declinedNeedReplacement.subList(0, requestedCount));
                Set<String> chosenDeclinedIds = new HashSet<>();
                for (WaitingList w : chosenDeclined) {
                    String eid = entryDocId(w);
                    if (eid != null) {
                        chosenDeclinedIds.add(eid);
                    }
                }
                List<WaitingList> replacementCandidates = new ArrayList<>();
                for (WaitingList w : replacementPool) {
                    String eid = entryDocId(w);
                    if (eid == null || !chosenDeclinedIds.contains(eid)) {
                        replacementCandidates.add(w);
                    }
                }
                if (replacementCandidates.size() < requestedCount) {
                    throw new IllegalArgumentException(LotteryErrorCodes.REQUEST_EXCEEDS_REPLACEMENT_CAPACITY);
                }
                List<WaitingList> chosenReplacements = pickReplacementAssigneesWithCapacity(
                        replacementCandidates, requestedCount, spotsAvailable, capacity);

                for (WaitingList declined : chosenDeclined) {
                    transaction.update(entriesCol.document(entryDocId(declined)),
                            "status", WaitingList.STATUS_DECLINED_FOUND_REPLACEMENT);
                }
                for (WaitingList replacement : chosenReplacements) {
                    transaction.update(entriesCol.document(entryDocId(replacement)),
                            "status", WaitingList.STATUS_SELECTED);
                }

                List<String> replacementIds = new ArrayList<>();
                for (WaitingList replacement : chosenReplacements) {
                    replacementIds.add(replacement.getDeviceId());
                }
                List<String> resolvedDeclinedIds = new ArrayList<>();
                for (WaitingList declined : chosenDeclined) {
                    resolvedDeclinedIds.add(declined.getDeviceId());
                }

                String eventName = event != null && event.getName() != null ? event.getName() : "the event";
                return new ReplacementLotteryOutcome(requestedCount, replacementIds, resolvedDeclinedIds, eventName);
            }).addOnSuccessListener(onSuccess).addOnFailureListener(onFailure);
        }, onFailure);
    }

    /** Result of {@link #runLotteryDrawTransactional}; notifications are sent after the transaction commits. */
    public static final class LotteryDrawOutcome {
        public final int winnerCount;
        public final List<String> winnerDeviceIds;
        public final List<String> loserDeviceIds;
        public final String eventName;

        public LotteryDrawOutcome(int winnerCount,
                                  List<String> winnerDeviceIds,
                                  List<String> loserDeviceIds,
                                  String eventName) {
            this.winnerCount = winnerCount;
            this.winnerDeviceIds = winnerDeviceIds;
            this.loserDeviceIds = loserDeviceIds;
            this.eventName = eventName;
        }
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

    /**
     * Atomically moves one entrant from {@link WaitingList#STATUS_SELECTED} to
     * {@link WaitingList#STATUS_PENDING} only if still selected. If they already accepted
     * ({@link WaitingList#STATUS_ENROLLED}), returns {@link RescindSelectionInviteOutcome#ALREADY_ENROLLED}
     * and does not write.
     *
     * @param eventId      Firestore event id
     * @param entryDocId   waitlist subcollection document id for this entrant
     * @param onSuccess    delivers {@link RescindSelectionInviteOutcome}; never {@code null} on success path
     * @param onFailure    transaction or Firestore failure
     */
    public void rescindSelectionInviteIfStillSelected(String eventId,
                                                      String entryDocId,
                                                      OnSuccessListener<RescindSelectionInviteOutcome> onSuccess,
                                                      OnFailureListener onFailure) {
        if (eventId == null || eventId.isEmpty() || entryDocId == null || entryDocId.isEmpty()) {
            if (onFailure != null) {
                onFailure.onFailure(new IllegalArgumentException("eventId and entryDocId required"));
            }
            return;
        }
        DocumentReference ref = db.collection(COLLECTION_WAITLISTS)
                .document(eventId)
                .collection(SUBCOLLECTION_ENTRIES)
                .document(entryDocId);
        db.runTransaction(transaction -> {
            DocumentSnapshot snap = transaction.get(ref);
            if (snap == null || !snap.exists()) {
                throw new IllegalStateException(LotteryErrorCodes.WAITLIST_ENTRY_NOT_FOUND);
            }
            String st = snap.getString("status");
            if (WaitingList.STATUS_ENROLLED.equals(st)) {
                return RescindSelectionInviteOutcome.ALREADY_ENROLLED;
            }
            if (!WaitingList.STATUS_SELECTED.equals(st)) {
                return RescindSelectionInviteOutcome.NOT_INVITED_ANYMORE;
            }
            transaction.update(ref, "status", WaitingList.STATUS_PENDING);
            return RescindSelectionInviteOutcome.APPLIED;
        }).addOnSuccessListener(result -> {
            if (onSuccess != null) {
                onSuccess.onSuccess(result != null ? result : RescindSelectionInviteOutcome.NOT_INVITED_ANYMORE);
            }
        }).addOnFailureListener(e -> {
            if (onFailure != null) {
                onFailure.onFailure(e);
            }
        });
    }

    /**
     * One transaction: require waitlist {@code selected}, set {@code enrolled}, and add {@code entrantDeviceId}
     * to the event's {@code confirmedAttendeeIds}. Prevents rescind/accept races from leaving inconsistent
     * waitlist vs confirmed list.
     */
    public void acceptSelectedInvitationTransactional(String eventId,
                                                      String entryDocId,
                                                      String entrantDeviceId,
                                                      OnSuccessListener<Void> onSuccess,
                                                      OnFailureListener onFailure) {
        if (eventId == null || eventId.isEmpty()
                || entryDocId == null || entryDocId.isEmpty()
                || entrantDeviceId == null || entrantDeviceId.isEmpty()) {
            if (onFailure != null) {
                onFailure.onFailure(new IllegalArgumentException("eventId, entryDocId, and entrantDeviceId required"));
            }
            return;
        }
        DocumentReference entryRef = db.collection(COLLECTION_WAITLISTS)
                .document(eventId)
                .collection(SUBCOLLECTION_ENTRIES)
                .document(entryDocId);
        DocumentReference eventRef = db.collection(COLLECTION_EVENTS).document(eventId);
        db.runTransaction(transaction -> {
            DocumentSnapshot entrySnap = transaction.get(entryRef);
            if (entrySnap == null || !entrySnap.exists()) {
                throw new IllegalStateException(LotteryErrorCodes.WAITLIST_ENTRY_NOT_FOUND);
            }
            String st = entrySnap.getString("status");
            if (WaitingList.STATUS_ENROLLED.equals(st)) {
                throw new IllegalStateException(LotteryErrorCodes.INVITATION_ALREADY_ENROLLED);
            }
            if (!WaitingList.STATUS_SELECTED.equals(st)) {
                throw new IllegalStateException(LotteryErrorCodes.INVITATION_NOT_ACTIVE);
            }
            DocumentSnapshot eventSnap = transaction.get(eventRef);
            if (eventSnap == null || !eventSnap.exists()) {
                throw new IllegalStateException("Event not found");
            }
            List<String> confirmed = confirmedAttendeeIdsFromSnapshot(eventSnap);
            if (!confirmed.contains(entrantDeviceId)) {
                List<String> updated = new ArrayList<>(confirmed);
                updated.add(entrantDeviceId);
                transaction.update(eventRef, "confirmedAttendeeIds", updated);
            }
            transaction.update(entryRef, "status", WaitingList.STATUS_ENROLLED);
            return null;
        }).addOnSuccessListener(unused -> {
            if (onSuccess != null) {
                onSuccess.onSuccess(null);
            }
        }).addOnFailureListener(onFailure);
    }

    /**
     * One transaction: require waitlist {@code selected}, set {@code declined}. If already declined or
     * found-replacement, returns {@link DeclineSelectionInviteOutcome#ALREADY_DECLINED} without writing.
     */
    public void declineSelectedInvitationTransactional(String eventId,
                                                       String entryDocId,
                                                       OnSuccessListener<DeclineSelectionInviteOutcome> onSuccess,
                                                       OnFailureListener onFailure) {
        if (eventId == null || eventId.isEmpty() || entryDocId == null || entryDocId.isEmpty()) {
            if (onFailure != null) {
                onFailure.onFailure(new IllegalArgumentException("eventId and entryDocId required"));
            }
            return;
        }
        DocumentReference entryRef = db.collection(COLLECTION_WAITLISTS)
                .document(eventId)
                .collection(SUBCOLLECTION_ENTRIES)
                .document(entryDocId);
        db.runTransaction(transaction -> {
            DocumentSnapshot snap = transaction.get(entryRef);
            if (snap == null || !snap.exists()) {
                throw new IllegalStateException(LotteryErrorCodes.WAITLIST_ENTRY_NOT_FOUND);
            }
            String st = snap.getString("status");
            if (WaitingList.STATUS_DECLINED.equals(st)
                    || WaitingList.STATUS_DECLINED_FOUND_REPLACEMENT.equals(st)) {
                return DeclineSelectionInviteOutcome.ALREADY_DECLINED;
            }
            if (WaitingList.STATUS_ENROLLED.equals(st)) {
                return DeclineSelectionInviteOutcome.ALREADY_ENROLLED;
            }
            if (!WaitingList.STATUS_SELECTED.equals(st)) {
                return DeclineSelectionInviteOutcome.NOT_INVITED_ANYMORE;
            }
            transaction.update(entryRef, "status", WaitingList.STATUS_DECLINED);
            return DeclineSelectionInviteOutcome.APPLIED;
        }).addOnSuccessListener(result -> {
            if (onSuccess != null) {
                onSuccess.onSuccess(result != null ? result : DeclineSelectionInviteOutcome.NOT_INVITED_ANYMORE);
            }
        }).addOnFailureListener(onFailure);
    }

    private static List<String> confirmedAttendeeIdsFromSnapshot(DocumentSnapshot eventSnap) {
        List<String> out = new ArrayList<>();
        Object raw = eventSnap.get("confirmedAttendeeIds");
        if (raw instanceof List<?>) {
            for (Object o : (List<?>) raw) {
                if (o instanceof String) {
                    String id = ((String) o).trim();
                    if (!id.isEmpty() && !out.contains(id)) {
                        out.add(id);
                    }
                }
            }
        }
        return out;
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

    private static final int BATCH_DELETE_MAX_OPS = 500;

    /**
     * Deletes every waitlist entry for this device: public and private events, and any
     * {@code entries} subcollection document that matches {@code deviceId} (same strategy as
     * {@link #getEntrantHistory}). Uses a collection-group query so nothing is missed when the
     * {@code events} catalog does not list an event or is incomplete.
     * <p>
     * If the collection-group query fails (e.g. missing index), falls back to
     * {@link #removeUserFromAllWaitlistsByEventScan}.
     */
    public void removeUserFromAllWaitlists(String deviceId,
                                           OnSuccessListener<Void> onSuccess,
                                           OnFailureListener onFailure) {
        if (deviceId == null || deviceId.isEmpty()) {
            onSuccess.onSuccess(null);
            return;
        }
        db.collectionGroup(SUBCOLLECTION_ENTRIES)
                .whereEqualTo("deviceId", deviceId)
                .get(Source.SERVER)
                .addOnSuccessListener(querySnapshot -> {
                    List<DocumentSnapshot> docs = new ArrayList<>();
                    if (querySnapshot != null) {
                        for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
                            docs.add(doc);
                        }
                    }
                    if (docs.isEmpty()) {
                        onSuccess.onSuccess(null);
                        return;
                    }
                    deleteEntryDocumentsInBatches(docs, 0, onSuccess, onFailure);
                })
                .addOnFailureListener(e -> removeUserFromAllWaitlistsByEventScan(deviceId, onSuccess, onFailure));
    }

    /**
     * Deletes every document in {@code waitlists/{eventId}/entries} and then the parent {@code waitlists/{eventId}} doc.
     */
    public void deleteAllWaitlistDataForEvent(String eventId,
                                              OnSuccessListener<Void> onSuccess,
                                              OnFailureListener onFailure) {
        if (eventId == null || eventId.isEmpty()) {
            if (onSuccess != null) {
                onSuccess.onSuccess(null);
            }
            return;
        }
        db.collection(COLLECTION_WAITLISTS)
                .document(eventId)
                .collection(SUBCOLLECTION_ENTRIES)
                .get()
                .addOnSuccessListener(snapshot -> {
                    List<DocumentSnapshot> docs = snapshot != null
                            ? snapshot.getDocuments()
                            : new ArrayList<>();
                    if (docs.isEmpty()) {
                        db.collection(COLLECTION_WAITLISTS).document(eventId).delete()
                                .addOnSuccessListener(v -> {
                                    if (onSuccess != null) {
                                        onSuccess.onSuccess(null);
                                    }
                                })
                                .addOnFailureListener(onFailure);
                        return;
                    }
                    deleteEntryDocumentsInBatches(docs, 0,
                            unused -> db.collection(COLLECTION_WAITLISTS).document(eventId).delete()
                                    .addOnSuccessListener(v -> {
                                        if (onSuccess != null) {
                                            onSuccess.onSuccess(null);
                                        }
                                    })
                                    .addOnFailureListener(onFailure),
                            onFailure);
                })
                .addOnFailureListener(onFailure);
    }

    private void deleteEntryDocumentsInBatches(List<DocumentSnapshot> docs,
                                               int startIndex,
                                               OnSuccessListener<Void> onSuccess,
                                               OnFailureListener onFailure) {
        if (startIndex >= docs.size()) {
            onSuccess.onSuccess(null);
            return;
        }
        int end = Math.min(startIndex + BATCH_DELETE_MAX_OPS, docs.size());
        WriteBatch batch = db.batch();
        for (int i = startIndex; i < end; i++) {
            batch.delete(docs.get(i).getReference());
        }
        batch.commit()
                .addOnSuccessListener(v -> deleteEntryDocumentsInBatches(docs, end, onSuccess, onFailure))
                .addOnFailureListener(onFailure);
    }

    /**
     * Fallback: removes {@code waitlists/{eventId}/entries/{deviceId}} for every {@code events} id.
     * Does not cover orphan waitlists; prefer {@link #removeUserFromAllWaitlists}'s collection-group path.
     */
    private void removeUserFromAllWaitlistsByEventScan(String deviceId,
                                                       OnSuccessListener<Void> onSuccess,
                                                       OnFailureListener onFailure) {
        db.collection("events").get(Source.SERVER)
                .addOnSuccessListener(eventSnapshot -> {
                    if (eventSnapshot == null || eventSnapshot.isEmpty()) {
                        onSuccess.onSuccess(null);
                        return;
                    }
                    List<String> eventIds = new ArrayList<>();
                    for (DocumentSnapshot doc : eventSnapshot.getDocuments()) {
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
