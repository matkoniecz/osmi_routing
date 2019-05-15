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
import java.nio.BufferUnderflowException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.carrotsearch.hppc.cursors.IntCursor;
import com.carrotsearch.hppc.cursors.IntObjectCursor;
import com.graphhopper.coll.GHIntHashSet;
import com.graphhopper.coll.GHIntObjectHashMap;
import com.graphhopper.routing.util.EdgeFilter;
import com.graphhopper.storage.GraphHopperStorage;
import com.graphhopper.storage.NodeAccess;
import com.graphhopper.storage.index.LocationIndex;
import com.graphhopper.storage.index.LocationIndexTree;
import com.graphhopper.storage.index.QueryResult;
import com.graphhopper.storage.index.QueryResult.Position;
import com.graphhopper.util.AngleCalc;
import com.graphhopper.util.EdgeExplorer;
import com.graphhopper.util.EdgeIterator;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.Parameters;
import com.graphhopper.util.PointList;
import com.graphhopper.util.shapes.GHPoint;

import de.geofabrik.osmi_routing.algorithm.DijkstraWithLimits;
import de.geofabrik.osmi_routing.flag_encoders.AllRoadsFlagEncoder;
import de.geofabrik.osmi_routing.flag_encoders.AllRoadsFlagEncoder.RoadClass;
import de.geofabrik.osmi_routing.reader.BarriersHook;

public class UnconnectedFinder implements Runnable {

    static final Logger logger = LogManager.getLogger(OsmiRoutingMain.class.getName());

    private GraphHopperStorage storage;
    private LocationIndex index;
    ThreadSafeOsmIdNoExitStoreAccessor nodeInfoStore;
    BarriersHook barriersHook;
    AllRoadsFlagEncoder encoder;
    private double maxDistance;
    DijkstraWithLimits dijkstra;
    AngleCalc angleCalc;
    private final OutputListener listener;
    private List<MissingConnection> results;
    private int startId;
    private int count;
    Map<RoadClass, int[]> priorities;
    private boolean doRouting;

    public UnconnectedFinder(GraphHopperSimple hopper, AllRoadsFlagEncoder encoder,
            double maxDistance, GraphHopperStorage graphhopperStorage,
            ThreadSafeOsmIdNoExitStoreAccessor infoStore, BarriersHook barriersHook,
            OutputListener listener, int start, int count, Map<RoadClass, int[]> priorities,
            boolean doRouting) {
        this.encoder = encoder;
        this.maxDistance = maxDistance;
        this.angleCalc = new AngleCalc();
        this.storage = graphhopperStorage;
        this.dijkstra = new DijkstraWithLimits(this.storage, 80, this.maxDistance);
        this.index = (LocationIndexTree) hopper.getLocationIndex();
        this.nodeInfoStore = infoStore;
        this.barriersHook = barriersHook;
        this.listener = listener;
        this.startId = start;
        this.count = count;
        this.priorities = priorities;
        this.doRouting = doRouting;
    }

    public boolean ready() {
        return startId != -1;
    }

    /**
     * Return the angle betweeen two edges with the given orientations (east-based, -180° to +180°).
     *
     * If matchPosition is QueryResult.Position.EDGE, the `180-result` is returned if it is
     * smaller than the result. In all other cases, the unchanged difference of the angles
     * is returned in order to allow the caller to take the orientation of the involved edges
     * into account.
     *
     * @param angle1 in degrees
     * @param angle2 in degrees
     * @param matchPosition match type returned by QueryResult.getSnappedPosition
     */
    private static double normaliseAngle(double angle1, double angle2, QueryResult.Position matchPosition) {
        if (matchPosition == QueryResult.Position.EDGE) {
            double result = Math.max(angle1, angle2) - Math.min(angle1, angle2);
            return Math.min(result, 180 - result);
        }
        return Math.max(angle1, angle2) - Math.min(angle1, angle2);
    }

