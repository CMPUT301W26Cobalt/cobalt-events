package com.example.cobaltevents.controller;

import android.app.Activity;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.Manifest;

import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.example.cobaltevents.R;
import com.example.cobaltevents.db.NotificationDB;
import com.example.cobaltevents.model.Notification;
import com.example.cobaltevents.ui.NotificationsActivity;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class NotificationController {

    private static final String CHANNEL_ID = "cobalt_events_notifications";
    private static final int PERMISSION_REQUEST_CODE = 1001;

    private final NotificationDB notificationDB;
    private ListenerRegistration listenerRegistration;
    private final Set<String> shownNotificationIds = new HashSet<>();

    public NotificationController() {
        this.notificationDB = new NotificationDB();
    }

    public void createNotificationChannel(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = context.getString(R.string.notification_channel_name);
            String description = "Notifications for event lottery results";
            int importance = NotificationManager.IMPORTANCE_HIGH;

            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, name, importance);
            channel.setDescription(description);

            NotificationManager manager = context.getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    public void requestNotificationPermission(Activity activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ActivityCompat.checkSelfPermission(activity, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(activity,
                        new String[]{Manifest.permission.POST_NOTIFICATIONS},
                        PERMISSION_REQUEST_CODE);
            }
        }
    }

    public void sendSelectedNotification(String entrantId, String eventId, String eventName) {
        Notification notification = new Notification(
                entrantId,
                eventId,
                "Congratulations! You've been selected!",
                "You have been chosen to attend " + eventName + ". Tap to view event details.",
                Notification.TYPE_SELECTED
        );

        notificationDB.saveNotification(notification,
                unused -> {},
                e -> e.printStackTrace()
        );
    }

    public void startNotificationListener(Context context, String deviceId) {
        stopNotificationListener();

        // Global toggle: if event update notifications are disabled, don't listen/show
        android.content.SharedPreferences prefs =
                context.getSharedPreferences("cobalt_prefs", android.content.Context.MODE_PRIVATE);
        boolean enabled = prefs.getBoolean("notification_event_updates", true);
        if (!enabled) {
            return;
        }

        listenerRegistration = notificationDB.listenForNotifications(deviceId,
                new NotificationDB.OnNotificationListener() {
                    @Override
                    public void onNotifications(List<Notification> notifications) {
                        for (Notification n : notifications) {
                            if (n.getId() != null && !shownNotificationIds.contains(n.getId())) {
                                shownNotificationIds.add(n.getId());
                                showSystemNotification(context, n);
                            }
                        }
                    }

                    @Override
                    public void onError(Exception e) {
                        e.printStackTrace();
                    }
                });
    }

    public void stopNotificationListener() {
        if (listenerRegistration != null) {
            listenerRegistration.remove();
            listenerRegistration = null;
        }
    }

    private void showSystemNotification(Context context, Notification notification) {
        // Open the notifications list screen; no event-specific deep link.
        Intent intent = new Intent(context, NotificationsActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        PendingIntent pendingIntent = PendingIntent.getActivity(
                context,
                notification.getId().hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle(notification.getTitle())
                .setContentText(notification.getMessage())
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent);

        NotificationManagerCompat manager = NotificationManagerCompat.from(context);
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                        == PackageManager.PERMISSION_GRANTED) {
            manager.notify(notification.getId().hashCode(), builder.build());
        }
    }
}
