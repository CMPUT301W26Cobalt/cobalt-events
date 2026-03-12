package com.example.cobaltevents.ui.admin;

import android.app.AlertDialog;
import android.content.res.ColorStateList;
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
 * Admin dashboard — implements all Admin user stories:
 *   US 03.01.01 — Remove events
 *   US 03.02.01 — Remove profiles
 *   US 03.03.01 — Remove images
 *   US 03.04.01 — Browse events
 *   US 03.05.01 — Browse profiles
 *   US 03.06.01 — Browse images
 *   US 03.07.01 — Remove organizers
 *   US 03.08.01 — Review notification logs
 */
public class AdminActivity extends AppCompatActivity {

    // Tabs (now LinearLayouts for underline support)
    private LinearLayout tabEvents, tabProfiles, tabImages, tabOrganizers, tabNotifications;

    private TextView tvSectionTitle, tvSectionCount;
    private RecyclerView adminRecycler;
    private EditText searchBar;
    private HorizontalScrollView adminTabsScroll;
    private TextView emptyMessage;
    private android.widget.ProgressBar loadingSpinner;

    private AdminAdapter adapter;
    private final List<AdminAdapter.AdminItem> allItems = new ArrayList<>();
    private String currentQuery = "";
    private String currentTab = "Events";

    private AdminController adminController;
    private final java.util.Map<String, Event> eventDetailMap = new java.util.HashMap<>();
    private final java.util.Map<String, Entrant> profileDetailMap = new java.util.HashMap<>();
    private final Handler searchHandler = new Handler(Looper.getMainLooper());
    private Runnable searchRunnable;
    private boolean isLoading = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin);

        adminController = new AdminController();

        // Tabs
        tabEvents        = findViewById(R.id.tab_events);
        tabProfiles      = findViewById(R.id.tab_profiles);
        tabImages        = findViewById(R.id.tab_images);
        tabOrganizers    = findViewById(R.id.tab_organizers);
        tabNotifications = findViewById(R.id.tab_notifications);



        tvSectionTitle   = findViewById(R.id.tvSectionTitle);
        tvSectionCount   = findViewById(R.id.tvSectionCount);
        searchBar        = findViewById(R.id.etSearch);
        adminRecycler    = findViewById(R.id.adminRecycler);
        adminTabsScroll  = findViewById(R.id.adminTabsScroll);
        emptyMessage     = findViewById(R.id.emptyMessage);
        loadingSpinner   = findViewById(R.id.loadingSpinner);

        adminRecycler.setLayoutManager(new LinearLayoutManager(this));
        adminRecycler.setHasFixedSize(true);
        adminRecycler.setItemViewCacheSize(20);

        // Adapter — no remove listener needed on cards (swipe handles delete)
        adapter = new AdminAdapter(new ArrayList<>(), this::handleRemoveClick, this::handleViewClick);
        adminRecycler.setAdapter(adapter);

        // Hide everything until first load
        emptyMessage.setVisibility(View.GONE);
        adminRecycler.setVisibility(View.GONE);

        // Swipe left to delete
        setupSwipeToDelete();

        // Tap anywhere on card to view details (handled via adapter click)
        adminRecycler.addOnItemTouchListener(new RecyclerView.SimpleOnItemTouchListener() {});

        tabEvents.setOnClickListener(v -> showEvents());
        tabProfiles.setOnClickListener(v -> showProfiles());
        tabImages.setOnClickListener(v -> showImages());
        tabOrganizers.setOnClickListener(v -> showOrganizers());
        tabNotifications.setOnClickListener(v -> showNotifications());

        searchBar.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable s) {
                currentQuery = s.toString().trim().toLowerCase();
                if (searchRunnable != null) searchHandler.removeCallbacks(searchRunnable);
                searchRunnable = AdminActivity.this::filterCurrentList;
                searchHandler.postDelayed(searchRunnable, 300);
            }
        });

        showEvents();
    }

    // ── Swipe to delete ───────────────────────────────────────────────────────

    private void setupSwipeToDelete() {
        ItemTouchHelper.SimpleCallback swipeCallback = new ItemTouchHelper.SimpleCallback(
                0, ItemTouchHelper.LEFT) {

            @Override
            public boolean onMove(@NonNull RecyclerView rv,
                                  @NonNull RecyclerView.ViewHolder vh,
                                  @NonNull RecyclerView.ViewHolder target) {
                return false;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
                int pos = viewHolder.getAdapterPosition();
                if (pos < 0 || pos >= adapter.getItems().size()) return;
                AdminAdapter.AdminItem item = adapter.getItems().get(pos);
                // Notifications are read-only — snap back
                if (currentTab.equals("Notifications")) {
                    adapter.notifyItemChanged(pos);
                    return;
                }
                handleRemoveClick(item);
                // Snap back visually — actual removal happens after confirm
                adapter.notifyItemChanged(pos);
            }

            @Override
            public void onChildDraw(@NonNull Canvas c,
                                    @NonNull RecyclerView recyclerView,
                                    @NonNull RecyclerView.ViewHolder viewHolder,
                                    float dX, float dY,
                                    int actionState, boolean isCurrentlyActive) {
                if (actionState == ItemTouchHelper.ACTION_STATE_SWIPE && dX < 0) {
                    View itemView = viewHolder.itemView;
                    Paint paint = new Paint();
                    paint.setColor(Color.parseColor("#D32F2F"));

                    float cornerRadius = 12f * recyclerView.getContext()
                            .getResources().getDisplayMetrics().density;
                    RectF background = new RectF(
                            itemView.getRight() + dX,
                            itemView.getTop() + 8,
                            itemView.getRight(),
                            itemView.getBottom() - 8);
                    c.drawRoundRect(background, cornerRadius, cornerRadius, paint);

                    // Draw trash emoji label
                    Paint textPaint = new Paint();
                    textPaint.setColor(Color.WHITE);
                    textPaint.setTextSize(14 * recyclerView.getContext()
                            .getResources().getDisplayMetrics().density);
                    textPaint.setTextAlign(Paint.Align.CENTER);
                    float textX = itemView.getRight() - 50 * recyclerView.getContext()
                            .getResources().getDisplayMetrics().density;
                    float textY = itemView.getTop() + (itemView.getHeight() / 2f)
                            - ((textPaint.descent() + textPaint.ascent()) / 2);
                    c.drawText("🗑 Delete", textX, textY, textPaint);
                }
                super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive);
            }

            @Override
            public float getSwipeThreshold(@NonNull RecyclerView.ViewHolder viewHolder) {
                return 0.4f;
            }
        };
        new ItemTouchHelper(swipeCallback).attachToRecyclerView(adminRecycler);
    }

    // ── US 03.04.01 — Browse events ───────────────────────────────────────────

    private void showEvents() {
        currentTab = "Events";
        resetSearch();
        setSelectedTab(tabEvents);
        tvSectionTitle.setText("Browse & Manage Events");
        showLoading();

        adminController.getAllEvents(events -> {
            List<AdminAdapter.AdminItem> newItems = new ArrayList<>();
            eventDetailMap.clear();
            if (events != null) {
                for (Event e : events) {
                    String id = e.getEventId();
                    if (id == null || id.trim().isEmpty()) continue;
                    String title = e.getName() != null ? e.getName() : "Untitled Event";
                    String subtitle = e.getDescription() != null && !e.getDescription().trim().isEmpty()
                            ? e.getDescription() : "No description";
                    String detail = e.getLocation() != null && !e.getLocation().trim().isEmpty()
                            ? "📍 " + e.getLocation() : null;
                    String meta1 = null;
                    if (e.getEventDate() != null)
                        meta1 = "📅 " + DateFormat.format("MMM d, yyyy", e.getEventDate().toDate()).toString();
                    String meta2 = null;
                    if (e.getConfirmedAttendeeIds() != null)
                        meta2 = "✅ " + e.getConfirmedAttendeeIds().size() + " confirmed";
                    String badge = (e.getCategory() != null && !e.getCategory().trim().isEmpty())
                            ? e.getCategory().toUpperCase() : null;
                    newItems.add(new AdminAdapter.AdminItem(
                            id, title, subtitle, badge, "#2962FF",
                            detail, meta1, meta2, e.getPosterImageUrl(),
                            null, null, false, true));
                    eventDetailMap.put(id, e);
                }
            }
            allItems.clear();
            allItems.addAll(newItems);
            filterCurrentList();
        }, e -> {
            Toast.makeText(this, "Failed to load events", Toast.LENGTH_SHORT).show();
            allItems.clear();
            filterCurrentList();
        });
    }

    // ── US 03.05.01 — Browse profiles ─────────────────────────────────────────

    private void showProfiles() {
        currentTab = "Profiles";
        resetSearch();
        setSelectedTab(tabProfiles);
        tvSectionTitle.setText("Browse & Manage User Profiles");
        showLoading();

        adminController.getAllProfiles(profiles -> {
            List<AdminAdapter.AdminItem> newItems = new ArrayList<>();
            profileDetailMap.clear();
            if (profiles != null) {
                for (Entrant p : profiles) {
                    String id = p.getDeviceId();
                    if (id == null || id.trim().isEmpty()) continue;
                    String title = (p.getName() != null && !p.getName().trim().isEmpty()) ? p.getName() : "Unnamed User";
                    String subtitle = (p.getEmail() != null && !p.getEmail().trim().isEmpty()) ? "✉ " + p.getEmail() : "No email";
                    String detail = (p.getPhone() != null && !p.getPhone().trim().isEmpty()) ? "📞 " + p.getPhone() : null;
                    String initials = getInitials(p.getName());
                    newItems.add(new AdminAdapter.AdminItem(id, title, subtitle, null, null, detail, null, null, null, p.getProfilePictureUrl(), initials));
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

    // ── US 03.06.01 — Browse images ───────────────────────────────────────────

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
                    String title = (e.getName() != null && !e.getName().trim().isEmpty()) ? e.getName() : "Untitled Event";
                    String organizerId = e.getOrganizerDeviceId();
                    Entrant organizer = profileDetailMap.get(organizerId);
                    String organizerName = (organizer != null && organizer.getName() != null)
                            ? "By: " + organizer.getName()
                            : (organizerId != null ? "By: " + organizerId : "By: Unknown");
                    String date = "";
                    if (e.getEventDate() != null)
                        date = android.text.format.DateFormat.format("yyyy-MM-dd", e.getEventDate().toDate()).toString();
                    newItems.add(new AdminAdapter.AdminItem(id, title, organizerName, null, null, date, null, null, e.getPosterImageUrl(), null, null, true));
                }
            }
            allItems.clear();
            allItems.addAll(newItems);
            filterCurrentList();
        }, e -> {
            Toast.makeText(this, "Failed to load images", Toast.LENGTH_SHORT).show();
            allItems.clear();
            filterCurrentList();
        });
    }

    // ── US 03.07.01 — Browse organizers ──────────────────────────────────────

    private void showOrganizers() {
        currentTab = "Organizers";
        resetSearch();
        setSelectedTab(tabOrganizers);
        tvSectionTitle.setText("Browse & Manage Organizers");
        showLoading();

        adminController.getAllOrganizers(organizers -> {
            List<AdminAdapter.AdminItem> newItems = new ArrayList<>();
            profileDetailMap.clear();
            if (organizers != null) {
                for (Entrant o : organizers) {
                    String id = o.getDeviceId();
                    if (id == null || id.trim().isEmpty()) continue;
                    String title = (o.getName() != null && !o.getName().trim().isEmpty()) ? o.getName() : "Unnamed Organizer";
                    String subtitle = (o.getEmail() != null && !o.getEmail().trim().isEmpty()) ? "✉ " + o.getEmail() : "No email";
                    String initials = getInitials(o.getName());
                    newItems.add(new AdminAdapter.AdminItem(id, title, subtitle, null, null, null, null, null, null, o.getProfilePictureUrl(), initials));
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
        });
    }

    // ── US 03.08.01 — Review notification logs ────────────────────────────────

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
                    String title = (n.getTitle() != null && !n.getTitle().trim().isEmpty()) ? n.getTitle() : "Untitled Notification";
                    String subtitle = (n.getMessage() != null && !n.getMessage().trim().isEmpty()) ? n.getMessage() : "";
                    String type = n.getType() != null ? n.getType().toUpperCase() : null;
                    String badgeColor = "#9C27B0";
                    if ("selected".equalsIgnoreCase(n.getType())) badgeColor = "#2E7D32";
                    String sentTime = n.getTimestamp() != null
                            ? "🕐 " + DateFormat.format("MMM d, yyyy h:mm a", n.getTimestamp().getTime()).toString() : "";
                    String meta2 = n.getRecipientId() != null
                            ? "To: " + n.getRecipientId().substring(0, Math.min(8, n.getRecipientId().length())) + "..." : "";
                    newItems.add(new AdminAdapter.AdminItem(id, title, subtitle, type, badgeColor, null, sentTime, meta2, null));
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

    // ── Loading state ─────────────────────────────────────────────────────────

    private void showLoading() {
        isLoading = true;
        loadingSpinner.setVisibility(View.VISIBLE);
        emptyMessage.setVisibility(View.GONE);
        adminRecycler.setVisibility(View.GONE);
    }

    private void hideLoading() {
        isLoading = false;
        loadingSpinner.setVisibility(View.GONE);
    }

    // ── Filtering ─────────────────────────────────────────────────────────────

    private void filterCurrentList() {
        hideLoading();
        List<AdminAdapter.AdminItem> filtered = new ArrayList<>();
        for (AdminAdapter.AdminItem item : allItems) {
            String title    = item.title    == null ? "" : item.title.toLowerCase();
            String subtitle = item.subtitle == null ? "" : item.subtitle.toLowerCase();
            if (currentQuery.isEmpty()
                    || title.contains(currentQuery)
                    || subtitle.contains(currentQuery)) {
                filtered.add(item);
            }
        }

        adapter.updateItems(filtered);
        adapter.setShowRemoveButton(!currentTab.equals("Notifications"));

        // Update count label
        String countLabel = filtered.size() + " " + currentTab.toLowerCase();
        tvSectionCount.setText(countLabel);

        emptyMessage.setVisibility(!isLoading && filtered.isEmpty() ? View.VISIBLE : View.GONE);
        adminRecycler.setVisibility(!isLoading && !filtered.isEmpty() ? View.VISIBLE : View.GONE);
    }

    // ── View click — show detail dialog ──────────────────────────────────────

    private String getInitials(String name) {
        if (name == null || name.trim().isEmpty()) return "?";
        String[] parts = name.trim().split("\\s+");
        if (parts.length >= 2)
            return (parts[0].substring(0, 1) + parts[1].substring(0, 1)).toUpperCase();
        return parts[0].substring(0, Math.min(2, parts[0].length())).toUpperCase();
    }

    private void handleViewClick(AdminAdapter.AdminItem item) {

        // Images tab — show special image detail dialog
        if (currentTab.equals("Images")) {
            showImageDetailDialog(item);
            return;
        }

        View dialogView = LayoutInflater.from(this)
                .inflate(R.layout.dialog_view_detail, null);

        TextView tvType     = dialogView.findViewById(R.id.tvDetailType);
        TextView tvTitle    = dialogView.findViewById(R.id.tvDetailTitle);
        TextView tvSubtitle = dialogView.findViewById(R.id.tvDetailSubtitle);

        LinearLayout rowDetail = dialogView.findViewById(R.id.rowDetail);
        TextView tvDetailLabel = dialogView.findViewById(R.id.tvDetailDetailLabel);
        TextView tvDetail      = dialogView.findViewById(R.id.tvDetailDetail);

        LinearLayout rowMeta = dialogView.findViewById(R.id.rowMeta);
        TextView tvMetaLabel = dialogView.findViewById(R.id.tvDetailMetaLabel);
        TextView tvMeta1     = dialogView.findViewById(R.id.tvDetailMeta1);
        TextView tvMeta2     = dialogView.findViewById(R.id.tvDetailMeta2);

        ImageView ivImage = dialogView.findViewById(R.id.ivDetailImage);
        Button btnClose   = dialogView.findViewById(R.id.btnDetailClose);

        tvType.setText(currentTab.toUpperCase());
        tvTitle.setText(item.title != null ? item.title : "");
        tvSubtitle.setText(item.subtitle != null ? item.subtitle : "");

        // Events — rich detail
        if (currentTab.equals("Events") && eventDetailMap.containsKey(item.id)) {
            Event e = eventDetailMap.get(item.id);

            // Detail row: location + registration dates
            StringBuilder detailText = new StringBuilder();
            if (e.getLocation() != null && !e.getLocation().trim().isEmpty()) {
                detailText.append("📍 ").append(e.getLocation());
            }
            if (e.getRegistrationOpen() != null) {
                detailText.append("\n📬 Opens: ")
                        .append(DateFormat.format("MMM d, yyyy", e.getRegistrationOpen().toDate()));
            }
            if (e.getRegistrationClose() != null) {
                detailText.append("\n🔒 Closes: ")
                        .append(DateFormat.format("MMM d, yyyy", e.getRegistrationClose().toDate()));
            }
            if (detailText.length() > 0) {
                rowDetail.setVisibility(View.VISIBLE);
                tvDetailLabel.setText("Schedule");
                tvDetail.setText(detailText.toString());
            }

            // Meta row: category, geolocation, attendees, capacity
            StringBuilder metaText1 = new StringBuilder();
            StringBuilder metaText2 = new StringBuilder();
            if (e.getCategory() != null && !e.getCategory().trim().isEmpty()) {
                metaText1.append("🏷 Category: ").append(e.getCategory());
            }
            metaText1.append("\n📍 Geolocation: ").append(e.isGeolocationRequired() ? "Required" : "Not required");
            if (e.getConfirmedAttendeeIds() != null) {
                metaText2.append("✅ Confirmed: ").append(e.getConfirmedAttendeeIds().size());
            }
            if (e.getWaitingListCapacity() > 0) {
                metaText2.append("\n👥 Waitlist capacity: ").append(e.getWaitingListCapacity());
            }
            rowMeta.setVisibility(View.VISIBLE);
            tvMetaLabel.setText("Details");
            tvMeta1.setText(metaText1.toString().trim());
            tvMeta2.setText(metaText2.toString().trim());

        } else if ((currentTab.equals("Profiles") || currentTab.equals("Organizers"))
                && profileDetailMap.containsKey(item.id)) {
            Entrant p = profileDetailMap.get(item.id);

            // Show profile picture if available
            if (p.getProfilePictureUrl() != null && !p.getProfilePictureUrl().isEmpty()) {
                ivImage.setVisibility(View.VISIBLE);
                com.bumptech.glide.Glide.with(this)
                        .load(p.getProfilePictureUrl())
                        .circleCrop()
                        .into(ivImage);
            }

            // Detail row: email + phone
            rowDetail.setVisibility(View.VISIBLE);
            tvDetailLabel.setText("Contact");
            StringBuilder contactText = new StringBuilder();
            if (p.getEmail() != null && !p.getEmail().isEmpty())
                contactText.append("✉ ").append(p.getEmail());
            if (p.getPhone() != null && !p.getPhone().isEmpty())
                contactText.append("\n📞 ").append(p.getPhone());
            tvDetail.setText(contactText.toString());

            // Meta row: device ID
            rowMeta.setVisibility(View.VISIBLE);
            tvMetaLabel.setText("Device ID");
            tvMeta1.setText(p.getDeviceId() != null ? p.getDeviceId() : "Unknown");
            tvMeta2.setText("");

        } else {
            // Non-event tabs — show generic fields
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
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }
    }

    // ── Image detail dialog ───────────────────────────────────────────────────

    private void showImageDetailDialog(AdminAdapter.AdminItem item) {
        View dialogView = LayoutInflater.from(this)
                .inflate(R.layout.dialog_view_image, null);

        ImageView ivPoster       = dialogView.findViewById(R.id.ivImageDialogPoster);
        TextView tvTitle         = dialogView.findViewById(R.id.tvImageDialogTitle);
        TextView tvEvent         = dialogView.findViewById(R.id.tvImageDialogEvent);
        TextView tvUploader      = dialogView.findViewById(R.id.tvImageDialogUploader);
        TextView tvDate          = dialogView.findViewById(R.id.tvImageDialogDate);
        Button btnClose          = dialogView.findViewById(R.id.btnImageDialogCloseBottom);

        // Load poster
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

    // ── Remove click — confirm dialog ─────────────────────────────────────────

    private void handleRemoveClick(AdminAdapter.AdminItem item) {
        // Images tab — show two-option delete dialog
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
                adminController.removeEventImage(item.id,
                        unused -> { Toast.makeText(this, "Image removed", Toast.LENGTH_SHORT).show(); showImages(); },
                        e -> Toast.makeText(this, "Failed to remove image", Toast.LENGTH_SHORT).show());
            });
            btnImageEvent.setOnClickListener(v -> {
                dialog.dismiss();
                adminController.removeEvent(item.id,
                        unused -> { Toast.makeText(this, "Event and image deleted", Toast.LENGTH_SHORT).show(); showImages(); },
                        e -> Toast.makeText(this, "Failed to delete event", Toast.LENGTH_SHORT).show());
            });
            btnCancel.setOnClickListener(v -> dialog.dismiss());
            dialog.show();
            if (dialog.getWindow() != null)
                dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            return;
        }

        View dialogView = LayoutInflater.from(this)
                .inflate(R.layout.dialog_delete_confirm, null);

        TextView titleView   = dialogView.findViewById(R.id.tvDeleteTitle);
        TextView messageView = dialogView.findViewById(R.id.tvDeleteMessage);
        Button   deleteBtn   = dialogView.findViewById(R.id.btnConfirmDelete);
        Button   cancelBtn   = dialogView.findViewById(R.id.btnCancelDelete);

        String itemType = getItemTypeLabel();
        titleView.setText("Delete " + itemType + "?");

        messageView.setText("Are you sure you want to delete \"" + item.title + "\"?");

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(dialogView)
                .create();

        deleteBtn.setOnClickListener(v -> {
            dialog.dismiss();
            performDelete(item);
        });
        cancelBtn.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }
    }

    private void performDelete(AdminAdapter.AdminItem item) {
        switch (currentTab) {
            case "Events":
                adminController.removeEvent(item.id,
                        unused -> { Toast.makeText(this, "Event removed", Toast.LENGTH_SHORT).show(); showEvents(); },
                        e -> Toast.makeText(this, "Failed to remove event", Toast.LENGTH_SHORT).show());
                break;
            case "Profiles":
                adminController.removeProfile(item.id,
                        unused -> { Toast.makeText(this, "Profile removed", Toast.LENGTH_SHORT).show(); showProfiles(); },
                        e -> Toast.makeText(this, "Failed to remove profile", Toast.LENGTH_SHORT).show());
                break;
            case "Images":
                adminController.removeEventImage(item.id,
                        unused -> { Toast.makeText(this, "Image removed", Toast.LENGTH_SHORT).show(); showImages(); },
                        e -> Toast.makeText(this, "Failed to remove image", Toast.LENGTH_SHORT).show());
                break;
            case "Organizers":
                adminController.removeOrganizer(item.id,
                        unused -> { Toast.makeText(this, "Organizer removed", Toast.LENGTH_SHORT).show(); showOrganizers(); },
                        e -> Toast.makeText(this, "Failed to remove organizer", Toast.LENGTH_SHORT).show());
                break;
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String getItemTypeLabel() {
        switch (currentTab) {
            case "Profiles":      return "Profile";
            case "Images":        return "Image";
            case "Organizers":    return "Organizer";
            case "Notifications": return "Notification";
            default:              return "Event";
        }
    }

    private void resetSearch() {
        currentQuery = "";
        if (searchBar != null) searchBar.setText("");
    }

    private void setSelectedTab(LinearLayout selectedTab) {
        for (LinearLayout tab : new LinearLayout[]{
                tabEvents, tabProfiles, tabImages, tabOrganizers, tabNotifications}) {
            tab.setSelected(false);
        }
        selectedTab.setSelected(true);
        selectedTab.post(() -> {
            int scrollX = selectedTab.getLeft()
                    - (adminTabsScroll.getWidth() - selectedTab.getWidth()) / 2;
            adminTabsScroll.smoothScrollTo(scrollX, 0);
        });
    }
}