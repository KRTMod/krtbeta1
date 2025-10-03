package com.krt.mod.system;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import com.krt.mod.KRTMod;
import com.krt.mod.block.SignalBlock;
import com.krt.mod.block.TrackBlock;
// import com.krt.mod.block.StationBlock;
import com.krt.mod.block.ATPSignalBlock;
import com.krt.mod.block.ATPSignalBlockEntity;
import com.krt.mod.entity.TrainEntity;
import com.krt.mod.entity.TrainConsist;
import com.krt.mod.entity.TrainCar;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 列车自动运行系统(ATO)
 * 负责控制列车的自动驾驶、精确停车和站点管理
 */
public class ATOSystem {
    private static final Map<World, ATOSystem> INSTANCES = new HashMap<>();
    private final World world;
    
    // 存储每个列车的ATO状态
    private final Map<String, ATOState> atoStates = new ConcurrentHashMap<>();
    
    // 运行模式枚举
    public enum OperationMode {
        MANUAL,         // 手动模式
        ATO,            // 自动运行模式
        ATO_STATION,    // 车站内ATO模式
        ATO_SHUNT,      // 调车模式
        ATPM,           // ATP监控模式 (司机操作，ATP监督)
        RM,             // 限制人工驾驶模式 (限速25km/h)
        URM,            // 非限制人工驾驶模式 (无ATP保护)
        ITC             // 中间折返模式
    }
    
    // 控制参数
    private static final double MAX_ACCELERATION = 0.8;     // 最大加速度 (m/s²)
    private static final double MAX_DECELERATION = 1.0;     // 最大减速度 (m/s²)
    private static final double CRUISE_ACCELERATION = 0.3;  // 巡航加速度 (m/s²)
    private static final double STOPPING_ACCELERATION = 0.5; // 停车减速度 (m/s²)
    private static final double STATION_APPROACH_SPEED = 2.0; // 进站速度 (m/s)
    private static final double PLATFORM_ALIGNMENT_TOLERANCE = 0.3; // 站台对齐容差 (m) - 提高精度
    private static final double JERK_LIMIT = 0.3;           // 加加速度限制 (m/s³) - 提高乘坐舒适度
    private static final double PRECISION_STOP_APPROACH_DISTANCE = 20.0; // 精确停车接近距离 (m)
    
    // 距离阈值
    private static final double STATION_APPROACH_DISTANCE = 300; // 进站准备距离 (m)
    private static final double BRAKING_DISTANCE = 50;        // 制动距离 (m)
    private static final double STOPPING_DISTANCE = 10;       // 停车距离 (m)
    
    private ATOSystem(World world) {
        this.world = world;
    }
    
    public static ATOSystem getInstance(World world) {
        return INSTANCES.computeIfAbsent(world, ATOSystem::new);
    }
    
    // 初始化列车ATO系统
    public void initializeATO(TrainEntity train) {
        String trainId = train.getUuidAsString();
        atoStates.put(trainId, new ATOState(train));
    }
    
    // 更新ATO系统
    public void update() {
        // 更新所有列车的ATO状态
        for (Map.Entry<String, ATOState> entry : atoStates.entrySet()) {
            String trainId = entry.getKey();
            ATOState atoState = entry.getValue();
            
            // 检查列车是否存在
            if (world.getEntity(UUID.fromString(trainId)) instanceof TrainEntity) {
                TrainEntity train = (TrainEntity) world.getEntity(UUID.fromString(trainId));
                
                // 根据运行模式执行不同的控制逻辑
                if (train.isATOEnabled()) {
                    executeATOControl(train, atoState);
                } else {
                    // ATO禁用时，重置状态
                    atoState.reset();
                }
            } else {
                // 移除不存在的列车ATO状态
                atoStates.remove(trainId);
            }
        }
    }
    
    // 执行ATO控制
    private void executeATOControl(TrainEntity train, ATOState atoState) {
        // 获取列车编组信息
        TrainConsist consist = train.getConsist();
        if (consist == null) {
            return;
        }
        
        // 更新运行模式
        updateOperationMode(train, atoState);
        
        // 根据运行模式执行控制
        switch (atoState.getCurrentMode()) {
            case ATO:
                executeNormalATOMode(train, atoState);
                break;
            case ATO_STATION:
                executeStationATOMode(train, atoState);
                break;
            case ATO_SHUNT:
                executeShuntATOMode(train, atoState);
                break;
            case MANUAL:
            default:
                // 手动模式下不执行ATO控制
                break;
        }
        
        // 更新ATO状态
        atoState.update(train);
    }
    
    // 更新运行模式
    private void updateOperationMode(TrainEntity train, ATOState atoState) {
        TrainConsist consist = train.getConsist();
        if (consist == null) {
            return;
        }
        
        // 首先检查列车手动设置的模式
        if (train.getOperationMode() != null) {
            atoState.setCurrentMode(train.getOperationMode());
            return;
        }
        
        // 检查是否在调车模式
        boolean inShuntMode = consist.isInShuntMode();
        if (inShuntMode) {
            atoState.setCurrentMode(OperationMode.ATO_SHUNT);
            return;
        }
        
        // 检查ATP状态
        if (train.isATPEnabled() && !train.isATOEnabled()) {
            atoState.setCurrentMode(OperationMode.ATPM);
            return;
        }
        
        // 检查限制人工驾驶模式
        if (!train.isATPEnabled() && train.isRMEnabled()) {
            atoState.setCurrentMode(OperationMode.RM);
            return;
        }
        
        // 检查非限制人工驾驶模式
        if (!train.isATPEnabled() && !train.isRMEnabled()) {
            atoState.setCurrentMode(OperationMode.URM);
            return;
        }
        
        // 在ATO模式下，根据位置设置子模式
        if (train.isATOEnabled()) {
            boolean inStation = isTrainInStation(train);
            if (inStation) {
                atoState.setCurrentMode(OperationMode.ATO_STATION);
            } else {
                atoState.setCurrentMode(OperationMode.ATO);
            }
        }
    }
    
