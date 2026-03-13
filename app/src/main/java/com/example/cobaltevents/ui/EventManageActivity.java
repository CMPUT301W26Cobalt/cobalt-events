package com.example.cobaltevents.ui;

import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;

import com.example.cobaltevents.R;
import com.example.cobaltevents.controller.ImageController;
import com.example.cobaltevents.db.EventDB;
import com.example.cobaltevents.db.ProfileDB;
import com.example.cobaltevents.db.WaitingListDB;
import com.example.cobaltevents.model.Event;
import com.example.cobaltevents.model.WaitingList;
import com.example.cobaltevents.ui.adapter.WaitlistEntrantAdapter;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * US 02.02.01 – View Waiting List Entrants
 * Displays and manages entrants for a specific event organized by the current user.
 */
public class EventManageActivity extends AppCompatActivity {

    private EventDB eventDB;
    private WaitingListDB waitingListDB;
    private ProfileDB profileDB;
    private ImageController imageController;
    private WaitlistEntrantAdapter adapter;

    private String eventId;
    private Event currentEvent;
    private List<WaitingList> allEntrants = new ArrayList<>();
    private String currentTab = WaitingList.STATUS_PENDING;

    private TextView tvEventName, tvEventLocation, tvEventDate, tvEventCapacity;
    private TextView tvCountWaitlisted, tvCountInvited, tvCountConfirmed;
    private TextView tabWaitlisted, tabInvited, tabConfirmed, tabDeclined;
    private FrameLayout loadingContainer;
    private ProgressBar progressBar;
    private TextView tvEmpty;
    private ImageView ivEventPoster;
    private TextView btnUpdatePoster;

    private static final SimpleDateFormat DATE_FORMAT =
            new SimpleDateFormat("MMM d, yyyy", Locale.getDefault());

    private final ActivityResultLauncher<String> pickPosterLauncher =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                if (uri != null && currentEvent != null) {
                    imageController.uploadPoster(
                            uri,
                            currentEvent,
                            unused -> runOnUiThread(() -> {
                                Glide.with(this)
                                        .load(currentEvent.getPosterImageUrl())
                                        .centerCrop()
                                        .into(ivEventPoster);
                                Toast.makeText(this, "Poster updated", Toast.LENGTH_SHORT).show();
                            }),
                            e -> runOnUiThread(() ->
                                    Toast.makeText(this, "Failed to update poster", Toast.LENGTH_SHORT).show())
                    );
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_event_manage);

        eventId = getIntent().getStringExtra("eventId");
        eventDB = new EventDB();
        waitingListDB = new WaitingListDB();
        profileDB = new ProfileDB();
        imageController = new ImageController();

        tvEventName = findViewById(R.id.tv_event_name);
        tvEventLocation = findViewById(R.id.tv_event_location);
        tvEventDate = findViewById(R.id.tv_event_date);
        tvEventCapacity = findViewById(R.id.tv_event_capacity);
        tvCountWaitlisted = findViewById(R.id.tv_count_waitlisted);
        tvCountInvited = findViewById(R.id.tv_count_invited);
        tvCountConfirmed = findViewById(R.id.tv_count_confirmed);
        tabWaitlisted = findViewById(R.id.tab_waitlisted);
        tabInvited = findViewById(R.id.tab_invited);
        tabConfirmed = findViewById(R.id.tab_confirmed);
        tabDeclined = findViewById(R.id.tab_declined);
        loadingContainer = findViewById(R.id.loading_container);
        progressBar = findViewById(R.id.progress_bar);
        tvEmpty = findViewById(R.id.tv_empty);
        ivEventPoster = findViewById(R.id.iv_event_poster);
        btnUpdatePoster = findViewById(R.id.btn_update_poster);

