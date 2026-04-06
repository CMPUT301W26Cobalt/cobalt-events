package com.example.cobaltevents.ui;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.SwitchCompat;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.widget.ImageViewCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.bumptech.glide.Glide;

import com.example.cobaltevents.R;
import com.example.cobaltevents.controller.ImageController;
import com.example.cobaltevents.controller.LotteryController;
import com.example.cobaltevents.controller.QRCodeController;
import com.example.cobaltevents.db.EntrantDB;
import com.example.cobaltevents.db.EventDB;
import com.example.cobaltevents.db.NotificationDB;
import com.example.cobaltevents.db.ProfileDB;
import com.example.cobaltevents.db.WaitingListDB;
import com.example.cobaltevents.model.Entrant;
import com.example.cobaltevents.model.Event;
import com.example.cobaltevents.model.LotteryErrorCodes;
import com.example.cobaltevents.model.Notification;
import com.example.cobaltevents.model.RescindSelectionInviteOutcome;
import com.example.cobaltevents.model.WaitingList;
import com.example.cobaltevents.ui.adapter.WaitlistEntrantAdapter;
import com.example.cobaltevents.ui.comments.EventCommentsUiBinder;
import com.example.cobaltevents.util.NetworkConnectivity;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.gms.maps.MapView;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.Source;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.nio.charset.StandardCharsets;
import java.io.OutputStream;
import java.io.IOException;

/**
 * US 02.02.01 – View Waiting List Entrants
 * Displays and manages entrants for a specific event organized by the current user.
 */
public class EventManageActivity extends AppCompatActivity {

    /** Main tab filter for declined-style statuses. */
    private static final String TAB_DECLINED_ONLY = WaitingList.STATUS_DECLINED;
    private static final String TAB_DECLINED_NEED_REPLACEMENT = WaitingList.STATUS_DECLINED;
    private static final String TAB_DECLINED_FOUND_REPLACEMENT = WaitingList.STATUS_DECLINED_FOUND_REPLACEMENT;

    private static final int MAIN_SECTION_WAITLIST = 0;
    private static final int MAIN_SECTION_ORGANIZERS = 1;
    private static final int MAIN_SECTION_COMMENTS = 2;

    private EventDB eventDB;
    private WaitingListDB waitingListDB;
    private ProfileDB profileDB;
    private NotificationDB notificationDB;
    private ImageController imageController;
    private LotteryController lotteryController;
    private QRCodeController qrCodeController;
    private WaitlistEntrantAdapter adapter;

    private String eventId;
    private Event currentEvent;
    private List<WaitingList> allEntrants = new ArrayList<>();
    private String currentTab = WaitingList.STATUS_PENDING;
    private String currentDeclinedSubtab = TAB_DECLINED_NEED_REPLACEMENT;

    private TextView tvEventName, tvEventLocation, tvEventDate, tvEventCapacity, tvEventRegistration;
    private TextView tvPrivateBadge;
    private TextView tvCountWaitlisted, tvCountInvited, tvCountConfirmed;
    private TextView tabWaitlisted, tabInvited, tabConfirmed, tabDeclined;
    private TextView tvNotifyAllCurrent;
    private View layoutNotifyAllSections;
    private View declinedSubtabsContainer;
    private TextView tabDeclinedNeedReplacement, tabDeclinedFoundReplacement;
    private View tabQrCode;
    private SwipeRefreshLayout swipeRefreshLayout;
    private FrameLayout loadingContainer;
    private ProgressBar progressBar;
    private TextView tvEmpty;
    private ImageView ivEventPoster;

    private View panelUserWaitlist;
    private LinearLayout panelOrganizers;
    private FrameLayout panelComments;
    private LinearLayout layoutOrganizersList;
    private TextView tvOrganizersEmpty;
    private EditText etOrganizerSearch;
    private LinearLayout layoutOrganizerSuggestions;
    private HorizontalScrollView scrollSelectedOrganizerChips;
    private LinearLayout layoutSelectedOrganizerChips;
    private View btnSendOrganizerInvite;
    private final LinkedHashMap<String, Entrant> selectedOrganizerCandidates = new LinkedHashMap<>();
    private View layoutPrivateInviteSection;
    private EditText etPrivateInviteSearch;
    private LinearLayout layoutPrivateInviteSuggestions;
    private HorizontalScrollView scrollSelectedPrivateInviteChips;
    private LinearLayout layoutSelectedPrivateInviteChips;
    private View btnSendPrivateInvites;
    private TextView tvPrivateInvitesEmpty;
    private LinearLayout layoutPrivateInviteList;
    private final LinkedHashMap<String, Entrant> selectedPrivateInviteCandidates = new LinkedHashMap<>();
    private final HashSet<String> privateInviteNotificationRecipientIds = new HashSet<>();
    private boolean privateInviteNotificationsLoaded = false;
    private List<Entrant> allProfilesCache = new ArrayList<>();
    private String currentDeviceId;
    private TextView tvMainTabWaitlist;
    private TextView tvMainTabOrganizers;
    private TextView tvMainTabComments;
    private ImageView ivMainTabWaitlist;
    private ImageView ivMainTabOrganizers;
    private ImageView ivMainTabComments;
    private View indicatorMainWaitlist;
    private View indicatorMainOrganizers;
    private View indicatorMainComments;
    private int mainSection = MAIN_SECTION_WAITLIST;
    private String commentsBoundEventId;
    private boolean kickedFromEventHandled = false;

    private static final SimpleDateFormat DATE_FORMAT =
            new SimpleDateFormat("MMM d, yyyy", Locale.getDefault());
    private static final SimpleDateFormat TIME_FORMAT =
            new SimpleDateFormat("h:mm a", Locale.getDefault());

    private EditEventDialog openEditEventDialog;
    private byte[] pendingCsvBytes;
    private String pendingCsvFileName;

