package com.daxia.mapmaching;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * <p>Project: mapmaching - RelChange
 * <p>Powered by berry On 2024-10-17
 *
 * @author berry
 * @version 1.0
 */
public class RelChange {
    private final static String inputGeo = "./rtic_xy.csv";
    private final static String inputTraj = "./tongzhou_merge.csv";
    private static StringBuilder builder = new StringBuilder();
    private final static IDSupplier idSupply=new IDSupplier();
    public static void main(String[] args) throws IOException {
        int cnt=0;
        var lines = Files.readAllLines(Path.of(inputTraj));//读取轨迹的每一行
        var iterator = lines.iterator();
        try {
            BufferedWriter writer = new BufferedWriter(new FileWriter("./tongzhou_roadmap_edge.rel", false));
            writer.write("rel_id,type,origin_id,destination_id\n");
            writer.close(); // 关闭流，这将清空文件
        } catch (IOException e) {
            e.printStackTrace();
        }
        while (iterator.hasNext()) {
            builder=new StringBuilder();
            cnt++;
            String line = iterator.next().trim();
            var strings = line.split(";");
            var s=strings[1].substring(1,strings[1].length()-1).split(",");
            //if(cnt==2) {
                for (int i = 0; i < s.length - 1; i++) {
                    builder.append(idSupply.getNew()-1);
                    builder.append(",geo,");
                    builder.append(s[i] + "," + s[i + 1]+"\n");
                    try {
                        BufferedWriter writer = new BufferedWriter(new FileWriter("./tongzhou_roadmap_edge.rel", true));
                        writer.write(String.valueOf(builder));
                        writer.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    builder.setLength(0);
                }
        }
    }
}
