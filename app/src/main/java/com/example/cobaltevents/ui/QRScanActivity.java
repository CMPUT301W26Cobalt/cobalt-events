package com.example.cobaltevents.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.graphics.drawable.ColorDrawable;
import android.view.ViewGroup;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.example.cobaltevents.R;
import com.example.cobaltevents.db.EventDB;
import com.example.cobaltevents.db.EntrantDB;
import com.example.cobaltevents.db.WaitingListDB;
import com.example.cobaltevents.model.Event;
import com.example.cobaltevents.model.Entrant;
import com.example.cobaltevents.model.WaitingList;
import com.google.firebase.Timestamp;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

public class QRScanActivity extends AppCompatActivity {

    private EditText editEventCode;
    private EventDB eventDB;
    private WaitingListDB waitingListDB;
    private EntrantDB entrantDB;
    private Entrant currentEntrant;
    private String deviceId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_qr_scan);
        // UI only for now; functionality will be added later.
        eventDB = new EventDB();
        waitingListDB = new WaitingListDB();
        entrantDB = new EntrantDB(this);
        currentEntrant = entrantDB.getEntrant();
        deviceId = currentEntrant != null ? currentEntrant.getDeviceId() : null;
        editEventCode = findViewById(R.id.edit_event_code);
        findViewById(R.id.btn_go_to_event).setOnClickListener(v -> onGoToEvent());
        setupBottomNavigation();
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
        // Populate fields similarly to EventAdapter, but show details expanded and hide actions
        TextView tvName = content.findViewById(R.id.tv_event_name);
        TextView tvCategory = content.findViewById(R.id.tv_category_tag);
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

        tvName.setText(event.getName() != null ? event.getName() : "Event");
        if (event.getCategory() != null && !event.getCategory().isEmpty()) {
            tvCategory.setVisibility(View.VISIBLE);
            tvCategory.setText(event.getCategory());
        } else {
            tvCategory.setVisibility(View.GONE);
        }
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
                        .setView(content)
                        .setCancelable(true)
                        .create();
        dialog.show();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(android.graphics.Color.TRANSPARENT));
        }
        btnCloseInline.setOnClickListener(v -> dialog.dismiss());

        // Remove inner (included) CardView elevation/margins so only the outer card provides corners
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

        // Restrict dialog scrollable height to ~65% of screen
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

        // Determine join/leave state and wire the button
        if (event.getEventId() != null && deviceId != null) {
            waitingListDB.getActiveRegistrationForEvent(event.getEventId(), deviceId,
                    reg -> {
                        boolean isJoined = reg != null;
                        applyJoinButtonState(btnJoin, isJoined);
                        btnJoin.setOnClickListener(v -> {
                            if (isJoined) {
                                leaveWaitlist(event, btnJoin);
                            } else {
                                joinWaitlist(event, btnJoin);
                            }
                            dialog.dismiss(); // close after action
                        });
                    },
                    e -> {
                        applyJoinButtonState(btnJoin, false);
                        btnJoin.setOnClickListener(v -> {
                            joinWaitlist(event, btnJoin);
                            dialog.dismiss();
                        });
                    });
        } else {
            applyJoinButtonState(btnJoin, false);
            btnJoin.setOnClickListener(v -> {
                joinWaitlist(event, btnJoin);
                dialog.dismiss();
            });
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

    private void applyJoinButtonState(TextView btn, boolean isJoined) {
        if (isJoined) {
            btn.setText("LEAVE WAITLIST");
            btn.setBackgroundResource(R.drawable.bg_button_red_pill);
            btn.setAlpha(1.0f);
            btn.setEnabled(true);
        } else {
            btn.setText("JOIN WAITLIST");
            btn.setBackgroundResource(R.drawable.bg_button_primary_account);
            btn.setAlpha(1.0f);
            btn.setEnabled(true);
        }
    }

    private void joinWaitlist(Event event, TextView btn) {
        if (event == null || event.getEventId() == null) return;
        if (currentEntrant == null || !currentEntrant.isValidName() || !currentEntrant.isValidEmail()) {
            android.widget.Toast.makeText(this, "Complete your name and email in Account settings first.", android.widget.Toast.LENGTH_LONG).show();
            startActivity(new Intent(this, AccountSettingsActivity.class));
            return;
        }
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
        waitingListDB.deleteRegistration(event.getEventId(), deviceId,
                unused -> android.widget.Toast.makeText(this, "Left waitlist for " + (event.getName() != null ? event.getName() : "event"), android.widget.Toast.LENGTH_SHORT).show(),
                e -> android.widget.Toast.makeText(this, "Failed to leave: " + e.getMessage(), android.widget.Toast.LENGTH_SHORT).show());
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

        // Dim other tabs to match active state behavior
        int inactive = ContextCompat.getColor(this, R.color.grey_nav_inactive);
        tintNavIconAndText(R.id.iv_nav_events, R.id.tv_nav_events, inactive);
        tintNavIconAndText(R.id.iv_nav_my_events, R.id.tv_nav_my_events, inactive);
        tintNavIconAndText(R.id.iv_nav_notifications, R.id.tv_nav_notifications, inactive);
        tintNavIconAndText(R.id.iv_nav_account, R.id.tv_nav_account, inactive);
        // Center QR stays the green pill by design
    }

    private void tintNavIconAndText(int iconId, int textId, int color) {
        ImageView icon = findViewById(iconId);
        TextView text = findViewById(textId);
        if (icon != null) icon.setColorFilter(color);
        if (text != null) text.setTextColor(color);
    }
}
