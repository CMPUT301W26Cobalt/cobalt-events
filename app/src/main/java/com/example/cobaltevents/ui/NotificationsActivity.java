package com.example.cobaltevents.ui;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.example.cobaltevents.R;
import com.example.cobaltevents.db.EntrantDB;
import com.example.cobaltevents.db.EventDB;
import com.example.cobaltevents.db.NotificationDB;
import com.example.cobaltevents.db.WaitingListDB;
import com.example.cobaltevents.model.Notification;
import com.example.cobaltevents.model.WaitingList;
import com.example.cobaltevents.ui.adapter.NotificationListAdapter;

public class NotificationsActivity extends AppCompatActivity {

    private static final String SEED_PREFS = "notifications_one_time_seed";
    /** Bump this string when you need another one-time push for all installs. */
    private static final String KEY_SEEDED_TRIPLE_MAR_2026 = "seeded_triple_private_notselected_star_v3";

    private NotificationDB notificationDB;
    private EventDB eventDB;
    private WaitingListDB waitingListDB;
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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_notifications);

        notificationDB = new NotificationDB();
        eventDB = new EventDB();
        waitingListDB = new WaitingListDB();
        deviceId = new EntrantDB(this).getEntrant().getDeviceId();

        RecyclerView recycler = findViewById(R.id.recycler_notifications);
        recycler.setLayoutManager(new LinearLayoutManager(this));
        adapter = new NotificationListAdapter();
        adapter.setOnNotificationActionListener(new NotificationListAdapter.OnNotificationActionListener() {
            @Override
            public void onAccept(com.example.cobaltevents.model.Notification notification) {
                if (notificationActionInProgress) return;
                notificationActionInProgress = true;
                if (isCoOrganizerType(notification)) {
                    updateResponseOnly(
                            notification,
                            com.example.cobaltevents.model.Notification.RESPONSE_ACCEPTED);
                } else if (isPrivateEventType(notification)) {
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
                if (!isNetworkAvailable()) {
                    Toast.makeText(NotificationsActivity.this, R.string.notification_no_internet,
                            Toast.LENGTH_SHORT).show();
                    return;
                }
                notificationActionInProgress = true;
                if (isCoOrganizerType(notification)) {
                    updateResponseOnly(
                            notification,
                            com.example.cobaltevents.model.Notification.RESPONSE_DECLINED);
                } else if (isPrivateEventType(notification)) {
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
            swipeRefreshLayout.setColorSchemeColors(getResources().getColor(R.color.user_green));
        }

        setupBottomNavigation();
        // One-time sample trio (private-event → not-selected → got-off-waitlist), then load list.
        maybeSeedOneTimeTripleNotifications(this::loadNotifications);
    }

    /**
     * Pushes three sample notifications once per install, in order: private-event, not-selected, got-off-waitlist.
     */
    private void maybeSeedOneTimeTripleNotifications(Runnable after) {
        if (deviceId == null || deviceId.isEmpty()) {
            after.run();
            return;
        }
        SharedPreferences prefs = getSharedPreferences(SEED_PREFS, MODE_PRIVATE);
        if (prefs.getBoolean(KEY_SEEDED_TRIPLE_MAR_2026, false)) {
            after.run();
            return;
        }

        final String evPrivate = "1dCPXQLN0wdTfaFfn9Y1";
        final String evOther = "2a383654-728c-446f-9c53-20ed48e671af";

        Notification n1 = new Notification(deviceId, evPrivate,
                "Private event invitation",
                "You've been invited to a private event. Accept or decline to continue.",
                Notification.TYPE_PRIVATE_EVENT);

        notificationDB.saveNotification(n1,
                id1 -> {
                    Notification n2 = new Notification(deviceId, evOther,
                            "Not selected",
                            "You were not selected for this draw. You remain on the waitlist.",
                            Notification.TYPE_NOT_SELECTED);
                    notificationDB.saveNotification(n2,
                            id2 -> {
                                Notification n3 = new Notification(deviceId, evOther,
                                        "Off the waitlist",
                                        "Good news — you're off the waitlist for this event. Please accept or decline enrollment.",
                                        Notification.TYPE_GOT_OFF_WAITLIST);
                                notificationDB.saveNotification(n3,
                                        id3 -> {
                                            prefs.edit().putBoolean(KEY_SEEDED_TRIPLE_MAR_2026, true).apply();
                                            after.run();
                                        },
                                        e3 -> {
                                            Log.e("NotificationsActivity", "Sample seed 3 failed", e3);
                                            after.run();
                                        });
                            },
                            e2 -> {
                                Log.e("NotificationsActivity", "Sample seed 2 failed", e2);
                                after.run();
                            });
                },
                e1 -> {
                    Log.e("NotificationsActivity", "Sample seed 1 failed", e1);
                    after.run();
                });
    }

    private void loadNotifications() {
        if (deviceId == null || deviceId.isEmpty()) {
            adapter.setItems(new java.util.ArrayList<>(), null);
            if (swipeRefreshLayout != null) swipeRefreshLayout.setRefreshing(false);
            return;
        }
        notificationDB.getNotificationsForRecipient(deviceId,
            list -> {
                java.util.List<com.example.cobaltevents.model.Notification> notifications =
                        list != null ? list : new java.util.ArrayList<>();
                loadStatusesForNotifications(notifications, eventIdToStatus -> {
                    currentNotifications = notifications;
                    currentEventIdToStatus = eventIdToStatus != null
                            ? eventIdToStatus
                            : new java.util.HashMap<>();
                    adapter.setItems(currentNotifications, currentEventIdToStatus);
                    if (tvEmpty != null) tvEmpty.setVisibility(notifications.isEmpty() ? View.VISIBLE : View.GONE);
                    if (swipeRefreshLayout != null) swipeRefreshLayout.setRefreshing(false);
                });
            },
            e -> {
                Log.e("NotificationsActivity", "Load notifications failed", e);
                String msg = e.getMessage() != null ? e.getMessage() : "Failed to load notifications";
                Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
                adapter.setItems(new java.util.ArrayList<>(), null);
                if (tvEmpty != null) tvEmpty.setVisibility(View.VISIBLE);
                if (swipeRefreshLayout != null) swipeRefreshLayout.setRefreshing(false);
            });
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
            adapter.setItems(currentNotifications, currentEventIdToStatus);
        }
    }

    private void setupBottomNavigation() {
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

        ImageView iv = findViewById(R.id.iv_nav_notifications);
        TextView tv = findViewById(R.id.tv_nav_notifications);
        if (iv != null) iv.setColorFilter(getResources().getColor(R.color.user_green));
        if (tv != null) tv.setTextColor(getResources().getColor(R.color.user_green));
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
                        if (WaitingList.STATUS_DECLINED.equals(currentStatus)) {
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

    private void updateResponseOnly(
            com.example.cobaltevents.model.Notification notification,
            String newResponse) {
        if (notification == null || notification.getId() == null || notification.getId().isEmpty()) {
            Toast.makeText(this, "Unable to update notification response.", Toast.LENGTH_SHORT).show();
            notificationActionInProgress = false;
            return;
        }
        notification.setResponse(newResponse);
        adapter.setItems(currentNotifications, currentEventIdToStatus);
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
                    if (WaitingList.STATUS_DECLINED.equals(currentStatus)) {
                        // For faster feedback, update the response badge locally now.
                        currentEventIdToStatus.put(eventId, WaitingList.STATUS_DECLINED);
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

    /** Same connectivity check as join/leave waitlist (Wi‑Fi, cellular, or Ethernet). */
    private boolean isNetworkAvailable() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return false;
        Network network = cm.getActiveNetwork();
        if (network == null) return false;
        NetworkCapabilities caps = cm.getNetworkCapabilities(network);
        return caps != null && (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                || caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
                || caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET));
    }

}
