package de.geofabrik.osmi_routing;

import java.nio.ByteBuffer;

/**
 * Provide thread-safe read access to OsmIdAndNoExitStore.
 */
public class ThreadSafeOsmIdNoExitStoreAccessor {
    
    private OsmIdAndNoExitStore store;
    private ByteBuffer buffer;

    public ThreadSafeOsmIdNoExitStoreAccessor(OsmIdAndNoExitStore store) {
        this.store = store;
        this.buffer = ByteBuffer.allocate(store.getBufferSize());
    }
    
    public long getOsmId(int nodeId) {
        buffer.clear();
        return store.getOsmId(nodeId, buffer);
    }

    public boolean getNoExit(int nodeId) {
        buffer.clear();
        return store.getNoExit(nodeId, buffer);
    }

}
