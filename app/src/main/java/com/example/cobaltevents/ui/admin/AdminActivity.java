package com.example.cobaltevents.ui.admin;

import android.content.res.ColorStateList;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.cobaltevents.R;
import com.example.cobaltevents.controller.EventController;
import com.example.cobaltevents.model.Event;

import java.util.ArrayList;
import java.util.List;

public class AdminActivity extends AppCompatActivity {

    private TextView tabEvents, tabProfiles, tabImages, tabOrganizers, tabNotifications;
    private RecyclerView adminRecycler;
    private EditText searchBar;
    private HorizontalScrollView adminTabsScroll;
    private TextView emptyMessage;

    private AdminAdapter adapter;
    private final List<AdminAdapter.AdminItem> allItems = new ArrayList<>();
    private String currentQuery = "";
    private String currentTab = "Events";

    private EventController eventController;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin);

        tabEvents = findViewById(R.id.tab_events);
        tabProfiles = findViewById(R.id.tab_profiles);
        tabImages = findViewById(R.id.tab_images);
        tabOrganizers = findViewById(R.id.tab_organizers);
        tabNotifications = findViewById(R.id.tab_notifications);

        searchBar = findViewById(R.id.etSearch);
        adminRecycler = findViewById(R.id.adminRecycler);
        adminTabsScroll = findViewById(R.id.adminTabsScroll);
        emptyMessage = findViewById(R.id.emptyMessage);

        eventController = new EventController();

        adminRecycler.setLayoutManager(new LinearLayoutManager(this));
        adapter = new AdminAdapter(new ArrayList<>(), this::handleRemoveClick);
        adminRecycler.setAdapter(adapter);

        tabEvents.setOnClickListener(v -> showEvents());
        tabProfiles.setOnClickListener(v -> showProfiles());
        tabImages.setOnClickListener(v -> showImages());
        tabOrganizers.setOnClickListener(v -> showOrganizers());
        tabNotifications.setOnClickListener(v -> showNotifications());

        searchBar.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) { }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) { }

            @Override
            public void afterTextChanged(Editable s) {
                currentQuery = s.toString().trim().toLowerCase();
                filterCurrentList();
            }
        });

        showEvents();
    }

    private void showEvents() {
        currentTab = "Events";
        resetSearch();
        setSelectedTab(tabEvents);

        eventController.getAllEvents(events -> {
            allItems.clear();

            if (events != null) {
                for (Event event : events) {
                    String id = event.getEventId();
                    if (id == null || id.trim().isEmpty()) {
                        continue;
                    }

                    String title = event.getName() == null ? "Untitled Event" : event.getName();
                    String subtitle = event.getLocation() == null ? "No location" : event.getLocation();

                    allItems.add(new AdminAdapter.AdminItem(id, title, subtitle));
                }
            }

            filterCurrentList();
        }, e -> {
            Toast.makeText(this, "Failed to load events: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            allItems.clear();
            filterCurrentList();
        });
    }

    private void showProfiles() {
        currentTab = "Profiles";
        resetSearch();
        setSelectedTab(tabProfiles);
        loadItems(
                new AdminAdapter.AdminItem("p1", "John Smith", "johnsmith@email.com"),
                new AdminAdapter.AdminItem("p2", "Maria Lopez", "marialopez@email.com"),
                new AdminAdapter.AdminItem("p3", "David Chen", "davidchen@email.com")
        );
    }

    private void showImages() {
        currentTab = "Images";
        resetSearch();
        setSelectedTab(tabImages);
        loadItems(
                new AdminAdapter.AdminItem("i1", "Poster Image 1", "Uploaded for Swimming Lessons"),
                new AdminAdapter.AdminItem("i2", "Poster Image 2", "Uploaded for Yoga Class"),
                new AdminAdapter.AdminItem("i3", "Poster Image 3", "Uploaded for Music Festival")
        );
    }

    private void showOrganizers() {
        currentTab = "Organizers";
        resetSearch();
        setSelectedTab(tabOrganizers);
        loadItems(
                new AdminAdapter.AdminItem("o1", "Community Center Admin", "Organizer account"),
                new AdminAdapter.AdminItem("o2", "Pool Manager", "Organizer account"),
                new AdminAdapter.AdminItem("o3", "Festival Team", "Organizer account")
        );
    }

    private void showNotifications() {
        currentTab = "Notifications";
        resetSearch();
        setSelectedTab(tabNotifications);
        loadItems(
                new AdminAdapter.AdminItem("n1", "Lottery Notice Sent", "Sent to 20 entrants"),
                new AdminAdapter.AdminItem("n2", "Reminder Notification", "Sent to waiting list"),
                new AdminAdapter.AdminItem("n3", "Cancellation Alert", "Sent to selected entrants")
        );
    }

    private void loadItems(AdminAdapter.AdminItem... items) {
        allItems.clear();
        for (AdminAdapter.AdminItem item : items) {
            allItems.add(item);
        }
        filterCurrentList();
    }

    private void filterCurrentList() {
        List<AdminAdapter.AdminItem> filtered = new ArrayList<>();

        for (AdminAdapter.AdminItem item : allItems) {
            String title = item.title == null ? "" : item.title.toLowerCase();
            String subtitle = item.subtitle == null ? "" : item.subtitle.toLowerCase();

            if (currentQuery.isEmpty() || title.contains(currentQuery) || subtitle.contains(currentQuery)) {
                filtered.add(item);
            }
        }

        adapter.updateItems(filtered);

        if (filtered.isEmpty()) {
            emptyMessage.setVisibility(View.VISIBLE);
            adminRecycler.setVisibility(View.GONE);
        } else {
            emptyMessage.setVisibility(View.GONE);
            adminRecycler.setVisibility(View.VISIBLE);
        }

        adapter.setShowRemoveButton(!currentTab.equals("Notifications"));
    }

    private void handleRemoveClick(AdminAdapter.AdminItem item) {
        if (currentTab.equals("Events")) {
            if (item.id == null || item.id.trim().isEmpty() || item.id.equals("events")) {
                Toast.makeText(this, "Invalid event id. Cannot delete.", Toast.LENGTH_SHORT).show();
                return;
            }

            eventController.deleteEvent(item.id, unused -> {
                Toast.makeText(this, "Event removed", Toast.LENGTH_SHORT).show();
                showEvents();
            }, e -> Toast.makeText(this, "Failed to remove event: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            return;
        }

        allItems.remove(item);
        filterCurrentList();
    }

    private void resetSearch() {
        currentQuery = "";
        searchBar.setText("");
    }

    private void setSelectedTab(TextView selectedTab) {
        resetTabStyles();

        selectedTab.setBackgroundTintList(ColorStateList.valueOf(0xFF000000));
        selectedTab.setTextColor(0xFFFFFFFF);

        selectedTab.post(() -> {
            int scrollX = selectedTab.getLeft() - (adminTabsScroll.getWidth() - selectedTab.getWidth()) / 2;
            adminTabsScroll.smoothScrollTo(scrollX, 0);
        });
    }

    private void resetTabStyles() {
        TextView[] tabs = {
                tabEvents, tabProfiles, tabImages, tabOrganizers, tabNotifications
        };

        for (TextView tab : tabs) {
            tab.setBackgroundResource(R.drawable.bg_tab);
            tab.setBackgroundTintList(ColorStateList.valueOf(0xFFFFFFFF));
            tab.setTextColor(0xFF333333);
        }
    }
}