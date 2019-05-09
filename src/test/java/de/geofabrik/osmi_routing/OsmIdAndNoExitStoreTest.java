package de.geofabrik.osmi_routing;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class OsmIdAndNoExitStoreTest {

    @Test
    public void testOsmId() {
        OsmIdAndNoExitStore ext = new OsmIdAndNoExitStore("/tmp/test");
        long[] osmIds = {
            1l << 16,
            (1l << 16) + 5,
            (1l << 16) - 7,
            (1l << 16) + 35,
            (1l << 36),
            (1l << 16) + 6,
            (1l << 16) - 8,
            (1l << 37)
        };
        boolean[] noExits = {true, true, true, true, true, false, false, false};
        for (int i = 0; i < osmIds.length; ++i) {
            ext.addNodeInfo(i, osmIds[i], noExits[i]);
        }
        for (int i = 0; i < osmIds.length; ++i) {
            long osmId = ext.getOsmId(i);
            boolean noExit = ext.getNoExit(i);
            assertEquals(osmIds[i], osmId);
            assertEquals(noExits[i], noExit);
        }
    }

    @Test
    public void testLargerNumbers() {
        OsmIdAndNoExitStore ext = new OsmIdAndNoExitStore("/tmp/test");
        ext.addNodeInfo(204, 3426740052l, true);
        assertEquals(3426740052l, ext.getOsmId(204));
        ext.addNodeInfo(205, 6205074608l, true);
        assertEquals(6205074608l, ext.getOsmId(205));
    }

    @Test
    public void testManyNumbers() {
        OsmIdAndNoExitStore ext = new OsmIdAndNoExitStore("/tmp/test");
        final int maxNodeCount = 10000000;
        for (int i = 0; i < maxNodeCount; ++i) {
            long osmId = ((long) i) * 2 + 1;
            ext.addNodeInfo(i, osmId, (i % 2 == 0));
        }
        for (int i = 0; i < maxNodeCount; ++i) {
            long osmId = ext.getOsmId(i);
            long expected = ((long) i) * 2 + 1;
            assertEquals(expected, osmId);
        }
    }
}