    private double[] getAngleDiff(EdgeIteratorState openEnd, QueryResult matched) throws IllegalStateException {
        double[] result = {-720, 720};
        QueryResult.Position matchType = matched.getSnappedPosition();
        NodeAccess nodeAccess = storage.getNodeAccess();
        // Get the two locations of the matched edge to calculate its orientation.
        double matchedLat1 = Double.MAX_VALUE;
        double matchedLon1 = Double.MAX_VALUE;
        double matchedLat2 = matched.getSnappedPoint().getLat();
        double matchedLon2 = matched.getSnappedPoint().getLon();
        double matchedLat3 = Double.MAX_VALUE;
        double matchedLon3 = Double.MAX_VALUE;
        switch (matchType) {
        case TOWER :
            int wayIndex = matched.getWayIndex();
            PointList points = matched.getClosestEdge().fetchWayGeometry(2);
            if (wayIndex == 0) {
                // index of PointList.toGHPoint() is 0 because the PointList is retrieved without the base node
                matchedLat1 = points.getLat(0);
                matchedLon1 = points.getLon(0);
            } else {
                matchedLat1 = points.getLat(points.size() - 1);
                matchedLon1 = points.getLon(points.size() - 1);
            }
            break;
        case EDGE:
            PointList allPoints = matched.getClosestEdge().fetchWayGeometry(3);
            int matchingI = dijkstra.lowerNeighbourPillars(allPoints, matched.getSnappedPoint());
            if (matchingI < 0) {
                long osmId = nodeInfoStore.getOsmId(openEnd.getBaseNode());
                throw new IllegalStateException("Could not find a matching segment for OSM node " + Long.toString(osmId));
            }
            matchedLat1 = allPoints.getLat(matchingI);
            matchedLon1 = allPoints.getLon(matchingI);
            matchedLat3 = allPoints.getLat(matchingI + 1);
            matchedLon3 = allPoints.getLon(matchingI + 1);

            break;
        case PILLAR:
        default:
            // The matched position is a pillar node.
            // Get the two neighbouring nodes.
            PointList pointsAll = matched.getClosestEdge().fetchWayGeometry(3);
            matchedLat1 = pointsAll.getLat(matched.getWayIndex() - 1);
            matchedLon1 = pointsAll.getLat(matched.getWayIndex() - 1);
            matchedLat3 = pointsAll.getLat(matched.getWayIndex() + 1);
            matchedLon3 = pointsAll.getLat(matched.getWayIndex() + 1);
            break;
        }
        // Get the two locations of the open end edge to calculate its orientation.
        double orientationMatched12 = angleCalc.calcOrientation(matchedLat1, matchedLon1, matchedLat2, matchedLon2, false);
        double orientationMatched23 = angleCalc.calcOrientation(matchedLat2, matchedLon2, matchedLat3, matchedLon3, false);


        double openEndLat1 = nodeAccess.getLat(openEnd.getBaseNode());
        double openEndLon1 = nodeAccess.getLon(openEnd.getBaseNode());
        PointList pointsAll = openEnd.fetchWayGeometry(2);
        // index of PointList.toGHPoint() is 0 because the PointList is retrieved without the base node
        double openEndLat2 = pointsAll.getLat(0);
        double openEndLon2 = pointsAll.getLon(0);
        double orientationOpenEnd = angleCalc.calcOrientation(openEndLat1, openEndLon1, openEndLat2, openEndLon2, false);
        if (matchType != QueryResult.Position.PILLAR) {
            result[0] = normaliseAngle(Math.toDegrees(orientationMatched12), Math.toDegrees(orientationOpenEnd), matchType);
            result[1] = normaliseAngle(Math.toDegrees(orientationMatched12), Math.toDegrees(orientationOpenEnd), matchType);
        } else {
            result[0] = normaliseAngle(Math.toDegrees(orientationMatched12), Math.toDegrees(orientationOpenEnd), matchType);
            result[1] = normaliseAngle(Math.toDegrees(orientationMatched23), Math.toDegrees(orientationOpenEnd), matchType);
        }
        return result;
    }

