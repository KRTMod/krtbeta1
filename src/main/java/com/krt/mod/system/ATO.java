package com.krt.mod.system;

import net.minecraft.world.World;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import com.krt.mod.entity.TrainEntity;


import java.util.*;
import java.util.stream.Collectors;

/**
 * 列车自动运行系统（ATO - Automatic Train Operation）
 * 负责实现列车自动起停、速度调节和精准停车
 */
public class ATO {
    private static final Map<World, ATO> INSTANCES = new HashMap<>();
    private final World world;
    private final ATP atp;
    private final ATS ats;
    private final Map<String, ATOTrainData> trainDataMap = new HashMap<>();
    private boolean autoModeEnabled = true; // 自动模式启用状态

    private ATO(World world) {
        this.world = world;
        this.atp = ATP.getInstance(world);
        this.ats = ATS.getInstance(world);
        initialize();
    }

    /**
     * 获取实例（单例模式）
     */
    public static ATO getInstance(World world) {
        return INSTANCES.computeIfAbsent(world, ATO::new);
    }

    /**
     * 初始化ATO系统
     */
    private void initialize() {
        LogSystem.systemLog("ATO系统初始化完成");
    }

    /**
     * 加载驾驶录制并应用到列车
     */
    public void loadDrivingRecord(String trainId, List<ATS.ActionRecord> actions) {
        ATOTrainData data = trainDataMap.computeIfAbsent(trainId, k -> new ATOTrainData(trainId));
        data.setCurrentRecording(actions);
        data.setRecordingStartTime(System.currentTimeMillis());
        data.setRecordingPlaying(true);
        LogSystem.systemLog("ATO系统：列车 " + trainId + " 已加载驾驶录制，共 " + actions.size() + " 个操作");
    }

    /**
     * 更新ATO系统状态
     */
    public void update() {
        if (!autoModeEnabled) {
            return; // 自动模式未启用
        }

        // 更新所有列车的ATO数据
        updateAllTrainATOData();
        
        // 为每个列车计算控制指令
        for (ATOTrainData data : trainDataMap.values()) {
            if (data.isAutoModeEnabled()) {
                calculateControlCommands(data);
            }
        }
    }

    /**
     * 更新所有列车的ATO数据
     */
    private void updateAllTrainATOData() {
        // 获取所有列车实体
        List<TrainEntity> trains = world.getEntitiesByClass(TrainEntity.class, 
                new Box(0, 0, 0, 6000000, 6000000, 6000000), 
                entity -> true);

        for (TrainEntity train : trains) {
            String trainId = train.getTrainId();
            ATOTrainData data = trainDataMap.computeIfAbsent(trainId, k -> new ATOTrainData(trainId));
            
            // 更新基本信息
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
            // TrainEntity没有getCurrentStation()方法，使用空字符串替代
            data.setCurrentStation("");
            data.setNextStation(train.getNextStation() != null ? train.getNextStation() : "");
            data.setLineId(train.getCurrentLine());
            // TrainEntity没有getDoorStatus()方法，使用默认值
            data.setDoorStatus("unknown");
            // 使用isATOEnabled()代替isAutoModeEnabled()
            data.setAutoModeEnabled(train.isATOEnabled());
            
            // 从ATP获取安全信息
            ATP.ATPTrainData atpData = atp.getTrainATPData(trainId);
            if (atpData != null) {
                data.setAllowedSpeed(atpData.get允许速度());
                data.setSignalStatus(atpData.get前方信号机状态());
                data.set前方信号机距离(atpData.get前方信号机距离());
            }
            
            // 从ATS获取调度信息
            ATS.TrainInfo atsTrainInfo = ats.getTrainInfo(trainId);
            if (atsTrainInfo != null) {
                data.setTargetSpeed(getTargetSpeedFromATS(trainId));
                data.setStopRequired(isStopRequired(trainId));
            }
            
            // 检测前方车站
            detect前方车站(data);
            
            // 计算到站距离和预计时间
            calculateStationDistanceAndTime(data);
        }
    }

    /**
     * 从ATS获取目标速度
     */
    private double getTargetSpeedFromATS(String trainId) {
        // TODO: 从ATS获取目标速度
        return 60.0; // 默认目标速度60km/h
    }

