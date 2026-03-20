package com.example.cobaltevents.ui;

import static org.junit.Assert.*;

import android.content.Context;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.example.cobaltevents.R;

import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Layout-level checks for {@link EventHistoryActivity} (My Events).
 * This avoids launching the Activity so tests don't depend on remote services.
 */
@RunWith(AndroidJUnit4.class)
public class EventHistoryActivityTest {

    private Context themed() {
        Context base = ApplicationProvider.getApplicationContext();
        return new ContextThemeWrapper(base, R.style.Theme_CobaltEvents);
    }

    @Test
    public void layout_hasCoreViews() {
        View root = LayoutInflater.from(themed()).inflate(R.layout.activity_event_history, null, false);

        assertNotNull(root.findViewById(R.id.tab_upcoming));
        assertNotNull(root.findViewById(R.id.tab_past));
        assertNotNull(root.findViewById(R.id.recycler_history));
        assertNotNull(root.findViewById(R.id.progress_bar));
        assertNotNull(root.findViewById(R.id.tv_empty));

        assertTrue(root.findViewById(R.id.progress_bar) instanceof ProgressBar);
    }

    @Test
    public void tabs_haveCorrectLabels() {
        View root = LayoutInflater.from(themed()).inflate(R.layout.activity_event_history, null, false);

        TextView tabUpcoming = root.findViewById(R.id.tab_upcoming);
        TextView tabPast = root.findViewById(R.id.tab_past);

        assertNotNull(tabUpcoming);
        assertNotNull(tabPast);
        assertEquals("Upcoming", tabUpcoming.getText().toString());
        assertEquals("Past Events", tabPast.getText().toString());
    }

    @Test
    public void emptyState_hasCorrectTextAndIsHiddenByDefault() {
        View root = LayoutInflater.from(themed()).inflate(R.layout.activity_event_history, null, false);

        TextView tvEmpty = root.findViewById(R.id.tv_empty);
        assertNotNull(tvEmpty);
        assertEquals("No events to show", tvEmpty.getText().toString());
        assertEquals(View.GONE, tvEmpty.getVisibility());
    }

    @Test
    public void header_hasTitleText() {
        View root = LayoutInflater.from(themed()).inflate(R.layout.activity_event_history, null, false);
        TextView title = findFirstTextViewWithText(root, "My Events");
        assertNotNull(title);
    }

    private TextView findFirstTextViewWithText(View root, String text) {
        if (root instanceof TextView) {
            TextView tv = (TextView) root;
            if (tv.getText() != null && text.equals(tv.getText().toString())) {
                return tv;
            }
        }

        if (!(root instanceof View)) return null;
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
