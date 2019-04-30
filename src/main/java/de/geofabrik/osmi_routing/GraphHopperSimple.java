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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.graphhopper.reader.DataReader;
import com.graphhopper.reader.osm.GraphHopperOSM;
import com.graphhopper.reader.osm.OSMReader;
import com.graphhopper.reader.osm.OSMReaderHook;
import com.graphhopper.routing.AlgorithmOptions;
import com.graphhopper.routing.Path;
import com.graphhopper.routing.QueryGraph;
import com.graphhopper.routing.RoutingAlgorithm;
import com.graphhopper.routing.RoutingAlgorithmFactory;
import com.graphhopper.routing.util.EdgeFilter;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.util.FlagEncoder;
import com.graphhopper.routing.util.HintsMap;
import com.graphhopper.routing.util.TraversalMode;
import com.graphhopper.routing.weighting.Weighting;
import com.graphhopper.storage.GraphHopperStorage;
import com.graphhopper.storage.index.LocationIndexTree;
import com.graphhopper.storage.index.QueryResult;
import com.graphhopper.storage.index.QueryResult.Position;
import com.graphhopper.util.EdgeExplorer;
import com.graphhopper.util.EdgeIterator;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.Parameters;
import com.graphhopper.util.PointList;
import com.graphhopper.util.shapes.GHPoint;


public class GraphHopperSimple extends GraphHopperOSM {

    static final Logger logger = LogManager.getLogger(OsmiRoutingMain.class.getName());

    String outputFile;
    FlagEncoder encoder;
    EncodingManager encodingManager;
    OsmIdAndNoExitStore nodeInfoStore;
    GeoJSONWriter writer;
    NoExitHook hook;

    public GraphHopperSimple(String args[]) {
        super();
        outputFile = args[2];
        nodeInfoStore = new OsmIdAndNoExitStore(getGraphHopperLocation());
        hook = new NoExitHook(nodeInfoStore);
        setDataReaderFile(args[0]);
        setGraphHopperLocation(args[1]);
        setCHEnabled(false);
        // Disable sorting of graph because that would overwrite the values stored in the additional properties field of the graph.
        setSortGraph(false);
        encoder = new AllRoadsFlagEncoder();
        encodingManager = EncodingManager.create(encoder);
        setEncodingManager(encodingManager);
    }

    /**
     * Overrides method in GrapHopper class calling removal of subnetworks code.
     */
    @Override
    protected void cleanUp() {
        logger.info("Skipping removal of subnetworks.");
    }

    @Override
    protected DataReader createReader(GraphHopperStorage ghStorage) {
        OSMReader reader = new OSMReader(ghStorage);
        reader.register(hook);
        return initDataReader(reader);
    }

    private Double getDistanceOnGraph(int fromNodeId, GHPoint fromLocation, int toNodeId, GHPoint toLocation) {
        HintsMap hints = new HintsMap("shortest");
        QueryGraph queryGraph = new QueryGraph(getGraphHopperStorage());
        Weighting weighting = createWeighting(hints, encoder, queryGraph);
        AlgorithmOptions algoOpts = AlgorithmOptions.start().
                algorithm(Parameters.Algorithms.DIJKSTRA).traversalMode(TraversalMode.NODE_BASED).weighting(weighting).
                maxVisitedNodes(80).
                hints(hints).
                build();
        RoutingAlgorithmFactory algoFactory = getAlgorithmFactory(hints);
        
        // We have to create two fake QueryResult objects and call QueryGraph.lookup with them.
        // Our QueryResult objects point to tower nodes and creating virtual edges is not required in our case.
        // However, GraphHopper insists on calling QueryGraph.lookup and throws
        // java.lang.IllegalStateException("Call lookup before using this graph") when calling
        // RoutingAlgorithmFactory.createAlgo.
        QueryResult qr1 = new QueryResult(fromLocation.lat, fromLocation.lon);
        qr1.setSnappedPosition(Position.TOWER);
        QueryResult qr2 = new QueryResult(toLocation.lat, toLocation.lon);
        qr2.setSnappedPosition(Position.TOWER);
        QueryResult[] qrs = {qr1, qr2};
        queryGraph.lookup(Arrays.asList(qrs));
        
        RoutingAlgorithm algo = algoFactory.createAlgo(queryGraph, algoOpts);
        List<Path> tmpPathList = algo.calcPaths(fromNodeId, toNodeId);
        if (algo.getVisitedNodes() >= algoOpts.getMaxVisitedNodes()) {
            // no path found because max_visited_notes limit reached
            return Double.MAX_VALUE;
        }
        return tmpPathList.get(0).getDistance();
    }

