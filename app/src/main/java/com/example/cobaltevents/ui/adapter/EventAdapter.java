package com.example.cobaltevents.ui.adapter;

import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.example.cobaltevents.model.Event;
import java.util.List;

/**
 * TODO: Implement EventAdapter for RecyclerView
 */
public class EventAdapter extends RecyclerView.Adapter<EventAdapter.EventViewHolder> {

    // TODO: Add fields (events list, click listener)

    public EventAdapter(List<Event> events) {
        // TODO: Initialize
    }

    @NonNull
    @Override
    public EventViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        // TODO: Inflate item layout and return ViewHolder
        return null;
    }

    @Override
    public void onBindViewHolder(@NonNull EventViewHolder holder, int position) {
        // TODO: Bind event data to views
    }

    @Override
    public int getItemCount() {
        return 0; // TODO: Return events.size()
    }

    static class EventViewHolder extends RecyclerView.ViewHolder {
        // TODO: Add view references

        EventViewHolder(@NonNull android.view.View itemView) {
            super(itemView);
            // TODO: findViewById
        }
    }
}
