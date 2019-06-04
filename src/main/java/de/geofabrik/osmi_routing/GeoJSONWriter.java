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

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.List;
import java.util.Locale;

import com.graphhopper.routing.util.FlagEncoder;
import com.graphhopper.util.PointList;
import com.graphhopper.util.shapes.GHPoint;

import de.geofabrik.osmi_routing.subnetworks.RemoveAndDumpSubnetworks;

public class GeoJSONWriter {
    private BufferedWriter writer;
    private DecimalFormatSymbols decimalFormatSymbols;
    private DecimalFormat df1;
    private DecimalFormat df2;
    private DecimalFormat df7;
    boolean firstFeature = true;

    public GeoJSONWriter(Path path) throws IOException {
        this.writer = Files.newBufferedWriter(path, Charset.forName("UTF-8"));
        String header = "{\"type\":\"FeatureCollection\",\n\"features\":[\n";
        this.writer.write(header);
        decimalFormatSymbols = new DecimalFormatSymbols(new Locale("en-US"));
        df1 = new DecimalFormat("#.#", decimalFormatSymbols);
        df2 = new DecimalFormat("#.##", decimalFormatSymbols);
        df7 = new DecimalFormat("#.#######", decimalFormatSymbols);
    }

    public void writeDuplicatedEdges(List<DuplicatedEdge> items) throws IOException {
        for (DuplicatedEdge item : items) {
            write(item);
        }
    }

    public void write(DuplicatedEdge e) throws IOException {
        StringBuilder out = new StringBuilder(900);
        if (!firstFeature) {
            out.append(",\n");
        }
        firstFeature = false;

        out.append("{\"type\":\"Feature\",\"properties\":{\"type\": \"duplicated_edge\", \"node_id\":");
        out.append(e.getOsmNodeId());
        out.append("}, \"geometry\":{\"type\":\"LineString\",\"coordinates\":[");
        boolean firstPoint = true;
        for (GHPoint p : e.getGeometry()) {
            if (!firstPoint) {
                out.append(",");
            }
            firstPoint = false;
            out.append('[');
            out.append(df7.format(p.getLon()));
            out.append(", ");
            out.append(df7.format(p.getLat()));
            out.append(']');
        }
        out.append("]}}\n");
        writer.write(out.toString());
    }
    
    public void writeMissingConnections(List<MissingConnection> items) throws IOException {
        for (MissingConnection item : items) {
            write(item);
        }
    }
    
    public void write(MissingConnection m) throws IOException {
        StringBuilder out = new StringBuilder(430);
        if (!firstFeature) {
            out.append(",\n");
        }
        firstFeature = false;
        String matchType = "unkown";
        switch (m.getMatchType()) {
        case EDGE:
            matchType = "edge";
            break;
        case PILLAR:
            matchType = "pillar";
            break;
        case TOWER:
            matchType = "tower";
        }
        out.append("{\"type\":\"Feature\",\"properties\":{\"type\": \"");
        out.append(matchType);
        out.append("\", \"highway\": \"");
        out.append(m.getRoadClass().toString());
        out.append("\", \"private\": ");
        if (m.getPrivateAccess()) {
            out.append("true");
        } else {
            out.append("false");
        }
        out.append(", \"distance\": ");
        out.append(df2.format(m.getDistance()));
        out.append(", \"priority\": ");
        out.append(df1.format(m.getPriority()));
        out.append(", \"node_id\": ");
        out.append(m.getOsmId());
        out.append("},");
        out.append(" \"geometry\":{\"type\":\"Point\",\"coordinates\":[");
        out.append(df7.format(m.getOpenEndPoint().lon));
        out.append(", ");
        out.append(df7.format(m.getOpenEndPoint().lat));
        out.append("]}},\n");

        out.append("{\"type\":\"Feature\",\"properties\":{\"type\": \"snap point\", \"distance\": ");
        out.append(df2.format(m.getDistance()));
        out.append(", \"priority\": ");
        out.append(df1.format(m.getPriority()));
        out.append(", \"node_id\": ");
        out.append(m.getOsmId());
        out.append("},");
        out.append(" \"geometry\":{\"type\":\"Point\",\"coordinates\":[");
        out.append(df7.format(m.getSnapPoint().lon));
        out.append(", ");
        out.append(df7.format(m.getSnapPoint().lat));
        out.append("]}}");

        writer.write(out.toString());
        out.setLength(0);
    }

    public void flush() throws IOException {
        writer.flush();
    }

    public void close() throws IOException {
        writer.write("]}\n");
        flush();
        writer.close();
    }

    protected void finalize() throws IOException {
        close();
    }

    public void writeEdge(PointList geom, RemoveAndDumpSubnetworks.SubnetworkType type, long osmId, FlagEncoder encoder) throws IOException {
        StringBuilder out = new StringBuilder(900);
        if (!firstFeature) {
            out.append(",\n");
        }
        firstFeature = false;

        out.append("{\"type\":\"Feature\",\"properties\":{\"type\": \"");

        if (type == RemoveAndDumpSubnetworks.SubnetworkType.ISLAND) {
            out.append("island");
        } else if (type == RemoveAndDumpSubnetworks.SubnetworkType.SINK_SOURCE) {
            out.append("sink_or_source");
        } else {
            out.append("undefined");
        }

        out.append("\", \"way_id\": ");
        out.append(osmId);
        out.append(", \"vehicle\": \"");
        out.append(encoder.toString());
        out.append("\"}, \"geometry\":{\"type\":\"LineString\",\"coordinates\":[");
        boolean firstPoint = true;
        for (GHPoint p : geom) {
            if (!firstPoint) {
                out.append(",");
            }
            firstPoint = false;
            out.append('[');
            out.append(df7.format(p.getLon()));
            out.append(", ");
            out.append(df7.format(p.getLat()));
            out.append(']');
        }
        out.append("]}}\n");
        writer.write(out.toString());
    }
}
