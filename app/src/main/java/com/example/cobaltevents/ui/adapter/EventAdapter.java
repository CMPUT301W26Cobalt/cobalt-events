package com.example.cobaltevents.ui.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.cobaltevents.R;
import com.example.cobaltevents.model.Event;
import com.example.cobaltevents.model.WaitingList;
import com.example.cobaltevents.ui.comments.EventCommentsUiBinder;
import com.example.cobaltevents.ui.waitlist.WaitlistCountDisplayUi;
import com.example.cobaltevents.ui.waitlist.WaitlistStatusUi;
import com.google.firebase.Timestamp;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

public class EventAdapter extends RecyclerView.Adapter<EventAdapter.EventViewHolder> {

    public interface OnEventClickListener {
        void onEventClick(Event event, boolean isJoined);
    }

    /** Fired when local comments/replies change so the row can refresh. */
    public interface OnEventCommentsChangedListener {
        void onEventCommentsChanged(String eventId);
    }

    /** Fired when the event was deleted on the server (stale card). */
    public interface OnEventDeletedListener {
        void onEventDeleted(String eventId);
    }

    private List<Event> events;
    private final OnEventClickListener listener;
    private final Set<String> expandedIds = new HashSet<>();
    private Map<String, WaitingList> activeRegistrationsByEventId;
    private Map<String, WaitingList> registrationsByEventId;
    private Map<String, Integer> waitlistCountByEventId;
    private Map<String, String> effectiveStatusByEventId;
    private String deviceId;
    private String commentAuthorName = "You";
    private OnEventCommentsChangedListener onEventCommentsChangedListener;
    private OnEventDeletedListener onEventDeletedListener;
    private Consumer<Event> onPrivateCommentDeniedRefresh;

    private static final SimpleDateFormat DATE_FORMAT =
            new SimpleDateFormat("MMM d, yyyy", Locale.getDefault());
    private static final SimpleDateFormat TIME_FORMAT =
            new SimpleDateFormat("h:mm a", Locale.getDefault());
    private static final java.util.regex.Pattern PRICE_NUMBER =
            java.util.regex.Pattern.compile("^\\d+(?:\\.\\d{1,2})?$");

    public EventAdapter(List<Event> events, OnEventClickListener listener) {
        this.events = events;
        this.listener = listener;
    }

    public void updateEvents(List<Event> newEvents) {
        this.events = newEvents;
        notifyDataSetChanged();
    }

    /** Sets map without triggering a redraw; call {@link #notifyDataSetChanged()} once when all maps are ready. */
    public void setActiveRegistrationsByEventId(Map<String, WaitingList> map) {
        this.activeRegistrationsByEventId = map;
    }

    /** Sets map without triggering a redraw; call {@link #notifyDataSetChanged()} once when all maps are ready. */
    public void setRegistrationsByEventId(Map<String, WaitingList> map) {
        this.registrationsByEventId = map;
    }

    /** Sets map without triggering a redraw; call {@link #notifyDataSetChanged()} once when all maps are ready. */
    public void setWaitlistCountByEventId(Map<String, Integer> map) {
        this.waitlistCountByEventId = map;
    }

