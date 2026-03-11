package com.example.cobaltevents.ui;

import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Bundle;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.cobaltevents.R;
import com.example.cobaltevents.controller.EventController;
import com.example.cobaltevents.db.WaitingListDB;
import com.example.cobaltevents.model.Event;
import com.example.cobaltevents.model.WaitingList;
import com.example.cobaltevents.ui.adapter.EventAdapter;
import com.google.firebase.Timestamp;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Displays a scrollable list of all available events fetched from Firestore.
 * Supports live search and a filter dialog (category, date, availability).
 */
public class EventListActivity extends AppCompatActivity {

    private static final String[] CATEGORY_OPTIONS = {
            "All", "Sports", "Music", "Arts", "Food", "Technology", "Community"
    };
    private static final String[] DATE_OPTIONS = {
            "All", "Today", "This Week", "This Month"
    };
    private static final String[] AVAILABILITY_OPTIONS = {
            "All", "Open", "Upcoming", "Closed"
    };

    private RecyclerView recyclerView;
    private ProgressBar progressBar;
    private TextView tvEmpty;
    private EventAdapter adapter;
    private String deviceId;
    private EventController eventController;
    private WaitingListDB waitingListDB;
    private final Map<String, WaitingList> activeRegistrationsByEventId = new HashMap<>();
    private final Map<String, Integer> waitlistCountByEventId = new HashMap<>();

