package com.example.cobaltevents.ui;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.*;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static org.junit.Assert.assertEquals;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.example.cobaltevents.R;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class AccountSettingsActivityTest {

    private ActivityScenario<AccountSettingsActivity> scenario;

    @Before
    public void setUp() {
        Context context = ApplicationProvider.getApplicationContext();
        // Clear all relevant preferences to ensure test isolation
        context.getSharedPreferences("EntrantProfile", Context.MODE_PRIVATE).edit().clear().commit();
        context.getSharedPreferences("cobalt_prefs", Context.MODE_PRIVATE).edit().clear().commit();

        // Add initial profile data
        context.getSharedPreferences("EntrantProfile", Context.MODE_PRIVATE).edit()
                .putString("name", "Emmanuel Okusanya")
                .putString("email", "eokusa@gmail.com")
                .putString("phone", "5879216315")
                .commit();
    }

    @After
    public void tearDown() {
        if (scenario != null) {
            scenario.close();
        }
    }

    @Test
    public void display_profileInformation_isCorrect() {
        scenario = ActivityScenario.launch(AccountSettingsActivity.class);
        
        onView(withId(R.id.tv_name)).check(matches(withText("Emmanuel Okusanya")));
        onView(withId(R.id.tv_email)).check(matches(withText("eokusa@gmail.com")));
        onView(withId(R.id.tv_phone)).check(matches(withText("5879216315")));
    }

    @Test
    public void editProfile_saveChanges_updatesUIAndPrefs() {
        scenario = ActivityScenario.launch(AccountSettingsActivity.class);

        // Click Edit
        onView(withId(R.id.btn_edit_info)).perform(click());

        // Update fields
        onView(withId(R.id.edit_name)).perform(replaceText("Leo Sexyy"), closeSoftKeyboard());
        onView(withId(R.id.edit_email)).perform(replaceText("leooo@example.com"), closeSoftKeyboard());
        onView(withId(R.id.edit_phone)).perform(replaceText("5879216314"), closeSoftKeyboard());

        // Save
        onView(withId(R.id.btn_save_changes)).perform(click());

        // Verify UI
        onView(withId(R.id.tv_name)).check(matches(withText("Leo Sexyy")));
        onView(withId(R.id.tv_email)).check(matches(withText("leooo@example.com")));
        onView(withId(R.id.tv_phone)).check(matches(withText("5879216314")));

        // Verify Prefs
        Context context = ApplicationProvider.getApplicationContext();
        SharedPreferences prefs = context.getSharedPreferences("EntrantProfile", Context.MODE_PRIVATE);
        assertEquals("Leo Sexyy", prefs.getString("name", ""));
    }

    @Test
    public void editProfile_cancelChanges_doesNotUpdate() {
        scenario = ActivityScenario.launch(AccountSettingsActivity.class);

        onView(withId(R.id.btn_edit_info)).perform(click());
        onView(withId(R.id.edit_name)).perform(replaceText("Cancelled Name"), closeSoftKeyboard());
        onView(withId(R.id.btn_cancel_edit)).perform(click());

        onView(withId(R.id.tv_name)).check(matches(withText("Emmanuel Okusanya")));
    }

    @Test
    public void switchToOrganizerMode_savesOrganizerModeToPrefs() throws InterruptedException {
        scenario = ActivityScenario.launch(AccountSettingsActivity.class);

        // Scroll to and click organizer mode
        onView(withId(R.id.btn_organizer_mode)).perform(scrollTo(), click());

        // Wait for async Firestore check and SharedPreferences update
        Thread.sleep(2000); 

        Context context = ApplicationProvider.getApplicationContext();
        SharedPreferences prefs = context.getSharedPreferences("cobalt_prefs", Context.MODE_PRIVATE);
        assertEquals("organizer", prefs.getString("account_mode", "user"));
    }

    @Test
    public void userModeSelected_userNavBarIsInflated() {
        Context context = ApplicationProvider.getApplicationContext();
        context.getSharedPreferences("cobalt_prefs", Context.MODE_PRIVATE)
                .edit().putString("account_mode", "user").commit();

        scenario = ActivityScenario.launch(AccountSettingsActivity.class);

        // User mode has QR button
        onView(withId(R.id.nav_qr)).check(matches(isDisplayed()));
    }

    @Test
    public void organizerModeSelected_organizerNavBarIsInflated() {
        Context context = ApplicationProvider.getApplicationContext();
        context.getSharedPreferences("cobalt_prefs", Context.MODE_PRIVATE)
                .edit().putString("account_mode", "organizer").commit();

        scenario = ActivityScenario.launch(AccountSettingsActivity.class);

        // Organizer mode has Create (+) button
        onView(withId(R.id.nav_create)).check(matches(isDisplayed()));
    }
}
