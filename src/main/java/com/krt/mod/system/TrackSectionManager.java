package com.krt.mod.system;

import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import com.krt.mod.block.TrackBlock;
import com.krt.mod.block.SwitchTrackBlock;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 轨道区段管理系统
 * 负责管理和划分轨道区段，是地铁信号系统的基础组件
 */
public class TrackSectionManager {
    private static final Map<World, TrackSectionManager> INSTANCES = new HashMap<>();
    private final World world;
    private final Map<String, TrackSection> sections = new HashMap<>();
    private final Set<BlockPos> trackPositions = new HashSet<>();

    private TrackSectionManager(World world) {
        this.world = world;
    }

    /**
     * 获取实例（单例模式）
     */
    public static TrackSectionManager getInstance(World world) {
        return INSTANCES.computeIfAbsent(world, TrackSectionManager::new);
    }

    /**
     * 创建新的轨道区段
     */
    public void createSection(String sectionId, String sectionName, List<BlockPos> positions) {
        // 检查位置是否都是轨道方块
        for (BlockPos pos : positions) {
            if (!(world.getBlockState(pos).getBlock() instanceof TrackBlock || 
                 world.getBlockState(pos).getBlock() instanceof SwitchTrackBlock)) {
                LogSystem.error("创建区段失败: 位置 " + pos + " 不是轨道方块");
                return;
            }
            trackPositions.add(pos);
        }
        
        TrackSection section = new TrackSection(sectionId, sectionName, positions);
        sections.put(sectionId, section);
        LogSystem.systemLog("创建轨道区段: " + sectionName + " (" + sectionId + ")");
    }

    /**
     * 自动划分轨道区段
     * 基于轨道连接关系自动将轨道划分为区段
     */
    public void autoDivideSections(String lineId) {
        LineControlSystem lineControlSystem = LineControlSystem.getInstance(world);
        LineControlSystem.LineInfo lineInfo = lineControlSystem.getLineInfo(lineId);
        
        if (lineInfo == null) {
            LogSystem.error("自动划分区段失败: 线路 " + lineId + " 不存在");
            return;
        }
        
        // 清空现有区段
        sections.clear();
        trackPositions.clear();
        trackPositions.addAll(lineInfo.getTracks());
        
        // 使用并查集算法识别连通的轨道区段
        Map<BlockPos, BlockPos> parentMap = new HashMap<>();
        
        // 初始化每个轨道位置的父节点为自己
        for (BlockPos pos : trackPositions) {
            parentMap.put(pos, pos);
        }
        
        // 检查每个轨道位置的相邻轨道，构建连通区域
        for (BlockPos pos : trackPositions) {
            for (BlockPos neighbor : getAdjacentTracks(pos)) {
                if (trackPositions.contains(neighbor)) {
                    union(pos, neighbor, parentMap);
                }
            }
        }
        
        // 按连通区域分组
        Map<BlockPos, List<BlockPos>> connectedComponents = new HashMap<>();
        for (BlockPos pos : trackPositions) {
            BlockPos root = find(pos, parentMap);
            connectedComponents.computeIfAbsent(root, k -> new ArrayList<>()).add(pos);
        }
        
        // 为每个连通区域创建区段
        int sectionIndex = 1;
        for (List<BlockPos> component : connectedComponents.values()) {
            // 只处理较大的连通区域（至少5个轨道方块）
            if (component.size() >= 5) {
                String sectionId = lineId + "_sec_" + sectionIndex;
                String sectionName = lineInfo.getLineName() + "区段" + sectionIndex;
                createSection(sectionId, sectionName, component);
                sectionIndex++;
            }
        }
        
        LogSystem.systemLog("自动划分完成，共创建 " + (sectionIndex - 1) + " 个区段");
    }

    /**
     * 获取指定位置所在的区段
     */
    public TrackSection getSectionAt(BlockPos pos) {
        for (TrackSection section : sections.values()) {
            if (section.containsPosition(pos)) {
                return section;
            }
        }
        return null;
    }

    /**
     * 获取所有区段
     */
    public Collection<TrackSection> getAllSections() {
        return sections.values();
    }
    
    /**
     * 更新轨道区段管理系统状态
     * 进行区段状态维护、清理无效区段等操作
     */
    public void update() {
        // 清理无效区段（区段内不再有轨道方块的区段）
        cleanupInvalidSections();
        
        // 其他维护操作可以在这里添加
    }
    
