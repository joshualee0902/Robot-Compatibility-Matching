package com.joshualee0902.myapplication;

import java.util.List;
import java.util.TreeMap;

/**
 * Parses the TBA snapshot written by `python -m analytics.export --snapshot EVENT`:
 * columns team, event_key, auto_opr, teleop_opr, endgame_opr (found by name, any order).
 * Empty or "nan" ratings become NaN (treated as missing by the feature builder).
 */
public final class SnapshotCsv {

    public static final class Snapshot {
        public final String eventKey;
        public final TreeMap<Integer, double[]> ratings;

        Snapshot(String eventKey, TreeMap<Integer, double[]> ratings) {
            this.eventKey = eventKey;
            this.ratings = ratings;
        }
    }

    private SnapshotCsv() {}

    public static Snapshot parse(String csv) throws CsvCodec.CsvException {
        List<String[]> rows = CsvCodec.parse(csv);
        if (rows.isEmpty()) throw new CsvCodec.CsvException("Snapshot file is empty");
        String[] header = rows.get(0);
        int team = col(header, "team");
        int event = col(header, "event_key");
        int[] val = {col(header, "auto_opr"), col(header, "teleop_opr"), col(header, "endgame_opr")};

        TreeMap<Integer, double[]> ratings = new TreeMap<>();
        String eventKey = "";
        for (int i = 1; i < rows.size(); i++) {
            String[] f = rows.get(i);
            if (f.length == 1 && f[0].isEmpty()) continue;
            if (f.length != header.length) {
                throw new CsvCodec.CsvException("Row " + (i + 1) + " has " + f.length + " columns, expected " + header.length);
            }
            int t;
            try {
                t = Integer.parseInt(f[team].trim());
            } catch (NumberFormatException e) {
                throw new CsvCodec.CsvException("Row " + (i + 1) + ": bad team number '" + f[team] + "'");
            }
            double[] v = new double[3];
            for (int j = 0; j < 3; j++) v[j] = number(f[val[j]], i + 1);
            ratings.put(t, v);
            eventKey = f[event].trim();
        }
        if (ratings.size() < 2) throw new CsvCodec.CsvException("Snapshot needs at least 2 teams");
        return new Snapshot(eventKey, ratings);
    }

    private static double number(String s, int line) throws CsvCodec.CsvException {
        String t = s.trim();
        if (t.isEmpty() || t.equalsIgnoreCase("nan")) return Double.NaN;
        try {
            return Double.parseDouble(t);
        } catch (NumberFormatException e) {
            throw new CsvCodec.CsvException("Row " + line + ": bad number '" + s + "'");
        }
    }

    private static int col(String[] header, String name) throws CsvCodec.CsvException {
        for (int i = 0; i < header.length; i++) if (header[i].trim().equals(name)) return i;
        throw new CsvCodec.CsvException("Snapshot is missing column '" + name + "'");
    }
}
