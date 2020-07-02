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

import com.graphhopper.routing.ev.BooleanEncodedValue;
import com.graphhopper.routing.subnetwork.PrepareRoutingSubnetworks;
import com.graphhopper.routing.util.FlagEncoder;
import com.graphhopper.storage.GraphHopperStorage;
import com.graphhopper.util.EdgeExplorer;
import com.graphhopper.util.EdgeIterator;
import com.graphhopper.util.FetchMode;
import com.graphhopper.util.Helper;
import com.graphhopper.util.StopWatch;

import de.geofabrik.osmi_routing.GeoJSONWriter;
import de.geofabrik.osmi_routing.OsmIdStore;
import de.geofabrik.osmi_routing.UnconnectedFinderManager;

public class RemoveAndDumpSubnetworks extends PrepareRoutingSubnetworks {

    static final Logger logger = LogManager.getLogger(RemoveAndDumpSubnetworks.class.getName());

    List<GeoJSONWriter> writers;
    int currentWriter;
    OsmIdStore edgeIdToWayId;

    public RemoveAndDumpSubnetworks(GraphHopperStorage ghStorage, List<PrepareJob> jobs, java.nio.file.Path path, OsmIdStore edgeIdToWayId) throws IOException {
        super(ghStorage, jobs, false);
        this.writers = new ArrayList<GeoJSONWriter>(jobs.size());
        for (PrepareJob j : jobs) {
            java.nio.file.Path destination = path.resolve("subnetworks_" + j.getName() + ".json");
            this.writers.add(new GeoJSONWriter(destination));
        }
        this.currentWriter = 0;
        this.edgeIdToWayId = edgeIdToWayId;
    }

    public static RemoveAndDumpSubnetworks build(GraphHopperStorage ghStorage, List<FlagEncoder> encoders, java.nio.file.Path path, OsmIdStore edgeIdToWayId) throws IOException {
        List<PrepareJob> jobs = new ArrayList<PrepareJob>();
        for (FlagEncoder encoder : encoders) {
            jobs.add(new PrepareJob(encoder.toString(), encoder.getAccessEnc(), null));
        }
        return new RemoveAndDumpSubnetworks(ghStorage, jobs, path, edgeIdToWayId);
    }

    public void close() throws IOException {
        for (GeoJSONWriter w : writers) {
            w.close();
        }
    }

    public void doWork() {
        if (getMinNetworkSize() <= 0) {
            logger.info("Skipping subnetwork removal and island detection: prepare.min_network_size: " + getMinNetworkSize());
            return;
        }

        StopWatch sw = new StopWatch().start();
        logger.info("start finding subnetworks (min:" + getMinNetworkSize() + ") " + Helper.getMemInfo());
        logger.info("Subnetwork removal jobs: " + getPrepareJobs());
        logger.info("Graph nodes: " + Helper.nf(getGraphHopperStorage().getNodes()));
        logger.info("Graph edges: " + Helper.nf(getGraphHopperStorage().getEdges()));
        for (currentWriter = 0; currentWriter < getPrepareJobs().size(); ++currentWriter) {
            PrepareJob job = getPrepareJobs().get(currentWriter);
            logger.info("--- vehicle: '" + job.getName() + "'");
            removeSmallSubNetworks(job.getAccessEnc(), job.getTurnCostProvider());
        }
        markNodesRemovedIfUnreachable();

        if (shouldOptimize) {
            optimize();
            logger.info("Finished finding and removing subnetworks for " + getPrepareJobs().size() + " vehicles, took: " + sw.stop().getSeconds() + "s, " + Helper.getMemInfo());
        } else {
            logger.info("Skipping optimization of subnetworks");
        }
    }

    protected int blockEdgesForNode(EdgeExplorer explorer, BooleanEncodedValue accessEnc, int node) {
        int removedEdges = 0;
        EdgeIterator edge = explorer.setBaseNode(node);
        while (edge.next()) {
            if (!edge.get(accessEnc) && !edge.getReverse(accessEnc))
                continue;
            edge.set(accessEnc, false).setReverse(accessEnc, false);
            try {
                writers.get(currentWriter).writeEdge(edge.fetchWayGeometry(FetchMode.ALL), SubnetworkType.ISLAND,
                        edgeIdToWayId.getOsmId(edge.getEdge()), getPrepareJobs().get(currentWriter).getName());
            } catch (IOException e) {
                logger.catching(e);
                System.exit(1);
            }
            removedEdges++;
        }
        return removedEdges;
    }

    public enum SubnetworkType {
        UNDEFINED,
        ISLAND,
        SINK_SOURCE
    }
}
