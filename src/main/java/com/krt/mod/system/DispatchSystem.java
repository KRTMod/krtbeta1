package com.krt.mod.system;

import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import com.krt.mod.KRTMod;
import com.krt.mod.block.SignalBlock;
import com.krt.mod.block.PlatformDoorBlock;
import com.krt.mod.entity.TrainEntity;
import java.util.*;
import java.util.stream.Collectors;
import java.util.concurrent.ConcurrentHashMap;
import com.krt.mod.system.PerformanceMonitor;
import com.krt.mod.system.LogSystem.LogLevel;

public class DispatchSystem {
    private static final Map<World, DispatchSystem> INSTANCES = new HashMap<>();
    private final World world;
    private final List<TrainEntity> trains = new ArrayList<>();
    private final List<BlockPos> signalMachines = new ArrayList<>();
    private final List<BlockPos> platformDoors = new ArrayList<>();
    private final LineControlSystem lineControlSystem;
    private DispatchMode dispatchMode = DispatchMode.SYSTEM;
    private final AISuggestionSystem aiSystem = new AISuggestionSystem();
    
    // 运行图管理系统
    private final TrainScheduleManager scheduleManager;
    // 智能调度算法系统
    private final IntelligentDispatchAlgorithm dispatchAlgorithm;
    // 实时客流监控系统
    private final PassengerFlowMonitor passengerFlowMonitor;
    // 列车位置实时跟踪
    private final Map<String, TrainPositionInfo> trainPositionMap = new ConcurrentHashMap<>();
    // 调度事件队列
    private final Queue<DispatchEvent> dispatchEventQueue = new LinkedList<>();
    // 运行调整记录
    private final Map<Long, DispatchAdjustment> adjustmentHistory = new LinkedHashMap<>();

    private DispatchSystem(World world) {
        this.world = world;
        this.lineControlSystem = LineControlSystem.getInstance(world);
        this.scheduleManager = new TrainScheduleManager();
        this.dispatchAlgorithm = new IntelligentDispatchAlgorithm();
        this.passengerFlowMonitor = new PassengerFlowMonitor();
    }

    public static DispatchSystem getInstance(World world) {
        return INSTANCES.computeIfAbsent(world, DispatchSystem::new);
    }

    // 注册列车到调度系统
    public void registerTrain(TrainEntity train) {
        if (!trains.contains(train)) {
            trains.add(train);
            KRTMod.LOGGER.info("列车已注册到调度系统: {}", train.getTrainId());
        }
    }

    // 注册信号机到调度系统
    public void registerSignalMachine(BlockPos pos) {
        if (!signalMachines.contains(pos)) {
            signalMachines.add(pos);
        }
    }

    // 注册屏蔽门到调度系统
    public void registerPlatformDoor(BlockPos pos) {
        if (!platformDoors.contains(pos)) {
            platformDoors.add(pos);
        }
    }

    // 每刻更新调度系统
    public void tick() {
        // 优化更新频率
        if (world.getTime() % 2 != 0) {
            return;
        }
        
        PerformanceMonitor.getInstance().startSystemExecution("DispatchSystem");
        
        // 更新所有列车状态
        updateTrains();
        
        // 更新列车位置信息
        updateTrainPositions();
        
        // 更新客流数据
        passengerFlowMonitor.update();
        
        // 更新运行图状态
        scheduleManager.updateCurrentSchedule();
        
        // 更新所有信号机状态
        updateSignals();
        
        // 更新所有屏蔽门状态
        updatePlatformDoors();
        
        // 如果是系统控制模式，执行调度
        if (dispatchMode == DispatchMode.SYSTEM) {
            // 处理调度事件队列
            processDispatchEvents();
            
            // 高峰期调度（优先级高于普通AI调度）
            if (isPeakHour()) {
                peakHourDispatch();
            } else {
                // 非高峰期使用普通AI调度
                runAIDispatch();
            }
            
            // 执行智能调度算法
            dispatchAlgorithm.executeScheduling();
            
            // 故障情况下的线路调整
            handleEmergencySituations();
            
            // 运行图动态调整
            adjustScheduleIfNeeded();
        }
        
        // 记录运行日志
        logSystemStatus();
        
        PerformanceMonitor.getInstance().endSystemExecution("DispatchSystem");
    }
    
    // 检查是否为高峰期
    private boolean isPeakHour() {
        // 简化实现：判断是否为游戏中的高峰期时段
        long timeOfDay = world.getTimeOfDay() % 24000;
        // 假设早上7:00-9:00和晚上17:00-19:00为高峰期
        return (timeOfDay >= 7000 && timeOfDay <= 9000) || (timeOfDay >= 17000 && timeOfDay <= 19000);
    }
    
    // 高峰期调度算法
    private void peakHourDispatch() {
        // 1. 增加高峰期列车频率
        dispatchAdditionalTrains();
        
        // 2. 优化停站时间
        optimizePeakHourStopTimes();
        
        // 3. 检查列车密度并优化
        checkTrainDensity();
        
        // 4. 线路容量分析
        analyzeLineCapacity();
        
        // 5. 高峰期专用运行图
        scheduleManager.activatePeakHourSchedule();
        
        // 6. 监控关键拥堵点
        monitorCriticalCongestionPoints();
    }
    
    // 监控关键拥堵点
    private void monitorCriticalCongestionPoints() {
        for (String lineId : lineControlSystem.getAllLines().keySet()) {
            List<String> criticalPoints = dispatchAlgorithm.getCriticalCongestionPoints(lineId);
            for (String stationName : criticalPoints) {
                int passengerCount = passengerFlowMonitor.getStationPassengerCount(stationName);
                if (passengerCount > 200) { // 假设200人是拥挤阈值
                    KRTMod.LOGGER.warning("高峰期注意：车站 {} 人流量过大 ({})", stationName, passengerCount);
                    // 可以增加列车在该站的停站时间，确保乘客上下车
                    adjustStopTimeForCrowdedStation(lineId, stationName, passengerCount);
                }
            }
        }
    }
    
    // 为拥挤车站调整停站时间
    private void adjustStopTimeForCrowdedStation(String lineId, String stationName, int passengerCount) {
        // 根据拥挤程度动态调整停站时间
        double multiplier = 1.0 + (Math.min(2.0, passengerCount / 100.0)); // 最多增加200%的停站时间
        
        for (TrainEntity train : getTrainsOnLine(lineId)) {
            train.adjustStationStopTime(stationName, multiplier);
        }
        
        LogSystem.dispatchLog("调整车站 " + stationName + " 的停站时间，乘数: " + multiplier);
    }
    
    // 增加高峰期额外列车
    private void dispatchAdditionalTrains() {
        // 获取所有线路
        for (String lineId : lineControlSystem.getAllLines().keySet()) {
            List<TrainEntity> currentTrains = getTrainsOnLine(lineId);
            int optimalCount = calculateOptimalTrainCount(lineId, true);
            
            if (currentTrains.size() < optimalCount) {
                KRTMod.LOGGER.info("高峰期调度: 线路 {} 需要增加 {} 列列车", lineId, optimalCount - currentTrains.size());
            }
        }
    }
    
    // 优化高峰期停站时间
    private void optimizePeakHourStopTimes() {
        // 高峰期停站时间缩短处理
        for (TrainEntity train : trains) {
            // 设置高峰期停站时间（缩短30%）
            train.setPeakHourStopTime(true);
        }
    }
    