        RecyclerView recyclerView = findViewById(R.id.recycler_entrants);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new WaitlistEntrantAdapter();
        recyclerView.setAdapter(adapter);
        recyclerView.setNestedScrollingEnabled(false);

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());

        tabWaitlisted.setOnClickListener(v -> selectTab(WaitingList.STATUS_PENDING));
        tabInvited.setOnClickListener(v -> selectTab(WaitingList.STATUS_SELECTED));
        tabConfirmed.setOnClickListener(v -> selectTab("confirmed"));
        tabDeclined.setOnClickListener(v -> selectTab(WaitingList.STATUS_NOT_SELECTED));

        btnUpdatePoster.setOnClickListener(v -> {
            if (currentEvent == null) {
                Toast.makeText(this, "Event not loaded yet", Toast.LENGTH_SHORT).show();
                return;
            }
            pickPosterLauncher.launch("image/*");
        });

        // TODO US 02.05.02 – Run lottery draw
        findViewById(R.id.btn_run_lottery).setOnClickListener(v ->
                Toast.makeText(this, "TODO: Run lottery draw (US 02.05.02)", Toast.LENGTH_SHORT).show());

        // TODO – Notify all waitlisted
        findViewById(R.id.btn_notify_all).setOnClickListener(v ->
                Toast.makeText(this, "TODO: Notify all waitlisted", Toast.LENGTH_SHORT).show());

        // TODO – Export CSV
        findViewById(R.id.btn_export_csv).setOnClickListener(v ->
                Toast.makeText(this, "TODO: Export attendees CSV", Toast.LENGTH_SHORT).show());

        // TODO US 02.02.03 – Geolocation toggle (reads initial state, saving not yet implemented)
        Switch switchGeo = findViewById(R.id.switch_geolocation);
        switchGeo.setOnCheckedChangeListener((btn, checked) ->
                Toast.makeText(this, "TODO: Save geolocation setting (US 02.02.03)", Toast.LENGTH_SHORT).show());

        // TODO US 02.02.02 – Map tab
        findViewById(R.id.tab_map).setOnClickListener(v ->
                Toast.makeText(this, "TODO: View entrants on map (US 02.02.02)", Toast.LENGTH_SHORT).show());

        setTabActive(tabWaitlisted);
        loadEventData();
    }

    private void loadEventData() {
        if (eventId == null) {
            finish();
            return;
        }
        loadingContainer.setVisibility(View.VISIBLE);
        progressBar.setVisibility(View.VISIBLE);
        tvEmpty.setVisibility(View.GONE);

        eventDB.getEvent(eventId, event -> {
            if (event == null) {
                Toast.makeText(this, "Event not found", Toast.LENGTH_SHORT).show();
                finish();
                return;
            }
            currentEvent = event;
            populateEventInfo(event);

            Switch switchGeo = findViewById(R.id.switch_geolocation);
            switchGeo.setChecked(event.isGeolocationRequired());

            loadEntrants();
        }, e -> {
            loadingContainer.setVisibility(View.GONE);
            Toast.makeText(this, "Failed to load event", Toast.LENGTH_SHORT).show();
        });
    }

    private void populateEventInfo(Event event) {
        if (event.getPosterImageUrl() != null && !event.getPosterImageUrl().isEmpty()) {
            Glide.with(this).load(event.getPosterImageUrl()).centerCrop().into(ivEventPoster);
        }
        tvEventName.setText(event.getName() != null ? event.getName() : "Untitled");
        tvEventLocation.setText(event.getLocation() != null ? event.getLocation() : "No location");
        tvEventDate.setText(event.getEventDate() != null
                ? DATE_FORMAT.format(event.getEventDate().toDate()) : "Date TBD");
        int cap = event.getWaitingListCapacity();
        tvEventCapacity.setText("Capacity: " + (cap > 0 ? String.valueOf(cap) : "Unlimited"));
    }

    private void loadEntrants() {
        waitingListDB.getEntrantsForEvent(eventId, registrations -> {
            allEntrants = registrations;
            updateCounts();
            if (registrations.isEmpty()) {
                loadingContainer.setVisibility(View.GONE);
                showFilteredEntrants();
                return;
            }
            loadProfileNames(registrations);
        }, e -> {
            loadingContainer.setVisibility(View.GONE);
            Toast.makeText(this, "Failed to load entrants: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        });
    }

    private void loadProfileNames(List<WaitingList> registrations) {
        Map<String, String> names = new HashMap<>();
        AtomicInteger pending = new AtomicInteger(registrations.size());

        for (WaitingList reg : registrations) {
            profileDB.getProfile(reg.getDeviceId(), profile -> {
                String name = (profile != null && profile.getName() != null && !profile.getName().isEmpty())
                        ? profile.getName() : reg.getDeviceId();
                names.put(reg.getDeviceId(), name);
                if (pending.decrementAndGet() == 0) {
                    adapter.setNames(names);
                    loadingContainer.setVisibility(View.GONE);
                    showFilteredEntrants();
                }
            }, e -> {
                names.put(reg.getDeviceId(), reg.getDeviceId());
                if (pending.decrementAndGet() == 0) {
                    adapter.setNames(names);
                    loadingContainer.setVisibility(View.GONE);
                    showFilteredEntrants();
                }
            });
        }
    }

    private void updateCounts() {
        int waitlisted = 0, invited = 0, confirmed = 0, declined = 0;
        for (WaitingList reg : allEntrants) {
            String s = reg.getStatus();
            if (WaitingList.STATUS_PENDING.equals(s)) waitlisted++;
            else if (WaitingList.STATUS_SELECTED.equals(s)) invited++;
            else if ("confirmed".equals(s)) confirmed++;
            else if (WaitingList.STATUS_NOT_SELECTED.equals(s)
                    || WaitingList.STATUS_CANCELLED.equals(s)) declined++;
        }
        tvCountWaitlisted.setText(String.valueOf(waitlisted));
        tvCountInvited.setText(String.valueOf(invited));
        tvCountConfirmed.setText(String.valueOf(confirmed));

        tabWaitlisted.setText("Waitlisted (" + waitlisted + ")");
        tabInvited.setText("Invited (" + invited + ")");
        tabConfirmed.setText("Confirmed (" + confirmed + ")");
        tabDeclined.setText("Declined (" + declined + ")");
    }

    private void selectTab(String status) {
        currentTab = status;
        switch (status) {
            case WaitingList.STATUS_PENDING:
                setTabActive(tabWaitlisted); break;
            case WaitingList.STATUS_SELECTED:
                setTabActive(tabInvited); break;
            case "confirmed":
                setTabActive(tabConfirmed); break;
            default:
                setTabActive(tabDeclined); break;
        }
        showFilteredEntrants();
    }

    private void showFilteredEntrants() {
        List<WaitingList> filtered = new ArrayList<>();
        for (WaitingList reg : allEntrants) {
            String s = reg.getStatus();
            boolean include;
            switch (currentTab) {
                case WaitingList.STATUS_PENDING:
                    include = WaitingList.STATUS_PENDING.equals(s); break;
                case WaitingList.STATUS_SELECTED:
                    include = WaitingList.STATUS_SELECTED.equals(s); break;
                case "confirmed":
                    include = "confirmed".equals(s); break;
                default:
                    include = WaitingList.STATUS_NOT_SELECTED.equals(s)
                            || WaitingList.STATUS_CANCELLED.equals(s); break;
            }
            if (include) filtered.add(reg);
        }
        adapter.setItems(filtered);
        if (filtered.isEmpty()) {
            loadingContainer.setVisibility(View.VISIBLE);
            progressBar.setVisibility(View.GONE);
            tvEmpty.setVisibility(View.VISIBLE);
        } else {
            loadingContainer.setVisibility(View.GONE);
            tvEmpty.setVisibility(View.GONE);
        }
    }

    private void setTabActive(TextView activeTab) {
        int blue = getResources().getColor(R.color.organizer_blue);
        int grey = getResources().getColor(R.color.grey_nav_inactive);
        for (TextView tab : new TextView[]{tabWaitlisted, tabInvited, tabConfirmed, tabDeclined}) {
            tab.setTextColor(tab == activeTab ? blue : grey);
        }
    }
}
