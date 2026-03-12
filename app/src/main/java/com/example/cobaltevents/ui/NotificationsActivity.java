package com.example.cobaltevents.ui;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.cobaltevents.R;
import com.example.cobaltevents.db.EntrantDB;
import com.example.cobaltevents.db.NotificationDB;
import com.example.cobaltevents.db.WaitingListDB;
import com.example.cobaltevents.model.Notification;
import com.example.cobaltevents.model.WaitlistEntryInfo;
import com.example.cobaltevents.ui.adapter.NotificationListAdapter;

public class NotificationsActivity extends AppCompatActivity {

    private NotificationDB notificationDB;
    private WaitingListDB waitingListDB;
    private NotificationListAdapter adapter;
    private String deviceId;
    private static boolean sShowedWaitlistIndexToast;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_notifications);

        notificationDB = new NotificationDB();
        waitingListDB = new WaitingListDB();
        deviceId = new EntrantDB(this).getEntrant().getDeviceId();

        RecyclerView recycler = findViewById(R.id.recycler_notifications);
        recycler.setLayoutManager(new LinearLayoutManager(this));
        adapter = new NotificationListAdapter();
        adapter.setOnActionListener(new NotificationListAdapter.OnActionListener() {
            @Override
            public void onAccept(Notification notification) {
                updateWaitlistStatus(notification, Notification.STATUS_ACCEPTED);
            }

            @Override
            public void onDecline(Notification notification) {
                updateWaitlistStatus(notification, Notification.STATUS_REJECTED);
            }
        });
        recycler.setAdapter(adapter);

        findViewById(R.id.btn_push_sample).setOnClickListener(v -> pushSampleNotifications());

        setupBottomNavigation();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (deviceId != null) {
            loadNotifications();
        }
    }

    private void loadNotifications() {
        if (deviceId == null || deviceId.isEmpty()) {
            adapter.setItems(new java.util.ArrayList<>());
            return;
        }
        notificationDB.getNotificationsForRecipient(deviceId,
            list -> {
                waitingListDB.getWaitlistInfoForDevice(deviceId,
                    infoMap -> {
                        if (list == null) {
                            adapter.setItems(new java.util.ArrayList<>());
                            return;
                        }
                        // No waitlist entries for this device: show all notifications so sample push and others still appear
                        if (infoMap == null || infoMap.isEmpty()) {
                            java.util.Map<String, String> statusByEventId = new java.util.HashMap<>();
                            for (Notification n : list) {
                                if (n.getEventId() != null) statusByEventId.put(n.getEventId(), Notification.STATUS_PENDING);
                            }
                            adapter.setItems(list, statusByEventId);
                            return;
                        }
                        java.util.List<Notification> filtered = new java.util.ArrayList<>();
                        java.util.Map<String, String> statusByEventId = new java.util.HashMap<>();
                        for (Notification n : list) {
                            if (n.getEventId() == null) continue;
                            WaitlistEntryInfo info = infoMap.get(n.getEventId());
                            if (info == null || !info.isNotificationsAllowed()) continue;
                            filtered.add(n);
                            statusByEventId.put(n.getEventId(), info.getStatus());
                        }
                        adapter.setItems(filtered, statusByEventId);
                    },
                    e -> {
                        // Waitlist query failed (e.g. collection group index not deployed): show all so list isn't empty
                        Log.w("NotificationsActivity", "Waitlist info failed, showing all notifications", e);
                        if (list != null && !list.isEmpty()) {
                            java.util.Map<String, String> statusByEventId = new java.util.HashMap<>();
                            for (Notification n : list) {
                                if (n.getEventId() != null) statusByEventId.put(n.getEventId(), Notification.STATUS_PENDING);
                            }
                            adapter.setItems(list, statusByEventId);
                            if (!sShowedWaitlistIndexToast) {
                                sShowedWaitlistIndexToast = true;
                                Toast.makeText(this, "To filter by event notifications: Firebase Console → Firestore → Indexes → add index for collection group \"entries\", field \"deviceId\"", Toast.LENGTH_LONG).show();
                            }
                        } else {
                            adapter.setItems(list != null ? list : new java.util.ArrayList<>(), new java.util.HashMap<>());
                        }
                    });
            },
            e -> {
                Log.e("NotificationsActivity", "Load notifications failed", e);
                String msg = e.getMessage() != null ? e.getMessage() : "Failed to load notifications";
                if (msg.contains("index") || msg.contains("INDEX")) {
                    Toast.makeText(this, "Notifications index required. Check Logcat for the index link.", Toast.LENGTH_LONG).show();
                } else {
                    Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
                }
                adapter.setItems(new java.util.ArrayList<>());
            });
    }

    /** Updates waitlist entry status (accepted/rejected); adapter shows status from its map. */
    private void updateWaitlistStatus(Notification notification, String status) {
        if (notification.getEventId() == null || notification.getRecipientId() == null) return;
        waitingListDB.updateStatus(notification.getEventId(), notification.getRecipientId(), status,
            v -> adapter.updateNotification(notification, status),
            e -> Toast.makeText(this, "Failed to update", Toast.LENGTH_SHORT).show());
    }

    /**
     * Pushes sample notifications for testing, based on the events (Test Event, Music Club, Swim Club).
     * Recipient is the current device. Only shows in log for events where this device is on the waitlist with notifications on.
     */
    private void pushSampleNotifications() {
        if (deviceId == null || deviceId.isEmpty()) {
            Toast.makeText(this, "No device ID", Toast.LENGTH_SHORT).show();
            return;
        }
        // Event IDs and names from your Firestore (Test Event, Music Club, Swim Club)
        String[][] samples = {
            { "P4EXTaLYngU6lUyo4DDB", "Test Event", "You've been selected!", "You have been chosen to attend Test Event. Tap to view details.", Notification.TYPE_SELECTED },
            { "clO1KsyWpVmRUuGPrFSh", "Music Club", "Update: Music Club", "Reminder: Music Club event is coming up. Check your status.", Notification.TYPE_SELECTED },
            { "XSOzVt1rjpyf7SzWnduU", "Swim Club", "Spots available", "There may be spots opening for Swim Club. Tap to view.", Notification.TYPE_SELECTED }
        };
        final int[] saved = { 0 };
        final int total = samples.length;
        for (String[] row : samples) {
            Notification n = new Notification(deviceId, row[0], row[2], row[3], row[4]);
            notificationDB.saveNotification(n,
                v -> {
                    saved[0]++;
                    if (saved[0] == total) {
                        findViewById(android.R.id.content).postDelayed(() -> {
                            loadNotifications();
                            Toast.makeText(NotificationsActivity.this, "Sample notifications pushed", Toast.LENGTH_SHORT).show();
                        }, 400);
                    }
                },
                e -> {
                    Toast.makeText(NotificationsActivity.this, "Failed to push sample: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
        }
    }

    private void setupBottomNavigation() {
        findViewById(R.id.nav_events).setOnClickListener(v -> {
            startActivity(new Intent(this, EventListActivity.class));
            finish();
        });
        findViewById(R.id.nav_my_events).setOnClickListener(v -> {
            startActivity(new Intent(this, EventHistoryActivity.class));
            finish();
        });
        findViewById(R.id.nav_qr).setOnClickListener(v -> {
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
}
