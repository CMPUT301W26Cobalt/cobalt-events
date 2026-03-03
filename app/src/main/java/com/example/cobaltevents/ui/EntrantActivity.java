package com.example.cobaltevents.ui;

import android.os.Bundle;
import android.util.Patterns;

import androidx.appcompat.app.AppCompatActivity;

import com.example.cobaltevents.R;
import com.example.cobaltevents.controller.EntrantController;
import com.example.cobaltevents.db.EntrantDB;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import android.widget.Button;

public class EntrantActivity extends AppCompatActivity {

    private TextInputLayout nameLayout, emailLayout, phoneLayout;
    private TextInputEditText nameInput, emailInput, phoneInput;
    private Button saveButton;

    private EntrantController controller;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_entrant);

        controller = new EntrantController(new EntrantDB(this));

        nameLayout = findViewById(R.id.nameLayout);
        emailLayout = findViewById(R.id.emailLayout);
        phoneLayout = findViewById(R.id.phoneLayout);

        nameInput = findViewById(R.id.nameInput);
        emailInput = findViewById(R.id.emailInput);
        phoneInput = findViewById(R.id.phoneInput);

        saveButton = findViewById(R.id.saveButton);

        saveButton.setOnClickListener(v -> saveProfile());
    }

    private void saveProfile() {

        clearErrors();

        String name = safe(nameInput);
        String email = safe(emailInput);
        String phone = safe(phoneInput);

        boolean hasError = false;

        if (name.isEmpty()) {
            nameLayout.setError("Name is required.");
            hasError = true;
        }

        if (email.isEmpty()) {
            emailLayout.setError("Email is required.");
            hasError = true;
        } else if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            emailLayout.setError("Invalid email format.");
            hasError = true;
        }

        if (!phone.isEmpty() && !phone.matches("^[0-9+()\\-\\s]{7,20}$")) {
            phoneLayout.setError("Invalid phone number.");
            hasError = true;
        }

        if (!hasError) {
            controller.saveEntrant(name, email, phone);
            finish();
        }
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