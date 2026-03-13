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
import com.example.cobaltevents.db.EntrantDB;
import com.example.cobaltevents.db.WaitingListDB;
import com.example.cobaltevents.model.Event;
import com.example.cobaltevents.model.WaitingList;
import com.example.cobaltevents.model.Entrant;
import com.example.cobaltevents.ui.adapter.EventAdapter;
import com.google.firebase.Timestamp;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class EventListActivity extends AppCompatActivity {

    private static final String[] CATEGORY_OPTIONS = {
            "All", "Sports", "Music", "Arts", "Food", "Technology", "Community"
    };
    private static final String[] PRICE_RANGE_OPTIONS = {
            "All", "Free", "Under $10", "$10-$25", "$25-$50", "$50+"
    };
    private static final String[] AGE_GROUP_OPTIONS = {
            "All", "All Ages", "Kids", "Teens", "Adults", "18+", "21+"
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
    private EntrantDB entrantDB;
    private final Map<String, WaitingList> activeRegistrationsByEventId = new HashMap<>();
    private final Map<String, Integer> waitlistCountByEventId = new HashMap<>();

    private List<Event> allEvents = new ArrayList<>();
    private String currentQuery = "";
    private int selectedCategoryIndex = 0;
    private int selectedPriceRangeIndex = 0;
    private int selectedAgeGroupIndex = 0;
    private int selectedAvailabilityIndex = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_event_list);

        deviceId = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);
        eventController = new EventController();
        waitingListDB = new WaitingListDB();
        entrantDB = new EntrantDB(this);

        recyclerView = findViewById(R.id.recycler_events);
        progressBar = findViewById(R.id.progress_bar);
        tvEmpty = findViewById(R.id.tv_empty);

        adapter = new EventAdapter(new ArrayList<>(), this::handleJoinOrLeave);
        adapter.setDeviceId(deviceId);
        adapter.setOnNotificationsToggleListener((eventId, devId, notificationsAllowed) -> {
            waitingListDB.updateNotificationsAllowed(eventId, devId, notificationsAllowed,
                    v -> adapter.updateNotificationsAllowedForEvent(eventId, notificationsAllowed),
                    e -> {
                        Toast.makeText(this, "Failed to update notification setting", Toast.LENGTH_SHORT).show();
                        loadActiveRegistrationsThenApplyFilters();
                    });
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
        loadActiveRegistrationsThenApplyFilters();
        }, e -> {
            progressBar.setVisibility(View.GONE);
            Toast.makeText(this, "Failed to load events: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        });
    }

    private void loadActiveRegistrationsThenApplyFilters() {
        activeRegistrationsByEventId.clear();
        if (allEvents == null || allEvents.isEmpty()) {
            adapter.setActiveRegistrationsByEventId(activeRegistrationsByEventId);
            loadWaitlistCountsThenApplyFilters();
            applyFilters();
            return;
        }
        // Load "am I on waitlist?" per event so we don't depend on collection-group index (getEntrantHistory can fail without it).
        java.util.concurrent.atomic.AtomicInteger pending = new java.util.concurrent.atomic.AtomicInteger(allEvents.size());
        for (Event event : allEvents) {
            if (event == null || event.getEventId() == null) {
                if (pending.decrementAndGet() == 0) finishLoadingRegistrations();
                continue;
            }
            String eventId = event.getEventId();
            waitingListDB.getActiveRegistrationForEvent(eventId, deviceId,
                    reg -> {
                        if (reg != null) activeRegistrationsByEventId.put(eventId, reg);
                        if (pending.decrementAndGet() == 0) finishLoadingRegistrations();
                    },
                    e -> {
                        if (pending.decrementAndGet() == 0) finishLoadingRegistrations();
                    });
        }
    }

    private void finishLoadingRegistrations() {
        adapter.setActiveRegistrationsByEventId(activeRegistrationsByEventId);
        loadWaitlistCountsThenApplyFilters();
        applyFilters();
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

        View navNotifications = findViewById(R.id.nav_notifications);
        if (navNotifications != null) {
            navNotifications.setOnClickListener(v -> {
                startActivity(new Intent(this, NotificationsActivity.class));
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
            if (!currentQuery.isEmpty()) {
                String query = currentQuery.toLowerCase();
                boolean matches = event.getName().toLowerCase().contains(query) ||
                                event.getDescription().toLowerCase().contains(query) ||
                                event.getLocation().toLowerCase().contains(query);
                if (!matches) continue;
            }
            if (selectedCategoryIndex > 0) {
                String selectedCategory = CATEGORY_OPTIONS[selectedCategoryIndex];
                String eventCategory = event.getCategory();
                if (eventCategory == null || !eventCategory.equalsIgnoreCase(selectedCategory)) continue;
            }
            if (selectedPriceRangeIndex > 0) {
                if (!matchesPriceRangeFilter(event, selectedPriceRangeIndex)) continue;
            }
            if (selectedAgeGroupIndex > 0) {
                String selectedAgeGroup = AGE_GROUP_OPTIONS[selectedAgeGroupIndex];
                String eventAgeGroup = event.getAgeGroup();
                if (eventAgeGroup == null || !eventAgeGroup.equalsIgnoreCase(selectedAgeGroup)) continue;
            }
            if (selectedAvailabilityIndex > 0) {
                if (!matchesAvailabilityFilter(event, selectedAvailabilityIndex)) continue;
            }

            filtered.add(event);
        }

        adapter.updateEvents(filtered);
        tvEmpty.setVisibility(filtered.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private boolean matchesPriceRangeFilter(Event event, int priceRangeIndex) {
        String priceStr = event.getPrice();
        if (priceStr == null || priceStr.trim().isEmpty()) return priceRangeIndex == 1;
        priceStr = priceStr.replaceAll("[^0-9.]", "").trim();
        double price;
        try {
            price = priceStr.isEmpty() ? 0 : Double.parseDouble(priceStr);
        } catch (NumberFormatException e) {
            return false;
        }
        switch (priceRangeIndex) {
            case 1: return price <= 0;
            case 2: return price > 0 && price < 10;
            case 3: return price >= 10 && price <= 25;
            case 4: return price > 25 && price <= 50;
            case 5: return price > 50;
            default: return true;
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

    private void showFilterDialog() {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_filter_events, null);

        Spinner spinnerPriceRange = dialogView.findViewById(R.id.spinner_price_range);
        Spinner spinnerAgeGroup = dialogView.findViewById(R.id.spinner_age_group);
        Spinner spinnerCategory = dialogView.findViewById(R.id.spinner_category);
        Spinner spinnerAvailability = dialogView.findViewById(R.id.spinner_availability);

        ArrayAdapter<String> priceRangeAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, PRICE_RANGE_OPTIONS);
        priceRangeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerPriceRange.setAdapter(priceRangeAdapter);
        spinnerPriceRange.setSelection(selectedPriceRangeIndex);

        ArrayAdapter<String> ageGroupAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, AGE_GROUP_OPTIONS);
        ageGroupAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerAgeGroup.setAdapter(ageGroupAdapter);
        spinnerAgeGroup.setSelection(selectedAgeGroupIndex);

        ArrayAdapter<String> categoryAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, CATEGORY_OPTIONS);
        categoryAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerCategory.setAdapter(categoryAdapter);
        spinnerCategory.setSelection(selectedCategoryIndex);

        ArrayAdapter<String> availabilityAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, AVAILABILITY_OPTIONS);
        availabilityAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerAvailability.setAdapter(availabilityAdapter);
        spinnerAvailability.setSelection(selectedAvailabilityIndex);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(dialogView)
                .create();

        dialogView.findViewById(R.id.btn_close_filter).setOnClickListener(v -> dialog.dismiss());

        dialogView.findViewById(R.id.btn_apply_filters).setOnClickListener(v -> {
            selectedPriceRangeIndex = spinnerPriceRange.getSelectedItemPosition();
            selectedAgeGroupIndex = spinnerAgeGroup.getSelectedItemPosition();
            selectedCategoryIndex = spinnerCategory.getSelectedItemPosition();
            selectedAvailabilityIndex = spinnerAvailability.getSelectedItemPosition();
            applyFilters();
            dialog.dismiss();
        });

        dialogView.findViewById(R.id.btn_clear_filters).setOnClickListener(v -> {
            selectedPriceRangeIndex = 0;
            selectedAgeGroupIndex = 0;
            selectedCategoryIndex = 0;
            selectedAvailabilityIndex = 0;
            spinnerPriceRange.setSelection(0);
            spinnerAgeGroup.setSelection(0);
            spinnerCategory.setSelection(0);
            spinnerAvailability.setSelection(0);
            applyFilters();
            dialog.dismiss();
        });

        dialog.show();
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
        Entrant entrant = entrantDB.getEntrant();
        if (!entrant.isValidName() || !entrant.isValidEmail()) {
            Toast.makeText(this, "Please complete your name and email in Account settings before joining a waitlist.", Toast.LENGTH_LONG).show();
            startActivity(new Intent(this, AccountSettingsActivity.class));
            return;
        }
        WaitingList registration = new WaitingList(
                event.getEventId(),
                deviceId,
                1,
                entrant.getName(),
                entrant.getEmail(),
                entrant.getPhone(),
                WaitingList.NOTIFY_EMAIL
        );
        waitingListDB.addRegistration(registration,
                id -> {
                    Toast.makeText(this, R.string.waitlist_success, Toast.LENGTH_SHORT).show();
                    activeRegistrationsByEventId.put(event.getEventId(), registration);
                    adapter.setActiveRegistrationsByEventId(activeRegistrationsByEventId);
                    loadWaitlistCountsThenApplyFilters();
                },
                e -> Toast.makeText(this, getString(R.string.waitlist_fail) + " " + e.getMessage(), Toast.LENGTH_SHORT).show());
    }

    private void leaveWaitlist(Event event) {
        if (event.getEventId() == null) {
            Toast.makeText(this, "Invalid event", Toast.LENGTH_SHORT).show();
            return;
        }
        WaitingList reg = activeRegistrationsByEventId.get(event.getEventId());
        if (reg == null || reg.getDeviceId() == null) {
            Toast.makeText(this, "Could not find your registration.", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!isNetworkAvailable()) {
            Toast.makeText(this, "Failed to leave waitlist: No internet connection.", Toast.LENGTH_SHORT).show();
            return;
        }
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_leave_waitlist_confirm, null);
        ((TextView) dialogView.findViewById(R.id.tv_message))
                .setText("Are you sure you want to leave the waitlist for " + event.getName() + "?");
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(dialogView)
                .create();

        TextView btnCancel = dialogView.findViewById(R.id.btn_cancel);
        TextView btnLeave = dialogView.findViewById(R.id.btn_leave);
        btnCancel.setOnClickListener(v -> dialog.dismiss());
        btnLeave.setOnClickListener(v -> {
            dialog.dismiss();
            waitingListDB.deleteRegistration(event.getEventId(), reg.getDeviceId(),
                    unused -> {
                        Toast.makeText(this, "Left waitlist for " + event.getName(), Toast.LENGTH_SHORT).show();
                        loadActiveRegistrationsThenApplyFilters();
                    },
                    e -> Toast.makeText(this, "Failed to leave waitlist: " + e.getMessage(), Toast.LENGTH_SHORT).show());
        });

        dialog.show();
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
