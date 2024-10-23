package com.daxia;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.util.Arrays;
import java.util.List;

import org.junit.Test;

import com.daxia.mapmaching.CsvConverter;
import com.graphhopper.GraphHopper;
import com.graphhopper.config.Profile;
import com.graphhopper.matching.MapMatching;
import com.graphhopper.matching.Observation;
import com.graphhopper.reader.ReaderElement;
import com.graphhopper.reader.osm.OSMInputFile;
import com.graphhopper.util.PMap;
import com.graphhopper.util.shapes.GHPoint;

/**
 * Unit test for simple App.
 */
public class AppTest 
{
    @Test
    public void readOSM() {
        try (var osm = new OSMInputFile(new File("./out.osm"))) {
            osm.open();
            ReaderElement elem;
            while((elem = osm.getNext()) != null) {
                System.out.println(elem);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Test
    public void customOSMTest() throws IOException {
        File file = new File("./test.osm");
        if (file.exists()) {
            file.delete();
        }
        file.createNewFile();

        try (var stream = new PrintStream(file)) {
            stream.println("""
                    <?xml version="1.0" encoding="UTF-8"?>
                    <osm version="0.6" generator="java">
                    """);

            stream.println("""
                    <node id="1" lat="40.7128" lon="-74.0060" />
                    <node id="2" lat="40.7138" lon="-74.0070" />
                    <node id="3" lat="40.7148" lon="-74.0080" />
                    <way id="1">
                        <nd ref="1" />
                        <nd ref="2" />
                        <nd ref="3" />
                    </way>
                    """);

            stream.println("</osm>");
        } catch (Exception e) {
            // TODO: handle exception
        }
    }

    @Test
    public void loadOSMTest() {
        String osmFile = "./out.osm";
        String directory = "./cache";
        
        var profile = new Profile("car").setVehicle("car");
        var hopper = new GraphHopper();
        hopper.setOSMFile(osmFile);
        hopper.setGraphHopperLocation(directory);
        hopper.setProfiles(profile);
        hopper.importOrLoad();
    }

    @Test
    public void matchTestSnippet() {
        String osmFile = "./reference.osm";
        String directory = "./cache";
        
        var profile = new Profile("car").setVehicle("car");
        var hopper = new GraphHopper();
        hopper.setOSMFile(osmFile);
        hopper.setGraphHopperLocation(directory);
        hopper.setProfiles(profile);
        hopper.setMinNetworkSize(200);
        hopper.importOrLoad();

        var mapMatching = MapMatching.fromGraphHopper(hopper, 
            new PMap().putObject("profile", "car"));

        List<Observation> list = Arrays.asList(
            new Observation(new GHPoint(40.4010000, 115.7552500))
        );
        
        var result = mapMatching.match(list);
        if (result.getEdgeMatches().size() > 0) {
            System.out.println("found");
        } else {
            System.out.println("not found");
        }
    }

    @Test
    public void translate() throws IOException {
        final String csvFile = "./rtic_xy.csv";
        final String outOSMFile = "./out.osm";

        var converter = new CsvConverter(csvFile);
        converter.trans(new File(outOSMFile));
    }

    @Test
    public void labSnippet() throws Exception {
        final String osmFile = "./reference.osm";
        final String graphHopperDirectory = "./cache";

        var profile = new Profile("car").setVehicle("car");
        var hopper = new GraphHopper();
        hopper.setOSMFile(osmFile);
        hopper.setGraphHopperLocation(graphHopperDirectory);
        hopper.setProfiles(profile);
        hopper.importOrLoad();

        var mapMatching = MapMatching.fromGraphHopper(hopper, new PMap().putObject("profile", "car"));
        
        List<Observation> list = Arrays.asList(
            // 前面是纬度(0~90)，后面是经度(0~180)
            new Observation(new GHPoint(40.4115000, 115.7755000)),
            new Observation(new GHPoint(40.4115000, 115.7777500)),
            new Observation(new GHPoint(40.4122500, 115.7787500)),
            new Observation(new GHPoint(40.4130000, 115.7815000))
        );

        var result = mapMatching.match(list);

        if (result.getEdgeMatches().size() > 0) {
            System.out.println("ok!");
        } else {
            System.out.println("no match found...");
        }
    }
}
