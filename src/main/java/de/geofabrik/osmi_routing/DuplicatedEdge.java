package de.geofabrik.osmi_routing;

import com.graphhopper.util.PointList;

public class DuplicatedEdge {
    
    private PointList geometry;
    private long osmWayId;
    private long osmWayIdOther;

    public DuplicatedEdge(PointList geometry, long osmWayId, long osmWayIdOther) {
        this.geometry = geometry;
        this.osmWayId = osmWayId;
        this.osmWayIdOther = osmWayIdOther;
    }

    public PointList getGeometry() {
        return geometry;
    }

    public long getOsmId() {
        return osmWayId;
    }

    public long getOsmIdOther() {
        return osmWayIdOther;
    }
}

