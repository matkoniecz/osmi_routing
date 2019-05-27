# Detect Routing Errors in OpenStreetMap

This repository contains an experimental tool to search for potential routing errors in OpenStreetMap using the [GraphHopper](https://github.com/graphhopper/graphhopper) routing engine.


## Detectable Errors

At the moment only missing connections between ways are detected.

### Missing Connections

For each missing connection two points are written to the output file – a point for the dead-end node of the graph and a point for the closest point in the graph. The properties of these points contain

* the distance between these points,
* the angle between the dead end and the matched way, and
* the quotient of the distance between the two points on the network and the beeline distance.

A quotient lower than 11 is usually a sign for a false positive.

The matched point of a dead end node is often not the closest point on the network. Especially in areas with very detailed mapping (parking aisles, footways etc.), the first match returned by GraphHoppers's location index is a neighbour edge of the dead-end edge or a neighbour of the neighbour. Both are usually not helpful.


## Dependencies

This tool depends heavily on GraphHopper and minor other dependencies. In order to store OSM node IDs for all tower nodes in the graph, this tool comes with a patched version of GraphHopper as a submodule. Call `build.sh` to build it using Maven.


## Known issues

* still to many false positives
* duplicated ways are not reported


## License

This tool is published under the GNU Public License version 3. See [LICENSE.md](LICENSE.md) for the full legal text of the license.
