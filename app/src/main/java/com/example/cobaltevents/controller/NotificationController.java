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
import com.example.cobaltevents.ui.EventDetailActivity;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Controller for notification logic.
 * Handles US 01.04.01 — Notification When Selected.
 *
 * Responsibilities:
 * - Creates an Android notification channel (required for API 26+)
 * - Requests POST_NOTIFICATIONS permission (required for API 33+)
 * - Sends a "selected" notification to Firestore when an entrant wins the lottery
 * - Listens for new notifications in Firestore in real-time
 * - Displays system notifications with a tap action that opens EventDetailActivity
 */
public class NotificationController {

    private static final String CHANNEL_ID = "cobalt_events_notifications";
    private static final int PERMISSION_REQUEST_CODE = 1001;

    private final NotificationDB notificationDB;
    private ListenerRegistration listenerRegistration;

    /** Tracks notification IDs we've already shown, to avoid duplicates on listener updates. */
    private final Set<String> shownNotificationIds = new HashSet<>();

    public NotificationController() {
        this.notificationDB = new NotificationDB();
    }

    /**
     * Creates the notification channel. Must be called once at app startup (API 26+).
     * Safe to call multiple times — Android ignores duplicate channel creation.
     */
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

    /**
     * Requests POST_NOTIFICATIONS permission on Android 13+ (API 33).
     * On older versions this is a no-op since notification permission is granted by default.
     */
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

    /**
     * Creates a "selected" notification document in Firestore.
     * Called by the organizer/lottery system when an entrant is chosen.
     * The entrant's device will pick this up via the real-time listener.
     *
     * @param entrantId  device ID of the selected entrant
     * @param eventId    ID of the event they were selected for
     * @param eventName  name of the event (for the notification message)
     */
    public void sendSelectedNotification(String entrantId, String eventId, String eventName) {
        Notification notification = new Notification(
                entrantId,
                eventId,
                "Congratulations! You've been selected!",
                "You have been chosen to attend " + eventName + ". Tap to view event details.",
                Notification.TYPE_SELECTED
        );

        notificationDB.saveNotification(notification,
                unused -> { /* success — entrant's listener will pick it up */ },
                e -> e.printStackTrace()
        );
    }

    /**
     * Starts a real-time Firestore listener for unread notifications for this device.
     * When a new notification arrives, it is shown as an Android system notification.
     *
     * @param context   application or activity context
     * @param deviceId  the current device's ID (used as recipientId in Firestore)
     */
    public void startNotificationListener(Context context, String deviceId) {
        // Remove existing listener before starting a new one
        stopNotificationListener();

        listenerRegistration = notificationDB.listenForNotifications(deviceId,
                new NotificationDB.OnNotificationListener() {
                    @Override
                    public void onNotifications(List<Notification> notifications) {
                        for (Notification n : notifications) {
                            // Only show each notification once per session
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

    /**
     * Stops the Firestore real-time listener. Call this in onPause() or onDestroy().
     */
    public void stopNotificationListener() {
        if (listenerRegistration != null) {
            listenerRegistration.remove();
            listenerRegistration = null;
        }
    }

    /**
     * Displays an Android system notification for a Firestore notification.
     * Tapping the notification opens EventDetailActivity for the related event.
     */
    private void showSystemNotification(Context context, Notification notification) {
        // Build an intent that opens EventDetailActivity with the eventId
        Intent intent = new Intent(context, EventDetailActivity.class);
        intent.putExtra("eventId", notification.getEventId());
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        PendingIntent pendingIntent = PendingIntent.getActivity(
                context,
                notification.getId().hashCode(),  // unique request code per notification
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

        // Check permission before posting (required for API 33+)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                        == PackageManager.PERMISSION_GRANTED) {
            manager.notify(notification.getId().hashCode(), builder.build());
        }
    }
}
