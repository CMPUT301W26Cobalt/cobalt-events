package com.example.cobaltevents.ui.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

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
    private final OnHistoryClickListener listener;
    
    public interface OnHistoryClickListener {
        void onHistoryClick(EventHistory history);
    }
    
    public EventHistoryAdapter(List<EventHistory> historyList, OnHistoryClickListener listener) {
        this.historyList = historyList;
        this.listener = listener;
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
        holder.tvEventLocation.setText(history.getEvent().getLocation());
        
        if (history.getEvent().getEventDate() != null) {
            SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault());
            holder.tvEventDate.setText(sdf.format(history.getEvent().getEventDate().toDate()));
        }
        
        String status = history.getStatus();
        holder.tvStatus.setText(getStatusText(status));
        holder.tvStatus.setTextColor(getStatusColor(holder.itemView, status));
        
        holder.itemView.setOnClickListener(v -> listener.onHistoryClick(history));
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
            case "not_selected": return "Not Selected";
            case "pending": return "Pending";
            case "cancelled": return "Cancelled";
            case "withdrawn": return "Withdrawn";
            default: return "Unknown";
        }
    }
    
    private int getStatusColor(View itemView, String status) {
        switch (status) {
            case "selected": return ContextCompat.getColor(itemView.getContext(), R.color.accent);
            case "not_selected": return ContextCompat.getColor(itemView.getContext(), R.color.alert_red);
            case "pending": return ContextCompat.getColor(itemView.getContext(), R.color.status_warning_orange);
            case "cancelled": return ContextCompat.getColor(itemView.getContext(), R.color.grey_nav_inactive);
            case "withdrawn": return ContextCompat.getColor(itemView.getContext(), R.color.grey_medium);
            default: return ContextCompat.getColor(itemView.getContext(), R.color.black);
        }
    }
    
    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvEventName, tvEventLocation, tvEventDate, tvStatus;
        
        ViewHolder(View itemView) {
            super(itemView);
            tvEventName = itemView.findViewById(R.id.tv_event_name);
            tvEventLocation = itemView.findViewById(R.id.tv_event_location);
            tvEventDate = itemView.findViewById(R.id.tv_event_date);
            tvStatus = itemView.findViewById(R.id.tv_status);
        }
    }
}
