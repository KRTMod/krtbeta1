package com.krt.mod.system;

import net.minecraft.world.World;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import com.krt.mod.entity.TrainEntity;
import com.krt.mod.block.ATPSignalBlockEntity;
import com.krt.mod.system.CBTCSystem.AlertType;

import java.util.HashMap;
import java.util.Map;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 列车自动监控系统（ATS - Automatic Train Supervision）
 * 负责实时追踪列车位置，生成运行计划，调整列车间隔，管理设备状态
 */
public class ATS {
    private static final Map<World, ATS> INSTANCES = new HashMap<>();
    private final World world;
    private final TrackSectionManager trackSectionManager;
    private final Map<String, TrainSchedule> schedules = new HashMap<>();
    private final Map<String, TrainInfo> trainInfos = new HashMap<>();
    private final Map<String, SignalInfo> signalInfos = new HashMap<>();
    private final Map<String, SwitchInfo> switchInfos = new HashMap<>();
    private final Map<String, DrivingRecord> drivingRecords = new ConcurrentHashMap<>();
    private final Map<String, DrivingAction> currentRecordings = new ConcurrentHashMap<>();
    private boolean centralMode = true; // 中央控制模式

    private ATS(World world) {
        this.world = world;
        this.trackSectionManager = TrackSectionManager.getInstance(world);
        initialize();
    }

    /**
     * 获取实例（单例模式）
     */
    public static ATS getInstance(World world) {
        return INSTANCES.computeIfAbsent(world, ATS::new);
    }

    /**
     * 初始化ATS系统
     */
    private void initialize() {
        // 启动定期更新任务
        startPeriodicUpdates();
        LogSystem.systemLog("ATS系统初始化完成");
    }

    /**
     * 开始驾驶录制
     */
    public void startRecording(String trainId) {
        if (currentRecordings.containsKey(trainId)) {
            LogSystem.atsWarning("列车 " + trainId + " 的驾驶录制已在进行中");
            return;
        }
        
        DrivingAction recording = new DrivingAction(trainId);
        currentRecordings.put(trainId, recording);
        LogSystem.systemLog("ATS系统：开始录制列车 " + trainId + " 的驾驶操作");
    }

    /**
     * 停止驾驶录制并保存
     */
    public void stopRecording(String trainId, String recordingName) {
        DrivingAction recording = currentRecordings.remove(trainId);
        if (recording == null) {
            LogSystem.atsWarning("未找到列车 " + trainId + " 的驾驶录制");
            return;
        }
        
        DrivingRecord record = new DrivingRecord(recordingName, recording.getActions());
        drivingRecords.put(recordingName, record);
        LogSystem.systemLog("ATS系统：已保存列车 " + trainId + " 的驾驶录制 '" + recordingName + "'");
    }

    /**
     * 应用录制的驾驶操作到列车
     */
    public void applyRecording(String trainId, String recordingName) {
        DrivingRecord record = drivingRecords.get(recordingName);
        if (record == null) {
            LogSystem.atsError("未找到名为 '" + recordingName + "' 的驾驶录制");
            return;
        }
        
        // 将录制的操作应用到ATO系统，实现自动驾驶复刻
        ATO ato = ATO.getInstance(world);
        ato.setTrainAutoMode(trainId, true);
        ato.loadDrivingRecord(trainId, record.getActions());
        LogSystem.systemLog("ATS系统：已将驾驶录制 '" + recordingName + "' 应用到列车 " + trainId);
    }

    /**
     * 记录单次驾驶操作
     */
    public void recordDrivingAction(String trainId, String actionType, double value) {
        DrivingAction recording = currentRecordings.get(trainId);
        if (recording != null) {
            recording.addAction(new ActionRecord(System.currentTimeMillis(), actionType, value));
        }
    }

    /**
     * 获取所有已保存的驾驶录制名称
     */
    public List<String> getAvailableRecordings() {
        return new ArrayList<>(drivingRecords.keySet());
    }

    /**
     * 驾驶录制类
     */
    public static class DrivingRecord {
        private final String name;
        private final List<ActionRecord> actions;
        
        public DrivingRecord(String name, List<ActionRecord> actions) {
            this.name = name;
            this.actions = new ArrayList<>(actions);
        }
        
        public String getName() { return name; }
        public List<ActionRecord> getActions() { return actions; }
    }

    /**
     * 当前录制会话类
     */
    private static class DrivingAction {
        private final String trainId;
        private final List<ActionRecord> actions;
        private final long startTime;
        
        public DrivingAction(String trainId) {
            this.trainId = trainId;
            this.actions = new ArrayList<>();
            this.startTime = System.currentTimeMillis();
        }
        
        public void addAction(ActionRecord action) {
            actions.add(action);
        }
        
