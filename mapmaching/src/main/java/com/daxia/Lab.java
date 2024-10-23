package com.daxia;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.stream.IntStream;

import javax.xml.stream.XMLStreamException;

import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

import com.graphhopper.GraphHopper;
import com.graphhopper.config.Profile;
import com.graphhopper.matching.EdgeMatch;
import com.graphhopper.matching.MapMatching;
import com.graphhopper.matching.MatchResult;
import com.graphhopper.matching.Observation;
import com.graphhopper.reader.ReaderElement;
import com.graphhopper.reader.osm.OSMInputFile;
import com.graphhopper.storage.BaseGraph;
import com.graphhopper.storage.NodeAccess;
import com.graphhopper.util.PMap;
import com.graphhopper.util.shapes.GHPoint;

public class Lab {
    private final static String osmFile = "./osms/translate/out_01.pbf";
    private final static String graphHopperDirectory = "./cache";
    private final static boolean outputOSM = false;
    private final static boolean outputEdgesAndNodes = false;
    private final static String osmParseOutFile = "./parse/out.txt";

    private final static Logger logger = LogManager.getLogger(Lab.class);

    public static void main_old(String[] args) throws IOException, XMLStreamException {
        if (outputOSM) {
            var outFile = new File("./parse/out.txt");
            if (!outFile.exists()) {
                outFile.getParentFile().mkdirs();
                outFile.createNewFile();
            }

            try (var osm = new OSMInputFile(new File(osmFile));
                    var stream = new FileWriter(osmParseOutFile, StandardCharsets.UTF_8)) {

                osm.open();
                ReaderElement elem;
                boolean found = false;
                while ((elem = osm.getNext()) != null) {
                    System.out.println(elem);
                }
                if (found) {
                    logger.info("found!");
                } else {
                    logger.error("not found...");
                }
            }
        }

        var profile = new Profile("car").setVehicle("car").setWeighting("fastest");
        var hopper = new GraphHopper();
        hopper.setOSMFile(osmFile);
        hopper.setGraphHopperLocation(graphHopperDirectory);
        hopper.setProfiles(profile);
        hopper.importOrLoad();

        var graph = hopper.getBaseGraph();
        logger.info("there are " + graph.getEdges() + " edges");
        logger.info("there are " + graph.getNodes() + " nodes");

        if (outputEdgesAndNodes) {
            outputEdgesAndNodesMultithread(graph);
        }

        var mapMatching = MapMatching.fromGraphHopper(hopper,
                new PMap().putObject("profile", "car").putObject("maxDistance", "10"));

        List<Observation> list = Arrays.asList(
                // 前面是纬度(0~90)，后面是经度(0~180)
                new Observation(new GHPoint(39.904, 116.544)));

        var result = mapMatching.match(list);

        if (result.getEdgeMatches().size() == 0) {
            logger.info("no match found...");
            return;
        }

        logger.info("found match!");
        final var nodeAccess = hopper.getBaseGraph().getNodeAccess();

        // 初始化最近路段和最小距离
        EdgeMatch closestMatch = null;
        double minDistance = Double.MAX_VALUE;

        // 轨迹点坐标
        double trackLat = list.get(0).getPoint().lat;
        double trackLon = list.get(0).getPoint().lon;

        // 遍历匹配到的所有路段，找到距离最近的一个
        for (var match : result.getEdgeMatches()) {
            var edgeState = match.getEdgeState();
            var base = edgeState.getBaseNode();
            var adj = edgeState.getAdjNode();

            double lat1 = nodeAccess.getLat(base);
            double lon1 = nodeAccess.getLon(base);
            double lat2 = nodeAccess.getLat(adj);
            double lon2 = nodeAccess.getLon(adj);

            // 计算点到线段的距离
            double distance = calculateDistanceToLineSegment(trackLat, trackLon, lat1, lon1, lat2, lon2);

            // 更新最近路段
            if (distance < minDistance) {
                minDistance = distance;
                closestMatch = match;
            }
        }

        if (closestMatch != null) {
            var edgeState = closestMatch.getEdgeState();
            var base = edgeState.getBaseNode();
            var adj = edgeState.getAdjNode();

            logger.info("===CLOSEST SEGMENT===");
            logger.info(
                    "from node: " + base + " (" + nodeAccess.getLat(base) + ", " + nodeAccess.getLon(base) + ")");
            logger.info("to node: " + adj + " (" + nodeAccess.getLat(adj) + ", " + nodeAccess.getLon(adj) + ")");
            logger.info("name: " + edgeState.getName());
            logger.info("internal id: " + edgeState.getEdgeKey());

            // 生成URL
            StringBuilder urlBuilder = new StringBuilder("https://graphhopper.com/maps/?");
            urlBuilder.append("point=")
                    .append(nodeAccess.getLat(base)).append("%2C")
                    .append(nodeAccess.getLon(base)).append("&");
            urlBuilder.append("point=")
                    .append(nodeAccess.getLat(adj)).append("%2C")
                    .append(nodeAccess.getLon(adj)).append("&");
            urlBuilder.append("profile=car&layer=Omniscale");

            // 打印生成的 URL
            String generatedUrl = urlBuilder.toString();
            logger.info("Generated URL: " + generatedUrl);
        } else {
            logger.info("No closest segment found...");
        }
    }

    // 计算点到线段的距离函数
    private static double calculateDistanceToLineSegment(double lat0, double lon0, double lat1, double lon1, double lat2, double lon2) {
        // 转换为平面坐标来简化计算
        double x0 = lon0, y0 = lat0;
        double x1 = lon1, y1 = lat1;
        double x2 = lon2, y2 = lat2;

        double numerator = Math.abs((y2 - y1) * x0 - (x2 - x1) * y0 + x2 * y1 - y2 * x1);
        double denominator = Math.sqrt(Math.pow(y2 - y1, 2) + Math.pow(x2 - x1, 2));

        return numerator / denominator;
    }

    private static void outputEdgesAndNodesMultithread(BaseGraph graph) {
        final String edgeOut = "./edgeOut";
        final String nodeOut = "./nodeOut";

        try (var edgeStream = new PrintStream(new FileOutputStream(edgeOut));
                var nodeStream = new PrintStream(new FileOutputStream(nodeOut))) {

            var edgeIterator = graph.getAllEdges();
            var nodeAccess = graph.getNodeAccess();
            final var totalNodes = graph.getNodes();

            Thread t1 = new Thread(() -> {
                IntStream.range(0, totalNodes)
                        .forEach(index -> {
                            nodeStream.printf(
                                    "node %d: lat: %f lon: %f\n",
                                    index,
                                    nodeAccess.getLat(index),
                                    nodeAccess.getLon(index));
                        });
            });

            Thread t2 = new Thread(() -> {
                while (true) {
                    edgeStream.printf(
                            "edge %s: from %d to %d\n",
                            edgeIterator.getName(),
                            edgeIterator.getBaseNode(),
                            edgeIterator.getAdjNode());

                    if (!edgeIterator.next()) {
                        break;
                    }
                }
            });

            t1.start();
            t2.start();
            t1.join();
            t2.join();
        } catch (Exception e) {
            // TODO: handle exception
        }
    }
}