    // 检查列车是否在车站内
    private boolean isTrainInStation(TrainEntity train) {
        BlockPos trainPos = train.getBlockPos();
        TrainConsist consist = train.getConsist();
        
        if (consist == null || consist.getLineId().isEmpty()) {
            return false;
        }
        
        // 获取线路控制系统
        LineControlSystem lineSystem = LineControlSystem.getInstance(world);
        
        // 检查列车位置是否在任何车站的范围内
        List<String> stations = lineSystem.getStationsOnLine(consist.getLineId());
        
        for (String stationName : stations) {
            BlockPos stationPos = lineSystem.getStationPosition(consist.getLineId(), stationName);
            if (stationPos != null) {
                double distance = train.getPos().distanceTo(new Vec3d(stationPos.getX() + 0.5, stationPos.getY(), stationPos.getZ() + 0.5));
                if (distance < 100) { // 假设车站范围为100米
                    return true;
                }
            }
        }
        
        return false;
    }
    
    // 执行正常ATO模式
    private void executeNormalATOMode(TrainEntity train, ATOState atoState) {
        TrainConsist consist = train.getConsist();
        if (consist == null) {
            return;
        }
        
        // 获取目标速度
        double targetSpeed = calculateTargetSpeed(train);
        
        // 获取当前速度
        double currentSpeed = train.getCurrentSpeed();
        
        // 应用速度控制
        applySpeedControl(train, currentSpeed, targetSpeed);
        
        // 检查是否需要准备进站
        checkStationApproach(train, consist);
    }
    
    // 计算目标速度
    private double calculateTargetSpeed(TrainEntity train) {
        // 获取列车当前信息
        double currentSpeed = train.getCurrentSpeed();
        TrainConsist consist = train.getConsist();
        if (consist == null) {
            return 0;
        }
        
        String trainType = consist.getTrainType();
        double trainWeight = train.getWeight();
        BlockPos pos = train.getBlockPos();
        Vec3d direction = train.getRotationVector();
        
        // 初始目标速度设置为基础最大速度
        double targetSpeed = getBaseMaxSpeed(trainType, consist.getMaxSpeed());
        
        // 信号系统限制 - 智能信号响应
        SignalInfo signalInfo = checkSignalAhead(train);
        if (signalInfo.hasSignal) {
            double distanceToSignal = signalInfo.distance;
            targetSpeed = Math.min(targetSpeed, calculateSignalSpeedLimit(signalInfo, currentSpeed, distanceToSignal));
        }
        
        // 轨道条件限制
        // 道岔限制 - 根据道岔类型和角度调整
        if (world.getBlockState(pos).getBlock() instanceof com.krt.mod.block.SwitchTrackBlock) {
            int turnoutAngle = getTurnoutAngle(pos);
            double turnoutSpeedLimit = calculateTurnoutSpeedLimit(turnoutAngle);
            targetSpeed = Math.min(targetSpeed, turnoutSpeedLimit);
        }
        
        // 曲线限速 - 根据曲线半径计算
        double curveRadius = calculateCurveRadius(pos, direction);
        if (curveRadius > 0) {
            double curveSpeedLimit = calculateCurveSpeedLimit(curveRadius, trainType);
            targetSpeed = Math.min(targetSpeed, curveSpeedLimit);
        }
        
        // 坡度影响 - 更精细的坡度控制
        double slope = calculateSlope(pos);
        targetSpeed = Math.min(targetSpeed, calculateSlopeSpeedLimit(slope, trainWeight));
        
        // 轨道状况限制 - 检查轨道磨损
        double trackConditionFactor = calculateTrackConditionFactor(pos);
        targetSpeed *= trackConditionFactor;
        
        // 前方车站准备 - 提前减速
        if (!consist.getNextStation().isEmpty()) {
            LineControlSystem lineSystem = LineControlSystem.getInstance(world);
            BlockPos stationPos = lineSystem.getStationPosition(consist.getLineId(), consist.getNextStation());
            if (stationPos != null) {
                double distanceToNextStation = train.getPos().distanceTo(new Vec3d(stationPos.getX() + 0.5, stationPos.getY(), stationPos.getZ() + 0.5));
                if (distanceToNextStation < 500) {
                    double stationApproachSpeed = calculateStationApproachSpeed(distanceToNextStation, currentSpeed);
                    targetSpeed = Math.min(targetSpeed, stationApproachSpeed);
                }
            }
        }
        
        // CBTC系统限制
        if (CBTCSystem.hasInstance(world)) {
            double cbtcLimitedSpeed = CBTCSystem.getInstance(world).getMaxAllowedSpeed(train.getUuidAsString());
            targetSpeed = Math.min(targetSpeed, cbtcLimitedSpeed);
        }
        
        // 最后确保速度不为负
        return Math.max(0.0, targetSpeed);
    }
    
