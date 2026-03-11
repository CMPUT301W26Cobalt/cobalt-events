package com.example.cobaltevents.ui.admin;

import android.app.AlertDialog;
import android.content.res.ColorStateList;
import android.os.Bundle;
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

import androidx.appcompat.app.AppCompatActivity;
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

    private TextView tabEvents, tabProfiles, tabImages, tabOrganizers, tabNotifications;
    private TextView tvSectionTitle, tvSectionCount;
    private RecyclerView adminRecycler;
    private EditText searchBar;
    private HorizontalScrollView adminTabsScroll;
    private TextView emptyMessage;

    private AdminAdapter adapter;
    private final List<AdminAdapter.AdminItem> allItems = new ArrayList<>();
    private String currentQuery = "";
    private String currentTab = "Events";

    private AdminController adminController;
    private final java.util.Map<String, Event> eventDetailMap = new java.util.HashMap<>();
    private final java.util.Map<String, Entrant> profileDetailMap = new java.util.HashMap<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin);

        adminController = new AdminController();

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

        adminRecycler.setLayoutManager(new LinearLayoutManager(this));
        adminRecycler.setHasFixedSize(true);
        adminRecycler.setItemViewCacheSize(20);

        adapter = new AdminAdapter(new ArrayList<>(), this::handleRemoveClick, this::handleViewClick);
        adminRecycler.setAdapter(adapter);

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
                filterCurrentList();
            }
        });

        showEvents();
    }

    // ── US 03.04.01 — Browse events ───────────────────────────────────────────

    private void showEvents() {
        currentTab = "Events";
        resetSearch();
        setSelectedTab(tabEvents);
        tvSectionTitle.setText("Browse & Manage Events");

        adminController.getAllEvents(events -> {
            allItems.clear();
            eventDetailMap.clear();
            if (events != null) {
                for (Event e : events) {
                    String id = e.getEventId();
                    if (id == null || id.trim().isEmpty()) continue;

                    String title = e.getName() != null ? e.getName() : "Untitled Event";
                    String subtitle = e.getDescription() != null && !e.getDescription().trim().isEmpty()
                            ? e.getDescription() : "No description";

                    // Detail: location
                    String detail = e.getLocation() != null && !e.getLocation().trim().isEmpty()
                            ? "📍 " + e.getLocation() : null;

                    // Meta1: event date
                    String meta1 = null;
                    if (e.getEventDate() != null) {
                        meta1 = "📅 " + DateFormat.format("MMM d, yyyy", e.getEventDate().toDate()).toString();
                    }

                    // Meta2: confirmed attendees count
                    String meta2 = null;
                    if (e.getConfirmedAttendeeIds() != null) {
                        meta2 = "✅ " + e.getConfirmedAttendeeIds().size() + " confirmed";
                    }

                    // Badge: category
                    String badge = (e.getCategory() != null && !e.getCategory().trim().isEmpty())
                            ? e.getCategory().toUpperCase() : null;

                    allItems.add(new AdminAdapter.AdminItem(
                            id, title, subtitle,
                            badge, "#2962FF",
                            detail, meta1, meta2, null));

                    // Store full event for detail dialog
                    eventDetailMap.put(id, e);
                }
            }
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

        adminController.getAllProfiles(profiles -> {
            allItems.clear();
            profileDetailMap.clear();
            if (profiles != null) {
                for (Entrant p : profiles) {
                    String id = p.getDeviceId();
                    if (id == null || id.trim().isEmpty()) continue;

                    String title = (p.getName() != null && !p.getName().trim().isEmpty())
                            ? p.getName() : "Unnamed User";

                    String subtitle = (p.getEmail() != null && !p.getEmail().trim().isEmpty())
                            ? "✉ " + p.getEmail() : "No email";

                    String detail = (p.getPhone() != null && !p.getPhone().trim().isEmpty())
                            ? "📞 " + p.getPhone() : null;

                    // Compute initials for avatar
                    String initials = getInitials(p.getName());

                    allItems.add(new AdminAdapter.AdminItem(
                            id, title, subtitle,
                            null, null,
                            detail, null, null, null,
                            p.getProfilePictureUrl(), initials));

                    profileDetailMap.put(id, p);
                }
            }
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

        adminController.getAllImagesFromEvents(events -> {
            allItems.clear();
            if (events != null) {
                for (Event e : events) {
                    String id = e.getEventId();
                    if (id == null || id.trim().isEmpty()) continue;

                    String title = (e.getName() != null && !e.getName().trim().isEmpty())
                            ? e.getName() : "Untitled Event";

                    String subtitle = e.getLocation() != null && !e.getLocation().trim().isEmpty()
                            ? "By: " + e.getLocation() : "Poster uploaded";

                    allItems.add(new AdminAdapter.AdminItem(
                            id, title, subtitle,
                            null, null,
                            null, null, null,
                            e.getPosterImageUrl())); // shows thumbnail
                }
            }
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

        adminController.getAllOrganizers(organizers -> {
            allItems.clear();
            profileDetailMap.clear();
            if (organizers != null) {
                for (Entrant o : organizers) {
                    String id = o.getDeviceId();
                    if (id == null || id.trim().isEmpty()) continue;

                    String title = (o.getName() != null && !o.getName().trim().isEmpty())
                            ? o.getName() : "Unnamed Organizer";

                    String subtitle = (o.getEmail() != null && !o.getEmail().trim().isEmpty())
                            ? "✉ " + o.getEmail() : "No email";

                    String initials = getInitials(o.getName());

                    allItems.add(new AdminAdapter.AdminItem(
                            id, title, subtitle,
                            null, null,
                            null, null, null, null,
                            o.getProfilePictureUrl(), initials));

                    profileDetailMap.put(id, o);
                }
            }
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

        adminController.getAllNotifications(notifications -> {
            allItems.clear();
            if (notifications != null) {
                for (Notification n : notifications) {
                    String id = n.getId();
                    if (id == null || id.trim().isEmpty()) continue;

                    String title = (n.getTitle() != null && !n.getTitle().trim().isEmpty())
                            ? n.getTitle() : "Untitled Notification";

                    // Message preview as subtitle
                    String subtitle = (n.getMessage() != null && !n.getMessage().trim().isEmpty())
                            ? n.getMessage() : "";

                    // Notification type as badge
                    String type = n.getType() != null ? n.getType().toUpperCase() : null;
                    String badgeColor = "#9C27B0"; // default purple
                    if ("selected".equalsIgnoreCase(n.getType())) badgeColor = "#2E7D32";

                    // Timestamp
                    String sentTime = n.getTimestamp() != null
                            ? "🕐 " + DateFormat.format("MMM d, yyyy h:mm a",
                            n.getTimestamp().getTime()).toString()
                            : "";

                    // Recipient info
                    String meta1 = sentTime;
                    String meta2 = n.getRecipientId() != null
                            ? "To: " + n.getRecipientId().substring(0,
                            Math.min(8, n.getRecipientId().length())) + "..."
                            : "";

                    allItems.add(new AdminAdapter.AdminItem(
                            id, title, subtitle,
                            type, badgeColor,
                            null, meta1, meta2, null));
                }
            }
            filterCurrentList();
        }, e -> {
            Toast.makeText(this, "Failed to load notifications", Toast.LENGTH_SHORT).show();
            allItems.clear();
            filterCurrentList();
        });
    }

    // ── Filtering ─────────────────────────────────────────────────────────────

    private void filterCurrentList() {
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

        emptyMessage.setVisibility(filtered.isEmpty() ? View.VISIBLE : View.GONE);
        adminRecycler.setVisibility(filtered.isEmpty() ? View.GONE : View.VISIBLE);
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

    // ── Remove click — confirm dialog ─────────────────────────────────────────

    private void handleRemoveClick(AdminAdapter.AdminItem item) {
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

    private void setSelectedTab(TextView selectedTab) {
        resetTabStyles();
        selectedTab.setBackgroundTintList(ColorStateList.valueOf(0xFF000000));
        selectedTab.setTextColor(0xFFFFFFFF);
        selectedTab.post(() -> {
            int scrollX = selectedTab.getLeft()
                    - (adminTabsScroll.getWidth() - selectedTab.getWidth()) / 2;
            adminTabsScroll.smoothScrollTo(scrollX, 0);
        });
    }

    private void resetTabStyles() {
        for (TextView tab : new TextView[]{
                tabEvents, tabProfiles, tabImages, tabOrganizers, tabNotifications}) {
            tab.setBackgroundResource(R.drawable.bg_tab);
            tab.setBackgroundTintList(ColorStateList.valueOf(0xFFFFFFFF));
            tab.setTextColor(0xFF333333);
        }
    }
}