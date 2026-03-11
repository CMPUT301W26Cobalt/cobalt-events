package com.example.cobaltevents.ui;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.cobaltevents.R;
import com.example.cobaltevents.controller.EntrantController;
import com.example.cobaltevents.db.EntrantDB;
import com.example.cobaltevents.db.EventDB;
import com.example.cobaltevents.db.ImageDB;
import com.example.cobaltevents.db.ProfileDB;
import com.example.cobaltevents.db.WaitingListDB;
import com.example.cobaltevents.model.Entrant;
import com.example.cobaltevents.model.Event;
import com.example.cobaltevents.model.WaitingList;
import com.example.cobaltevents.ui.adapter.NotificationEventAdapter;
import com.squareup.picasso.Picasso;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class AccountSettingsActivity extends AppCompatActivity {

    private TextView tvName, tvEmail, tvPhone;
    private ImageView profileImage;
    private TextView profileInitials;
    private View profileViewPanel;
    private View profileEditPanel;
    private EditText editName, editEmail, editPhone;
    private View btnSaveChanges;
    private View btnCancelEdit;

    private EntrantController controller;
    private EntrantDB entrantDB;
    private ImageDB imageDB;
    private ProfileDB profileDB;
    private WaitingListDB waitingListDB;
    private EventDB eventDB;
    private Entrant currentEntrant;
    private SharedPreferences notificationPrefs;
    private RecyclerView recyclerNotificationEvents;
    private NotificationEventAdapter notificationEventAdapter;

    private final ActivityResultLauncher<String> getContent = registerForActivityResult(
            new ActivityResultContracts.GetContent(),
            uri -> {
                if (uri != null) {
                    uploadImage(uri);
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_account_settings);

        entrantDB = new EntrantDB(this);
        imageDB = new ImageDB();
        profileDB = new ProfileDB();
        waitingListDB = new WaitingListDB();
        eventDB = new EventDB();
        notificationPrefs = getSharedPreferences("cobalt_prefs", MODE_PRIVATE);
        controller = new EntrantController(entrantDB);
        currentEntrant = entrantDB.getEntrant();

        tvName = findViewById(R.id.tv_name);
        tvEmail = findViewById(R.id.tv_email);
        tvPhone = findViewById(R.id.tv_phone);
        profileImage = findViewById(R.id.profile_image);
        profileInitials = findViewById(R.id.profile_initials);
        profileViewPanel = findViewById(R.id.profile_view_panel);
        profileEditPanel = findViewById(R.id.profile_edit_panel);
        editName = findViewById(R.id.edit_name);
        editEmail = findViewById(R.id.edit_email);
        editPhone = findViewById(R.id.edit_phone);
        btnSaveChanges = findViewById(R.id.btn_save_changes);
        btnCancelEdit = findViewById(R.id.btn_cancel_edit);
        View btnEditInfo = findViewById(R.id.btn_edit_info);
        View btnDeleteAccount = findViewById(R.id.btn_delete_account);
        View deleteConfirmPanel = findViewById(R.id.delete_confirm_panel);
        View btnDeleteCancel = findViewById(R.id.btn_delete_cancel);
        View btnDeleteConfirm = findViewById(R.id.btn_delete_confirm);

        displayProfile();

        btnEditInfo.setOnClickListener(v -> switchToEditMode());
        btnSaveChanges.setOnClickListener(v -> saveAndSwitchToViewMode());
        btnCancelEdit.setOnClickListener(v -> switchToViewMode());
        btnDeleteAccount.setOnClickListener(v -> {
            btnDeleteAccount.setVisibility(View.GONE);
            deleteConfirmPanel.setVisibility(View.VISIBLE);
        });
        btnDeleteCancel.setOnClickListener(v -> {
            deleteConfirmPanel.setVisibility(View.GONE);
            btnDeleteAccount.setVisibility(View.VISIBLE);
        });
        btnDeleteConfirm.setOnClickListener(v -> performAccountDeletion());

        recyclerNotificationEvents = findViewById(R.id.recycler_notification_events);
        recyclerNotificationEvents.setLayoutManager(new LinearLayoutManager(this));
        notificationEventAdapter = new NotificationEventAdapter();
        notificationEventAdapter.setOnToggleListener((eventId, enabled) -> {
            notificationPrefs.edit().putBoolean("event_notifications_" + eventId, enabled).apply();
        });
        recyclerNotificationEvents.setAdapter(notificationEventAdapter);

        androidx.appcompat.widget.SwitchCompat switchGeneral = findViewById(R.id.switch_general);
        androidx.appcompat.widget.SwitchCompat switchEventUpdates = findViewById(R.id.switch_event_updates);
        applySwitchTints(switchGeneral);
        applySwitchTints(switchEventUpdates);
        switchGeneral.setChecked(notificationPrefs.getBoolean("notification_general", true));
        switchEventUpdates.setChecked(notificationPrefs.getBoolean("notification_event_updates", true));
        switchGeneral.setOnCheckedChangeListener((v, isChecked) -> notificationPrefs.edit().putBoolean("notification_general", isChecked).apply());
        switchEventUpdates.setOnCheckedChangeListener((v, isChecked) -> notificationPrefs.edit().putBoolean("notification_event_updates", isChecked).apply());

        loadNotificationEvents();
        setupAccountModeSelector();
        setupBottomNavigation();
    }

    private void setupAccountModeSelector() {
        View btnUserMode = findViewById(R.id.btn_user_mode);
        View btnOrganizerMode = findViewById(R.id.btn_organizer_mode);
        if (btnUserMode == null || btnOrganizerMode == null) return;

        String currentMode = notificationPrefs.getString("account_mode", "user");
        updateModeSelectionUI(currentMode, btnUserMode, btnOrganizerMode);

        btnUserMode.setOnClickListener(v -> {
            notificationPrefs.edit().putString("account_mode", "user").apply();
            updateModeSelectionUI("user", btnUserMode, btnOrganizerMode);
            Intent intent = new Intent(this, EventListActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
        });

        btnOrganizerMode.setOnClickListener(v -> {
            notificationPrefs.edit().putString("account_mode", "organizer").apply();
            updateModeSelectionUI("organizer", btnUserMode, btnOrganizerMode);
            Intent intent = new Intent(this, OrganizerActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
        });
    }

    private void updateModeSelectionUI(String mode, View btnUserMode, View btnOrganizerMode) {
        boolean isUser = "user".equals(mode);

        btnUserMode.setBackground(isUser
                ? getDrawable(R.drawable.bg_mode_option_selected)
                : getDrawable(R.drawable.bg_mode_option));

        View radioUser = btnUserMode.findViewById(R.id.radio_user_mode);
        View radioOrg = btnOrganizerMode.findViewById(R.id.radio_organizer_mode);
        if (radioUser != null) {
            radioUser.setVisibility(isUser ? View.VISIBLE : View.GONE);
            radioUser.setBackground(isUser
                    ? getDrawable(R.drawable.bg_mode_option_selected)
                    : getDrawable(R.drawable.bg_mode_option));
        }

        btnOrganizerMode.setBackground(!isUser
                ? getDrawable(R.drawable.bg_mode_option_selected)
                : getDrawable(R.drawable.bg_mode_option));

        if (radioOrg != null) {
            radioOrg.setVisibility(!isUser ? View.VISIBLE : View.GONE);
            radioOrg.setBackground(!isUser
                    ? getDrawable(R.drawable.bg_mode_option_selected)
                    : getDrawable(R.drawable.bg_mode_option));
        }

        TextView tvUserLabel = btnUserMode.findViewById(R.id.tv_user_mode_label);
        TextView tvOrgLabel = btnOrganizerMode.findViewById(R.id.tv_organizer_mode_label);
        if (tvUserLabel != null) tvUserLabel.setTextColor(getResources().getColor(
                isUser ? R.color.organizer_blue : R.color.text_primary));
        if (tvOrgLabel != null) tvOrgLabel.setTextColor(getResources().getColor(
                !isUser ? R.color.organizer_blue : R.color.text_primary));
    }

    private void applySwitchTints(androidx.appcompat.widget.SwitchCompat switchCompat) {
        switchCompat.setThumbTintList(ContextCompat.getColorStateList(this, R.color.thumb_white));
        switchCompat.setTrackTintList(ContextCompat.getColorStateList(this, R.color.switch_track_selector));
    }

    private void loadNotificationEvents() {
        String deviceId = currentEntrant.getDeviceId();
        waitingListDB.getEntrantHistory(deviceId,
            registrations -> {
                Set<String> eventIds = new HashSet<>();
                for (WaitingList wl : registrations) {
                    String status = wl.getStatus();
                    if (status == null || (!WaitingList.STATUS_WITHDRAWN.equals(status) && !WaitingList.STATUS_CANCELLED.equals(status))) {
                        String eid = wl.getEventId();
                        if (eid != null) eventIds.add(eid);
                    }
                }
                List<NotificationEventAdapter.Item> items = new ArrayList<>();
                if (eventIds.isEmpty()) {
                    notificationEventAdapter.setItems(items);
                    return;
                }
                int[] pending = new int[] { eventIds.size() };
                for (String eventId : eventIds) {
                    eventDB.getEvent(eventId,
                        event -> {
                            String name = event != null && event.getName() != null ? event.getName() : "Event";
                            boolean enabled = notificationPrefs.getBoolean("event_notifications_" + eventId, false);
                            synchronized (items) {
                                items.add(new NotificationEventAdapter.Item(eventId, name, enabled));
                            }
                            synchronized (pending) {
                                if (--pending[0] == 0) {
                                    runOnUiThread(() -> {
                                        synchronized (items) {
                                            Collections.sort(items, (a, b) -> a.eventName.compareToIgnoreCase(b.eventName));
                                        }
                                        notificationEventAdapter.setItems(items);
                                    });
                                }
                            }
                        },
                        e -> {
                            synchronized (pending) {
                                if (--pending[0] == 0) {
                                    runOnUiThread(() -> notificationEventAdapter.setItems(items));
                                }
                            }
                        });
                }
            },
            e -> notificationEventAdapter.setItems(new ArrayList<>()));
    }

    private void switchToEditMode() {
        editName.setText(currentEntrant.getName());
        editEmail.setText(currentEntrant.getEmail());
        editPhone.setText(currentEntrant.getPhone() != null && !currentEntrant.getPhone().isEmpty() ? currentEntrant.getPhone() : "");
        profileViewPanel.setVisibility(View.GONE);
        profileEditPanel.setVisibility(View.VISIBLE);
    }

    private void switchToViewMode() {
        profileEditPanel.setVisibility(View.GONE);
        profileViewPanel.setVisibility(View.VISIBLE);
    }

    private void saveAndSwitchToViewMode() {
        String name = editName.getText().toString().trim();
        String email = editEmail.getText().toString().trim();
        String phone = editPhone.getText().toString().trim();
        if (name.isEmpty() || email.isEmpty()) {
            Toast.makeText(this, "Name and Email are required", Toast.LENGTH_SHORT).show();
            return;
        }
        Entrant updatedEntrant = new Entrant(entrantDB.getEntrant().getDeviceId(), name, email, phone, currentEntrant.getProfilePictureUrl());
        if (controller.saveEntrant(updatedEntrant)) {
            currentEntrant = updatedEntrant;
            displayProfile();
            switchToViewMode();
            Toast.makeText(this, "Profile updated", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "Failed to update profile", Toast.LENGTH_SHORT).show();
        }
    }

    private void displayProfile() {
        tvName.setText(currentEntrant.getName());
        tvEmail.setText(currentEntrant.getEmail());
        tvPhone.setText(currentEntrant.getPhone() != null && !currentEntrant.getPhone().isEmpty() ? currentEntrant.getPhone() : "Not provided");
        updateProfileImageUI(profileImage, profileInitials);
    }

    private void updateProfileImageUI(ImageView imageView, TextView initialsView) {
        String url = currentEntrant.getProfilePictureUrl();
        if (url != null && !url.isEmpty()) {
            imageView.setVisibility(View.VISIBLE);
            initialsView.setVisibility(View.GONE);
            imageView.setImageDrawable(null);
            imageView.setColorFilter(null);
            Picasso.get().load(url).into(imageView);
        } else {
            imageView.setVisibility(View.VISIBLE);
            initialsView.setVisibility(View.GONE);
            imageView.setImageResource(R.drawable.ic_avatar_person);
            imageView.setColorFilter(ContextCompat.getColor(this, android.R.color.white));
        }
    }

    private void performAccountDeletion() {
        String deviceId = currentEntrant.getDeviceId();
        waitingListDB.removeUserFromAllWaitlists(deviceId,
            v -> profileDB.deleteProfile(deviceId,
                v2 -> {
                    entrantDB.clearEntrant();
                    Intent intent = new Intent(this, WelcomeActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                    finish();
                },
                e -> Toast.makeText(this, "Failed to delete profile", Toast.LENGTH_SHORT).show()),
            e -> Toast.makeText(this, "Failed to remove from waitlists", Toast.LENGTH_SHORT).show());
    }

    private void uploadImage(Uri uri) {
        imageDB.uploadProfileImage(uri, url -> {
            currentEntrant.setProfilePictureUrl(url);
            updateProfileImageUI(profileImage, profileInitials);
        }, e -> Toast.makeText(this, "Upload failed", Toast.LENGTH_SHORT).show());
    }

    private void setupBottomNavigation() {
        String mode = notificationPrefs.getString("account_mode", "user");
        FrameLayout navContainer = findViewById(R.id.nav_container);

        // Tint header to match current mode
        View headerBar = findViewById(R.id.header_bar);
        if (headerBar != null) {
            headerBar.setBackgroundColor(ContextCompat.getColor(this,
                    "organizer".equals(mode) ? R.color.organizer_blue : R.color.header_teal));
        }

        if ("organizer".equals(mode)) {
            LayoutInflater.from(this).inflate(R.layout.partial_bottom_nav_organizer, navContainer, true);

            navContainer.findViewById(R.id.nav_create).setOnClickListener(v ->
                    startActivity(new Intent(this, EventCreateActivity.class)));
            navContainer.findViewById(R.id.nav_dashboard).setOnClickListener(v -> {
                startActivity(new Intent(this, OrganizerActivity.class));
                finish();
            });
            navContainer.findViewById(R.id.nav_notifications).setOnClickListener(v ->
                    startActivity(new Intent(this, NotificationsActivity.class)
                            .putExtra("fromOrganizer", true)));
            navContainer.findViewById(R.id.nav_my_events).setOnClickListener(v -> {
                startActivity(new Intent(this, OrganizerActivity.class));
                finish();
            });

            ImageView ivAccount = navContainer.findViewById(R.id.iv_nav_account);
            TextView tvAccount = navContainer.findViewById(R.id.tv_nav_account);
            if (ivAccount != null) ivAccount.setColorFilter(getResources().getColor(R.color.organizer_blue));
            if (tvAccount != null) tvAccount.setTextColor(getResources().getColor(R.color.organizer_blue));
        } else {
            LayoutInflater.from(this).inflate(R.layout.partial_bottom_nav, navContainer, true);

            navContainer.findViewById(R.id.nav_events).setOnClickListener(v -> {
                startActivity(new Intent(this, EventListActivity.class));
                finish();
            });
            navContainer.findViewById(R.id.nav_notifications).setOnClickListener(v ->
                    startActivity(new Intent(this, NotificationsActivity.class)));
            navContainer.findViewById(R.id.nav_my_events).setOnClickListener(v ->
                    startActivity(new Intent(this, EventHistoryActivity.class)));
            navContainer.findViewById(R.id.nav_qr).setOnClickListener(v ->
                    startActivity(new Intent(this, QRScanActivity.class)));

            ImageView ivAccount = navContainer.findViewById(R.id.iv_nav_account);
            TextView tvAccount = navContainer.findViewById(R.id.tv_nav_account);
            if (ivAccount != null) ivAccount.setColorFilter(getResources().getColor(R.color.user_green));
            if (tvAccount != null) tvAccount.setTextColor(getResources().getColor(R.color.user_green));
        }
    }
}
