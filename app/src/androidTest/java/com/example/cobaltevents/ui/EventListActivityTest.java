package com.example.cobaltevents.ui;

import static org.junit.Assert.assertNotNull;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ContextThemeWrapper;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.example.cobaltevents.R;

import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Layout-level checks that avoid launching the Activity (removes dependency on remote services).
 * We inflate the layouts and assert that critical views exist.
 */
@RunWith(AndroidJUnit4.class)
public class EventListActivityTest {

    private Context themed() {
        Context base = ApplicationProvider.getApplicationContext();
        return new ContextThemeWrapper(base, R.style.Theme_CobaltEvents);
    }

    @Test
    public void activityLayout_hasSearchFilterAndRecycler() {
        View root = LayoutInflater.from(themed()).inflate(R.layout.activity_event_list, null, false);
        assertNotNull(root.findViewById(R.id.et_search));
        assertNotNull(root.findViewById(R.id.btn_filter));
        assertNotNull(root.findViewById(R.id.recycler_events));
    }

    @Test
    public void filterDialogLayout_hasAllSpinnersAndButtons() {
        View dialog = LayoutInflater.from(themed()).inflate(R.layout.dialog_filter_events, null, false);
        assertNotNull(dialog.findViewById(R.id.spinner_category));
        assertNotNull(dialog.findViewById(R.id.spinner_age_group));
        assertNotNull(dialog.findViewById(R.id.btn_availability_start));
        assertNotNull(dialog.findViewById(R.id.btn_availability_end));
        assertNotNull(dialog.findViewById(R.id.spinner_capacity));
        assertNotNull(dialog.findViewById(R.id.spinner_price_range));
        assertNotNull(dialog.findViewById(R.id.btn_apply_filters));
        assertNotNull(dialog.findViewById(R.id.btn_clear_filters));
        assertNotNull(dialog.findViewById(R.id.btn_close_filter));
    }
}
