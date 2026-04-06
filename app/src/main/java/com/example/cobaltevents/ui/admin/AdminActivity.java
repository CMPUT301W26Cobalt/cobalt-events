package com.example.cobaltevents.ui.admin;

import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.drawable.ColorDrawable;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.format.DateFormat;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.example.cobaltevents.R;
import com.example.cobaltevents.controller.AdminController;
import com.example.cobaltevents.util.NetworkConnectivity;
import com.example.cobaltevents.model.Comment;
import com.example.cobaltevents.model.Entrant;
import com.example.cobaltevents.model.Event;
import com.example.cobaltevents.model.Notification;
import com.example.cobaltevents.ui.EntrantActivity;
import com.example.cobaltevents.ui.OrganizerActivity;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * US 03.04.01 – Browse and manage platform data (admin dashboard).
 *
 * <p>Tabbed home for system administrators: events, profiles, images, organizers, notifications, and
 * comments, with swipe-to-delete where allowed. Uses {@link AdminController} for Firestore.
 *
 * <p>Also covers US 03.01.01–03.03.01 and 03.05.01–03.08.01 / 03.10.01 per tab, and US 03.09.01: the header
 * overflow (three dots) opens {@code panel_role_switch}, which shows the current Admin role as a highlighted row
 * plus actions to continue as User or Organizer ({@link EntrantActivity} / {@link OrganizerActivity} with
 * {@code IS_ADMIN_SWITCH}).
 */
public class AdminActivity extends AppCompatActivity {

    // ── Tab views ─────────────────────────────────────────────────────────────
    private LinearLayout tabEvents, tabProfiles, tabImages, tabOrganizers, tabNotifications;
    private LinearLayout tabComments;

    // ── UI references ─────────────────────────────────────────────────────────
    private TextView tvSectionTitle, tvSectionCount;
    private RecyclerView adminRecycler;
    private EditText searchBar;
    private HorizontalScrollView adminTabsScroll;
    private View emptyMessage;
    private android.widget.ProgressBar loadingSpinner;
    private SwipeRefreshLayout swipeRefresh;

    // ── Adapter & state ───────────────────────────────────────────────────────
    private AdminAdapter adapter;
    private final List<AdminAdapter.AdminItem> allItems = new ArrayList<>();
    /**
     * Cache for slow tabs (Notifications, Comments). Cleared on pull-to-refresh and on
     * {@link #onResume} (after the initial resume) so returning from child screens stays fresh.
     */
    private final java.util.Map<String, List<AdminAdapter.AdminItem>> slowTabCache = new java.util.HashMap<>();

    /** Avoid double-loading on the first {@link #onResume} right after {@link #onCreate}. */
    private boolean suppressResumeRefreshOnce = true;

    /** Item cache per tab — avoids re-fetching Firestore on every tab switch. */
    private final java.util.Map<String, List<AdminAdapter.AdminItem>> tabItemCache = new java.util.HashMap<>();
    private String currentQuery = "";
    private String currentTab = "Events";
    private String currentSort = "default";

    // ── Tab order for swipe gesture ───────────────────────────────────────────
    private static final String[] TAB_ORDER = {
            "Events", "Profiles", "Images", "Organizers", "Notifications", "Comments"
    };

    // ── Controller + caches ───────────────────────────────────────────────────
    private AdminController adminController;
    private final java.util.Map<String, Event>   eventDetailMap   = new java.util.HashMap<>();
    private final java.util.Map<String, Entrant> profileDetailMap = new java.util.HashMap<>();

    // ── Search debounce ───────────────────────────────────────────────────────
    private final Handler searchHandler = new Handler(Looper.getMainLooper());
    private Runnable searchRunnable;
    private boolean isLoading = false;

    // ── Swipe-to-switch-tab gesture ───────────────────────────────────────────
    private GestureDetector tabSwipeDetector;

