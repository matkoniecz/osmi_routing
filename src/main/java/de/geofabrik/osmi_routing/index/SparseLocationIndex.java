package de.geofabrik.osmi_routing.index;

import java.nio.ByteOrder;
import java.util.NoSuchElementException;

import com.graphhopper.coll.GHLongLongHashMap;
import com.graphhopper.util.BitUtil;
import com.graphhopper.util.Helper;

public class SparseLocationIndex {

    private GHLongLongHashMap index;
    private BitUtil bitUtil;
    private final int invalid = 0xFFFFFFFF;

    public SparseLocationIndex() {
        this.index = new GHLongLongHashMap();
        this.bitUtil = BitUtil.get(ByteOrder.nativeOrder());
    }
    
    public void setAsInterested(long osmId) {
        index.put(osmId, bitUtil.toLong(invalid, invalid));
    }

    public boolean getInterested(long osmId) {
        long value = getLocationAsLong(osmId);
        return bitUtil.getIntHigh(value) == invalid || bitUtil.getIntLow(value) == invalid;
    }

    public void setLocation(long osmId, double lat, double lon) {
        int latInt = Helper.degreeToInt(lat);
        int lonInt = Helper.degreeToInt(lon);
        index.put(osmId, bitUtil.toLong(latInt, lonInt));
    }

    public double getLat(long osmId) {
        return latFromLong(getLocationAsLong(osmId));
    }

    public double getLon(long osmId) {
        return lonFromLong(getLocationAsLong(osmId));
    }

    public double latFromLong(long location) {
        return Helper.intToDegree(bitUtil.getIntLow(location));
    }

    public double lonFromLong(long location) {
        return Helper.intToDegree(bitUtil.getIntHigh(location));
    }

    public boolean hasKey(long osmId) {
        return index.containsKey(osmId);
    }

    public long getLocationAsLong(long osmId) {
        if (!hasKey(osmId)) {
            throw new NoSuchElementException("OSM ID " + Long.toString(osmId) + " not found.");
        }
        return index.get(osmId);
    }

    public void release() {
        index.release();
    }
}
