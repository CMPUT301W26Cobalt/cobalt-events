package com.example.cobaltevents.ui.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.cobaltevents.R;
import com.example.cobaltevents.model.Event;

import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;

public class OrganizerEventAdapter extends RecyclerView.Adapter<OrganizerEventAdapter.ViewHolder> {

    public interface OnManageClickListener {
        void onManageClick(Event event);
    }

    private List<Event> events;
    private OnManageClickListener manageClickListener;

    private static final SimpleDateFormat DATE_FORMAT =
            new SimpleDateFormat("MMM d, yyyy", Locale.getDefault());

    public OrganizerEventAdapter(List<Event> events) {
        this.events = events;
    }

    public void setOnManageClickListener(OnManageClickListener listener) {
        this.manageClickListener = listener;
    }

    public void updateEvents(List<Event> newEvents) {
        this.events = newEvents;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_organizer_event, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Event event = events.get(position);
        holder.tvName.setText(event.getName() != null ? event.getName() : "Untitled Event");
        holder.tvDate.setText(event.getEventDate() != null
                ? DATE_FORMAT.format(event.getEventDate().toDate()) : "Date TBD");
        holder.tvLocation.setText(event.getLocation() != null ? event.getLocation() : "Location TBD");
        holder.btnManage.setOnClickListener(v -> {
            if (manageClickListener != null) manageClickListener.onManageClick(event);
        });
    }

    @Override
    public int getItemCount() {
        return events.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvName, tvDate, tvLocation, btnManage;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.tv_event_name);
            tvDate = itemView.findViewById(R.id.tv_event_date);
            tvLocation = itemView.findViewById(R.id.tv_event_location);
            btnManage = itemView.findViewById(R.id.btn_manage);
        }
    }
}
