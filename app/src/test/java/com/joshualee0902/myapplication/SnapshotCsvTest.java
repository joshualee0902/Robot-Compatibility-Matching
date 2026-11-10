package com.joshualee0902.myapplication;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class SnapshotCsvTest {

    @Test
    public void parsesByColumnNameInAnyOrderAndTreatsBlankAsNaN() throws Exception {
        String csv = "team,event_key,auto_opr,teleop_opr,endgame_opr\n254,2020x,5.5,20.25,\n1678,2020x,nan,18,7\n";
        SnapshotCsv.Snapshot s = SnapshotCsv.parse(csv);
        assertEquals("2020x", s.eventKey);
        assertEquals(2, s.ratings.size());
        double[] a = s.ratings.get(254);
        assertEquals(5.5, a[0], 0);
        assertEquals(20.25, a[1], 0);
        assertTrue(Double.isNaN(a[2]));
        assertTrue(Double.isNaN(s.ratings.get(1678)[0]));
        assertEquals(1678, (int) s.ratings.lastKey());

        SnapshotCsv.Snapshot r = SnapshotCsv.parse("endgame_opr,auto_opr,teleop_opr,event_key,team\n3,1,2,e,10\n4,1,2,e,11\n");
        assertArrayEquals(new double[]{1, 2, 3}, r.ratings.get(10), 0);
    }

    @Test(expected = CsvCodec.CsvException.class)
    public void missingColumnRejected() throws Exception {
        SnapshotCsv.parse("team,auto_opr\n1,2\n2,3\n");
    }

    @Test(expected = CsvCodec.CsvException.class)
    public void badNumberRejected() throws Exception {
        SnapshotCsv.parse("team,event_key,auto_opr,teleop_opr,endgame_opr\n1,e,x,2,3\n2,e,1,2,3\n");
    }

    @Test(expected = CsvCodec.CsvException.class)
    public void tooFewTeamsRejected() throws Exception {
        SnapshotCsv.parse("team,event_key,auto_opr,teleop_opr,endgame_opr\n1,e,1,2,3\n");
    }
}