    // =========================================================================
    // onCreate
    // =========================================================================

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin);

        adminController = new AdminController(this);

        // Bind tabs
        tabEvents        = findViewById(R.id.tab_events);
        tabProfiles      = findViewById(R.id.tab_profiles);
        tabImages        = findViewById(R.id.tab_images);
        tabOrganizers    = findViewById(R.id.tab_organizers);
        tabNotifications = findViewById(R.id.tab_notifications);
        tabComments      = findViewById(R.id.tab_comments);

        // Bind other views
        tvSectionTitle  = findViewById(R.id.tvSectionTitle);
        tvSectionCount  = findViewById(R.id.tvSectionCount);
        searchBar       = findViewById(R.id.etSearch);
        adminRecycler   = findViewById(R.id.adminRecycler);
        adminTabsScroll = findViewById(R.id.adminTabsScroll);
        emptyMessage    = findViewById(R.id.emptyMessage);
        loadingSpinner  = findViewById(R.id.loadingSpinner);
        swipeRefresh    = findViewById(R.id.swipeRefresh);

        // Search toggle — magnifying glass shows/hides search bar
        View btnSearchToggle = findViewById(R.id.btnSearchToggle);
        if (btnSearchToggle != null) {
            btnSearchToggle.setOnClickListener(v -> {
                if (searchBar.getVisibility() == View.VISIBLE) {
                    searchBar.setVisibility(View.GONE);
                    searchBar.setText("");
                    currentQuery = "";
                    filterCurrentList();
                } else {
                    searchBar.setVisibility(View.VISIBLE);
                    searchBar.requestFocus();
                    android.view.inputmethod.InputMethodManager imm =
                            (android.view.inputmethod.InputMethodManager)
                                    getSystemService(android.content.Context.INPUT_METHOD_SERVICE);
                    if (imm != null) imm.showSoftInput(searchBar, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);
                }
            });
        }

        // Pull-to-refresh — same cache bust as returning to this screen (see onResume)
        if (swipeRefresh != null) {
            swipeRefresh.setColorSchemeColors(
                    android.graphics.Color.parseColor("#0D6EFD"),
                    android.graphics.Color.parseColor("#0D1B2A"));
            swipeRefresh.setOnRefreshListener(() -> {
                AdminController.invalidateAll();
                slowTabCache.clear();
                allItems.clear();
                adapter.updateItems(new java.util.ArrayList<>());
                refreshCurrentTab();
            });
        }

        // RecyclerView
        adminRecycler.setLayoutManager(new LinearLayoutManager(this));
        adminRecycler.setHasFixedSize(true);
        adminRecycler.setItemViewCacheSize(20);

        DefaultItemAnimator animator = new DefaultItemAnimator();
        animator.setAddDuration(220);
        animator.setRemoveDuration(180);
        adminRecycler.setItemAnimator(animator);

        adapter = new AdminAdapter(new ArrayList<>(), this::handleRemoveClick, this::handleViewClick);
        adminRecycler.setAdapter(adapter);

        emptyMessage.setVisibility(View.GONE);
        adminRecycler.setVisibility(View.GONE);

        // Swipe left-to-delete
        setupSwipeToDelete();

        // Swipe left/right to switch tabs
        setupTabSwipeGesture();

        // Sort button
        View btnSort = findViewById(R.id.btnSort);
        if (btnSort != null) btnSort.setOnClickListener(v -> showSortDialog(btnSort));

        // Three-dot menu for role switch (US 03.09.01)
        View btnMoreOptions = findViewById(R.id.btnMoreOptions);
        if (btnMoreOptions != null) {
            btnMoreOptions.setOnClickListener(v -> showRoleSwitchPanel(btnMoreOptions));
        }

        // Tab click listeners — each switch invalidates cache for fresh data
        tabEvents.setOnClickListener(v -> showEvents());
        tabProfiles.setOnClickListener(v -> showProfiles());
        tabImages.setOnClickListener(v -> showImages());
        tabOrganizers.setOnClickListener(v -> showOrganizers());
        tabNotifications.setOnClickListener(v -> showNotifications());
        if (tabComments != null) tabComments.setOnClickListener(v -> showComments());

        // Search bar with 300ms debounce
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

        // Pre-warm cache: load profiles in parallel with events so "By: name" resolves fast
        adminController.getAllProfiles(profiles -> {
            if (profiles != null)
                for (Entrant p : profiles)
                    if (p.getDeviceId() != null) profileDetailMap.put(p.getDeviceId(), p);
        }, e -> {}, null);

        showEvents();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (suppressResumeRefreshOnce) {
            suppressResumeRefreshOnce = false;
            return;
        }
        // Bust AdminController session caches (events/profiles) and UI tab cache, then reload
        // the visible tab — same net effect as pull-to-refresh, for any return path (child
        // activities, role switch, home/recents, etc.).
        AdminController.invalidateAll();
        slowTabCache.clear();
        refreshCurrentTab();
    }

    // =========================================================================
    // Swipe left/right on RecyclerView to switch tabs
    // =========================================================================

    private void setupTabSwipeGesture() {
        tabSwipeDetector = new GestureDetector(this,
                new GestureDetector.SimpleOnGestureListener() {
                    private static final int MIN_DISTANCE = 60;
                    private static final int MAX_OFF_PATH = 300;
                    private static final int MIN_VELOCITY = 100;

                    @Override
                    public boolean onFling(MotionEvent e1, @NonNull MotionEvent e2,
                                           float vX, float vY) {
                        if (e1 == null) return false;
                        float diffY = Math.abs(e2.getY() - e1.getY());
                        float diffX = e2.getX() - e1.getX();
                        if (diffY > MAX_OFF_PATH) return false;
                        if (Math.abs(diffX) < MIN_DISTANCE || Math.abs(vX) < MIN_VELOCITY)
                            return false;
                        int idx = Arrays.asList(TAB_ORDER).indexOf(currentTab);
                        if (diffX < 0 && idx < TAB_ORDER.length - 1) {
                            // Swipe left → next tab
                            switchToTabByName(TAB_ORDER[idx + 1]);
                        } else if (diffX > 0 && idx > 0) {
                            // Swipe right → previous tab
                            switchToTabByName(TAB_ORDER[idx - 1]);
                        }
                        return true;
                    }
                });

        //noinspection AndroidLintClickableViewAccessibility
        adminRecycler.setOnTouchListener((v, event) -> {
            tabSwipeDetector.onTouchEvent(event);
            return false;
        });
    }

    private void switchToTabByName(String name) {
        switch (name) {
            case "Events":        showEvents();        break;
            case "Profiles":      showProfiles();      break;
            case "Images":        showImages();        break;
            case "Organizers":    showOrganizers();    break;
            case "Notifications": showNotifications(); break;
            case "Comments":      showComments();      break;
        }
    }

    // =========================================================================
    // Sort dialog
    // =========================================================================

    private void showSortDialog(View anchorView) {
        String[] options;
        String[] sortKeys;

        switch (currentTab) {
            case "Comments":
                options  = new String[]{"Default", "Name A to Z", "Name Z to A", "Most comments"};
                sortKeys = new String[]{"default", "az", "za", "most_comments"};
                break;
            case "Notifications":
                options  = new String[]{"Default", "Newest first", "Oldest first", "Title A to Z", "Title Z to A"};
                sortKeys = new String[]{"default", "newest", "oldest", "az", "za"};
                break;
            case "Profiles":
            case "Organizers":
                options  = new String[]{"Default", "Name A to Z", "Name Z to A"};
                sortKeys = new String[]{"default", "az", "za"};
                break;
            case "Events":
                options  = new String[]{"Default", "Name A to Z", "Name Z to A", "By event date"};
                sortKeys = new String[]{"default", "az", "za", "date"};
                break;
            case "Images":
                options  = new String[]{"Default", "Name A to Z", "Name Z to A"};
                sortKeys = new String[]{"default", "az", "za"};
                break;
            default:
                options  = new String[]{"Default", "Name A to Z", "Name Z to A"};
                sortKeys = new String[]{"default", "az", "za"};
                break;
        }


        // Custom styled sort dialog
        View sortView = LayoutInflater.from(this).inflate(R.layout.dialog_sort, null);
        LinearLayout sortContainer = sortView.findViewById(R.id.sortOptionsContainer);
        final android.widget.PopupWindow[] popupRef = new android.widget.PopupWindow[1];
        for (int si = 0; si < options.length; si++) {
            final int sidx = si;
            View row = LayoutInflater.from(this).inflate(R.layout.item_sort_option, sortContainer, false);
            TextView tvSortLabel = row.findViewById(R.id.tvSortLabel);
            android.widget.ImageView ivSortCheck = row.findViewById(R.id.ivSortCheck);
            tvSortLabel.setText(options[si]);
            if (sortKeys[si].equals(currentSort)) {
                tvSortLabel.setTextColor(android.graphics.Color.parseColor("#0D6EFD"));
                tvSortLabel.setTypeface(null, android.graphics.Typeface.BOLD);
                ivSortCheck.setVisibility(View.VISIBLE);
                row.setBackgroundColor(android.graphics.Color.parseColor("#F0F5FF"));
            } else {
                tvSortLabel.setTextColor(android.graphics.Color.parseColor("#0D1B2A"));
                tvSortLabel.setTypeface(null, android.graphics.Typeface.NORMAL);
                ivSortCheck.setVisibility(View.GONE);
            }
            row.setOnClickListener(v -> { currentSort = sortKeys[sidx]; popupRef[0].dismiss(); filterCurrentList(); });
            sortContainer.addView(row);
            if (si < options.length - 1) {
                View div = new View(this);
                div.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1));
                div.setBackgroundColor(android.graphics.Color.parseColor("#F3F4F6"));
                sortContainer.addView(div);
            }
        }
        // Show as small popup anchored below the Sort button
        popupRef[0] = new android.widget.PopupWindow(
                sortView,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
                true);
        popupRef[0].setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
        popupRef[0].setElevation(16f);
        popupRef[0].setOutsideTouchable(true);

        sortView.measure(
                android.view.View.MeasureSpec.makeMeasureSpec(0, android.view.View.MeasureSpec.UNSPECIFIED),
                android.view.View.MeasureSpec.makeMeasureSpec(0, android.view.View.MeasureSpec.UNSPECIFIED));

        int[] location = new int[2];
        anchorView.getLocationOnScreen(location);
        int offsetX = location[0] + anchorView.getWidth() - sortView.getMeasuredWidth() - 8;
        int offsetY = location[1] + anchorView.getHeight() + 4;
        popupRef[0].showAtLocation(anchorView, android.view.Gravity.NO_GRAVITY, offsetX, offsetY);

    }


    private void applySort(List<AdminAdapter.AdminItem> list) {
        switch (currentSort) {
            case "az":
                list.sort((a, b) -> {
                    if (a.title == null) return 1;
                    if (b.title == null) return -1;
                    return a.title.compareToIgnoreCase(b.title);
                });
                break;
            case "za":
                list.sort((a, b) -> {
                    if (a.title == null) return 1;
                    if (b.title == null) return -1;
                    return b.title.compareToIgnoreCase(a.title);
                });
                break;
            case "newest":
                // Sort by meta1 which contains the timestamp string descending
                list.sort((a, b) -> {
                    String da = a.meta1 != null ? a.meta1 : "";
                    String db = b.meta1 != null ? b.meta1 : "";
                    return db.compareToIgnoreCase(da);
                });
                break;
            case "oldest":
                list.sort((a, b) -> {
                    String da = a.meta1 != null ? a.meta1 : "";
                    String db = b.meta1 != null ? b.meta1 : "";
                    return da.compareToIgnoreCase(db);
                });
                break;
            case "date":
                // Sort by event date stored in meta1 (e.g. "📅 Jan 5, 2025")
                list.sort((a, b) -> {
                    String da = a.meta1 != null ? a.meta1 : "";
                    String db = b.meta1 != null ? b.meta1 : "";
                    return da.compareToIgnoreCase(db);
                });
                break;
            case "most_comments":
                // Sort by comment count stored in subtitle (e.g. "3 comments")
                list.sort((a, b) -> {
                    int ca = extractNumber(a.subtitle);
                    int cb = extractNumber(b.subtitle);
                    return Integer.compare(cb, ca);
                });
                break;
            default:
                break;
        }
    }

    private int extractNumber(String s) {
        if (s == null) return 0;
        try {
            String digits = s.replaceAll("[^0-9].*", "").trim();
            return digits.isEmpty() ? 0 : Integer.parseInt(digits);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    // =========================================================================
    // Swipe-to-delete
    // =========================================================================

    private void setupSwipeToDelete() {
        ItemTouchHelper.SimpleCallback swipeCallback = new ItemTouchHelper.SimpleCallback(
                0, ItemTouchHelper.LEFT) {

            @Override
            public int getMovementFlags(@NonNull RecyclerView recyclerView,
                                        @NonNull RecyclerView.ViewHolder viewHolder) {
                if (currentTab.equals("Notifications") || currentTab.equals("Comments")) {
                    return makeMovementFlags(0, 0);
                }
                return makeMovementFlags(0, ItemTouchHelper.LEFT);
            }

            @Override
            public boolean onMove(@NonNull RecyclerView rv,
                                  @NonNull RecyclerView.ViewHolder vh,
                                  @NonNull RecyclerView.ViewHolder target) {
                return false;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
                int pos = viewHolder.getBindingAdapterPosition();
                if (pos < 0 || pos >= adapter.getItems().size()) return;
                AdminAdapter.AdminItem item = adapter.getItems().get(pos);
                // Notifications and Comments tabs are read-only — snap back
                if (currentTab.equals("Notifications") || currentTab.equals("Comments")) {
                    adapter.notifyItemChanged(pos);
                    return;
                }
                handleRemoveClick(item);
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
                    float density = recyclerView.getContext()
                            .getResources().getDisplayMetrics().density;
                    Paint paint = new Paint();
                    paint.setColor(Color.parseColor("#D32F2F"));
                    float cornerRadius = 12f * density;
                    RectF background = new RectF(
                            itemView.getRight() + dX, itemView.getTop() + 8,
                            itemView.getRight(), itemView.getBottom() - 8);
                    c.drawRoundRect(background, cornerRadius, cornerRadius, paint);
                    Paint textPaint = new Paint();
                    textPaint.setColor(Color.WHITE);
                    textPaint.setTextSize(14 * density);
                    textPaint.setTextAlign(Paint.Align.CENTER);
                    float textX = itemView.getRight() - 50 * density;
                    float textY = itemView.getTop() + (itemView.getHeight() / 2f)
                            - ((textPaint.descent() + textPaint.ascent()) / 2);
                    c.drawText("Delete", textX, textY, textPaint);
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

    // =========================================================================
    // US 03.04.01 — Browse events
    // =========================================================================

    /**
     * Blocks Firestore-backed admin actions when the device has no validated internet (same as
     * {@link com.example.cobaltevents.ui.AccountSettingsActivity} / browse flows).
     *
     * @return false if offline (toast shown)
     */
    private boolean requireAdminInternet() {
        if (!NetworkConnectivity.hasValidatedInternet(this)) {
            Toast.makeText(this, R.string.comments_no_internet, Toast.LENGTH_SHORT).show();
            return false;
        }
        return true;
    }

    private void showEvents() {
        currentTab = "Events";
        currentSort = "default";
        resetSearch();
        setSelectedTab(tabEvents);
        tvSectionTitle.setText("Browse Events");
        if (!requireAdminInternet()) {
            hideLoading();
            allItems.clear();
            filterCurrentList();
            return;
        }
        showLoading();

        // Load profiles first, then events inside — guarantees "By: name" resolves
        adminController.getAllProfiles(profilesForEvents -> {
            if (profilesForEvents != null)
                for (Entrant ep : profilesForEvents)
                    if (ep.getDeviceId() != null) profileDetailMap.put(ep.getDeviceId(), ep);

            adminController.getAllEvents(events -> {
                List<AdminAdapter.AdminItem> newItems = new ArrayList<>();
                eventDetailMap.clear();

                if (events != null) {
                    for (Event e : events) {
                        String id = e.getEventId();
                        if (id == null || id.trim().isEmpty()) continue;

                        String title = e.getName() != null ? e.getName() : "Untitled Event";

                        // Resolve organizer name for "By: name" subtitle
                        List<String> evtOrgs = e.getOrganizers();
                        String evtOrgId = (evtOrgs != null && !evtOrgs.isEmpty()) ? evtOrgs.get(0) : null;
                        Entrant evtOrg = evtOrgId != null ? profileDetailMap.get(evtOrgId) : null;
                        String byLine = (evtOrg != null && evtOrg.getName() != null)
                                ? "By: " + evtOrg.getName()
                                : (evtOrgId != null ? "By: " + evtOrgId : "");
                        String descText = (e.getDescription() != null && !e.getDescription().trim().isEmpty())
                                ? e.getDescription() : "No description";
                        String subtitle = byLine.isEmpty() ? descText : byLine;

                        String detail = (e.getLocation() != null && !e.getLocation().trim().isEmpty())
                                ? "\ud83d\udccd " + e.getLocation() : null;
                        String meta1 = null;
                        if (e.getEventDate() != null)
                            meta1 = "\ud83d\udcc5 " + DateFormat.format("MMM d, yyyy", e.getEventDate().toDate());
                        String meta2 = null;
                        if (e.getConfirmedAttendeeIds() != null)
                            meta2 = "\u2705 " + e.getConfirmedAttendeeIds().size() + " confirmed";
                        String primaryCategory = e.getPrimaryCategory();
                        String badge = (primaryCategory != null && !primaryCategory.trim().isEmpty())
                                ? primaryCategory.toUpperCase() : null;

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
            }, null);
        }, e2 -> {}, null);
    }

    // =========================================================================
    // US 03.05.01 — Browse profiles
    // =========================================================================

    private void showProfiles() {
        currentTab = "Profiles";
        currentSort = "default";
        resetSearch();
        setSelectedTab(tabProfiles);
        tvSectionTitle.setText("Browse Profiles");
        if (!requireAdminInternet()) {
            hideLoading();
            allItems.clear();
            filterCurrentList();
            return;
        }
        showLoading();

        adminController.getAllProfiles(profiles -> {
            List<AdminAdapter.AdminItem> newItems = new ArrayList<>();
            profileDetailMap.clear();

            if (profiles != null) {
                for (Entrant p : profiles) {
                    String id = p.getDeviceId();
                    if (id == null || id.trim().isEmpty()) continue;

                    String title    = (p.getName() != null && !p.getName().trim().isEmpty())
                            ? p.getName() : "Unnamed User";
                    String subtitle = (p.getEmail() != null && !p.getEmail().trim().isEmpty())
                            ? "\u2709 " + p.getEmail() : "No email";
                    String detail   = (p.getPhone() != null && !p.getPhone().trim().isEmpty())
                            ? "\ud83d\udcde " + p.getPhone() : null;
                    String initials = getInitials(p.getName());

                    newItems.add(new AdminAdapter.AdminItem(
                            id, title, subtitle, null, null,
                            detail, null, null, null,
                            p.getProfilePictureUrl(), initials));

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
    // =========================================================================

    private void showImages() {
        currentTab = "Images";
        currentSort = "default";
        resetSearch();
        setSelectedTab(tabImages);
        tvSectionTitle.setText("Browse Images");
        if (!requireAdminInternet()) {
            hideLoading();
            allItems.clear();
            filterCurrentList();
            return;
        }
        showLoading();

        // Load profiles first, then images inside — guarantees "By: name" resolves
        adminController.getAllProfiles(profilesForImages -> {
            if (profilesForImages != null)
                for (Entrant ep : profilesForImages)
                    if (ep.getDeviceId() != null) profileDetailMap.put(ep.getDeviceId(), ep);

            adminController.getAllImagesFromEvents(events -> {
                List<AdminAdapter.AdminItem> newItems = new ArrayList<>();

                if (events != null) {
                    for (Event e : events) {
                        String id = e.getEventId();
                        if (id == null || id.trim().isEmpty()) continue;

                        String title = (e.getName() != null && !e.getName().trim().isEmpty())
                                ? e.getName() : "Untitled Event";

                        List<String> orgs = e.getOrganizers();
                        String organizerId = (orgs != null && !orgs.isEmpty()) ? orgs.get(0) : null;
                        Entrant organizer  = organizerId != null ? profileDetailMap.get(organizerId) : null;
                        String subtitle    = (organizer != null && organizer.getName() != null)
                                ? "By: " + organizer.getName()
                                : (organizerId != null ? "By: " + organizerId : "By: Unknown");

                        String date = "";
                        if (e.getEventDate() != null)
                            date = DateFormat.format("yyyy-MM-dd", e.getEventDate().toDate()).toString();

                        newItems.add(new AdminAdapter.AdminItem(
                                id, title, subtitle,
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
            }, null);
        }, e2 -> {}, null);
    }

    // =========================================================================
    // US 03.07.01 — Browse organizers
    // =========================================================================

    private void showOrganizers() {
        currentTab = "Organizers";
        currentSort = "default";
        resetSearch();
        setSelectedTab(tabOrganizers);
        tvSectionTitle.setText("Browse Organizers");
        if (!requireAdminInternet()) {
            hideLoading();
            allItems.clear();
            filterCurrentList();
            return;
        }
        showLoading();

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
                            ? "\u2709 " + o.getEmail() : "No email";
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
        }, null);
    }

    // =========================================================================
    // US 03.10.01 — Browse comments
    // =========================================================================

    private void showComments() {
        currentTab = "Comments";
        currentSort = "default";
        resetSearch();
        if (tabComments != null) setSelectedTab(tabComments);
        tvSectionTitle.setText("Browse Comments by Events");
        if (slowTabCache.containsKey("Comments")) {
            allItems.clear();
            allItems.addAll(slowTabCache.get("Comments"));
            filterCurrentList();
            return;
        }
        if (!requireAdminInternet()) {
            hideLoading();
            allItems.clear();
            filterCurrentList();
            return;
        }
        showLoading();

        adminController.getAllCommentsGroupedByEvent(grouped -> {
            List<AdminAdapter.AdminItem> newItems = new ArrayList<>();

            if (grouped != null) {
                for (java.util.Map.Entry<Event, List<Comment>> entry : grouped.entrySet()) {
                    Event event = entry.getKey();
                    List<Comment> comments = entry.getValue();
                    if (comments == null || comments.isEmpty()) continue;

                    String eventId   = event.getEventId();
                    String eventName = event.getName() != null ? event.getName() : "Unnamed Event";
                    int total        = comments.size();
                    int replyCount   = 0;
                    for (Comment c : comments) {
                        if (c.getReplies() != null) replyCount += c.getReplies().size();
                    }

                    String subtitle = total + " comment" + (total == 1 ? "" : "s")
                            + (replyCount > 0
                            ? " \u00b7 " + replyCount + " repl" + (replyCount == 1 ? "y" : "ies")
                            : "");
                    String meta1 = event.getPrimaryCategory() != null
                            ? "\ud83d\udcc2 " + event.getPrimaryCategory() : "";
                    String meta2 = "Tap to review and manage";

                    newItems.add(new AdminAdapter.AdminItem(
                            eventId, eventName, subtitle, "EVENT COMMENTS", "#0D6EFD",
                            null, meta1, meta2, null));
                }
            }

            allItems.clear();
            allItems.addAll(newItems);
            slowTabCache.put("Comments", new ArrayList<>(newItems));
            filterCurrentList();
        }, e -> {
            Toast.makeText(this, "Failed to load comments", Toast.LENGTH_SHORT).show();
            allItems.clear();
            filterCurrentList();
        });
    }

    // =========================================================================
    // US 03.08.01 — Browse notifications (read-only)
    // =========================================================================

    private void showNotifications() {
        currentTab = "Notifications";
        currentSort = "default";
        resetSearch();
        setSelectedTab(tabNotifications);
        tvSectionTitle.setText("Browse Notifications");
        if (slowTabCache.containsKey("Notifications")) {
            allItems.clear();
            allItems.addAll(slowTabCache.get("Notifications"));
            filterCurrentList();
            return;
        }
        if (!requireAdminInternet()) {
            hideLoading();
            allItems.clear();
            filterCurrentList();
            return;
        }
        showLoading();
        // Pre-load profiles so "To: name" always resolves
        adminController.getAllProfiles(profilesForNotif -> {
            if (profilesForNotif != null) for (Entrant ep : profilesForNotif) { if (ep.getDeviceId() != null) profileDetailMap.put(ep.getDeviceId(), ep); }
        }, ignore -> {}, null);

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

                    String badgeColor = "#9C27B0";
                    if ("selected".equalsIgnoreCase(n.getType())) badgeColor = "#2E7D32";

                    String sentTime = n.getTimestamp() != null
                            ? "\ud83d\udd50 " + DateFormat.format("MMM d, yyyy h:mm a",
                            n.getTimestamp().getTime()) : "";

                    // Browse: "To: name" — look up from profile cache
                    String recipientId = n.getRecipientId();
                    Entrant recipient  = recipientId != null ? profileDetailMap.get(recipientId) : null;
                    String recipientName = (recipient != null && recipient.getName() != null)
                            ? recipient.getName() : null;
                    String meta2 = recipientId != null
                            ? "To: " + (recipientName != null
                            ? recipientName
                            : recipientId.substring(0, Math.min(8, recipientId.length())) + "...")
                            : "";

                    newItems.add(new AdminAdapter.AdminItem(
                            id, title, subtitle, type, badgeColor,
                            null, sentTime, meta2, null));
                }
            }

            allItems.clear();
            allItems.addAll(newItems);
            slowTabCache.put("Notifications", new ArrayList<>(newItems));
            filterCurrentList();
        }, e -> {
            Toast.makeText(this, "Failed to load notifications", Toast.LENGTH_SHORT).show();
            allItems.clear();
            filterCurrentList();
        });
    }

    // =========================================================================
    // Loading helpers
    // =========================================================================

    private void showLoading() {
        isLoading = true;
        allItems.clear();
        adapter.updateItems(new ArrayList<>());
        loadingSpinner.setVisibility(View.VISIBLE);
        emptyMessage.setVisibility(View.GONE);
        adminRecycler.setVisibility(View.VISIBLE);
    }

    private void hideLoading() {
        isLoading = false;
        loadingSpinner.setVisibility(View.GONE);
        if (swipeRefresh != null && swipeRefresh.isRefreshing())
            swipeRefresh.setRefreshing(false);
    }

    // =========================================================================
    // Filtering + sorting
    // =========================================================================

    private void filterCurrentList() {
        hideLoading();

        List<AdminAdapter.AdminItem> filtered = new ArrayList<>();
        for (AdminAdapter.AdminItem item : allItems) {
            if (currentQuery.isEmpty()) {
                filtered.add(item);
                continue;
            }
            // Search title + subtitle + detail + meta fields
            String title    = item.title    != null ? item.title.toLowerCase()    : "";
            String subtitle = item.subtitle != null ? item.subtitle.toLowerCase() : "";
            String detail   = item.detail   != null ? item.detail.toLowerCase()   : "";
            String meta1    = item.meta1    != null ? item.meta1.toLowerCase()    : "";
            String meta2    = item.meta2    != null ? item.meta2.toLowerCase()    : "";
            if (title.contains(currentQuery) || subtitle.contains(currentQuery)
                    || detail.contains(currentQuery) || meta1.contains(currentQuery)
                    || meta2.contains(currentQuery)) {
                filtered.add(item);
            }
        }

        applySort(filtered);
        adapter.updateItems(filtered);
        adminRecycler.scrollToPosition(0);

        // Remove button hidden for read-only tabs
        adapter.setShowRemoveButton(
                currentTab.equals("Events") || currentTab.equals("Images"));

        tvSectionCount.setText(filtered.size() + " " + currentTab.toLowerCase());

        emptyMessage.setVisibility(!isLoading && filtered.isEmpty() ? View.VISIBLE : View.GONE);
        adminRecycler.setVisibility(View.VISIBLE);
    }

    // =========================================================================
    // Tap-to-view detail dialog
    // =========================================================================

    private void handleViewClick(AdminAdapter.AdminItem item) {
        // Comments tab — launch AdminCommentsActivity
        if (currentTab.equals("Comments")) {
            if (!requireAdminInternet()) {
                return;
            }
            try {
                Class<?> cls = Class.forName("com.example.cobaltevents.ui.admin.AdminCommentsActivity");
                Intent intent = new Intent(this, cls);
                intent.putExtra("EXTRA_EVENT_ID", item.id);
                intent.putExtra("EXTRA_EVENT_NAME", item.title);
                startActivity(intent);
            } catch (ClassNotFoundException ex) {
                Toast.makeText(this, "AdminCommentsActivity not found", Toast.LENGTH_SHORT).show();
            }
            return;
        }

        if (currentTab.equals("Images")) {
            showImageDetailDialog(item);
            return;
        }

        View dialogView = LayoutInflater.from(this)
                .inflate(R.layout.dialog_view_detail, null);

        TextView     tvType      = dialogView.findViewById(R.id.tvDetailType);
        TextView     tvTitle     = dialogView.findViewById(R.id.tvDetailTitle);
        TextView     tvSubtitle  = dialogView.findViewById(R.id.tvDetailSubtitle);
        LinearLayout rowDetail   = dialogView.findViewById(R.id.rowDetail);
        TextView     tvDetLabel  = dialogView.findViewById(R.id.tvDetailDetailLabel);
        TextView     tvDetail    = dialogView.findViewById(R.id.tvDetailDetail);
        LinearLayout rowMeta     = dialogView.findViewById(R.id.rowMeta);
        TextView     tvMetaLabel = dialogView.findViewById(R.id.tvDetailMetaLabel);
        TextView     tvMeta1     = dialogView.findViewById(R.id.tvDetailMeta1);
        TextView     tvMeta2     = dialogView.findViewById(R.id.tvDetailMeta2);
        ImageView    ivImage     = dialogView.findViewById(R.id.ivDetailImage);
        Button       btnClose    = dialogView.findViewById(R.id.btnDetailClose);

        tvType.setText(currentTab.toUpperCase());
        tvTitle.setText(item.title != null ? item.title : "");
        tvSubtitle.setText(item.subtitle != null ? item.subtitle : "");

        if (currentTab.equals("Events") && eventDetailMap.containsKey(item.id)) {
            Event e = eventDetailMap.get(item.id);

            StringBuilder detailText = new StringBuilder();
            if (e.getLocation() != null && !e.getLocation().trim().isEmpty())
                detailText.append("\ud83d\udccd ").append(e.getLocation());
            if (e.getRegistrationOpen() != null)
                detailText.append("\n\ud83d\udcec Opens: ")
                        .append(DateFormat.format("MMM d, yyyy", e.getRegistrationOpen().toDate()));
            if (e.getRegistrationClose() != null)
                detailText.append("\n\ud83d\udd12 Closes: ")
                        .append(DateFormat.format("MMM d, yyyy", e.getRegistrationClose().toDate()));
            if (detailText.length() > 0) {
                rowDetail.setVisibility(View.VISIBLE);
                tvDetLabel.setText("Schedule");
                tvDetail.setText(detailText.toString());
            }

            // Resolve uploader name and ID
            List<String> evtOrgs = e.getOrganizers();
            String uploaderIdStr = (evtOrgs != null && !evtOrgs.isEmpty()) ? evtOrgs.get(0) : null;
            Entrant uploader = uploaderIdStr != null ? profileDetailMap.get(uploaderIdStr) : null;
            String uploaderName = (uploader != null && uploader.getName() != null)
                    ? uploader.getName() : (uploaderIdStr != null ? uploaderIdStr : "Unknown");

            StringBuilder metaText1 = new StringBuilder();
            String categoryText = e.getCategoryDisplayText();
            if (!categoryText.isEmpty())
                metaText1.append("\ud83c\udff7 Category: ").append(categoryText);
            metaText1.append("\n\ud83d\udccd Geolocation: ")
                    .append(e.isGeolocationRequired() ? "Required" : "Not required");

            StringBuilder metaText2 = new StringBuilder();
            metaText2.append("Uploader: ").append(uploaderName);
            if (uploaderIdStr != null)
                metaText2.append("\nUploader ID: ").append(uploaderIdStr);
            if (e.getConfirmedAttendeeIds() != null)
                metaText2.append("\n\u2705 Confirmed: ").append(e.getConfirmedAttendeeIds().size());
            if (e.getWaitingListCapacity() > 0)
                metaText2.append("\n\ud83d\udc65 Waitlist capacity: ").append(e.getWaitingListCapacity());

            rowMeta.setVisibility(View.VISIBLE);
            tvMetaLabel.setText("Details");
            tvMeta1.setText(metaText1.toString().trim());
            tvMeta2.setText(metaText2.toString().trim());

        } else if ((currentTab.equals("Profiles") || currentTab.equals("Organizers"))
                && profileDetailMap.containsKey(item.id)) {
            Entrant p = profileDetailMap.get(item.id);

            if (p.getProfilePictureUrl() != null && !p.getProfilePictureUrl().isEmpty()) {
                ivImage.setVisibility(View.VISIBLE);
                com.bumptech.glide.Glide.with(this)
                        .load(p.getProfilePictureUrl())
                        .circleCrop()
                        .into(ivImage);
            }

            rowDetail.setVisibility(View.VISIBLE);
            tvDetLabel.setText("Contact");
            StringBuilder contactText = new StringBuilder();
            if (p.getEmail() != null && !p.getEmail().isEmpty())
                contactText.append("\u2709 ").append(p.getEmail());
            if (p.getPhone() != null && !p.getPhone().isEmpty())
                contactText.append("\n\ud83d\udcde ").append(p.getPhone());
            tvDetail.setText(contactText.toString());

            rowMeta.setVisibility(View.VISIBLE);
            tvMetaLabel.setText("Device ID");
            tvMeta1.setText(p.getDeviceId() != null ? p.getDeviceId() : "Unknown");
            tvMeta2.setText("");

        } else if (currentTab.equals("Notifications")) {
            // Detail: show "To: name (shortId)"
            if (item.detail != null && !item.detail.isEmpty()) {
                rowDetail.setVisibility(View.VISIBLE);
                tvDetLabel.setText("Sent");
                tvDetail.setText(item.detail);
            }
            if (item.meta1 != null || item.meta2 != null) {
                rowMeta.setVisibility(View.VISIBLE);
                tvMetaLabel.setText("Recipient");
                tvMeta1.setText(item.meta1 != null ? item.meta1 : "");
                tvMeta2.setText(item.meta2 != null ? item.meta2 : "");
            }
        } else {
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
        if (dialog.getWindow() != null)
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        applyDetailDialogScrollMaxHeight(dialogView);
    }

    /** Same scroll max-height behavior as {@code QRScanActivity} QR event popup. */
    private void applyDetailDialogScrollMaxHeight(View dialogRoot) {
        final View scroll = dialogRoot.findViewById(R.id.scroll_admin_detail_dialog);
        if (scroll == null) return;
        scroll.post(() -> {
            int screenH = getResources().getDisplayMetrics().heightPixels;
            int maxH = (int) (screenH * 0.65f);
            if (scroll.getHeight() > maxH) {
                ViewGroup.LayoutParams lp = scroll.getLayoutParams();
                lp.height = maxH;
                scroll.setLayoutParams(lp);
            }
        });
    }

    // =========================================================================
    // Image detail dialog
    // =========================================================================

    private void showImageDetailDialog(AdminAdapter.AdminItem item) {
        View dialogView = LayoutInflater.from(this)
                .inflate(R.layout.dialog_view_image, null);

        ImageView ivPoster  = dialogView.findViewById(R.id.ivImageDialogPoster);
        TextView  tvTitle   = dialogView.findViewById(R.id.tvImageDialogTitle);
        TextView  tvEvent   = dialogView.findViewById(R.id.tvImageDialogEvent);
        TextView  tvUploader = dialogView.findViewById(R.id.tvImageDialogUploader);
        TextView  tvDate    = dialogView.findViewById(R.id.tvImageDialogDate);
        Button    btnClose  = dialogView.findViewById(R.id.btnImageDialogCloseBottom);

        if (item.imageUrl != null && !item.imageUrl.isEmpty()) {
            com.bumptech.glide.Glide.with(this)
                    .load(item.imageUrl)
                    .centerCrop()
                    .into(ivPoster);
        }

        tvTitle.setText(item.title != null ? item.title : "");
        tvEvent.setText(item.title != null ? item.title : "");

        // Resolve uploader name and ID separately
        Event imgEvent = eventDetailMap.get(item.id);
        String uploaderIdStr = "Unknown";
        String uploaderName  = item.subtitle != null ? item.subtitle.replace("By: ", "") : "Unknown";
        if (imgEvent != null) {
            List<String> orgs = imgEvent.getOrganizers();
            if (orgs != null && !orgs.isEmpty()) {
                uploaderIdStr = orgs.get(0);
                Entrant org = profileDetailMap.get(uploaderIdStr);
                if (org != null && org.getName() != null) uploaderName = org.getName();
            }
        }
        tvUploader.setText("Uploader: " + uploaderName + "\nUploader ID: " + uploaderIdStr);
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
    // Delete flow
    // =========================================================================

    private void handleRemoveClick(AdminAdapter.AdminItem item) {
        if (!requireAdminInternet()) {
            return;
        }
        if (currentTab.equals("Images")) {
            View dialogView = LayoutInflater.from(this)
                    .inflate(R.layout.dialog_delete_image, null);
            TextView tvTitle     = dialogView.findViewById(R.id.tvImageDeleteTitle);
            Button btnImageOnly  = dialogView.findViewById(R.id.btnDeleteImageOnly);
            Button btnImageEvent = dialogView.findViewById(R.id.btnDeleteImageAndEvent);
            Button btnCancel     = dialogView.findViewById(R.id.btnImageDeleteCancel);
            tvTitle.setText(item.title != null ? item.title : "");
            AlertDialog dialog = new AlertDialog.Builder(this).setView(dialogView).create();
            btnImageOnly.setOnClickListener(v -> {
                if (!requireAdminInternet()) {
                    return;
                }
                dialog.dismiss();
                adminController.removeEventImage(item.id, item.imageUrl,
                        unused -> {
                            removeItemFromList(item);
                            Toast.makeText(this, "Image removed", Toast.LENGTH_SHORT).show();
                        },
                        e -> {
                            if (AdminController.isPosterUrlMismatchFailure(e)) {
                                Toast.makeText(this, R.string.admin_remove_image_poster_changed,
                                        Toast.LENGTH_LONG).show();
                            } else {
                                Toast.makeText(this, "Failed to remove image", Toast.LENGTH_SHORT).show();
                            }
                            showImages();
                        });
            });
            btnImageEvent.setOnClickListener(v -> {
                if (!requireAdminInternet()) {
                    return;
                }
                dialog.dismiss();
                removeItemFromList(item);
                adminController.removeEvent(item.id,
                        unused -> Toast.makeText(this, "Event and image deleted", Toast.LENGTH_SHORT).show(),
                        e -> { Toast.makeText(this, "Failed to delete event", Toast.LENGTH_SHORT).show(); showImages(); });
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
        Button deleteBtn     = dialogView.findViewById(R.id.btnConfirmDelete);
        Button cancelBtn     = dialogView.findViewById(R.id.btnCancelDelete);
        titleView.setText("Delete " + getItemTypeLabel() + "?");
        messageView.setText("Are you sure you want to delete \"" + item.title + "\"?");
        AlertDialog dialog = new AlertDialog.Builder(this).setView(dialogView).create();
        deleteBtn.setOnClickListener(v -> {
            if (!requireAdminInternet()) {
                return;
            }
            dialog.dismiss();
            v.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
            // Profiles / Organizers: do not optimistically remove — user may have deleted their
            // account already; refresh after the async delete finishes.
            if (!"Profiles".equals(currentTab) && !"Organizers".equals(currentTab)) {
                removeItemFromList(item);
            }
            performDelete(item);
        });
        cancelBtn.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
        if (dialog.getWindow() != null)
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
    }

    private void removeItemFromList(AdminAdapter.AdminItem item) {
        allItems.remove(item);
        filterCurrentList();
    }

    private void performDelete(AdminAdapter.AdminItem item) {
        switch (currentTab) {
            case "Events":
                adminController.removeEvent(item.id,
                        unused -> Toast.makeText(this, "Event removed", Toast.LENGTH_SHORT).show(),
                        e -> Toast.makeText(this, "Failed to remove event", Toast.LENGTH_SHORT).show());
                break;
            case "Profiles":
                adminController.removeProfile(item.id,
                        unused -> {
                            AdminController.invalidateAll();
                            refreshCurrentTab();
                            Toast.makeText(this, "Profile removed", Toast.LENGTH_SHORT).show();
                        },
                        e -> {
                            AdminController.invalidateAll();
                            refreshCurrentTab();
                            Toast.makeText(this, "Failed to remove profile", Toast.LENGTH_SHORT).show();
                        });
                break;
            case "Organizers":
                adminController.removeOrganizer(item.id,
                        unused -> {
                            AdminController.invalidateAll();
                            refreshCurrentTab();
                            Toast.makeText(this, "Organizer removed", Toast.LENGTH_SHORT).show();
                        },
                        e -> {
                            AdminController.invalidateAll();
                            refreshCurrentTab();
                            Toast.makeText(this, "Failed to remove organizer", Toast.LENGTH_SHORT).show();
                        });
                break;
        }
    }

    // =========================================================================
    // Utility helpers
    // =========================================================================

    private void refreshCurrentTab() {
        switch (currentTab) {
            case "Events":        showEvents();        break;
            case "Profiles":      showProfiles();      break;
            case "Images":        showImages();        break;
            case "Organizers":    showOrganizers();    break;
            case "Notifications": showNotifications(); break;
            case "Comments":      showComments();      break;
        }
    }

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

    private String getInitials(String name) {
        if (name == null || name.trim().isEmpty()) return "?";
        String[] parts = name.trim().split("\\s+");
        if (parts.length >= 2)
            return ("" + parts[0].charAt(0) + parts[1].charAt(0)).toUpperCase();
        return parts[0].substring(0, Math.min(2, parts[0].length())).toUpperCase();
    }

    /**
     * US 03.09.01: anchored popup from the header three-dot control. Inflates {@code panel_role_switch}:
     * a non-interactive, tinted current-role Admin row ({@code panelRowAdminCurrent}), then User and Organizer
     * rows that launch {@link EntrantActivity} / {@link OrganizerActivity} with {@code IS_ADMIN_SWITCH}.
     */
    private void showRoleSwitchPanel(View anchorView) {
        View panelView = LayoutInflater.from(this).inflate(R.layout.panel_role_switch, null);

        final android.widget.PopupWindow panel = new android.widget.PopupWindow(
                panelView,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
                true);
        panel.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
        panel.setElevation(16f);
        panel.setOutsideTouchable(true);

        // Slide in from right animation
        panel.setAnimationStyle(android.R.style.Animation_Dialog);

        panelView.measure(
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));

        int[] location = new int[2];
        anchorView.getLocationOnScreen(location);
        int offsetX = location[0] + anchorView.getWidth() - panelView.getMeasuredWidth();
        int offsetY = location[1] + anchorView.getHeight() + 8;
        panel.showAtLocation(anchorView, android.view.Gravity.NO_GRAVITY, offsetX, offsetY);

        panelView.findViewById(R.id.panelBtnSwitchToEntrant).setOnClickListener(v -> {
            panel.dismiss();
            Intent intent = new Intent(this, EntrantActivity.class);
            intent.putExtra("IS_ADMIN_SWITCH", true);
            startActivity(intent);
        });

        panelView.findViewById(R.id.panelBtnSwitchToOrganizer).setOnClickListener(v -> {
            panel.dismiss();
            Intent intent = new Intent(this, OrganizerActivity.class);
            intent.putExtra("IS_ADMIN_SWITCH", true);
            startActivity(intent);
        });
    }

    private void setSelectedTab(LinearLayout selectedTab) {
        LinearLayout[] allTabs = tabComments != null
                ? new LinearLayout[]{tabEvents, tabProfiles, tabImages, tabOrganizers, tabNotifications, tabComments}
                : new LinearLayout[]{tabEvents, tabProfiles, tabImages, tabOrganizers, tabNotifications};
        for (LinearLayout tab : allTabs) tab.setSelected(false);
        selectedTab.setSelected(true);
        // No fade animation — instant tab switch for smooth experience
        selectedTab.post(() -> {
            int scrollX = selectedTab.getLeft()
                    - (adminTabsScroll.getWidth() - selectedTab.getWidth()) / 2;
            adminTabsScroll.smoothScrollTo(scrollX, 0);
        });
    }
}