    /**
     * 检查是否需要停车
     */
    private boolean isStopRequired(String trainId) {
        // TODO: 检查是否需要停车
        return false;
    }

    /**
     * 检测前方车站
     */
    private void detect前方车站(ATOTrainData data) {
        // 计算到下一站的距离和预计时间
        calculateStationDistanceAndTime(data);
    }

    /**
     * 根据ID查找车站
     */


    /**
     * 计算两点之间的距离
     */
    private double calculateDistance(BlockPos pos1, BlockPos pos2) {
        return Math.sqrt(Math.pow(pos1.getX() - pos2.getX(), 2) + 
                         Math.pow(pos1.getY() - pos2.getY(), 2) + 
                         Math.pow(pos1.getZ() - pos2.getZ(), 2));
    }

    /**
     * 计算到站距离和预计时间
     */
    private void calculateStationDistanceAndTime(ATOTrainData data) {
        BlockPos nextStationPos = data.getNextStationPosition();
        if (nextStationPos != null && !nextStationPos.equals(BlockPos.ZERO)) {
            double distance = calculateDistance(data.getPosition(), nextStationPos);
            data.setDistanceToNextStation(distance);
            
            if (data.getSpeed() > 0) {
                double estimatedTime = (distance / 1000) / (data.getSpeed() / 3600) * 60; // 转换为分钟
                data.setEstimatedArrivalTime(estimatedTime);
            }
        }
    }

    /**
     * 计算控制指令
     */
    private void calculateControlCommands(ATOTrainData data) {
        String trainId = data.getTrainId();
        double currentSpeed = data.getSpeed();
        double targetSpeed = data.getTargetSpeed();
        double allowedSpeed = data.getAllowedSpeed();
        double distanceToNextStation = data.getDistanceToNextStation();
        
        // 检查是否正在播放驾驶录制
        if (data.isRecordingPlaying() && !data.isRecordingFinished()) {
            handleDrivingRecording(data);
            return; // 如果正在播放录制，则跳过常规控制逻辑
        }
        
        // 确保目标速度不超过ATP允许的速度
        targetSpeed = Math.min(targetSpeed, allowedSpeed);
        
        // 判断是否需要停车
        if (data.isStopRequired() || 
            (distanceToNextStation > 0 && distanceToNextStation < 200)) {
            // 接近车站，准备停车
            handleStationApproach(data);
        } else if (data.getSignalStatus() == com.krt.mod.block.ATPSignalBlockEntity.SignalStatus.RED) {
            // 红灯，需要停车
            applyBrake(trainId, 1.0); // 全力制动
        } else if (data.getSignalStatus() == com.krt.mod.block.ATPSignalBlockEntity.SignalStatus.YELLOW) {
            // 黄灯，需要减速
            targetSpeed = Math.min(targetSpeed, allowedSpeed * 0.7);
            adjustSpeed(trainId, currentSpeed, targetSpeed);
        } else {
            // 绿灯或其他情况，正常行驶
            adjustSpeed(trainId, currentSpeed, targetSpeed);
        }
        
        // 检查是否到达车站
        if (distanceToNextStation < 10 && currentSpeed < 5) {
            // 已到达车站，停车并开门
            stopTrain(trainId);
            openDoors(trainId);
            
            // 设置停车时间倒计时
            if (data.getStationStopTime() == 0) {
                data.setStationStopTime(30); // 停车30秒
            }
        }
        
        // 处理车站停车倒计时
        if (data.getStationStopTime() > 0) {
            data.decrementStationStopTime();
            if (data.getStationStopTime() == 0) {
                // 停车时间结束，关门并启动
                closeDoors(trainId);
                startTrain(trainId);
            }
        }
    }

