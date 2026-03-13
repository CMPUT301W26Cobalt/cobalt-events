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
import com.example.cobaltevents.controller.LotteryController;
import com.example.cobaltevents.db.EntrantDB;
import com.example.cobaltevents.db.NotificationDB;
import com.example.cobaltevents.db.WaitingListDB;
import com.example.cobaltevents.model.Notification;
import com.example.cobaltevents.model.WaitlistEntryInfo;
import com.example.cobaltevents.ui.adapter.NotificationListAdapter;

public class NotificationsActivity extends AppCompatActivity {

    private NotificationDB notificationDB;
    private WaitingListDB waitingListDB;
    private LotteryController lotteryController;
    private NotificationListAdapter adapter;
    private String deviceId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_notifications);

        notificationDB = new NotificationDB();
        waitingListDB = new WaitingListDB();
        lotteryController = new LotteryController();
        deviceId = new EntrantDB(this).getEntrant().getDeviceId();

        RecyclerView recycler = findViewById(R.id.recycler_notifications);
        recycler.setLayoutManager(new LinearLayoutManager(this));
        adapter = new NotificationListAdapter();
        adapter.setOnActionListener(new NotificationListAdapter.OnActionListener() {
            @Override
            public void onAccept(Notification notification) {
                acceptInvitation(notification);
            }

            @Override
            public void onDecline(Notification notification) {
                declineInvitation(notification);
            }
        });
        recycler.setAdapter(adapter);

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
                if (list == null) {
                    adapter.setItems(new java.util.ArrayList<>());
                    return;
                }
                java.util.Set<String> eventIds = new java.util.LinkedHashSet<>();
                for (Notification n : list) {
                    if (n.getEventId() != null) eventIds.add(n.getEventId());
                }
                waitingListDB.getWaitlistInfoForEvents(deviceId, new java.util.ArrayList<>(eventIds),
                    infoMap -> {
                        if (infoMap == null) infoMap = new java.util.HashMap<>();
                        java.util.List<Notification> filtered = new java.util.ArrayList<>();
                        java.util.Map<String, String> statusByEventId = new java.util.HashMap<>();
                        for (Notification n : list) {
                            if (n.getEventId() == null) continue;
                            WaitlistEntryInfo info = infoMap.get(n.getEventId());
                            if (info == null) {
                                filtered.add(n);
                                statusByEventId.put(n.getEventId(), Notification.STATUS_PENDING);
                            } else if (info.isNotificationsAllowed()) {
                                filtered.add(n);
                                statusByEventId.put(n.getEventId(), info.getStatus());
                            }
                        }
                        adapter.setItems(filtered, statusByEventId);
                    },
                    e -> {
                        Log.w("NotificationsActivity", "Waitlist info failed", e);
                        adapter.setItems(list, new java.util.HashMap<>());
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

    private void acceptInvitation(Notification notification) {
        if (notification.getEventId() == null) return;
        lotteryController.acceptInvitation(deviceId, notification.getEventId(),
            v -> {
                updateReadStatus(notification, Notification.READ_ACCEPTED);
                Toast.makeText(this, "Invitation Accepted", Toast.LENGTH_SHORT).show();
            },
            e -> Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show());
    }

    private void declineInvitation(Notification notification) {
        if (notification.getEventId() == null) return;
        lotteryController.declineInvitation(deviceId, notification.getEventId(),
            v -> {
                updateReadStatus(notification, Notification.READ_REJECTED);
                Toast.makeText(this, "Invitation Declined", Toast.LENGTH_SHORT).show();
            },
            e -> Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show());
    }

    private void updateReadStatus(Notification notification, String status) {
        if (notification.getId() == null) return;
        notificationDB.updateReadStatus(notification.getId(), status,
            v -> {
                notification.setRead(status);
                adapter.updateNotification(notification, status);
            },
            e -> Toast.makeText(this, "Failed to update notification status", Toast.LENGTH_SHORT).show());
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
