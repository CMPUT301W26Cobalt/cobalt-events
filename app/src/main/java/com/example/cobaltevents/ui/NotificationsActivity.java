package com.example.cobaltevents.ui;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.example.cobaltevents.R;
import com.example.cobaltevents.controller.GeolocationController;
import com.example.cobaltevents.db.EntrantDB;
import com.example.cobaltevents.db.EventDB;
import com.example.cobaltevents.db.NotificationDB;
import com.example.cobaltevents.db.WaitingListDB;
import com.example.cobaltevents.model.Notification;
import com.example.cobaltevents.model.WaitingList;
import com.example.cobaltevents.ui.adapter.NotificationListAdapter;
import com.example.cobaltevents.util.NetworkConnectivity;

public class NotificationsActivity extends AppCompatActivity {

    /** When true, use organizer-themed layout + bottom nav (opened from organizer flow). */
    public static final String EXTRA_FROM_ORGANIZER = "fromOrganizer";

    /** Same prefs as {@link AccountSettingsActivity} — drives system + in-app notification log. */
    private static final String PREFS_COALT = "cobalt_prefs";
    private static final String KEY_NOTIFICATION_EVENT_UPDATES = "notification_event_updates";

    private boolean fromOrganizer;
    private SharedPreferences notificationPrefs;
    private NotificationDB notificationDB;
    private EventDB eventDB;
    private WaitingListDB waitingListDB;
    private GeolocationController geolocationController;
    private NotificationListAdapter adapter;
    private String deviceId;
    private SwipeRefreshLayout swipeRefreshLayout;
    private TextView tvEmpty;
    // Prevent user navigation while Firestore updates for Accept/Decline are in-flight.
    private volatile boolean notificationActionInProgress = false;

    // Used for optimistic UI updates after Accept/Decline.
    private java.util.List<com.example.cobaltevents.model.Notification> currentNotifications =
            new java.util.ArrayList<>();
    private java.util.Map<String, String> currentEventIdToStatus = new java.util.HashMap<>();
    /** Resolved from Firestore so list rows can show "Event: &lt;name&gt;" on every card. */
    private java.util.Map<String, String> currentEventIdToEventName = new java.util.HashMap<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        fromOrganizer = getIntent().getBooleanExtra(EXTRA_FROM_ORGANIZER, false);
        setContentView(fromOrganizer
                ? R.layout.activity_notifications_organizer
                : R.layout.activity_notifications);

        notificationPrefs = getSharedPreferences(PREFS_COALT, MODE_PRIVATE);

        notificationDB = new NotificationDB();
        eventDB = new EventDB();
        waitingListDB = new WaitingListDB();
        geolocationController = new GeolocationController();
        deviceId = new EntrantDB(this).getEntrant().getDeviceId();

        RecyclerView recycler = findViewById(R.id.recycler_notifications);
        recycler.setLayoutManager(new LinearLayoutManager(this));
        adapter = new NotificationListAdapter();
        adapter.setOnNotificationActionListener(new NotificationListAdapter.OnNotificationActionListener() {
            @Override
            public void onAccept(com.example.cobaltevents.model.Notification notification) {
                if (notificationActionInProgress) return;
                if (!NetworkConnectivity.hasValidatedInternet(NotificationsActivity.this)) {
                    Toast.makeText(NotificationsActivity.this, R.string.notification_no_internet,
                            Toast.LENGTH_SHORT).show();
                    return;
                }
                notificationActionInProgress = true;
                if (isOrganizerModeCoOrganizerInvite(notification)) {
                    updateCoOrganizerInviteResponse(notification,
                            com.example.cobaltevents.model.Notification.RESPONSE_ACCEPTED);
                    return;
                }
                if (isPrivateEventType(notification)) {
                    addOrUpdateResponseThenWaitlist(notification,
                            com.example.cobaltevents.model.Notification.RESPONSE_ACCEPTED,
                            WaitingList.STATUS_PENDING);
                } else {
                    updateResponseThenWaitlist(notification,
                            com.example.cobaltevents.model.Notification.RESPONSE_ACCEPTED,
                            WaitingList.STATUS_ENROLLED);
                }
            }

            @Override
            public void onReject(com.example.cobaltevents.model.Notification notification) {
                if (notificationActionInProgress) return;
                if (!NetworkConnectivity.hasValidatedInternet(NotificationsActivity.this)) {
                    Toast.makeText(NotificationsActivity.this, R.string.notification_no_internet,
                            Toast.LENGTH_SHORT).show();
                    return;
                }
                notificationActionInProgress = true;
                if (isOrganizerModeCoOrganizerInvite(notification)) {
                    updateCoOrganizerInviteResponse(notification,
                            com.example.cobaltevents.model.Notification.RESPONSE_DECLINED);
                    return;
                }
                if (isPrivateEventType(notification)) {
                    declinePrivateEventAndRemoveWaitlist(notification);
                } else {
                    updateResponseThenWaitlist(notification,
                            com.example.cobaltevents.model.Notification.RESPONSE_DECLINED,
                            WaitingList.STATUS_DECLINED);
                }
            }
        });
        recycler.setAdapter(adapter);

