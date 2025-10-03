package com.krt.mod.system;

import com.krt.mod.entity.TrainCar;
import com.krt.mod.entity.TrainConsist;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * 车辆段系统类，负责管理车辆段设备及其维护设施
 */
public class DepotSystem {
    // 车辆段类型
    public enum DepotType {
        MAJOR_DEPOT, // 主车辆段
        MINOR_DEPOT, // 辅助车辆段
        STORAGE_DEPOT // 存车段
    }
    
    // 车辆段区域类型
    public enum DepotAreaType {
        PARKING_AREA, // 停车区域
        WASHING_BAY, // 洗车库
        MAINTENANCE_BAY, // 检修库
        TEST_TRACK // 试车线
    }
    
    private final World world;
    private final Map<String, Depot> depots = new HashMap<>();
    private int nextDepotId = 1;
    
    /**
     * 创建车辆段系统
     */
    public DepotSystem(World world) {
        this.world = world;
    }
    
    /**
     * 创建新的车辆段
     */
    public Depot createDepot(String name, DepotType type, BlockPos position) {
        // 生成车辆段ID
        String depotId = "depot_" + nextDepotId++;
        
        // 创建新车辆段
        Depot depot = new Depot(depotId, name, type, position, world);
        
        // 添加到管理列表
        depots.put(depotId, depot);
        
        return depot;
    }
    
    /**
     * 移除车辆段
     */
    public boolean removeDepot(String depotId) {
        Depot depot = depots.get(depotId);
        if (depot == null) {
            return false;
        }
        
        // 确保车辆段中没有列车
        if (depot.hasTrains()) {
            return false;
        }
        
        // 从管理列表中移除
        depots.remove(depotId);
        
        return true;
    }
    
    /**
     * 在车辆段中添加区域
     */
    public boolean addDepotArea(String depotId, DepotAreaType type, BlockPos startPos, BlockPos endPos) {
        Depot depot = depots.get(depotId);
        if (depot == null) {
            return false;
        }
        
        return depot.addArea(type, startPos, endPos);
    }
    
    /**
     * 分配列车到车辆段
     */
    public boolean assignTrainToDepot(TrainConsist consist, String depotId) {
        Depot depot = depots.get(depotId);
        if (depot == null || consist == null) {
            return false;
        }
        
        return depot.assignTrain(consist);
    }
    
    /**
     * 从车辆段中移除列车
     */
    public boolean removeTrainFromDepot(TrainConsist consist, String depotId) {
        Depot depot = depots.get(depotId);
        if (depot == null || consist == null) {
            return false;
        }
        
        return depot.removeTrain(consist);
    }
    
    /**
     * 在车辆段中洗车
     */
    public boolean washTrain(TrainConsist consist, String depotId) {
        Depot depot = depots.get(depotId);
        if (depot == null || consist == null) {
            return false;
        }
        
        return depot.washTrain(consist);
    }
    
    /**
     * 在车辆段中检修列车
     */
    public boolean maintainTrain(TrainConsist consist, String depotId) {
        Depot depot = depots.get(depotId);
        if (depot == null || consist == null) {
            return false;
        }
        
        return depot.maintainTrain(consist);
    }
    
    /**
     * 在车辆段中测试列车
     */
    public boolean testTrain(TrainConsist consist, String depotId) {
        Depot depot = depots.get(depotId);
        if (depot == null || consist == null) {
            return false;
        }
        
        return depot.testTrain(consist);
    }
    
    /**
     * 查找车辆段
     */
    public Depot getDepot(String depotId) {
        return depots.get(depotId);
    }
    
    /**
     * 查找车辆段中是否有可用的检修库
     */
    public boolean hasAvailableMaintenanceBay(String depotId) {
        Depot depot = depots.get(depotId);
        if (depot == null) {
            return false;
        }
        
        return depot.hasAvailableMaintenanceBay();
    }
    
    /**
     * 更新系统
     */
    public void update() {
        // 更新所有车辆段
        for (Depot depot : depots.values()) {
            depot.update();
        }
    }
    
    /**
     * 获取车辆段状态报告
     */
    public Text getDepotStatusReport(String depotId) {
        Depot depot = depots.get(depotId);
        if (depot == null) {
            return Text.literal("车辆段不存在: " + depotId);
        }
        
        return depot.getStatusReport();
    }
    
    /**
     * 获取系统状态报告
     */
    public Text getSystemStatusReport() {
        // 计算各类型车辆段数量
        int majorDepots = 0;
        int minorDepots = 0;
        int storageDepots = 0;
        int totalTrains = 0;
        
        for (Depot depot : depots.values()) {
            switch (depot.getType()) {
                case MAJOR_DEPOT -> majorDepots++;
                case MINOR_DEPOT -> minorDepots++;
                case STORAGE_DEPOT -> storageDepots++;
            }
            totalTrains += depot.getTrainCount();
        }
        
        return Text.literal(
            "车辆段系统状态: " +
            "车辆段总数: " + depots.size() + "(主车辆段: " + majorDepots + ", 辅助车辆段: " + minorDepots + ", 存车段: " + storageDepots + ") " +
            "停放列车总数: " + totalTrains
        );
    }
    
