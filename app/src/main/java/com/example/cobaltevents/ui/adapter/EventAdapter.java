package com.example.cobaltevents.ui.adapter;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.cobaltevents.R;
import com.example.cobaltevents.model.Event;
import com.google.firebase.Timestamp;

import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;

/**
 * RecyclerView adapter for displaying a list of Event objects.
 * Each card shows the event name, location, date, registration status,
 * and a join button. Closed events are visually dimmed.
 */
public class EventAdapter extends RecyclerView.Adapter<EventAdapter.EventViewHolder> {

    public interface OnEventClickListener {
        void onEventClick(Event event);
    }

    private List<Event> events;
    private final OnEventClickListener listener;
    private static final SimpleDateFormat DATE_FORMAT =
            new SimpleDateFormat("MMM d, yyyy", Locale.getDefault());

    public EventAdapter(List<Event> events, OnEventClickListener listener) {
        this.events = events;
        this.listener = listener;
    }

    public void updateEvents(List<Event> newEvents) {
        this.events = newEvents;
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

        holder.tvName.setText(event.getName());
        holder.tvLocation.setText(event.getLocation() != null ? event.getLocation() : "");

        if (event.getEventDate() != null) {
            holder.tvDate.setText(DATE_FORMAT.format(event.getEventDate().toDate()));
        } else {
            holder.tvDate.setText("Date TBD");
        }

        // Determine registration status
        Timestamp now = Timestamp.now();
        boolean registrationClosed = event.getRegistrationClose() != null
                && event.getRegistrationClose().compareTo(now) < 0;
        boolean registrationNotOpen = event.getRegistrationOpen() != null
                && event.getRegistrationOpen().compareTo(now) > 0;

        if (registrationClosed) {
            holder.tvStatus.setText("CLOSED");
            holder.tvStatus.setTextColor(Color.parseColor("#C62828"));
            holder.btnJoin.setText("REGISTRATION CLOSED");
            holder.btnJoin.setAlpha(0.45f);
            holder.btnJoin.setEnabled(false);
        } else if (registrationNotOpen) {
            holder.tvStatus.setText("UPCOMING");
            holder.tvStatus.setTextColor(Color.parseColor("#E65100"));
            holder.btnJoin.setText("JOIN WAITLIST");
            holder.btnJoin.setAlpha(0.65f);
            holder.btnJoin.setEnabled(true);
        } else {
            holder.tvStatus.setText("OPEN");
            holder.tvStatus.setTextColor(Color.parseColor("#2E7D32"));
            holder.btnJoin.setText("JOIN WAITLIST");
            holder.btnJoin.setAlpha(1.0f);
            holder.btnJoin.setEnabled(true);
        }

        holder.itemView.setOnClickListener(v -> listener.onEventClick(event));
        holder.btnJoin.setOnClickListener(v -> listener.onEventClick(event));
    }

    @Override
    public int getItemCount() {
        return events.size();
    }

    static class EventViewHolder extends RecyclerView.ViewHolder {
        TextView tvName, tvLocation, tvDate, tvStatus, btnJoin;

        EventViewHolder(@NonNull View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.tv_event_name);
            tvLocation = itemView.findViewById(R.id.tv_event_location);
            tvDate = itemView.findViewById(R.id.tv_event_date);
            tvStatus = itemView.findViewById(R.id.tv_event_status);
            btnJoin = itemView.findViewById(R.id.btn_join);
        }
    }
}
