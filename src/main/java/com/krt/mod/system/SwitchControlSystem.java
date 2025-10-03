package com.krt.mod.system;

import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import com.krt.mod.KRTMod;
import com.krt.mod.block.SwitchTrackBlock;
import com.krt.mod.block.SignalBlock;
import com.krt.mod.entity.TrainEntity;
import com.krt.mod.block.ATPSignalBlockEntity;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 道岔控制系统
 * 负责管理和控制道岔的状态和切换逻辑
 */
public class SwitchControlSystem {
    private static final Map<World, SwitchControlSystem> INSTANCES = new HashMap<>();
    private final World world;
    
    // 存储道岔的控制状态
    private final Map<String, SwitchControlState> switchControlStates = new ConcurrentHashMap<>();
    
    // 道岔操作模式
    public enum SwitchMode {
        MANUAL,         // 手动控制
        SEMI_AUTOMATIC, // 半自动控制（通过信号系统请求）
        FULL_AUTOMATIC  // 全自动控制（根据列车运行计划自动控制）
    }
    
    // 道岔状态
    public enum SwitchState {
        NORMAL,         // 直股开通
        REVERSE,        // 弯股开通
        MOVING,         // 转换中
        LOCKED,         // 锁定状态
        FAILED          // 故障状态
    }
    
    // 控制参数
    private static final long SWITCH_OPERATION_TIME = 2000; // 道岔转换所需时间（毫秒）
    private static final long SWITCH_LOCK_TIME = 5000;     // 道岔锁定时间（毫秒）
    private static final int MAX_SWITCH_ATTEMPTS = 3;      // 最大尝试次数
    
    private SwitchControlSystem(World world) {
        this.world = world;
    }
    
    public static SwitchControlSystem getInstance(World world) {
        return INSTANCES.computeIfAbsent(world, SwitchControlSystem::new);
    }
    
    // 初始化道岔控制
    public void initializeSwitch(BlockPos switchPos) {
        String switchId = getSwitchId(switchPos);
        if (!switchControlStates.containsKey(switchId)) {
            switchControlStates.put(switchId, new SwitchControlState(switchPos));
            KRTMod.LOGGER.info("Initialized switch control for: {}", switchId);
        }
    }
    
    // 更新道岔控制系统
    public void update() {
        // 更新所有道岔的状态
        for (Map.Entry<String, SwitchControlState> entry : switchControlStates.entrySet()) {
            String switchId = entry.getKey();
            SwitchControlState state = entry.getValue();
            
            // 检查道岔是否存在
            if (world.getBlockState(state.getSwitchPos()).getBlock() instanceof SwitchTrackBlock) {
                state.update(world);
            } else {
                // 移除不存在的道岔控制状态
                switchControlStates.remove(switchId);
                KRTMod.LOGGER.info("Removed switch control for non-existent switch: {}", switchId);
            }
        }
        
        // 在全自动模式下，根据列车运行计划和位置自动控制道岔
        autoControlSwitches();
    }
    
    // 更新道岔关联的信号机状态
    private void updateRelatedSignalsForSwitch(BlockPos switchPos, boolean isSwitchInPosition) {
        // 查找与道岔关联的信号机
        List<BlockPos> relatedSignals = findRelatedSignals(switchPos);
        
        for (BlockPos signalPos : relatedSignals) {
            if (world.getBlockState(signalPos).getBlock() instanceof SignalBlock) {
                ATPSignalBlockEntity signalEntity = (ATPSignalBlockEntity) world.getBlockEntity(signalPos);
                if (signalEntity != null) {
                    // 根据道岔状态设置信号机状态
                    // isSwitchInPosition为true表示道岔已到位，可允许通行
                    // 为false表示道岔在转换中或故障，应禁止通行（红灯）
                    signalEntity.setAllowPass(isSwitchInPosition && getSwitchControlState(switchPos).getCurrentState() != SwitchState.FAILED);
                    KRTMod.LOGGER.info("Updated signal at {} to {}", signalPos, isSwitchInPosition ? "allow pass" : "stop");
                }
            }
        }
    }
    
