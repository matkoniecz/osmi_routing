package de.geofabrik.osmi_routing.algorithm;

import com.carrotsearch.hppc.cursors.IntObjectCursor;
import com.graphhopper.coll.GHIntHashSet;
import com.graphhopper.coll.GHIntObjectHashMap;
import com.graphhopper.storage.GraphHopperStorage;
import com.graphhopper.util.EdgeExplorer;
import com.graphhopper.util.EdgeIterator;

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

    public DijkstraWithLimits(GraphHopperStorage storage, int maxNodes, double maxDistance) {
        this.storage = storage;
        this.maxNodes = maxNodes;
        this.maxDistance = maxDistance;
    }

    public Result route(int fromNodeId, int toNodeId) {
        GHIntObjectHashMap<DijkstraNodeInfo> nodeInfo = new GHIntObjectHashMap<DijkstraNodeInfo>(100);
        //TODO maybe use plain array/list instead
        GHIntHashSet visitedNodes = new GHIntHashSet(100);
        EdgeExplorer explorer = storage.createEdgeExplorer();
        nodeInfo.put(fromNodeId, new DijkstraNodeInfo(0, -1));
        EdgeIterator adjIter = explorer.setBaseNode(fromNodeId);

        while (visitedNodes.size() < maxNodes) {
            // get node with shortest distance
            int baseNode = -1;
            double shortestDistance = Double.MAX_VALUE;
            for (IntObjectCursor<DijkstraNodeInfo> dni : nodeInfo) {
                if (dni.value.distance <= shortestDistance && !visitedNodes.contains(dni.key)) {
                    baseNode = dni.key;
                    shortestDistance = dni.value.distance;
                }
            }
            if (shortestDistance > maxDistance) {
                return new Result(Status.TOO_LONG, maxDistance);
            }
            // mark as visited
            visitedNodes.add(baseNode);
            // visit all neighbour nodes of this node
            adjIter = explorer.setBaseNode(baseNode);
            while (adjIter.next()) {
                int adj = adjIter.getAdjNode();
                if (visitedNodes.contains(adj)) {
                    continue;
                }
                double thisDist = adjIter.getDistance();
                if (toNodeId == adj) {
                    return new Result(Status.OK, shortestDistance + thisDist);
                }
                DijkstraNodeInfo adjInfo = nodeInfo.get(adj);
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
