package com.joshualee0902.myapplication;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;

/**
 * Local SQLite store. Schema versions:
 * v1 - original table (strings for flags, wheel 1/2/3, climb "Climbed"/"Did not Climb").
 * v3 - adds the `snapshot` table (imported TBA ratings used by Alliance Selection).
 * v2 - adds DEFENSE_RATING and DEFENDED_AGAINST, normalises flags to 0/1 and wheel to a
 *      bitmask. Upgrading migrates in place; scouted data is never dropped.
 */
public class Database extends SQLiteOpenHelper {

    public static final String DATABASE_NAME = "info.db";
    public static final int SCHEMA_VERSION = 3;
    public static final String SNAPSHOT_TABLE = "snapshot";
    public static final String TABLE_NAME = "data";
    public static final String COL_TEAM = "TEAM_NUM";
    public static final String COL_MATCH = "MATCH_NUM";
    public static final String COL_OUTCOME = "OUTCOME";
    public static final String COL_CROSS = "CROSS_LINE";
    public static final String COL_AUTO_UPPER = "AUTO_UPPER";
    public static final String COL_AUTO_LOWER = "AUTO_LOWER";
    public static final String COL_TELE_UPPER = "TELE_UPPER";
    public static final String COL_TELE_LOWER = "TELE_LOWER";
    public static final String COL_WHEEL = "WHEEL";
    public static final String COL_CLIMB = "CLIMB";
    public static final String COL_FOULS = "FOULS";
    public static final String COL_TOTAL = "TOTAL_POINTS";
    public static final String COL_RP = "RANKING_POINTS";
    public static final String COL_BRICKED = "BRICKED";
    public static final String COL_COMMENT = "COMMENT";
    public static final String COL_DEFENSE = "DEFENSE_RATING";
    public static final String COL_DEFENDED = "DEFENDED_AGAINST";

    public Database(Context context) {
        super(context, DATABASE_NAME, null, SCHEMA_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + TABLE_NAME + " ("
                + COL_TEAM + " INTEGER, " + COL_MATCH + " INTEGER, " + COL_OUTCOME + " TEXT, "
                + COL_CROSS + " TEXT, " + COL_AUTO_UPPER + " INTEGER, " + COL_AUTO_LOWER + " INTEGER, "
                + COL_TELE_UPPER + " INTEGER, " + COL_TELE_LOWER + " INTEGER, " + COL_WHEEL + " TEXT, "
                + COL_CLIMB + " TEXT, " + COL_FOULS + " INTEGER, " + COL_TOTAL + " INTEGER, "
                + COL_RP + " INTEGER, " + COL_BRICKED + " TEXT, " + COL_COMMENT + " TEXT, "
                + COL_DEFENSE + " INTEGER DEFAULT 0, " + COL_DEFENDED + " INTEGER DEFAULT 0)");
        createSnapshotTable(db);
    }

    private static void createSnapshotTable(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS " + SNAPSHOT_TABLE + " (TEAM INTEGER PRIMARY KEY, EVENT_KEY TEXT, "
                + "AUTO_OPR REAL, TELEOP_OPR REAL, ENDGAME_OPR REAL)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) {
            db.beginTransaction();
            try {
                db.execSQL("ALTER TABLE " + TABLE_NAME + " ADD COLUMN " + COL_DEFENSE + " INTEGER DEFAULT 0");
                db.execSQL("ALTER TABLE " + TABLE_NAME + " ADD COLUMN " + COL_DEFENDED + " INTEGER DEFAULT 0");
                // legacy wheel 1/2/3 (neither/spin/both) -> bitmask 0/1/3
                db.execSQL("UPDATE " + TABLE_NAME + " SET " + COL_WHEEL + " = CASE CAST(" + COL_WHEEL
                        + " AS INTEGER) WHEN 2 THEN 1 WHEN 3 THEN 3 ELSE 0 END");
                db.execSQL("UPDATE " + TABLE_NAME + " SET " + COL_CLIMB + " = CASE WHEN " + COL_CLIMB
                        + " = 'Climbed' THEN '1' ELSE '0' END");
                db.setTransactionSuccessful();
            } finally {
                db.endTransaction();
            }
        }
        if (oldVersion < 3) {
            createSnapshotTable(db);
        }
    }

