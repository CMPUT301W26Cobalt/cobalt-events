package com.example.cobaltevents.ui;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;

import androidx.appcompat.app.AppCompatActivity;

import com.example.cobaltevents.R;
import com.example.cobaltevents.ui.admin.AdminActivity;
import android.provider.Settings;

/**
 * Welcome screen shown to new/returning users.
 *
 * Admin check: if the device ID matches the hardcoded admin ID,
 * the user is redirected straight to AdminActivity and never sees
 * the welcome screen.
 */
public class WelcomeActivity extends AppCompatActivity {

    // ── Replace this with your actual device ID ──────────────────────────────
    // To find your device ID: run the app once, check Logcat for
    // "ADMIN_DEVICE_ID" tag, or go to Settings > About > ANDROID_ID
    private static final String ADMIN_DEVICE_ID = "2a223f0105de68c0";
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_welcome);

        // Get this device's unique ID
        String deviceId = Settings.Secure.getString(
                getContentResolver(), Settings.Secure.ANDROID_ID);

        // If this is the admin device, skip welcome and go straight to admin
        if (ADMIN_DEVICE_ID.equals(deviceId)) {
            startActivity(new Intent(this, AdminActivity.class));
            finish(); // prevent back button returning to welcome screen
            return;
        }

        // Normal user flow
        Button btnContinueUser = findViewById(R.id.btn_continue_user);
        Button btnContinueOrganizer = findViewById(R.id.btn_continue_organizer);

        btnContinueUser.setOnClickListener(v -> {
            startActivity(new Intent(this, EntrantActivity.class));
        });

        btnContinueOrganizer.setOnClickListener(v -> {
            startActivity(new Intent(this, OrganizerActivity.class));
        });
    }
}