    // 分析线路容量
    private void analyzeLineCapacity() {
        for (String lineId : lineControlSystem.getAllLines().keySet()) {
            List<TrainEntity> trainsOnLine = getTrainsOnLine(lineId);
            double loadFactor = calculateLineLoadFactor(lineId, trainsOnLine.size());
            
            if (loadFactor > 0.9) {
                KRTMod.LOGGER.warning("高峰期线路 {} 负载过高: {}%", lineId, (int)(loadFactor * 100));
                implementLineCapacityControl(lineId);
            } else if (loadFactor > 0.75) {
                KRTMod.LOGGER.info("高峰期线路 {} 负载较高: {}%", lineId, (int)(loadFactor * 100));
                // 可以考虑增开临时列车
                if (isPeakHour() && needsAdditionalTrain(lineId)) {
                    dispatchAlgorithm.dispatchEmergencyTrain(lineId);
                }
            }
        }
    }
    
    // 判断是否需要额外列车
    private boolean needsAdditionalTrain(String lineId) {
        // 检查客流量增长趋势
        double flowTrend = passengerFlowMonitor.getPassengerFlowTrend(lineId);
        // 如果客流量仍在增长且列车密度未达上限，需要额外列车
        return flowTrend > 0.05 && calculateTrainDensity(lineId, getTrainsOnLine(lineId).size()) < 0.9;
    }
    
    // 故障情况下的线路调整
    private void handleEmergencySituations() {
        for (TrainEntity train : trains) {
            // 检查列车是否出现严重故障
            if (train.getHealth() < 30) {
                handleFaultyTrain(train);
            }
        }
    }
    
    // 处理故障列车
    private void handleFaultyTrain(TrainEntity train) {
        if (train.getDriver() != null) {
            train.getDriver().sendMessage(Text.literal("⚠ 列车故障严重，请尽快前往最近的车站进行疏散！"), false);
        }
        
        String nearestStation = findNearestStation(train);
        if (!nearestStation.isEmpty()) {
            KRTMod.LOGGER.warning("紧急通知: 车站 {} 即将有故障列车到达，请准备疏散！", nearestStation);
            adjustFollowingTrains(train);
        }
    }

    // 更新列车状态
    private void updateTrains() {
        for (TrainEntity train : trains) {
            // 检查列车是否在线路上
            checkTrainOnLine(train);
            // 检查列车与信号机的关系
            checkTrainSignalRelations(train);
            // 检查列车是否接近车站
            checkTrainApproachingStation(train);
        }
    }

    // 检查列车是否在线路上
    private void checkTrainOnLine(TrainEntity train) {
        if (train.getCurrentLine() == null) {
            // 尝试为列车分配线路
            String nearestLine = findNearestLine(train.getPos());
            if (nearestLine != null) {
                train.setCurrentLine(nearestLine);
                if (train.getDriver() != null) {
                    train.getDriver().sendMessage(Text.literal("已自动分配至线路: " + lineControlSystem.getLineInfo(nearestLine).getLineName()), false);
                }
            }
        }
    }

    // 检查列车与信号机的关系
    private void checkTrainSignalRelations(TrainEntity train) {
        // 查找附近的信号机
        BlockPos trainPos = train.getBlockPos();
        for (BlockPos signalPos : signalMachines) {
            double distance = trainPos.getSquaredDistance(signalPos);
            SignalBlock.SignalState currentState = world.getBlockState(signalPos).get(SignalBlock.SIGNAL_STATE);
            
            // 如果列车在信号机100米以内且信号机是红色，触发紧急制动
            if (Math.sqrt(distance) < 100 && currentState == SignalBlock.SignalState.RED) {
                if (!train.isEmergencyBraking()) {
                    train.applyEmergencyBrake();
                    if (train.getDriver() != null) {
                        train.getDriver().sendMessage(Text.literal("紧急制动: 前方信号机显示红色！"), false);
                    }
                }
            } 
            // 如果列车在信号机200米以内且信号机是黄色，提示减速
            else if (Math.sqrt(distance) < 200 && currentState == SignalBlock.SignalState.YELLOW) {
                if (train.getControlSystem().getControlMode() == TrainControlSystem.TrainControlMode.ATO) {
                    train.getControlSystem().slowDown();
                }
            }
        }
    }

    // 检查列车是否接近车站
    private void checkTrainApproachingStation(TrainEntity train) {
        String currentLine = train.getCurrentLine();
        if (currentLine != null) {
            LineControlSystem.LineInfo lineInfo = lineControlSystem.getLineInfo(currentLine);
            if (lineInfo != null) {
                for (LineControlSystem.StationInfo station : lineInfo.getStations()) {
                    double distance = train.getPos().squaredDistanceTo(station.getPosition().getX() + 0.5, 
                            station.getPosition().getY() + 0.5, station.getPosition().getZ() + 0.5);
                    
                    // 如果列车接近车站（200米内），准备进站
                    if (Math.sqrt(distance) < 200 && Math.sqrt(distance) > 50) {
                        // 减速
                        if (train.getControlSystem().getControlMode() == TrainControlSystem.TrainControlMode.ATO) {
                            train.getControlSystem().setTargetSpeed(30.0);
                        }
                        // 通知司机
                        if (train.getDriver() != null) {
                            train.getDriver().sendMessage(Text.literal("即将进站: " + station.getStationName()), false);
                        }
                    } 
                    // 如果列车在车站内（50米内），停车
                    else if (Math.sqrt(distance) < 50 && train.getCurrentSpeed() > 0) {
                        if (train.getControlSystem().getControlMode() == TrainControlSystem.TrainControlMode.ATO) {
                            train.getControlSystem().setTargetSpeed(0.0);
                        }
                        // 打开屏蔽门
                        openPlatformDoors(station.getPosition());
                    }
                }
            }
        }
    }

    // 更新信号机状态
    private void updateSignals() {
        for (BlockPos signalPos : signalMachines) {
            // 检查前方是否有列车
            boolean hasTrainAhead = checkTrainAhead(signalPos);
            // 检查前方道岔状态
            boolean switchAhead = checkSwitchAhead(signalPos);
            
            SignalBlock.SignalState newState = SignalBlock.SignalState.GREEN;
            if (hasTrainAhead) {
                newState = SignalBlock.SignalState.RED;
            } else if (switchAhead) {
                newState = SignalBlock.SignalState.YELLOW;
            }
            
            // 更新信号机状态
            SignalBlock.updateSignalState(world, signalPos, newState);
        }
    }

    // 检查信号机前方是否有列车
    private boolean checkTrainAhead(BlockPos signalPos) {
        // 简化版：检查信号机前方200米内是否有列车
        for (TrainEntity train : trains) {
            double distance = train.getPos().squaredDistanceTo(signalPos.getX() + 0.5, 
                    signalPos.getY() + 0.5, signalPos.getZ() + 0.5);
            if (Math.sqrt(distance) < 200) {
                return true;
            }
        }
        return false;
    }

    // 检查前方道岔状态
    private boolean checkSwitchAhead(BlockPos signalPos) {
        // 简化版：检查信号机前方100米内是否有道岔
        // 实际应用中需要根据轨道方向进行更准确的判断
        return false;
    }

    // 更新屏蔽门状态
    private void updatePlatformDoors() {
        for (BlockPos doorPos : platformDoors) {
            // 检查附近是否有列车停靠
            boolean hasTrainStopped = checkTrainStoppedNearby(doorPos);
            BlockState state = world.getBlockState(doorPos);
            
            if (hasTrainStopped && !state.get(PlatformDoorBlock.OPEN)) {
                // 打开屏蔽门
                PlatformDoorBlock.autoOpenDoor(world, doorPos);
            } else if (!hasTrainStopped && !state.get(PlatformDoorBlock.OPEN)) {
                // 关闭屏蔽门
                PlatformDoorBlock.autoCloseDoor(world, doorPos);
            }
        }
    }

