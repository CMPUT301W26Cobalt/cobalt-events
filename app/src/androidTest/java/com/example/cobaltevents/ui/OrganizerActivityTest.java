package com.example.cobaltevents.ui;

import static org.junit.Assert.*;

import android.content.Context;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
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
 * Layout-level checks for {@link OrganizerActivity}.
 * Avoids launching the Activity to remove dependency on Firebase.
 */
@RunWith(AndroidJUnit4.class)
public class OrganizerActivityTest {

    private Context themed() {
        Context base = ApplicationProvider.getApplicationContext();
        return new ContextThemeWrapper(base, R.style.Theme_CobaltEvents);
    }

    @Test
    public void layout_hasCoreViews() {
        View root = LayoutInflater.from(themed())
                .inflate(R.layout.activity_organizer, null, false);

        assertNotNull(root.findViewById(R.id.recycler_organizer_events));
        assertNotNull(root.findViewById(R.id.swipe_refresh_organizer));
        assertNotNull(root.findViewById(R.id.progress_bar));
        assertNotNull(root.findViewById(R.id.tv_empty));
        assertNotNull(root.findViewById(R.id.et_organizer_search));
    }

    @Test
    public void recycler_isRecyclerViewInstance() {
        View root = LayoutInflater.from(themed())
                .inflate(R.layout.activity_organizer, null, false);
        assertTrue(root.findViewById(R.id.recycler_organizer_events) instanceof RecyclerView);
    }

    @Test
    public void swipeRefresh_isSwipeRefreshLayoutInstance() {
        View root = LayoutInflater.from(themed())
                .inflate(R.layout.activity_organizer, null, false);
        assertTrue(root.findViewById(R.id.swipe_refresh_organizer) instanceof SwipeRefreshLayout);
    }

    @Test
    public void progressBar_isProgressBarInstance() {
        View root = LayoutInflater.from(themed())
                .inflate(R.layout.activity_organizer, null, false);
        assertTrue(root.findViewById(R.id.progress_bar) instanceof ProgressBar);
    }

    @Test
    public void searchField_isEditTextInstance() {
        View root = LayoutInflater.from(themed())
                .inflate(R.layout.activity_organizer, null, false);
        assertTrue(root.findViewById(R.id.et_organizer_search) instanceof EditText);
    }

    @Test
    public void emptyState_isTextViewAndHiddenByDefault() {
        View root = LayoutInflater.from(themed())
                .inflate(R.layout.activity_organizer, null, false);
        View tvEmpty = root.findViewById(R.id.tv_empty);
        assertTrue(tvEmpty instanceof TextView);
        assertEquals(View.GONE, tvEmpty.getVisibility());
    }
}