    /**
     * 处理驾驶录制的播放
     */
    private void handleDrivingRecording(ATOTrainData data) {
        String trainId = data.getTrainId();
        long currentTime = System.currentTimeMillis();
        long elapsedTime = currentTime - data.getRecordingStartTime();
        
        List<ATS.ActionRecord> actions = data.getCurrentRecording();
        if (actions == null || actions.isEmpty() || !data.isRecordingPlaying()) {
            return;
        }
        
        int currentIndex = data.getCurrentRecordingIndex();
        
        if (currentIndex >= actions.size()) {
            // 录制已经播放完毕
            data.setRecordingPlaying(false);
            LogSystem.systemLog("ATO系统：列车 " + trainId + " 的驾驶录制播放完毕");
            return;
        }
        
        // 获取第一个操作的时间戳作为基准时间
        long firstActionTime = actions.get(0).getTimestamp();
        
        // 处理所有应该执行的操作
        while (currentIndex < actions.size()) {
            ATS.ActionRecord currentAction = actions.get(currentIndex);
            long actionTimeOffset = currentAction.getTimestamp() - firstActionTime;
            
            // 如果还未到达执行时间，退出循环
            if (elapsedTime < actionTimeOffset) {
                break;
            }
            
            // 执行录制的操作
            String actionType = currentAction.getActionType();
            double value = currentAction.getValue();
            
            // 确保列车存在
            TrainEntity train = findTrainById(trainId);
            if (train == null) {
                LogSystem.error("ATO系统：找不到列车 " + trainId + "，无法执行驾驶录制");
                data.setRecordingPlaying(false);
                break;
            }
            
            switch (actionType) {
                case "power":
                    applyPower(trainId, value);
                    break;
                case "brake":
                    applyBrake(trainId, value);
                    break;
                case "maintain_speed":
                    maintainSpeed(trainId);
                    break;
                case "open_doors":
                    // 确保列车停稳后再开门
                    if (train.getCurrentSpeed() < 1) {
                        openDoors(trainId);
                    }
                    break;
                case "close_doors":
                    closeDoors(trainId);
                    break;
                case "stop":
                    stopTrain(trainId);
                    break;
                case "start":
                    startTrain(trainId);
                    break;
                default:
                    LogSystem.warning("ATO系统：未知的驾驶操作类型 '" + actionType + "'");
                    break;
            }
            
            currentIndex++;
        }
        
        // 更新当前索引
        data.setCurrentRecordingIndex(currentIndex);
        
        // 检查录制是否播放完毕
        if (currentIndex >= actions.size()) {
            data.setRecordingPlaying(false);
            LogSystem.systemLog("ATO系统：列车 " + trainId + " 的驾驶录制播放完毕");
        }
    }
    
    /**
     * 处理接近车站
     */
    private void handleStationApproach(ATOTrainData data) {
        String trainId = data.getTrainId();
        double currentSpeed = data.getSpeed();
        double distanceToStation = data.getDistanceToNextStation();
        
        // 根据距离计算目标速度
        double targetSpeed;
        if (distanceToStation < 50) {
            targetSpeed = 0; // 非常接近车站，准备停车
        } else if (distanceToStation < 100) {
            targetSpeed = 15; // 接近车站，低速
        } else {
            targetSpeed = 30; // 进入车站区域，减速
        }
        
        adjustSpeed(trainId, currentSpeed, targetSpeed);
    }

    /**
     * 调整列车速度
     */
    private void adjustSpeed(String trainId, double currentSpeed, double targetSpeed) {
        double speedDiff = targetSpeed - currentSpeed;
        
        if (speedDiff > 5) {
            // 需要加速
            applyPower(trainId, Math.min(speedDiff / 10, 1.0));
        } else if (speedDiff < -5) {
            // 需要减速
            double brakeForce = Math.min(Math.abs(speedDiff) / 10, 0.8); // 最大常用制动力0.8
            applyBrake(trainId, brakeForce);
        } else {
            // 速度合适，保持动力
            maintainSpeed(trainId);
        }
    }

    /**
     * 施加动力
     */
    private void applyPower(String trainId, double powerLevel) {
        // 找到列车实体
        TrainEntity train = findTrainById(trainId);
        if (train != null) {
            // 获取当前速度
            double currentSpeed = train.getCurrentSpeed();
            // 根据powerLevel计算目标速度增量
            double speedIncrement = powerLevel * 10; // 简化计算，实际应该基于功率和车辆参数
            // 设置新的目标速度
            train.getControlSystem().setTargetSpeed(currentSpeed + speedIncrement);
        }
    }

