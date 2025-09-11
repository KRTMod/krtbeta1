package com.krt.api.data;

import java.util.*;

/**
 * 线路数据模型
 * 表示地铁系统中的线路信息
 */
public class Line {
    private String id;
    private String name;
    private String color;
    private int maxSpeed;
    private String depotId;
    private List<Station> stations = new ArrayList<>();
    private List<TrackPoint> trackPoints = new ArrayList<>();
    private boolean atcEnabled; // ATC系统是否启用
    private String powerSystem; // 电力系统类型：第三轨、接触网
    private String lineType; // 线路类型：正线、联络线、出入线、车场线、折返线、停车线

    // 坡度限制常量（根据规范）
    public static final int MAX_GRADIENT_MAIN_LINE = 30; // 正线最大坡度30‰
    public static final int MAX_GRADIENT_MAIN_LINE_DIFFICULT = 35; // 正线困难条件35‰
    public static final int MAX_GRADIENT_CONNECTION_LINE = 35; // 联络线、出入线最大坡度35‰
    public static final int MAX_GRADIENT_STATION = 3; // 车站最大坡度3‰
    public static final int MAX_GRADIENT_DEPOT_LINE = 1; // 车场线最大坡度1.5‰，这里简化为1
    public static final int MAX_GRADIENT_TURNING_LINE = 2; // 折返线、停车线最大坡度2‰

    // 最小转弯半径常量（根据规范）
    public static final int MIN_TURNING_RADIUS = 300; // 最小转弯半径300米（格）

    // 构造函数
    public Line(String id, String name, String color, int maxSpeed, String depotId) {
        this.id = id;
        this.name = name;
        this.color = color;
        this.maxSpeed = maxSpeed;
        this.depotId = depotId;
        this.atcEnabled = true;
        this.powerSystem = "第三轨";
        this.lineType = "正线";
    }

    // 添加车站到线路
    public boolean addStation(Station station, int order) {
        if (order >= 0 && order <= stations.size()) {
            stations.add(order, station);
            return true;
        }
        return false;
    }

    // 移除线路中的车站
    public boolean removeStation(String stationId) {
        for (Iterator<Station> it = stations.iterator(); it.hasNext();) {
            Station station = it.next();
            if (station.getId().equals(stationId)) {
                it.remove();
                return true;
            }
        }
        return false;
    }

    // 添加轨道位置点
    public void addTrackPoint(int x, int y, int z) {
        trackPoints.add(new TrackPoint(x, y, z));
    }

    // 检查线路有效性
    public boolean isValid() {
        // 检查车站数量是否合理
        if (stations.size() < 2) {
            return false;
        }
        
        // 检查车厂是否存在
        if (depotId == null || depotId.isEmpty()) {
            return false;
        }
        
        // 检查坡度是否符合规范
        if (!checkGradient()) {
            return false;
        }
        
        // 检查转弯半径是否符合规范
        if (!checkTurningRadius()) {
            return false;
        }
        
        return true;
    }

    // 检查坡度是否符合规范
    private boolean checkGradient() {
        // 根据线路类型获取最大允许坡度
        int maxAllowedGradient = MAX_GRADIENT_MAIN_LINE;
        switch (lineType) {
            case "联络线":
            case "出入线":
                maxAllowedGradient = MAX_GRADIENT_CONNECTION_LINE;
                break;
            case "车场线":
                maxAllowedGradient = MAX_GRADIENT_DEPOT_LINE;
                break;
            case "折返线":
            case "停车线":
                maxAllowedGradient = MAX_GRADIENT_TURNING_LINE;
                break;
        }
        
        // 检查相邻轨道点之间的坡度
        for (int i = 0; i < trackPoints.size() - 1; i++) {
            TrackPoint p1 = trackPoints.get(i);
            TrackPoint p2 = trackPoints.get(i + 1);
            
            // 计算水平距离（仅考虑X和Z轴）
            double horizontalDistance = Math.sqrt(Math.pow(p2.x - p1.x, 2) + Math.pow(p2.z - p1.z, 2));
            if (horizontalDistance == 0) continue;
            
            // 计算垂直高度差
            int verticalDifference = Math.abs(p2.y - p1.y);
            
            // 计算坡度（‰）
            double gradient = (verticalDifference / horizontalDistance) * 1000;
            
            if (gradient > maxAllowedGradient) {
                return false;
            }
        }
        
        return true;
    }

    // 检查转弯半径是否符合规范
    private boolean checkTurningRadius() {
        // 简化的转弯半径检查逻辑
        // 实际应用中可能需要更复杂的算法
        for (int i = 1; i < trackPoints.size() - 1; i++) {
            TrackPoint p0 = trackPoints.get(i - 1);
            TrackPoint p1 = trackPoints.get(i);
            TrackPoint p2 = trackPoints.get(i + 1);
            
            // 计算三个点形成的圆的半径
            double radius = calculateCircleRadius(p0, p1, p2);
            
            if (radius > 0 && radius < MIN_TURNING_RADIUS) {
                return false;
            }
        }
        
        return true;
    }

    // 计算三个点形成的圆的半径
    private double calculateCircleRadius(TrackPoint p0, TrackPoint p1, TrackPoint p2) {
        // 简化计算，仅考虑X和Z轴
        double A = p1.x - p0.x;
        double B = p1.z - p0.z;
        double C = p2.x - p0.x;
        double D = p2.z - p0.z;
        
        double E = A * (p0.x + p1.x) + B * (p0.z + p1.z);
        double F = C * (p0.x + p2.x) + D * (p0.z + p2.z);
        double G = 2 * (A * (p2.z - p1.z) - B * (p2.x - p1.x));
        
        if (G == 0) return -1; // 三点共线
        
        double cx = (D * E - B * F) / G;
        double cz = (A * F - C * E) / G;
        
        // 计算圆心到任意点的距离即为半径
        return Math.sqrt(Math.pow(p0.x - cx, 2) + Math.pow(p0.z - cz, 2));
    }

    // 获取线路上的车站顺序
    public List<Station> getStationsInOrder() {
        return new ArrayList<>(stations);
    }

    // Getter和Setter方法
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getColor() {
        return color;
    }

    public void setColor(String color) {
        this.color = color;
    }

    public int getMaxSpeed() {
        return maxSpeed;
    }

    public void setMaxSpeed(int maxSpeed) {
        this.maxSpeed = maxSpeed;
    }

    public String getDepotId() {
        return depotId;
    }

    public void setDepotId(String depotId) {
        this.depotId = depotId;
    }

    public boolean isAtcEnabled() {
        return atcEnabled;
    }

    public void setAtcEnabled(boolean atcEnabled) {
        this.atcEnabled = atcEnabled;
    }

    public String getPowerSystem() {
        return powerSystem;
    }

    public void setPowerSystem(String powerSystem) {
        this.powerSystem = powerSystem;
    }

    public String getLineType() {
        return lineType;
    }

    public void setLineType(String lineType) {
        this.lineType = lineType;
    }

    public List<TrackPoint> getTrackPoints() {
        return trackPoints;
    }

    public void setTrackPoints(List<TrackPoint> trackPoints) {
        this.trackPoints = trackPoints;
    }

    // 轨道点内部类
    public static class TrackPoint {
        public final int x, y, z;

        public TrackPoint(int x, int y, int z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }
}