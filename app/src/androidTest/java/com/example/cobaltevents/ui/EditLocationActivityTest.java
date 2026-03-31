package com.example.cobaltevents.ui;

import static org.junit.Assert.*;

import android.content.Context;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.example.cobaltevents.R;

import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Layout-level checks for {@link EditLocationActivity}.
 */
@RunWith(AndroidJUnit4.class)
public class EditLocationActivityTest {

    private Context themed() {
        Context base = ApplicationProvider.getApplicationContext();
        return new ContextThemeWrapper(base, R.style.Theme_CobaltEvents);
    }

    @Test
    public void layout_hasCoreViews() {
        View root = LayoutInflater.from(themed())
                .inflate(R.layout.activity_edit_location, null, false);

        assertNotNull(root.findViewById(R.id.edit_location_btn_close));
        assertNotNull(root.findViewById(R.id.edit_location_btn_save));
        assertNotNull(root.findViewById(R.id.edit_location_et));
    }

    @Test
    public void layout_hasMapPreviewButton() {
        View root = LayoutInflater.from(themed())
                .inflate(R.layout.activity_edit_location, null, false);
        assertNotNull(root.findViewById(R.id.edit_location_btn_preview_map));
    }

    @Test
    public void locationField_isEditText() {
        View root = LayoutInflater.from(themed())
                .inflate(R.layout.activity_edit_location, null, false);
        assertTrue(root.findViewById(R.id.edit_location_et) instanceof EditText);
    }

    @Test
    public void allButtons_areClickable() {
        View root = LayoutInflater.from(themed())
                .inflate(R.layout.activity_edit_location, null, false);
        assertTrue(root.findViewById(R.id.edit_location_btn_close).isClickable());
        assertTrue(root.findViewById(R.id.edit_location_btn_save).isClickable());
        assertTrue(root.findViewById(R.id.edit_location_btn_preview_map).isClickable());
    }
}
