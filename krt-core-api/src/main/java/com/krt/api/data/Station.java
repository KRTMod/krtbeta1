package com.krt.api.data;

/**
 * 车站数据模型
 * 表示地铁系统中的车站信息
 */
public class Station {
    private String id;
    private String name;
    private String lineId;
    private int x, y, z; // 坐标位置
    private boolean hasPlatformDoors; // 是否有屏蔽门
    private boolean isTransferStation; // 是否为换乘站
    private int platformCount; // 站台数量
    private String stationType; // 车站类型：地下站、地面站、高架站
    private int maxPassengerCapacity; // 最大乘客容量

    // 构造函数
    public Station(String id, String name, String lineId, int x, int y, int z) {
        this.id = id;
        this.name = name;
        this.lineId = lineId;
        this.x = x;
        this.y = y;
        this.z = z;
        this.hasPlatformDoors = true;
        this.isTransferStation = false;
        this.platformCount = 2;
        this.stationType = "地下站";
        this.maxPassengerCapacity = 1000;
    }

    // 检查车站位置是否适合建设
    public boolean isSuitableForConstruction() {
        // 根据不同车站类型检查位置合理性
        // 这里可以添加更详细的检查逻辑
        return true;
    }

    // 检查屏蔽门状态
    public boolean checkPlatformDoorStatus() {
        // 检查屏蔽门是否正常工作
        return hasPlatformDoors;
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

    public String getLineId() {
        return lineId;
    }

    public void setLineId(String lineId) {
        this.lineId = lineId;
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

    public boolean isHasPlatformDoors() {
        return hasPlatformDoors;
    }

    public void setHasPlatformDoors(boolean hasPlatformDoors) {
        this.hasPlatformDoors = hasPlatformDoors;
    }

    public boolean isTransferStation() {
        return isTransferStation;
    }

    public void setTransferStation(boolean transferStation) {
        isTransferStation = transferStation;
    }

    public int getPlatformCount() {
        return platformCount;
    }

    public void setPlatformCount(int platformCount) {
        this.platformCount = platformCount;
    }

    public String getStationType() {
        return stationType;
    }

    public void setStationType(String stationType) {
        this.stationType = stationType;
    }

    public int getMaxPassengerCapacity() {
        return maxPassengerCapacity;
    }

    public void setMaxPassengerCapacity(int maxPassengerCapacity) {
        this.maxPassengerCapacity = maxPassengerCapacity;
    }
}