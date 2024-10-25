package com.daxia;

import java.io.FileWriter;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;


import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

import com.daxia.mapmaching.DataConvertTask;
import com.daxia.mapmaching.IDSupplier;
import com.graphhopper.GraphHopper;
import com.graphhopper.config.Profile;
import com.graphhopper.util.PMap;
import com.graphhopper.matching.MapMatching;

public class Main {

    private final static String osmFile = "./osms/translate/out_01.pbf";
    private final static String graphHopperDirectory = "./cache";
    private final static String inputFile = "./traj.csv";

    private final static Logger logger = LogManager.getLogger(Lab.class);
    //IDSupplier:生成唯一标识符的类
    private final static IDSupplier dataIDSupply = new IDSupplier();
    private final static IDSupplier trajIDSupply = new IDSupplier();
    private final static IDSupplier userIDSupply = new IDSupplier();
    private final static Map<String,IDSupplier> usr2TrajId=new HashMap<>();
    private final static Map<String,Integer> usr2UsrId=new HashMap<>();
    private static int idCnt=0;
    private static String idIn;
    public static void main(String[] args) throws Exception {
        ThreadLocal<GraphHopper> hopperLocal = ThreadLocal.withInitial(() -> {
            var profile = new Profile("car").setVehicle("car").setWeighting("fastest");
            var hopper = new GraphHopper();
            hopper.setOSMFile(osmFile);
            hopper.setGraphHopperLocation(graphHopperDirectory);
            hopper.setProfiles(profile);
            hopper.importOrLoad();
            
            return hopper;
        });
        ThreadLocal<Logger> loggerLocal = ThreadLocal.withInitial(() -> LogManager.getLogger(Thread.currentThread().getName()));

        List<String> results = new ArrayList<>();
        
        // 创建线程池
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
            Runtime.getRuntime().availableProcessors() * 2,
            Runtime.getRuntime().availableProcessors() * 4,
            2,
            TimeUnit.MINUTES,
            new LinkedBlockingDeque<>());
            
            
        // 正式读取文件
        var lines = Files.readAllLines(Path.of(inputFile));
        String currentName = null;
        List<String> rawDatas = new ArrayList<>();
        int total = 0;

        for (var line : lines) {
            var name = line.substring(0, line.indexOf(',', 0));
            if (currentName == null) {
                currentName = name;
                rawDatas.add(line);
                continue;
            } 

            if (currentName.equals(name)) {
                rawDatas.add(line);
            } else {
                executor.submit(new DataConvertTask(
                    results, 
                    rawDatas, 
                    hopperLocal, 
                    loggerLocal));
                total++;

                currentName = null;
                rawDatas = new ArrayList<>();
            }
        }
        if (!rawDatas.isEmpty()) {
            executor.submit(new DataConvertTask(
                results, 
                rawDatas, 
                hopperLocal, 
                loggerLocal));
            total++;
        }

        executor.shutdown();

        try {
            executor.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        logger.info(String.format("processed %d rawdatas, success: %d, failure: %d, success rate: %f",
            total, 
            results.size(),
            total - results.size(),
            (double)results.size() / total));;
        //1.0;[59566331310, 5956643292, 59566344907];[1704086438, 1704086438, 1704087202];1.0;2.0;1.0;2024-01-01
        //1.0;[59566331310, 5956643292, 59566344907];[1704086438, 1704086438, 1704087202];1.0;2.0;1.0;2024-01-01
        // 获取results内容
        //synchronized (results) {
            //格式例子:1.0;[59566441551,59566441551,595652383];[1704040201,1704040201,1704041965];1;1.0;1.0;2024-01-01
            /*
            1.0;[59566441551
            59566441551
            595652383];[1704040201
            1704040201
            1704041965];1;1.0;1.0;2024-01-01
            */
            try (FileWriter fw = new FileWriter("tongzhou_train.csv", false); PrintWriter pw = new PrintWriter(fw)) {
                pw.print("id;path;tlist;usr_id;traj_id;vflag;start_time");
                pw.println();
            }
            try (FileWriter fw = new FileWriter("tongzhou_eval.csv", false); PrintWriter pw = new PrintWriter(fw)) {
                pw.print("id;path;tlist;usr_id;traj_id;vflag;start_time");
                pw.println();
            }
            try (FileWriter fw = new FileWriter("tongzhou_test.csv", false); PrintWriter pw = new PrintWriter(fw)) {
                pw.print("id;path;tlist;usr_id;traj_id;vflag;start_time");
                pw.println();
            }
            try (FileWriter fw = new FileWriter("tongzhou_merge.csv", false); PrintWriter pw = new PrintWriter(fw)) {
                pw.print("id;path;tlist;usr_id;traj_id;vflag;start_time");
                pw.println();
            }
            for (int j=0;j<results.size();j++) {
                System.out.println(results.get(j));
                List<String> grids=new ArrayList<>();
                String[] semicParts = results.get(j).split(";");
                String[] locArray = semicParts[1].substring(1, semicParts[1].length() - 1).split(",");
                String[] timeArray = semicParts[2].substring(1, semicParts[2].length() - 1).split(",");
                grids.add(semicParts[0]+";["+locArray[0]);
                for(int i=1;i<locArray.length-1;i++){
                    grids.add(locArray[i]);
                }
                grids.add(locArray[locArray.length-1]+"];["+timeArray[0]);
                for(int i=1;i<timeArray.length-1;i++){
                    grids.add(timeArray[i]);
                }
                grids.add(timeArray[timeArray.length-1]+"];"+semicParts[3]+";"+semicParts[4]+";"+semicParts[5]+";"+semicParts[6]);
                try (FileWriter fw = new FileWriter("tongzhou_merge.csv", true); PrintWriter pw = new PrintWriter(fw)) {
                    for (int k = 0; k < grids.size(); k++) {
                        //System.out.print(s+",");
                        pw.print(grids.get(k));
                        if (k != grids.size() - 1) pw.print(",");
                    }
                    pw.println();
                }
                //for(int f=1;f<=32;f++) {
                    if (j < 64) {
                        try (FileWriter fw = new FileWriter("tongzhou_train.csv", true); PrintWriter pw = new PrintWriter(fw)) {
                            for (int k = 0; k < grids.size(); k++) {
                                //System.out.print(s+",");
                                pw.print(grids.get(k));
                                if (k != grids.size() - 1) pw.print(",");
                            }
                            pw.println();
                        }
                    } else if (j < 96) {
                        try (FileWriter fw = new FileWriter("tongzhou_eval.csv", true); PrintWriter pw = new PrintWriter(fw)) {
                            for (int k = 0; k < grids.size(); k++) {
                                //System.out.print(s+",");
                                pw.print(grids.get(k));
                                if (k != grids.size() - 1) pw.print(",");
                            }
                            pw.println();
                        }
                    } else {
                        try (FileWriter fw = new FileWriter("tongzhou_test.csv", true); PrintWriter pw = new PrintWriter(fw)) {
                            for (int k = 0; k < grids.size(); k++) {
                                //System.out.print(s+",");
                                pw.print(grids.get(k));
                                if (k != grids.size() - 1) pw.print(",");
                            }
                            pw.println();
                        }
                    }
                }
                //System.out.print("\n");
            //}
        //}

        logger.info("done!");
    }
}
