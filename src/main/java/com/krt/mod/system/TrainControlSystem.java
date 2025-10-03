package com.krt.mod.system;

import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.Entity;
import net.minecraft.text.Text;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Material;
import net.minecraft.block.LeavesBlock;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.util.math.Box;
import com.krt.mod.KRTMod;
import com.krt.mod.block.SignalBlock;
import com.krt.mod.block.ATPSignalBlock;
import com.krt.mod.block.ATPSignalBlockEntity;
import com.krt.mod.block.TrackBlock;
import com.krt.mod.block.SwitchTrackBlock;
import com.krt.mod.block.PlatformBlock;
import com.krt.mod.entity.TrainEntity;
import com.krt.mod.entity.TrainConsist;
import com.krt.mod.entity.TrainCar;
import com.krt.mod.system.PowerSupplySystem;
import com.krt.mod.system.ATS;
import com.krt.mod.system.PassengerInformationSystem;
import com.krt.mod.system.TimetableSystem;

import java.util.List;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

public class TrainControlSystem {
    private static final Map<World, TrainControlSystem> INSTANCES = new HashMap<>();
    private final TrainEntity train;
    private final World world;
    private boolean isAccelerating = false;
    private boolean isBraking = false;
    private double targetSpeed = 0.0;
    private double maxSpeed = 80.0; // 默认最大速度80km/h
    private TrainControlMode controlMode = TrainControlMode.ATO;
    
    // 加速度相关参数（单位：m/s²）
    private static final double MAX_NORMAL_ACCELERATION = 1.2; // 正常最大加速度
    private static final double MAX_COMFORT_ACCELERATION = 0.9; // 舒适模式最大加速度
    private static final double MAX_EMERGENCY_ACCELERATION = 1.5; // 紧急模式最大加速度
    
    // 减速度相关参数（单位：m/s²）
    private static final double MAX_NORMAL_DECELERATION = 1.2; // 正常最大减速度
    private static final double MAX_COMFORT_DECELERATION = 1.0; // 舒适模式最大减速度
    private static final double MAX_EMERGENCY_DECELERATION = 1.5; // 紧急制动减速度
    
    // 纵向加速度变化率（Jerk）相关参数（单位：m/s³）
    private static final double MAX_JERK = 1.5; // 最大纵向加速度变化率
    private static final double COMFORT_JERK = 1.0; // 舒适模式纵向加速度变化率
    
    // 车门控制相关参数
    private static final long MINIMUM_DOOR_OPEN_TIME_MS = 10000; // 最小车门打开时间（10秒）
    private static final long DOOR_CLOSE_WARNING_DELAY_MS = 1500; // 关门警告延迟时间（1.5秒）
    private long currentDwellTimeMs = MINIMUM_DOOR_OPEN_TIME_MS; // 当前停站时间（毫秒）
    
    // 上一次加速度值，用于计算jerk
    private double lastAcceleration = 0.0;
    private double lastDeceleration = 0.0;
    private long lastUpdateTime = System.currentTimeMillis();
    private long lastComfortModeCheckTime = System.currentTimeMillis();
    private long doorOpenStartTime = 0; // 车门开始打开的时间戳
    private String lastStationName = null; // 上一个车站名称
    private double lastLoadFactor = 0.0; // 上一次的负载系数
    private boolean wasInTunnel = false; // 上一次的隧道状态
    private int lastCheckedHour = -1; // 上一次检查的小时

    public TrainControlSystem(TrainEntity train) {
        this.train = train;
        this.world = train.world;
        initialize();
    }
    
    /**
     * 初始化列车控制系统
     */
    private void initialize() {
        // 初始化控制参数和状态
        this.isAccelerating = false;
        this.isBraking = false;
        this.targetSpeed = 0.0;
        this.maxSpeed = 80.0;
        this.controlMode = TrainControlMode.ATO;
        this.lastAcceleration = 0.0;
        this.lastDeceleration = 0.0;
        this.lastUpdateTime = System.currentTimeMillis();
        this.lastComfortModeCheckTime = System.currentTimeMillis();
        this.doorOpenStartTime = 0;
        this.lastStationName = null;
        this.lastLoadFactor = 0.0;
        this.wasInTunnel = false;
        this.lastCheckedHour = -1;
    }

    /**
     * 获取实例（单例模式）
     */
    public static TrainControlSystem getInstance(World world) {
        return INSTANCES.computeIfAbsent(world, w -> new TrainControlSystem(null) {
            @Override
            public void applyEmergencyBrake(String trainId) {
                // 在这个特殊实例中，我们需要找到对应的列车并应用紧急制动
                for (Entity entity : world.getEntitiesByClass(Entity.class, new Box(0, 0, 0, 6000000, 6000000, 6000000), 
                        entity -> entity instanceof TrainEntity && entity.getUuidAsString().equals(trainId))) {
                    ((TrainEntity) entity).getControlSystem().applyEmergencyBrake();
                    break;
                }
            }

            @Override
            public void applyServiceBrake(String trainId) {
                // 在这个特殊实例中，我们需要找到对应的列车并应用常用制动
                for (Entity entity : world.getEntitiesByClass(Entity.class, new Box(0, 0, 0, 6000000, 6000000, 6000000), 
                        entity -> entity instanceof TrainEntity && entity.getUuidAsString().equals(trainId))) {
                    ((TrainEntity) entity).getControlSystem().applyServiceBrake();
                    break;
                }
            }
        });
    }

    /**
     * 应用紧急制动（带trainId参数版本，供外部系统调用）
     */
    public void applyEmergencyBrake(String trainId) {
        // 普通实例的实现（如果这个实例恰好是目标列车的控制系统）
        if (train != null && train.getUuidAsString().equals(trainId)) {
            applyEmergencyBrake();
        }
    }

    /**
     * 应用常用制动（带trainId参数版本，供外部系统调用）
     */
    public void applyServiceBrake(String trainId) {
        // 普通实例的实现（如果这个实例恰好是目标列车的控制系统）
        if (train != null && train.getUuidAsString().equals(trainId)) {
            applyServiceBrake();
        }
    }// 运行ATO系统（自动驾驶）
    public void runATO() {
        if (!controlMode.isAutomatic()) return;

        // 检查列车是否适合运行
        if (!isTrainReadyToRun()) {
            train.applyEmergencyBrake();
            if (train.getDriver() != null) {
                train.getDriver().sendMessage(Text.literal("⚠ 列车状态不适合运行，已触发紧急制动！"), false);
            }
            return;
        }

        // 获取ATP数据
        ATPSignalBlockEntity.ATPData atpData = getATPSignalData();
        
        // 检查ATP数据，如果需要紧急制动，立即触发
        if (atpData.emergencyStop) {
            train.applyEmergencyBrake();
            return;
        }

        // 根据ATP数据设置最大速度
        this.maxSpeed = atpData.maxSpeed;
        
        // 从列车编组获取实际最大速度（考虑车辆状态）
        TrainConsist consist = train.getConsist();
        if (consist != null) {
            double consistMaxSpeed = consist.getMaxSpeed();
            if (consistMaxSpeed < this.maxSpeed) {
                this.maxSpeed = consistMaxSpeed;
            }
        }

        // 特殊模式处理
        if (controlMode == TrainControlMode.ITC) {
            // 执行中间折返模式逻辑
            executeTurnbackOperation();
            return;
        }

        // 检测前方的车站和信号机
        detectObstacles();

        // 检查并更新舒适模式状态
        updateComfortModeStatus();

        // 优化的速度曲线控制
        applyOptimizedSpeedCurve();
        
        // 增强的列车自检系统
        performEnhancedSelfCheck();
    }
    
    // 执行中间折返操作
    private void executeTurnbackOperation() {
        TrainConsist consist = train.getConsist();
        if (consist == null) return;
        
        // 检查是否在车站且已停车
        if (isAtStation() && train.getCurrentSpeed() < 0.1) {
            // 等待乘客下车
            if (System.currentTimeMillis() - doorOpenStartTime < 20000) { // 等待20秒
                return;
            }
            
            // 确保所有车门关闭
            if (!consist.areAllDoorsClosed()) {
                consist.closeAllDoors();
                return;
            }
            
            // 执行折返操作
            train.reverseDirection();
            
            // 更新运行线路
            LineControlSystem lineSystem = LineControlSystem.getInstance(world);
            if (lineSystem != null) {
                String currentLine = consist.getLineId();
                String nextStation = lineSystem.getPreviousStation(currentLine, consist.getNextStation());
                consist.setNextStation(nextStation);
            }
            
            // 模式转换回ATO
            setControlMode(TrainControlMode.ATO);
            
            // 通知司机
            if (train.getDriver() != null) {
                train.getDriver().sendMessage(Text.literal("✅ 中间折返操作完成"), false);
            }
        }
    }
    
