package com.krt.mod.system;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import com.krt.mod.entity.TrainEntity;
import com.krt.mod.system.CBTCSystem.MobileBlockInfo;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * 区间闭塞管理系统
 * 负责管理铁路线路上的闭塞区间，确保列车之间保持安全距离
 * 支持固定闭塞和移动闭塞技术
 */
public class BlockSectionManagement {
    private static final Map<World, BlockSectionManagement> INSTANCES = new HashMap<>();
    private final World world;
    
    // 存储所有闭塞区间
    private final Map<String, BlockSection> blockSections = new ConcurrentHashMap<>();
    
    // 存储线路与闭塞区间的映射
    private final Map<String, List<String>> lineToBlockSections = new ConcurrentHashMap<>();
    
    // 存储闭塞区间之间的连接关系
    private final Map<String, List<String>> blockSectionConnections = new ConcurrentHashMap<>();
    
    // 存储各列车的移动闭塞信息
    private final Map<String, MobileBlockInfo> mobileBlockInfo = new ConcurrentHashMap<>();
    
    // 默认闭塞区间长度（米）
    private static final int DEFAULT_BLOCK_LENGTH = 200;
    
    // 安全距离（米）
    private static final int SAFETY_DISTANCE = 50;
    
    // 移动闭塞相关配置
    private static final boolean MOBILE_BLOCK_ENABLED = true;          // 是否启用移动闭塞
    private static final double EMERGENCY_BRAKING_DECELERATION = 3.5;  // 紧急制动减速度 m/s²
    private static final double REACTION_TIME = 2.0;                   // 反应时间秒
    private static final double TRAIN_LENGTH_FACTOR = 1.5;             // 列车长度倍数（移动闭塞计算用）
    private static final double MOBILE_BLOCK_UPDATE_INTERVAL = 0.1;    // 移动闭塞更新间隔（秒）
    
    // 原子时间戳，用于移动闭塞更新
    private final AtomicLong lastMobileBlockUpdateTime = new AtomicLong(0);
    
    private BlockSectionManagement(World world) {
        this.world = world;
    }
    
    public static BlockSectionManagement getInstance(World world) {
        return INSTANCES.computeIfAbsent(world, BlockSectionManagement::new);
    }
    
    // 创建闭塞区间
    public void createBlockSection(String lineId, String sectionId, BlockPos startPos, BlockPos endPos) {
        BlockSection section = new BlockSection(sectionId, lineId, startPos, endPos);
        blockSections.put(sectionId, section);
        
        // 添加到线路映射
        lineToBlockSections.computeIfAbsent(lineId, k -> new ArrayList<>()).add(sectionId);
        
        KRTMod.LOGGER.info("Created block section: {}", sectionId);
    }
    
    // 根据线路自动生成闭塞区间
    public void generateBlockSectionsForLine(String lineId, List<BlockPos> trackPositions) {
        if (trackPositions.size() < 2) {
            return;
        }
        
        // 清除旧的闭塞区间
        if (lineToBlockSections.containsKey(lineId)) {
            for (String sectionId : lineToBlockSections.get(lineId)) {
                blockSections.remove(sectionId);
            }
            lineToBlockSections.remove(lineId);
        }
        
        // 生成新的闭塞区间
        List<BlockPos> sortedPositions = sortTrackPositions(trackPositions);
        List<BlockSectionPoint> sectionPoints = new ArrayList<>();
        
        // 添加起点
        BlockPos firstPos = sortedPositions.get(0);
        sectionPoints.add(new BlockSectionPoint(firstPos, BlockSectionPoint.Type.START));
        
        double accumulatedDistance = 0;
        BlockPos previousPos = firstPos;
        
        // 计算中间点
        for (int i = 1; i < sortedPositions.size(); i++) {
            BlockPos currentPos = sortedPositions.get(i);
            double distance = calculateDistance(previousPos, currentPos);
            accumulatedDistance += distance;
            
            if (accumulatedDistance >= DEFAULT_BLOCK_LENGTH) {
                sectionPoints.add(new BlockSectionPoint(currentPos, BlockSectionPoint.Type.MID));
                accumulatedDistance = 0;
            }
            
            previousPos = currentPos;
        }
        
        // 添加终点
        BlockPos lastPos = sortedPositions.get(sortedPositions.size() - 1);
        sectionPoints.add(new BlockSectionPoint(lastPos, BlockSectionPoint.Type.END));
        
        // 创建闭塞区间
        for (int i = 0; i < sectionPoints.size() - 1; i++) {
            BlockSectionPoint startPoint = sectionPoints.get(i);
            BlockSectionPoint endPoint = sectionPoints.get(i + 1);
            
            String sectionId = String.format("%s_block_%d", lineId, i + 1);
            createBlockSection(lineId, sectionId, startPoint.position, endPoint.position);
        }
        
        KRTMod.LOGGER.info("Generated {} block sections for line: {}", sectionPoints.size() - 1, lineId);
    }
    