    // 查找与道岔关联的信号机
    private List<BlockPos> findRelatedSignals(BlockPos switchPos) {
        List<BlockPos> signals = new ArrayList<>();
        // 搜索道岔周围一定范围内的信号机（例如20格范围内）
        int searchRadius = 20;
        
        for (int x = -searchRadius; x <= searchRadius; x++) {
            for (int z = -searchRadius; z <= searchRadius; z++) {
                BlockPos checkPos = switchPos.add(x, 0, z);
                if (world.getBlockState(checkPos).getBlock() instanceof SignalBlock) {
                    signals.add(checkPos);
                }
            }
        }
        
        return signals;
    }
    
    // 获取道岔控制状态
    public SwitchControlState getSwitchControlState(BlockPos switchPos) {
        String switchId = getSwitchId(switchPos);
        return switchControlStates.getOrDefault(switchId, null);
    }
    
    // 自动控制道岔
    private void autoControlSwitches() {
        // 收集世界中的所有列车
        List<TrainEntity> trains = world.getEntitiesByClass(TrainEntity.class, 
            world.getViewport(), entity -> true);
        
        // 对每个道岔进行自动控制决策
        for (SwitchControlState switchState : switchControlStates.values()) {
            if (switchState.getMode() == SwitchMode.FULL_AUTOMATIC) {
                // 找到接近该道岔的列车
                TrainEntity approachingTrain = findApproachingTrain(switchState.getSwitchPos(), 200); // 200米范围内
                
                if (approachingTrain != null) {
                    // 根据列车的目的地和运行方向确定道岔应处的位置
                    determineOptimalSwitchPosition(switchState, approachingTrain);
                }
            }
        }
    }
    
    // 找到接近道岔的列车
    private TrainEntity findApproachingTrain(BlockPos switchPos, double maxDistance) {
        List<TrainEntity> nearbyTrains = world.getEntitiesByClass(TrainEntity.class, 
            world.getViewport(), entity -> {
                double distance = entity.getPos().distanceTo(new net.minecraft.util.math.Vec3d(
                    switchPos.getX() + 0.5, switchPos.getY(), switchPos.getZ() + 0.5));
                return distance <= maxDistance;
            });
        
        // 按距离排序，返回最近的列车
        return nearbyTrains.stream()
            .min(Comparator.comparingDouble(train -> 
                train.getPos().distanceTo(new net.minecraft.util.math.Vec3d(
                    switchPos.getX() + 0.5, switchPos.getY(), switchPos.getZ() + 0.5))))
            .orElse(null);
    }
    
    // 确定道岔的最佳位置
    private void determineOptimalSwitchPosition(SwitchControlState switchState, TrainEntity train) {
        // 获取线路控制系统
        LineControlSystem lineSystem = LineControlSystem.getInstance(world);
        
        // 获取列车所属线路
        String lineId = train.getConsist() != null ? train.getConsist().getLineId() : "";
        
        if (!lineId.isEmpty()) {
            // 获取道岔在线路中的位置和连接关系
            List<String> connectedTracks = lineSystem.getConnectedTracks(lineId, switchState.getSwitchPos());
            
            if (connectedTracks.size() >= 2) {
                // 根据列车的目的地确定应选择的轨道
                String nextStation = train.getConsist() != null ? train.getConsist().getNextStation() : "";
                
                if (!nextStation.isEmpty()) {
                    // 获取到下一站的最佳轨道
                    String optimalTrack = lineSystem.getOptimalTrackToStation(lineId, switchState.getSwitchPos(), nextStation);
                    
                    if (optimalTrack != null) {
                        // 确定道岔应处的状态
                        SwitchState desiredState = determineDesiredSwitchState(switchState.getSwitchPos(), optimalTrack);
                        
                        // 如果需要，切换道岔
                        if (switchState.getCurrentState() != desiredState && switchState.getCurrentState() != SwitchState.MOVING) {
                            requestSwitchState(switchState.getSwitchPos(), desiredState, "Automatic control");
                        }
                    }
                }
            }
        }
    }
    