    // 检查屏蔽门附近是否有列车停靠
    private boolean checkTrainStoppedNearby(BlockPos doorPos) {
        for (TrainEntity train : trains) {
            double distance = train.getPos().squaredDistanceTo(doorPos.getX() + 0.5, 
                    doorPos.getY() + 0.5, doorPos.getZ() + 0.5);
            if (Math.sqrt(distance) < 30 && Math.abs(train.getCurrentSpeed()) < 0.1) {
                return true;
            }
        }
        return false;
    }

    // 打开车站的所有屏蔽门
    private void openPlatformDoors(BlockPos stationPos) {
        for (BlockPos doorPos : platformDoors) {
            if (doorPos.getSquaredDistance(stationPos) < 100 * 100) { // 100米范围内
                PlatformDoorBlock.autoOpenDoor(world, doorPos);
            }
        }
    }

    // 运行AI调度
    private void runAIDispatch() {
        // 检查列车密度，调整发车间隔
        checkTrainDensity();
        // 检查线路阻塞情况
        checkLineCongestion();
        // 检查列车健康状况
        checkTrainHealth();
        
        // 智能调度建议生成
        generateDispatchSuggestions();
        
        // 列车运行状态评估
        evaluateTrainRunningStates();
    }
    
    // 生成调度建议
    private void generateDispatchSuggestions() {
        List<DispatchSuggestion> suggestions = dispatchAlgorithm.generateDispatchSuggestions();
        for (DispatchSuggestion suggestion : suggestions) {
            if (suggestion.getPriority() >= DispatchSuggestion.Priority.MEDIUM) {
                // 高优先级建议直接执行
                suggestion.execute();
                LogSystem.dispatchLog("执行调度建议: " + suggestion.getDescription());
            } else {
                // 低优先级建议记录备用
                LogSystem.dispatchLog("低优先级建议: " + suggestion.getDescription());
            }
        }
    }
    
    // 评估列车运行状态
    private void evaluateTrainRunningStates() {
        for (TrainEntity train : trains) {
            TrainRunningStatus status = dispatchAlgorithm.evaluateTrainStatus(train);
            
            if (status == TrainRunningStatus.DELAYED) {
                // 处理延误列车
                handleDelayedTrain(train);
            } else if (status == TrainRunningStatus.TOO_FAST) {
                // 处理超速列车
                handleOverSpeedingTrain(train);
            } else if (status == TrainRunningStatus.MALFUNCTION) {
                // 处理故障列车
                handleFaultyTrain(train);
            }
        }
    }
    
    // 处理延误列车
    private void handleDelayedTrain(TrainEntity train) {
        double delayTime = train.getDelayTime();
        
        if (delayTime < 60) { // 小于1分钟延误
            // 可以通过减少后续停站时间来恢复正点
            train.reduceStationStopTime(0.8);
        } else if (delayTime < 300) { // 1-5分钟延误
            // 调整后续车站的停站时间和运行速度
            train.setRecoveryMode(true);
        } else { // 严重延误
            // 通知调度员和司机
            if (train.getDriver() != null) {
                train.getDriver().sendMessage(Text.literal("⚠ 列车严重延误，请按调度指令运行，必要时跳过部分站点"), false);
            }
            // 考虑调整后续列车
            adjustFollowingTrainsForDelay(train);
        }
    }
    
    // 处理超速列车
    private void handleOverSpeedingTrain(TrainEntity train) {
        if (train.getDriver() != null) {
            train.getDriver().sendMessage(Text.literal("⚠ 注意：您的列车运行速度过快，请减速至安全速度"), false);
        }
        
        // 在ATO模式下自动调整速度
        if (train.getControlSystem().getControlMode() == TrainControlSystem.TrainControlMode.ATO) {
            double currentSpeed = train.getCurrentSpeed();
            double speedLimit = train.getCurrentSpeedLimit();
            if (currentSpeed > speedLimit) {
                train.getControlSystem().setTargetSpeed(speedLimit * 0.9);
            }
        }
    }
    
    // 为延误调整后续列车
    private void adjustFollowingTrainsForDelay(TrainEntity delayedTrain) {
        String lineId = delayedTrain.getCurrentLine();
        if (lineId == null) return;
        
        List<TrainEntity> followingTrains = getFollowingTrains(delayedTrain);
        for (TrainEntity train : followingTrains) {
            // 增加与延误列车的间隔
            train.increaseFollowingDistance(1.5);
            
            if (train.getDriver() != null) {
                train.getDriver().sendMessage(Text.literal("⚠ 前方列车严重延误，请保持安全距离并做好乘客安抚工作"), false);
            }
        }
    }
    
    // 获取后续列车
    private List<TrainEntity> getFollowingTrains(TrainEntity targetTrain) {
        List<TrainEntity> followingTrains = new ArrayList<>();
        String lineId = targetTrain.getCurrentLine();
        if (lineId == null) return followingTrains;
        
        for (TrainEntity train : getTrainsOnLine(lineId)) {
            if (train != targetTrain && isTrainFollowing(train, targetTrain)) {
                followingTrains.add(train);
            }
        }
        
        // 按距离排序，最近的在前
        followingTrains.sort(Comparator.comparingDouble(t -> calculateDistance(t, targetTrain)));
        return followingTrains;
    }
    
    // 计算两列车之间的距离
    private double calculateDistance(TrainEntity train1, TrainEntity train2) {
        return train1.getPos().distanceTo(train2.getPos());
    }
    
    // 获取线路上的列车
    private List<TrainEntity> getTrainsOnLine(String lineId) {
        return trains.stream()
                .filter(train -> lineId.equals(train.getCurrentLine()))
                .collect(Collectors.toList());
    }
    
    // 计算最优列车数量
    private int calculateOptimalTrainCount(String lineId, boolean isPeak) {
        // 基础列车数量
        int baseCount = 5;
        
        // 高峰期增加列车数量
        if (isPeak) {
            baseCount = (int)(baseCount * 1.5); // 高峰期增加50%的列车
        }
        
        return baseCount;
    }
    
    // 计算线路负载因子
    private double calculateLineLoadFactor(String lineId, int trainCount) {
        // 假设每条线路的最大容量为10列列车
        int maxCapacity = 10;
        
        // 计算负载因子
        return Math.min((double)trainCount / maxCapacity, 1.0);
    }
    
    // 实施线路容量控制
    private void implementLineCapacityControl(String lineId) {
        KRTMod.LOGGER.info("对线路 {} 实施容量控制措施", lineId);
        
        // 1. 调整发车间隔，避免进一步拥挤
        scheduleManager.increaseHeadwayTemporarily(lineId, 1.5); // 临时增加50%间隔
        
        // 2. 激活限流措施
        activateStationFlowControl(lineId);
        
        // 3. 调整部分列车为区间车
        dispatchAlgorithm.convertToShortTurnTrains(lineId);
        
        // 4. 通知所有车站
        notifyStationsOfCapacityControl(lineId);
    }
    
    // 激活车站限流措施
    private void activateStationFlowControl(String lineId) {
        LineControlSystem.LineInfo lineInfo = lineControlSystem.getLineInfo(lineId);
        if (lineInfo != null) {
            for (LineControlSystem.StationInfo station : lineInfo.getStations()) {
                int passengerCount = passengerFlowMonitor.getStationPassengerCount(station.getStationName());
                if (passengerCount > 150) { // 假设150人需要限流
                    LogSystem.dispatchLog("对车站 " + station.getStationName() + " 实施限流措施");
                    // 这里可以实现车站限流的逻辑
                }
            }
        }
    }
    
