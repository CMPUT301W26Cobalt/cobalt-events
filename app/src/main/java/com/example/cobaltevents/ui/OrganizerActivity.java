package com.example.cobaltevents.ui;

import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.cobaltevents.R;
import com.example.cobaltevents.db.EventDB;
import com.example.cobaltevents.model.Event;
import com.example.cobaltevents.ui.adapter.OrganizerEventAdapter;

import java.util.ArrayList;
import java.util.List;

public class OrganizerActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private ProgressBar progressBar;
    private TextView tvEmpty;
    private OrganizerEventAdapter adapter;
    private EventDB eventDB;
    private String deviceId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_organizer);

        deviceId = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);
        eventDB = new EventDB();

        recyclerView = findViewById(R.id.recycler_organizer_events);
        progressBar = findViewById(R.id.progress_bar);
        tvEmpty = findViewById(R.id.tv_empty);

        adapter = new OrganizerEventAdapter(new ArrayList<>());
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);

        loadOrganizerEvents();
        setupBottomNavigation();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadOrganizerEvents();
    }

    private void loadOrganizerEvents() {
        progressBar.setVisibility(View.VISIBLE);
        tvEmpty.setVisibility(View.GONE);

        eventDB.getEventsByOrganizer(deviceId,
                events -> {
                    progressBar.setVisibility(View.GONE);
                    List<Event> list = events != null ? events : new ArrayList<>();
                    adapter.updateEvents(list);
                    tvEmpty.setVisibility(list.isEmpty() ? View.VISIBLE : View.GONE);
                },
                e -> {
                    progressBar.setVisibility(View.GONE);
                    Toast.makeText(this, "Failed to load events: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    private void openManageEvent(Event event) {
        Intent intent = new Intent(this, EventManageActivity.class);
        intent.putExtra("eventId", event.getEventId());
        startActivity(intent);
    }

    private void setupBottomNavigation() {
        setDashboardTabActive();

        findViewById(R.id.nav_create).setOnClickListener(v ->
                startActivity(new Intent(this, EventCreateActivity.class)));

        findViewById(R.id.nav_my_events).setOnClickListener(v -> {
            // already showing organizer's events on the dashboard
        });

        findViewById(R.id.nav_notifications).setOnClickListener(v ->
                startActivity(new Intent(this, NotificationsActivity.class)
                        .putExtra("fromOrganizer", true)));

        findViewById(R.id.nav_account).setOnClickListener(v ->
                startActivity(new Intent(this, AccountSettingsActivity.class)));
    }

    private void setDashboardTabActive() {
        int active = ContextCompat.getColor(this, R.color.organizer_blue);
        int inactive = ContextCompat.getColor(this, R.color.grey_nav_inactive);

        tintNavItem(R.id.iv_nav_notifications, R.id.tv_nav_notifications, inactive);
        tintNavItem(R.id.iv_nav_my_events, R.id.tv_nav_my_events, inactive);
        tintNavItem(R.id.iv_nav_account, R.id.tv_nav_account, inactive);
        tintNavItem(R.id.iv_nav_dashboard, R.id.tv_nav_dashboard, active);
    }

    private void tintNavItem(int iconId, int textId, int color) {
        ImageView icon = findViewById(iconId);
        TextView text = findViewById(textId);
        if (icon != null) icon.setColorFilter(color);
        if (text != null) text.setTextColor(color);
    }
}
