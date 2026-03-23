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
 * Layout-level checks for {@link NotificationsActivity}.
 * Covers the updated empty-state message.
 */
@RunWith(AndroidJUnit4.class)
public class NotificationsActivityTest {

    private Context themed() {
        Context base = ApplicationProvider.getApplicationContext();
        return new ContextThemeWrapper(base, R.style.Theme_CobaltEvents);
    }

    @Test
    public void layout_hasCoreViews() {
        View root = LayoutInflater.from(themed()).inflate(R.layout.activity_notifications, null, false);

        assertNotNull(root.findViewById(R.id.swipe_refresh_notifications));
        assertNotNull(root.findViewById(R.id.recycler_notifications));
        assertNotNull(root.findViewById(R.id.tv_empty_notifications));
    }

    @Test
    public void emptyState_hasCorrectTextAndIsHiddenByDefault() {
        View root = LayoutInflater.from(themed()).inflate(R.layout.activity_notifications, null, false);

        TextView tvEmpty = root.findViewById(R.id.tv_empty_notifications);
        assertNotNull(tvEmpty);
        assertEquals("No notifications to show", tvEmpty.getText().toString());
        assertEquals(View.GONE, tvEmpty.getVisibility());
    }

    @Test
    public void header_hasTitleText() {
        View root = LayoutInflater.from(themed()).inflate(R.layout.activity_notifications, null, false);
        TextView title = findFirstTextViewWithText(root, "Notifications");
        assertNotNull(title);
    }

    /** Organizer-themed layout: same ids as entrant screen for shared activity logic. */
    @Test
    public void organizerLayout_hasSameCoreViews() {
        View root = LayoutInflater.from(themed()).inflate(R.layout.activity_notifications_organizer, null, false);
        assertNotNull(root.findViewById(R.id.swipe_refresh_notifications));
        assertNotNull(root.findViewById(R.id.recycler_notifications));
        assertNotNull(root.findViewById(R.id.tv_empty_notifications));
    }

    private TextView findFirstTextViewWithText(View root, String text) {
        if (root instanceof TextView) {
            TextView tv = (TextView) root;
            if (tv.getText() != null && text.equals(tv.getText().toString())) {
                return tv;
            }
        }

        if (!(root instanceof android.view.ViewGroup)) return null;

        android.view.ViewGroup vg = (android.view.ViewGroup) root;
        for (int i = 0; i < vg.getChildCount(); i++) {
            View child = vg.getChildAt(i);
            TextView hit = findFirstTextViewWithText(child, text);
            if (hit != null) return hit;
        }
        return null;
    }
}