    // 通知车站容量控制
    private void notifyStationsOfCapacityControl(String lineId) {
        // 通知相关人员线路容量控制情况
        LogSystem.dispatchLog("线路 " + lineId + " 已实施容量控制，请相关人员做好应对准备");
    }
    
    // 查找最近的车站
    private String findNearestStation(TrainEntity train) {
        String currentLine = train.getCurrentLine();
        if (currentLine == null) return "";
        
        LineControlSystem.LineInfo lineInfo = lineControlSystem.getLineInfo(currentLine);
        if (lineInfo == null) return "";
        
        String nearestStation = "";
        double minDistance = Double.MAX_VALUE;
        
        for (LineControlSystem.StationInfo station : lineInfo.getStations()) {
            double distance = train.getPos().squaredDistanceTo(station.getPosition().getX() + 0.5, 
                    station.getPosition().getY() + 0.5, station.getPosition().getZ() + 0.5);
            if (distance < minDistance) {
                minDistance = distance;
                nearestStation = station.getStationName();
            }
        }
        
        return nearestStation;
    }
    
    // 调整后续列车运行
    private void adjustFollowingTrains(TrainEntity faultyTrain) {
        String lineId = faultyTrain.getCurrentLine();
        if (lineId == null) return;
        
        Vec3d faultyPos = faultyTrain.getPos();
        
        for (TrainEntity train : getTrainsOnLine(lineId)) {
            if (train != faultyTrain && isTrainFollowing(train, faultyTrain)) {
                if (train.getDriver() != null) {
                    train.getDriver().sendMessage(Text.literal("⚠ 前方列车故障，请减速并保持安全距离！"), false);
                }
                
                // 降低后续列车速度
                if (train.getControlSystem().getControlMode() == TrainControlSystem.TrainControlMode.ATO) {
                    train.getControlSystem().setTargetSpeed(train.getCurrentSpeed() * 0.7);
                }
            }
        }
    }
    
    // 判断列车是否在另一列车后面
    private boolean isTrainFollowing(TrainEntity train, TrainEntity targetTrain) {
        // 简化实现：假设沿着Z轴方向运行，比较Z坐标
        // 实际应该根据轨道方向和列车位置进行更准确的判断
        return train.getPos().z > targetTrain.getPos().z && 
               Math.abs(train.getPos().x - targetTrain.getPos().x) < 10;
    }

    // 更新列车位置信息
    private void updateTrainPositions() {
        for (TrainEntity train : trains) {
            String trainId = train.getUuidAsString();
            TrainPositionInfo posInfo = new TrainPositionInfo(
                trainId,
                train.getPos(),
                train.getCurrentSpeed(),
                train.getCurrentLine(),
                train.getCurrentStation(),
                System.currentTimeMillis()
            );
            trainPositionMap.put(trainId, posInfo);
        }
    }
    
    // 处理调度事件队列
    private void processDispatchEvents() {
        while (!dispatchEventQueue.isEmpty()) {
            DispatchEvent event = dispatchEventQueue.poll();
            if (event.isValid()) {
                event.execute();
                LogSystem.dispatchLog("执行调度事件: " + event.getDescription());
            }
        }
    }
    
    // 动态调整运行图
    private void adjustScheduleIfNeeded() {
        // 每分钟检查一次是否需要调整运行图
        if (world.getTime() % 1200 != 0) {
            return;
        }
        
        // 根据客流情况调整
        for (String lineId : lineControlSystem.getAllLines().keySet()) {
            double passengerFlowRate = passengerFlowMonitor.getLinePassengerFlowRate(lineId);
            boolean needsAdjustment = dispatchAlgorithm.needsScheduleAdjustment(lineId, passengerFlowRate);
            
            if (needsAdjustment) {
                ScheduleAdjustment adjustment = dispatchAlgorithm.calculateOptimalAdjustment(lineId, passengerFlowRate);
                applyScheduleAdjustment(lineId, adjustment);
            }
        }
    }
    
    // 应用运行图调整
    private void applyScheduleAdjustment(String lineId, ScheduleAdjustment adjustment) {
        if (adjustment.getType() == ScheduleAdjustment.AdjustmentType.INCREASE_FREQUENCY) {
            KRTMod.LOGGER.info("增加线路 {} 的发车频率: 间隔从 {} 调整为 {} 秒", 
                lineId, adjustment.getOriginalValue(), adjustment.getNewValue());
            scheduleManager.adjustHeadway(lineId, adjustment.getNewValue());
        } else if (adjustment.getType() == ScheduleAdjustment.AdjustmentType.REDUCE_FREQUENCY) {
            KRTMod.LOGGER.info("减少线路 {} 的发车频率: 间隔从 {} 调整为 {} 秒", 
                lineId, adjustment.getOriginalValue(), adjustment.getNewValue());
            scheduleManager.adjustHeadway(lineId, adjustment.getNewValue());
        } else if (adjustment.getType() == ScheduleAdjustment.AdjustmentType.ADD_EXPRESS_TRAIN) {
            KRTMod.LOGGER.info("为线路 {} 添加快速列车", lineId);
            dispatchAlgorithm.dispatchExpressTrain(lineId);
        }
        
        // 记录调整历史
        DispatchAdjustment record = new DispatchAdjustment(
            world.getTime(),
            lineId,
            adjustment.getType().name(),
            adjustment.getOriginalValue(),
            adjustment.getNewValue(),
            passengerFlowMonitor.getLinePassengerFlowRate(lineId)
        );
        adjustmentHistory.put(world.getTime(), record);
    }
    
    // 检查列车密度
    private void checkTrainDensity() {
        for (String lineId : lineControlSystem.getAllLines().keySet()) {
            List<TrainEntity> lineTrains = getTrainsOnLine(lineId);
            double density = calculateTrainDensity(lineId, lineTrains.size());
            
            if (density > 0.85) { // 密度过高
                KRTMod.LOGGER.warning("线路 {} 列车密度过高: {}", lineId, density);
                // 可以采取减少发车频率等措施
            } else if (density < 0.4) { // 密度过低
                KRTMod.LOGGER.info("线路 {} 列车密度较低: {}, 可以适当增加列车", lineId, density);
                // 可以采取增加发车频率等措施
            }
        }
    }
    
    // 计算列车密度
    private double calculateTrainDensity(String lineId, int trainCount) {
        LineControlSystem.LineInfo lineInfo = lineControlSystem.getLineInfo(lineId);
        if (lineInfo == null) return 0.0;
        
        // 根据线路长度和最优列车数计算密度
        double lineLength = lineInfo.getLength();
        int optimalTrainCount = calculateOptimalTrainCount(lineId, false);
        
        return Math.min(1.0, (double) trainCount / optimalTrainCount);
    }

    // 检查线路阻塞情况
    private void checkLineCongestion() {
        // 检查每个线路的阻塞情况
        for (String lineId : lineControlSystem.getAllLines().keySet()) {
            // 计算平均运行延误
            double avgDelay = calculateAverageDelay(lineId);
            
            if (avgDelay > 30) { // 平均延误超过30秒
                KRTMod.LOGGER.warning("线路 {} 出现拥堵，平均延误 {} 秒", lineId, avgDelay);
                // 采取拥堵缓解措施
                handleLineCongestion(lineId, avgDelay);
            }
        }
    }
    
