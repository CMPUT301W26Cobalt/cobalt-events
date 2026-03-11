package com.example.cobaltevents.ui.admin;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.cobaltevents.R;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Adapter for the Admin RecyclerView.
 * Uses DiffUtil for performance — only changed rows are redrawn.
 * Supports rich card data: badge, detail line, meta row, image thumbnail.
 */
public class AdminAdapter extends RecyclerView.Adapter<AdminAdapter.AdminViewHolder> {

    public interface OnRemoveClickListener {
        void onRemoveClick(AdminItem item);
    }

    public interface OnViewClickListener {
        void onViewClick(AdminItem item);
    }

    // ── Data model ────────────────────────────────────────────────────────────

    public static class AdminItem {
        public final String id;
        public final String title;
        public final String subtitle;
        public final String badge;
        public final String badgeColor;
        public final String detail;
        public final String meta1;
        public final String meta2;
        public final String imageUrl;
        public final String avatarUrl;
        public final String initials;
        public final boolean isImageCard;
        public final boolean isEventCard;

        public AdminItem(String id, String title, String subtitle) {
            this(id, title, subtitle, null, null, null, null, null, null, null, null, false, false);
        }

        public AdminItem(String id, String title, String subtitle,
                         String badge, String badgeColor,
                         String detail, String meta1, String meta2, String imageUrl) {
            this(id, title, subtitle, badge, badgeColor, detail, meta1, meta2, imageUrl, null, null, false, false);
        }

        public AdminItem(String id, String title, String subtitle,
                         String badge, String badgeColor,
                         String detail, String meta1, String meta2, String imageUrl,
                         String avatarUrl, String initials) {
            this(id, title, subtitle, badge, badgeColor, detail, meta1, meta2, imageUrl, avatarUrl, initials, false, false);
        }

        public AdminItem(String id, String title, String subtitle,
                         String badge, String badgeColor,
                         String detail, String meta1, String meta2, String imageUrl,
                         String avatarUrl, String initials, boolean isImageCard) {
            this(id, title, subtitle, badge, badgeColor, detail, meta1, meta2, imageUrl, avatarUrl, initials, isImageCard, false);
        }

