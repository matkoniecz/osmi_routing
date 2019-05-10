/*
 *  © 2019 Geofabrik GmbH
 *
 *  This file contains code copied from the GraphHopper project,
 *  © 2012–2019 GraphHopper GmbH, licensed under Apache License
 *  version 2.
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

package de.geofabrik.osmi_routing.subnetworks;

import java.io.IOException;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.carrotsearch.hppc.IntArrayList;
import com.carrotsearch.hppc.IntIndexedContainer;
import com.graphhopper.routing.profiles.BooleanEncodedValue;
import com.graphhopper.routing.subnetwork.PrepareRoutingSubnetworks;
import com.graphhopper.routing.util.FlagEncoder;
import com.graphhopper.storage.GraphHopperStorage;
import com.graphhopper.util.EdgeExplorer;
import com.graphhopper.util.EdgeIterator;

import de.geofabrik.osmi_routing.GeoJSONWriter;
import de.geofabrik.osmi_routing.OsmIdStore;
import de.geofabrik.osmi_routing.UnconnectedFinderManager;

public class RemoveAndDumpSubnetworks extends PrepareRoutingSubnetworks {

    static final Logger logger = LogManager.getLogger(UnconnectedFinderManager.class.getName());
    
    GeoJSONWriter writer;
    OsmIdStore edgeIdToWayId;

    public RemoveAndDumpSubnetworks(GraphHopperStorage ghStorage, List<FlagEncoder> encoders, java.nio.file.Path path, OsmIdStore edgeIdToWayId) throws IOException {
        super(ghStorage, encoders, false);
        this.writer = new GeoJSONWriter(path);
        this.edgeIdToWayId = edgeIdToWayId;
    }

    protected void finalize() throws IOException {
        this.writer.close();
    }

    /**
     * Deletes all but the largest subnetworks.
     */
    protected int keepLargeNetworks(PrepEdgeFilter filter, List<IntArrayList> components) {
        if (components.size() <= 1)
            return 0;

        int maxCount = -1;
        IntIndexedContainer oldComponent = null;
        int allRemoved = 0;
        BooleanEncodedValue accessEnc = filter.getAccessEnc();
        EdgeExplorer explorer = getGraphHopperStorage().createEdgeExplorer(filter);
        for (IntArrayList component : components) {
            if (maxCount < 0) {
                maxCount = component.size();
                oldComponent = component;
                continue;
            }

            int removedEdges;
            if (maxCount < component.size()) {
                // new biggest area found. remove old
                removedEdges = removeEdges(explorer, accessEnc, oldComponent, getMinNetworkSize(), SubnetworkType.ISLAND);

                maxCount = component.size();
                oldComponent = component;
            } else {
                removedEdges = removeEdges(explorer, accessEnc, component, getMinNetworkSize(), SubnetworkType.ISLAND);
            }

            allRemoved += removedEdges;
        }

        if (allRemoved > getGraphHopperStorage().getAllEdges().length() / 2)
            throw new IllegalStateException("Too many total edges were removed: " + allRemoved + ", all edges:" + getGraphHopperStorage().getAllEdges().length());
        return allRemoved;
    }

    /**
     * This method removes the access to edges available from the nodes contained in the components.
     * But only if a components' size is smaller then the specified min value.
     *
     * @return number of removed edges
     */
    protected int removeEdges(final PrepEdgeFilter bothFilter, List<IntArrayList> components, int min) {
        // remove edges determined from nodes but only if less than minimum size
        EdgeExplorer explorer = getGraphHopperStorage().createEdgeExplorer(bothFilter);
        int removedEdges = 0;
        for (IntArrayList component : components) {
            removedEdges += removeEdges(explorer, bothFilter.getAccessEnc(), component, min, SubnetworkType.SINK_SOURCE);
        }
        return removedEdges;
    }

    int removeEdges(EdgeExplorer explorer, BooleanEncodedValue accessEnc, IntIndexedContainer component, int min, SubnetworkType type) {
        int removedEdges = 0;
        if (component.size() < min) {
            for (int i = 0; i < component.size(); i++) {
                EdgeIterator edge = explorer.setBaseNode(component.get(i));
                while (edge.next()) {
                    edge.set(accessEnc, false).setReverse(accessEnc, false);
                    try {
                        writer.writeEdge(edge.fetchWayGeometry(3), type, edgeIdToWayId.getOsmId(edge.getEdge()));
                    } catch (IOException e) {
                        logger.catching(e);
                        System.exit(1);
                    }
                    removedEdges++;
                }
            }
        }

        return removedEdges;
    }
    
    public enum SubnetworkType {
        UNDEFINED,
        ISLAND,
        SINK_SOURCE
    }
}
