package com.example.cobaltevents.ui.admin;

import android.os.Bundle;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.cobaltevents.R;

/**
 * Placeholder list shell; primary admin profile browsing lives on {@link AdminActivity} (US 03.05.01).
 */
public class AdminProfileListActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin_list);

        TextView title = findViewById(R.id.adminListTitle);
        title.setText("Browse & Manage Profiles");
    }
}