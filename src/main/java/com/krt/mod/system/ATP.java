package com.krt.mod.system;

import net.minecraft.world.World;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import com.krt.mod.entity.TrainEntity;
import com.krt.mod.block.ATPSignalBlockEntity;
import com.krt.mod.block.SwitchTrackBlock;
import com.krt.mod.system.CBTCSystem;
import com.krt.mod.system.LogSystem;

import java.util.HashMap;
import java.util.Map;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 列车自动防护系统（ATP - Automatic Train Protection）
 * 负责监督列车速度，确保安全间隔，防止超速和冒进信号
 */
public class ATP {
    private static final Map<World, ATP> INSTANCES = new HashMap<>();
    private final World world;
    private final TrackSectionManager trackSectionManager;
    private final Map<String, ATPTrainData> trainDataMap = new HashMap<>();
    private final Map<String, SpeedRestriction> speedRestrictions = new HashMap<>();
    private boolean emergencyMode = false; // 紧急模式

    private ATP(World world) {
        this.world = world;
        this.trackSectionManager = TrackSectionManager.getInstance(world);
        initialize();
    }

    /**
     * 获取实例（单例模式）
     */
    public static ATP getInstance(World world) {
        return INSTANCES.computeIfAbsent(world, ATP::new);
    }

    /**
     * 初始化ATP系统
     */
    private void initialize() {
        LogSystem.systemLog("ATP系统初始化完成");
        // 加载默认速度限制
        loadDefaultSpeedRestrictions();
    }

    /**
     * 加载默认速度限制
     */
    private void loadDefaultSpeedRestrictions() {
        // 添加默认的速度限制规则
        addSpeedRestriction("default", 80.0); // 默认最大速度80km/h
        addSpeedRestriction("station_area", 30.0); // 车站区域30km/h
        addSpeedRestriction("curve", 50.0); // 弯道50km/h
        addSpeedRestriction("switch", 40.0); // 道岔区域40km/h
        addSpeedRestriction("slippery", 60.0); // 湿滑轨道60km/h
    }

    /**
     * 更新ATP系统状态
     */
    public void update() {
        // 1. 更新所有列车的ATP数据
        updateAllTrainATPData();
        
        // 2. 检查每个列车的安全状态
        checkTrainSafety();
    }

    /**
     * 更新所有列车的ATP数据
     */
    private void updateAllTrainATPData() {
        // 获取所有列车实体
        List<TrainEntity> trains = world.getEntitiesByClass(TrainEntity.class, 
                new Box(0, 0, 0, 6000000, 6000000, 6000000), 
                entity -> true);

        for (TrainEntity train : trains) {
            String trainId = train.getTrainId();
            ATPTrainData data = trainDataMap.computeIfAbsent(trainId, k -> new ATPTrainData(trainId));
            
            // 更新列车基本信息
            data.setPosition(train.getBlockPos());
            // 使用当前速度，已经是km/h单位
            data.setSpeed(train.getCurrentSpeed());
            // 从旋转向量获取方向
            Vec3d rotation = train.getRotationVector();
            String direction = "北";
            if (Math.abs(rotation.x) > Math.abs(rotation.z)) {
                direction = rotation.x > 0 ? "东" : "西";
            } else {
                direction = rotation.z > 0 ? "南" : "北";
            }
            data.setDirection(direction);
            data.setCurrentSection(trackSectionManager.getSectionAt(train.getBlockPos()));
            data.setLineId(train.getCurrentLine());
            
            // 检测前方信号机
            detect前方信号机(data);
            
            // 检测前方轨道状态
            detect前方轨道状态(data);
            
            // 计算允许速度
            calculate允许速度(data);
        }
    }

