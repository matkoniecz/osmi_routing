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
        if (args.length < 3) {
            System.err.println("ERROR: too few arguments.\nUsage: PROGRAM_NAME INFILE TMP_DIR OUTFILE");
            System.exit(1);
        }
        GraphHopperSimple hopper = (GraphHopperSimple) new GraphHopperSimple(args).forServer();
        hopper.run();
    }
}
