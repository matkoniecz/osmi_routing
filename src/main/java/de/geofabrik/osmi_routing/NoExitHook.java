package de.geofabrik.osmi_routing;

import com.graphhopper.reader.ReaderNode;
import com.graphhopper.reader.osm.OSMReader;
import com.graphhopper.reader.osm.OSMReaderHook;

public class NoExitHook implements OSMReaderHook {
    
    private OSMReader reader;
    private OsmIdAndNoExitStore nodeInfo;

    public NoExitHook(OSMReader reader, OsmIdAndNoExitStore nodeInfo) {
        this.reader = reader;
        this.nodeInfo = nodeInfo;
    }
    
    public void processNode(ReaderNode node) {
        int internalNodeId;
        try {
            internalNodeId = reader.getNodeMap().get(node.getId());
        } catch (NullPointerException e) {
            return;
        }
        if (internalNodeId >= 0) {
            nodeInfo.addNodeInfo(internalNodeId, node.getId(), node.hasTag("noexit", "yes"));
        }
    }
}
