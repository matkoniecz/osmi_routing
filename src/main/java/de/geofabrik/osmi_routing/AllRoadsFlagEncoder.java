package de.geofabrik.osmi_routing;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.graphhopper.reader.ReaderRelation;
import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.profiles.EncodedValue;
import com.graphhopper.routing.profiles.FactorizedDecimalEncodedValue;
import com.graphhopper.routing.util.AbstractFlagEncoder;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.util.EncodingManager.Access;
import com.graphhopper.storage.GraphExtension;
import com.graphhopper.storage.IntsRef;

public class AllRoadsFlagEncoder extends AbstractFlagEncoder {
    
    protected final Set<String> allowedRoadTypes = new HashSet<String>();
    
    public AllRoadsFlagEncoder() {
        super(4, 2, 0);
        allowedRoadTypes.add("motorway");
        allowedRoadTypes.add("motorway_link");
        allowedRoadTypes.add("trunk");
        allowedRoadTypes.add("trunk_link");
        allowedRoadTypes.add("primary");
        allowedRoadTypes.add("primary_link");
        allowedRoadTypes.add("secondary");
        allowedRoadTypes.add("secondary_link");
        allowedRoadTypes.add("tertiary");
        allowedRoadTypes.add("tertiary_link");
        allowedRoadTypes.add("unclassified");
        allowedRoadTypes.add("residential");
        allowedRoadTypes.add("living_street");
        allowedRoadTypes.add("pedestrian");
        allowedRoadTypes.add("service");
        allowedRoadTypes.add("track");
        allowedRoadTypes.add("footway");
        allowedRoadTypes.add("cycleway");
        allowedRoadTypes.add("path");
        allowedRoadTypes.add("raceway");
        allowedRoadTypes.add("steps");
        init();
    }

    public int getVersion() {
        return 2;
    }
    
    /**
     * Define the place of the speedBits in the edge flags for car.
     */
    @Override
    public void createEncodedValues(List<EncodedValue> registerNewEncodedValue, String prefix, int index) {
        // first two bits are reserved for route handling in superclass
        super.createEncodedValues(registerNewEncodedValue, prefix, index);
        registerNewEncodedValue.add(speedEncoder = new FactorizedDecimalEncodedValue(prefix + "average_speed", speedBits, speedFactor, false));
    }

    @Override
    public long handleRelationTags(long oldRelation, ReaderRelation relation) {
        return oldRelation;
    }

    @Override
    public Access getAccess(ReaderWay way) {
        String highwayValue = way.getTag("highway");
        if (highwayValue != null && allowedRoadTypes.contains(highwayValue)) {
            return EncodingManager.Access.WAY;
        }
        String routeValue = way.getTag("route");
        if (routeValue != null && routeValue.equals("ferry")) {
            return EncodingManager.Access.FERRY;
        }
        return EncodingManager.Access.CAN_SKIP;
    }

    @Override
    public IntsRef handleWayTags(IntsRef edgeFlags, ReaderWay way, Access access, long relationFlags) {
        if (access.canSkip()) {
            return edgeFlags;
        }
        speedEncoder.setDecimal(false, edgeFlags, 10);
//        setSpeed(false, edgeFlags, 10);
//        setSpeed(true, edgeFlags, 10);
        accessEnc.setBool(false, edgeFlags, true);
        accessEnc.setBool(true, edgeFlags, true);
        return edgeFlags;
    }

    @Override
    public boolean supports(Class<?> feature) {
        return false;
    }

    @Override
    public String toString() {
        return "all_roads";
    }
}
