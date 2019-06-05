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
import com.graphhopper.util.DistanceCalc;
import com.graphhopper.util.DistanceCalc2D;
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
    OsmIdStore.ThreadSafeOsmIdAccessor wayIdStore;
    BarriersHook barriersHook;
    AllRoadsFlagEncoder encoder;
    private double maxDistance;
    DijkstraWithLimits dijkstra;
    AngleCalc angleCalc;
    DistanceCalc distanceCalc;
    private final OutputListener listener;
    private List<MissingConnection> resultsMissingConnections;
    private List<DuplicatedEdge> resultsDuplicatedEdges;
    private int startId;
    private int count;
    Map<RoadClass, int[]> priorities;
    private boolean doRouting;

    public UnconnectedFinder(GraphHopperSimple hopper, AllRoadsFlagEncoder encoder,
            double maxDistance, GraphHopperStorage graphhopperStorage,
            ThreadSafeOsmIdNoExitStoreAccessor infoStore, OsmIdStore.ThreadSafeOsmIdAccessor wayIdStore, BarriersHook barriersHook,
            OutputListener listener, int start, int count, Map<RoadClass, int[]> priorities,
            boolean doRouting) {
        this.encoder = encoder;
        this.maxDistance = maxDistance;
        this.angleCalc = new AngleCalc();
        this.distanceCalc = new DistanceCalc2D();
        this.storage = graphhopperStorage;
        this.dijkstra = new DijkstraWithLimits(this.storage, 80, this.maxDistance);
        this.index = (LocationIndexTree) hopper.getLocationIndex();
        this.nodeInfoStore = infoStore;
        this.wayIdStore = wayIdStore;
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
     */
    private static double normaliseAngle(double angle1, double angle2) {
        double result = Math.max(angle1, angle2) - Math.min(angle1, angle2);
        return Math.min(result, 360 - result);
    }

    private double getAngleDiff(EdgeIteratorState openEnd, QueryResult matched) throws IllegalStateException {
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
            PointList points = matched.getClosestEdge().fetchWayGeometry(3);
            if (wayIndex == 0) {
                matchedLat1 = points.getLat(1);
                matchedLon1 = points.getLon(1);
            } else {
                matchedLat1 = points.getLat(points.size() - 2);
                matchedLon1 = points.getLon(points.size() - 2);
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
            return normaliseAngle(Math.toDegrees(orientationMatched12), Math.toDegrees(orientationOpenEnd)/*, matchType*/);
        } else {
            return 0.5 * (normaliseAngle(Math.toDegrees(orientationMatched12), Math.toDegrees(orientationOpenEnd))
                    + normaliseAngle(Math.toDegrees(orientationMatched23), Math.toDegrees(orientationOpenEnd)));
        }
    }

    private Double getDistanceOnGraph(int fromNodeId, QueryResult closestResult) {
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
        double categoryWidth = maxDistance / 4;
        int index = (int) (distance / categoryWidth);
        index = Math.min(3, index);
        int priority = thisClassPriorities[index];
        if (priority > 0 && distance < 1.0) {
            priority = Math.min(1, priority - 2);
        } else if (priority > 0 && distance < 2.0) {
            priority = Math.min(1, priority - 1);
        }
        if (isPrivate) {
            priority += 1;
        }
        return priority;
    }

    /**
     * Get increment or decrement for error importance class.
     *
     * For parking aisles and driveways: If the angle between the open end and the connection is
     * nearly 90° and the difference of the normalised orientation of both edges is nearly 0°,
     * the priority is reduced by 1.
     *
     * For footways, paths and steps: The priority is reduced by 1 if the conditions above apply
     * (but larger thresholds) and the distances is larger than the length of the edge. 
     */
    private int getImportanceDecrement(RoadClass roadClass, EdgeIteratorState openEnd, QueryResult queryResult) {
        if (roadClass != RoadClass.FOOTWAY && roadClass != RoadClass.PATH
                && roadClass != RoadClass.SERVICE_PARKING_AISLE && roadClass != RoadClass.SERVICE_DRIVEWAY
                && roadClass != RoadClass.STEPS) {
            return 0;
        }

        // Get orientation of the open end
        double openEndLat1 = storage.getNodeAccess().getLat(openEnd.getBaseNode());
        double openEndLon1 = storage.getNodeAccess().getLon(openEnd.getBaseNode());
        PointList pointsAll = openEnd.fetchWayGeometry(2);
        // index of PointList.toGHPoint() is 0 because the PointList is retrieved without the base node
        double openEndLat2 = pointsAll.getLat(0);
        double openEndLon2 = pointsAll.getLon(0);
        double orientationOpenEnd = Math.toDegrees(angleCalc.calcOrientation(openEndLat1, openEndLon1, openEndLat2, openEndLon2, false));

        // Get orientation of the connection line.
        double matchedPointLon = queryResult.getSnappedPoint().lon;
        double matchedPointLat = queryResult.getSnappedPoint().lat;
        double orientationConnection = Math.toDegrees(angleCalc.calcOrientation(openEndLat1, openEndLon1, matchedPointLat, matchedPointLon));

        // Get 0 <= alpha <= 360
        double openEndConnectionAngle = Math.abs(orientationOpenEnd - orientationConnection);
        // Get 0 <= alpha <= 180
        if (openEndConnectionAngle > 180) {
            openEndConnectionAngle = 360 - openEndConnectionAngle;
        }
        openEndConnectionAngle = Math.min(openEndConnectionAngle, 180 - openEndConnectionAngle);

        if (roadClass == RoadClass.SERVICE_DRIVEWAY || roadClass == RoadClass.SERVICE_PARKING_AISLE) {
            // Get angle between open end and matched edge
            double angleBetweenEdges = getAngleDiff(openEnd, queryResult);
            angleBetweenEdges = Math.min(angleBetweenEdges, 180 - angleBetweenEdges);
            if (openEndConnectionAngle >= 86 && openEndConnectionAngle <= 90
                    && angleBetweenEdges <= 4) {
                return 1;
            }
        }
        if (roadClass == RoadClass.PATH || roadClass == RoadClass.FOOTWAY || roadClass == RoadClass.STEPS) {
            // For footways it is adviseable to compare the distance on the graph with the beeline distance. If they
            // don't differ a lot, it is likely a false positive or less important issue.
            double distanceOnGraph = doRouting ? getDistanceOnGraph(openEnd.getBaseNode(), queryResult) : 1000;
            double ratio = distanceOnGraph / queryResult.getQueryDistance();
            if (ratio < 2) {
                // Hide it totally from output
                return 100;
            }
            if (ratio < 6) {
                return 1;
            }
        }
        return 0;
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
        ArrayList<EdgeIteratorState> edgeIteratorStates = new ArrayList<EdgeIteratorState>(5);
        // IDs of the adjacent node
        ArrayList<Integer> adjNodes = new ArrayList<Integer>(5);

        // get all edges and neighbour nodes
        while (iter.next()) {
            edgeIteratorStates.add(iter.detach(false));
            adjNodes.add(iter.getAdjNode());
            roadClass = encoder.getRoadClass(iter);
            isPrivate = encoder.isPrivateAccess(iter);
        }
        // check for duplicated edges
        int adjNode = -1;
        for (int i = 0; i < adjNodes.size() - 1; ++i) {
            adjNode = adjNodes.get(i);
            for (int j = i + 1; j < adjNodes.size(); ++j) {
                if (adjNodes.get(j) == adjNode) {
                    // Compare geometry
                    EdgeIteratorState edge1 = edgeIteratorStates.get(i);
                    EdgeIteratorState edge2 = edgeIteratorStates.get(j);
                    if (edge1.getDistance() != edge2.getDistance()) {
                        continue;
                    }
                    PointList points = edge1.fetchWayGeometry(3);
                    if (points.equals(edge2.fetchWayGeometry(3))) {
                        long wayId1 = wayIdStore.getOsmId(edge1.getEdge());
                        long wayId2 = wayIdStore.getOsmId(edge2.getEdge());
                        boolean areaInvolved = encoder.isArea(edge1) || encoder.isArea(edge2);
                        resultsDuplicatedEdges.add(new DuplicatedEdge(points, wayId1, wayId2, areaInvolved));
                        break;
                    }
                }
            }
        }
        if (adjNodes.size() > 1 || adjNode == -1 || edgeIteratorStates.size() != 1) {
            // more than one or zero edges leading to this node
            return;
        }

        // Retrieve edges connected the only adjacent node because they are often matched by the
        // location index lookup but are usually false positives. We exclude them before we later
        // check their geometric distance on the graph.
        EdgeIteratorState firstEdge = edgeIteratorStates.get(0);
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
        boolean endLevelValid = encoder.isLevelValid(firstEdge);
        int endMinLevel = encoder.getLevel(firstEdge);
        int endMaxLevel = encoder.getLevelDiff(firstEdge);
        for (QueryResult r : result) {
            EdgeIteratorState foundEdge = r.getClosestEdge();
            // Check if the matched edge is the only edge connected to our node.
            if (foundEdge.getEdge() == edgeIteratorStates.get(0).getEdge()) {
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
            // Check if matched edge is on same level
            if (endLevelValid && encoder.isLevelValid(r.getClosestEdge())) {
                int foundMinLevel = encoder.getLevel(r.getClosestEdge());
                int foundMaxLevel = foundMinLevel + encoder.getLevelDiff(r.getClosestEdge());
                if (foundMinLevel > endMaxLevel || foundMaxLevel < endMinLevel) {
                    continue;
                }
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
        GHPoint queryPoint = closestResult.getQueryPoint();
        GHPoint snappedPoint = closestResult.getSnappedPoint();
        int priority = getPriority(roadClass, isPrivate, distanceClosest);
        priority += getImportanceDecrement(roadClass, firstEdge, closestResult);
        if (priority > 0 && priority <= 6) {
            resultsMissingConnections.add(new MissingConnection(queryPoint, snappedPoint, distanceClosest, id, osmId,
                    closestResult.getSnappedPosition(), roadClass, isPrivate, priority));
        }
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
        resultsMissingConnections = new ArrayList<MissingConnection>();
        resultsDuplicatedEdges = new ArrayList<DuplicatedEdge>();
        try {
            runAndCatchExceptions();
            listener.complete(resultsMissingConnections, resultsDuplicatedEdges);

        } catch (RuntimeException e) {
            listener.error(e);
        }
    }

}