    // 获取列车基础最大速度
    private double getBaseMaxSpeed(String trainType, double consistMaxSpeed) {
        // 根据列车类型返回不同的基础最大速度
        switch (trainType) {
            case "HighSpeed":
                return Math.min(120.0, consistMaxSpeed); // 高速列车
            case "Express":
                return Math.min(100.0, consistMaxSpeed); // 快速列车
            case "Local":
                return Math.min(80.0, consistMaxSpeed); // 普通列车
            default:
                return consistMaxSpeed; // 使用编组设定的最大速度
        }
    }
    
    // 计算信号速度限制
    private double calculateSignalSpeedLimit(SignalInfo signal, double currentSpeed, double distance) {
        // 根据信号显示和距离动态调整速度限制
        switch (signal.state) {
            case RED:
                // 红灯时，根据距离计算需要的制动速度
                double stoppingDistance = calculateBrakingDistance(currentSpeed);
                if (distance < stoppingDistance) {
                    return 0.0; // 立即停车
                } else {
                    // 计算安全降速曲线
                    return calculateDecelerationCurve(currentSpeed, 0.0, distance - stoppingDistance * 0.8);
                }
            case YELLOW:
                // 黄灯时，考虑信号距离和当前速度给出合理限速
                return Math.min(40.0, calculateDecelerationCurve(currentSpeed, 40.0, distance * 0.5));
            case GREEN:
            default:
                // 绿灯时，允许全速运行
                return Double.MAX_VALUE;
        }
    }
    
    // 获取道岔角度
    private int getTurnoutAngle(BlockPos pos) {
        // 简化实现，假设从轨道状态获取道岔角度
        // 实际实现中应从道岔方块状态或数据中获取
        return 15; // 默认中等角度
    }
    
    // 计算道岔角度对应的速度限制
    private double calculateTurnoutSpeedLimit(int turnoutAngle) {
        // 根据道岔角度返回对应的速度限制
        if (turnoutAngle < 15) {
            return 30.0; // 小角度道岔
        } else if (turnoutAngle < 30) {
            return 25.0; // 中角度道岔
        } else {
            return 20.0; // 大角度道岔
        }
    }
    
    // 计算曲线半径
    private double calculateCurveRadius(BlockPos pos, Vec3d direction) {
        // 简化实现：检查前方轨道的弯曲情况
        // 实际实现中应使用更复杂的算法检测曲线
        return -1; // 默认返回-1表示非曲线轨道
    }
    
    // 计算曲线半径对应的速度限制
    private double calculateCurveSpeedLimit(double radius, String trainType) {
        // 基础曲线限速公式: V = sqrt(r * 0.2) * 10 (简化公式)
        double baseLimit = Math.sqrt(radius * 0.2) * 10;
        
        // 根据列车类型调整
        switch (trainType) {
            case "HighSpeed":
                return Math.min(baseLimit * 1.1, 120.0);
            case "Local":
                return Math.min(baseLimit * 0.9, 80.0);
            default:
                return Math.min(baseLimit, 100.0);
        }
    }
    
    // 计算坡度对应的速度限制
    private double calculateSlopeSpeedLimit(double slope, double trainWeight) {
        // 上坡时可能需要减速以保持动力
        if (slope > 30.0) {
            double weightFactor = 1.0 - (trainWeight / 100000.0);
            return 30.0 * Math.max(0.6, weightFactor);
        } 
        // 下坡时需要限制速度以保证制动安全
        else if (slope < -20.0) {
            return 60.0; // 下坡限速
        }
        return Double.MAX_VALUE;
    }
    
    // 计算轨道状况因子
    private double calculateTrackConditionFactor(BlockPos pos) {
        // 基础状况因子
        double conditionFactor = 1.0;
        
        // 简化实现，实际应从轨道方块状态获取磨损信息
        // 此处返回默认值
        return conditionFactor;
    }
    
    // 计算车站接近速度
    private double calculateStationApproachSpeed(double distanceToStation, double currentSpeed) {
        // 根据距离车站的距离计算建议速度
        if (distanceToStation < 100) {
            return 20.0; // 接近站台时低速
        } else if (distanceToStation < 300) {
            // 计算减速曲线
            return calculateDecelerationCurve(currentSpeed, 20.0, distanceToStation - 100);
        }
        return Double.MAX_VALUE;
    }
    
    // 计算减速曲线
    private double calculateDecelerationCurve(double startSpeed, double endSpeed, double distance) {
        // 简化的减速曲线计算
        if (distance <= 0) {
            return endSpeed;
        }
        
        // 计算平均减速度
        double deltaV = startSpeed - endSpeed;
        double avgDeceleration = (deltaV * deltaV) / (2 * distance);
        
        // 确保减速度在安全范围内
        avgDeceleration = Math.min(avgDeceleration, MAX_DECELERATION * 0.8);
        
        // 计算当前应有的速度
        return Math.max(endSpeed, Math.sqrt(startSpeed * startSpeed - 2 * avgDeceleration * distance * 0.3));
    }
    
    // 计算制动距离
    private double calculateBrakingDistance(double currentSpeed) {
        // 简化的制动距离计算
        return (currentSpeed * currentSpeed) / (2 * MAX_DECELERATION);
    }
    
