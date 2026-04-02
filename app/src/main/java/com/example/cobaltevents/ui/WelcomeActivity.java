package com.example.cobaltevents.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;

import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

import com.example.cobaltevents.R;
import com.example.cobaltevents.controller.AdminController;
import com.example.cobaltevents.db.EntrantDB;
import com.example.cobaltevents.model.Entrant;
import com.example.cobaltevents.ui.admin.AdminActivity;
import com.example.cobaltevents.ui.admin.AdminConfig;
import android.provider.Settings;

/**
 * WelcomeActivity — the first screen shown when the app launches.
 *
 * Always shows the welcome screen with User and Organizer options.
 * If the device ID matches the admin ID, an Admin card is also shown.
 */
public class WelcomeActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_welcome);

        // Get this device's unique Android ID
        String deviceId = Settings.Secure.getString(
                getContentResolver(), Settings.Secure.ANDROID_ID);

        // Log device ID for debugging
        android.util.Log.d("ADMIN_DEVICE_ID", "Device ID: " + deviceId);

        // ── Admin card — only show if device ID matches ────────────────────────
        CardView adminCard = findViewById(R.id.adminCard);
        Button btnAdmin    = findViewById(R.id.btn_continue_admin);

        if (AdminConfig.ADMIN_DEVICE_ID.equals(deviceId)) {
            adminCard.setVisibility(View.VISIBLE);
            // Pre-fetch admin data in background so dashboard loads instantly
            AdminController adminController = new AdminController();
            adminController.getAllEvents(e -> {}, err -> {});
            adminController.getAllProfiles(p -> {}, err -> {});
            btnAdmin.setOnClickListener(v ->
                    startActivity(new Intent(this, AdminActivity.class)));
        }

        // ── User button ───────────────────────────────────────────────────────
        Button btnContinueUser = findViewById(R.id.btn_continue_user);
        btnContinueUser.setOnClickListener(v ->
                startActivity(new Intent(this, EntrantActivity.class)));

        // ── Organizer button ──────────────────────────────────────────────────
        Button btnContinueOrganizer = findViewById(R.id.btn_continue_organizer);
        btnContinueOrganizer.setOnClickListener(v -> {
            Entrant entrant = new EntrantDB(this).getEntrant();
            if (!entrant.isValid()) {
                Intent i = new Intent(this, EntrantActivity.class);
                i.putExtra(EntrantActivity.EXTRA_LAUNCH_ORGANIZER_AFTER_SIGNUP, true);
                startActivity(i);
            } else {
                startActivity(new Intent(this, OrganizerActivity.class));
            }
        });
    }
}