    // 确定所需的道岔状态
    private SwitchState determineDesiredSwitchState(BlockPos switchPos, String trackId) {
        // 这里应该根据道岔的具体配置和轨道ID确定所需的状态
        // 简化版：假设偶数轨道ID使用直股，奇数轨道ID使用弯股
        try {
            int trackNumber = Integer.parseInt(trackId.replaceAll("\\D+", ""));
            return trackNumber % 2 == 0 ? SwitchState.NORMAL : SwitchState.REVERSE;
        } catch (NumberFormatException e) {
            return SwitchState.NORMAL;
        }
    }
    
    // 请求道岔状态切换
    public boolean requestSwitchState(BlockPos switchPos, SwitchState desiredState, String requester) {
        String switchId = getSwitchId(switchPos);
        SwitchControlState switchState = switchControlStates.computeIfAbsent(switchId, k -> new SwitchControlState(switchPos));
        
        // 检查道岔是否可操作
        if (switchState.getCurrentState() == SwitchState.LOCKED || 
            switchState.getCurrentState() == SwitchState.FAILED || 
            (switchState.getCurrentState() == SwitchState.MOVING && System.currentTimeMillis() - switchState.getLastOperationTime() < SWITCH_OPERATION_TIME)) {
            KRTMod.LOGGER.warn("Cannot switch state for {}: current state is {}", switchId, switchState.getCurrentState());
            return false;
        }
        
        // 检查是否需要切换
        if (switchState.getCurrentState() == desiredState) {
            KRTMod.LOGGER.info("Switch {} already in desired state {}", switchId, desiredState);
            return true;
        }
        
        // 尝试切换道岔状态
        boolean success = attemptSwitchStateChange(switchPos, desiredState);
        
        if (success) {
            // 更新道岔控制状态
            switchState.setCurrentState(SwitchState.MOVING);
            switchState.setDesiredState(desiredState);
            switchState.setLastOperationTime(System.currentTimeMillis());
            switchState.setLastRequester(requester);
            
            KRTMod.LOGGER.info("Requested switch state change for {} to {} by {}", switchId, desiredState, requester);
            
            // 道岔转换中时，更新关联信号机为红灯
            updateRelatedSignalsForSwitch(switchPos, false);
        } else {
            // 增加尝试次数
            switchState.incrementAttemptCount();
            
            // 如果尝试次数过多，标记为故障
            if (switchState.getAttemptCount() >= MAX_SWITCH_ATTEMPTS) {
                switchState.setCurrentState(SwitchState.FAILED);
                KRTMod.LOGGER.error("Failed to switch state for {} after {} attempts", switchId, MAX_SWITCH_ATTEMPTS);
            }
        }
        
        return success;
    }
    
    // 尝试切换道岔状态
    private boolean attemptSwitchStateChange(BlockPos switchPos, SwitchState desiredState) {
        // 检查道岔是否存在
        if (!(world.getBlockState(switchPos).getBlock() instanceof SwitchTrackBlock)) {
            return false;
        }
        
        // 获取当前道岔方块
        SwitchTrackBlock switchBlock = (SwitchTrackBlock) world.getBlockState(switchPos).getBlock();
        
        try {
            // 更新道岔方块的状态
            // 注意：这里需要根据SwitchTrackBlock的具体实现来调用相应的方法
            // 以下是简化的示例代码
            if (desiredState == SwitchState.NORMAL) {
                switchBlock.setNormal(world, switchPos);
            } else if (desiredState == SwitchState.REVERSE) {
                switchBlock.setReverse(world, switchPos);
            }
            
            return true;
        } catch (Exception e) {
            KRTMod.LOGGER.error("Error changing switch state at {}: {}", switchPos, e.getMessage());
            return false;
        }
    }
    
