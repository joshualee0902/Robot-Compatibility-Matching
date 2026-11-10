package com.joshualee0902.myapplication;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Port of analytics/matching.py: complementary k-NN.
 *
 *   target = max( (S - captain - sum(picks so far)) / picks remaining , 0 )
 *   distance(candidate) = sqrt( sum_j w_j * (x_j - target_j)^2 )      (smaller is better)
 *
 * Ties are broken by team number. The per-feature terms are returned so the UI can explain a rank.
 */
public final class PartnerMatcher {

    public static final class Ranked {
        public final int team;
        public final double distance;
        public final double[] contributions;   // w_j * (x_j - target_j)^2; distance = sqrt(sum)

        Ranked(int team, double distance, double[] contributions) {
            this.team = team;
            this.distance = distance;
            this.contributions = contributions;
        }
    }

    private PartnerMatcher() {}

    public static double[] targetProfile(double[] winning, double[] captain, List<double[]> picked, int remaining) {
        int r = Math.max(remaining, 1);
        double[] target = new double[winning.length];
        for (int i = 0; i < target.length; i++) {
            double gap = winning[i] - captain[i];
            for (double[] p : picked) gap -= p[i];
            target[i] = Math.max(gap / r, 0.0);
        }
        return target;
    }

    public static List<Ranked> rank(double[] target, FeatureBuilder.Table table, double[] weights, Iterable<Integer> pool) {
        List<Ranked> out = new ArrayList<>();
        for (int team : pool) {
            double[] x = table.row(team);
            double[] contrib = new double[x.length];
            double sum = 0;
            for (int j = 0; j < x.length; j++) {
                double d = x[j] - target[j];
                contrib[j] = weights[j] * d * d;
                sum += contrib[j];
            }
            out.add(new Ranked(team, Math.sqrt(sum), contrib));
        }
        Collections.sort(out, new Comparator<Ranked>() {
            @Override
            public int compare(Ranked a, Ranked b) {
                int c = Double.compare(a.distance, b.distance);
                return c != 0 ? c : Integer.compare(a.team, b.team);
            }
        });
        return out;
    }

    /** Live draft for one captain: pick (ours), decline (gone / taken by others), recommend. */
    public static final class Draft {
        private final FeatureBuilder.Table table;
        private final ModelParams params;
        private final int captain;
        private final Set<Integer> pool = new LinkedHashSet<>();
        private final List<Integer> picked = new ArrayList<>();

        public Draft(FeatureBuilder.Table table, ModelParams params, int captain, Iterable<Integer> pool) {
            this.table = table;
            this.params = params;
            this.captain = captain;
            table.row(captain);                       // throws if the captain is not in the snapshot
            for (int t : pool) if (t != captain) this.pool.add(t);
        }

        public void pick(int team) {
            if (!pool.remove(team)) throw new IllegalArgumentException("Team " + team + " is not available");
            picked.add(team);
        }

        public void decline(int team) {
            if (!pool.remove(team)) throw new IllegalArgumentException("Team " + team + " is not available");
        }

        public List<Integer> picks() {
            return Collections.unmodifiableList(picked);
        }

        public int remainingPicks() {
            return ModelParams.ALLIANCE_SIZE - 1 - picked.size();
        }

        public double[] target() {
            List<double[]> rows = new ArrayList<>();
            for (int t : picked) rows.add(table.row(t));
            return targetProfile(params.winningProfile, table.row(captain), rows, remainingPicks());
        }

        public List<Ranked> recommend(int n) {
            List<Ranked> all = rank(target(), table, params.weights, pool);
            return n > 0 && n < all.size() ? all.subList(0, n) : all;
        }
    }
}
