package com.example.cobaltevents.ui;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import androidx.appcompat.app.AppCompatActivity;

import com.example.cobaltevents.R;
import com.example.cobaltevents.controller.EntrantController;
import com.example.cobaltevents.controller.NotificationController;
import com.google.firebase.firestore.ListenerRegistration;

/**
 * App entry point and navigation hub.
 * US 01.04.01 criteria 1: Creates notification channel and requests permission on startup.
 */
public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";

    private EntrantController entrantController;
    private NotificationController notificationController;
    private ListenerRegistration notificationListener;
    private String deviceId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Initialize controllers
        entrantController = new EntrantController();
        notificationController = new NotificationController(this);
        deviceId = EntrantController.getDeviceId(this);

        // US 01.04.01 criteria 1: Create notification channel and request permission
        NotificationController.createNotificationChannel(this);
        NotificationController.requestNotificationPermission(this);

        // Ensure entrant profile exists (device-based auth)
        entrantController.getOrCreateEntrant(this, new EntrantController.OnEntrantReadyListener() {
            @Override
            public void onEntrantReady(com.example.cobaltevents.model.Entrant entrant) {
                Log.d(TAG, "Entrant ready: " + entrant.getDeviceId());
            }

            @Override
            public void onError(Exception e) {
                Log.e(TAG, "Failed to get/create entrant", e);
            }
        });

        // Navigation buttons
        findViewById(R.id.btn_events).setOnClickListener(v ->
                startActivity(new Intent(this, EventListActivity.class)));

        findViewById(R.id.btn_profile).setOnClickListener(v ->
                startActivity(new Intent(this, EntrantActivity.class)));

        findViewById(R.id.btn_notifications).setOnClickListener(v ->
                startActivity(new Intent(this, NotificationListActivity.class)));

        findViewById(R.id.btn_manage_event).setOnClickListener(v ->
                startActivity(new Intent(this, EventManageActivity.class)));
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Start listening for new notifications
        notificationListener = notificationController.startNotificationListener(deviceId);
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Stop listening when app goes to background
        if (notificationListener != null) {
            notificationListener.remove();
            notificationListener = null;
        }
    }
}
