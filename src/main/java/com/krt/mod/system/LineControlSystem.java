package com.krt.mod.system;

import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import com.krt.mod.KRTMod;
import com.krt.mod.block.TrackBlock;
import com.krt.mod.block.SwitchTrackBlock;
import com.krt.mod.block.SignalBlock;
import java.util.*;
import java.util.stream.Collectors;

public class LineControlSystem {
    private static final Map<String, LineInfo> lines = new HashMap<>();
    private static final Map<World, LineControlSystem> INSTANCES = new HashMap<>();

    private final World world;
    private final Set<BlockPos> checkedBlocks = new HashSet<>();

    private LineControlSystem(World world) {
        this.world = world;
    }

    public static LineControlSystem getInstance(World world) {
        return INSTANCES.computeIfAbsent(world, LineControlSystem::new);
    }

    // 创建新线路
    public void createLine(String lineId, String lineName) {
        if (!lines.containsKey(lineId)) {
            lines.put(lineId, new LineInfo(lineId, lineName));
            KRTMod.LOGGER.info("创建新线路: {}", lineName);
        }
    }

    // 添加车站到线路
    public void addStationToLine(String lineId, String stationId, String stationName, BlockPos pos) {
        LineInfo line = lines.get(lineId);
        if (line != null) {
            line.addStation(new StationInfo(stationId, stationName, pos));
            KRTMod.LOGGER.info("添加车站 {} 到线路 {}", stationName, line.getLineName());
        }
    }

    // 添加轨道到线路
    public void addTrackToLine(String lineId, BlockPos pos) {
        LineInfo line = lines.get(lineId);
        if (line != null && world.getBlockState(pos).getBlock() instanceof TrackBlock) {
            line.addTrack(pos);
        }
    }

    // 检查线路完整性
    public List<String> checkLineIntegrity(String lineId) {
        List<String> issues = new ArrayList<>();
        LineInfo line = lines.get(lineId);
        if (line == null) {
            issues.add("线路不存在");
            return issues;
        }

        // 检查车站数量
        if (line.getStations().size() < 2) {
            issues.add("线路至少需要2个车站");
        }

        // 检查线路是否连通
        boolean isConnected = checkLineConnectivity(line);
        if (!isConnected) {
            issues.add("线路不连通");
        }

        // 检查坡度限制
        List<BlockPos> steepSlopes = checkSlopes(line);
        if (!steepSlopes.isEmpty()) {
            issues.add("发现过陡坡度的轨道: " + steepSlopes.size() + "处");
        }

        // 检查转弯半径
        List<BlockPos> sharpCurves = checkCurves(line);
        if (!sharpCurves.isEmpty()) {
            issues.add("发现过小转弯半径的轨道: " + sharpCurves.size() + "处");
        }

        // 检查信号系统
        boolean hasSignals = checkSignalSystem(line);
        if (!hasSignals) {
            issues.add("线路缺少信号系统");
        }

        return issues;
    }

    // 检查线路连通性
    private boolean checkLineConnectivity(LineInfo line) {
        // 简化版：检查所有轨道是否连接
        if (line.getTracks().isEmpty()) {
            return false;
        }

        Set<BlockPos> connectedTracks = new HashSet<>();
        Queue<BlockPos> queue = new LinkedList<>();
        BlockPos firstTrack = line.getTracks().iterator().next();
        queue.add(firstTrack);
        connectedTracks.add(firstTrack);

        while (!queue.isEmpty()) {
            BlockPos current = queue.poll();
            // 检查周围的轨道
            for (BlockPos neighbor : getAdjacentBlocks(current)) {
                if (line.getTracks().contains(neighbor) && !connectedTracks.contains(neighbor)) {
                    connectedTracks.add(neighbor);
                    queue.add(neighbor);
                }
            }
        }

        return connectedTracks.size() == line.getTracks().size();
    }

    // 检查坡度限制
    private List<BlockPos> checkSlopes(LineInfo line) {
        List<BlockPos> steepSlopes = new ArrayList<>();
        for (BlockPos pos : line.getTracks()) {
            double slope = calculateSlope(pos);
            // 检查是否超过最大坡度限制（35‰）
            if (Math.abs(slope) > 35.0) {
                steepSlopes.add(pos);
            }
        }
        return steepSlopes;
    }

