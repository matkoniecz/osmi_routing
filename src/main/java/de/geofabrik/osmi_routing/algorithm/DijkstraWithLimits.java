package de.geofabrik.osmi_routing.algorithm;

import com.carrotsearch.hppc.cursors.IntObjectCursor;
import com.graphhopper.coll.GHIntObjectHashMap;
import com.graphhopper.storage.GraphHopperStorage;
import com.graphhopper.util.DistanceCalc;
import com.graphhopper.util.DistancePlaneProjection;
import com.graphhopper.util.EdgeExplorer;
import com.graphhopper.util.EdgeIterator;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.PointList;
import com.graphhopper.util.shapes.GHPoint;

/**
 * Simplified implementation of Dijkstra's algorithm.
 * 
 * This implementation aborts if the maximum distance is reached or the maximum number of nodes is reached.
 * @author Michael Reichert
 *
 */
public class DijkstraWithLimits {

    protected class DijkstraNodeInfo {
        public double distance = 0;
        public int baseNode = -1;
        public boolean visited = false;

        public DijkstraNodeInfo(double distance, int baseNode) {
            this.distance = distance;
            this.baseNode = baseNode;
        }
    }

    public enum Status {
        OK, TOO_LONG, TOO_MANY_NODES;
    }

    public class Result {
        public Status status;
        public double distance;

        public Result(Status status, double distance) {
            this.status = status;
            this.distance = distance;
        }
    }

    GraphHopperStorage storage;
    int maxNodes;
    double maxDistance;
    DistanceCalc distCalc;

    public DijkstraWithLimits(GraphHopperStorage storage, int maxNodes, double maxDistance, DistanceCalc distCalc) {
        this.storage = storage;
        this.maxNodes = maxNodes;
        this.maxDistance = maxDistance;
        this.distCalc = distCalc;
    }

    public DijkstraWithLimits(GraphHopperStorage storage, int maxNodes, double maxDistance) {
        this(storage, maxNodes, maxDistance, new DistancePlaneProjection());
    }

    double distanceOnEdge(PointList points, GHPoint location) {
        double distance = 0;
        final double EPSILON = 0.0000001;
        int index = 0;
        for (int i = 0; i < points.size() - 1; ++i) {
            if (Math.abs(points.getLat(i) - location.lat) < EPSILON && Math.abs(points.getLon(i) - location.lon) < EPSILON) {
                break;
            }
            distance += distCalc.calcDist(points.getLat(i), points.getLon(i), points.getLat(i+1), points.getLon(i+1));
        }
        if (index == points.size() - 1) {
            throw new IllegalStateException("Failed to find pillar in its edge");
        }
        return distance;
    }

    public int lowerNeighbourPillars(PointList allPoints, GHPoint point) {
        int matchingI = -1;
        double mMin = Double.MAX_VALUE;
        for (int i = 0; i < allPoints.size() - 1; ++i) {
            double minLat = Math.min(allPoints.getLat(i), allPoints.getLat(i + 1));
            double maxLat = Math.max(allPoints.getLat(i), allPoints.getLat(i + 1));
            double minLon = Math.min(allPoints.getLon(i), allPoints.getLon(i + 1));
            double maxLon = Math.max(allPoints.getLon(i), allPoints.getLon(i + 1));
            if (point.getLat() < minLat || point.getLat() > maxLat
                    || point.getLon() < minLon || point.getLon() > maxLon) {
                continue;
            }
            double mExpected, mThis;
            // Check if ratio matches
            // First check if the matching edge segment goes straightly or almost straightly in north-south direction.
            if (maxLon - minLon < 0.0000005) {
                // almost north-south, work with inverse value: dx/dy
                mExpected = (allPoints.getLon(i + 1) - allPoints.getLon(i)) / (allPoints.getLat(i + 1) - allPoints.getLat(i));
                mThis = (allPoints.getLon(i + 1) - point.getLon()) / (allPoints.getLat(i + 1) - point.getLat());
            } else {
                // all other cases: dy/dx
                mExpected = (allPoints.getLat(i + 1) - allPoints.getLat(i)) / (allPoints.getLon(i + 1) - allPoints.getLon(i));
                mThis = (allPoints.getLat(i + 1) - point.getLat()) / (allPoints.getLon(i + 1) - point.getLon());
            }
            if (Math.abs(mExpected - mThis) < mMin) {
                matchingI = i;
            }
        }
        return matchingI;
    }

