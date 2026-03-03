package com.example.cobaltevents.ui;

import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.cobaltevents.R;
import com.example.cobaltevents.controller.EntrantController;
import com.example.cobaltevents.model.Entrant;
import com.google.android.material.switchmaterial.SwitchMaterial;

/**
 * Entrant profile screen with notification opt-out toggle.
 * US 01.04.03: The notification switch allows entrants to opt out of notifications.
 */
public class EntrantActivity extends AppCompatActivity {

    private static final String TAG = "EntrantActivity";

    private EntrantController entrantController;
    private String deviceId;
    private TextView tvName;
    private TextView tvEmail;
    private SwitchMaterial switchNotifications;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_entrant);

        entrantController = new EntrantController();
        deviceId = EntrantController.getDeviceId(this);

        tvName = findViewById(R.id.tv_entrant_name);
        tvEmail = findViewById(R.id.tv_entrant_email);
        switchNotifications = findViewById(R.id.switch_notifications);

        // Load entrant profile
        loadEntrant();

        // US 01.04.03: Toggle notification preference
        switchNotifications.setOnCheckedChangeListener((buttonView, isChecked) -> {
            entrantController.setNotificationsEnabled(deviceId, isChecked,
                    unused -> Log.d(TAG, "Notification preference updated: " + isChecked),
                    e -> {
                        Log.e(TAG, "Failed to update notification preference", e);
                        Toast.makeText(this, R.string.error_generic, Toast.LENGTH_SHORT).show();
                    }
            );
        });

        // Edit profile button
        findViewById(R.id.btn_edit_profile).setOnClickListener(v ->
                startActivity(new android.content.Intent(this, EntrantEditActivity.class)));
    }

    private void loadEntrant() {
        entrantController.getEntrant(deviceId, entrant -> {
            if (entrant != null) {
                tvName.setText(entrant.getName());
                tvEmail.setText(entrant.getEmail() != null && !entrant.getEmail().isEmpty()
                        ? entrant.getEmail() : "No email set");
                // Set switch without triggering the listener
                switchNotifications.setOnCheckedChangeListener(null);
                switchNotifications.setChecked(entrant.isNotificationsEnabled());
                // Re-attach listener
                switchNotifications.setOnCheckedChangeListener((buttonView, isChecked) -> {
                    entrantController.setNotificationsEnabled(deviceId, isChecked,
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

    @Override
    protected void onResume() {
        super.onResume();
        loadEntrant();
    }
}
