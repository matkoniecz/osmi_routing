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

package de.geofabrik.osmi_routing;

import org.apache.logging.log4j.Logger;

import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.impl.Arguments;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.ArgumentParserException;
import net.sourceforge.argparse4j.inf.Namespace;

import java.io.IOException;

import org.apache.logging.log4j.LogManager;

public class OsmiRoutingMain {

    static {
        System.setProperty("log4j2.configurationFile", "logging.xml");
    }

    static final Logger logger = LogManager.getLogger(OsmiRoutingMain.class.getName());

    GraphHopperSimple hopper;

    public static void main(String[] args) {
        ArgumentParser parser = ArgumentParsers.newFor("osmi_routing").build()
                .defaultHelp(true)
                .description("Find potential routing errors in OpenStreetMap data");
        parser.addArgument("-d", "--do-routing")
                .action(Arguments.storeTrue())
                .help("calculate quotient of distance over graph and beeline for all missing connections");
        parser.addArgument("-r", "--radius")
                .type(Double.class)
                .setDefault(15)
                .help("search radius for missing connections");
        parser.addArgument("-w", "--worker-threads")
                .type(Integer.class)
                .setDefault(2)
                .help("number of worker threads to search missing connections");
        parser.addArgument("input_file").help("input file");
        parser.addArgument("graph_directory").help("directory where to store graph");
        parser.addArgument("output_directory").help("output directory");

        Namespace ns = null;
        try {
            ns = parser.parseArgs(args);
        } catch (ArgumentParserException e) {
            parser.handleError(e);
            System.exit(1);
        }

        GraphHopperSimple hopper;
        try {
            hopper = (GraphHopperSimple) new GraphHopperSimple(ns).forServer();
            hopper.run();
        } catch (IOException e) {
            e.printStackTrace();
            System.exit(1);
        }
    }
}
