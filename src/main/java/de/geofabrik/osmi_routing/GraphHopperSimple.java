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
import com.graphhopper.routing.util.EncodingManager;
import com.graphhopper.routing.util.FlagEncoder;
import com.graphhopper.storage.GraphHopperStorage;
import com.graphhopper.util.Helper;

import de.geofabrik.osmi_routing.reader.BarriersHook;
import de.geofabrik.osmi_routing.reader.NoExitHook;
import de.geofabrik.osmi_routing.subnetworks.RemoveAndDumpSubnetworks;


public class GraphHopperSimple extends GraphHopperOSM {

    static final Logger logger = LogManager.getLogger(OsmiRoutingMain.class.getName());

    OsmIdAndNoExitStore nodeInfoStore;
    NoExitHook hook;
    BarriersHook barriersHook;
    private OsmIdStore edgeMapping;
    String outputDirectory;
    UnconnectedFinderManager unconnectedFinderManager;

    public GraphHopperSimple(String args[]) throws IOException {
        super();
        nodeInfoStore = new OsmIdAndNoExitStore(getGraphHopperLocation());
        hook = new NoExitHook(nodeInfoStore);
        barriersHook = new BarriersHook();
        setDataReaderFile(args[0]);
        setGraphHopperLocation(args[1]);
        setCHEnabled(false);
        // Disable sorting of graph because that would overwrite the values stored in the additional properties field of the graph.
        setSortGraph(false);
        AllRoadsFlagEncoder encoder = new AllRoadsFlagEncoder();
        outputDirectory = args[2];
        List<FlagEncoder> encoders = new ArrayList<FlagEncoder>(1);
        encoders.add(encoder);
        EncodingManager.Builder emBuilder = EncodingManager.createBuilder(encoders, 4);
        emBuilder.setEnableInstructions(false);
        setEncodingManager(emBuilder.build());
        double maxDistance = (args.length >= 4) ? Double.parseDouble(args[3]) : 10;
        int workers = (args.length == 5) ? Integer.parseInt(args[4]) : 2;
        try {
            java.nio.file.Path outputFileNameConnections = Paths.get(outputDirectory, "unconnected_nodes.json"); 
            unconnectedFinderManager = new UnconnectedFinderManager(this, encoder, outputFileNameConnections, maxDistance, workers);
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
            java.nio.file.Path outputFileNameSubnetworks = Paths.get(outputDirectory, "subnetworks.json"); 
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
        unconnectedFinderManager.init(getGraphHopperStorage(), nodeInfoStore, barriersHook);
        unconnectedFinderManager.run();
        close();
    }
}
