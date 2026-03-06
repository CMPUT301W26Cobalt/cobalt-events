package com.example.cobaltevents.ui;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.*;
import static androidx.test.espresso.assertion.ViewAssertions.doesNotExist;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.*;

import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.example.cobaltevents.R;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class EventListActivityTest {

    @Rule
    public ActivityScenarioRule<EventListActivity> rule =
            new ActivityScenarioRule<>(EventListActivity.class);

    // ── Layout presence ──────────────────────────────────────────────────────

    @Test
    public void searchBar_isDisplayed() {
        onView(withId(R.id.et_search)).check(matches(isDisplayed()));
    }

    @Test
    public void filterButton_isDisplayed() {
        onView(withId(R.id.btn_filter)).check(matches(isDisplayed()));
    }

    @Test
    public void recyclerView_isDisplayed() {
        onView(withId(R.id.recycler_events)).check(matches(isDisplayed()));
    }

    // ── Search bar ───────────────────────────────────────────────────────────

    @Test
    public void searchBar_acceptsTextInput() {
        onView(withId(R.id.et_search))
                .perform(typeText("music"), closeSoftKeyboard());

        onView(withId(R.id.et_search))
                .check(matches(withText("music")));
    }

    @Test
    public void searchBar_clearsText() {
        onView(withId(R.id.et_search))
                .perform(typeText("music"), clearText(), closeSoftKeyboard());

        onView(withId(R.id.et_search))
                .check(matches(withText("")));
    }

    // ── Filter dialog opens ──────────────────────────────────────────────────

    @Test
    public void filterButton_click_opensFilterDialog() {
        onView(withId(R.id.btn_filter)).perform(click());

        onView(withText("Filter Events")).check(matches(isDisplayed()));
    }

    @Test
    public void filterDialog_showsCategorySpinner() {
        onView(withId(R.id.btn_filter)).perform(click());

        onView(withId(R.id.spinner_category)).check(matches(isDisplayed()));
    }

    @Test
    public void filterDialog_showsDateSpinner() {
        onView(withId(R.id.btn_filter)).perform(click());

        onView(withId(R.id.spinner_date)).check(matches(isDisplayed()));
    }

    @Test
    public void filterDialog_showsAvailabilitySpinner() {
        onView(withId(R.id.btn_filter)).perform(click());

        onView(withId(R.id.spinner_availability)).check(matches(isDisplayed()));
    }

    @Test
    public void filterDialog_showsApplyButton() {
        onView(withId(R.id.btn_filter)).perform(click());

        onView(withId(R.id.btn_apply_filters)).check(matches(isDisplayed()));
    }

    @Test
    public void filterDialog_showsClearButton() {
        onView(withId(R.id.btn_filter)).perform(click());

        onView(withId(R.id.btn_clear_filters)).check(matches(isDisplayed()));
    }

    // ── Filter dialog dismissal ──────────────────────────────────────────────

    @Test
    public void filterDialog_closeButton_dismissesDialog() {
        onView(withId(R.id.btn_filter)).perform(click());
        onView(withText("Filter Events")).check(matches(isDisplayed()));

        onView(withId(R.id.btn_close_filter)).perform(click());

        onView(withText("Filter Events")).check(doesNotExist());
    }

    @Test
    public void filterDialog_applyFilters_dismissesDialog() {
        onView(withId(R.id.btn_filter)).perform(click());

        onView(withId(R.id.btn_apply_filters)).perform(click());

        onView(withText("Filter Events")).check(doesNotExist());
    }

    @Test
    public void filterDialog_clearFilters_dismissesDialog() {
        onView(withId(R.id.btn_filter)).perform(click());

        onView(withId(R.id.btn_clear_filters)).perform(click());

        onView(withText("Filter Events")).check(doesNotExist());
    }
}