    // 计算平均延误时间
    private double calculateAverageDelay(String lineId) {
        List<TrainEntity> lineTrains = getTrainsOnLine(lineId);
        if (lineTrains.isEmpty()) return 0;
        
        double totalDelay = 0;
        int count = 0;
        
        for (TrainEntity train : lineTrains) {
            double delay = train.getDelayTime();
            if (delay > 0) {
                totalDelay += delay;
                count++;
            }
        }
        
        return count > 0 ? totalDelay / count : 0;
    }
    
    // 处理线路拥堵
    private void handleLineCongestion(String lineId, double avgDelay) {
        // 根据拥堵程度采取不同措施
        if (avgDelay < 60) { // 轻度拥堵
            // 调整信号优先级
            adjustSignalPriorities(lineId);
        } else if (avgDelay < 120) { // 中度拥堵
            // 减少停站时间
            reduceStationStopTimes(lineId);
        } else { // 严重拥堵
            // 考虑临时调整运行图
            scheduleManager.activateEmergencySchedule(lineId);
            // 通知所有相关列车
            notifyTrainsOfCongestion(lineId);
        }
    }
    
    // 调整信号优先级
    private void adjustSignalPriorities(String lineId) {
        // 这里可以实现信号机优先级调整的逻辑
        LogSystem.dispatchLog("调整线路 " + lineId + " 的信号优先级以缓解拥堵");
    }
    
    // 减少停站时间
    private void reduceStationStopTimes(String lineId) {
        List<TrainEntity> lineTrains = getTrainsOnLine(lineId);
        for (TrainEntity train : lineTrains) {
            // 减少停站时间10%
            train.reduceStationStopTime(0.9);
        }
    }
    
    // 通知列车拥堵情况
    private void notifyTrainsOfCongestion(String lineId) {
        List<TrainEntity> lineTrains = getTrainsOnLine(lineId);
        for (TrainEntity train : lineTrains) {
            if (train.getDriver() != null) {
                train.getDriver().sendMessage(Text.literal("⚠ 线路严重拥堵，请按调度指令减速运行！"), false);
            }
        }
    }

    // 检查列车健康状况
    private void checkTrainHealth() {
        for (TrainEntity train : trains) {
            if (train.getHealth() < 50) {
                // 提示列车返回车辆段检修
                if (train.getDriver() != null) {
                    train.getDriver().sendMessage(Text.literal("调度命令: 您的列车健康状况不佳，请返回车辆段进行检修。"), false);
                }
            }
        }
    }

    // 查找最近的线路
    private String findNearestLine(Vec3d pos) {
        String nearestLine = null;
        double minDistance = Double.MAX_VALUE;
        
        for (LineControlSystem.LineInfo line : lineControlSystem.getAllLines()) {
            for (BlockPos trackPos : line.getTracks()) {
                double distance = pos.squaredDistanceTo(trackPos.getX() + 0.5, trackPos.getY() + 0.5, trackPos.getZ() + 0.5);
                if (distance < minDistance && distance < 100) { // 10米范围内
                    minDistance = distance;
                    nearestLine = line.getLineId();
                }
            }
        }
        
        return nearestLine;
    }

    // 记录系统状态日志
    private void logSystemStatus() {
        // 每分钟记录一次系统状态
        if (world.getTime() % 1200 == 0) {
            KRTMod.LOGGER.info("调度系统状态 - 时间: {}, 列车数量: {}, 信号机数量: {}, 屏蔽门数量: {}",
                    world.getTime(), trains.size(), signalMachines.size(), platformDoors.size());
            PerformanceMonitor.getInstance().endSystemExecution("DispatchSystem");
        }
    }

    // 设置调度模式
    public void setDispatchMode(DispatchMode mode) {
        this.dispatchMode = mode;
        KRTMod.LOGGER.info("调度模式已切换至: {}", mode.getDisplayName());
    }

    // 获取调度模式
    public DispatchMode getDispatchMode() {
        return dispatchMode;
    }
    
    // 获取所有车站信息
    public Map<String, BlockPos> getAllStations() {
        Map<String, BlockPos> stationsMap = new HashMap<>();
        
        // 遍历所有线路，收集所有车站
        for (LineControlSystem.LineInfo lineInfo : lineControlSystem.getAllLines()) {
            for (LineControlSystem.StationInfo stationInfo : lineInfo.getStations()) {
                stationsMap.put(stationInfo.getStationName(), stationInfo.getPosition());
            }
        }
        
        return stationsMap;
    }

    // 获取AI建议
    public String getAISuggestion() {
        return aiSystem.getSuggestion(this);
    }

    // 获取所有列车
    public List<TrainEntity> getAllTrains() {
        return new ArrayList<>(trains);
    }

    // 获取所有信号机
    public List<BlockPos> getAllSignalMachines() {
        return new ArrayList<>(signalMachines);
    }

    // 获取所有屏蔽门
    public List<BlockPos> getAllPlatformDoors() {
        return new ArrayList<>(platformDoors);
    }

    // 调度模式枚举
    public enum DispatchMode {
        MANUAL("手动控制"),
        SYSTEM("系统控制"),
        SEMI_AUTO("半自动控制"),
        EMERGENCY("应急模式");

        private final String displayName;

        DispatchMode(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }
    
    // 列车位置信息类
    private static class TrainPositionInfo {
        private final String trainId;
        private final Vec3d position;
        private final double speed;
        private final String lineId;
        private final String currentStation;
        private final long timestamp;
        
        public TrainPositionInfo(String trainId, Vec3d position, double speed, 
                               String lineId, String currentStation, long timestamp) {
            this.trainId = trainId;
            this.position = position;
            this.speed = speed;
            this.lineId = lineId;
            this.currentStation = currentStation;
            this.timestamp = timestamp;
        }
        
        // 获取与前一位置的距离变化
        public double getDistanceChange(TrainPositionInfo previous) {
            return position.distanceTo(previous.position);
        }
        
        // 检查数据是否新鲜
        public boolean isFresh() {
            return System.currentTimeMillis() - timestamp < 5000; // 5秒内的数据视为新鲜
        }
    }
    
    // 调度事件类
    private abstract class DispatchEvent {
        private final long creationTime;
        private final String description;
        
        public DispatchEvent(String description) {
            this.creationTime = System.currentTimeMillis();
            this.description = description;
        }
        
        public abstract void execute();
        
        public boolean isValid() {
            // 事件有效期为10分钟
            return System.currentTimeMillis() - creationTime < 600000;
        }
        
        public String getDescription() {
            return description;
        }
    }
    
    // 列车运行图管理器
    private class TrainScheduleManager {
        private final Map<String, LineSchedule> lineSchedules = new ConcurrentHashMap<>();
        private final Map<String, LineSchedule> peakHourSchedules = new ConcurrentHashMap<>();
        private final Map<String, LineSchedule> emergencySchedules = new ConcurrentHashMap<>();
        private boolean isPeakHourModeActive = false;
        
        public void updateCurrentSchedule() {
            // 每分钟更新一次运行图状态
            if (world.getTime() % 1200 == 0) {
                // 更新各线路运行图状态
                for (String lineId : lineControlSystem.getAllLines().keySet()) {
                    updateLineSchedule(lineId);
                }
            }
        }
        
        private void updateLineSchedule(String lineId) {
            LineSchedule schedule = getCurrentSchedule(lineId);
            if (schedule != null) {
                schedule.update();
            }
        }
        
