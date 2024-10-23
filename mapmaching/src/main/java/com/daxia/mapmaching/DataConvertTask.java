package com.daxia.mapmaching;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import com.graphhopper.matching.EdgeMatch;
import com.graphhopper.matching.MapMatching;
import com.graphhopper.matching.MatchResult;
import com.graphhopper.matching.Observation;
import com.graphhopper.storage.NodeAccess;
import com.graphhopper.util.shapes.GHPoint;

public class DataConvertTask implements Runnable {
    
    private final IDSupplier trajIDSupply;
    private final IDSupplier userIDSupply;
    private final IDSupplier dataIDSupply;
    private final Map<String,IDSupplier> usr2TrajId;
    //private final Map<String,IDSupplier> usr2UsrId;
    private String idIn;
    private final List<String> data;
    private final List<String> resultDest;
    private final StringBuilder builder = new StringBuilder();
    private final ThreadLocal<MapMatching> local;
    private final NodeAccess nodeAccess;
    private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    
    private final List<String> tlist = new ArrayList<>();
    private final List<String> locations = new ArrayList<>();
    private final List<Observation> observations = new ArrayList<>(1);
    private String startTime;
    private String usrId;
    public DataConvertTask(
        IDSupplier trajIDSupply,
        IDSupplier userIDSupply,
        IDSupplier dataIDSupply,
        Map<String,IDSupplier> usr2TrajId,
        //Map<String,IDSupplier> usr2UsrId,
        String idIn,
        List<String> data,
        List<String> resultDest,
        ThreadLocal<MapMatching> local,
        NodeAccess nodeAccess) {
        
        this.trajIDSupply = trajIDSupply;
        this.userIDSupply = userIDSupply;
        this.dataIDSupply = dataIDSupply;
        this.usr2TrajId=usr2TrajId;
        //this.usr2UsrId=usr2UsrId;
        this.idIn=idIn;
        this.data = data;
        this.resultDest = resultDest;
        this.local = local;//MapMatching对象
        this.nodeAccess = nodeAccess;
    }
    
    
    @Override
    public void run() {
        this.locations.clear();
        this.tlist.clear();

        var matcher = local.get();//当前线程的MapMatching实例

        //对data中的一行进行操作
        //data.stream().map(entry -> {
        try {
            IntStream.range(0, data.size())
                    .mapToObj(index -> {
                        String entry = data.get(index);
                        var strings = entry.split(",");
                        var lonlat = strings[1].split("_");
                        var ids = strings[0].split("_");

                        //记录开始时间,datas中的第一个
                        if (index == 0) {
                            startTime = strings[strings.length - 1];
                            usrId = ids[0];
                            if (!usr2TrajId.containsKey(ids[0])) {
                                usr2TrajId.put(ids[0], new IDSupplier());
                            }
                            //System.out.println(ids[1]);
                        }
                        String timeString = strings[strings.length - 1];//最后的那个：20240101003001
                        LocalDateTime dateTime = LocalDateTime.parse(timeString, formatter);
                        long unixTimestamp = dateTime.atZone(ZoneId.systemDefault()).toEpochSecond();//Unix 时间戳
                        tlist.add(Long.toString(unixTimestamp));

                        return new TrajectoryRecord(
                                strings[0], //整合的id
                                Double.parseDouble(lonlat[1]),//纬度
                                Double.parseDouble(lonlat[0]), //经度
                                strings[strings.length - 1]); //进入时间
                    })
                    .map(traj -> {
                        observations.add(new Observation(new GHPoint(
                                traj.lat(),
                                traj.lon())));
                        var result = matcher.match(observations);
                        //match:
                        var match = getClosestMatch(traj.lat(), traj.lon(), result, this.nodeAccess);
                        if (match == null) {
                            throw new RuntimeException(String.format(
                                    "match not found for id %s, lat: %f, lon: %f",
                                    traj.id(),
                                    traj.lat(),
                                    traj.lon()));
                        }

                        observations.clear();
                        return match;
                    }).forEach(match -> {
                        locations.add(match.getEdgeState().getName());
                        //System.out.println("locations的内容:"+match.getEdgeState().getName());
                    });
        }catch(Exception e){
            System.out.println("exception");
            return;
        }

        builder.append(dataIDSupply.getNew());
        builder.append(".0;[");

        final var locationIterator = this.locations.listIterator();
        while(locationIterator.hasNext()) {
            builder.append(locationIterator.next());
            if (locationIterator.hasNext()) {
                builder.append(", ");
            }
        }
        builder.append("];[");
        final var tlistIterator = this.tlist.listIterator();
        while(tlistIterator.hasNext()) {
            builder.append(tlistIterator.next());
            if (tlistIterator.hasNext()) {
                builder.append(", ");
            }
        }
        builder.append("];");
        builder.append(idIn);
        builder.append(";");
        builder.append(usr2TrajId.get(usrId).getNew());
        builder.append(".0");
        builder.append(";1.0;");
        //builder.append(this.tlist.get(0));改掉
        String subTime = startTime.substring(0, 8); // 提取前8位
        LocalDate date = LocalDate.parse(subTime, DateTimeFormatter.ofPattern("yyyyMMdd"));
        String formattedDate = date.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        builder.append(formattedDate);

        synchronized(resultDest) {
            //System.out.println(builder.toString().split(";")[0]);
            resultDest.add(builder.toString());
        }
        
        builder.setLength(0);
        return;
    }

    private EdgeMatch getClosestMatch(
        double trackLat, 
        double trackLon,
        MatchResult result, //对observation的match result
        final NodeAccess nodeAccess) 
    {
        EdgeMatch closestMatch = null;
        double minDistance = Double.MAX_VALUE;

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
        return closestMatch;
    }

    // 计算点到线段的距离函数
    private double calculateDistanceToLineSegment(double lat0, double lon0, double lat1, double lon1, double lat2, double lon2) {
        // 转换为平面坐标来简化计算
        double x0 = lon0, y0 = lat0;
        double x1 = lon1, y1 = lat1;
        double x2 = lon2, y2 = lat2;

        double numerator = Math.abs((y2 - y1) * x0 - (x2 - x1) * y0 + x2 * y1 - y2 * x1);
        double denominator = Math.sqrt(Math.pow(y2 - y1, 2) + Math.pow(x2 - x1, 2));

        return numerator / denominator;
    }
}
