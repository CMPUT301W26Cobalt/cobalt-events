package com.example.cobaltevents.ui;

import android.app.AlertDialog;
import android.os.Bundle;
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
import android.content.Intent;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.cobaltevents.R;
import com.example.cobaltevents.controller.EventController;
import com.example.cobaltevents.model.Event;
import com.example.cobaltevents.ui.adapter.EventAdapter;
import com.google.firebase.Timestamp;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

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

    private List<Event> allEvents = new ArrayList<>();
    private String currentQuery = "";
    private int selectedCategoryIndex = 0;
    private int selectedDateIndex = 0;
    private int selectedAvailabilityIndex = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_event_list);

        deviceId = getIntent().getStringExtra("deviceId");
        eventController = new EventController();

        recyclerView = findViewById(R.id.recycler_events);
        progressBar = findViewById(R.id.progress_bar);
        tvEmpty = findViewById(R.id.tv_empty);

        adapter = new EventAdapter(new ArrayList<>(), event -> {
            Intent intent = new Intent(this, EventDetailActivity.class);
            intent.putExtra("eventId", event.getEventId());
            intent.putExtra("deviceId", deviceId);
            startActivity(intent);
        });

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

        loadEvents();
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
            applyFilters();
        }, e -> {
            progressBar.setVisibility(View.GONE);
            Toast.makeText(this, "Failed to load events: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        });
    }

    private void applyFilters() {
        Timestamp now = Timestamp.now();
        List<Event> filtered = new ArrayList<>();

        for (Event event : allEvents) {
            if (!matchesQuery(event)) continue;
            if (!matchesCategory(event)) continue;
            if (!matchesDate(event, now)) continue;
            if (!matchesAvailability(event, now)) continue;
            filtered.add(event);
        }

        adapter.updateEvents(filtered);
        tvEmpty.setVisibility(filtered.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private boolean matchesQuery(Event event) {
        if (currentQuery.isEmpty()) return true;
        String q = currentQuery.toLowerCase();
        if (event.getName() != null && event.getName().toLowerCase().contains(q)) return true;
        if (event.getDescription() != null && event.getDescription().toLowerCase().contains(q)) return true;
        if (event.getLocation() != null && event.getLocation().toLowerCase().contains(q)) return true;
        return false;
    }

    private boolean matchesCategory(Event event) {
        if (selectedCategoryIndex == 0) return true;
        String selected = CATEGORY_OPTIONS[selectedCategoryIndex];
        return selected.equalsIgnoreCase(event.getCategory());
    }

    private boolean matchesDate(Event event, Timestamp now) {
        if (selectedDateIndex == 0) return true;
        if (event.getEventDate() == null) return false;

        Calendar eventCal = Calendar.getInstance();
        eventCal.setTime(event.getEventDate().toDate());

        Calendar nowCal = Calendar.getInstance();
        nowCal.setTime(now.toDate());

        switch (selectedDateIndex) {
            case 1: // Today
                return eventCal.get(Calendar.YEAR) == nowCal.get(Calendar.YEAR)
                        && eventCal.get(Calendar.DAY_OF_YEAR) == nowCal.get(Calendar.DAY_OF_YEAR);
            case 2: // This Week
                return eventCal.get(Calendar.YEAR) == nowCal.get(Calendar.YEAR)
                        && eventCal.get(Calendar.WEEK_OF_YEAR) == nowCal.get(Calendar.WEEK_OF_YEAR);
            case 3: // This Month
                return eventCal.get(Calendar.YEAR) == nowCal.get(Calendar.YEAR)
                        && eventCal.get(Calendar.MONTH) == nowCal.get(Calendar.MONTH);
            default:
                return true;
        }
    }

    private boolean matchesAvailability(Event event, Timestamp now) {
        if (selectedAvailabilityIndex == 0) return true;

        boolean isClosed = event.getRegistrationClose() != null
                && event.getRegistrationClose().compareTo(now) < 0;
        boolean isUpcoming = event.getRegistrationOpen() != null
                && event.getRegistrationOpen().compareTo(now) > 0;
        boolean isOpen = !isClosed && !isUpcoming;

        switch (selectedAvailabilityIndex) {
            case 1: return isOpen;      // Open
            case 2: return isUpcoming;  // Upcoming
            case 3: return isClosed;    // Closed
            default: return true;
        }
    }

    private void showFilterDialog() {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_filter_events, null);

        Spinner spinnerCategory = dialogView.findViewById(R.id.spinner_category);
        Spinner spinnerDate = dialogView.findViewById(R.id.spinner_date);
        Spinner spinnerAvailability = dialogView.findViewById(R.id.spinner_availability);

        spinnerCategory.setAdapter(makeAdapter(CATEGORY_OPTIONS));
        spinnerDate.setAdapter(makeAdapter(DATE_OPTIONS));
        spinnerAvailability.setAdapter(makeAdapter(AVAILABILITY_OPTIONS));

        spinnerCategory.setSelection(selectedCategoryIndex);
        spinnerDate.setSelection(selectedDateIndex);
        spinnerAvailability.setSelection(selectedAvailabilityIndex);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(dialogView)
                .create();

        dialog.show();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        dialogView.findViewById(R.id.btn_close_filter).setOnClickListener(v -> dialog.dismiss());

        dialogView.findViewById(R.id.btn_apply_filters).setOnClickListener(v -> {
            selectedCategoryIndex = spinnerCategory.getSelectedItemPosition();
            selectedDateIndex = spinnerDate.getSelectedItemPosition();
            selectedAvailabilityIndex = spinnerAvailability.getSelectedItemPosition();
            applyFilters();
            dialog.dismiss();
        });

        dialogView.findViewById(R.id.btn_clear_filters).setOnClickListener(v -> {
            selectedCategoryIndex = 0;
            selectedDateIndex = 0;
            selectedAvailabilityIndex = 0;
            applyFilters();
            dialog.dismiss();
        });
    }

    private ArrayAdapter<String> makeAdapter(String[] items) {
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, items);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        return adapter;
    }
}