        public LineSchedule getCurrentSchedule(String lineId) {
            if (emergencySchedules.containsKey(lineId) && emergencySchedules.get(lineId).isActive()) {
                return emergencySchedules.get(lineId);
            }
            
            if (isPeakHourModeActive && peakHourSchedules.containsKey(lineId)) {
                return peakHourSchedules.get(lineId);
            }
            
            return lineSchedules.get(lineId);
        }
        
        public void adjustHeadway(String lineId, double newHeadway) {
            LineSchedule schedule = getCurrentSchedule(lineId);
            if (schedule != null) {
                schedule.setHeadway(newHeadway);
            }
        }
        
        public void increaseHeadwayTemporarily(String lineId, double multiplier) {
            LineSchedule schedule = getCurrentSchedule(lineId);
            if (schedule != null) {
                schedule.increaseHeadwayTemporarily(multiplier);
            }
        }
        
        public void activatePeakHourSchedule() {
            isPeakHourModeActive = true;
            LogSystem.dispatchLog("激活高峰期运行图");
        }
        
        public void deactivatePeakHourSchedule() {
            isPeakHourModeActive = false;
            LogSystem.dispatchLog("停用高峰期运行图");
        }
        
        public void activateEmergencySchedule(String lineId) {
            // 创建或激活应急运行图
            if (!emergencySchedules.containsKey(lineId)) {
                emergencySchedules.put(lineId, new LineSchedule(lineId, true));
            }
            emergencySchedules.get(lineId).activate();
            LogSystem.dispatchLog("为线路 " + lineId + " 激活应急运行图");
        }
        
        // 线路运行图类
        private class LineSchedule {
            private final String lineId;
            private double headway; // 发车间隔（秒）
            private double originalHeadway;
            private long temporaryAdjustmentEndTime;
            private boolean isEmergency; // 是否为应急运行图
            private boolean isActive = true;
            
            public LineSchedule(String lineId, boolean isEmergency) {
                this.lineId = lineId;
                this.isEmergency = isEmergency;
                // 根据线路设置默认发车间隔
                this.headway = isEmergency ? 480 : 300; // 应急模式8分钟，正常5分钟
                this.originalHeadway = this.headway;
            }
            
            public void update() {
                // 检查临时调整是否结束
                if (temporaryAdjustmentEndTime > 0 && System.currentTimeMillis() > temporaryAdjustmentEndTime) {
                    this.headway = this.originalHeadway;
                    this.temporaryAdjustmentEndTime = 0;
                    LogSystem.dispatchLog("线路 " + lineId + " 发车间隔恢复正常");
                }
            }
            
            public void setHeadway(double newHeadway) {
                this.headway = newHeadway;
                this.originalHeadway = newHeadway;
            }
            
            public void increaseHeadwayTemporarily(double multiplier) {
                double newHeadway = this.originalHeadway * multiplier;
                this.headway = newHeadway;
                // 临时调整持续30分钟
                this.temporaryAdjustmentEndTime = System.currentTimeMillis() + 30 * 60 * 1000;
            }
            
            public double getHeadway() {
                return headway;
            }
            
            public boolean isActive() {
                return isActive;
            }
            
            public void activate() {
                this.isActive = true;
            }
            
            public void deactivate() {
                this.isActive = false;
            }
        }
    }
    
    // 智能调度算法系统
    private class IntelligentDispatchAlgorithm {
        // 执行调度算法
        public void executeScheduling() {
            // 这里可以实现更复杂的调度算法
            // 如基于强化学习的动态调度、遗传算法优化等
        }
        
        // 判断是否需要调整运行图
        public boolean needsScheduleAdjustment(String lineId, double passengerFlowRate) {
            // 当客流变化超过20%时需要调整
            return Math.abs(passengerFlowRate - 1.0) > 0.2;
        }
        
        // 计算最优调整方案
        public ScheduleAdjustment calculateOptimalAdjustment(String lineId, double passengerFlowRate) {
            double originalHeadway = scheduleManager.getCurrentSchedule(lineId).getHeadway();
            
            if (passengerFlowRate > 1.2) { // 客流增加20%以上
                // 增加发车频率，缩短间隔
                double newHeadway = originalHeadway / passengerFlowRate;
                // 确保最小间隔不小于安全限制（假设为2分钟）
                newHeadway = Math.max(newHeadway, 120);
                return new ScheduleAdjustment(ScheduleAdjustment.AdjustmentType.INCREASE_FREQUENCY, 
                                            originalHeadway, newHeadway);
            } else if (passengerFlowRate < 0.8) { // 客流减少20%以上
                // 减少发车频率，延长间隔
                double newHeadway = originalHeadway / passengerFlowRate;
                // 确保最大间隔不超过舒适限制（假设为10分钟）
                newHeadway = Math.min(newHeadway, 600);
                return new ScheduleAdjustment(ScheduleAdjustment.AdjustmentType.REDUCE_FREQUENCY, 
                                            originalHeadway, newHeadway);
            } else if (passengerFlowRate > 1.5 && hasLongLineSections(lineId)) {
                // 客流特别大且线路较长时，考虑增开快速列车
                return new ScheduleAdjustment(ScheduleAdjustment.AdjustmentType.ADD_EXPRESS_TRAIN, 0, 0);
            }
            
            // 默认不需要调整
            return new ScheduleAdjustment(ScheduleAdjustment.AdjustmentType.NO_CHANGE, 0, 0);
        }
        
        // 检查线路是否有长区间
        private boolean hasLongLineSections(String lineId) {
            LineControlSystem.LineInfo lineInfo = lineControlSystem.getLineInfo(lineId);
            if (lineInfo == null) return false;
            
            // 如果线路长度超过10公里，认为有长区间
            return lineInfo.getLength() > 10000;
        }
        
        // 调度快速列车
        public void dispatchExpressTrain(String lineId) {
            // 查找可以转为快速列车的普通列车
            List<TrainEntity> lineTrains = getTrainsOnLine(lineId);
            for (TrainEntity train : lineTrains) {
                if (!train.isExpressTrain() && train.getPassengerCount() < train.getCapacity() * 0.5) {
                    // 将列车转为快速列车
                    train.setExpressTrain(true);
                    // 设置快速停站
                    List<String> expressStations = selectExpressStations(lineId);
                    train.setExpressStations(expressStations);
                    LogSystem.dispatchLog("列车 " + train.getTrainId() + " 已转为快速列车");
                    return;
                }
            }
            
            // 如果没有合适的列车，考虑从车辆段调度
            LogSystem.dispatchLog("尝试从车辆段调度快速列车到线路 " + lineId);
        }
        
        // 选择快速列车停站
        private List<String> selectExpressStations(String lineId) {
            List<String> expressStations = new ArrayList<>();
            LineControlSystem.LineInfo lineInfo = lineControlSystem.getLineInfo(lineId);
            if (lineInfo == null) return expressStations;
            
            List<LineControlSystem.StationInfo> stations = lineInfo.getStations();
            // 选择客流大的车站作为快速列车停靠站
            for (LineControlSystem.StationInfo station : stations) {
                int passengerCount = passengerFlowMonitor.getStationPassengerCount(station.getStationName());
                // 选择客流量前30%的车站
                if (passengerCount > passengerFlowMonitor.getAverageStationPassengerCount(lineId) * 0.7) {
                    expressStations.add(station.getStationName());
                }
            }
            
            // 确保首末站一定停靠
            if (!expressStations.contains(stations.get(0).getStationName())) {
                expressStations.add(0, stations.get(0).getStationName());
            }
            if (!expressStations.contains(stations.get(stations.size() - 1).getStationName())) {
                expressStations.add(stations.get(stations.size() - 1).getStationName());
            }
            
            return expressStations;
        }
        
