package de.geofabrik.osmi_routing.index;

import static org.junit.Assert.assertEquals;

import org.junit.Before;
import org.junit.Test;

public class SparseLocationIndexTest {

    SparseLocationIndex index;

    public SparseLocationIndexTest() {
    }

    @Before
    public void setUp() {
        index = new SparseLocationIndex();
    }

    @Test
    public void testSparseLocationIndexEurope() {
        final double startLon = 7.00;
        final double startLat = 49.0;
        for (long i = 1; i < 1000; ++i) {
            double lon = startLon + i * 0.01;
            double lat = startLat + i * 0.01;
            index.setLocation(i, lat, lon);
        }
        for (long i = 1; i < 1000; ++i) {
            assertEquals(startLon + i * 0.01, index.getLon(i), 0.0000002);
            assertEquals(startLat + i * 0.01, index.getLat(i), 0.0000002);
        }
    }

    @Test
    public void testSparseLocationIndexLargeIDs() {
        final double startLon = 7.00;
        final double startLat = 49.0;
        final long int32 = 1l << 32;
        for (long i = int32; i < int32 + 1000; ++i) {
            double lon = startLon + (i - int32) * 0.01;
            double lat = startLat + (i - int32) * 0.01;
            index.setLocation(i, lat, lon);
        }
        for (long i = int32; i < int32 + 1000; ++i) {
            assertEquals(startLon + (i - int32) * 0.01, index.getLon(i), 0.0000002);
            assertEquals(startLat + (i - int32) * 0.01, index.getLat(i), 0.0000002);
        }
    }

    @Test
    public void testSparseLocationIndexLargeLonsLats() {
        final double startLon = 90.00;
        final double startLat = 180.0;
        for (long i = 1; i < 1000; ++i) {
            double lon = startLon - i * 0.01;
            double lat = startLat - i * 0.01;
            index.setLocation(i, lat, lon);
        }
        for (long i = 1; i < 1000; ++i) {
            assertEquals(startLon - i * 0.01, index.getLon(i), 0.0000002);
            assertEquals(startLat - i * 0.01, index.getLat(i), 0.0000002);
        }
    }

    @Test
    public void testSparseLocationIndexLowLonsLats() {
        final double startLon = -90.00;
        final double startLat = -180.0;
        for (long i = 1; i < 1000; ++i) {
            double lon = startLon + i * 0.01;
            double lat = startLat + i * 0.01;
            index.setLocation(i, lat, lon);
        }
        for (long i = 1; i < 1000; ++i) {
            assertEquals(startLon + i * 0.01, index.getLon(i), 0.0000002);
            assertEquals(startLat + i * 0.01, index.getLat(i), 0.0000002);
        }
    }
}
