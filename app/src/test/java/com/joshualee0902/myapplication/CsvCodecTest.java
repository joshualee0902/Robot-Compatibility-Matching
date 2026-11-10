package com.joshualee0902.myapplication;

import static org.junit.Assert.*;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class CsvCodecTest {

    private MatchRecord full(int team, int match) {
        MatchRecord r = new MatchRecord();
        r.teamNumber = team;
        r.matchNumber = match;
        r.outcome = "L";
        r.crossLine = true;
        r.autoUpper = 1;
        r.autoLower = 2;
        r.teleUpper = 3;
        r.teleLower = 4;
        r.wheel = MatchRecord.WHEEL_COLOR;
        r.climbed = true;
        r.fouls = 2;
        r.defenseRating = 3;
        r.defendedAgainst = true;
        r.rankingPoints = 1;
        r.bricked = true;
        r.comment = "Said \"hi\", then\nbroke down; tried again\r\nok";
        r.recomputeTotal();
        return r;
    }

    @Test
    public void roundTripLosesNothing() throws Exception {
        List<MatchRecord> in = new ArrayList<>();
        in.add(full(254, 1));
        in.add(full(1678, 2));
        MatchRecord plain = new MatchRecord();
        plain.teamNumber = 1;
        plain.matchNumber = 3;
        plain.outcome = "D";
        in.add(plain);

        List<MatchRecord> out = CsvCodec.decode(CsvCodec.encode(in));
        assertEquals(in, out);
    }

    @Test
    public void emptyListRoundTrips() throws Exception {
        assertTrue(CsvCodec.decode(CsvCodec.encode(new ArrayList<MatchRecord>())).isEmpty());
    }

    @Test
    public void legacyHandTypedTotalIsPreserved() throws Exception {
        MatchRecord r = full(10, 1);
        r.totalPoints = 999; // hand-typed legacy value must survive, not be recomputed
        List<MatchRecord> out = CsvCodec.decode(CsvCodec.encode(java.util.Arrays.asList(r)));
        assertEquals(999, out.get(0).totalPoints);
    }

    @Test(expected = CsvCodec.CsvException.class)
    public void badHeaderRejected() throws Exception {
        CsvCodec.decode("a,b,c\r\n1,2,3\r\n");
    }

    @Test(expected = CsvCodec.CsvException.class)
    public void wrongColumnCountRejected() throws Exception {
        String good = CsvCodec.encode(java.util.Arrays.asList(full(1, 1)));
        CsvCodec.decode(good + "1,2,3\r\n");
    }

    @Test(expected = CsvCodec.CsvException.class)
    public void invalidRowRejected() throws Exception {
        MatchRecord r = full(1, 1);
        r.teamNumber = -5;
        CsvCodec.decode(CsvCodec.encode(java.util.Arrays.asList(r)));
    }

    @Test(expected = CsvCodec.CsvException.class)
    public void unterminatedQuoteRejected() throws Exception {
        CsvCodec.decode("\"abc");
    }
}
