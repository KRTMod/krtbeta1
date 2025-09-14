package com.krt.mod.system;

import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import com.krt.mod.KRTMod;
import com.krt.mod.block.TrackBlock;
import com.krt.mod.entity.TrainEntity;
import java.util.*;
import java.util.stream.Collectors;

public class DepotManagementSystem {
    private static final Map<World, DepotManagementSystem> INSTANCES = new HashMap<>();
    private final World world;
    private final Map<String, DepotInfo> depots = new HashMap<>();

    private DepotManagementSystem(World world) {
        this.world = world;
    }

    public static DepotManagementSystem getInstance(World world) {
        return INSTANCES.computeIfAbsent(world, DepotManagementSystem::new);
    }

    // 创建新车厂
    public boolean createDepot(String depotId, String depotName, BlockPos pos) {
        if (depots.containsKey(depotId)) {
            KRTMod.LOGGER.warn("车厂ID '{}' 已存在", depotId);
            return false;
        }

        DepotInfo depot = new DepotInfo(depotId, depotName, pos);
        depots.put(depotId, depot);
        KRTMod.LOGGER.info("创建新车厂: {}", depotName);
        return true;
    }

    // 注册停车库
    public boolean registerParkingArea(String depotId, BlockPos startPos, BlockPos endPos) {
        DepotInfo depot = depots.get(depotId);
        if (depot == null) {
            KRTMod.LOGGER.warn("车厂 '{}' 不存在", depotId);
            return false;
        }

        // 检查停车库是否足够长（至少10格，相当于10米）
        double length = calculateDistance(startPos, endPos);
        if (length < 10.0) {
            KRTMod.LOGGER.warn("停车库长度不足，至少需要10米");
            return false;
        }

        ParkingArea parkingArea = new ParkingArea(startPos, endPos);
        depot.addParkingArea(parkingArea);
        KRTMod.LOGGER.info("停车库已注册到车厂: {}", depot.getDepotName());
        return true;
    }

    // 注册洗车库
    public boolean registerWashArea(String depotId, BlockPos pos) {
        DepotInfo depot = depots.get(depotId);
        if (depot == null) {
            KRTMod.LOGGER.warn("车厂 '{}' 不存在", depotId);
            return false;
        }

        WashArea washArea = new WashArea(pos);
        depot.addWashArea(washArea);
        KRTMod.LOGGER.info("洗车库已注册到车厂: {}", depot.getDepotName());
        return true;
    }

    // 注册检修库
    public boolean registerMaintenanceArea(String depotId, BlockPos pos) {
        DepotInfo depot = depots.get(depotId);
        if (depot == null) {
            KRTMod.LOGGER.warn("车厂 '{}' 不存在", depotId);
            return false;
        }

        MaintenanceArea maintenanceArea = new MaintenanceArea(pos);
        depot.addMaintenanceArea(maintenanceArea);
        KRTMod.LOGGER.info("检修库已注册到车厂: {}", depot.getDepotName());
        return true;
    }

    // 注册试车线
    public boolean registerTestTrack(String depotId, BlockPos startPos, BlockPos endPos) {
        DepotInfo depot = depots.get(depotId);
        if (depot == null) {
            KRTMod.LOGGER.warn("车厂 '{}' 不存在", depotId);
            return false;
        }

        TestTrack testTrack = new TestTrack(startPos, endPos);
        depot.addTestTrack(testTrack);
        KRTMod.LOGGER.info("试车线已注册到车厂: {}", depot.getDepotName());
        return true;
    }

    // 检查车厂完整性
    public List<String> checkDepotIntegrity(String depotId) {
        List<String> issues = new ArrayList<>();
        DepotInfo depot = depots.get(depotId);
        if (depot == null) {
            issues.add("车厂不存在");
            return issues;
        }

        // 检查必须的设施
        if (depot.getParkingAreas().isEmpty()) {
            issues.add("车厂缺少停车库");
        }

        if (depot.getWashAreas().isEmpty()) {
            issues.add("车厂缺少洗车库");
        }

        if (depot.getTestTracks().isEmpty()) {
            issues.add("车厂缺少试车线");
        }

        // 检查停车库长度
        for (ParkingArea parkingArea : depot.getParkingAreas()) {
            double length = calculateDistance(parkingArea.getStartPos(), parkingArea.getEndPos());
            if (length < 10.0) {
                issues.add("停车库长度不足，至少需要10米");
                break;
            }
        }

        // 检查轨道连接
        boolean hasConnectedTracks = checkConnectedTracks(depot);
        if (!hasConnectedTracks) {
            issues.add("车厂与主线轨道未连接");
        }

        return issues;
    }

