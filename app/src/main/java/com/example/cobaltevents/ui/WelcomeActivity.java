package com.example.cobaltevents.ui;

import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Button;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.example.cobaltevents.R;
import com.example.cobaltevents.db.ProfileDB;

public class WelcomeActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_welcome);

        Button btnContinueUser = findViewById(R.id.btn_continue_user);
        Button btnContinueOrganizer = findViewById(R.id.btn_continue_organizer);

        btnContinueUser.setOnClickListener(v -> {
            Intent intent = new Intent(WelcomeActivity.this, EntrantActivity.class);
            startActivity(intent);
        });

        btnContinueOrganizer.setOnClickListener(v -> {
            String deviceId = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);
            new ProfileDB().getProfile(deviceId,
                profile -> {
                    if (profile != null && Boolean.FALSE.equals(profile.getOrganizerEnabled())) {
                        Toast.makeText(this, "Organizer access has been revoked for this account", Toast.LENGTH_LONG).show();
                        return;
                    }
                    Intent intent = new Intent(this, EntrantActivity.class);
                    intent.putExtra("isOrganizer", true);
                    startActivity(intent);
                },
                e -> {
                    Intent intent = new Intent(this, EntrantActivity.class);
                    intent.putExtra("isOrganizer", true);
                    startActivity(intent);
                });
        });
    }
}
