package com.example.cobaltevents.ui.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.example.cobaltevents.R;
import com.example.cobaltevents.model.Event;
import com.example.cobaltevents.model.WaitingList;
import com.google.firebase.Timestamp;

import java.text.SimpleDateFormat;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class EventAdapter extends RecyclerView.Adapter<EventAdapter.EventViewHolder> {

    public interface OnEventClickListener {
        void onEventClick(Event event, boolean isJoined);
    }


    private List<Event> events;
    private final OnEventClickListener listener;
    private final Set<String> expandedIds = new HashSet<>();
    private Map<String, WaitingList> activeRegistrationsByEventId;
    private Map<String, Integer> waitlistCountByEventId;
    private String deviceId;

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

    public void setActiveRegistrationsByEventId(Map<String, WaitingList> map) {
        this.activeRegistrationsByEventId = map;
        notifyDataSetChanged();
    }

    public void setWaitlistCountByEventId(Map<String, Integer> map) {
        this.waitlistCountByEventId = map;
        notifyDataSetChanged();
    }

    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
    }


    /** Call after updating notificationsAllowed in Firestore so the card reflects the new value. */
    public void updateNotificationsAllowedForEvent(String eventId, boolean enabled) {
        if (activeRegistrationsByEventId != null) {
            WaitingList reg = activeRegistrationsByEventId.get(eventId);
            if (reg != null) {
                reg.setNotificationsAllowed(enabled);
                for (int i = 0; i < events.size(); i++) {
                    if (eventId.equals(events.get(i).getEventId())) {
                        notifyItemChanged(i);
                        break;
                    }
                }
            }
        }
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
        Integer count = (event.getEventId() != null && waitlistCountByEventId != null)
                ? waitlistCountByEventId.get(event.getEventId())
                : null;

        holder.tvName.setText(event.getName());

        if (count != null) {
            holder.tvWaitlistCount.setText(count + " on waitlist");
        } else {
            holder.tvWaitlistCount.setText("");
        }

        if (event.getCategory() != null && !event.getCategory().isEmpty()) {
            holder.tvCategoryTag.setVisibility(View.VISIBLE);
            holder.tvCategoryTag.setText(event.getCategory());
        } else {
            holder.tvCategoryTag.setVisibility(View.GONE);
        }

        Timestamp now = Timestamp.now();
        boolean registrationClosed = event.getRegistrationClose() != null
                && event.getRegistrationClose().compareTo(now) < 0;
        boolean upcoming = event.getRegistrationOpen() != null
                && event.getRegistrationOpen().compareTo(now) > 0
                && event.getEventDate() != null
                && event.getEventDate().compareTo(now) > 0;
        int capacity = event.getWaitingListCapacity();
        boolean full = !isJoined
                && capacity > 0
                && count != null
                && count >= capacity;

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
        } else {
            holder.btnJoin.setText(isJoined ? "LEAVE WAITLIST" : "JOIN WAITLIST");
            holder.btnJoin.setAlpha(1.0f);
            holder.btnJoin.setEnabled(true);
            holder.btnJoin.setBackgroundResource(isJoined ? R.drawable.bg_button_red_pill : R.drawable.bg_button_join_solid);
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

            if (event.getRegistrationClose() != null) {
                holder.tvRegClose.setText(DATE_FORMAT.format(event.getRegistrationClose().toDate()));
            } else {
                holder.tvRegClose.setText("TBD");
            }

            String criteriaText = (event.getCriteria() != null && !event.getCriteria().isEmpty())
                    ? event.getCriteria()
                    : "No special criteria.";
            holder.tvCriteriaDescription.setText(criteriaText);
            holder.layoutGeoNote.setVisibility(View.GONE);
            holder.layoutEventNotifications.setVisibility(View.GONE);
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

        holder.btnJoin.setOnClickListener(v -> listener.onEventClick(event, isJoined));
    }

    private static String formatPrice(String raw) {
        if (raw == null) return "TBD";
        String p = raw.trim();
        if (p.isEmpty()) return "TBD";
        if (p.startsWith("$")) return p;
        if (PRICE_NUMBER.matcher(p).matches()) return "$" + p;
        return p;
    }

    @Override
    public int getItemCount() {
        return events.size();
    }

    static class EventViewHolder extends RecyclerView.ViewHolder {
        TextView tvName, tvStatus, tvChevron, tvCategoryTag;
        TextView btnJoin;
        TextView tvWaitlistCount;
        TextView tvDescription, tvDetailDate, tvDetailTime, tvDetailLocation, tvPrice, tvCapacity, tvRegClose, tvGeoNote, tvCriteriaDescription;
        LinearLayout layoutExpandedDetails, layoutGeoNote, layoutEventNotifications;
        androidx.appcompat.widget.SwitchCompat switchEventNotifications;

        EventViewHolder(@NonNull View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.tv_event_name);
            tvStatus = itemView.findViewById(R.id.tv_event_status);
            tvChevron = itemView.findViewById(R.id.tv_chevron);
            tvCategoryTag = itemView.findViewById(R.id.tv_category_tag);
            btnJoin = itemView.findViewById(R.id.btn_join);
            tvWaitlistCount = itemView.findViewById(R.id.tv_waitlist_count);
            layoutExpandedDetails = itemView.findViewById(R.id.layout_expanded_details);
            tvDescription = itemView.findViewById(R.id.tv_description);
            tvDetailDate = itemView.findViewById(R.id.tv_detail_date);
            tvDetailTime = itemView.findViewById(R.id.tv_detail_time);
            tvDetailLocation = itemView.findViewById(R.id.tv_detail_location);
            tvPrice = itemView.findViewById(R.id.tv_price);
            tvCapacity = itemView.findViewById(R.id.tv_capacity);
            tvRegClose = itemView.findViewById(R.id.tv_reg_close);
            layoutGeoNote = itemView.findViewById(R.id.layout_geo_note);
            tvGeoNote = itemView.findViewById(R.id.tv_geo_note);
            tvCriteriaDescription = itemView.findViewById(R.id.tv_criteria_description);
            layoutEventNotifications = itemView.findViewById(R.id.layout_event_notifications);
            switchEventNotifications = itemView.findViewById(R.id.switch_event_notifications);
        }
    }
}
