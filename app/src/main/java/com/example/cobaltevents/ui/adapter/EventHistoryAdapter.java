package com.example.cobaltevents.ui.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.MotionEvent;
import android.widget.TextView;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.example.cobaltevents.R;
import com.example.cobaltevents.model.EventHistory;

import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;

public class EventHistoryAdapter extends RecyclerView.Adapter<EventHistoryAdapter.ViewHolder> {
    
    private List<EventHistory> historyList;
    private final Listener listener;
    private java.util.Map<String, String> effectiveStatusByEventId;
    
    public interface Listener {
        void onHistoryClick(EventHistory history);
        void onDeleteClick(EventHistory history);
    }
    
    public EventHistoryAdapter(List<EventHistory> historyList, Listener listener) {
        this.historyList = historyList;
        this.listener = listener;
    }

    /** Sets map without triggering a redraw; the subsequent {@link #updateHistory} call will notify. */
    public void setEffectiveStatusByEventId(java.util.Map<String, String> effectiveStatusByEventId) {
        this.effectiveStatusByEventId = effectiveStatusByEventId;
    }
    
    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_event_history, parent, false);
        return new ViewHolder(view);
    }
    
    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        EventHistory history = historyList.get(position);
        
        holder.tvEventName.setText(history.getEvent().getName());

        String location = history.getEvent().getLocation();
        holder.tvEventLocation.setText(location);
        if (location != null && !location.isEmpty()) {
            holder.tvEventLocation.setOnClickListener(v -> {
                android.content.Intent intent = new android.content.Intent(
                        v.getContext(), com.example.cobaltevents.ui.MapPreviewActivity.class);
                intent.putExtra(com.example.cobaltevents.ui.MapPreviewActivity.EXTRA_LOCATION, location);
                intent.putExtra(com.example.cobaltevents.ui.MapPreviewActivity.EXTRA_EVENT_NAME,
                        history.getEvent().getName());
                v.getContext().startActivity(intent);
            });
        }
        
        if (history.getEvent().getEventDate() != null) {
            SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault());
            holder.tvEventDate.setText(sdf.format(history.getEvent().getEventDate().toDate()));
        }
        
        String statusDb = history.getStatus();
        String eventId = history.getEvent() != null ? history.getEvent().getEventId() : null;
        String status = statusDb;
        if (eventId != null && effectiveStatusByEventId != null) {
            String effective = effectiveStatusByEventId.get(eventId);
            if (effective != null) status = effective;
        }
        holder.tvStatus.setText(getStatusText(status));
        holder.tvStatus.setTextColor(getStatusColor(holder.itemView, status));
        holder.tvStatus.setBackgroundResource(getStatusBackgroundRes(status));
        
        holder.itemView.setOnClickListener(v -> listener.onHistoryClick(history));
        boolean canLeave = status == null
                || "pending".equals(status)
                || "selected".equals(status)
                || "not_selected".equals(status);
        holder.ivDelete.setVisibility(canLeave ? View.VISIBLE : View.GONE);
        holder.ivDelete.setOnClickListener(null);
        if (canLeave) {
            // Consume touch events so tapping the trash does not also trigger the parent row click
            // (which opens the read-only event detail popup).
            holder.ivDelete.setOnTouchListener((v, event) -> {
                if (event == null) return false;
                if (event.getAction() == MotionEvent.ACTION_UP) {
                    if (listener != null) listener.onDeleteClick(history);
                }
                return true;
            });
        } else {
            holder.ivDelete.setOnTouchListener(null);
        }
    }
    
    @Override
    public int getItemCount() {
        return historyList.size();
    }
    
    public void updateHistory(List<EventHistory> newHistory) {
        this.historyList = newHistory;
        notifyDataSetChanged();
    }
    
    private String getStatusText(String status) {
        switch (status) {
            case "selected": return "Selected";
            case "enrolled": return "Enrolled";
            case "declined": return "Declined";
            case "declined_found_replacement": return "Declined";
            case "not_selected": return "Not Selected";
            case "pending": return "Pending";
            case "rejected": return "Rejected";
            default: return "Unknown";
        }
    }

    private int getStatusColor(View itemView, String status) {
        switch (status) {
            case "selected": return ContextCompat.getColor(itemView.getContext(), R.color.accent);
            case "enrolled": return ContextCompat.getColor(itemView.getContext(), R.color.accent);
            case "declined": return ContextCompat.getColor(itemView.getContext(), R.color.alert_red);
            case "declined_found_replacement": return ContextCompat.getColor(itemView.getContext(), R.color.alert_red);
            case "not_selected": return ContextCompat.getColor(itemView.getContext(), R.color.alert_red);
            case "rejected": return ContextCompat.getColor(itemView.getContext(), R.color.alert_red);
            case "pending": return ContextCompat.getColor(itemView.getContext(), R.color.status_warning_orange);
            default: return ContextCompat.getColor(itemView.getContext(), R.color.black);
        }
    }
    
    private int getStatusBackgroundRes(String status) {
        switch (status) {
            case "selected":
            case "enrolled":
                return R.drawable.badge_selected_bg;
            case "declined":
            case "declined_found_replacement":
            case "not_selected":
            case "rejected":
                return R.drawable.badge_declined_bg;
            case "pending":
                return R.drawable.badge_pending_bg;
            default:
                return R.drawable.badge_neutral_bg;
        }
    }
    
    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvEventName, tvEventLocation, tvEventDate, tvStatus;
        ImageView ivDelete;
        
        ViewHolder(View itemView) {
            super(itemView);
            tvEventName = itemView.findViewById(R.id.tv_event_name);
            tvEventLocation = itemView.findViewById(R.id.tv_event_location);
            tvEventDate = itemView.findViewById(R.id.tv_event_date);
            tvStatus = itemView.findViewById(R.id.tv_status);
            ivDelete = itemView.findViewById(R.id.iv_delete);
        }
    }
}
