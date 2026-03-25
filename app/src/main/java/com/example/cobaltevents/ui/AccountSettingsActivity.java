package com.example.cobaltevents.ui;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
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
import androidx.core.widget.ImageViewCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.cobaltevents.R;
import com.example.cobaltevents.controller.EntrantController;
import com.example.cobaltevents.db.CommentDB;
import com.example.cobaltevents.db.EntrantDB;
import com.example.cobaltevents.db.EventDB;
import com.example.cobaltevents.db.ImageDB;
import com.example.cobaltevents.db.ProfileDB;
import com.example.cobaltevents.db.WaitingListDB;
import com.example.cobaltevents.model.Entrant;
import com.example.cobaltevents.model.Event;
import com.example.cobaltevents.model.WaitingList;
import com.example.cobaltevents.util.NetworkConnectivity;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.material.button.MaterialButton;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class AccountSettingsActivity extends AppCompatActivity {

    /**
     * Persisted account mode: true = user (entrant) flow, false = organizer flow.
     * Used with {@link NotificationsActivity#EXTRA_FROM_ORGANIZER} so the UI matches how the user arrived.
     */
    private static final String PREF_ACCOUNT_MODE_USER = "account_mode_user";

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
    private EventDB eventDB;
    private CommentDB commentDB;
    private Entrant currentEntrant;
    private SharedPreferences notificationPrefs;

    /** Matches {@link NotificationsActivity#EXTRA_FROM_ORGANIZER} — drives accent colors on this screen. */
    private boolean fromOrganizerFlow;
    private View accountHeaderBar;
    private ImageView ivNotificationSettingsCardBell;

    private View accountModeRowUser;
    private View accountModeRowOrganizer;
    private View accountModeIconWellUser;
    private View accountModeIconWellOrganizer;
    private ImageView ivAccountModeUser;
    private ImageView ivAccountModeOrganizer;
    private View accountModeRadioUser;
    private View accountModeRadioOrganizer;
    private TextView tvAccountModeUserTitle;
    private TextView tvAccountModeOrganizerTitle;
    private TextView tvAccountModeUserSubtitle;
    private TextView tvAccountModeOrganizerSubtitle;

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
        fromOrganizerFlow = getIntent().getBooleanExtra(NotificationsActivity.EXTRA_FROM_ORGANIZER, false);

        entrantDB = new EntrantDB(this);
        imageDB = new ImageDB();
        profileDB = new ProfileDB();
        waitingListDB = new WaitingListDB();
        eventDB = new EventDB();
        commentDB = new CommentDB();
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
        accountHeaderBar = findViewById(R.id.account_header_bar);
        ivNotificationSettingsCardBell = findViewById(R.id.iv_notification_settings_card_bell);
        MaterialButton btnEditInfo = findViewById(R.id.btn_edit_info);
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

        applyAccountScreenTheme();

        setupAccountModeSection();

        setupBottomNavigation();
    }

    /**
     * User / Organizer mode: updates UI, persists choice, and switches root flow when the user picks the other mode.
     */
    private void setupAccountModeSection() {
        accountModeRowUser = findViewById(R.id.account_mode_row_user);
        accountModeRowOrganizer = findViewById(R.id.account_mode_row_organizer);
        accountModeIconWellUser = findViewById(R.id.account_mode_icon_well_user);
        accountModeIconWellOrganizer = findViewById(R.id.account_mode_icon_well_organizer);
        ivAccountModeUser = findViewById(R.id.iv_account_mode_user);
        ivAccountModeOrganizer = findViewById(R.id.iv_account_mode_organizer);
        accountModeRadioUser = findViewById(R.id.account_mode_radio_user);
        accountModeRadioOrganizer = findViewById(R.id.account_mode_radio_organizer);
        tvAccountModeUserTitle = findViewById(R.id.tv_account_mode_user_title);
        tvAccountModeOrganizerTitle = findViewById(R.id.tv_account_mode_organizer_title);
        tvAccountModeUserSubtitle = findViewById(R.id.tv_account_mode_user_subtitle);
        tvAccountModeOrganizerSubtitle = findViewById(R.id.tv_account_mode_organizer_subtitle);

        accountModeRowUser.setContentDescription(getString(R.string.account_mode_cd_user_row));
        accountModeRowOrganizer.setContentDescription(getString(R.string.account_mode_cd_organizer_row));

        accountModeRowUser.setOnClickListener(v -> onAccountModeUserChosen());
        accountModeRowOrganizer.setOnClickListener(v -> onAccountModeOrganizerChosen());

        boolean userMode = !fromOrganizerFlow;
        notificationPrefs.edit().putBoolean(PREF_ACCOUNT_MODE_USER, userMode).apply();
        applyAccountModeUi(userMode);
    }

    private void onAccountModeUserChosen() {
        applyAccountModeUi(true);
        notificationPrefs.edit().putBoolean(PREF_ACCOUNT_MODE_USER, true).apply();
        if (fromOrganizerFlow) {
            Intent intent = new Intent(this, EventListActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        }
    }

    private void onAccountModeOrganizerChosen() {
        applyAccountModeUi(false);
        notificationPrefs.edit().putBoolean(PREF_ACCOUNT_MODE_USER, false).apply();
        if (!fromOrganizerFlow) {
            Intent intent = new Intent(this, OrganizerActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        }
    }

    /** Accent + header: user flow (green / teal header) vs organizer flow (organizer blue). */
    private void applyAccountScreenTheme() {
        int accent = ContextCompat.getColor(this, fromOrganizerFlow ? R.color.organizer_blue : R.color.user_green);
        int headerColor = ContextCompat.getColor(this, fromOrganizerFlow ? R.color.organizer_blue : R.color.header_teal);
        if (accountHeaderBar != null) {
            accountHeaderBar.setBackgroundColor(headerColor);
        }
        if (profileImage != null) {
            profileImage.setStrokeColor(solidColorStateList(accent));
        }
        MaterialButton btnEdit = findViewById(R.id.btn_edit_info);
        if (btnEdit != null) {
            btnEdit.setBackgroundTintList(solidColorStateList(accent));
        }
        if (btnSaveChanges != null) {
            btnSaveChanges.setBackgroundResource(fromOrganizerFlow
                    ? R.drawable.bg_button_primary_account_solid_organizer
                    : R.drawable.bg_button_primary_account_solid);
        }
        TextView changePic = findViewById(R.id.btn_change_picture);
        if (changePic != null) {
            changePic.setTextColor(accent);
        }
        // Notification bell icon in the header was removed from the layout.
    }

    private static ColorStateList solidColorStateList(int color) {
        return new ColorStateList(new int[][]{new int[0]}, new int[]{color});
    }

    private void applyAccountModeUi(boolean userSelected) {
        int titleDefault = ContextCompat.getColor(this, R.color.account_mode_title_default);
        int subtitleMuted = ContextCompat.getColor(this, R.color.account_mode_subtitle);
        int green = ContextCompat.getColor(this, R.color.account_mode_green);
        int greenSubtitle = ContextCompat.getColor(this, R.color.account_mode_accent_subtitle_green);
        int blue = ContextCompat.getColor(this, R.color.account_mode_blue);
        int blueSubtitle = ContextCompat.getColor(this, R.color.account_mode_accent_subtitle_blue);

        if (userSelected) {
            accountModeRowUser.setBackgroundResource(R.drawable.bg_account_mode_row_outline_green);
            accountModeRowOrganizer.setBackgroundResource(R.drawable.bg_account_mode_row_neutral);
            tvAccountModeUserTitle.setTextColor(green);
            tvAccountModeUserSubtitle.setTextColor(greenSubtitle);
            tvAccountModeOrganizerTitle.setTextColor(titleDefault);
            tvAccountModeOrganizerSubtitle.setTextColor(subtitleMuted);

            accountModeIconWellUser.setBackgroundResource(R.drawable.bg_account_mode_icon_well_solid_green);
            ivAccountModeUser.setImageResource(R.drawable.ic_account_mode_user_white);

            accountModeIconWellOrganizer.setBackgroundResource(R.drawable.bg_account_mode_icon_well_user);
            ivAccountModeOrganizer.setImageResource(R.drawable.ic_account_mode_organizer_neutral);

            accountModeRadioUser.setBackgroundResource(R.drawable.account_mode_radio_indicator_green);
            accountModeRadioUser.setVisibility(View.VISIBLE);
            accountModeRadioOrganizer.setVisibility(View.GONE);
        } else {
            accountModeRowUser.setBackgroundResource(R.drawable.bg_account_mode_row_neutral);
            accountModeRowOrganizer.setBackgroundResource(R.drawable.bg_account_mode_row_outline_blue);
            tvAccountModeUserTitle.setTextColor(titleDefault);
            tvAccountModeUserSubtitle.setTextColor(subtitleMuted);
            tvAccountModeOrganizerTitle.setTextColor(blue);
            tvAccountModeOrganizerSubtitle.setTextColor(blueSubtitle);

            accountModeIconWellUser.setBackgroundResource(R.drawable.bg_account_mode_icon_well_user);
            ivAccountModeUser.setImageResource(R.drawable.ic_account_mode_user);

            accountModeIconWellOrganizer.setBackgroundResource(R.drawable.bg_account_mode_icon_well_solid_blue);
            ivAccountModeOrganizer.setImageResource(R.drawable.ic_account_mode_organizer_white);

            accountModeRadioUser.setVisibility(View.GONE);
            accountModeRadioOrganizer.setVisibility(View.VISIBLE);
            accountModeRadioOrganizer.setBackgroundResource(R.drawable.account_mode_radio_indicator_blue);
        }
        // Notification Settings card bell: teal in user flow, blue in organizer flow.
        if (ivNotificationSettingsCardBell != null) {
            int bellColor = userSelected
                    ? ContextCompat.getColor(this, R.color.header_teal)
                    : ContextCompat.getColor(this, R.color.organizer_blue);
            ImageViewCompat.setImageTintList(ivNotificationSettingsCardBell, solidColorStateList(bellColor));
        }
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
        clearEditErrors();
        profileViewPanel.setVisibility(View.GONE);
        profileEditPanel.setVisibility(View.VISIBLE);
    }

    private void switchToViewMode() {
        profileEditPanel.setVisibility(View.GONE);
        profileViewPanel.setVisibility(View.VISIBLE);
    }

    private void saveAndSwitchToViewMode() {
        clearEditErrors();

        String name = editName.getText() == null ? "" : editName.getText().toString().trim();
        String email = editEmail.getText() == null ? "" : editEmail.getText().toString().trim();
        String phone = editPhone.getText() == null ? "" : editPhone.getText().toString().trim();

        boolean hasError = false;
        String nameError = controller.validateName(name);
        if (nameError != null) {
            editName.setError(nameError);
            if (!hasError) editName.requestFocus();
            hasError = true;
        }
        String emailError = controller.validateEmail(email);
        if (emailError != null) {
            editEmail.setError(emailError);
            if (!hasError) editEmail.requestFocus();
            hasError = true;
        }
        String phoneError = controller.validatePhone(phone);
        if (phoneError != null) {
            editPhone.setError(phoneError);
            if (!hasError) editPhone.requestFocus();
            hasError = true;
        }
        if (hasError) {
            return;
        }

        if (!NetworkConnectivity.hasValidatedInternet(this)) {
            Toast.makeText(this, R.string.account_save_no_internet, Toast.LENGTH_SHORT).show();
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

    private void clearEditErrors() {
        editName.setError(null);
        editEmail.setError(null);
        editPhone.setError(null);
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
        if (!NetworkConnectivity.hasValidatedInternet(this)) {
            Toast.makeText(this, R.string.account_save_no_internet, Toast.LENGTH_LONG).show();
            return;
        }
        eventDB.getEventsForOrganizerParticipation(deviceId,
                events -> {
                    List<Event> list = events != null ? events : new ArrayList<>();
                    runAccountDeletionOrganizerPass(list, 0, deviceId, () ->
                            waitingListDB.removeUserFromAllWaitlists(deviceId,
                                    v -> profileDB.deleteProfile(deviceId,
                                            v2 -> runOnUiThread(() -> {
                                                entrantDB.clearEntrant();
                                                Intent intent = new Intent(this, WelcomeActivity.class);
                                                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                                                startActivity(intent);
                                                finish();
                                            }),
                                            e -> runOnUiThread(() ->
                                                    Toast.makeText(this, "Failed to delete profile", Toast.LENGTH_SHORT).show())),
                                    e -> runOnUiThread(() ->
                                            Toast.makeText(this, R.string.unable_to_delete_account, Toast.LENGTH_LONG).show())));
                },
                e -> runOnUiThread(() ->
                        Toast.makeText(this, R.string.unable_to_delete_account, Toast.LENGTH_LONG).show()));
    }

    /**
     * For each event the user organizes: delete event + waitlist + comments if sole organizer; otherwise remove
     * their device id from {@code organizers}.
     */
    private void runAccountDeletionOrganizerPass(List<Event> events, int index, String deviceId, Runnable afterAll) {
        if (index >= events.size()) {
            afterAll.run();
            return;
        }
        Event ev = events.get(index);
        if (ev == null || ev.getEventId() == null || !ev.isDeviceAnOrganizer(deviceId)) {
            runAccountDeletionOrganizerPass(events, index + 1, deviceId, afterAll);
            return;
        }
        if (ev.isSoleOrganizer(deviceId)) {
            deleteEventCascadeForAccountDeletion(ev.getEventId(),
                    () -> runAccountDeletionOrganizerPass(events, index + 1, deviceId, afterAll),
                    err -> runOnUiThread(() ->
                            Toast.makeText(this, R.string.unable_to_delete_account, Toast.LENGTH_LONG).show()));
        } else {
            removeSelfAsCoOrganizer(ev, deviceId,
                    () -> runAccountDeletionOrganizerPass(events, index + 1, deviceId, afterAll),
                    err -> runOnUiThread(() ->
                            Toast.makeText(this, R.string.unable_to_delete_account, Toast.LENGTH_LONG).show()));
        }
    }

    private void deleteEventCascadeForAccountDeletion(String eventId, Runnable onSuccess, OnFailureListener onFailure) {
        commentDB.deleteAllCommentsAndRepliesForEvent(eventId,
                unused -> waitingListDB.deleteAllWaitlistDataForEvent(eventId,
                        unused2 -> eventDB.deleteEvent(eventId,
                                unused3 -> onSuccess.run(),
                                onFailure),
                        onFailure),
                onFailure);
    }

    private void removeSelfAsCoOrganizer(Event ev, String deviceId, Runnable onSuccess, OnFailureListener onFailure) {
        List<String> remaining = new ArrayList<>(ev.getMergedOrganizerDeviceIds());
        remaining.removeIf(id -> deviceId != null && deviceId.equals(id));
        if (remaining.isEmpty()) {
            deleteEventCascadeForAccountDeletion(ev.getEventId(), onSuccess, onFailure);
            return;
        }
        ev.setOrganizers(new ArrayList<>(remaining));
        eventDB.updateEvent(ev, unused -> onSuccess.run(), onFailure);
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
        boolean fromOrganizer = getIntent().getBooleanExtra(NotificationsActivity.EXTRA_FROM_ORGANIZER, false);
        int layoutRes = fromOrganizer ? R.layout.partial_bottom_nav_organizer : R.layout.partial_bottom_nav;
        android.view.LayoutInflater.from(this).inflate(layoutRes, navContainer, true);

        if (fromOrganizer) {
            findViewById(R.id.nav_dashboard).setOnClickListener(v ->
                    startActivity(new Intent(this, OrganizerActivity.class)));
            findViewById(R.id.nav_create).setOnClickListener(v ->
                    startActivity(new Intent(this, EventCreateActivity.class)));
            findViewById(R.id.nav_notifications).setOnClickListener(v ->
                    startActivity(new Intent(this, NotificationsActivity.class)
                            .putExtra(NotificationsActivity.EXTRA_FROM_ORGANIZER, true)));
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