    private List<Event> allEvents = new ArrayList<>();
    private String currentQuery = "";
    private int selectedCategoryIndex = 0;
    private int selectedDateIndex = 0;
    private int selectedAvailabilityIndex = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_event_list);

        deviceId = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);
        eventController = new EventController();
        waitingListDB = new WaitingListDB();

        recyclerView = findViewById(R.id.recycler_events);
        progressBar = findViewById(R.id.progress_bar);
        tvEmpty = findViewById(R.id.tv_empty);

        adapter = new EventAdapter(new ArrayList<>(), this::handleJoinOrLeave);

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);

        EditText etSearch = findViewById(R.id.et_search);
        etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable s) {
                currentQuery = s.toString().trim();
                applyFilters();
            }
        });

        findViewById(R.id.btn_filter).setOnClickListener(v -> showFilterDialog());

        View fabAdd = findViewById(R.id.fab_add_event);
        if (fabAdd != null) {
            fabAdd.setOnClickListener(v -> startActivity(new Intent(this, EventCreateActivity.class)));
        }

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
            allEvents = events != null ? events : new ArrayList<>();
            loadActiveRegistrationsThenApplyFilters();
        }, e -> {
            progressBar.setVisibility(View.GONE);
            Toast.makeText(this, "Failed to load events: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        });
    }

    private void loadActiveRegistrationsThenApplyFilters() {
        activeRegistrationsByEventId.clear();
        waitingListDB.getEntrantHistory(deviceId, registrations -> {
            if (registrations != null) {
                for (WaitingList r : registrations) {
                    if (r == null) continue;
                    String status = r.getStatus();
                    boolean isActive = status == null
                            || (!WaitingList.STATUS_WITHDRAWN.equals(status)
                            && !WaitingList.STATUS_CANCELLED.equals(status));
                    if (isActive && r.getEventId() != null) {
                        activeRegistrationsByEventId.put(r.getEventId(), r);
                    }
                }
            }
            adapter.setActiveRegistrationsByEventId(activeRegistrationsByEventId);
            loadWaitlistCountsThenApplyFilters();
            applyFilters();
        }, e -> {
            // If we can't load registrations, still show events.
            adapter.setActiveRegistrationsByEventId(activeRegistrationsByEventId);
            loadWaitlistCountsThenApplyFilters();
            applyFilters();
        });
    }

    private void loadWaitlistCountsThenApplyFilters() {
        waitlistCountByEventId.clear();
        for (Event e : allEvents) {
            if (e == null || e.getEventId() == null) continue;
            String eventId = e.getEventId();
            waitingListDB.getActiveCountForEvent(eventId,
                    count -> {
                        waitlistCountByEventId.put(eventId, count);
                        adapter.setWaitlistCountByEventId(waitlistCountByEventId);
                        applyFilters();
                    },
                    err -> {
                        adapter.setWaitlistCountByEventId(waitlistCountByEventId);
                        applyFilters();
                    });
        }
        adapter.setWaitlistCountByEventId(waitlistCountByEventId);
    }

    private void setupBottomNavigation() {
        // Mark this tab as active (teal), others inactive (grey)
        setEventsTabActive();

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

        View navMyEvents = findViewById(R.id.nav_my_events);
        if (navMyEvents != null) {
            navMyEvents.setOnClickListener(v -> {
                Intent intent = new Intent(this, EventHistoryActivity.class);
                intent.putExtra("deviceId", deviceId);
                startActivity(intent);
            });
        }
    }

    private void setEventsTabActive() {
        int active = ContextCompat.getColor(this, R.color.header_teal);
        int inactive = ContextCompat.getColor(this, R.color.grey_nav_inactive);

        tintNavIconAndText(R.id.iv_nav_notifications, R.id.tv_nav_notifications, inactive);
        tintNavIconAndText(R.id.iv_nav_my_events, R.id.tv_nav_my_events, inactive);
        tintNavIconAndText(R.id.iv_nav_account, R.id.tv_nav_account, inactive);

        tintNavIconAndText(R.id.iv_nav_events, R.id.tv_nav_events, active);
    }

    private void tintNavIconAndText(int iconId, int textId, int color) {
        android.widget.ImageView icon = findViewById(iconId);
        TextView text = findViewById(textId);
        if (icon != null) icon.setColorFilter(color);
        if (text != null) text.setTextColor(color);
    }
    
    private void applyFilters() {
        List<Event> filtered = new ArrayList<>();
        
        for (Event event : allEvents) {
            // Search filter
            if (!currentQuery.isEmpty()) {
                String query = currentQuery.toLowerCase();
                boolean matches = event.getName().toLowerCase().contains(query) ||
                                event.getDescription().toLowerCase().contains(query) ||
                                event.getLocation().toLowerCase().contains(query);
                if (!matches) continue;
            }
            
            // Category filter (placeholder - add category field to Event model if needed)
            // Date filter
            if (selectedDateIndex > 0) {
                if (!matchesDateFilter(event, selectedDateIndex)) continue;
            }
            
            // Availability filter
            if (selectedAvailabilityIndex > 0) {
                if (!matchesAvailabilityFilter(event, selectedAvailabilityIndex)) continue;
            }
            
            filtered.add(event);
        }
        
        adapter.updateEvents(filtered);
        tvEmpty.setVisibility(filtered.isEmpty() ? View.VISIBLE : View.GONE);
    }
    
    private boolean matchesDateFilter(Event event, int dateIndex) {
        if (event.getEventDate() == null) return false;
        
        Calendar eventCal = Calendar.getInstance();
        eventCal.setTime(event.getEventDate().toDate());
        Calendar now = Calendar.getInstance();
        
        switch (dateIndex) {
            case 1: // Today
                return isSameDay(eventCal, now);
            case 2: // This Week
                return isSameWeek(eventCal, now);
            case 3: // This Month
                return isSameMonth(eventCal, now);
            default:
                return true;
        }
    }
    
    private boolean matchesAvailabilityFilter(Event event, int availabilityIndex) {
        Timestamp now = Timestamp.now();
        
        switch (availabilityIndex) {
            case 1: // Open
                return event.getRegistrationOpen() != null && 
                       event.getRegistrationClose() != null &&
                       now.compareTo(event.getRegistrationOpen()) >= 0 &&
                       now.compareTo(event.getRegistrationClose()) <= 0;
            case 2: // Upcoming
                return event.getRegistrationOpen() != null &&
                       now.compareTo(event.getRegistrationOpen()) < 0;
            case 3: // Closed
                return event.getRegistrationClose() != null &&
                       now.compareTo(event.getRegistrationClose()) > 0;
            default:
                return true;
        }
    }
    
    private boolean isSameDay(Calendar cal1, Calendar cal2) {
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
               cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR);
    }
    
    private boolean isSameWeek(Calendar cal1, Calendar cal2) {
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
               cal1.get(Calendar.WEEK_OF_YEAR) == cal2.get(Calendar.WEEK_OF_YEAR);
    }
    
    private boolean isSameMonth(Calendar cal1, Calendar cal2) {
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
               cal1.get(Calendar.MONTH) == cal2.get(Calendar.MONTH);
    }
    
    private void showFilterDialog() {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_filter_events, null);
        
        Spinner spinnerCategory = dialogView.findViewById(R.id.spinner_category);
        Spinner spinnerDate = dialogView.findViewById(R.id.spinner_date);
        Spinner spinnerAvailability = dialogView.findViewById(R.id.spinner_availability);
        
        ArrayAdapter<String> categoryAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, CATEGORY_OPTIONS);
        categoryAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerCategory.setAdapter(categoryAdapter);
        spinnerCategory.setSelection(selectedCategoryIndex);
        
        ArrayAdapter<String> dateAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, DATE_OPTIONS);
        dateAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerDate.setAdapter(dateAdapter);
        spinnerDate.setSelection(selectedDateIndex);
        
        ArrayAdapter<String> availabilityAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, AVAILABILITY_OPTIONS);
        availabilityAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerAvailability.setAdapter(availabilityAdapter);
        spinnerAvailability.setSelection(selectedAvailabilityIndex);
        
        new AlertDialog.Builder(this)
                .setTitle("Filter Events")
                .setView(dialogView)
                .setPositiveButton("Apply", (dialog, which) -> {
                    selectedCategoryIndex = spinnerCategory.getSelectedItemPosition();
                    selectedDateIndex = spinnerDate.getSelectedItemPosition();
                    selectedAvailabilityIndex = spinnerAvailability.getSelectedItemPosition();
                    applyFilters();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void handleJoinOrLeave(Event event, boolean isJoined) {
        if (isJoined) {
            leaveWaitlist(event);
        } else {
            showJoinConfirmDialog(event);
        }
    }

    private void showJoinConfirmDialog(Event event) {
        if (event == null || event.getEventId() == null) {
            Toast.makeText(this, "Invalid event", Toast.LENGTH_SHORT).show();
            return;
        }
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_join_waitlist_confirm, null);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(dialogView)
                .create();

        TextView btnCancel = dialogView.findViewById(R.id.btn_cancel);
        TextView btnJoin = dialogView.findViewById(R.id.btn_join);
        View btnClose = dialogView.findViewById(R.id.btn_close);
        btnCancel.setOnClickListener(v -> dialog.dismiss());
        if (btnClose != null) btnClose.setOnClickListener(v -> dialog.dismiss());
        btnJoin.setOnClickListener(v -> {
            dialog.dismiss();
            joinWaitlist(event);
        });

        dialog.show();
    }

    private void joinWaitlist(Event event) {
        if (event == null || event.getEventId() == null) {
            Toast.makeText(this, "Invalid event", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!isNetworkAvailable()) {
            Toast.makeText(this, getString(R.string.waitlist_fail) + " No internet connection.", Toast.LENGTH_SHORT).show();
            return;
        }

        WaitingList registration = new WaitingList(event.getEventId(), deviceId, WaitingList.STATUS_PENDING);
        waitingListDB.addRegistration(registration,
                id -> {
                    Toast.makeText(this, R.string.waitlist_success, Toast.LENGTH_SHORT).show();
                    loadActiveRegistrationsThenApplyFilters();
                },
                e -> Toast.makeText(this, getString(R.string.waitlist_fail) + " " + e.getMessage(), Toast.LENGTH_SHORT).show());
    }

    private void leaveWaitlist(Event event) {
        if (event.getEventId() == null) {
            Toast.makeText(this, "Invalid event", Toast.LENGTH_SHORT).show();
            return;
        }
        WaitingList reg = activeRegistrationsByEventId.get(event.getEventId());
        if (reg == null || reg.getId() == null) {
            Toast.makeText(this, "Could not find your registration.", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!isNetworkAvailable()) {
            Toast.makeText(this, "Failed to leave waitlist: No internet connection.", Toast.LENGTH_SHORT).show();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("Leave Waitlist")
                .setMessage("Are you sure you want to leave the waitlist for " + event.getName() + "?")
                .setPositiveButton("Leave", (d, which) -> waitingListDB.updateStatus(reg.getId(), WaitingList.STATUS_WITHDRAWN,
                        unused -> {
                            Toast.makeText(this, "Left waitlist for " + event.getName(), Toast.LENGTH_SHORT).show();
                            loadActiveRegistrationsThenApplyFilters();
                        },
                        e -> Toast.makeText(this, "Failed to leave waitlist: " + e.getMessage(), Toast.LENGTH_SHORT).show()))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private boolean isNetworkAvailable() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return false;
        Network network = cm.getActiveNetwork();
        if (network == null) return false;
        NetworkCapabilities caps = cm.getNetworkCapabilities(network);
        return caps != null && (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                || caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
                || caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET));
    }
}
