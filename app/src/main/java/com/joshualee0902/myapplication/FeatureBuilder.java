package com.joshualee0902.myapplication;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Port of analytics/features.py: builds the z-scored feature matrix for every team in the TBA
 * snapshot. Rules (must match the Python reference):
 *  - TBA features come from the snapshot (auto_opr, teleop_opr, endgame_opr).
 *  - Scouting features (defense, climb_rate, bricked_rate, foul_rate) are per-team means shrunk
 *    toward the event mean with weight n / (n + m); teams never scouted get the event mean.
 *    With no scouting data at all, a requested scouting feature is 0 (neutral).
 *  - Each column is z-scored across the snapshot teams with the population std; NaNs are skipped
 *    when computing mean/std and become 0; a column with std <= 1e-12 is all 0.
 */
public final class FeatureBuilder {
    public static final String[] TBA = {"auto_opr", "teleop_opr", "endgame_opr"};
    public static final String[] SCOUT = {"defense", "climb_rate", "bricked_rate", "foul_rate"};
    static final double ZERO_STD = 1e-12;

    /** Z-scored features: row i is team teams[i], column j is names[j]. */
    public static final class Table {
        public final int[] teams;
        public final String[] names;
        public final double[][] z;

        Table(int[] teams, String[] names, double[][] z) {
            this.teams = teams;
            this.names = names;
            this.z = z;
        }

        public int indexOf(int team) {
            return Arrays.binarySearch(teams, team);
        }

        public double[] row(int team) {
            int i = indexOf(team);
            if (i < 0) throw new IllegalArgumentException("Team " + team + " is not in the snapshot");
            return z[i];
        }
    }

    private FeatureBuilder() {}

    public static boolean isKnown(String name) {
        return indexIn(TBA, name) >= 0 || indexIn(SCOUT, name) >= 0;
    }

    private static int indexIn(String[] arr, String name) {
        for (int i = 0; i < arr.length; i++) if (arr[i].equals(name)) return i;
        return -1;
    }

    /** @param ratings team -> {auto_opr, teleop_opr, endgame_opr}; values may be NaN */
    public static Table build(ModelParams params, Map<Integer, double[]> ratings, List<MatchRecord> scouting) {
        TreeMap<Integer, double[]> sorted = new TreeMap<>(ratings);
        int n = sorted.size();
        int[] teams = new int[n];
        int k = 0;
        for (int t : sorted.keySet()) teams[k++] = t;

        boolean haveScouting = scouting != null && !scouting.isEmpty();
        double[][] scoutRaw = haveScouting ? scoutingFeatures(scouting, teams, params.scoutingPriorStrength) : null;

        String[] names = params.features.toArray(new String[0]);
        double[][] z = new double[n][names.length];
        for (int j = 0; j < names.length; j++) {
            double[] col = new double[n];
            int ti = indexIn(TBA, names[j]);
            int si = indexIn(SCOUT, names[j]);
            boolean available = ti >= 0 || haveScouting;
            for (int i = 0; i < n; i++) {
                if (ti >= 0) col[i] = sorted.get(teams[i])[ti];
                else if (available) col[i] = scoutRaw[i][si];
                else col[i] = 0.0;
            }
            double[] zc = available ? zscore(col) : new double[n];
            for (int i = 0; i < n; i++) z[i][j] = zc[i];
        }
        return new Table(teams, names, z);
    }

    static double[] zscore(double[] col) {
        int count = 0;
        double sum = 0;
        for (double v : col) {
            if (!Double.isNaN(v)) {
                sum += v;
                count++;
            }
        }
        double[] out = new double[col.length];
        if (count == 0) return out;
        double mean = sum / count;
        double ss = 0;
        for (double v : col) {
            if (!Double.isNaN(v)) ss += (v - mean) * (v - mean);
        }
        double std = Math.sqrt(ss / count);
        if (!(std > ZERO_STD)) return out;
        for (int i = 0; i < col.length; i++) {
            out[i] = Double.isNaN(col[i]) ? 0.0 : (col[i] - mean) / std;
        }
        return out;
    }

    /** shrunk = w * teamMean + (1 - w) * eventMean, w = n / (n + m). Rows follow `teams`. */
    static double[][] scoutingFeatures(List<MatchRecord> scouting, int[] teams, double m) {
        double[] eventSum = new double[SCOUT.length];
        for (MatchRecord r : scouting) add(eventSum, r);
        double[] eventMean = new double[SCOUT.length];
        for (int j = 0; j < SCOUT.length; j++) eventMean[j] = eventSum[j] / scouting.size();

        TreeMap<Integer, double[]> sums = new TreeMap<>();
        TreeMap<Integer, Integer> counts = new TreeMap<>();
        for (MatchRecord r : scouting) {
            double[] s = sums.computeIfAbsent(r.teamNumber, x -> new double[SCOUT.length]);
            add(s, r);
            counts.merge(r.teamNumber, 1, Integer::sum);
        }
        double[][] out = new double[teams.length][SCOUT.length];
        for (int i = 0; i < teams.length; i++) {
            int nt = counts.getOrDefault(teams[i], 0);
            double w = (double) nt / (nt + m);
            for (int j = 0; j < SCOUT.length; j++) {
                double raw = nt > 0 ? sums.get(teams[i])[j] / nt : eventMean[j];
                out[i][j] = w * raw + (1.0 - w) * eventMean[j];
            }
        }
        return out;
    }

    private static void add(double[] acc, MatchRecord r) {
        acc[0] += r.defenseRating;
        acc[1] += r.climbed ? 1 : 0;
        acc[2] += r.bricked ? 1 : 0;
        acc[3] += r.fouls;
    }
}
