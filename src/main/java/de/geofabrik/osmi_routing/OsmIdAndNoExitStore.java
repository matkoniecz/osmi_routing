/*
 *  © 2019 Geofabrik GmbH
 *
 *  This file is part of osmi_routing.
 *
 *  osmi_routing is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License.
 *
 *  osmi_routing is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with osmi_simple_views. If not, see <http://www.gnu.org/licenses/>.
 */

package de.geofabrik.osmi_routing;

import java.nio.ByteBuffer;

import com.graphhopper.storage.DAType;
import com.graphhopper.storage.DataAccess;
import com.graphhopper.storage.GHDirectory;

public class OsmIdAndNoExitStore {
    
    private int NO_ENTRY = -1;
    // The size of the OSM ID is reduced from 64 to 56 bits to have space for one extra bit for the noexit=yes state.
    private final int OSM_ID_BYTES = 7;
    
    private DataAccess nodesInfo;
    private int entryBytes = 8;
    private int entriesCount;

    public OsmIdAndNoExitStore(String location) {
        this.entriesCount = 0;
        GHDirectory dir = new GHDirectory(location, DAType.RAM);
        this.nodesInfo = dir.find("node_info", DAType.RAM);
        this.nodesInfo.create(100000);
        if (entriesCount > 0)
            throw new AssertionError("The nodes info storage must be initialized only once.");
    }

    public void close() {
        nodesInfo.close();
    }

    private void ensureCapacity(int nodeIndex) {
        nodesInfo.ensureCapacity(((long) nodeIndex + 1) * entryBytes);
        // intialize with NO_ENTRY value
        for (int i = entriesCount; i <= nodeIndex; ++i) {
            setLong(i, NO_ENTRY);
        }
    }

    private void checkNodeIdValid(long osmNodeId) {
        long upperLimit = 1l << (OSM_ID_BYTES * 8);
        if (osmNodeId > upperLimit) {
            throw new AssertionError(String.format("Unable to handle OSM node ID %d because it exceeds the upper limit of %d.", osmNodeId, upperLimit));
        }
        if (osmNodeId < 0) {
            throw new AssertionError(String.format("Unable to handle negative node ID %d.", osmNodeId));
        }
    }
    
    private void setLong(int nodeId, long value) {
        ByteBuffer buffer = ByteBuffer.allocate(Long.BYTES);
        buffer.putLong(value);
        nodesInfo.setBytes((long) nodeId  * entryBytes, buffer.array(), Long.BYTES);
    }

    private long getLong(int nodeId) {
        ByteBuffer buffer = ByteBuffer.allocate(Long.BYTES);
        byte[] bytes = new byte[Long.BYTES];
        nodesInfo.getBytes((long) nodeId  * entryBytes, bytes, Long.BYTES);
        buffer.put(bytes);
        buffer.flip();
        long result = buffer.getLong();
        return result;
    }

    public void addNodeInfo(int nodeId, long osmNodeId, boolean hasNoExit) {
        ensureCapacity(nodeId);
        checkNodeIdValid(osmNodeId);
        long toInsert = osmNodeId & 0x00FFFFFFFFFFFFFFl;
        if (hasNoExit) {
            toInsert |= (1l << 63);
        }
        setLong(nodeId, toInsert);
        entriesCount = nodeId + 1;
    }

    public long getOsmId(int nodeId) {
        if (nodeId == -1) {
            // no entry for this node
            return -1;
        }
        long osmId = getLong(nodeId);
        if (osmId == -1) {
            return osmId;
        }
        osmId &= 0x00FFFFFFFFFFFFFFl;
        return osmId;
    }

    public boolean getNoExit(int nodeId) {
        if (nodeId == -1) {
            // no entry for this node
            return false;
        }
        long stored = getLong(nodeId);
        if (stored == -1) {
            // no entry for this node, return default
            return false;
        }
        stored = stored >>> 63;
        return stored == 1l;
    }
}
