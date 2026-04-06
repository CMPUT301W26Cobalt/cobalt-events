package com.example.cobaltevents.ui;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.core.content.ContextCompat;

import com.bumptech.glide.Glide;
import com.example.cobaltevents.R;
import com.example.cobaltevents.db.EventDB;
import com.example.cobaltevents.db.WaitingListDB;
import com.example.cobaltevents.model.Event;
import com.example.cobaltevents.ui.waitlist.WaitlistCountDisplayUi;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Read-only event card in the same shell as the QR code popup ({@link R.layout#dialog_event_card}),
 * without join/leave, without comments — only details + close.
 * <p>
 * Matches {@link QRScanActivity}: content stays {@link View#INVISIBLE} with a centered progress
 * indicator until the server event document and waitlist count have loaded, then reveals the card.
 */
public final class EventReadOnlyCardPopup {

    private static final SimpleDateFormat DATE_FORMAT =
            new SimpleDateFormat("MMM d, yyyy", Locale.getDefault());
    private static final SimpleDateFormat TIME_FORMAT =
            new SimpleDateFormat("h:mm a", Locale.getDefault());

    private EventReadOnlyCardPopup() {}

    public static void show(AppCompatActivity activity, Event event) {
        if (activity == null || activity.isFinishing() || event == null) return;

        View content = LayoutInflater.from(activity).inflate(R.layout.dialog_event_card, null, false);
        content.setVisibility(View.INVISIBLE);

        TextView tvWaitlist = content.findViewById(R.id.tv_waitlist_count);
        TextView tvChevron = content.findViewById(R.id.tv_chevron);
        View layoutExpanded = content.findViewById(R.id.layout_expanded_details);
        TextView btnJoin = content.findViewById(R.id.btn_join);
        View closeInlineLayout = content.findViewById(R.id.layout_close_inline);
        TextView btnCloseInline = content.findViewById(R.id.btn_close_inline);
        View commentsSection = content.findViewById(R.id.layout_event_comments_section);

        tvChevron.setVisibility(View.GONE);
        layoutExpanded.setVisibility(View.VISIBLE);
        btnJoin.setVisibility(View.GONE);
        closeInlineLayout.setVisibility(View.VISIBLE);
        if (commentsSection != null) {
            commentsSection.setVisibility(View.GONE);
        }
        tvWaitlist.setVisibility(View.VISIBLE);
        tvWaitlist.setText("");

        FrameLayout popupCanvas = new FrameLayout(activity);
        FrameLayout.LayoutParams contentLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        popupCanvas.addView(content, contentLp);

        ProgressBar canvasSpinner = new ProgressBar(activity);
        FrameLayout.LayoutParams spinnerLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        spinnerLp.gravity = Gravity.CENTER;
        popupCanvas.addView(canvasSpinner, spinnerLp);

        AlertDialog dialog = new MaterialAlertDialogBuilder(activity)
                .setView(popupCanvas)
                .setCancelable(true)
                .create();
        btnCloseInline.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        final Event[] resolvedEvent = new Event[] { event };
        final Integer[] resolvedCount = new Integer[] { null };
        String eid = event.getEventId();

        Runnable revealIfReady = () -> activity.runOnUiThread(() -> {
            if (activity.isFinishing() || !dialog.isShowing()) return;
            bindEventFields(activity, content, resolvedEvent[0]);
            tvWaitlist.setText(WaitlistCountDisplayUi.formatLine(
                    activity, resolvedCount[0],
                    resolvedEvent[0] != null ? resolvedEvent[0].getWaitingListCapacity() : 0));
            applyInnerCardStyle(content);
            canvasSpinner.setVisibility(View.GONE);
            content.setVisibility(View.VISIBLE);
            applyScrollMaxHeight(activity, content);
        });

        if (eid != null && !eid.isEmpty()) {
            AtomicInteger remaining = new AtomicInteger(2);
            Runnable partDone = () -> {
                if (remaining.decrementAndGet() != 0) return;
                revealIfReady.run();
            };

            EventDB eventDB = new EventDB();
            eventDB.getEventFromServer(eid, ev -> {
                if (ev != null) {
                    resolvedEvent[0] = ev;
                }
                partDone.run();
            }, e -> partDone.run());

            WaitingListDB waitingListDB = new WaitingListDB();
            waitingListDB.getActiveCountForEvent(eid,
                    count -> {
                        resolvedCount[0] = count;
                        partDone.run();
                    },
                    e -> partDone.run());
        } else {
            activity.runOnUiThread(() -> {
                if (activity.isFinishing() || !dialog.isShowing()) return;
                bindEventFields(activity, content, resolvedEvent[0]);
                tvWaitlist.setText("");
                applyInnerCardStyle(content);
                canvasSpinner.setVisibility(View.GONE);
                content.setVisibility(View.VISIBLE);
                applyScrollMaxHeight(activity, content);
            });
        }
    }

    private static void applyInnerCardStyle(View content) {
        View innerCardMaybe = content.findViewById(R.id.include_event_card);
        if (innerCardMaybe instanceof CardView) {
            CardView innerCard = (CardView) innerCardMaybe;
            innerCard.setCardElevation(0f);
            innerCard.setUseCompatPadding(false);
            innerCard.setPreventCornerOverlap(false);
            innerCard.setCardBackgroundColor(Color.TRANSPARENT);
            ViewGroup.LayoutParams params = innerCard.getLayoutParams();
            if (params instanceof ViewGroup.MarginLayoutParams) {
                ((ViewGroup.MarginLayoutParams) params).setMargins(0, 0, 0, 0);
                innerCard.setLayoutParams(params);
            }
        }
    }

    private static void applyScrollMaxHeight(AppCompatActivity activity, View content) {
        View scroll = content.findViewById(R.id.scroll_event_dialog);
        if (scroll == null) return;
        scroll.post(() -> {
            int screenH = activity.getResources().getDisplayMetrics().heightPixels;
            int maxH = (int) (screenH * 0.65f);
            if (scroll.getHeight() > maxH) {
                ViewGroup.LayoutParams lp = scroll.getLayoutParams();
                lp.height = maxH;
                scroll.setLayoutParams(lp);
            }
        });
    }

    private static void bindEventFields(Context ctx, View content, Event event) {
        if (event == null) return;

        TextView tvName = content.findViewById(R.id.tv_event_name);
        ImageView ivEventImage = content.findViewById(R.id.iv_event_image);
        LinearLayout layoutCategoryTags = content.findViewById(R.id.layout_category_tags);
        TextView tvDescription = content.findViewById(R.id.tv_description);
        TextView tvDetailDate = content.findViewById(R.id.tv_detail_date);
        TextView tvDetailTime = content.findViewById(R.id.tv_detail_time);
        TextView tvDetailLocation = content.findViewById(R.id.tv_detail_location);
        TextView tvPrice = content.findViewById(R.id.tv_price);
        TextView tvCapacity = content.findViewById(R.id.tv_capacity);
        TextView tvRegOpen = content.findViewById(R.id.tv_reg_open);
        TextView tvRegClose = content.findViewById(R.id.tv_reg_close);
        TextView tvCriteria = content.findViewById(R.id.tv_criteria_description);
        View layoutGeo = content.findViewById(R.id.layout_geo_note);

        if (tvName != null) {
            tvName.setText(event.getName() != null ? event.getName() : "Event");
        }
        if (ivEventImage != null) {
            if (event.getPosterImageUrl() != null && !event.getPosterImageUrl().trim().isEmpty()) {
                Glide.with(ctx)
                        .load(event.getPosterImageUrl())
                        .centerCrop()
                        .placeholder(android.R.drawable.ic_menu_gallery)
                        .into(ivEventImage);
            } else {
                ivEventImage.setImageResource(android.R.drawable.ic_menu_gallery);
            }
        }
        if (layoutCategoryTags != null) {
            layoutCategoryTags.removeAllViews();
            boolean hasTags = false;
            if (event.isPrivate()) {
                layoutCategoryTags.addView(createPrivateChip(ctx));
                hasTags = true;
            }
            String ageGroup = event.getAgeGroup();
            if (ageGroup != null && !ageGroup.trim().isEmpty()) {
                layoutCategoryTags.addView(createAgeGroupTag(ctx, ageGroup.trim()));
                hasTags = true;
            }
            List<String> categories = event.getCategory();
            if (categories != null && !categories.isEmpty()) {
                for (String category : categories) {
                    if (category == null || category.trim().isEmpty()) continue;
                    layoutCategoryTags.addView(createCategoryChip(ctx, category.trim()));
                    hasTags = true;
                }
            }
            layoutCategoryTags.setVisibility(hasTags ? View.VISIBLE : View.GONE);
        }
        if (tvDescription != null) {
            tvDescription.setText(event.getDescription() != null && !event.getDescription().isEmpty()
                    ? event.getDescription()
                    : "No description available.");
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
            tvCapacity.setText(event.getWaitingListCapacity() > 0
                    ? event.getWaitingListCapacity() + " spots"
                    : "Unlimited");
        }
        if (tvRegOpen != null) {
            if (event.getRegistrationOpen() != null) {
                tvRegOpen.setText(DATE_FORMAT.format(event.getRegistrationOpen().toDate())
                        + " · "
                        + TIME_FORMAT.format(event.getRegistrationOpen().toDate()));
            } else {
                tvRegOpen.setText("TBD");
            }
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
        if (layoutGeo != null) {
            layoutGeo.setVisibility(View.GONE);
        }
    }

    private static String formatPrice(String raw) {
        if (raw == null) return "TBD";
        String p = raw.trim();
        if (p.isEmpty()) return "TBD";
        if (p.startsWith("$")) return p;
        if (p.matches("^\\d+(?:\\.\\d{1,2})?$")) return "$" + p;
        return p;
    }

    private static int dpToPx(Context ctx, int dp) {
        float density = ctx.getResources().getDisplayMetrics().density;
        return Math.round(dp * density);
    }

    private static TextView createAgeGroupTag(Context ctx, String label) {
        TextView chip = new TextView(ctx);
        chip.setText(label);
        chip.setTextSize(12f);
        chip.setTypeface(chip.getTypeface(), android.graphics.Typeface.BOLD);
        chip.setTextColor(ContextCompat.getColor(ctx, R.color.age_group_tag_text));
        chip.setBackgroundResource(R.drawable.bg_age_group_tag);
        int hPad = dpToPx(ctx, 10);
        int vPad = dpToPx(ctx, 4);
        chip.setPadding(hPad, vPad, hPad, vPad);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMarginEnd(dpToPx(ctx, 6));
        chip.setLayoutParams(lp);
        return chip;
    }

    private static TextView createCategoryChip(Context ctx, String label) {
        TextView chip = new TextView(ctx);
        chip.setText(label);
        chip.setTextSize(11f);
        chip.setTextColor(ContextCompat.getColor(ctx, R.color.header_teal));
        chip.setBackgroundResource(R.drawable.bg_tag_teal);
        int hPad = dpToPx(ctx, 10);
        int vPad = dpToPx(ctx, 3);
        chip.setPadding(hPad, vPad, hPad, vPad);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMarginEnd(dpToPx(ctx, 6));
        chip.setLayoutParams(lp);
        return chip;
    }

    private static LinearLayout createPrivateChip(Context ctx) {
        LinearLayout chip = new LinearLayout(ctx);
        chip.setOrientation(LinearLayout.HORIZONTAL);
        chip.setGravity(android.view.Gravity.CENTER_VERTICAL);
        chip.setBackgroundResource(R.drawable.bg_private_tag);
        int hPad = dpToPx(ctx, 8);
        int vPad = dpToPx(ctx, 2);
        chip.setPadding(hPad, vPad, hPad, vPad);

        ImageView icon = new ImageView(ctx);
        icon.setImageResource(R.drawable.ic_lock_private);
        LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(dpToPx(ctx, 12), dpToPx(ctx, 12));
        icon.setLayoutParams(iconLp);

        TextView label = new TextView(ctx);
        label.setText(R.string.private_tag_label);
        label.setTextSize(12f);
        label.setTextColor(ContextCompat.getColor(ctx, R.color.private_tag_text));
        label.setTypeface(label.getTypeface(), android.graphics.Typeface.BOLD);
        LinearLayout.LayoutParams textLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        textLp.setMarginStart(dpToPx(ctx, 4));
        label.setLayoutParams(textLp);

        LinearLayout.LayoutParams chipLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        chipLp.setMarginEnd(dpToPx(ctx, 6));
        chip.setLayoutParams(chipLp);

        chip.addView(icon);
        chip.addView(label);
        return chip;
    }
}
