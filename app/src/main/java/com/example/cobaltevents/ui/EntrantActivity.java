package com.example.cobaltevents.ui;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.example.cobaltevents.R;
import com.example.cobaltevents.controller.EntrantController;
import com.example.cobaltevents.db.EntrantDB;
import com.example.cobaltevents.db.ImageDB;
import com.example.cobaltevents.model.Entrant;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.squareup.picasso.Picasso;

public class EntrantActivity extends AppCompatActivity {

    private TextInputLayout nameLayout, emailLayout, phoneLayout;
    private TextInputEditText nameInput, emailInput, phoneInput;
    private ImageView profileImage;
    private TextView avatarText;
    private Button uploadButton, removeButton, saveButton;

    private EntrantController controller;
    private EntrantDB entrantDB;
    private ImageDB imageDB;
    private Entrant currentEntrant;

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

        entrantDB = new EntrantDB(this);
        imageDB = new ImageDB();
        controller = new EntrantController(entrantDB);
        currentEntrant = entrantDB.getEntrant();

        // If profile is already complete, go to EventListActivity immediately
        if (currentEntrant.isValid()) {
            navigateToMain();
            return;
        }

        setContentView(R.layout.activity_entrant);

        nameLayout = findViewById(R.id.nameLayout);
        emailLayout = findViewById(R.id.emailLayout);
        phoneLayout = findViewById(R.id.phoneLayout);
        nameInput = findViewById(R.id.nameInput);
        emailInput = findViewById(R.id.emailInput);
        phoneInput = findViewById(R.id.phoneInput);

        profileImage = findViewById(R.id.profileImage);
        avatarText = findViewById(R.id.avatarText);
        uploadButton = findViewById(R.id.uploadImageButton);
        removeButton = findViewById(R.id.removeImageButton);
        saveButton = findViewById(R.id.saveButton);

        loadProfileFields();

        uploadButton.setOnClickListener(v -> getContent.launch("image/*"));
        removeButton.setOnClickListener(v -> removeImage());
        saveButton.setOnClickListener(v -> saveProfile());
    }

    private void loadProfileFields() {
        nameInput.setText(currentEntrant.getName());
        emailInput.setText(currentEntrant.getEmail());
        phoneInput.setText(currentEntrant.getPhone());
        updateProfileImageUI();
    }

    private void updateProfileImageUI() {
        String url = currentEntrant.getProfilePictureUrl();
        if (url != null && !url.isEmpty()) {
            profileImage.setVisibility(View.VISIBLE);
            avatarText.setVisibility(View.GONE);
            Picasso.get().load(url).into(profileImage);
        } else {
            profileImage.setVisibility(View.GONE);
            avatarText.setVisibility(View.VISIBLE);
            avatarText.setText(currentEntrant.getInitials());
        }
    }

    private void uploadImage(Uri uri) {
        imageDB.uploadProfileImage(uri, url -> {
            currentEntrant.setProfilePictureUrl(url);
            updateProfileImageUI();
        }, e -> Toast.makeText(this, "Upload failed", Toast.LENGTH_SHORT).show());
    }

    private void removeImage() {
        imageDB.deleteImageByUrl(currentEntrant.getProfilePictureUrl(), unused -> {
            currentEntrant.setProfilePictureUrl(null);
            updateProfileImageUI();
        }, e -> Toast.makeText(this, "Remove failed", Toast.LENGTH_SHORT).show());
    }

    private void saveProfile() {
        clearErrors();

        String name = safe(nameInput);
        String email = safe(emailInput);
        String phone = safe(phoneInput);

        boolean hasError = false;

        String nameError = controller.validateName(name);
        if (nameError != null) {
            nameLayout.setError(nameError);
            hasError = true;
        }

        String emailError = controller.validateEmail(email);
        if (emailError != null) {
            emailLayout.setError(emailError);
            hasError = true;
        }

        String phoneError = controller.validatePhone(phone);
        if (phoneError != null) {
            phoneLayout.setError(phoneError);
            hasError = true;
        }

        if (!hasError) {
            Entrant entrant = new Entrant(name, email, phone, currentEntrant.getProfilePictureUrl());
            if (controller.saveEntrant(entrant)) {
                navigateToMain();
            } else {
                Toast.makeText(this, "Failed to save profile", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void navigateToMain() {
        Intent intent = new Intent(this, EventListActivity.class);
        startActivity(intent);
        finish();
    }

    private void clearErrors() {
        nameLayout.setError(null);
        emailLayout.setError(null);
        phoneLayout.setError(null);
    }

    private String safe(TextInputEditText input) {
        return input.getText() == null ? "" : input.getText().toString().trim();
    }
}