    /**
     * 检测前方信号机
     */
    private void detect前方信号机(ATPTrainData data) {
        BlockPos trainPos = data.getPosition();
        String direction = data.getDirection();
        
        // 向前方搜索信号机（最多搜索200米）
            for (int distance = 1; distance <= 200; distance++) {
                BlockPos searchPos = getSearchPosition(trainPos, direction, distance);
                
                // 简化实现，跳过isLoaded检查
                ATPSignalBlockEntity signal = findSignalAtPosition(searchPos);
                if (signal != null) {
                    // 使用位置信息作为信号机ID
                    data.set前方信号机(searchPos.toString());
                    data.set前方信号机状态(signal.getSignalStatus());
                    data.set前方信号机距离(distance);
                    break;
                }
            }
    }

    /**
     * 获取搜索位置
     */
    private BlockPos getSearchPosition(BlockPos basePos, String direction, int distance) {
        switch (direction) {
            case "north":
                return basePos.north(distance);
            case "south":
                return basePos.south(distance);
            case "east":
                return basePos.east(distance);
            case "west":
                return basePos.west(distance);
            default:
                return basePos.north(distance);
        }
    }

    /**
     * 查找位置上的信号机
     */
    private ATPSignalBlockEntity findSignalAtPosition(BlockPos pos) {
        if (world.getBlockEntity(pos) instanceof ATPSignalBlockEntity) {
            return (ATPSignalBlockEntity) world.getBlockEntity(pos);
        }
        return null;
    }

    /**
     * 检测前方轨道状态
     */
    private void detect前方轨道状态(ATPTrainData data) {
        BlockPos trainPos = data.getPosition();
        String direction = data.getDirection();
        
        // 检查前方50米内的轨道状态
        for (int distance = 1; distance <= 50; distance++) {
            BlockPos searchPos = getSearchPosition(trainPos, direction, distance);
            
            // 简化实现，跳过isLoaded检查
            // 检查是否有障碍物
            if (isObstacleAtPosition(searchPos)) {
                data.set前方障碍物(true);
                data.set前方障碍物距离(distance);
                return;
            }
            
            // 检查是否有道岔
            if (world.getBlockState(searchPos).getBlock() instanceof SwitchTrackBlock) {
                data.set前方有道岔(true);
                data.set前方道岔距离(distance);
                // 简化实现，假设道岔状态正确
                data.set前方道岔状态正确(true);
            }
            
            // 检查轨道条件
            checkTrackConditions(data, searchPos, distance);
        }
    }

    /**
     * 检查位置是否有障碍物
     */
    private boolean isObstacleAtPosition(BlockPos pos) {
        // 检查是否有其他列车
        List<TrainEntity> otherTrains = world.getEntitiesByClass(TrainEntity.class, 
                new Box(pos.getX(), pos.getY(), pos.getZ(), pos.getX()+1, pos.getY()+1, pos.getZ()+1), 
                entity -> true);
        
        if (!otherTrains.isEmpty()) {
            return true;
        }
        
        // 检查是否有阻挡方块
        // 简化实现，使用isAir代替isSolid
        return !world.isAir(pos);
    }

    /**
     * 检查轨道条件
     */
    private void checkTrackConditions(ATPTrainData data, BlockPos pos, int distance) {
        // 检查是否在车站区域
        if (isInStationArea(pos)) {
            data.setInStationArea(true);
            data.setStationAreaStartDistance(distance);
        }
        
        // 检查是否是弯道
        if (isCurvedTrack(pos)) {
            data.setInCurve(true);
            data.setCurveStartDistance(distance);
        }
        
        // 检查轨道附着力（模拟湿滑条件）
        if (isLowAdhesionTrack(pos)) {
            data.setLowAdhesion(true);
            data.setLowAdhesionStartDistance(distance);
        }
    }

    /**
     * 检查是否在车站区域
     */
    private boolean isInStationArea(BlockPos pos) {
        // TODO: 实现车站区域检测
        return false;
    }

    /**
     * 检查是否是弯道
     */
    private boolean isCurvedTrack(BlockPos pos) {
        // TODO: 实现弯道检测
        return false;
    }

    /**
     * 检查轨道附着力是否较低
     */
    private boolean isLowAdhesionTrack(BlockPos pos) {
        // 简单模拟：雨天或下雪时附着力较低
        return world.isRaining() || world.isThundering();
    }

