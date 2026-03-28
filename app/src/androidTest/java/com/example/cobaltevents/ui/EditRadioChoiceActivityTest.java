package com.example.cobaltevents.ui;

import static org.junit.Assert.*;

import android.content.Context;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.RadioGroup;
import android.widget.TextView;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.example.cobaltevents.R;

import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Layout-level checks for {@link EditRadioChoiceActivity}.
 */
@RunWith(AndroidJUnit4.class)
public class EditRadioChoiceActivityTest {

    private Context themed() {
        Context base = ApplicationProvider.getApplicationContext();
        return new ContextThemeWrapper(base, R.style.Theme_CobaltEvents);
    }

    @Test
    public void layout_hasCoreViews() {
        View root = LayoutInflater.from(themed())
                .inflate(R.layout.activity_edit_radio_choice, null, false);

        assertNotNull(root.findViewById(R.id.edit_radio_btn_close));
        assertNotNull(root.findViewById(R.id.edit_radio_btn_save));
        assertNotNull(root.findViewById(R.id.edit_radio_group));
        assertNotNull(root.findViewById(R.id.edit_radio_tv_title));
        assertNotNull(root.findViewById(R.id.edit_radio_tv_helper));
    }

    @Test
    public void radioGroup_isRadioGroupInstance() {
        View root = LayoutInflater.from(themed())
                .inflate(R.layout.activity_edit_radio_choice, null, false);
        assertTrue(root.findViewById(R.id.edit_radio_group) instanceof RadioGroup);
    }

    @Test
    public void titleAndHelper_areTextViews() {
        View root = LayoutInflater.from(themed())
                .inflate(R.layout.activity_edit_radio_choice, null, false);
        assertTrue(root.findViewById(R.id.edit_radio_tv_title) instanceof TextView);
        assertTrue(root.findViewById(R.id.edit_radio_tv_helper) instanceof TextView);
    }
}