    /** Replaces the stored TBA snapshot (one event at a time). NaN ratings are stored as NULL. */
    public void replaceSnapshot(String eventKey, TreeMap<Integer, double[]> ratings) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            db.delete(SNAPSHOT_TABLE, null, null);
            for (java.util.Map.Entry<Integer, double[]> e : ratings.entrySet()) {
                ContentValues cv = new ContentValues();
                cv.put("TEAM", e.getKey());
                cv.put("EVENT_KEY", eventKey);
                String[] cols = {"AUTO_OPR", "TELEOP_OPR", "ENDGAME_OPR"};
                for (int j = 0; j < 3; j++) {
                    double v = e.getValue()[j];
                    if (Double.isNaN(v)) cv.putNull(cols[j]);
                    else cv.put(cols[j], v);
                }
                db.insert(SNAPSHOT_TABLE, null, cv);
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    public TreeMap<Integer, double[]> getSnapshot() {
        TreeMap<Integer, double[]> out = new TreeMap<>();
        try (Cursor c = getReadableDatabase().rawQuery(
                "SELECT TEAM, AUTO_OPR, TELEOP_OPR, ENDGAME_OPR FROM " + SNAPSHOT_TABLE, null)) {
            while (c.moveToNext()) {
                double[] v = new double[3];
                for (int j = 0; j < 3; j++) v[j] = c.isNull(j + 1) ? Double.NaN : c.getDouble(j + 1);
                out.put(c.getInt(0), v);
            }
        }
        return out;
    }

    public String getSnapshotEventKey() {
        try (Cursor c = getReadableDatabase().rawQuery("SELECT EVENT_KEY FROM " + SNAPSHOT_TABLE + " LIMIT 1", null)) {
            return c.moveToFirst() ? c.getString(0) : null;
        }
    }

    public boolean insert(MatchRecord r) {
        ContentValues cv = new ContentValues();
        cv.put(COL_TEAM, r.teamNumber);
        cv.put(COL_MATCH, r.matchNumber);
        cv.put(COL_OUTCOME, r.outcome);
        cv.put(COL_CROSS, r.crossLine ? "1" : "0");
        cv.put(COL_AUTO_UPPER, r.autoUpper);
        cv.put(COL_AUTO_LOWER, r.autoLower);
        cv.put(COL_TELE_UPPER, r.teleUpper);
        cv.put(COL_TELE_LOWER, r.teleLower);
        cv.put(COL_WHEEL, String.valueOf(r.wheel));
        cv.put(COL_CLIMB, r.climbed ? "1" : "0");
        cv.put(COL_FOULS, r.fouls);
        cv.put(COL_TOTAL, r.totalPoints);
        cv.put(COL_RP, r.rankingPoints);
        cv.put(COL_BRICKED, r.bricked ? "1" : "0");
        cv.put(COL_COMMENT, r.comment);
        cv.put(COL_DEFENSE, r.defenseRating);
        cv.put(COL_DEFENDED, r.defendedAgainst ? 1 : 0);
        return getWritableDatabase().insert(TABLE_NAME, null, cv) != -1;
    }

    public List<MatchRecord> getAll() {
        List<MatchRecord> out = new ArrayList<>();
        try (Cursor c = getReadableDatabase().rawQuery("SELECT * FROM " + TABLE_NAME, null)) {
            while (c.moveToNext()) {
                MatchRecord r = new MatchRecord();
                r.teamNumber = c.getInt(c.getColumnIndexOrThrow(COL_TEAM));
                r.matchNumber = c.getInt(c.getColumnIndexOrThrow(COL_MATCH));
                String outcome = c.getString(c.getColumnIndexOrThrow(COL_OUTCOME));
                r.outcome = outcome == null ? "" : outcome;
                r.crossLine = flag(c, COL_CROSS);
                r.autoUpper = c.getInt(c.getColumnIndexOrThrow(COL_AUTO_UPPER));
                r.autoLower = c.getInt(c.getColumnIndexOrThrow(COL_AUTO_LOWER));
                r.teleUpper = c.getInt(c.getColumnIndexOrThrow(COL_TELE_UPPER));
                r.teleLower = c.getInt(c.getColumnIndexOrThrow(COL_TELE_LOWER));
                r.wheel = c.getInt(c.getColumnIndexOrThrow(COL_WHEEL));
                r.climbed = flag(c, COL_CLIMB);
                r.fouls = c.getInt(c.getColumnIndexOrThrow(COL_FOULS));
                r.totalPoints = c.getInt(c.getColumnIndexOrThrow(COL_TOTAL));
                r.rankingPoints = c.getInt(c.getColumnIndexOrThrow(COL_RP));
                r.bricked = flag(c, COL_BRICKED);
                String comment = c.getString(c.getColumnIndexOrThrow(COL_COMMENT));
                r.comment = comment == null ? "" : comment;
                r.defenseRating = c.getInt(c.getColumnIndexOrThrow(COL_DEFENSE));
                r.defendedAgainst = c.getInt(c.getColumnIndexOrThrow(COL_DEFENDED)) != 0;
                out.add(r);
            }
        }
        return out;
    }

    private static boolean flag(Cursor c, String col) {
        String v = c.getString(c.getColumnIndexOrThrow(col));
        return "1".equals(v);
    }

    /**
     * Inserts records that are not already stored (exact duplicates are skipped), so
     * importing the same tablet's file twice is harmless.
     * @return {added, skipped}
     */
    public int[] importRecords(List<MatchRecord> records) {
        Set<MatchRecord> existing = new HashSet<>(getAll());
        int added = 0, skipped = 0;
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            for (MatchRecord r : records) {
                if (existing.contains(r)) {
                    skipped++;
                } else if (insert(r)) {
                    existing.add(r);
                    added++;
                }
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
        return new int[]{added, skipped};
    }

    public int count() {
        try (Cursor c = getReadableDatabase().rawQuery("SELECT COUNT(*) FROM " + TABLE_NAME, null)) {
            return c.moveToFirst() ? c.getInt(0) : 0;
        }
    }
}
