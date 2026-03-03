package com.example.cobaltevents.ui;

import android.os.Bundle;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.example.cobaltevents.R;
import com.example.cobaltevents.db.EventDB;
import com.example.cobaltevents.model.Event;

/**
 * Displays detailed information about a selected event.
 * US 01.04.01 criteria 4: Notification links to this activity via eventId intent extra.
 */
public class EventDetailActivity extends AppCompatActivity {

    private ImageView eventPoster;
    private TextView eventName;
    private TextView eventDescription;
    private TextView eventLocation;

    private EventDB eventDB;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_event_detail);

        eventPoster = findViewById(R.id.eventPoster);
        eventName = findViewById(R.id.eventName);
        eventDescription = findViewById(R.id.eventDescription);
        eventLocation = findViewById(R.id.eventLocation);

        eventDB = new EventDB();

        // Get eventId passed from previous activity or notification tap
        String eventId = getIntent().getStringExtra("eventId");

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

                        eventName.setText(event.getName());
                        eventDescription.setText(event.getDescription());
                        eventLocation.setText(event.getLocation());

                        if (event.getPosterImageUrl() != null) {
                            Glide.with(this)
                                    .load(event.getPosterImageUrl())
                                    .into(eventPoster);
                        }
                    }
                },
                e -> e.printStackTrace()
        );
    }
}
