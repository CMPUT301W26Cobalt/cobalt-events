package com.example.cobaltevents.ui;

import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;

import androidx.appcompat.app.AppCompatActivity;

import com.example.cobaltevents.R;
import com.example.cobaltevents.controller.NotificationController;

/**
 * App entry point. Initializes the notification system for US 01.04.01,
 * then redirects to EventListActivity.
 *
 * Notification setup:
 * - Creates notification channel (required for Android 8+)
 * - Requests POST_NOTIFICATIONS permission (required for Android 13+)
 * - Starts a real-time Firestore listener for incoming notifications
 * - Stops the listener when the activity pauses
 */
public class MainActivity extends AppCompatActivity {

    private NotificationController notificationController;
    private String deviceId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Get this device's unique ID (used as recipientId for notifications)
        deviceId = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);

        // Initialize notification system
        notificationController = new NotificationController();
        notificationController.createNotificationChannel(this);
        notificationController.requestNotificationPermission(this);

        // Navigate to event list
        Intent intent = new Intent(this, EventListActivity.class);
        startActivity(intent);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Start listening for new notifications in Firestore
        if (notificationController != null && deviceId != null) {
            notificationController.startNotificationListener(this, deviceId);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Stop the listener to avoid background updates
        if (notificationController != null) {
            notificationController.stopNotificationListener();
        }
    }
}