    // 排序轨道位置
    private List<BlockPos> sortTrackPositions(List<BlockPos> positions) {
        // 简化版：使用贪心算法排序轨道位置
        if (positions.isEmpty()) {
            return new ArrayList<>();
        }
        
        List<BlockPos> sorted = new ArrayList<>();
        Set<BlockPos> remaining = new HashSet<>(positions);
        
        // 从第一个点开始
        BlockPos current = positions.get(0);
        sorted.add(current);
        remaining.remove(current);
        
        while (!remaining.isEmpty()) {
            BlockPos closest = null;
            double minDistance = Double.MAX_VALUE;
            
            for (BlockPos pos : remaining) {
                double distance = calculateDistance(current, pos);
                if (distance < minDistance) {
                    minDistance = distance;
                    closest = pos;
                }
            }
            
            if (closest != null) {
                sorted.add(closest);
                remaining.remove(closest);
                current = closest;
            } else {
                break;
            }
        }
        
        return sorted;
    }
    
    // 计算两点之间的距离
    private double calculateDistance(BlockPos pos1, BlockPos pos2) {
        return Math.sqrt(
            Math.pow(pos1.getX() - pos2.getX(), 2) +
            Math.pow(pos1.getY() - pos2.getY(), 2) +
            Math.pow(pos1.getZ() - pos2.getZ(), 2)
        );
    }
    
    // 更新闭塞区间状态
    public void update() {
        // 清除失效的列车占用
        for (BlockSection section : blockSections.values()) {
            section.update();
        }
        
        // 检查列车位置并更新闭塞区间占用
        updateTrainOccupancy();
        
        // 更新信号机显示
        updateSignalDisplay();
    }
    
    // 更新列车占用情况
    private void updateTrainOccupancy() {
        // 清除所有占用
        for (BlockSection section : blockSections.values()) {
            section.clearTrainOccupancy();
        }
        
        // 收集世界中的所有列车
        List<TrainEntity> trains = world.getEntitiesByClass(TrainEntity.class, 
            world.getViewport(), entity -> true);
        
        // 检查每个列车所在的闭塞区间
        for (TrainEntity train : trains) {
            String trainId = train.getUuidAsString();
            BlockPos trainPos = train.getBlockPos();
            
            // 查找包含列车的闭塞区间
            for (BlockSection section : blockSections.values()) {
                if (section.containsBlockPos(trainPos)) {
                    section.setTrainOccupancy(trainId);
                    break;
                }
            }
            
            // 检查前方安全距离内的闭塞区间
            checkForwardSafetyDistance(train);
        }
    }
    
