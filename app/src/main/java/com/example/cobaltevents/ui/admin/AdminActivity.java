package com.example.cobaltevents.ui.admin;

import android.app.AlertDialog;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.format.DateFormat;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.cobaltevents.R;
import com.example.cobaltevents.controller.AdminController;
import com.example.cobaltevents.model.Entrant;
import com.example.cobaltevents.model.Event;
import com.example.cobaltevents.model.Notification;

import java.util.ArrayList;
import java.util.List;

/**
 * AdminActivity — the main admin dashboard screen.
 *
 * Implements all Admin user stories:
 *   US 03.01.01 — Remove events        (swipe left or tap to view → delete)
 *   US 03.02.01 — Remove profiles      (swipe left or tap to view → delete)
 *   US 03.03.01 — Remove images        (swipe left → image-only or full event delete)
 *   US 03.04.01 — Browse events        (Events tab)
 *   US 03.05.01 — Browse profiles      (Profiles tab)
 *   US 03.06.01 — Browse images        (Images tab — shows events with poster images)
 *   US 03.07.01 — Remove organizers    (Organizers tab — delete profile + their events)
 *   US 03.08.01 — Review notifications (Notifications tab — read-only log)
 *
 * UX features:
 *   - Tabs with underline indicator (blue = selected)
 *   - Debounced search bar (300ms delay before filtering)
 *   - Loading spinner while Firestore data loads
 *   - Swipe LEFT on any card to reveal red delete background
 *   - Tap anywhere on card to open a detail dialog
 *   - Instant optimistic delete: item disappears immediately,
 *     Firestore delete happens in background
 */
public class AdminActivity extends AppCompatActivity {

    // ── Tab views (LinearLayouts support the underline selector drawable) ─────
    private LinearLayout tabEvents, tabProfiles, tabImages, tabOrganizers, tabNotifications;

    // ── Other UI references ───────────────────────────────────────────────────
    private TextView tvSectionTitle, tvSectionCount;
    private RecyclerView adminRecycler;
    private EditText searchBar;
    private HorizontalScrollView adminTabsScroll;
    private TextView emptyMessage;
    private android.widget.ProgressBar loadingSpinner;

    // ── State ─────────────────────────────────────────────────────────────────
    private AdminAdapter adapter;

    /** Master list — holds ALL items for the current tab (unfiltered). */
    private final List<AdminAdapter.AdminItem> allItems = new ArrayList<>();

    /** Current search query (lowercased). Empty string means "show all". */
    private String currentQuery = "";

    /** Which tab is currently visible — used to route delete/view actions. */
    private String currentTab = "Events";

    // ── Controller + detail maps ──────────────────────────────────────────────
    private AdminController adminController;

    /**
     * Stores full Event objects keyed by eventId.
     * Used to show rich detail in the tap-to-view dialog.
     */
    private final java.util.Map<String, Event> eventDetailMap = new java.util.HashMap<>();

    /**
     * Stores full Entrant objects keyed by deviceId.
     * Used for Profiles, Organizers, and resolving organizer names in Images tab.
     */
    private final java.util.Map<String, Entrant> profileDetailMap = new java.util.HashMap<>();

    // ── Debounce handler for search bar ──────────────────────────────────────
    private final Handler searchHandler = new Handler(Looper.getMainLooper());
    private Runnable searchRunnable;

    /** True while a Firestore fetch is in progress — controls spinner visibility. */
    private boolean isLoading = false;