        tvEmpty = findViewById(R.id.tv_empty_notifications);

        swipeRefreshLayout = findViewById(R.id.swipe_refresh_notifications);
        if (swipeRefreshLayout != null) {
            swipeRefreshLayout.setOnRefreshListener(this::loadNotifications);
            int accent = ContextCompat.getColor(this,
                    fromOrganizer ? R.color.organizer_blue : R.color.user_green);
            swipeRefreshLayout.setColorSchemeColors(accent);
        }

        setupBottomNavigation();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadNotifications();
    }

    /** When false, in-app notification log is cleared (no Firestore load) — matches system notification mute. */
    private boolean isEventNotificationUpdatesEnabled() {
        return notificationPrefs.getBoolean(KEY_NOTIFICATION_EVENT_UPDATES, true);
    }

    private void loadNotifications() {
        if (deviceId == null || deviceId.isEmpty()) {
            adapter.setItems(new java.util.ArrayList<>(), null, null);
            if (tvEmpty != null) {
                tvEmpty.setText(R.string.notifications_empty);
                tvEmpty.setVisibility(View.VISIBLE);
            }
            if (swipeRefreshLayout != null) swipeRefreshLayout.setRefreshing(false);
            return;
        }
        if (!isEventNotificationUpdatesEnabled()) {
            currentNotifications = new java.util.ArrayList<>();
            currentEventIdToStatus = new java.util.HashMap<>();
            currentEventIdToEventName = new java.util.HashMap<>();
            adapter.setItems(currentNotifications, null, null);
            if (tvEmpty != null) {
                tvEmpty.setText(R.string.notifications_log_disabled_message);
                tvEmpty.setVisibility(View.VISIBLE);
            }
            if (swipeRefreshLayout != null) swipeRefreshLayout.setRefreshing(false);
            return;
        }
        notificationDB.getNotificationsForRecipient(deviceId,
            list -> {
                java.util.List<com.example.cobaltevents.model.Notification> notifications =
                        filterNotificationsForCurrentFlow(list != null ? list : new java.util.ArrayList<>());
                loadStatusesForNotifications(notifications, eventIdToStatus -> {
                    loadEventNamesForNotifications(notifications, eventIdToName -> {
                        currentNotifications = notifications;
                        currentEventIdToStatus = eventIdToStatus != null
                                ? eventIdToStatus
                                : new java.util.HashMap<>();
                        currentEventIdToEventName = eventIdToName != null
                                ? eventIdToName
                                : new java.util.HashMap<>();
                        adapter.setItems(currentNotifications, currentEventIdToStatus, currentEventIdToEventName);
                        if (tvEmpty != null) tvEmpty.setVisibility(notifications.isEmpty() ? View.VISIBLE : View.GONE);
                        if (swipeRefreshLayout != null) swipeRefreshLayout.setRefreshing(false);
                    });
                });
            },
            e -> {
                Log.e("NotificationsActivity", "Load notifications failed", e);
                String msg = e.getMessage() != null ? e.getMessage() : "Failed to load notifications";
                Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
                adapter.setItems(new java.util.ArrayList<>(), null, null);
                if (tvEmpty != null) {
                    tvEmpty.setText(R.string.notifications_empty);
                    tvEmpty.setVisibility(View.VISIBLE);
                }
                if (swipeRefreshLayout != null) swipeRefreshLayout.setRefreshing(false);
            });
    }

    private java.util.List<com.example.cobaltevents.model.Notification> filterNotificationsForCurrentFlow(
            java.util.List<com.example.cobaltevents.model.Notification> source) {
        String expectedMode = fromOrganizer
                ? com.example.cobaltevents.model.Notification.RECIPIENT_MODE_ORGANIZER
                : com.example.cobaltevents.model.Notification.RECIPIENT_MODE_USER;
        java.util.List<com.example.cobaltevents.model.Notification> out = new java.util.ArrayList<>();
        for (com.example.cobaltevents.model.Notification n : source) {
            if (n == null) continue;
            if (expectedMode.equals(n.getRecipientMode())) {
                out.add(n);
            }
        }
        return out;
    }

    /**
     * Loads each distinct event's display name from Firestore so the adapter can prefix every message with it.
     */
    private void loadEventNamesForNotifications(
            java.util.List<com.example.cobaltevents.model.Notification> notifications,
            com.google.android.gms.tasks.OnSuccessListener<java.util.Map<String, String>> onSuccess) {
        java.util.Map<String, String> eventIdToName = new java.util.concurrent.ConcurrentHashMap<>();
        if (notifications == null || notifications.isEmpty()) {
            onSuccess.onSuccess(eventIdToName);
            return;
        }
        java.util.Set<String> eventIds = new java.util.HashSet<>();
        for (com.example.cobaltevents.model.Notification n : notifications) {
            if (n == null) continue;
            String eid = n.getEventId();
            if (eid != null && !eid.isEmpty()) {
                eventIds.add(eid);
            }
        }
        if (eventIds.isEmpty()) {
            onSuccess.onSuccess(eventIdToName);
            return;
        }
        java.util.concurrent.atomic.AtomicInteger pending = new java.util.concurrent.atomic.AtomicInteger(eventIds.size());
        String unknown = getString(R.string.notification_event_unknown);
        for (String eventId : eventIds) {
            eventDB.getEvent(eventId,
                    event -> {
                        String label = unknown;
                        if (event != null && event.getName() != null && !event.getName().trim().isEmpty()) {
                            label = event.getName().trim();
                        }
                        eventIdToName.put(eventId, label);
                        if (pending.decrementAndGet() == 0) {
                            onSuccess.onSuccess(eventIdToName);
                        }
                    },
                    err -> {
                        eventIdToName.put(eventId, unknown);
                        if (pending.decrementAndGet() == 0) {
                            onSuccess.onSuccess(eventIdToName);
                        }
                    });
        }
    }

    /**
     * Loads waitlist statuses for the visible notifications without collectionGroup queries.
     * This avoids Firestore composite-index requirements for entries/deviceId.
     */
    private void loadStatusesForNotifications(
            java.util.List<com.example.cobaltevents.model.Notification> notifications,
            com.google.android.gms.tasks.OnSuccessListener<java.util.Map<String, String>> onSuccess) {
        java.util.Map<String, String> eventIdToStatus = new java.util.concurrent.ConcurrentHashMap<>();
        if (notifications == null || notifications.isEmpty()) {
            onSuccess.onSuccess(eventIdToStatus);
            return;
        }

        java.util.Set<String> eventIds = new java.util.HashSet<>();
        for (com.example.cobaltevents.model.Notification n : notifications) {
            if (n == null) continue;
            if (isCoOrganizerType(n)) continue;
            String eventId = n.getEventId();
            if (eventId == null || eventId.isEmpty()) continue;
            eventIds.add(eventId);
            if (com.example.cobaltevents.model.Notification.TYPE_NOT_SELECTED.equals(n.getType())) {
                // X (not-selected) should NOT kick the user out of the waitlist.
                // Treat it as "still in waitlist, but not selected".
                eventIdToStatus.put(eventId, WaitingList.STATUS_NOT_SELECTED);
            }
        }

        if (eventIds.isEmpty()) {
            onSuccess.onSuccess(eventIdToStatus);
            return;
        }

        java.util.concurrent.atomic.AtomicInteger pending = new java.util.concurrent.atomic.AtomicInteger(eventIds.size());
        for (String eventId : eventIds) {
            waitingListDB.getRegistrationForEventAnyStatus(eventId, deviceId,
                    reg -> {
                        // Default to pending when no entry exists; keeps actionable cards usable.
                        String status = reg != null && reg.getStatus() != null
                                ? reg.getStatus()
                                : WaitingList.STATUS_PENDING;
                        eventIdToStatus.put(eventId, status);
                        if (pending.decrementAndGet() == 0) onSuccess.onSuccess(eventIdToStatus);
                    },
                    e -> {
                        if (pending.decrementAndGet() == 0) onSuccess.onSuccess(eventIdToStatus);
                    });
        }
    }

    /**
     * Updates the notification response in-memory so the card badge updates immediately,
     * without waiting for a full Firestore reload.
     *
     * We still call {@link #loadNotifications()} after the backend writes succeed/fail
     * to keep DB and UI consistent.
     */
    private void applyLocalResponseUpdate(
            com.example.cobaltevents.model.Notification target,
            String newResponse) {
        if (target == null || newResponse == null) return;
        String notificationId = target.getId();
        if (notificationId == null || notificationId.isEmpty()) return;

        // Update the item object we already have (in case it's the same reference),
        // and also update the copy inside currentNotifications by id.
        target.setResponse(newResponse);
        if (currentNotifications != null) {
            for (com.example.cobaltevents.model.Notification n : currentNotifications) {
                if (n == null) continue;
                if (notificationId.equals(n.getId())) {
                    n.setResponse(newResponse);
                    break;
                }
            }
        }
        if (adapter != null) {
            adapter.setItems(currentNotifications, currentEventIdToStatus, currentEventIdToEventName);
        }
    }

    private void setupBottomNavigation() {
        if (fromOrganizer) {
            setupOrganizerBottomNavigation();
        } else {
            setupEntrantBottomNavigation();
        }
    }

    private void setupOrganizerBottomNavigation() {
        int active = getResources().getColor(R.color.organizer_blue);
        findViewById(R.id.nav_dashboard).setOnClickListener(v -> {
            if (notificationActionInProgress) {
                Toast.makeText(this, "Updating notification. Please wait…", Toast.LENGTH_SHORT).show();
                return;
            }
            startActivity(new Intent(this, OrganizerActivity.class));
            finish();
        });
        findViewById(R.id.nav_create).setOnClickListener(v -> {
            if (notificationActionInProgress) {
                Toast.makeText(this, "Updating notification. Please wait…", Toast.LENGTH_SHORT).show();
                return;
            }
            startActivity(new Intent(this, EventCreateActivity.class));
        });
        findViewById(R.id.nav_notifications).setOnClickListener(v -> { });
        findViewById(R.id.nav_account).setOnClickListener(v -> {
            startActivity(new Intent(this, AccountSettingsActivity.class)
                    .putExtra(EXTRA_FROM_ORGANIZER, true));
            finish();
        });

        ImageView iv = findViewById(R.id.iv_nav_notifications);
        TextView tv = findViewById(R.id.tv_nav_notifications);
        if (iv != null) iv.setColorFilter(active);
        if (tv != null) tv.setTextColor(active);
    }

    private void setupEntrantBottomNavigation() {
        findViewById(R.id.nav_events).setOnClickListener(v -> {
            if (notificationActionInProgress) {
                Toast.makeText(this, "Updating notification. Please wait…", Toast.LENGTH_SHORT).show();
                return;
            }
            startActivity(new Intent(this, EventListActivity.class));
            finish();
        });
        findViewById(R.id.nav_my_events).setOnClickListener(v -> {
            if (notificationActionInProgress) {
                Toast.makeText(this, "Updating notification. Please wait…", Toast.LENGTH_SHORT).show();
                return;
            }
            startActivity(new Intent(this, EventHistoryActivity.class));
            finish();
        });
        findViewById(R.id.nav_qr).setOnClickListener(v -> {
            if (notificationActionInProgress) {
                Toast.makeText(this, "Updating notification. Please wait…", Toast.LENGTH_SHORT).show();
                return;
            }
            startActivity(new Intent(this, QRScanActivity.class));
        });
        findViewById(R.id.nav_notifications).setOnClickListener(v -> {});
        findViewById(R.id.nav_account).setOnClickListener(v -> {
            startActivity(new Intent(this, AccountSettingsActivity.class));
            finish();
        });

        int active = getResources().getColor(R.color.user_green);
        ImageView iv = findViewById(R.id.iv_nav_notifications);
        TextView tv = findViewById(R.id.tv_nav_notifications);
        if (iv != null) iv.setColorFilter(active);
        if (tv != null) tv.setTextColor(active);
    }

    private void updateResponseThenWaitlist(com.example.cobaltevents.model.Notification notification,
                                            String newResponse,
                                            String newStatus) {
        if (notification == null
                || notification.getEventId() == null
                || notification.getEventId().isEmpty()) {
            Toast.makeText(this, "Unable to update waitlist status.", Toast.LENGTH_SHORT).show();
            notificationActionInProgress = false;
            return;
        }
        if (notification.getId() == null || notification.getId().isEmpty()) {
            Toast.makeText(this, "Unable to update notification response.", Toast.LENGTH_SHORT).show();
            notificationActionInProgress = false;
            return;
        }

        final String eventId = notification.getEventId();
        final String notificationId = notification.getId();

        // IMPORTANT: verify the waitlist entry exists BEFORE touching UI or updating any fields.
        waitingListDB.getRegistrationForEventAnyStatus(eventId, deviceId,
                reg -> {
                    if (reg == null) {
                        Toast.makeText(this,
                                "Notification was not meant for you.",
                                Toast.LENGTH_LONG).show();
                        notificationDB.deleteNotification(notificationId,
                                unused -> {
                                    loadNotifications();
                                    notificationActionInProgress = false;
                                },
                                e -> {
                                    loadNotifications();
                                    notificationActionInProgress = false;
                                });
                        return;
                    }
                    // Only update notification/DB after we confirm the waitlist entry exists.
                    // Also, don't update UI until both DB writes succeed so DB and button stay aligned.
                    // For faster feedback, update only the notification response badge locally now.
                    currentEventIdToStatus.put(eventId, newStatus);
                    applyLocalResponseUpdate(notification, newResponse);
                    waitingListDB.updateStatus(eventId, deviceId, newStatus,
                            unused2 -> notificationDB.updateResponse(notificationId, newResponse,
                                    unused3 -> {
                                        loadNotifications();
                                        notificationActionInProgress = false;
                                    },
                                    e2 -> {
                                        String msg = e2 != null ? e2.toString() : "Failed to update notification response";
                                        Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
                                        loadNotifications();
                                        notificationActionInProgress = false;
                                    }),
                            e -> {
                                String msg = e != null ? e.toString() : "Failed to update waitlist status";
                                Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
                                loadNotifications();
                                notificationActionInProgress = false;
                            });
                },
                e -> {
                    String msg = e != null ? e.toString() : "Failed to verify waitlist entry";
                    Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
                    loadNotifications();
                    notificationActionInProgress = false;
                });
    }

    /**
     * For private-event notifications, registration may not exist yet.
     * Accept => pending (joined waitlist), Reject => declined.
     */
    private void addOrUpdateResponseThenWaitlist(com.example.cobaltevents.model.Notification notification,
                                                 String newResponse,
                                                 String waitlistStatus) {
        if (notification == null || notification.getEventId() == null || notification.getEventId().isEmpty()) {
            Toast.makeText(this, "Unable to update waitlist status.", Toast.LENGTH_SHORT).show();
            notificationActionInProgress = false;
            return;
        }
        if (notification.getId() == null || notification.getId().isEmpty()) {
            Toast.makeText(this, "Unable to update notification response.", Toast.LENGTH_SHORT).show();
            notificationActionInProgress = false;
            return;
        }

        final String eventId = notification.getEventId();
        final String notificationId = notification.getId();

        // Read current waitlist first, so private notifications cannot override a final "declined".
        waitingListDB.getRegistrationForEventAnyStatus(eventId, deviceId,
                reg -> {
                    if (reg != null) {
                        String currentStatus = reg.getStatus();
                        if (WaitingList.STATUS_DECLINED.equals(currentStatus)
                                || WaitingList.STATUS_DECLINED_FOUND_REPLACEMENT.equals(currentStatus)) {
                            // If user already declined enrollment, they can't accept private invites.
                            Toast.makeText(this,
                                    "Notification was not meant for you.",
                                    Toast.LENGTH_LONG).show();
                            notificationDB.deleteNotification(notificationId,
                                    unused -> {
                                        loadNotifications();
                                        notificationActionInProgress = false;
                                    },
                                    e -> {
                                        loadNotifications();
                                        notificationActionInProgress = false;
                                    });
                            return;
                        }

                        // Don't downgrade already finalized states.
                        if (WaitingList.STATUS_ENROLLED.equals(currentStatus)
                                || WaitingList.STATUS_SELECTED.equals(currentStatus)) {
                            // Response badge can update immediately; waitlist is already final.
                            currentEventIdToStatus.put(eventId, currentStatus);
                            applyLocalResponseUpdate(notification, newResponse);
                            notificationDB.updateResponse(notificationId, newResponse,
                                    unused -> {
                                        loadNotifications();
                                        notificationActionInProgress = false;
                                    },
                                    e -> {
                                        loadNotifications();
                                        notificationActionInProgress = false;
                                    });
                            return;
                        }
                    }

                    // Update waitlist status (or create entry) first...
                    if (reg != null) {
                        // For faster feedback, update the response badge locally now.
                        currentEventIdToStatus.put(eventId, waitlistStatus);
                        applyLocalResponseUpdate(notification, newResponse);
                        waitingListDB.updateStatus(eventId, deviceId, waitlistStatus,
                                unused -> notificationDB.updateResponse(notificationId, newResponse,
                                        unused2 -> {
                                            loadNotifications();
                                            notificationActionInProgress = false;
                                        },
                                        e2 -> {
                                            loadNotifications();
                                            notificationActionInProgress = false;
                                        }),
                                e -> {
                                    Toast.makeText(this,
                                            "Failed to update waitlist status",
                                            Toast.LENGTH_LONG).show();
                                    loadNotifications();
                                    notificationActionInProgress = false;
                                });
                    } else {
                        WaitingList newReg = new WaitingList(eventId, deviceId, waitlistStatus);
                        // For faster feedback, update the response badge locally now.
                        currentEventIdToStatus.put(eventId, waitlistStatus);
                        applyLocalResponseUpdate(notification, newResponse);
                        eventDB.getEvent(eventId, event -> {
                            int cap = event != null ? event.getWaitingListCapacity() : 0;
                            waitingListDB.addRegistrationWithJoinChecks(newReg, cap,
                                    event != null ? event.getRegistrationClose() : null,
                                    unused -> notificationDB.updateResponse(notificationId, newResponse,
                                            unused2 -> {
                                                recordLocationIfPermitted(eventId);
                                                loadNotifications();
                                                notificationActionInProgress = false;
                                            },
                                            e2 -> {
                                                loadNotifications();
                                                notificationActionInProgress = false;
                                            }),
                                    err -> {
                                        if (WaitingListDB.REASON_WAITLIST_FULL.equals(err.getMessage())) {
                                            Toast.makeText(this, R.string.waitlist_full_capacity, Toast.LENGTH_LONG).show();
                                        } else if (WaitingListDB.REASON_REGISTRATION_CLOSED.equals(err.getMessage())) {
                                            Toast.makeText(this, R.string.waitlist_registration_closed, Toast.LENGTH_LONG).show();
                                        } else if (WaitingListDB.REASON_ORGANIZER_CANNOT_JOIN.equals(err.getMessage())) {
                                            Toast.makeText(this, R.string.waitlist_organizer_cannot_join, Toast.LENGTH_LONG).show();
                                        } else {
                                            Toast.makeText(this,
                                                    "Failed to update waitlist status",
                                                    Toast.LENGTH_LONG).show();
                                        }
                                        loadNotifications();
                                        notificationActionInProgress = false;
                                    });
                        }, err -> {
                            Toast.makeText(this,
                                    "Failed to load event",
                                    Toast.LENGTH_LONG).show();
                            loadNotifications();
                            notificationActionInProgress = false;
                        });
                    }
                },
                e -> {
                    Toast.makeText(this,
                            "Failed to verify waitlist entry",
                            Toast.LENGTH_LONG).show();
                    loadNotifications();
                    notificationActionInProgress = false;
                });
    }

    private void recordLocationIfPermitted(String eventId) {
        if (eventId == null || eventId.isEmpty()) return;
        if (geolocationController == null || !geolocationController.hasLocationPermission(this)) return;
        geolocationController.getCurrentDeviceLocation(this, loc -> {
            if (loc == null) return;
            geolocationController.recordLocationForEvent(
                    NotificationsActivity.this,
                    deviceId,
                    eventId,
                    loc,
                    unused -> {},
                    e -> {});
        });
    }

    private void updateResponseOnly(
            com.example.cobaltevents.model.Notification notification,
            String newResponse) {
        if (notification == null || notification.getId() == null || notification.getId().isEmpty()) {
            Toast.makeText(this, "Unable to update notification response.", Toast.LENGTH_SHORT).show();
            notificationActionInProgress = false;
            return;
        }
        notification.setResponse(newResponse);
        adapter.setItems(currentNotifications, currentEventIdToStatus, currentEventIdToEventName);
        notificationDB.updateResponse(notification.getId(), newResponse,
                unused -> {
                    loadNotifications();
                    notificationActionInProgress = false;
                },
                e -> {
                    String msg = e != null ? e.toString() : "Failed to update notification response";
                    Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
                    loadNotifications();
                    notificationActionInProgress = false;
                });
    }

    private boolean isPrivateEventType(com.example.cobaltevents.model.Notification notification) {
        if (notification == null || notification.getType() == null) return false;
        String type = notification.getType().toLowerCase(java.util.Locale.US);
        return com.example.cobaltevents.model.Notification.TYPE_PRIVATE_EVENT.equals(type)
                || "private".equals(type)
                || "private_event".equals(type);
    }

    private boolean isCoOrganizerType(com.example.cobaltevents.model.Notification notification) {
        if (notification == null || notification.getType() == null) return false;
        return com.example.cobaltevents.model.Notification.TYPE_CO_ORGANIZER
                .equals(notification.getType().toLowerCase(java.util.Locale.US));
    }

    private boolean isOrganizerModeCoOrganizerInvite(com.example.cobaltevents.model.Notification notification) {
        return isCoOrganizerType(notification)
                && com.example.cobaltevents.model.Notification.RECIPIENT_MODE_ORGANIZER
                .equals(notification.getRecipientMode());
    }

    private void updateCoOrganizerInviteResponse(com.example.cobaltevents.model.Notification notification,
                                                 String response) {
        if (notification == null || notification.getEventId() == null || notification.getEventId().isEmpty()
                || notification.getId() == null || notification.getId().isEmpty()) {
            Toast.makeText(this, "Unable to update organizer invite.", Toast.LENGTH_SHORT).show();
            notificationActionInProgress = false;
            return;
        }
        final String eventId = notification.getEventId();
        final String notificationId = notification.getId();
        if (com.example.cobaltevents.model.Notification.RESPONSE_DECLINED.equals(response)) {
            applyLocalResponseUpdate(notification, response);
            notificationDB.updateResponse(notificationId, response,
                    unused -> {
                        loadNotifications();
                        notificationActionInProgress = false;
                    },
                    e -> {
                        loadNotifications();
                        notificationActionInProgress = false;
                    });
            return;
        }
        eventDB.getEvent(eventId, event -> {
            if (event == null) {
                Toast.makeText(this, "Event not found.", Toast.LENGTH_SHORT).show();
                loadNotifications();
                notificationActionInProgress = false;
                return;
            }
            java.util.List<String> organizers = new java.util.ArrayList<>(event.getMergedOrganizerDeviceIds());
            if (!organizers.contains(deviceId)) {
                organizers.add(deviceId);
            }
            event.setOrganizers(organizers);
            applyLocalResponseUpdate(notification, response);
            eventDB.updateEvent(event,
                    unused -> waitingListDB.deleteRegistration(
                            eventId,
                            deviceId,
                            v -> notificationDB.updateResponse(notificationId, response,
                                    unused2 -> {
                                        loadNotifications();
                                        notificationActionInProgress = false;
                                    },
                                    e2 -> {
                                        loadNotifications();
                                        notificationActionInProgress = false;
                                    }),
                            eDel -> notificationDB.updateResponse(notificationId, response,
                                    unused2 -> {
                                        loadNotifications();
                                        notificationActionInProgress = false;
                                    },
                                    e2 -> {
                                        loadNotifications();
                                        notificationActionInProgress = false;
                                    })),
                    e -> {
                        loadNotifications();
                        notificationActionInProgress = false;
                    });
        }, e -> {
            loadNotifications();
            notificationActionInProgress = false;
        });
    }
    private void declinePrivateEventAndRemoveWaitlist(com.example.cobaltevents.model.Notification notification) {
        if (notification == null
                || notification.getEventId() == null
                || notification.getEventId().isEmpty()) {
            Toast.makeText(this, "Unable to update notification response.", Toast.LENGTH_SHORT).show();
            notificationActionInProgress = false;
            return;
        }
        if (notification.getId() == null || notification.getId().isEmpty()) {
            Toast.makeText(this, "Unable to update notification response.", Toast.LENGTH_SHORT).show();
            notificationActionInProgress = false;
            return;
        }

        final String eventId = notification.getEventId();
        final String notificationId = notification.getId();

        // If the user already has a final "declined" waitlist state, don't delete it.
        waitingListDB.getRegistrationForEventAnyStatus(eventId, deviceId,
                reg -> {
                    String currentStatus = reg != null ? reg.getStatus() : null;
                    if (WaitingList.STATUS_DECLINED.equals(currentStatus)
                            || WaitingList.STATUS_DECLINED_FOUND_REPLACEMENT.equals(currentStatus)) {
                        // For faster feedback, update the response badge locally now.
                        currentEventIdToStatus.put(eventId, currentStatus);
                        applyLocalResponseUpdate(notification, com.example.cobaltevents.model.Notification.RESPONSE_DECLINED);
                        notificationDB.updateResponse(notificationId,
                                        com.example.cobaltevents.model.Notification.RESPONSE_DECLINED,
                                        unused -> {
                                            loadNotifications();
                                            notificationActionInProgress = false;
                                        },
                                        e -> {
                                            loadNotifications();
                                            notificationActionInProgress = false;
                                        });
                        return;
                    }

                    notificationDB.updateResponse(notificationId,
                                    com.example.cobaltevents.model.Notification.RESPONSE_DECLINED,
                                    unused -> waitingListDB.deleteRegistration(
                                            eventId,
                                            deviceId,
                                            v -> {
                                                loadNotifications();
                                                notificationActionInProgress = false;
                                            },
                                            e -> {
                                                loadNotifications();
                                                notificationActionInProgress = false;
                                            }),
                                    e -> {
                                        loadNotifications();
                                        notificationActionInProgress = false;
                                    });
                },
                e -> {
                    loadNotifications();
                    notificationActionInProgress = false;
                });
    }

}
