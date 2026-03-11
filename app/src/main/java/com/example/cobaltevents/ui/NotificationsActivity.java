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
import com.example.cobaltevents.model.Notification;
import com.example.cobaltevents.ui.adapter.NotificationListAdapter;

public class NotificationsActivity extends AppCompatActivity {

    private NotificationDB notificationDB;
    private NotificationListAdapter adapter;
    private String deviceId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_notifications);

        notificationDB = new NotificationDB();
        deviceId = new EntrantDB(this).getEntrant().getDeviceId();

        RecyclerView recycler = findViewById(R.id.recycler_notifications);
        recycler.setLayoutManager(new LinearLayoutManager(this));
        adapter = new NotificationListAdapter();
        adapter.setOnActionListener(new NotificationListAdapter.OnActionListener() {
            @Override
            public void onAccept(Notification notification) {
                updateReadStatus(notification, Notification.READ_ACCEPTED);
            }

            @Override
            public void onDecline(Notification notification) {
                updateReadStatus(notification, Notification.READ_REJECTED);
            }
        });
        recycler.setAdapter(adapter);

        loadNotifications();
        setupBottomNavigation();
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

    private void updateReadStatus(Notification notification, String status) {
        if (notification.getId() == null) return;
        notificationDB.updateReadStatus(notification.getId(), status,
            v -> {
                notification.setRead(status);
                adapter.updateNotification(notification);
            },
            e -> Toast.makeText(this, "Failed to update", Toast.LENGTH_SHORT).show());
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