    // =========================================================================
    // onCreate — wires up all views, adapter, swipe, tabs, search
    // =========================================================================

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin);

        adminController = new AdminController();

        // ── Bind tab views ────────────────────────────────────────────────────
        tabEvents        = findViewById(R.id.tab_events);
        tabProfiles      = findViewById(R.id.tab_profiles);
        tabImages        = findViewById(R.id.tab_images);
        tabOrganizers    = findViewById(R.id.tab_organizers);
        tabNotifications = findViewById(R.id.tab_notifications);

        // ── Bind other UI views ───────────────────────────────────────────────
        tvSectionTitle  = findViewById(R.id.tvSectionTitle);
        tvSectionCount  = findViewById(R.id.tvSectionCount);
        searchBar       = findViewById(R.id.etSearch);
        adminRecycler   = findViewById(R.id.adminRecycler);
        adminTabsScroll = findViewById(R.id.adminTabsScroll);
        emptyMessage    = findViewById(R.id.emptyMessage);
        loadingSpinner  = findViewById(R.id.loadingSpinner);

        // ── RecyclerView setup ────────────────────────────────────────────────
        adminRecycler.setLayoutManager(new LinearLayoutManager(this));
        adminRecycler.setHasFixedSize(true);
        adminRecycler.setItemViewCacheSize(20); // Cache 20 off-screen views

        // Adapter receives two callbacks:
        //   onRemove → handleRemoveClick (confirm dialog → Firestore delete)
        //   onView   → handleViewClick   (detail popup)
        adapter = new AdminAdapter(new ArrayList<>(), this::handleRemoveClick, this::handleViewClick);
        adminRecycler.setAdapter(adapter);

        // Hide list/empty message until first data loads
        emptyMessage.setVisibility(View.GONE);
        adminRecycler.setVisibility(View.GONE);

        // ── Swipe left to delete ──────────────────────────────────────────────
        setupSwipeToDelete();

        // ── Tab click listeners ───────────────────────────────────────────────
        tabEvents.setOnClickListener(v -> { AdminController.invalidateAll(); showEvents(); });
        tabProfiles.setOnClickListener(v -> { AdminController.invalidateAll(); showProfiles(); });
        tabImages.setOnClickListener(v -> { AdminController.invalidateAll(); showImages(); });
        tabOrganizers.setOnClickListener(v -> { AdminController.invalidateAll(); showOrganizers(); });
        tabNotifications.setOnClickListener(v -> showNotifications());

        // ── Search bar with 300ms debounce ────────────────────────────────────
        // Debouncing prevents filtering on every single keystroke — waits until
        // the user pauses typing before running the filter.
        searchBar.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable s) {
                currentQuery = s.toString().trim().toLowerCase();
                if (searchRunnable != null) searchHandler.removeCallbacks(searchRunnable);
                searchRunnable = AdminActivity.this::filterCurrentList;
                searchHandler.postDelayed(searchRunnable, 300); // 300ms debounce
            }
        });

        // ── Default tab on open ───────────────────────────────────────────────
        showEvents();
    }

    // =========================================================================
    // Swipe-to-delete setup
    // Swipe LEFT reveals a red "🗑 Delete" background.
    // On full swipe: shows confirm dialog (or snaps back for Notifications).
    // =========================================================================

    private void setupSwipeToDelete() {
        ItemTouchHelper.SimpleCallback swipeCallback = new ItemTouchHelper.SimpleCallback(
                0, ItemTouchHelper.LEFT) {

            @Override
            public boolean onMove(@NonNull RecyclerView rv,
                                  @NonNull RecyclerView.ViewHolder vh,
                                  @NonNull RecyclerView.ViewHolder target) {
                return false; // We don't support drag-to-reorder
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
                int pos = viewHolder.getAdapterPosition();
                if (pos < 0 || pos >= adapter.getItems().size()) return;

                AdminAdapter.AdminItem item = adapter.getItems().get(pos);

                // Notifications tab is read-only — snap the card back
                if (currentTab.equals("Notifications")) {
                    adapter.notifyItemChanged(pos);
                    return;
                }

                // Show confirm dialog; snap back visually while user decides
                handleRemoveClick(item);
                adapter.notifyItemChanged(pos); // Snap card back to normal position
            }

            /** Draws the red delete background as the user swipes left. */
            @Override
            public void onChildDraw(@NonNull Canvas c,
                                    @NonNull RecyclerView recyclerView,
                                    @NonNull RecyclerView.ViewHolder viewHolder,
                                    float dX, float dY,
                                    int actionState, boolean isCurrentlyActive) {
                if (actionState == ItemTouchHelper.ACTION_STATE_SWIPE && dX < 0) {
                    View itemView = viewHolder.itemView;
                    float density = recyclerView.getContext()
                            .getResources().getDisplayMetrics().density;

                    // Draw red rounded background behind the swiped card
                    Paint paint = new Paint();
                    paint.setColor(Color.parseColor("#D32F2F")); // Material Red 700
                    float cornerRadius = 12f * density;
                    RectF background = new RectF(
                            itemView.getRight() + dX,
                            itemView.getTop() + 8,
                            itemView.getRight(),
                            itemView.getBottom() - 8);
                    c.drawRoundRect(background, cornerRadius, cornerRadius, paint);

                    // Draw "🗑 Delete" label on the red background
                    Paint textPaint = new Paint();
                    textPaint.setColor(Color.WHITE);
                    textPaint.setTextSize(14 * density);
                    textPaint.setTextAlign(Paint.Align.CENTER);
                    float textX = itemView.getRight() - 50 * density;
                    float textY = itemView.getTop() + (itemView.getHeight() / 2f)
                            - ((textPaint.descent() + textPaint.ascent()) / 2);
                    c.drawText("🗑 Delete", textX, textY, textPaint);
                }
                super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive);
            }

            /** How far the user must swipe before it counts as a full swipe (40%). */
            @Override
            public float getSwipeThreshold(@NonNull RecyclerView.ViewHolder viewHolder) {
                return 0.4f;
            }
        };
        new ItemTouchHelper(swipeCallback).attachToRecyclerView(adminRecycler);
    }

    // =========================================================================
    // US 03.04.01 — Browse events
    // Loads all events from Firestore (via AdminController cache) and
    // displays them as cards with title, description, location, date.
    // =========================================================================

    private void showEvents() {
        currentTab = "Events";
        resetSearch();
        setSelectedTab(tabEvents);
        tvSectionTitle.setText("Browse & Manage Events");
        showLoading();

        adminController.getAllEvents(events -> {
            List<AdminAdapter.AdminItem> newItems = new ArrayList<>();
            eventDetailMap.clear(); // Clear old event detail references

            if (events != null) {
                for (Event e : events) {
                    String id = e.getEventId();
                    if (id == null || id.trim().isEmpty()) continue; // Skip events with no ID

                    // Build display strings for the card
                    String title    = e.getName() != null ? e.getName() : "Untitled Event";
                    String subtitle = (e.getDescription() != null && !e.getDescription().trim().isEmpty())
                            ? e.getDescription() : "No description";
                    String detail   = (e.getLocation() != null && !e.getLocation().trim().isEmpty())
                            ? "📍 " + e.getLocation() : null;
                    String meta1    = null;
                    if (e.getEventDate() != null)
                        meta1 = "📅 " + DateFormat.format("MMM d, yyyy", e.getEventDate().toDate());
                    String meta2 = null;
                    if (e.getConfirmedAttendeeIds() != null)
                        meta2 = "✅ " + e.getConfirmedAttendeeIds().size() + " confirmed";
                    String primaryCategory = e.getPrimaryCategory();
                    String badge = (primaryCategory != null && !primaryCategory.trim().isEmpty())
                            ? primaryCategory.toUpperCase() : null;

                    newItems.add(new AdminAdapter.AdminItem(
                            id, title, subtitle, badge, "#2962FF",
                            detail, meta1, meta2, e.getPosterImageUrl(),
                            null, null, false, true));

                    // Store full Event object so the detail dialog can show rich info
                    eventDetailMap.put(id, e);
                }
            }

            allItems.clear();
            allItems.addAll(newItems);
            filterCurrentList(); // Apply any active search query and update the list
        }, e -> {
            Toast.makeText(this, "Failed to load events", Toast.LENGTH_SHORT).show();
            allItems.clear();
            filterCurrentList();
        }, () -> runOnUiThread(() ->
                Toast.makeText(this, R.string.admin_in_memory_cache_message, Toast.LENGTH_LONG).show()));
    }

    // =========================================================================
    // US 03.05.01 — Browse profiles
    // Loads all user profiles and displays name, email, phone, avatar.
    // =========================================================================

    private void showProfiles() {
        currentTab = "Profiles";
        resetSearch();
        setSelectedTab(tabProfiles);
        tvSectionTitle.setText("Browse & Manage User Profiles");
        showLoading();

        adminController.getAllProfiles(profiles -> {
            List<AdminAdapter.AdminItem> newItems = new ArrayList<>();
            profileDetailMap.clear(); // Clear old profile detail references

            if (profiles != null) {
                for (Entrant p : profiles) {
                    String id = p.getDeviceId();
                    if (id == null || id.trim().isEmpty()) continue;

                    String title    = (p.getName() != null && !p.getName().trim().isEmpty())
                            ? p.getName() : "Unnamed User";
                    String subtitle = (p.getEmail() != null && !p.getEmail().trim().isEmpty())
                            ? "✉ " + p.getEmail() : "No email";
                    String detail   = (p.getPhone() != null && !p.getPhone().trim().isEmpty())
                            ? "📞 " + p.getPhone() : null;
                    String initials = getInitials(p.getName());

                    newItems.add(new AdminAdapter.AdminItem(
                            id, title, subtitle, null, null,
                            detail, null, null, null,
                            p.getProfilePictureUrl(), initials));

                    // Store full Entrant object for the detail dialog
                    profileDetailMap.put(id, p);
                }
            }

            allItems.clear();
            allItems.addAll(newItems);
            filterCurrentList();
        }, e -> {
            Toast.makeText(this, "Failed to load profiles", Toast.LENGTH_SHORT).show();
            allItems.clear();
            filterCurrentList();
        });
    }

    // =========================================================================
    // US 03.06.01 — Browse images
    // Shows all events that have a poster image. Reuses the event cache so
    // no extra Firestore fetch is needed if events were already loaded.
    // =========================================================================

    private void showImages() {
        currentTab = "Images";
        resetSearch();
        setSelectedTab(tabImages);
        tvSectionTitle.setText("Browse & Manage Uploaded Images");
        showLoading();

        adminController.getAllImagesFromEvents(events -> {
            List<AdminAdapter.AdminItem> newItems = new ArrayList<>();

            if (events != null) {
                for (Event e : events) {
                    String id = e.getEventId();
                    if (id == null || id.trim().isEmpty()) continue;

                    String title = (e.getName() != null && !e.getName().trim().isEmpty())
                            ? e.getName() : "Untitled Event";

                    // Resolve organizer name from the profile cache if available
                    String organizerId   = e.getOrganizerDeviceId();
                    Entrant organizer    = profileDetailMap.get(organizerId);
                    String organizerName = (organizer != null && organizer.getName() != null)
                            ? "By: " + organizer.getName()
                            : (organizerId != null ? "By: " + organizerId : "By: Unknown");

                    String date = "";
                    if (e.getEventDate() != null)
                        date = android.text.format.DateFormat
                                .format("yyyy-MM-dd", e.getEventDate().toDate()).toString();

                    // isImageItem=true tells the adapter to use the image card layout
                    newItems.add(new AdminAdapter.AdminItem(
                            id, title, organizerName,
                            null, null, date,
                            null, null, e.getPosterImageUrl(),
                            null, null, true));
                }
            }

            allItems.clear();
            allItems.addAll(newItems);
            filterCurrentList();
        }, e -> {
            Toast.makeText(this, "Failed to load images", Toast.LENGTH_SHORT).show();
            allItems.clear();
            filterCurrentList();
        }, () -> runOnUiThread(() ->
                Toast.makeText(this, R.string.admin_in_memory_cache_message, Toast.LENGTH_LONG).show()));
    }

    // =========================================================================
    // US 03.07.01 — Browse organizers
    // Shows profiles that have at least one event. Reuses both caches —
    // no extra Firestore fetch needed after events and profiles are loaded.
    // =========================================================================

    private void showOrganizers() {
        currentTab = "Organizers";
        resetSearch();
        setSelectedTab(tabOrganizers);
        tvSectionTitle.setText("Browse & Manage Organizers");
        showLoading();

        final boolean[] adminCacheToastShown = { false };
        Runnable onAdminSessionCache = () -> runOnUiThread(() -> {
            if (adminCacheToastShown[0]) return;
            adminCacheToastShown[0] = true;
            Toast.makeText(this, R.string.admin_in_memory_cache_message, Toast.LENGTH_LONG).show();
        });

        adminController.getAllOrganizers(organizers -> {
            List<AdminAdapter.AdminItem> newItems = new ArrayList<>();
            profileDetailMap.clear();

            if (organizers != null) {
                for (Entrant o : organizers) {
                    String id = o.getDeviceId();
                    if (id == null || id.trim().isEmpty()) continue;

                    String title    = (o.getName() != null && !o.getName().trim().isEmpty())
                            ? o.getName() : "Unnamed Organizer";
                    String subtitle = (o.getEmail() != null && !o.getEmail().trim().isEmpty())
                            ? "✉ " + o.getEmail() : "No email";
                    String initials = getInitials(o.getName());

                    newItems.add(new AdminAdapter.AdminItem(
                            id, title, subtitle, null, null,
                            null, null, null, null,
                            o.getProfilePictureUrl(), initials));

                    profileDetailMap.put(id, o);
                }
            }

            allItems.clear();
            allItems.addAll(newItems);
            filterCurrentList();
        }, e -> {
            Toast.makeText(this, "Failed to load organizers", Toast.LENGTH_SHORT).show();
            allItems.clear();
            filterCurrentList();
        }, onAdminSessionCache);
    }

    // =========================================================================
    // US 03.08.01 — Review notification logs (read-only)
    // Shows all notifications with type badge, message, timestamp, recipient.
    // Swipe is disabled for this tab — notifications cannot be deleted.
    // =========================================================================

    private void showNotifications() {
        currentTab = "Notifications";
        resetSearch();
        setSelectedTab(tabNotifications);
        tvSectionTitle.setText("Review Notification Logs");
        showLoading();

        adminController.getAllNotifications(notifications -> {
            List<AdminAdapter.AdminItem> newItems = new ArrayList<>();

            if (notifications != null) {
                for (Notification n : notifications) {
                    String id = n.getId();
                    if (id == null || id.trim().isEmpty()) continue;

                    String title    = (n.getTitle() != null && !n.getTitle().trim().isEmpty())
                            ? n.getTitle() : "Untitled Notification";
                    String subtitle = (n.getMessage() != null && !n.getMessage().trim().isEmpty())
                            ? n.getMessage() : "";
                    String type     = n.getType() != null ? n.getType().toUpperCase() : null;

                    // Colour the type badge: green for selected, purple for others
                    String badgeColor = "#9C27B0";
                    if ("selected".equalsIgnoreCase(n.getType())) badgeColor = "#2E7D32";

                    String sentTime = n.getTimestamp() != null
                            ? "🕐 " + DateFormat.format("MMM d, yyyy h:mm a",
                            n.getTimestamp().getTime()) : "";
                    // Truncate recipient ID for display (device IDs are long)
                    String meta2 = n.getRecipientId() != null
                            ? "To: " + n.getRecipientId()
                            .substring(0, Math.min(8, n.getRecipientId().length())) + "..." : "";

                    newItems.add(new AdminAdapter.AdminItem(
                            id, title, subtitle, type, badgeColor,
                            null, sentTime, meta2, null));
                }
            }

            allItems.clear();
            allItems.addAll(newItems);
            filterCurrentList();
        }, e -> {
            Toast.makeText(this, "Failed to load notifications", Toast.LENGTH_SHORT).show();
            allItems.clear();
            filterCurrentList();
        });
    }

    // =========================================================================
    // Loading state helpers
    // =========================================================================

    /** Shows spinner, hides list and empty message while Firestore loads. */
    private void showLoading() {
        isLoading = true;
        loadingSpinner.setVisibility(View.VISIBLE);
        emptyMessage.setVisibility(View.GONE);
        adminRecycler.setVisibility(View.GONE);
    }

    /** Hides spinner — called as soon as data (or an error) arrives. */
    private void hideLoading() {
        isLoading = false;
        loadingSpinner.setVisibility(View.GONE);
    }

    // =========================================================================
    // Filtering — runs after every tab switch, data load, or search keystroke.
    // Filters allItems by currentQuery and pushes result to the adapter.
    // =========================================================================

    private void filterCurrentList() {
        hideLoading();

        List<AdminAdapter.AdminItem> filtered = new ArrayList<>();
        for (AdminAdapter.AdminItem item : allItems) {
            String title    = item.title    == null ? "" : item.title.toLowerCase();
            String subtitle = item.subtitle == null ? "" : item.subtitle.toLowerCase();
            // Include item if search is empty OR title/subtitle matches the query
            if (currentQuery.isEmpty()
                    || title.contains(currentQuery)
                    || subtitle.contains(currentQuery)) {
                filtered.add(item);
            }
        }

        adapter.updateItems(filtered);

        // Hide the remove button in the Notifications tab (read-only)
        adapter.setShowRemoveButton(!currentTab.equals("Notifications"));

        // Update the count label (e.g. "12 events")
        tvSectionCount.setText(filtered.size() + " " + currentTab.toLowerCase());

        // Show empty state or the list depending on results
        emptyMessage.setVisibility(!isLoading && filtered.isEmpty() ? View.VISIBLE : View.GONE);
        adminRecycler.setVisibility(!isLoading && !filtered.isEmpty() ? View.VISIBLE : View.GONE);
    }

    // =========================================================================
    // Tap-to-view: opens a detail dialog for the tapped card.
    // Images tab gets a special image-focused dialog.
    // Events and Profiles/Organizers get rich detail from their maps.
    // =========================================================================

    private void handleViewClick(AdminAdapter.AdminItem item) {
        // Images tab uses its own specialised dialog
        if (currentTab.equals("Images")) {
            showImageDetailDialog(item);
            return;
        }

        // Inflate the general detail dialog layout
        View dialogView = LayoutInflater.from(this)
                .inflate(R.layout.dialog_view_detail, null);

        TextView tvType     = dialogView.findViewById(R.id.tvDetailType);
        TextView tvTitle    = dialogView.findViewById(R.id.tvDetailTitle);
        TextView tvSubtitle = dialogView.findViewById(R.id.tvDetailSubtitle);
        LinearLayout rowDetail = dialogView.findViewById(R.id.rowDetail);
        TextView tvDetailLabel = dialogView.findViewById(R.id.tvDetailDetailLabel);
        TextView tvDetail      = dialogView.findViewById(R.id.tvDetailDetail);
        LinearLayout rowMeta   = dialogView.findViewById(R.id.rowMeta);
        TextView tvMetaLabel   = dialogView.findViewById(R.id.tvDetailMetaLabel);
        TextView tvMeta1       = dialogView.findViewById(R.id.tvDetailMeta1);
        TextView tvMeta2       = dialogView.findViewById(R.id.tvDetailMeta2);
        ImageView ivImage      = dialogView.findViewById(R.id.ivDetailImage);
        Button btnClose        = dialogView.findViewById(R.id.btnDetailClose);

        tvType.setText(currentTab.toUpperCase());
        tvTitle.setText(item.title != null ? item.title : "");
        tvSubtitle.setText(item.subtitle != null ? item.subtitle : "");

        if (currentTab.equals("Events") && eventDetailMap.containsKey(item.id)) {
            // ── Events: show location, registration dates, category, capacity ──
            Event e = eventDetailMap.get(item.id);

            StringBuilder detailText = new StringBuilder();
            if (e.getLocation() != null && !e.getLocation().trim().isEmpty())
                detailText.append("📍 ").append(e.getLocation());
            if (e.getRegistrationOpen() != null)
                detailText.append("\n📬 Opens: ")
                        .append(DateFormat.format("MMM d, yyyy", e.getRegistrationOpen().toDate()));
            if (e.getRegistrationClose() != null)
                detailText.append("\n🔒 Closes: ")
                        .append(DateFormat.format("MMM d, yyyy", e.getRegistrationClose().toDate()));
            if (detailText.length() > 0) {
                rowDetail.setVisibility(View.VISIBLE);
                tvDetailLabel.setText("Schedule");
                tvDetail.setText(detailText.toString());
            }

            StringBuilder metaText1 = new StringBuilder();
            StringBuilder metaText2 = new StringBuilder();
            String categoryText = e.getCategoryDisplayText();
            if (!categoryText.isEmpty())
                metaText1.append("🏷 Category: ").append(categoryText);
            metaText1.append("\n📍 Geolocation: ")
                    .append(e.isGeolocationRequired() ? "Required" : "Not required");
            if (e.getConfirmedAttendeeIds() != null)
                metaText2.append("✅ Confirmed: ").append(e.getConfirmedAttendeeIds().size());
            if (e.getWaitingListCapacity() > 0)
                metaText2.append("\n👥 Waitlist capacity: ").append(e.getWaitingListCapacity());

            rowMeta.setVisibility(View.VISIBLE);
            tvMetaLabel.setText("Details");
            tvMeta1.setText(metaText1.toString().trim());
            tvMeta2.setText(metaText2.toString().trim());

        } else if ((currentTab.equals("Profiles") || currentTab.equals("Organizers"))
                && profileDetailMap.containsKey(item.id)) {
            // ── Profiles / Organizers: show profile picture, email, phone, device ID ──
            Entrant p = profileDetailMap.get(item.id);

            if (p.getProfilePictureUrl() != null && !p.getProfilePictureUrl().isEmpty()) {
                ivImage.setVisibility(View.VISIBLE);
                com.bumptech.glide.Glide.with(this)
                        .load(p.getProfilePictureUrl())
                        .circleCrop()
                        .into(ivImage);
            }

            rowDetail.setVisibility(View.VISIBLE);
            tvDetailLabel.setText("Contact");
            StringBuilder contactText = new StringBuilder();
            if (p.getEmail() != null && !p.getEmail().isEmpty())
                contactText.append("✉ ").append(p.getEmail());
            if (p.getPhone() != null && !p.getPhone().isEmpty())
                contactText.append("\n📞 ").append(p.getPhone());
            tvDetail.setText(contactText.toString());

            rowMeta.setVisibility(View.VISIBLE);
            tvMetaLabel.setText("Device ID");
            tvMeta1.setText(p.getDeviceId() != null ? p.getDeviceId() : "Unknown");
            tvMeta2.setText("");

        } else {
            // ── Generic fallback: show whatever detail/meta fields the item has ──
            if (item.detail != null && !item.detail.isEmpty()) {
                rowDetail.setVisibility(View.VISIBLE);
                tvDetail.setText(item.detail);
            }
            if (item.meta1 != null || item.meta2 != null) {
                rowMeta.setVisibility(View.VISIBLE);
                tvMeta1.setText(item.meta1 != null ? item.meta1 : "");
                tvMeta2.setText(item.meta2 != null ? item.meta2 : "");
            }
        }

        // Load poster image into dialog if available
        if (item.imageUrl != null && !item.imageUrl.isEmpty()) {
            ivImage.setVisibility(View.VISIBLE);
            com.bumptech.glide.Glide.with(this)
                    .load(item.imageUrl)
                    .centerCrop()
                    .into(ivImage);
        }

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(dialogView)
                .create();
        btnClose.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
        if (dialog.getWindow() != null)
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
    }

    // =========================================================================
    // Image detail dialog — shown when tapping a card in the Images tab.
    // Shows a large poster preview with event name, uploader, and date.
    // =========================================================================

    private void showImageDetailDialog(AdminAdapter.AdminItem item) {
        View dialogView = LayoutInflater.from(this)
                .inflate(R.layout.dialog_view_image, null);

        ImageView ivPoster  = dialogView.findViewById(R.id.ivImageDialogPoster);
        TextView tvTitle    = dialogView.findViewById(R.id.tvImageDialogTitle);
        TextView tvEvent    = dialogView.findViewById(R.id.tvImageDialogEvent);
        TextView tvUploader = dialogView.findViewById(R.id.tvImageDialogUploader);
        TextView tvDate     = dialogView.findViewById(R.id.tvImageDialogDate);
        Button btnClose     = dialogView.findViewById(R.id.btnImageDialogCloseBottom);

        // Load the full-size poster image
        if (item.imageUrl != null && !item.imageUrl.isEmpty()) {
            com.bumptech.glide.Glide.with(this)
                    .load(item.imageUrl)
                    .centerCrop()
                    .into(ivPoster);
        }

        tvTitle.setText(item.title != null ? item.title : "");
        tvEvent.setText(item.title != null ? item.title : "");
        tvUploader.setText(item.subtitle != null ? item.subtitle.replace("By: ", "") : "Unknown");
        tvDate.setText(item.detail != null && !item.detail.isEmpty() ? item.detail : "Unknown");

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(dialogView)
                .create();
        btnClose.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
        if (dialog.getWindow() != null)
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
    }

    // =========================================================================
    // Delete flow — called from swipe OR from a button click inside a dialog.
    //
    // KEY FIX: We now remove the item from allItems immediately (optimistic
    // delete) so the list updates instantly without waiting for Firestore.
    // Firestore delete happens in the background. If it fails, we show an
    // error toast (in a production app you'd also re-add the item, but for
    // this project instant feedback is the priority).
    // =========================================================================

    private void handleRemoveClick(AdminAdapter.AdminItem item) {
        // Images tab: special two-option dialog (image only vs. image + event)
        if (currentTab.equals("Images")) {
            View dialogView = LayoutInflater.from(this)
                    .inflate(R.layout.dialog_delete_image, null);

            TextView tvTitle     = dialogView.findViewById(R.id.tvImageDeleteTitle);
            Button btnImageOnly  = dialogView.findViewById(R.id.btnDeleteImageOnly);
            Button btnImageEvent = dialogView.findViewById(R.id.btnDeleteImageAndEvent);
            Button btnCancel     = dialogView.findViewById(R.id.btnImageDeleteCancel);

            tvTitle.setText(item.title != null ? item.title : "");

            AlertDialog dialog = new AlertDialog.Builder(this)
                    .setView(dialogView)
                    .create();

            btnImageOnly.setOnClickListener(v -> {
                dialog.dismiss();
                // Instantly remove from list (optimistic)
                removeItemFromList(item);
                adminController.removeEventImage(item.id,
                        unused -> Toast.makeText(this, "Image removed", Toast.LENGTH_SHORT).show(),
                        e -> {
                            Toast.makeText(this, "Failed to remove image", Toast.LENGTH_SHORT).show();
                            showImages(); // Reload on failure
                        });
            });
            btnImageEvent.setOnClickListener(v -> {
                dialog.dismiss();
                // Instantly remove from list (optimistic)
                removeItemFromList(item);
                adminController.removeEvent(item.id,
                        unused -> Toast.makeText(this, "Event and image deleted", Toast.LENGTH_SHORT).show(),
                        e -> {
                            Toast.makeText(this, "Failed to delete event", Toast.LENGTH_SHORT).show();
                            showImages(); // Reload on failure
                        });
            });
            btnCancel.setOnClickListener(v -> dialog.dismiss());
            dialog.show();
            if (dialog.getWindow() != null)
                dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            return;
        }

        // All other tabs: standard single-action confirm dialog
        View dialogView = LayoutInflater.from(this)
                .inflate(R.layout.dialog_delete_confirm, null);

        TextView titleView   = dialogView.findViewById(R.id.tvDeleteTitle);
        TextView messageView = dialogView.findViewById(R.id.tvDeleteMessage);
        Button deleteBtn     = dialogView.findViewById(R.id.btnConfirmDelete);
        Button cancelBtn     = dialogView.findViewById(R.id.btnCancelDelete);

        titleView.setText("Delete " + getItemTypeLabel() + "?");
        messageView.setText("Are you sure you want to delete \"" + item.title + "\"?");

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(dialogView)
                .create();

        deleteBtn.setOnClickListener(v -> {
            dialog.dismiss();
            // Instantly remove from list before Firestore confirms (optimistic delete)
            removeItemFromList(item);
            performDelete(item);
        });
        cancelBtn.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
        if (dialog.getWindow() != null)
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
    }

    /**
     * Instantly removes an item from allItems and refreshes the visible list.
     * This makes the delete feel immediate — no waiting for Firestore.
     */
    private void removeItemFromList(AdminAdapter.AdminItem item) {
        allItems.remove(item);
        filterCurrentList(); // Re-render the list with the item gone
    }

    /**
     * Sends the actual delete request to Firestore via AdminController.
     * Called after the item is already removed from the local list.
     * On failure: shows a toast (item is already gone from UI — acceptable for this project).
     */
    private void performDelete(AdminAdapter.AdminItem item) {
        switch (currentTab) {
            case "Events":
                adminController.removeEvent(item.id,
                        unused -> Toast.makeText(this, "Event removed", Toast.LENGTH_SHORT).show(),
                        e -> Toast.makeText(this, "Failed to remove event — please refresh", Toast.LENGTH_SHORT).show());
                break;
            case "Profiles":
                adminController.removeProfile(item.id,
                        unused -> Toast.makeText(this, "Profile removed", Toast.LENGTH_SHORT).show(),
                        e -> Toast.makeText(this, "Failed to remove profile — please refresh", Toast.LENGTH_SHORT).show());
                break;
            case "Organizers":
                adminController.removeOrganizer(item.id,
                        unused -> Toast.makeText(this, "Organizer removed", Toast.LENGTH_SHORT).show(),
                        e -> Toast.makeText(this, "Failed to remove organizer — please refresh", Toast.LENGTH_SHORT).show());
                break;
        }
    }

    // =========================================================================
    // Utility helpers
    // =========================================================================

    /** Returns a human-readable label for the current tab (used in dialog titles). */
    private String getItemTypeLabel() {
        switch (currentTab) {
            case "Profiles":      return "Profile";
            case "Images":        return "Image";
            case "Organizers":    return "Organizer";
            case "Notifications": return "Notification";
            default:              return "Event";
        }
    }

    /** Clears the search bar and resets the query string. */
    private void resetSearch() {
        currentQuery = "";
        if (searchBar != null) searchBar.setText("");
    }

    /**
     * Extracts up to 2 initials from a name string.
     * e.g. "John Smith" → "JS", "Alice" → "AL", null → "?"
     */
    private String getInitials(String name) {
        if (name == null || name.trim().isEmpty()) return "?";
        String[] parts = name.trim().split("\\s+");
        if (parts.length >= 2)
            return (parts[0].substring(0, 1) + parts[1].substring(0, 1)).toUpperCase();
        return parts[0].substring(0, Math.min(2, parts[0].length())).toUpperCase();
    }

    /**
     * Marks the given tab as selected (shows underline) and deselects all others.
     * Also scrolls the tab bar so the selected tab is centered.
     */
    private void setSelectedTab(LinearLayout selectedTab) {
        // Deselect all tabs
        for (LinearLayout tab : new LinearLayout[]{
                tabEvents, tabProfiles, tabImages, tabOrganizers, tabNotifications}) {
            tab.setSelected(false);
        }
        // Select the chosen tab
        selectedTab.setSelected(true);
        // Smoothly scroll the tab bar to center the selected tab
        selectedTab.post(() -> {
            int scrollX = selectedTab.getLeft()
                    - (adminTabsScroll.getWidth() - selectedTab.getWidth()) / 2;
            adminTabsScroll.smoothScrollTo(scrollX, 0);
        });
    }
}