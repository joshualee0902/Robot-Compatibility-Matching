package com.joshualee0902.myapplication;

import static org.junit.Assert.*;

import org.junit.Test;

public class MatchRecordTest {

    private MatchRecord valid() {
        MatchRecord r = new MatchRecord();
        r.teamNumber = 1234;
        r.matchNumber = 7;
        r.outcome = "W";
        return r;
    }

    @Test
    public void wheelMapsEveryCheckboxCombination() {
        assertEquals(MatchRecord.WHEEL_NONE, MatchRecord.wheelFromChecks(false, false));
        assertEquals(MatchRecord.WHEEL_SPIN, MatchRecord.wheelFromChecks(true, false));
        assertEquals(MatchRecord.WHEEL_COLOR, MatchRecord.wheelFromChecks(false, true)); // was undefined
        assertEquals(MatchRecord.WHEEL_BOTH, MatchRecord.wheelFromChecks(true, true));
    }

    @Test
    public void legacyWheelValuesAreMapped() {
        assertEquals(MatchRecord.WHEEL_NONE, MatchRecord.wheelFromLegacy(1));
        assertEquals(MatchRecord.WHEEL_SPIN, MatchRecord.wheelFromLegacy(2));
        assertEquals(MatchRecord.WHEEL_BOTH, MatchRecord.wheelFromLegacy(3));
        assertEquals(MatchRecord.WHEEL_NONE, MatchRecord.wheelFromLegacy(0));
    }

    @Test
    public void totalPointsComputedFromComponents() {
        MatchRecord r = valid();
        r.crossLine = true;          // 5
        r.autoUpper = 2;             // 8
        r.autoLower = 1;             // 2
        r.teleUpper = 3;             // 6
        r.teleLower = 4;             // 4
        r.wheel = MatchRecord.WHEEL_BOTH; // 30
        r.climbed = true;            // 25
        r.fouls = 5;                 // not subtracted
        assertEquals(80, r.computeTotalPoints());
        r.recomputeTotal();
        assertEquals(80, r.totalPoints);
    }

    @Test
    public void validRecordHasNoErrors() {
        assertTrue(valid().validate().isEmpty());
    }

    @Test
    public void teamAndMatchRequired() {
        MatchRecord r = valid();
        r.teamNumber = 0;
        r.matchNumber = 0;
        assertEquals(2, r.validatePreMatch().size());
    }

    @Test
    public void negativeCountsRejected() {
        MatchRecord r = valid();
        r.teleUpper = -1;
        assertFalse(r.validate().isEmpty());
    }

    @Test
    public void defenseRatingRange() {
        MatchRecord r = valid();
        r.defenseRating = 4;
        assertFalse(r.validate().isEmpty());
        r.defenseRating = 3;
        assertTrue(r.validate().isEmpty());
    }

    @Test
    public void outcomeRequired() {
        MatchRecord r = valid();
        r.outcome = "";
        assertFalse(r.validate().isEmpty());
        r.outcome = "maybe";
        assertFalse(r.validate().isEmpty());
    }
}
