package com.daxia.mapmaching;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;

import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

public class CsvConverter {
    private final String targetFile;
    private final Logger logger = LogManager.getLogger(CsvConverter.class);
    private final IDProvider idProvider = new IDProvider();

    public CsvConverter(String targetFile) {
        this.targetFile = targetFile;
    }

    public void trans(File outFile) throws IOException {
        logger.info("正在读取csv...");
        var lines = Files.readAllLines(Path.of(targetFile));
        logger.info("读取csv结束，开始解析并写入osm");
        var iterator = lines.iterator();
        iterator.next();

        if (outFile.exists()) {
            outFile.delete();
        }
        outFile.createNewFile();

        HashMap<Vector2, Point> existingPoints = new HashMap<>();
        
        try (var stream = new PrintStream(outFile)) {
            this.printHead(stream);
            ArrayList<Road> roads = new ArrayList<>();
            
            var minLat = new Wrapper<Double>(Double.MAX_VALUE);
            var maxLat = new Wrapper<Double>(Double.MIN_VALUE);
            var minLon = new Wrapper<Double>(Double.MAX_VALUE);
            var maxLon = new Wrapper<Double>(Double.MIN_VALUE);

            iterator.forEachRemaining(string -> {
                ArrayList<Point> points = new ArrayList<>();
                if (string.isBlank()) {
                    return;
                }
                var list = Arrays.asList(string.split(","))
                .stream()
                .filter(s -> !s.isBlank())
                .toList();
                var id = list.get(0);
                for (int i = 1; i <= list.size() - 2; i += 2) {
                    // 纬度lat => 0~90  经度lon => 0~180
                    var lon = Double.parseDouble(list.get(i));
                    var lat = Double.parseDouble(list.get(i + 1));
                    
                    if (lat < minLat.getValue()) {
                        minLat.setValue(lat);
                    }

                    if (lat > maxLat.getValue()) {
                        maxLat.setValue(lat);
                    }

                    if (lon < minLon.getValue()) {
                        minLon.setValue(lon);
                    }

                    if (lon > maxLon.getValue()) {
                        maxLon.setValue(lon);
                    }
                    var pos = new Vector2(lat, lon);
                    Point pointInstance;

                    if ((pointInstance = existingPoints.get(pos)) == null) {
                        pointInstance = new Point(idProvider.get(), lat, lon);
                        existingPoints.put(pos, pointInstance);
                        stream.println(pointInstance);
                    }
                    if (!points.isEmpty()) {
                        var last = points.get(points.size() - 1);
                        if (last.lat == pos.x && last.lon == pos.y) {
                            // 如果和上一个重复了就不加了
                            continue;
                        }
                    }
                    points.add(pointInstance);
                }
                var road = new Road(id, points);
                roads.add(road);
            });

            logger.info("started printing...");
            
            roads.forEach(stream::println);
            
            this.printEnd(stream);

            logger.info("add this to xml start: " + String.format(
                "<bounds minlat=\"%f\" minlon=\"%f\" maxlat=\"%f\" maxlon=\"%f\"/>",
                minLat.getValue(),
                minLon.getValue(),
                maxLat.getValue(),
                maxLon.getValue()));

        } catch (Exception e) {
            logger.error(e);
        }
    }

    private void printHead(PrintStream printStream) {
        printStream.println("""
                    <?xml version="1.0" encoding="UTF-8"?>
                    <osm version="0.6" generator="java">
                    """);
    }

    private void printEnd(PrintStream printStream) {
        printStream.println("</osm>");
    }

    private record Point(long id, double lat, double lon) {
        @Override
        public final String toString() {
            return String.format("<node id=\"%d\" visible=\"true\" version=\"1\" lat=\"%f\" lon=\"%f\" />", id, lat, lon);
        }
    }
    private record Road(String id, ArrayList<Point> nodes) {

        public Road(String id, ArrayList<Point> nodes) {
            this.id = id.replaceAll("_", "");
            this.nodes = nodes;
        }

        @Override
        public final String toString() {
            var builder = new StringBuilder(String.format("<way id=\"%s\" visible=\"true\" version=\"1\">\n", this.id));

            this.nodes.forEach(node -> builder.append(String.format("<nd ref=\"%d\"/>\n", node.id)));
            
            builder.append(String.format("""
            <tag k="highway" v="residential"/>
            <tag k="name" v="%s"/>
            <tag k="maxspeed" v="50"/>        
            """, this.id));
            
            builder.append("</way>");

            return builder.toString();
        }
    }

    private class IDProvider {
        private long id = 0;

        public long get() {
            return id++;
        }
    }

    private class Wrapper<T> {
        private T value;

        public T getValue() {
            return value;
        }

        public void setValue(T value) {
            this.value = value;
        }

        public Wrapper(T init) {
            this.value = init;
        }
    }

    private static class Vector2 {
        private double x;
        private double y;

        public Vector2(double x, double y) {
            this.x = x;
            this.y = y;
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            long temp;
            temp = Double.doubleToLongBits(x);
            result = prime * result + (int) (temp ^ (temp >>> 32));
            temp = Double.doubleToLongBits(y);
            result = prime * result + (int) (temp ^ (temp >>> 32));
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }

            if (!(obj instanceof Vector2)) {
                return false;
            }

            var cast = (Vector2) obj;

            return this.x == cast.x && this.y == cast.y;
        }

    }
}