        public AdminItem(String id, String title, String subtitle,
                         String badge, String badgeColor,
                         String detail, String meta1, String meta2, String imageUrl,
                         String avatarUrl, String initials, boolean isImageCard, boolean isEventCard) {
            this.id = id;
            this.title = title;
            this.subtitle = subtitle;
            this.badge = badge;
            this.badgeColor = badgeColor;
            this.detail = detail;
            this.meta1 = meta1;
            this.meta2 = meta2;
            this.imageUrl = imageUrl;
            this.avatarUrl = avatarUrl;
            this.initials = initials;
            this.isImageCard = isImageCard;
            this.isEventCard = isEventCard;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof AdminItem)) return false;
            AdminItem that = (AdminItem) o;
            return Objects.equals(id, that.id)
                    && Objects.equals(title, that.title)
                    && Objects.equals(subtitle, that.subtitle)
                    && Objects.equals(badge, that.badge)
                    && Objects.equals(detail, that.detail)
                    && Objects.equals(meta1, that.meta1)
                    && Objects.equals(meta2, that.meta2)
                    && Objects.equals(imageUrl, that.imageUrl)
                    && Objects.equals(avatarUrl, that.avatarUrl)
                    && Objects.equals(initials, that.initials);
        }

        @Override
        public int hashCode() {
            return Objects.hash(id, title, subtitle, badge, detail, meta1, meta2, imageUrl, avatarUrl, initials);
        }
    }

    // ── DiffUtil ──────────────────────────────────────────────────────────────

    private static class AdminDiffCallback extends DiffUtil.Callback {
        private final List<AdminItem> oldList;
        private final List<AdminItem> newList;

        AdminDiffCallback(List<AdminItem> oldList, List<AdminItem> newList) {
            this.oldList = oldList;
            this.newList = newList;
        }

        @Override public int getOldListSize() { return oldList.size(); }
        @Override public int getNewListSize() { return newList.size(); }

        @Override
        public boolean areItemsTheSame(int oldPos, int newPos) {
            return Objects.equals(oldList.get(oldPos).id, newList.get(newPos).id);
        }

        @Override
        public boolean areContentsTheSame(int oldPos, int newPos) {
            return oldList.get(oldPos).equals(newList.get(newPos));
        }
    }

    // ── Adapter ───────────────────────────────────────────────────────────────

    private static final int VIEW_TYPE_DEFAULT = 0;
    private static final int VIEW_TYPE_IMAGE   = 1;
    private static final int VIEW_TYPE_EVENT   = 2;

    private List<AdminItem> items;
    private boolean showRemoveButton = true;
    private final OnRemoveClickListener removeListener;
    private final OnViewClickListener viewListener;

    public AdminAdapter(List<AdminItem> items,
                        OnRemoveClickListener removeListener,
                        OnViewClickListener viewListener) {
        this.items = new ArrayList<>(items);
        this.removeListener = removeListener;
        this.viewListener = viewListener;
    }

    public void updateItems(List<AdminItem> newItems) {
        List<AdminItem> newList = new ArrayList<>(newItems);
        DiffUtil.DiffResult result = DiffUtil.calculateDiff(new AdminDiffCallback(items, newList));
        items = newList;
        result.dispatchUpdatesTo(this);
    }

    public void setShowRemoveButton(boolean show) {
        if (this.showRemoveButton != show) {
            this.showRemoveButton = show;
            notifyItemRangeChanged(0, items.size());
        }
    }

    public List<AdminItem> getItems() {
        return items;
    }

    @Override
    public int getItemViewType(int position) {
        AdminItem item = items.get(position);
        if (item.isImageCard) return VIEW_TYPE_IMAGE;
        if (item.isEventCard) return VIEW_TYPE_EVENT;
        return VIEW_TYPE_DEFAULT;
    }

    @NonNull
    @Override
    public AdminViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == VIEW_TYPE_IMAGE) {
            return new ImageViewHolder(inflater.inflate(R.layout.item_admin_image, parent, false));
        }
        if (viewType == VIEW_TYPE_EVENT) {
            return new EventViewHolder(inflater.inflate(R.layout.item_admin_event, parent, false));
        }
        return new DefaultViewHolder(inflater.inflate(R.layout.item_admin, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull AdminViewHolder holder, int position) {
        AdminItem item = items.get(position);

        if (holder instanceof ImageViewHolder) {
            ImageViewHolder h = (ImageViewHolder) holder;
            h.eventName.setText(item.title != null ? item.title : "");
            h.organizerName.setText(item.subtitle != null ? item.subtitle : "");
            h.date.setText(item.detail != null ? item.detail : "");
            h.fileSize.setText(item.meta1 != null ? item.meta1 : "");
            if (item.imageUrl != null && !item.imageUrl.isEmpty()) {
                Glide.with(h.poster.getContext()).load(item.imageUrl).centerCrop()
                        .placeholder(android.R.color.darker_gray).into(h.poster);
            }
            h.itemView.setOnClickListener(v -> { if (viewListener != null) viewListener.onViewClick(item); });
            h.viewButton.setOnClickListener(v -> { if (viewListener != null) viewListener.onViewClick(item); });
            h.deleteButton.setOnClickListener(v -> { if (removeListener != null) removeListener.onRemoveClick(item); });
            return;
        }

        if (holder instanceof EventViewHolder) {
            EventViewHolder h = (EventViewHolder) holder;
            h.title.setText(item.title != null ? item.title : "");
            h.subtitle.setText(item.subtitle != null ? item.subtitle : "");

            // Badge
            if (item.badge != null && !item.badge.isEmpty()) {
                h.badge.setVisibility(View.VISIBLE);
                h.badge.setText(item.badge);
            } else {
                h.badge.setVisibility(View.GONE);
            }

            // Detail
            if (item.detail != null && !item.detail.isEmpty()) {
                h.detail.setVisibility(View.VISIBLE);
                h.detail.setText(item.detail);
            } else {
                h.detail.setVisibility(View.GONE);
            }

            // Meta
            if (item.meta1 != null || item.meta2 != null) {
                h.metaRow.setVisibility(View.VISIBLE);
                h.meta1.setText(item.meta1 != null ? item.meta1 : "");
                h.meta2.setText(item.meta2 != null ? item.meta2 : "");
            } else {
                h.metaRow.setVisibility(View.GONE);
            }

            // Background image
            if (item.imageUrl != null && !item.imageUrl.isEmpty()) {
                Glide.with(h.bg.getContext())
                        .load(item.imageUrl)
                        .centerCrop()
                        .placeholder(android.R.color.darker_gray)
                        .into(h.bg);
            }

            h.viewButton.setOnClickListener(v -> { if (viewListener != null) viewListener.onViewClick(item); });
            h.deleteButton.setOnClickListener(v -> { if (removeListener != null) removeListener.onRemoveClick(item); });
            h.itemView.setOnClickListener(v -> { if (viewListener != null) viewListener.onViewClick(item); });
            return;
        }

        DefaultViewHolder h = (DefaultViewHolder) holder;
        h.title.setText(item.title != null ? item.title : "");
        h.subtitle.setText(item.subtitle != null ? item.subtitle : "");

        // Badge
        if (item.badge != null && !item.badge.isEmpty()) {
            h.badge.setVisibility(View.VISIBLE);
            h.badge.setText(item.badge);
            if (item.badgeColor != null) {
                try { h.badge.setTextColor(android.graphics.Color.parseColor(item.badgeColor)); }
                catch (Exception ignored) {}
            }
        } else {
            h.badge.setVisibility(View.GONE);
        }

        // Detail line
        if (item.detail != null && !item.detail.isEmpty()) {
            h.detail.setVisibility(View.VISIBLE);
            h.detail.setText(item.detail);
        } else {
            h.detail.setVisibility(View.GONE);
        }

        // Meta row
        if (item.meta1 != null || item.meta2 != null) {
            h.metaRow.setVisibility(View.VISIBLE);
            h.meta1.setText(item.meta1 != null ? item.meta1 : "");
            h.meta2.setText(item.meta2 != null ? item.meta2 : "");
        } else {
            h.metaRow.setVisibility(View.GONE);
        }

        // Avatar
        if (item.initials != null || item.avatarUrl != null) {
            h.avatarContainer.setVisibility(View.VISIBLE);
            if (item.avatarUrl != null && !item.avatarUrl.isEmpty()) {
                h.initials.setVisibility(View.GONE);
                h.avatar.setVisibility(View.VISIBLE);
                Glide.with(h.avatar.getContext())
                        .load(item.avatarUrl)
                        .circleCrop()
                        .placeholder(android.R.color.darker_gray)
                        .into(h.avatar);
            } else {
                h.avatar.setVisibility(View.GONE);
                h.initials.setVisibility(View.VISIBLE);
                h.initials.setText(item.initials != null ? item.initials : "?");
            }
        } else {
            h.avatarContainer.setVisibility(View.GONE);
        }

        // Buttons
        h.deleteButton.setVisibility(showRemoveButton ? View.VISIBLE : View.GONE);
        h.deleteButton.setOnClickListener(v -> { if (removeListener != null) removeListener.onRemoveClick(item); });
        h.viewButton.setOnClickListener(v -> { if (viewListener != null) viewListener.onViewClick(item); });
        h.itemView.setOnClickListener(v -> { if (viewListener != null) viewListener.onViewClick(item); });
    }

    @Override
    public int getItemCount() { return items.size(); }

    static class AdminViewHolder extends RecyclerView.ViewHolder {
        AdminViewHolder(@NonNull View itemView) { super(itemView); }
    }

    static class DefaultViewHolder extends AdminViewHolder {
        TextView title, subtitle, badge, detail, meta1, meta2, initials;
        LinearLayout metaRow;
        ImageView image, avatar;
        android.widget.FrameLayout avatarContainer;
        ImageButton viewButton, deleteButton;

        DefaultViewHolder(@NonNull View itemView) {
            super(itemView);
            title           = itemView.findViewById(R.id.adminItemTitle);
            subtitle        = itemView.findViewById(R.id.adminItemSubtitle);
            badge           = itemView.findViewById(R.id.adminItemBadge);
            detail          = itemView.findViewById(R.id.adminItemDetail);
            meta1           = itemView.findViewById(R.id.adminItemMeta1);
            meta2           = itemView.findViewById(R.id.adminItemMeta2);
            metaRow         = itemView.findViewById(R.id.adminItemMetaRow);
            image           = itemView.findViewById(R.id.adminItemImage);
            avatarContainer = itemView.findViewById(R.id.adminItemAvatarContainer);
            avatar          = itemView.findViewById(R.id.adminItemAvatar);
            initials        = itemView.findViewById(R.id.adminItemInitials);
            viewButton      = itemView.findViewById(R.id.adminItemViewButton);
            deleteButton    = itemView.findViewById(R.id.adminItemDeleteButton);
        }
    }

    static class EventViewHolder extends AdminViewHolder {
        ImageView bg;
        TextView title, subtitle, badge, detail, meta1, meta2;
        LinearLayout metaRow;
        ImageButton viewButton, deleteButton;

        EventViewHolder(@NonNull View itemView) {
            super(itemView);
            bg           = itemView.findViewById(R.id.eventCardBg);
            title        = itemView.findViewById(R.id.adminItemTitle);
            subtitle     = itemView.findViewById(R.id.adminItemSubtitle);
            badge        = itemView.findViewById(R.id.adminItemBadge);
            detail       = itemView.findViewById(R.id.adminItemDetail);
            meta1        = itemView.findViewById(R.id.adminItemMeta1);
            meta2        = itemView.findViewById(R.id.adminItemMeta2);
            metaRow      = itemView.findViewById(R.id.adminItemMetaRow);
            viewButton   = itemView.findViewById(R.id.adminItemViewButton);
            deleteButton = itemView.findViewById(R.id.adminItemDeleteButton);
        }
    }

    static class ImageViewHolder extends AdminViewHolder {
        ImageView poster;
        TextView eventName, organizerName, date, fileSize;
        ImageButton viewButton, deleteButton;

        ImageViewHolder(@NonNull View itemView) {
            super(itemView);
            poster        = itemView.findViewById(R.id.imgPoster);
            eventName     = itemView.findViewById(R.id.imgEventName);
            organizerName = itemView.findViewById(R.id.imgOrganizerName);
            date          = itemView.findViewById(R.id.imgDate);
            fileSize      = itemView.findViewById(R.id.imgFileSize);
            viewButton    = itemView.findViewById(R.id.imgViewButton);
            deleteButton  = itemView.findViewById(R.id.imgDeleteButton);
        }
    }
}