    private void checkNode(int id) throws IOException {
        EdgeExplorer explorer = getGraphHopperStorage().createEdgeExplorer();
        LocationIndexTree index = (LocationIndexTree) getLocationIndex();
        if (getGraphHopperStorage().isNodeRemoved(id)) {
            return;
        }
        long osmId;
        try {
            osmId = nodeInfoStore.getOsmId(id);
        } catch (NullPointerException e) {
            return;
        }
        if (nodeInfoStore.getNoExit(id)) {
            // node is tagged with noexit=yes
            return;
        }
        EdgeIterator iter = explorer.setBaseNode(id);
        
        // edge ID of the blind end node we are currently working on
        int blindEndEdgeId = -1;
        // number of edges connected with this node
        int edgesCount = 0;
        // ID of the adjacent node
        int adjNode = -1;
        
        // get all edges and neighbour nodes
        while (iter.next()) {
            blindEndEdgeId = iter.getEdge();
            adjNode = iter.getAdjNode();
            ++edgesCount;
        }
        if (edgesCount > 1 || adjNode == -1 || blindEndEdgeId == -1) {
            // more than one edge leading to this node
            return;
        }
        
        // Retrieve edges connected the only adjacent node because they are often matched by the
        // location index lookup but are usually false positives. We exclude them before we later
        // check their geometric distance on the graph.
        EdgeIteratorState firstEdge = getGraphHopperStorage().getEdgeIteratorState(blindEndEdgeId, adjNode);
        PointList fromPoints = firstEdge.fetchWayGeometry(1);
        List<Integer> neighboursOfAdjNode = new ArrayList<Integer>();
        EdgeIterator adjIter = explorer.setBaseNode(adjNode);
        while (adjIter.next()) {
            int adjAdjNode = adjIter.getAdjNode();
            if (id != adjAdjNode) {
                neighboursOfAdjNode.add(adjAdjNode);
            }
        }
        // fetch edge geometry
        List<QueryResult> result = index.findNClosest(fromPoints.getLat(0), fromPoints.getLon(0), EdgeFilter.ALL_EDGES, 20);
        // distance to closest accpeted match
        double distanceClosest = Double.MAX_VALUE;
        // ratio between distance over graph and beeline; ratios within the range (1.0,4.0) are
        // an indicator for false positives.
        double networkBeelineRatio = 0;
        GHPoint queryPoint = null;
        GHPoint snappedPoint = null;
        int snapPointID = -1;
        // iterate over results
        for (QueryResult r : result) {
            EdgeIteratorState foundEdge = r.getClosestEdge();
            // Check if the matched edge is the only edge connected to our node.
            if (foundEdge.getEdge() == blindEndEdgeId) {
                continue;
            }
            // Check if the matched node is our adjacent node.
            if (r.getClosestNode() == adjNode) {
                continue;
            }
            // Check if matched node is not in the neighbourhood of our adjacent node.
            if (neighboursOfAdjNode.contains(r.getClosestNode())) {
                continue;
            }
            double distance = r.getQueryDistance();
            if (distance > 0 && distance < distanceClosest) {
                distanceClosest = distance;
                queryPoint = r.getQueryPoint();
                snappedPoint = r.getSnappedPoint();
                snapPointID = r.getClosestNode();
                double distanceOnGraph = getDistanceOnGraph(id, queryPoint, snapPointID, snappedPoint);
                networkBeelineRatio = Math.min(distanceOnGraph / distance, 2000);
            }
        }
        if (snappedPoint != null) {
            writer.write(queryPoint, "open end", "distance", distanceClosest, "node_id", id, "ratio", networkBeelineRatio, osmId);
            writer.write(snappedPoint, "snap point", "node_id", snapPointID, "refersTo", id, "ratio", networkBeelineRatio, 0);
        }
    }

    private void lookForUnconnected() {
        GraphHopperStorage storage = getGraphHopperStorage();
        int nodes = storage.getNodes();
        logger.info("Iterate over {} nodes and look for nearby nodes.", nodes);
        try {
            writer = new GeoJSONWriter(outputFile);
            for (int start = 0; start < nodes; start++) {
                if (start % 10000 == 0) {
                    writer.flush();
                    logger.info("Detection of unconnected roads: {} of {}", start, nodes);
                }
                checkNode(start);
            }
            writer.close();
        } catch (IOException e) {
            logger.fatal("error opening output file");
            System.exit(1);
        }
        logger.info("finished writing");
        
    }

    public void run() {
        importOrLoad();
        lookForUnconnected();
        close();
    }
}