    // 检查前方安全距离
    private void checkForwardSafetyDistance(TrainEntity train) {
        String trainId = train.getUuidAsString();
        Vec3d direction = train.getRotationVector();
        BlockPos currentPos = train.getBlockPos();
        
        // 如果启用了移动闭塞，使用移动闭塞算法
        if (MOBILE_BLOCK_ENABLED) {
            updateMobileBlockForTrain(train);
            return;
        }
        
        // 固定闭塞模式下的安全距离检查
        double currentSpeed = train.getSpeed();
        // 计算基于速度的动态安全距离
        double dynamicSafetyDistance = SAFETY_DISTANCE + (currentSpeed * currentSpeed) / (2 * EMERGENCY_BRAKING_DECELERATION);
        
        // 计算前方安全距离位置
        BlockPos safetyPos = new BlockPos(
            currentPos.getX() + direction.x * dynamicSafetyDistance,
            currentPos.getY(),
            currentPos.getZ() + direction.z * dynamicSafetyDistance
        );
        
        // 查找从当前位置到安全距离位置之间的所有闭塞区间
        Set<BlockSection> forwardSections = new HashSet<>();
        
        for (BlockSection section : blockSections.values()) {
            if (section.intersectsLine(currentPos, safetyPos)) {
                forwardSections.add(section);
            }
        }
        
        // 标记这些区间为被该列车"预留"
        for (BlockSection section : forwardSections) {
            section.reserveForTrain(trainId);
        }
    }
    
    // 更新列车的移动闭塞信息
    private void updateMobileBlockForTrain(TrainEntity train) {
        // 检查是否需要更新（防止过于频繁）
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastMobileBlockUpdateTime.get() < MOBILE_BLOCK_UPDATE_INTERVAL * 1000) {
            return;
        }
        
        // 更新时间戳
        lastMobileBlockUpdateTime.set(currentTime);
        
        String trainId = train.getUuidAsString();
        double speed = train.getSpeed(); // m/s
        double trainLength = train.getLength(); // 米
        BlockPos pos = train.getBlockPos();
        Vec3d direction = train.getRotationVector();
        
        // 计算移动闭塞所需安全距离
        double safetyDistance = calculateMobileBlockDistance(speed, trainLength);
        
        // 计算移动闭塞区域的终点位置
        BlockPos blockEndPos = new BlockPos(
            pos.getX() + direction.x * safetyDistance,
            pos.getY(),
            pos.getZ() + direction.z * safetyDistance
        );
        
        // 创建移动闭塞信息
        MobileBlockInfo blockInfo = new MobileBlockInfo(
            trainId,
            pos,
            blockEndPos,
            speed,
            currentTime
        );
        
        // 存储移动闭塞信息
        mobileBlockInfo.put(trainId, blockInfo);
        
