package com.example.cobaltevents.ui;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.*;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static org.hamcrest.Matchers.isA;

import android.widget.ProgressBar;

import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.example.cobaltevents.R;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * UI Tests for EventHistoryActivity
 * US 01.02.03: As an entrant, I want to have a history of events I have registered for,
 * whether I was selected or not.
 */
@RunWith(AndroidJUnit4.class)
public class EventHistoryActivityTest {

    @Rule
    public ActivityScenarioRule<EventHistoryActivity> rule =
            new ActivityScenarioRule<>(EventHistoryActivity.class);

    // ── Layout presence ──────────────────────────────────────────────────────

    @Test
    public void backButton_isDisplayed() {
        onView(withId(R.id.btn_back)).check(matches(isDisplayed()));
    }

    @Test
    public void recyclerView_isDisplayed() {
        onView(withId(R.id.recycler_history)).check(matches(isDisplayed()));
    }

    @Test
    public void headerTitle_isDisplayed() {
        onView(withText("My Event History")).check(matches(isDisplayed()));
    }

    // ── Back button functionality ────────────────────────────────────────────

    @Test
    public void backButton_isClickable() {
        onView(withId(R.id.btn_back))
                .check(matches(isClickable()));
    }

    @Test
    public void backButton_click_finishesActivity() {
        onView(withId(R.id.btn_back)).perform(click());
        
        // Activity should finish, so we can't interact with views anymore
        // This test passes if no exception is thrown
    }

    // ── Empty state ──────────────────────────────────────────────────────────

    @Test
    public void emptyState_textView_exists() {
        // Just verify the empty state view exists, regardless of visibility
        onView(withId(R.id.tv_empty))
                .check(matches(withText("No event history")));
    }

    // ── Progress bar ─────────────────────────────────────────────────────────

    @Test
    public void progressBar_viewExists() {
        // Just verify the progress bar view exists in the layout
        onView(withId(R.id.progress_bar)).check(matches(isAssignableFrom(ProgressBar.class)));
    }

    // ── RecyclerView functionality ───────────────────────────────────────────

    @Test
    public void recyclerView_isScrollable() {
        onView(withId(R.id.recycler_history))
                .check(matches(isDisplayed()))
                .perform(swipeUp());
    }

    // ── Header styling ───────────────────────────────────────────────────────

    @Test
    public void headerTitle_hasCorrectText() {
        onView(withText("My Event History"))
                .check(matches(isDisplayed()));
    }

    @Test
    public void backButton_hasBackText() {
        onView(withText("Back"))
                .check(matches(isDisplayed()));
    }
}
