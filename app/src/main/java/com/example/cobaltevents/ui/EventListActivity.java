package com.example.cobaltevents.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.cobaltevents.R;
import com.example.cobaltevents.controller.EventController;
import com.example.cobaltevents.ui.adapter.EventAdapter;

import java.util.ArrayList;

/**
 * Displays a scrollable list of all available events fetched from Firestore.
 * Tapping an event (or its join button) navigates to EventDetailActivity.
 */
public class EventListActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private ProgressBar progressBar;
    private EventAdapter adapter;
    private String deviceId;
    private EventController eventController;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_event_list);

        deviceId = getIntent().getStringExtra("deviceId");
        eventController = new EventController();

        recyclerView = findViewById(R.id.recycler_events);
        progressBar = findViewById(R.id.progress_bar);

        adapter = new EventAdapter(new ArrayList<>(), event -> {
            Intent intent = new Intent(this, EventDetailActivity.class);
            intent.putExtra("eventId", event.getEventId());
            intent.putExtra("deviceId", deviceId);
            startActivity(intent);
        });

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);

        loadEvents();
        setupBottomNavigation();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadEvents();
    }

    private void loadEvents() {
        progressBar.setVisibility(View.VISIBLE);
        eventController.getAllEvents(events -> {
            progressBar.setVisibility(View.GONE);
            adapter.updateEvents(events);
        }, e -> {
            progressBar.setVisibility(View.GONE);
            Toast.makeText(this, "Failed to load events: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        });
    }

    private void setupBottomNavigation() {
        View navAccount = findViewById(R.id.nav_account);
        if (navAccount != null) {
            navAccount.setOnClickListener(v -> {
                Intent intent = new Intent(this, AccountSettingsActivity.class);
                intent.putExtra("deviceId", deviceId);
                startActivity(intent);
            });
        }

        View navQr = findViewById(R.id.nav_qr);
        if (navQr != null) {
            navQr.setOnClickListener(v -> {
                Intent intent = new Intent(this, QRScanActivity.class);
                intent.putExtra("deviceId", deviceId);
                startActivity(intent);
            });
        }
    }
}
