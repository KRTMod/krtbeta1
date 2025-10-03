package com.krt.mod.system;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import com.krt.mod.KRTMod;
import com.krt.mod.block.SignalBlock;
import com.krt.mod.block.TrackBlock;
import com.krt.mod.block.SwitchTrackBlock;
import com.krt.mod.block.ATPSignalBlock;
import com.krt.mod.block.ATPSignalBlockEntity;
import com.krt.mod.entity.TrainEntity;
import com.krt.mod.entity.TrainConsist;
import com.krt.mod.entity.TrainCar;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 高级列车AI系统
 * 负责列车的自动驾驶、路径规划和决策制定
 */
public class AdvancedTrainAISystem {
    private static final Map<World, AdvancedTrainAISystem> INSTANCES = new HashMap<>();
    private final World world;
    
    // 存储每个列车的AI状态
    private final Map<String, TrainAIState> trainAIStates = new ConcurrentHashMap<>();
    
    // 决策制定的权重参数
    private static final double SAFETY_WEIGHT = 0.4;
    private static final double EFFICIENCY_WEIGHT = 0.3;
    private static final double COMFORT_WEIGHT = 0.2;
    private static final double ENERGY_SAVING_WEIGHT = 0.1;
    
    // 路径搜索的最大距离
    private static final int MAX_PATH_SEARCH_DISTANCE = 1000;
    
    private AdvancedTrainAISystem(World world) {
        this.world = world;
    }
    
    public static AdvancedTrainAISystem getInstance(World world) {
        return INSTANCES.computeIfAbsent(world, AdvancedTrainAISystem::new);
    }
    
    // 初始化列车AI
    public void initializeTrainAI(TrainEntity train) {
        String trainId = train.getUuidAsString();
        trainAIStates.put(trainId, new TrainAIState(train));
    }
    
    // 更新AI系统
    public void update() {
        // 更新所有列车的AI状态
        for (Map.Entry<String, TrainAIState> entry : trainAIStates.entrySet()) {
            String trainId = entry.getKey();
            TrainAIState aiState = entry.getValue();
            
            // 检查列车是否存在
            if (world.getEntity(UUID.fromString(trainId)) instanceof TrainEntity) {
                TrainEntity train = (TrainEntity) world.getEntity(UUID.fromString(trainId));
                
                // 如果ATO启用，运行AI决策
                if (train.isATOEnabled()) {
                    runAIDecision(train, aiState);
                }
            } else {
                // 移除不存在的列车AI状态
                trainAIStates.remove(trainId);
            }
        }
    }
    
    // 运行AI决策
    private void runAIDecision(TrainEntity train, TrainAIState aiState) {
        // 获取当前状态信息
        TrainState currentState = gatherCurrentState(train);
        
        // 预测未来状态
        List<PredictedState> predictedStates = predictFutureStates(currentState);
        
        // 评估所有可能的行动
        Map<AIAction, Double> evaluatedActions = evaluateActions(currentState, predictedStates);
        
        // 选择最佳行动
        AIAction bestAction = selectBestAction(evaluatedActions);
        
        // 执行最佳行动
        executeAction(train, bestAction);
        
        // 更新AI状态
        aiState.update(currentState, bestAction);
    }
    
    // 收集当前状态
    private TrainState gatherCurrentState(TrainEntity train) {
        TrainState state = new TrainState();
        
        // 列车基本信息
        state.trainId = train.getUuidAsString();
        state.position = train.getPos();
        state.direction = train.getRotationVector();
        state.speed = train.getCurrentSpeed();
        state.maxSpeed = train.getConsist() != null ? train.getConsist().getMaxSpeed() : 80;
        state.acceleration = calculateCurrentAcceleration(train);
        
        // 信号系统信息
        state.signalAhead = checkSignalAhead(train);
        
        // 轨道信息
        state.trackCondition = checkTrackCondition(train);
        
        // 车站信息
        state.nextStationDistance = calculateDistanceToNextStation(train);
        
        // 列车状态信息
        state.trainHealth = train.getHealth();
        state.emergencyBrake = train.isEmergencyBrakeApplied();
        state.doorOpen = train.getConsist() != null && !train.getConsist().areAllDoorsClosed();
        
        return state;
    }
    
