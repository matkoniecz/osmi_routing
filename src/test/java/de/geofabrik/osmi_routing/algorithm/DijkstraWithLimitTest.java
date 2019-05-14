package de.geofabrik.osmi_routing.algorithm;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.storage.GraphBuilder;
import com.graphhopper.storage.GraphHopperStorage;
import com.graphhopper.util.DistanceCalc2D;
import com.graphhopper.util.PointList;
import com.graphhopper.util.shapes.GHPoint;

import de.geofabrik.osmi_routing.flag_encoders.AllRoadsFlagEncoder;
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

    private PointList makePointList(GHPoint point3) {
        PointList pointList = new PointList(5, false);
        pointList.add(18.35, 34.1);
        // cartesian distance 0--1: 0.02236068
        pointList.add(18.36, 34.12);
        // cartesian distance 1--2: 0.015
        pointList.add(18.36, 34.135);
        // cartesian distance 2--3: 0.15435349
        pointList.add(point3.lat, point3.lon);
        // cartesian distance 3--4: 0.200997512
        pointList.add(18.52, 34.0);
        return pointList;
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

    @Test
    public void testDistanceCalc() {
        GHPoint p = new GHPoint(18.5, 34.2);
        PointList pointList = makePointList(p);
        // use simple DistanceCalc2D which assumes cartesian 2D coordinates
        double distance = new DijkstraWithLimits(graph, 100, 1, new DistanceCalc2D()).distanceOnEdge(pointList, p);
        assertEquals(0.19171417, distance, 0.000000001);
        // reverse point list
        pointList.reverse();
        distance = new DijkstraWithLimits(graph, 100, 1, new DistanceCalc2D()).distanceOnEdge(pointList, p);
        assertEquals(0.200997512, distance, 0.000000001);
    }
}
