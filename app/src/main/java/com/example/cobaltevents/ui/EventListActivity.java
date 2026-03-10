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
            // TODO: handle join waitlist action for event
            Toast.makeText(this, "Joining: " + event.getName(), Toast.LENGTH_SHORT).show();
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
            applyFilters();
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
