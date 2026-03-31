package com.example.cobaltevents.ui;

import static org.junit.Assert.*;

import android.content.Context;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.example.cobaltevents.R;

import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Layout-level checks for {@link EventManageActivity}.
 * Inflates the layout and verifies critical views exist without launching
 * the Activity or touching Firebase.
 */
@RunWith(AndroidJUnit4.class)
public class EventManageActivityTest {

    private Context themed() {
        Context base = ApplicationProvider.getApplicationContext();
        return new ContextThemeWrapper(base, R.style.Theme_CobaltEvents);
    }

    // ── Core navigation / header views ──────────────────────────────────────

    @Test
    public void layout_hasBackButtonAndPoster() {
        View root = LayoutInflater.from(themed())
                .inflate(R.layout.activity_event_manage, null, false);

        assertNotNull(root.findViewById(R.id.btn_back));
        assertNotNull(root.findViewById(R.id.iv_event_poster));
    }

    // ── Event info views ────────────────────────────────────────────────────

    @Test
    public void layout_hasEventInfoTextViews() {
        View root = LayoutInflater.from(themed())
                .inflate(R.layout.activity_event_manage, null, false);

        assertNotNull(root.findViewById(R.id.tv_event_name));
        assertNotNull(root.findViewById(R.id.tv_event_date));
        assertNotNull(root.findViewById(R.id.tv_event_location));
        assertNotNull(root.findViewById(R.id.tv_event_capacity));
        assertNotNull(root.findViewById(R.id.tv_event_registration));
    }

    // ── Attendee count badges ────────────────────────────────────────────────

    @Test
    public void layout_hasAttendeeCounts() {
        View root = LayoutInflater.from(themed())
                .inflate(R.layout.activity_event_manage, null, false);

        assertNotNull(root.findViewById(R.id.tv_count_waitlisted));
        assertNotNull(root.findViewById(R.id.tv_count_invited));
        assertNotNull(root.findViewById(R.id.tv_count_confirmed));
    }

    // ── Main tab bar ────────────────────────────────────────────────────────

    @Test
    public void layout_hasMainTabBar() {
        View root = LayoutInflater.from(themed())
                .inflate(R.layout.activity_event_manage, null, false);

        assertNotNull(root.findViewById(R.id.event_manage_main_tab_bar));
        assertNotNull(root.findViewById(R.id.tab_main_waitlist));
        assertNotNull(root.findViewById(R.id.tab_main_organizers));
        assertNotNull(root.findViewById(R.id.tab_main_comments));
    }

    // ── Waitlist sub-tabs ───────────────────────────────────────────────────

    @Test
    public void layout_hasWaitlistSubtabs() {
        View root = LayoutInflater.from(themed())
                .inflate(R.layout.activity_event_manage, null, false);

        assertNotNull(root.findViewById(R.id.tab_waitlisted));
        assertNotNull(root.findViewById(R.id.tab_invited));
        assertNotNull(root.findViewById(R.id.tab_confirmed));
        assertNotNull(root.findViewById(R.id.tab_declined));
        assertNotNull(root.findViewById(R.id.tab_replacement));
        assertNotNull(root.findViewById(R.id.tab_map));
        assertNotNull(root.findViewById(R.id.tab_qr_code));
    }

    // ── Action buttons ──────────────────────────────────────────────────────

    @Test
    public void layout_hasActionButtons() {
        View root = LayoutInflater.from(themed())
                .inflate(R.layout.activity_event_manage, null, false);

        assertNotNull(root.findViewById(R.id.btn_edit_event));
        assertNotNull(root.findViewById(R.id.btn_run_lottery));
        assertNotNull(root.findViewById(R.id.btn_export_csv));
    }

    // ── Content panels ──────────────────────────────────────────────────────

    @Test
    public void layout_hasContentPanels() {
        View root = LayoutInflater.from(themed())
                .inflate(R.layout.activity_event_manage, null, false);

        assertNotNull(root.findViewById(R.id.panel_user_waitlist));
        assertNotNull(root.findViewById(R.id.panel_organizers));
        assertNotNull(root.findViewById(R.id.panel_comments));
    }

    // ── Entrant recycler + progress + empty state ────────────────────────────

    @Test
    public void layout_hasRecyclerProgressEmpty() {
        View root = LayoutInflater.from(themed())
                .inflate(R.layout.activity_event_manage, null, false);

        assertNotNull(root.findViewById(R.id.recycler_entrants));
        assertNotNull(root.findViewById(R.id.progress_bar));
        assertNotNull(root.findViewById(R.id.tv_empty));
    }

    @Test
    public void recycler_isRecyclerViewInstance() {
        View root = LayoutInflater.from(themed())
                .inflate(R.layout.activity_event_manage, null, false);
        assertTrue(root.findViewById(R.id.recycler_entrants) instanceof RecyclerView);
    }

    @Test
    public void progressBar_isProgressBarInstance() {
        View root = LayoutInflater.from(themed())
                .inflate(R.layout.activity_event_manage, null, false);
        assertTrue(root.findViewById(R.id.progress_bar) instanceof ProgressBar);
    }

    // ── Geolocation switch ──────────────────────────────────────────────────

    @Test
    public void layout_hasGeolocationSwitch() {
        View root = LayoutInflater.from(themed())
                .inflate(R.layout.activity_event_manage, null, false);
        assertNotNull(root.findViewById(R.id.switch_geolocation));
    }

    // ── Private invite section ──────────────────────────────────────────────

    @Test
    public void layout_hasPrivateInviteSection() {
        View root = LayoutInflater.from(themed())
                .inflate(R.layout.activity_event_manage, null, false);

        assertNotNull(root.findViewById(R.id.tv_private_badge));
        assertNotNull(root.findViewById(R.id.layout_private_invite_section));
        assertNotNull(root.findViewById(R.id.btn_send_private_invites));
    }
}