    // 锁定道岔
    public boolean lockSwitch(BlockPos switchPos, String locker) {
        String switchId = getSwitchId(switchPos);
        SwitchControlState switchState = switchControlStates.computeIfAbsent(switchId, k -> new SwitchControlState(switchPos));
        
        if (switchState.getCurrentState() == SwitchState.LOCKED) {
            KRTMod.LOGGER.warn("Switch {} is already locked by {}", switchId, switchState.getLocker());
            return false;
        }
        
        if (switchState.getCurrentState() == SwitchState.MOVING) {
            KRTMod.LOGGER.warn("Cannot lock switch {} while it's moving", switchId);
            return false;
        }
        
        switchState.setCurrentState(SwitchState.LOCKED);
        switchState.setLocker(locker);
        switchState.setLockTime(System.currentTimeMillis());
        
        KRTMod.LOGGER.info("Switch {} locked by {}", switchId, locker);
        return true;
    }
    
    // 解锁道岔
    public boolean unlockSwitch(BlockPos switchPos, String unlocker) {
        String switchId = getSwitchId(switchPos);
        SwitchControlState switchState = switchControlStates.get(switchId);
        
        if (switchState == null) {
            KRTMod.LOGGER.warn("No control state found for switch {}", switchId);
            return false;
        }
        
        if (switchState.getCurrentState() != SwitchState.LOCKED) {
            KRTMod.LOGGER.warn("Switch {} is not locked", switchId);
            return false;
        }
        
        // 检查锁定时间是否已过
        if (System.currentTimeMillis() - switchState.getLockTime() < SWITCH_LOCK_TIME) {
            KRTMod.LOGGER.warn("Cannot unlock switch {} before lock time expires", switchId);
            return false;
        }
        
        switchState.setCurrentState(SwitchState.NORMAL); // 默认为直股状态
        switchState.setLocker("");
        switchState.setLockTime(0);
        
        KRTMod.LOGGER.info("Switch {} unlocked by {}", switchId, unlocker);
        return true;
    }
    
    // 设置道岔操作模式
    public void setSwitchMode(BlockPos switchPos, SwitchMode mode) {
        String switchId = getSwitchId(switchPos);
        SwitchControlState switchState = switchControlStates.computeIfAbsent(switchId, k -> new SwitchControlState(switchPos));
        
        switchState.setMode(mode);
        KRTMod.LOGGER.info("Set switch {} mode to {}", switchId, mode);
    }
    
    // 获取道岔状态
    public SwitchState getSwitchState(BlockPos switchPos) {
        String switchId = getSwitchId(switchPos);
        SwitchControlState switchState = switchControlStates.get(switchId);
        
        if (switchState != null) {
            return switchState.getCurrentState();
        }
        
        // 如果没有控制状态，检查实际道岔状态
        if (world.getBlockState(switchPos).getBlock() instanceof SwitchTrackBlock) {
            SwitchTrackBlock switchBlock = (SwitchTrackBlock) world.getBlockState(switchPos).getBlock();
            // 这里需要根据SwitchTrackBlock的具体实现来获取状态
            // 以下是简化的示例代码
            boolean isNormal = switchBlock.isNormal(world, switchPos);
            return isNormal ? SwitchState.NORMAL : SwitchState.REVERSE;
        }
        
        return SwitchState.FAILED;
    }
    
    // 获取道岔ID
    private String getSwitchId(BlockPos pos) {
        return String.format("switch_%d_%d_%d", pos.getX(), pos.getY(), pos.getZ());
    }
    
    // 注意：getSwitchControlState方法已在文件上方定义
    
    // 获取所有道岔控制状态
    public Collection<SwitchControlState> getAllSwitchControlStates() {
        return switchControlStates.values();
    }
    
    // 道岔控制状态类
    public static class SwitchControlState {
        private final BlockPos switchPos;
        private SwitchState currentState = SwitchState.NORMAL;
        private SwitchState desiredState = SwitchState.NORMAL;
        private SwitchMode mode = SwitchMode.SEMI_AUTOMATIC;
        private long lastOperationTime = 0;
        private String lastRequester = "";
        private String locker = "";
        private long lockTime = 0;
        private int attemptCount = 0;
        private long lastFailureTime = 0;
        
        public SwitchControlState(BlockPos switchPos) {
            this.switchPos = switchPos;
        }
        
        public BlockPos getSwitchPos() {
            return switchPos;
        }
        
        public SwitchState getCurrentState() {
            return currentState;
        }
        
