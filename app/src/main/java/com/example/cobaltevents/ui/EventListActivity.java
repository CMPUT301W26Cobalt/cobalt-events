package com.example.cobaltevents.ui;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
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
import com.example.cobaltevents.ui.waitlist.RegistrationPeriodUi;
import com.example.cobaltevents.ui.waitlist.WaitlistStatusUi;
import com.example.cobaltevents.util.EventGoneUi;
import com.example.cobaltevents.util.NetworkConnectivity;
import com.google.firebase.Timestamp;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class EventListActivity extends AppCompatActivity {

    private static final String[] PRICE_RANGE_OPTIONS = {
            "All", "Free", "Under $10", "$10-$25", "$25-$50", "$50+"
    };
    private static final String[] CAPACITY_OPTIONS = {
            "All", "Spots Available", "Full", "Unlimited"
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
    /** Shown only while verifying location for geolocked waitlist join. */
    private AlertDialog geoJoinLoadingDialog;
    private Event pendingGeoJoinEvent;
    private final ActivityResultLauncher<String> locationPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) {
                    geolocationController.fetchLocationOnStartup(this);
                    if (pendingGeoJoinEvent != null) {
                        final String pendingEventId = pendingGeoJoinEvent.getEventId();
                        pendingGeoJoinEvent = null;
                        eventController.getEventFromServer(pendingEventId, fresh -> runOnUiThread(() -> {
                            if (fresh == null) {
                                Toast.makeText(this, "Could not load event.", Toast.LENGTH_SHORT).show();
                                return;
                            }
                            mergeServerEventIntoAllEvents(fresh);
                            applyFilters();
                            if (fresh.isPrivate() && !adapter.isEffectivelyJoinedOnWaitlist(fresh)
                                    && !(deviceId != null && fresh.isDeviceAnOrganizer(deviceId))) {
                                Toast.makeText(this, R.string.event_switched_to_private, Toast.LENGTH_LONG).show();
                                refreshEventRowUi(fresh.getEventId());
                                return;
                            }
                            if (!RegistrationPeriodUi.isNowWithinRegistrationWindow(fresh)) {
                                Toast.makeText(this, R.string.waitlist_registration_period_altered, Toast.LENGTH_LONG).show();
                                refreshEventRowUi(fresh.getEventId());
                                return;
                            }
                            if (deviceId != null && fresh.isDeviceAnOrganizer(deviceId)) {
                                Toast.makeText(this, R.string.waitlist_organizer_cannot_join, Toast.LENGTH_LONG).show();
                                refreshEventRowUi(fresh.getEventId());
                                return;
                            }
                            checkCapacityThenProceedJoin(fresh, () -> joinAndRecordLocation(fresh));
                        }), e -> runOnUiThread(() ->
                                Toast.makeText(this, "Could not load event.", Toast.LENGTH_SHORT).show()));
                    }
                } else {
                    Toast.makeText(this, "Location permission denied — cannot join this event.", Toast.LENGTH_LONG).show();
                    pendingGeoJoinEvent = null;
                }
            });
    private final Map<String, WaitingList> activeRegistrationsByEventId = new HashMap<>();
    private final Map<String, WaitingList> registrationsByEventId = new HashMap<>();
    private final Map<String, Integer> waitlistCountByEventId = new HashMap<>();
    /** Timestamp (millis) of the last successful full load; used to debounce onResume reloads. */
    private long lastFullLoadTimestamp = 0;
    /** Skip redundant reload only when the last full load just finished (keeps resume snappy). */
    private static final long RESUME_DEBOUNCE_MS = 2_000;

    /** Per {@link #loadEvents()} session — invalidates stale waitlist prefetch callbacks. */
    private int loadEventsSessionId = 0;
    /** True while Firestore count queries from catalog prefetch are in flight (same session as {@link #loadEventsSessionId}). */
    private volatile boolean waitlistPrefetchInFlight = false;
    private volatile boolean waitlistPrefetchCompleteForSession = false;
    private int waitlistPrefetchSessionId = -1;
    private Runnable finalizeAfterWaitlistPrefetch;
    /** Bumped on each {@link #loadEvents()} and when non-bootstrap waitlist refresh invalidates prefetch. */
    private int prefetchEpoch = 0;

    private List<Event> allEvents = new ArrayList<>();
    private String currentQuery = "";
    private String currentKeywordInput = "";
    private int selectedPriceRangeIndex = 0;
    private int selectedAgeGroupIndex = 0;
    private int selectedCapacityIndex = 0;
    private Long availabilityStartMillis = null;
    private Long availabilityEndMillis = null;
    private final List<String> selectedKeywords = new ArrayList<>();
    /** Same order as `R.array.event_age_group_options` (filter + create event). */
    private String[] ageGroupOptions;
    private EditText etKeywords;
    private HorizontalScrollView scrollKeywordChips;
    private LinearLayout layoutKeywordChips;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_event_list);
        ageGroupOptions = getResources().getStringArray(R.array.event_age_group_options);

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
        adapter.setCommentAuthorName(resolveCommentAuthorName());
        adapter.setOnEventCommentsChangedListener(eventId -> {
            int pos = adapter.findPositionByEventId(eventId);
            if (pos >= 0) {
                adapter.notifyItemChanged(pos);
            }
        });
        adapter.setOnEventDeletedListener(this::removeStaleEventFromBrowse);
        adapter.setOnPrivateCommentDeniedRefresh(fresh -> runOnUiThread(() -> {
            if (fresh == null) {
                return;
            }
            mergeServerEventIntoAllEvents(fresh);
            applyFilters();
            Toast.makeText(this, R.string.event_switched_to_private, Toast.LENGTH_LONG).show();
            if (fresh.getEventId() != null) {
                refreshEventRowUi(fresh.getEventId());
            }
        }));

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setHasFixedSize(true);
        recyclerView.setItemViewCacheSize(12);
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
            swipeRefreshLayout.setOnRefreshListener(() -> loadEvents(true));
            swipeRefreshLayout.setColorSchemeColors(ContextCompat.getColor(this, R.color.user_green));
        }
        loadEvents(false);
        setupBottomNavigation();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (adapter != null) {
            adapter.setCommentAuthorName(resolveCommentAuthorName());
        }
        if (System.currentTimeMillis() - lastFullLoadTimestamp < RESUME_DEBOUNCE_MS) {
            return;
        }
        loadEvents(false);
    }

    /**
     * Reloads catalog, merges registrations/notifications, then waitlist counts.
     *
     * @param fromPullToRefresh when true, keep the list visible and show {@link SwipeRefreshLayout} progress
     *                          until {@link #applyWaitlistCountsUiComplete()} (do not cancel the swipe spinner early).
     */
    private void loadEvents(boolean fromPullToRefresh) {
        eventsLoading = true;
        loadEventsSessionId++;
        prefetchEpoch++;
        final int sessionId = loadEventsSessionId;
        waitlistPrefetchInFlight = false;
        waitlistPrefetchCompleteForSession = false;
        waitlistPrefetchSessionId = -1;
        finalizeAfterWaitlistPrefetch = null;
        waitlistCountByEventId.clear();

        if (fromPullToRefresh) {
            progressBar.setVisibility(View.GONE);
            if (recyclerView != null) recyclerView.setVisibility(View.VISIBLE);
            if (tvEmpty != null) tvEmpty.setVisibility(View.GONE);
        } else {
            progressBar.setVisibility(View.VISIBLE);
            if (recyclerView != null) recyclerView.setVisibility(View.INVISIBLE);
            if (tvEmpty != null) tvEmpty.setVisibility(View.GONE);
            if (swipeRefreshLayout != null) swipeRefreshLayout.setRefreshing(false);
        }

        // Overlap: events catalog + entrant history + notifications (same merge as before; wall-clock ~max of the three).
        // Waitlist count() queries also start as soon as the catalog returns, overlapping with history + notifications.
        final int bootstrapParts = (deviceId != null && !deviceId.isEmpty()) ? 3 : 1;
        final java.util.concurrent.atomic.AtomicInteger bootstrapRemaining = new java.util.concurrent.atomic.AtomicInteger(bootstrapParts);
        final boolean[] eventsLoadOk = { true };
        final List<Event>[] eventsHolder = new List[1];
        final List<WaitingList>[] historyHolder = new List[1];
        final List<Notification>[] notificationsHolder = new List[1];
        final boolean[] historyFailed = { false };
        final boolean[] notificationsFailed = { false };

        Runnable bootstrapPartDone = () -> {
            if (bootstrapRemaining.decrementAndGet() != 0) return;
            runOnUiThread(() -> completeParallelEventsBootstrap(
                    eventsLoadOk[0],
                    eventsHolder[0],
                    historyHolder[0],
                    historyFailed[0],
                    notificationsHolder[0],
                    notificationsFailed[0]));
        };

        eventController.getAllEvents(events -> {
            eventsHolder[0] = events != null ? events : new ArrayList<>();
            if (eventsLoadOk[0]) {
                beginWaitlistCountPrefetchForSession(eventsHolder[0], sessionId);
            }
            bootstrapPartDone.run();
        }, e -> {
            if (swipeRefreshLayout != null) swipeRefreshLayout.setRefreshing(false);
            eventsLoadOk[0] = false;
            progressBar.setVisibility(View.GONE);
            eventsLoading = false;
            if (recyclerView != null) recyclerView.setVisibility(View.VISIBLE);
            Toast.makeText(this, "Failed to load events: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            bootstrapPartDone.run();
        }, () -> runOnUiThread(() ->
                Toast.makeText(this, R.string.firebase_cache_fallback_message, Toast.LENGTH_LONG).show()));

        if (deviceId != null && !deviceId.isEmpty()) {
            waitingListDB.getEntrantHistory(deviceId,
                    list -> {
                        historyHolder[0] = list;
                        bootstrapPartDone.run();
                    },
                    err -> {
                        historyFailed[0] = true;
                        bootstrapPartDone.run();
                    });

            notificationDB.getNotificationsForRecipient(deviceId,
                    list -> {
                        notificationsHolder[0] = list;
                        bootstrapPartDone.run();
                    },
                    err -> {
                        notificationsFailed[0] = true;
                        bootstrapPartDone.run();
                    });
        }
    }

    private void completeParallelEventsBootstrap(boolean eventsLoadOk,
                                                 List<Event> events,
                                                 List<WaitingList> history,
                                                 boolean historyFailed,
                                                 List<Notification> notifications,
                                                 boolean notificationsFailed) {
        if (!eventsLoadOk) {
            return;
        }
        allEvents = events != null ? events : new ArrayList<>();
        mergeRegistrationsNotificationsAndFinalizeUi(history, historyFailed, notifications, notificationsFailed, true);
    }

    /**
     * Refresh registrations + notification-derived status, then waitlist counts (after join/leave, etc.).
     * Fetches entrant history + notifications (unlike loadEvents, which may pass pre-fetched lists).
     */
    private void loadActiveRegistrationsThenApplyFilters() {
        if (allEvents == null || allEvents.isEmpty()) {
            activeRegistrationsByEventId.clear();
            registrationsByEventId.clear();
            adapter.setActiveRegistrationsByEventId(activeRegistrationsByEventId);
            adapter.setRegistrationsByEventId(registrationsByEventId);
            adapter.setEffectiveStatusByEventId(new HashMap<>());
            loadWaitlistCountsThenFinalize(false);
            return;
        }

        if (deviceId == null || deviceId.isEmpty()) {
            activeRegistrationsByEventId.clear();
            registrationsByEventId.clear();
            adapter.setActiveRegistrationsByEventId(activeRegistrationsByEventId);
            adapter.setRegistrationsByEventId(registrationsByEventId);
            adapter.setEffectiveStatusByEventId(new HashMap<>());
            loadWaitlistCountsThenFinalize(false);
            return;
        }

        final java.util.concurrent.atomic.AtomicInteger completed = new java.util.concurrent.atomic.AtomicInteger(0);
        final List<WaitingList>[] historyHolder = new List[1];
        final List<Notification>[] notificationsHolder = new List[1];
        final boolean[] historyFailed = { false };
        final boolean[] notificationsFailed = { false };

        Runnable mergeWhenBothDone = () -> {
            if (completed.incrementAndGet() != 2) return;
            runOnUiThread(() -> mergeRegistrationsNotificationsAndFinalizeUi(
                    historyHolder[0],
                    historyFailed[0],
                    notificationsHolder[0],
                    notificationsFailed[0],
                    false));
        };

        waitingListDB.getEntrantHistory(deviceId,
                list -> {
                    historyHolder[0] = list;
                    mergeWhenBothDone.run();
                },
                e -> {
                    historyFailed[0] = true;
                    loadRegistrationsPerEventForMerge(mergeWhenBothDone);
                });

        notificationDB.getNotificationsForRecipient(deviceId,
                list -> {
                    notificationsHolder[0] = list;
                    mergeWhenBothDone.run();
                },
                e -> {
                    notificationsFailed[0] = true;
                    mergeWhenBothDone.run();
                });
    }

    private void applyEffectiveStatusAdaptersAndWaitlistCounts(List<Notification> notifications,
                                                               boolean notificationsFailed,
                                                               boolean mayUseBootstrapPrefetch) {
        Map<String, String> effective = new HashMap<>();
        if (!notificationsFailed && notifications != null) {
            effective = computeEffectiveStatusByEventId(notifications);
        }
        adapter.setActiveRegistrationsByEventId(activeRegistrationsByEventId);
        adapter.setRegistrationsByEventId(registrationsByEventId);
        adapter.setEffectiveStatusByEventId(effective);
        loadWaitlistCountsThenFinalize(mayUseBootstrapPrefetch);
    }

    /**
     * Same UI outcome as the pre-overlap flow: build reg maps, merge notification overrides, then counts + filters.
     */
    private void mergeRegistrationsNotificationsAndFinalizeUi(List<WaitingList> history,
                                                              boolean historyFailed,
                                                              List<Notification> notifications,
                                                              boolean notificationsFailed,
                                                              boolean mayUseBootstrapPrefetch) {
        activeRegistrationsByEventId.clear();
        registrationsByEventId.clear();

        if (allEvents == null || allEvents.isEmpty()) {
            adapter.setActiveRegistrationsByEventId(activeRegistrationsByEventId);
            adapter.setRegistrationsByEventId(registrationsByEventId);
            adapter.setEffectiveStatusByEventId(new HashMap<>());
            loadWaitlistCountsThenFinalize(mayUseBootstrapPrefetch);
            return;
        }

        if (deviceId == null || deviceId.isEmpty()) {
            adapter.setActiveRegistrationsByEventId(activeRegistrationsByEventId);
            adapter.setRegistrationsByEventId(registrationsByEventId);
            adapter.setEffectiveStatusByEventId(new HashMap<>());
            loadWaitlistCountsThenFinalize(mayUseBootstrapPrefetch);
            return;
        }

        if (!historyFailed) {
            fillRegistrationMapsFromEntrantHistory(history, false);
            applyEffectiveStatusAdaptersAndWaitlistCounts(notifications, notificationsFailed, mayUseBootstrapPrefetch);
        } else {
            loadRegistrationsPerEventForMerge(() -> runOnUiThread(() ->
                    applyEffectiveStatusAdaptersAndWaitlistCounts(notifications, notificationsFailed, mayUseBootstrapPrefetch)));
        }
    }

    /**
     * Legacy path: same as pre-optimization behavior when getEntrantHistory fails.
     */
    private void loadRegistrationsPerEventForMerge(Runnable whenAllRegistrationsLoaded) {
        if (allEvents == null || allEvents.isEmpty()) {
            runOnUiThread(whenAllRegistrationsLoaded);
            return;
        }
        runOnUiThread(() -> Toast.makeText(this, R.string.registration_history_fallback_message,
                Toast.LENGTH_LONG).show());
        final java.util.concurrent.atomic.AtomicInteger pending = new java.util.concurrent.atomic.AtomicInteger(allEvents.size());
        for (Event event : allEvents) {
            if (event == null || event.getEventId() == null) {
                runOnUiThread(() -> {
                    if (pending.decrementAndGet() == 0) whenAllRegistrationsLoaded.run();
                });
                continue;
            }
            final String eventId = event.getEventId();
            waitingListDB.getRegistrationForEventAnyStatus(eventId, deviceId,
                    reg -> runOnUiThread(() -> {
                        if (reg != null) {
                            registrationsByEventId.put(eventId, reg);
                            if (isActiveStatus(reg.getStatus())) {
                                activeRegistrationsByEventId.put(eventId, reg);
                            }
                        }
                        if (pending.decrementAndGet() == 0) whenAllRegistrationsLoaded.run();
                    }),
                    err -> runOnUiThread(() -> {
                        if (pending.decrementAndGet() == 0) whenAllRegistrationsLoaded.run();
                    }));
        }
    }

    private void fillRegistrationMapsFromEntrantHistory(List<WaitingList> history, boolean historyFailed) {
        activeRegistrationsByEventId.clear();
        registrationsByEventId.clear();
        if (historyFailed || history == null || allEvents == null) {
            return;
        }
        Map<String, WaitingList> byEventId = new HashMap<>();
        for (WaitingList w : history) {
            if (w == null || w.getEventId() == null) continue;
            byEventId.put(w.getEventId(), w);
        }
        Set<String> knownEventIds = new HashSet<>();
        for (Event event : allEvents) {
            if (event == null || event.getEventId() == null) continue;
            knownEventIds.add(event.getEventId());
        }
        for (String eventId : knownEventIds) {
            WaitingList reg = byEventId.get(eventId);
            if (reg == null) continue;
            registrationsByEventId.put(eventId, reg);
            if (isActiveStatus(reg.getStatus())) {
                activeRegistrationsByEventId.put(eventId, reg);
            }
        }
    }

    private java.util.Map<String, String> computeEffectiveStatusByEventId(List<Notification> notifications) {
        return WaitlistStatusUi.effectiveStatusByEventIdFromNotifications(notifications);
    }

    private boolean isActiveStatus(String status) {
        return WaitingList.STATUS_PENDING.equals(status)
                || WaitingList.STATUS_SELECTED.equals(status)
                || WaitingList.STATUS_ENROLLED.equals(status)
                // X / "not-selected" should still count as being in the waitlist.
                || WaitingList.STATUS_NOT_SELECTED.equals(status);
    }

    /**
     * Starts waitlist {@code count()} queries as soon as the catalog returns, overlapping with
     * entrant history + notifications so the list can finalize sooner after merge.
     */
    private void beginWaitlistCountPrefetchForSession(List<Event> events, int sessionId) {
        if (sessionId != loadEventsSessionId) {
            return;
        }
        final int epochAtPrefetchStart = prefetchEpoch;
        java.util.List<String> eventIds = new java.util.ArrayList<>();
        if (events != null) {
            for (Event e : events) {
                if (e == null || e.getEventId() == null) continue;
                eventIds.add(e.getEventId());
            }
        }
        if (eventIds.isEmpty()) {
            waitlistPrefetchInFlight = false;
            waitlistPrefetchCompleteForSession = true;
            waitlistPrefetchSessionId = sessionId;
            return;
        }
        waitlistPrefetchInFlight = true;
        waitlistPrefetchCompleteForSession = false;
        waitlistPrefetchSessionId = sessionId;
        runWaitlistCountQueryPool(eventIds, sessionId, epochAtPrefetchStart, () -> runOnUiThread(() -> {
            if (sessionId != loadEventsSessionId || epochAtPrefetchStart != prefetchEpoch) {
                return;
            }
            waitlistPrefetchInFlight = false;
            waitlistPrefetchCompleteForSession = true;
            Runnable r = finalizeAfterWaitlistPrefetch;
            finalizeAfterWaitlistPrefetch = null;
            if (r != null) {
                r.run();
            }
        }));
    }

    private void applyWaitlistCountsUiComplete() {
        adapter.setWaitlistCountByEventId(waitlistCountByEventId);
        eventsLoading = false;
        lastFullLoadTimestamp = System.currentTimeMillis();
        applyFilters();
        progressBar.setVisibility(View.GONE);
        if (recyclerView != null) recyclerView.setVisibility(View.VISIBLE);
        if (swipeRefreshLayout != null) swipeRefreshLayout.setRefreshing(false);
    }

    /**
     * Parallel Firestore aggregate count queries for each event id. Callback runs on the UI thread.
     *
     * @param prefetchEpochGuard pass {@code -1} to skip epoch checks (full refresh after join/leave);
     *                           otherwise only writes when {@code prefetchEpochGuard == prefetchEpoch}.
     */
    private void runWaitlistCountQueryPool(List<String> eventIds, int sessionId, int prefetchEpochGuard,
                                         Runnable onCompleteOnUiThread) {
        if (eventIds == null || eventIds.isEmpty()) {
            runOnUiThread(onCompleteOnUiThread);
            return;
        }
        // Fetch waitlist counts with limited parallelism to avoid Firestore throttling/timeouts.
        // Count() aggregation is lightweight (no doc downloads), so higher concurrency is safe.
        final int maxConcurrent = 16;
        java.util.concurrent.atomic.AtomicInteger nextIndex = new java.util.concurrent.atomic.AtomicInteger(0);
        java.util.concurrent.atomic.AtomicInteger inFlight = new java.util.concurrent.atomic.AtomicInteger(0);
        java.util.concurrent.atomic.AtomicInteger remaining = new java.util.concurrent.atomic.AtomicInteger(eventIds.size());
        java.util.concurrent.atomic.AtomicBoolean finalized = new java.util.concurrent.atomic.AtomicBoolean(false);

        final Runnable[] startMoreRef = new Runnable[1];
        startMoreRef[0] = new Runnable() {
            @Override
            public void run() {
                while (!finalized.get()
                        && inFlight.get() < maxConcurrent
                        && nextIndex.get() < eventIds.size()) {
                    final int idx = nextIndex.getAndIncrement();
                    if (idx >= eventIds.size()) break;

                    final String eventId = eventIds.get(idx);
                    inFlight.incrementAndGet();
                    waitingListDB.getActiveCountForEvent(eventId,
                            count -> {
                                boolean epochOk = prefetchEpochGuard < 0 || prefetchEpochGuard == prefetchEpoch;
                                if (sessionId == loadEventsSessionId && epochOk) {
                                    waitlistCountByEventId.put(eventId, count);
                                }
                                inFlight.decrementAndGet();
                                if (remaining.decrementAndGet() == 0
                                        && finalized.compareAndSet(false, true)) {
                                    runOnUiThread(onCompleteOnUiThread);
                                } else {
                                    if (startMoreRef[0] != null) runOnUiThread(startMoreRef[0]);
                                }
                            },
                            err -> {
                                inFlight.decrementAndGet();
                                if (remaining.decrementAndGet() == 0
                                        && finalized.compareAndSet(false, true)) {
                                    runOnUiThread(onCompleteOnUiThread);
                                } else {
                                    if (startMoreRef[0] != null) runOnUiThread(startMoreRef[0]);
                                }
                            });
                }
            }
        };

        if (startMoreRef[0] != null) runOnUiThread(startMoreRef[0]);
    }

    /**
     * Wait until all events have waitlist counts + notification effective status before showing the list.
     *
     * @param mayUseBootstrapPrefetch when true (initial {@link #loadEvents()} merge), reuse prefetch if ready.
     */
    private void loadWaitlistCountsThenFinalize(boolean mayUseBootstrapPrefetch) {
        if (!mayUseBootstrapPrefetch) {
            prefetchEpoch++;
            waitlistPrefetchInFlight = false;
            waitlistPrefetchCompleteForSession = false;
            waitlistPrefetchSessionId = -1;
            finalizeAfterWaitlistPrefetch = null;
        }

        java.util.List<String> eventIds = new java.util.ArrayList<>();
        for (Event e : allEvents) {
            if (e == null || e.getEventId() == null) continue;
            eventIds.add(e.getEventId());
        }

        if (eventIds.isEmpty()) {
            waitlistCountByEventId.clear();
            adapter.setWaitlistCountByEventId(waitlistCountByEventId);
            eventsLoading = false;
            lastFullLoadTimestamp = System.currentTimeMillis();
            applyFilters();
            progressBar.setVisibility(View.GONE);
            if (recyclerView != null) recyclerView.setVisibility(View.VISIBLE);
            if (swipeRefreshLayout != null) swipeRefreshLayout.setRefreshing(false);
            return;
        }

        if (mayUseBootstrapPrefetch && waitlistPrefetchCompleteForSession
                && waitlistPrefetchSessionId == loadEventsSessionId) {
            applyWaitlistCountsUiComplete();
            return;
        }
        if (mayUseBootstrapPrefetch && waitlistPrefetchInFlight
                && waitlistPrefetchSessionId == loadEventsSessionId) {
            finalizeAfterWaitlistPrefetch = this::applyWaitlistCountsUiComplete;
            return;
        }

        waitlistCountByEventId.clear();
        runWaitlistCountQueryPool(eventIds, loadEventsSessionId, -1, this::applyWaitlistCountsUiComplete);
    }

    /**
     * After join/leave for one event, refresh only that event's active waitlist count (not the full list).
     */
    private void refreshWaitlistCountForEventId(String eventId) {
        if (eventId == null || eventId.isEmpty()) {
            return;
        }
        waitingListDB.getActiveCountForEvent(eventId,
                count -> runOnUiThread(() -> {
                    waitlistCountByEventId.put(eventId, count);
                    adapter.setWaitlistCountByEventId(waitlistCountByEventId);
                    applyFilters();
                }),
                e -> runOnUiThread(() ->
                        Toast.makeText(this, "Could not refresh waitlist count.", Toast.LENGTH_SHORT).show()));
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
            if (selectedPriceRangeIndex > 0) {
                if (!matchesPriceRangeFilter(event, selectedPriceRangeIndex)) continue;
            }
            if (selectedAgeGroupIndex > 0) {
                if (!matchesAgeGroupFilter(event, selectedAgeGroupIndex)) continue;
            }
            if (!matchesAvailabilityDateRangeFilter(event)) {
                continue;
            }
            if (selectedCapacityIndex > 0) {
                if (!matchesCapacityFilter(event, selectedCapacityIndex)) continue;
            }
            // Private events: visible only on waitlist or to organizers (organizers cannot join from browse).
            if (event.isPrivate() && !adapter.isEffectivelyJoinedOnWaitlist(event)) {
                if (deviceId == null || !event.isDeviceAnOrganizer(deviceId)) {
                    continue;
                }
            }
            if (!selectedKeywords.isEmpty()) {
                if (!matchesKeywordFilters(event)) continue;
            }

            filtered.add(event);
        }

        adapter.updateEvents(filtered);
        tvEmpty.setVisibility(filtered.isEmpty() ? View.VISIBLE : View.GONE);
    }

    /**
     * Age filter uses `event_age_group_options`. Index 0 is "All" (no filter).
     * Other indices match {@link Event#getAgeGroup()} (case-insensitive).
     */
    private boolean matchesAgeGroupFilter(Event event, int selectedIndex) {
        if (ageGroupOptions == null || selectedIndex <= 0 || selectedIndex >= ageGroupOptions.length) {
            return true;
        }
        String selected = ageGroupOptions[selectedIndex];
        String eventAge = event.getAgeGroup();
        return eventAge != null && selected.equalsIgnoreCase(eventAge.trim());
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
        Spinner spinnerCapacity = dialogView.findViewById(R.id.spinner_capacity);
        TextView btnAvailabilityStart = dialogView.findViewById(R.id.btn_availability_start);
        TextView btnAvailabilityEnd = dialogView.findViewById(R.id.btn_availability_end);

        ArrayAdapter<String> priceRangeAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, PRICE_RANGE_OPTIONS);
        priceRangeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerPriceRange.setAdapter(priceRangeAdapter);
        spinnerPriceRange.setSelection(selectedPriceRangeIndex);

        ArrayAdapter<String> ageGroupAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, ageGroupOptions);
        ageGroupAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerAgeGroup.setAdapter(ageGroupAdapter);
        spinnerAgeGroup.setSelection(selectedAgeGroupIndex);

        ArrayAdapter<String> capacityAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, CAPACITY_OPTIONS);
        capacityAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerCapacity.setAdapter(capacityAdapter);
        spinnerCapacity.setSelection(selectedCapacityIndex);

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
            selectedCapacityIndex = spinnerCapacity.getSelectedItemPosition();
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
            selectedCapacityIndex = 0;
            availabilityStartMillis = null;
            availabilityEndMillis = null;
            spinnerPriceRange.setSelection(0);
            spinnerAgeGroup.setSelection(0);
            spinnerCapacity.setSelection(0);
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
        // Avoid default highlight / extra font padding that looks like a grey box under the ×
        removeText.setBackgroundResource(android.R.color.transparent);
        removeText.setIncludeFontPadding(false);
        removeText.setMinHeight(0);
        removeText.setMinWidth(0);
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

    private String resolveCommentAuthorName() {
        Entrant e = entrantDB.getEntrant();
        if (e != null && e.getName() != null && !e.getName().trim().isEmpty()) {
            return e.getName().trim();
        }
        return "You";
    }

    private void mergeServerEventIntoAllEvents(Event fresh) {
        if (fresh == null || fresh.getEventId() == null) {
            return;
        }
        for (int i = 0; i < allEvents.size(); i++) {
            Event e = allEvents.get(i);
            if (e != null && fresh.getEventId().equals(e.getEventId())) {
                allEvents.set(i, fresh);
                return;
            }
        }
    }

    private void joinWaitlist(Event event) {
        if (event == null || event.getEventId() == null) {
            Toast.makeText(this, "Invalid event", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!NetworkConnectivity.hasValidatedInternet(this)) {
            Toast.makeText(this, getString(R.string.waitlist_fail) + " No internet connection.", Toast.LENGTH_SHORT).show();
            return;
        }
        eventController.getEventFromServer(event.getEventId(), fresh -> runOnUiThread(() -> {
            if (fresh == null) {
                EventGoneUi.toast(this);
                removeStaleEventFromBrowse(event.getEventId());
                return;
            }
            mergeServerEventIntoAllEvents(fresh);
            applyFilters();
            proceedJoinAfterFreshEvent(fresh);
        }), e -> runOnUiThread(() ->
                Toast.makeText(this, "Could not load event.", Toast.LENGTH_SHORT).show()));
    }

    private void proceedJoinAfterFreshEvent(Event event) {
        Entrant entrant = entrantDB.getEntrant();
        if (!entrant.isValidName() || !entrant.isValidEmail()) {
            Toast.makeText(this, "Please complete your name and email in Account settings before joining a waitlist.", Toast.LENGTH_LONG).show();
            startActivity(new Intent(this, AccountSettingsActivity.class));
            return;
        }
        if (!RegistrationPeriodUi.isNowWithinRegistrationWindow(event)) {
            Toast.makeText(this, R.string.waitlist_registration_period_altered, Toast.LENGTH_LONG).show();
            refreshEventRowUi(event.getEventId());
            return;
        }
        if (deviceId != null && event.isDeviceAnOrganizer(deviceId)) {
            Toast.makeText(this, R.string.waitlist_organizer_cannot_join, Toast.LENGTH_LONG).show();
            refreshEventRowUi(event.getEventId());
            return;
        }
        if (event.isPrivate() && !adapter.isEffectivelyJoinedOnWaitlist(event)) {
            Toast.makeText(this, R.string.event_switched_to_private, Toast.LENGTH_LONG).show();
            refreshEventRowUi(event.getEventId());
            return;
        }
        checkCapacityThenProceedJoin(event, () -> {
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
            } else {
                performJoinWaitlist(event);
            }
        });
    }

    /**
     * Server-side active count vs capacity (organizer may have lowered capacity after the list was shown).
     */
    private void checkCapacityThenProceedJoin(Event event, Runnable proceed) {
        if (event == null || event.getEventId() == null) {
            return;
        }
        int cap = event.getWaitingListCapacity();
        if (cap <= 0) {
            proceed.run();
            return;
        }
        waitingListDB.getActiveCountForEvent(event.getEventId(),
                count -> runOnUiThread(() -> {
                    if (count >= cap) {
                        Toast.makeText(this, R.string.waitlist_capacity_altered, Toast.LENGTH_LONG).show();
                        refreshEventFromServerAfterWaitlistMutation(event.getEventId());
                    } else {
                        proceed.run();
                    }
                }),
                e -> runOnUiThread(() ->
                        Toast.makeText(this, "Could not verify waitlist capacity.", Toast.LENGTH_SHORT).show()));
    }

    private void joinAndRecordLocation(Event event) {
        showGeoJoinLoadingDialog();
        geolocationController.checkDistanceForEvent(this, event,
                new GeolocationController.GeoJoinCallback() {
                    @Override
                    public void onAllowed(android.location.Location userLocation) {
                        dismissGeoJoinLoadingDialog();
                        // Capacity was checked before geo; server enforces again in addRegistrationWithJoinChecks.
                        performJoinWaitlist(event);
                        geolocationController.recordLocationForEvent(
                                EventListActivity.this, deviceId, event.getEventId(),
                                userLocation, unused -> {}, e -> {});
                    }
                    @Override
                    public void onBlocked(float distanceMeters) {
                        dismissGeoJoinLoadingDialog();
                        int km = Math.round(distanceMeters / 1000f);
                        Toast.makeText(EventListActivity.this,
                                "You are " + km + "km away. Must be within 30km to join.",
                                Toast.LENGTH_LONG).show();
                        refreshEventRowUi(event.getEventId());
                    }
                    @Override
                    public void onError(String message) {
                        dismissGeoJoinLoadingDialog();
                        Toast.makeText(EventListActivity.this, message, Toast.LENGTH_LONG).show();
                        refreshEventRowUi(event.getEventId());
                    }
                });
    }

    private void showGeoJoinLoadingDialog() {
        dismissGeoJoinLoadingDialog();
        View content = LayoutInflater.from(this).inflate(R.layout.dialog_geo_join_loading, null, false);
        geoJoinLoadingDialog = new MaterialAlertDialogBuilder(this)
                .setView(content)
                .setCancelable(false)
                .create();
        geoJoinLoadingDialog.show();
    }

    private void dismissGeoJoinLoadingDialog() {
        if (geoJoinLoadingDialog == null) {
            return;
        }
        try {
            if (geoJoinLoadingDialog.isShowing()) {
                geoJoinLoadingDialog.dismiss();
            }
        } catch (Exception ignored) {
        }
        geoJoinLoadingDialog = null;
    }

    @Override
    protected void onDestroy() {
        dismissGeoJoinLoadingDialog();
        super.onDestroy();
    }

    private void refreshEventRowUi(String eventId) {
        if (eventId == null) {
            return;
        }
        int pos = adapter.findPositionByEventId(eventId);
        if (pos >= 0) {
            adapter.notifyItemChanged(pos);
        }
    }

    /**
     * Re-fetch the event from the server after join/leave (or failed join) so name, poster, categories,
     * description, criteria, registration dates, etc. stay in sync with Firestore.
     */
    private void refreshEventFromServerAfterWaitlistMutation(String eventId) {
        if (eventId == null || eventId.isEmpty()) {
            return;
        }
        eventController.getEventFromServer(eventId, fresh -> runOnUiThread(() -> {
            if (fresh == null) {
                removeStaleEventFromBrowse(eventId);
                return;
            }
            mergeServerEventIntoAllEvents(fresh);
            applyFilters();
            refreshEventRowUi(eventId);
            refreshWaitlistCountForEventId(eventId);
        }), e -> runOnUiThread(() -> {
            refreshEventRowUi(eventId);
            refreshWaitlistCountForEventId(eventId);
        }));
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
        waitingListDB.addRegistrationWithJoinChecks(registration, event.getWaitingListCapacity(),
                event.getRegistrationClose(),
                id -> {
                    Toast.makeText(this, R.string.waitlist_success, Toast.LENGTH_SHORT).show();
                    activeRegistrationsByEventId.put(event.getEventId(), registration);
                    registrationsByEventId.put(event.getEventId(), registration);
                    adapter.setActiveRegistrationsByEventId(activeRegistrationsByEventId);
                    adapter.setRegistrationsByEventId(registrationsByEventId);
                    recordLocationIfPermitted(event.getEventId());
                    refreshEventFromServerAfterWaitlistMutation(event.getEventId());
                },
                e -> {
                    if (WaitingListDB.REASON_EVENT_DELETED.equals(e.getMessage())) {
                        EventGoneUi.toast(this);
                        removeStaleEventFromBrowse(event.getEventId());
                    } else if (WaitingListDB.REASON_WAITLIST_FULL.equals(e.getMessage())) {
                        Toast.makeText(this, R.string.waitlist_capacity_altered, Toast.LENGTH_LONG).show();
                        refreshEventFromServerAfterWaitlistMutation(event.getEventId());
                    } else if (WaitingListDB.REASON_REGISTRATION_CLOSED.equals(e.getMessage())) {
                        Toast.makeText(this, R.string.waitlist_registration_closed, Toast.LENGTH_LONG).show();
                        refreshEventFromServerAfterWaitlistMutation(event.getEventId());
                    } else if (WaitingListDB.REASON_ORGANIZER_CANNOT_JOIN.equals(e.getMessage())) {
                        Toast.makeText(this, R.string.waitlist_organizer_cannot_join, Toast.LENGTH_LONG).show();
                        refreshEventFromServerAfterWaitlistMutation(event.getEventId());
                    } else {
                        Toast.makeText(this, getString(R.string.waitlist_fail) + " " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        refreshEventFromServerAfterWaitlistMutation(event.getEventId());
                    }
                });
    }

    private void recordLocationIfPermitted(String eventId) {
        if (eventId == null || eventId.isEmpty()) return;
        if (!geolocationController.hasLocationPermission(this)) return;
        geolocationController.getCurrentDeviceLocation(this, loc -> {
            if (loc == null) return;
            geolocationController.recordLocationForEvent(
                    EventListActivity.this,
                    deviceId,
                    eventId,
                    loc,
                    unused -> {},
                    e -> {});
        });
    }

    private void removeStaleEventFromBrowse(String eventId) {
        if (eventId == null) {
            return;
        }
        allEvents.removeIf(e -> e != null && eventId.equals(e.getEventId()));
        activeRegistrationsByEventId.remove(eventId);
        registrationsByEventId.remove(eventId);
        waitlistCountByEventId.remove(eventId);
        adapter.clearAuxiliaryStateForEvent(eventId);
        adapter.setActiveRegistrationsByEventId(activeRegistrationsByEventId);
        adapter.setRegistrationsByEventId(registrationsByEventId);
        adapter.setWaitlistCountByEventId(waitlistCountByEventId);
        applyFilters();
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
        if (!NetworkConnectivity.hasValidatedInternet(this)) {
            Toast.makeText(this, "Failed to leave waitlist: No internet connection.", Toast.LENGTH_SHORT).show();
            return;
        }
        eventController.getEventFromServer(event.getEventId(), fresh -> runOnUiThread(() -> {
            if (fresh == null) {
                EventGoneUi.toast(this);
                removeStaleEventFromBrowse(event.getEventId());
                return;
            }
            continueLeaveWaitlistAfterEventVerified(event, reg);
        }), e -> runOnUiThread(() ->
                Toast.makeText(this, "Could not verify event.", Toast.LENGTH_SHORT).show()));
    }

    private void continueLeaveWaitlistAfterEventVerified(Event event, WaitingList reg) {
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
            final String eid = event.getEventId();
            waitingListDB.deleteRegistration(eid, reg.getDeviceId(),
                    unused -> {
                        Toast.makeText(this, "Left waitlist for " + event.getName(), Toast.LENGTH_SHORT).show();
                        activeRegistrationsByEventId.remove(eid);
                        registrationsByEventId.remove(eid);
                        adapter.setActiveRegistrationsByEventId(activeRegistrationsByEventId);
                        adapter.setRegistrationsByEventId(registrationsByEventId);
                        refreshEventFromServerAfterWaitlistMutation(eid);
                    },
                    e -> {
                        if (EventGoneUi.isFirestoreNotFound(e)) {
                            EventGoneUi.toast(this);
                            removeStaleEventFromBrowse(eid);
                        } else {
                            Toast.makeText(this, "Failed to leave waitlist: " + e.getMessage(),
                                    Toast.LENGTH_SHORT).show();
                        }
                    });
        });

        dialog.show();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
        }
    }

}
