package com.example.cobaltevents;

import static org.junit.Assert.*;

import android.content.Context;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.example.cobaltevents.R;

import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Updated for the new notifications behavior:
 * notifications are informational-only; Accept/Decline buttons and badges are hidden.
 */
@RunWith(AndroidJUnit4.class)
public class LotteryInvitationTest {

    private Context themed() {
        Context base = ApplicationProvider.getApplicationContext();
        return new ContextThemeWrapper(base, R.style.Theme_CobaltEvents);
    }

    @Test
    public void notification_card_actionsAndBadgesAreHidden() {
        View root = LayoutInflater.from(themed()).inflate(R.layout.item_notification_card, null, false);

        assertNotNull(root.findViewById(R.id.notification_card_root));
        assertNotNull(root.findViewById(R.id.title));
        assertNotNull(root.findViewById(R.id.message));
        assertNotNull(root.findViewById(R.id.date));

        // Buttons/badges should be hidden in the current UI.
        assertNotNull(root.findViewById(R.id.buttons_row));
        assertEquals(View.GONE, root.findViewById(R.id.buttons_row).getVisibility());

        assertNotNull(root.findViewById(R.id.badge_accepted));
        assertEquals(View.GONE, root.findViewById(R.id.badge_accepted).getVisibility());

        assertNotNull(root.findViewById(R.id.badge_declined));
        assertEquals(View.GONE, root.findViewById(R.id.badge_declined).getVisibility());

        assertNotNull(root.findViewById(R.id.btn_accept));
        assertNotNull(root.findViewById(R.id.btn_decline));
    }
}