    private Double getDistanceOnGraph(int fromNodeId, GHPoint fromLocation, QueryResult closestResult) {
        if (closestResult.getSnappedPosition() == QueryResult.Position.TOWER) {
            DijkstraWithLimits.Result result = dijkstra.route(fromNodeId, closestResult.getClosestNode());
            return result.distance;
        }
        if (closestResult.getSnappedPosition() == QueryResult.Position.PILLAR) {
            DijkstraWithLimits.Result result = dijkstra.routeToPillar(fromNodeId, closestResult.getClosestEdge(), closestResult.getSnappedPoint());
            return result.distance;
        }
        DijkstraWithLimits.Result result = dijkstra.routeBetweenPillars(fromNodeId, closestResult.getClosestEdge(), closestResult.getSnappedPoint());
        return result.distance;
    }

    private int getPriority(AllRoadsFlagEncoder.RoadClass roadClass, boolean isPrivate, double distance) {
        int[] thisClassPriorities = priorities.getOrDefault(roadClass, new int[]{0, 0, 0, 0});
        double categoryWith = maxDistance / 4;
        int index = (int) (distance / categoryWith);
        index = Math.min(3, index);
        int priority = thisClassPriorities[index];
        if (isPrivate) {
            priority += 1;
        }
        return priority;
    }

    private void checkNode(int id) throws IOException, IllegalStateException {
        EdgeExplorer explorer = storage.createEdgeExplorer();
        if (storage.isNodeRemoved(id)) {
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
        RoadClass roadClass = RoadClass.UNDEFINED;
        boolean isPrivate = false;

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
            roadClass = encoder.getRoadClass(iter);
            isPrivate = encoder.isPrivateAccess(iter);
            ++edgesCount;
        }
        if (edgesCount > 1 || adjNode == -1 || blindEndEdgeId == -1) {
            // more than one edge leading to this node
            return;
        }

        // Retrieve edges connected the only adjacent node because they are often matched by the
        // location index lookup but are usually false positives. We exclude them before we later
        // check their geometric distance on the graph.
        EdgeIteratorState firstEdge = storage.getEdgeIteratorState(blindEndEdgeId, adjNode);
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
        List<QueryResult> result = ((LocationIndexTree) index).findNClosest(fromPoints.getLat(0), fromPoints.getLon(0), EdgeFilter.ALL_EDGES, maxDistance);
        // distance to closest accpeted match
        double distanceClosest = Double.MAX_VALUE;
        QueryResult closestResult = null;
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
                closestResult = r;
            }
        }
        if (distanceClosest == Double.MAX_VALUE) {
            return;
        }
        // check if the closest node intersects with a barrier
        if (barriersHook.crossesBarrier(fromPoints.getLon(0), fromPoints.getLat(0), closestResult.getSnappedPoint().lon, closestResult.getSnappedPoint().lat)) {
            return;
        }
        // ratio between distance over graph and beeline; ratios within the range (1.0,4.0) are
        // an indicator for false positives.
        double distanceOnGraph = doRouting ? getDistanceOnGraph(id, fromPoints.toGHPoint(0), closestResult) : 0;
        GHPoint queryPoint = closestResult.getQueryPoint();
        GHPoint snappedPoint = closestResult.getSnappedPoint();
        double[] angleDiff = getAngleDiff(firstEdge, closestResult);
        int priority = getPriority(roadClass, isPrivate, distanceClosest);
        results.add(new MissingConnection(queryPoint, snappedPoint, distanceClosest,
                distanceOnGraph, id, angleDiff, osmId,
                closestResult.getSnappedPosition(), roadClass, isPrivate, priority));
    }

    private void runAndCatchExceptions() {
        try {
            for (int nodeId = startId; nodeId < startId + count; ++nodeId) {
                checkNode(nodeId);
            }
        } catch (IOException e) {
            throw new RuntimeException("Unable to read internal data", e);
        } catch (Exception e) {
            throw new RuntimeException("Unable to find closest edge although there should be one.", e);
        }
    }

    @Override
    public void run() {
        results = new ArrayList<MissingConnection>();
        try {
            runAndCatchExceptions();
            listener.complete(results);

        } catch (RuntimeException e) {
            listener.error(e);
        }
    }

}
