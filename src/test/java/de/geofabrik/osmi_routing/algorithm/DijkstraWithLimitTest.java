package de.geofabrik.osmi_routing;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.storage.GraphBuilder;
import com.graphhopper.storage.GraphHopperStorage;

import de.geofabrik.osmi_routing.algorithm.DijkstraWithLimits;

public class DijkstraWithLimitTest {

    EncodingManager encodingManager;
    GraphHopperStorage graph;


    // 0-1-2-3-4
    // |     / |
    // |    8  |
    // \   /   |
    //  7-6----5
    public DijkstraWithLimitTest() {
        this.encodingManager = EncodingManager.create(new AllRoadsFlagEncoder());
        graph = new GraphBuilder(this.encodingManager).set3D(false).create();
        graph.edge(0, 1, 1, true);
        graph.edge(1, 2, 1, true);
        graph.edge(2, 3, 1, true);
        graph.edge(3, 4, 1, true);
        graph.edge(4, 5, 1, true);
        graph.edge(5, 6, 1, true);
        graph.edge(6, 7, 1, true);
        graph.edge(7, 0, 1, true);
        graph.edge(3, 8, 1, true);
        graph.edge(8, 6, 1, true);
    }

    @Test
    public void testRoute() {
        DijkstraWithLimits.Result r = new DijkstraWithLimits(graph, 100, 40).route(2, 5);
        assertEquals(3, r.distance, 0.01);
        assertEquals(DijkstraWithLimits.Status.OK, r.status);
    }

    @Test
    public void testRunIntoMaxNodesLimit() {
        int maxNodes = 4;
        double maxDistance = 40;
        DijkstraWithLimits.Result r = new DijkstraWithLimits(graph, maxNodes, maxDistance).route(2, 5);
        assertEquals(DijkstraWithLimits.Status.TOO_MANY_NODES, r.status);
        assertEquals(maxDistance, r.distance, 0.0001);
    }

    @Test
    public void testRunIntoDistanceLimit() {
        int maxDistance = 2;
        DijkstraWithLimits.Result r = new DijkstraWithLimits(graph, 100, maxDistance).route(1, 5);
        assertEquals(DijkstraWithLimits.Status.TOO_LONG, r.status);
        assertEquals(maxDistance, r.distance, 0.0001);
    }
}
