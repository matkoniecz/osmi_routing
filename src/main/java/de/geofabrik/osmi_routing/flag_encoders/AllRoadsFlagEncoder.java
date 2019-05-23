/*
 *  © 2019 Geofabrik GmbH
 *
 *  This file is part of osmi_routing.
 *
 *  osmi_routing is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License.
 *
 *  osmi_routing is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with osmi_simple_views. If not, see <http://www.gnu.org/licenses/>.
 */

package de.geofabrik.osmi_routing.flag_encoders;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.graphhopper.reader.ReaderRelation;
import com.graphhopper.reader.ReaderWay;
import com.graphhopper.routing.profiles.BooleanEncodedValue;
import com.graphhopper.routing.profiles.EncodedValue;
import com.graphhopper.routing.profiles.EnumEncodedValue;
import com.graphhopper.routing.profiles.FactorizedDecimalEncodedValue;
import com.graphhopper.routing.profiles.IntEncodedValue;
import com.graphhopper.routing.profiles.SimpleBooleanEncodedValue;
import com.graphhopper.routing.profiles.SimpleIntEncodedValue;
import com.graphhopper.routing.util.AbstractFlagEncoder;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.util.EncodingManager.Access;
import com.graphhopper.storage.GraphExtension;
import com.graphhopper.storage.IntsRef;
import com.graphhopper.util.EdgeIteratorState;

import de.geofabrik.osmi_routing.flag_encoders.AllRoadsFlagEncoder.RoadClass;
import de.geofabrik.osmi_routing.flag_encoders.properties.LevelEncoder;

public class AllRoadsFlagEncoder extends AbstractFlagEncoder {
    
    public enum RoadClass {
        UNDEFINED(null),
        ROAD("road"),
        MOTORWAY("motorway"),
        MOTORWAY_LINK("motorway_link"),
        TRUNK("trunk"),
        TRUNK_LINK("trunk_link"),
        PRIMARY("primary"),
        PRIMARY_LINK("primary_link"),
        SECONDARY("secondary"),
        SECONDARY_LINK("secondary_link"),
        TERTIARY("tertiary"),
        TERTIARY_LINK("tertiary_link"),
        UNCLASSIFIED("unclassified"),
        RESIDENTIAL("residential"),
        LIVING_STREET("living_street"),
        PEDESTRIAN("pedestrian"),
        SERVICE("service"),
        SERVICE_DRIVEWAY("service_driveway"),
        SERVICE_ALLEY("service_alley"),
        SERVICE_PARKING_AISLE("service_parking_aisle"),
        TRACK("track"),
        FOOTWAY("footway"),
        CYCLEWAY("cycleway"),
        PATH("path"),
        RACEWAY("raceway"),
        STEPS("steps"),
        PLATFORM("platform"),
        FERRY("ferry");

        private String value;

        private RoadClass(String value) {
            this.value = value;
        }

        @Override
        public String toString() {
            return value;
        }

        public static RoadClass getRoadCode(String highway, String service, String public_transport, String railway, String route) {
            if (highway != null) {
                if (highway.equals("road")) {
                    return RoadClass.ROAD;
                } else if (highway.equals("motorway")) {
                    return RoadClass.MOTORWAY;
                } else if (highway.equals("motorway_link")) {
                    return RoadClass.MOTORWAY_LINK;
                } else if (highway.equals("trunk")) {
                    return RoadClass.TRUNK;
                } else if (highway.equals("trunk_link")) {
                    return RoadClass.TRUNK_LINK;
                } else if (highway.equals("primary")) {
                    return RoadClass.PRIMARY;
                } else if (highway.equals("primary_link")) {
                    return RoadClass.PRIMARY_LINK;
                } else if (highway.equals("secondary")) {
                    return RoadClass.SECONDARY;
                } else if (highway.equals("secondary_link")) {
                    return RoadClass.SECONDARY_LINK;
                } else if (highway.equals("tertiary")) {
                    return RoadClass.TERTIARY;
                } else if (highway.equals("tertiary_link")) {
                    return RoadClass.TERTIARY_LINK;
                } else if (highway.equals("unclassified")) {
                    return RoadClass.UNCLASSIFIED;
                } else if (highway.equals("residential")) {
                    return RoadClass.RESIDENTIAL;
                } else if (highway.equals("living_street")) {
                    return RoadClass.LIVING_STREET;
                } else if (highway.equals("pedestrian")) {
                    return RoadClass.PEDESTRIAN;
                } else if (highway.equals("service")) {
                    if (service == null) {
                        return RoadClass.SERVICE;
                    } else if (service.equals("driveway")) {
                        return RoadClass.SERVICE_DRIVEWAY;
                    } else if (service.equals("alley")) {
                        return RoadClass.SERVICE_ALLEY;
                    } else if (service.equals("parking_aisle")) {
                        return RoadClass.SERVICE_PARKING_AISLE;
                    }
                    return RoadClass.SERVICE;
                } else if (highway.equals("track")) {
                    return RoadClass.TRACK;
                } else if (highway.equals("footway")) {
                    return RoadClass.FOOTWAY;
                } else if (highway.equals("cycleway")) {
                    return RoadClass.CYCLEWAY;
                } else if (highway.equals("path")) {
                    return RoadClass.PATH;
                } else if (highway.equals("raceway")) {
                    return RoadClass.RACEWAY;
                } else if (highway.equals("steps")) {
                    return RoadClass.STEPS;
                } else if (highway.equals("platform")) {
                    return RoadClass.PLATFORM;
                }
            } else if (railway != null && railway.equals("platform")) {
                return RoadClass.PLATFORM;
            } else if (public_transport != null && public_transport.equals("platform")) {
                return RoadClass.PLATFORM;
            } else if (route != null && route.equals("ferry"));
            return RoadClass.UNDEFINED;
        }
    }