        public List<ActionRecord> getActions() { return actions; }
        public String getTrainId() { return trainId; }
        public long getStartTime() { return startTime; }
    }

    /**
     * 单个操作记录类
     */
    public static class ActionRecord {
        private final long timestamp;
        private final String actionType;
        private final double value;
        
        public ActionRecord(long timestamp, String actionType, double value) {
            this.timestamp = timestamp;
            this.actionType = actionType;
            this.value = value;
        }
        
        public long getTimestamp() { return timestamp; }
        public String getActionType() { return actionType; }
        public double getValue() { return value; }
    }

    /**
     * 启动定期更新任务
     */
    private void startPeriodicUpdates() {
        // 在Minecraft的游戏循环中更新ATS系统
        // 这里使用虚拟实现，实际需要集成到游戏的tick系统
    }

    /**
     * 更新ATS系统状态
     */
    public void update() {
        if (!centralMode) {
            return; // 非中央模式下不进行自动控制
        }

        // 1. 更新列车位置信息
        updateTrainPositions();

        // 2. 检查信号机状态
        updateSignalStatuses();

        // 3. 管理道岔状态
        updateSwitchPositions();

        // 4. 调整列车间隔
        adjustTrainIntervals();

        // 5. 生成进路控制命令
        generateRouteCommands();
        
        // 6. 定期更新列车到站信息（每2秒更新一次）
        if (System.currentTimeMillis() % 2000 < 50) {
            updateTrainArrivalInformation();
        }
    }
    
    /**
     * 更新列车到站信息
     */
    private void updateTrainArrivalInformation() {
        // 使用TrainDisplaySystem自动更新所有列车到站信息
        TrainDisplaySystem.getInstance().autoUpdateAllTrainArrivalInfo(world);
    }
    
    /**
     * 获取所有时刻表
     */
    public Collection<TrainSchedule> getTrainSchedules() {
        return schedules.values();
    }

    /**
     * 更新列车位置信息
     */
    private void updateTrainPositions() {
        // 获取所有列车实体
        List<TrainEntity> trains = world.getEntitiesByClass(TrainEntity.class, 
                new Box(0, 0, 0, 6000000, 6000000, 6000000), 
                entity -> true);

        for (TrainEntity train : trains) {
            String trainId = train.getTrainId();
            BlockPos currentPos = train.getBlockPos();
            TrackSectionManager.TrackSection section = trackSectionManager.getSectionAt(currentPos);
            
            // 更新列车信息
            TrainInfo info = trainInfos.computeIfAbsent(trainId, k -> new TrainInfo(trainId));
            info.updatePosition(currentPos, section != null ? section.getSectionId() : "");
            info.setSpeed(train.getCurrentSpeed());
            // 从旋转向量获取方向
            Vec3d rotation = train.getRotationVector();
            String direction = "北";
            if (Math.abs(rotation.x) > Math.abs(rotation.z)) {
                direction = rotation.x > 0 ? "东" : "西";
            } else {
                direction = rotation.z > 0 ? "南" : "北";
            }
            info.setDirection(direction);
            // TrainEntity没有getCurrentStation()方法，使用空字符串替代
            info.setCurrentStation("");
            info.setNextStation(train.getNextStation() != null ? train.getNextStation() : "");
            info.setLineId(train.getCurrentLine() != null ? train.getCurrentLine() : "");
            
            // 更新轨道区段占用状态
            if (section != null) {
                trackSectionManager.updateSectionOccupancy(section.getSectionId(), true, trainId);
                // 同时标记相邻区段为预占用状态
                markAdjacentSectionsAsPreoccupied(section.getSectionId(), trainId);
            }
        }
    }

    /**
     * 标记相邻区段为预占用状态
     */
    private void markAdjacentSectionsAsPreoccupied(String sectionId, String trainId) {
        // TODO: 实现相邻区段预占用标记逻辑
    }

    /**
     * 更新信号机状态
     */
    private void updateSignalStatuses() {
        // 获取所有信号机方块实体
        List<ATPSignalBlockEntity> signals = new ArrayList<>();
        for (BlockPos pos : BlockPos.iterate(0, 0, 0, 6000000, 6000000, 6000000)) {
            if (world.getBlockEntity(pos) instanceof ATPSignalBlockEntity) {
                signals.add((ATPSignalBlockEntity) world.getBlockEntity(pos));
            }
        }

        for (ATPSignalBlockEntity signal : signals) {
            if (signal != null && signal.getPos() != null) {
                BlockPos pos = signal.getPos();
                String signalId = "signal_" + pos.getX() + "_" + pos.getY() + "_" + pos.getZ();
                
                // 更新信号机信息
                SignalInfo info = signalInfos.computeIfAbsent(signalId, k -> new SignalInfo(signalId));
                info.setPosition(pos);
                info.setStatus(signal.getSignalStatus());
                // ATPSignalBlockEntity没有getControlledSectionId()方法，使用空字符串替代
                info.setControlledSection("");
                
                // 基于区段占用状态更新信号机显示
                updateSignalDisplay(signal);
            }
        }
    }

