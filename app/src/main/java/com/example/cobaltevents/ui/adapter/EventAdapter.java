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

/**
 * RecyclerView adapter for displaying a list of Event objects.
 * Tapping a card expands it inline to show full event details.
 * The JOIN button calls the provided listener.
 */
public class EventAdapter extends RecyclerView.Adapter<EventAdapter.EventViewHolder> {

    public interface OnEventClickListener {
        void onEventClick(Event event, boolean isJoined);
    }

    private List<Event> events;
    private final OnEventClickListener listener;
    private final Set<String> expandedIds = new HashSet<>();
    private Map<String, WaitingList> activeRegistrationsByEventId;
    private Map<String, Integer> waitlistCountByEventId;

    private static final SimpleDateFormat DATE_FORMAT =
            new SimpleDateFormat("MMM d, yyyy", Locale.getDefault());
    private static final SimpleDateFormat TIME_FORMAT =
            new SimpleDateFormat("h:mm a", Locale.getDefault());

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

    /** Map eventId -> active waitlist count. */
    public void setWaitlistCountByEventId(Map<String, Integer> map) {
        this.waitlistCountByEventId = map;
        notifyDataSetChanged();
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

        // Basic fields
        holder.tvName.setText(event.getName());

        if (count != null) {
            holder.tvWaitlistCount.setText(count + " on waitlist");
        } else {
            holder.tvWaitlistCount.setText("");
        }

        // Category tag
        if (event.getCategory() != null && !event.getCategory().isEmpty()) {
            holder.tvCategoryTag.setVisibility(View.VISIBLE);
            holder.tvCategoryTag.setText(event.getCategory());
        } else {
            holder.tvCategoryTag.setVisibility(View.GONE);
        }

        // Registration status (controls JOIN button state only)
        Timestamp now = Timestamp.now();
        boolean registrationClosed = event.getRegistrationClose() != null
                && event.getRegistrationClose().compareTo(now) < 0;
        boolean registrationNotOpen = event.getRegistrationOpen() != null
                && event.getRegistrationOpen().compareTo(now) > 0;

        if (registrationClosed) {
            holder.btnJoin.setText("REGISTRATION CLOSED");
            holder.btnJoin.setAlpha(0.45f);
            holder.btnJoin.setEnabled(false);
            holder.btnJoin.setBackgroundResource(R.drawable.bg_button_green_pill);
        } else if (registrationNotOpen) {
            holder.btnJoin.setText(isJoined ? "LEAVE WAITLIST" : "JOIN WAITLIST");
            holder.btnJoin.setAlpha(0.65f);
            holder.btnJoin.setEnabled(true);
            holder.btnJoin.setBackgroundResource(isJoined ? R.drawable.bg_button_red_pill : R.drawable.bg_button_green_pill);
        } else {
            holder.btnJoin.setText(isJoined ? "LEAVE WAITLIST" : "JOIN WAITLIST");
            holder.btnJoin.setAlpha(1.0f);
            holder.btnJoin.setEnabled(true);
            holder.btnJoin.setBackgroundResource(isJoined ? R.drawable.bg_button_red_pill : R.drawable.bg_button_green_pill);
        }

        // Expanded detail section
        holder.layoutExpandedDetails.setVisibility(isExpanded ? View.VISIBLE : View.GONE);
        holder.tvChevron.setText(isExpanded ? "▲" : "▼");

        if (isExpanded) {
            // Description
            holder.tvDescription.setText(
                    event.getDescription() != null && !event.getDescription().isEmpty()
                            ? event.getDescription() : "No description available.");

            // Date + time
            if (event.getEventDate() != null) {
                holder.tvDetailDate.setText(DATE_FORMAT.format(event.getEventDate().toDate()));
                holder.tvDetailTime.setText(TIME_FORMAT.format(event.getEventDate().toDate()));
            } else {
                holder.tvDetailDate.setText("TBD");
                holder.tvDetailTime.setText("TBD");
            }

            // Location
            holder.tvDetailLocation.setText(event.getLocation() != null ? event.getLocation() : "TBD");

            // Price
            String price = event.getPrice();
            holder.tvPrice.setText(price != null && !price.trim().isEmpty() ? price : "TBD");

            // Capacity
            if (event.getWaitingListCapacity() > 0) {
                holder.tvCapacity.setText(event.getWaitingListCapacity() + " spots");
            } else {
                holder.tvCapacity.setText("Unlimited");
            }

            // Registration close date
            if (event.getRegistrationClose() != null) {
                holder.tvRegClose.setText(DATE_FORMAT.format(event.getRegistrationClose().toDate()));
            } else {
                holder.tvRegClose.setText("TBD");
            }

            // Geolocation note
            if (event.isGeolocationRequired()) {
                holder.layoutGeoNote.setVisibility(View.VISIBLE);
            } else {
                holder.layoutGeoNote.setVisibility(View.GONE);
            }
        }

        // Toggle expand on card tap (but not on JOIN button)
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

        // JOIN button calls the listener
        holder.btnJoin.setOnClickListener(v -> listener.onEventClick(event, isJoined));
    }

    @Override
    public int getItemCount() {
        return events.size();
    }

    static class EventViewHolder extends RecyclerView.ViewHolder {
        TextView tvName, tvStatus, tvChevron, tvCategoryTag;
        TextView btnJoin;
        TextView tvWaitlistCount;
        TextView tvDescription, tvDetailDate, tvDetailTime, tvDetailLocation, tvPrice, tvCapacity, tvRegClose, tvGeoNote;
        LinearLayout layoutExpandedDetails, layoutGeoNote;

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
        }
    }
}
