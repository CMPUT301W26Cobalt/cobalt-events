package com.example.cobaltevents.ui;

import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.example.cobaltevents.R;
import com.example.cobaltevents.db.EntrantDB;
import com.example.cobaltevents.db.EventDB;
import com.example.cobaltevents.model.Event;
import com.example.cobaltevents.ui.adapter.OrganizerEventAdapter;

import java.util.ArrayList;
import java.util.List;

public class OrganizerActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private ProgressBar progressBar;
    private TextView tvEmpty;
    private SwipeRefreshLayout swipeRefreshLayout;
    private OrganizerEventAdapter adapter;
    private EventDB eventDB;
    private String deviceId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (!new EntrantDB(this).getEntrant().isValid()) {
            Intent i = new Intent(this, EntrantActivity.class);
            i.putExtra(EntrantActivity.EXTRA_LAUNCH_ORGANIZER_AFTER_SIGNUP, true);
            startActivity(i);
            finish();
            return;
        }

        setContentView(R.layout.activity_organizer);

        deviceId = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);
        eventDB = new EventDB();

        recyclerView = findViewById(R.id.recycler_organizer_events);
        progressBar = findViewById(R.id.progress_bar);
        tvEmpty = findViewById(R.id.tv_empty);
        swipeRefreshLayout = findViewById(R.id.swipe_refresh_organizer);
        if (swipeRefreshLayout != null) {
            swipeRefreshLayout.setOnRefreshListener(() -> loadOrganizerEvents(true));
            swipeRefreshLayout.setColorSchemeColors(ContextCompat.getColor(this, R.color.organizer_blue));
        }

        adapter = new OrganizerEventAdapter(new ArrayList<>());
        adapter.setOnManageClickListener(this::openManageEvent);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);

        loadOrganizerEvents(false);
        setupBottomNavigation();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadOrganizerEvents(false);
    }

    /**
     * @param fromPullToRefresh when true, use swipe indicator only (no center {@link ProgressBar})
     */
    private void loadOrganizerEvents(boolean fromPullToRefresh) {
        if (!fromPullToRefresh) {
            progressBar.setVisibility(View.VISIBLE);
            if (swipeRefreshLayout != null) {
                swipeRefreshLayout.setRefreshing(false);
            }
        }
        tvEmpty.setVisibility(View.GONE);

        eventDB.getEventsByOrganizer(deviceId,
                events -> {
                    progressBar.setVisibility(View.GONE);
                    if (swipeRefreshLayout != null) {
                        swipeRefreshLayout.setRefreshing(false);
                    }
                    List<Event> list = events != null ? events : new ArrayList<>();
                    adapter.updateEvents(list);
                    tvEmpty.setVisibility(list.isEmpty() ? View.VISIBLE : View.GONE);
                },
                e -> {
                    progressBar.setVisibility(View.GONE);
                    if (swipeRefreshLayout != null) {
                        swipeRefreshLayout.setRefreshing(false);
                    }
                    Toast.makeText(this, "Failed to load events: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    private void openManageEvent(Event event) {
        Intent intent = new Intent(this, EventManageActivity.class);
        intent.putExtra("eventId", event.getEventId());
        startActivity(intent);
    }

    private void setupBottomNavigation() {
        setDashboardTabActive();

        findViewById(R.id.nav_create).setOnClickListener(v ->
                startActivity(new Intent(this, EventCreateActivity.class)));

        findViewById(R.id.nav_notifications).setOnClickListener(v ->
                startActivity(new Intent(this, NotificationsActivity.class)
                        .putExtra(NotificationsActivity.EXTRA_FROM_ORGANIZER, true)));

        findViewById(R.id.nav_account).setOnClickListener(v ->
                startActivity(new Intent(this, AccountSettingsActivity.class)
                        .putExtra(NotificationsActivity.EXTRA_FROM_ORGANIZER, true)));
    }

    private void setDashboardTabActive() {
        int active = ContextCompat.getColor(this, R.color.organizer_blue);
        int inactive = ContextCompat.getColor(this, R.color.grey_nav_inactive);

        tintNavItem(R.id.iv_nav_notifications, R.id.tv_nav_notifications, inactive);
        tintNavItem(R.id.iv_nav_account, R.id.tv_nav_account, inactive);
        tintNavItem(R.id.iv_nav_dashboard, R.id.tv_nav_dashboard, active);
    }

    private void tintNavItem(int iconId, int textId, int color) {
        ImageView icon = findViewById(iconId);
        TextView text = findViewById(textId);
        if (icon != null) icon.setColorFilter(color);
        if (text != null) text.setTextColor(color);
    }
}