        // 调度应急列车
        public void dispatchEmergencyTrain(String lineId) {
            LogSystem.dispatchLog("调度应急列车到线路 " + lineId);
            // 这里可以实现从车辆段调度应急列车的逻辑
        }
        
        // 转换为区间车
        public void convertToShortTurnTrains(String lineId) {
            LogSystem.dispatchLog("将部分列车转换为区间车以缓解线路 " + lineId + " 的拥堵");
            // 这里可以实现列车转为区间车的逻辑
        }
        
        // 获取关键拥堵点
        public List<String> getCriticalCongestionPoints(String lineId) {
            List<String> criticalPoints = new ArrayList<>();
            LineControlSystem.LineInfo lineInfo = lineControlSystem.getLineInfo(lineId);
            if (lineInfo == null) return criticalPoints;
            
            // 找出客流量最大的5个车站作为关键拥堵点
            Map<String, Integer> stationFlow = new HashMap<>();
            for (LineControlSystem.StationInfo station : lineInfo.getStations()) {
                int count = passengerFlowMonitor.getStationPassengerCount(station.getStationName());
                stationFlow.put(station.getStationName(), count);
            }
            
            // 按客流量排序，取前5个
            stationFlow.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(5)
                .forEach(entry -> criticalPoints.add(entry.getKey()));
            
            return criticalPoints;
        }
        
        // 生成调度建议
        public List<DispatchSuggestion> generateDispatchSuggestions() {
            List<DispatchSuggestion> suggestions = new ArrayList<>();
            
            // 分析各线路状态，生成建议
            for (String lineId : lineControlSystem.getAllLines().keySet()) {
                // 基于列车密度的建议
                double density = calculateTrainDensity(lineId, getTrainsOnLine(lineId).size());
                if (density < 0.4 && passengerFlowMonitor.getLinePassengerFlowRate(lineId) > 1.1) {
                    suggestions.add(new AddTrainSuggestion(lineId));
                }
                
                // 基于延误情况的建议
                double avgDelay = calculateAverageDelay(lineId);
                if (avgDelay > 60) {
                    suggestions.add(new AdjustScheduleSuggestion(lineId, avgDelay));
                }
                
                // 基于客流分布的建议
                if (hasUnevenPassengerDistribution(lineId)) {
                    suggestions.add(new RedistributeTrainsSuggestion(lineId));
                }
            }
            
            return suggestions;
        }
        
        // 检查客流分布是否不均匀
        private boolean hasUnevenPassengerDistribution(String lineId) {
            LineControlSystem.LineInfo lineInfo = lineControlSystem.getLineInfo(lineId);
            if (lineInfo == null) return false;
            
            List<Integer> passengerCounts = new ArrayList<>();
            for (LineControlSystem.StationInfo station : lineInfo.getStations()) {
                passengerCounts.add(passengerFlowMonitor.getStationPassengerCount(station.getStationName()));
            }
            
            // 计算标准差，如果标准差太大，说明分布不均匀
            if (passengerCounts.isEmpty()) return false;
            
            double mean = passengerCounts.stream().mapToInt(Integer::intValue).average().orElse(0);
            double variance = passengerCounts.stream()
                .mapToDouble(count -> Math.pow(count - mean, 2))
                .average().orElse(0);
            double stdDev = Math.sqrt(variance);
            
            // 如果标准差大于平均值的一半，认为分布不均匀
            return stdDev > mean * 0.5;
        }
        
        // 评估列车运行状态
        public TrainRunningStatus evaluateTrainStatus(TrainEntity train) {
            if (train.getHealth() < 30) {
                return TrainRunningStatus.MALFUNCTION;
            }
            
            if (train.getDelayTime() > 60) {
                return TrainRunningStatus.DELAYED;
            }
            
            double speedLimit = train.getCurrentSpeedLimit();
            double currentSpeed = train.getCurrentSpeed();
            if (currentSpeed > speedLimit * 1.1) { // 超速10%以上
                return TrainRunningStatus.TOO_FAST;
            }
            
            return TrainRunningStatus.NORMAL;
        }
        
        // 调度建议类
        private abstract class DispatchSuggestion {
            private final Priority priority;
            
            public enum Priority {
                LOW, MEDIUM, HIGH, CRITICAL
            }
            
            public DispatchSuggestion(Priority priority) {
                this.priority = priority;
            }
            
            public abstract void execute();
            public abstract String getDescription();
            
            public Priority getPriority() {
                return priority;
            }
        }
        
        // 增加列车建议
        private class AddTrainSuggestion extends DispatchSuggestion {
            private final String lineId;
            
            public AddTrainSuggestion(String lineId) {
                super(Priority.MEDIUM);
                this.lineId = lineId;
            }
            
            @Override
            public void execute() {
                // 实现增加列车的逻辑
                LogSystem.dispatchLog("建议为线路 " + lineId + " 增加列车以应对客流增长");
            }
            
            @Override
            public String getDescription() {
                return "为线路 " + lineId + " 增加列车以应对客流增长";
            }
        }
        
        // 调整运行图建议
        private class AdjustScheduleSuggestion extends DispatchSuggestion {
            private final String lineId;
            private final double avgDelay;
            
            public AdjustScheduleSuggestion(String lineId, double avgDelay) {
                super(avgDelay > 180 ? Priority.HIGH : Priority.MEDIUM); // 3分钟以上延误为高优先级
                this.lineId = lineId;
                this.avgDelay = avgDelay;
            }
            
            @Override
            public void execute() {
                // 实现调整运行图的逻辑
                LogSystem.dispatchLog("建议调整线路 " + lineId + " 的运行图以缓解平均延误 " + avgDelay + " 秒");
            }
            
            @Override
            public String getDescription() {
                return "调整线路 " + lineId + " 的运行图以缓解平均延误 " + avgDelay + " 秒";
            }
        }
        
        // 重新分配列车建议
        private class RedistributeTrainsSuggestion extends DispatchSuggestion {
            private final String lineId;
            
            public RedistributeTrainsSuggestion(String lineId) {
                super(Priority.MEDIUM);
                this.lineId = lineId;
            }
            
            @Override
            public void execute() {
                // 实现重新分配列车的逻辑
                LogSystem.dispatchLog("建议重新分配线路 " + lineId + " 的列车以平衡客流分布");
            }
            
            @Override
            public String getDescription() {
                return "重新分配线路 " + lineId + " 的列车以平衡客流分布";
            }
        }
    }
    
    // 客流监控系统
    private class PassengerFlowMonitor {
        private final Map<String, Integer> stationPassengerCounts = new ConcurrentHashMap<>();
        private final Map<String, Double> linePassengerFlowRates = new ConcurrentHashMap<>();
        private final Map<String, Double> passengerFlowTrends = new ConcurrentHashMap<>();
        private final Map<String, Integer> previousStationPassengerCounts = new ConcurrentHashMap<>();
        
        public void update() {
            // 每分钟更新一次客流数据
            if (world.getTime() % 1200 == 0) {
                // 更新各车站的客流数据
                updateStationPassengerCounts();
                // 更新各线路的客流率
                updateLinePassengerFlowRates();
                // 更新客流趋势
                updatePassengerFlowTrends();
            }
        }
        
