package com.joshualee0902.myapplication;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.TreeMap;

public class PartnerMatcherTest {

    private static ModelParams params(String features, String weights, String profile) {
        return ModelParams.fromJson("{\"schema_version\":1,\"features\":" + features + ",\"weights\":" + weights
                + ",\"winning_profile\":" + profile + ",\"alliance_size\":3}");
    }

    private static final ModelParams P3 = params("[\"auto_opr\",\"teleop_opr\",\"endgame_opr\"]",
            "[0.3333333333333333,0.3333333333333333,0.3333333333333333]", "[3,3,3]");

    /** Table whose z-scores we control: build from ratings, then compare against known values. */
    private static FeatureBuilder.Table table(double[][] ratings) {
        TreeMap<Integer, double[]> m = new TreeMap<>();
        for (int i = 0; i < ratings.length; i++) m.put(i + 1, ratings[i]);
        return FeatureBuilder.build(P3, m, new ArrayList<MatchRecord>());
    }

    @Test
    public void zscoreHasMeanZeroStdOneAndConstantColumnIsZero() {
        FeatureBuilder.Table t = table(new double[][]{{1, 5, 0}, {2, 5, 0}, {3, 5, 0}, {4, 5, 9}});
        double mean = 0, ss = 0;
        for (double[] r : t.z) mean += r[0] / 4;
        for (double[] r : t.z) ss += (r[0] - mean) * (r[0] - mean) / 4;
        assertEquals(0, mean, 1e-12);
        assertEquals(1, Math.sqrt(ss), 1e-12);
        for (double[] r : t.z) assertEquals(0.0, r[1], 0.0);
    }

    @Test
    public void targetProfileFillsGapAndClipsAtZero() {
        double[] t = PartnerMatcher.targetProfile(new double[]{3, 3, 3}, new double[]{1, 1, 5},
                new ArrayList<double[]>(), 2);
        assertArrayEquals(new double[]{1, 1, 0}, t, 0);
        double[] t2 = PartnerMatcher.targetProfile(new double[]{3, 3, 3}, new double[]{1, 1, 5},
                Arrays.asList(new double[]{1, 0, 0}), 1);
        assertArrayEquals(new double[]{1, 2, 0}, t2, 0);
    }

    @Test
    public void contributionsExplainDistanceAndTiesBreakByTeam() {
        FeatureBuilder.Table t = table(new double[][]{{1, 1, 1}, {1, 1, 1}, {5, 2, 9}, {0, 4, 4}});
        List<PartnerMatcher.Ranked> r = PartnerMatcher.rank(new double[]{0, 0, 0}, t,
                new double[]{0.5, 0.3, 0.2}, Arrays.asList(4, 2, 1, 3));
        for (PartnerMatcher.Ranked x : r) {
            double s = 0;
            for (double c : x.contributions) s += c;
            assertEquals(Math.sqrt(s), x.distance, 1e-12);
        }
        // identical vectors (teams 1 and 2) tie exactly -> lower team number first
        int i1 = -1, i2 = -1;
        for (int i = 0; i < r.size(); i++) {
            if (r.get(i).team == 1) i1 = i;
            if (r.get(i).team == 2) i2 = i;
        }
        assertTrue(i1 < i2);
    }

    @Test
    public void draftPickDeclineAndSecondPickRecomputesTarget() {
        FeatureBuilder.Table t = table(new double[][]{{9, 1, 1}, {1, 9, 1}, {1, 1, 9}, {2, 2, 2}, {5, 5, 5}});
        ModelParams p = params("[\"auto_opr\",\"teleop_opr\",\"endgame_opr\"]", "[0.5,0.3,0.2]", "[3,3,3]");
        PartnerMatcher.Draft d = new PartnerMatcher.Draft(t, p, 1, Arrays.asList(1, 2, 3, 4, 5));
        List<PartnerMatcher.Ranked> first = d.recommend(0);
        assertEquals(4, first.size());                       // captain excluded
        double[] t0 = d.target();

        d.decline(first.get(0).team);
        for (PartnerMatcher.Ranked r : d.recommend(0)) assertFalse(r.team == first.get(0).team);
        assertArrayEquals(t0, d.target(), 0);                // declining does not change our alliance

        int mine = d.recommend(0).get(0).team;
        d.pick(mine);
        assertEquals(1, d.remainingPicks());
        assertEquals(Arrays.asList(mine), d.picks());
        for (PartnerMatcher.Ranked r : d.recommend(0)) assertFalse(r.team == mine);
        assertFalse(Arrays.equals(t0, d.target()));          // recomputed after the pick
        assertEquals(1, d.recommend(1).size());
    }

    @Test(expected = IllegalArgumentException.class)
    public void pickingUnavailableTeamFails() {
        FeatureBuilder.Table t = table(new double[][]{{1, 2, 3}, {2, 3, 4}, {3, 4, 5}});
        PartnerMatcher.Draft d = new PartnerMatcher.Draft(t, P3, 1, Arrays.asList(1, 2, 3));
        d.decline(2);
        d.pick(2);
    }

    @Test(expected = IllegalArgumentException.class)
    public void captainMustBeInSnapshot() {
        new PartnerMatcher.Draft(table(new double[][]{{1, 2, 3}, {2, 3, 4}}), P3, 99, Arrays.asList(1, 2));
    }

    @Test
    public void shrinkageUsesNOverNPlusM() {
        // team 1: 5 matches all defense 3; team 2: 5 matches all defense 0; event mean 1.5; m = 5 -> w = 0.5
        List<MatchRecord> rows = new ArrayList<>();
        for (int i = 0; i < 5; i++) rows.add(rec(1, 3));
        for (int i = 0; i < 5; i++) rows.add(rec(2, 0));
        double[][] f = FeatureBuilder.scoutingFeatures(rows, new int[]{1, 2, 3}, 5.0);
        assertEquals(0.5 * 3 + 0.5 * 1.5, f[0][0], 1e-12);
        assertEquals(0.5 * 0 + 0.5 * 1.5, f[1][0], 1e-12);
        assertEquals(1.5, f[2][0], 1e-12);                   // never scouted -> event mean
    }

    private static MatchRecord rec(int team, int defense) {
        MatchRecord r = new MatchRecord();
        r.teamNumber = team;
        r.defenseRating = defense;
        return r;
    }

    @Test(expected = IllegalArgumentException.class)
    public void paramsRejectWrongSchema() {
        ModelParams.fromJson("{\"schema_version\":2,\"features\":[],\"weights\":[],\"winning_profile\":[]}");
    }

    @Test(expected = IllegalArgumentException.class)
    public void paramsRejectLengthMismatch() {
        params("[\"auto_opr\",\"teleop_opr\"]", "[1.0]", "[1.0,1.0]");
    }

    @Test(expected = IllegalArgumentException.class)
    public void paramsRejectNegativeWeights() {
        params("[\"auto_opr\"]", "[-0.1]", "[1.0]");
    }

    @Test(expected = IllegalArgumentException.class)
    public void paramsRejectUnknownFeature() {
        params("[\"vibes\"]", "[1.0]", "[1.0]");
    }
}
