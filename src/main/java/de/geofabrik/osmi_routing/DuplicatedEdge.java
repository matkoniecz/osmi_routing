package de.geofabrik.osmi_routing;

import com.graphhopper.util.PointList;

public class DuplicatedEdge {
    
    private PointList geometry;
    private long osmNodeId;

    public DuplicatedEdge(PointList geometry, long osmNodeId) {
        this.geometry = geometry;
        this.osmNodeId = osmNodeId;
    }

    public PointList getGeometry() {
        return geometry;
    }

    public long getOsmNodeId() {
        return osmNodeId;
    }
}