        private void updateStationPassengerCounts() {
            // 模拟更新车站客流数据
            // 实际应用中应该从车站系统获取真实数据
            for (String stationName : getAllStationNames()) {
                // 模拟客流波动，基于时间变化
                int baseCount = getBasePassengerCount(stationName);
                double timeFactor = isPeakHour() ? 2.5 : 1.0;
                double randomFactor = 0.8 + Math.random() * 0.4; // 0.8-1.2之间的随机因子
                
                int passengerCount = (int)(baseCount * timeFactor * randomFactor);
                previousStationPassengerCounts.put(stationName, 
                                                  stationPassengerCounts.getOrDefault(stationName, baseCount));
                stationPassengerCounts.put(stationName, passengerCount);
            }
        }
        
        private void updateLinePassengerFlowRates() {
            for (String lineId : lineControlSystem.getAllLines().keySet()) {
                LineControlSystem.LineInfo lineInfo = lineControlSystem.getLineInfo(lineId);
                if (lineInfo == null) continue;
                
                int totalPassengers = 0;
                int stationCount = 0;
                
                for (LineControlSystem.StationInfo station : lineInfo.getStations()) {
                    totalPassengers += stationPassengerCounts.getOrDefault(station.getStationName(), 0);
                    stationCount++;
                }
                
                double avgPassengers = stationCount > 0 ? (double)totalPassengers / stationCount : 0;
                double baseFlowRate = 100; // 假设基准客流为100人/站
                double flowRate = baseFlowRate > 0 ? avgPassengers / baseFlowRate : 0;
                
                linePassengerFlowRates.put(lineId, flowRate);
            }
        }
        
        private void updatePassengerFlowTrends() {
            for (String lineId : lineControlSystem.getAllLines().keySet()) {
                LineControlSystem.LineInfo lineInfo = lineControlSystem.getLineInfo(lineId);
                if (lineInfo == null) continue;
                
                double totalChange = 0;
                int stationCount = 0;
                
                for (LineControlSystem.StationInfo station : lineInfo.getStations()) {
                    String stationName = station.getStationName();
                    int current = stationPassengerCounts.getOrDefault(stationName, 0);
                    int previous = previousStationPassengerCounts.getOrDefault(stationName, current);
                    
                    if (previous > 0) {
                        double change = (double)(current - previous) / previous;
                        totalChange += change;
                        stationCount++;
                    }
                }
                
                double avgChange = stationCount > 0 ? totalChange / stationCount : 0;
                passengerFlowTrends.put(lineId, avgChange);
            }
        }
        
        private Set<String> getAllStationNames() {
            Set<String> stationNames = new HashSet<>();
            
            for (LineControlSystem.LineInfo lineInfo : lineControlSystem.getAllLines()) {
                for (LineControlSystem.StationInfo stationInfo : lineInfo.getStations()) {
                    stationNames.add(stationInfo.getStationName());
                }
            }
            
            return stationNames;
        }
        
        private int getBasePassengerCount(String stationName) {
            // 为不同类型的车站设置不同的基准客流
            // 实际应用中应该基于历史数据
            if (stationName.contains("中心") || stationName.contains("枢纽")) {
                return 150;
            } else if (stationName.contains("广场") || stationName.contains("商城")) {
                return 120;
            } else if (stationName.contains("医院") || stationName.contains("学校")) {
                return 100;
            } else {
                return 60;
            }
        }
        
        public int getStationPassengerCount(String stationName) {
            return stationPassengerCounts.getOrDefault(stationName, 0);
        }
        
        public double getLinePassengerFlowRate(String lineId) {
            return linePassengerFlowRates.getOrDefault(lineId, 1.0);
        }
        
        public double getPassengerFlowTrend(String lineId) {
            return passengerFlowTrends.getOrDefault(lineId, 0.0);
        }
        
        public double getAverageStationPassengerCount(String lineId) {
            LineControlSystem.LineInfo lineInfo = lineControlSystem.getLineInfo(lineId);
            if (lineInfo == null) return 0;
            
            int total = 0;
            int count = 0;
            
            for (LineControlSystem.StationInfo station : lineInfo.getStations()) {
                total += stationPassengerCounts.getOrDefault(station.getStationName(), 0);
                count++;
            }
            
            return count > 0 ? (double)total / count : 0;
        }
    }
    
    // 运行图调整类
    private static class ScheduleAdjustment {
        public enum AdjustmentType {
            NO_CHANGE,
            INCREASE_FREQUENCY,
            REDUCE_FREQUENCY,
            ADD_EXPRESS_TRAIN
        }
        
        private final AdjustmentType type;
        private final double originalValue;
        private final double newValue;
        
        public ScheduleAdjustment(AdjustmentType type, double originalValue, double newValue) {
            this.type = type;
            this.originalValue = originalValue;
            this.newValue = newValue;
        }
        
        public AdjustmentType getType() {
            return type;
        }
        
        public double getOriginalValue() {
            return originalValue;
        }
        
        public double getNewValue() {
            return newValue;
        }
    }
    
    // 调度调整记录类
    private static class DispatchAdjustment {
        private final long timestamp;
        private final String lineId;
        private final String adjustmentType;
        private final double originalValue;
        private final double newValue;
        private final double passengerFlowRate;
        
        public DispatchAdjustment(long timestamp, String lineId, String adjustmentType,
                               double originalValue, double newValue, double passengerFlowRate) {
            this.timestamp = timestamp;
            this.lineId = lineId;
            this.adjustmentType = adjustmentType;
            this.originalValue = originalValue;
            this.newValue = newValue;
            this.passengerFlowRate = passengerFlowRate;
        }
    }
    
    // 列车运行状态枚举
    private enum TrainRunningStatus {
        NORMAL,
        DELAYED,
        TOO_FAST,
        MALFUNCTION
    }

    // AI建议系统
    private class AISuggestionSystem {
        private final Random random = new Random();
        
        public String getSuggestion(DispatchSystem dispatchSystem) {
            // 基于当前系统状态生成智能建议
            List<String> availableSuggestions = new ArrayList<>();
            
            // 检查各线路状态
            for (String lineId : lineControlSystem.getAllLines().keySet()) {
                double passengerFlowRate = passengerFlowMonitor.getLinePassengerFlowRate(lineId);
                double density = calculateTrainDensity(lineId, getTrainsOnLine(lineId).size());
                double avgDelay = calculateAverageDelay(lineId);
                
                if (passengerFlowRate > 1.5) {
                    availableSuggestions.add(lineId + "客流过大，建议增开临时列车并实施限流措施。");
                } else if (passengerFlowRate < 0.5) {
                    availableSuggestions.add(lineId + "客流较小，建议减少发车频率以节约资源。");
                }
                
                if (avgDelay > 120) {
                    availableSuggestions.add(lineId + "平均延误超过2分钟，建议调整运行图并增加备用列车。");
                }
                
                if (density > 0.9) {
                    availableSuggestions.add(lineId + "列车密度过高，请注意监控安全间隔。");
                }
            }
            
            // 检查故障列车
            long faultyTrainCount = trains.stream().filter(train -> train.getHealth() < 50).count();
            if (faultyTrainCount > 0) {
                availableSuggestions.add("当前有" + faultyTrainCount + "列列车需要检修，建议尽快安排回厂。");
            }
            
            // 高峰期提醒
            if (isPeakHour()) {
                availableSuggestions.add("当前为高峰期，建议加强关键站点的客流疏导。");
            }
            
            // 如没有特殊情况，返回正常状态信息
            if (availableSuggestions.isEmpty()) {
                return "当前系统运行正常，各线路状态良好，暂无异常情况。";
            }
            
            // 返回一条随机的建议
            return availableSuggestions.get(random.nextInt(availableSuggestions.size()));
        }
    }
}