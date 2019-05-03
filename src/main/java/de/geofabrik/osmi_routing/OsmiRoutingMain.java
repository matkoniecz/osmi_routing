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
import org.apache.logging.log4j.LogManager;

public class OsmiRoutingMain {

    static {
        System.setProperty("log4j2.configurationFile", "logging.xml");
    }

    static final Logger logger = LogManager.getLogger(OsmiRoutingMain.class.getName());

    GraphHopperSimple hopper;

    public static void main(String[] args) {
        if (args.length < 3 || args.length > 5) {
            System.err.println("ERROR: too few arguments.\nUsage: PROGRAM_NAME INFILE TMP_DIR OUTFILE [RADIUS [WORKERS]]");
            System.exit(1);
        }
        GraphHopperSimple hopper = (GraphHopperSimple) new GraphHopperSimple(args).forServer();
        hopper.run();
    }
}
