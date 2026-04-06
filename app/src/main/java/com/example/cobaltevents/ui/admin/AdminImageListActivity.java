package com.example.cobaltevents.ui.admin;

import android.os.Bundle;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatActivity;
import android.widget.TextView;

import com.example.cobaltevents.R;

/**
 * Placeholder list shell; primary admin image browsing lives on {@link AdminActivity} (US 03.06.01).
 */
public class AdminImageListActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin_list);

        TextView title = findViewById(R.id.adminListTitle);
        title.setText("Browse & Manage Images");
    }
}