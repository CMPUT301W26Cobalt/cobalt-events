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

        public AdminItem(String id, String title, String subtitle) {
            this(id, title, subtitle, null, null, null, null, null, null, null, null);
        }

        public AdminItem(String id, String title, String subtitle,
                         String badge, String badgeColor,
                         String detail, String meta1, String meta2, String imageUrl) {
            this(id, title, subtitle, badge, badgeColor, detail, meta1, meta2, imageUrl, null, null);
        }

        public AdminItem(String id, String title, String subtitle,
                         String badge, String badgeColor,
                         String detail, String meta1, String meta2, String imageUrl,
                         String avatarUrl, String initials) {
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

    @NonNull
    @Override
    public AdminViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_admin, parent, false);
        return new AdminViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull AdminViewHolder holder, int position) {
        AdminItem item = items.get(position);

        holder.title.setText(item.title != null ? item.title : "");
        holder.subtitle.setText(item.subtitle != null ? item.subtitle : "");

        // Badge (status / notification type)
        if (item.badge != null && !item.badge.isEmpty()) {
            holder.badge.setVisibility(View.VISIBLE);
            holder.badge.setText(item.badge);
            if (item.badgeColor != null) {
                try {
                    holder.badge.setTextColor(android.graphics.Color.parseColor(item.badgeColor));
                } catch (Exception ignored) {}
            }
        } else {
            holder.badge.setVisibility(View.GONE);
        }

        // Detail line
        if (item.detail != null && !item.detail.isEmpty()) {
            holder.detail.setVisibility(View.VISIBLE);
            holder.detail.setText(item.detail);
        } else {
            holder.detail.setVisibility(View.GONE);
        }

        // Meta row (waitlist count, price, recipient count etc)
        if (item.meta1 != null || item.meta2 != null) {
            holder.metaRow.setVisibility(View.VISIBLE);
            holder.meta1.setText(item.meta1 != null ? item.meta1 : "");
            holder.meta2.setText(item.meta2 != null ? item.meta2 : "");
        } else {
            holder.metaRow.setVisibility(View.GONE);
        }

        // Avatar (Profiles/Organizers tabs)
        if (item.initials != null || item.avatarUrl != null) {
            holder.avatarContainer.setVisibility(View.VISIBLE);
            if (item.avatarUrl != null && !item.avatarUrl.isEmpty()) {
                holder.initials.setVisibility(View.GONE);
                holder.avatar.setVisibility(View.VISIBLE);
                Glide.with(holder.avatar.getContext())
                        .load(item.avatarUrl)
                        .circleCrop()
                        .placeholder(android.R.color.darker_gray)
                        .into(holder.avatar);
            } else {
                holder.avatar.setVisibility(View.GONE);
                holder.initials.setVisibility(View.VISIBLE);
                holder.initials.setText(item.initials != null ? item.initials : "?");
            }
        } else {
            holder.avatarContainer.setVisibility(View.GONE);
        }

        // Image thumbnail (Images tab)
        if (item.imageUrl != null && !item.imageUrl.isEmpty()) {
            holder.image.setVisibility(View.VISIBLE);
            Glide.with(holder.image.getContext())
                    .load(item.imageUrl)
                    .centerCrop()
                    .placeholder(android.R.color.darker_gray)
                    .into(holder.image);
        } else {
            holder.image.setVisibility(View.GONE);
        }

        // Buttons
        holder.deleteButton.setVisibility(showRemoveButton ? View.VISIBLE : View.GONE);
        holder.deleteButton.setOnClickListener(v -> {
            if (removeListener != null) removeListener.onRemoveClick(item);
        });
        holder.viewButton.setOnClickListener(v -> {
            if (viewListener != null) viewListener.onViewClick(item);
        });
    }

    @Override
    public int getItemCount() { return items.size(); }

    static class AdminViewHolder extends RecyclerView.ViewHolder {
        TextView title, subtitle, badge, detail, meta1, meta2, initials;
        LinearLayout metaRow;
        ImageView image, avatar;
        android.widget.FrameLayout avatarContainer;
        ImageButton viewButton, deleteButton;

        AdminViewHolder(@NonNull View itemView) {
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
}