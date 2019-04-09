package de.geofabrik.osmi_routing;

import com.graphhopper.GraphHopper;
import com.graphhopper.routing.util.EdgeFilter;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.storage.GraphHopperStorage;
import com.graphhopper.storage.index.LocationIndexTree;
import com.graphhopper.storage.index.QueryResult;
import com.graphhopper.util.EdgeExplorer;
import com.graphhopper.util.EdgeIterator;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.PointList;
import com.graphhopper.util.shapes.GHPoint;

import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;

import org.apache.logging.log4j.LogManager;

public class OsmiRoutingMain {

    static {
        System.setProperty("log4j2.configurationFile", "logging.xml");
    }

    static final Logger logger = LogManager.getLogger(OsmiRoutingMain.class.getName());

    public static void main(String[] args) {
        if (args.length < 3) {
            System.err.println("ERROR: too few arguments.\nUsage: PROGRAM_NAME INFILE TMP_DIR OUTFILE");
            System.exit(1);
        }
        GraphHopper hopper = new GraphHopperSimple().forServer();
        hopper.setDataReaderFile(args[0]);
        hopper.setGraphHopperLocation(args[1]);
        hopper.setCHEnabled(false);
        hopper.setEncodingManager(EncodingManager.create("car"));
        hopper.importOrLoad();
        
        GraphHopperStorage storage = hopper.getGraphHopperStorage();
        EdgeExplorer explorer = storage.createEdgeExplorer();
        LocationIndexTree index = (LocationIndexTree) hopper.getLocationIndex();
        int nodes = storage.getNodes();
        logger.info("Iterate over {} nodes and look for nearby nodes.", nodes);
        for (int start = 0; start < nodes; start++) {
            if (start % 10000 == 0) {
                logger.info("Detection of unconnected roads: {} of {}", start, nodes);
            }
            if (storage.isNodeRemoved(start)) {
                continue;
            }
            EdgeIterator iter = explorer.setBaseNode(start);
            ArrayList<Integer> edges = new ArrayList<Integer>();
            ArrayList<Integer> adjNodes = new ArrayList<Integer>();

            // get all edges and neighbour nodes
            while (iter.next()) {
                edges.add(iter.getEdge());
                adjNodes.add(iter.getAdjNode());
            }
            if (edges.size() > 1) {
                // more than one edge leading to this node
                continue;
            }
            EdgeIteratorState firstEdge = storage.getEdgeIteratorState(edges.get(0), adjNodes.get(0));
            PointList fromPoints = firstEdge.fetchWayGeometry(1);
            List<QueryResult> result = index.findNClosest(fromPoints.getLat(0), fromPoints.getLon(0), EdgeFilter.ALL_EDGES, 5);
            double distanceClosest = Integer.MAX_VALUE;
            GHPoint queryPoint = null;
            GHPoint snappedPoint = null;
            int snapEdge = -1;
            for (QueryResult r : result) {
                EdgeIteratorState foundEdge = r.getClosestEdge();
                // check if the foundEdge is connected to our node
                if (foundEdge.getEdge() == edges.get(0)) {
                    continue;
                }
                // check if the found node is the other end of our edge
                if (r.getClosestNode() == adjNodes.get(0)) {
                    continue;
                }
//                boolean connectedToSearchPoint = false;
//                for (int e : edges) {
//                    if (e == foundEdge.getEdge()) {
//                        connectedToSearchPoint = true;
//                    }
//                }
//                if (connectedToSearchPoint) {
//                    continue;
//                }
                double distance = r.getQueryDistance();
                if (distance > 0 && distance < distanceClosest) {
                    distanceClosest = distance;
                    queryPoint = r.getQueryPoint();
                    snappedPoint = r.getSnappedPoint();
                    snapEdge = foundEdge.getEdge();
                }
            }
            if (snappedPoint != null) {
                System.out.println("Found potential missing connection between " + queryPoint.toShortString() + " and " + snappedPoint.toShortString());
            }
        }
        hopper.close();
    }
}
