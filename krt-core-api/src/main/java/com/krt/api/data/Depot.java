package com.krt.api.data;

import java.util.ArrayList;
import java.util.List;

/**
 * 车厂数据模型
 * 表示地铁系统中的车辆段/停车场信息
 */
public class Depot {
    private String id;
    private String name;
    private int x, y, z; // 车厂中心位置坐标
    private List<DepotFacility> facilities = new ArrayList<>();

    // 构造函数
    public Depot(String id, String name, int x, int y, int z) {
        this.id = id;
        this.name = name;
        this.x = x;
        this.y = y;
        this.z = z;
    }

    // 添加车厂设施
    public boolean addFacility(String type, int startX, int startY, int startZ, int endX, int endY, int endZ) {
        DepotFacility facility = new DepotFacility(type, startX, startY, startZ, endX, endY, endZ);
        
        // 检查停车库长度是否符合要求
        if (type.equals("停车库")) {
            int length = calculateDistance(startX, startZ, endX, endZ);
            // 停车库长度不能少于一辆列车的长度（假设一辆列车长度为20格）
            if (length < 20) {
                return false;
            }
        }
        
        facilities.add(facility);
        return true;
    }

    // 计算两点之间的距离（简化版，仅考虑X和Z轴）
    private int calculateDistance(int x1, int z1, int x2, int z2) {
        return (int) Math.sqrt(Math.pow(x2 - x1, 2) + Math.pow(z2 - z1, 2));
    }

    // 检查车厂是否具备必要设施
    public boolean hasRequiredFacilities() {
        boolean hasParking = false;
        boolean hasTestTrack = false;
        boolean hasWashTrack = false;
        
        for (DepotFacility facility : facilities) {
            switch (facility.getType()) {
                case "停车库":
                    hasParking = true;
                    break;
                case "试车线":
                    hasTestTrack = true;
                    break;
                case "洗车库":
                    hasWashTrack = true;
                    break;
            }
        }
        
        // 必须同时具备停车库、试车线和洗车库
        return hasParking && hasTestTrack && hasWashTrack;
    }

    // 获取指定类型的设施
    public List<DepotFacility> getFacilitiesByType(String type) {
        List<DepotFacility> result = new ArrayList<>();
        for (DepotFacility facility : facilities) {
            if (facility.getType().equals(type)) {
                result.add(facility);
            }
        }
        return result;
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

    public int getX() {
        return x;
    }

    public void setX(int x) {
        this.x = x;
    }

    public int getY() {
        return y;
    }

    public void setY(int y) {
        this.y = y;
    }

    public int getZ() {
        return z;
    }

    public void setZ(int z) {
        this.z = z;
    }

    public List<DepotFacility> getFacilities() {
        return facilities;
    }

    public void setFacilities(List<DepotFacility> facilities) {
        this.facilities = facilities;
    }

    // 车厂设施内部类
    public static class DepotFacility {
        private String type; // 设施类型：停车库、洗车库、检修库、试车线、出入库线等
        private int startX, startY, startZ; // 起始坐标
        private int endX, endY, endZ; // 结束坐标

        public DepotFacility(String type, int startX, int startY, int startZ, int endX, int endY, int endZ) {
            this.type = type;
            this.startX = startX;
            this.startY = startY;
            this.startZ = startZ;
            this.endX = endX;
            this.endY = endY;
            this.endZ = endZ;
        }

        // Getter和Setter方法
        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public int getStartX() {
            return startX;
        }

        public void setStartX(int startX) {
            this.startX = startX;
        }

        public int getStartY() {
            return startY;
        }

        public void setStartY(int startY) {
            this.startY = startY;
        }

        public int getStartZ() {
            return startZ;
        }

        public void setStartZ(int startZ) {
            this.startZ = startZ;
        }

        public int getEndX() {
            return endX;
        }

        public void setEndX(int endX) {
            this.endX = endX;
        }

        public int getEndY() {
            return endY;
        }

        public void setEndY(int endY) {
            this.endY = endY;
        }

        public int getEndZ() {
            return endZ;
        }

        public void setEndZ(int endZ) {
            this.endZ = endZ;
        }
    }
}