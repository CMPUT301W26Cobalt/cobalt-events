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
import androidx.appcompat.app.AlertDialog;
import android.app.Dialog;
import android.view.Window;
import android.graphics.drawable.ColorDrawable;
import android.graphics.Color;
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
import java.util.Date;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * US 01.02.03: Display entrant's event registration history
 */
public class EventHistoryActivity extends AppCompatActivity {
    
    private RecyclerView recyclerView;
    private ProgressBar progressBar;
    private TextView tvEmpty;
    private EventHistoryAdapter adapter;
    private TextView tabUpcoming;
    private TextView tabPast;
    private final List<EventHistory> allHistory = new ArrayList<>();
    
    private String deviceId;
    private WaitingListDB waitingListDB;
    private EventController eventController;
    private enum Tab { UPCOMING, PAST }
    private Tab currentTab = Tab.UPCOMING;
    
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
        
        adapter = new EventHistoryAdapter(new ArrayList<>(), new EventHistoryAdapter.Listener() {
            @Override
            public void onHistoryClick(EventHistory history) {
                EventHistoryActivity.this.onEventClick(history);
            }
            @Override
            public void onDeleteClick(EventHistory history) {
                confirmRemoveFromWaitlist(history);
            }
        });
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);

        setupBottomNavigation();
        setupTabs();
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
    
    private void setupTabs() {
        tabUpcoming = findViewById(R.id.tab_upcoming);
        tabPast = findViewById(R.id.tab_past);
        
        if (tabUpcoming != null) {
            tabUpcoming.setOnClickListener(v -> {
                currentTab = Tab.UPCOMING;
                applyFilter();
                updateTabStyles();
            });
        }
        if (tabPast != null) {
            tabPast.setOnClickListener(v -> {
                currentTab = Tab.PAST;
                applyFilter();
                updateTabStyles();
            });
        }
        updateTabStyles();
    }
    
    private void updateTabStyles() {
        if (tabUpcoming == null || tabPast == null) return;
        int white = getResources().getColor(android.R.color.white);
        int green = getResources().getColor(R.color.user_green);
        
        switch (currentTab) {
            case UPCOMING:
                tabUpcoming.setBackgroundResource(R.drawable.tab_active_green_solid);
                tabUpcoming.setTextColor(white);
                tabPast.setBackgroundResource(R.drawable.tab_inactive_white);
                tabPast.setTextColor(green);
                break;
            case PAST:
                tabPast.setBackgroundResource(R.drawable.tab_active_green_solid);
                tabPast.setTextColor(white);
                tabUpcoming.setBackgroundResource(R.drawable.tab_inactive_white);
                tabUpcoming.setTextColor(green);
                break;
        }
    }
    
    private void loadHistory() {
        progressBar.setVisibility(View.VISIBLE);
        tvEmpty.setVisibility(View.GONE);
        allHistory.clear();

        eventController.getAllEvents(events -> {
            if (events == null || events.isEmpty()) {
                progressBar.setVisibility(View.GONE);
                tvEmpty.setVisibility(View.VISIBLE);
                adapter.updateHistory(new ArrayList<>());
                return;
            }
            final int total = events.size();
            final int[] processed = {0};
            List<EventHistory> temp = new ArrayList<>();
            for (Event event : events) {
                if (event == null || event.getEventId() == null) {
                    if (++processed[0] == total) finishHistoryLoad(temp);
                    continue;
                }
                waitingListDB.getRegistrationForEventAnyStatus(event.getEventId(), deviceId, reg -> {
                    if (reg != null) {
                        temp.add(new EventHistory(event, reg));
                    }
                    if (++processed[0] == total) finishHistoryLoad(temp);
                }, e -> {
                    if (++processed[0] == total) finishHistoryLoad(temp);
                });
            }
        }, e -> {
            progressBar.setVisibility(View.GONE);
            Toast.makeText(this, "Failed to load history: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        });
    }

    private void finishHistoryLoad(List<EventHistory> historyList) {
        progressBar.setVisibility(View.GONE);
        allHistory.clear();
        allHistory.addAll(historyList);
        applyFilter();
        updateTabStyles();
    }
    
    private void applyFilter() {
        List<EventHistory> filtered = new ArrayList<>();
        Date now = new Date();
        for (EventHistory eh : allHistory) {
            Event event = eh.getEvent();
            if (event == null) continue;
            switch (currentTab) {
                case UPCOMING:
                    if (event.getEventDate() != null && event.getEventDate().toDate().after(now)) {
                        filtered.add(eh);
                    }
                    break;
                case PAST:
                    if (event.getEventDate() != null && event.getEventDate().toDate().before(now)) {
                        filtered.add(eh);
                    }
                    break;
            }
        }
        Collections.sort(filtered, new Comparator<EventHistory>() {
            @Override
            public int compare(EventHistory a, EventHistory b) {
                Date da = a.getEvent().getEventDate() != null ? a.getEvent().getEventDate().toDate() : new Date(0);
                Date db = b.getEvent().getEventDate() != null ? b.getEvent().getEventDate().toDate() : new Date(0);
                return Long.compare(db.getTime(), da.getTime());
            }
        });
        adapter.updateHistory(filtered);
        tvEmpty.setVisibility(filtered.isEmpty() ? View.VISIBLE : View.GONE);
    }
    
    private void onEventClick(EventHistory history) {
        Intent intent = new Intent(this, EventDetailActivity.class);
        intent.putExtra("eventId", history.getEvent().getEventId());
        startActivity(intent);
    }

    private void confirmRemoveFromWaitlist(EventHistory history) {
        if (history == null || history.getEvent() == null || history.getEvent().getEventId() == null) return;
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_leave_waitlist_confirm, null);
        AlertDialog dialog = new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setView(dialogView)
                .create();

        TextView btnCancel = dialogView.findViewById(R.id.btn_cancel);
        TextView btnLeave = dialogView.findViewById(R.id.btn_leave);
        btnCancel.setOnClickListener(v -> dialog.dismiss());
        btnLeave.setOnClickListener(v -> {
            dialog.dismiss();
            waitingListDB.deleteRegistration(history.getEvent().getEventId(), deviceId,
                    unused -> {
                        Toast.makeText(this, "Left waitlist.", Toast.LENGTH_SHORT).show();
                        loadHistory();
                    },
                    e -> Toast.makeText(this, "Failed to leave waitlist: " + e.getMessage(), Toast.LENGTH_SHORT).show());
        });

        dialog.show();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
        }
    }
}
