package com.example.cobaltevents.ui.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.cobaltevents.R;
import com.example.cobaltevents.model.Notification;
import com.example.cobaltevents.model.WaitingList;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;

public class NotificationListAdapter extends RecyclerView.Adapter<NotificationListAdapter.ViewHolder> {

    private final List<Notification> items = new ArrayList<>();
    private Map<String, String> eventIdToStatus = new HashMap<>();
    /** Resolved event names keyed by eventId; used so every card body mentions the event. */
    private Map<String, String> eventIdToEventName = new HashMap<>();
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd h:mm a", Locale.getDefault());
    private OnNotificationActionListener actionListener;

    public interface OnNotificationActionListener {
        void onAccept(Notification notification);
        void onReject(Notification notification);
    }

    public void setOnNotificationActionListener(OnNotificationActionListener listener) {
        this.actionListener = listener;
    }

    public void setItems(List<Notification> newItems,
                         Map<String, String> newEventIdToStatus,
                         Map<String, String> newEventIdToEventName) {
        items.clear();
        if (newItems != null) items.addAll(newItems);
        eventIdToStatus = newEventIdToStatus != null ? newEventIdToStatus : new HashMap<>();
        eventIdToEventName = newEventIdToEventName != null ? newEventIdToEventName : new HashMap<>();
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
        String type = n.getType() != null ? n.getType() : "";
        if (Notification.TYPE_EVENT_ALERT.equals(type)) {
            holder.title.setText(holder.itemView.getContext().getString(R.string.notification_event_alert_title));
            holder.title.setTextSize(18f);
        } else {
            holder.title.setText(n.getTitle() != null ? n.getTitle() : "");
            holder.title.setTextSize(15f);
        }
        holder.message.setText(formatMessageWithEventLine(holder.itemView, n));
        holder.date.setText(n.getTimestamp() != null ? DATE_FORMAT.format(n.getTimestamp()) : "");

        int iconBgRes;
        int iconRes;
        if (Notification.TYPE_EVENT_ALERT.equals(type)) {
            iconBgRes = R.drawable.bg_notif_icon_alert;
            iconRes = R.drawable.ic_notif_alert;
        } else if (isPrivateEventType(type)) {
            iconBgRes = R.drawable.bg_notif_icon_lock;
            iconRes = R.drawable.ic_notif_lock;
        } else if (Notification.TYPE_CO_ORGANIZER.equals(type)) {
            if (Notification.RECIPIENT_MODE_ORGANIZER.equals(n.getRecipientMode())) {
                iconBgRes = R.drawable.bg_notif_icon_trophy;
                iconRes = R.drawable.ic_organizer_building;
            } else {
                iconBgRes = R.drawable.bg_notif_icon_medal;
                iconRes = R.drawable.ic_notif_medal;
            }
        } else if (Notification.TYPE_GOT_OFF_WAITLIST.equals(type)) {
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
        if (Notification.TYPE_CO_ORGANIZER.equals(type)
                && Notification.RECIPIENT_MODE_ORGANIZER.equals(n.getRecipientMode())) {
            holder.btnAccept.setBackgroundResource(R.drawable.bg_button_primary_account_solid_organizer);
            holder.btnDecline.setTextColor(holder.itemView.getResources().getColor(R.color.organizer_blue));
        } else {
            holder.btnAccept.setBackgroundResource(R.drawable.bg_notif_accept_btn);
            holder.btnDecline.setTextColor(holder.itemView.getResources().getColor(R.color.notif_decline_text));
        }

        holder.buttonsRow.setVisibility(View.GONE);
        holder.badgeAccepted.setVisibility(View.GONE);
        holder.badgeDeclined.setVisibility(View.GONE);
        holder.btnAccept.setOnClickListener(null);
        holder.btnDecline.setOnClickListener(null);
        if (isActionableType(n)) {
            String normalizedResponse = normalizeResponse(n.getResponse());
            if (Notification.RESPONSE_PENDING.equals(normalizedResponse)) {
                holder.buttonsRow.setVisibility(View.VISIBLE);
                // Ensure these views actually receive touch events.
                holder.buttonsRow.setClickable(true);
                holder.btnAccept.setClickable(true);
                holder.btnAccept.setFocusable(true);
                holder.btnDecline.setClickable(true);
                holder.btnDecline.setFocusable(true);
                holder.btnAccept.setOnClickListener(v -> {
                    if (actionListener != null) actionListener.onAccept(n);
                });
                holder.btnDecline.setOnClickListener(v -> {
                    if (actionListener != null) actionListener.onReject(n);
                });
            } else if (Notification.RESPONSE_ACCEPTED.equals(normalizedResponse)) {
                holder.badgeAccepted.setVisibility(View.VISIBLE);
            } else if (Notification.RESPONSE_DECLINED.equals(normalizedResponse)) {
                holder.badgeDeclined.setVisibility(View.VISIBLE);
            } else {
                // Backward compatibility for older notifications missing `response`.
                String eventId = n.getEventId();
                String waitlistStatus = eventIdToStatus.get(eventId);
                String normalizedStatus = waitlistStatus != null
                        ? waitlistStatus.toLowerCase(Locale.US)
                        : WaitingList.STATUS_PENDING;
                if (WaitingList.STATUS_PENDING.equals(normalizedStatus)
                        || WaitingList.STATUS_SELECTED.equals(normalizedStatus)) {
                    holder.buttonsRow.setVisibility(View.VISIBLE);
                    holder.buttonsRow.setClickable(true);
                    holder.btnAccept.setClickable(true);
                    holder.btnAccept.setFocusable(true);
                    holder.btnDecline.setClickable(true);
                    holder.btnDecline.setFocusable(true);
                    holder.btnAccept.setOnClickListener(v -> {
                        if (actionListener != null) actionListener.onAccept(n);
                    });
                    holder.btnDecline.setOnClickListener(v -> {
                        if (actionListener != null) actionListener.onReject(n);
                    });
                } else if (WaitingList.STATUS_ENROLLED.equals(normalizedStatus)) {
                    holder.badgeAccepted.setVisibility(View.VISIBLE);
                } else if (WaitingList.STATUS_DECLINED.equals(normalizedStatus)
                        || WaitingList.STATUS_NOT_SELECTED.equals(normalizedStatus)) {
                    holder.badgeDeclined.setVisibility(View.VISIBLE);
                }
            }
        }
    }

    /**
     * Prefixes the stored message with {@code Event: &lt;name&gt;} using the resolved Firestore event name.
     */
    private String formatMessageWithEventLine(View itemView, Notification n) {
        String body = n.getMessage() != null ? n.getMessage() : "";
        String eventLabel;
        String eid = n.getEventId();
        if (eid == null || eid.isEmpty()) {
            eventLabel = itemView.getContext().getString(R.string.notification_event_unknown);
        } else {
            String resolved = eventIdToEventName.get(eid);
            if (resolved != null && !resolved.trim().isEmpty()) {
                eventLabel = resolved.trim();
            } else {
                eventLabel = itemView.getContext().getString(R.string.notification_event_unknown);
            }
        }
        return itemView.getContext().getString(R.string.notification_message_with_event, eventLabel, body);
    }

    private String normalizeResponse(String response) {
        if (response == null) return null;
        String normalized = response.toLowerCase(Locale.US);
        if (normalized.trim().isEmpty()) return null;
        return normalized;
    }

    private boolean isPrivateEventType(String type) {
        if (type == null) return false;
        String normalized = type.toLowerCase(Locale.US);
        return Notification.TYPE_PRIVATE_EVENT.equals(normalized)
                || "private".equals(normalized)
                || "private_event".equals(normalized);
    }

    private boolean isActionableType(Notification notification) {
        if (notification == null || notification.getType() == null) return false;
        String normalized = notification.getType().toLowerCase(Locale.US);
        return isPrivateEventType(normalized)
                || Notification.TYPE_SELECTED.equals(normalized)
                || Notification.TYPE_GOT_OFF_WAITLIST.equals(normalized)
                || (Notification.TYPE_CO_ORGANIZER.equals(normalized)
                && Notification.RECIPIENT_MODE_ORGANIZER.equals(notification.getRecipientMode()));
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