    /**
     * 施加制动
     */
    private void applyBrake(String trainId, double brakeLevel) {
        TrainEntity train = findTrainById(trainId);
        if (train != null) {
            // 设置目标速度为0来触发制动
            train.getControlSystem().setTargetSpeed(0.0);
        }
    }

    /**
     * 保持速度
     */
    private void maintainSpeed(String trainId) {
        // 找到列车实体
        TrainEntity train = findTrainById(trainId);
        if (train != null) {
            // 获取列车当前速度
            double currentSpeed = train.getCurrentSpeed();
            // 设置目标速度为当前速度，以保持速度
            train.getControlSystem().setTargetSpeed(currentSpeed);
        }
    }

    /**
     * 停止列车
     */
    private void stopTrain(String trainId) {
        TrainControlSystem trainControlSystem = TrainControlSystem.getInstance(world);
        trainControlSystem.applyServiceBrake(trainId);
    }

    /**
     * 启动列车
     */
    private void startTrain(String trainId) {
        TrainControlSystem trainControlSystem = TrainControlSystem.getInstance(world);
        trainControlSystem.startTrain(trainId);
    }

    /**
     * 打开车门
     */
    private void openDoors(String trainId) {
        TrainControlSystem trainControlSystem = TrainControlSystem.getInstance(world);
        trainControlSystem.openDoors(trainId);
    }

    /**
     * 关闭车门
     */
    private void closeDoors(String trainId) {
        TrainControlSystem trainControlSystem = TrainControlSystem.getInstance(world);
        trainControlSystem.closeDoors(trainId);
    }

    /**
     * 设置列车自动模式
     */
    public void setTrainAutoMode(String trainId, boolean enabled) {
        ATOTrainData data = trainDataMap.get(trainId);
        if (data != null) {
            data.setAutoModeEnabled(enabled);
            
            // 同时更新列车实体的自动模式状态
            TrainEntity train = findTrainById(trainId);
            if (train != null) {
                train.setATOEnabled(enabled);
            }
            
            String status = enabled ? "启用" : "禁用";
            LogSystem.systemLog("ATO系统：列车 " + trainId + " 自动模式" + status);
        }
    }

    /**
     * 根据ID查找列车
     */
    private TrainEntity findTrainById(String trainId) {
        List<TrainEntity> trains = world.getEntitiesByClass(TrainEntity.class, 
                new Box(0, 0, 0, 6000000, 6000000, 6000000), 
                entity -> entity.getTrainId().equals(trainId));
        return trains.isEmpty() ? null : trains.get(0);
    }

    /**
     * 启用/禁用全局自动模式
     */
    public void setGlobalAutoMode(boolean enabled) {
        this.autoModeEnabled = enabled;
        String status = enabled ? "启用" : "禁用";
        LogSystem.systemLog("ATO系统：全局自动模式" + status);
    }

    /**
     * 获取全局自动模式状态
     */
    public boolean getAutoModeEnabled() {
        return this.autoModeEnabled;
    }

    /**
     * 获取ATO列车数据
     */
    public ATOTrainData getTrainATOData(String trainId) {
        return trainDataMap.get(trainId);
    }

    /**
     * ATO列车数据类
     */
    public static class ATOTrainData {
        private final String trainId;
        private BlockPos position = new BlockPos(0, 0, 0);
        private double speed = 0;
        private String direction = "";
        private String currentStation = "";
        private String nextStation = "";
        private BlockPos nextStationPosition = new BlockPos(0, 0, 0);
        private String lineId = "";
        private String doorStatus = "closed";
        private boolean autoModeEnabled = false;
        private double allowedSpeed = 80.0;
        private double targetSpeed = 60.0;
        private com.krt.mod.block.ATPSignalBlockEntity.SignalStatus signalStatus = com.krt.mod.block.ATPSignalBlockEntity.SignalStatus.RED;
        private int 前方信号机距离 = Integer.MAX_VALUE;
        private boolean stopRequired = false;
        private double distanceToNextStation = Double.MAX_VALUE;
        private double estimatedArrivalTime = Double.MAX_VALUE;
        private int stationStopTime = 0;
        private long lastUpdateTime = System.currentTimeMillis();
        // 驾驶录制相关字段
        private List<ATS.ActionRecord> currentRecording = new ArrayList<>();
        private boolean isRecordingPlaying = false;
        private int currentRecordingIndex = 0;
        private long recordingStartTime = 0;

