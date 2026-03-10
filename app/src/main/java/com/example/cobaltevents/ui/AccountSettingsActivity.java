package com.example.cobaltevents.ui;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.example.cobaltevents.R;
import com.example.cobaltevents.controller.EntrantController;
import com.example.cobaltevents.db.EntrantDB;
import com.example.cobaltevents.db.ImageDB;
import com.example.cobaltevents.model.Entrant;
import com.squareup.picasso.Picasso;

public class AccountSettingsActivity extends AppCompatActivity {

    private TextView tvName, tvEmail, tvPhone;
    private ImageView profileImage;
    private TextView profileInitials;
    private Button btnEditInfo, btnDeleteAccount;

    private EntrantController controller;
    private EntrantDB entrantDB;
    private ImageDB imageDB;
    private Entrant currentEntrant;

    private ImageView dialogProfileImage;
    private TextView dialogProfileInitials;
    private String originalProfilePictureUrl;

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
        controller = new EntrantController(entrantDB);
        currentEntrant = entrantDB.getEntrant();

        tvName = findViewById(R.id.tv_name);
        tvEmail = findViewById(R.id.tv_email);
        tvPhone = findViewById(R.id.tv_phone);
        profileImage = findViewById(R.id.profile_image);
        profileInitials = findViewById(R.id.profile_initials);
        btnEditInfo = findViewById(R.id.btn_edit_info);
        btnDeleteAccount = findViewById(R.id.btn_delete_account);

        displayProfile();

        btnEditInfo.setOnClickListener(v -> showEditProfileDialog());
        
        setupBottomNavigation();
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
            Picasso.get().load(url).into(imageView);
        } else {
            imageView.setVisibility(View.GONE);
            initialsView.setVisibility(View.VISIBLE);
            initialsView.setText(currentEntrant.getInitials());
        }
    }

    private void showEditProfileDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_edit_profile, null);
        builder.setView(dialogView);

        EditText editName = dialogView.findViewById(R.id.edit_name);
        EditText editEmail = dialogView.findViewById(R.id.edit_email);
        EditText editPhone = dialogView.findViewById(R.id.edit_phone);
        dialogProfileImage = dialogView.findViewById(R.id.edit_profile_image);
        dialogProfileInitials = dialogView.findViewById(R.id.edit_profile_initials);
        Button btnUpload = dialogView.findViewById(R.id.btn_upload_picture);
        Button btnCancel = dialogView.findViewById(R.id.btn_cancel);
        Button btnSave = dialogView.findViewById(R.id.btn_save_changes);

        editName.setText(currentEntrant.getName());
        editEmail.setText(currentEntrant.getEmail());
        editPhone.setText(currentEntrant.getPhone());
        updateProfileImageUI(dialogProfileImage, dialogProfileInitials);
        
        // Store original state for cancel restoration
        originalProfilePictureUrl = currentEntrant.getProfilePictureUrl();

        AlertDialog dialog = builder.create();

        btnUpload.setOnClickListener(v -> getContent.launch("image/*"));
        
        btnCancel.setOnClickListener(v -> {
            // Restore previous profile picture URL if it was changed during upload
            currentEntrant.setProfilePictureUrl(originalProfilePictureUrl);
            displayProfile();
            dialog.dismiss();
        });
        
        btnSave.setOnClickListener(v -> {
            String name = editName.getText().toString().trim();
            String email = editEmail.getText().toString().trim();
            String phone = editPhone.getText().toString().trim();

            if (name.isEmpty() || email.isEmpty()) {
                Toast.makeText(this, "Name and Email are required", Toast.LENGTH_SHORT).show();
                return;
            }

            // Confirm update
            new AlertDialog.Builder(this)
                .setTitle("Update Profile")
                .setMessage("Are you sure you want to save these changes?")
                .setPositiveButton("Yes", (d, which) -> {
                    currentEntrant.setDeviceId(entrantDB.getEntrant().getDeviceId());
                    Entrant updatedEntrant = new Entrant(currentEntrant.getDeviceId(), name, email, phone, currentEntrant.getProfilePictureUrl());
                    if (controller.saveEntrant(updatedEntrant)) {
                        currentEntrant = updatedEntrant;
                        displayProfile();
                        dialog.dismiss();
                        Toast.makeText(this, "Profile updated", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(this, "Failed to update profile", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("No", null)
                .show();
        });

        dialog.show();
    }

    private void uploadImage(Uri uri) {
        imageDB.uploadProfileImage(uri, url -> {
            currentEntrant.setProfilePictureUrl(url);
            if (dialogProfileImage != null) {
                updateProfileImageUI(dialogProfileImage, dialogProfileInitials);
            }
            updateProfileImageUI(profileImage, profileInitials);
        }, e -> Toast.makeText(this, "Upload failed", Toast.LENGTH_SHORT).show());
    }

    private void setupBottomNavigation() {
        findViewById(R.id.nav_events).setOnClickListener(v -> {
            startActivity(new Intent(this, EventListActivity.class));
            finish();
        });
        
        findViewById(R.id.nav_notifications).setOnClickListener(v -> {
            // Navigate to notifications
        });

        findViewById(R.id.nav_my_events).setOnClickListener(v -> {
            // Navigate to my events
        });

        findViewById(R.id.nav_qr).setOnClickListener(v -> {
            startActivity(new Intent(this, QRScanActivity.class));
        });

        // Set active state for account
        ImageView ivAccount = findViewById(R.id.iv_nav_account);
        TextView tvAccount = findViewById(R.id.tv_nav_account);
        ivAccount.setColorFilter(getResources().getColor(R.color.user_green));
        tvAccount.setTextColor(getResources().getColor(R.color.user_green));
    }
}
