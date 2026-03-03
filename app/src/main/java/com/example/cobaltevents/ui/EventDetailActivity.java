package com.example.cobaltevents.ui;

import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.cobaltevents.R;
import com.example.cobaltevents.controller.EntrantController;
import com.example.cobaltevents.db.EventDB;
import com.example.cobaltevents.db.WaitingListDB;
import com.example.cobaltevents.model.Event;
import com.example.cobaltevents.model.WaitingList;

import java.text.SimpleDateFormat;
import java.util.Locale;

/**
 * Displays event details. Target screen when user taps a notification.
 * US 01.04.01 criteria 4: Notification links to event details (this activity).
 */
public class EventDetailActivity extends AppCompatActivity {

    private static final String TAG = "EventDetailActivity";

    private EventDB eventDB;
    private WaitingListDB waitingListDB;
    private String eventId;
    private String deviceId;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("MMM d, yyyy", Locale.getDefault());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_event_detail);

        eventDB = new EventDB();
        waitingListDB = new WaitingListDB();
        deviceId = EntrantController.getDeviceId(this);

        // Get eventId from intent (from notification tap or event list)
        eventId = getIntent().getStringExtra("eventId");

        if (eventId != null) {
            loadEvent();
        } else {
            Toast.makeText(this, "No event specified", Toast.LENGTH_SHORT).show();
            finish();
        }

        // Join waiting list button
        Button btnJoin = findViewById(R.id.btn_join_waiting_list);
        btnJoin.setOnClickListener(v -> joinWaitingList());
    }

    private void loadEvent() {
        eventDB.getEvent(eventId, event -> {
            if (event != null) {
                displayEvent(event);
            } else {
                Toast.makeText(this, "Event not found", Toast.LENGTH_SHORT).show();
            }
        }, e -> {
            Log.e(TAG, "Failed to load event", e);
            Toast.makeText(this, R.string.error_generic, Toast.LENGTH_SHORT).show();
        });
    }

    private void displayEvent(Event event) {
        TextView tvTitle = findViewById(R.id.tv_event_title);
        TextView tvDescription = findViewById(R.id.tv_event_description);
        TextView tvDate = findViewById(R.id.tv_event_date);

        tvTitle.setText(event.getTitle());
        tvDescription.setText(event.getDescription());
        if (event.getDate() != null) {
            tvDate.setText(dateFormat.format(event.getDate()));
        }
    }

    private void joinWaitingList() {
        WaitingList entry = new WaitingList(eventId, deviceId, WaitingList.STATUS_WAITING);
        waitingListDB.addToWaitingList(entry,
                unused -> Toast.makeText(this, "Joined waiting list!", Toast.LENGTH_SHORT).show(),
                e -> {
                    Log.e(TAG, "Failed to join waiting list", e);
                    Toast.makeText(this, R.string.error_generic, Toast.LENGTH_SHORT).show();
                }
        );
    }
}
