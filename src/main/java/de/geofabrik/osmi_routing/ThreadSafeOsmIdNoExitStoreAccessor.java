package de.geofabrik.osmi_routing;

import java.nio.Buffer;
import java.nio.ByteBuffer;

/**
 * Provide thread-safe read access to OsmIdAndNoExitStore.
 */
public class ThreadSafeOsmIdNoExitStoreAccessor {
    
    protected OsmIdAndNoExitStore store;
    protected ByteBuffer buffer;

    public ThreadSafeOsmIdNoExitStoreAccessor(OsmIdAndNoExitStore store) {
        this.store = store;
        this.buffer = ByteBuffer.allocate(store.getBufferSize());
    }
    
    public long getOsmId(int nodeId) {
        // Casting to java.nio.Buffer is necessary because ByteBuffer.clear and .flip are have covariant return types
        // compared to their parent class since Java 9. This is incompatible to Java 8.
        // https://jira.mongodb.org/browse/JAVA-2559
        ((Buffer) buffer).clear();
        return store.getOsmId(nodeId, buffer);
    }

    public boolean getNoExit(int nodeId) {
        ((Buffer) buffer).clear();
        return store.getNoExit(nodeId, buffer);
    }

}
