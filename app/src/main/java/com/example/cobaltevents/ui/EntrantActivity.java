package com.example.cobaltevents.ui;

import android.os.Bundle;
import android.util.Log;
import android.util.Patterns;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.cobaltevents.R;
import com.example.cobaltevents.controller.EntrantController;
import com.example.cobaltevents.model.Entrant;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

/**
 * Combined entrant profile screen: main's editing form + lx's notification toggle.
 * US 01.04.03: SwitchMaterial for notification opt-out.
 */
public class EntrantActivity extends AppCompatActivity {

    private static final String TAG = "EntrantActivity";

    private TextInputLayout nameLayout, emailLayout, phoneLayout;
    private TextInputEditText nameInput, emailInput, phoneInput;
    private SwitchMaterial switchNotifications;

    private EntrantController controller;
    private String deviceId;
    private Entrant currentEntrant;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_entrant);

        controller = new EntrantController();
        deviceId = EntrantController.getDeviceId(this);

        nameLayout = findViewById(R.id.nameLayout);
        emailLayout = findViewById(R.id.emailLayout);
        phoneLayout = findViewById(R.id.phoneLayout);
        nameInput = findViewById(R.id.nameInput);
        emailInput = findViewById(R.id.emailInput);
        phoneInput = findViewById(R.id.phoneInput);
        switchNotifications = findViewById(R.id.switch_notifications);

        // US 01.04.03: Toggle notification preference
        switchNotifications.setOnCheckedChangeListener((buttonView, isChecked) -> {
            controller.setNotificationsEnabled(deviceId, isChecked,
                    unused -> Log.d(TAG, "Notification preference updated: " + isChecked),
                    e -> {
                        Log.e(TAG, "Failed to update notification preference", e);
                        Toast.makeText(this, R.string.error_generic, Toast.LENGTH_SHORT).show();
                    }
            );
        });

        findViewById(R.id.saveButton).setOnClickListener(v -> saveProfile());

        loadEntrant();
    }

    private void loadEntrant() {
        controller.getEntrant(deviceId, entrant -> {
            if (entrant != null) {
                currentEntrant = entrant;
                nameInput.setText(entrant.getName());
                emailInput.setText(entrant.getEmail());
                if (entrant.getPhone() != null) {
                    phoneInput.setText(entrant.getPhone());
                }
                // Set switch without triggering the listener
                switchNotifications.setOnCheckedChangeListener(null);
                switchNotifications.setChecked(entrant.isNotificationsEnabled());
                switchNotifications.setOnCheckedChangeListener((buttonView, isChecked) -> {
                    controller.setNotificationsEnabled(deviceId, isChecked,
                            unused -> Log.d(TAG, "Notification preference updated: " + isChecked),
                            e -> {
                                Log.e(TAG, "Failed to update notification preference", e);
                                Toast.makeText(this, R.string.error_generic, Toast.LENGTH_SHORT).show();
                            }
                    );
                });
            }
        }, e -> Log.e(TAG, "Failed to load entrant", e));
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
            Entrant entrant = currentEntrant != null ? currentEntrant : new Entrant(deviceId, name, email);
            entrant.setName(name.trim());
            entrant.setEmail(email.trim());
            entrant.setPhone(phone.trim());
            entrant.setDeviceId(deviceId);

            controller.saveEntrant(entrant,
                    unused -> {
                        Toast.makeText(this, "Profile saved!", Toast.LENGTH_SHORT).show();
                        finish();
                    },
                    e -> {
                        Log.e(TAG, "Failed to save profile", e);
                        Toast.makeText(this, R.string.error_generic, Toast.LENGTH_SHORT).show();
                    }
            );
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

    @Override
    protected void onResume() {
        super.onResume();
        loadEntrant();
    }
}