    /**
     * 更新信号机显示
     */
    private void updateSignalDisplay(ATPSignalBlockEntity signal) {
        // ATPSignalBlockEntity没有getControlledSectionId()方法
        // 简化实现，不基于区段占用状态更新信号机显示
        // 如果需要完整功能，需要在ATPSignalBlockEntity中添加相关方法和字段
        signal.setSignalStatus(ATPSignalBlockEntity.SignalStatus.GREEN);
    }

    /**
     * 检查区段是否有活跃的进路请求
     */
    private boolean hasActiveRouteRequestForSection(String sectionId) {
        // TODO: 实现进路请求检查逻辑
        return false;
    }

    /**
     * 更新道岔状态
     */
    private void updateSwitchPositions() {
        // TODO: 实现道岔状态管理逻辑
    }

    /**
     * 调整列车间隔
     */
    private void adjustTrainIntervals() {
        // 按线路分组列车
        Map<String, List<TrainInfo>> trainsByLine = new HashMap<>();
        for (TrainInfo info : trainInfos.values()) {
            if (info.getLineId() != null && !info.getLineId().isEmpty()) {
                trainsByLine.computeIfAbsent(info.getLineId(), k -> new ArrayList<>()).add(info);
            }
        }

        for (Map.Entry<String, List<TrainInfo>> entry : trainsByLine.entrySet()) {
            String lineId = entry.getKey();
            List<TrainInfo> lineTrains = entry.getValue();
            
            // 按位置排序列车
            lineTrains.sort(Comparator.comparing(TrainInfo::getPosition));
            
            // 检查并调整列车间隔
            for (int i = 1; i < lineTrains.size(); i++) {
                TrainInfo frontTrain = lineTrains.get(i - 1);
                TrainInfo rearTrain = lineTrains.get(i);
                
                double distance = calculateDistance(frontTrain.getPosition(), rearTrain.getPosition());
                double safeDistance = calculateSafeDistance(frontTrain.getSpeed(), rearTrain.getSpeed());
                
                if (distance < safeDistance) {
                    // 发送减速命令给后车
                    sendSpeedCommand(rearTrain.getTrainId(), frontTrain.getSpeed() * 0.8);
                    
                    // 触发安全距离警报
                    String alertMessage = "与前方列车距离过近！当前距离: " + String.format("%.1f", distance) + "m, 安全距离: " + String.format("%.1f", safeDistance) + "m";
                    Map<String, Object> additionalInfo = new HashMap<>();
                    additionalInfo.put("frontTrainId", frontTrain.getTrainId());
                    additionalInfo.put("distance", distance);
                    additionalInfo.put("safeDistance", safeDistance);
                    
                    // 获取CBTC系统实例并触发警报
                    CBTCSystem.getInstance(world).triggerAlert(rearTrain.getTrainId(), AlertType.SAFE_DISTANCE_VIOLATION, alertMessage, additionalInfo);
                }
            }
        }
    }

    /**
     * 计算两点之间的距离
     */
    private double calculateDistance(BlockPos pos1, BlockPos pos2) {
        return Math.sqrt(Math.pow(pos1.getX() - pos2.getX(), 2) + 
                         Math.pow(pos1.getY() - pos2.getY(), 2) + 
                         Math.pow(pos1.getZ() - pos2.getZ(), 2));
    }

    /**
     * 计算安全距离
     */
    private double calculateSafeDistance(double frontSpeed, double rearSpeed) {
        // 基础安全距离 + 速度相关的安全距离
        double baseDistance = 50; // 基础安全距离（方块）
        double speedFactor = Math.max(0, rearSpeed - frontSpeed) * 5; // 速度差带来的额外安全距离
        
        return baseDistance + speedFactor;
    }

