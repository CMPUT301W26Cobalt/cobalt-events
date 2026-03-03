package com.example.cobaltevents.controller;

import android.app.Activity;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.Log;

import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import com.example.cobaltevents.R;
import com.example.cobaltevents.db.EntrantDB;
import com.example.cobaltevents.db.NotificationDB;
import com.example.cobaltevents.model.Event;
import com.example.cobaltevents.model.Notification;
import com.example.cobaltevents.ui.EventDetailActivity;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.firestore.DocumentChange;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.ArrayList;
import java.util.List;

/**
 * Central notification logic for all three user stories.
 * US 01.04.01: Creates notification channel, requests permission, sends "selected" notifications,
 *              shows system notifications with PendingIntent linking to event details.
 * US 01.04.02: Sends "not_selected" notifications with clear outcome messages.
 * US 01.04.03: Checks entrant.notificationsEnabled before sending any notification.
 */
public class NotificationController {

    private static final String TAG = "NotificationController";
    private static final String CHANNEL_ID = "cobalt_events_lottery";
    private static final String CHANNEL_NAME = "Lottery Results";
    private static final int PERMISSION_REQUEST_CODE = 1001;

    private final NotificationDB notificationDB;
    private final EntrantDB entrantDB;
    private final Context context;

    public NotificationController(Context context) {
        this.context = context;
        this.notificationDB = new NotificationDB();
        this.entrantDB = new EntrantDB();
    }

    /**
     * Creates the Android notification channel. Must be called on app startup (API 26+).
     * US 01.04.01 criteria 1: Prompt user to enable notifications.
     */
    public static void createNotificationChannel(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription("Notifications for event lottery results");
            NotificationManager manager = context.getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    /**
     * Requests POST_NOTIFICATIONS runtime permission on Android 13+ (API 33).
     * US 01.04.01 criteria 1: Prompt user to enable notifications.
     */
    public static void requestNotificationPermission(Activity activity) {
        if (Build.VERSION.SDK_INT >= 33) {
            if (ContextCompat.checkSelfPermission(activity, "android.permission.POST_NOTIFICATIONS")
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(activity,
                        new String[]{"android.permission.POST_NOTIFICATIONS"},
                        PERMISSION_REQUEST_CODE);
            }
        }
    }

    /**
     * Sends lottery result notifications to all selected and not-selected entrants.
     * For each entrant: checks opt-out preference, saves Firestore doc, shows system notification.
     * US 01.04.01 criteria 2: Notification sent when user is selected.
     * US 01.04.02 criteria 1: Notification sent when lottery closes.
     */
    public void sendLotteryNotifications(List<String> selectedIds, List<String> notSelectedIds, Event event) {
        for (String entrantId : selectedIds) {
            sendNotificationToEntrant(
                    entrantId,
                    "You've been selected!",
                    "Congratulations! You've been selected for " + event.getTitle() + "!",
                    Notification.TYPE_SELECTED,
                    event.getEventId()
            );
        }
        for (String entrantId : notSelectedIds) {
            sendNotificationToEntrant(
                    entrantId,
                    "Lottery Result",
                    "Unfortunately, you were not selected for " + event.getTitle() + ".",
                    Notification.TYPE_NOT_SELECTED,
                    event.getEventId()
            );
        }
    }

    /**
     * Sends a notification to a single entrant after checking their opt-out preference.
     * US 01.04.01 criteria 5: If notifications disabled, nothing is sent.
     * US 01.04.02 criteria 4: Opt-out preference respected.
     * US 01.04.03: Checks notificationsEnabled before sending.
     */
    private void sendNotificationToEntrant(String entrantId, String title, String message, String type, String eventId) {
        entrantDB.getEntrant(entrantId, entrant -> {
            if (entrant == null || !entrant.isNotificationsEnabled()) {
                Log.d(TAG, "Skipping notification for " + entrantId + " (opt-out or not found)");
                return;
            }

            Notification notification = new Notification(entrantId, eventId, title, message, type);
            notificationDB.saveNotification(notification,
                    unused -> {
                        Log.d(TAG, "Notification saved for " + entrantId);
                        showSystemNotification(title, message, eventId, entrantId.hashCode());
                    },
                    e -> Log.e(TAG, "Failed to save notification for " + entrantId, e)
            );
        }, e -> Log.e(TAG, "Failed to get entrant " + entrantId, e));
    }

    /**
     * Shows an Android system notification with a PendingIntent that opens EventDetailActivity.
     * US 01.04.01 criteria 4: Notification links to event details.
     */
    private void showSystemNotification(String title, String message, String eventId, int notificationId) {
        Intent intent = new Intent(context, EventDetailActivity.class);
        intent.putExtra("eventId", eventId);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        PendingIntent pendingIntent = PendingIntent.getActivity(
                context,
                notificationId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(title)
                .setContentText(message)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true);

        if (ContextCompat.checkSelfPermission(context, "android.permission.POST_NOTIFICATIONS")
                == PackageManager.PERMISSION_GRANTED || Build.VERSION.SDK_INT < 33) {
            NotificationManagerCompat.from(context).notify(notificationId, builder.build());
        }
    }

    /**
     * Fetches all in-app notifications for the current user.
     * US 01.04.01 criteria 3: A notification appears in the app.
     * US 01.04.02 criteria 3: A notification appears in the app.
     */
    public void getNotifications(String deviceId, OnSuccessListener<List<Notification>> onSuccess, OnFailureListener onFailure) {
        notificationDB.getNotificationsForRecipient(deviceId, onSuccess, onFailure);
    }

    /** Mark a notification as read when the user taps it. */
    public void markAsRead(String notificationId) {
        notificationDB.markAsRead(notificationId, unused -> {}, e -> Log.e(TAG, "Failed to mark as read", e));
    }

    /**
     * Starts a real-time Firestore listener for new notifications.
     * When a new notification document appears, shows a system notification.
     */
    public ListenerRegistration startNotificationListener(String deviceId) {
        return notificationDB.listenForNotifications(deviceId, (snapshots, error) -> {
            if (error != null) {
                Log.e(TAG, "Notification listener error", error);
                return;
            }
            if (snapshots != null) {
                for (DocumentChange dc : snapshots.getDocumentChanges()) {
                    if (dc.getType() == DocumentChange.Type.ADDED) {
                        Notification n = dc.getDocument().toObject(Notification.class);
                        if (n != null && !n.isRead()) {
                            n.setId(dc.getDocument().getId());
                            showSystemNotification(n.getTitle(), n.getMessage(), n.getEventId(), n.getId().hashCode());
                        }
                    }
                }
            }
        });
    }
}
