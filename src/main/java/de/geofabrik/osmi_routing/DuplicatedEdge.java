package de.geofabrik.osmi_routing;

import com.graphhopper.util.PointList;

public class DuplicatedEdge {
    
    private PointList geometry;
    private long osmWayId;
    private long osmWayIdOther;
    private int rank;

    public DuplicatedEdge(PointList geometry, long osmWayId, long osmWayIdOther, boolean areaInvolved) {
        this.geometry = geometry;
        this.osmWayId = osmWayId;
        this.osmWayIdOther = osmWayIdOther;
        this.rank = areaInvolved ? 2 : 1;
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

    public int getRank() {
        return rank;
    }
}

