package com.example.cobaltevents.ui;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.content.ContextCompat;

import com.bumptech.glide.Glide;
import com.example.cobaltevents.R;
import com.example.cobaltevents.db.EntrantDB;
import com.example.cobaltevents.db.EventDB;
import com.example.cobaltevents.db.WaitingListDB;
import com.example.cobaltevents.model.Event;
import com.example.cobaltevents.model.WaitingList;
import com.example.cobaltevents.model.Entrant;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import com.example.cobaltevents.controller.GeolocationController;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import android.app.Dialog;
import android.view.Window;
import android.graphics.drawable.ColorDrawable;
import android.graphics.Color;

public class EventDetailActivity extends AppCompatActivity {

    private ImageView eventPoster;
    private TextView eventName;
    private TextView eventDescription;
    private TextView eventLocation;
    private TextView tvWaitlistCount;
    private TextView btnJoinLeave;
    private LinearLayout layoutEventNotifications;
    private SwitchCompat switchEventNotifications;

    private EventDB eventDB;
    private WaitingListDB waitingListDB;
    private EntrantDB entrantDB;
    private Event currentEvent;
    private WaitingList activeRegistration;
    private String deviceId;

    private SharedPreferences prefs;
    private GeolocationController geolocationController;
    private String pendingGeoJoinEventId;
    private final ActivityResultLauncher<String> locationPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted && pendingGeoJoinEventId != null) {
                    joinAndRecordLocation(pendingGeoJoinEventId);
                } else if (!granted) {
                    Toast.makeText(this, "Location permission denied — cannot join this event.", Toast.LENGTH_LONG).show();
                }
                pendingGeoJoinEventId = null;
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_event_detail);

        eventPoster = findViewById(R.id.eventPoster);
        eventName = findViewById(R.id.eventName);
        eventDescription = findViewById(R.id.eventDescription);
        eventLocation = findViewById(R.id.eventLocation);
        tvWaitlistCount = findViewById(R.id.tv_waitlist_count);
        btnJoinLeave = findViewById(R.id.btn_join_leave);
        layoutEventNotifications = findViewById(R.id.layout_event_notifications);
        switchEventNotifications = findViewById(R.id.switch_event_notifications);

        eventDB = new EventDB();
        waitingListDB = new WaitingListDB();
        entrantDB = new EntrantDB(this);
        deviceId = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);
        prefs = getSharedPreferences("cobalt_prefs", MODE_PRIVATE);
        geolocationController = new GeolocationController();

        String eventId = getIntent().getStringExtra("eventId");

        btnJoinLeave.setOnClickListener(v -> {
            if (currentEvent == null || currentEvent.getEventId() == null) return;
            if (activeRegistration == null) {
                showJoinConfirmDialog(currentEvent.getEventId());
            } else {
                showLeaveConfirmDialog();
            }
        });

        if (eventId != null) {
            loadEvent(eventId);
        }
    }

    private void loadEvent(String eventId) {

        eventDB.getEvent(eventId,
                event -> {
                    if (event != null) {
                        currentEvent = event;
                        eventName.setText(event.getName());
                        eventDescription.setText(event.getDescription());
                        eventLocation.setText(event.getLocation());

                        if (event.getPosterImageUrl() != null && !event.getPosterImageUrl().isEmpty()) {
                            Glide.with(this)
                                    .load(event.getPosterImageUrl())
                                    .placeholder(android.R.drawable.ic_menu_gallery)
                                    .into(eventPoster);
                            eventPoster.setVisibility(android.view.View.VISIBLE);
                        } else {
                            eventPoster.setVisibility(android.view.View.VISIBLE);
                            eventPoster.setImageResource(android.R.drawable.ic_menu_gallery);
                        }

                        refreshWaitlistCount();
                        refreshRegistrationState();
                    }
                },
                e -> e.printStackTrace()
        );
    }

    private void refreshWaitlistCount() {
        if (currentEvent == null || currentEvent.getEventId() == null) return;
        waitingListDB.getActiveCountForEvent(currentEvent.getEventId(),
                count -> tvWaitlistCount.setText(count + " on waitlist"),
                e -> tvWaitlistCount.setText(""));
    }

    private void refreshRegistrationState() {
        if (currentEvent == null || currentEvent.getEventId() == null) return;
        waitingListDB.getActiveRegistrationForEvent(currentEvent.getEventId(), deviceId,
                reg -> {
                    activeRegistration = reg;
                    applyJoinLeaveUi();
                },
                e -> {
                    activeRegistration = null;
                    applyJoinLeaveUi();
                });
    }

    private void applyJoinLeaveUi() {
        boolean joined = activeRegistration != null;
        btnJoinLeave.setText(joined ? getString(R.string.leave_waitlist_button) : getString(R.string.join_waitlist_button));
        btnJoinLeave.setBackgroundResource(joined ? R.drawable.bg_button_red_pill : R.drawable.bg_button_join_solid);

        if (joined) {
            layoutEventNotifications.setVisibility(View.VISIBLE);
            boolean enabled = prefs.getBoolean(notificationsKey(), false);
            switchEventNotifications.setChecked(enabled);
            switchEventNotifications.setOnCheckedChangeListener((buttonView, isChecked) ->
                    prefs.edit().putBoolean(notificationsKey(), isChecked).apply()
            );
        } else {
            layoutEventNotifications.setVisibility(View.GONE);
            switchEventNotifications.setOnCheckedChangeListener(null);
        }
    }

    private String notificationsKey() {
        String eventId = (currentEvent != null) ? currentEvent.getEventId() : "";
        return "event_notifications_" + eventId;
    }

    private void showJoinConfirmDialog(String eventId) {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_join_waitlist_confirm, null);
        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setView(dialogView)
                .create();

        TextView btnCancel = dialogView.findViewById(R.id.btn_cancel);
        TextView btnJoin = dialogView.findViewById(R.id.btn_join);
        View btnClose = dialogView.findViewById(R.id.btn_close);

        btnCancel.setOnClickListener(v -> dialog.dismiss());
        if (btnClose != null) btnClose.setOnClickListener(v -> dialog.dismiss());
        btnJoin.setOnClickListener(v -> {
            dialog.dismiss();
            joinWaitlist(eventId);
        });

        dialog.show();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }
    }

    private void joinWaitlist(String eventId) {
        if (!isNetworkAvailable()) {
            Toast.makeText(this, getString(R.string.waitlist_fail) + " No internet connection.", Toast.LENGTH_SHORT).show();
            return;
        }
        Entrant entrant = entrantDB.getEntrant();
        if (!entrant.isValidName() || !entrant.isValidEmail()) {
            Toast.makeText(this, "Please complete your name and email in Account settings before joining a waitlist.", Toast.LENGTH_LONG).show();
            startActivity(new Intent(this, AccountSettingsActivity.class));
            return;
        }
        if (currentEvent != null && currentEvent.isGeolocationRequired()) {
            if (geolocationController.hasLocationPermission(this)) {
                joinAndRecordLocation(eventId);
            } else {
                pendingGeoJoinEventId = eventId;
                new androidx.appcompat.app.AlertDialog.Builder(this)
                        .setTitle("Location Required")
                        .setMessage("This event requires your location to be recorded when joining the waitlist.")
                        .setPositiveButton("Allow", (d, w) ->
                                locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION))
                        .setNegativeButton("Cancel", null)
                        .show();
            }
            return;
        }
        performJoinWaitlist(eventId);
    }

    private void joinAndRecordLocation(String eventId) {
        if (currentEvent == null) return;
        geolocationController.checkDistanceForEvent(this, currentEvent,
                new GeolocationController.GeoJoinCallback() {
                    @Override
                    public void onAllowed(android.location.Location userLocation) {
                        performJoinWaitlist(eventId);
                        geolocationController.recordLocationForEvent(
                                EventDetailActivity.this, deviceId, eventId,
                                userLocation, unused -> {}, e -> {});
                    }
                    @Override
                    public void onBlocked(float distanceMeters) {
                        int km = Math.round(distanceMeters / 1000f);
                        Toast.makeText(EventDetailActivity.this,
                                "You are " + km + "km away. Must be within 30km to join.",
                                Toast.LENGTH_LONG).show();
                    }
                    @Override
                    public void onError(String message) {
                        Toast.makeText(EventDetailActivity.this, message, Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void performJoinWaitlist(String eventId) {
        Entrant entrant = entrantDB.getEntrant();
        WaitingList registration = new WaitingList(
                eventId,
                deviceId,
                1,
                entrant.getName(),
                entrant.getEmail(),
                entrant.getPhone(),
                WaitingList.NOTIFY_EMAIL
        );
        waitingListDB.addRegistration(registration,
                id -> {
                    Toast.makeText(this, R.string.waitlist_success, Toast.LENGTH_SHORT).show();
                    activeRegistration = registration;
                    applyJoinLeaveUi();
                    refreshWaitlistCount();
                },
                e -> Toast.makeText(this, getString(R.string.waitlist_fail) + " " + e.getMessage(), Toast.LENGTH_SHORT).show());
    }

    private void showLeaveConfirmDialog() {
        if (activeRegistration == null || activeRegistration.getDeviceId() == null) return;
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_leave_waitlist_confirm, null);
        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setView(dialogView)
                .create();

        TextView btnCancel = dialogView.findViewById(R.id.btn_cancel);
        TextView btnLeave = dialogView.findViewById(R.id.btn_leave);
        btnCancel.setOnClickListener(v -> dialog.dismiss());
        btnLeave.setOnClickListener(v -> {
            dialog.dismiss();
            leaveWaitlist();
        });

        dialog.show();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }
    }

    private void leaveWaitlist() {
        if (activeRegistration == null || activeRegistration.getDeviceId() == null) return;
        if (!isNetworkAvailable()) {
            Toast.makeText(this, "Failed to leave waitlist: No internet connection.", Toast.LENGTH_SHORT).show();
            return;
        }
        waitingListDB.deleteRegistration(currentEvent.getEventId(), activeRegistration.getDeviceId(),
                unused -> {
                    Toast.makeText(this, "Left waitlist.", Toast.LENGTH_SHORT).show();
                    refreshWaitlistCount();
                    refreshRegistrationState();
                },
                e -> Toast.makeText(this, "Failed to leave waitlist: " + e.getMessage(), Toast.LENGTH_SHORT).show());
    }

    private boolean isNetworkAvailable() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return false;
        Network network = cm.getActiveNetwork();
        if (network == null) return false;
        NetworkCapabilities caps = cm.getNetworkCapabilities(network);
        return caps != null && (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                || caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
                || caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET));
    }
}