    // 检查车厂轨道连接情况
    private boolean checkConnectedTracks(DepotInfo depot) {
        // 简化版：检查车厂附近是否有轨道
        BlockPos depotPos = depot.getDepotPos();
        for (BlockPos pos : BlockPos.iterateOutwards(depotPos, 50, 10, 50)) {
            if (world.getBlockState(pos).getBlock() instanceof TrackBlock) {
                return true;
            }
        }
        return false;
    }

    // 计算两点之间的距离
    private double calculateDistance(BlockPos pos1, BlockPos pos2) {
        double dx = pos1.getX() - pos2.getX();
        double dy = pos1.getY() - pos2.getY();
        double dz = pos1.getZ() - pos2.getZ();
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    // 安排列车入库
    public String assignTrainToDepot(TrainEntity train) {
        // 查找有空闲停车位的车厂
        for (DepotInfo depot : depots.values()) {
            if (hasFreeParkingSpace(depot)) {
                // 分配列车到该车厂
                train.setCurrentDepot(depot.getDepotId());
                if (train.getDriver() != null) {
                    train.getDriver().sendMessage(Text.literal("已分配至车厂: " + depot.getDepotName()), false);
                }
                KRTMod.LOGGER.info("列车 '{}' 已分配至车厂: {}", train.getTrainId(), depot.getDepotName());
                return depot.getDepotId();
            }
        }
        return null;
    }

    // 检查车厂是否有空闲停车位
    private boolean hasFreeParkingSpace(DepotInfo depot) {
        // 简化版：假设每个停车库都有足够的空间
        return !depot.getParkingAreas().isEmpty();
    }

    // 获取车厂信息
    public DepotInfo getDepotInfo(String depotId) {
        return depots.get(depotId);
    }

    // 获取所有车厂
    public Collection<DepotInfo> getAllDepots() {
        return depots.values();
    }

    // 车厂信息类
    public static class DepotInfo {
        private final String depotId;
        private final String depotName;
        private final BlockPos depotPos;
        private final List<ParkingArea> parkingAreas = new ArrayList<>();
        private final List<WashArea> washAreas = new ArrayList<>();
        private final List<MaintenanceArea> maintenanceAreas = new ArrayList<>();
        private final List<TestTrack> testTracks = new ArrayList<>();

        public DepotInfo(String depotId, String depotName, BlockPos depotPos) {
            this.depotId = depotId;
            this.depotName = depotName;
            this.depotPos = depotPos;
        }

        public String getDepotId() {
            return depotId;
        }

        public String getDepotName() {
            return depotName;
        }

        public BlockPos getDepotPos() {
            return depotPos;
        }

        public List<ParkingArea> getParkingAreas() {
            return parkingAreas;
        }

        public void addParkingArea(ParkingArea parkingArea) {
            parkingAreas.add(parkingArea);
        }

        public List<WashArea> getWashAreas() {
            return washAreas;
        }

        public void addWashArea(WashArea washArea) {
            washAreas.add(washArea);
        }

        public List<MaintenanceArea> getMaintenanceAreas() {
            return maintenanceAreas;
        }

        public void addMaintenanceArea(MaintenanceArea maintenanceArea) {
            maintenanceAreas.add(maintenanceArea);
        }

        public List<TestTrack> getTestTracks() {
            return testTracks;
        }

        public void addTestTrack(TestTrack testTrack) {
            testTracks.add(testTrack);
        }
    }

    // 停车库类
    public static class ParkingArea {
        private final BlockPos startPos;
        private final BlockPos endPos;

        public ParkingArea(BlockPos startPos, BlockPos endPos) {
            this.startPos = startPos;
            this.endPos = endPos;
        }

        public BlockPos getStartPos() {
            return startPos;
        }

        public BlockPos getEndPos() {
            return endPos;
        }
    }

    // 洗车库类
    public static class WashArea {
        private final BlockPos pos;

        public WashArea(BlockPos pos) {
            this.pos = pos;
        }

        public BlockPos getPos() {
            return pos;
        }
    }

    // 检修库类
    public static class MaintenanceArea {
        private final BlockPos pos;

        public MaintenanceArea(BlockPos pos) {
            this.pos = pos;
        }

        public BlockPos getPos() {
            return pos;
        }
    }

    // 试车线类
    public static class TestTrack {
        private final BlockPos startPos;
        private final BlockPos endPos;

        public TestTrack(BlockPos startPos, BlockPos endPos) {
            this.startPos = startPos;
            this.endPos = endPos;
        }

        public BlockPos getStartPos() {
            return startPos;
        }

        public BlockPos getEndPos() {
            return endPos;
        }
    }
}