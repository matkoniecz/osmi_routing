package de.geofabrik.osmi_routing;

import com.graphhopper.storage.DataAccess;
import com.graphhopper.storage.Directory;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.GraphExtension;
import com.graphhopper.storage.NodeAccess;
import com.graphhopper.storage.TurnCostExtension;

public class NodeInfoExtension implements GraphExtension {
    
    private int NO_ENTRY = -1;
    // The size of the OSM ID is reduced from 64 to 56 bits to have space for one extra bit for the noexit=yes state.
    private final int OSM_ID_BYTES = 7;
    
    private DataAccess nodesInfo;
    private int entryBytes = 8;
    private int entriesCount;
    private NodeAccess nodeAccess;

    public NodeInfoExtension() {
        entriesCount = 0;
    }

    public void init(Graph graph, Directory dir) {
        if (entriesCount > 0)
            throw new AssertionError("The nodes info storage must be initialized only once.");

        this.nodeAccess = graph.getNodeAccess();
        this.nodesInfo = dir.find("nodes_info");
    }

    public void close() {
        nodesInfo.close();
    }

    private void ensureCapacity(int nodeIndex) {
        nodesInfo.ensureCapacity(((long) nodeIndex + 1) * entryBytes);
    }

    private void checkNodeIdUpperLimit(long osmNodeId) {
        long upperLimit = 1l << (OSM_ID_BYTES * 8);
        if (osmNodeId > upperLimit) {
            throw new AssertionError(String.format("Unable to handle OSM node ID %d because it exceeds the upper limit of %d.", osmNodeId, upperLimit));
        }
    }

    public int addNodeInfo(int nodeId, long osmNodeId, boolean hasNoExit) {
        System.out.printf("addNodeInfo(%d %d) ", nodeId, osmNodeId);
        int newEntryIndex = entriesCount;
        ensureCapacity(newEntryIndex);
        checkNodeIdUpperLimit(osmNodeId);
        nodeAccess.setAdditionalNodeField(nodeId, newEntryIndex);
        // DataAccess.setLong does not exist. We have to work around that and use setInt twice.
        int highInt = ((int) (osmNodeId >> (4 * 8))) & 0x00FFFFFF;
        if (hasNoExit) {
            // insert hasNoExit flag into the leftmost bits of the OSM ID
            highInt |= 0x01000000;
        }
        int lowInt = (int) (osmNodeId & 0xFFFFFFFFl);
        nodesInfo.setInt((long) newEntryIndex * entryBytes, lowInt);
        nodesInfo.setInt((long) (newEntryIndex * entryBytes + 4), highInt);
        entriesCount = newEntryIndex + 1;
        System.out.printf(" -> %d\n", newEntryIndex);
        return newEntryIndex;
    }

    public long getOsmIdByNodeId(int nodeId) {
        int index = nodeAccess.getAdditionalNodeField(nodeId);
        System.out.printf("getOsmIdByNodeId(nodeId=%d) -> ", nodeId);
        return getOsmId(index);
    }

    public long getOsmId(int index) {
        if (index == -1) {
            // no entry for this node
            System.out.print("\n");
            return -1;
        }
        int lowInt = nodesInfo.getInt((long) (index) * entryBytes);
        int highInt = nodesInfo.getInt((long) (index) * entryBytes + 4);
        long osmId = (long) (highInt & 0x00FFFFFF) << (4 * 8);
        osmId |= (long) lowInt;
        System.out.printf("getOsmId(index=%d) -> %d\n", index, osmId);
        return osmId;
    }
    
    public boolean getNoExitByNodeId(int nodeId) {
        int index = nodeAccess.getAdditionalNodeField(nodeId);
        return getNoExit(index);
    }

    public boolean getNoExit(int index) {
        if (index == -1) {
            // no entry for this node
            return false;
        }
        int highInt = nodesInfo.getInt((long) index * entryBytes + 4);
        return (highInt & 0x01000000) == 0x01000000;
    }

    public GraphExtension create(long initBytes) {
        nodesInfo.create(initBytes);
        return this;
    }

    public void flush() {
        nodesInfo.setHeader(0, entryBytes);
        nodesInfo.setHeader(1 * 4, entriesCount);
        nodesInfo.flush();
    }

    public long getCapacity() {
        return nodesInfo.getCapacity();
    }

    public boolean isClosed() {
        return nodesInfo.isClosed();
    }

    public boolean loadExisting() {
        if (!nodesInfo.loadExisting())
            return false;

        entryBytes = nodesInfo.getHeader(0);
        entriesCount = nodesInfo.getHeader(4);
        return true;
    }

    public GraphExtension copyTo(GraphExtension clonedStorage) {
        if (!(clonedStorage instanceof NodeInfoExtension)) {
            throw new IllegalStateException("the extended storage to clone must be the same");
        }

        NodeInfoExtension clonedTC = (NodeInfoExtension) clonedStorage;

        nodesInfo.copyTo(clonedTC.nodesInfo);
        clonedTC.entriesCount = entriesCount;

        return clonedStorage;
    }

    public int getDefaultEdgeFieldValue() {
        throw new UnsupportedOperationException("Not supported by this storage");
    }

    public int getDefaultNodeFieldValue() {
        return NO_ENTRY;
    }

    public boolean isRequireEdgeField() {
        return false;
    }

    public boolean isRequireNodeField() {
        return true;
    }

    public void setSegmentSize(int bytes) {
        nodesInfo.setSegmentSize(bytes);

    }

}
