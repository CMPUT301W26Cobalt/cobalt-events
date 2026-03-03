package com.example.cobaltevents.ui;



import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.*;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.*;

import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.example.cobaltevents.R;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class EntrantActivityTest {

    @Rule
    public ActivityScenarioRule<EntrantActivity> rule =
            new ActivityScenarioRule<>(EntrantActivity.class);

    @Test
    public void save_withEmptyName_showsNameRequiredError() {
        onView(withId(R.id.emailInput))
                .perform(typeText("test@example.com"), closeSoftKeyboard());

        onView(withId(R.id.saveButton)).perform(click());

        // TextInputLayout error text appears in the view hierarchy
        onView(withText("Name is required.")).check(matches(isDisplayed()));
    }

    @Test
    public void save_withEmptyEmail_showsEmailRequiredError() {
        onView(withId(R.id.nameInput))
                .perform(typeText("Emmanuel"), closeSoftKeyboard());

        onView(withId(R.id.saveButton)).perform(click());

        onView(withText("Email is required.")).check(matches(isDisplayed()));
    }

    @Test
    public void save_withInvalidEmail_showsInvalidEmailError() {
        onView(withId(R.id.nameInput))
                .perform(typeText("Emmanuel"), closeSoftKeyboard());

        onView(withId(R.id.emailInput))
                .perform(typeText("abc"), closeSoftKeyboard());

        onView(withId(R.id.saveButton)).perform(click());

        onView(withText("Invalid email format.")).check(matches(isDisplayed()));
    }

    @Test
    public void save_withInvalidPhone_showsInvalidPhoneError() {
        onView(withId(R.id.nameInput))
                .perform(typeText("Emmanuel"), closeSoftKeyboard());

        onView(withId(R.id.emailInput))
                .perform(typeText("test@example.com"), closeSoftKeyboard());

        onView(withId(R.id.phoneInput))
                .perform(typeText("abc###"), closeSoftKeyboard());

        onView(withId(R.id.saveButton)).perform(click());

        onView(withText("Invalid phone number.")).check(matches(isDisplayed()));
    }

    @Test
    public void save_withValidNameAndEmail_andBlankPhone_hasNoErrors() {
        onView(withId(R.id.nameInput))
                .perform(typeText("Emmanuel"), closeSoftKeyboard());

        onView(withId(R.id.emailInput))
                .perform(typeText("test@example.com"), closeSoftKeyboard());

        onView(withId(R.id.phoneInput))
                .perform(replaceText(""), closeSoftKeyboard());

        onView(withId(R.id.saveButton)).perform(click());



        onView(withText("Name is required.")).check(matches(withEffectiveVisibility(Visibility.GONE)));
        onView(withText("Email is required.")).check(matches(withEffectiveVisibility(Visibility.GONE)));
        onView(withText("Invalid email format.")).check(matches(withEffectiveVisibility(Visibility.GONE)));
        onView(withText("Invalid phone number.")).check(matches(withEffectiveVisibility(Visibility.GONE)));
    }
}