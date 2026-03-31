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
 * Layout-level checks for {@link WelcomeActivity}.
 */
@RunWith(AndroidJUnit4.class)
public class WelcomeActivityTest {

    private Context themed() {
        Context base = ApplicationProvider.getApplicationContext();
        return new ContextThemeWrapper(base, R.style.Theme_CobaltEvents);
    }

    @Test
    public void layout_hasBothContinueButtons() {
        View root = LayoutInflater.from(themed())
                .inflate(R.layout.activity_welcome, null, false);

        assertNotNull(root.findViewById(R.id.btn_continue_user));
        assertNotNull(root.findViewById(R.id.btn_continue_organizer));
    }

    @Test
    public void layout_hasWelcomeHeading() {
        View root = LayoutInflater.from(themed())
                .inflate(R.layout.activity_welcome, null, false);
        TextView heading = findFirstTextViewWithText(root, "Welcome!");
        assertNotNull("Expected a 'Welcome!' heading", heading);
    }

    @Test
    public void layout_hasUserRoleLabel() {
        View root = LayoutInflater.from(themed())
                .inflate(R.layout.activity_welcome, null, false);
        TextView label = findFirstTextViewWithText(root, "User");
        assertNotNull("Expected a 'User' label", label);
    }

    @Test
    public void layout_hasOrganizerRoleLabel() {
        View root = LayoutInflater.from(themed())
                .inflate(R.layout.activity_welcome, null, false);
        TextView label = findFirstTextViewWithText(root, "Organizer");
        assertNotNull("Expected an 'Organizer' label", label);
    }

    @Test
    public void continueAsUserButton_isClickable() {
        View root = LayoutInflater.from(themed())
                .inflate(R.layout.activity_welcome, null, false);
        View btn = root.findViewById(R.id.btn_continue_user);
        assertNotNull(btn);
        assertTrue(btn.isClickable());
    }

    @Test
    public void continueAsOrganizerButton_isClickable() {
        View root = LayoutInflater.from(themed())
                .inflate(R.layout.activity_welcome, null, false);
        View btn = root.findViewById(R.id.btn_continue_organizer);
        assertNotNull(btn);
        assertTrue(btn.isClickable());
    }

    private TextView findFirstTextViewWithText(View root, String text) {
        if (root instanceof TextView) {
            TextView tv = (TextView) root;
            if (tv.getText() != null && text.equals(tv.getText().toString())) return tv;
        }
        if (!(root instanceof android.view.ViewGroup)) return null;
        android.view.ViewGroup vg = (android.view.ViewGroup) root;
        for (int i = 0; i < vg.getChildCount(); i++) {
            TextView hit = findFirstTextViewWithText(vg.getChildAt(i), text);
            if (hit != null) return hit;
        }
        return null;
    }
}
