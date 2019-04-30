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
import java.util.Locale;

import com.graphhopper.util.shapes.GHPoint;

public class GeoJSONWriter {
    private BufferedWriter writer;
//    private DataOutputStream outStream;
//    private BufferedWriter bw;
//    private PrintWriter out;
    private Locale locale = new Locale("en-US");
    boolean firstFeature = true;

    public GeoJSONWriter(String filename) throws IOException {
        Path path = Paths.get(filename);
        this.writer = Files.newBufferedWriter(path, Charset.forName("UTF-8"));
        String header = "{\"type\":\"FeatureCollection\",\n\"features\":[\n";
        this.writer.write(header);
    }

    public void write(GHPoint point, String v1, String k2, double v2, String k3, int v3, String k4, double v4, long osmId) throws IOException {
        String out = "";
        if (!firstFeature) {
            out = ",\n";
        }
        firstFeature = false;
        out += String.format(locale, "{\"type\":\"Feature\",\"properties\":{\"type\": \"%s\", \"%s\": %.2f, \"%s\": %d, \"%s\": %.4f, \"osm_id\": %d},", v1, k2, v2, k3, v3, k4, v4, osmId);
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

//    public void write(GHPoint point, String... args) throws IOException {
//        String out = "";
//        if (!firstFeature) {
//            out = ",\n";
//        }
//        firstFeature = false;
//        out += String.format(locale, "{\"type\":\"Feature\",\"properties\":{");
//        int c = 0;
//        String cache = "";
//        boolean first = true;
//        for (String arg : args) {
//            if (c % 2 == 1) {
//                if (!first) {
//                    out += ", ";
//                }
//                first = false;
//                out += String.format("\"%s\": \"%s\"", cache, arg);
//                cache = "";
//            } else {
//                cache = arg;
//            }
//            ++c;
//        }
//        out += String.format(locale, "}, \"geometry\":{\"type\":\"Point\",\"coordinates\":[%.7f, %.7f]}}\n", point.lon, point.lat);
//        writer.write(out);
//    }
//
//    public void write(PointList points, String type) throws IOException {
//        String out = "";
//        if (!firstFeature) {
//            out = ",\n";
//        }
//        firstFeature = false;
//        out += "{\"type\":\"Feature\",\"properties\":{\"type\": \"" + type
//                + "\"}, \"geometry\":{\"type\":\"LineString\",\"coordinates\":[";
//
//        boolean firstPoint = true;
//        for (Iterator<GHPoint3D> iterator = points.iterator(); iterator.hasNext();) {
//            Double[] coords = iterator.next().toGeoJson();
//            if (!firstPoint) {
//                out += ",\n";
//            }
//            firstPoint = false;
//            out += "[" + coords[0].toString() + "," + coords[1].toString() + "]";
//        }
//        out += "]}}\n";
//        writer.write(out);
//    }
    
    public void flush() throws IOException {
        writer.flush();
    }

    public void close() throws IOException {
        String out ="]}\n";
        writer.write(out);
        flush();
        writer.close();
    }

    public void finalized() throws IOException {
        close();
    }
}
