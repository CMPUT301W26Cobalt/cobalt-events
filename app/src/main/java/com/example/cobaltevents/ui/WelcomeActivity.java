package com.example.cobaltevents.ui;

import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Button;

import androidx.appcompat.app.AppCompatActivity;

import com.example.cobaltevents.R;
import com.example.cobaltevents.controller.AdminController;
import com.example.cobaltevents.ui.admin.AdminActivity;

/**
 * WelcomeActivity — the first screen shown when the app launches.
 *
 * Admin detection:
 *   If the device's Android ID matches ADMIN_DEVICE_ID, the user is
 *   redirected straight to AdminActivity and never sees the welcome screen.
 *   The cache is also cleared on every admin open so the dashboard always
 *   shows the latest data from Firestore.
 *
 * Normal user flow:
 *   Two buttons let the user continue as an Entrant or Organizer.
 */
public class WelcomeActivity extends AppCompatActivity {

    // ── Admin device ID ───────────────────────────────────────────────────────
    // This is the Android ID of the admin's device.
    // To find your device ID: run the app, check Logcat for "ADMIN_DEVICE_ID" tag.
    private static final String ADMIN_DEVICE_ID = "4f8e9c86d5ae1fde";
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_welcome);

        // Get this device's unique Android ID
        String deviceId = Settings.Secure.getString(
                getContentResolver(), Settings.Secure.ANDROID_ID);

        // Log device ID for debugging (remove before final submission)
        android.util.Log.d("ADMIN_DEVICE_ID", "Device ID: " + deviceId);

        // ── Admin check ───────────────────────────────────────────────────────
        if (ADMIN_DEVICE_ID.equals(deviceId)) {
            // Clear the static cache so admin always sees fresh Firestore data
            // This ensures any events/profiles created since last session show up
            AdminController.invalidateAll();

            // Skip welcome screen and go straight to admin dashboard
            startActivity(new Intent(this, AdminActivity.class));
            finish(); // Prevent back button returning to welcome screen
            return;
        }

        // ── Normal user flow ──────────────────────────────────────────────────
        Button btnContinueUser      = findViewById(R.id.btn_continue_user);
        Button btnContinueOrganizer = findViewById(R.id.btn_continue_organizer);

        btnContinueUser.setOnClickListener(v ->
                startActivity(new Intent(this, EntrantActivity.class)));

        btnContinueOrganizer.setOnClickListener(v ->
                startActivity(new Intent(this, OrganizerActivity.class)));
    }
}