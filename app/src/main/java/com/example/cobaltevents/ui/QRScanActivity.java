package com.example.cobaltevents.ui;

import android.Manifest;
import android.content.Intent;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.graphics.drawable.ColorDrawable;
import android.view.ViewGroup;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.bumptech.glide.Glide;
import com.example.cobaltevents.R;
import com.example.cobaltevents.controller.GeolocationController;
import com.example.cobaltevents.db.EventDB;
import com.example.cobaltevents.db.EntrantDB;
import com.example.cobaltevents.db.NotificationDB;
import com.example.cobaltevents.db.WaitingListDB;
import com.example.cobaltevents.model.Event;
import com.example.cobaltevents.model.Entrant;
import com.example.cobaltevents.model.Notification;
import com.example.cobaltevents.model.WaitingList;
import com.google.firebase.Timestamp;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.List;

public class QRScanActivity extends AppCompatActivity {

    private EditText editEventCode;
    private EventDB eventDB;
    private WaitingListDB waitingListDB;
    private NotificationDB notificationDB;
    private EntrantDB entrantDB;
    private Entrant currentEntrant;
    private String deviceId;
    private GeolocationController geolocationController;
    private Event pendingGeoJoinEvent;
    private TextView pendingGeoJoinBtn;
    private final ActivityResultLauncher<String> locationPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted && pendingGeoJoinEvent != null) {
                    joinAndRecordLocation(pendingGeoJoinEvent, pendingGeoJoinBtn);
                } else if (!granted) {
                    android.widget.Toast.makeText(this,
                            "Location permission denied — cannot join this event.", android.widget.Toast.LENGTH_LONG).show();
                }
                pendingGeoJoinEvent = null;
                pendingGeoJoinBtn = null;
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_qr_scan);
        eventDB = new EventDB();
        waitingListDB = new WaitingListDB();
        notificationDB = new NotificationDB();
        entrantDB = new EntrantDB(this);
        currentEntrant = entrantDB.getEntrant();
        deviceId = currentEntrant != null ? currentEntrant.getDeviceId() : null;
        geolocationController = new GeolocationController();
        editEventCode = findViewById(R.id.edit_event_code);
        findViewById(R.id.btn_go_to_event).setOnClickListener(v -> onGoToEvent());
        findViewById(R.id.btn_simulate_qr).setOnClickListener(v -> onSimulate());
        setupBottomNavigation();
    }

    private void onSimulate() {
        eventDB.getAllEvents(events -> {
            if (events == null || events.isEmpty()) {
                android.widget.Toast.makeText(this, "No events to demo with", android.widget.Toast.LENGTH_SHORT).show();
                return;
            }
            java.util.List<com.example.cobaltevents.model.Event> valid = new java.util.ArrayList<>();
            for (com.example.cobaltevents.model.Event e : events) {
                if (e != null && e.getEventId() != null) valid.add(e);
            }
            if (valid.isEmpty()) {
                android.widget.Toast.makeText(this, "No events to demo with", android.widget.Toast.LENGTH_SHORT).show();
                return;
            }
            int randomIndex = (int) (Math.random() * valid.size());
            showEventPopup(valid.get(randomIndex));
        }, e -> android.widget.Toast.makeText(this, "No events to demo with", android.widget.Toast.LENGTH_SHORT).show());
    }

    private void onGoToEvent() {
        String code = editEventCode.getText() != null ? editEventCode.getText().toString().trim() : "";
        if (code.isEmpty()) {
            android.widget.Toast.makeText(this, "Please enter an event code", android.widget.Toast.LENGTH_SHORT).show();
            return;
        }
        eventDB.getEventByQrCode(code,
                event -> {
                    if (event == null) {
                        android.widget.Toast.makeText(this, "No event found for this code", android.widget.Toast.LENGTH_SHORT).show();
                    } else {
                        showEventPopup(event);
                    }
                },
                e -> android.widget.Toast.makeText(this, "Lookup failed: " + e.getMessage(), android.widget.Toast.LENGTH_SHORT).show());
    }

    private void showEventPopup(Event event) {
        View content = LayoutInflater.from(this).inflate(R.layout.dialog_event_card, null, false);
        content.setVisibility(View.INVISIBLE);
        android.widget.FrameLayout popupCanvas = new android.widget.FrameLayout(this);
        popupCanvas.addView(content);
        android.widget.ProgressBar canvasSpinner = new android.widget.ProgressBar(this);
        android.widget.FrameLayout.LayoutParams spinnerLp = new android.widget.FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        spinnerLp.gravity = android.view.Gravity.CENTER;
        popupCanvas.addView(canvasSpinner, spinnerLp);
        TextView tvName = content.findViewById(R.id.tv_event_name);
        ImageView ivEventImage = content.findViewById(R.id.iv_event_image);
        LinearLayout layoutCategoryTags = content.findViewById(R.id.layout_category_tags);
        TextView tvWaitlist = content.findViewById(R.id.tv_waitlist_count);
        TextView tvChevron = content.findViewById(R.id.tv_chevron);
        TextView tvDescription = content.findViewById(R.id.tv_description);
        TextView tvDetailDate = content.findViewById(R.id.tv_detail_date);
        TextView tvDetailTime = content.findViewById(R.id.tv_detail_time);
        TextView tvDetailLocation = content.findViewById(R.id.tv_detail_location);
        TextView tvPrice = content.findViewById(R.id.tv_price);
        TextView tvCapacity = content.findViewById(R.id.tv_capacity);
        TextView tvRegClose = content.findViewById(R.id.tv_reg_close);
        TextView tvCriteria = content.findViewById(R.id.tv_criteria_description);
        View layoutExpanded = content.findViewById(R.id.layout_expanded_details);
        View layoutGeo = content.findViewById(R.id.layout_geo_note);
        View layoutEventNotifs = content.findViewById(R.id.layout_event_notifications);
        TextView btnJoin = content.findViewById(R.id.btn_join);
        View closeInlineLayout = content.findViewById(R.id.layout_close_inline);
        TextView btnCloseInline = content.findViewById(R.id.btn_close_inline);

        // Keep layout stable while async registration/status loads.
        btnJoin.setVisibility(View.VISIBLE);
        btnJoin.setEnabled(false);
        btnJoin.setText("LOADING...");
        btnJoin.setBackgroundResource(R.drawable.bg_button_join_solid);
        btnJoin.setAlpha(0.45f);

        tvName.setText(event.getName() != null ? event.getName() : "Event");
        if (event.getPosterImageUrl() != null && !event.getPosterImageUrl().trim().isEmpty()) {
            Glide.with(this)
                    .load(event.getPosterImageUrl())
                    .centerCrop()
                    .placeholder(android.R.drawable.ic_menu_gallery)
                    .into(ivEventImage);
        } else {
            ivEventImage.setImageResource(android.R.drawable.ic_menu_gallery);
        }
        layoutCategoryTags.removeAllViews();
        boolean hasTags = false;
        if (event.isPrivate()) {
            layoutCategoryTags.addView(createPrivateChip());
            hasTags = true;
        }
        List<String> categories = event.getCategory();
        if (categories != null && !categories.isEmpty()) {
            for (String category : categories) {
                if (category == null || category.trim().isEmpty()) continue;
                layoutCategoryTags.addView(createCategoryChip(category.trim()));
                hasTags = true;
            }
        }
        layoutCategoryTags.setVisibility(hasTags ? View.VISIBLE : View.GONE);
        tvWaitlist.setVisibility(View.GONE); // not computing count in popup
        tvChevron.setVisibility(View.GONE);
        layoutExpanded.setVisibility(View.VISIBLE);
        layoutGeo.setVisibility(View.GONE);
        layoutEventNotifs.setVisibility(View.GONE);
        btnJoin.setVisibility(View.VISIBLE);
        closeInlineLayout.setVisibility(View.VISIBLE);

        tvDescription.setText(event.getDescription() != null ? event.getDescription() : "No description available.");
        if (event.getEventDate() != null) {
            tvDetailDate.setText(DATE_FORMAT.format(event.getEventDate().toDate()));
            tvDetailTime.setText(TIME_FORMAT.format(event.getEventDate().toDate()));
        } else {
            tvDetailDate.setText("TBD");
            tvDetailTime.setText("TBD");
        }
        tvDetailLocation.setText(event.getLocation() != null ? event.getLocation() : "TBD");
        tvPrice.setText(formatPrice(event.getPrice()));
        tvCapacity.setText(event.getWaitingListCapacity() > 0 ? event.getWaitingListCapacity() + " spots" : "Unlimited");
        if (event.getRegistrationClose() != null) {
            tvRegClose.setText(DATE_FORMAT.format(event.getRegistrationClose().toDate()));
        } else {
            tvRegClose.setText("TBD");
        }
        String criteriaText = (event.getCriteria() != null && !event.getCriteria().isEmpty())
                ? event.getCriteria()
                : "No special criteria.";
        tvCriteria.setText(criteriaText);

        final androidx.appcompat.app.AlertDialog dialog =
                new MaterialAlertDialogBuilder(this)
                        .setView(popupCanvas)
                        .setCancelable(true)
                        .create();
        dialog.show();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(android.graphics.Color.TRANSPARENT));
        }
        btnCloseInline.setOnClickListener(v -> dialog.dismiss());

        View innerCardMaybe = content.findViewById(R.id.include_event_card);
        if (innerCardMaybe instanceof androidx.cardview.widget.CardView) {
            androidx.cardview.widget.CardView innerCard = (androidx.cardview.widget.CardView) innerCardMaybe;
            innerCard.setCardElevation(0f);
            innerCard.setUseCompatPadding(false);
            innerCard.setPreventCornerOverlap(false);
            innerCard.setCardBackgroundColor(android.graphics.Color.TRANSPARENT);
            ViewGroup.LayoutParams params = innerCard.getLayoutParams();
            if (params instanceof ViewGroup.MarginLayoutParams) {
                ((ViewGroup.MarginLayoutParams) params).setMargins(0, 0, 0, 0);
                innerCard.setLayoutParams(params);
            }
        }

        final View scroll = content.findViewById(R.id.scroll_event_dialog);
        if (scroll != null) {
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

        if (event.getEventId() != null && deviceId != null) {
            waitingListDB.getRegistrationForEventAnyStatus(event.getEventId(), deviceId,
                    reg -> {
                        String baseStatus = reg != null ? reg.getStatus() : null;
                        notificationDB.getNotificationsForRecipientAndEvent(deviceId, event.getEventId(),
                                notifications -> {
                                    String status = applyNotificationStatusOverride(baseStatus, notifications);
                                    boolean isJoinedActive = isActiveStatus(status);
                                    waitingListDB.getActiveCountForEvent(event.getEventId(),
                                            count -> bindJoinButton(btnJoin, event, status, isJoinedActive, count, dialog, content, canvasSpinner),
                                            e2 -> bindJoinButton(btnJoin, event, status, isJoinedActive, null, dialog, content, canvasSpinner));
                                },
                                e3 -> {
                                    boolean isJoinedActive = isActiveStatus(baseStatus);
                                    waitingListDB.getActiveCountForEvent(event.getEventId(),
                                            count -> bindJoinButton(btnJoin, event, baseStatus, isJoinedActive, count, dialog, content, canvasSpinner),
                                            e2 -> bindJoinButton(btnJoin, event, baseStatus, isJoinedActive, null, dialog, content, canvasSpinner));
                                });
                    },
                    e -> {
                        waitingListDB.getActiveCountForEvent(event.getEventId(),
                                count -> bindJoinButton(btnJoin, event, null, false, count, dialog, content, canvasSpinner),
                                e2 -> bindJoinButton(btnJoin, event, null, false, null, dialog, content, canvasSpinner));
                    });
        } else {
            bindJoinButton(btnJoin, event, null, false, null, dialog, content, canvasSpinner);
        }
    }

    private static final java.text.SimpleDateFormat DATE_FORMAT =
            new java.text.SimpleDateFormat("MMM d, yyyy", java.util.Locale.getDefault());
    private static final java.text.SimpleDateFormat TIME_FORMAT =
            new java.text.SimpleDateFormat("h:mm a", java.util.Locale.getDefault());

    private static String formatPrice(String raw) {
        if (raw == null) return "TBD";
        String p = raw.trim();
        if (p.isEmpty()) return "TBD";
        if (p.startsWith("$")) return p;
        if (p.matches("^\\d+(?:\\.\\d{1,2})?$")) return "$" + p;
        return p;
    }

    private TextView createCategoryChip(String label) {
        TextView chip = new TextView(this);
        chip.setText(label);
        chip.setTextSize(11f);
        chip.setTextColor(ContextCompat.getColor(this, R.color.header_teal));
        chip.setBackgroundResource(R.drawable.bg_tag_teal);
        int hPad = dpToPx(10);
        int vPad = dpToPx(3);
        chip.setPadding(hPad, vPad, hPad, vPad);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        lp.setMarginEnd(dpToPx(6));
        chip.setLayoutParams(lp);
        return chip;
    }

    private LinearLayout createPrivateChip() {
        LinearLayout chip = new LinearLayout(this);
        chip.setOrientation(LinearLayout.HORIZONTAL);
        chip.setGravity(android.view.Gravity.CENTER_VERTICAL);
        chip.setBackgroundResource(R.drawable.bg_private_tag);
        int hPad = dpToPx(8);
        int vPad = dpToPx(2);
        chip.setPadding(hPad, vPad, hPad, vPad);

        ImageView icon = new ImageView(this);
        icon.setImageResource(R.drawable.ic_lock_private);
        LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(dpToPx(12), dpToPx(12));
        icon.setLayoutParams(iconLp);

        TextView label = new TextView(this);
        label.setText(R.string.private_tag_label);
        label.setTextSize(12f);
        label.setTextColor(ContextCompat.getColor(this, R.color.private_tag_text));
        label.setTypeface(label.getTypeface(), android.graphics.Typeface.BOLD);
        LinearLayout.LayoutParams textLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        textLp.setMarginStart(dpToPx(4));
        label.setLayoutParams(textLp);

        LinearLayout.LayoutParams chipLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        chipLp.setMarginEnd(dpToPx(6));
        chip.setLayoutParams(chipLp);

        chip.addView(icon);
        chip.addView(label);
        return chip;
    }

    private int dpToPx(int dp) {
        float density = getResources().getDisplayMetrics().density;
        return Math.round(dp * density);
    }

    private void applyJoinButtonState(TextView btn, Event event, String status, boolean isJoined, Integer waitlistCount) {
        Timestamp now = Timestamp.now();
        boolean registrationClosed = event != null
                && event.getRegistrationClose() != null
                && event.getRegistrationClose().compareTo(now) < 0;
        boolean upcoming = event != null
                && event.getRegistrationOpen() != null
                && event.getRegistrationOpen().compareTo(now) > 0
                && event.getEventDate() != null
                && event.getEventDate().compareTo(now) > 0;
        int capacity = event != null ? event.getWaitingListCapacity() : 0;
        // If you're already joined, you should be able to leave even when the waitlist is full.
        boolean full = !isJoined && capacity > 0 && waitlistCount != null && waitlistCount >= capacity;

        if (registrationClosed) {
            btn.setText("REGISTRATION CLOSED");
            btn.setBackgroundResource(R.drawable.bg_button_join_solid);
            btn.setAlpha(0.45f);
            btn.setEnabled(false);
        } else if (upcoming) {
            btn.setText("UPCOMING");
            btn.setBackgroundResource(R.drawable.bg_button_upcoming);
            btn.setAlpha(0.45f);
            btn.setEnabled(false);
        } else if (full) {
            btn.setText("WAITLIST FULL");
            btn.setBackgroundResource(R.drawable.bg_button_join_solid);
            btn.setAlpha(0.45f);
            btn.setEnabled(false);
        } else if (WaitingList.STATUS_ENROLLED.equals(status)) {
            btn.setText("ENROLLED");
            btn.setBackgroundResource(R.drawable.bg_button_join_solid);
            btn.setAlpha(0.45f);
            btn.setEnabled(false);
        } else if (isDeclinedStyleStatus(status)) {
            btn.setText("DECLINED");
            btn.setBackgroundResource(R.drawable.bg_button_join_solid);
            btn.setAlpha(0.45f);
            btn.setEnabled(false);
        } else if (event != null && event.isPrivate() && !isJoined) {
            // Declined must always win over "PRIVATE EVENT" styling.
            btn.setText("PRIVATE EVENT");
            btn.setBackgroundResource(R.drawable.bg_button_join_solid);
            btn.setAlpha(0.45f);
            btn.setEnabled(false);
        } else if (isJoined) {
            btn.setText("LEAVE WAITLIST");
            btn.setBackgroundResource(R.drawable.bg_button_red_pill);
            btn.setAlpha(1.0f);
            btn.setEnabled(true);
        } else {
            btn.setText("JOIN WAITLIST");
            btn.setBackgroundResource(R.drawable.bg_button_join_solid);
            btn.setAlpha(1.0f);
            btn.setEnabled(true);
        }
    }

    private void bindJoinButton(TextView btn, Event event, String status, boolean isJoinedActive,
                                Integer waitlistCount, androidx.appcompat.app.AlertDialog dialog,
                                View content, View canvasSpinner) {
        if (canvasSpinner != null) canvasSpinner.setVisibility(View.GONE);
        content.setVisibility(View.VISIBLE);
        applyJoinButtonState(btn, event, status, isJoinedActive, waitlistCount);
        btn.setOnClickListener(v -> {
            if (!btn.isEnabled()) return;
            if (isJoinedActive) {
                leaveWaitlist(event, btn);
            } else {
                joinWaitlist(event, btn);
            }
            dialog.dismiss();
        });
    }

    private boolean isActiveStatus(String status) {
        return WaitingList.STATUS_PENDING.equals(status)
                || WaitingList.STATUS_SELECTED.equals(status)
                || WaitingList.STATUS_NOT_SELECTED.equals(status)
                || WaitingList.STATUS_ENROLLED.equals(status);
    }

    private boolean isDeclinedStyleStatus(String status) {
        return WaitingList.STATUS_DECLINED.equals(status)
                || "rejected".equals(status);
    }

    private String applyNotificationStatusOverride(String baseStatus, List<Notification> notifications) {
        if (notifications == null || notifications.isEmpty()) return baseStatus;
        for (Notification n : notifications) {
            if (n == null || n.getType() == null) continue;
            String type = n.getType();
            String response = n.getResponse();
            // X (not-selected) means the user wasn't selected, but they remain on the waitlist.
            if (Notification.TYPE_NOT_SELECTED.equals(type)) {
                if (baseStatus == null) return null;
                // Never override a real/final DB decision (e.g. rejected/declined).
                if (WaitingList.STATUS_DECLINED.equals(baseStatus)) return baseStatus;
                if (WaitingList.STATUS_ENROLLED.equals(baseStatus) || WaitingList.STATUS_SELECTED.equals(baseStatus)) {
                    return baseStatus;
                }
                return WaitingList.STATUS_NOT_SELECTED;
            }
            if (Notification.TYPE_PRIVATE_EVENT.equals(type)) {
                // Private invitations should not force join/leave state in the QR popup
                // until the user presses accept/decline and a waitlist entry exists.
                return baseStatus;
            }
            if (Notification.TYPE_SELECTED.equals(type) || Notification.TYPE_GOT_OFF_WAITLIST.equals(type)) {
                if (Notification.RESPONSE_ACCEPTED.equals(response)) return WaitingList.STATUS_ENROLLED;
                if (Notification.RESPONSE_DECLINED.equals(response)) return WaitingList.STATUS_DECLINED;
                // Pending selected/star must not force join/leave UI.
                return baseStatus;
            }
        }
        return baseStatus;
    }

    private void joinWaitlist(Event event, TextView btn) {
        if (event == null || event.getEventId() == null) return;
        if (!isNetworkAvailable()) {
            android.widget.Toast.makeText(this,
                    "Failed to join: No internet connection.",
                    android.widget.Toast.LENGTH_SHORT).show();
            return;
        }
        if (currentEntrant == null || !currentEntrant.isValidName() || !currentEntrant.isValidEmail()) {
            android.widget.Toast.makeText(this, "Complete your name and email in Account settings first.", android.widget.Toast.LENGTH_LONG).show();
            startActivity(new Intent(this, AccountSettingsActivity.class));
            return;
        }
        if (event.isGeolocationRequired()) {
            if (geolocationController.hasLocationPermission(this)) {
                joinAndRecordLocation(event, btn);
            } else {
                pendingGeoJoinEvent = event;
                pendingGeoJoinBtn = btn;
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
        performJoinWaitlist(event, btn);
    }

    private void joinAndRecordLocation(Event event, TextView btn) {
        geolocationController.checkDistanceForEvent(this, event,
                new GeolocationController.GeoJoinCallback() {
                    @Override
                    public void onAllowed(android.location.Location userLocation) {
                        performJoinWaitlist(event, btn);
                        geolocationController.recordLocationForEvent(
                                QRScanActivity.this, deviceId, event.getEventId(),
                                userLocation, unused -> {}, e -> {});
                    }
                    @Override
                    public void onBlocked(float distanceMeters) {
                        int km = Math.round(distanceMeters / 1000f);
                        android.widget.Toast.makeText(QRScanActivity.this,
                                "You are " + km + "km away. Must be within 30km to join.",
                                android.widget.Toast.LENGTH_LONG).show();
                    }
                    @Override
                    public void onError(String message) {
                        android.widget.Toast.makeText(QRScanActivity.this, message,
                                android.widget.Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void performJoinWaitlist(Event event, TextView btn) {
        WaitingList registration = new WaitingList(
                event.getEventId(),
                deviceId,
                1,
                currentEntrant.getName(),
                currentEntrant.getEmail(),
                currentEntrant.getPhone(),
                WaitingList.NOTIFY_EMAIL
        );
        waitingListDB.addRegistration(registration,
                id -> android.widget.Toast.makeText(this, R.string.waitlist_success, android.widget.Toast.LENGTH_SHORT).show(),
                e -> android.widget.Toast.makeText(this, getString(R.string.waitlist_fail) + " " + e.getMessage(), android.widget.Toast.LENGTH_SHORT).show());
    }

    private void leaveWaitlist(Event event, TextView btn) {
        if (event == null || event.getEventId() == null || deviceId == null) return;
        if (!isNetworkAvailable()) {
            android.widget.Toast.makeText(this,
                    "Failed to leave waitlist: No internet connection.",
                    android.widget.Toast.LENGTH_SHORT).show();
            return;
        }
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
                        android.widget.Toast.makeText(this,
                                "Cannot leave waitlist was selected for enrollment.",
                                android.widget.Toast.LENGTH_LONG).show();
                        return;
                    }
                    waitingListDB.deleteRegistration(event.getEventId(), deviceId,
                            unused -> android.widget.Toast.makeText(this, "Left waitlist for " + (event.getName() != null ? event.getName() : "event"), android.widget.Toast.LENGTH_SHORT).show(),
                            e -> android.widget.Toast.makeText(this, "Failed to leave: " + e.getMessage(), android.widget.Toast.LENGTH_SHORT).show());
                },
                e -> android.widget.Toast.makeText(this,
                        "Unable to verify selection status. Please try again.",
                        android.widget.Toast.LENGTH_SHORT).show());
    }
    private void setupBottomNavigation() {
        findViewById(R.id.nav_events).setOnClickListener(v -> {
            startActivity(new Intent(this, EventListActivity.class));
            finish();
        });
        findViewById(R.id.nav_my_events).setOnClickListener(v -> {
            startActivity(new Intent(this, EventHistoryActivity.class));
            finish();
        });
        findViewById(R.id.nav_notifications).setOnClickListener(v -> {
            startActivity(new Intent(this, NotificationsActivity.class));
        });
        findViewById(R.id.nav_account).setOnClickListener(v -> {
            startActivity(new Intent(this, AccountSettingsActivity.class));
            finish();
        });

        int inactive = ContextCompat.getColor(this, R.color.grey_nav_inactive);
        tintNavIconAndText(R.id.iv_nav_events, R.id.tv_nav_events, inactive);
        tintNavIconAndText(R.id.iv_nav_my_events, R.id.tv_nav_my_events, inactive);
        tintNavIconAndText(R.id.iv_nav_notifications, R.id.tv_nav_notifications, inactive);
        tintNavIconAndText(R.id.iv_nav_account, R.id.tv_nav_account, inactive);
    }

    private void tintNavIconAndText(int iconId, int textId, int color) {
        ImageView icon = findViewById(iconId);
        TextView text = findViewById(textId);
        if (icon != null) icon.setColorFilter(color);
        if (text != null) text.setTextColor(color);
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
