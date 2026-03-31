package com.example.cobaltevents.ui;

import static org.junit.Assert.*;

import android.content.Context;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.RadioGroup;
import android.widget.TextView;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.example.cobaltevents.R;

import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Layout-level checks for {@link JoinWaitlistActivity}.
 * Inflates the layout to verify all form fields and buttons are present
 * without launching the Activity or touching Firebase.
 */
@RunWith(AndroidJUnit4.class)
public class JoinWaitlistActivityTest {

    private Context themed() {
        Context base = ApplicationProvider.getApplicationContext();
        return new ContextThemeWrapper(base, R.style.Theme_CobaltEvents);
    }

    @Test
    public void layout_hasCoreViews() {
        View root = LayoutInflater.from(themed())
                .inflate(R.layout.activity_join_waitlist, null, false);

        assertNotNull(root.findViewById(R.id.btn_back));
        assertNotNull(root.findViewById(R.id.btn_confirm_join));
        assertNotNull(root.findViewById(R.id.tv_event_name));
    }

    @Test
    public void layout_hasFormFields() {
        View root = LayoutInflater.from(themed())
                .inflate(R.layout.activity_join_waitlist, null, false);

        assertNotNull(root.findViewById(R.id.edit_email));
        assertNotNull(root.findViewById(R.id.edit_phone));
        assertNotNull(root.findViewById(R.id.edit_participants));
    }

    @Test
    public void layout_hasFormFieldLayouts() {
        View root = LayoutInflater.from(themed())
                .inflate(R.layout.activity_join_waitlist, null, false);

        assertNotNull(root.findViewById(R.id.layout_email));
        assertNotNull(root.findViewById(R.id.layout_phone));
        assertNotNull(root.findViewById(R.id.layout_participants));
    }

    @Test
    public void layout_hasNotificationRadioOptions() {
        View root = LayoutInflater.from(themed())
                .inflate(R.layout.activity_join_waitlist, null, false);

        assertNotNull(root.findViewById(R.id.radio_notification));
        assertNotNull(root.findViewById(R.id.radio_email));
        assertNotNull(root.findViewById(R.id.radio_phone));
        assertNotNull(root.findViewById(R.id.radio_push));
    }

    @Test
    public void emailField_isEditText() {
        View root = LayoutInflater.from(themed())
                .inflate(R.layout.activity_join_waitlist, null, false);
        assertTrue(root.findViewById(R.id.edit_email) instanceof EditText);
    }

    @Test
    public void eventNameField_isTextView() {
        View root = LayoutInflater.from(themed())
                .inflate(R.layout.activity_join_waitlist, null, false);
        assertTrue(root.findViewById(R.id.tv_event_name) instanceof TextView);
    }
}
