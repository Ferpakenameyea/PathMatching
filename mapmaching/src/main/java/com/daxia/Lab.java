package com.daxia;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.xml.stream.XMLStreamException;

import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

import com.graphhopper.GHRequest;
import com.graphhopper.GraphHopper;
import com.graphhopper.config.Profile;
import com.graphhopper.matching.MapMatching;
import com.graphhopper.matching.Observation;
import com.graphhopper.reader.ReaderElement;
import com.graphhopper.reader.osm.OSMInputFile;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.PMap;
import com.graphhopper.util.Parameters;
import com.graphhopper.util.shapes.GHPoint;

public class Lab {
    private final static String osmFile = "./osms/translate/out_01.pbf";
    private final static String graphHopperDirectory = "./cache";
    private final static boolean outputOSM = false;
    private final static String osmParseOutFile = "./parse/out.txt";

    private final static Logger logger = LogManager.getLogger(Lab.class);

    public static void main(String[] args) throws IOException, XMLStreamException {
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

        var mapMatching = MapMatching.fromGraphHopper(hopper,
                new PMap().putObject("profile", "car").putObject("maxDistance", "10"));

        List<GHPoint> list = Arrays.asList(
                // 前面是纬度(0~90)，后面是经度(0~180)
                new GHPoint(39.904, 116.549),
                new GHPoint(39.904, 116.544));

        var result = hopper.route(new GHRequest(list)
            .setProfile("car")
            .setAlgorithm(Parameters.Algorithms.ASTAR)
            .setLocale("zh"));
            
        
        list = new ArrayList<>();
        result.getBest().getPoints().forEach(list::add);
        
        var matchResult = mapMatching.match(list.stream().map(Observation::new).toList());
        matchResult.getMergedPath()
            .calcEdges()
            .stream()
            .map(EdgeIteratorState::getName)
            .forEach(System.out::println);
    }
}
