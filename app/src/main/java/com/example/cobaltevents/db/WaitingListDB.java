package com.example.cobaltevents.db;

import com.example.cobaltevents.model.WaitingList;
import com.example.cobaltevents.model.WaitlistEntryInfo;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public class WaitingListDB {

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

    public void getEntrantHistory(String deviceId, OnSuccessListener<List<WaitingList>> onSuccess, OnFailureListener onFailure) {
        db.collectionGroup(SUBCOLLECTION_ENTRIES)
                .whereEqualTo("deviceId", deviceId)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    List<WaitingList> registrations = new ArrayList<>();
                    for (QueryDocumentSnapshot doc : querySnapshot) {
                        WaitingList reg = doc.toObject(WaitingList.class);
                        if (reg == null) continue;
                        String eventId = doc.getReference().getParent().getId();
                        reg.setEventId(eventId);
                        registrations.add(reg);
                    }
                    onSuccess.onSuccess(registrations);
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
                .get()
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

    public void updateNotificationsAllowed(String eventId, String deviceId, boolean notificationsAllowed,
                                           OnSuccessListener<Void> onSuccess, OnFailureListener onFailure) {
        db.collection(COLLECTION_WAITLISTS)
                .document(eventId)
                .collection(SUBCOLLECTION_ENTRIES)
                .document(deviceId)
                .update("notificationsAllowed", notificationsAllowed)
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

    public void getActiveCountForEvent(String eventId,
                                       OnSuccessListener<Integer> onSuccess,
                                       OnFailureListener onFailure) {
        db.collection(COLLECTION_WAITLISTS)
                .document(eventId)
                .collection(SUBCOLLECTION_ENTRIES)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    int count = 0;
                    for (QueryDocumentSnapshot doc : querySnapshot) {
                        WaitingList reg = doc.toObject(WaitingList.class);
                        if (reg == null) continue;
                        String status = reg.getStatus();
                        if (!isEntryActive(status)) continue;
                        count++;
                    }
                    onSuccess.onSuccess(count);
                })
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
        // Fetch all event IDs, then delete deviceId entry under each one
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

    public void getWaitlistInfoForEvents(String deviceId,
                                         List<String> eventIds,
                                         OnSuccessListener<Map<String, WaitlistEntryInfo>> onSuccess,
                                         OnFailureListener onFailure) {
        if (eventIds == null || eventIds.isEmpty()) {
            onSuccess.onSuccess(new HashMap<>());
            return;
        }
        Map<String, WaitlistEntryInfo> result = new HashMap<>();
        AtomicInteger pending = new AtomicInteger(eventIds.size());

        for (String eventId : eventIds) {
            if (eventId == null || eventId.isEmpty()) {
                if (pending.decrementAndGet() == 0) onSuccess.onSuccess(result);
                continue;
            }
            db.collection(COLLECTION_WAITLISTS)
                    .document(eventId)
                    .collection(SUBCOLLECTION_ENTRIES)
                    .document(deviceId)
                    .get()
                    .addOnSuccessListener(doc -> {
                        if (doc != null && doc.exists()) {
                            String status = doc.getString("status");
                            if (status == null) status = WaitingList.STATUS_PENDING;
                            boolean notificationsAllowed = doc.contains("notificationsAllowed")
                                    ? Boolean.TRUE.equals(doc.getBoolean("notificationsAllowed"))
                                    : true;
                            result.put(eventId, new WaitlistEntryInfo(status, notificationsAllowed));
                        }
                        if (pending.decrementAndGet() == 0) onSuccess.onSuccess(result);
                    })
                    .addOnFailureListener(e -> {
                        if (pending.decrementAndGet() == 0) onSuccess.onSuccess(result);
                    });
        }
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

    public void getWaitlistInfoForDevice(String deviceId,
                                         OnSuccessListener<Map<String, WaitlistEntryInfo>> onSuccess,
                                         OnFailureListener onFailure) {
        db.collectionGroup(SUBCOLLECTION_ENTRIES)
                .whereEqualTo("deviceId", deviceId)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    Map<String, WaitlistEntryInfo> map = new HashMap<>();
                    for (QueryDocumentSnapshot doc : querySnapshot) {
                        String eventId = doc.getReference().getParent().getId();
                        if (eventId == null) continue;
                        String status = doc.getString("status");
                        if (status == null) status = WaitingList.STATUS_PENDING;
                        boolean notificationsAllowed = doc.contains("notificationsAllowed")
                                ? Boolean.TRUE.equals(doc.getBoolean("notificationsAllowed"))
                                : true;
                        map.put(eventId, new WaitlistEntryInfo(status, notificationsAllowed));
                    }
                    onSuccess.onSuccess(map);
                })
                .addOnFailureListener(onFailure);
    }

    public void getWaitlistStatusesForDevice(String deviceId,
                                             OnSuccessListener<Map<String, String>> onSuccess,
                                             OnFailureListener onFailure) {
        db.collectionGroup(SUBCOLLECTION_ENTRIES)
                .whereEqualTo("deviceId", deviceId)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    Map<String, String> eventIdToStatus = new HashMap<>();
                    for (QueryDocumentSnapshot doc : querySnapshot) {
                        String eventId = doc.getReference().getParent().getId();
                        String status = doc.getString("status");
                        if (eventId != null) {
                            eventIdToStatus.put(eventId, status != null ? status : WaitingList.STATUS_PENDING);
                        }
                    }
                    onSuccess.onSuccess(eventIdToStatus);
                })
                .addOnFailureListener(onFailure);
    }

    private static boolean isEntryActive(String status) {
        if (status == null) return true;
        // Active entries: still participating in the flow
        // Pending → awaiting draw/response
        // Selected → invited, awaiting response
        // Enrolled → accepted and enrolled (still an active participant)
        return WaitingList.STATUS_PENDING.equals(status)
                || WaitingList.STATUS_SELECTED.equals(status)
                || WaitingList.STATUS_ENROLLED.equals(status);
    }
}