    /**
     * 计算允许速度
     */
    private void calculate允许速度(ATPTrainData data) {
        double basicSpeed = speedRestrictions.getOrDefault("default", new SpeedRestriction("default", 80.0)).getSpeed();
        double currentSpeed = data.getSpeed();
        
        // 根据信号机状态调整速度
        if (data.get前方信号机状态() == ATPSignalBlockEntity.SignalStatus.RED) {
            data.set允许速度(0.0); // 红灯停车
        } else if (data.get前方信号机状态() == ATPSignalBlockEntity.SignalStatus.YELLOW) {
            data.set允许速度(Math.min(basicSpeed * 0.5, currentSpeed * 0.8)); // 黄灯减速
        } else {
            // 正常情况，根据轨道条件调整速度
            double adjustedSpeed = basicSpeed;
            
            // 车站区域速度限制
            if (data.isInStationArea()) {
                adjustedSpeed = Math.min(adjustedSpeed, speedRestrictions.get("station_area").getSpeed());
            }
            
            // 弯道速度限制
            if (data.isInCurve()) {
                adjustedSpeed = Math.min(adjustedSpeed, speedRestrictions.get("curve").getSpeed());
            }
            
            // 道岔区域速度限制
            if (data.is前方有道岔()) {
                adjustedSpeed = Math.min(adjustedSpeed, speedRestrictions.get("switch").getSpeed());
            }
            
            // 湿滑轨道速度限制
            if (data.isLowAdhesion()) {
                adjustedSpeed = Math.min(adjustedSpeed, speedRestrictions.get("slippery").getSpeed());
            }
            
            data.set允许速度(adjustedSpeed);
        }
        
        // 计算紧急制动距离
        calculate制动距离(data);
    }

    /**
     * 计算制动距离
     */
    private void calculate制动距离(ATPTrainData data) {
        double speed = data.getSpeed();
        double adhesionFactor = data.isLowAdhesion() ? 0.7 : 1.0; // 湿滑轨道附着力系数降低
        
        // 简单的制动距离计算公式：v²/(2*a*g*f)
        // v: 速度(km/h)，a: 减速度系数，g: 重力加速度，f: 附着力系数
        double brakingDistance = (speed * speed) / (2 * 7 * 9.8 * adhesionFactor);
        data.set制动距离(brakingDistance);
    }

    /**
     * 检查列车安全状态
     */
    private void checkTrainSafety() {
        for (ATPTrainData data : trainDataMap.values()) {
            String trainId = data.getTrainId();
            double currentSpeed = data.getSpeed();
            double allowedSpeed = data.get允许速度();
            
            // 检查是否超速
            if (currentSpeed > allowedSpeed * 1.1) { // 允许10%的误差
                // 触发常用制动
                triggerServiceBrake(trainId);
                LogSystem.atpWarning("ATP系统：列车 " + trainId + " 超速，触发常用制动");
                
                // 触发速度超限警报
                String alertMessage = "列车严重超速！当前速度: " + String.format("%.1f", currentSpeed) + "km/h, 允许速度: " + String.format("%.1f", allowedSpeed) + "km/h";
                Map<String, Object> additionalInfo = new HashMap<>();
                additionalInfo.put("currentSpeed", currentSpeed);
                additionalInfo.put("allowedSpeed", allowedSpeed);
                additionalInfo.put("overspeedPercentage", ((currentSpeed - allowedSpeed) / allowedSpeed) * 100);
                
                // 获取CBTC系统实例并触发警报
                CBTCSystem.getInstance(world).triggerAlert(trainId, CBTCSystem.AlertType.SPEED_OVERLIMIT, alertMessage, additionalInfo);
            } else if (currentSpeed > allowedSpeed) {
                // 轻微超速，只触发警告
                String alertMessage = "列车超速警告！当前速度: " + String.format("%.1f", currentSpeed) + "km/h, 允许速度: " + String.format("%.1f", allowedSpeed) + "km/h";
                CBTCSystem.getInstance(world).triggerAlert(trainId, CBTCSystem.AlertType.SPEED_OVERLIMIT_WARNING, alertMessage);
            }
            
            // 检查是否有紧急情况
            if (hasEmergencyCondition(data)) {
                // 触发紧急制动
                triggerEmergencyBrake(trainId);
                LogSystem.error("ATP系统：列车 " + trainId + " 遇到紧急情况，触发紧急制动");
            }
            
            // 检查信号机越红灯
            if (data.get前方信号机状态() == ATPSignalBlockEntity.SignalStatus.RED && 
                data.get前方信号机距离() < data.get制动距离()) {
                triggerEmergencyBrake(trainId);
                LogSystem.error("ATP系统：列车 " + trainId + " 即将冒进红灯，触发紧急制动");
            }
        }
    }

