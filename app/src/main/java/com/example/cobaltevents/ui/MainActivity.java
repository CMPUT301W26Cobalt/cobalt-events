package com.example.cobaltevents.ui;

import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;

import androidx.appcompat.app.AppCompatActivity;

import com.example.cobaltevents.R;
import com.example.cobaltevents.controller.NotificationController;

public class MainActivity extends AppCompatActivity {

    private NotificationController notificationController;
    private String deviceId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        deviceId = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);
        notificationController = new NotificationController();
        notificationController.createNotificationChannel(this);
        notificationController.requestNotificationPermission(this);
        Intent intent = new Intent(this, NotificationsActivity.class);
        startActivity(intent);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (notificationController != null && deviceId != null) {
            notificationController.startNotificationListener(this, deviceId);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (notificationController != null) {
            notificationController.stopNotificationListener();
        }
    }
}