    private final ActivityResultLauncher<String> pickEditPosterLauncher =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                if (openEditEventDialog != null) {
                    openEditEventDialog.onPosterPicked(uri);
                }
            });

    private final ActivityResultLauncher<String> createCsvDocumentLauncher =
            registerForActivityResult(new ActivityResultContracts.CreateDocument("text/csv"), uri -> {
                if (uri == null) {
                    pendingCsvBytes = null;
                    pendingCsvFileName = null;
                    return;
                }
                if (pendingCsvBytes == null) {
                    Toast.makeText(this, "No CSV data to export.", Toast.LENGTH_SHORT).show();
                    return;
                }
                try (OutputStream os = getContentResolver().openOutputStream(uri, "w")) {
                    if (os == null) {
                        Toast.makeText(this, "Failed to create CSV file.", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    os.write(pendingCsvBytes);
                    os.flush();
                    Toast.makeText(this, "CSV exported successfully.", Toast.LENGTH_SHORT).show();
                } catch (IOException e) {
                    Toast.makeText(this, "Failed to export CSV.", Toast.LENGTH_SHORT).show();
                } finally {
                    pendingCsvBytes = null;
                    pendingCsvFileName = null;
                }
            });

    private final ActivityResultLauncher<Intent> editDetailLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (openEditEventDialog != null) {
                    openEditEventDialog.onEditDetailResult(result.getResultCode(), result.getData());
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
        notificationDB = new NotificationDB();
        imageController = new ImageController();
        lotteryController = new LotteryController();
        qrCodeController = new QRCodeController();
        currentDeviceId = new EntrantDB(this).getEntrant().getDeviceId();

        tvEventName = findViewById(R.id.tv_event_name);
        tvEventLocation = findViewById(R.id.tv_event_location);
        tvEventDate = findViewById(R.id.tv_event_date);
        tvEventCapacity = findViewById(R.id.tv_event_capacity);
        tvEventRegistration = findViewById(R.id.tv_event_registration);
        tvPrivateBadge = findViewById(R.id.tv_private_badge);
        tvCountWaitlisted = findViewById(R.id.tv_count_waitlisted);
        tvCountInvited = findViewById(R.id.tv_count_invited);
        tvCountConfirmed = findViewById(R.id.tv_count_confirmed);
        tabWaitlisted = findViewById(R.id.tab_waitlisted);
        tabInvited = findViewById(R.id.tab_invited);
        tabConfirmed = findViewById(R.id.tab_confirmed);
        tabDeclined = findViewById(R.id.tab_declined);
        tvNotifyAllCurrent = findViewById(R.id.tv_notify_all_current);
        layoutNotifyAllSections = findViewById(R.id.layout_notify_all_sections);
        declinedSubtabsContainer = findViewById(R.id.declined_subtabs_container);
        tabDeclinedNeedReplacement = findViewById(R.id.tab_declined_need_replacement);
        tabDeclinedFoundReplacement = findViewById(R.id.tab_declined_found_replacement);
        tabQrCode = findViewById(R.id.tab_qr_code);
        loadingContainer = findViewById(R.id.loading_container);
        progressBar = findViewById(R.id.progress_bar);
        tvEmpty = findViewById(R.id.tv_empty);
        ivEventPoster = findViewById(R.id.iv_event_poster);
        swipeRefreshLayout = findViewById(R.id.swipe_refresh_event_manage);
        if (swipeRefreshLayout != null) {
            swipeRefreshLayout.setOnRefreshListener(() -> loadEventData(true));
            swipeRefreshLayout.setColorSchemeColors(ContextCompat.getColor(this, R.color.organizer_blue));
        }

        RecyclerView recyclerView = findViewById(R.id.recycler_entrants);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new WaitlistEntrantAdapter();
        adapter.setOnRescindInviteListener(reg ->
                verifyOrganizerAccessAndThen(() -> confirmRescindInvite(reg)));
        recyclerView.setAdapter(adapter);
        recyclerView.setNestedScrollingEnabled(false);

        findViewById(R.id.btn_back).setOnClickListener(v -> finish());

        tabWaitlisted.setOnClickListener(v -> selectTab(WaitingList.STATUS_PENDING));
        tabInvited.setOnClickListener(v -> selectTab(WaitingList.STATUS_SELECTED));
        tabConfirmed.setOnClickListener(v -> selectTab(WaitingList.STATUS_ENROLLED));
        tabDeclined.setOnClickListener(v -> selectTab(TAB_DECLINED_ONLY));
        if (tabDeclinedNeedReplacement != null) {
            tabDeclinedNeedReplacement.setOnClickListener(v -> selectDeclinedSubtab(TAB_DECLINED_NEED_REPLACEMENT));
        }
        if (tabDeclinedFoundReplacement != null) {
            tabDeclinedFoundReplacement.setOnClickListener(v -> selectDeclinedSubtab(TAB_DECLINED_FOUND_REPLACEMENT));
        }
        if (layoutNotifyAllSections != null) {
            layoutNotifyAllSections.setOnClickListener(v ->
                    verifyOrganizerAccessAndThen(this::showNotifyAllDialog));
        }

        findViewById(R.id.btn_edit_event).setOnClickListener(v ->
                verifyOrganizerAccessAndThen(this::showEditEventDialog));

        findViewById(R.id.btn_run_lottery).setOnClickListener(v ->
                verifyOrganizerAccessAndThen(this::showRunLotteryDialog));

        findViewById(R.id.btn_export_csv).setOnClickListener(v ->
                verifyOrganizerAccessAndThen(this::exportEnrolledEntrantsCsv));

        // Geolocation switch is bound in loadEventData once the event is loaded (avoids save on programmatic setChecked).

        findViewById(R.id.tab_replacement).setOnClickListener(v ->
                verifyOrganizerAccessAndThen(this::showFindReplacementDialog));

        if (tabQrCode != null) {
            tabQrCode.setOnClickListener(v -> verifyOrganizerAccessAndThen(this::showQrCodePopup));
        }

        // TODO US 02.02.02 – Map tab
        findViewById(R.id.tab_map).setOnClickListener(v ->
                verifyOrganizerAccessAndThen(this::showEntrantLocationsMapPopup));

        View tabNotifyEntireWaitlist = findViewById(R.id.tab_notify_entire_waitlist);
        if (tabNotifyEntireWaitlist != null) {
            tabNotifyEntireWaitlist.setOnClickListener(v ->
                    verifyOrganizerAccessAndThen(this::showNotifyEntireWaitlistDialog));
        }

        panelUserWaitlist = findViewById(R.id.panel_user_waitlist);
        panelOrganizers = findViewById(R.id.panel_organizers);
        panelComments = findViewById(R.id.panel_comments);
        layoutOrganizersList = findViewById(R.id.layout_organizers_list);
        tvOrganizersEmpty = findViewById(R.id.tv_organizers_empty);
        etOrganizerSearch = findViewById(R.id.et_organizer_search);
        layoutOrganizerSuggestions = findViewById(R.id.layout_organizer_suggestions);
        scrollSelectedOrganizerChips = findViewById(R.id.scroll_selected_organizer_chips);
        layoutSelectedOrganizerChips = findViewById(R.id.layout_selected_organizer_chips);
        btnSendOrganizerInvite = findViewById(R.id.btn_send_organizer_invite);
        layoutPrivateInviteSection = findViewById(R.id.layout_private_invite_section);
        etPrivateInviteSearch = findViewById(R.id.et_private_invite_search);
        layoutPrivateInviteSuggestions = findViewById(R.id.layout_private_invite_suggestions);
        scrollSelectedPrivateInviteChips = findViewById(R.id.scroll_selected_private_invite_chips);
        layoutSelectedPrivateInviteChips = findViewById(R.id.layout_selected_private_invite_chips);
        btnSendPrivateInvites = findViewById(R.id.btn_send_private_invites);
        tvPrivateInvitesEmpty = findViewById(R.id.tv_private_invites_empty);
        layoutPrivateInviteList = findViewById(R.id.layout_private_invite_list);
        tvMainTabWaitlist = findViewById(R.id.tv_main_tab_waitlist);
        tvMainTabOrganizers = findViewById(R.id.tv_main_tab_organizers);
        tvMainTabComments = findViewById(R.id.tv_main_tab_comments);
        ivMainTabWaitlist = findViewById(R.id.iv_main_tab_waitlist);
        ivMainTabOrganizers = findViewById(R.id.iv_main_tab_organizers);
        ivMainTabComments = findViewById(R.id.iv_main_tab_comments);
        indicatorMainWaitlist = findViewById(R.id.indicator_main_waitlist);
        indicatorMainOrganizers = findViewById(R.id.indicator_main_organizers);
        indicatorMainComments = findViewById(R.id.indicator_main_comments);
        findViewById(R.id.tab_main_waitlist).setOnClickListener(v -> selectMainSection(MAIN_SECTION_WAITLIST));
        findViewById(R.id.tab_main_organizers).setOnClickListener(v -> selectMainSection(MAIN_SECTION_ORGANIZERS));
        findViewById(R.id.tab_main_comments).setOnClickListener(v -> selectMainSection(MAIN_SECTION_COMMENTS));
        setupOrganizerInviteUi();
        setupPrivateInviteUi();
        selectMainSection(MAIN_SECTION_WAITLIST);

        setTabActive(tabWaitlisted);
        selectDeclinedSubtab(currentDeclinedSubtab);
        updateNotifyAllButtonLabel();
        loadEventData(false);
    }

    private void loadEventData() {
        loadEventData(false);
    }

    /**
     * Reloads event details, tab counts, and entrants. Pull-to-refresh keeps the screen visible and
     * stops the swipe indicator only after tab counts and entrants have both finished loading.
     */
    private void loadEventData(boolean fromPullToRefresh) {
        if (eventId == null) {
            finish();
            return;
        }
        if (!fromPullToRefresh) {
        loadingContainer.setVisibility(View.VISIBLE);
        progressBar.setVisibility(View.VISIBLE);
        tvEmpty.setVisibility(View.GONE);
            if (swipeRefreshLayout != null) {
                swipeRefreshLayout.setRefreshing(false);
            }
        }

        final AtomicInteger pullRemain =
                fromPullToRefresh ? new AtomicInteger(2) : null;
        Runnable markPullDone = () -> {
            if (pullRemain == null) {
                return;
            }
            if (pullRemain.decrementAndGet() == 0 && swipeRefreshLayout != null) {
                swipeRefreshLayout.setRefreshing(false);
            }
        };

        eventDB.getEventFromServer(eventId, event -> {
            if (event == null) {
                if (swipeRefreshLayout != null) {
                    swipeRefreshLayout.setRefreshing(false);
                }
                Toast.makeText(this, "Event not found", Toast.LENGTH_SHORT).show();
                finish();
                return;
            }
            if (!isCurrentDeviceOrganizer(event)) {
                handleKickedFromEvent();
                return;
            }
            currentEvent = event;
            commentsBoundEventId = null;
            populateEventInfo(event);
            populateOrganizersPanel();
            if (mainSection == MAIN_SECTION_COMMENTS) {
                ensureCommentsBound();
            }

            setupGeolocationSwitch((SwitchCompat) findViewById(R.id.switch_geolocation));

            refreshTabCountsFromServer(fromPullToRefresh ? markPullDone : null);
            loadEntrants(fromPullToRefresh ? markPullDone : null);
        }, e -> {
            loadingContainer.setVisibility(View.GONE);
            if (swipeRefreshLayout != null) {
                swipeRefreshLayout.setRefreshing(false);
            }
            Toast.makeText(this, "Failed to load event", Toast.LENGTH_SHORT).show();
        });
    }

    /**
     * Persists whether geolocation is required to join the waitlist (US 02.02.03).
     * Listener is attached only after load so {@link SwitchCompat#setChecked(boolean)} does not trigger a write.
     */
    private void setupGeolocationSwitch(SwitchCompat switchGeo) {
        if (switchGeo == null) return;
        switchGeo.setOnCheckedChangeListener(null);
        if (currentEvent != null) {
            switchGeo.setChecked(currentEvent.isGeolocationRequired());
        }
        switchGeo.setOnCheckedChangeListener((btn, checked) -> {
            final boolean targetChecked = checked;
            verifyOrganizerAccessAndThen(() -> {
                if (currentEvent == null || currentEvent.getEventId() == null) {
                    Toast.makeText(this, "Event not loaded yet", Toast.LENGTH_SHORT).show();
                    setupGeolocationSwitch(switchGeo);
                    return;
                }
                final boolean previous = currentEvent.isGeolocationRequired();
                if (previous == targetChecked) {
                    return;
                }
                currentEvent.setGeolocationRequired(targetChecked);
                eventDB.updateEvent(currentEvent,
                        unused -> runOnUiThread(() ->
                                Toast.makeText(this, R.string.event_manage_geolocation_updated, Toast.LENGTH_SHORT).show()),
                        e -> runOnUiThread(() -> {
                            currentEvent.setGeolocationRequired(previous);
                            String detail = e.getMessage() != null ? e.getMessage() : "";
                            Toast.makeText(this,
                                    getString(R.string.event_manage_geolocation_save_failed, detail),
                                    Toast.LENGTH_LONG).show();
                            setupGeolocationSwitch(switchGeo);
                        }));
            });
        });
    }

    private void populateEventInfo(Event event) {
        if (event.getPosterImageUrl() != null && !event.getPosterImageUrl().isEmpty()) {
            ivEventPoster.setBackgroundColor(Color.TRANSPARENT);
            Glide.with(this).load(event.getPosterImageUrl()).centerCrop().into(ivEventPoster);
        } else {
            Glide.with(this).clear(ivEventPoster);
            ivEventPoster.setImageDrawable(null);
            ivEventPoster.setBackgroundColor(ContextCompat.getColor(this, R.color.organizer_blue_light));
        }

        if (tvPrivateBadge != null) {
            tvPrivateBadge.setVisibility(event.isPrivate() ? View.VISIBLE : View.GONE);
        }
        if (tabQrCode != null) {
            tabQrCode.setVisibility(event.isPrivate() ? View.GONE : View.VISIBLE);
        }

        tvEventName.setText(event.getName() != null ? event.getName() : "Untitled");

        String dateStr = event.getEventDate() != null
                ? DATE_FORMAT.format(event.getEventDate().toDate()) : getString(R.string.event_manage_date_tbd);
        tvEventDate.setText(getString(R.string.event_manage_meta_date, dateStr));

        String locStr = event.getLocation() != null && !event.getLocation().trim().isEmpty()
                ? event.getLocation().trim() : getString(R.string.event_manage_no_location);
        tvEventLocation.setText(getString(R.string.event_manage_meta_location, locStr));

        int cap = event.getWaitingListCapacity();
        String capLabel = getString(R.string.event_manage_capacity_value,
                cap > 0 ? String.valueOf(cap) : getString(R.string.event_manage_capacity_unlimited));
        tvEventCapacity.setText(getString(R.string.event_manage_meta_capacity, capLabel));

        if (tvEventRegistration != null) {
            if (event.getRegistrationOpen() != null && event.getRegistrationClose() != null) {
                String range = getString(R.string.event_manage_registration_range,
                        DATE_FORMAT.format(event.getRegistrationOpen().toDate())
                                + " · "
                                + TIME_FORMAT.format(event.getRegistrationOpen().toDate()),
                        DATE_FORMAT.format(event.getRegistrationClose().toDate())
                                + " · "
                                + TIME_FORMAT.format(event.getRegistrationClose().toDate()));
                tvEventRegistration.setVisibility(View.VISIBLE);
                tvEventRegistration.setText(getString(R.string.event_manage_meta_registration, range));
            } else {
                tvEventRegistration.setVisibility(View.GONE);
            }
        }
        if (layoutPrivateInviteSection != null) {
            layoutPrivateInviteSection.setVisibility(event.isPrivate() ? View.VISIBLE : View.GONE);
        }
        if (event.isPrivate()) {
            populatePrivateInvitePanel();
        }
    }

    private void selectMainSection(int section) {
        mainSection = section;
        applyMainSectionVisibility();
        updateMainTabAppearance();
        if (section == MAIN_SECTION_ORGANIZERS) {
            populateOrganizersPanel();
        } else if (section == MAIN_SECTION_COMMENTS) {
            ensureCommentsBound();
        }
    }

    private void applyMainSectionVisibility() {
        if (panelUserWaitlist != null) {
            panelUserWaitlist.setVisibility(mainSection == MAIN_SECTION_WAITLIST ? View.VISIBLE : View.GONE);
        }
        if (panelOrganizers != null) {
            panelOrganizers.setVisibility(mainSection == MAIN_SECTION_ORGANIZERS ? View.VISIBLE : View.GONE);
        }
        if (panelComments != null) {
            panelComments.setVisibility(mainSection == MAIN_SECTION_COMMENTS ? View.VISIBLE : View.GONE);
        }
    }

    private void updateMainTabAppearance() {
        boolean w = mainSection == MAIN_SECTION_WAITLIST;
        boolean o = mainSection == MAIN_SECTION_ORGANIZERS;
        boolean c = mainSection == MAIN_SECTION_COMMENTS;
        int accent = ContextCompat.getColor(this, R.color.organizer_blue);
        int mutedText = ContextCompat.getColor(this, R.color.grey_muted_text);
        int mutedIcon = ContextCompat.getColor(this, R.color.grey_nav_inactive);

        if (tvMainTabWaitlist != null) {
            tvMainTabWaitlist.setTextColor(w ? accent : mutedText);
        }
        if (tvMainTabOrganizers != null) {
            tvMainTabOrganizers.setTextColor(o ? accent : mutedText);
        }
        if (tvMainTabComments != null) {
            tvMainTabComments.setTextColor(c ? accent : mutedText);
        }
        if (ivMainTabWaitlist != null) {
            ImageViewCompat.setImageTintList(ivMainTabWaitlist,
                    ColorStateList.valueOf(w ? accent : mutedIcon));
        }
        if (ivMainTabOrganizers != null) {
            ImageViewCompat.setImageTintList(ivMainTabOrganizers,
                    ColorStateList.valueOf(o ? accent : mutedIcon));
        }
        if (ivMainTabComments != null) {
            ImageViewCompat.setImageTintList(ivMainTabComments,
                    ColorStateList.valueOf(c ? accent : mutedIcon));
        }
        if (indicatorMainWaitlist != null) {
            if (w) {
                indicatorMainWaitlist.setBackgroundResource(R.drawable.bg_event_manage_main_tab_indicator);
            } else {
                indicatorMainWaitlist.setBackgroundColor(Color.TRANSPARENT);
            }
        }
        if (indicatorMainOrganizers != null) {
            if (o) {
                indicatorMainOrganizers.setBackgroundResource(R.drawable.bg_event_manage_main_tab_indicator);
            } else {
                indicatorMainOrganizers.setBackgroundColor(Color.TRANSPARENT);
            }
        }
        if (indicatorMainComments != null) {
            if (c) {
                indicatorMainComments.setBackgroundResource(R.drawable.bg_event_manage_main_tab_indicator);
            } else {
                indicatorMainComments.setBackgroundColor(Color.TRANSPARENT);
            }
        }
    }

    private void setupOrganizerInviteUi() {
        if (etOrganizerSearch == null || btnSendOrganizerInvite == null) {
            return;
        }
        etOrganizerSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) { }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                showOrganizerSuggestions(s != null ? s.toString() : "");
            }

            @Override
            public void afterTextChanged(Editable s) { }
        });
        btnSendOrganizerInvite.setOnClickListener(v -> sendOrganizerInvite());
        renderSelectedOrganizerInviteChips();
    }

    private void showOrganizerSuggestions(String rawQuery) {
        if (layoutOrganizerSuggestions == null) return;
        String query = rawQuery != null ? rawQuery.trim().toLowerCase(Locale.US) : "";
        if (query.isEmpty()) {
            layoutOrganizerSuggestions.removeAllViews();
            layoutOrganizerSuggestions.setVisibility(View.GONE);
            return;
        }
        Runnable render = () -> runOnUiThread(() -> {
            if (layoutOrganizerSuggestions == null) return;
            layoutOrganizerSuggestions.removeAllViews();
            LayoutInflater inflater = LayoutInflater.from(this);
            List<String> existingOrganizers = currentEvent != null
                    ? currentEvent.getMergedOrganizerDeviceIds()
                    : new ArrayList<>();
            HashSet<String> eligibleEntrantIds = new HashSet<>();
            if (allEntrants != null) {
                for (WaitingList reg : allEntrants) {
                    if (reg == null || reg.getDeviceId() == null) continue;
                    if (isActiveWaitlistStatus(reg.getStatus())) {
                        eligibleEntrantIds.add(reg.getDeviceId());
                    }
                }
            }
            int shown = 0;
            for (Entrant profile : allProfilesCache) {
                if (profile == null || profile.getDeviceId() == null) continue;
                if (!eligibleEntrantIds.contains(profile.getDeviceId())) {
                    // Organizer invites must come from existing event waitlist entries only.
                    continue;
                }
                String name = profile.getName() != null ? profile.getName() : "";
                String email = profile.getEmail() != null ? profile.getEmail() : "";
                String phone = profile.getPhone() != null ? profile.getPhone() : "";

                // Match by name, email, and/or phone (partial match).
                boolean matchesName = !name.trim().isEmpty() && name.toLowerCase(Locale.US).contains(query);
                boolean matchesEmail = !email.trim().isEmpty() && email.toLowerCase(Locale.US).contains(query);
                boolean matchesPhone = false;
                String phoneDigits = normalizePhone(phone);
                String queryDigits = normalizePhone(query);
                if (!queryDigits.isEmpty() && !phoneDigits.isEmpty()) {
                    matchesPhone = phoneDigits.contains(queryDigits);
                }
                if (!(matchesName || matchesEmail || matchesPhone)) continue;

                if (existingOrganizers.contains(profile.getDeviceId())) continue; // can't invite organizers
                View row = inflater.inflate(R.layout.item_organizer_suggestion, layoutOrganizerSuggestions, false);
                TextView tvName = row.findViewById(R.id.tv_suggestion_name);
                TextView tvId = row.findViewById(R.id.tv_suggestion_device_id);
                tvName.setText(!name.trim().isEmpty() ? name : profile.getDeviceId());
                tvId.setText(profile.getDeviceId());
                row.setOnClickListener(v -> {
                    selectedOrganizerCandidates.put(profile.getDeviceId(), profile);
                    renderSelectedOrganizerInviteChips();
                    if (etOrganizerSearch != null) {
                        etOrganizerSearch.setText("");
                    }
                    layoutOrganizerSuggestions.setVisibility(View.GONE);
                });
                layoutOrganizerSuggestions.addView(row);
                shown++;
                if (shown >= 6) break;
            }
            layoutOrganizerSuggestions.setVisibility(shown > 0 ? View.VISIBLE : View.GONE);
        });
        if (!allProfilesCache.isEmpty()) {
            render.run();
            return;
        }
        profileDB.getAllProfiles(profiles -> {
            allProfilesCache = profiles != null ? profiles : new ArrayList<>();
            render.run();
        }, e -> runOnUiThread(() ->
                Toast.makeText(this, "Failed to load profiles", Toast.LENGTH_SHORT).show()));
    }

    private void sendOrganizerInvite() {
        if (currentEvent == null || currentEvent.getEventId() == null) {
            Toast.makeText(this, "Event not loaded yet", Toast.LENGTH_SHORT).show();
            return;
        }
        if (selectedOrganizerCandidates.isEmpty()) {
            Toast.makeText(this, R.string.event_manage_organizer_pick_user, Toast.LENGTH_SHORT).show();
            return;
        }
        String eventLabel = currentEvent.getName() != null && !currentEvent.getName().trim().isEmpty()
                ? currentEvent.getName().trim()
                : getString(R.string.notification_co_organizer_event_fallback);
        List<String> existingOrganizerIds = currentEvent.getMergedOrganizerDeviceIds();
        List<String> targetIds = new ArrayList<>();
        for (Map.Entry<String, Entrant> entry : selectedOrganizerCandidates.entrySet()) {
            String candidateId = entry.getKey();
            if (candidateId != null && !candidateId.trim().isEmpty() && !existingOrganizerIds.contains(candidateId)) {
                targetIds.add(candidateId);
            }
        }
        if (targetIds.isEmpty()) {
            Toast.makeText(this, R.string.event_manage_organizer_pick_user, Toast.LENGTH_SHORT).show();
            return;
        }
        sendOrganizerInviteNotificationsSequentially(targetIds, 0, eventLabel, () -> runOnUiThread(() -> {
            Toast.makeText(this, R.string.event_manage_organizer_invite_sent, Toast.LENGTH_SHORT).show();
            selectedOrganizerCandidates.clear();
            renderSelectedOrganizerInviteChips();
            if (etOrganizerSearch != null) {
                etOrganizerSearch.setText("");
            }
            populateOrganizersPanel();
        }));
    }

    private void renderSelectedOrganizerInviteChips() {
        if (layoutSelectedOrganizerChips == null || scrollSelectedOrganizerChips == null) {
            return;
        }
        layoutSelectedOrganizerChips.removeAllViews();
        if (selectedOrganizerCandidates.isEmpty()) {
            scrollSelectedOrganizerChips.setVisibility(View.GONE);
            return;
        }
        scrollSelectedOrganizerChips.setVisibility(View.VISIBLE);
        for (Entrant entrant : selectedOrganizerCandidates.values()) {
            if (entrant == null || entrant.getDeviceId() == null) continue;
            String label = entrant.getName() != null && !entrant.getName().trim().isEmpty()
                    ? entrant.getName().trim()
                    : entrant.getDeviceId();
            layoutSelectedOrganizerChips.addView(createOrganizerInviteChip(label, entrant.getDeviceId()));
        }
    }

    private View createOrganizerInviteChip(String label, String deviceId) {
        LinearLayout chip = new LinearLayout(this);
        chip.setOrientation(LinearLayout.HORIZONTAL);
        chip.setGravity(Gravity.CENTER_VERTICAL);
        chip.setBackgroundResource(R.drawable.bg_keyword_chip_white);
        int hPad = dpToPx(12);
        int vPad = dpToPx(6);
        chip.setPadding(hPad, vPad, hPad, vPad);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMarginEnd(dpToPx(8));
        chip.setLayoutParams(lp);

        TextView tv = new TextView(this);
        tv.setText(label);
        tv.setTextColor(ContextCompat.getColor(this, R.color.organizer_blue));
        tv.setTextSize(14f);

        TextView remove = new TextView(this);
        remove.setText("×");
        remove.setTextColor(ContextCompat.getColor(this, R.color.organizer_blue));
        remove.setTextSize(14f);
        remove.setPadding(dpToPx(8), 0, 0, 0);
        remove.setOnClickListener(v -> {
            selectedOrganizerCandidates.remove(deviceId);
            renderSelectedOrganizerInviteChips();
        });
        chip.addView(tv);
        chip.addView(remove);
        return chip;
    }

    private void sendOrganizerInviteNotificationsSequentially(List<String> targetIds,
                                                              int index,
                                                              String eventLabel,
                                                              Runnable onComplete) {
        if (index >= targetIds.size()) {
            onComplete.run();
            return;
        }
        String candidateId = targetIds.get(index);
        Notification userNotification = new Notification(
                candidateId,
                currentEvent.getEventId(),
                getString(R.string.notification_co_organizer_title),
                getString(R.string.notification_co_organizer_message, eventLabel),
                Notification.TYPE_CO_ORGANIZER
        );
        userNotification.setRecipientMode(Notification.RECIPIENT_MODE_USER);
        userNotification.setResponse(null);

        Notification organizerNotification = new Notification(
                candidateId,
                currentEvent.getEventId(),
                getString(R.string.notification_co_organizer_title),
                getString(R.string.notification_co_organizer_message, eventLabel),
                Notification.TYPE_CO_ORGANIZER
        );
        organizerNotification.setRecipientMode(Notification.RECIPIENT_MODE_ORGANIZER);
        organizerNotification.setResponse(Notification.RESPONSE_PENDING);

        notificationDB.saveNotification(userNotification,
                unused -> notificationDB.saveNotification(organizerNotification,
                        unused2 -> sendOrganizerInviteNotificationsSequentially(targetIds, index + 1, eventLabel, onComplete),
                        e2 -> runOnUiThread(() ->
                                Toast.makeText(this, R.string.comments_action_failed, Toast.LENGTH_SHORT).show())),
                e -> runOnUiThread(() ->
                        Toast.makeText(this, R.string.comments_action_failed, Toast.LENGTH_SHORT).show()));
    }

    private void setupPrivateInviteUi() {
        if (etPrivateInviteSearch == null || btnSendPrivateInvites == null) {
            return;
        }
        etPrivateInviteSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) { }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                showPrivateInviteSuggestions(s != null ? s.toString() : "");
            }

            @Override
            public void afterTextChanged(Editable s) { }
        });
        btnSendPrivateInvites.setOnClickListener(v -> sendPrivateInvites());
        renderSelectedPrivateInviteChips();
    }

    private void showPrivateInviteSuggestions(String rawQuery) {
        if (layoutPrivateInviteSuggestions == null) return;
        String query = rawQuery != null ? rawQuery.trim().toLowerCase(Locale.US) : "";
        if (query.isEmpty()) {
            layoutPrivateInviteSuggestions.removeAllViews();
            layoutPrivateInviteSuggestions.setVisibility(View.GONE);
            return;
        }
        Runnable render = () -> runOnUiThread(() -> {
            if (layoutPrivateInviteSuggestions == null) return;
            if (currentEvent != null && currentEvent.isPrivate() && !privateInviteNotificationsLoaded) {
                layoutPrivateInviteSuggestions.removeAllViews();
                layoutPrivateInviteSuggestions.setVisibility(View.GONE);
                return;
            }
            layoutPrivateInviteSuggestions.removeAllViews();
            LayoutInflater inflater = LayoutInflater.from(this);
            List<String> organizerIds = currentEvent != null
                    ? currentEvent.getMergedOrganizerDeviceIds()
                    : new ArrayList<>();
            HashSet<String> waitlistIds = new HashSet<>();
            if (allEntrants != null) {
                for (WaitingList reg : allEntrants) {
                    if (reg == null || reg.getDeviceId() == null) continue;
                    // Private invites should not target anyone already present in the event entries (any status).
                    waitlistIds.add(reg.getDeviceId());
                }
            }
            int shown = 0;
            for (Entrant profile : allProfilesCache) {
                if (profile == null || profile.getDeviceId() == null) continue;
                if (organizerIds.contains(profile.getDeviceId())) continue;
                if (waitlistIds.contains(profile.getDeviceId())) continue; // already in waitlist/enrolled/declined
                if (privateInviteNotificationRecipientIds.contains(profile.getDeviceId())) continue; // already invited/accepted/declined

                String name = profile.getName() != null ? profile.getName() : "";

                String email = profile.getEmail() != null ? profile.getEmail() : "";
                String phone = profile.getPhone() != null ? profile.getPhone() : "";

                boolean matchesName = name.toLowerCase(Locale.US).contains(query);
                boolean matchesEmail = !email.trim().isEmpty() && email.toLowerCase(Locale.US).contains(query);
                boolean matchesPhone = false;
                String phoneDigits = normalizePhone(phone);
                String queryDigits = normalizePhone(query);
                if (!queryDigits.isEmpty() && !phoneDigits.isEmpty()) {
                    matchesPhone = phoneDigits.contains(queryDigits);
                }
                if (!(matchesName || matchesEmail || matchesPhone)) continue;

                View row = inflater.inflate(R.layout.item_organizer_suggestion, layoutPrivateInviteSuggestions, false);
                TextView tvName = row.findViewById(R.id.tv_suggestion_name);
                TextView tvId = row.findViewById(R.id.tv_suggestion_device_id);
                tvName.setText(!name.trim().isEmpty() ? name : profile.getDeviceId());
                tvId.setText(profile.getDeviceId());
                row.setOnClickListener(v -> {
                    selectedPrivateInviteCandidates.put(profile.getDeviceId(), profile);
                    renderSelectedPrivateInviteChips();
                    if (etPrivateInviteSearch != null) {
                        etPrivateInviteSearch.setText("");
                    }
                    layoutPrivateInviteSuggestions.setVisibility(View.GONE);
                });
                layoutPrivateInviteSuggestions.addView(row);
                shown++;
                if (shown >= 6) break;
            }
            layoutPrivateInviteSuggestions.setVisibility(shown > 0 ? View.VISIBLE : View.GONE);
        });
        if (!allProfilesCache.isEmpty()) {
            render.run();
            return;
        }
        profileDB.getAllProfiles(profiles -> {
            allProfilesCache = profiles != null ? profiles : new ArrayList<>();
            render.run();
        }, e -> runOnUiThread(() ->
                Toast.makeText(this, "Failed to load profiles", Toast.LENGTH_SHORT).show()));
    }

    private void renderSelectedPrivateInviteChips() {
        if (layoutSelectedPrivateInviteChips == null || scrollSelectedPrivateInviteChips == null) {
            return;
        }
        layoutSelectedPrivateInviteChips.removeAllViews();
        if (selectedPrivateInviteCandidates.isEmpty()) {
            scrollSelectedPrivateInviteChips.setVisibility(View.GONE);
            return;
        }
        scrollSelectedPrivateInviteChips.setVisibility(View.VISIBLE);
        for (Entrant entrant : selectedPrivateInviteCandidates.values()) {
            if (entrant == null || entrant.getDeviceId() == null) continue;
            String label = entrant.getName() != null && !entrant.getName().trim().isEmpty()
                    ? entrant.getName().trim()
                    : entrant.getDeviceId();
            layoutSelectedPrivateInviteChips.addView(createPrivateInviteChip(label, entrant.getDeviceId()));
        }
    }

    private View createPrivateInviteChip(String label, String deviceId) {
        LinearLayout chip = new LinearLayout(this);
        chip.setOrientation(LinearLayout.HORIZONTAL);
        chip.setGravity(Gravity.CENTER_VERTICAL);
        chip.setBackgroundResource(R.drawable.bg_keyword_chip_white);
        int hPad = dpToPx(12);
        int vPad = dpToPx(6);
        chip.setPadding(hPad, vPad, hPad, vPad);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMarginEnd(dpToPx(8));
        chip.setLayoutParams(lp);

        TextView tv = new TextView(this);
        tv.setText(label);
        tv.setTextColor(ContextCompat.getColor(this, R.color.organizer_blue));
        tv.setTextSize(14f);

        TextView remove = new TextView(this);
        remove.setText("×");
        remove.setTextColor(ContextCompat.getColor(this, R.color.organizer_blue));
        remove.setTextSize(14f);
        remove.setPadding(dpToPx(8), 0, 0, 0);
        remove.setOnClickListener(v -> {
            selectedPrivateInviteCandidates.remove(deviceId);
            renderSelectedPrivateInviteChips();
        });
        chip.addView(tv);
        chip.addView(remove);
        return chip;
    }

    private void sendPrivateInvites() {
        if (currentEvent == null || currentEvent.getEventId() == null) {
            Toast.makeText(this, "Event not loaded yet", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!currentEvent.isPrivate()) {
            Toast.makeText(this, R.string.comments_action_failed, Toast.LENGTH_SHORT).show();
            return;
        }
        if (selectedPrivateInviteCandidates.isEmpty()) {
            Toast.makeText(this, R.string.event_manage_organizer_pick_user, Toast.LENGTH_SHORT).show();
            return;
        }
        String eventLabel = currentEvent.getName() != null && !currentEvent.getName().trim().isEmpty()
                ? currentEvent.getName().trim()
                : getString(R.string.notification_event_fallback);
        waitingListDB.getEntrantsForEvent(currentEvent.getEventId(), Source.SERVER, registrations ->
                        notificationDB.getNotificationsForEventTypeAndMode(
                                currentEvent.getEventId(),
                                Notification.TYPE_PRIVATE_EVENT,
                                Notification.RECIPIENT_MODE_USER,
                                existing -> {
                                    HashSet<String> activeWaitlistIds = new HashSet<>();
                                    if (registrations != null) {
                                        for (WaitingList reg : registrations) {
                                            if (reg == null || reg.getDeviceId() == null) continue;
                                            if (isActiveWaitlistStatus(reg.getStatus())) {
                                                activeWaitlistIds.add(reg.getDeviceId());
                                            }
                                        }
                                    }

                                    HashSet<String> skipIds = new HashSet<>();
                                    List<String> organizerIds = currentEvent.getMergedOrganizerDeviceIds();
                                    for (String organizerId : organizerIds) {
                                        if (organizerId != null) skipIds.add(organizerId);
                                    }
                                    if (existing != null) {
                                        for (Notification n : existing) {
                                            if (n == null || n.getRecipientId() == null) continue;
                                            String response = n.getResponse() != null
                                                    ? n.getResponse().toLowerCase(Locale.US)
                                                    : Notification.RESPONSE_PENDING;
                                            // Pending invites are always blocked from duplicate sends.
                                            if (Notification.RESPONSE_PENDING.equals(response)) {
                                                skipIds.add(n.getRecipientId());
                                                continue;
                                            }
                                            // Accepted invites are blocked only while user is still active in event.
                                            if (Notification.RESPONSE_ACCEPTED.equals(response)
                                                    && activeWaitlistIds.contains(n.getRecipientId())) {
                                                skipIds.add(n.getRecipientId());
                                            }
                                        }
                                    }
                                    List<String> targetIds = new ArrayList<>();
                                    for (Map.Entry<String, Entrant> entry : selectedPrivateInviteCandidates.entrySet()) {
                                        String candidateId = entry.getKey();
                                        if (candidateId != null && !candidateId.trim().isEmpty() && !skipIds.contains(candidateId)) {
                                            targetIds.add(candidateId);
                                        }
                                    }
                                    if (targetIds.isEmpty()) {
                                        runOnUiThread(() -> Toast.makeText(this, R.string.event_manage_organizer_pick_user, Toast.LENGTH_SHORT).show());
                                        return;
                                    }
                                    sendPrivateInviteNotificationsSequentially(targetIds, 0, eventLabel, () -> runOnUiThread(() -> {
                                        Toast.makeText(this, R.string.event_manage_private_invite_sent, Toast.LENGTH_SHORT).show();
                                        selectedPrivateInviteCandidates.clear();
                                        renderSelectedPrivateInviteChips();
                                        if (etPrivateInviteSearch != null) {
                                            etPrivateInviteSearch.setText("");
                                        }
                                        populatePrivateInvitePanel();
                                    }));
                                },
                                e -> runOnUiThread(() ->
                                        Toast.makeText(this, R.string.comments_action_failed, Toast.LENGTH_SHORT).show())),
                e -> runOnUiThread(() ->
                        Toast.makeText(this, R.string.comments_action_failed, Toast.LENGTH_SHORT).show()));
    }

    private void sendPrivateInviteNotificationsSequentially(List<String> targetIds,
                                                            int index,
                                                            String eventLabel,
                                                            Runnable onComplete) {
        if (index >= targetIds.size()) {
            onComplete.run();
            return;
        }
        String candidateId = targetIds.get(index);
        Notification privateInvite = new Notification(
                candidateId,
                currentEvent.getEventId(),
                getString(R.string.notification_private_event_title),
                getString(R.string.notification_private_event_message, eventLabel),
                Notification.TYPE_PRIVATE_EVENT
        );
        privateInvite.setRecipientMode(Notification.RECIPIENT_MODE_USER);
        privateInvite.setResponse(Notification.RESPONSE_PENDING);
        notificationDB.saveNotification(privateInvite,
                unused -> sendPrivateInviteNotificationsSequentially(targetIds, index + 1, eventLabel, onComplete),
                e -> runOnUiThread(() ->
                        Toast.makeText(this, R.string.comments_action_failed, Toast.LENGTH_SHORT).show()));
    }

    private void populatePrivateInvitePanel() {
        if (layoutPrivateInviteList == null || tvPrivateInvitesEmpty == null) {
            return;
        }
        layoutPrivateInviteList.removeAllViews();
        if (currentEvent == null || !currentEvent.isPrivate()) {
            tvPrivateInvitesEmpty.setVisibility(View.GONE);
            return;
        }
        privateInviteNotificationsLoaded = false;
        waitingListDB.getEntrantsForEvent(currentEvent.getEventId(), Source.SERVER, registrations ->
                        notificationDB.getNotificationsForEventTypeAndMode(
                                currentEvent.getEventId(),
                                Notification.TYPE_PRIVATE_EVENT,
                                Notification.RECIPIENT_MODE_USER,
                                notifications -> {
                                    HashSet<String> activeWaitlistIds = new HashSet<>();
                                    if (registrations != null) {
                                        for (WaitingList reg : registrations) {
                                            if (reg == null || reg.getDeviceId() == null) continue;
                                            if (isActiveWaitlistStatus(reg.getStatus())) {
                                                activeWaitlistIds.add(reg.getDeviceId());
                                            }
                                        }
                                    }
                    privateInviteNotificationRecipientIds.clear();
                    if (notifications != null) {
                        for (Notification notification : notifications) {
                            if (notification == null || notification.getRecipientId() == null || notification.getRecipientId().trim().isEmpty()) {
                                continue;
                            }
                            // Search must exclude anyone already invited (pending/accepted/declined).
                            privateInviteNotificationRecipientIds.add(notification.getRecipientId());
                        }
                    }
                    privateInviteNotificationsLoaded = true;
                                    LinkedHashMap<String, String> statusById = new LinkedHashMap<>();
                                    if (notifications != null) {
                                        for (Notification notification : notifications) {
                                            if (notification == null || notification.getRecipientId() == null || notification.getRecipientId().trim().isEmpty()) {
                                                continue;
                                            }
                                            String response = notification.getResponse() != null
                                                    ? notification.getResponse().toLowerCase(Locale.US)
                                                    : Notification.RESPONSE_PENDING;
                                            if (Notification.RESPONSE_PENDING.equals(response)) {
                                                if (!statusById.containsKey(notification.getRecipientId())) {
                                                    statusById.put(notification.getRecipientId(), Notification.RESPONSE_PENDING);
                                                }
                                                continue;
                                            }
                                            // Only show accepted while user still has an active waitlist entry.
                                            if (Notification.RESPONSE_ACCEPTED.equals(response)
                                                    && activeWaitlistIds.contains(notification.getRecipientId())
                                                    && !statusById.containsKey(notification.getRecipientId())) {
                                                statusById.put(notification.getRecipientId(), Notification.RESPONSE_ACCEPTED);
                                            }
                                        }
                                    }
                                    renderPrivateInviteRows(statusById);
                                },
                                e -> renderPrivateInviteRows(new LinkedHashMap<>())),
                e -> renderPrivateInviteRows(new LinkedHashMap<>()));
    }

    private boolean isActiveWaitlistStatus(String status) {
        if (status == null) return true;
        return WaitingList.STATUS_PENDING.equals(status)
                || WaitingList.STATUS_SELECTED.equals(status)
                || WaitingList.STATUS_ENROLLED.equals(status)
                || WaitingList.STATUS_NOT_SELECTED.equals(status);
    }

    private String normalizePhone(String phoneOrQuery) {
        if (phoneOrQuery == null) return "";
        // Keep digits only for forgiving phone-number matching.
        return phoneOrQuery.replaceAll("[^0-9]", "");
    }

    private void showQrCodePopup() {
        if (currentEvent == null || currentEvent.getEventId() == null || currentEvent.getEventId().trim().isEmpty()) {
            Toast.makeText(this, "Event not loaded yet", Toast.LENGTH_SHORT).show();
            return;
        }
        Bitmap bitmap = qrCodeController.generateQRCode(currentEvent.getEventId());
        if (bitmap == null) {
            Toast.makeText(this, R.string.event_create_qr_generate_failed, Toast.LENGTH_SHORT).show();
            return;
        }

        ImageView imageView = new ImageView(this);
        imageView.setImageBitmap(bitmap);
        int qrSize = dpToPx(180);
        LinearLayout.LayoutParams imageLp = new LinearLayout.LayoutParams(qrSize, qrSize);
        imageView.setLayoutParams(imageLp);
        int pad = dpToPx(8);
        imageView.setPadding(pad, pad, pad, pad);
        imageView.setAdjustViewBounds(true);
        imageView.setScaleType(ImageView.ScaleType.FIT_CENTER);

        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setView(imageView)
                .setPositiveButton(android.R.string.ok, null)
                .create();
        dialog.show();
        TextView okButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
        if (okButton != null) {
            okButton.setTextColor(ContextCompat.getColor(this, R.color.organizer_blue));
        }
        if (dialog.getWindow() != null) {
            dialog.getWindow().setLayout(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
        }
    }

    private void showEntrantLocationsMapPopup() {
        View content = LayoutInflater.from(this).inflate(R.layout.dialog_event_manage_map_popup, null);
        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setView(content)
                .create();

        View close = content.findViewById(R.id.btn_map_popup_close);
        if (close != null) {
            close.setOnClickListener(v -> dialog.dismiss());
        }
        TextView countView = content.findViewById(R.id.tv_map_popup_count);
        MapView mapView = content.findViewById(R.id.map_popup_view);
        View loadingOverlay = content.findViewById(R.id.layout_map_popup_loading);
        if (countView != null) {
            countView.setText(getString(R.string.event_manage_map_popup_count_placeholder));
        }
        if (loadingOverlay != null) {
            loadingOverlay.setVisibility(View.VISIBLE);
        }

        if (mapView != null) {
            mapView.onCreate(null);
            mapView.onResume();
        }

        dialog.show();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }
        dialog.setOnDismissListener(d -> {
            if (mapView != null) {
                mapView.onPause();
                mapView.onDestroy();
            }
        });

        if (currentEvent == null || currentEvent.getEventId() == null) {
            Toast.makeText(this, "Event not loaded yet", Toast.LENGTH_SHORT).show();
            return;
        }

        waitingListDB.getEntrantsForEvent(currentEvent.getEventId(), Source.SERVER, registrations -> {
            List<LatLng> pendingOrInvited = new ArrayList<>();
            List<LatLng> confirmed = new ArrayList<>();
            List<LatLng> declined = new ArrayList<>();
            int missingPendingOrInvited = 0;
            int missingConfirmed = 0;
            int missingDeclined = 0;
            int entrantCount = 0;

            if (registrations != null) {
                for (WaitingList entry : registrations) {
                    if (entry == null) {
                        continue;
                    }
                    entrantCount++;
                    String s = entry.getStatus();
                    boolean hasCoords = entry.getLatitude() != null && entry.getLongitude() != null;
                    LatLng ll = hasCoords ? new LatLng(entry.getLatitude(), entry.getLongitude()) : null;
                    if (WaitingList.STATUS_ENROLLED.equals(s)) {
                        if (hasCoords) {
                            confirmed.add(ll);
                        } else {
                            missingConfirmed++;
                        }
                    } else if (WaitingList.STATUS_DECLINED.equals(s)
                            || WaitingList.STATUS_DECLINED_FOUND_REPLACEMENT.equals(s)) {
                        if (hasCoords) {
                            declined.add(ll);
                        } else {
                            missingDeclined++;
                        }
                    } else {
                        // Includes: pending, not_selected, selected, etc.
                        if (hasCoords) {
                            pendingOrInvited.add(ll);
                        } else {
                            missingPendingOrInvited++;
                        }
                    }
                }
            }

            int safeEntrantCount = Math.max(0, entrantCount);
            if (countView != null) {
                final int c = safeEntrantCount;
                runOnUiThread(() ->
                        countView.setText(getString(R.string.event_manage_map_popup_count_format, c)));
            }

            LatLng eventPoint = null;
            if (currentEvent.getLocationLatitude() != null && currentEvent.getLocationLongitude() != null) {
                eventPoint = new LatLng(currentEvent.getLocationLatitude(), currentEvent.getLocationLongitude());
            }
            final LatLng finalEventPoint = eventPoint;
            final int finalMissingPendingOrInvited = missingPendingOrInvited;
            final int finalMissingConfirmed = missingConfirmed;
            final int finalMissingDeclined = missingDeclined;

            if (mapView != null) {
                mapView.getMapAsync(googleMap -> renderEntrantLocationMarkers(
                        googleMap,
                        pendingOrInvited,
                        confirmed,
                        declined,
                        finalEventPoint,
                        finalMissingPendingOrInvited,
                        finalMissingConfirmed,
                        finalMissingDeclined));
                if (loadingOverlay != null) {
                    runOnUiThread(() -> loadingOverlay.setVisibility(View.GONE));
                }
            }
        }, e -> {
            Toast.makeText(this, "Failed to load entrant locations", Toast.LENGTH_SHORT).show();
            if (loadingOverlay != null) {
                runOnUiThread(() -> loadingOverlay.setVisibility(View.GONE));
            }
        });
    }

    private void renderEntrantLocationMarkers(
            GoogleMap map,
            List<LatLng> pendingOrInvited,
            List<LatLng> confirmed,
            List<LatLng> declined,
            LatLng eventPoint,
            int missingPendingOrInvited,
            int missingConfirmed,
            int missingDeclined) {
        if (map == null) return;
        map.clear();
        map.getUiSettings().setMapToolbarEnabled(false);
        map.getUiSettings().setMyLocationButtonEnabled(false);

        LatLngBounds.Builder boundsBuilder = new LatLngBounds.Builder();

        int totalPins = 0;

        if (pendingOrInvited != null) {
            for (int i = 0; i < pendingOrInvited.size(); i++) {
                LatLng ll = pendingOrInvited.get(i);
                if (ll == null) continue;
                boundsBuilder.include(ll);
                totalPins++;
                map.addMarker(new MarkerOptions()
                        .position(ll)
                        .title("Pending / Invited " + (i + 1))
                        .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE)));
            }
        }

        if (confirmed != null) {
            for (int i = 0; i < confirmed.size(); i++) {
                LatLng ll = confirmed.get(i);
                if (ll == null) continue;
                boundsBuilder.include(ll);
                totalPins++;
                map.addMarker(new MarkerOptions()
                        .position(ll)
                        .title("Confirmed " + (i + 1))
                        .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN)));
            }
        }

        if (declined != null) {
            for (int i = 0; i < declined.size(); i++) {
                LatLng ll = declined.get(i);
                if (ll == null) continue;
                boundsBuilder.include(ll);
                totalPins++;
                map.addMarker(new MarkerOptions()
                        .position(ll)
                        .title("Declined " + (i + 1))
                        .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED)));
            }
        }

        if (eventPoint != null) {
            boundsBuilder.include(eventPoint);
            totalPins++;
            map.addMarker(new MarkerOptions()
                    .position(eventPoint)
                    .title("Event Location")
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_BLUE)));

            // For entrants missing saved coordinates, place small offset pins around event location
            // so every entrant still appears on the map.
            totalPins += addFallbackPinsAroundEvent(
                    map, boundsBuilder, eventPoint, missingPendingOrInvited,
                    "Pending / Invited (no saved location)",
                    BitmapDescriptorFactory.HUE_AZURE, totalPins);
            totalPins += addFallbackPinsAroundEvent(
                    map, boundsBuilder, eventPoint, missingConfirmed,
                    "Confirmed (no saved location)",
                    BitmapDescriptorFactory.HUE_GREEN, totalPins);
            totalPins += addFallbackPinsAroundEvent(
                    map, boundsBuilder, eventPoint, missingDeclined,
                    "Declined (no saved location)",
                    BitmapDescriptorFactory.HUE_RED, totalPins);
        }

        if (totalPins == 0) {
            LatLng fallback = new LatLng(53.5461, -113.4938);
            map.moveCamera(CameraUpdateFactory.newLatLngZoom(fallback, 10f));
            return;
        }

        if (totalPins == 1) {
            LatLng single = eventPoint != null ? eventPoint
                    : (pendingOrInvited != null && !pendingOrInvited.isEmpty() ? pendingOrInvited.get(0)
                    : (confirmed != null && !confirmed.isEmpty() ? confirmed.get(0)
                    : (declined != null && !declined.isEmpty() ? declined.get(0) : eventPoint)));
            map.moveCamera(CameraUpdateFactory.newLatLngZoom(single, 14f));
            return;
        }

        map.moveCamera(CameraUpdateFactory.newLatLngBounds(boundsBuilder.build(), dpToPx(36)));
    }

    private int addFallbackPinsAroundEvent(
            GoogleMap map,
            LatLngBounds.Builder boundsBuilder,
            LatLng eventPoint,
            int count,
            String title,
            float hue,
            int seedOffset) {
        if (map == null || boundsBuilder == null || eventPoint == null || count <= 0) {
            return 0;
        }
        for (int i = 0; i < count; i++) {
            int ringIndex = seedOffset + i;
            double angle = Math.toRadians((ringIndex * 50) % 360);
            double radiusDeg = 0.00035 + ((ringIndex % 3) * 0.00015);
            double lat = eventPoint.latitude + (Math.sin(angle) * radiusDeg);
            double lng = eventPoint.longitude + (Math.cos(angle) * radiusDeg);
            LatLng fallback = new LatLng(lat, lng);
            boundsBuilder.include(fallback);
            map.addMarker(new MarkerOptions()
                    .position(fallback)
                    .title(title)
                    .icon(BitmapDescriptorFactory.defaultMarker(hue))
                    .alpha(0.88f));
        }
        return count;
    }

    private void renderPrivateInviteRows(Map<String, String> statusById) {
        if (layoutPrivateInviteList == null || tvPrivateInvitesEmpty == null) return;
        List<String> ids = new ArrayList<>(statusById.keySet());
        if (ids.isEmpty()) {
            runOnUiThread(() -> {
                layoutPrivateInviteList.removeAllViews();
                tvPrivateInvitesEmpty.setVisibility(View.VISIBLE);
            });
            return;
        }
        Map<String, String> namesById = new HashMap<>();
        AtomicInteger pending = new AtomicInteger(ids.size());
        Runnable finish = () -> {
            if (pending.decrementAndGet() != 0) return;
            runOnUiThread(() -> {
                layoutPrivateInviteList.removeAllViews();
                tvPrivateInvitesEmpty.setVisibility(View.GONE);
                LayoutInflater inflater = LayoutInflater.from(this);
                for (String id : ids) {
                    View row = inflater.inflate(R.layout.item_event_manage_private_invite_row, layoutPrivateInviteList, false);
                    TextView tvName = row.findViewById(R.id.tv_invitee_name);
                    TextView tvId = row.findViewById(R.id.tv_invitee_id);
                    TextView tvStatus = row.findViewById(R.id.tv_invite_status);
                    tvName.setText(namesById.getOrDefault(id, id));
                    tvId.setText(id);
                    bindPrivateInviteStatusTag(tvStatus, statusById.get(id));
                    layoutPrivateInviteList.addView(row);
                }
            });
        };
        for (String id : ids) {
            profileDB.getProfile(id, profile -> {
                String name = (profile != null && profile.getName() != null && !profile.getName().isEmpty())
                        ? profile.getName() : id;
                synchronized (namesById) {
                    namesById.put(id, name);
                }
                finish.run();
            }, e -> {
                synchronized (namesById) {
                    namesById.put(id, id);
                }
                finish.run();
            });
        }
    }

    private void bindPrivateInviteStatusTag(TextView tvStatus, String status) {
        if (tvStatus == null) return;
        String normalized = status != null ? status.toLowerCase(Locale.US) : Notification.RESPONSE_PENDING;
        if (Notification.RESPONSE_ACCEPTED.equals(normalized)) {
            tvStatus.setText(R.string.event_manage_private_status_accepted);
            tvStatus.setBackgroundResource(R.drawable.bg_notif_badge_accepted);
            tvStatus.setTextColor(ContextCompat.getColor(this, R.color.notif_accepted_text));
            return;
        }
        tvStatus.setText(R.string.event_manage_private_status_pending);
        tvStatus.setBackgroundResource(R.drawable.bg_attendee_tab_item);
        tvStatus.setTextColor(ContextCompat.getColor(this, R.color.event_manage_meta));
    }

    private void populateOrganizersPanel() {
        if (layoutOrganizersList == null || tvOrganizersEmpty == null) {
            return;
        }
        layoutOrganizersList.removeAllViews();
        if (currentEvent == null) {
            tvOrganizersEmpty.setVisibility(View.VISIBLE);
            return;
        }
        List<String> ids = currentEvent.getMergedOrganizerDeviceIds();
        notificationDB.getNotificationsForEventTypeAndMode(
                currentEvent.getEventId(),
                Notification.TYPE_CO_ORGANIZER,
                Notification.RECIPIENT_MODE_ORGANIZER,
                notifications -> {
                    Map<String, String> statusById = new LinkedHashMap<>();
                    for (String id : ids) {
                        statusById.put(id, Notification.RESPONSE_ACCEPTED);
                    }
                    if (notifications != null) {
                        for (Notification n : notifications) {
                            if (n == null || n.getRecipientId() == null || n.getRecipientId().trim().isEmpty()) {
                                continue;
                            }
                            String response = n.getResponse() != null
                                    ? n.getResponse().toLowerCase(Locale.US)
                                    : Notification.RESPONSE_PENDING;
                            if (!Notification.RESPONSE_ACCEPTED.equals(response)
                                    && !Notification.RESPONSE_DECLINED.equals(response)) {
                                response = Notification.RESPONSE_PENDING;
                            }
                            // Organizers list should show accepted + pending only.
                            if (Notification.RESPONSE_DECLINED.equals(response)) {
                                continue;
                            }
                            // Accepted organizers come from the authoritative event.organizers list only.
                            // Notification docs are used for pending invite visibility.
                            if (Notification.RESPONSE_ACCEPTED.equals(response)) {
                                continue;
                            }
                            if (!statusById.containsKey(n.getRecipientId())) {
                                statusById.put(n.getRecipientId(), response);
                            }
                        }
                    }
                    renderOrganizerStatusRows(statusById);
                },
                e -> renderOrganizerStatusRows(new HashMap<>()));
    }

    private void renderOrganizerStatusRows(Map<String, String> statusById) {
        if (layoutOrganizersList == null) return;
        List<String> ids = new ArrayList<>(statusById.keySet());
        if (ids.isEmpty()) {
            tvOrganizersEmpty.setVisibility(View.VISIBLE);
            return;
        }
        tvOrganizersEmpty.setVisibility(View.GONE);
        Map<String, String> namesById = new HashMap<>();
        AtomicInteger pending = new AtomicInteger(ids.size());
        Runnable finish = () -> {
            if (pending.decrementAndGet() != 0) return;
            runOnUiThread(() -> {
                layoutOrganizersList.removeAllViews();
                LayoutInflater inflater = LayoutInflater.from(this);
                List<String> acceptedOrganizerOrder = currentEvent != null
                        ? currentEvent.getMergedOrganizerDeviceIds()
                        : new ArrayList<>();
                String ownerId = acceptedOrganizerOrder.isEmpty() ? null : acceptedOrganizerOrder.get(0);
                for (String id : ids) {
                    View row = inflater.inflate(
                            R.layout.item_event_manage_organizer_status_row, layoutOrganizersList, false);
                    TextView tvName = row.findViewById(R.id.tv_organizer_name);
                    TextView tvId = row.findViewById(R.id.tv_organizer_id);
                    TextView tvStatus = row.findViewById(R.id.tv_organizer_status);
                    View btnRemove = row.findViewById(R.id.btn_remove_organizer);
                    tvName.setText(namesById.getOrDefault(id, id));
                    tvId.setText(id);
                    String status = statusById.get(id);
                    bindOrganizerStatusTag(tvStatus, status, id, ownerId);
                    boolean isPending = Notification.RESPONSE_PENDING.equals(status);
                    boolean isCurrentUser = currentDeviceId != null && currentDeviceId.equals(id);
                    boolean isOwnerRow = ownerId != null && ownerId.equals(id);
                    boolean isCurrentUserOwner = ownerId != null && ownerId.equals(currentDeviceId);
                    boolean canRemove = !isPending && (acceptedOrganizerOrder.size() > 1 || !isCurrentUser);
                    // Co-organizers should never see remove on the owner row.
                    if (isOwnerRow && !isCurrentUserOwner) {
                        canRemove = false;
                    }
                    if (btnRemove != null) {
                        btnRemove.setVisibility(canRemove ? View.VISIBLE : View.GONE);
                        btnRemove.setOnClickListener(v -> showRemoveOrganizerConfirmDialog(id));
                    }
                    layoutOrganizersList.addView(row);
                }
            });
        };
        for (String id : ids) {
            profileDB.getProfile(id, profile -> {
                String name = (profile != null && profile.getName() != null && !profile.getName().isEmpty())
                        ? profile.getName() : id;
                synchronized (namesById) {
                    namesById.put(id, name);
                }
                finish.run();
            }, e -> {
                synchronized (namesById) {
                    namesById.put(id, id);
                }
                finish.run();
            });
        }
    }

    private void bindOrganizerStatusTag(TextView tvStatus, String status, String organizerId, String ownerId) {
        if (tvStatus == null) return;
        String normalized = status != null ? status.toLowerCase(Locale.US) : Notification.RESPONSE_PENDING;
        if (Notification.RESPONSE_ACCEPTED.equals(normalized)) {
            if (ownerId != null && ownerId.equals(organizerId)) {
                tvStatus.setText(R.string.event_manage_organizer_status_owner);
            } else {
                tvStatus.setText(R.string.event_manage_organizer_status_co_organizer);
            }
            tvStatus.setBackgroundResource(R.drawable.bg_notif_badge_accepted);
            tvStatus.setTextColor(ContextCompat.getColor(this, R.color.notif_accepted_text));
        } else if (Notification.RESPONSE_DECLINED.equals(normalized)) {
            tvStatus.setText(R.string.event_manage_organizer_status_declined);
            tvStatus.setBackgroundResource(R.drawable.bg_notif_badge_declined);
            tvStatus.setTextColor(ContextCompat.getColor(this, R.color.notif_declined_text));
        } else {
            tvStatus.setText(R.string.event_manage_organizer_status_pending);
            tvStatus.setBackgroundResource(R.drawable.bg_attendee_tab_item);
            tvStatus.setTextColor(ContextCompat.getColor(this, R.color.event_manage_meta));
        }
    }

    private void removeOrganizer(String organizerDeviceId) {
        if (currentEvent == null || currentEvent.getEventId() == null) {
            Toast.makeText(this, "Event not loaded yet", Toast.LENGTH_SHORT).show();
            return;
        }
        eventDB.removeOrganizerEnsuringAtLeastOne(currentEvent.getEventId(), organizerDeviceId,
                unused -> runOnUiThread(this::loadEventData),
                err -> runOnUiThread(() -> {
                    if (err != null && EventDB.ERR_LAST_ORGANIZER.equals(err.getMessage())) {
                        Toast.makeText(this, R.string.event_manage_err_last_organizer, Toast.LENGTH_LONG).show();
                    } else {
                        Toast.makeText(this, R.string.comments_action_failed, Toast.LENGTH_SHORT).show();
                    }
                }));
    }

    private void showRemoveOrganizerConfirmDialog(String organizerDeviceId) {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_leave_waitlist_confirm, null);
        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setView(dialogView)
                .create();

        TextView tvTitle = dialogView.findViewById(R.id.tv_title);
        TextView tvMessage = dialogView.findViewById(R.id.tv_message);
        TextView btnCancel = dialogView.findViewById(R.id.btn_cancel);
        TextView btnLeave = dialogView.findViewById(R.id.btn_leave);

        if (tvTitle != null) {
            tvTitle.setText(R.string.event_manage_remove_organizer_title);
        }
        if (tvMessage != null) {
            tvMessage.setText(R.string.event_manage_remove_organizer_message);
        }
        if (btnLeave != null) {
            btnLeave.setText(R.string.event_manage_remove_organizer_confirm);
            btnLeave.setOnClickListener(v -> {
                dialog.dismiss();
                removeOrganizer(organizerDeviceId);
            });
        }
        if (btnCancel != null) {
            btnCancel.setOnClickListener(v -> dialog.dismiss());
        }
        dialog.show();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }
    }

    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }

    private boolean isCurrentDeviceOrganizer(Event event) {
        if (event == null || currentDeviceId == null || currentDeviceId.trim().isEmpty()) {
            return false;
        }
        return event.isDeviceAnOrganizer(currentDeviceId);
    }

    private void verifyOrganizerAccessAndThen(Runnable onAllowed) {
        if (eventId == null || eventId.trim().isEmpty()) {
            finish();
            return;
        }
        eventDB.getEventFromServer(eventId, fresh -> runOnUiThread(() -> {
            if (fresh == null || !isCurrentDeviceOrganizer(fresh)) {
                handleKickedFromEvent();
                return;
            }
            currentEvent = fresh;
            if (onAllowed != null) {
                onAllowed.run();
            }
        }), e -> runOnUiThread(() ->
                Toast.makeText(this, "Failed to verify organizer access", Toast.LENGTH_SHORT).show()));
    }

    private void handleKickedFromEvent() {
        if (kickedFromEventHandled) {
            return;
        }
        kickedFromEventHandled = true;
        Toast.makeText(this, "you were kicked from event", Toast.LENGTH_LONG).show();
        Intent intent = new Intent(this, OrganizerActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void ensureCommentsBound() {
        if (currentEvent == null || currentEvent.getEventId() == null) {
            Toast.makeText(this, "Event not loaded yet", Toast.LENGTH_SHORT).show();
            return;
        }
        View root = findViewById(R.id.layout_event_comments_section);
        if (root == null) {
            return;
        }
        String eid = currentEvent.getEventId();
        if (eid.equals(commentsBoundEventId)) {
            return;
        }
        EntrantDB entrantDB = new EntrantDB(this);
        Entrant entrant = entrantDB.getEntrant();
        String deviceId = entrant.getDeviceId();
        String userName = entrant.getName();
        EventCommentsUiBinder.setCommentsExpandedForEvent(eid, true);
        EventCommentsUiBinder.bindManage(root, currentEvent, deviceId, userName, () -> { },
                () -> loadEventData(true));
        commentsBoundEventId = eid;
    }

    private void loadEntrants() {
        loadEntrants(null);
    }

    /**
     * @param onPullRefreshSegmentDone when pull-to-refresh is active, invoked when entrants (including
     *                                 profile names) have finished loading; paired with {@link #refreshTabCountsFromServer}
     */
    private void loadEntrants(Runnable onPullRefreshSegmentDone) {
        waitingListDB.getEntrantsForEvent(eventId, Source.SERVER, registrations -> {
            allEntrants = registrations;
            if (registrations.isEmpty()) {
                loadingContainer.setVisibility(View.GONE);
                showFilteredEntrants();
                if (onPullRefreshSegmentDone != null) {
                    onPullRefreshSegmentDone.run();
                }
                return;
            }
            loadProfileNames(registrations, onPullRefreshSegmentDone);
        }, e -> {
            loadingContainer.setVisibility(View.GONE);
            if (onPullRefreshSegmentDone != null) {
                onPullRefreshSegmentDone.run();
            }
            Toast.makeText(this, "Failed to load entrants: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        });
    }

    private void loadProfileNames(List<WaitingList> registrations) {
        loadProfileNames(registrations, null);
    }

    private void loadProfileNames(List<WaitingList> registrations, Runnable onComplete) {
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
                    if (onComplete != null) {
                        onComplete.run();
                    }
                }
            }, e -> {
                names.put(reg.getDeviceId(), reg.getDeviceId());
                if (pending.decrementAndGet() == 0) {
                    adapter.setNames(names);
                    loadingContainer.setVisibility(View.GONE);
                    showFilteredEntrants();
                    if (onComplete != null) {
                        onComplete.run();
                    }
                }
            });
        }
    }

    /**
     * Tab labels: pending, selected (invited), enrolled, and declined counts from the waitlist
     * {@code entries} subcollection. All queries use the server only.
     */
    private void refreshTabCountsFromServer(Runnable onComplete) {
        if (eventId == null) {
            if (onComplete != null) {
                onComplete.run();
            }
            return;
        }
        final AtomicReference<Integer> pendingC = new AtomicReference<>();
        final AtomicReference<Integer> invitedC = new AtomicReference<>();
        final AtomicReference<Integer> enrolledC = new AtomicReference<>();
        final AtomicReference<Integer> declinedNeedC = new AtomicReference<>();
        final AtomicReference<Integer> declinedFoundC = new AtomicReference<>();
        final AtomicInteger finished = new AtomicInteger(0);

        Runnable maybeApply = () -> {
            if (finished.incrementAndGet() < 5) {
                return;
            }
            int p = pendingC.get() != null ? pendingC.get() : 0;
            int i = invitedC.get() != null ? invitedC.get() : 0;
            int e = enrolledC.get() != null ? enrolledC.get() : 0;
            int declinedNeed = declinedNeedC.get() != null ? declinedNeedC.get() : 0;
            int declinedFound = declinedFoundC.get() != null ? declinedFoundC.get() : 0;
            int d = declinedNeed + declinedFound;
            runOnUiThread(() -> {
                tvCountWaitlisted.setText(String.valueOf(p));
                tvCountInvited.setText(String.valueOf(i));
                tvCountConfirmed.setText(String.valueOf(e));
                tabWaitlisted.setText("Waitlisted (" + p + ")");
                tabInvited.setText("Invited (" + i + ")");
                tabConfirmed.setText("Confirmed (" + e + ")");
                tabDeclined.setText("Declined (" + d + ")");
                if (tabDeclinedNeedReplacement != null) {
                    tabDeclinedNeedReplacement.setText(
                            getString(R.string.event_manage_declined_need_replacement, declinedNeed));
                }
                if (tabDeclinedFoundReplacement != null) {
                    tabDeclinedFoundReplacement.setText(
                            getString(R.string.event_manage_declined_found_replacement, declinedFound));
                }
                if (onComplete != null) {
                    onComplete.run();
                }
            });
        };

        waitingListDB.countLotteryEligibleEntries(eventId,
                n -> {
                    pendingC.set(n);
                    maybeApply.run();
                },
                e -> {
                    runOnUiThread(() ->
                            Toast.makeText(this, R.string.event_manage_lottery_load_pending_failed, Toast.LENGTH_SHORT).show());
                    pendingC.set(0);
                    maybeApply.run();
                });

        waitingListDB.countEntriesWithStatus(eventId, WaitingList.STATUS_SELECTED,
                n -> {
                    invitedC.set(n);
                    maybeApply.run();
                },
                e -> {
                    runOnUiThread(() ->
                            Toast.makeText(this, R.string.event_manage_lottery_load_pending_failed, Toast.LENGTH_SHORT).show());
                    invitedC.set(0);
                    maybeApply.run();
                });

        waitingListDB.countEntriesWithStatus(eventId, WaitingList.STATUS_ENROLLED,
                n -> {
                    enrolledC.set(n);
                    maybeApply.run();
                },
                e -> {
                    runOnUiThread(() ->
                            Toast.makeText(this, R.string.event_manage_lottery_load_pending_failed, Toast.LENGTH_SHORT).show());
                    enrolledC.set(0);
                    maybeApply.run();
                });

        waitingListDB.countEntriesWithStatus(eventId, WaitingList.STATUS_DECLINED,
                n -> {
                    declinedNeedC.set(n);
                    maybeApply.run();
                },
                e -> {
                    runOnUiThread(() ->
                            Toast.makeText(this, R.string.event_manage_lottery_load_pending_failed, Toast.LENGTH_SHORT).show());
                    declinedNeedC.set(0);
                    maybeApply.run();
                });

        waitingListDB.countEntriesWithStatus(eventId, WaitingList.STATUS_DECLINED_FOUND_REPLACEMENT,
                n -> {
                    declinedFoundC.set(n);
                    maybeApply.run();
                },
                e -> {
                    runOnUiThread(() ->
                            Toast.makeText(this, R.string.event_manage_lottery_load_pending_failed, Toast.LENGTH_SHORT).show());
                    declinedFoundC.set(0);
                    maybeApply.run();
                });

    }

    private void selectTab(String status) {
        currentTab = status;
        switch (status) {
            case WaitingList.STATUS_PENDING:
                setTabActive(tabWaitlisted); break;
            case WaitingList.STATUS_SELECTED:
                setTabActive(tabInvited); break;
            case WaitingList.STATUS_ENROLLED:
                setTabActive(tabConfirmed); break;
            default:
                setTabActive(tabDeclined); break;
        }
        if (declinedSubtabsContainer != null) {
            declinedSubtabsContainer.setVisibility(TAB_DECLINED_ONLY.equals(status) ? View.VISIBLE : View.GONE);
        }
        if (TAB_DECLINED_ONLY.equals(status)) {
            selectDeclinedSubtab(currentDeclinedSubtab);
        }
        updateNotifyAllButtonLabel();
        showFilteredEntrants();
    }

    private void selectDeclinedSubtab(String subtabStatus) {
        currentDeclinedSubtab = subtabStatus;
        if (tabDeclinedNeedReplacement != null) {
            tabDeclinedNeedReplacement.setSelected(TAB_DECLINED_NEED_REPLACEMENT.equals(subtabStatus));
        }
        if (tabDeclinedFoundReplacement != null) {
            tabDeclinedFoundReplacement.setSelected(TAB_DECLINED_FOUND_REPLACEMENT.equals(subtabStatus));
        }
        if (TAB_DECLINED_ONLY.equals(currentTab)) {
            updateNotifyAllButtonLabel();
            showFilteredEntrants();
        }
    }

    private void updateNotifyAllButtonLabel() {
        if (tvNotifyAllCurrent == null) return;
        if (WaitingList.STATUS_SELECTED.equals(currentTab)) {
            tvNotifyAllCurrent.setText(R.string.event_manage_notify_all_invited);
            return;
        }
        if (WaitingList.STATUS_ENROLLED.equals(currentTab)) {
            tvNotifyAllCurrent.setText(R.string.event_manage_notify_all_confirmed);
            return;
        }
        if (TAB_DECLINED_ONLY.equals(currentTab)) {
            if (TAB_DECLINED_FOUND_REPLACEMENT.equals(currentDeclinedSubtab)) {
                tvNotifyAllCurrent.setText(R.string.event_manage_notify_all_found_replacement);
            } else {
                tvNotifyAllCurrent.setText(R.string.event_manage_notify_all_need_replacement);
            }
            return;
        }
        tvNotifyAllCurrent.setText(R.string.event_manage_notify_all_waitlisted);
    }

    private void showNotifyAllDialog() {
        if (currentEvent == null || currentEvent.getEventId() == null || currentEvent.getEventId().trim().isEmpty()) {
            Toast.makeText(this, "Event not loaded yet", Toast.LENGTH_SHORT).show();
            return;
        }
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_event_manage_notify_alert, null);
        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setView(dialogView)
                .create();
        TextView tvScope = dialogView.findViewById(R.id.tv_notify_dialog_scope);
        EditText input = dialogView.findViewById(R.id.et_notify_dialog_message);
        View btnCancel = dialogView.findViewById(R.id.btn_notify_dialog_cancel);
        View btnSend = dialogView.findViewById(R.id.btn_notify_dialog_send);

        if (tvScope != null) {
            CharSequence scope = tvNotifyAllCurrent != null ? tvNotifyAllCurrent.getText() : "";
            tvScope.setText(getString(R.string.event_manage_notify_dialog_scope_format, scope));
        }
        if (btnCancel != null) {
            btnCancel.setOnClickListener(v -> dialog.dismiss());
        }
        if (btnSend != null && input != null) {
            btnSend.setOnClickListener(v -> {
                String message = input.getText() != null ? input.getText().toString().trim() : "";
                if (message.isEmpty()) {
                    input.setError(getString(R.string.event_manage_notify_dialog_required));
                    return;
                }
                dialog.dismiss();
                sendNotifyAllForCurrentTab(message);
            });
        }

        dialog.show();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }
    }

    private void sendNotifyAllForCurrentTab(String userMessage) {
        if (currentEvent == null || currentEvent.getEventId() == null || currentEvent.getEventId().trim().isEmpty()) {
            Toast.makeText(this, "Event not loaded yet", Toast.LENGTH_SHORT).show();
            return;
        }
        waitingListDB.getEntrantsForEvent(currentEvent.getEventId(), Source.SERVER, registrations -> {
            LinkedHashSet<String> recipientIds = new LinkedHashSet<>();
            if (registrations != null) {
                for (WaitingList reg : registrations) {
                    if (reg == null || reg.getDeviceId() == null || reg.getDeviceId().trim().isEmpty()) continue;
                    if (matchesNotifyBucketForCurrentTab(reg.getStatus())) {
                        recipientIds.add(reg.getDeviceId());
                    }
                }
            }
            List<String> targets = new ArrayList<>(recipientIds);
            if (targets.isEmpty()) {
                runOnUiThread(() ->
                        Toast.makeText(this, R.string.event_manage_notify_dialog_none, Toast.LENGTH_SHORT).show());
                return;
            }
            sendEventAlertNotificationsSequentially(targets, 0, userMessage, () -> runOnUiThread(() ->
                    Toast.makeText(this,
                            getString(R.string.event_manage_notify_dialog_sent_format, targets.size()),
                            Toast.LENGTH_SHORT).show()));
        }, e -> runOnUiThread(() ->
                Toast.makeText(this, R.string.comments_action_failed, Toast.LENGTH_SHORT).show()));
    }

    private void showNotifyEntireWaitlistDialog() {
        if (currentEvent == null || currentEvent.getEventId() == null || currentEvent.getEventId().trim().isEmpty()) {
            Toast.makeText(this, "Event not loaded yet", Toast.LENGTH_SHORT).show();
            return;
        }
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_event_manage_notify_alert, null);
        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setView(dialogView)
                .create();
        TextView tvScope = dialogView.findViewById(R.id.tv_notify_dialog_scope);
        EditText input = dialogView.findViewById(R.id.et_notify_dialog_message);
        View btnCancel = dialogView.findViewById(R.id.btn_notify_dialog_cancel);
        View btnSend = dialogView.findViewById(R.id.btn_notify_dialog_send);

        if (tvScope != null) {
            tvScope.setText(getString(R.string.event_manage_notify_dialog_scope_format,
                    getString(R.string.event_manage_notify_entire_waitlist_scope)));
        }
        if (btnCancel != null) {
            btnCancel.setOnClickListener(v -> dialog.dismiss());
        }
        if (btnSend != null && input != null) {
            btnSend.setOnClickListener(v -> {
                String message = input.getText() != null ? input.getText().toString().trim() : "";
                if (message.isEmpty()) {
                    input.setError(getString(R.string.event_manage_notify_dialog_required));
                    return;
                }
                dialog.dismiss();
                sendNotifyEntireWaitlist(message);
            });
        }

        dialog.show();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }
    }

    private void sendNotifyEntireWaitlist(String userMessage) {
        if (currentEvent == null || currentEvent.getEventId() == null || currentEvent.getEventId().trim().isEmpty()) {
            Toast.makeText(this, "Event not loaded yet", Toast.LENGTH_SHORT).show();
            return;
        }
        waitingListDB.getEntrantsForEvent(currentEvent.getEventId(), Source.SERVER, registrations -> {
            LinkedHashSet<String> recipientIds = new LinkedHashSet<>();
            if (registrations != null) {
                for (WaitingList reg : registrations) {
                    if (reg == null || reg.getDeviceId() == null || reg.getDeviceId().trim().isEmpty()) {
                        continue;
                    }
                    recipientIds.add(reg.getDeviceId().trim());
                }
            }
            List<String> targets = new ArrayList<>(recipientIds);
            if (targets.isEmpty()) {
                runOnUiThread(() ->
                        Toast.makeText(this, R.string.event_manage_notify_dialog_none, Toast.LENGTH_SHORT).show());
                return;
            }
            sendEventAlertNotificationsSequentially(targets, 0, userMessage, () -> runOnUiThread(() ->
                    Toast.makeText(this,
                            getString(R.string.event_manage_notify_dialog_sent_format, targets.size()),
                            Toast.LENGTH_SHORT).show()));
        }, e -> runOnUiThread(() ->
                Toast.makeText(this, R.string.comments_action_failed, Toast.LENGTH_SHORT).show()));
    }

    private boolean matchesNotifyBucketForCurrentTab(String status) {
        if (WaitingList.STATUS_SELECTED.equals(currentTab)) {
            return WaitingList.STATUS_SELECTED.equals(status);
        }
        if (WaitingList.STATUS_ENROLLED.equals(currentTab)) {
            return WaitingList.STATUS_ENROLLED.equals(status);
        }
        if (TAB_DECLINED_ONLY.equals(currentTab)) {
            if (TAB_DECLINED_FOUND_REPLACEMENT.equals(currentDeclinedSubtab)) {
                return WaitingList.STATUS_DECLINED_FOUND_REPLACEMENT.equals(status);
            }
            return WaitingList.STATUS_DECLINED.equals(status);
        }
        return WaitingList.STATUS_PENDING.equals(status)
                || WaitingList.STATUS_NOT_SELECTED.equals(status);
    }

    private void sendEventAlertNotificationsSequentially(List<String> targetIds,
                                                         int index,
                                                         String userMessage,
                                                         Runnable onComplete) {
        if (index >= targetIds.size()) {
            if (onComplete != null) onComplete.run();
            return;
        }
        String recipientId = targetIds.get(index);
        Notification alert = new Notification(
                recipientId,
                currentEvent.getEventId(),
                getString(R.string.notification_event_alert_title),
                userMessage,
                Notification.TYPE_EVENT_ALERT
        );
        alert.setRecipientMode(Notification.RECIPIENT_MODE_USER);
        alert.setResponse(null);
        notificationDB.saveNotification(alert,
                unused -> sendEventAlertNotificationsSequentially(targetIds, index + 1, userMessage, onComplete),
                e -> runOnUiThread(() ->
                        Toast.makeText(this, R.string.comments_action_failed, Toast.LENGTH_SHORT).show()));
    }

    private void exportEnrolledEntrantsCsv() {
        if (eventId == null || eventId.trim().isEmpty()) {
            Toast.makeText(this, "Event not loaded yet", Toast.LENGTH_SHORT).show();
            return;
        }
        waitingListDB.getEntrantsForEvent(eventId, Source.SERVER, registrations -> {
            List<WaitingList> enrolled = new ArrayList<>();
            if (registrations != null) {
                for (WaitingList reg : registrations) {
                    if (reg != null && WaitingList.STATUS_ENROLLED.equals(reg.getStatus())) {
                        enrolled.add(reg);
                    }
                }
            }
            if (enrolled.isEmpty()) {
                runOnUiThread(() ->
                        Toast.makeText(this, "No confirmed (enrolled) entrants to export.", Toast.LENGTH_SHORT).show());
                return;
            }
            String csv = buildEnrolledEntrantsCsv(enrolled);
            pendingCsvBytes = csv.getBytes(StandardCharsets.UTF_8);
            String safeName = currentEvent != null && currentEvent.getName() != null
                    ? currentEvent.getName().trim().replaceAll("[^a-zA-Z0-9-_ ]", "")
                    : "event";
            if (safeName.isEmpty()) safeName = "event";
            pendingCsvFileName = safeName + "-confirmed-attendees.csv";
            runOnUiThread(() -> createCsvDocumentLauncher.launch(pendingCsvFileName));
        }, e -> runOnUiThread(() ->
                Toast.makeText(this, "Failed to load attendees for CSV export.", Toast.LENGTH_SHORT).show()));
    }

    private String buildEnrolledEntrantsCsv(List<WaitingList> enrolled) {
        StringBuilder sb = new StringBuilder();
        sb.append("Name,Email,Phone,Device ID,Status,Participants,Notification Method,Joined At,Latitude,Longitude\n");
        for (WaitingList reg : enrolled) {
            String joined = formatJoinedAtForCsv(reg);
            sb.append(csvEscape(reg.getName())).append(',')
                    .append(csvEscape(reg.getEmail())).append(',')
                    .append(csvEscape(formatPhoneForCsv(reg.getPhone()))).append(',')
                    .append(csvEscape(reg.getDeviceId())).append(',')
                    .append(csvEscape(reg.getStatus())).append(',')
                    .append(reg.getNumParticipants()).append(',')
                    .append(csvEscape(reg.getNotificationMethod())).append(',')
                    .append(csvEscape(joined)).append(',')
                    .append(reg.getLatitude() != null ? reg.getLatitude() : "").append(',')
                    .append(reg.getLongitude() != null ? reg.getLongitude() : "")
                    .append('\n');
        }
        return sb.toString();
    }

    private String csvEscape(String value) {
        if (value == null) return "";
        String escaped = value.replace("\"", "\"\"");
        return "\"" + escaped + "\"";
    }

    private String formatPhoneForCsv(String rawPhone) {
        if (rawPhone == null) return "";
        String trimmed = rawPhone.trim();
        if (trimmed.isEmpty()) return "";

        String digits = trimmed.replaceAll("[^0-9]", "");
        if (digits.length() == 10) {
            return "(" + digits.substring(0, 3) + ") "
                    + digits.substring(3, 6) + "-"
                    + digits.substring(6);
        }
        if (digits.length() == 11 && digits.startsWith("1")) {
            return "+1 "
                    + digits.substring(1, 4) + "-"
                    + digits.substring(4, 7) + "-"
                    + digits.substring(7);
        }
        return trimmed;
    }

    private String formatJoinedAtForCsv(WaitingList reg) {
        if (reg == null || reg.getRegisteredAt() == null) return "";
        // Prefix with apostrophe so spreadsheet apps keep this as text (avoids #### rendering).
        return "'" + DATE_FORMAT.format(reg.getRegisteredAt().toDate())
                + " " + TIME_FORMAT.format(reg.getRegisteredAt().toDate());
    }

    private void confirmRescindInvite(WaitingList reg) {
        if (reg == null || eventId == null || eventId.trim().isEmpty()) {
            return;
        }
        if (!WaitingList.STATUS_SELECTED.equals(reg.getStatus())) {
            Toast.makeText(this, R.string.event_manage_rescind_invite_failed, Toast.LENGTH_SHORT).show();
            return;
        }
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_rescind_invite_confirm, null);
        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setView(dialogView)
                .create();

        TextView tvSubtitle = dialogView.findViewById(R.id.tv_rescind_subtitle);
        if (tvSubtitle != null) {
            String name = reg.getName();
            if (name != null && !name.trim().isEmpty()) {
                tvSubtitle.setVisibility(View.VISIBLE);
                tvSubtitle.setText(getString(R.string.event_manage_rescind_invite_subtitle, name.trim()));
            } else {
                tvSubtitle.setVisibility(View.GONE);
            }
        }

        View btnCancel = dialogView.findViewById(R.id.btn_rescind_cancel);
        View btnConfirm = dialogView.findViewById(R.id.btn_rescind_confirm);
        if (btnCancel != null) {
            btnCancel.setOnClickListener(v -> dialog.dismiss());
        }
        if (btnConfirm != null) {
            btnConfirm.setOnClickListener(v -> {
                dialog.dismiss();
                performRescindInvite(reg);
            });
        }

        dialog.show();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }
    }

    private void performRescindInvite(WaitingList reg) {
        if (reg == null || eventId == null || eventId.trim().isEmpty()) {
            return;
        }
        if (!WaitingList.STATUS_SELECTED.equals(reg.getStatus())) {
            runOnUiThread(() ->
                    Toast.makeText(this, R.string.event_manage_rescind_invite_failed, Toast.LENGTH_SHORT).show());
            return;
        }
        String entryDocId = reg.getId();
        if (entryDocId == null || entryDocId.isEmpty()) {
            entryDocId = reg.getDeviceId();
        }
        String deviceId = reg.getDeviceId();
        if (entryDocId == null || entryDocId.isEmpty() || deviceId == null || deviceId.isEmpty()) {
            runOnUiThread(() ->
                    Toast.makeText(this, R.string.event_manage_rescind_invite_failed, Toast.LENGTH_SHORT).show());
            return;
        }
        waitingListDB.rescindSelectionInviteIfStillSelected(eventId, entryDocId,
                outcome -> {
                    if (outcome == RescindSelectionInviteOutcome.APPLIED) {
                        notificationDB.deleteLotteryInviteNotifications(deviceId, eventId,
                                v2 -> runOnUiThread(() -> {
                                    Toast.makeText(this, R.string.event_manage_rescind_invite_success,
                                            Toast.LENGTH_SHORT).show();
                                    loadEventData(true);
                                }),
                                e2 -> runOnUiThread(() -> {
                                    Toast.makeText(this, R.string.event_manage_rescind_invite_notif_cleanup_failed,
                                            Toast.LENGTH_LONG).show();
                                    loadEventData(true);
                                }));
                    } else if (outcome == RescindSelectionInviteOutcome.ALREADY_ENROLLED) {
                        runOnUiThread(() -> {
                            Toast.makeText(this, R.string.event_manage_rescind_invite_entrant_enrolled,
                                    Toast.LENGTH_LONG).show();
                            loadEventData(true);
                        });
                    } else {
                        runOnUiThread(() -> {
                            Toast.makeText(this, R.string.event_manage_rescind_invite_already_done,
                                    Toast.LENGTH_SHORT).show();
                            loadEventData(true);
                        });
                    }
                },
                e -> runOnUiThread(() -> {
                    Toast.makeText(this, R.string.event_manage_rescind_invite_failed, Toast.LENGTH_SHORT).show();
                    loadEventData(true);
                }));
    }

    private void showFilteredEntrants() {
        List<WaitingList> filtered = new ArrayList<>();
        for (WaitingList reg : allEntrants) {
            String s = reg.getStatus();
            boolean include;
            switch (currentTab) {
                case WaitingList.STATUS_PENDING:
                    include = WaitingList.STATUS_PENDING.equals(s)
                            || WaitingList.STATUS_NOT_SELECTED.equals(s);
                    break;
                case WaitingList.STATUS_SELECTED:
                    include = WaitingList.STATUS_SELECTED.equals(s); break;
                case WaitingList.STATUS_ENROLLED:
                    include = WaitingList.STATUS_ENROLLED.equals(s); break;
                case TAB_DECLINED_ONLY:
                    include = currentDeclinedSubtab.equals(s);
                    break;
                default:
                    include = false;
            }
            if (include) filtered.add(reg);
        }
        adapter.setItems(filtered, WaitingList.STATUS_SELECTED.equals(currentTab));
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
        for (TextView tab : new TextView[]{tabWaitlisted, tabInvited, tabConfirmed, tabDeclined}) {
            tab.setSelected(tab == activeTab);
        }
    }

    private String messageForLotteryFailure(Throwable err) {
        String code = firstLotteryCodeInChain(err);
        if (code == null) {
            if (err == null || err.getMessage() == null) {
                return getString(R.string.event_manage_lottery_load_pending_failed);
            }
            return err.getMessage();
        }
        if (LotteryErrorCodes.NO_PENDING_ENTRANTS.equals(code)) {
            return getString(R.string.event_manage_lottery_err_no_pending);
        }
        if (LotteryErrorCodes.REQUEST_EXCEEDS_PENDING.equals(code)) {
            return getString(R.string.event_manage_lottery_err_exceeds_pending);
        }
        if (LotteryErrorCodes.REQUEST_EXCEEDS_REPLACEMENT_CAPACITY.equals(code)) {
            return getString(R.string.event_manage_lottery_err_replacement_capacity);
        }
        if (LotteryErrorCodes.NO_CAPACITY.equals(code)) {
            return getString(R.string.event_manage_lottery_err_no_capacity);
        }
        if (LotteryErrorCodes.TOO_MANY_WAITS.equals(code)) {
            return getString(R.string.event_manage_lottery_err_too_many_waits, 500);
        }
        return code;
    }

    /** Firestore may wrap the exception from a failed transaction; walk the cause chain for our codes. */
    private static String firstLotteryCodeInChain(Throwable err) {
        for (Throwable t = err; t != null; t = t.getCause()) {
            String m = t.getMessage();
            if (m == null) {
                continue;
            }
            if (LotteryErrorCodes.NO_PENDING_ENTRANTS.equals(m)
                    || LotteryErrorCodes.REQUEST_EXCEEDS_PENDING.equals(m)
                    || LotteryErrorCodes.REQUEST_EXCEEDS_REPLACEMENT_CAPACITY.equals(m)
                    || LotteryErrorCodes.NO_CAPACITY.equals(m)
                    || LotteryErrorCodes.TOO_MANY_WAITS.equals(m)) {
                return m;
            }
        }
        return null;
    }

    private void showRunLotteryDialog() {
        if (!NetworkConnectivity.hasValidatedInternet(this)) {
            Toast.makeText(this, R.string.notification_no_internet, Toast.LENGTH_SHORT).show();
            return;
        }
        waitingListDB.countLotteryEligibleEntries(eventId,
                eligibleCount -> runOnUiThread(() -> showRunLotteryDialogWithPendingCount(eligibleCount)),
                e -> runOnUiThread(() ->
                        Toast.makeText(this, R.string.event_manage_lottery_load_pending_failed, Toast.LENGTH_LONG).show()));
    }

    private void showFindReplacementDialog() {
        if (!NetworkConnectivity.hasValidatedInternet(this)) {
            Toast.makeText(this, R.string.notification_no_internet, Toast.LENGTH_SHORT).show();
            return;
        }
        final AtomicReference<Integer> declinedNeedCount = new AtomicReference<>();
        final AtomicReference<Integer> replacementPoolCount = new AtomicReference<>();
        final AtomicInteger done = new AtomicInteger(0);
        Runnable maybeShow = () -> {
            if (done.incrementAndGet() < 2) return;
            int declinedNeed = declinedNeedCount.get() != null ? declinedNeedCount.get() : 0;
            int replacementPool = replacementPoolCount.get() != null ? replacementPoolCount.get() : 0;
            int max = Math.min(declinedNeed, replacementPool);
            runOnUiThread(() -> showFindReplacementDialogWithMax(max));
        };

        waitingListDB.countDeclinedNeedReplacementEntries(eventId,
                n -> {
                    declinedNeedCount.set(n);
                    maybeShow.run();
                },
                e -> runOnUiThread(() ->
                        Toast.makeText(this, R.string.event_manage_lottery_load_pending_failed, Toast.LENGTH_LONG).show()));

        waitingListDB.countReplacementPoolEntries(eventId,
                n -> {
                    replacementPoolCount.set(n);
                    maybeShow.run();
                },
                e -> runOnUiThread(() ->
                        Toast.makeText(this, R.string.event_manage_lottery_load_pending_failed, Toast.LENGTH_LONG).show()));
    }

    private void showFindReplacementDialogWithMax(int maxReplaceable) {
        if (maxReplaceable <= 0) {
            Toast.makeText(this, R.string.event_manage_lottery_err_replacement_capacity, Toast.LENGTH_LONG).show();
            return;
        }
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_run_lottery, null);
        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setView(dialogView)
                .create();

        TextView tvTitle = dialogView.findViewById(R.id.tv_run_lottery_title);
        TextView tvBody = dialogView.findViewById(R.id.tv_run_lottery_body);
        TextView tvCountLabel = dialogView.findViewById(R.id.tv_count_label);
        EditText etCount = dialogView.findViewById(R.id.et_attendee_count);
        TextView tvHelper = dialogView.findViewById(R.id.tv_waitlist_helper);
        TextView btnConfirm = dialogView.findViewById(R.id.btn_run_lottery);

        if (tvTitle != null) tvTitle.setText(R.string.dialog_find_replacement_title);
        if (tvBody != null) tvBody.setText(R.string.dialog_find_replacement_body);
        if (tvCountLabel != null) tvCountLabel.setText(R.string.dialog_find_replacement_count_label);
        if (btnConfirm != null) btnConfirm.setText(R.string.dialog_find_replacement_confirm);
        etCount.setHint(getString(R.string.dialog_run_lottery_hint_max, maxReplaceable));
        tvHelper.setText(getResources().getQuantityString(
                R.plurals.dialog_run_lottery_waitlist_helper, maxReplaceable, maxReplaceable));

        dialogView.findViewById(R.id.btn_cancel).setOnClickListener(v -> dialog.dismiss());
        dialogView.findViewById(R.id.btn_close).setOnClickListener(v -> dialog.dismiss());
        dialogView.findViewById(R.id.btn_run_lottery).setOnClickListener(v -> {
            String raw = etCount.getText() != null ? etCount.getText().toString().trim() : "";
            int n;
            try {
                n = Integer.parseInt(raw);
            } catch (NumberFormatException ex) {
                Toast.makeText(this, R.string.event_manage_lottery_invalid_number, Toast.LENGTH_SHORT).show();
                return;
            }
            if (n <= 0 || n > maxReplaceable) {
                Toast.makeText(this, R.string.event_manage_lottery_invalid_number, Toast.LENGTH_SHORT).show();
                return;
            }
            if (currentEvent == null || currentEvent.getEventId() == null) {
                Toast.makeText(this, "Event not loaded yet", Toast.LENGTH_SHORT).show();
                return;
            }
            v.setEnabled(false);
            lotteryController.runReplacementLottery(currentEvent.getEventId(), n,
                    count -> runOnUiThread(() -> {
                        dialog.dismiss();
                        Toast.makeText(this,
                                getString(R.string.event_manage_replacement_success, count),
                                Toast.LENGTH_LONG).show();
                        loadEventData();
                    }),
                    err -> runOnUiThread(() -> {
                        v.setEnabled(true);
                        Toast.makeText(this, messageForLotteryFailure(err), Toast.LENGTH_LONG).show();
                    }));
        });

        dialog.show();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }
    }

    private void showRunLotteryDialogWithPendingCount(int pending) {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_run_lottery, null);
        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
                .setView(dialogView)
                .create();

        EditText etCount = dialogView.findViewById(R.id.et_attendee_count);
        TextView tvHelper = dialogView.findViewById(R.id.tv_waitlist_helper);
        etCount.setHint(getString(R.string.dialog_run_lottery_hint_max, pending));
        tvHelper.setText(getResources().getQuantityString(
                R.plurals.dialog_run_lottery_waitlist_helper, pending, pending));

        dialogView.findViewById(R.id.btn_cancel).setOnClickListener(v -> dialog.dismiss());
        dialogView.findViewById(R.id.btn_close).setOnClickListener(v -> dialog.dismiss());
        dialogView.findViewById(R.id.btn_run_lottery).setOnClickListener(v -> {
            if (!NetworkConnectivity.hasValidatedInternet(this)) {
                Toast.makeText(this, R.string.notification_no_internet, Toast.LENGTH_SHORT).show();
                return;
            }
            String raw = etCount.getText() != null ? etCount.getText().toString().trim() : "";
            int n;
            try {
                n = Integer.parseInt(raw);
            } catch (NumberFormatException ex) {
                Toast.makeText(this, R.string.event_manage_lottery_invalid_number, Toast.LENGTH_SHORT).show();
                return;
            }
            if (n <= 0) {
                Toast.makeText(this, R.string.event_manage_lottery_invalid_number, Toast.LENGTH_SHORT).show();
                return;
            }
            if (n > pending) {
                Toast.makeText(this, R.string.event_manage_lottery_invalid_number, Toast.LENGTH_SHORT).show();
                return;
            }
            if (currentEvent == null || currentEvent.getEventId() == null) {
                Toast.makeText(this, "Event not loaded yet", Toast.LENGTH_SHORT).show();
                return;
            }
            v.setEnabled(false);
            lotteryController.runLotteryDraw(currentEvent.getEventId(), n,
                    count -> runOnUiThread(() -> {
                        dialog.dismiss();
                        Toast.makeText(this,
                                getString(R.string.event_manage_lottery_success, count),
                                Toast.LENGTH_LONG).show();
                        loadEventData();
                    }),
                    err -> runOnUiThread(() -> {
                        v.setEnabled(true);
                        Toast.makeText(this, messageForLotteryFailure(err), Toast.LENGTH_LONG).show();
                    }));
        });

        dialog.show();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }
    }

    private void showEditEventDialog() {
        if (currentEvent == null) {
            return;
        }
        openEditEventDialog = new EditEventDialog(this, currentEvent, eventDB, imageController,
                () -> runOnUiThread(() -> {
                    loadEventData();
                    populateEventInfo(currentEvent);
                }),
                () -> pickEditPosterLauncher.launch("image/*"),
                editDetailLauncher,
                () -> openEditEventDialog = null);
        openEditEventDialog.show();
    }
}
