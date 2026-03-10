package com.example.cobaltevents.ui.admin;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.cobaltevents.R;

import java.util.ArrayList;
import java.util.List;

public class AdminAdapter extends RecyclerView.Adapter<AdminAdapter.AdminViewHolder> {

    public interface OnRemoveClickListener {
        void onRemoveClick(AdminItem item);
    }

    public static class AdminItem {
        String id;
        String title;
        String subtitle;

        public AdminItem(String id, String title, String subtitle) {
            this.id = id;
            this.title = title;
            this.subtitle = subtitle;
        }
    }

    private final List<AdminItem> items;
    private boolean showRemoveButton = true;
    private final OnRemoveClickListener removeClickListener;

    public AdminAdapter(List<AdminItem> items, OnRemoveClickListener removeClickListener) {
        this.items = new ArrayList<>(items);
        this.removeClickListener = removeClickListener;
    }

    public void updateItems(List<AdminItem> newItems) {
        items.clear();
        items.addAll(newItems);
        notifyDataSetChanged();
    }

    public void setShowRemoveButton(boolean showRemoveButton) {
        this.showRemoveButton = showRemoveButton;
        notifyDataSetChanged();
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

        holder.title.setText(item.title);
        holder.subtitle.setText(item.subtitle);

        holder.deleteButton.setVisibility(showRemoveButton ? View.VISIBLE : View.GONE);
        holder.deleteButton.setOnClickListener(v -> {
            if (removeClickListener != null) {
                removeClickListener.onRemoveClick(item);
            }
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class AdminViewHolder extends RecyclerView.ViewHolder {
        TextView title;
        TextView subtitle;
        Button deleteButton;

        AdminViewHolder(@NonNull View itemView) {
            super(itemView);
            title = itemView.findViewById(R.id.adminItemTitle);
            subtitle = itemView.findViewById(R.id.adminItemSubtitle);
            deleteButton = itemView.findViewById(R.id.adminItemDeleteButton);
        }
    }
}