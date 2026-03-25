package com.example.cobaltevents.ui.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.cobaltevents.R;
import com.example.cobaltevents.model.WaitingList;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class WaitlistEntrantAdapter extends RecyclerView.Adapter<WaitlistEntrantAdapter.ViewHolder> {

    private List<WaitingList> items = new ArrayList<>();
    private Map<String, String> names = new HashMap<>();
    private static final SimpleDateFormat DATE_FORMAT =
            new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());

    public void setItems(List<WaitingList> items) {
        this.items = items != null ? items : new ArrayList<>();
        notifyDataSetChanged();
    }

    public void setNames(Map<String, String> names) {
        this.names = names != null ? names : new HashMap<>();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_waitlist_entrant, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        WaitingList reg = items.get(position);
        String name = names.getOrDefault(reg.getDeviceId(), "Unknown");

        holder.tvName.setText(name);

        if (reg.getEmail() != null && !reg.getEmail().isEmpty()) {
            holder.tvEmail.setText(reg.getEmail());
            holder.tvEmail.setVisibility(View.VISIBLE);
        } else {
            holder.tvEmail.setVisibility(View.GONE);
        }

        if (reg.getPhone() != null && !reg.getPhone().isEmpty()) {
            holder.tvPhone.setText(reg.getPhone());
            holder.tvPhone.setVisibility(View.VISIBLE);
        } else {
            holder.tvPhone.setVisibility(View.GONE);
        }

        if (reg.getLatitude() != null && reg.getLongitude() != null) {
            holder.tvLocation.setVisibility(View.VISIBLE);
            holder.tvLocation.setText(holder.itemView.getContext().getString(
                    R.string.event_manage_entrant_location,
                    reg.getLatitude(), reg.getLongitude()));
        } else {
            holder.tvLocation.setVisibility(View.GONE);
        }

        if (reg.getRegisteredAt() != null) {
            holder.tvJoined.setVisibility(View.VISIBLE);
            holder.tvJoined.setText(holder.itemView.getContext().getString(
                    R.string.event_manage_entrant_joined,
                    DATE_FORMAT.format(reg.getRegisteredAt().toDate())));
        } else {
            holder.tvJoined.setVisibility(View.GONE);
        }
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvName, tvEmail, tvPhone, tvLocation, tvJoined;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.tv_entrant_name);
            tvEmail = itemView.findViewById(R.id.tv_entrant_email);
            tvPhone = itemView.findViewById(R.id.tv_entrant_phone);
            tvLocation = itemView.findViewById(R.id.tv_entrant_location);
            tvJoined = itemView.findViewById(R.id.tv_joined_date);
        }
    }
}
