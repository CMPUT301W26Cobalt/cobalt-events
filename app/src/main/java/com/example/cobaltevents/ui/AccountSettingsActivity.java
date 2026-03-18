package com.example.cobaltevents.ui;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
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
import com.example.cobaltevents.db.ImageDB;
import com.example.cobaltevents.db.ProfileDB;
import com.example.cobaltevents.db.WaitingListDB;
import com.example.cobaltevents.model.Entrant;
import com.example.cobaltevents.model.WaitingList;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class AccountSettingsActivity extends AppCompatActivity {

    private TextView tvName, tvEmail, tvPhone;
    private com.google.android.material.imageview.ShapeableImageView profileImage;
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
    private Entrant currentEntrant;
    private SharedPreferences notificationPrefs;


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
        notificationPrefs = getSharedPreferences("cobalt_prefs", MODE_PRIVATE);
        controller = new EntrantController(entrantDB);
        currentEntrant = entrantDB.getEntrant();

        tvName = findViewById(R.id.tv_name);
        tvEmail = findViewById(R.id.tv_email);
        tvPhone = findViewById(R.id.tv_phone);
        profileImage = findViewById(R.id.profile_image);
        profileViewPanel = findViewById(R.id.profile_view_panel);
        profileEditPanel = findViewById(R.id.profile_edit_panel);
        editName = findViewById(R.id.edit_name);
        editEmail = findViewById(R.id.edit_email);
        editPhone = findViewById(R.id.edit_phone);
        btnSaveChanges = findViewById(R.id.btn_save_changes);
        btnCancelEdit = findViewById(R.id.btn_cancel_edit);
        View btnEditInfo = findViewById(R.id.btn_edit_info);
        View btnDeleteAccount = findViewById(R.id.btn_delete_account);
        View btnChangePicture = findViewById(R.id.btn_change_picture);
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
        btnChangePicture.setOnClickListener(v -> getContent.launch("image/*"));

        androidx.appcompat.widget.SwitchCompat switchGeneral = findViewById(R.id.switch_general);
        androidx.appcompat.widget.SwitchCompat switchEventUpdates = findViewById(R.id.switch_event_updates);
        applySwitchTints(switchGeneral);
        applySwitchTints(switchEventUpdates);
        switchGeneral.setChecked(notificationPrefs.getBoolean("notification_general", true));
        switchEventUpdates.setChecked(notificationPrefs.getBoolean("notification_event_updates", true));
        switchGeneral.setOnCheckedChangeListener((v, isChecked) -> notificationPrefs.edit().putBoolean("notification_general", isChecked).apply());
        switchEventUpdates.setOnCheckedChangeListener((v, isChecked) ->
                notificationPrefs.edit().putBoolean("notification_event_updates", isChecked).apply());

        setupBottomNavigation();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (currentEntrant != null) currentEntrant = entrantDB.getEntrant();
    }

    private void applySwitchTints(androidx.appcompat.widget.SwitchCompat switchCompat) {
        switchCompat.setThumbTintList(ContextCompat.getColorStateList(this, R.color.thumb_white));
        switchCompat.setTrackTintList(ContextCompat.getColorStateList(this, R.color.switch_track_selector));
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
        updateProfileImageUI();
    }

    private void updateProfileImageUI() {
        String url = currentEntrant.getProfilePictureUrl();
        if (url != null && !url.isEmpty()) {
            Glide.with(this)
                    .load(url)
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .circleCrop()
                    .placeholder(R.drawable.ic_default_avatar)
                    .into(profileImage);
        } else {
            profileImage.setImageResource(R.drawable.ic_default_avatar);
            profileImage.setScaleType(android.widget.ImageView.ScaleType.CENTER_CROP);
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
            e -> Toast.makeText(this, "Failed to remove from waitlists: " + e.getMessage(), Toast.LENGTH_SHORT).show());
    }

    private void uploadImage(Uri uri) {
        Toast.makeText(this, "Uploading profile picture…", Toast.LENGTH_SHORT).show();
        final String oldUrl = currentEntrant.getProfilePictureUrl();
        imageDB.uploadProfileImage(uri, url -> {
            currentEntrant.setProfilePictureUrl(url);
            entrantDB.saveEntrant(currentEntrant);
            profileDB.saveProfile(currentEntrant,
                    unused -> {
                        updateProfileImageUI();
                        Toast.makeText(this, "Profile picture updated", Toast.LENGTH_SHORT).show();
                        if (oldUrl != null && !oldUrl.isEmpty()) {
                            new ImageDB().deleteImageByUrl(oldUrl, v -> {}, err -> {});
                        }
                    },
                    err -> {
                        updateProfileImageUI();
                        Toast.makeText(this, "Saved locally; cloud update failed", Toast.LENGTH_LONG).show();
                    });
        }, e -> Toast.makeText(this, "Upload failed", Toast.LENGTH_SHORT).show());
    }

    private void setupBottomNavigation() {
        android.widget.FrameLayout navContainer = findViewById(R.id.nav_container);
        boolean fromOrganizer = getIntent().getBooleanExtra("fromOrganizer", false);
        int layoutRes = fromOrganizer ? R.layout.partial_bottom_nav_organizer : R.layout.partial_bottom_nav;
        android.view.LayoutInflater.from(this).inflate(layoutRes, navContainer, true);

        if (fromOrganizer) {
            findViewById(R.id.nav_dashboard).setOnClickListener(v ->
                    startActivity(new Intent(this, OrganizerActivity.class)));
            findViewById(R.id.nav_create).setOnClickListener(v ->
                    startActivity(new Intent(this, EventCreateActivity.class)));
            findViewById(R.id.nav_my_events).setOnClickListener(v -> {
                startActivity(new Intent(this, OrganizerActivity.class));
                finish();
            });
            findViewById(R.id.nav_notifications).setOnClickListener(v ->
                    startActivity(new Intent(this, NotificationsActivity.class)
                            .putExtra("fromOrganizer", true)));
            ImageView iv = findViewById(R.id.iv_nav_account);
            TextView tv = findViewById(R.id.tv_nav_account);
            if (iv != null) iv.setColorFilter(getResources().getColor(R.color.organizer_blue));
            if (tv != null) tv.setTextColor(getResources().getColor(R.color.organizer_blue));
        } else {
            findViewById(R.id.nav_events).setOnClickListener(v -> {
                startActivity(new Intent(this, EventListActivity.class));
                finish();
            });
            findViewById(R.id.nav_notifications).setOnClickListener(v -> {
                startActivity(new Intent(this, NotificationsActivity.class));
            });
            findViewById(R.id.nav_my_events).setOnClickListener(v -> {
                startActivity(new Intent(this, EventHistoryActivity.class));
                finish();
            });
            findViewById(R.id.nav_qr).setOnClickListener(v -> {
                startActivity(new Intent(this, QRScanActivity.class));
            });
            ImageView ivAccount = findViewById(R.id.iv_nav_account);
            TextView tvAccount = findViewById(R.id.tv_nav_account);
            if (ivAccount != null) ivAccount.setColorFilter(getResources().getColor(R.color.user_green));
            if (tvAccount != null) tvAccount.setTextColor(getResources().getColor(R.color.user_green));
        }
    }
}
