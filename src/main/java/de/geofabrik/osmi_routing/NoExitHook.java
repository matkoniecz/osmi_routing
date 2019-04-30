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
        boolean noExit = node.hasTag("noexit", "yes");
        String entranceValue = node.getTag("entrance");
        noExit = noExit || (entranceValue != null && !entranceValue.equals("no"));
        if (noExit) {
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
