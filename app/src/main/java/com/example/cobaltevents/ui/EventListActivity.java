package com.example.cobaltevents.ui;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
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
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.example.cobaltevents.R;
import com.example.cobaltevents.controller.EventController;
import com.example.cobaltevents.db.EntrantDB;
import com.example.cobaltevents.db.NotificationDB;
import com.example.cobaltevents.db.WaitingListDB;
import com.example.cobaltevents.model.Event;
import com.example.cobaltevents.model.Notification;
import com.example.cobaltevents.model.WaitingList;
import com.example.cobaltevents.model.Entrant;
import com.example.cobaltevents.controller.GeolocationController;
import com.example.cobaltevents.ui.adapter.EventAdapter;
import com.google.firebase.Timestamp;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
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
    private static final String[] CAPACITY_OPTIONS = {
            "All", "Spots Available", "Full", "Unlimited"
    };
    private static final String[] VISIBILITY_OPTIONS = {
            "All", "Public", "Private"
    };

    private RecyclerView recyclerView;
    private ProgressBar progressBar;
    private TextView tvEmpty;
    private SwipeRefreshLayout swipeRefreshLayout;
    private EventAdapter adapter;
    private boolean eventsLoading = false;
    private String deviceId;
    private EventController eventController;
    private NotificationDB notificationDB;
    private WaitingListDB waitingListDB;
    private EntrantDB entrantDB;
    private GeolocationController geolocationController;
    private Event pendingGeoJoinEvent;
    private final ActivityResultLauncher<String> locationPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) {
                    geolocationController.fetchLocationOnStartup(this);
                    if (pendingGeoJoinEvent != null) {
                        joinAndRecordLocation(pendingGeoJoinEvent);
                    }
                } else {
                    Toast.makeText(this, "Location permission denied — cannot join this event.", Toast.LENGTH_LONG).show();
                }
                pendingGeoJoinEvent = null;
            });
    private final Map<String, WaitingList> activeRegistrationsByEventId = new HashMap<>();
    private final Map<String, WaitingList> registrationsByEventId = new HashMap<>();
    private final Map<String, Integer> waitlistCountByEventId = new HashMap<>();

    private List<Event> allEvents = new ArrayList<>();
    private String currentQuery = "";
    private String currentKeywordInput = "";
    private int selectedCategoryIndex = 0;
    private int selectedPriceRangeIndex = 0;
    private int selectedAgeGroupIndex = 0;
    private int selectedCapacityIndex = 0;
    private int selectedVisibilityIndex = 0;
    private Long availabilityStartMillis = null;
    private Long availabilityEndMillis = null;
    private final List<String> selectedKeywords = new ArrayList<>();
    private EditText etKeywords;
    private HorizontalScrollView scrollKeywordChips;
    private LinearLayout layoutKeywordChips;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_event_list);

        deviceId = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);
        eventController = new EventController();
        notificationDB = new NotificationDB();
        waitingListDB = new WaitingListDB();
        entrantDB = new EntrantDB(this);
        geolocationController = new GeolocationController();

        if (geolocationController.hasLocationPermission(this)) {
            geolocationController.fetchLocationOnStartup(this);
        } else {
            locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION);
        }

        recyclerView = findViewById(R.id.recycler_events);
        progressBar = findViewById(R.id.progress_bar);
        tvEmpty = findViewById(R.id.tv_empty);
        swipeRefreshLayout = findViewById(R.id.swipe_refresh_events);

        adapter = new EventAdapter(new ArrayList<>(), this::handleJoinOrLeave);
        adapter.setDeviceId(deviceId);

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

        etKeywords = findViewById(R.id.et_keywords);
        scrollKeywordChips = findViewById(R.id.scroll_keyword_chips);
        layoutKeywordChips = findViewById(R.id.layout_keyword_chips);
        if (etKeywords != null) {
            etKeywords.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
                @Override
                public void afterTextChanged(Editable s) {
                    currentKeywordInput = s.toString();
                }
            });
            etKeywords.setOnEditorActionListener((v, actionId, event) -> {
                boolean isImeAction = actionId == android.view.inputmethod.EditorInfo.IME_ACTION_DONE
                        || actionId == android.view.inputmethod.EditorInfo.IME_ACTION_GO
                        || actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH
                        || actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEND;
                boolean isEnter = event != null
                        && event.getAction() == android.view.KeyEvent.ACTION_DOWN
                        && event.getKeyCode() == android.view.KeyEvent.KEYCODE_ENTER;
                if (isImeAction || isEnter) {
                    addKeywordChipFromInput();
                    return true;
                }
                return false;
            });
        }
        TextView btnAddKeyword = findViewById(R.id.btn_add_keyword);
        if (btnAddKeyword != null) {
            btnAddKeyword.setOnClickListener(v -> addKeywordChipFromInput());
        }
        renderKeywordChips();

        findViewById(R.id.btn_filter).setOnClickListener(v -> showFilterDialog());

        if (swipeRefreshLayout != null) {
            swipeRefreshLayout.setOnRefreshListener(this::loadEvents);
            swipeRefreshLayout.setColorSchemeColors(ContextCompat.getColor(this, R.color.user_green));
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
        eventsLoading = true;
        progressBar.setVisibility(View.VISIBLE);
        if (recyclerView != null) recyclerView.setVisibility(View.INVISIBLE);
        if (tvEmpty != null) tvEmpty.setVisibility(View.GONE);
        if (swipeRefreshLayout != null) swipeRefreshLayout.setRefreshing(false);
        eventController.getAllEvents(events -> {
            if (swipeRefreshLayout != null) swipeRefreshLayout.setRefreshing(false);
            allEvents = events != null ? events : new ArrayList<>();
        loadActiveRegistrationsThenApplyFilters();
        }, e -> {
            progressBar.setVisibility(View.GONE);
            if (swipeRefreshLayout != null) swipeRefreshLayout.setRefreshing(false);
            eventsLoading = false;
            if (recyclerView != null) recyclerView.setVisibility(View.VISIBLE);
            Toast.makeText(this, "Failed to load events: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        });
    }

    private void loadActiveRegistrationsThenApplyFilters() {
        activeRegistrationsByEventId.clear();
        registrationsByEventId.clear();
        if (allEvents == null || allEvents.isEmpty()) {
            adapter.setActiveRegistrationsByEventId(activeRegistrationsByEventId);
            adapter.setRegistrationsByEventId(registrationsByEventId);
            loadWaitlistCountsThenFinalize();
            applyFilters();
            return;
        }
        java.util.concurrent.atomic.AtomicInteger pending = new java.util.concurrent.atomic.AtomicInteger(allEvents.size());
        for (Event event : allEvents) {
            if (event == null || event.getEventId() == null) {
                if (pending.decrementAndGet() == 0) finishLoadingRegistrations();
                continue;
            }
            String eventId = event.getEventId();
            waitingListDB.getRegistrationForEventAnyStatus(eventId, deviceId,
                    reg -> {
                        if (reg != null) {
                            registrationsByEventId.put(eventId, reg);
                            if (isActiveStatus(reg.getStatus())) {
                                activeRegistrationsByEventId.put(eventId, reg);
                            }
                        }
                        if (pending.decrementAndGet() == 0) finishLoadingRegistrations();
                    },
                    e -> {
                        if (pending.decrementAndGet() == 0) finishLoadingRegistrations();
                    });
        }
    }

    private void finishLoadingRegistrations() {
        adapter.setActiveRegistrationsByEventId(activeRegistrationsByEventId);
        adapter.setRegistrationsByEventId(registrationsByEventId);
        applyNotificationEffectiveStatusToAdapter();
    }

    private void applyNotificationEffectiveStatusToAdapter() {
        if (deviceId == null || deviceId.isEmpty()) {
            adapter.setActiveRegistrationsByEventId(activeRegistrationsByEventId);
            adapter.setRegistrationsByEventId(registrationsByEventId);
            adapter.setEffectiveStatusByEventId(new java.util.HashMap<>());
            loadWaitlistCountsThenFinalize();
            return;
        }
        notificationDB.getNotificationsForRecipient(deviceId,
                notifications -> {
                    adapter.setActiveRegistrationsByEventId(activeRegistrationsByEventId);
                    adapter.setRegistrationsByEventId(registrationsByEventId);
                    adapter.setEffectiveStatusByEventId(computeEffectiveStatusByEventId(notifications));
                    loadWaitlistCountsThenFinalize();
                },
                e -> {
                    adapter.setActiveRegistrationsByEventId(activeRegistrationsByEventId);
                    adapter.setRegistrationsByEventId(registrationsByEventId);
                    adapter.setEffectiveStatusByEventId(new java.util.HashMap<>());
                    loadWaitlistCountsThenFinalize();
                });
    }

    private java.util.Map<String, String> computeEffectiveStatusByEventId(List<Notification> notifications) {
        java.util.Map<String, String> effective = new java.util.HashMap<>();
        if (notifications == null) return effective;
        java.util.Set<String> processedEventIds = new java.util.HashSet<>();
        for (Notification n : notifications) {
            if (n == null || n.getEventId() == null || n.getType() == null) continue;
            String eventId = n.getEventId();
            if (processedEventIds.contains(eventId)) continue;
            String effectiveStatus = getOverrideStatusFromNotification(n);
            if (effectiveStatus == null) continue;
            processedEventIds.add(eventId);
            effective.put(eventId, effectiveStatus);
        }
        return effective;
    }

    private String getOverrideStatusFromNotification(Notification n) {
        if (n == null || n.getType() == null) return null;
        String type = n.getType();
        String response = n.getResponse();
        if (Notification.TYPE_CO_ORGANIZER.equals(type)) {
            // No waitlist connection for co-organization.
            return null;
        }
        if (Notification.TYPE_NOT_SELECTED.equals(type)) {
            // Still in the waitlist; only indicates you weren't selected.
            return WaitingList.STATUS_NOT_SELECTED;
        }
        if (Notification.TYPE_PRIVATE_EVENT.equals(type)) {
            // Private invitations should not force event-list join/leave state
            // until the user actually presses accept/decline and a waitlist entry exists.
            return null;
        }
        if (Notification.TYPE_SELECTED.equals(type) || Notification.TYPE_GOT_OFF_WAITLIST.equals(type)) {
            if (Notification.RESPONSE_ACCEPTED.equals(response)) return WaitingList.STATUS_ENROLLED;
            if (Notification.RESPONSE_DECLINED.equals(response)) return WaitingList.STATUS_DECLINED;
            // Pending selected/star must not flip JOIN/LEAVE UI by itself.
            return null;
        }
        return null;
    }

    private boolean isActiveStatus(String status) {
        return WaitingList.STATUS_PENDING.equals(status)
                || WaitingList.STATUS_SELECTED.equals(status)
                || WaitingList.STATUS_ENROLLED.equals(status)
                // X / "not-selected" should still count as being in the waitlist.
                || WaitingList.STATUS_NOT_SELECTED.equals(status);
    }

    private void loadWaitlistCountsThenFinalize() {
        // Wait until all events have their waitlist counts + notification effective status
        // computed before showing the list UI.
        waitlistCountByEventId.clear();

        java.util.List<String> eventIds = new java.util.ArrayList<>();
        for (Event e : allEvents) {
            if (e == null || e.getEventId() == null) continue;
            eventIds.add(e.getEventId());
        }

        if (eventIds.isEmpty()) {
            adapter.setWaitlistCountByEventId(waitlistCountByEventId);
            eventsLoading = false;
            applyFilters();
            progressBar.setVisibility(View.GONE);
            if (recyclerView != null) recyclerView.setVisibility(View.VISIBLE);
            if (swipeRefreshLayout != null) swipeRefreshLayout.setRefreshing(false);
            return;
        }

        java.util.concurrent.atomic.AtomicInteger pending = new java.util.concurrent.atomic.AtomicInteger(eventIds.size());
        for (String eventId : eventIds) {
            waitingListDB.getActiveCountForEvent(eventId,
                    count -> {
                        waitlistCountByEventId.put(eventId, count);
                        if (pending.decrementAndGet() == 0) {
                            adapter.setWaitlistCountByEventId(waitlistCountByEventId);
                            eventsLoading = false;
                            applyFilters();
                            progressBar.setVisibility(View.GONE);
                            if (recyclerView != null) recyclerView.setVisibility(View.VISIBLE);
                            if (swipeRefreshLayout != null) swipeRefreshLayout.setRefreshing(false);
                        }
                    },
                    err -> {
                        // Still finalize when all callbacks return.
                        if (pending.decrementAndGet() == 0) {
                            adapter.setWaitlistCountByEventId(waitlistCountByEventId);
                            eventsLoading = false;
                            applyFilters();
                            progressBar.setVisibility(View.GONE);
                            if (recyclerView != null) recyclerView.setVisibility(View.VISIBLE);
                            if (swipeRefreshLayout != null) swipeRefreshLayout.setRefreshing(false);
                        }
                    });
        }
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
        if (eventsLoading) return;
        List<Event> filtered = new ArrayList<>();

        for (Event event : allEvents) {
            if (!currentQuery.isEmpty()) {
                final String query = currentQuery.toLowerCase();
                String name = event.getName();
                String desc = event.getDescription();
                String loc = event.getLocation();
                boolean matches =
                        (name != null && name.toLowerCase().contains(query)) ||
                        (desc != null && desc.toLowerCase().contains(query)) ||
                        (loc != null && loc.toLowerCase().contains(query));
                if (!matches) continue;
            }
            if (selectedCategoryIndex > 0) {
                String selectedCategory = CATEGORY_OPTIONS[selectedCategoryIndex];
                List<String> eventCategories = event.getCategory();
                boolean hasMatch = false;
                for (String eventCategory : eventCategories) {
                    if (eventCategory != null && eventCategory.equalsIgnoreCase(selectedCategory)) {
                        hasMatch = true;
                        break;
                    }
                }
                if (!hasMatch) continue;
            }
            if (selectedPriceRangeIndex > 0) {
                if (!matchesPriceRangeFilter(event, selectedPriceRangeIndex)) continue;
            }
            if (selectedAgeGroupIndex > 0) {
                String selectedAgeGroup = AGE_GROUP_OPTIONS[selectedAgeGroupIndex];
                String eventAgeGroup = event.getAgeGroup();
                if (eventAgeGroup == null || !eventAgeGroup.equalsIgnoreCase(selectedAgeGroup)) continue;
            }
            if (!matchesAvailabilityDateRangeFilter(event)) {
                continue;
            }
            if (selectedCapacityIndex > 0) {
                if (!matchesCapacityFilter(event, selectedCapacityIndex)) continue;
            }
            if (selectedVisibilityIndex > 0) {
                if (!matchesVisibilityFilter(event, selectedVisibilityIndex)) continue;
            }
            if (!selectedKeywords.isEmpty()) {
                if (!matchesKeywordFilters(event)) continue;
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
    private boolean matchesAvailabilityDateRangeFilter(Event event) {
        if (availabilityStartMillis == null && availabilityEndMillis == null) return true;
        Timestamp eventTimestamp = event.getEventDate();
        if (eventTimestamp == null) return false;
        long eventMillis = eventTimestamp.toDate().getTime();
        if (availabilityStartMillis != null && eventMillis < availabilityStartMillis) return false;
        return availabilityEndMillis == null || eventMillis <= availabilityEndMillis;
    }
    private boolean matchesCapacityFilter(Event event, int capacityIndex) {
        int capacity = event.getWaitingListCapacity();
        int currentCount = waitlistCountByEventId.getOrDefault(event.getEventId(), 0);
        boolean isUnlimited = capacity <= 0;

        switch (capacityIndex) {
            case 1: // Spots Available
                return isUnlimited || currentCount < capacity;
            case 2: // Full
                return !isUnlimited && currentCount >= capacity;
            case 3: // Unlimited
                return isUnlimited;
            default:
                return true;
        }
    }
    private boolean matchesVisibilityFilter(Event event, int visibilityIndex) {
        switch (visibilityIndex) {
            case 1: // Public
                return !event.isPrivate();
            case 2: // Private
                return event.isPrivate();
            default:
                return true;
        }
    }

    /**
     * Keyword chips match only {@link Event#getCategory()} strings (substring, case-insensitive).
     * Title, description, location, private/public, criteria, age group, etc. are not considered.
     */
    private boolean matchesKeywordFilters(Event event) {
        List<String> normalizedCategories = new ArrayList<>();
        for (String category : event.getCategory()) {
            if (category != null) normalizedCategories.add(category.toLowerCase(Locale.US));
        }
        if (normalizedCategories.isEmpty()) {
            return false;
        }

        for (String keyword : selectedKeywords) {
            if (keyword == null) continue;
            String term = keyword.trim().toLowerCase(Locale.US);
            if (term.isEmpty()) continue;
            for (String category : normalizedCategories) {
                if (category.contains(term)) return true;
            }
        }
        return false;
    }

    private void showFilterDialog() {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_filter_events, null);

        Spinner spinnerPriceRange = dialogView.findViewById(R.id.spinner_price_range);
        Spinner spinnerAgeGroup = dialogView.findViewById(R.id.spinner_age_group);
        Spinner spinnerCategory = dialogView.findViewById(R.id.spinner_category);
        Spinner spinnerCapacity = dialogView.findViewById(R.id.spinner_capacity);
        Spinner spinnerVisibility = dialogView.findViewById(R.id.spinner_visibility);
        TextView btnAvailabilityStart = dialogView.findViewById(R.id.btn_availability_start);
        TextView btnAvailabilityEnd = dialogView.findViewById(R.id.btn_availability_end);

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

        ArrayAdapter<String> capacityAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, CAPACITY_OPTIONS);
        capacityAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerCapacity.setAdapter(capacityAdapter);
        spinnerCapacity.setSelection(selectedCapacityIndex);

        ArrayAdapter<String> visibilityAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, VISIBILITY_OPTIONS);
        visibilityAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerVisibility.setAdapter(visibilityAdapter);
        spinnerVisibility.setSelection(selectedVisibilityIndex);

        final Long[] tempAvailabilityStart = {availabilityStartMillis};
        final Long[] tempAvailabilityEnd = {availabilityEndMillis};
        btnAvailabilityStart.setText(formatDateLabel(tempAvailabilityStart[0], "Start date"));
        btnAvailabilityEnd.setText(formatDateLabel(tempAvailabilityEnd[0], "End date"));
        btnAvailabilityStart.setOnClickListener(v -> showDatePicker(tempAvailabilityStart[0], false, selected -> {
            tempAvailabilityStart[0] = selected;
            btnAvailabilityStart.setText(formatDateLabel(selected, "Start date"));
        }));
        btnAvailabilityEnd.setOnClickListener(v -> showDatePicker(tempAvailabilityEnd[0], true, selected -> {
            tempAvailabilityEnd[0] = selected;
            btnAvailabilityEnd.setText(formatDateLabel(selected, "End date"));
        }));

        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setView(dialogView)
                .create();

        dialogView.findViewById(R.id.btn_close_filter).setOnClickListener(v -> dialog.dismiss());

        dialogView.findViewById(R.id.btn_apply_filters).setOnClickListener(v -> {
            selectedPriceRangeIndex = spinnerPriceRange.getSelectedItemPosition();
            selectedAgeGroupIndex = spinnerAgeGroup.getSelectedItemPosition();
            selectedCategoryIndex = spinnerCategory.getSelectedItemPosition();
            selectedCapacityIndex = spinnerCapacity.getSelectedItemPosition();
            selectedVisibilityIndex = spinnerVisibility.getSelectedItemPosition();
            if (tempAvailabilityStart[0] != null && tempAvailabilityEnd[0] != null
                    && tempAvailabilityStart[0] > tempAvailabilityEnd[0]) {
                Toast.makeText(this, "Start date cannot be after end date.", Toast.LENGTH_SHORT).show();
                return;
            }
            availabilityStartMillis = tempAvailabilityStart[0];
            availabilityEndMillis = tempAvailabilityEnd[0];
            applyFilters();
            dialog.dismiss();
        });

        dialogView.findViewById(R.id.btn_clear_filters).setOnClickListener(v -> {
            selectedPriceRangeIndex = 0;
            selectedAgeGroupIndex = 0;
            selectedCategoryIndex = 0;
            selectedCapacityIndex = 0;
            selectedVisibilityIndex = 0;
            availabilityStartMillis = null;
            availabilityEndMillis = null;
            spinnerPriceRange.setSelection(0);
            spinnerAgeGroup.setSelection(0);
            spinnerCategory.setSelection(0);
            spinnerCapacity.setSelection(0);
            spinnerVisibility.setSelection(0);
            btnAvailabilityStart.setText(formatDateLabel(null, "Start date"));
            btnAvailabilityEnd.setText(formatDateLabel(null, "End date"));
            applyFilters();
            dialog.dismiss();
        });

        dialog.show();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
        }
    }

    private void addKeywordChipFromInput() {
        if (etKeywords == null) return;
        String raw = currentKeywordInput != null ? currentKeywordInput : "";
        String keyword = raw.trim();
        if (keyword.isEmpty()) return;
        if (!containsKeywordIgnoreCase(keyword)) {
            selectedKeywords.add(keyword);
            renderKeywordChips();
            applyFilters();
        }
        etKeywords.setText("");
        currentKeywordInput = "";
    }

    private void renderKeywordChips() {
        if (layoutKeywordChips == null || scrollKeywordChips == null) return;
        layoutKeywordChips.removeAllViews();
        if (selectedKeywords.isEmpty()) {
            scrollKeywordChips.setVisibility(View.GONE);
            return;
        }
        scrollKeywordChips.setVisibility(View.VISIBLE);
        for (String keyword : selectedKeywords) {
            layoutKeywordChips.addView(createKeywordChip(keyword));
        }
    }

    private View createKeywordChip(String keyword) {
        LinearLayout chip = new LinearLayout(this);
        chip.setOrientation(LinearLayout.HORIZONTAL);
        chip.setGravity(android.view.Gravity.CENTER_VERTICAL);
        chip.setBackgroundResource(R.drawable.bg_keyword_chip_white);
        int horizontalPadding = dpToPx(12);
        int verticalPadding = dpToPx(6);
        chip.setPadding(horizontalPadding, verticalPadding, horizontalPadding, verticalPadding);

        LinearLayout.LayoutParams chipParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        chipParams.setMarginEnd(dpToPx(8));
        chip.setLayoutParams(chipParams);

        TextView keywordText = new TextView(this);
        keywordText.setText(keyword);
        keywordText.setTextColor(ContextCompat.getColor(this, R.color.header_teal));
        keywordText.setTextSize(14f);

        TextView removeText = new TextView(this);
        removeText.setText("×");
        removeText.setTextColor(ContextCompat.getColor(this, R.color.header_teal));
        removeText.setTextSize(14f);
        removeText.setPadding(dpToPx(8), 0, 0, 0);
        removeText.setOnClickListener(v -> {
            selectedKeywords.remove(keyword);
            renderKeywordChips();
            applyFilters();
        });

        chip.addView(keywordText);
        chip.addView(removeText);
        return chip;
    }

    private int dpToPx(int dp) {
        float density = getResources().getDisplayMetrics().density;
        return Math.round(dp * density);
    }

    private boolean containsKeywordIgnoreCase(String candidate) {
        for (String existing : selectedKeywords) {
            if (existing != null && existing.equalsIgnoreCase(candidate)) return true;
        }
        return false;
    }

    private interface OnDateSelectedListener {
        void onDateSelected(long millis);
    }

    private void showDatePicker(Long initialMillis, boolean asEndOfDay, OnDateSelectedListener listener) {
        Calendar cal = Calendar.getInstance();
        if (initialMillis != null) {
            cal.setTimeInMillis(initialMillis);
        }
        new android.app.DatePickerDialog(
                this,
                (view, year, month, dayOfMonth) -> {
                    Calendar selected = Calendar.getInstance();
                    selected.set(Calendar.YEAR, year);
                    selected.set(Calendar.MONTH, month);
                    selected.set(Calendar.DAY_OF_MONTH, dayOfMonth);
                    if (asEndOfDay) {
                        selected.set(Calendar.HOUR_OF_DAY, 23);
                        selected.set(Calendar.MINUTE, 59);
                        selected.set(Calendar.SECOND, 59);
                        selected.set(Calendar.MILLISECOND, 999);
                    } else {
                        selected.set(Calendar.HOUR_OF_DAY, 0);
                        selected.set(Calendar.MINUTE, 0);
                        selected.set(Calendar.SECOND, 0);
                        selected.set(Calendar.MILLISECOND, 0);
                    }
                    listener.onDateSelected(selected.getTimeInMillis());
                },
                cal.get(Calendar.YEAR),
                cal.get(Calendar.MONTH),
                cal.get(Calendar.DAY_OF_MONTH)
        ).show();
    }

    private String formatDateLabel(Long millis, String placeholder) {
        if (millis == null) return placeholder;
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(millis);
        return String.format(Locale.US, "%04d-%02d-%02d",
                cal.get(Calendar.YEAR),
                cal.get(Calendar.MONTH) + 1,
                cal.get(Calendar.DAY_OF_MONTH));
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
        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
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
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
        }
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
        if (event.isGeolocationRequired()) {
            if (geolocationController.hasLocationPermission(this)) {
                joinAndRecordLocation(event);
            } else {
                pendingGeoJoinEvent = event;
                new androidx.appcompat.app.AlertDialog.Builder(this)
                        .setTitle("Location Required")
                        .setMessage("This event requires your location to be recorded when joining the waitlist.")
                        .setPositiveButton("Allow", (d, w) ->
                                locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION))
                        .setNegativeButton("Cancel", null)
                        .show();
            }
            return;
        }
        performJoinWaitlist(event);
    }

    private void joinAndRecordLocation(Event event) {
        geolocationController.checkDistanceForEvent(this, event,
                new GeolocationController.GeoJoinCallback() {
                    @Override
                    public void onAllowed(android.location.Location userLocation) {
                        performJoinWaitlist(event);
                        geolocationController.recordLocationForEvent(
                                EventListActivity.this, deviceId, event.getEventId(),
                                userLocation, unused -> {}, e -> {});
                    }
                    @Override
                    public void onBlocked(float distanceMeters) {
                        int km = Math.round(distanceMeters / 1000f);
                        Toast.makeText(EventListActivity.this,
                                "You are " + km + "km away. Must be within 30km to join.",
                                Toast.LENGTH_LONG).show();
                    }
                    @Override
                    public void onError(String message) {
                        Toast.makeText(EventListActivity.this, message, Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void performJoinWaitlist(Event event) {
        Entrant entrant = entrantDB.getEntrant();
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
                    registrationsByEventId.put(event.getEventId(), registration);
                    adapter.setActiveRegistrationsByEventId(activeRegistrationsByEventId);
                    adapter.setRegistrationsByEventId(registrationsByEventId);
                    loadWaitlistCountsThenFinalize();
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
        // If user has a selection/replacement notification for this event, force response via Notifications tab.
        notificationDB.getNotificationsForRecipientAndEvent(deviceId, event.getEventId(),
                notifications -> {
                    boolean hasSelectionNotification = false;
                    if (notifications != null) {
                        for (Notification n : notifications) {
                            if (n == null || n.getType() == null) continue;
                            String type = n.getType();
                            if (Notification.TYPE_SELECTED.equals(type)
                                    || Notification.TYPE_GOT_OFF_WAITLIST.equals(type)) {
                                hasSelectionNotification = true;
                                break;
                            }
                        }
                    }
                    if (hasSelectionNotification) {
                        Toast.makeText(this,
                                "Cannot leave waitlist was selected for enrollment.",
                                Toast.LENGTH_LONG).show();
                        return;
                    }
                    showLeaveWaitlistDialog(event, reg);
                },
                e -> Toast.makeText(this,
                        "Unable to verify selection status. Please try again.",
                        Toast.LENGTH_SHORT).show());
    }

    private void showLeaveWaitlistDialog(Event event, WaitingList reg) {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_leave_waitlist_confirm, null);
        ((TextView) dialogView.findViewById(R.id.tv_message))
                .setText("Are you sure you want to leave the waitlist for " + event.getName() + "?");
        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
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
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
        }
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