        public ATOTrainData(String trainId) {
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
        public String getCurrentStation() { return currentStation; }
        public void setCurrentStation(String currentStation) { this.currentStation = currentStation; }
        public String getNextStation() { return nextStation; }
        public void setNextStation(String nextStation) { this.nextStation = nextStation; }
        public BlockPos getNextStationPosition() { return nextStationPosition; }
        public void setNextStationPosition(BlockPos nextStationPosition) { this.nextStationPosition = nextStationPosition; }
        public String getLineId() { return lineId; }
        public void setLineId(String lineId) { this.lineId = lineId; }
        public String getDoorStatus() { return doorStatus; }
        public void setDoorStatus(String doorStatus) { this.doorStatus = doorStatus; }
        public boolean isAutoModeEnabled() { return autoModeEnabled; }
        public void setAutoModeEnabled(boolean autoModeEnabled) { this.autoModeEnabled = autoModeEnabled; }
        public double getAllowedSpeed() { return allowedSpeed; }
        public void setAllowedSpeed(double allowedSpeed) { this.allowedSpeed = allowedSpeed; }
        public double getTargetSpeed() { return targetSpeed; }
        public void setTargetSpeed(double targetSpeed) { this.targetSpeed = targetSpeed; }
        public com.krt.mod.block.ATPSignalBlockEntity.SignalStatus getSignalStatus() { return signalStatus; }
        public void setSignalStatus(com.krt.mod.block.ATPSignalBlockEntity.SignalStatus signalStatus) { this.signalStatus = signalStatus; }
        public int get前方信号机距离() { return 前方信号机距离; }
        public void set前方信号机距离(int 前方信号机距离) { this.前方信号机距离 = 前方信号机距离; }
        public boolean isStopRequired() { return stopRequired; }
        public void setStopRequired(boolean stopRequired) { this.stopRequired = stopRequired; }
        public double getDistanceToNextStation() { return distanceToNextStation; }
        public void setDistanceToNextStation(double distanceToNextStation) { this.distanceToNextStation = distanceToNextStation; }
        public double getEstimatedArrivalTime() { return estimatedArrivalTime; }
        public void setEstimatedArrivalTime(double estimatedArrivalTime) { this.estimatedArrivalTime = estimatedArrivalTime; }
        public int getStationStopTime() { return stationStopTime; }
        public void setStationStopTime(int stationStopTime) { this.stationStopTime = stationStopTime; }
        public void decrementStationStopTime() { this.stationStopTime--; }
        public long getLastUpdateTime() { return lastUpdateTime; }
        public void setLastUpdateTime(long lastUpdateTime) { this.lastUpdateTime = lastUpdateTime; }
        
        // 驾驶录制相关方法
        public List<ATS.ActionRecord> getCurrentRecording() { return currentRecording; }
        public void setCurrentRecording(List<ATS.ActionRecord> currentRecording) {
            this.currentRecording = currentRecording;
            this.currentRecordingIndex = 0;
        }
        public boolean isRecordingPlaying() { return isRecordingPlaying; }
        public void setRecordingPlaying(boolean recordingPlaying) { isRecordingPlaying = recordingPlaying; }
        public int getCurrentRecordingIndex() { return currentRecordingIndex; }
        public void setCurrentRecordingIndex(int currentRecordingIndex) { this.currentRecordingIndex = currentRecordingIndex; }
        public long getRecordingStartTime() { return recordingStartTime; }
        public void setRecordingStartTime(long recordingStartTime) { this.recordingStartTime = recordingStartTime; }
        
        /**
         * 增加录制索引
         */
        public void incrementRecordingIndex() {
            this.currentRecordingIndex++;
        }
        
        /**
         * 检查录制是否已播放完毕
         */
        public boolean isRecordingFinished() {
            return currentRecordingIndex >= currentRecording.size();
        }
        
        /**
         * 获取当前应该执行的操作记录
         */
        public ATS.ActionRecord getCurrentActionRecord() {
            if (isRecordingPlaying && currentRecordingIndex < currentRecording.size()) {
                return currentRecording.get(currentRecordingIndex);
            }
            return null;
        }
    }
}