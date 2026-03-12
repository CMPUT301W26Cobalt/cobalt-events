package com.example.cobaltevents.ui.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.example.cobaltevents.R;

import java.util.ArrayList;
import java.util.List;

public class NotificationEventAdapter extends RecyclerView.Adapter<NotificationEventAdapter.ViewHolder> {

    public static class Item {
        public final String eventId;
        public final String eventName;
        public boolean enabled;

        public Item(String eventId, String eventName, boolean enabled) {
            this.eventId = eventId;
            this.eventName = eventName;
            this.enabled = enabled;
        }
    }

    private final List<Item> items = new ArrayList<>();
    private OnToggleListener onToggleListener;

    public interface OnToggleListener {
        void onToggle(String eventId, boolean enabled);
    }

    public void setOnToggleListener(OnToggleListener listener) {
        this.onToggleListener = listener;
    }

    public void setItems(List<Item> newItems) {
        items.clear();
        items.addAll(newItems);
        notifyDataSetChanged();
    }

    /** Set notifications on/off for all listed events (e.g. master switch). Updates UI immediately. */
    public void setAllEnabled(boolean enabled) {
        for (Item item : items) {
            item.enabled = enabled;
        }
        notifyDataSetChanged();
    }

    public List<String> getEventIds() {
        List<String> ids = new ArrayList<>(items.size());
        for (Item item : items) {
            ids.add(item.eventId);
        }
        return ids;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_notification_event, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Item item = items.get(position);
        holder.tvEventName.setText(item.eventName);
        holder.switchEventNotification.setChecked(item.enabled);
        holder.layoutNotificationsEnabled.setVisibility(item.enabled ? View.VISIBLE : View.GONE);
        holder.divider.setVisibility(position < items.size() - 1 ? View.VISIBLE : View.GONE);
        holder.switchEventNotification.setThumbTintList(ContextCompat.getColorStateList(holder.itemView.getContext(), R.color.thumb_white));
        holder.switchEventNotification.setTrackTintList(ContextCompat.getColorStateList(holder.itemView.getContext(), R.color.switch_track_selector));

        holder.switchEventNotification.setOnCheckedChangeListener(null);
        holder.switchEventNotification.setChecked(item.enabled);
        holder.switchEventNotification.setOnCheckedChangeListener((buttonView, isChecked) -> {
            item.enabled = isChecked;
            holder.layoutNotificationsEnabled.setVisibility(isChecked ? View.VISIBLE : View.GONE);
            if (onToggleListener != null) {
                onToggleListener.onToggle(item.eventId, isChecked);
            }
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvEventName;
        View layoutNotificationsEnabled;
        androidx.appcompat.widget.SwitchCompat switchEventNotification;
        View divider;

        ViewHolder(View itemView) {
            super(itemView);
            tvEventName = itemView.findViewById(R.id.tv_event_name);
            layoutNotificationsEnabled = itemView.findViewById(R.id.layout_notifications_enabled);
            switchEventNotification = itemView.findViewById(R.id.switch_event_notification);
            divider = itemView.findViewById(R.id.divider);
        }
    }
}