    // 获取信号系统限制速度
    private double getSignalLimitedSpeed(TrainEntity train) {
        // 获取前方信号状态
        SignalInfo signalInfo = checkSignalAhead(train);
        
        // 根据信号状态确定限制速度
        if (signalInfo.hasSignal) {
            switch (signalInfo.state) {
                case RED:
                    return 0.0; // 红灯停车
                case YELLOW:
                    return 40.0; // 黄灯减速
                case GREEN:
                    // 绿灯按最高速度运行
                    break;
            }
        }
        
        // 默认无限制
        return Double.MAX_VALUE;
    }
    
    // 检查前方信号
    private SignalInfo checkSignalAhead(TrainEntity train) {
        SignalInfo signalInfo = new SignalInfo();
        BlockPos trainPos = train.getBlockPos();
        Vec3d direction = train.getRotationVector();
        
        // 搜索前方200米内的信号机
        for (int distance = 10; distance <= 200; distance += 5) {
            BlockPos checkPos = new BlockPos(trainPos.getX() + direction.x * distance,
                                           trainPos.getY(),
                                           trainPos.getZ() + direction.z * distance);
            
            if (world.getBlockState(checkPos).getBlock() instanceof SignalBlock) {
                SignalBlock.SignalState state = world.getBlockState(checkPos).get(SignalBlock.SIGNAL_STATE);
                signalInfo.hasSignal = true;
                signalInfo.distance = distance;
                signalInfo.state = state;
                break;
            } else if (world.getBlockState(checkPos).getBlock() instanceof ATPSignalBlock) {
                ATPSignalBlockEntity be = (ATPSignalBlockEntity) world.getBlockEntity(checkPos);
                if (be != null) {
                    ATPSignalBlockEntity.ATPData data = be.getATPData();
                    signalInfo.hasSignal = true;
                    signalInfo.distance = distance;
                    signalInfo.state = data.emergencyStop ? SignalBlock.SignalState.RED : 
                                      (data.maxSpeed < 40 ? SignalBlock.SignalState.YELLOW : SignalBlock.SignalState.GREEN);
                    break;
                }
            }
        }
        
        return signalInfo;
    }
    
    // 获取轨道条件限制速度
    private double getTrackLimitedSpeed(TrainEntity train) {
        // 检查弯道、道岔、坡度等条件
        BlockPos trainPos = train.getBlockPos();
        double limitedSpeed = Double.MAX_VALUE;
        
        // 检查当前轨道是否为道岔
        if (world.getBlockState(trainPos).getBlock() instanceof com.krt.mod.block.SwitchTrackBlock) {
            limitedSpeed = 20.0; // 道岔限速20m/s
        }
        
        // 检查前方50米内是否有道岔
        Vec3d direction = train.getRotationVector();
        for (int distance = 1; distance <= 50; distance++) {
            BlockPos checkPos = new BlockPos(trainPos.getX() + direction.x * distance,
                                           trainPos.getY(),
                                           trainPos.getZ() + direction.z * distance);
            
            if (world.getBlockState(checkPos).getBlock() instanceof com.krt.mod.block.SwitchTrackBlock) {
                limitedSpeed = 20.0; // 道岔限速20m/s
                break;
            }
        }
        
        // 检查坡度
        double slope = calculateSlope(trainPos);
        if (Math.abs(slope) > 30) { // 坡度大于30‰
            limitedSpeed = Math.min(limitedSpeed, 30.0); // 限速30m/s
        }
        
        return limitedSpeed;
    }
    
    // 计算坡度
    private double calculateSlope(BlockPos pos) {
        // 简化版：检查前后方块的高度差
        double maxSlope = 0;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) continue;
                
