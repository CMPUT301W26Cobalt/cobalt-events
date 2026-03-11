package com.example.cobaltevents.ui;

import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.cobaltevents.R;
import com.example.cobaltevents.controller.EventController;
import com.example.cobaltevents.db.WaitingListDB;
import com.example.cobaltevents.model.Event;
import com.example.cobaltevents.model.EventHistory;
import com.example.cobaltevents.model.WaitingList;
import com.example.cobaltevents.ui.adapter.EventHistoryAdapter;

import java.util.ArrayList;
import java.util.List;

/**
 * US 01.02.03: Display entrant's event registration history
 */
public class EventHistoryActivity extends AppCompatActivity {
    
    private RecyclerView recyclerView;
    private ProgressBar progressBar;
    private TextView tvEmpty;
    private EventHistoryAdapter adapter;
    
    private String deviceId;
    private WaitingListDB waitingListDB;
    private EventController eventController;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_event_history);
        
        deviceId = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);
        waitingListDB = new WaitingListDB();
        eventController = new EventController();
        
        recyclerView = findViewById(R.id.recycler_history);
        progressBar = findViewById(R.id.progress_bar);
        tvEmpty = findViewById(R.id.tv_empty);
        
        adapter = new EventHistoryAdapter(new ArrayList<>(), this::onEventClick);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);

        setupBottomNavigation();
        loadHistory();
    }

    private void setupBottomNavigation() {
        FrameLayout navContainer = findViewById(R.id.nav_container);
        LayoutInflater.from(this).inflate(R.layout.partial_bottom_nav, navContainer, true);

        navContainer.findViewById(R.id.nav_events).setOnClickListener(v -> {
            startActivity(new Intent(this, EventListActivity.class));
            finish();
        });
        navContainer.findViewById(R.id.nav_my_events).setOnClickListener(v -> {});
        navContainer.findViewById(R.id.nav_qr).setOnClickListener(v ->
                startActivity(new Intent(this, QRScanActivity.class)));
        navContainer.findViewById(R.id.nav_notifications).setOnClickListener(v -> {
            startActivity(new Intent(this, NotificationsActivity.class));
            finish();
        });
        navContainer.findViewById(R.id.nav_account).setOnClickListener(v -> {
            startActivity(new Intent(this, AccountSettingsActivity.class));
            finish();
        });

        ImageView iv = navContainer.findViewById(R.id.iv_nav_my_events);
        TextView tv = navContainer.findViewById(R.id.tv_nav_my_events);
        if (iv != null) iv.setColorFilter(getResources().getColor(R.color.user_green));
        if (tv != null) tv.setTextColor(getResources().getColor(R.color.user_green));
    }
    
    private void loadHistory() {
        progressBar.setVisibility(View.VISIBLE);
        tvEmpty.setVisibility(View.GONE);
        
        waitingListDB.getEntrantHistory(deviceId, registrations -> {
            if (registrations.isEmpty()) {
                progressBar.setVisibility(View.GONE);
                tvEmpty.setVisibility(View.VISIBLE);
                adapter.updateHistory(new ArrayList<>());
                return;
            }
            
            loadEventsForRegistrations(registrations);
        }, e -> {
            progressBar.setVisibility(View.GONE);
            Toast.makeText(this, "Failed to load history: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        });
    }
    
    private void loadEventsForRegistrations(List<WaitingList> registrations) {
        List<EventHistory> historyList = new ArrayList<>();
        final int[] loadedCount = {0};
        
        for (WaitingList registration : registrations) {
            eventController.getEvent(registration.getEventId(), event -> {
                historyList.add(new EventHistory(event, registration));
                loadedCount[0]++;
                
                if (loadedCount[0] == registrations.size()) {
                    progressBar.setVisibility(View.GONE);
                    adapter.updateHistory(historyList);
                }
            }, e -> {
                loadedCount[0]++;
                if (loadedCount[0] == registrations.size()) {
                    progressBar.setVisibility(View.GONE);
                    adapter.updateHistory(historyList);
                }
            });
        }
    }
    
    private void onEventClick(EventHistory history) {
        Intent intent = new Intent(this, EventDetailActivity.class);
        intent.putExtra("eventId", history.getEvent().getEventId());
        startActivity(intent);
    }
}
