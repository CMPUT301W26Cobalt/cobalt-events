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
public class EntrantActivityTest {

    private ActivityScenario<EntrantActivity> scenario;

    @Before
    public void setUp() {
        // Clear SharedPreferences before each test to ensure isolation
        Context context = ApplicationProvider.getApplicationContext();
        context.getSharedPreferences("EntrantProfile", Context.MODE_PRIVATE).edit().clear().commit();
    }

    @After
    public void tearDown() {
        if (scenario != null) {
            scenario.close();
        }
    }

    @Test
    public void save_withEmptyName_showsNameRequiredError() {
        scenario = ActivityScenario.launch(EntrantActivity.class);
        onView(withId(R.id.nameInput)).perform(replaceText(""), closeSoftKeyboard());
        onView(withId(R.id.emailInput))
                .perform(replaceText("test@example.com"), closeSoftKeyboard());

        onView(withId(R.id.saveButton)).perform(click());

        // TextInputLayout error text appears in the view hierarchy
        onView(withText("Name is required.")).check(matches(isDisplayed()));
    }

    @Test
    public void save_withEmptyEmail_showsEmailRequiredError() {
        scenario = ActivityScenario.launch(EntrantActivity.class);
        onView(withId(R.id.nameInput))
                .perform(replaceText("Emmanuel"), closeSoftKeyboard());
        onView(withId(R.id.emailInput)).perform(replaceText(""), closeSoftKeyboard());

        onView(withId(R.id.saveButton)).perform(click());

        onView(withText("Email is required.")).check(matches(isDisplayed()));
    }

    @Test
    public void save_withInvalidEmail_showsInvalidEmailError() {
        scenario = ActivityScenario.launch(EntrantActivity.class);
        onView(withId(R.id.nameInput))
                .perform(replaceText("Emmanuel"), closeSoftKeyboard());

        onView(withId(R.id.emailInput))
                .perform(replaceText("abc"), closeSoftKeyboard());

        onView(withId(R.id.saveButton)).perform(click());

        onView(withText("Invalid email format.")).check(matches(isDisplayed()));
    }

    @Test
    public void save_withInvalidPhone_showsInvalidPhoneError() {
        scenario = ActivityScenario.launch(EntrantActivity.class);
        onView(withId(R.id.nameInput))
                .perform(replaceText("Emmanuel"), closeSoftKeyboard());

        onView(withId(R.id.emailInput))
                .perform(replaceText("test@example.com"), closeSoftKeyboard());

        onView(withId(R.id.phoneInput))
                .perform(replaceText("abc###"), closeSoftKeyboard());

        onView(withId(R.id.saveButton)).perform(click());

        onView(withText("Invalid phone number.")).check(matches(isDisplayed()));
    }

    @Test
    public void save_withValidNameAndEmail_andBlankPhone_hasNoErrors() {
        scenario = ActivityScenario.launch(EntrantActivity.class);
        onView(withId(R.id.nameInput))
                .perform(replaceText("Emmanuel"), closeSoftKeyboard());

        onView(withId(R.id.emailInput))
                .perform(replaceText("test@example.com"), closeSoftKeyboard());

        onView(withId(R.id.phoneInput))
                .perform(replaceText(""), closeSoftKeyboard());

        onView(withId(R.id.saveButton)).perform(click());

        // Verify that the data was saved in SharedPreferences instead of checking views
        // as the activity finishes upon successful save.
        Context context = ApplicationProvider.getApplicationContext();
        SharedPreferences prefs = context.getSharedPreferences("EntrantProfile", Context.MODE_PRIVATE);
        assertEquals("Emmanuel", prefs.getString("name", ""));
        assertEquals("test@example.com", prefs.getString("email", ""));
        assertEquals("", prefs.getString("phone", ""));
    }
}
