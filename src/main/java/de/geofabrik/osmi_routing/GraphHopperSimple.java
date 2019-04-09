package de.geofabrik.osmi_routing;

import com.graphhopper.reader.osm.GraphHopperOSM;

public class GraphHopperSimple extends GraphHopperOSM {

    /**
     * Overrides method in GrapHopper class calling removal of subnetworks code.
     */
    @Override
    protected void cleanUp() {
        System.out.println("Skipping removal of subnetworks.");
    }
}
