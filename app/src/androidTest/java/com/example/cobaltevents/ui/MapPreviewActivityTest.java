package com.example.cobaltevents.ui;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.junit.Assert.assertEquals;

import android.content.Intent;

import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.example.cobaltevents.R;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class MapPreviewActivityTest {

    private Intent intentWith(String location, String eventName) {
        Intent intent = new Intent(ApplicationProvider.getApplicationContext(),
                MapPreviewActivity.class);
        intent.putExtra(MapPreviewActivity.EXTRA_LOCATION, location);
        intent.putExtra(MapPreviewActivity.EXTRA_EVENT_NAME, eventName);
        return intent;
    }

    // ── Intent extra constants ────────────────────────────────────────────────

    @Test
    public void extraLocationConstant_hasExpectedValue() {
        assertEquals("location", MapPreviewActivity.EXTRA_LOCATION);
    }

    @Test
    public void extraEventNameConstant_hasExpectedValue() {
        assertEquals("event_name", MapPreviewActivity.EXTRA_EVENT_NAME);
    }

    // ── Layout presence ───────────────────────────────────────────────────────

    @Test
    public void backButton_isDisplayed() {
        try (ActivityScenario<MapPreviewActivity> ignored =
                     ActivityScenario.launch(intentWith("Central Park", "Yoga Session"))) {
            onView(withId(R.id.btn_back)).check(matches(isDisplayed()));
        }
    }

    @Test
    public void locationTitle_showsPassedLocation() {
        try (ActivityScenario<MapPreviewActivity> ignored =
                     ActivityScenario.launch(intentWith("Central Park", "Yoga Session"))) {
            onView(withId(R.id.tv_location_title)).check(matches(withText("Central Park")));
        }
    }

    @Test
    public void locationTitle_noExtra_showsFallback() {
        Intent intent = new Intent(ApplicationProvider.getApplicationContext(),
                MapPreviewActivity.class);
        try (ActivityScenario<MapPreviewActivity> ignored =
                     ActivityScenario.launch(intent)) {
            onView(withId(R.id.tv_location_title)).check(matches(withText("Location")));
        }
    }
}