    public Result routeBetweenPillars(int fromNodeId, EdgeIteratorState destinationEdge, GHPoint destinationLocation) {
        // find neighbour pillars first
        PointList geometry = destinationEdge.fetchWayGeometry(3);
        int neighbour1Index = lowerNeighbourPillars(geometry, destinationLocation);
        GHPoint neighbour1 = geometry.toGHPoint(neighbour1Index);
        GHPoint neighbour2 = geometry.toGHPoint(neighbour1Index + 1);
        // get distance to these neighbours
        double distanceTo1 = distCalc.calcDist(neighbour1.lat, neighbour1.lon, destinationLocation.lat, destinationLocation.lon);
        double distanceTo2 = distCalc.calcDist(neighbour2.lat, neighbour2.lon, destinationLocation.lat, destinationLocation.lon);
        // get distance from these neighbours to the base/adjacent node
        double distanceOnEdge1 = (neighbour1Index == 0) ? 0 : distanceOnEdge(geometry, destinationLocation);
        geometry.reverse();
        double distanceOnEdge2 = (neighbour1Index == geometry.size() - 1) ? 0 : distanceOnEdge(geometry, destinationLocation);
        // get distance from start of our routing request to base and adjacent node of the destination edge
        Result graphToBase = route(fromNodeId, destinationEdge.getBaseNode());
        Result graphToAdj = route(fromNodeId, destinationEdge.getAdjNode());
        double totalDistance1 = graphToBase.distance + distanceTo1 + distanceOnEdge1;
        double totalDistance2 = graphToAdj.distance + distanceTo2 + distanceOnEdge2;
        if (graphToBase.status != Status.OK || totalDistance1 > totalDistance2) {
            graphToAdj.distance = totalDistance2;
            return graphToAdj;
        }
        graphToBase.distance = totalDistance1;
        return graphToBase;
    }

    public Result routeToPillar(int fromNodeId, EdgeIteratorState destinationEdge, GHPoint destinationLocation) {
        // get route to either base or adjacent node of the destination edge
        Result resultToBase = route(fromNodeId, destinationEdge.getBaseNode());
        Result resultToAdj = route(fromNodeId, destinationEdge.getAdjNode());
        if (resultToBase.status != Status.OK && resultToAdj.status != Status.OK){
            return resultToBase;
        }
        // Get distance from destination pillar node to both base and adjacent node.
        double distanceToBase = Double.MAX_VALUE;
        double distanceToAdj = Double.MAX_VALUE;
        if (resultToBase.status == Status.OK) {
            distanceToBase = distanceOnEdge(destinationEdge.fetchWayGeometry(3), destinationLocation);
        }
        EdgeIteratorState destinationEdgeReverse = storage.getEdgeIteratorState(destinationEdge.getEdge(), destinationEdge.getBaseNode());
        if (resultToAdj.status == Status.OK) {
            distanceToAdj = distanceOnEdge(destinationEdgeReverse.fetchWayGeometry(3), destinationLocation);
        }
        if (distanceToBase < distanceToAdj) {
            resultToBase.distance += distanceToBase;
            return resultToBase;
        }
        resultToAdj.distance += distanceToAdj;
        return resultToAdj;
    }

    public Result route(int fromNodeId, int toNodeId) {
        GHIntObjectHashMap<DijkstraNodeInfo> nodeInfo = new GHIntObjectHashMap<DijkstraNodeInfo>(100);
        EdgeExplorer explorer = storage.createEdgeExplorer();
        nodeInfo.put(fromNodeId, new DijkstraNodeInfo(0, -1));
        EdgeIterator adjIter = explorer.setBaseNode(fromNodeId);

        while (nodeInfo.size() < maxNodes) {
            // get node with shortest distance
            int baseNode = -1;
            double shortestDistance = Double.MAX_VALUE;
            DijkstraNodeInfo info = null;
            for (IntObjectCursor<DijkstraNodeInfo> dni : nodeInfo) {
                if (dni.value.distance <= shortestDistance && !dni.value.visited) {
                    baseNode = dni.key;
                    shortestDistance = dni.value.distance;
                    info = dni.value;
                }
            }
            if (shortestDistance > maxDistance) {
                return new Result(Status.TOO_LONG, maxDistance);
            }
            // mark as visited
            info.visited = true;
            // visit all neighbour nodes of this node
            adjIter = explorer.setBaseNode(baseNode);
            while (adjIter.next()) {
                int adj = adjIter.getAdjNode();
                DijkstraNodeInfo adjInfo = nodeInfo.get(adj);
                if (adjInfo != null && adjInfo.visited) {
                    continue;
                }
                double thisDist = adjIter.getDistance();
                if (toNodeId == adj) {
                    return new Result(Status.OK, shortestDistance + thisDist);
                }
                if (adjInfo == null) {
                    nodeInfo.put(
                        adj,
                        new DijkstraNodeInfo(shortestDistance + thisDist, baseNode)
                    );
                } else if (adjInfo.distance > shortestDistance + thisDist) {
                    // update distance if required
                    adjInfo.baseNode = baseNode;
                    nodeInfo.put(adj, adjInfo);
                }
            }
        }
        return new Result(Status.TOO_MANY_NODES, maxDistance);
    }
}
