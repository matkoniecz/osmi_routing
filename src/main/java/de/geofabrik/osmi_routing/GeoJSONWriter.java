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
import java.nio.file.Paths;
import java.util.List;
import java.util.Locale;

import com.graphhopper.util.PointList;
import com.graphhopper.util.shapes.GHPoint;

import de.geofabrik.osmi_routing.subnetworks.RemoveAndDumpSubnetworks;

public class GeoJSONWriter {
    private BufferedWriter writer;
//    private DataOutputStream outStream;
//    private BufferedWriter bw;
//    private PrintWriter out;
    private Locale locale = new Locale("en-US");
    boolean firstFeature = true;

    public GeoJSONWriter(Path path) throws IOException {
        this.writer = Files.newBufferedWriter(path, Charset.forName("UTF-8"));
        String header = "{\"type\":\"FeatureCollection\",\n\"features\":[\n";
        this.writer.write(header);
    }
    
    public void write(List<MissingConnection> items) throws IOException {
        for (MissingConnection item : items) {
            write(item);
        }
    }
    
    public void write(MissingConnection m) throws IOException {
        String out = "";
        if (!firstFeature) {
            out = ",\n";
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
        out += String.format(locale, "{\"type\":\"Feature\",\"properties\":{\"type\": \"%s\", \"distance\": %.2f, \"ratio\": %.2f, \"angle2\": %.1f, \"angleD2\": %.1f, \"osm_id\": %d},",
                matchType, m.getDistance(), m.getRatio(), m.getAngles()[0], m.getAngles()[1], m.getOsmId());
        out += String.format(locale, " \"geometry\":{\"type\":\"Point\",\"coordinates\":[%.7f, %.7f]}},\n", m.getOpenEndPoint().lon, m.getOpenEndPoint().lat);
        out += String.format(locale, "{\"type\":\"Feature\",\"properties\":{\"type\": \"snap point\", \"distance\": %.2f, \"ratio\": %.2f, \"angle2\": %.1f, \"angleD2\": %.1f, \"to_osm_id\": %d},",
                m.getDistance(), m.getRatio(), m.getAngles()[0], m.getAngles()[1], m.getOsmId());
        out += String.format(locale, " \"geometry\":{\"type\":\"Point\",\"coordinates\":[%.7f, %.7f]}}\n", m.getSnapPoint().lon, m.getSnapPoint().lat);
        writer.write(out);
    }

    public void write(GHPoint point, String v1, String k2, double v2, String k3, int v3, String k4, double v4, double v5, double v6, long osmId) throws IOException {
        String out = "";
        if (!firstFeature) {
            out = ",\n";
        }
        firstFeature = false;
        out += String.format(locale, "{\"type\":\"Feature\",\"properties\":{\"type\": \"%s\", \"%s\": %.2f, \"%s\": %d, \"%s\": %.4f, \"angleD1\": %.1f, \"angleD2\": %.1f, \"osm_id\": %d},", v1, k2, v2, k3, v3, k4, v4, v5, v6, osmId);
        out += String.format(locale, " \"geometry\":{\"type\":\"Point\",\"coordinates\":[%.7f, %.7f]}}\n", point.lon, point.lat);
        writer.write(out);
    }

    public void write(GHPoint point, String v1, String k2, int v2, String k3, int v3, String k4, double v4) throws IOException {
        String out = "";
        if (!firstFeature) {
            out = ",\n";
        }
        firstFeature = false;
        out += String.format(locale, "{\"type\":\"Feature\",\"properties\":{\"type\": \"%s\", \"%s\": %d, \"%s\": %d, \"%s\": %.4f},", v1, k2, v2, k3, v3, k4, v4);
        out += String.format(locale, " \"geometry\":{\"type\":\"Point\",\"coordinates\":[%.7f, %.7f]}}\n", point.lon, point.lat);
        writer.write(out);
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

    public void writeEdge(PointList geom, RemoveAndDumpSubnetworks.SubnetworkType type) throws IOException {
        String out = "";
        if (!firstFeature) {
            out = ",\n";
        }
        firstFeature = false;
        String typeStr = "undefined";
        if (type == RemoveAndDumpSubnetworks.SubnetworkType.ISLAND) {
            typeStr = "island";
        }
        if (type == RemoveAndDumpSubnetworks.SubnetworkType.SINK_SOURCE) {
            typeStr = "sink_or_source";
        }
        out += String.format(locale, "{\"type\":\"Feature\",\"properties\":{\"type\": \"%s\"},", typeStr);
        out += " \"geometry\":{\"type\":\"LineString\",\"coordinates\":[";
        boolean firstPoint = true;
        for (GHPoint p : geom) {
            if (!firstPoint) {
                out += ",";
            }
            firstPoint = false;
            out += String.format(locale, "[%.7f, %.7f]\n", p.getLon(), p.getLat());
        }
        out += "]}}\n";
        writer.write(out);
    }
}
