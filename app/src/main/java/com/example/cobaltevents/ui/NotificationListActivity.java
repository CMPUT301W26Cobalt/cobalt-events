package com.example.cobaltevents.ui;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.cobaltevents.R;
import com.example.cobaltevents.controller.EntrantController;
import com.example.cobaltevents.controller.NotificationController;
import com.example.cobaltevents.model.Notification;
import com.example.cobaltevents.ui.adapter.NotificationAdapter;
import com.google.android.material.appbar.MaterialToolbar;

import java.util.ArrayList;
import java.util.List;

/**
 * Displays in-app notification list. Tapping a notification opens EventDetailActivity.
 * US 01.04.01 criteria 3: Notification appears in the app.
 * US 01.04.01 criteria 4: Notification links to event details.
 * US 01.04.02 criteria 3: Notification appears in the app.
 */
public class NotificationListActivity extends AppCompatActivity implements NotificationAdapter.OnNotificationClickListener {

    private static final String TAG = "NotificationListAct";

    private NotificationController notificationController;
    private NotificationAdapter adapter;
    private List<Notification> notifications;
    private RecyclerView recyclerView;
    private TextView tvEmpty;
    private String deviceId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_notification_list);

        notificationController = new NotificationController(this);
        deviceId = EntrantController.getDeviceId(this);

        // Toolbar with back navigation
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        tvEmpty = findViewById(R.id.tv_empty);
        recyclerView = findViewById(R.id.rv_notifications);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        notifications = new ArrayList<>();
        adapter = new NotificationAdapter(notifications, this);
        recyclerView.setAdapter(adapter);

        loadNotifications();
    }

    private void loadNotifications() {
        notificationController.getNotifications(deviceId, notifList -> {
            notifications.clear();
            notifications.addAll(notifList);
            adapter.notifyDataSetChanged();

            if (notifications.isEmpty()) {
                tvEmpty.setVisibility(View.VISIBLE);
                recyclerView.setVisibility(View.GONE);
            } else {
                tvEmpty.setVisibility(View.GONE);
                recyclerView.setVisibility(View.VISIBLE);
            }
        }, e -> Log.e(TAG, "Failed to load notifications", e));
    }

    /**
     * US 01.04.01 criteria 4: Tapping a notification opens EventDetailActivity with eventId.
     */
    @Override
    public void onNotificationClick(Notification notification) {
        // Mark as read
        if (!notification.isRead()) {
            notificationController.markAsRead(notification.getId());
            notification.setRead(true);
            adapter.notifyDataSetChanged();
        }

        // Open event details
        Intent intent = new Intent(this, EventDetailActivity.class);
        intent.putExtra("eventId", notification.getEventId());
        startActivity(intent);
    }
}