    private EnumEncodedValue<RoadClass> roadClassEncoder;
    private BooleanEncodedValue privateEncoder;
    private LevelEncoder levelEncoder;

    public AllRoadsFlagEncoder() {
        super(1, 10, 0);
        init();
        setBlockFords(false);
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
        registerNewEncodedValue.add(roadClassEncoder = new EnumEncodedValue<RoadClass>("road_class", RoadClass.class));
        registerNewEncodedValue.add(privateEncoder = new SimpleBooleanEncodedValue("private_access"));
        levelEncoder = new LevelEncoder();
        registerNewEncodedValue = levelEncoder.register(registerNewEncodedValue);
    }

    @Override
    public long handleRelationTags(long oldRelation, ReaderRelation relation) {
        return oldRelation;
    }

    @Override
    public Access getAccess(ReaderWay way) {
        String highway = way.getTag("highway");
        String service = way.getTag("service");
        String public_transport = way.getTag("public_transport");
        String railway = way.getTag("railway");
        String route = way.getTag("route");
        RoadClass type = RoadClass.getRoadCode(highway, service, public_transport, railway, route);
        if (type == RoadClass.UNDEFINED) {
            return EncodingManager.Access.CAN_SKIP;
        }
        if (type == RoadClass.FERRY) {
            return EncodingManager.Access.FERRY;
        }
        return EncodingManager.Access.WAY;
    }

    @Override
    public IntsRef handleWayTags(IntsRef edgeFlags, ReaderWay way, Access access, long relationFlags) {
        if (access.canSkip()) {
            return edgeFlags;
        }
        String highway = way.getTag("highway");
        String service = way.getTag("service");
        String public_transport = way.getTag("public_transport");
        String railway = way.getTag("railway");
        String route = way.getTag("route");
        RoadClass type = RoadClass.getRoadCode(highway, service, public_transport, railway, route);
        roadClassEncoder.setEnum(false, edgeFlags, type);
        speedEncoder.setDecimal(false, edgeFlags, 10);
        if (way.hasTag("access", "private") || way.hasTag("access", "no")) {
            privateEncoder.setBool(false, edgeFlags, true);
            privateEncoder.setBool(true, edgeFlags, true);
        } else {
            privateEncoder.setBool(false, edgeFlags, false);
            privateEncoder.setBool(true, edgeFlags, false);
        }
        accessEnc.setBool(false, edgeFlags, true);
        accessEnc.setBool(true, edgeFlags, true);
        // encode level
        String level = way.getTag("level");
        if (level == null) {
            level = way.getTag("layer");
        }
        edgeFlags = levelEncoder.setLevel(level, edgeFlags);
        return edgeFlags;
    }

    public boolean isLevelValid(EdgeIteratorState state) {
        return levelEncoder.isLevelValid(state.getFlags());
    }

    public int getLevelDiff(EdgeIteratorState state) {
        return levelEncoder.getLevelDiff(state.getFlags());
    }

    public int getLevel(EdgeIteratorState state) {
        return levelEncoder.getLevel(state.getFlags());
    }

    public RoadClass getRoadClass(EdgeIteratorState state) {
        return roadClassEncoder.getEnum(false, state.getFlags());
    }

    public boolean isPrivateAccess(EdgeIteratorState state) {
        return privateEncoder.getBool(false, state.getFlags());
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
