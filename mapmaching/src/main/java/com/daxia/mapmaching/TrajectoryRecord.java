package com.daxia.mapmaching;

public record TrajectoryRecord (
    String id,
    double lat,//纬度
    double lon,//经度
    String time
){}