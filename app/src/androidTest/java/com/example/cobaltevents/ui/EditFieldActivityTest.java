package com.example.cobaltevents.ui;

import static org.junit.Assert.*;

import android.content.Context;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.example.cobaltevents.R;

import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Layout-level checks for {@link EditFieldActivity}.
 */
@RunWith(AndroidJUnit4.class)
public class EditFieldActivityTest {

    private Context themed() {
        Context base = ApplicationProvider.getApplicationContext();
        return new ContextThemeWrapper(base, R.style.Theme_CobaltEvents);
    }

    @Test
    public void layout_hasCoreViews() {
        View root = LayoutInflater.from(themed())
                .inflate(R.layout.activity_edit_field, null, false);

        assertNotNull(root.findViewById(R.id.edit_field_btn_close));
        assertNotNull(root.findViewById(R.id.edit_field_btn_save));
        assertNotNull(root.findViewById(R.id.edit_field_et_value));
        assertNotNull(root.findViewById(R.id.edit_field_tv_header_title));
        assertNotNull(root.findViewById(R.id.edit_field_tv_helper));
    }

    @Test
    public void valueField_isEditText() {
        View root = LayoutInflater.from(themed())
                .inflate(R.layout.activity_edit_field, null, false);
        assertTrue(root.findViewById(R.id.edit_field_et_value) instanceof EditText);
    }

    @Test
    public void headerTitle_isTextView() {
        View root = LayoutInflater.from(themed())
                .inflate(R.layout.activity_edit_field, null, false);
        assertTrue(root.findViewById(R.id.edit_field_tv_header_title) instanceof TextView);
    }

    @Test
    public void layout_hasScrollView() {
        View root = LayoutInflater.from(themed())
                .inflate(R.layout.activity_edit_field, null, false);
        assertNotNull(root.findViewById(R.id.edit_field_scroll));
    }
}
