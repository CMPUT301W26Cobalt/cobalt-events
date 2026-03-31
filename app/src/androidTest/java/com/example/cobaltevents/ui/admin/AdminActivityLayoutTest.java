package com.example.cobaltevents.ui.admin;

import static org.junit.Assert.*;

import android.content.Context;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.example.cobaltevents.R;

import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Layout-level checks for all Admin UI screens.
 * Inflates each layout without launching activities, avoiding Firebase dependencies.
 *
 * Covers:
 * - {@link AdminActivity}              → activity_admin.xml
 * - {@link AdminEventListActivity}     → activity_admin_list.xml
 * - {@link AdminProfileListActivity}   → activity_admin_list.xml
 * - {@link AdminImageListActivity}     → activity_admin_list.xml
 */
@RunWith(AndroidJUnit4.class)
public class AdminActivityLayoutTest {

    private Context themed() {
        Context base = ApplicationProvider.getApplicationContext();
        return new ContextThemeWrapper(base, R.style.Theme_CobaltEvents);
    }

    // ── AdminActivity (activity_admin.xml) ────────────────────────────────────

    @Test
    public void adminDashboard_hasCoreViews() {
        View root = LayoutInflater.from(themed())
                .inflate(R.layout.activity_admin, null, false);

        assertNotNull(root.findViewById(R.id.adminRecycler));
        assertNotNull(root.findViewById(R.id.etSearch));
        assertNotNull(root.findViewById(R.id.emptyMessage));
        assertNotNull(root.findViewById(R.id.loadingSpinner));
    }

    @Test
    public void adminDashboard_hasAllTabButtons() {
        View root = LayoutInflater.from(themed())
                .inflate(R.layout.activity_admin, null, false);

        assertNotNull(root.findViewById(R.id.tab_events));
        assertNotNull(root.findViewById(R.id.tab_profiles));
        assertNotNull(root.findViewById(R.id.tab_images));
        assertNotNull(root.findViewById(R.id.tab_organizers));
        assertNotNull(root.findViewById(R.id.tab_notifications));
    }

    @Test
    public void adminDashboard_hasTabLabels() {
        View root = LayoutInflater.from(themed())
                .inflate(R.layout.activity_admin, null, false);

        assertNotNull(root.findViewById(R.id.tabLabelEvents));
        assertNotNull(root.findViewById(R.id.tabLabelProfiles));
        assertNotNull(root.findViewById(R.id.tabLabelImages));
        assertNotNull(root.findViewById(R.id.tabLabelOrganizers));
        assertNotNull(root.findViewById(R.id.tabLabelNotifications));
    }

    @Test
    public void adminDashboard_hasSectionCountAndTitle() {
        View root = LayoutInflater.from(themed())
                .inflate(R.layout.activity_admin, null, false);

        assertNotNull(root.findViewById(R.id.tvSectionCount));
        assertNotNull(root.findViewById(R.id.tvSectionTitle));
    }

    @Test
    public void adminDashboard_searchField_isEditText() {
        View root = LayoutInflater.from(themed())
                .inflate(R.layout.activity_admin, null, false);
        assertTrue(root.findViewById(R.id.etSearch) instanceof EditText);
    }

    @Test
    public void adminDashboard_recycler_isRecyclerView() {
        View root = LayoutInflater.from(themed())
                .inflate(R.layout.activity_admin, null, false);
        assertTrue(root.findViewById(R.id.adminRecycler) instanceof RecyclerView);
    }

    @Test
    public void adminDashboard_hasScrollableTabBar() {
        View root = LayoutInflater.from(themed())
                .inflate(R.layout.activity_admin, null, false);
        assertNotNull(root.findViewById(R.id.adminTabsScroll));
    }

    // ── AdminEventListActivity / AdminProfileListActivity / AdminImageListActivity
    //    All use activity_admin_list.xml ──────────────────────────────────────

    @Test
    public void adminList_hasCoreViews() {
        View root = LayoutInflater.from(themed())
                .inflate(R.layout.activity_admin_list, null, false);

        assertNotNull(root.findViewById(R.id.adminListRecycler));
        assertNotNull(root.findViewById(R.id.adminListTitle));
    }

    @Test
    public void adminList_recycler_isRecyclerView() {
        View root = LayoutInflater.from(themed())
                .inflate(R.layout.activity_admin_list, null, false);
        assertTrue(root.findViewById(R.id.adminListRecycler) instanceof RecyclerView);
    }

    @Test
    public void adminList_title_isTextView() {
        View root = LayoutInflater.from(themed())
                .inflate(R.layout.activity_admin_list, null, false);
        assertTrue(root.findViewById(R.id.adminListTitle) instanceof TextView);
    }
}