    // 计算当前加速度
    private double calculateCurrentAcceleration(TrainEntity train) {
        // 简化版：根据速度变化计算加速度
        TrainAIState aiState = trainAIStates.get(train.getUuidAsString());
        if (aiState != null && aiState.lastSpeed >= 0) {
            double speedChange = train.getCurrentSpeed() - aiState.lastSpeed;
            return speedChange / 0.05; // 假设每刻0.05秒
        }
        return 0;
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
    
    // 检查轨道状况
    private TrackCondition checkTrackCondition(TrainEntity train) {
        TrackCondition condition = new TrackCondition();
        BlockPos trainPos = train.getBlockPos();
        
        // 检查当前轨道
        BlockState state = world.getBlockState(trainPos);
        if (state.getBlock() instanceof TrackBlock) {
            condition.isOnTrack = true;
        }
        
        // 检查前方轨道是否有弯道或道岔
        Vec3d direction = train.getRotationVector();
        for (int distance = 1; distance <= 10; distance++) {
            BlockPos checkPos = new BlockPos(trainPos.getX() + direction.x * distance,
                                           trainPos.getY(),
                                           trainPos.getZ() + direction.z * distance);
            
            if (world.getBlockState(checkPos).getBlock() instanceof SwitchTrackBlock) {
                condition.hasSwitchAhead = true;
                condition.switchDistance = distance;
                break;
            }
        }
        
        // 检查坡度
        condition.slope = calculateSlope(trainPos);
        
        return condition;
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
    
    // 计算到下一站的距离
    private double calculateDistanceToNextStation(TrainEntity train) {
        // 简化版：根据列车编组中的下一站信息计算距离
        TrainConsist consist = train.getConsist();
        if (consist != null && !consist.getNextStation().isEmpty()) {
            LineControlSystem lineSystem = LineControlSystem.getInstance(world);
            BlockPos stationPos = lineSystem.getStationPosition(consist.getLineId(), consist.getNextStation());
            if (stationPos != null) {
                return train.getPos().distanceTo(new Vec3d(stationPos.getX() + 0.5, stationPos.getY(), stationPos.getZ() + 0.5));
            }
        }
        return Double.MAX_VALUE;
    }
    
    // 预测未来状态
    private List<PredictedState> predictFutureStates(TrainState currentState) {
        List<PredictedState> predictedStates = new ArrayList<>();
        
        // 预测未来5秒内的状态（每秒一个状态）
        for (int i = 1; i <= 5; i++) {
            double timeStep = i * 1.0; // 秒
            
            // 创建预测状态
            PredictedState predictedState = new PredictedState();
            predictedState.time = timeStep;
            
            // 基于当前加速度预测未来位置和速度
            predictedState.speed = currentState.speed + currentState.acceleration * timeStep;
            predictedState.position = currentState.position.add(currentState.direction.multiply(
                currentState.speed * timeStep + 0.5 * currentState.acceleration * timeStep * timeStep
            ));
            
            // 考虑信号变化的可能性
            if (currentState.signalAhead.hasSignal) {
                double signalDistance = currentState.signalAhead.distance - 
                                      (currentState.speed * timeStep + 0.5 * currentState.acceleration * timeStep * timeStep);
                
                if (signalDistance < 50) {
                    // 信号可能变为黄色或红色
                    predictedState.expectedSignalState = SignalBlock.SignalState.YELLOW;
                }
            }
            
            predictedStates.add(predictedState);
        }
        
        return predictedStates;
    }
    
    // 评估所有可能的行动
    private Map<AIAction, Double> evaluateActions(TrainState currentState, List<PredictedState> predictedStates) {
        Map<AIAction, Double> evaluations = new HashMap<>();
        
        // 生成可能的行动
        List<AIAction> possibleActions = generatePossibleActions(currentState);
        
        // 评估每个行动
        for (AIAction action : possibleActions) {
            double evaluation = evaluateAction(currentState, predictedStates, action);
            evaluations.put(action, evaluation);
        }
        
        return evaluations;
    }
    
    // 生成可能的行动
    private List<AIAction> generatePossibleActions(TrainState currentState) {
        List<AIAction> actions = new ArrayList<>();
        
        // 如果车门打开，只能停车
        if (currentState.doorOpen) {
            actions.add(AIAction.STOP);
            return actions;
        }
        
        // 如果触发了紧急制动，只能停车
        if (currentState.emergencyBrake) {
            actions.add(AIAction.STOP);
            return actions;
        }
        
        // 基本行动：加速、保持、减速、停车
        actions.add(AIAction.ACCELERATE);
        actions.add(AIAction.MAINTAIN);
        actions.add(AIAction.DECELERATE);
        actions.add(AIAction.STOP);
        
        // 如果接近车站，考虑准备停车
        if (currentState.nextStationDistance < 500 && currentState.nextStationDistance != Double.MAX_VALUE) {
            actions.add(AIAction.PREPARE_TO_STOP);
        }
        
        // 如果是在弯道或道岔前，考虑减速
        TrackCondition trackCondition = currentState.trackCondition;
        if (trackCondition.hasSwitchAhead && trackCondition.switchDistance < 50) {
            actions.add(AIAction.SLOW_DOWN_FOR_SWITCH);
        }
        
        return actions;
    }
    
    // 评估单个行动
    private double evaluateAction(TrainState currentState, List<PredictedState> predictedStates, AIAction action) {
        double safetyScore = evaluateSafety(currentState, predictedStates, action);
        double efficiencyScore = evaluateEfficiency(currentState, predictedStates, action);
        double comfortScore = evaluateComfort(currentState, predictedStates, action);
        double energyScore = evaluateEnergySaving(currentState, predictedStates, action);
        
        // 加权总和
        return safetyScore * SAFETY_WEIGHT + 
               efficiencyScore * EFFICIENCY_WEIGHT + 
               comfortScore * COMFORT_WEIGHT + 
               energyScore * ENERGY_SAVING_WEIGHT;
    }
    
    // 评估安全性
    private double evaluateSafety(TrainState currentState, List<PredictedState> predictedStates, AIAction action) {
        double score = 1.0;
        
        // 检查信号安全
        if (currentState.signalAhead.hasSignal) {
            if (currentState.signalAhead.state == SignalBlock.SignalState.RED) {
                if (action != AIAction.STOP && action != AIAction.DECELERATE) {
                    score *= 0.1; // 非常不安全
                }
            } else if (currentState.signalAhead.state == SignalBlock.SignalState.YELLOW) {
                if (action == AIAction.ACCELERATE) {
                    score *= 0.5; // 不安全
                }
            }
        }
        
        // 检查车站距离安全
        if (currentState.nextStationDistance < 200 && currentState.nextStationDistance != Double.MAX_VALUE) {
            if (action == AIAction.ACCELERATE) {
                score *= 0.3; // 不安全
            }
        }
        
        // 检查速度安全
        if (currentState.speed > currentState.maxSpeed) {
            if (action != AIAction.DECELERATE && action != AIAction.STOP) {
                score *= 0.2; // 非常不安全
            }
        }
        
        return Math.max(0, Math.min(1, score));
    }
    
    // 评估效率
    private double evaluateEfficiency(TrainState currentState, List<PredictedState> predictedStates, AIAction action) {
        double score = 0.5; // 默认分数
        
        // 如果目标是尽快到达，加速更有效率
        if (currentState.nextStationDistance > 1000 && currentState.nextStationDistance != Double.MAX_VALUE) {
            if (action == AIAction.ACCELERATE && currentState.speed < currentState.maxSpeed) {
                score = 0.9;
            } else if (action == AIAction.MAINTAIN && currentState.speed == currentState.maxSpeed) {
                score = 0.8;
            }
        }
        
        // 如果接近车站，减速准备停车更有效率
        if (currentState.nextStationDistance < 300 && currentState.nextStationDistance != Double.MAX_VALUE) {
            if (action == AIAction.PREPARE_TO_STOP || action == AIAction.DECELERATE) {
                score = 0.9;
            }
        }
        
        return Math.max(0, Math.min(1, score));
    }
    
    // 评估舒适度
    private double evaluateComfort(TrainState currentState, List<PredictedState> predictedStates, AIAction action) {
        double score = 0.5; // 默认分数
        
        // 急剧加速或减速会降低舒适度
        if ((action == AIAction.ACCELERATE && currentState.acceleration > 1.0) ||
            (action == AIAction.DECELERATE && currentState.acceleration < -1.0)) {
            score = 0.3;
        }
        
        // 保持恒定速度最舒适
        if (action == AIAction.MAINTAIN && Math.abs(currentState.acceleration) < 0.2) {
            score = 0.9;
        }
        
        return Math.max(0, Math.min(1, score));
    }
    
    // 评估节能性
    private double evaluateEnergySaving(TrainState currentState, List<PredictedState> predictedStates, AIAction action) {
        double score = 0.5; // 默认分数
        
        // 下坡时加速更节能
        if (currentState.trackCondition.slope < -5.0) { // 下坡
            if (action == AIAction.MAINTAIN || action == AIAction.ACCELERATE) {
                score = 0.8;
            }
        }
        
        // 上坡时减速或保持更节能
        if (currentState.trackCondition.slope > 5.0) { // 上坡
            if (action == AIAction.DECELERATE || action == AIAction.MAINTAIN) {
                score = 0.8;
            }
        }
        
        // 避免不必要的加速和减速
        if ((action == AIAction.ACCELERATE && currentState.speed > currentState.maxSpeed * 0.8) ||
            (action == AIAction.DECELERATE && currentState.speed < currentState.maxSpeed * 0.2)) {
            score = 0.3;
        }
        
        return Math.max(0, Math.min(1, score));
    }
    
    // 选择最佳行动
    private AIAction selectBestAction(Map<AIAction, Double> evaluatedActions) {
        return evaluatedActions.entrySet().stream()
            .max(Map.Entry.comparingByValue())
            .map(Map.Entry::getKey)
            .orElse(AIAction.MAINTAIN);
    }
    
    // 执行行动
    private void executeAction(TrainEntity train, AIAction action) {
        TrainControlSystem controlSystem = train.getControlSystem();
        
        switch (action) {
            case ACCELERATE:
                controlSystem.increaseAcceleration();
                break;
            case MAINTAIN:
                controlSystem.maintainSpeed();
                break;
            case DECELERATE:
                controlSystem.decreaseAcceleration();
                break;
            case STOP:
                controlSystem.applyServiceBrake();
                break;
            case PREPARE_TO_STOP:
                controlSystem.prepareToStop();
                break;
            case SLOW_DOWN_FOR_SWITCH:
                controlSystem.slowDownForSwitch();
                break;
        }
    }
    
    // 列车AI状态类
    private static class TrainAIState {
        private final String trainId;
        private double lastSpeed = -1;
        private AIAction lastAction = AIAction.MAINTAIN;
        private long lastDecisionTime = System.currentTimeMillis();
        
        public TrainAIState(TrainEntity train) {
            this.trainId = train.getUuidAsString();
            this.lastSpeed = train.getCurrentSpeed();
        }
        
        public void update(TrainState currentState, AIAction action) {
            this.lastSpeed = currentState.speed;
            this.lastAction = action;
            this.lastDecisionTime = System.currentTimeMillis();
        }
    }
    
    // 列车状态类
    private static class TrainState {
        private String trainId;
        private Vec3d position;
        private Vec3d direction;
        private double speed;
        private double maxSpeed;
        private double acceleration;
        private SignalInfo signalAhead = new SignalInfo();
        private TrackCondition trackCondition = new TrackCondition();
        private double nextStationDistance = Double.MAX_VALUE;
        private int trainHealth;
        private boolean emergencyBrake;
        private boolean doorOpen;
    }
    
    // 信号信息类
    private static class SignalInfo {
        private boolean hasSignal = false;
        private double distance = Double.MAX_VALUE;
        private SignalBlock.SignalState state = SignalBlock.SignalState.GREEN;
    }
    
    // 轨道状况类
    private static class TrackCondition {
        private boolean isOnTrack = false;
        private boolean hasSwitchAhead = false;
        private double switchDistance = Double.MAX_VALUE;
        private double slope = 0;
    }
    
    // 预测状态类
    private static class PredictedState {
        private double time;
        private Vec3d position;
        private double speed;
        private SignalBlock.SignalState expectedSignalState = SignalBlock.SignalState.GREEN;
    }
    
    // AI行动枚举
    private enum AIAction {
        ACCELERATE,     // 加速
        MAINTAIN,       // 保持速度
        DECELERATE,     // 减速
        STOP,           // 停车
        PREPARE_TO_STOP, // 准备停车
        SLOW_DOWN_FOR_SWITCH // 为道岔减速
    }
}