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
 * Layout-level checks for {@link QRScanActivity}.
 * These tests avoid launching the Activity to reduce dependency on remote services.
 */
@RunWith(AndroidJUnit4.class)
public class QRScanActivityTest {

    private Context themed() {
        Context base = ApplicationProvider.getApplicationContext();
        return new ContextThemeWrapper(base, R.style.Theme_CobaltEvents);
    }

    @Test
    public void layout_hasCoreViews() {
        View root = LayoutInflater.from(themed()).inflate(R.layout.activity_qr_scan, null, false);

        assertNotNull(root.findViewById(R.id.btn_simulate_qr));
        assertNotNull(root.findViewById(R.id.edit_event_code));
        assertNotNull(root.findViewById(R.id.btn_go_to_event));
    }

    @Test
    public void header_hasTitleText() {
        View root = LayoutInflater.from(themed()).inflate(R.layout.activity_qr_scan, null, false);
        TextView title = findFirstTextViewWithText(root, "Scan QR Code");
        assertNotNull(title);
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