        public void setCurrentState(SwitchState state) {
            this.currentState = state;
        }
        
        public SwitchState getDesiredState() {
            return desiredState;
        }
        
        public void setDesiredState(SwitchState state) {
            this.desiredState = state;
        }
        
        public SwitchMode getMode() {
            return mode;
        }
        
        public void setMode(SwitchMode mode) {
            this.mode = mode;
        }
        
        public long getLastOperationTime() {
            return lastOperationTime;
        }
        
        public void setLastOperationTime(long time) {
            this.lastOperationTime = time;
        }
        
        public String getLastRequester() {
            return lastRequester;
        }
        
        public void setLastRequester(String requester) {
            this.lastRequester = requester;
        }
        
        public String getLocker() {
            return locker;
        }
        
        public void setLocker(String locker) {
            this.locker = locker;
        }
        
        public long getLockTime() {
            return lockTime;
        }
        
        public void setLockTime(long time) {
            this.lockTime = time;
        }
        
        public int getAttemptCount() {
            return attemptCount;
        }
        
        public void incrementAttemptCount() {
            this.attemptCount++;
        }
        
        public void resetAttemptCount() {
            this.attemptCount = 0;
        }
        
        public long getLastFailureTime() {
            return lastFailureTime;
        }
        
        public void setLastFailureTime(long time) {
            this.lastFailureTime = time;
        }
        
        // 更新道岔状态
        public void update(World world) {
            // 检查道岔是否在转换中
            if (currentState == SwitchState.MOVING) {
                long elapsedTime = System.currentTimeMillis() - lastOperationTime;
                
                // 如果转换时间已到，更新状态
                if (elapsedTime >= SWITCH_OPERATION_TIME) {
                    // 检查实际道岔状态是否与预期一致
                    if (world.getBlockState(switchPos).getBlock() instanceof SwitchTrackBlock) {
                        SwitchTrackBlock switchBlock = (SwitchTrackBlock) world.getBlockState(switchPos).getBlock();
                        boolean isNormal = switchBlock.isNormal(world, switchPos);
                        SwitchState actualState = isNormal ? SwitchState.NORMAL : SwitchState.REVERSE;
                        
                        if (actualState == desiredState) {
                            currentState = desiredState;
                            resetAttemptCount();
                            KRTMod.LOGGER.info("Switch at {} successfully moved to {}", switchPos, desiredState);
                            
                            // 道岔成功切换到位后，更新关联信号机为允许通行状态
                            updateRelatedSignalsForSwitch(switchPos, true);
                        } else {
                            // 如果不一致，标记为故障
                            currentState = SwitchState.FAILED;
                            lastFailureTime = System.currentTimeMillis();
                            KRTMod.LOGGER.error("Switch at {} failed to move to {}. Actual state: {}", 
                                switchPos, desiredState, actualState);
                        }
                    }
                }
            }
            
            // 检查故障状态是否已恢复
            if (currentState == SwitchState.FAILED) {
                long elapsedTimeSinceFailure = System.currentTimeMillis() - lastFailureTime;
                
                // 如果故障时间超过一定时间，尝试恢复
                if (elapsedTimeSinceFailure >= 30000) { // 30秒后尝试恢复
                    // 尝试将道岔重置为直股状态
                    if (world.getBlockState(switchPos).getBlock() instanceof SwitchTrackBlock) {
                        SwitchTrackBlock switchBlock = (SwitchTrackBlock) world.getBlockState(switchPos).getBlock();
                        
                        try {
                            switchBlock.setNormal(world, switchPos);
                            currentState = SwitchState.NORMAL;
                            desiredState = SwitchState.NORMAL;
                            resetAttemptCount();
                            KRTMod.LOGGER.info("Switch at {} recovered from failure", switchPos);
                        } catch (Exception e) {
                            KRTMod.LOGGER.error("Failed to recover switch at {}: {}", switchPos, e.getMessage());
                        }
                    }
                }
            }
        }
        
        @Override
        public String toString() {
            return String.format("SwitchControlState{pos=%s, state=%s, mode=%s}",
                switchPos, currentState, mode);
        }
    }
}