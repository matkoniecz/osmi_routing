package de.geofabrik.osmi_routing;

import com.carrotsearch.hppc.LongScatterSet;
import com.carrotsearch.hppc.LongSet;
import com.graphhopper.reader.ReaderNode;
import com.graphhopper.reader.osm.OSMReader;
import com.graphhopper.reader.osm.OSMReaderHook;

public class NoExitHook extends OSMReaderHook {

    private OsmIdAndNoExitStore nodeInfo;
    private LongSet noExitNodes;

    public NoExitHook(OsmIdAndNoExitStore nodeInfo) {
        this.nodeInfo = nodeInfo;
        this.noExitNodes = new LongScatterSet();
    }

    public void processNode(ReaderNode node) {
        if (node.hasTag("noexit", "yes")) {
            noExitNodes.add(node.getId());
        }
    }

    public void addTowerNode(long osmId, double lat, double lon, double ele, int towerId) {
        // Ensure that artifically created OSM IDs don't mess things up. They are a hack in
        // GraphHopper when handling barriers which split an edge.
        if (osmId > 0) {
            nodeInfo.addNodeInfo(OSMReader.towerIdToMapId(towerId), osmId, noExitNodes.contains(osmId));
        }
    }
}