                BlockPos neighbor = pos.add(dx, 0, dz);
                if (world.getBlockState(neighbor).getBlock() instanceof TrackBlock) {
                    double heightDiff = Math.abs(pos.getY() - neighbor.getY());
                    double distance = Math.sqrt(dx * dx + dz * dz);
                    double slope = (heightDiff / distance) * 1000; // 转换为‰
                    maxSlope = Math.max(maxSlope, slope);
                }
            }
        }
        return maxSlope;
    }
    
    // 应用速度控制
    private void applySpeedControl(TrainEntity train, double currentSpeed, double targetSpeed) {
        TrainControlSystem controlSystem = train.getControlSystem();
        if (controlSystem == null) {
            return;
        }
        
        // 计算速度差异
        double speedDiff = targetSpeed - currentSpeed;
        
        // 获取当前加速度和列车重量信息
        double currentAcceleration = train.getCurrentAcceleration();
        double trainWeight = train.getWeight();
        
        // 计算坡度影响
        double slopeCompensation = calculateSlopeCompensation(train.getBlockPos());
        
        // 计算基础加速度/减速度
        double baseAcceleration = 0;
        if (speedDiff > 0.5) {
            // 需要加速，考虑列车重量和当前速度
            baseAcceleration = calculateOptimalAcceleration(currentSpeed, targetSpeed, trainWeight);
        } else if (speedDiff < -0.5) {
            // 需要减速，考虑列车重量、当前速度和制动效率
            baseAcceleration = -calculateOptimalDeceleration(currentSpeed, targetSpeed, trainWeight);
        } else {
            // 速度在目标范围内，考虑坡度和阻力进行微调
            baseAcceleration = slopeCompensation;
        }
        
        // 平滑过渡加速度，避免急剧变化
        double smoothedAcceleration = smoothAccelerationChange(currentAcceleration, baseAcceleration);
        
        // 应用最终加速度
        controlSystem.setAcceleration(smoothedAcceleration);
    }
    
    // 计算最优加速度（考虑能耗和舒适度）
    private double calculateOptimalAcceleration(double currentSpeed, double targetSpeed, double trainWeight) {
        // 计算速度差
        double speedDiff = targetSpeed - currentSpeed;
        
        // 基础加速度根据速度差和列车重量调整
        double baseAcceleration = speedDiff * 0.15 / (1 + trainWeight / 10000);
        
        // 低速时允许较大加速度，高速时减少加速度以节省能源
        if (currentSpeed < 20) {
            baseAcceleration *= 1.2; // 起步加速增强
        } else if (currentSpeed > 60) {
            baseAcceleration *= 0.8; // 高速加速减弱
        }
        
        // 限制最大加速度
        return Math.min(MAX_ACCELERATION, baseAcceleration);
    }
    
    // 计算最优减速度（考虑舒适度和制动效率）
    private double calculateOptimalDeceleration(double currentSpeed, double targetSpeed, double trainWeight) {
        // 计算速度差
        double speedDiff = Math.abs(targetSpeed - currentSpeed);
        
        // 基础减速度根据速度差和列车重量调整
        double baseDeceleration = speedDiff * 0.2 / (1 + trainWeight / 10000);
        
        // 高速时允许较大减速度，低速时减少减速度以提高舒适度
        if (currentSpeed > 60) {
            baseDeceleration *= 1.1; // 高速减速增强
        } else if (currentSpeed < 10) {
            baseDeceleration *= 0.7; // 低速减速减弱
        }
        
        // 限制最大减速度
        return Math.min(MAX_DECELERATION, baseDeceleration);
    }
    
    // 平滑加速度变化
    private double smoothAccelerationChange(double currentAcceleration, double targetAcceleration) {
        // 计算加速度变化率限制
        final double MAX_ACCELERATION_CHANGE = 0.2; // 最大加速度变化率
        
        double accelerationDiff = targetAcceleration - currentAcceleration;
        
        // 限制加速度变化率
        if (Math.abs(accelerationDiff) > MAX_ACCELERATION_CHANGE) {
            if (accelerationDiff > 0) {
                return currentAcceleration + MAX_ACCELERATION_CHANGE;
            } else {
                return currentAcceleration - MAX_ACCELERATION_CHANGE;
            }
        }
        
        return targetAcceleration;
    }
    
    // 检查是否需要准备进站
    private void checkStationApproach(TrainEntity train, TrainConsist consist) {
        if (consist.getNextStation().isEmpty()) {
            return;
        }
        
        // 获取线路控制系统
        LineControlSystem lineSystem = LineControlSystem.getInstance(world);
        
        // 获取下一站位置
        BlockPos stationPos = lineSystem.getStationPosition(consist.getLineId(), consist.getNextStation());
        if (stationPos == null) {
            return;
        }
        
        // 计算到下一站的距离
        double distanceToStation = train.getPos().distanceTo(new Vec3d(stationPos.getX() + 0.5, stationPos.getY(), stationPos.getZ() + 0.5));
        
        // 如果接近车站，开始准备进站
        if (distanceToStation < STATION_APPROACH_DISTANCE && distanceToStation > STOPPING_DISTANCE) {
            // 计算需要的制动距离
            double currentSpeed = train.getCurrentSpeed();
            double requiredBrakingDistance = (currentSpeed * currentSpeed) / (2 * MAX_DECELERATION);
            
            // 如果距离小于所需制动距离，开始制动
            if (distanceToStation < requiredBrakingDistance + 20) { // 加20米安全余量
                TrainControlSystem controlSystem = train.getControlSystem();
                if (controlSystem != null) {
                    controlSystem.prepareToStop();
                }
            }
        }
    }
    
    // 执行车站ATO模式
    private void executeStationATOMode(TrainEntity train, ATOState atoState) {
        TrainConsist consist = train.getConsist();
        if (consist == null) {
            return;
        }
        
        // 获取线路控制系统
        LineControlSystem lineSystem = LineControlSystem.getInstance(world);
        
        // 检查是否在正确的站点
        if (!consist.getNextStation().isEmpty()) {
            BlockPos stationPos = lineSystem.getStationPosition(consist.getLineId(), consist.getNextStation());
            if (stationPos != null) {
                // 计算到停车点的精确距离和方向
                Vec3d stationVec = new Vec3d(stationPos.getX() + 0.5, stationPos.getY(), stationPos.getZ() + 0.5);
                Vec3d trainPos = train.getPos();
                Vec3d direction = train.getRotationVector();
                
                // 计算沿轨道方向的距离
                Vec3d toStation = stationVec.subtract(trainPos);
                double distanceToStation = toStation.dotProduct(direction.normalize());
                double currentSpeed = train.getCurrentSpeed();
                
                // 精确停车控制算法（优化版）
                if (Math.abs(distanceToStation) > PLATFORM_ALIGNMENT_TOLERANCE) {
                    // 分阶段控制
                    if (distanceToStation > PRECISION_STOP_APPROACH_DISTANCE) {
                        // 远距离阶段：减速到目标速度
                        double targetSpeed = calculateOptimalApproachSpeed(distanceToStation, train.getWeight());
                        applySmoothSpeedControl(train, currentSpeed, targetSpeed, atoState);
                    } else {
                        // 近距离阶段：使用增强的PID控制精确停车
                        double acceleration = calculateEnhancedStationStoppingAcceleration(train, distanceToStation, atoState);
                        
                        TrainControlSystem controlSystem = train.getControlSystem();
                        if (controlSystem != null) {
                            controlSystem.setAcceleration(acceleration);
                        }
                    }
                } else {
                    // 已到达停车位置，精确停车并开门
                    if (!atoState.isAtStationStop()) {
                        TrainControlSystem controlSystem = train.getControlSystem();
                        if (controlSystem != null) {
                            // 应用精确停车制动（带防滑控制）
                            controlSystem.applyPrecisionStop();
                        }
                        
                        // 设置已到达站点标志
                        atoState.setAtStationStop(true);
                        atoState.setStationStopTime(System.currentTimeMillis());
                        
                        // 通知列车编组开门
                        if (consist != null) {
                            // 根据站台位置自动选择开左侧或右侧门
                            boolean leftSide = determinePlatformSide(stationPos, direction);
                            consist.openDoors();
                        }
                    } else {
                        // 检查停留时间是否已到
                        long stopTime = System.currentTimeMillis() - atoState.getStationStopTime();
                        // 根据客流量动态调整停留时间
                        int passengerCount = consist.getPassengerCount();
                        long adjustedStopTime = calculateAdjustedStopTime(passengerCount);
                        
                        if (stopTime > adjustedStopTime && stopTime <= adjustedStopTime + 2000) {
                            if (consist != null && !consist.areAllDoorsClosed()) {
                                // 关门前列车广播
                                broadcastDoorClosingAnnouncement(train);
                            }
                        }
                        
                        if (stopTime > adjustedStopTime + 2000) { // 停留调整时间+2秒后关门
                            if (consist != null && !consist.areAllDoorsClosed()) {
                                consist.closeDoors();
                            }
                        }
                        
                        if (stopTime > adjustedStopTime + 5000) { // 停留调整时间+5秒后发车
                            atoState.setAtStationStop(false);
                            consist.setNextStation(lineSystem.getNextStation(consist.getLineId(), consist.getNextStation()));
                        }
                    }
                }
            }
        }
    }
    
    // 计算优化的进站速度
    private double calculateOptimalApproachSpeed(double distanceToStation, double trainWeight) {
        // 根据距离和列车重量计算最佳进站速度
        double baseSpeed = Math.sqrt(2 * STOPPING_ACCELERATION * distanceToStation * 0.8);
        // 重车减速更早，速度更低
        double weightFactor = Math.min(1.0, 0.7 + trainWeight * 0.0001);
        return Math.min(STATION_APPROACH_SPEED, baseSpeed * weightFactor);
    }
    
    // 增强的车站停车加速度计算
    private double calculateEnhancedStationStoppingAcceleration(TrainEntity train, double distanceToStation, ATOState atoState) {
        double currentSpeed = train.getCurrentSpeed();
        double weight = train.getWeight();
        
        // PID控制参数
        double Kp = 0.3;  // 比例增益
        double Ki = 0.05; // 积分增益
        double Kd = 0.1;  // 微分增益
        
        // 计算误差
        double error = distanceToStation;
        atoState.updateErrorSum(error);
        double errorDerivative = atoState.calculateErrorDerivative(error);
        
        // 基础PID控制
        double pidOutput = Kp * error + Ki * atoState.getErrorSum() + Kd * errorDerivative;
        
        // 速度补偿
        double speedCompensation = currentSpeed * 0.1;
        
        // 重量补偿
        double weightCompensation = weight * 0.00005;
        
        // 最终加速度
        double acceleration = pidOutput - speedCompensation - weightCompensation;
        
        // 限制最大加速度变化率（加加速度限制）
        acceleration = limitJerk(acceleration, atoState.getLastAcceleration(), JERK_LIMIT);
        atoState.setLastAcceleration(acceleration);
        
        // 限制在合理范围内
        return Math.max(-MAX_DECELERATION, Math.min(MAX_ACCELERATION, acceleration));
    }
    
    // 限制加加速度
    private double limitJerk(double newAcceleration, double lastAcceleration, double jerkLimit) {
        double deltaAcceleration = newAcceleration - lastAcceleration;
        if (Math.abs(deltaAcceleration) > jerkLimit) {
            return lastAcceleration + Math.signum(deltaAcceleration) * jerkLimit;
        }
        return newAcceleration;
    }
    
    // 根据客流量计算调整的停留时间
    private long calculateAdjustedStopTime(int passengerCount) {
        // 基础停留时间10秒
        long baseStopTime = 10000;
        // 每增加10名乘客，增加1秒停留时间
        long additionalTime = (long)(passengerCount / 10.0 * 1000);
        // 最大额外增加30秒
        return baseStopTime + Math.min(30000, additionalTime);
    }
    
    // 确定站台位置（左侧或右侧）
    private boolean determinePlatformSide(BlockPos stationPos, Vec3d direction) {
        // 简化实现：根据站台相对于轨道的位置判断
        // 实际应该根据线路数据或站台标记来确定
        return true; // 默认左侧
    }
    
    // 广播车门关闭提示
    private void broadcastDoorClosingAnnouncement(TrainEntity train) {
        // 调用乘客信息系统进行广播
        // PassengerInformationSystem类可能需要后续实现
        if (train.getDriver() != null) {
            train.getDriver().sendMessage(Text.literal("车门即将关闭，请抓紧时间上下车"), false);
        }
    }
    
    // 平滑速度控制（考虑加加速度限制）
    private void applySmoothSpeedControl(TrainEntity train, double currentSpeed, double targetSpeed, ATOState atoState) {
        TrainControlSystem controlSystem = train.getControlSystem();
        if (controlSystem == null) {
            return;
        }
        
        double speedDiff = targetSpeed - currentSpeed;
        
        // 计算目标加速度
        double targetAcceleration = 0;
        if (Math.abs(speedDiff) > 0.2) {
            // 根据速度差异计算加速度
            double accelerationFactor = Math.min(1.0, Math.abs(speedDiff) / 2.0);
            targetAcceleration = speedDiff > 0 ? 
                MAX_ACCELERATION * accelerationFactor : 
                -MAX_DECELERATION * accelerationFactor;
        }
        
        // 限制加加速度
        double smoothedAcceleration = limitJerk(targetAcceleration, atoState.getLastAcceleration(), JERK_LIMIT);
        atoState.setLastAcceleration(smoothedAcceleration);
        
        // 应用加速度
        controlSystem.setAcceleration(smoothedAcceleration);
    }
    
    // 计算车站停车加速度（PID控制算法）
    private double calculateStationStoppingAcceleration(TrainEntity train, double distanceToStation) {
        // PID参数
        final double Kp = 0.05;  // 比例增益
        final double Ki = 0.001; // 积分增益
        final double Kd = 0.1;   // 微分增益
        
        // 获取当前速度
        double currentSpeed = train.getCurrentSpeed();
        
        // 计算目标速度曲线
        double targetSpeed = calculateStationTargetSpeed(distanceToStation);
        
        // 计算速度误差
        double speedError = targetSpeed - currentSpeed;
        
        // 简化的PID控制计算
        double proportionalTerm = Kp * speedError;
        
        // 考虑坡度影响
        double slopeCompensation = calculateSlopeCompensation(train.getBlockPos());
        
        // 计算最终加速度
        double acceleration = proportionalTerm + slopeCompensation;
        
        // 限制加速度范围
        acceleration = Math.max(-MAX_DECELERATION, Math.min(MAX_ACCELERATION, acceleration));
        
        return acceleration;
    }
    
    // 计算车站目标速度曲线
    private double calculateStationTargetSpeed(double distanceToStation) {
        // 根据距离计算目标速度，实现平滑的减速曲线
        if (distanceToStation > 200) {
            return STATION_APPROACH_SPEED; // 保持进站速度
        } else if (distanceToStation > 100) {
            return STATION_APPROACH_SPEED * 0.8;
        } else if (distanceToStation > 50) {
            return STATION_APPROACH_SPEED * 0.5;
        } else if (distanceToStation > 10) {
            return STATION_APPROACH_SPEED * 0.3;
        } else if (distanceToStation > 1) {
            return STATION_APPROACH_SPEED * 0.1;
        } else {
            return 0; // 到达停车位置
        }
    }
    
    // 计算坡度补偿
    private double calculateSlopeCompensation(BlockPos pos) {
        double slope = calculateSlope(pos);
        // 对于上坡，需要增加加速度；对于下坡，需要增加减速度
        return -slope * 0.001; // 坡度补偿系数
    }
    
    // 执行调车ATO模式
    private void executeShuntATOMode(TrainEntity train, ATOState atoState) {
        // 调车模式下，限制速度并简化控制逻辑
        double targetSpeed = 10.0; // 调车模式限速10m/s
        double currentSpeed = train.getCurrentSpeed();
        
        // 应用速度控制
        TrainControlSystem controlSystem = train.getControlSystem();
        if (controlSystem != null) {
            double speedDiff = targetSpeed - currentSpeed;
            
            if (speedDiff > 0.5) {
                controlSystem.setAcceleration(0.3);
            } else if (speedDiff < -0.5) {
                controlSystem.setAcceleration(-0.5);
            } else {
                controlSystem.maintainSpeed();
            }
        }
    }
    
    // 设置ATO启用状态
    public void setATOEnabled(TrainEntity train, boolean enabled) {
        String trainId = train.getUuidAsString();
        ATOState atoState = atoStates.computeIfAbsent(trainId, k -> new ATOState(train));
        
        if (enabled) {
            atoState.setATOEnabled(true);
            train.setATOEnabled(true);
            KRTMod.LOGGER.info("ATO system enabled for train: {}", trainId);
        } else {
            atoState.setATOEnabled(false);
            train.setATOEnabled(false);
            KRTMod.LOGGER.info("ATO system disabled for train: {}", trainId);
        }
    }
    
    // 获取ATO状态
    public ATOState getATOState(TrainEntity train) {
        return atoStates.get(train.getUuidAsString());
    }
    
    // ATO状态类
    public static class ATOState {
        private final String trainId;
        private boolean atoEnabled = false;
        private OperationMode currentMode = OperationMode.MANUAL;
        private boolean atStationStop = false;
        private long stationStopTime = 0;
        private long doorClosingTime = 0;
        private double lastSpeed = 0;
        private double lastAcceleration = 0;
        private String currentStation = "";
        private String nextStation = "";
        
        // PID控制相关变量
        private double errorSum = 0;
        private double lastError = 0;
        private long lastErrorTime = System.currentTimeMillis();
        
        // 驾驶模式相关状态
        private boolean isSpeedLimited = false;
        private double limitedSpeed = 0;
        
        public ATOState(TrainEntity train) {
            this.trainId = train.getUuidAsString();
        }
        
        public boolean isATOEnabled() {
            return atoEnabled;
        }
        
        public void setATOEnabled(boolean enabled) {
            this.atoEnabled = enabled;
        }
        
        public OperationMode getCurrentMode() {
            return currentMode;
        }
        
        public void setCurrentMode(OperationMode mode) {
            this.currentMode = mode;
            // 根据模式设置限速
            switch (mode) {
                case RM:
                    this.isSpeedLimited = true;
                    this.limitedSpeed = 7.0; // 约25km/h
                    break;
                case URM:
                    this.isSpeedLimited = false;
                    break;
                case ATPM:
                    this.isSpeedLimited = true;
                    // ATPM模式下由ATP系统控制限速
                    break;
                default:
                    this.isSpeedLimited = true;
                    // 其他模式使用线路限速
                    break;
            }
        }
        
        public boolean isAtStationStop() {
            return atStationStop;
        }
        
        public void setAtStationStop(boolean atStop) {
            this.atStationStop = atStop;
        }
        
        public long getStationStopTime() {
            return stationStopTime;
        }
        
        public void setStationStopTime(long time) {
            this.stationStopTime = time;
        }
        
        public void setDoorClosingTime(long time) {
            this.doorClosingTime = time;
        }
        
        public long getDoorClosingTime() {
            return doorClosingTime;
        }
        
        // 更新误差累积
        public void updateErrorSum(double error) {
            long currentTime = System.currentTimeMillis();
            double dt = (currentTime - lastErrorTime) / 1000.0; // 转换为秒
            
            // 积分项，考虑时间步长
            this.errorSum += error * dt;
            
            // 防止积分饱和
            this.errorSum = Math.max(-100, Math.min(100, this.errorSum));
            
            this.lastError = error;
            this.lastErrorTime = currentTime;
        }
        
        // 计算误差导数
        public double calculateErrorDerivative(double error) {
            long currentTime = System.currentTimeMillis();
            double dt = (currentTime - lastErrorTime) / 1000.0;
            
            if (dt > 0) {
                return (error - lastError) / dt;
            }
            return 0;
        }
        
        public double getErrorSum() {
            return errorSum;
        }
        
        public void setLastAcceleration(double acceleration) {
            this.lastAcceleration = acceleration;
        }
        
        public double getLastAcceleration() {
            return lastAcceleration;
        }
        
        public void update(TrainEntity train) {
            this.lastSpeed = train.getCurrentSpeed();
            
            // 更新车站信息
            TrainConsist consist = train.getConsist();
            if (consist != null) {
                this.nextStation = consist.getNextStation();
            }
            
            // 根据驾驶模式执行特定更新
            switch (currentMode) {
                case RM:
                    // 限制人工驾驶模式下监控速度
                    double currentSpeed = train.getCurrentSpeed();
                    if (currentSpeed > limitedSpeed + 0.5) {
                        // 超速警告
                        if (train.getDriver() != null) {
                            train.getDriver().sendMessage(Text.literal("⚠ 限速警告：RM模式下速度不得超过25km/h"), false);
                        }
                    }
                    break;
                case ATPM:
                    // ATP监控模式下检查ATP状态
                    if (!train.isATPEnabled()) {
                        // ATP故障时降级
                        setCurrentMode(OperationMode.URM);
                        if (train.getDriver() != null) {
                            train.getDriver().sendMessage(Text.literal("⚠ ATP系统故障，已降级为URM模式"), false);
                        }
                    }
                    break;
                case ITC:
                    // 中间折返模式处理
                    handleIntermediateTurnback(train);
                    break;
            }
        }
        
        // 处理中间折返
        private void handleIntermediateTurnback(TrainEntity train) {
            // 简化的中间折返逻辑
            if (isAtStationStop && System.currentTimeMillis() - stationStopTime > 30000) {
                // 停留30秒后执行折返
                if (train.getControlSystem() != null) {
                    train.getControlSystem().reverseDirection();
                }
                reset();
            }
        }
        
        public void reset() {
            this.currentMode = OperationMode.MANUAL;
            this.atStationStop = false;
            this.stationStopTime = 0;
            this.doorClosingTime = 0;
            this.errorSum = 0;
            this.lastError = 0;
            this.lastAcceleration = 0;
        }
        
        @Override
        public String toString() {
            return String.format("ATOState{enabled=%s, mode=%s, atStation=%s}",
                atoEnabled, currentMode, atStationStop);
        }
    }
    
    // 信号信息类
    private static class SignalInfo {
        private boolean hasSignal = false;
        private double distance = Double.MAX_VALUE;
        private SignalBlock.SignalState state = SignalBlock.SignalState.GREEN;
    }
}