package com.joshualee0902.myapplication;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.Espresso.pressBack;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.closeSoftKeyboard;
import static androidx.test.espresso.action.ViewActions.replaceText;
import static androidx.test.espresso.action.ViewActions.scrollTo;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isChecked;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.junit.Assert.assertEquals;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;

/** Walks the whole scouting flow, including back-navigation, and checks what was saved. */
@RunWith(AndroidJUnit4.class)
public class ScoutingFlowTest {

    @Rule
    public ActivityScenarioRule<MainActivity> rule = new ActivityScenarioRule<>(MainActivity.class);

    @Before
    public void cleanDb() {
        Context ctx = ApplicationProvider.getApplicationContext();
        ctx.deleteDatabase(Database.DATABASE_NAME);
    }

    @Test
    public void fullFlowSavesDefenseAndSurvivesBackNavigation() {
        onView(withId(R.id.start)).perform(click());

        onView(withId(R.id.teamNum)).perform(replaceText("1234"), closeSoftKeyboard());
        onView(withId(R.id.matchNum)).perform(replaceText("7"), closeSoftKeyboard());
        onView(withId(R.id.next1)).perform(click());

        // Auto
        onView(withId(R.id.checkCrossedLine)).perform(click());
        onView(withId(R.id.next2)).perform(click());

        // TeleOp: climb + heavy defense, then go Back and Next again; values must remain.
        onView(withId(R.id.checkClimb)).perform(scrollTo(), click());
        onView(withId(R.id.defense3)).perform(scrollTo(), click());
        pressBack();
        onView(withId(R.id.checkCrossedLine)).check(matches(isChecked()));
        onView(withId(R.id.next2)).perform(click());
        onView(withId(R.id.checkClimb)).check(matches(isChecked()));
        onView(withId(R.id.defense3)).check(matches(isChecked()));
        onView(withId(R.id.next3)).perform(click());

        // Post-match: outcome is required.
        onView(withId(R.id.next4)).perform(click());          // rejected: no outcome
        onView(withId(R.id.outcomeW)).perform(click());
        onView(withId(R.id.next4)).perform(scrollTo(), click());

        // Save
        onView(withId(R.id.submit)).perform(click());

        Context ctx = ApplicationProvider.getApplicationContext();
        List<MatchRecord> rows = new Database(ctx).getAll();
        assertEquals(1, rows.size());
        MatchRecord r = rows.get(0);
        assertEquals(1234, r.teamNumber);
        assertEquals(7, r.matchNumber);
        assertEquals("W", r.outcome);
        assertEquals(3, r.defenseRating);
        assertEquals(30, r.totalPoints); // cross line 5 + climb 25
    }
}
