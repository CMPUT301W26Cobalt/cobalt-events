package com.example.cobaltevents.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.SharedPreferences;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.example.cobaltevents.R;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Layout-level and preference tests that do not launch the Activity.
 * This avoids remote dependencies during instrumentation.
 */
@RunWith(AndroidJUnit4.class)
public class AccountSettingsActivityTest {

    private Context themed() {
        Context base = ApplicationProvider.getApplicationContext();
        return new ContextThemeWrapper(base, R.style.Theme_CobaltEvents);
    }

    @Before
    public void seedPrefs() {
        Context context = ApplicationProvider.getApplicationContext();
        context.getSharedPreferences("EntrantProfile", Context.MODE_PRIVATE).edit()
                .putString("name", "Emmanuel Okusanya")
                .putString("email", "eokusa@gmail.com")
                .putString("phone", "5879216315")
                .commit();
        context.getSharedPreferences("cobalt_prefs", Context.MODE_PRIVATE).edit().clear().commit();
    }

    @Test
    public void layout_hasCoreViews() {
        View root = LayoutInflater.from(themed()).inflate(R.layout.activity_account_settings, null, false);
        assertNotNull(root.findViewById(R.id.tv_name));
        assertNotNull(root.findViewById(R.id.tv_email));
        assertNotNull(root.findViewById(R.id.tv_phone));
        assertNotNull(root.findViewById(R.id.btn_edit_info));
        assertNotNull(root.findViewById(R.id.btn_delete_account));
        assertNotNull(root.findViewById(R.id.switch_general));
        assertNotNull(root.findViewById(R.id.switch_event_updates));
    }

    @Test
    public void prefs_canBeUpdatedForGeneralNotifications() {
        Context context = ApplicationProvider.getApplicationContext();
        SharedPreferences prefs = context.getSharedPreferences("cobalt_prefs", Context.MODE_PRIVATE);
        prefs.edit().putBoolean("notification_general", false).commit();
        // Simulate toggle ON
        prefs.edit().putBoolean("notification_general", true).commit();
        assertTrue(prefs.getBoolean("notification_general", false));
    }
}
