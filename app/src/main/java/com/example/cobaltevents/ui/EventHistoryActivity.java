package com.example.cobaltevents.ui;

import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AlertDialog;
import android.app.Dialog;
import android.view.Window;
import android.graphics.drawable.ColorDrawable;
import android.graphics.Color;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.example.cobaltevents.R;
import com.example.cobaltevents.controller.EventController;
import com.example.cobaltevents.db.NotificationDB;
import com.example.cobaltevents.db.WaitingListDB;
import com.example.cobaltevents.model.Event;
import com.example.cobaltevents.model.EventHistory;
import com.example.cobaltevents.model.Notification;
import com.example.cobaltevents.model.WaitingList;
import com.example.cobaltevents.ui.adapter.EventHistoryAdapter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * US 01.02.03: Display entrant's event registration history
 */
public class EventHistoryActivity extends AppCompatActivity {
    
    private RecyclerView recyclerView;
    private ProgressBar progressBar;
    private TextView tvEmpty;
    private SwipeRefreshLayout swipeRefreshLayout;
    private EventHistoryAdapter adapter;
    private TextView tabUpcoming;
    private TextView tabPast;
    private final List<EventHistory> allHistory = new ArrayList<>();
    
    private String deviceId;
    private WaitingListDB waitingListDB;
    private NotificationDB notificationDB;
    private EventController eventController;
    private boolean refreshFromUserGesture = false;
    private java.util.Map<String, String> effectiveStatusByEventId = new java.util.HashMap<>();
    // Prevent accidental navigation to EventDetailActivity while the delete/leave dialog is showing.
    private volatile boolean historyDeleteDialogShowing = false;
    private enum Tab { UPCOMING, PAST }
    private Tab currentTab = Tab.UPCOMING;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_event_history);
        
        deviceId = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);
        waitingListDB = new WaitingListDB();
        notificationDB = new NotificationDB();
        eventController = new EventController();
        
        recyclerView = findViewById(R.id.recycler_history);
        progressBar = findViewById(R.id.progress_bar);
        tvEmpty = findViewById(R.id.tv_empty);
        swipeRefreshLayout = findViewById(R.id.swipe_refresh_history);
        
        adapter = new EventHistoryAdapter(new ArrayList<>(), new EventHistoryAdapter.Listener() {
            @Override
            public void onHistoryClick(EventHistory history) {
                EventHistoryActivity.this.onEventClick(history);
            }
            @Override
            public void onDeleteClick(EventHistory history) {
                confirmRemoveFromWaitlist(history);
            }
        });
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setHasFixedSize(true);
        recyclerView.setItemViewCacheSize(12);
        recyclerView.setAdapter(adapter);

        setupBottomNavigation();
        setupTabs();
        if (swipeRefreshLayout != null) {
            swipeRefreshLayout.setOnRefreshListener(() -> {
                refreshFromUserGesture = true;
                loadHistory();
            });
            swipeRefreshLayout.setColorSchemeColors(getResources().getColor(R.color.user_green));
        }
        loadHistory();
    }

    private void setupBottomNavigation() {
        FrameLayout navContainer = findViewById(R.id.nav_container);
        LayoutInflater.from(this).inflate(R.layout.partial_bottom_nav, navContainer, true);

        navContainer.findViewById(R.id.nav_events).setOnClickListener(v -> {
            startActivity(new Intent(this, EventListActivity.class));
            finish();
        });
        navContainer.findViewById(R.id.nav_my_events).setOnClickListener(v -> {});
        navContainer.findViewById(R.id.nav_qr).setOnClickListener(v ->
                startActivity(new Intent(this, QRScanActivity.class)));
        navContainer.findViewById(R.id.nav_notifications).setOnClickListener(v -> {
            startActivity(new Intent(this, NotificationsActivity.class));
            finish();
        });
        navContainer.findViewById(R.id.nav_account).setOnClickListener(v -> {
            startActivity(new Intent(this, AccountSettingsActivity.class));
            finish();
        });

        ImageView iv = navContainer.findViewById(R.id.iv_nav_my_events);
        TextView tv = navContainer.findViewById(R.id.tv_nav_my_events);
        if (iv != null) iv.setColorFilter(getResources().getColor(R.color.user_green));
        if (tv != null) tv.setTextColor(getResources().getColor(R.color.user_green));
    }
    
    private void setupTabs() {
        tabUpcoming = findViewById(R.id.tab_upcoming);
        tabPast = findViewById(R.id.tab_past);
        
        if (tabUpcoming != null) {
            tabUpcoming.setOnClickListener(v -> {
                currentTab = Tab.UPCOMING;
                applyFilter();
                updateTabStyles();
            });
        }
        if (tabPast != null) {
            tabPast.setOnClickListener(v -> {
                currentTab = Tab.PAST;
                applyFilter();
                updateTabStyles();
            });
        }
        updateTabStyles();
    }
    
    private void updateTabStyles() {
        if (tabUpcoming == null || tabPast == null) return;
        int white = getResources().getColor(android.R.color.white);
        int green = getResources().getColor(R.color.user_green);
        
        switch (currentTab) {
            case UPCOMING:
                tabUpcoming.setBackgroundResource(R.drawable.tab_active_green_solid);
                tabUpcoming.setTextColor(white);
                tabPast.setBackgroundResource(R.drawable.tab_inactive_white);
                tabPast.setTextColor(green);
                break;
            case PAST:
                tabPast.setBackgroundResource(R.drawable.tab_active_green_solid);
                tabPast.setTextColor(white);
                tabUpcoming.setBackgroundResource(R.drawable.tab_inactive_white);
                tabUpcoming.setTextColor(green);
                break;
        }
    }
    
    private void loadHistory() {
        progressBar.setVisibility(View.VISIBLE);
        tvEmpty.setVisibility(View.GONE);
        if (swipeRefreshLayout != null) swipeRefreshLayout.setRefreshing(refreshFromUserGesture);
        allHistory.clear();

        // No device: only need catalog (same as before).
        if (deviceId == null || deviceId.isEmpty()) {
            eventController.getAllEvents(events -> {
                if (events == null || events.isEmpty()) {
                    progressBar.setVisibility(View.GONE);
                    tvEmpty.setVisibility(View.VISIBLE);
                    adapter.updateHistory(new ArrayList<>());
                    if (swipeRefreshLayout != null) swipeRefreshLayout.setRefreshing(false);
                    refreshFromUserGesture = false;
                    return;
                }
                effectiveStatusByEventId.clear();
                completeHistoryUiRefresh(new ArrayList<>());
            }, e -> {
                progressBar.setVisibility(View.GONE);
                if (swipeRefreshLayout != null) swipeRefreshLayout.setRefreshing(false);
                refreshFromUserGesture = false;
                Toast.makeText(this, "Failed to load history: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }, () -> runOnUiThread(() ->
                    Toast.makeText(this, R.string.firebase_cache_fallback_message, Toast.LENGTH_LONG).show()));
            return;
        }

        // Overlap: catalog + entrant history + notifications (wall-clock ~max of the three).
        final AtomicInteger remaining = new AtomicInteger(3);
        final boolean[] eventsOk = { true };
        final List<Event>[] eventsHolder = new List[1];
        final List<WaitingList>[] historyHolder = new List[1];
        final List<Notification>[] notificationsHolder = new List[1];
        final boolean[] historyFailed = { false };
        final boolean[] notificationsFailed = { false };
        final List<EventHistory>[] legacyHistoryResult = new List[1];
        final boolean[] legacyStarted = { false };
        /** True after catalog success or failure — used so history error can complete if legacy never runs. */
        final boolean[] eventsResolved = { false };

        Runnable onPartComplete = () -> {
            if (remaining.decrementAndGet() != 0) return;
            runOnUiThread(() -> {
                if (!eventsOk[0]) {
                    return;
                }
                List<Event> eventList = eventsHolder[0] != null ? eventsHolder[0] : new ArrayList<>();
                if (eventList.isEmpty()) {
                    progressBar.setVisibility(View.GONE);
                    tvEmpty.setVisibility(View.VISIBLE);
                    adapter.updateHistory(new ArrayList<>());
                    if (swipeRefreshLayout != null) swipeRefreshLayout.setRefreshing(false);
                    refreshFromUserGesture = false;
                    return;
                }
                completeHistoryParallelMerge(
                        eventList,
                        historyHolder[0],
                        historyFailed[0],
                        notificationsHolder[0],
                        notificationsFailed[0],
                        legacyHistoryResult);
            });
        };

        Runnable tryStartLegacyIfNeeded = () -> {
            if (!historyFailed[0] || legacyStarted[0]) {
                return;
            }
            List<Event> el = eventsHolder[0];
            if (el == null) {
                return;
            }
            legacyStarted[0] = true;
            loadHistoryRegistrationsLegacy(el, legacyHistoryResult, () -> runOnUiThread(onPartComplete));
        };

        eventController.getAllEvents(
                events -> {
                    eventsResolved[0] = true;
                    eventsHolder[0] = events != null ? events : new ArrayList<>();
                    tryStartLegacyIfNeeded.run();
                    onPartComplete.run();
                },
                e -> {
                    eventsResolved[0] = true;
                    progressBar.setVisibility(View.GONE);
                    if (swipeRefreshLayout != null) swipeRefreshLayout.setRefreshing(false);
                    refreshFromUserGesture = false;
                    Toast.makeText(this, "Failed to load history: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    eventsOk[0] = false;
                    onPartComplete.run();
                },
                () -> runOnUiThread(() ->
                        Toast.makeText(this, R.string.firebase_cache_fallback_message, Toast.LENGTH_LONG).show()));

        waitingListDB.getEntrantHistory(deviceId,
                list -> {
                    historyHolder[0] = list;
                    onPartComplete.run();
                },
                err -> {
                    historyFailed[0] = true;
                    tryStartLegacyIfNeeded.run();
                    // If catalog already resolved and legacy did not start, complete the history "slot"
                    // (e.g. events load failed so no waitlist docs to scan).
                    if (eventsResolved[0] && !legacyStarted[0]) {
                        onPartComplete.run();
                    }
                });

        notificationDB.getNotificationsForRecipient(deviceId,
                list -> {
                    notificationsHolder[0] = list;
                    onPartComplete.run();
                },
                err -> {
                    notificationsFailed[0] = true;
                    onPartComplete.run();
                });
    }

    /** Per-event registration fetch when collection-group query fails (matches pre-optimization behavior). */
    private void loadHistoryRegistrationsLegacy(List<Event> eventList,
                                                List<EventHistory>[] out,
                                                Runnable whenDone) {
        if (eventList == null || eventList.isEmpty()) {
            out[0] = new ArrayList<>();
            whenDone.run();
            return;
        }
        runOnUiThread(() -> Toast.makeText(this, R.string.registration_history_fallback_message,
                Toast.LENGTH_LONG).show());
        final List<EventHistory> temp = new ArrayList<>();
        final AtomicInteger pending = new AtomicInteger(eventList.size());
        for (Event event : eventList) {
            if (event == null || event.getEventId() == null) {
                runOnUiThread(() -> {
                    if (pending.decrementAndGet() == 0) {
                        out[0] = temp;
                        whenDone.run();
                    }
                });
                continue;
            }
            final String eventId = event.getEventId();
            waitingListDB.getRegistrationForEventAnyStatus(eventId, deviceId,
                    reg -> runOnUiThread(() -> {
                        if (reg != null) {
                            temp.add(new EventHistory(event, reg));
                        }
                        if (pending.decrementAndGet() == 0) {
                            out[0] = temp;
                            whenDone.run();
                        }
                    }),
                    e -> runOnUiThread(() -> {
                        if (pending.decrementAndGet() == 0) {
                            out[0] = temp;
                            whenDone.run();
                        }
                    }));
        }
    }

    /**
     * Join catalog events with one collection-group registration query and notification overrides
     * (same result as N per-event reads + sequential notifications).
     */
    private void completeHistoryParallelMerge(List<Event> eventList,
                                              List<WaitingList> history,
                                              boolean historyFailed,
                                              List<Notification> notifications,
                                              boolean notificationsFailed,
                                              List<EventHistory>[] legacyHistoryResult) {
        List<EventHistory> temp = new ArrayList<>();
        if (historyFailed) {
            if (legacyHistoryResult != null && legacyHistoryResult[0] != null) {
                temp = legacyHistoryResult[0];
                legacyHistoryResult[0] = null;
            }
        } else if (history != null) {
            Map<String, WaitingList> byEventId = new HashMap<>();
            for (WaitingList w : history) {
                if (w == null || w.getEventId() == null) continue;
                byEventId.put(w.getEventId(), w);
            }
            for (Event event : eventList) {
                if (event == null || event.getEventId() == null) continue;
                WaitingList reg = byEventId.get(event.getEventId());
                if (reg != null) {
                    temp.add(new EventHistory(event, reg));
                }
            }
        }

        effectiveStatusByEventId.clear();
        if (!notificationsFailed && notifications != null) {
            applyNotificationEffectiveStatuses(notifications);
        }

        completeHistoryUiRefresh(temp);
    }

    private void applyNotificationEffectiveStatuses(List<Notification> notifications) {
        if (notifications == null) return;
        for (Notification n : notifications) {
            if (n == null || n.getEventId() == null || n.getType() == null) continue;
            if (effectiveStatusByEventId.containsKey(n.getEventId())) continue;
            String overrideStatus = getOverrideStatusFromNotification(n);
            if (overrideStatus != null) {
                effectiveStatusByEventId.put(n.getEventId(), overrideStatus);
            }
        }
    }

    private String getOverrideStatusFromNotification(Notification n) {
        if (n == null || n.getType() == null) return null;
        String type = n.getType();
        String response = n.getResponse();
        if (Notification.TYPE_CO_ORGANIZER.equals(type)) {
            // No waitlist connection for co-organization.
            return null;
        }
        if (Notification.TYPE_NOT_SELECTED.equals(type)) {
            // X (not-selected) means the user wasn't selected, but they remain on waitlist.
            return WaitingList.STATUS_NOT_SELECTED;
        }
        if (Notification.TYPE_PRIVATE_EVENT.equals(type)) {
            // Private invitations should not force My Events state
            // until a waitlist entry exists.
            return null;
        }
        if (Notification.TYPE_SELECTED.equals(type) || Notification.TYPE_GOT_OFF_WAITLIST.equals(type)) {
            if (Notification.RESPONSE_ACCEPTED.equals(response)) return WaitingList.STATUS_ENROLLED;
            if (Notification.RESPONSE_DECLINED.equals(response)) return WaitingList.STATUS_DECLINED;
            // Pending selected/star must not flip UI by itself.
            return null;
        }
        return null;
    }

    private void completeHistoryUiRefresh(List<EventHistory> historyList) {
        progressBar.setVisibility(View.GONE);
        allHistory.clear();
        allHistory.addAll(historyList);
        adapter.setEffectiveStatusByEventId(effectiveStatusByEventId);
        applyFilter();
        updateTabStyles();
        if (swipeRefreshLayout != null) swipeRefreshLayout.setRefreshing(false);
        refreshFromUserGesture = false;
    }
    
    private void applyFilter() {
        List<EventHistory> filtered = new ArrayList<>();
        Date now = new Date();
        for (EventHistory eh : allHistory) {
            Event event = eh.getEvent();
            if (event == null) continue;
            switch (currentTab) {
                case UPCOMING:
                    if (event.getEventDate() != null && event.getEventDate().toDate().after(now)) {
                        filtered.add(eh);
                    }
                    break;
                case PAST:
                    if (event.getEventDate() != null && event.getEventDate().toDate().before(now)) {
                        filtered.add(eh);
                    }
                    break;
            }
        }
        Collections.sort(filtered, new Comparator<EventHistory>() {
            @Override
            public int compare(EventHistory a, EventHistory b) {
                int groupA = statusSortGroup(a != null ? getEffectiveStatusForHistory(a) : null);
                int groupB = statusSortGroup(b != null ? getEffectiveStatusForHistory(b) : null);
                if (groupA != groupB) {
                    return Integer.compare(groupA, groupB);
                }
                Date da = a.getEvent().getEventDate() != null ? a.getEvent().getEventDate().toDate() : new Date(0);
                Date db = b.getEvent().getEventDate() != null ? b.getEvent().getEventDate().toDate() : new Date(0);
                return Long.compare(db.getTime(), da.getTime());
            }
        });
        adapter.updateHistory(filtered);
        tvEmpty.setVisibility(filtered.isEmpty() ? View.VISIBLE : View.GONE);
    }

    /**
     * Sort priority for My Events:
     * 0 = pending/enrolled (top), 1 = other statuses, 2 = declined-style statuses (bottom).
     */
    private int statusSortGroup(String status) {
        if (status == null) return 1;
        if (WaitingList.STATUS_PENDING.equals(status)
                || WaitingList.STATUS_ENROLLED.equals(status)) {
            return 0;
        }
        if (WaitingList.STATUS_DECLINED.equals(status)
                || WaitingList.STATUS_DECLINED_FOUND_REPLACEMENT.equals(status)
                || "declined".equals(status)
                || WaitingList.STATUS_NOT_SELECTED.equals(status)
                || "rejected".equals(status)) {
            return 2;
        }
        return 1;
    }
    
    private void onEventClick(EventHistory history) {
        if (historyDeleteDialogShowing) return;
        if (history == null || history.getEvent() == null) return;
        EventReadOnlyCardPopup.show(this, history.getEvent());
    }

    private void confirmRemoveFromWaitlist(EventHistory history) {
        if (history == null || history.getEvent() == null || history.getEvent().getEventId() == null) return;
        String status = getEffectiveStatusForHistory(history);
        if (WaitingList.STATUS_ENROLLED.equals(status)
                || WaitingList.STATUS_DECLINED.equals(status)
                || WaitingList.STATUS_DECLINED_FOUND_REPLACEMENT.equals(status)) {
            Toast.makeText(this, "You cannot leave after a final decision.", Toast.LENGTH_SHORT).show();
            return;
        }
        String eventId = history.getEvent().getEventId();
        notificationDB.getNotificationsForRecipientAndEvent(deviceId, eventId,
                notifications -> {
                    boolean hasSelectionNotification = false;
                    if (notifications != null) {
                        for (Notification n : notifications) {
                            if (n == null || n.getType() == null) continue;
                            String type = n.getType();
                            if (Notification.TYPE_SELECTED.equals(type)
                                    || Notification.TYPE_GOT_OFF_WAITLIST.equals(type)) {
                                hasSelectionNotification = true;
                                break;
                            }
                        }
                    }
                    if (hasSelectionNotification) {
                        Toast.makeText(this,
                                "Cannot leave waitlist was selected for enrollment.",
                                Toast.LENGTH_LONG).show();
                        return;
                    }
                    showLeaveHistoryDialog(history);
                },
                e -> Toast.makeText(this,
                        "Unable to verify selection status. Please try again.",
                        Toast.LENGTH_SHORT).show());
    }

    private String getEffectiveStatusForHistory(EventHistory history) {
        if (history == null || history.getEvent() == null) return history != null ? history.getStatus() : null;
        String eventId = history.getEvent().getEventId();
        if (eventId == null) return history.getStatus();
        String effective = effectiveStatusByEventId.get(eventId);
        if (effective == null) return history.getStatus();

        // Never let an X/not-selected notification overwrite a final DB decision.
        if (WaitingList.STATUS_NOT_SELECTED.equals(effective)) {
            String base = history.getStatus();
            if (WaitingList.STATUS_DECLINED.equals(base)
                    || WaitingList.STATUS_DECLINED_FOUND_REPLACEMENT.equals(base)
                    || "declined".equalsIgnoreCase(base)) {
                return base;
            }
            if (WaitingList.STATUS_ENROLLED.equals(base) || WaitingList.STATUS_SELECTED.equals(base)) {
                return base;
            }
        }

        if (WaitingList.STATUS_ENROLLED.equals(effective)) {
            String base = history.getStatus();
            if (WaitingList.STATUS_PENDING.equals(base)
                    || WaitingList.STATUS_SELECTED.equals(base)
                    || WaitingList.STATUS_NOT_SELECTED.equals(base)) {
                return base;
            }
        }

        return effective;
    }

    private void showLeaveHistoryDialog(EventHistory history) {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_leave_waitlist_confirm, null);
        AlertDialog dialog = new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setView(dialogView)
                .create();

        TextView btnCancel = dialogView.findViewById(R.id.btn_cancel);
        TextView btnLeave = dialogView.findViewById(R.id.btn_leave);
        btnCancel.setOnClickListener(v -> dialog.dismiss());
        btnLeave.setOnClickListener(v -> {
            dialog.dismiss();
            waitingListDB.deleteRegistration(history.getEvent().getEventId(), deviceId,
                    unused -> {
                        Toast.makeText(this, "Left waitlist.", Toast.LENGTH_SHORT).show();
                        loadHistory();
                    },
                    e -> Toast.makeText(this, "Failed to leave waitlist: " + e.getMessage(), Toast.LENGTH_SHORT).show());
        });

        dialog.show();
        historyDeleteDialogShowing = true;
        dialog.setOnDismissListener(d -> historyDeleteDialogShowing = false);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
        }
    }
}