    /**
     * 发送速度命令给列车
     */
    private void sendSpeedCommand(String trainId, double targetSpeed) {
        // 找到对应的列车实体
        TrainEntity train = findTrainById(trainId);
        if (train != null) {
            train.getControlSystem().setTargetSpeed(targetSpeed);
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
     * 生成进路控制命令
     */
    private void generateRouteCommands() {
        // TODO: 实现进路生成逻辑
    }

    /**
     * 创建新的列车时刻表
     */
    public void createSchedule(String scheduleId, String lineId, List<ScheduleItem> items) {
        TrainSchedule schedule = new TrainSchedule(scheduleId, lineId, items);
        schedules.put(scheduleId, schedule);
        LogSystem.systemLog("创建列车时刻表: " + scheduleId + " (线路: " + lineId + ")");
    }

    /**
     * 设置控制模式（中央控制或车站控制）
     */
    public void setControlMode(boolean centralMode) {
        this.centralMode = centralMode;
        String mode = centralMode ? "中央控制" : "车站控制";
        LogSystem.systemLog("ATS系统切换至" + mode + "模式");
    }

    /**
     * 获取控制模式
     */
    public boolean isCentralMode() {
        return centralMode;
    }

    /**
     * 获取列车信息
     */
    public TrainInfo getTrainInfo(String trainId) {
        return trainInfos.get(trainId);
    }

    /**
     * 获取所有列车信息
     */
    public Collection<TrainInfo> getAllTrainInfos() {
        return trainInfos.values();
    }

    /**
     * 列车信息类
     */
    public static class TrainInfo {
        private final String trainId;
        private BlockPos position = new BlockPos(0, 0, 0);
        private String currentSectionId = "";
        private double speed = 0;
        private String direction = "";
        private String currentStation = "";
        private String nextStation = "";
        private String lineId = "";
        private long lastUpdateTime = System.currentTimeMillis();

        public TrainInfo(String trainId) {
            this.trainId = trainId;
        }

        public void updatePosition(BlockPos position, String sectionId) {
            this.position = position;
            this.currentSectionId = sectionId;
            this.lastUpdateTime = System.currentTimeMillis();
        }

        // Getters and setters
        public String getTrainId() { return trainId; }
        public BlockPos getPosition() { return position; }
        public String getCurrentSectionId() { return currentSectionId; }
        public double getSpeed() { return speed; }
        public void setSpeed(double speed) { this.speed = speed; }
        public String getDirection() { return direction; }
        public void setDirection(String direction) { this.direction = direction; }
        public String getCurrentStation() { return currentStation; }
        public void setCurrentStation(String currentStation) { this.currentStation = currentStation; }
        public String getNextStation() { return nextStation; }
        public void setNextStation(String nextStation) { this.nextStation = nextStation; }
        public String getLineId() { return lineId; }
        public void setLineId(String lineId) { this.lineId = lineId; }
        public long getLastUpdateTime() { return lastUpdateTime; }
    }

    /**
     * 信号机信息类
     */
    public static class SignalInfo {
        private final String signalId;
        private BlockPos position = new BlockPos(0, 0, 0);
        private ATPSignalBlockEntity.SignalStatus status = ATPSignalBlockEntity.SignalStatus.RED;
        private String controlledSection = "";

        public SignalInfo(String signalId) {
            this.signalId = signalId;
        }

        // Getters and setters
        public String getSignalId() { return signalId; }
        public BlockPos getPosition() { return position; }
        public void setPosition(BlockPos position) { this.position = position; }
        public ATPSignalBlockEntity.SignalStatus getStatus() { return status; }
        public void setStatus(ATPSignalBlockEntity.SignalStatus status) { this.status = status; }
        public String getControlledSection() { return controlledSection; }
        public void setControlledSection(String controlledSection) { this.controlledSection = controlledSection; }
    }

    /**
     * 道岔信息类
     */
    public static class SwitchInfo {
        private final String switchId;
        private BlockPos position = new BlockPos(0, 0, 0);
        private boolean isMainRoute = true;
        private boolean isLocked = false;

        public SwitchInfo(String switchId) {
            this.switchId = switchId;
        }

        // Getters and setters
        public String getSwitchId() { return switchId; }
        public BlockPos getPosition() { return position; }
        public void setPosition(BlockPos position) { this.position = position; }
        public boolean isMainRoute() { return isMainRoute; }
        public void setMainRoute(boolean mainRoute) { isMainRoute = mainRoute; }
        public boolean isLocked() { return isLocked; }
        public void setLocked(boolean locked) { isLocked = locked; }
    }

    /**
     * 列车时刻表类
     */
    public static class TrainSchedule {
        private final String scheduleId;
        private final String lineId;
        private final List<ScheduleItem> items;

        public TrainSchedule(String scheduleId, String lineId, List<ScheduleItem> items) {
            this.scheduleId = scheduleId;
            this.lineId = lineId;
            this.items = items;
        }

        // Getters
        public String getScheduleId() { return scheduleId; }
        public String getLineId() { return lineId; }
        public List<ScheduleItem> getItems() { return items; }
    }

    /**
     * 时刻表项类
     */
    public static class ScheduleItem {
        private final String stationId;
        private final long arrivalTime;
        private final long departureTime;

        public ScheduleItem(String stationId, long arrivalTime, long departureTime) {
            this.stationId = stationId;
            this.arrivalTime = arrivalTime;
            this.departureTime = departureTime;
        }

        // Getters
        public String getStationId() { return stationId; }
        public long getArrivalTime() { return arrivalTime; }
        public long getDepartureTime() { return departureTime; }
    }
}