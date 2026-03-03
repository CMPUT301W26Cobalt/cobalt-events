package com.example.cobaltevents.ui;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.example.cobaltevents.R;
import com.example.cobaltevents.controller.EntrantController;
import com.example.cobaltevents.controller.LotteryController;
import com.example.cobaltevents.db.EventDB;
import com.example.cobaltevents.db.WaitingListDB;
import com.example.cobaltevents.model.Event;
import com.example.cobaltevents.model.WaitingList;

import java.util.List;

/**
 * Organizer screen for managing an event. Contains the "Draw Lottery" button.
 * US 01.04.01: "Draw Lottery" triggers "selected" notifications.
 * US 01.04.02: "Draw Lottery" triggers "not_selected" notifications.
 */
public class EventManageActivity extends AppCompatActivity {

    private static final String TAG = "EventManageActivity";

    private EventDB eventDB;
    private WaitingListDB waitingListDB;
    private LotteryController lotteryController;
    private Event currentEvent;
    private String eventId;

    private TextView tvEventTitle;
    private TextView tvEventDescription;
    private TextView tvWaitingCount;
    private TextView tvMaxEntrants;
    private TextView tvLotteryStatus;
    private Button btnDrawLottery;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_event_manage);

        eventDB = new EventDB();
        waitingListDB = new WaitingListDB();
        lotteryController = new LotteryController(this);

        tvEventTitle = findViewById(R.id.tv_manage_event_title);
        tvEventDescription = findViewById(R.id.tv_manage_event_description);
        tvWaitingCount = findViewById(R.id.tv_waiting_count);
        tvMaxEntrants = findViewById(R.id.tv_max_entrants);
        tvLotteryStatus = findViewById(R.id.tv_lottery_status);
        btnDrawLottery = findViewById(R.id.btn_draw_lottery);

        // Get eventId from intent
        eventId = getIntent().getStringExtra("eventId");

        if (eventId != null) {
            loadEvent();
        } else {
            // If no eventId, show a message (in a real app, this would list organizer's events)
            tvEventTitle.setText("No event selected");
            btnDrawLottery.setEnabled(false);
        }

        btnDrawLottery.setOnClickListener(v -> confirmAndDrawLottery());
    }

    private void loadEvent() {
        eventDB.getEvent(eventId, event -> {
            if (event != null) {
                currentEvent = event;
                displayEvent(event);
                loadWaitingCount();
            }
        }, e -> Log.e(TAG, "Failed to load event", e));
    }

    private void displayEvent(Event event) {
        tvEventTitle.setText(event.getTitle());
        tvEventDescription.setText(event.getDescription());
        tvMaxEntrants.setText("Max entrants: " + event.getMaxEntrants());

        if (event.isLotteryDrawn()) {
            btnDrawLottery.setEnabled(false);
            tvLotteryStatus.setText("Lottery already drawn");
            tvLotteryStatus.setVisibility(View.VISIBLE);
        }
    }

    private void loadWaitingCount() {
        waitingListDB.getWaitingListByEvent(eventId, entries -> {
            int waitingCount = 0;
            for (WaitingList entry : entries) {
                if (WaitingList.STATUS_WAITING.equals(entry.getStatus())) {
                    waitingCount++;
                }
            }
            tvWaitingCount.setText("Waiting list: " + waitingCount + " entrants");
        }, e -> Log.e(TAG, "Failed to load waiting count", e));
    }

    /**
     * Shows a confirmation dialog before drawing the lottery.
     * On confirm, triggers LotteryController which sends notifications for US 01.04.01 and US 01.04.02.
     */
    private void confirmAndDrawLottery() {
        if (currentEvent == null) return;

        new AlertDialog.Builder(this)
                .setTitle(R.string.lottery_confirm_title)
                .setMessage(R.string.lottery_confirm_message)
                .setPositiveButton("Draw", (dialog, which) -> drawLottery())
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void drawLottery() {
        btnDrawLottery.setEnabled(false);

        lotteryController.drawLottery(currentEvent, new LotteryController.OnLotteryCompleteListener() {
            @Override
            public void onComplete(List<String> selectedIds, List<String> notSelectedIds) {
                runOnUiThread(() -> {
                    tvLotteryStatus.setText("Lottery drawn! " + selectedIds.size() + " selected, "
                            + notSelectedIds.size() + " not selected.");
                    tvLotteryStatus.setVisibility(View.VISIBLE);
                    Toast.makeText(EventManageActivity.this,
                            "Lottery drawn! Notifications sent.", Toast.LENGTH_LONG).show();
                });
            }

            @Override
            public void onError(Exception e) {
                runOnUiThread(() -> {
                    Log.e(TAG, "Lottery draw failed", e);
                    btnDrawLottery.setEnabled(true);
                    Toast.makeText(EventManageActivity.this,
                            "Lottery failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        });
    }
}
