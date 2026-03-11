package com.example.cobaltevents.ui;

import android.content.Intent;
import android.os.Bundle;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.example.cobaltevents.R;
import com.example.cobaltevents.db.EventDB;
import com.example.cobaltevents.model.Event;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

/**
 * Displays detailed information about a selected event.
 * US 01.01.01: Add button in bottom right opens JoinWaitlistActivity.
 */
public class EventDetailActivity extends AppCompatActivity {

    private ImageView eventPoster;
    private TextView eventName;
    private TextView eventDescription;
    private TextView eventLocation;

    private EventDB eventDB;
    private Event currentEvent;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_event_detail);

        eventPoster = findViewById(R.id.eventPoster);
        eventName = findViewById(R.id.eventName);
        eventDescription = findViewById(R.id.eventDescription);
        eventLocation = findViewById(R.id.eventLocation);

        eventDB = new EventDB();

        String eventId = getIntent().getStringExtra("eventId");

        FloatingActionButton fabAdd = findViewById(R.id.fab_add_waitlist);
        fabAdd.setOnClickListener(v -> {
            if (currentEvent != null) {
                Intent intent = new Intent(this, JoinWaitlistActivity.class);
                intent.putExtra("eventId", currentEvent.getEventId());
                intent.putExtra("eventName", currentEvent.getName());
                startActivityForResult(intent, 1001);
            }
        });

        if (eventId != null) {
            loadEvent(eventId);
        }
    }

    /**
     * Loads the event from Firestore and updates the UI.
     */
    private void loadEvent(String eventId) {

        eventDB.getEvent(eventId,
                event -> {
                    if (event != null) {
                        currentEvent = event;
                        eventName.setText(event.getName());
                        eventDescription.setText(event.getDescription());
                        eventLocation.setText(event.getLocation());

                        if (event.getPosterImageUrl() != null && !event.getPosterImageUrl().isEmpty()) {
                            Glide.with(this)
                                    .load(event.getPosterImageUrl())
                                    .placeholder(android.R.drawable.ic_menu_gallery)
                                    .into(eventPoster);
                            eventPoster.setVisibility(android.view.View.VISIBLE);
                        } else {
                            eventPoster.setVisibility(android.view.View.VISIBLE);
                            eventPoster.setImageResource(android.R.drawable.ic_menu_gallery);
                        }
                    }
                },
                e -> e.printStackTrace()
        );
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 1001 && resultCode == RESULT_OK) {
            finish();
        }
    }
}