    /**
     * 清理无效区段
     */
    private void cleanupInvalidSections() {
        Iterator<TrackSection> iterator = sections.values().iterator();
        int cleanedCount = 0;
        
        while (iterator.hasNext()) {
            TrackSection section = iterator.next();
            boolean hasValidTracks = false;
            
            // 检查区段内是否至少有一个有效的轨道方块
            for (BlockPos pos : section.getPositions()) {
                if (world.getBlockState(pos).getBlock() instanceof TrackBlock || 
                    world.getBlockState(pos).getBlock() instanceof SwitchTrackBlock) {
                    hasValidTracks = true;
                    break;
                }
            }
            
            // 如果区段内没有有效的轨道方块，移除该区段
            if (!hasValidTracks) {
                iterator.remove();
                // 从trackPositions中移除该区段的所有位置
                for (BlockPos pos : section.getPositions()) {
                    trackPositions.remove(pos);
                }
                cleanedCount++;
            }
        }
        
        if (cleanedCount > 0) {
            LogSystem.systemLog("轨道区段管理系统：清理了 " + cleanedCount + " 个无效区段");
        }
    }

    /**
     * 更新区段占用状态
     */
    public void updateSectionOccupancy(String sectionId, boolean occupied, String trainId) {
        TrackSection section = sections.get(sectionId);
        if (section != null) {
            section.setOccupied(occupied, trainId);
        }
    }

    /**
     * 检查区段是否被占用
     */
    public boolean isSectionOccupied(String sectionId) {
        TrackSection section = sections.get(sectionId);
        return section != null && section.isOccupied();
    }

    /**
     * 获取指定区段的长度（以方块数计算）
     */
    public int getSectionLength(String sectionId) {
        TrackSection section = sections.get(sectionId);
        return section != null ? section.getLength() : 0;
    }

    /**
     * 获取相邻的轨道方块
     */
    private List<BlockPos> getAdjacentTracks(BlockPos pos) {
        List<BlockPos> adjacent = new ArrayList<>();
        
        // 检查上下左右前后六个方向
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    // 排除自己和对角线方向
                    if ((dx == 0 && dy == 0 && dz == 0) || 
                        (Math.abs(dx) + Math.abs(dy) + Math.abs(dz)) > 1) {
                        continue;
                    }
                    
                    BlockPos neighbor = pos.add(dx, dy, dz);
                    if (world.getBlockState(neighbor).getBlock() instanceof TrackBlock || 
                        world.getBlockState(neighbor).getBlock() instanceof SwitchTrackBlock) {
                        adjacent.add(neighbor);
                    }
                }
            }
        }
        
        return adjacent;
    }

    /**
     * 并查集查找操作
     */
    private BlockPos find(BlockPos pos, Map<BlockPos, BlockPos> parentMap) {
        if (!parentMap.get(pos).equals(pos)) {
            parentMap.put(pos, find(parentMap.get(pos), parentMap));
        }
        return parentMap.get(pos);
    }

    /**
     * 并查集合并操作
     */
    private void union(BlockPos pos1, BlockPos pos2, Map<BlockPos, BlockPos> parentMap) {
        BlockPos root1 = find(pos1, parentMap);
        BlockPos root2 = find(pos2, parentMap);
        if (!root1.equals(root2)) {
            parentMap.put(root2, root1);
        }
    }

    /**
     * 轨道区段类
     * 表示一段连续的轨道，包含位置信息、占用状态等
     */
    public static class TrackSection {
        private final String sectionId;
        private final String sectionName;
        private final Set<BlockPos> positions;
        private boolean occupied = false;
        private String occupyingTrainId = "";
        private long occupancyStartTime = 0;
        
        public TrackSection(String sectionId, String sectionName, List<BlockPos> positions) {
            this.sectionId = sectionId;
            this.sectionName = sectionName;
            this.positions = new HashSet<>(positions);
        }
        
        /**
         * 检查区段是否包含指定位置
         */
        public boolean containsPosition(BlockPos pos) {
            return positions.contains(pos);
        }
        
        /**
         * 设置区段占用状态
         */
        public void setOccupied(boolean occupied, String trainId) {
            if (this.occupied != occupied) {
                this.occupied = occupied;
                this.occupyingTrainId = occupied ? trainId : "";
                this.occupancyStartTime = occupied ? System.currentTimeMillis() : 0;
                
                String status = occupied ? "被占用" : "空闲";
                LogSystem.systemLog("区段 " + sectionName + " (" + sectionId + ") 状态变更为: " + status + 
                                   (occupied ? "，占用列车ID: " + trainId : ""));
            }
        }
        
        /**
         * 获取区段是否被占用
         */
        public boolean isOccupied() {
            return occupied;
        }
        
        /**
         * 获取占用区段的列车ID
         */
        public String getOccupyingTrainId() {
            return occupyingTrainId;
        }
        
        /**
         * 获取区段长度
         */
        public int getLength() {
            return positions.size();
        }
        
        /**
         * 获取区段ID
         */
        public String getSectionId() {
            return sectionId;
        }
        
        /**
         * 获取区段名称
         */
        public String getSectionName() {
            return sectionName;
        }
        
        /**
         * 获取区段内的所有位置
         */
        public Set<BlockPos> getPositions() {
            return Collections.unmodifiableSet(positions);
        }
        
        /**
         * 获取区段占用时间
         */
        public long getOccupancyTime() {
            if (!occupied) return 0;
            return System.currentTimeMillis() - occupancyStartTime;
        }
    }
}