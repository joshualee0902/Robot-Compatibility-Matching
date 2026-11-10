package com.joshualee0902.myapplication;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * One scouted robot in one match. This single object is carried through the whole
 * scouting flow (and saved to the database / CSV), replacing the old public static
 * fields on each screen.
 *
 * Plain Java (no Android classes) so it can be unit-tested on the JVM.
 */
public class MatchRecord implements Serializable {
    private static final long serialVersionUID = 2L;

    // Wheel (control panel) is a bitmask so every checkbox combination has a defined value.
    public static final int WHEEL_NONE = 0;
    public static final int WHEEL_SPIN = 1;   // rotation control
    public static final int WHEEL_COLOR = 2;  // position control
    public static final int WHEEL_BOTH = WHEEL_SPIN | WHEEL_COLOR;

    // Infinite Recharge scoring used to compute total points (this robot's own contribution).
    // Fouls are points given to the opponent, so they are not subtracted here.
    public static final int PTS_CROSS_LINE = 5;
    public static final int PTS_AUTO_UPPER = 4;
    public static final int PTS_AUTO_LOWER = 2;
    public static final int PTS_TELE_UPPER = 2;
    public static final int PTS_TELE_LOWER = 1;
    public static final int PTS_WHEEL_SPIN = 10;
    public static final int PTS_WHEEL_COLOR = 20;
    public static final int PTS_CLIMB = 25;

    public static final int MAX_DEFENSE = 3;
    public static final int MAX_RANKING_POINTS = 4;

    public int teamNumber;
    public int matchNumber;
    /** "W", "D" or "L"; empty until chosen. */
    public String outcome = "";
    public boolean crossLine;
    public int autoUpper;
    public int autoLower;
    public int teleUpper;
    public int teleLower;
    public int wheel = WHEEL_NONE;
    public boolean climbed;
    public int fouls;
    /** Scout-judged defense played: 0 = none, 1 = light, 2 = medium, 3 = heavy. */
    public int defenseRating;
    /** True if the scouted robot was itself defended against. */
    public boolean defendedAgainst;
    public int totalPoints;
    public int rankingPoints;
    public boolean bricked;
    public String comment = "";

    /** Maps the two wheel checkboxes to a defined value for every combination. */
    public static int wheelFromChecks(boolean spin, boolean color) {
        return (spin ? WHEEL_SPIN : 0) | (color ? WHEEL_COLOR : 0);
    }

    /** Legacy (schema v1) wheel values were 1 = neither, 2 = spin, 3 = both. */
    public static int wheelFromLegacy(int legacy) {
        switch (legacy) {
            case 2: return WHEEL_SPIN;
            case 3: return WHEEL_BOTH;
            default: return WHEEL_NONE;
        }
    }

    public int computeTotalPoints() {
        int total = 0;
        if (crossLine) total += PTS_CROSS_LINE;
        total += autoUpper * PTS_AUTO_UPPER + autoLower * PTS_AUTO_LOWER;
        total += teleUpper * PTS_TELE_UPPER + teleLower * PTS_TELE_LOWER;
        if ((wheel & WHEEL_SPIN) != 0) total += PTS_WHEEL_SPIN;
        if ((wheel & WHEEL_COLOR) != 0) total += PTS_WHEEL_COLOR;
        if (climbed) total += PTS_CLIMB;
        return total;
    }

    /** Recomputes {@link #totalPoints} from the components. */
    public void recomputeTotal() {
        totalPoints = computeTotalPoints();
    }

    /** Errors for the pre-match screen only (team and match number). */
    public List<String> validatePreMatch() {
        List<String> errors = new ArrayList<>();
        if (teamNumber <= 0) errors.add("Team number is required (positive integer)");
        if (matchNumber <= 0) errors.add("Match number is required (positive integer)");
        return errors;
    }

    /** All validation errors; empty means the record can be saved. */
    public List<String> validate() {
        List<String> errors = validatePreMatch();
        if (autoUpper < 0 || autoLower < 0 || teleUpper < 0 || teleLower < 0 || fouls < 0) {
            errors.add("Counts cannot be negative");
        }
        if (defenseRating < 0 || defenseRating > MAX_DEFENSE) {
            errors.add("Defense rating must be 0 to " + MAX_DEFENSE);
        }
        if (wheel < 0 || wheel > WHEEL_BOTH) {
            errors.add("Wheel value is invalid");
        }
        if (!(outcome.equals("W") || outcome.equals("D") || outcome.equals("L"))) {
            errors.add("Choose an outcome (W, D or L)");
        }
        if (rankingPoints < 0 || rankingPoints > MAX_RANKING_POINTS) {
            errors.add("Ranking points must be 0 to " + MAX_RANKING_POINTS);
        }
        if (totalPoints < 0) errors.add("Total points cannot be negative");
        return errors;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MatchRecord)) return false;
        MatchRecord r = (MatchRecord) o;
        return teamNumber == r.teamNumber && matchNumber == r.matchNumber
                && outcome.equals(r.outcome) && crossLine == r.crossLine
                && autoUpper == r.autoUpper && autoLower == r.autoLower
                && teleUpper == r.teleUpper && teleLower == r.teleLower
                && wheel == r.wheel && climbed == r.climbed && fouls == r.fouls
                && defenseRating == r.defenseRating && defendedAgainst == r.defendedAgainst
                && totalPoints == r.totalPoints && rankingPoints == r.rankingPoints
                && bricked == r.bricked && comment.equals(r.comment);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(teamNumber, matchNumber, outcome, crossLine, autoUpper,
                autoLower, teleUpper, teleLower, wheel, climbed, fouls, defenseRating,
                defendedAgainst, totalPoints, rankingPoints, bricked, comment);
    }
}
