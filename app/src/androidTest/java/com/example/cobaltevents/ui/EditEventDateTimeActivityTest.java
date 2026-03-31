package com.example.cobaltevents.ui;

import static org.junit.Assert.*;

import android.content.Context;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.example.cobaltevents.R;

import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Layout-level checks for {@link EditEventDateTimeActivity}.
 */
@RunWith(AndroidJUnit4.class)
public class EditEventDateTimeActivityTest {

    private Context themed() {
        Context base = ApplicationProvider.getApplicationContext();
        return new ContextThemeWrapper(base, R.style.Theme_CobaltEvents);
    }

    @Test
    public void layout_hasCoreViews() {
        View root = LayoutInflater.from(themed())
                .inflate(R.layout.activity_edit_event_datetime, null, false);

        assertNotNull(root.findViewById(R.id.edit_dt_btn_close));
        assertNotNull(root.findViewById(R.id.edit_dt_btn_save));
        assertNotNull(root.findViewById(R.id.edit_dt_btn_pick_date));
        assertNotNull(root.findViewById(R.id.edit_dt_btn_pick_time));
    }

    @Test
    public void layout_hasTitleAndHelper() {
        View root = LayoutInflater.from(themed())
                .inflate(R.layout.activity_edit_event_datetime, null, false);

        assertNotNull(root.findViewById(R.id.edit_dt_tv_title));
        assertNotNull(root.findViewById(R.id.edit_dt_tv_helper));
    }

    @Test
    public void titleAndHelper_areTextViews() {
        View root = LayoutInflater.from(themed())
                .inflate(R.layout.activity_edit_event_datetime, null, false);
        assertTrue(root.findViewById(R.id.edit_dt_tv_title) instanceof TextView);
        assertTrue(root.findViewById(R.id.edit_dt_tv_helper) instanceof TextView);
    }

    @Test
    public void dateAndTimePickerButtons_areClickable() {
        View root = LayoutInflater.from(themed())
                .inflate(R.layout.activity_edit_event_datetime, null, false);
        assertTrue(root.findViewById(R.id.edit_dt_btn_pick_date).isClickable());
        assertTrue(root.findViewById(R.id.edit_dt_btn_pick_time).isClickable());
    }
}