    // 检查是否在车站
    private boolean isAtStation() {
        BlockPos trainPos = train.getBlockPos();
        // 简化检查，实际应该查询线路数据库
        return false;
    }
    
    // 应用优化的速度曲线控制
    private void applyOptimizedSpeedCurve() {
        // 获取当前速度和目标速度
        double currentSpeed = train.getCurrentSpeed();
        double targetSpeed = this.targetSpeed;
        
        // 考虑能耗优化的加速度选择
        double optimalAcceleration = calculateOptimalAcceleration(currentSpeed, targetSpeed);
        
        // 应用jerk限制，确保乘坐舒适度
        double jerkLimitedAcceleration = applyJerkLimit(optimalAcceleration);
        
        // 应用加速度/减速度
        if (jerkLimitedAcceleration > 0) {
            // 加速
            applyAcceleration(jerkLimitedAcceleration);
        } else if (jerkLimitedAcceleration < 0) {
            // 减速
            applyBrakes(Math.abs(jerkLimitedAcceleration));
        } else {
            // 保持速度
            maintainSpeed();
        }
    }
    
    // 计算最优加速度（考虑能耗和舒适度）
    private double calculateOptimalAcceleration(double currentSpeed, double targetSpeed) {
        double speedDiff = targetSpeed - currentSpeed;
        
        // 选择适当的加速度曲线
        if (speedDiff > 0) {
            // 加速情况
            if (isComfortMode()) {
                // 舒适模式下的加速曲线
                return Math.min(MAX_COMFORT_ACCELERATION, 
                        MAX_COMFORT_ACCELERATION * (1.0 - Math.pow(currentSpeed / targetSpeed, 2)));
            } else {
                // 正常模式下的加速曲线
                return Math.min(MAX_NORMAL_ACCELERATION, 
                        MAX_NORMAL_ACCELERATION * (1.0 - Math.pow(currentSpeed / targetSpeed, 1.5)));
            }
        } else if (speedDiff < 0) {
            // 减速情况
            double decelerationFactor = Math.abs(speedDiff) / currentSpeed;
            if (isComfortMode()) {
                // 舒适模式下的减速曲线
                return -Math.min(MAX_COMFORT_DECELERATION, 
                        MAX_COMFORT_DECELERATION * decelerationFactor);
            } else {
                // 正常模式下的减速曲线
                return -Math.min(MAX_NORMAL_DECELERATION, 
                        MAX_NORMAL_DECELERATION * decelerationFactor);
            }
        }
        
        return 0.0; // 无需加速或减速
    }
    
    // 应用jerk限制
    private double applyJerkLimit(double targetAcceleration) {
        long currentTime = System.currentTimeMillis();
        double timeDelta = (currentTime - lastUpdateTime) / 1000.0; // 转换为秒
        
        // 计算允许的加速度变化
        double maxAccelerationChange = (isComfortMode() ? COMFORT_JERK : MAX_JERK) * timeDelta;
        
        // 限制加速度变化
        double actualAcceleration = lastAcceleration;
        double accelerationDiff = targetAcceleration - lastAcceleration;
        
        if (Math.abs(accelerationDiff) > maxAccelerationChange) {
            actualAcceleration += Math.signum(accelerationDiff) * maxAccelerationChange;
        } else {
            actualAcceleration = targetAcceleration;
        }
        
        // 更新状态
        lastAcceleration = actualAcceleration;
        lastUpdateTime = currentTime;
        
        return actualAcceleration;
    }
    
    // 增强的列车自检系统
    private void performEnhancedSelfCheck() {
        // 初始化自检结果
        Map<String, String> selfCheckResults = new HashMap<>();
        List<String> errorMessages = new ArrayList<>();
        
        // 检查列车组件状态
        checkTrainComponents(selfCheckResults, errorMessages);
        
        // 检查系统间通信
        checkSystemCommunications(selfCheckResults, errorMessages);
        
        // 检查环境条件
        checkEnvironmentalConditions(selfCheckResults, errorMessages);
        
        // 根据自检结果分级处理
        handleSelfCheckResults(selfCheckResults, errorMessages);
    }
    
    // 检查列车组件状态
    private void checkTrainComponents(Map<String, String> results, List<String> errors) {
        TrainConsist consist = train.getConsist();
        if (consist != null) {
            // 检查每节车厢
            for (int i = 0; i < consist.getCarCount(); i++) {
                TrainCar car = consist.getCar(i);
                if (car != null) {
                    String carId = "car_" + i;
                    String status = car.checkStatus();
                    results.put(carId, status);
                    
                    if (!status.equals("NORMAL")) {
                        errors.add("第" + (i+1) + "节车厢状态异常：" + translateStatus(status));
                    }
                }
            }
            
            // 检查制动系统
            boolean brakeStatus = consist.checkBrakeSystem();
            results.put("brake_system", brakeStatus ? "NORMAL" : "ABNORMAL");
            if (!brakeStatus) {
                errors.add("制动系统异常，建议减速行驶");
            }
            
            // 检查动力系统
            boolean powerStatus = consist.checkPowerSystem();
            results.put("power_system", powerStatus ? "NORMAL" : "ABNORMAL");
            if (!powerStatus) {
                errors.add("动力系统异常，可能影响加速性能");
            }
        }
    }
    
    // 检查系统间通信
    private void checkSystemCommunications(Map<String, String> results, List<String> errors) {
        // 检查ATP通信
        boolean atpCommunication = train.checkATPCommunication();
        results.put("atp_communication", atpCommunication ? "NORMAL" : "ABNORMAL");
        if (!atpCommunication) {
            errors.add("ATP通信中断，切换至ATP监督模式");
        }
        
        // 检查ATS通信
        boolean atsCommunication = checkATSCommunication();
        results.put("ats_communication", atsCommunication ? "NORMAL" : "ABNORMAL");
        if (!atsCommunication && train.getDriver() != null) {
            train.getDriver().sendMessage(Text.literal("📢 ATS通信中断，列车将按预设模式运行"), false);
        }
    }
    
    // 检查环境条件
    private void checkEnvironmentalConditions(Map<String, String> results, List<String> errors) {
        // 检查轨道附着力
        double trackAdhesion = checkTrackAdhesion();
        results.put("track_adhesion", String.format("%.2f", trackAdhesion));
        
        if (trackAdhesion < 0.6) {
            errors.add("轨道附着力低，请注意制动距离增加");
            // 降低最大减速度
            this.MAX_NORMAL_DECELERATION = 0.8;
            this.MAX_COMFORT_DECELERATION = 0.7;
        } else {
            // 恢复正常减速度
            this.MAX_NORMAL_DECELERATION = 1.2;
            this.MAX_COMFORT_DECELERATION = 1.0;
        }
    }
    
    // 处理自检结果
    private void handleSelfCheckResults(Map<String, String> results, List<String> errors) {
        if (errors.isEmpty()) {
            // 所有系统正常
            if (System.currentTimeMillis() - lastComfortModeCheckTime > 30000) { // 每30秒更新一次
                lastComfortModeCheckTime = System.currentTimeMillis();
                updateComfortModeStatus();
            }
            return;
        }
        
        // 根据错误数量和严重程度分级
        int errorLevel = determineErrorLevel(errors);
        
        switch (errorLevel) {
            case 1: // 轻微错误
                // 只通知司机
                for (String error : errors) {
                    if (train.getDriver() != null) {
                        train.getDriver().sendMessage(Text.literal("ℹ " + error), false);
                    }
                }
                break;
            case 2: // 中度错误
                // 通知司机并降低速度
                for (String error : errors) {
                    if (train.getDriver() != null) {
                        train.getDriver().sendMessage(Text.literal("⚠ " + error), false);
                    }
                }
                // 降低最大速度
                this.maxSpeed = Math.max(40.0, this.maxSpeed * 0.7);
                break;
            case 3: // 严重错误
                // 通知司机并准备停车
                for (String error : errors) {
                    if (train.getDriver() != null) {
                        train.getDriver().sendMessage(Text.literal("🚨 " + error), false);
                    }
                }
                // 设置停车目标
                this.targetSpeed = 0.0;
                // 通知ATS系统
                notifyATSError(errorLevel);
                break;
        }
    }
    
    // 判断错误级别
    private int determineErrorLevel(List<String> errors) {
        // 简化实现：根据错误数量和关键词判断
        if (errors.size() >= 5 || errors.stream().anyMatch(e -> e.contains("制动") || e.contains("动力"))) {
            return 3; // 严重错误
        } else if (errors.size() >= 2 || errors.stream().anyMatch(e -> e.contains("通信"))) {
            return 2; // 中度错误
        }
        return 1; // 轻微错误
    }
    
    // 通知ATS系统错误
    private void notifyATSError(int errorLevel) {
        ATS ats = ATS.getInstance(world);
        if (ats != null) {
            ats.reportTrainError(train.getTrainId(), errorLevel);
        }
    }
    
