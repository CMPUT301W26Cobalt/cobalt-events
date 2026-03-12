package com.example.cobaltevents.ui;

import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.cobaltevents.R;
import com.example.cobaltevents.controller.EntrantController;
import com.example.cobaltevents.db.EntrantDB;
import com.example.cobaltevents.model.Entrant;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

public class EntrantActivity extends AppCompatActivity {

    private TextInputLayout nameLayout, emailLayout, phoneLayout;
    private TextInputEditText nameInput, emailInput, phoneInput;
    private Button getStartedButton;
    private LinearLayout backButton;

    private EntrantController controller;
    private EntrantDB entrantDB;
    private Entrant currentEntrant;
    private boolean isOrganizer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        android.util.Log.d("ADMIN_DEVICE_ID", Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID));
        entrantDB = new EntrantDB(this);
        controller = new EntrantController(entrantDB);
        currentEntrant = entrantDB.getEntrant();
        isOrganizer = getIntent().getBooleanExtra("isOrganizer", false);

        // If profile is already complete, go straight to the appropriate screen
        if (currentEntrant.isValid()) {
            navigateToMain();
            return;
        }

        setContentView(R.layout.activity_entrant);

        backButton = findViewById(R.id.btn_back);
        nameLayout = findViewById(R.id.nameLayout);
        emailLayout = findViewById(R.id.emailLayout);
        phoneLayout = findViewById(R.id.phoneLayout);
        nameInput = findViewById(R.id.nameInput);
        emailInput = findViewById(R.id.emailInput);
        phoneInput = findViewById(R.id.phoneInput);
        getStartedButton = findViewById(R.id.saveButton);

        loadProfileFields();

        backButton.setOnClickListener(v -> finish());
        getStartedButton.setOnClickListener(v -> saveProfile());
    }

    private void loadProfileFields() {
        nameInput.setText(currentEntrant.getName());
        emailInput.setText(currentEntrant.getEmail());
        phoneInput.setText(currentEntrant.getPhone());
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
            entrant.setOrganizerEnabled(true);
            if (controller.saveEntrant(entrant)) {
                navigateToMain();
            } else {
                Toast.makeText(this, "Failed to save profile", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void navigateToMain() {
        Intent intent = new Intent(this, isOrganizer ? OrganizerActivity.class : EventListActivity.class);
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
