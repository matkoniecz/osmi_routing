# OSMI Routing

This repository contains a tool to search for potential routing errors in OpenStreetMap using the [GraphHopper](https://github.com/graphhopper/graphhopper) routing engine. It is used to generate the data available in the OSM Inspector Routing View.


## Usage and Output

```sh
java -jar ./target/osmi_routing-0.0.1-SNAPSHOT-jar-with-dependencies.jar  [-h] [-d] [-r RADIUS] [-w WORKER_THREADS] input_file graph_directory output_directory
```

Named arguments:

* `-d, --do-routing`: calculate quotient of distance over graph and beeline for all missing connections (default: false)
* `-r RADIUS, --radius RADIUS`: search radiusin meter (default: 15)
* `-w THREADS, --worker-threads THREADS`: number of worker threads (default: 2)

Positional arguments:
* `input_file`: input file
* `graph_directory`: directory where to store graph
* `output_directory`: output directory

It is advisable to allow the Java Virtual Machine to allocate more memory than usual by using the command line arguments `-Xms1g` and `-Xmx10g` (this example allocates 1 GB at minimum and allows the VM to allocate up to 10 GB). The upper limit should be lower than the available amount of RAM on your computer but higher than the amount required to process the OSM dataset you are working on. For the whole world, this tool requires about 60 to 70 GB as of May 2019.


### Missing Connections

For each missing connection two points are written to the output file – a point for the dead-end node of the graph and a point for the closest point in the graph. The properties of these points contain

* `type`:
  * `snap point`: This is a snap point.
  * `tower`: This is an open end point whose snap point is a tower node.
  * `pillar`: This is an open end point whose snap point is a pillar node.
  * `edge`: This is an open end point whose snap point is neither a tower nor a pillar node.
* `distance`: distance between these points in metre
* `priority`: priority of the error (1: highly important, 6: least important)
* `highway`: road class
* `private`: open end edge has private access only
* `node_id`: OSM ID of the open end node

The matched point of a dead end node is often not the closest point on the network. Especially in areas with very detailed mapping (parking aisles, footways etc.), the first match returned by GraphHoppers's location index is a neighbour edge of the dead-end edge or a neighbour of the neighbour. Both are usually not helpful.

The priority is calculated based on the road class, distance and access restrictions of the open end edge. For footways, paths and steps, the quotient of the distance of the graph and the beeline distance of the missing connection comes into play. For parking aisles and driveways, open ends which snap on a parallel edge are discarded.


### Islands

This tool produces three output files, one per profile.

* `type`: `island` or `sink_source`
* `way_id`: OSM way ID
* `vehicle`: vehicle (`car`, `bike_simple` or `allroads`)


## Dependencies

This tool depends heavily on GraphHopper and minor other dependencies. In order to store OSM node IDs for all tower nodes in the graph, this tool comes with a patched version of GraphHopper as a submodule.


## Building

```sh
./build.sh
```


## Known issues

* still to many false positives
* duplicated ways are not reported


## License

This tool is published under the GNU Public License version 3. See [LICENSE.md](LICENSE.md) for the full legal text of the license.
