package com.example.cobaltevents.ui;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;
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
import com.example.cobaltevents.model.Notification;
import com.example.cobaltevents.ui.adapter.NotificationListAdapter;

public class NotificationsActivity extends AppCompatActivity {

    private NotificationDB notificationDB;
    private LotteryController lotteryController;
    private NotificationListAdapter adapter;
    private String deviceId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_notifications);

        notificationDB = new NotificationDB();
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

        loadNotifications();
        setupBottomNavigation(getIntent().getBooleanExtra("fromOrganizer", false));
    }

    private void loadNotifications() {
        if (deviceId == null || deviceId.isEmpty()) {
            adapter.setItems(new java.util.ArrayList<>());
            return;
        }
        notificationDB.getNotificationsForRecipient(deviceId,
            list -> adapter.setItems(list),
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
                adapter.updateNotification(notification);
            },
            e -> Toast.makeText(this, "Failed to update notification status", Toast.LENGTH_SHORT).show());
    }

    private void setupBottomNavigation(boolean fromOrganizer) {
        FrameLayout navContainer = findViewById(R.id.nav_container);

        View headerBar = findViewById(R.id.header_bar);
        if (headerBar != null) {
            headerBar.setBackgroundColor(getResources().getColor(
                    fromOrganizer ? R.color.organizer_blue : R.color.notif_header_teal));
        }

        if (fromOrganizer) {
            LayoutInflater.from(this).inflate(R.layout.partial_bottom_nav_organizer, navContainer, true);

            navContainer.findViewById(R.id.nav_dashboard).setOnClickListener(v -> finish());
            navContainer.findViewById(R.id.nav_my_events).setOnClickListener(v -> finish());
            navContainer.findViewById(R.id.nav_create).setOnClickListener(v ->
                    startActivity(new Intent(this, EventCreateActivity.class)));
            navContainer.findViewById(R.id.nav_notifications).setOnClickListener(v -> {});
            navContainer.findViewById(R.id.nav_account).setOnClickListener(v -> {
                startActivity(new Intent(this, AccountSettingsActivity.class));
                finish();
            });

            ImageView iv = navContainer.findViewById(R.id.iv_nav_notifications);
            TextView tv = navContainer.findViewById(R.id.tv_nav_notifications);
            if (iv != null) iv.setColorFilter(getResources().getColor(R.color.organizer_blue));
            if (tv != null) tv.setTextColor(getResources().getColor(R.color.organizer_blue));
        } else {
            LayoutInflater.from(this).inflate(R.layout.partial_bottom_nav, navContainer, true);

            navContainer.findViewById(R.id.nav_events).setOnClickListener(v -> {
                startActivity(new Intent(this, EventListActivity.class));
                finish();
            });
            navContainer.findViewById(R.id.nav_my_events).setOnClickListener(v -> {
                startActivity(new Intent(this, EventHistoryActivity.class));
                finish();
            });
            navContainer.findViewById(R.id.nav_qr).setOnClickListener(v ->
                    startActivity(new Intent(this, QRScanActivity.class)));
            navContainer.findViewById(R.id.nav_notifications).setOnClickListener(v -> {});
            navContainer.findViewById(R.id.nav_account).setOnClickListener(v -> {
                startActivity(new Intent(this, AccountSettingsActivity.class));
                finish();
            });

            ImageView iv = navContainer.findViewById(R.id.iv_nav_notifications);
            TextView tv = navContainer.findViewById(R.id.tv_nav_notifications);
            if (iv != null) iv.setColorFilter(getResources().getColor(R.color.user_green));
            if (tv != null) tv.setTextColor(getResources().getColor(R.color.user_green));
        }
    }
}
