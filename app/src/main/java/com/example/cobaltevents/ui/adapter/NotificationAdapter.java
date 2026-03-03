package com.example.cobaltevents.ui.adapter;

import android.graphics.Typeface;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.cobaltevents.R;
import com.example.cobaltevents.model.Notification;

import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;

/**
 * RecyclerView adapter for rendering notification items.
 * US 01.04.01 criteria 3: Shows notifications in-app.
 * US 01.04.02 criteria 2: Message clearly indicates outcome (shown in message text).
 */
public class NotificationAdapter extends RecyclerView.Adapter<NotificationAdapter.NotificationViewHolder> {

    private final List<Notification> notifications;
    private final OnNotificationClickListener clickListener;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("MMM d, yyyy h:mm a", Locale.getDefault());

    public NotificationAdapter(List<Notification> notifications, OnNotificationClickListener clickListener) {
        this.notifications = notifications;
        this.clickListener = clickListener;
    }

    @NonNull
    @Override
    public NotificationViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_notification, parent, false);
        return new NotificationViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull NotificationViewHolder holder, int position) {
        Notification notification = notifications.get(position);

        holder.tvTitle.setText(notification.getTitle());
        holder.tvMessage.setText(notification.getMessage());

        if (notification.getTimestamp() != null) {
            holder.tvTimestamp.setText(dateFormat.format(notification.getTimestamp()));
        } else {
            holder.tvTimestamp.setText("");
        }

        // Visual unread indicator
        if (notification.isRead()) {
            holder.unreadDot.setVisibility(View.INVISIBLE);
            holder.tvTitle.setTypeface(null, Typeface.NORMAL);
        } else {
            holder.unreadDot.setVisibility(View.VISIBLE);
            holder.tvTitle.setTypeface(null, Typeface.BOLD);
        }

        holder.itemView.setOnClickListener(v -> clickListener.onNotificationClick(notification));
    }

    @Override
    public int getItemCount() {
        return notifications.size();
    }

    static class NotificationViewHolder extends RecyclerView.ViewHolder {
        final View unreadDot;
        final TextView tvTitle;
        final TextView tvMessage;
        final TextView tvTimestamp;

        NotificationViewHolder(@NonNull View itemView) {
            super(itemView);
            unreadDot = itemView.findViewById(R.id.view_unread_dot);
            tvTitle = itemView.findViewById(R.id.tv_notif_title);
            tvMessage = itemView.findViewById(R.id.tv_notif_message);
            tvTimestamp = itemView.findViewById(R.id.tv_notif_timestamp);
        }
    }

    public interface OnNotificationClickListener {
        void onNotificationClick(Notification notification);
    }
}