    /** Sets map without triggering a redraw; call {@link #notifyDataSetChanged()} once when all maps are ready. */
    public void setEffectiveStatusByEventId(Map<String, String> map) {
        this.effectiveStatusByEventId = map;
    }

    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
    }

    public void setCommentAuthorName(String name) {
        this.commentAuthorName = (name != null && !name.trim().isEmpty()) ? name.trim() : "You";
    }

    public void setOnEventCommentsChangedListener(OnEventCommentsChangedListener listener) {
        this.onEventCommentsChangedListener = listener;
    }

    public void setOnEventDeletedListener(OnEventDeletedListener listener) {
        this.onEventDeletedListener = listener;
    }

    public void setOnPrivateCommentDeniedRefresh(Consumer<Event> listener) {
        this.onPrivateCommentDeniedRefresh = listener;
    }

    /** Aligned with join rules: public, on waitlist, or organizer may use comments on a private event. */
    public boolean serverEventAllowsCommentWrite(Event fresh) {
        if (fresh == null) {
            return false;
        }
        return !fresh.isPrivate()
                || isEffectivelyJoinedOnWaitlist(fresh)
                || (deviceId != null && fresh.isDeviceAnOrganizer(deviceId));
    }

    /** Drops per-event UI state when the event document no longer exists. */
    public void clearAuxiliaryStateForEvent(String eventId) {
        if (eventId == null) {
            return;
        }
        expandedIds.remove(eventId);
        if (waitlistCountByEventId != null) {
            waitlistCountByEventId.remove(eventId);
        }
        if (effectiveStatusByEventId != null) {
            effectiveStatusByEventId.remove(eventId);
        }
    }

    /** Position in the current (filtered) list, or -1. */
    public int findPositionByEventId(String eventId) {
        if (eventId == null || events == null) {
            return -1;
        }
        for (int i = 0; i < events.size(); i++) {
            Event e = events.get(i);
            if (e != null && eventId.equals(e.getEventId())) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Whether the user is treated as on the waitlist for join/leave UI (aligned with {@link #onBindViewHolder}).
     * Used when re-checking server state before joining (e.g. event switched to private).
     */
    public boolean isEffectivelyJoinedOnWaitlist(Event event) {
        if (event == null || event.getEventId() == null) {
            return false;
        }
        String eventId = event.getEventId();
        boolean isJoined = activeRegistrationsByEventId != null
                && activeRegistrationsByEventId.containsKey(eventId);
        WaitingList anyReg = (registrationsByEventId != null)
                ? registrationsByEventId.get(eventId)
                : null;
        String anyStatusDb = anyReg != null ? anyReg.getStatus() : null;
        String anyStatus = anyStatusDb;
        if (anyStatusDb != null && effectiveStatusByEventId != null && event.getEventId() != null) {
            String effective = effectiveStatusByEventId.get(eventId);
            anyStatus = WaitlistStatusUi.mergeDbStatusWithEffectiveOverride(anyStatusDb, effective);
        }
        boolean isJoinedEffective = isJoined;
        if (effectiveStatusByEventId != null && event.getEventId() != null) {
            if (anyStatus != null) {
                isJoinedEffective = isActiveStatusForUi(anyStatus);
            }
        }
        return isJoinedEffective;
    }

    @NonNull
    @Override
    public EventViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_event, parent, false);
        return new EventViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull EventViewHolder holder, int position) {
        Event event = events.get(position);
        String eventId = event.getEventId() != null ? event.getEventId() : String.valueOf(position);
        boolean isExpanded = expandedIds.contains(eventId);
        boolean isJoined = event.getEventId() != null
                && activeRegistrationsByEventId != null
                && activeRegistrationsByEventId.containsKey(event.getEventId());
        WaitingList anyReg = (event.getEventId() != null && registrationsByEventId != null)
                ? registrationsByEventId.get(event.getEventId())
                : null;
        String anyStatusDb = anyReg != null ? anyReg.getStatus() : null;
        String anyStatus = anyStatusDb;
        // Only apply notification-derived overrides when we actually have a waitlist entry.
        // This prevents "leave waitlist" UI appearing for events where the registration is missing.
        if (anyStatusDb != null && effectiveStatusByEventId != null && event.getEventId() != null) {
            String effective = effectiveStatusByEventId.get(event.getEventId());
            anyStatus = WaitlistStatusUi.mergeDbStatusWithEffectiveOverride(anyStatusDb, effective);
        }

        boolean isJoinedEffective = isJoined;
        if (effectiveStatusByEventId != null && event.getEventId() != null) {
            if (anyStatus != null) {
                isJoinedEffective = isActiveStatusForUi(anyStatus);
            }
        }
        Integer count = (event.getEventId() != null && waitlistCountByEventId != null)
                ? waitlistCountByEventId.get(event.getEventId())
                : null;

        if (event.getPosterImageUrl() != null && !event.getPosterImageUrl().trim().isEmpty()) {
            Glide.with(holder.itemView.getContext())
                    .load(event.getPosterImageUrl())
                    .centerCrop()
                    .placeholder(android.R.drawable.ic_menu_gallery)
                    .into(holder.ivEventImage);
        } else {
            holder.ivEventImage.setImageResource(android.R.drawable.ic_menu_gallery);
        }

        holder.tvName.setText(event.getName());

        holder.tvWaitlistCount.setText(WaitlistCountDisplayUi.formatLine(
                holder.itemView.getContext(), count, event.getWaitingListCapacity()));

        holder.layoutCategoryTags.removeAllViews();
        boolean hasTags = false;
        // Tag order (left→right): private → age group → categories
        if (event.isPrivate()) {
            holder.layoutCategoryTags.addView(createPrivateChip(holder));
            hasTags = true;
        }
        String ageGroup = event.getAgeGroup();
        if (ageGroup != null && !ageGroup.trim().isEmpty()) {
            holder.layoutCategoryTags.addView(createAgeGroupTag(holder, ageGroup.trim()));
            hasTags = true;
        }
        List<String> categories = event.getCategory();
        if (categories != null && !categories.isEmpty()) {
            for (String category : categories) {
                if (category == null || category.trim().isEmpty()) continue;
                holder.layoutCategoryTags.addView(createCategoryChip(holder, category.trim()));
                hasTags = true;
            }
        }
        holder.layoutCategoryTags.setVisibility(hasTags ? View.VISIBLE : View.GONE);

        Timestamp now = Timestamp.now();
        boolean registrationClosed = event.getRegistrationClose() != null
                && event.getRegistrationClose().compareTo(now) < 0;
        boolean upcoming = event.getRegistrationOpen() != null
                && event.getRegistrationOpen().compareTo(now) > 0
                && event.getEventDate() != null
                && event.getEventDate().compareTo(now) > 0;
        int capacity = event.getWaitingListCapacity();
        boolean full = !isJoinedEffective
                && capacity > 0
                && count != null
                && count >= capacity;
        boolean isOrganizer = deviceId != null && event.isDeviceAnOrganizer(deviceId);
        // Organizers may see private events in browse but cannot join; use "your event" row, not "PRIVATE EVENT".
        boolean privateBlocked = !isJoinedEffective && event.isPrivate() && !isOrganizer;
        boolean enrolled = WaitingList.STATUS_ENROLLED.equals(anyStatus);
        boolean declined = WaitingList.STATUS_DECLINED.equals(anyStatus)
                || WaitingList.STATUS_DECLINED_FOUND_REPLACEMENT.equals(anyStatus);

        if (registrationClosed) {
            holder.btnJoin.setText("REGISTRATION CLOSED");
            holder.btnJoin.setAlpha(0.45f);
            holder.btnJoin.setEnabled(false);
            holder.btnJoin.setBackgroundResource(R.drawable.bg_button_join_solid);
        } else if (upcoming) {
            holder.btnJoin.setText("UPCOMING");
            holder.btnJoin.setAlpha(0.45f);
            holder.btnJoin.setEnabled(false);
            holder.btnJoin.setBackgroundResource(R.drawable.bg_button_upcoming);
        } else if (full) {
            holder.btnJoin.setText("WAITLIST FULL");
            holder.btnJoin.setAlpha(0.45f);
            holder.btnJoin.setEnabled(false);
            holder.btnJoin.setBackgroundResource(R.drawable.bg_button_join_solid);
        } else if (enrolled) {
            holder.btnJoin.setText("ENROLLED");
            holder.btnJoin.setAlpha(0.45f);
            holder.btnJoin.setEnabled(false);
            holder.btnJoin.setBackgroundResource(R.drawable.bg_button_join_solid);
        } else if (declined) {
            holder.btnJoin.setText("DECLINED");
            holder.btnJoin.setAlpha(0.45f);
            holder.btnJoin.setEnabled(false);
            holder.btnJoin.setBackgroundResource(R.drawable.bg_button_join_solid);
        } else if (privateBlocked) {
            // Declined must always win over "PRIVATE EVENT" styling.
            holder.btnJoin.setText("PRIVATE EVENT");
            holder.btnJoin.setAlpha(0.45f);
            holder.btnJoin.setEnabled(false);
            holder.btnJoin.setBackgroundResource(R.drawable.bg_button_join_solid);
        } else if (isOrganizer && !isJoinedEffective) {
            holder.btnJoin.setText(holder.itemView.getContext().getString(R.string.join_waitlist_your_event));
            holder.btnJoin.setAlpha(0.45f);
            holder.btnJoin.setEnabled(false);
            holder.btnJoin.setBackgroundResource(R.drawable.bg_button_join_solid);
        } else {
            holder.btnJoin.setText(isJoinedEffective ? "LEAVE WAITLIST" : "JOIN WAITLIST");
            holder.btnJoin.setAlpha(1.0f);
            holder.btnJoin.setEnabled(true);
            holder.btnJoin.setBackgroundResource(isJoinedEffective ? R.drawable.bg_button_red_pill : R.drawable.bg_button_join_solid);
        }

        holder.layoutExpandedDetails.setVisibility(isExpanded ? View.VISIBLE : View.GONE);
        holder.tvChevron.setText(isExpanded ? "▲" : "▼");

        if (isExpanded) {
            holder.tvDescription.setText(
                    event.getDescription() != null && !event.getDescription().isEmpty()
                            ? event.getDescription() : "No description available.");

            if (event.getEventDate() != null) {
                holder.tvDetailDate.setText(DATE_FORMAT.format(event.getEventDate().toDate()));
                holder.tvDetailTime.setText(TIME_FORMAT.format(event.getEventDate().toDate()));
            } else {
                holder.tvDetailDate.setText("TBD");
                holder.tvDetailTime.setText("TBD");
            }

            String location = event.getLocation() != null ? event.getLocation() : "TBD";
            holder.tvDetailLocation.setText(location);
            holder.tvDetailLocation.setOnClickListener(v -> {
                if (event.getLocation() != null && !event.getLocation().isEmpty()) {
                    android.content.Intent intent = new android.content.Intent(
                            v.getContext(), com.example.cobaltevents.ui.MapPreviewActivity.class);
                    intent.putExtra(com.example.cobaltevents.ui.MapPreviewActivity.EXTRA_LOCATION,
                            event.getLocation());
                    intent.putExtra(com.example.cobaltevents.ui.MapPreviewActivity.EXTRA_EVENT_NAME,
                            event.getName());
                    v.getContext().startActivity(intent);
                }
            });

            holder.tvPrice.setText(formatPrice(event.getPrice()));

            if (event.getWaitingListCapacity() > 0) {
                holder.tvCapacity.setText(event.getWaitingListCapacity() + " spots");
            } else {
                holder.tvCapacity.setText("Unlimited");
            }

            if (event.getRegistrationOpen() != null) {
                holder.tvRegOpen.setText(DATE_FORMAT.format(event.getRegistrationOpen().toDate())
                        + " · "
                        + TIME_FORMAT.format(event.getRegistrationOpen().toDate()));
            } else {
                holder.tvRegOpen.setText("TBD");
            }

            if (event.getRegistrationClose() != null) {
                holder.tvRegClose.setText(DATE_FORMAT.format(event.getRegistrationClose().toDate())
                        + " · "
                        + TIME_FORMAT.format(event.getRegistrationClose().toDate()));
            } else {
                holder.tvRegClose.setText("TBD");
            }

            String criteriaText = (event.getCriteria() != null && !event.getCriteria().isEmpty())
                    ? event.getCriteria()
                    : "No special criteria.";
            holder.tvCriteriaDescription.setText(criteriaText);
            holder.layoutGeoNote.setVisibility(View.GONE);

            EventCommentsUiBinder.bind(holder.itemView, event, deviceId, commentAuthorName, () -> {
                if (onEventCommentsChangedListener != null && event.getEventId() != null) {
                    onEventCommentsChangedListener.onEventCommentsChanged(event.getEventId());
                }
            }, () -> {
                if (onEventDeletedListener != null && event.getEventId() != null) {
                    onEventDeletedListener.onEventDeleted(event.getEventId());
                }
            }, this::serverEventAllowsCommentWrite, fresh -> {
                if (onPrivateCommentDeniedRefresh != null) {
                    onPrivateCommentDeniedRefresh.accept(fresh);
                }
            });
        }

        View.OnClickListener toggleExpand = v -> {
            if (expandedIds.contains(eventId)) {
                expandedIds.remove(eventId);
            } else {
                expandedIds.add(eventId);
            }
            notifyItemChanged(holder.getAdapterPosition());
        };

        holder.itemView.setOnClickListener(toggleExpand);
        holder.tvChevron.setOnClickListener(toggleExpand);

        // Make sure captured variables are effectively-final for the lambda.
        final boolean isJoinedForClick = isJoinedEffective;
        holder.btnJoin.setOnClickListener(v -> listener.onEventClick(event, isJoinedForClick));
    }

    private boolean isActiveStatusForUi(String status) {
        return WaitingList.STATUS_PENDING.equals(status)
                || WaitingList.STATUS_SELECTED.equals(status)
                || WaitingList.STATUS_NOT_SELECTED.equals(status)
                || WaitingList.STATUS_ENROLLED.equals(status);
    }

    private static String formatPrice(String raw) {
        if (raw == null) return "TBD";
        String p = raw.trim();
        if (p.isEmpty()) return "TBD";
        if (p.startsWith("$")) return p;
        if (PRICE_NUMBER.matcher(p).matches()) return "$" + p;
        return p;
    }

    private TextView createCategoryChip(@NonNull EventViewHolder holder, @NonNull String label) {
        TextView chip = new TextView(holder.itemView.getContext());
        chip.setText(label);
        chip.setTextSize(11f);
        chip.setTextColor(ContextCompat.getColor(holder.itemView.getContext(), R.color.header_teal));
        chip.setBackgroundResource(R.drawable.bg_tag_teal);
        int hPad = dpToPx(holder, 10);
        int vPad = dpToPx(holder, 3);
        chip.setPadding(hPad, vPad, hPad, vPad);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        lp.setMarginEnd(dpToPx(holder, 6));
        chip.setLayoutParams(lp);
        return chip;
    }

    private TextView createAgeGroupTag(@NonNull EventViewHolder holder, @NonNull String label) {
        TextView chip = new TextView(holder.itemView.getContext());
        chip.setText(label);
        chip.setTextSize(12f);
        chip.setTypeface(chip.getTypeface(), android.graphics.Typeface.BOLD);
        chip.setTextColor(ContextCompat.getColor(holder.itemView.getContext(), R.color.age_group_tag_text));
        chip.setBackgroundResource(R.drawable.bg_age_group_tag);
        int hPad = dpToPx(holder, 10);
        int vPad = dpToPx(holder, 4);
        chip.setPadding(hPad, vPad, hPad, vPad);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        lp.setMarginEnd(dpToPx(holder, 6));
        chip.setLayoutParams(lp);
        return chip;
    }

    private LinearLayout createPrivateChip(@NonNull EventViewHolder holder) {
        LinearLayout chip = new LinearLayout(holder.itemView.getContext());
        chip.setOrientation(LinearLayout.HORIZONTAL);
        chip.setGravity(android.view.Gravity.CENTER_VERTICAL);
        chip.setBackgroundResource(R.drawable.bg_private_tag);
        int hPad = dpToPx(holder, 8);
        int vPad = dpToPx(holder, 2);
        chip.setPadding(hPad, vPad, hPad, vPad);

        android.widget.ImageView icon = new android.widget.ImageView(holder.itemView.getContext());
        icon.setImageResource(R.drawable.ic_lock_private);
        LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(dpToPx(holder, 12), dpToPx(holder, 12));
        icon.setLayoutParams(iconLp);

        TextView label = new TextView(holder.itemView.getContext());
        label.setText(R.string.private_tag_label);
        label.setTextSize(12f);
        label.setTextColor(ContextCompat.getColor(holder.itemView.getContext(), R.color.private_tag_text));
        label.setTypeface(label.getTypeface(), android.graphics.Typeface.BOLD);
        LinearLayout.LayoutParams textLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        textLp.setMarginStart(dpToPx(holder, 4));
        label.setLayoutParams(textLp);

        LinearLayout.LayoutParams chipLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        chipLp.setMarginEnd(dpToPx(holder, 6));
        chip.setLayoutParams(chipLp);

        chip.addView(icon);
        chip.addView(label);
        return chip;
    }

    private int dpToPx(@NonNull EventViewHolder holder, int dp) {
        float density = holder.itemView.getResources().getDisplayMetrics().density;
        return Math.round(dp * density);
    }

    @Override
    public int getItemCount() {
        return events.size();
    }

    static class EventViewHolder extends RecyclerView.ViewHolder {
        TextView tvName, tvStatus, tvChevron;
        android.widget.ImageView ivEventImage;
        LinearLayout layoutCategoryTags;
        TextView btnJoin;
        TextView tvWaitlistCount;
        TextView tvRegistrationOpens;
        TextView tvDescription, tvDetailDate, tvDetailTime, tvDetailLocation, tvPrice, tvCapacity, tvRegOpen, tvRegClose, tvGeoNote, tvCriteriaDescription;
        LinearLayout layoutExpandedDetails, layoutGeoNote;

        EventViewHolder(@NonNull View itemView) {
            super(itemView);
            ivEventImage = itemView.findViewById(R.id.iv_event_image);
            tvName = itemView.findViewById(R.id.tv_event_name);
            tvStatus = itemView.findViewById(R.id.tv_event_status);
            tvChevron = itemView.findViewById(R.id.tv_chevron);
            layoutCategoryTags = itemView.findViewById(R.id.layout_category_tags);
            btnJoin = itemView.findViewById(R.id.btn_join);
            tvWaitlistCount = itemView.findViewById(R.id.tv_waitlist_count);
            layoutExpandedDetails = itemView.findViewById(R.id.layout_expanded_details);
            tvDescription = itemView.findViewById(R.id.tv_description);
            tvDetailDate = itemView.findViewById(R.id.tv_detail_date);
            tvDetailTime = itemView.findViewById(R.id.tv_detail_time);
            tvDetailLocation = itemView.findViewById(R.id.tv_detail_location);
            tvPrice = itemView.findViewById(R.id.tv_price);
            tvCapacity = itemView.findViewById(R.id.tv_capacity);
            tvRegOpen = itemView.findViewById(R.id.tv_reg_open);
            tvRegClose = itemView.findViewById(R.id.tv_reg_close);
            layoutGeoNote = itemView.findViewById(R.id.layout_geo_note);
            tvGeoNote = itemView.findViewById(R.id.tv_geo_note);
            tvCriteriaDescription = itemView.findViewById(R.id.tv_criteria_description);
        }
    }
}
