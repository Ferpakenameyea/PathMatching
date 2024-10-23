package com.daxia.mapmaching;
import java.nio.file.Files;
import java.io.*;
import java.nio.file.*;
import java.util.*;
/**
 * <p>Project: mapmaching - GeoChange
 * <p>Powered by berry On 2024-10-16
 *
 * @author berry
 * @version 1.0
 */
public class GeoChange {
    private final static String inputFile = "./rtic_xy.csv";
    private static StringBuilder builder = new StringBuilder();
    private final static IDSupplier idSupply=new IDSupplier();
    private static final double EARTH_RADIUS = 6371;//单位是公里
    //工具类
    public static double calculateLength(double lon1, double lat1, double lon2, double lat2) {//经度，纬度
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        lat1 = Math.toRadians(lat1);
        lat2 = Math.toRadians(lat2);

        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.sin(dLon / 2) * Math.sin(dLon / 2) * Math.cos(lat1) * Math.cos(lat2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        double distance = EARTH_RADIUS * c;
        return distance;
    }
    public static void main(String[] args) throws IOException {
        double test=calculateLength(116.09286576840532, 40.55462492562604,116.09284570452648, 40.5547348376113);
        System.out.println(test);
        int cnt=0;
        double length=0;
        var lines = Files.readAllLines(Path.of(inputFile));
        //System.out.println(lines.get(1));
        var iterator = lines.iterator();
        try {
            BufferedWriter writer = new BufferedWriter(new FileWriter("./tongzhou_roadmap_edge.geo", false));
            writer.write("geo_id,type,coordinates,highway,lanes,length,maxspeed\n");
            writer.close(); // 关闭流，这将清空文件
        } catch (IOException e) {
            e.printStackTrace();
        }
        while (iterator.hasNext()) {
            length=0;
            cnt++;//iterator中的第几个
            String line = iterator.next().trim();
            if(cnt!=1) {
                builder=new StringBuilder();

                for(int i=0;i<line.length()-1;i++){
                    if(line.charAt(i)==','&&line.charAt(i+1)==','){
                        line=line.substring(0,i);
                    }
                }
                var strings = line.split(",");
                var str=strings[0].split("_");
                String s1=new String();
                for(String s:str){
                    s1+=s;
                }
                //System.out.println(s1);
                //builder.append(idSupply.getNew()-2);
                builder.append(s1);
                builder.append(",");
                builder.append("LineString");
                builder.append(",");
                builder.append("\"[");
                for(int i=1;i<strings.length;i+=2){
                    String s=String.format("[%s, %s]", strings[i], strings[i+1]);
                    if(i!=1){
                        length+=calculateLength(Double.parseDouble(strings[i-2]),Double.parseDouble(strings[i-1]),Double.parseDouble(strings[i]),Double.parseDouble(strings[i+1]));
                    }
                    builder.append(s);
                    if(i!=strings.length-2){
                        builder.append(", ");
                    }
                }
                builder.append("]\"");
                /*
                if(cnt%2==0) builder.append(",7,1,115.105,6");
                else builder.append(",7,1,10.05,6");

                 */
                builder.append(",7,1,");
                builder.append(String.valueOf(length));
                builder.append(",6");
                builder.append("\n");

                try {
                    BufferedWriter writer = new BufferedWriter(new FileWriter("./tongzhou_roadmap_edge.geo", true));
                    writer.write(String.valueOf(builder));
                    writer.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }
}