    // Getters and setters
    public Map<String, Depot> getDepots() {
        return new HashMap<>(depots);
    }
    
    public World getWorld() {
        return world;
    }
    
    /**
     * 车辆段类，表示单个车辆段
     */
    public static class Depot {
        private final String id;
        private final String name;
        private final DepotType type;
        private final BlockPos position;
        private final World world;
        private final List<DepotArea> areas = new ArrayList<>();
        private final List<TrainConsist> assignedTrains = new ArrayList<>();
        private int staffCount = 0;
        private int maxStaffCount = 20;
        private boolean isOperational = true;
        
        public Depot(String id, String name, DepotType type, BlockPos position, World world) {
            this.id = id;
            this.name = name;
            this.type = type;
            this.position = position;
            this.world = world;
        }
        
        /**
         * 添加区域
         */
        public boolean addArea(DepotAreaType type, BlockPos startPos, BlockPos endPos) {
            // 检查区域是否重叠
            for (DepotArea area : areas) {
                if (area.intersects(startPos, endPos)) {
                    return false;
                }
            }
            
            // 创建新区域
            DepotArea area = new DepotArea(type, startPos, endPos);
            areas.add(area);
            
            return true;
        }
        
        /**
         * 分配列车到车辆段
         */
        public boolean assignTrain(TrainConsist consist) {
            if (!isOperational || assignedTrains.contains(consist)) {
                return false;
            }
            
            // 检查是否有可用的停车区域
            if (!hasAvailableParking()) {
                return false;
            }
            
            assignedTrains.add(consist);
            
            // 更新列车的车辆段信息
            if (consist.getTrainEntity() != null) {
                consist.getTrainEntity().setCurrentDepot(name);
            }
            
            return true;
        }
        
        /**
         * 从车辆段中移除列车
         */
        public boolean removeTrain(TrainConsist consist) {
            return assignedTrains.remove(consist);
        }
        
        /**
         * 洗车
         */
        public boolean washTrain(TrainConsist consist) {
            if (!isOperational || !assignedTrains.contains(consist)) {
                return false;
            }
            
            // 检查是否有可用的洗车库
            if (!hasAvailableWashingBay()) {
                return false;
            }
            
            // 开始洗车过程
            for (TrainCar car : consist.getCars()) {
                car.setCleanliness(100); // 假设洗车后清洁度为100%
            }
            
            return true;
        }
        
        /**
         * 检修列车
         */
        public boolean maintainTrain(TrainConsist consist) {
            if (!isOperational || !assignedTrains.contains(consist)) {
                return false;
            }
            
            // 检查是否有可用的检修库
            if (!hasAvailableMaintenanceBay()) {
                return false;
            }
            
            // 开始检修过程
            consist.startMaintenance();
            
            // 维修每辆车
            for (TrainCar car : consist.getCars()) {
                car.repair();
            }
            
            return true;
        }
        
        /**
         * 测试列车
         */
        public boolean testTrain(TrainConsist consist) {
            if (!isOperational || !assignedTrains.contains(consist)) {
                return false;
            }
            
            // 检查是否有可用的试车线
            if (!hasAvailableTestTrack()) {
                return false;
            }
            
            // 开始测试过程
            // 这里可以添加测试逻辑，例如检查列车的各项性能指标
            
            return true;
        }
        
        /**
         * 检查是否有可用的停车区域
         */
        public boolean hasAvailableParking() {
            int availableSpaces = 0;
            int requiredSpaces = 0;
            
            // 计算可用停车空间
            for (DepotArea area : areas) {
                if (area.getType() == DepotAreaType.PARKING_AREA) {
                    availableSpaces += area.getCapacity();
                }
            }
            
            // 计算已用停车空间
            for (TrainConsist consist : assignedTrains) {
                requiredSpaces += consist.getCarCount();
            }
            
            return availableSpaces > requiredSpaces;
        }
        
        /**
         * 检查是否有可用的洗车库
         */
        public boolean hasAvailableWashingBay() {
            return hasAvailableArea(DepotAreaType.WASHING_BAY);
        }
        
        /**
         * 检查是否有可用的检修库
         */
        public boolean hasAvailableMaintenanceBay() {
            return hasAvailableArea(DepotAreaType.MAINTENANCE_BAY);
        }
        
        /**
         * 检查是否有可用的试车线
         */
        public boolean hasAvailableTestTrack() {
            return hasAvailableArea(DepotAreaType.TEST_TRACK);
        }
        
        /**
         * 检查是否有可用的指定类型区域
         */
        private boolean hasAvailableArea(DepotAreaType type) {
            for (DepotArea area : areas) {
                if (area.getType() == type && !area.isInUse()) {
                    return true;
                }
            }
            return false;
        }
        
