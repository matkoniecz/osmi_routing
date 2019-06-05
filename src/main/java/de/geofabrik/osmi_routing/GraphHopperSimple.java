/*
 *  © 2019 Geofabrik GmbH
 *
 *  This file contains code copied from the GraphHopper project,
 *  © 2012–2019 GraphHopper GmbH, licensed under Apache License
 *  version 2.
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


package de.geofabrik.osmi_routing;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.graphhopper.reader.DataReader;
import com.graphhopper.reader.osm.GraphHopperOSM;
import com.graphhopper.reader.osm.OSMReader;
import com.graphhopper.routing.util.CarFlagEncoder;
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.util.FlagEncoder;
import com.graphhopper.storage.GraphHopperStorage;
import com.graphhopper.util.Helper;

import de.geofabrik.osmi_routing.flag_encoders.AllRoadsFlagEncoder;
import de.geofabrik.osmi_routing.flag_encoders.SimpleBikeFlagEncoder;
import de.geofabrik.osmi_routing.reader.BarriersHook;
import de.geofabrik.osmi_routing.reader.NoExitHook;
import de.geofabrik.osmi_routing.subnetworks.RemoveAndDumpSubnetworks;
import net.sourceforge.argparse4j.inf.Namespace;


public class GraphHopperSimple extends GraphHopperOSM {

    static final Logger logger = LogManager.getLogger(OsmiRoutingMain.class.getName());

    OsmIdAndNoExitStore nodeInfoStore;
    NoExitHook hook;
    BarriersHook barriersHook;
    private OsmIdStore edgeMapping;
    String outputDirectory;
    UnconnectedFinderManager unconnectedFinderManager;
    boolean doRouting;

    public GraphHopperSimple(Namespace args) throws IOException {
        super();
        nodeInfoStore = new OsmIdAndNoExitStore(getGraphHopperLocation());
        hook = new NoExitHook(nodeInfoStore);
        barriersHook = new BarriersHook();
        setDataReaderFile(args.getString("input_file"));
        setGraphHopperLocation(args.getString("graph_directory"));
        doRouting = args.getBoolean("do_routing");
        setCHEnabled(false);
        // Disable sorting of graph because that would overwrite the values stored in the additional properties field of the graph.
        setSortGraph(false);
        AllRoadsFlagEncoder encoder = new AllRoadsFlagEncoder();
        CarFlagEncoder carEncoder = new CarFlagEncoder(2, 50, 1);
        SimpleBikeFlagEncoder bicycleEncoder = new SimpleBikeFlagEncoder();
        outputDirectory = args.getString("output_directory");
        List<FlagEncoder> encoders = new ArrayList<FlagEncoder>(4);
        encoders.add(encoder);
        encoders.add(carEncoder);
        encoders.add(bicycleEncoder);
        EncodingManager.Builder emBuilder = EncodingManager.createBuilder(encoders, 4);
        emBuilder.setEnableInstructions(false);
        setEncodingManager(emBuilder.build());
        double maxDistance = args.getDouble("radius");
        int workers = args.getInt("worker_threads");
        try {
            unconnectedFinderManager = new UnconnectedFinderManager(this, encoder, outputDirectory, maxDistance, workers);
        } catch (IOException e) {
            logger.fatal(e);
            e.printStackTrace();
        }
    }

    /**
     * Overrides method in GrapHopper class calling removal of subnetworks code.
     */
    @Override
    protected void cleanUp() {
        try {
            java.nio.file.Path outputFileNameSubnetworks = Paths.get(outputDirectory);
            int prevNodeCount = getGraphHopperStorage().getNodes();
            RemoveAndDumpSubnetworks preparation = new RemoveAndDumpSubnetworks(getGraphHopperStorage(), getEncodingManager().fetchEdgeEncoders(), outputFileNameSubnetworks, edgeMapping);
            preparation.setMinNetworkSize(getMinNetworkSize());
            preparation.setMinOneWayNetworkSize(getMinOneWayNetworkSize());
            preparation.doWork();
            preparation.close();
            int currNodeCount = getGraphHopperStorage().getNodes();
            logger.info("edges: " + Helper.nf(getGraphHopperStorage().getAllEdges().length()) + ", nodes " + Helper.nf(currNodeCount)
                    + ", there were " + Helper.nf(preparation.getMaxSubnetworks())
                    + " subnetworks. removed them => " + Helper.nf(prevNodeCount - currNodeCount)
                    + " less nodes");
        } catch (IOException ex) {
            logger.catching(ex);
            System.exit(1);
        }
    }

    @Override
    protected DataReader createReader(GraphHopperStorage ghStorage) {
        edgeMapping = new OsmIdStore(getGraphHopperLocation());

        OSMReader reader = new OSMReader(ghStorage) {
                @Override
                protected void storeOsmWayID(int edgeId, long osmWayId) {
                    super.storeOsmWayID(edgeId, osmWayId);
                    edgeMapping.addWayId(edgeId, osmWayId);
                }
        };
        reader.register(hook);
        reader.register(barriersHook);
        return initDataReader(reader);
    }

    public void run() {
        importOrLoad();
        hook.releaseNoExitSet();
        barriersHook.prepareForQuery();
        unconnectedFinderManager.init(getGraphHopperStorage(), nodeInfoStore, edgeMapping, barriersHook, doRouting);
        unconnectedFinderManager.run();
        close();
    }
}
