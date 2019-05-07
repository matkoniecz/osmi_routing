package de.geofabrik.osmi_routing;

import java.nio.ByteBuffer;

public class OsmIdStore extends OsmIdAndNoExitStore {

    // The size of the OSM ID is not reduced from 64 to 56 bits because we store OSM IDs only.
    protected final int OSM_ID_BYTES = 8;

    public OsmIdStore(String location) {
        super(location, "osm_way_ids");
    }

    /**
     * Add a OSM node ID and its noexit/entrance status to the data store.
     * 
     * This method is not thread safe!
     * 
     * @param edgeId internal edge ID
     * @param osmWayId OSM node ID
     */
    public void addWayId(int edgeId, long osmWayId) {
        ensureCapacity(edgeId);
        checkNodeIdValid(osmWayId);
        setLong(edgeId, osmWayId);
        entriesCount = edgeId + 1;
    }

    long getOsmId(int internalId, ByteBuffer buffer) {
        if (internalId == -1) {
            // no entry for this edge ID
            return -1;
        }
        return getLong(internalId, buffer);
    }

    public long getOsmId(int internalId) {
        return getOsmId(internalId, inputByteBuffer);
    }

}
