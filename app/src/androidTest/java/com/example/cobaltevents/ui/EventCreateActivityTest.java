package com.example.cobaltevents.ui;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.clearText;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.closeSoftKeyboard;
import static androidx.test.espresso.action.ViewActions.scrollTo;
import static androidx.test.espresso.action.ViewActions.typeText;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.hasErrorText;
import static androidx.test.espresso.matcher.ViewMatchers.isClickable;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withEffectiveVisibility;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;

import androidx.test.espresso.matcher.ViewMatchers.Visibility;
import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.example.cobaltevents.R;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class EventCreateActivityTest {

    @Rule
    public ActivityScenarioRule<EventCreateActivity> rule =
            new ActivityScenarioRule<>(EventCreateActivity.class);

    // ── Layout presence ──────────────────────────────────────────────────────

    @Test
    public void backButton_isDisplayed() {
        onView(withId(R.id.btn_back)).check(matches(isDisplayed()));
    }

    @Test
    public void titleField_isDisplayed() {
        onView(withId(R.id.et_title)).check(matches(isDisplayed()));
    }

    @Test
    public void descriptionField_isDisplayed() {
        onView(withId(R.id.et_description)).check(matches(isDisplayed()));
    }

    @Test
    public void locationField_isDisplayed() {
        onView(withId(R.id.et_location)).check(matches(isDisplayed()));
    }

    @Test
    public void capacityField_isDisplayed() {
        onView(withId(R.id.et_capacity)).perform(scrollTo()).check(matches(isDisplayed()));
    }

    @Test
    public void priceField_isDisplayed() {
        onView(withId(R.id.et_price)).perform(scrollTo()).check(matches(isDisplayed()));
    }

    @Test
    public void categorySpinner_isDisplayed() {
        onView(withId(R.id.spinner_category)).perform(scrollTo()).check(matches(isDisplayed()));
    }

    @Test
    public void selectionCriteriaField_isDisplayed() {
        onView(withId(R.id.et_selection_criteria)).perform(scrollTo()).check(matches(isDisplayed()));
    }

    @Test
    public void qrPlaceholder_isDisplayed() {
        onView(withId(R.id.qr_placeholder)).perform(scrollTo()).check(matches(isDisplayed()));
    }

    @Test
    public void qrCodeImage_isInitiallyGone() {
        onView(withId(R.id.iv_qr_code))
                .check(matches(withEffectiveVisibility(Visibility.GONE)));
    }

    @Test
    public void createEventButton_isDisplayed() {
        onView(withId(R.id.btn_create_event)).perform(scrollTo()).check(matches(isDisplayed()));
    }

    @Test
    public void imageUploadContainer_isDisplayed() {
        onView(withId(R.id.image_upload_container)).check(matches(isDisplayed()));
    }

    // ── Date / time picker buttons ───────────────────────────────────────────

    @Test
    public void pickDateButton_isClickable() {
        onView(withId(R.id.btn_pick_date)).check(matches(isClickable()));
    }

    @Test
    public void pickTimeButton_isClickable() {
        onView(withId(R.id.btn_pick_time)).check(matches(isClickable()));
    }

    @Test
    public void pickRegOpenButton_isClickable() {
        onView(withId(R.id.btn_pick_reg_open)).perform(scrollTo()).check(matches(isClickable()));
    }

    @Test
    public void pickDeadlineButton_isClickable() {
        onView(withId(R.id.btn_pick_deadline)).perform(scrollTo()).check(matches(isClickable()));
    }

    // ── Input acceptance ─────────────────────────────────────────────────────

    @Test
    public void titleField_acceptsTextInput() {
        onView(withId(R.id.et_title))
                .perform(typeText("Community Yoga Sessions"), closeSoftKeyboard());
        onView(withId(R.id.et_title)).check(matches(withText("Community Yoga Sessions")));
    }

    @Test
    public void descriptionField_acceptsTextInput() {
        onView(withId(R.id.et_description))
                .perform(typeText("A relaxing outdoor yoga session"), closeSoftKeyboard());
        onView(withId(R.id.et_description))
                .check(matches(withText("A relaxing outdoor yoga session")));
    }

    @Test
    public void locationField_acceptsTextInput() {
        onView(withId(R.id.et_location))
                .perform(typeText("Community Center Room 205"), closeSoftKeyboard());
        onView(withId(R.id.et_location))
                .check(matches(withText("Community Center Room 205")));
    }

    @Test
    public void capacityField_acceptsNumericInput() {
        onView(withId(R.id.et_capacity))
                .perform(scrollTo(), typeText("50"), closeSoftKeyboard());
        onView(withId(R.id.et_capacity)).check(matches(withText("50")));
    }

    @Test
    public void priceField_acceptsTextInput() {
        onView(withId(R.id.et_price))
                .perform(scrollTo(), typeText("Free"), closeSoftKeyboard());
        onView(withId(R.id.et_price)).check(matches(withText("Free")));
    }

    @Test
    public void titleField_clearText_becomesEmpty() {
        onView(withId(R.id.et_title))
                .perform(typeText("Something"), clearText(), closeSoftKeyboard());
        onView(withId(R.id.et_title)).check(matches(withText("")));
    }

    // ── Validation ───────────────────────────────────────────────────────────

    @Test
    public void createEvent_emptyTitle_showsError() {
        onView(withId(R.id.btn_create_event)).perform(scrollTo(), click());
        onView(withId(R.id.et_title))
                .check(matches(hasErrorText("Event title is required")));
    }

    @Test
    public void createEvent_emptyLocation_showsError() {
        onView(withId(R.id.et_title))
                .perform(typeText("Tech Meetup"), closeSoftKeyboard());
        onView(withId(R.id.btn_create_event)).perform(scrollTo(), click());
        onView(withId(R.id.et_location))
                .check(matches(hasErrorText("Location is required")));
    }

    @Test
    public void capacityField_filtersNonNumericInput() {
        // inputType="number" means letters are rejected by the system keyboard
        onView(withId(R.id.et_capacity))
                .perform(scrollTo(), typeText("abc"), closeSoftKeyboard());
        // Non-numeric characters are filtered, so the field should be empty
        onView(withId(R.id.et_capacity)).check(matches(withText("")));
    }
}