    // 检查转弯半径
    private List<BlockPos> checkCurves(LineInfo line) {
        List<BlockPos> sharpCurves = new ArrayList<>();
        List<BlockPos> sortedTracks = new ArrayList<>(line.getTracks());
        // 简化版：检查轨道方向变化
        for (int i = 1; i < sortedTracks.size() - 1; i++) {
            BlockPos prev = sortedTracks.get(i - 1);
            BlockPos curr = sortedTracks.get(i);
            BlockPos next = sortedTracks.get(i + 1);
            
            double angle = calculateAngle(prev, curr, next);
            // 如果角度变化过大，认为是小半径弯道
            if (Math.abs(angle) > 45.0) {
                sharpCurves.add(curr);
            }
        }
        return sharpCurves;
    }

    // 检查信号系统
    private boolean checkSignalSystem(LineInfo line) {
        // 简单检查是否有信号机
        for (BlockPos pos : line.getTracks()) {
            for (BlockPos neighbor : getAdjacentBlocks(pos, 10)) {
                if (world.getBlockState(neighbor).getBlock() instanceof SignalBlock) {
                    return true;
                }
            }
        }
        return false;
    }

    // 计算坡度
    private double calculateSlope(BlockPos pos) {
        // 简化版：检查相邻方块的高度差
        double maxSlope = 0;
        for (BlockPos neighbor : getAdjacentBlocks(pos)) {
            if (world.getBlockState(neighbor).getBlock() instanceof TrackBlock) {
                double heightDiff = Math.abs(pos.getY() - neighbor.getY());
                double distance = Math.sqrt(pos.getSquaredDistance(neighbor));
                double slope = (heightDiff / distance) * 1000; // 转换为‰
                maxSlope = Math.max(maxSlope, slope);
            }
        }
        return maxSlope;
    }

    // 计算角度
    private double calculateAngle(BlockPos a, BlockPos b, BlockPos c) {
        double ax = a.getX() - b.getX();
        double az = a.getZ() - b.getZ();
        double cx = c.getX() - b.getX();
        double cz = c.getZ() - b.getZ();

        double dot = ax * cx + az * cz;
        double magA = Math.sqrt(ax * ax + az * az);
        double magC = Math.sqrt(cx * cx + cz * cz);
        double cosAngle = dot / (magA * magC);
        
        // 防止计算误差
        cosAngle = Math.max(-1, Math.min(1, cosAngle));
        
        return Math.toDegrees(Math.acos(cosAngle));
    }

    // 获取相邻方块
    private List<BlockPos> getAdjacentBlocks(BlockPos pos) {
        return getAdjacentBlocks(pos, 1);
    }

    private List<BlockPos> getAdjacentBlocks(BlockPos pos, int radius) {
        List<BlockPos> neighbors = new ArrayList<>();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) continue;
                    neighbors.add(pos.add(dx, dy, dz));
                }
            }
        }
        return neighbors;
    }

    // 获取所有线路
    public static Collection<LineInfo> getAllLines() {
        return lines.values();
    }

    // 获取线路信息
    public LineInfo getLineInfo(String lineId) {
        return lines.get(lineId);
    }

    // 线路信息类
    public static class LineInfo {
        private final String lineId;
        private final String lineName;
        private final List<StationInfo> stations = new ArrayList<>();
        private final Set<BlockPos> tracks = new HashSet<>();
        private double maxSpeed = 80.0; // 默认最大运营速度80km/h

        public LineInfo(String lineId, String lineName) {
            this.lineId = lineId;
            this.lineName = lineName;
        }

        public String getLineId() {
            return lineId;
        }

        public String getLineName() {
            return lineName;
        }

        public List<StationInfo> getStations() {
            return stations;
        }

        public void addStation(StationInfo station) {
            stations.add(station);
        }

        public Set<BlockPos> getTracks() {
            return tracks;
        }

        public void addTrack(BlockPos pos) {
            tracks.add(pos);
        }

        public double getMaxSpeed() {
            return maxSpeed;
        }

        public void setMaxSpeed(double maxSpeed) {
            this.maxSpeed = maxSpeed;
        }
    }

    // 车站信息类
    public static class StationInfo {
        private final String stationId;
        private final String stationName;
        private final BlockPos position;

        public StationInfo(String stationId, String stationName, BlockPos position) {
            this.stationId = stationId;
            this.stationName = stationName;
            this.position = position;
        }

        public String getStationId() {
            return stationId;
        }

        public String getStationName() {
            return stationName;
        }

        public BlockPos getPosition() {
            return position;
        }
    }
}