        /**
         * 更新车辆段
         */
        public void update() {
            // 更新所有区域
            for (DepotArea area : areas) {
                area.update();
            }
            
            // 检查是否有足够的工作人员
            if (staffCount < maxStaffCount * 0.5) {
                isOperational = false;
            } else {
                isOperational = true;
            }
            
            // 如果车辆段不运营，处理相关逻辑
            if (!isOperational) {
                // 可以添加不运营时的处理逻辑
            }
        }
        
        /**
         * 获取车辆段状态报告
         */
        public Text getStatusReport() {
            String typeText;
            switch (type) {
                case MAJOR_DEPOT -> typeText = "主车辆段";
                case MINOR_DEPOT -> typeText = "辅助车辆段";
                case STORAGE_DEPOT -> typeText = "存车段";
                default -> typeText = "未知类型";
            }
            
            // 计算各类型区域数量
            int parkingAreas = 0;
            int washingBays = 0;
            int maintenanceBays = 0;
            int testTracks = 0;
            
            for (DepotArea area : areas) {
                switch (area.getType()) {
                    case PARKING_AREA -> parkingAreas++;
                    case WASHING_BAY -> washingBays++;
                    case MAINTENANCE_BAY -> maintenanceBays++;
                    case TEST_TRACK -> testTracks++;
                }
            }
            
            return Text.literal(
                "车辆段: " + name + ", 类型: " + typeText + ", 状态: " + (isOperational ? "运营中" : "非运营") +
                ", 位置: " + position.getX() + "," + position.getY() + "," + position.getZ() +
                ", 工作人员: " + staffCount + "/" + maxStaffCount +
                ", 区域: 停车区(" + parkingAreas + ") 洗车库(" + washingBays + ") 检修库(" + maintenanceBays + ") 试车线(" + testTracks + ")" +
                ", 停放列车: " + assignedTrains.size() + "列"
            );
        }
        
        /**
         * 检查车辆段中是否有列车
         */
        public boolean hasTrains() {
            return !assignedTrains.isEmpty();
        }
        
        // Getters and setters
        public String getId() {
            return id;
        }
        
        public String getName() {
            return name;
        }
        
        public DepotType getType() {
            return type;
        }
        
        public BlockPos getPosition() {
            return position;
        }
        
        public List<DepotArea> getAreas() {
            return new ArrayList<>(areas);
        }
        
        public List<TrainConsist> getAssignedTrains() {
            return new ArrayList<>(assignedTrains);
        }
        
        public int getStaffCount() {
            return staffCount;
        }
        
        public void setStaffCount(int staffCount) {
            this.staffCount = Math.max(0, Math.min(maxStaffCount, staffCount));
        }
        
        public int getMaxStaffCount() {
            return maxStaffCount;
        }
        
        public void setMaxStaffCount(int maxStaffCount) {
            this.maxStaffCount = Math.max(0, maxStaffCount);
            this.staffCount = Math.min(this.staffCount, maxStaffCount);
        }
        
        public boolean isOperational() {
            return isOperational;
        }
        
        public int getTrainCount() {
            return assignedTrains.size();
        }
    }
    
    /**
     * 车辆段区域类，表示车辆段中的一个区域
     */
    public static class DepotArea {
        private final DepotAreaType type;
        private final BlockPos startPos;
        private final BlockPos endPos;
        private boolean isInUse = false;
        private int capacity;
        
        public DepotArea(DepotAreaType type, BlockPos startPos, BlockPos endPos) {
            this.type = type;
            this.startPos = startPos;
            this.endPos = endPos;
            
            // 计算容量
            calculateCapacity();
        }
        
        /**
         * 计算区域容量
         */
        private void calculateCapacity() {
            // 根据区域类型和大小计算容量
            int length = Math.abs(endPos.getX() - startPos.getX());
            
            switch (type) {
                case PARKING_AREA -> capacity = length / 10; // 假设每10格可以停放一列标准长度的列车
                case WASHING_BAY -> capacity = 1; // 每个洗车库一次只能洗一列列车
                case MAINTENANCE_BAY -> capacity = 1; // 每个检修库一次只能检修一列列车
                case TEST_TRACK -> capacity = 1; // 每个试车线一次只能测试一列列车
                default -> capacity = 0;
            }
        }
        
        /**
         * 检查区域是否与指定范围重叠
         */
        public boolean intersects(BlockPos start, BlockPos end) {
            // 简化的重叠检查逻辑
            return !(endPos.getX() < start.getX() || 
                     startPos.getX() > end.getX() || 
                     endPos.getZ() < start.getZ() || 
                     startPos.getZ() > end.getZ());
        }
        
        /**
         * 更新区域
         */
        public void update() {
            // 可以添加区域状态更新逻辑
        }
        
        // Getters and setters
        public DepotAreaType getType() {
            return type;
        }
        
        public BlockPos getStartPos() {
            return startPos;
        }
        
        public BlockPos getEndPos() {
            return endPos;
        }
        
        public boolean isInUse() {
            return isInUse;
        }
        
        public void setInUse(boolean inUse) {
            this.isInUse = inUse;
        }
        
        public int getCapacity() {
            return capacity;
        }
    }
}