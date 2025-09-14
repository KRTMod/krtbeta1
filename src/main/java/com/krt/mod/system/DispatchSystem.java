package com.krt.mod.system;

import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import com.krt.mod.KRTMod;
import com.krt.mod.block.SignalBlock;
import com.krt.mod.block.PlatformDoorBlock;
import com.krt.mod.entity.TrainEntity;
import java.util.*;
import java.util.stream.Collectors;

public class DispatchSystem {
    private static final Map<World, DispatchSystem> INSTANCES = new HashMap<>();
    private final World world;
    private final List<TrainEntity> trains = new ArrayList<>();
    private final List<BlockPos> signalMachines = new ArrayList<>();
    private final List<BlockPos> platformDoors = new ArrayList<>();
    private final LineControlSystem lineControlSystem;
    private DispatchMode dispatchMode = DispatchMode.SYSTEM;
    private final AISuggestionSystem aiSystem = new AISuggestionSystem();

    private DispatchSystem(World world) {
        this.world = world;
        this.lineControlSystem = LineControlSystem.getInstance(world);
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
        // 更新所有列车状态
        updateTrains();
        // 更新所有信号机状态
        updateSignals();
        // 更新所有屏蔽门状态
        updatePlatformDoors();
        // 如果是系统控制模式，执行AI调度
        if (dispatchMode == DispatchMode.SYSTEM) {
            runAIDispatch();
        }
        // 记录运行日志
        logSystemStatus();
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
                    double distance = train.getPos().squaredDistance(station.getPosition().getX() + 0.5, 
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
            SignalBlock.setSignalState(world, signalPos, newState);
        }
    }

    // 检查信号机前方是否有列车
    private boolean checkTrainAhead(BlockPos signalPos) {
        // 简化版：检查信号机前方200米内是否有列车
        for (TrainEntity train : trains) {
            double distance = train.getPos().squaredDistance(signalPos.getX() + 0.5, 
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
            PlatformDoorBlock.DoorState currentState = world.getBlockState(doorPos).get(PlatformDoorBlock.DOOR_STATE);
            
            if (hasTrainStopped && currentState == PlatformDoorBlock.DoorState.CLOSED) {
                // 打开屏蔽门
                PlatformDoorBlock.openDoor(world, doorPos);
            } else if (!hasTrainStopped && currentState == PlatformDoorBlock.DoorState.OPEN && PlatformDoorBlock.canAutoClose(world, doorPos)) {
                // 关闭屏蔽门
                PlatformDoorBlock.closeDoor(world, doorPos);
            }
        }
    }

    // 检查屏蔽门附近是否有列车停靠
    private boolean checkTrainStoppedNearby(BlockPos doorPos) {
        for (TrainEntity train : trains) {
            double distance = train.getPos().squaredDistance(doorPos.getX() + 0.5, 
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
                PlatformDoorBlock.openDoor(world, doorPos);
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
    }

    // 检查列车密度
    private void checkTrainDensity() {
        // 简化版：根据线路上的列车数量调整
    }

    // 检查线路阻塞情况
    private void checkLineCongestion() {
        // 简化版：检查线路上是否有长时间停留在同一位置的列车
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
        SYSTEM("系统控制");

        private final String displayName;

        DispatchMode(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    // AI建议系统
    private static class AISuggestionSystem {
        private final Random random = new Random();
        private final List<String> suggestions = Arrays.asList(
                "当前线路运营正常，建议保持现有发车频率。",
                "注意3号线列车密度较高，建议适当增加行车间隔。",
                "1号线有列车健康状况不佳，建议安排回厂检修。",
                "高峰期即将到来，建议增加2号线的列车数量。",
                "车站客流较大，建议增开区间车缓解压力。",
                "设备运行正常，暂无异常情况报告。",
                "部分区段信号系统负载较高，建议加强监控。"
        );

        public String getSuggestion(DispatchSystem dispatchSystem) {
            // 简化版：随机返回一条建议
            // 实际应用中应该根据系统状态生成有针对性的建议
            return suggestions.get(random.nextInt(suggestions.size()));
        }
    }
}