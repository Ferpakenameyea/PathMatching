package com.daxia.mapmaching;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import org.apache.log4j.Logger;

import com.graphhopper.GHRequest;
import com.graphhopper.GraphHopper;
import com.graphhopper.matching.MapMatching;
import com.graphhopper.matching.Observation;
import com.graphhopper.util.PMap;
import com.graphhopper.util.Parameters;
import com.graphhopper.util.shapes.GHPoint;

public class DataConvertTask implements Runnable {
    private final IDSupplier trajIDSupply;
    private final IDSupplier userIDSupply;
    private final IDSupplier dataIDSupply;
    private String usrId;
    private final List<String> resultDest;
    private final List<String> rawData;
    private final GraphHopper graphHopper;
    private final Logger logger;
    private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private final ThreadLocal<MapMatching> mapMatchingLocal = new ThreadLocal<>();


    public DataConvertTask(
        IDSupplier trajIDSupply,
        IDSupplier userIDSupply,
        IDSupplier dataIDSupply,
        List<String> resultDest,
        List<String> rawData,
        ThreadLocal<GraphHopper> graphHopper,
        ThreadLocal<Logger> logger)
    {
        this.trajIDSupply = trajIDSupply;
        this.userIDSupply = userIDSupply;
        this.dataIDSupply = dataIDSupply;
        this.resultDest = resultDest;
        this.rawData = rawData;
        this.graphHopper = graphHopper.get();
        this.logger = logger.get();
    }
    
    private long parseToUnixStamp(String timeString) {
        LocalDateTime dateTime = LocalDateTime.parse(timeString, formatter);
        long unixTimestamp = dateTime.atZone(ZoneId.systemDefault()).toEpochSecond();
        return unixTimestamp;
    }

    @Override
    public void run() {
        List<GHPoint> points = new ArrayList<>();

        List<String> roadIDs = new ArrayList<>();
        List<Long> timeStamps = new ArrayList<>();

        Wrapper<String> startDatetimeTimestamp = new Wrapper<>();

        // 预处理成列表形式
        rawData.stream()
            .map(s -> s.split(","))
            .forEachOrdered(array -> {
                var lonlat = array[1].split("_");
                var lon = Double.parseDouble(lonlat[0]);
                var lat = Double.parseDouble(lonlat[1]);
                points.add(new GHPoint(lat, lon));
                var timeString = array[array.length - 1];
                timeStamps.add(parseToUnixStamp(timeString.trim()));
                
                if (startDatetimeTimestamp.value == null) {
                    startDatetimeTimestamp.value = timeString; 
                } 
            });
        
        long startTime = timeStamps.get(0);
        long endTime = timeStamps.get(timeStamps.size() - 1);
        long elapsed = endTime - startTime;
        long now = startTime;

        timeStamps.clear();
        
        try {
            MapMatching mapMatching;

            if ((mapMatching = mapMatchingLocal.get()) == null) {
                mapMatching = MapMatching.fromGraphHopper(graphHopper, new PMap().putObject("profile", "car").putObject("maxDistance", "10"));
                mapMatchingLocal.set(mapMatching);
            }

            var edges = mapMatching
                .match(points.stream().map(Observation::new).toList())
                .getMergedPath()
                .calcEdges();

            double totalLength = 0;

            for (int i = 0; i < edges.size(); i++) {
                totalLength += edges.get(i).getDistance();
            }
            
            for (int i = 0; i < edges.size(); i++) {
                var id = edges.get(i).getName();
                if (!roadIDs.isEmpty() && roadIDs.get(roadIDs.size() - 1).equals(id)) {
                    // merge
                    timeStamps.set(
                        timeStamps.size() - 1, 
                        now);
                } else {
                    roadIDs.add(edges.get(i).getName());
                    timeStamps.add(now);
                }
                now += (edges.get(i).getDistance() / totalLength) * elapsed;
            }
        } catch (Exception e) {
            e.printStackTrace();
            logger.warn("Impossible mapmatching!");

            return;
        }

        //System.out.println(size+" "+timeStamps.size());
        StringBuilder builder = new StringBuilder();
        // TODO: 做一些处理工作，把它转换成一行字符串
        //timeStamps时间戳,instructions路段号
        builder.append(dataIDSupply.getNew());
        builder.append(".0;[");

        final var locationIterator = roadIDs.listIterator();
        while(locationIterator.hasNext()) {
            builder.append(locationIterator.next());
            if (locationIterator.hasNext()) {
                builder.append(", ");
            }
        }
        builder.append("];[");
        final var tlistIterator = timeStamps.listIterator();
        while(tlistIterator.hasNext()) {
            builder.append(tlistIterator.next());
            if (tlistIterator.hasNext()) {
                builder.append(", ");
            }
        }
        builder.append("];");
        builder.append(userIDSupply.getNew());
        builder.append(";");
        builder.append(trajIDSupply.getNew());
        builder.append(".0");
        builder.append(";1.0;");
        String subTime = startDatetimeTimestamp.value.substring(0, 8); // 提取前8位
        LocalDate date = LocalDate.parse(subTime, DateTimeFormatter.ofPattern("yyyyMMdd"));
        String formattedDate = date.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        builder.append(formattedDate);
        synchronized(resultDest) {
            resultDest.add(builder.toString());
        }
        return;
    }

    private class Wrapper<T> {
        public T value;
    }
}
