package com.example.cobaltevents.ui.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.example.cobaltevents.R;
import com.example.cobaltevents.model.Notification;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class NotificationListAdapter extends RecyclerView.Adapter<NotificationListAdapter.ViewHolder> {

    private final List<Notification> items = new ArrayList<>();
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd h:mm a", Locale.getDefault());

    public void setItems(List<Notification> newItems) {
        items.clear();
        if (newItems != null) items.addAll(newItems);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_notification_card, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Notification n = items.get(position);
        holder.title.setText(n.getTitle() != null ? n.getTitle() : "");
        holder.message.setText(n.getMessage() != null ? n.getMessage() : "");
        holder.date.setText(n.getTimestamp() != null ? DATE_FORMAT.format(n.getTimestamp()) : "");

        String type = n.getType() != null ? n.getType() : "";
        int iconBgRes;
        int iconRes;
        if (Notification.TYPE_GOT_OFF_WAITLIST.equals(type)) {
            iconBgRes = R.drawable.bg_notif_icon_star;
            iconRes = R.drawable.ic_notif_star;
        } else if (Notification.TYPE_NOT_SELECTED.equals(type)) {
            iconBgRes = R.drawable.bg_notif_icon_x;
            iconRes = R.drawable.ic_notif_x;
        } else {
            iconBgRes = R.drawable.bg_notif_icon_trophy;
            iconRes = R.drawable.ic_notif_trophy;
        }
        holder.iconContainer.setBackgroundResource(iconBgRes);
        holder.icon.setImageResource(iconRes);

        // Event-specific actions and badges removed; notifications are informational only.
        holder.buttonsRow.setVisibility(View.GONE);
        holder.badgeAccepted.setVisibility(View.GONE);
        holder.badgeDeclined.setVisibility(View.GONE);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        FrameLayout iconContainer;
        ImageView icon;
        TextView title;
        TextView message;
        TextView date;
        LinearLayout badgeAccepted;
        LinearLayout badgeDeclined;
        LinearLayout buttonsRow;
        TextView btnAccept;
        TextView btnDecline;

        ViewHolder(View itemView) {
            super(itemView);
            iconContainer = itemView.findViewById(R.id.icon_container);
            icon = itemView.findViewById(R.id.icon);
            title = itemView.findViewById(R.id.title);
            message = itemView.findViewById(R.id.message);
            date = itemView.findViewById(R.id.date);
            badgeAccepted = itemView.findViewById(R.id.badge_accepted);
            badgeDeclined = itemView.findViewById(R.id.badge_declined);
            buttonsRow = itemView.findViewById(R.id.buttons_row);
            btnAccept = itemView.findViewById(R.id.btn_accept);
            btnDecline = itemView.findViewById(R.id.btn_decline);
        }
    }
}
