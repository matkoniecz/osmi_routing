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
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.carrotsearch.hppc.IntArrayList;
import com.carrotsearch.hppc.IntIndexedContainer;
import com.graphhopper.routing.profiles.BooleanEncodedValue;
import com.graphhopper.routing.subnetwork.PrepareRoutingSubnetworks;
import com.graphhopper.routing.subnetwork.TarjansSCCAlgorithm;
import com.graphhopper.routing.util.DefaultEdgeFilter;
import com.graphhopper.routing.util.EdgeFilter;
import com.graphhopper.routing.util.FlagEncoder;
import com.graphhopper.storage.GraphHopperStorage;
import com.graphhopper.util.EdgeExplorer;
import com.graphhopper.util.EdgeIterator;
import com.graphhopper.util.Helper;
import com.graphhopper.util.StopWatch;

import de.geofabrik.osmi_routing.GeoJSONWriter;
import de.geofabrik.osmi_routing.OsmIdStore;
import de.geofabrik.osmi_routing.UnconnectedFinderManager;

public class RemoveAndDumpSubnetworks extends PrepareRoutingSubnetworks {

    static final Logger logger = LogManager.getLogger(UnconnectedFinderManager.class.getName());

    List<GeoJSONWriter> writers;
    int currentWriter;
    OsmIdStore edgeIdToWayId;

    public RemoveAndDumpSubnetworks(GraphHopperStorage ghStorage, List<FlagEncoder> encoders, java.nio.file.Path path, OsmIdStore edgeIdToWayId) throws IOException {
        super(ghStorage, encoders, false);
        this.writers = new ArrayList<GeoJSONWriter>(encoders.size());
        for (FlagEncoder e : encoders) {
            java.nio.file.Path destination = path.resolve("subnetworks_" + e.toString() + ".json");
            this.writers.add(new GeoJSONWriter(destination));
        }
        this.currentWriter = 0;
        this.edgeIdToWayId = edgeIdToWayId;
    }

    public void close() throws IOException {
        for (GeoJSONWriter w : writers) {
            w.close();
        }
    }

    public void doWork() {
        if (getMinNetworkSize() <= 0 && getMinOneWayNetworkSize() <= 0)
            return;

        logger.info("start finding subnetworks (min:" + getMinNetworkSize() + ", min one way:" + getMinOneWayNetworkSize() + ") " + Helper.getMemInfo());
        int unvisitedDeadEnds = 0;
        for (currentWriter = 0; currentWriter < getEncoders().size(); ++currentWriter) {
            FlagEncoder encoder = getEncoders().get(currentWriter);
            // mark edges for one vehicle as inaccessible
            PrepEdgeFilterWithEncoder filter = new PrepEdgeFilterWithEncoder(encoder);
            if (getMinOneWayNetworkSize() > 0)
                unvisitedDeadEnds += removeDeadEndUnvisitedNetworks(filter);

            List<IntArrayList> components = findSubnetworks(filter);
            keepLargeNetworks(filter, components);
            subnetworks = Math.max(components.size(), subnetworks);
            logger.info(components.size() + " subnetworks found for " + encoder + ", " + Helper.getMemInfo());
        }

        markNodesRemovedIfUnreachable();

        if (optimize) {
            logger.info("optimize to remove subnetworks (" + subnetworks + "), "
                    + "unvisited-dead-end-nodes (" + unvisitedDeadEnds + "), "
                    + "maxEdges/node (" + maxEdgesPerNode.get() + ")");
            getGraphHopperStorage().optimize();
        } else {
            logger.info("skipping optimization: subnetworks ({}), unvisited-dead-end-nodes ({}), maxEdges/node ({})", subnetworks, unvisitedDeadEnds, maxEdgesPerNode.get());
        }
    }

    /**
     * Deletes all but the largest subnetworks.
     */
    protected int keepLargeNetworks(PrepEdgeFilterWithEncoder filter, List<IntArrayList> components) {
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
                removedEdges = removeEdges(explorer, accessEnc, oldComponent, getMinNetworkSize(), filter.getEncoder(), SubnetworkType.ISLAND);

                maxCount = component.size();
                oldComponent = component;
            } else {
                removedEdges = removeEdges(explorer, accessEnc, component, getMinNetworkSize(), filter.getEncoder(), SubnetworkType.ISLAND);
            }

            allRemoved += removedEdges;
        }

        if (allRemoved > getGraphHopperStorage().getAllEdges().length() / 2)
            throw new IllegalStateException("Too many total edges were removed: " + allRemoved + ", all edges:" + getGraphHopperStorage().getAllEdges().length());
        return allRemoved;
    }

    /**
     * This method removes networks that will be never be visited by this filter. See #235 for
     * example, small areas like parking lots are sometimes connected to the whole network through a
     * one-way road. This is clearly an error - but it causes the routing to fail when a point gets
     * connected to this small area. This routine removes all these networks from the graph.
     *
     * @return number of removed edges
     */
    int removeDeadEndUnvisitedNetworks(final PrepEdgeFilterWithEncoder bothFilter) {
        StopWatch sw = new StopWatch(bothFilter.getAccessEnc() + " findComponents").start();
        final EdgeFilter outFilter = DefaultEdgeFilter.outEdges(bothFilter.getAccessEnc());

        // partition graph into strongly connected components using Tarjan's algorithm
        TarjansSCCAlgorithm tarjan = new TarjansSCCAlgorithm(getGraphHopperStorage(), outFilter, true);
        List<IntArrayList> components = tarjan.findComponents();
        logger.info(sw.stop() + ", size:" + components.size());

        return removeEdges(bothFilter, components, getMinOneWayNetworkSize());
    }

    /**
     * This method removes the access to edges available from the nodes contained in the components.
     * But only if a components' size is smaller then the specified min value.
     *
     * @return number of removed edges
     */
    protected int removeEdges(final PrepEdgeFilterWithEncoder bothFilter, List<IntArrayList> components, int min) {
        // remove edges determined from nodes but only if less than minimum size
        EdgeExplorer explorer = getGraphHopperStorage().createEdgeExplorer(bothFilter);
        int removedEdges = 0;
        for (IntArrayList component : components) {
            removedEdges += removeEdges(explorer, bothFilter.getAccessEnc(), component, min, bothFilter.getEncoder(), SubnetworkType.SINK_SOURCE);
        }
        return removedEdges;
    }

    int removeEdges(EdgeExplorer explorer, BooleanEncodedValue accessEnc, IntIndexedContainer component, int min, FlagEncoder encoder, SubnetworkType type) {
        int removedEdges = 0;
        if (component.size() < min) {
            for (int i = 0; i < component.size(); i++) {
                EdgeIterator edge = explorer.setBaseNode(component.get(i));
                while (edge.next()) {
                    edge.set(accessEnc, false).setReverse(accessEnc, false);
                    try {
                        writers.get(currentWriter).writeEdge(edge.fetchWayGeometry(3), type, edgeIdToWayId.getOsmId(edge.getEdge()), encoder);
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

    protected static class PrepEdgeFilterWithEncoder extends PrepEdgeFilter {

        FlagEncoder encoder;

        public PrepEdgeFilterWithEncoder(FlagEncoder encoder) {
            super(encoder);
            this.encoder = encoder;
        }

        public FlagEncoder getEncoder() {
            return encoder;
        }

        public BooleanEncodedValue getAccessEnc() {
            return accessEnc;
        }
    }
    
    public enum SubnetworkType {
        UNDEFINED,
        ISLAND,
        SINK_SOURCE
    }
}
