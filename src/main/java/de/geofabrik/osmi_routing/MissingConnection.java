package de.geofabrik.osmi_routing;

import com.graphhopper.storage.index.QueryResult;
import com.graphhopper.util.shapes.GHPoint;

public class MissingConnection {
    
    private GHPoint openEndPoint;
    private GHPoint snapPoint;
    private double distance;
    private double distanceGraph;
    private int internalNodeId;
    private double[] angleDiff;
    private long osmId;
    private QueryResult.Position matchType;

    public MissingConnection(GHPoint openEndPoint, GHPoint snapPoint, double distanceBeeline,
            double distanceGraph, int internalNodeId, double[] angleDiff, long osmId,
            QueryResult.Position matchType) {
        this.openEndPoint = openEndPoint;
        this.snapPoint = snapPoint;
        this.distance = distanceBeeline;
        this.distanceGraph = distanceGraph;
        this.internalNodeId = internalNodeId;
        this.angleDiff = angleDiff;
        this.osmId = osmId;
        this.matchType = matchType;
    }

    public GHPoint getOpenEndPoint() {
        return openEndPoint;
    }
    
    public GHPoint getSnapPoint() {
        return snapPoint;
    }
    
    public double getDistance() {
        return distance;
    }
    
    public double getRatio() {
        return Math.min(distanceGraph / distance, 2000);
    }
    
    public int getInternalNodeId() {
        return internalNodeId;
    }
    
    public double[] getAngles() {
        return angleDiff;
    }
    
    public long getOsmId() {
        return osmId;
    }
    
    public QueryResult.Position getMatchType() {
        return matchType;
    }
}
