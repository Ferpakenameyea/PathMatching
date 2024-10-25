package com.daxia.mapmaching;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import org.apache.log4j.Logger;

import com.graphhopper.GHRequest;
import com.graphhopper.GraphHopper;
import com.graphhopper.util.Parameters;
import com.graphhopper.util.shapes.GHPoint;

public class DataConvertTask implements Runnable {
    
    private final List<String> resultDest;
    private final List<String> rawData;
    private final GraphHopper graphHopper;
    private final Logger logger;
    private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    public DataConvertTask(
        List<String> resultDest,
        List<String> rawData,
        ThreadLocal<GraphHopper> graphHopper,
        ThreadLocal<Logger> logger) 
    {
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

        // 预处理成列表形式
        rawData.stream()
            .map(s -> s.split(","))
            .forEachOrdered(array -> {
                var lonlat = array[1].split("_");
                var lon = Double.parseDouble(lonlat[0]);
                var lat = Double.parseDouble(lonlat[1]);
                points.add(new GHPoint(lat, lon));
                var timeString = array[array.length - 1].trim();
                timeStamps.add(parseToUnixStamp(timeString));
            });
        
        GHRequest request = new GHRequest(points)
            .setProfile("car")
            .setAlgorithm(Parameters.Algorithms.ASTAR)
            .setLocale("zh");

        // 交给 Graphhopper 进行导航
        var result = graphHopper.route(request);
        
        if (result.getAll().isEmpty() || result.getBest().isImpossible()) {
            logger.warn("impossible route!");
            return;
        }

        long startTime = timeStamps.get(0);
        long endTime = timeStamps.get(timeStamps.size() - 1);

        timeStamps.clear();

        var instructions = result.getBest().getInstructions();
        var size = instructions.size();
        var timeNow = startTime;

        for (int i = 0; i < size; i++) {
            var instruction = instructions.get(i);
            roadIDs.add(instruction.getName());
            if (i == size - 1) {
                timeStamps.add(endTime);
            } else {
                timeStamps.add(timeNow);
                // instruction.getTime() 是指在这段道路上的预计行驶时间
                // 单位为 ms
                // FIXME: 这里不太确定
                timeNow += instruction.getTime() / 1000;
            }
        }

        StringBuilder builder = new StringBuilder();
        // TODO: 做一些处理工作，把它转换成一行字符串
        
        synchronized(resultDest) {
            resultDest.add(builder.toString());
        }
    }
}