        // 更新闭塞区间状态
        updateBlockSectionsForMobileBlock(trainId, blockInfo);
    }
    
    // 计算移动闭塞所需距离
    private double calculateMobileBlockDistance(double speed, double trainLength) {
        // 移动闭塞距离 = 列车长度×系数 + 速度×反应时间 + 速度²/(2×减速度) + 安全余量
        double brakingDistance = (speed * speed) / (2 * EMERGENCY_BRAKING_DECELERATION);
        double reactionDistance = speed * REACTION_TIME;
        double trainFactorDistance = trainLength * TRAIN_LENGTH_FACTOR;
        
        return trainFactorDistance + reactionDistance + brakingDistance + SAFETY_DISTANCE;
    }
    
    // 根据移动闭塞更新闭塞区间状态
    private void updateBlockSectionsForMobileBlock(String trainId, MobileBlockInfo blockInfo) {
        // 清除所有该列车的预留状态
        for (BlockSection section : blockSections.values()) {
            section.clearTrainReservation(trainId);
        }
        
        // 计算移动闭塞区域的起点（列车前方）
        BlockPos startPos = blockInfo.startPos;
        BlockPos endPos = blockInfo.endPos;
        
        // 查找与移动闭塞区域相交的闭塞区间
        for (BlockSection section : blockSections.values()) {
            if (section.intersectsLine(startPos, endPos)) {
                // 标记这些区间为该列车预留
                section.reserveForTrain(trainId);
            }
        }
    }
    
    // 添加闭塞区间连接关系
    public void addBlockSectionConnection(String fromSectionId, String toSectionId) {
        blockSectionConnections.computeIfAbsent(fromSectionId, k -> new ArrayList<>()).add(toSectionId);
    }
    
    // 获取闭塞区间的下一区间
    public List<BlockSection> getNextBlockSections(String sectionId) {
        List<BlockSection> nextSections = new ArrayList<>();
        List<String> nextSectionIds = blockSectionConnections.getOrDefault(sectionId, Collections.emptyList());
        
        for (String nextId : nextSectionIds) {
            BlockSection section = blockSections.get(nextId);
            if (section != null) {
                nextSections.add(section);
            }
        }
        
        return nextSections;
    }
    
    // 获取可用的闭塞区间路径
    public List<List<BlockSection>> findAvailablePaths(String startSectionId, String endSectionId, TrainEntity train) {
        List<List<BlockSection>> paths = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        List<BlockSection> currentPath = new ArrayList<>();
        
        BlockSection startSection = getBlockSection(startSectionId);
        if (startSection != null) {
            dfsFindPath(startSection, endSectionId, visited, currentPath, paths, train);
        }
        
        return paths;
    }
    
    // 深度优先搜索查找路径
    private void dfsFindPath(BlockSection currentSection, String endSectionId, Set<String> visited, 
                           List<BlockSection> currentPath, List<List<BlockSection>> paths, TrainEntity train) {
        if (visited.contains(currentSection.getId())) {
            return;
        }
        
        // 检查当前区间是否可用
        if (currentSection.isOccupied() || currentSection.isReservedByOtherTrain(train.getUuidAsString())) {
            return;
        }
        
        visited.add(currentSection.getId());
        currentPath.add(currentSection);
        
        // 找到终点
        if (currentSection.getId().equals(endSectionId)) {
            paths.add(new ArrayList<>(currentPath));
        } else {
            // 继续搜索下一区间
            for (BlockSection nextSection : getNextBlockSections(currentSection.getId())) {
                dfsFindPath(nextSection, endSectionId, visited, currentPath, paths, train);
            }
        }
        
        // 回溯
        currentPath.remove(currentPath.size() - 1);
        visited.remove(currentSection.getId());
    }
    
    // 获取区间之间的最小安全间隔时间
    public double getMinHeadwayTime(double speed, double blockLength) {
        // 计算理论最小间隔时间
        double brakingDistance = (speed * speed) / (2 * EMERGENCY_BRAKING_DECELERATION);
        double totalDistance = brakingDistance + blockLength + SAFETY_DISTANCE;
        return totalDistance / speed;
    }
    
    // 更新信号机显示
    private void updateSignalDisplay() {
        // 这里应该更新相关信号机的显示状态
        // 暂时省略具体实现，因为需要与信号机方块的具体实现交互
    }
    
    // 获取列车前方的闭塞区间状态
    public List<BlockSectionStatus> getBlockStatusAhead(TrainEntity train, int lookAheadDistance) {
        List<BlockSectionStatus> statusList = new ArrayList<>();
        Vec3d direction = train.getRotationVector();
        BlockPos currentPos = train.getBlockPos();
        
        // 计算最远查看位置
        BlockPos lookAheadPos = new BlockPos(
            currentPos.getX() + direction.x * lookAheadDistance,
            currentPos.getY(),
            currentPos.getZ() + direction.z * lookAheadDistance
        );
        
        // 查找从当前位置到最远位置之间的所有闭塞区间
        List<BlockSection> aheadSections = new ArrayList<>();
        
        for (BlockSection section : blockSections.values()) {
            if (section.intersectsLine(currentPos, lookAheadPos)) {
                aheadSections.add(section);
            }
        }
        
        // 按距离排序
        aheadSections.sort(Comparator.comparingDouble(s -> calculateDistance(currentPos, s.getStartPos())));
        
        // 转换为状态对象
        for (BlockSection section : aheadSections) {
            BlockSectionStatus status = new BlockSectionStatus();
            status.sectionId = section.getId();
            status.distance = calculateDistance(currentPos, section.getStartPos());
            status.isOccupied = section.isOccupied();
            status.isReserved = section.isReservedByOtherTrain(train.getUuidAsString());
            
            statusList.add(status);
        }
        
        return statusList;
    }
    
    // 获取线路的闭塞区间
    public List<BlockSection> getBlockSectionsForLine(String lineId) {
        if (!lineToBlockSections.containsKey(lineId)) {
            return new ArrayList<>();
        }
        
        return lineToBlockSections.get(lineId).stream()
            .map(blockSections::get)
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
    }
    
    // 获取指定闭塞区间
    public BlockSection getBlockSection(String sectionId) {
        return blockSections.get(sectionId);
    }
    
    // 闭塞区间类
    public static class BlockSection {
        private final String id;
        private final String lineId;
        private final BlockPos startPos;
        private final BlockPos endPos;
        private final double length;
        
        // 区间状态
        private String occupyingTrainId = null;
        private Set<String> reservedTrainIds = new HashSet<>();
        private long lastUpdateTime = System.currentTimeMillis();
        
        // 附加信息
        private boolean hasSwitch = false;        // 是否包含道岔
        private boolean isStationArea = false;    // 是否为车站区域
        private boolean isSpecialZone = false;    // 是否为特殊区域
        private double gradient = 0.0;            // 坡度（百分比）
        private double speedLimit = 80.0;         // 速度限制（m/s）
        private int trackNumber = 1;              // 股道号
        
        public BlockSection(String id, String lineId, BlockPos startPos, BlockPos endPos) {
            this.id = id;
            this.lineId = lineId;
            this.startPos = startPos;
            this.endPos = endPos;
            this.length = calculateDistance(startPos, endPos);
        }
        
        public String getId() {
            return id;
        }
        
        public String getLineId() {
            return lineId;
        }
        
        public BlockPos getStartPos() {
            return startPos;
        }
        
        public BlockPos getEndPos() {
            return endPos;
        }
        
        public double getLength() {
            return length;
        }
        
        // 获取区间中心位置
        public BlockPos getCenterPos() {
            return new BlockPos(
                (startPos.getX() + endPos.getX()) / 2,
                (startPos.getY() + endPos.getY()) / 2,
                (startPos.getZ() + endPos.getZ()) / 2
            );
        }
        
        // 获取区间状态（占用/预留/空闲）
        public SectionStatus getStatus() {
            if (occupyingTrainId != null) {
                return SectionStatus.OCCUPIED;
            } else if (!reservedTrainIds.isEmpty()) {
                return SectionStatus.RESERVED;
            } else {
                return SectionStatus.FREE;
            }
        }
        
        // 检查位置是否在闭塞区间内
        public boolean containsBlockPos(BlockPos pos) {
            // 改进的检查方法：使用线段距离检查
            // 先进行包围盒快速排除
            int minX = Math.min(startPos.getX(), endPos.getX()) - 1;
            int maxX = Math.max(startPos.getX(), endPos.getX()) + 1;
            int minY = Math.min(startPos.getY(), endPos.getY()) - 1;
            int maxY = Math.max(startPos.getY(), endPos.getY()) + 1;
            int minZ = Math.min(startPos.getZ(), endPos.getZ()) - 1;
            int maxZ = Math.max(startPos.getZ(), endPos.getZ()) + 1;
            
            if (!(pos.getX() >= minX && pos.getX() <= maxX &&
                  pos.getY() >= minY && pos.getY() <= maxY &&
                  pos.getZ() >= minZ && pos.getZ() <= maxZ)) {
                return false;
            }
            
            // 计算点到线段的距离
            double distance = pointToLineDistance(pos, startPos, endPos);
            return distance <= 1.0; // 距离小于等于1米认为在区间内
        }
        
        // 计算点到线段的距离
        private double pointToLineDistance(BlockPos point, BlockPos lineStart, BlockPos lineEnd) {
            Vec3d p = new Vec3d(point.getX(), point.getY(), point.getZ());
            Vec3d a = new Vec3d(lineStart.getX(), lineStart.getY(), lineStart.getZ());
            Vec3d b = new Vec3d(lineEnd.getX(), lineEnd.getY(), lineEnd.getZ());
            
            Vec3d ap = p.subtract(a);
            Vec3d ab = b.subtract(a);
            double t = ap.dotProduct(ab) / ab.dotProduct(ab);
            
            // 确保t在[0,1]范围内
            t = Math.max(0, Math.min(1, t));
            
            Vec3d closest = a.add(ab.multiply(t));
            return closest.distanceTo(p);
        }
        
        // 检查线段是否与闭塞区间相交
        public boolean intersectsLine(BlockPos start, BlockPos end) {
            // 更精确的线段相交检测
            return containsBlockPos(start) || containsBlockPos(end) || 
                   lineIntersectsLine(start, end, this.startPos, this.endPos);
        }
        
        // 检查两条线段是否相交
        private boolean lineIntersectsLine(BlockPos a1, BlockPos a2, BlockPos b1, BlockPos b2) {
            // 简化的2D相交检查（忽略Y轴）
            double d1 = crossProduct(a2.getX() - a1.getX(), a2.getZ() - a1.getZ(), 
                                    b1.getX() - a1.getX(), b1.getZ() - a1.getZ());
            double d2 = crossProduct(a2.getX() - a1.getX(), a2.getZ() - a1.getZ(), 
                                    b2.getX() - a1.getX(), b2.getZ() - a1.getZ());
            double d3 = crossProduct(b2.getX() - b1.getX(), b2.getZ() - b1.getZ(), 
                                    a1.getX() - b1.getX(), a1.getZ() - b1.getZ());
            double d4 = crossProduct(b2.getX() - b1.getX(), b2.getZ() - b1.getZ(), 
                                    a2.getX() - b1.getX(), a2.getZ() - b1.getZ());
            
            return ((d1 < 0 && d2 > 0) || (d1 > 0 && d2 < 0)) && 
                   ((d3 < 0 && d4 > 0) || (d3 > 0 && d4 < 0));
        }
        
        // 计算叉积
        private double crossProduct(double x1, double z1, double x2, double z2) {
            return x1 * z2 - x2 * z1;
        }
        
        // 设置列车占用
        public void setTrainOccupancy(String trainId) {
            this.occupyingTrainId = trainId;
            this.lastUpdateTime = System.currentTimeMillis();
        }
        
        // 清除列车占用
        public void clearTrainOccupancy() {
            // 如果超过一定时间没有更新，自动清除占用
            if (System.currentTimeMillis() - lastUpdateTime > 5000) { // 5秒
                this.occupyingTrainId = null;
            }
        }
        
        // 为列车预留
        public void reserveForTrain(String trainId) {
            this.reservedTrainIds.add(trainId);
            this.lastUpdateTime = System.currentTimeMillis();
        }
        
        // 清除特定列车的预留
        public void clearTrainReservation(String trainId) {
            this.reservedTrainIds.remove(trainId);
        }
        
        // 清除所有预留
        public void clearAllReservations() {
            this.reservedTrainIds.clear();
        }
        
        // 检查是否被占用
        public boolean isOccupied() {
            return occupyingTrainId != null;
        }
        
        // 检查是否被其他列车预留
        public boolean isReservedByOtherTrain(String trainId) {
            for (String reservedId : reservedTrainIds) {
                if (!reservedId.equals(trainId)) {
                    return true;
                }
            }
            return false;
        }
        
        // 检查是否被特定列车预留
        public boolean isReservedByTrain(String trainId) {
            return reservedTrainIds.contains(trainId);
        }
        
        // 获取占用列车ID
        public String getOccupyingTrainId() {
            return occupyingTrainId;
        }
        
        // 获取所有预留列车ID
        public Set<String> getReservedTrainIds() {
            return new HashSet<>(reservedTrainIds);
        }
        
        // 更新
        public void update() {
            clearTrainOccupancy();
            // 清理过期预留
            if (System.currentTimeMillis() - lastUpdateTime > 10000) { // 10秒
                this.reservedTrainIds.clear();
            }
        }
        
        // 获取区间的可用速度（考虑各种限制）
        public double getAvailableSpeed() {
            // 如果被占用或预留，速度为0
            if (isOccupied() || !reservedTrainIds.isEmpty()) {
                return 0.0;
            }
            return speedLimit;
        }
        
        // 设置区间属性
        public void setHasSwitch(boolean hasSwitch) {
            this.hasSwitch = hasSwitch;
        }
        
        public void setStationArea(boolean stationArea) {
            isStationArea = stationArea;
        }
        
        public void setSpecialZone(boolean specialZone) {
            isSpecialZone = specialZone;
        }
        
        public void setGradient(double gradient) {
            this.gradient = gradient;
        }
        
        public void setSpeedLimit(double speedLimit) {
            this.speedLimit = speedLimit;
        }
        
        public void setTrackNumber(int trackNumber) {
            this.trackNumber = trackNumber;
        }
        
        // 获取区间属性
        public boolean hasSwitch() {
            return hasSwitch;
        }
        
        public boolean isStationArea() {
            return isStationArea;
        }
        
        public boolean isSpecialZone() {
            return isSpecialZone;
        }
        
        public double getGradient() {
            return gradient;
        }
        
        public double getSpeedLimit() {
            return speedLimit;
        }
        
        public int getTrackNumber() {
            return trackNumber;
        }
        
        // 区间状态枚举
        public enum SectionStatus {
            FREE,      // 空闲
            OCCUPIED,  // 占用
            RESERVED   // 预留
        }
        
        @Override
        public String toString() {
            return String.format("BlockSection{id='%s', lineId='%s', occupied=%s}",
                id, lineId, occupyingTrainId != null);
        }
    }
    
    // 闭塞区间点类
    private static class BlockSectionPoint {
        private final BlockPos position;
        private final Type type;
        
        public BlockSectionPoint(BlockPos position, Type type) {
            this.position = position;
            this.type = type;
        }
        
        public enum Type {
            START, MID, END
        }
    }
    
    // 闭塞区间状态类
    public static class BlockSectionStatus {
        public String sectionId;
        public double distance;
        public boolean isOccupied;
        public boolean isReserved;
        public double speedLimit;
        public boolean hasSwitch;
        public boolean isStationArea;
        public double gradient;
        public int trackNumber;
        public String occupyingTrainId;
        
        @Override
        public String toString() {
            return String.format("BlockSectionStatus{sectionId='%s', distance=%.2f, occupied=%s, reserved=%s, speedLimit=%.1f}",
                sectionId, distance, isOccupied, isReserved, speedLimit);
        }
    }
    
    // 移动闭塞信息类
    public static class MobileBlockInfo {
        public final String trainId;
        public final BlockPos startPos;
        public final BlockPos endPos;
        public final double speed;
        public final long timestamp;
        
        public MobileBlockInfo(String trainId, BlockPos startPos, BlockPos endPos, double speed, long timestamp) {
            this.trainId = trainId;
            this.startPos = startPos;
            this.endPos = endPos;
            this.speed = speed;
            this.timestamp = timestamp;
        }
        
        // 获取移动闭塞区域长度
        public double getLength() {
            return calculateDistance(startPos, endPos);
        }
        
        // 检查是否过期
        public boolean isExpired() {
            return System.currentTimeMillis() - timestamp > 2000; // 2秒过期
        }
    }
}