    // 翻译状态码为中文描述
    private String translateStatus(String status) {
        switch (status) {
            case "NORMAL": return "正常";
            case "WARNING": return "警告";
            case "ERROR": return "错误";
            case "CRITICAL": return "严重";
            default: return status;
        }
    }
    
    // 检查ATS通信
    private boolean checkATSCommunication() {
        // 简化实现，实际应该有更复杂的通信检查逻辑
        return true;
    }

    // 获取ATP信号数据
    private ATPSignalBlockEntity.ATPData getATPSignalData() {
        // 查找最近的ATP信号块
        BlockPos trainPos = train.getBlockPos();
        for (BlockPos pos : BlockPos.iterateOutwards(trainPos, 20, 5, 20)) {
            if (world.getBlockState(pos).getBlock() instanceof ATPSignalBlock) {
                BlockEntity blockEntity = world.getBlockEntity(pos);
                if (blockEntity instanceof ATPSignalBlockEntity) {
                    return ((ATPSignalBlockEntity) blockEntity).getATPData();
                }
            }
        }
        // 如果没有找到ATP信号块，返回默认数据
        return new ATPSignalBlockEntity.ATPData();
    }

    // 检测前方障碍物
    private void detectObstacles() {
        // 检测前方信号机状态
        Vec3d forwardPos = train.getPos().add(train.getRotationVector().multiply(20));
        BlockPos checkPos = new BlockPos(forwardPos);

        for (BlockPos pos : BlockPos.iterateOutwards(checkPos, 200, 5, 200)) {
            if (world.getBlockState(pos).getBlock() instanceof SignalBlock) {
                SignalBlock.SignalState signalState = world.getBlockState(pos).get(SignalBlock.SIGNAL_STATE);
                double distance = train.getPos().squaredDistanceTo(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
                double actualDistance = Math.sqrt(distance);
                double currentSpeed = train.getCurrentSpeed();
                double brakingDistance = calculateBrakingDistance(currentSpeed);
                
                if (signalState == SignalBlock.SignalState.RED) {
                    // 前方有红灯，减速停车
                    if (actualDistance < brakingDistance * 1.2) {
                        // 距离不足，需要紧急减速
                        handleEmergencyDeceleration(currentSpeed, actualDistance);
                        if (train.getDriver() != null) {
                            train.getDriver().sendMessage(Text.literal("⚠ 前方红灯，紧急减速！"), false);
                        }
                        return;
                    }
                    
                    if (actualDistance < brakingDistance * 1.5) {
                        setTargetSpeed(0.0);
                        applyBrakes();
                        if (train.getDriver() != null) {
                            train.getDriver().sendMessage(Text.literal("⚠ 前方红灯，准备停车"), false);
                        }
                        return;
                    }
                } else if (signalState == SignalBlock.SignalState.YELLOW) {
                    // 前方有黄灯，减速
                    if (actualDistance < brakingDistance * 2) {
                        setTargetSpeed(maxSpeed * 0.5);
                        return;
                    }
                }
            }
        }

        // 如果前方没有障碍物，加速到最大速度
        setTargetSpeed(maxSpeed);
    }
    
    // 处理紧急减速
    private void handleEmergencyDeceleration(double currentSpeed, double distanceToObstacle) {
        // 在紧急情况下，使用最大允许的减速度和jerk
        lastUpdateTime = System.currentTimeMillis();
        double emergencyDeceleration = MAX_EMERGENCY_DECELERATION;
        
        // 根据剩余距离计算所需减速度（考虑速度单位转换）
        double speedInMs = currentSpeed / 3.6;
        double requiredDeceleration = (speedInMs * speedInMs) / (2 * distanceToObstacle);
        
        // 使用较大的减速度值
        double targetDeceleration = Math.max(emergencyDeceleration, requiredDeceleration);
        
        // 应用紧急减速度
        applyBrakes(targetDeceleration);
        lastDeceleration = targetDeceleration;
        lastAcceleration = 0.0;
        isAccelerating = false;
        isBraking = true;
        
        // 发送紧急通知
        sendEmergencyNotification("紧急情况：前方障碍物，请立即制动！");
    }
    
    // 发送紧急通知
    private void sendEmergencyNotification(String message) {
        // 通过司机消息系统通知司机
        if (train.getDriver() != null) {
            train.getDriver().sendMessage(Text.literal("⚠ " + message), false);
        }
        
        // 通过PIS系统通知乘客
        PassengerInformationSystem.getInstance().addEmergencyMessage(
            message, 
            30000, // 30秒
            true, 
            "emergency_audio_obstacle"
        );
    }

    // 计算制动距离
    private double calculateBrakingDistance(double speed) {
        // 实际制动距离计算，考虑多种因素
        
        // 初始速度转换为m/s
        double speedInMs = speed / 3.6;
        
        // 选择适当的减速度值
        double deceleration = isComfortMode() ? MAX_COMFORT_DECELERATION : MAX_NORMAL_DECELERATION;
        
        // 根据载客量调整减速度
        TrainConsist consist = train.getConsist();
        if (consist != null) {
            double passengerRatio = (double) consist.getTotalPassengers() / (consist.getCarCount() * 60);
            // 满载时，减速度降低10-20%
            if (passengerRatio > 0.8) {
                deceleration *= (1.0 - Math.min(0.2, (passengerRatio - 0.8) * 2));
            }
        }
        
        // 根据轨道附着力调整减速度
        double trackAdhesion = checkTrackAdhesion();
        deceleration *= trackAdhesion;
        
        // 基本制动距离计算公式：距离 = 速度^2 / (2 * 减速度)
        double basicDistance = (speedInMs * speedInMs) / (2 * deceleration);
        
        // 添加反应时间（约1秒）带来的额外距离
        double reactionTime = 1.0; // 秒
        double reactionDistance = speedInMs * reactionTime;
        
        // 总制动距离（转换回km/h单位）
        return (basicDistance + reactionDistance) * 3.6;
    }
    
    // 检查是否应该使用舒适模式
    private boolean isComfortMode() {
        // 实际系统中，舒适模式的判断应该基于时间段、载客量、线路条件等多种因素
        // 1. 检查是否为特殊舒适度模式
        if (isSpecialComfortMode()) {
            return true;
        }
        
        // 2. 检查载客量（低峰期判断）
        TrainConsist consist = train.getConsist();
        if (consist != null) {
            // 载客量小于30%时采用舒适模式
            if (consist.getTotalPassengers() < consist.getCarCount() * 60 * 0.3) {
                return true;
            }
        }
        
        // 3. 检查时间段（高峰/平峰/低峰）
        int hour = new Date().getHours();
        // 假设早晚高峰为7-9点和17-19点
        boolean isPeakHour = (hour >= 7 && hour < 9) || (hour >= 17 && hour < 19);
        if (!isPeakHour) {
            // 非高峰时段优先使用舒适模式
            return true;
        }
        
        // 4. 检查是否在隧道中（隧道中通常使用舒适模式减少噪音）
        if (isInTunnel()) {
            return true;
        }
        
        return false;
    }
    
    // 检查是否需要特殊舒适度模式（针对老年乘客或儿童区域）
    private boolean isSpecialComfortMode() {
        // 实际系统中，这应该基于车站类型、时间段等信息
        // 1. 检查是否在特殊车站（如儿童医院、养老院、学校附近的车站）
        String currentStation = getCurrentStationName();
        if (isSpecialStation(currentStation)) {
            return true;
        }
        
        // 2. 检查时间段（如学校放学时间）
        int hour = new Date().getHours();
        int minute = new Date().getMinutes();
        // 假设学校放学时间为15:30-16:30
        boolean isSchoolDismissalTime = (hour == 15 && minute >= 30) || (hour == 16 && minute < 30);
        if (isSchoolDismissalTime) {
            return true;
        }
        
        // 3. 检查是否有特殊需求乘客请求（通过列车内部系统）
        if (hasSpecialPassengerRequest()) {
            return true;
        }
        
        return false;
    }
    
    /**
     * 检查是否为特殊车站
     */
    private boolean isSpecialStation(String stationName) {
        // 这应该基于车站类型数据库进行判断
        // 目前返回false，实际应用中需要实现
        return false;
    }
    
    /**
     * 检查是否有特殊需求乘客请求
     */
    private boolean hasSpecialPassengerRequest() {
        // 这应该从列车内部系统中获取特殊请求信息
        // 目前返回false，实际应用中需要实现
        return false;
    }
    
    // 检查当前是否在隧道中
    private boolean isInTunnel() {
        // 实际系统中，这应该基于轨道数据
        // 1. 获取列车当前位置
        BlockPos pos = train.getBlockPos();
        
        // 2. 检查列车周围的方块是否为隧道方块
        // 检查上方方块
        for (int y = pos.getY() + 1; y < pos.getY() + 5; y++) {
            BlockState stateAbove = world.getBlockState(new BlockPos(pos.getX(), y, pos.getZ()));
            if (isTunnelBlock(stateAbove.getBlock())) {
                // 上方有隧道方块，认为在隧道中
                return true;
            }
        }
        
        // 3. 检查轨道数据中是否标记为隧道区域
        return isTrackSectionInTunnel(pos);
    }
    
    /**
     * 检查方块是否为隧道方块
     */
    private boolean isTunnelBlock(Block block) {
        // 这应该根据实际的隧道方块类型进行判断
        // 目前返回false，实际应用中需要实现
        return false;
    }
    
    /**
     * 检查轨道区段是否在隧道中
     */
    private boolean isTrackSectionInTunnel(BlockPos pos) {
        // 这应该从轨道数据库或轨道数据系统中查询
        // 目前返回false，实际应用中需要实现
        return false;
    }

    // 调整速度
    public void adjustSpeed() {
        double currentSpeed = train.getCurrentSpeed();
        double speedDifference = targetSpeed - currentSpeed;

        // 检查列车编组状态，如果有故障，降低最大速度
        TrainConsist consist = train.getConsist();
        if (consist != null && !consist.getErrorCars().isEmpty()) {
            // 有故障车辆时，降低最大速度
            this.maxSpeed = Math.min(maxSpeed, 60.0);
        }

        // 根据车辆负载调整加速度性能
        if (consist != null && consist.getTotalPassengers() > consist.getCarCount() * 60 * 0.8) {
            // 满载时，加速度性能下降20%
            if (Math.abs(speedDifference) < 0.8) {
                isAccelerating = false;
                isBraking = false;
                return;
            }
        }

        // 计算时间差（毫秒）
        long currentTime = System.currentTimeMillis();
        double deltaTime = (currentTime - lastUpdateTime) / 1000.0; // 转换为秒
        lastUpdateTime = currentTime;
        
        // 检查是否需要停车
        if (targetSpeed < 0.1 && currentSpeed < 5.0) {
            // 停车阶段特殊处理
            handleStoppingPhase(currentSpeed, deltaTime);
            return;
        }

        if (Math.abs(speedDifference) < 0.5) {
            // 速度已接近目标，平滑停止加速或减速
            if (isAccelerating && lastAcceleration > 0.0) {
                double actualAcceleration = applyJerkControl(lastAcceleration, 0.0, deltaTime);
                applyAcceleration(actualAcceleration);
                lastAcceleration = actualAcceleration;
                if (Math.abs(actualAcceleration) < 0.1) {
                    isAccelerating = false;
                }
            } else if (isBraking && lastDeceleration > 0.0) {
                double actualDeceleration = applyJerkControl(lastDeceleration, 0.0, deltaTime);
                applyBrakes(actualDeceleration);
                lastDeceleration = actualDeceleration;
                if (Math.abs(actualDeceleration) < 0.1) {
                    isBraking = false;
                }
            } else {
                // 速度已经接近目标速度，保持当前速度
                isAccelerating = false;
                isBraking = false;
            }
        } else if (speedDifference > 0) {
            // 需要加速
            double targetAcceleration = calculateTargetAcceleration(speedDifference);
            // 应用jerk控制，平滑加速度变化
            double actualAcceleration = applyJerkControl(lastAcceleration, targetAcceleration, deltaTime);
            applyAcceleration(actualAcceleration);
            lastAcceleration = actualAcceleration;
            lastDeceleration = 0.0;
            isAccelerating = true;
            isBraking = false;
        } else {
            // 需要减速
            double targetDeceleration = calculateTargetDeceleration(speedDifference);
            // 应用jerk控制，平滑减速度变化
            double actualDeceleration = applyJerkControl(lastDeceleration, targetDeceleration, deltaTime);
            applyBrakes(actualDeceleration);
            lastDeceleration = actualDeceleration;
            lastAcceleration = 0.0;
            isAccelerating = false;
            isBraking = true;
        }
    }
    
    // 更新舒适模式状态
    private void updateComfortModeStatus() {
        // 实际系统中，这应该基于时间、线路、车站类型等信息
        // 1. 检查时间是否发生变化（如进入或离开高峰时段）
        checkTimeBasedComfortModeChange();
        
        // 2. 检查车站是否发生变化
        String currentStation = getCurrentStationName();
        if (currentStation != null && !currentStation.equals(lastStationName)) {
            lastStationName = currentStation;
            // 车站变化时重新评估舒适模式
            reevaluateComfortModeParameters();
        }
        
        // 3. 检查载客量变化是否超过阈值
        TrainConsist consist = train.getConsist();
        if (consist != null) {
            double currentLoadFactor = (double) consist.getTotalPassengers() / (consist.getCarCount() * 60);
            if (Math.abs(currentLoadFactor - lastLoadFactor) > 0.1) { // 负载变化超过10%
                lastLoadFactor = currentLoadFactor;
                // 负载变化显著时重新评估舒适模式
                reevaluateComfortModeParameters();
            }
        }
        
        // 4. 检查是否进入或离开隧道
        boolean isCurrentlyInTunnel = isInTunnel();
        if (isCurrentlyInTunnel != wasInTunnel) {
            wasInTunnel = isCurrentlyInTunnel;
            // 隧道状态变化时重新评估舒适模式
            reevaluateComfortModeParameters();
        }
    }
    
    /**
     * 检查基于时间的舒适模式变化
     */
    private void checkTimeBasedComfortModeChange() {
        int currentHour = new Date().getHours();
        if (currentHour != lastCheckedHour) {
            lastCheckedHour = currentHour;
            // 整点时重新评估舒适模式
            reevaluateComfortModeParameters();
        }
    }
    
    /**
     * 重新评估舒适模式参数
     */
    private void reevaluateComfortModeParameters() {
        // 这里可以根据各种条件调整舒适模式相关参数
        // 例如根据时间段、车站类型、载客量等调整加速度和减速度限制
    }

    // 处理停车阶段
    private void handleStoppingPhase(double currentSpeed, double deltaTime) {
        // 停车阶段需要非常平滑的减速，以避免乘客不适
        if (currentSpeed > 1.0) {
            // 高速阶段
            double targetDeceleration = 0.2; // 较小的减速度
            double actualDeceleration = applyJerkControl(lastDeceleration, targetDeceleration, deltaTime);
            applyBrakes(actualDeceleration);
            lastDeceleration = actualDeceleration;
            lastAcceleration = 0.0;
            isAccelerating = false;
            isBraking = true;
        } else if (currentSpeed > 0.1) {
            // 低速阶段，使用更小的减速度
            double targetDeceleration = 0.1;
            double actualDeceleration = applyJerkControl(lastDeceleration, targetDeceleration, deltaTime);
            applyBrakes(actualDeceleration);
            lastDeceleration = actualDeceleration;
            lastAcceleration = 0.0;
            isAccelerating = false;
            isBraking = true;
        } else {
            // 几乎停止，完全释放制动
            applyBrakes(0.0);
            isAccelerating = false;
            isBraking = false;
            lastAcceleration = 0.0;
            lastDeceleration = 0.0;
            // 确保列车完全停止
            train.setVelocity(Vec3d.ZERO);
        }
    }

    /**
     * 应用紧急制动（实例方法，不带参数）
     */
    public void applyEmergencyBrake() {
        if (train != null) {
            train.applyEmergencyBrake();
        }
    }

    /**
     * 应用常用制动（实例方法，不带参数）
     */
    public void applyServiceBrake() {
        if (train != null && controlMode == TrainControlMode.ATO) {
            // 设置目标速度为0
            setTargetSpeed(0.0);
            // 应用常用制动
            applyBrakes(isComfortMode() ? MAX_COMFORT_DECELERATION : MAX_NORMAL_DECELERATION);
        }
    }
    
    // 计算目标加速度
    private double calculateTargetAcceleration(double speedDiff) {
        // 根据速度差和当前状态确定目标加速度
        double maxAcceleration = isComfortMode() ? MAX_COMFORT_ACCELERATION : MAX_NORMAL_ACCELERATION;
        
        // 当接近目标速度时，逐渐减小加速度
        double ratio = Math.min(1.0, Math.abs(speedDiff) / 10.0);
        return maxAcceleration * ratio;
    }
    
    // 计算目标减速度
    private double calculateTargetDeceleration(double speedDiff) {
        // 根据速度差和当前状态确定目标减速度
        double maxDeceleration = isComfortMode() ? MAX_COMFORT_DECELERATION : MAX_NORMAL_DECELERATION;
        
        // 当接近目标速度时，逐渐减小减速度
        double ratio = Math.min(1.0, Math.abs(speedDiff) / 10.0);
        return maxDeceleration * ratio;
    }
    
    // 应用jerk控制，限制加速度变化率
    private double applyJerkControl(double currentValue, double targetValue, double deltaTime) {
        double maxJerk = isComfortMode() ? COMFORT_JERK : MAX_JERK;
        double maxChange = maxJerk * deltaTime;
        
        if (targetValue > currentValue) {
            // 增加加速度/减速度
            return Math.min(targetValue, currentValue + maxChange);
        } else {
            // 减小加速度/减速度
            return Math.max(targetValue, currentValue - maxChange);
        }
    }

    /**
     * 获取列车编组信息
     */
    public String getConsistInfo() {
        TrainConsist consist = train.getConsist();
        if (consist == null) {
            return "无列车编组信息";
        }
        
        StringBuilder info = new StringBuilder();
        info.append("列车编组信息：\n");
        info.append("- 总车辆数：").append(consist.getCarCount()).append("\n");
        info.append("- 总长度：").append(String.format("%.1f", consist.getCarCount() * 20.0)).append("米\n");
        info.append("- 总重量：").append(String.format("%.1f", consist.getTotalWeight())).append("吨\n");
        info.append("- 乘客数量：").append(consist.getTotalPassengers()).append("/").append(consist.getCarCount() * 60).append("\n");
        info.append("- 最大速度：").append(String.format("%.1f", consist.getMaxSpeed())).append("km/h\n");
        
        if (!consist.getErrorCars().isEmpty()) {
            info.append("⚠ 警告：列车存在故障车辆！\n");
        }
        
        return info.toString();
    }

    // 应用加速度
    private void applyAcceleration(double targetAcceleration) {
        // 在手动模式下记录加速操作
        if (controlMode == TrainControlMode.MANUAL) {
            recordDrivingAction("power", 0.5); // 记录一个中等的动力值
        }
        
        double currentSpeed = train.getCurrentSpeed();
        double accelerationFactor = targetAcceleration;
        
        // 检查是否处于启动阶段
        if (currentSpeed < 15.0 && isAccelerating) {
            // 启动阶段特殊处理，确保平滑加速
            accelerationFactor = handleStartupAcceleration(currentSpeed);
        } else {
            // 根据当前速度调整动力系数（速度越高，动力系数越小）
            if (currentSpeed > maxSpeed * 0.7) {
                accelerationFactor *= 0.5; // 高速时动力系数减半
            }
            
            // 检查轨道附着力（模拟不同轨道条件）
            double trackAdhesion = checkTrackAdhesion();
            accelerationFactor *= trackAdhesion;
        }
        
        // 检查特殊舒适度模式
        if (isSpecialComfortMode()) {
            // 为老年或儿童区域提供更柔和的加速度
            accelerationFactor *= 0.6; // 降低40%的加速度
        }
        
        // 检查是否在隧道中
        if (isInTunnel()) {
            // 隧道内空气阻力较大，适当降低加速度
            accelerationFactor *= 0.9;
        }
        
        // 如果目标速度为负，表示需要后退
        // 注意：TrainEntity类不直接支持设置倒车模式，
        // 方向控制会在applyAcceleration方法中通过调整速度向量来实现
        
        // 通过列车编组应用动力
        TrainConsist consist = train.getConsist();
        if (consist != null) {
            consist.applyPower(accelerationFactor);
        }
    }
    
    // 处理启动阶段的加速度
    private double handleStartupAcceleration(double currentSpeed) {
        // 启动阶段，根据速度分阶段调整加速度
        double startupAcceleration = isComfortMode() ? 0.5 : 0.7;
        
        if (currentSpeed < 5.0) {
            // 初始阶段，较小的加速度
            startupAcceleration = isComfortMode() ? 0.3 : 0.5;
        } else if (currentSpeed < 10.0) {
            // 加速阶段，逐渐增加加速度
            startupAcceleration = isComfortMode() ? 0.5 : 0.8;
        }
        
        // 考虑轨道坡度
        double trackGrade = getCurrentTrackGrade();
        if (trackGrade > 0.02) { // 上坡路段
            startupAcceleration *= (1.0 + trackGrade * 10.0); // 增加加速度
        }
        
        return Math.min(startupAcceleration, isComfortMode() ? MAX_COMFORT_ACCELERATION : MAX_NORMAL_ACCELERATION);
    }
    
    // 旧版applyAcceleration方法，保持向后兼容
    private void applyAcceleration() {
        applyAcceleration(isComfortMode() ? MAX_COMFORT_ACCELERATION : MAX_NORMAL_ACCELERATION);
    }

    // 应用制动
    private void applyBrakes(double targetDeceleration) {
        // 在手动模式下记录制动操作
        if (controlMode == TrainControlMode.MANUAL) {
            recordDrivingAction("brake", 0.3); // 记录常用制动值
        }
        
        double brakeFactor = targetDeceleration;
        
        // 紧急情况下使用紧急制动
        if (train.isEmergencyBraking()) {
            // 使用最大紧急减速度
            brakeFactor = MAX_EMERGENCY_DECELERATION;
            train.applyEmergencyBrake();
            return;
        }
        
        // 检查轨道附着力
        double trackAdhesion = checkTrackAdhesion();
        brakeFactor *= trackAdhesion;
        
        // 考虑轨道坡度对制动力的影响
        double trackGrade = getCurrentTrackGrade();
        if (trackGrade < -0.02) { // 下坡路段
            brakeFactor *= (1.0 + Math.abs(trackGrade) * 5.0); // 增加制动力
        }
        
        // 通过列车编组应用制动
        TrainConsist consist = train.getConsist();
        if (consist != null) {
            consist.applyBrake(brakeFactor);
        }
    }
    
    // 获取当前轨道坡度
    private double getCurrentTrackGrade() {
        // 实际系统中，这应该从轨道数据中获取
        // 1. 获取列车当前位置下方的轨道方块
        BlockPos pos = train.getBlockPos().down();
        BlockState state = world.getBlockState(pos);
        
        // 2. 检查方块是否为轨道方块
        // 注意：TrackBlock和SwitchTrackBlock类都不支持getTrackGrade方法，
        // 暂时返回0.0表示没有坡度
        if (state.getBlock() instanceof TrackBlock || state.getBlock() instanceof SwitchTrackBlock) {
            return 0.0; // 默认没有坡度
        }
        
        // 如果下方没有轨道方块，检查周围是否有轨道
        // 搜索周围1格范围内的轨道方块
        for (int x = pos.getX() - 1; x <= pos.getX() + 1; x++) {
            for (int z = pos.getZ() - 1; z <= pos.getZ() + 1; z++) {
                BlockPos nearbyPos = new BlockPos(x, pos.getY(), z);
                BlockState nearbyState = world.getBlockState(nearbyPos);
                if (nearbyState.getBlock() instanceof TrackBlock) {
                    // 注意：TrackBlock类不支持getTrackGrade方法，暂时返回0.0表示没有坡度
                    return 0.0; // 默认没有坡度
                }
            }
        }
        
        // 如果没有找到轨道，返回0表示平道
        return 0.0;
    }
    
    // 旧版applyBrakes方法，保持向后兼容
    private void applyBrakes() {
        applyBrakes(isComfortMode() ? MAX_COMFORT_DECELERATION : MAX_NORMAL_DECELERATION);
    }
    
    // 检查轨道附着力
    private double checkTrackAdhesion() {
        // 实际系统中，这应该基于轨道状态、天气、列车速度等因素计算
        // 基础附着力系数（干燥轨道）
        double baseAdhesion = 0.35;
        
        // 1. 考虑天气因素
        if (world.isRaining() || world.isThundering()) {
            // 雨天或雷暴天气降低附着力
            baseAdhesion *= 0.85;
        }
        
        // 2. 考虑轨道状态
        // 检查轨道清洁度
        BlockPos pos = train.getBlockPos().down();
        BlockState state = world.getBlockState(pos);
        
        // 检查轨道是否被污染
        if (isTrackContaminated(world, pos, state)) {
            // 污染的轨道降低附着力
            baseAdhesion *= 0.75;
        }
        
        // 3. 考虑列车速度
        double speed = train.getCurrentSpeed();
        if (speed > 2.0) { // 速度大于2格/秒
            // 速度增加时，附着力略有下降
            baseAdhesion *= 1.0 - (speed - 2.0) * 0.005;
        }
        
        // 4. 温度影响：暂时移除，因为新版API中getBiome返回类型已更改
        // 确保附着力系数在合理范围内
        return Math.max(0.1, Math.min(0.45, baseAdhesion));
    }
    
    /**
     * 检查轨道是否被污染
     */
    private boolean isTrackContaminated(World world, BlockPos pos, BlockState state) {
        // 实际系统中，这应该检查轨道表面是否有油污、落叶等污染物
        // 简单实现：检查轨道周围是否有特定方块或实体
        
        // 检查周围是否有液体方块
        for (int yOffset = -1; yOffset <= 1; yOffset++) {
            BlockPos checkPos = pos.up(yOffset);
            BlockState checkState = world.getBlockState(checkPos);
            if (checkState.getMaterial() == Material.WATER || checkState.getMaterial() == Material.LAVA) {
                return true;
            }
        }
        
        // 检查周围是否有特定污染物方块（如树叶）
        for (int x = pos.getX() - 1; x <= pos.getX() + 1; x++) {
            for (int y = pos.getY() - 1; y <= pos.getY() + 1; y++) {
                for (int z = pos.getZ() - 1; z <= pos.getZ() + 1; z++) {
                    BlockPos checkPos = new BlockPos(x, y, z);
                    BlockState checkState = world.getBlockState(checkPos);
                    if (checkState.getBlock() instanceof LeavesBlock) {
                        return true;
                    }
                }
            }
        }
        
        return false;
    }

    // 减速（用于信号系统提示减速时）
    public void slowDown() {
        if (controlMode == TrainControlMode.ATO) {
            double currentSpeed = train.getCurrentSpeed();
            double targetSpeed = maxSpeed * 0.7;
            
            if (currentSpeed > targetSpeed) {
                // 计算速度差
                double speedDifference = currentSpeed - targetSpeed;
                
                // 1. 考虑舒适模式限制
                double maxDeceleration = MAX_NORMAL_DECELERATION;
                if (isComfortMode()) {
                    maxDeceleration = MAX_COMFORT_DECELERATION;
                    if (isSpecialComfortMode()) {
                        maxDeceleration *= 0.8; // 特殊舒适模式下减速度更小
                    }
                }
                
                // 2. 考虑轨道附着力限制
                double trackAdhesion = checkTrackAdhesion();
                double adhesionLimitedDeceleration = maxDeceleration * trackAdhesion;
                maxDeceleration = Math.min(maxDeceleration, adhesionLimitedDeceleration);
                
                // 3. 考虑轨道坡度影响
                double trackGrade = getCurrentTrackGrade();
                if (trackGrade < -0.02) { // 下坡路段
                    maxDeceleration *= (1.0 + Math.abs(trackGrade) * 3.0); // 增加制动力
                } else if (trackGrade > 0.02) { // 上坡路段
                    maxDeceleration = Math.max(0.1, maxDeceleration * 0.8); // 减小制动力
                }
                
                // 4. 根据速度差计算合适的减速度
                double requiredDeceleration;
                if (speedDifference > 10.0) {
                    // 速度差大时使用较大减速度
                    requiredDeceleration = maxDeceleration * 0.9;
                } else if (speedDifference > 5.0) {
                    // 速度差中等时使用中等减速度
                    requiredDeceleration = maxDeceleration * 0.6;
                } else {
                    // 接近目标速度时使用较小减速度
                    requiredDeceleration = maxDeceleration * 0.3;
                }
                
                // 确保减速度不为负
                requiredDeceleration = Math.max(0.05, requiredDeceleration);
                
                // 应用制动
                applyBrakes(requiredDeceleration);
                
                // 设置目标速度
                setTargetSpeed(targetSpeed);
            }
        }
    }

    /**
     * 手动应用动力（由玩家操作触发）
     */
    public void applyManualPower(double powerLevel) {
        if (controlMode == TrainControlMode.MANUAL) {
            // 记录手动动力操作
            recordDrivingAction("power", powerLevel);
            
            // 1. 限制功率百分比范围
            powerLevel = Math.max(0.0, Math.min(1.0, powerLevel));
            
            // 2. 考虑轨道附着力限制
            double adhesionFactor = checkTrackAdhesion();
            double maxUsablePowerLevel = adhesionFactor * 1.1; // 考虑安全余量
            powerLevel = Math.min(powerLevel, maxUsablePowerLevel);
            
            // 3. 考虑轨道坡度影响
            double trackGrade = getCurrentTrackGrade();
            if (trackGrade > 0.02) { // 上坡路段
                // 上坡需要额外功率，调整功率百分比
                powerLevel = Math.min(1.0, powerLevel * (1.0 + trackGrade * 50.0));
            }
            
            // 4. 考虑当前速度影响
            double currentSpeed = train.getCurrentSpeed();
            if (currentSpeed > 0.5 * maxSpeed) {
                // 高速时由于空气阻力增加，需要更大的功率来维持加速
                powerLevel = Math.min(1.0, powerLevel * (1.0 + (currentSpeed / maxSpeed) * 0.5));
            }
            
            // 5. 舒适模式下平滑功率变化
            if (isComfortMode()) {
                // 计算功率变化率限制
                long currentTime = System.currentTimeMillis();
                double deltaTime = (currentTime - lastUpdateTime) / 1000.0;
                double maxPowerChange = 0.3 * deltaTime; // 每秒最大变化30%
                
                // 应用平滑限制后的功率
                // 通过列车编组应用动力
                TrainConsist consist = train.getConsist();
                if (consist != null) {
                    consist.applyPower(powerLevel);
                }
            } else {
                // 非舒适模式下直接应用功率
                // 通过列车编组应用动力
                TrainConsist consist = train.getConsist();
                if (consist != null) {
                    consist.applyPower(powerLevel);
                }
            }
        }
    }
    
    /**
     * 手动应用制动（由玩家操作触发）
     */
    public void applyManualBrake(double brakeLevel) {
        if (controlMode == TrainControlMode.MANUAL) {
            // 记录手动制动操作
            recordDrivingAction("brake", brakeLevel);
            
            // 1. 限制制动百分比范围
            brakeLevel = Math.max(0.0, Math.min(1.0, brakeLevel));
            
            // 2. 考虑轨道附着力限制
            double trackAdhesion = checkTrackAdhesion();
            double maxUsableBrakeLevel = trackAdhesion * 1.0; // 制动通常不能超过附着力限制
            brakeLevel = Math.min(brakeLevel, maxUsableBrakeLevel);
            
            // 3. 考虑轨道坡度影响
            double trackGrade = getCurrentTrackGrade();
            if (trackGrade < -0.02) { // 下坡路段
                // 下坡需要更大的制动力
                brakeLevel = Math.min(1.0, brakeLevel * (1.0 + Math.abs(trackGrade) * 40.0));
            } else if (trackGrade > 0.02) { // 上坡路段
                // 上坡时自然减速，可适当减小制动力
                brakeLevel = Math.max(0.0, brakeLevel * (1.0 - trackGrade * 30.0));
            }
            
            // 4. 考虑当前速度影响
            double currentSpeed = train.getCurrentSpeed();
            if (currentSpeed < 0.5) {
                // 低速时减小制动力以避免突然停车
                brakeLevel *= 0.7;
            }
            
            // 通过列车编组应用制动
            TrainConsist consist = train.getConsist();
            if (consist != null) {
                consist.applyBrake(brakeLevel);
            }
        }
    }
    
    /**
     * 手动开门（由玩家操作触发）
     */
    public void manualOpenDoors() {
        if (controlMode == TrainControlMode.MANUAL && train.getCurrentSpeed() < 1) {
            // 记录手动开门操作
            recordDrivingAction("open_doors", 0);
            train.openAllDoors();
            
            // 通过PIS系统通知乘客（暂时移除，因为notifyPassengers方法不存在）
        }
    }
    
    /**
     * 手动关门（由玩家操作触发）
     */
    public void manualCloseDoors() {
        if (controlMode == TrainControlMode.MANUAL) {
            // 记录手动关门操作
            recordDrivingAction("close_doors", 0);
            train.closeAllDoors();
            
            // 通过PIS系统提醒乘客准备关门（暂时移除，因为notifyPassengers方法不存在）
        }
    }
    
    /**
     * 处理紧急情况
     */
    public void handleEmergency(String emergencyType, String message) {
        // 触发紧急制动
        train.applyEmergencyBrake();
        
        // 通过PIS系统发布紧急信息
        PassengerInformationSystem.getInstance().addEmergencyMessage(
            message, 
            30000, // 30秒
            true, 
            "emergency_audio_" + emergencyType
        );
    }
    
    /**
 * 启动列车（实例方法，不带参数）
 */
public void startTrain() {
        if (controlMode == TrainControlMode.ATO && train.getCurrentSpeed() == 0 && !train.areAllDoorsClosed()) {
        // 记录启动操作
        recordDrivingAction("start", 0);
        
        // 1. 执行启动前安全检查
        if (!isTrainReadyToRun()) {
            if (train.getDriver() != null) {
                train.getDriver().sendMessage(Text.literal("⚠ 安全检查失败，无法启动列车！"), false);
            }
            return;
        }
        
        // 2. 检查车门状态
        if (!train.areAllDoorsClosed()) {
            // 车门未关闭，自动尝试关闭车门
            if (controlMode == TrainControlMode.ATO) {
                closeDoors(train.getTrainId());
            }
        }
        
        // 3. 设置列车控制参数
        setInitialControlParameters();
        
        // 4. 启动平滑启动过程
        applySmoothStartup();
        
        // 5. 通过PIS系统通知乘客列车即将启动
        // 注意：PassengerInformationSystem类中没有notifyPassengers方法，已移除该调用
    }
}

/**
 * 设置初始控制参数
 */
private void setInitialControlParameters() {
    // 根据线路和负载设置初始参数
    TrainConsist consist = train.getConsist();
    if (consist != null) {
        // 每节车厢最大容量假设为60人（基于getConsistInfo方法中的实现）
        double loadFactor = (double) consist.getTotalPassengers() / (consist.getCarCount() * 60);
        if (loadFactor > 0.8) { // 高负载
            // 高负载时降低初始加速度
            this.maxSpeed = Math.min(maxSpeed, 70.0);
        }
    }
    
    // 初始目标速度设为较低值，确保平滑启动
    setTargetSpeed(10.0); // 初始目标速度10km/h
}

/**
 * 应用平滑启动
 */
private void applySmoothStartup() {
    // 设置启动阶段标志
    isAccelerating = true;
    isBraking = false;
    lastAcceleration = 0.0;
    lastDeceleration = 0.0;
    lastUpdateTime = System.currentTimeMillis();
    
    // 启动阶段使用较小的加速度
    if (isComfortMode()) {
        // 舒适模式下启动更平滑
        lastAcceleration = 0.3; // 0.3 m/s²
    } else {
        lastAcceleration = 0.5; // 0.5 m/s²
    }
}
    
    /**
     * 启动列车（静态方法，由ATO系统调用）
     */
    public void startTrain(String trainId) {
        // 这里应该根据trainId找到对应的列车并启动它
        // 简化实现：如果当前系统的列车ID匹配，则启动
        if (train != null && train.getTrainId().equals(trainId)) {
            startTrain(); // 调用实例方法
        }
    }
    
    /**
     * 检查列车是否在车站内
     */
    private boolean isAtStation() {
        if (train == null || world == null) {
            return false;
        }
        
        BlockPos pos = train.getBlockPos();
        // 检查周围3格范围内是否有站台方块
        for (int x = pos.getX() - 3; x <= pos.getX() + 3; x++) {
            for (int z = pos.getZ() - 3; z <= pos.getZ() + 3; z++) {
                BlockPos checkPos = new BlockPos(x, pos.getY() - 1, z);
                BlockState state = world.getBlockState(checkPos);
                if (state.getBlock() instanceof PlatformBlock) {
                    return true;
                }
            }
        }
        
        return false;
    }
    
    /**
     * 获取当前车站名称
     */
    private String getCurrentStationName() {
        if (train == null || world == null) {
            return null;
        }
        
        // 注意：StationSignBlockEntity类不存在，直接返回默认车站名称
        return "当前车站";
    }
    
    /**
     * 确定是否应该打开左侧车门
     */
    private boolean shouldOpenLeftDoors(String stationName) {
        // 实际系统中，这应该根据车站布局和列车行驶方向确定
        // 简单实现：根据车站名称和列车ID决定
        if (stationName == null || train == null) {
            return false;
        }
        
        // 检查是否为双侧开门车站
        if (isDoubleSidedStation(stationName)) {
            return true;
        }
        
        // 根据车站名称和列车ID决定
        return stationName.endsWith("东站") || stationName.endsWith("西站") || 
               (stationName.length() > 0 && stationName.charAt(0) % 2 == 0);
    }
    
    /**
     * 确定是否应该打开右侧车门
     */
    private boolean shouldOpenRightDoors(String stationName) {
        // 实际系统中，这应该根据车站布局和列车行驶方向确定
        // 简单实现：根据车站名称和列车ID决定
        if (stationName == null || train == null) {
            return false;
        }
        
        // 检查是否为双侧开门车站
        if (isDoubleSidedStation(stationName)) {
            return true;
        }
        
        // 根据车站名称和列车ID决定
        return stationName.endsWith("南站") || stationName.endsWith("北站") || 
               (stationName.length() > 0 && stationName.charAt(0) % 2 == 1);
    }
    
    /**
     * 检查是否为双侧开门车站
     */
    private boolean isDoubleSidedStation(String stationName) {
        // 实际系统中，这应该查询车站数据库
        // 简单实现：特定车站名称为双侧开门
        if (stationName == null) {
            return false;
        }
        
        return stationName.contains("换乘") || stationName.contains("枢纽") || 
               stationName.equals("中央车站") || stationName.equals("终点站");
    }
    
    /**
     * 获取车门开启方向文本
     */
    private String getDoorOpeningSideText(String stationName) {
        if (stationName == null || train == null) {
            return "车门即将打开";
        }
        
        boolean leftOpen = shouldOpenLeftDoors(stationName);
        boolean rightOpen = shouldOpenRightDoors(stationName);
        
        if (leftOpen && rightOpen) {
            return "双侧车门即将打开";
        } else if (leftOpen) {
            return "左侧车门即将打开";
        } else if (rightOpen) {
            return "右侧车门即将打开";
        } else {
            return "车门即将打开";
        }
    }
    
    /**
     * 检查车门是否有障碍物
     */
    private boolean hasDoorObstructions() {
        // 实际系统中，这应该通过传感器检测
        // 简单实现：随机模拟，大部分情况下没有障碍物
        return new Random().nextDouble() < 0.05; // 5%概率有障碍物
    }
    
    /**
     * 列车是否准备运行
     */
    private boolean isTrainReadyToRun() {
        if (train == null) {
            return false;
        }
        
        // 检查制动系统（替换不存在的isBrakeSystemReady方法）
        if (train.getConsist() == null || train.getHealth() < 50) {
            return false;
        }
        
        // 检查动力系统（替换不存在的isPowerSystemReady方法）
        if (train.getPowerSupplySystem() == null || train.getConsist() == null || train.getHealth() < 50) {
            return false;
        }
        
        // 检查车门状态
        if (!train.areAllDoorsClosed()) {
            return false;
        }
        
        // 检查信号系统
        if (!isSignalClear()) {
            return false;
        }
        
        return true;
    }
    
    /**
     * 检查信号是否允许通行
     */
    private boolean isSignalClear() {
        // 实际系统中，这应该查询信号系统
        // 简单实现：默认返回true
        return true;
    }
    
    /**
     * 打开车门（由ATO系统调用）
     */
    public void openDoors(String trainId) {
        // 这里应该根据trainId找到对应的列车并打开车门
        if (train != null && train.getTrainId().equals(trainId)) {
            // 1. 检查列车速度是否为0
            if (train.getCurrentSpeed() >= 0.5) {
                // 速度不为0，不能打开车门
                if (train.getDriver() != null) {
                    train.getDriver().sendMessage(Text.literal("⚠ 列车未完全停稳，无法开门！"), false);
                }
                return;
            }
            
            // 2. 检查是否在车站内
            if (!isAtStation()) {
                // 不在车站内，不能打开车门
                if (train.getDriver() != null) {
                    train.getDriver().sendMessage(Text.literal("⚠ 列车不在车站内，无法开门！"), false);
                }
                return;
            }
            
            // 3. 检查当前车站信息
            String stationName = getCurrentStationName();
            if (stationName == null) {
                if (train.getDriver() != null) {
                    train.getDriver().sendMessage(Text.literal("⚠ 无法识别当前车站，无法开门！"), false);
                }
                return;
            }
            
            // 4. 确定开门方向
            boolean leftOpen = shouldOpenLeftDoors(stationName);
            boolean rightOpen = shouldOpenRightDoors(stationName);
            
            if (!leftOpen && !rightOpen) {
                if (train.getDriver() != null) {
                    train.getDriver().sendMessage(Text.literal("⚠ 无法确定开门方向，无法开门！"), false);
                }
                return;
            }
            
            // 5. 执行开门操作
            if (leftOpen && rightOpen) {
                train.openAllDoors();
            } else if (leftOpen) {
                train.openAllDoors();
            } else if (rightOpen) {
                train.openAllDoors();
            }
            
            // 6. 记录开门时间，用于自动关闭
            doorOpenStartTime = System.currentTimeMillis();
            
            // 7. 获取并设置当前时刻表的停站时间
            try {
                String lineId = train.getCurrentLine();
                if (lineId != null && !lineId.isEmpty()) {
                    // 从时刻表系统获取停站时间（秒）
                    int dwellTimeSeconds = TimetableSystem.getInstance(world).getCurrentDwellTime(lineId);
                    // 转换为毫秒
                    currentDwellTimeMs = dwellTimeSeconds * 1000L;
                    // 确保不小于最小停站时间
                    currentDwellTimeMs = Math.max(currentDwellTimeMs, MINIMUM_DOOR_OPEN_TIME_MS);
                    KRTMod.LOGGER.info("列车 {} 在线路 {} 的停站时间设置为 {} 秒", 
                                     train.getTrainId(), lineId, dwellTimeSeconds);
                }
            } catch (Exception e) {
                KRTMod.LOGGER.error("获取时刻表停站时间失败: {}", e.getMessage());
                currentDwellTimeMs = MINIMUM_DOOR_OPEN_TIME_MS; // 出错时使用默认值
            }
            
            // 7. 通过PIS系统通知乘客
            // 注意：PassengerInformationSystem类中没有notifyPassengers方法
            // 相关信息会通过PIS系统的updateTrainInteriorDisplay方法自动更新
            
            // 记录操作
            recordDrivingAction("doors_open", 1.0); // 使用1.0表示车门打开操作
        }
    }
    
    /**
     * 关闭车门（由ATO系统调用）
     */
    public void closeDoors(String trainId) {
        // 这里应该根据trainId找到对应的列车并关闭车门
        if (train != null && train.getTrainId().equals(trainId)) {
            // 1. 检查车门是否已经关闭
            if (train.areAllDoorsClosed()) {
                return; // 车门已关闭，无需操作
            }
            
            // 2. 检查是否到达停站时间
            if (doorOpenStartTime > 0) {
                long doorOpenTime = System.currentTimeMillis() - doorOpenStartTime;
                if (doorOpenTime < currentDwellTimeMs) {
                    // 车门打开时间不足，等待
                    return;
                }
            }
            
            // 3. 第一次提醒：即将关门
            // 注意：PassengerInformationSystem类中没有notifyPassengers方法
            // 相关信息会通过PIS系统的updateTrainInteriorDisplay方法自动更新
            
            // 4. 短暂延迟，给乘客时间反应
            try {
                Thread.sleep(DOOR_CLOSE_WARNING_DELAY_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            
            // 5. 第二次提醒：即将关门
            // 注意：PassengerInformationSystem类中没有notifyPassengers方法
            // 相关信息会通过PIS系统的updateTrainInteriorDisplay方法自动更新
            
            // 6. 再次短暂延迟
            try {
                Thread.sleep(DOOR_CLOSE_WARNING_DELAY_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            
            // 7. 检查是否有障碍物
            if (hasDoorObstructions()) {
                // 有障碍物，通知司机
                if (train.getDriver() != null) {
                    train.getDriver().sendMessage(Text.literal("⚠ 车门有障碍物，无法关闭！"), false);
                }
                return;
            }
            
            // 8. 执行关门操作
            train.closeAllDoors();
            
            // 9. 重置开门时间
            doorOpenStartTime = 0;
            
            // 10. 记录操作
            recordDrivingAction("doors_close", 1.0); // 使用1.0表示车门关闭操作
        }
    }
    
    /**
     * 获取车门开启方向文本
     */
    private String getDoorOpeningSideText() {
        // 在实际实现中，这里应该根据车站布局和列车位置来确定开门方向
        // 简化处理，返回"左侧"或"右侧"
        return train.getId() % 2 == 0 ? "左侧开门" : "右侧开门";
    }

    // 设置目标速度
    public void setTargetSpeed(double speed) {
        this.targetSpeed = Math.min(speed, maxSpeed);
    }

    // 设置控制模式
    public void setControlMode(TrainControlMode mode) {
        // 检查模式切换是否有效
        if (!this.controlMode.canSwitchTo(mode)) {
            if (train.getDriver() != null) {
                train.getDriver().sendMessage(Text.literal("⚠ 模式切换无效：从" + 
                    this.controlMode.getDisplayName() + "无法切换至" + mode.getDisplayName()), false);
            }
            return;
        }
        
        // 检查是否有必要的保护系统
        if (mode.isATPProtected() && !train.isATPEnabled()) {
            if (train.getDriver() != null) {
                train.getDriver().sendMessage(Text.literal("⚠ 切换失败：ATP系统未激活，无法切换至需要ATP保护的模式"), false);
            }
            return;
        }
        
        this.controlMode = mode;
        
        // 模式特定设置
        switch (mode) {
            case RM:
                // RM模式限速25km/h
                this.maxSpeed = 25.0;
                break;
            case ITC:
                // 中间折返模式设置
                initializeTurnbackMode();
                break;
            case ATO:
                // 重置ATO相关状态
                initializeATO();
                break;
        }
        
        // 通知司机
        if (train.getDriver() != null) {
            train.getDriver().sendMessage(Text.literal("控制模式已切换至: " + mode.getDisplayName()), false);
            // 发送模式注意事项
            sendModeSafetyTips(mode);
        }
        
        // 通过PIS系统通知乘客控制模式变更
        notifyPassengersOfModeChange(mode);
    }
    
    // 初始化ATO系统
    private void initializeATO() {
        // 重置ATO相关参数
        this.targetSpeed = 0.0;
        this.isAccelerating = false;
        this.isBraking = false;
        this.lastAcceleration = 0.0;
        this.lastDeceleration = 0.0;
        
        // 检查并初始化ATOSystem
        ATOSystem atoSystem = ATOSystem.getInstance(world);
        if (atoSystem != null) {
            atoSystem.setATOEnabled(train.getUuidAsString(), true);
        }
    }
    
    // 初始化折返模式
    private void initializeTurnbackMode() {
        // 记录当前方向
        this.lastAcceleration = 0.0;
        this.lastDeceleration = 0.0;
        
        // 通知ATO系统设置折返模式
        ATOSystem atoSystem = ATOSystem.getInstance(world);
        if (atoSystem != null) {
            atoSystem.setTrainOperationMode(train.getUuidAsString(), ATOSystem.OperationMode.ITC);
        }
    }
    
    // 发送模式安全提示
    private void sendModeSafetyTips(TrainControlMode mode) {
        if (train.getDriver() == null) return;
        
        switch (mode) {
            case RM:
                train.getDriver().sendMessage(Text.literal("📢 RM模式注意事项：速度限制25km/h，请密切观察前方信号和轨道状况"), false);
                break;
            case URM:
                train.getDriver().sendMessage(Text.literal("⚠ URM模式警告：无ATP保护，请极度谨慎驾驶，保持瞭望"), false);
                break;
            case ATPM:
                train.getDriver().sendMessage(Text.literal("📢 ATPM模式：ATP系统将监督操作，请按照速度限制安全驾驶"), false);
                break;
            case ITC:
                train.getDriver().sendMessage(Text.literal("📢 中间折返模式已启动，请确认站台清空后再执行折返操作"), false);
                break;
        }
    }
    
    // 通知乘客模式变更
    private void notifyPassengersOfModeChange(TrainControlMode mode) {
        PassengerInformationSystem pis = PassengerInformationSystem.getInstance(world);
        if (pis != null) {
            String message = "";
            switch (mode) {
                case ATO:
                    message = "列车已切换至自动驾驶模式，请系好安全带，注意安全";
                    break;
                case MANUAL:
                    message = "列车已切换至手动驾驶模式";
                    break;
                case ITC:
                    message = "列车即将执行折返作业，请扶稳坐好";
                    break;
            }
            if (!message.isEmpty()) {
                // 假设有addInteriorMessage方法
                pis.addInteriorMessage(train.getTrainId(), message);
            }
        }
    }

    /**
     * 记录驾驶操作
     */
    private void recordDrivingAction(String actionType, double value) {
        // 只在手动模式下记录操作
        if (controlMode == TrainControlMode.MANUAL) {
            ATS ats = ATS.getInstance(world);
            ats.recordDrivingAction(train.getTrainId(), actionType, value);
        }
    }

    // 获取控制模式
    public TrainControlMode getControlMode() {
        return controlMode;
    }

    // 获取最大速度
    public double getMaxSpeed() {
        return maxSpeed;
    }

    // 设置最大速度
    public void setMaxSpeed(double maxSpeed) {
        this.maxSpeed = maxSpeed;
        if (this.targetSpeed > maxSpeed) {
            this.targetSpeed = maxSpeed;
        }
    }

    // 控制模式枚举
    public enum TrainControlMode {
        MANUAL("手动控制", false, false),
        ATO("自动驾驶", true, true),
        ATPM("ATP监督下手动驾驶", false, true),
        RM("限制人工驾驶", false, false),
        URM("非限制人工驾驶", false, false),
        ITC("中间折返模式", true, true);

        private final String displayName;
        private final boolean isAutomatic;
        private final boolean isATPProtected;

        TrainControlMode(String displayName, boolean isAutomatic, boolean isATPProtected) {
            this.displayName = displayName;
            this.isAutomatic = isAutomatic;
            this.isATPProtected = isATPProtected;
        }

        public String getDisplayName() {
            return displayName;
        }
        
        public boolean isAutomatic() {
            return isAutomatic;
        }
        
        public boolean isATPProtected() {
            return isATPProtected;
        }
        
        // 检查模式切换是否有效
        public boolean canSwitchTo(TrainControlMode targetMode) {
            // 从URM模式只能切换到RM模式
            if (this == URM) {
                return targetMode == RM;
            }
            // 其他模式间可以自由切换（除了自动切换保护）
            return true;
        }
    }
}