    /**
     * 检查是否有紧急情况
     */
    private boolean hasEmergencyCondition(ATPTrainData data) {
        // 前方有障碍物且距离小于安全距离
        if (data.is前方障碍物() && data.get前方障碍物距离() < data.get制动距离() * 1.2) {
            return true;
        }
        
        // 前方道岔位置不正确
        if (data.is前方有道岔() && !data.is前方道岔状态正确() && data.get前方道岔距离() < 10) {
            return true;
        }
        
        // 紧急模式
        if (emergencyMode) {
            return true;
        }
        
        return false;
    }

    /**
     * 触发常用制动
     */
    private void triggerServiceBrake(String trainId) {
        TrainControlSystem trainControlSystem = TrainControlSystem.getInstance(world);
        trainControlSystem.applyServiceBrake(trainId);
    }

    /**
     * 触发紧急制动
     */
    private void triggerEmergencyBrake(String trainId) {
        TrainControlSystem trainControlSystem = TrainControlSystem.getInstance(world);
        trainControlSystem.applyEmergencyBrake(trainId);
    }

    /**
     * 添加速度限制
     */
    public void addSpeedRestriction(String restrictionId, double speed) {
        speedRestrictions.put(restrictionId, new SpeedRestriction(restrictionId, speed));
        LogSystem.systemLog("ATP系统：添加速度限制 " + restrictionId + "，速度 " + speed + "km/h");
    }

    /**
     * 设置紧急模式
     */
    public void setEmergencyMode(boolean emergencyMode) {
        this.emergencyMode = emergencyMode;
        String mode = emergencyMode ? "启用" : "禁用";
        LogSystem.systemLog("ATP系统：紧急模式" + mode);
        
        if (emergencyMode) {
            // 紧急模式下，所有列车紧急制动
            for (String trainId : trainDataMap.keySet()) {
                triggerEmergencyBrake(trainId);
            }
        }
    }

    /**
     * 获取列车ATP数据
     */
    public ATPTrainData getTrainATPData(String trainId) {
        return trainDataMap.get(trainId);
    }

    /**
     * ATP列车数据类
     */
    public static class ATPTrainData {
        private final String trainId;
        private BlockPos position = new BlockPos(0, 0, 0);
        private double speed = 0;
        private String direction = "";
        private TrackSectionManager.TrackSection currentSection = null;
        private String lineId = "";
        private String 前方信号机 = "";
        private ATPSignalBlockEntity.SignalStatus 前方信号机状态 = ATPSignalBlockEntity.SignalStatus.RED;
        private int 前方信号机距离 = Integer.MAX_VALUE;
        private boolean 前方障碍物 = false;
        private int 前方障碍物距离 = Integer.MAX_VALUE;
        private boolean 前方有道岔 = false;
        private int 前方道岔距离 = Integer.MAX_VALUE;
        private boolean 前方道岔状态正确 = true;
        private boolean inStationArea = false;
        private int stationAreaStartDistance = Integer.MAX_VALUE;
        private boolean inCurve = false;
        private int curveStartDistance = Integer.MAX_VALUE;
        private boolean lowAdhesion = false;
        private int lowAdhesionStartDistance = Integer.MAX_VALUE;
        private double 允许速度 = 80.0;
        private double 制动距离 = 0;
        private long lastUpdateTime = System.currentTimeMillis();

