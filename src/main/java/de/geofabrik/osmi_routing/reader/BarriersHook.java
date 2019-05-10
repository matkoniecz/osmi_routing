package de.geofabrik.osmi_routing.reader;


import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.index.strtree.STRtree;

import com.carrotsearch.hppc.cursors.LongCursor;
import com.graphhopper.reader.ReaderNode;
import com.graphhopper.reader.ReaderWay;
import com.graphhopper.reader.osm.OSMReaderHook;

import de.geofabrik.osmi_routing.OsmiRoutingMain;
import de.geofabrik.osmi_routing.index.SparseLocationIndex;

public class BarriersHook extends OSMReaderHook {

    static final Logger logger = LogManager.getLogger(OsmiRoutingMain.class.getName());
    
    private SparseLocationIndex locationIndex;
    private GeometryFactory geomFactory;
    private STRtree spatialIndex;
    
    public BarriersHook() {
        this.locationIndex = new SparseLocationIndex();
        this.geomFactory = new GeometryFactory();
        this.spatialIndex = new STRtree();
    }
    
    private boolean isBarrier(ReaderWay way) {
        String value = way.getTag("barrier");
        if (value == null) {
            return false;
        }
        if (value.equals("fence") || value.equals("wall") || value.equals("embankment")
                || value.equals("hedge") || value.equals("guard_rail") || value.equals("handrail") || value.equals("ditch") || value.equals("retaining_wall")) {
            return true;
        }
        return false;
    }

    @Override
    public void preProcessWay(ReaderWay way) {
        if (isBarrier(way)) {
            for (int i = 0; i < way.getNodes().size(); ++i) {
                locationIndex.setAsInterested(way.getNodes().get(i));
            }
        }
    }

    @Override
    public void beforeProcessNode(ReaderNode node) {
        if (locationIndex.hasKey(node.getId())) {
            locationIndex.setLocation(node.getId(), node.getLat(), node.getLon());
        }
    }

    @Override
    public boolean beforeProcessWay(ReaderWay way, boolean continueWithProcessing) {
        if (!isBarrier(way)) {
            return true;
        }
        // construct linestring
        Coordinate[] coords = new Coordinate[way.getNodes().size()];
        for (int i = 0; i < way.getNodes().size(); ++i) {
            long location = locationIndex.getLocationAsLong(way.getNodes().get(i));
            double lon = locationIndex.lonFromLong(location);
            double lat = locationIndex.latFromLong(location);
            coords[i] = new Coordinate(lon, lat);
        }
        LineString lineString = geomFactory.createLineString(coords);
        // insert into spatial index
        spatialIndex.insert(lineString.getEnvelopeInternal(), lineString);
        return false;
    }

    /**
     * Drop location cache and prepare spatial index for queries.
     */
    public void prepareForQuery() {
        locationIndex.release();
        logger.info("Preparing spatial index of barriers for querying.");
        spatialIndex.build();
    }

    /**
     * Return whether the line from (lon1, lat1) to (lon2, lat2) crosses a linear barrier.
     *
     * @param lon1 longitude of first point
     * @param lat1 latitude of first point
     * @param lon2 longitude of second point
     * @param lat2 latitude of second point
     */
    public boolean crossesBarrier(double lon1, double lat1, double lon2, double lat2) {
        double minLon = Math.min(lon1, lon2);
        double maxLon = Math.min(lon1, lon2);
        double minLat = Math.min(lat1, lat2);
        double maxLat = Math.min(lat1, lat2);
        Coordinate[] lineCoords = {new Coordinate(lon1, lat1), new Coordinate(lon2, lat2)};
        LineString queryLine = geomFactory.createLineString(lineCoords);
        Envelope envelope = new Envelope(minLon, maxLon, minLat, maxLat);
        @SuppressWarnings("unchecked")
        List<LineString> indexResult = spatialIndex.query(envelope);
        for (LineString l : indexResult) {
            if (l.intersects(queryLine)) {
                return true;
            }
        }
        return false;
    }
}
