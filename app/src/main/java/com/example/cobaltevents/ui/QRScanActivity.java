package com.example.cobaltevents.ui;

import android.Manifest;
import android.content.Intent;
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
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
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
import com.example.cobaltevents.ui.comments.EventCommentsUiBinder;
import com.example.cobaltevents.ui.waitlist.RegistrationPeriodUi;
import com.example.cobaltevents.ui.waitlist.WaitlistCountDisplayUi;
import com.example.cobaltevents.ui.waitlist.WaitlistStatusUi;
import com.example.cobaltevents.util.EventGoneUi;
import com.example.cobaltevents.util.NetworkConnectivity;
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
    /** Shown only while verifying location for geolocked waitlist join from the QR popup. */
    private AlertDialog geoJoinLoadingDialog;
    private Event pendingGeoJoinEvent;
    private TextView pendingGeoJoinBtn;
    /** While the QR event dialog is open, re-fetch join state (e.g. after capacity error). */
    private Runnable qrRefreshJoinUi;
    private View qrPopupContent;
    private TextView qrPopupBtnJoin;
    private androidx.appcompat.app.AlertDialog qrActiveDialog;
    private View qrPopupCanvasSpinner;
    private TextView qrPopupTvWaitlist;
    /** Join/waitlist state for the open QR dialog; used to allow comments on private events when already enrolled. */
    private boolean qrPopupIsJoinedActive;
    private final ActivityResultLauncher<String> locationPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted && pendingGeoJoinEvent != null) {
                    final Event pending = pendingGeoJoinEvent;
                    final TextView btn = pendingGeoJoinBtn;
                    pendingGeoJoinEvent = null;
                    pendingGeoJoinBtn = null;
                    eventDB.getEventFromServer(pending.getEventId(), fresh -> runOnUiThread(() -> {
                        if (fresh == null) {
                            android.widget.Toast.makeText(this, "Could not load event.", android.widget.Toast.LENGTH_SHORT).show();
                            return;
                        }
                        if (qrPopupContent != null) {
                            applyQrPopupEventFields(qrPopupContent, fresh);
                        }
                        if (qrPopupBtnJoin != null && qrActiveDialog != null) {
                            loadQrPopupJoinButtonState(fresh, qrPopupBtnJoin, qrActiveDialog, qrPopupContent,
                                    qrPopupCanvasSpinner, qrPopupTvWaitlist);
                        }
                        Runnable dismiss = qrActiveDialog != null ? qrActiveDialog::dismiss : null;
                        checkCapacityThenProceedJoinQr(fresh, btn, qrActiveDialog, qrPopupContent, qrPopupCanvasSpinner,
                                qrPopupTvWaitlist, () -> joinAndRecordLocation(fresh, btn, dismiss));
                    }), e -> runOnUiThread(() ->
                            android.widget.Toast.makeText(this, "Could not load event.", android.widget.Toast.LENGTH_SHORT).show()));
                } else if (!granted) {
                    android.widget.Toast.makeText(this,
                            "Location permission denied — cannot join this event.", android.widget.Toast.LENGTH_LONG).show();
                    pendingGeoJoinEvent = null;
                    pendingGeoJoinBtn = null;
                }
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
        }, e -> android.widget.Toast.makeText(this, "No events to demo with", android.widget.Toast.LENGTH_SHORT).show(),
                () -> runOnUiThread(() -> android.widget.Toast.makeText(this, R.string.firebase_cache_fallback_message,
                        android.widget.Toast.LENGTH_LONG).show()));
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
                e -> android.widget.Toast.makeText(this, "Lookup failed: " + e.getMessage(), android.widget.Toast.LENGTH_SHORT).show(),
                () -> runOnUiThread(() -> android.widget.Toast.makeText(this, R.string.firebase_cache_fallback_message,
                        android.widget.Toast.LENGTH_LONG).show()));
    }

    private void showEventPopup(Event event) {
        qrRefreshJoinUi = null;
        qrPopupIsJoinedActive = false;
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
        // Tag order: private → age group → categories
        if (event.isPrivate()) {
            layoutCategoryTags.addView(createPrivateChip());
            hasTags = true;
        }
        String ageGroup = event.getAgeGroup();
        if (ageGroup != null && !ageGroup.trim().isEmpty()) {
            layoutCategoryTags.addView(createAgeGroupTag(ageGroup.trim()));
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
        tvWaitlist.setVisibility(View.VISIBLE);
        tvWaitlist.setText(""); // filled after waitlist count loads (same as event list)
        tvChevron.setVisibility(View.GONE);
        layoutExpanded.setVisibility(View.VISIBLE);
        layoutGeo.setVisibility(View.GONE);
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
        String location = event.getLocation() != null ? event.getLocation() : "TBD";
        tvDetailLocation.setText(location);
        tvDetailLocation.setOnClickListener(v -> {
            if (event.getLocation() != null && !event.getLocation().isEmpty()) {
                android.content.Intent intent = new android.content.Intent(
                        v.getContext(), MapPreviewActivity.class);
                intent.putExtra(MapPreviewActivity.EXTRA_LOCATION, event.getLocation());
                intent.putExtra(MapPreviewActivity.EXTRA_EVENT_NAME, event.getName());
                v.getContext().startActivity(intent);
            }
        });
        tvPrice.setText(formatPrice(event.getPrice()));
        tvCapacity.setText(event.getWaitingListCapacity() > 0 ? event.getWaitingListCapacity() + " spots" : "Unlimited");
        if (event.getRegistrationClose() != null) {
            tvRegClose.setText(DATE_FORMAT.format(event.getRegistrationClose().toDate())
                    + " · "
                    + TIME_FORMAT.format(event.getRegistrationClose().toDate()));
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

        loadQrPopupJoinButtonState(event, btnJoin, dialog, content, canvasSpinner, tvWaitlist);
        qrRefreshJoinUi = () -> loadQrPopupJoinButtonState(event, btnJoin, dialog, content, canvasSpinner, tvWaitlist);
        qrPopupContent = content;
        qrPopupBtnJoin = btnJoin;
        qrActiveDialog = dialog;
        qrPopupCanvasSpinner = canvasSpinner;
        qrPopupTvWaitlist = tvWaitlist;

        wireQrPopupComments(content, event);
    }

    private void wireQrPopupComments(View content, Event event) {
        String commentName = currentEntrant != null && currentEntrant.getName() != null
                && !currentEntrant.getName().trim().isEmpty()
                ? currentEntrant.getName().trim() : "You";
        final Runnable[] rebindComments = new Runnable[1];
        Runnable onCommentsEventGone = () -> runOnUiThread(() -> {
            if (qrActiveDialog != null && qrActiveDialog.isShowing()) {
                qrActiveDialog.dismiss();
            }
        });
        java.util.function.Predicate<Event> privateOk = freshEv ->
                !freshEv.isPrivate()
                        || qrPopupIsJoinedActive
                        || (deviceId != null && freshEv.isDeviceAnOrganizer(deviceId));
        java.util.function.Consumer<Event> onPrivateDenied = freshEv -> runOnUiThread(() -> {
            applyQrPopupEventFields(content, freshEv);
            View popupContent = qrPopupContent != null ? qrPopupContent : content;
            if (qrPopupBtnJoin != null && qrActiveDialog != null) {
                loadQrPopupJoinButtonState(freshEv, qrPopupBtnJoin, qrActiveDialog, popupContent,
                        qrPopupCanvasSpinner, qrPopupTvWaitlist);
            }
        });
        rebindComments[0] = () -> EventCommentsUiBinder.bind(content, event, deviceId, commentName,
                rebindComments[0], onCommentsEventGone, privateOk, onPrivateDenied);
        EventCommentsUiBinder.bind(content, event, deviceId, commentName, rebindComments[0],
                onCommentsEventGone, privateOk, onPrivateDenied);
    }

    /**
     * Re-loads registration, notification merge, and active count from the server, then binds the join button
     * (e.g. after "event at full capacity" so the UI shows WAITLIST FULL).
     */
    private void loadQrPopupJoinButtonState(Event event, TextView btnJoin,
                                            androidx.appcompat.app.AlertDialog dialog,
                                            View content, View canvasSpinner, TextView tvWaitlist) {
        if (event.getEventId() != null && deviceId != null) {
            waitingListDB.getRegistrationForEventAnyStatus(event.getEventId(), deviceId,
                    reg -> {
                        String baseStatus = reg != null ? reg.getStatus() : null;
                        notificationDB.getNotificationsForRecipient(deviceId,
                                allNotifications -> {
                                    String effectiveOverride = WaitlistStatusUi.firstEffectiveOverrideForEvent(
                                            allNotifications, event.getEventId());
                                    String anyStatus = WaitlistStatusUi.mergeDbStatusWithEffectiveOverride(
                                            baseStatus, effectiveOverride);
                                    boolean isJoinedActive = isActiveStatus(anyStatus);
                                    waitingListDB.getActiveCountForEvent(event.getEventId(),
                                            count -> bindJoinButton(btnJoin, event, anyStatus, isJoinedActive, count, dialog, content, canvasSpinner, tvWaitlist),
                                            e2 -> bindJoinButton(btnJoin, event, anyStatus, isJoinedActive, null, dialog, content, canvasSpinner, tvWaitlist));
                                },
                                e3 -> {
                                    String anyStatus = WaitlistStatusUi.mergeDbStatusWithEffectiveOverride(baseStatus, null);
                                    boolean isJoinedActive = isActiveStatus(anyStatus);
                                    waitingListDB.getActiveCountForEvent(event.getEventId(),
                                            count -> bindJoinButton(btnJoin, event, anyStatus, isJoinedActive, count, dialog, content, canvasSpinner, tvWaitlist),
                                            e2 -> bindJoinButton(btnJoin, event, anyStatus, isJoinedActive, null, dialog, content, canvasSpinner, tvWaitlist));
                                });
                    },
                    e -> {
                        waitingListDB.getActiveCountForEvent(event.getEventId(),
                                count -> bindJoinButton(btnJoin, event, null, false, count, dialog, content, canvasSpinner, tvWaitlist),
                                e2 -> bindJoinButton(btnJoin, event, null, false, null, dialog, content, canvasSpinner, tvWaitlist));
                    });
        } else {
            bindJoinButton(btnJoin, event, null, false, null, dialog, content, canvasSpinner, tvWaitlist);
        }
    }

    private static final java.text.SimpleDateFormat DATE_FORMAT =
            new java.text.SimpleDateFormat("MMM d, yyyy", java.util.Locale.getDefault());
    private static final java.text.SimpleDateFormat TIME_FORMAT =
            new java.text.SimpleDateFormat("h:mm a", java.util.Locale.getDefault());

    /** Updates the QR dialog card from a server-fresh event (geo lock, address, capacity, registration close, …). */
    private void applyQrPopupEventFields(View content, Event event) {
        if (content == null || event == null) {
            return;
        }
        TextView tvName = content.findViewById(R.id.tv_event_name);
        ImageView ivEventImage = content.findViewById(R.id.iv_event_image);
        LinearLayout layoutCategoryTags = content.findViewById(R.id.layout_category_tags);
        TextView tvDescription = content.findViewById(R.id.tv_description);
        TextView tvDetailDate = content.findViewById(R.id.tv_detail_date);
        TextView tvDetailTime = content.findViewById(R.id.tv_detail_time);
        TextView tvDetailLocation = content.findViewById(R.id.tv_detail_location);
        TextView tvPrice = content.findViewById(R.id.tv_price);
        TextView tvCapacity = content.findViewById(R.id.tv_capacity);
        TextView tvRegClose = content.findViewById(R.id.tv_reg_close);
        TextView tvCriteria = content.findViewById(R.id.tv_criteria_description);

        if (tvName != null) {
            tvName.setText(event.getName() != null ? event.getName() : "Event");
        }
        if (ivEventImage != null) {
            if (event.getPosterImageUrl() != null && !event.getPosterImageUrl().trim().isEmpty()) {
                Glide.with(this).load(event.getPosterImageUrl()).centerCrop()
                        .placeholder(android.R.drawable.ic_menu_gallery).into(ivEventImage);
            } else {
                ivEventImage.setImageResource(android.R.drawable.ic_menu_gallery);
            }
        }
        if (layoutCategoryTags != null) {
            layoutCategoryTags.removeAllViews();
            boolean hasTags = false;
            // Tag order: private → age group → categories
            if (event.isPrivate()) {
                layoutCategoryTags.addView(createPrivateChip());
                hasTags = true;
            }
            String ageGroup = event.getAgeGroup();
            if (ageGroup != null && !ageGroup.trim().isEmpty()) {
                layoutCategoryTags.addView(createAgeGroupTag(ageGroup.trim()));
                hasTags = true;
            }
            List<String> categories = event.getCategory();
            if (categories != null && !categories.isEmpty()) {
                for (String category : categories) {
                    if (category == null || category.trim().isEmpty()) {
                        continue;
                    }
                    layoutCategoryTags.addView(createCategoryChip(category.trim()));
                    hasTags = true;
                }
            }
            layoutCategoryTags.setVisibility(hasTags ? View.VISIBLE : View.GONE);
        }
        if (tvDescription != null) {
            tvDescription.setText(
                    event.getDescription() != null && !event.getDescription().isEmpty()
                            ? event.getDescription() : "No description available.");
        }
        if (tvDetailDate != null && tvDetailTime != null) {
            if (event.getEventDate() != null) {
                tvDetailDate.setText(DATE_FORMAT.format(event.getEventDate().toDate()));
                tvDetailTime.setText(TIME_FORMAT.format(event.getEventDate().toDate()));
            } else {
                tvDetailDate.setText("TBD");
                tvDetailTime.setText("TBD");
            }
        }
        if (tvDetailLocation != null) {
            String location = event.getLocation() != null ? event.getLocation() : "TBD";
            tvDetailLocation.setText(location);
            tvDetailLocation.setOnClickListener(v -> {
                if (event.getLocation() != null && !event.getLocation().isEmpty()) {
                    android.content.Intent intent = new android.content.Intent(
                            v.getContext(), MapPreviewActivity.class);
                    intent.putExtra(MapPreviewActivity.EXTRA_LOCATION, event.getLocation());
                    intent.putExtra(MapPreviewActivity.EXTRA_EVENT_NAME, event.getName());
                    v.getContext().startActivity(intent);
                }
            });
        }
        if (tvPrice != null) {
            tvPrice.setText(formatPrice(event.getPrice()));
        }
        if (tvCapacity != null) {
            tvCapacity.setText(event.getWaitingListCapacity() > 0 ? event.getWaitingListCapacity() + " spots" : "Unlimited");
        }
        if (tvRegClose != null) {
            if (event.getRegistrationClose() != null) {
                tvRegClose.setText(DATE_FORMAT.format(event.getRegistrationClose().toDate())
                        + " · "
                        + TIME_FORMAT.format(event.getRegistrationClose().toDate()));
            } else {
                tvRegClose.setText("TBD");
            }
        }
        if (tvCriteria != null) {
            String criteriaText = (event.getCriteria() != null && !event.getCriteria().isEmpty())
                    ? event.getCriteria()
                    : "No special criteria.";
            tvCriteria.setText(criteriaText);
        }
        wireQrPopupComments(content, event);
    }

    private static String formatPrice(String raw) {
        if (raw == null) return "TBD";
        String p = raw.trim();
        if (p.isEmpty()) return "TBD";
        if (p.startsWith("$")) return p;
        if (p.matches("^\\d+(?:\\.\\d{1,2})?$")) return "$" + p;
        return p;
    }

    private TextView createAgeGroupTag(String label) {
        TextView chip = new TextView(this);
        chip.setText(label);
        chip.setTextSize(12f);
        chip.setTypeface(chip.getTypeface(), android.graphics.Typeface.BOLD);
        chip.setTextColor(ContextCompat.getColor(this, R.color.age_group_tag_text));
        chip.setBackgroundResource(R.drawable.bg_age_group_tag);
        int hPad = dpToPx(10);
        int vPad = dpToPx(4);
        chip.setPadding(hPad, vPad, hPad, vPad);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        lp.setMarginEnd(dpToPx(6));
        chip.setLayoutParams(lp);
        return chip;
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

        if (WaitingList.STATUS_ENROLLED.equals(status)) {
            btn.setText("ENROLLED");
            btn.setBackgroundResource(R.drawable.bg_button_join_solid);
            btn.setAlpha(0.45f);
            btn.setEnabled(false);
        } else if (WaitingList.STATUS_DECLINED.equals(status)
                || WaitingList.STATUS_DECLINED_FOUND_REPLACEMENT.equals(status)) {
            btn.setText("DECLINED");
            btn.setBackgroundResource(R.drawable.bg_button_join_solid);
            btn.setAlpha(0.45f);
            btn.setEnabled(false);
        } else if (registrationClosed) {
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
        } else if (event != null && event.isPrivate() && !isJoined) {
            // Declined must always win over "PRIVATE EVENT" styling.
            btn.setText("PRIVATE EVENT");
            btn.setBackgroundResource(R.drawable.bg_button_join_solid);
            btn.setAlpha(0.45f);
            btn.setEnabled(false);
        } else if (event != null && deviceId != null && event.isDeviceAnOrganizer(deviceId) && !isJoined) {
            btn.setText(getString(R.string.join_waitlist_your_event));
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
                                View content, View canvasSpinner, TextView tvWaitlist) {
        qrPopupIsJoinedActive = isJoinedActive;
        if (canvasSpinner != null) canvasSpinner.setVisibility(View.GONE);
        content.setVisibility(View.VISIBLE);
        if (tvWaitlist != null) {
            if (waitlistCount != null) {
                tvWaitlist.setVisibility(View.VISIBLE);
                tvWaitlist.setText(WaitlistCountDisplayUi.formatLine(
                        this, waitlistCount, event.getWaitingListCapacity()));
            } else {
                tvWaitlist.setText("");
            }
        }
        applyJoinButtonState(btn, event, status, isJoinedActive, waitlistCount);
        btn.setOnClickListener(v -> {
            if (!btn.isEnabled()) return;
            if (isJoinedActive) {
                leaveWaitlist(event, btn);
                dialog.dismiss();
            } else {
                joinWaitlist(event, btn, dialog, content, canvasSpinner, tvWaitlist);
            }
        });
    }

    private boolean isActiveStatus(String status) {
        return WaitingList.STATUS_PENDING.equals(status)
                || WaitingList.STATUS_SELECTED.equals(status)
                || WaitingList.STATUS_NOT_SELECTED.equals(status)
                || WaitingList.STATUS_ENROLLED.equals(status);
    }

    /**
     * If the event is private and the user is not already on the waitlist, blocks joining and refreshes the dialog.
     * Otherwise runs {@code onEligibleToJoin} (geo path or direct waitlist add).
     */
    private void qrPrivateJoinGateIfNeeded(Event fresh, TextView btn, androidx.appcompat.app.AlertDialog dialog,
                                           View content, View canvasSpinner, TextView tvWaitlist,
                                           Runnable onEligibleToJoin) {
        if (!fresh.isPrivate()) {
            onEligibleToJoin.run();
            return;
        }
        if (fresh.getEventId() == null || deviceId == null) {
            android.widget.Toast.makeText(this, R.string.event_switched_to_private, android.widget.Toast.LENGTH_LONG).show();
            applyQrPopupEventFields(content, fresh);
            loadQrPopupJoinButtonState(fresh, btn, dialog, content, canvasSpinner, tvWaitlist);
            return;
        }
        waitingListDB.getRegistrationForEventAnyStatus(fresh.getEventId(), deviceId,
                reg -> notificationDB.getNotificationsForRecipient(deviceId,
                        allNotifications -> runOnUiThread(() -> {
                            String baseStatus = reg != null ? reg.getStatus() : null;
                            String effectiveOverride = WaitlistStatusUi.firstEffectiveOverrideForEvent(
                                    allNotifications, fresh.getEventId());
                            String anyStatus = WaitlistStatusUi.mergeDbStatusWithEffectiveOverride(
                                    baseStatus, effectiveOverride);
                            boolean isJoinedActive = isActiveStatus(anyStatus);
                            if (!isJoinedActive) {
                                android.widget.Toast.makeText(this, R.string.event_switched_to_private,
                                        android.widget.Toast.LENGTH_LONG).show();
                                applyQrPopupEventFields(content, fresh);
                                loadQrPopupJoinButtonState(fresh, btn, dialog, content, canvasSpinner, tvWaitlist);
                            } else {
                                // Already on waitlist; refresh UI only (do not join again).
                                applyQrPopupEventFields(content, fresh);
                                loadQrPopupJoinButtonState(fresh, btn, dialog, content, canvasSpinner, tvWaitlist);
                            }
                        }),
                        e -> runOnUiThread(() -> {
                            android.widget.Toast.makeText(this, R.string.event_switched_to_private,
                                    android.widget.Toast.LENGTH_LONG).show();
                            applyQrPopupEventFields(content, fresh);
                            loadQrPopupJoinButtonState(fresh, btn, dialog, content, canvasSpinner, tvWaitlist);
                        })),
                e -> runOnUiThread(() -> {
                    android.widget.Toast.makeText(this, R.string.event_switched_to_private,
                            android.widget.Toast.LENGTH_LONG).show();
                    applyQrPopupEventFields(content, fresh);
                    loadQrPopupJoinButtonState(fresh, btn, dialog, content, canvasSpinner, tvWaitlist);
                }));
    }

    private void joinWaitlist(Event event, TextView btn, androidx.appcompat.app.AlertDialog dialog,
                              View content, View canvasSpinner, TextView tvWaitlist) {
        if (event == null || event.getEventId() == null) return;
        if (!NetworkConnectivity.hasValidatedInternet(this)) {
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
        eventDB.getEventFromServer(event.getEventId(), fresh -> runOnUiThread(() -> {
            if (fresh == null) {
                EventGoneUi.toast(this);
                if (qrActiveDialog != null && qrActiveDialog.isShowing()) {
                    qrActiveDialog.dismiss();
                }
                return;
            }
            applyQrPopupEventFields(content, fresh);
            loadQrPopupJoinButtonState(fresh, btn, dialog, content, canvasSpinner, tvWaitlist);
            qrRefreshJoinUi = () -> loadQrPopupJoinButtonState(fresh, btn, dialog, content, canvasSpinner, tvWaitlist);
            Runnable dismiss = dialog::dismiss;
            qrPrivateJoinGateIfNeeded(fresh, btn, dialog, content, canvasSpinner, tvWaitlist, () -> {
                if (deviceId != null && fresh.isDeviceAnOrganizer(deviceId)) {
                    android.widget.Toast.makeText(this, R.string.waitlist_organizer_cannot_join,
                            android.widget.Toast.LENGTH_LONG).show();
                    applyQrPopupEventFields(content, fresh);
                    loadQrPopupJoinButtonState(fresh, btn, dialog, content, canvasSpinner, tvWaitlist);
                    return;
                }
                if (!RegistrationPeriodUi.isNowWithinRegistrationWindow(fresh)) {
                    android.widget.Toast.makeText(this, R.string.waitlist_registration_period_altered,
                            android.widget.Toast.LENGTH_LONG).show();
                    applyQrPopupEventFields(content, fresh);
                    loadQrPopupJoinButtonState(fresh, btn, dialog, content, canvasSpinner, tvWaitlist);
                    return;
                }
                checkCapacityThenProceedJoinQr(fresh, btn, dialog, content, canvasSpinner, tvWaitlist, () -> {
                    if (fresh.isGeolocationRequired()) {
                        if (geolocationController.hasLocationPermission(this)) {
                            joinAndRecordLocation(fresh, btn, dismiss);
                        } else {
                            pendingGeoJoinEvent = fresh;
                            pendingGeoJoinBtn = btn;
                            new androidx.appcompat.app.AlertDialog.Builder(this)
                                    .setTitle("Location Required")
                                    .setMessage("This event requires your location to be recorded when joining the waitlist.")
                                    .setPositiveButton("Allow", (d, w) ->
                                            locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION))
                                    .setNegativeButton("Cancel", null)
                                    .show();
                        }
                    } else {
                        performJoinWaitlist(fresh, btn, dismiss);
                    }
                });
            });
        }), e -> runOnUiThread(() ->
                android.widget.Toast.makeText(this, "Could not load event.", android.widget.Toast.LENGTH_SHORT).show()));
    }

    private void joinAndRecordLocation(Event event, TextView btn, Runnable dismissDialog) {
        showGeoJoinLoadingDialog();
        geolocationController.checkDistanceForEvent(this, event,
                new GeolocationController.GeoJoinCallback() {
                    @Override
                    public void onAllowed(android.location.Location userLocation) {
                        dismissGeoJoinLoadingDialog();
                        // Capacity was checked before geo; server enforces again in addRegistrationWithJoinChecks.
                        performJoinWaitlist(event, btn, dismissDialog);
                        geolocationController.recordLocationForEvent(
                                QRScanActivity.this, deviceId, event.getEventId(),
                                userLocation, unused -> {}, err -> {});
                    }
                    @Override
                    public void onBlocked(float distanceMeters) {
                        dismissGeoJoinLoadingDialog();
                        int km = Math.round(distanceMeters / 1000f);
                        android.widget.Toast.makeText(QRScanActivity.this,
                                "You are " + km + "km away. Must be within 30km to join.",
                                android.widget.Toast.LENGTH_LONG).show();
                        refreshQrPopupAfterGeoOrJoinFailure(event);
                        if (dismissDialog != null) {
                            dismissDialog.run();
                        }
                    }
                    @Override
                    public void onError(String message) {
                        dismissGeoJoinLoadingDialog();
                        android.widget.Toast.makeText(QRScanActivity.this, message,
                                android.widget.Toast.LENGTH_LONG).show();
                        refreshQrPopupAfterGeoOrJoinFailure(event);
                        if (dismissDialog != null) {
                            dismissDialog.run();
                        }
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

    private void refreshQrPopupAfterGeoOrJoinFailure(Event event) {
        if (qrPopupBtnJoin != null && qrActiveDialog != null && qrActiveDialog.isShowing()
                && qrPopupContent != null && event != null) {
            applyQrPopupEventFields(qrPopupContent, event);
            loadQrPopupJoinButtonState(event, qrPopupBtnJoin, qrActiveDialog, qrPopupContent,
                    qrPopupCanvasSpinner, qrPopupTvWaitlist);
        }
    }

    /**
     * Server-side active count vs capacity (organizer may have lowered capacity after the list was shown).
     */
    private void checkCapacityThenProceedJoinQr(Event event, TextView btn,
                                                  androidx.appcompat.app.AlertDialog dialog, View content,
                                                  View canvasSpinner, TextView tvWaitlist,
                                                  Runnable proceed) {
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
                        android.widget.Toast.makeText(this, R.string.waitlist_capacity_altered,
                                android.widget.Toast.LENGTH_LONG).show();
                        refreshQrPopupAfterWaitlistMutation(event.getEventId(), null);
                    } else {
                        proceed.run();
                    }
                }),
                e -> runOnUiThread(() ->
                        android.widget.Toast.makeText(this, "Could not verify waitlist capacity.",
                                android.widget.Toast.LENGTH_SHORT).show()));
    }

    /**
     * Re-fetch event from server after join/leave so poster, title, categories, description, criteria, etc. match Firestore.
     * {@code afterUi} runs on the UI thread after apply (e.g. dismiss dialog); may be null.
     */
    private void refreshQrPopupAfterWaitlistMutation(String eventId, Runnable afterUi) {
        if (eventId == null || eventId.isEmpty()) {
            if (afterUi != null) {
                afterUi.run();
            }
            return;
        }
        eventDB.getEventFromServer(eventId, fresh -> runOnUiThread(() -> {
            if (fresh != null && qrPopupContent != null && qrActiveDialog != null && qrActiveDialog.isShowing()) {
                applyQrPopupEventFields(qrPopupContent, fresh);
                if (qrPopupBtnJoin != null) {
                    loadQrPopupJoinButtonState(fresh, qrPopupBtnJoin, qrActiveDialog, qrPopupContent,
                            qrPopupCanvasSpinner, qrPopupTvWaitlist);
                }
                qrRefreshJoinUi = () -> loadQrPopupJoinButtonState(fresh, qrPopupBtnJoin, qrActiveDialog, qrPopupContent,
                        qrPopupCanvasSpinner, qrPopupTvWaitlist);
            }
            if (afterUi != null) {
                afterUi.run();
            }
        }), e -> runOnUiThread(() -> {
            if (qrRefreshJoinUi != null) {
                qrRefreshJoinUi.run();
            }
            if (afterUi != null) {
                afterUi.run();
            }
        }));
    }

    private void performJoinWaitlist(Event event, TextView btn, Runnable dismissDialog) {
        WaitingList registration = new WaitingList(
                event.getEventId(),
                deviceId,
                1,
                currentEntrant.getName(),
                currentEntrant.getEmail(),
                currentEntrant.getPhone(),
                WaitingList.NOTIFY_EMAIL
        );
        waitingListDB.addRegistrationWithJoinChecks(registration, event.getWaitingListCapacity(),
                event.getRegistrationClose(),
                id -> {
                    android.widget.Toast.makeText(this, R.string.waitlist_success, android.widget.Toast.LENGTH_SHORT).show();
                    recordLocationIfPermitted(event.getEventId());
                    refreshQrPopupAfterWaitlistMutation(event.getEventId(), dismissDialog);
                },
                e -> {
                    if (WaitingListDB.REASON_EVENT_DELETED.equals(e.getMessage())) {
                        EventGoneUi.toast(this);
                        if (dismissDialog != null) {
                            dismissDialog.run();
                        }
                    } else if (WaitingListDB.REASON_WAITLIST_FULL.equals(e.getMessage())) {
                        android.widget.Toast.makeText(this, R.string.waitlist_capacity_altered, android.widget.Toast.LENGTH_LONG).show();
                    } else if (WaitingListDB.REASON_REGISTRATION_CLOSED.equals(e.getMessage())) {
                        android.widget.Toast.makeText(this, R.string.waitlist_registration_closed, android.widget.Toast.LENGTH_LONG).show();
                    } else if (WaitingListDB.REASON_ORGANIZER_CANNOT_JOIN.equals(e.getMessage())) {
                        android.widget.Toast.makeText(this, R.string.waitlist_organizer_cannot_join, android.widget.Toast.LENGTH_LONG).show();
                    } else {
                        android.widget.Toast.makeText(this, getString(R.string.waitlist_fail) + " " + e.getMessage(), android.widget.Toast.LENGTH_SHORT).show();
                    }
                    refreshQrPopupAfterWaitlistMutation(event.getEventId(), dismissDialog);
                });
    }

    private void recordLocationIfPermitted(String eventId) {
        if (eventId == null || eventId.isEmpty()) return;
        if (!geolocationController.hasLocationPermission(this)) return;
        geolocationController.getCurrentDeviceLocation(this, loc -> {
            if (loc == null) return;
            geolocationController.recordLocationForEvent(
                    QRScanActivity.this,
                    deviceId,
                    eventId,
                    loc,
                    unused -> {},
                    err -> {});
        });
    }

    private void leaveWaitlist(Event event, TextView btn) {
        if (event == null || event.getEventId() == null || deviceId == null) return;
        if (!NetworkConnectivity.hasValidatedInternet(this)) {
            android.widget.Toast.makeText(this,
                    "Failed to leave waitlist: No internet connection.",
                    android.widget.Toast.LENGTH_SHORT).show();
            return;
        }
        eventDB.getEventFromServer(event.getEventId(), fresh -> runOnUiThread(() -> {
            if (fresh == null) {
                EventGoneUi.toast(this);
                if (qrActiveDialog != null && qrActiveDialog.isShowing()) {
                    qrActiveDialog.dismiss();
                }
                return;
            }
            continueQrLeaveWaitlist(event);
        }), e -> runOnUiThread(() ->
                android.widget.Toast.makeText(this, "Could not verify event.",
                        android.widget.Toast.LENGTH_SHORT).show()));
    }

    private void continueQrLeaveWaitlist(Event event) {
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
                            unused -> {
                                android.widget.Toast.makeText(this, "Left waitlist for " + (event.getName() != null ? event.getName() : "event"), android.widget.Toast.LENGTH_SHORT).show();
                                refreshQrPopupAfterWaitlistMutation(event.getEventId(), null);
                            },
                            e -> {
                                if (EventGoneUi.isFirestoreNotFound(e)) {
                                    EventGoneUi.toast(this);
                                    if (qrActiveDialog != null && qrActiveDialog.isShowing()) {
                                        qrActiveDialog.dismiss();
                                    }
                                } else {
                                    android.widget.Toast.makeText(this, "Failed to leave: " + e.getMessage(),
                                            android.widget.Toast.LENGTH_SHORT).show();
                                }
                            });
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

}