        public ATPTrainData(String trainId) {
            this.trainId = trainId;
        }

        // Getters and setters
        public String getTrainId() { return trainId; }
        public BlockPos getPosition() { return position; }
        public void setPosition(BlockPos position) { this.position = position; }
        public double getSpeed() { return speed; }
        public void setSpeed(double speed) { this.speed = speed; }
        public String getDirection() { return direction; }
        public void setDirection(String direction) { this.direction = direction; }
        public TrackSectionManager.TrackSection getCurrentSection() { return currentSection; }
        public void setCurrentSection(TrackSectionManager.TrackSection currentSection) { this.currentSection = currentSection; }
        public String getLineId() { return lineId; }
        public void setLineId(String lineId) { this.lineId = lineId; }
        public String get前方信号机() { return 前方信号机; }
        public void set前方信号机(String 前方信号机) { this.前方信号机 = 前方信号机; }
        public ATPSignalBlockEntity.SignalStatus get前方信号机状态() { return 前方信号机状态; }
        public void set前方信号机状态(ATPSignalBlockEntity.SignalStatus 前方信号机状态) { this.前方信号机状态 = 前方信号机状态; }
        public int get前方信号机距离() { return 前方信号机距离; }
        public void set前方信号机距离(int 前方信号机距离) { this.前方信号机距离 = 前方信号机距离; }
        public boolean is前方障碍物() { return 前方障碍物; }
        public void set前方障碍物(boolean 前方障碍物) { this.前方障碍物 = 前方障碍物; }
        public int get前方障碍物距离() { return 前方障碍物距离; }
        public void set前方障碍物距离(int 前方障碍物距离) { this.前方障碍物距离 = 前方障碍物距离; }
        public boolean is前方有道岔() { return 前方有道岔; }
        public void set前方有道岔(boolean 前方有道岔) { this.前方有道岔 = 前方有道岔; }
        public int get前方道岔距离() { return 前方道岔距离; }
        public void set前方道岔距离(int 前方道岔距离) { this.前方道岔距离 = 前方道岔距离; }
        public boolean is前方道岔状态正确() { return 前方道岔状态正确; }
        public void set前方道岔状态正确(boolean 前方道岔状态正确) { this.前方道岔状态正确 = 前方道岔状态正确; }
        public boolean isInStationArea() { return inStationArea; }
        public void setInStationArea(boolean inStationArea) { this.inStationArea = inStationArea; }
        public int getStationAreaStartDistance() { return stationAreaStartDistance; }
        public void setStationAreaStartDistance(int stationAreaStartDistance) { this.stationAreaStartDistance = stationAreaStartDistance; }
        public boolean isInCurve() { return inCurve; }
        public void setInCurve(boolean inCurve) { this.inCurve = inCurve; }
        public int getCurveStartDistance() { return curveStartDistance; }
        public void setCurveStartDistance(int curveStartDistance) { this.curveStartDistance = curveStartDistance; }
        public boolean isLowAdhesion() { return lowAdhesion; }
        public void setLowAdhesion(boolean lowAdhesion) { this.lowAdhesion = lowAdhesion; }
        public int getLowAdhesionStartDistance() { return lowAdhesionStartDistance; }
        public void setLowAdhesionStartDistance(int lowAdhesionStartDistance) { this.lowAdhesionStartDistance = lowAdhesionStartDistance; }
        public double get允许速度() { return 允许速度; }
        public void set允许速度(double 允许速度) { this.允许速度 = 允许速度; }
        public double get制动距离() { return 制动距离; }
        public void set制动距离(double 制动距离) { this.制动距离 = 制动距离; }
        public long getLastUpdateTime() { return lastUpdateTime; }
    }

    /**
     * 速度限制类
     */
    public static class SpeedRestriction {
        private final String restrictionId;
        private final double speed; // km/h

        public SpeedRestriction(String restrictionId, double speed) {
            this.restrictionId = restrictionId;
            this.speed = speed;
        }

        // Getters
        public String getRestrictionId() { return restrictionId; }
        public double getSpeed() { return speed; }
    }
}