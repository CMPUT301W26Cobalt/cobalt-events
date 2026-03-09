package com.example.cobaltevents.ui;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import androidx.appcompat.app.AppCompatActivity;
import com.example.cobaltevents.R;

public class WelcomeActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_welcome);

        Button btnContinueUser = findViewById(R.id.btn_continue_user);
        Button btnContinueOrganizer = findViewById(R.id.btn_continue_organizer);

        btnContinueUser.setOnClickListener(v -> {
            // Navigate to EntrantActivity (which currently handles entrant profile/login)
            Intent intent = new Intent(WelcomeActivity.this, EntrantActivity.class);
            startActivity(intent);
        });

        btnContinueOrganizer.setOnClickListener(v -> {
            // For now, this can be a placeholder or go to OrganizerActivity if it exists
            Intent intent = new Intent(WelcomeActivity.this, OrganizerActivity.class);
            startActivity(intent);
        });
    }
}
