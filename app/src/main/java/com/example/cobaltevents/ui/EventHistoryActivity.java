package com.example.cobaltevents.ui;

import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
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
        
        findViewById(R.id.btn_back).setOnClickListener(v -> finish());
        
        adapter = new EventHistoryAdapter(new ArrayList<>(), this::onEventClick);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);
        
        loadHistory();
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
