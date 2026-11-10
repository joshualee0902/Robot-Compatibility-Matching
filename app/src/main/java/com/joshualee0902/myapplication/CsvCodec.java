package com.joshualee0902.myapplication;

import java.util.ArrayList;
import java.util.List;

/**
 * CSV export/import for {@link MatchRecord} lists (RFC 4180 quoting, so comments may
 * contain commas, quotes and newlines). Plain Java, unit-tested on the JVM.
 *
 * First line is a comment-free header; the schema version is carried by the header
 * itself so future versions can be recognised.
 */
public final class CsvCodec {
    public static final int SCHEMA_VERSION = 2;

    public static final String[] HEADER = {
            "team_num", "match_num", "outcome", "cross_line", "auto_upper", "auto_lower",
            "tele_upper", "tele_lower", "wheel", "climbed", "fouls", "defense_rating",
            "defended_against", "total_points", "ranking_points", "bricked", "comment"
    };

    public static class CsvException extends Exception {
        public CsvException(String message) {
            super(message);
        }
    }

    private CsvCodec() {}

    public static String encode(List<MatchRecord> records) {
        StringBuilder sb = new StringBuilder();
        appendRow(sb, HEADER);
        for (MatchRecord r : records) {
            appendRow(sb, new String[]{
                    String.valueOf(r.teamNumber), String.valueOf(r.matchNumber), r.outcome,
                    bool(r.crossLine), String.valueOf(r.autoUpper), String.valueOf(r.autoLower),
                    String.valueOf(r.teleUpper), String.valueOf(r.teleLower),
                    String.valueOf(r.wheel), bool(r.climbed), String.valueOf(r.fouls),
                    String.valueOf(r.defenseRating), bool(r.defendedAgainst),
                    String.valueOf(r.totalPoints), String.valueOf(r.rankingPoints),
                    bool(r.bricked), r.comment
            });
        }
        return sb.toString();
    }

    public static List<MatchRecord> decode(String csv) throws CsvException {
        List<String[]> rows = parse(csv);
        if (rows.isEmpty()) throw new CsvException("File is empty");
        String[] header = rows.get(0);
        if (header.length != HEADER.length) {
            throw new CsvException("Unexpected header: expected " + HEADER.length
                    + " columns, found " + header.length);
        }
        for (int i = 0; i < HEADER.length; i++) {
            if (!HEADER[i].equals(header[i].trim())) {
                throw new CsvException("Unexpected header column " + (i + 1) + ": '"
                        + header[i] + "' (expected '" + HEADER[i] + "')");
            }
        }
        List<MatchRecord> out = new ArrayList<>();
        for (int line = 1; line < rows.size(); line++) {
            String[] f = rows.get(line);
            if (f.length == 1 && f[0].isEmpty()) continue; // blank line
            if (f.length != HEADER.length) {
                throw new CsvException("Row " + (line + 1) + " has " + f.length
                        + " columns, expected " + HEADER.length);
            }
            MatchRecord r = new MatchRecord();
            try {
                r.teamNumber = Integer.parseInt(f[0].trim());
                r.matchNumber = Integer.parseInt(f[1].trim());
                r.outcome = f[2].trim();
                r.crossLine = parseBool(f[3]);
                r.autoUpper = Integer.parseInt(f[4].trim());
                r.autoLower = Integer.parseInt(f[5].trim());
                r.teleUpper = Integer.parseInt(f[6].trim());
                r.teleLower = Integer.parseInt(f[7].trim());
                r.wheel = Integer.parseInt(f[8].trim());
                r.climbed = parseBool(f[9]);
                r.fouls = Integer.parseInt(f[10].trim());
                r.defenseRating = Integer.parseInt(f[11].trim());
                r.defendedAgainst = parseBool(f[12]);
                r.totalPoints = Integer.parseInt(f[13].trim());
                r.rankingPoints = Integer.parseInt(f[14].trim());
                r.bricked = parseBool(f[15]);
                r.comment = f[16];
            } catch (NumberFormatException e) {
                throw new CsvException("Row " + (line + 1) + ": " + e.getMessage());
            }
            List<String> errors = r.validate();
            if (!errors.isEmpty()) {
                throw new CsvException("Row " + (line + 1) + ": " + errors.get(0));
            }
            out.add(r);
        }
        return out;
    }

    private static String bool(boolean b) {
        return b ? "1" : "0";
    }

    private static boolean parseBool(String s) throws CsvException {
        String t = s.trim();
        if (t.equals("1")) return true;
        if (t.equals("0")) return false;
        throw new CsvException("Expected 0 or 1 but found '" + s + "'");
    }

    private static void appendRow(StringBuilder sb, String[] fields) {
        for (int i = 0; i < fields.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(quote(fields[i]));
        }
        sb.append("\r\n");
    }

    private static String quote(String s) {
        boolean needs = s.indexOf(',') >= 0 || s.indexOf('"') >= 0
                || s.indexOf('\n') >= 0 || s.indexOf('\r') >= 0;
        if (!needs) return s;
        return "\"" + s.replace("\"", "\"\"") + "\"";
    }

    /** Minimal RFC 4180 parser: quoted fields, doubled quotes, CRLF or LF line ends. */
    static List<String[]> parse(String text) throws CsvException {
        List<String[]> rows = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean inQuotes = false;
        boolean anyContent = false;
        int n = text.length();
        for (int i = 0; i < n; i++) {
            char c = text.charAt(i);
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < n && text.charAt(i + 1) == '"') {
                        field.append('"');
                        i++;
                    } else {
                        inQuotes = false;
                    }
                } else {
                    field.append(c);
                }
            } else if (c == '"') {
                inQuotes = true;
                anyContent = true;
            } else if (c == ',') {
                row.add(field.toString());
                field.setLength(0);
                anyContent = true;
            } else if (c == '\r' || c == '\n') {
                if (c == '\r' && i + 1 < n && text.charAt(i + 1) == '\n') i++;
                row.add(field.toString());
                field.setLength(0);
                rows.add(row.toArray(new String[0]));
                row.clear();
                anyContent = false;
            } else {
                field.append(c);
                anyContent = true;
            }
        }
        if (inQuotes) throw new CsvException("Unterminated quoted field");
        if (anyContent || field.length() > 0) {
            row.add(field.toString());
            rows.add(row.toArray(new String[0]));
        }
        return rows;
    }
}
