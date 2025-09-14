package com.krt.mod.block;

import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import com.krt.mod.system.LanguageSystem;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

public class TrackPlacementHelper {
    // 轨道方向映射
    public static final Map<Direction, Direction[]> DIRECTION_CONNECTIONS = new HashMap<>();

    static {
        // 为每个方向定义连接方向
        DIRECTION_CONNECTIONS.put(Direction.NORTH, new Direction[]{Direction.SOUTH, Direction.EAST, Direction.WEST});
        DIRECTION_CONNECTIONS.put(Direction.SOUTH, new Direction[]{Direction.NORTH, Direction.EAST, Direction.WEST});
        DIRECTION_CONNECTIONS.put(Direction.EAST, new Direction[]{Direction.WEST, Direction.NORTH, Direction.SOUTH});
        DIRECTION_CONNECTIONS.put(Direction.WEST, new Direction[]{Direction.EAST, Direction.NORTH, Direction.SOUTH});
    }

    /**
     * 检查指定位置的方块是否可以连接到轨道
     */
    public static boolean canConnectToTrack(World world, BlockPos pos, Direction direction) {
        BlockPos neighborPos = pos.offset(direction);
        BlockState neighborState = world.getBlockState(neighborPos);
        return isTrackBlock(neighborState) || isSwitchTrackBlock(neighborState);
    }

    /**
     * 检查方块是否为普通轨道
     */
    public static boolean isTrackBlock(BlockState state) {
        return state.getBlock() instanceof TrackBlock;
    }

    /**
     * 检查方块是否为道岔轨道
     */
    public static boolean isSwitchTrackBlock(BlockState state) {
        return state.getBlock() instanceof SwitchTrackBlock;
    }

    /**
     * 获取指定位置的轨道连接方向列表
     */
    public static List<Direction> getTrackConnections(World world, BlockPos pos) {
        List<Direction> connections = new ArrayList<>();
        for (Direction dir : Direction.values()) {
            if (dir.getAxis().isHorizontal() && canConnectToTrack(world, pos, dir)) {
                connections.add(dir);
            }
        }
        return connections;
    }

    /**
     * 检查轨道连接是否有效
     */
    public static boolean isValidTrackConnection(World world, BlockPos pos, Direction from, Direction to) {
        BlockPos neighborPos = pos.offset(from);
        BlockState neighborState = world.getBlockState(neighborPos);
        
        if (neighborState.getBlock() instanceof SwitchTrackBlock) {
            // 对于道岔，检查是否可以连接到指定方向
            SwitchTrackBlock switchBlock = (SwitchTrackBlock) neighborState.getBlock();
            Direction switchFacing = neighborState.get(SwitchTrackBlock.FACING);
            boolean isMainRoute = neighborState.get(SwitchTrackBlock.IS_MAIN_ROUTE);
            
            return switchBlock.canConnectTo(world, neighborPos, switchFacing, isMainRoute, to.getOpposite());
        } else if (neighborState.getBlock() instanceof TrackBlock) {
            // 对于普通轨道，只要方向相反即可连接
            return to.getOpposite() == neighborState.get(TrackBlock.FACING);
        }
        return false;
    }

    /**
     * 计算轨道铺设方向
     */
    public static Direction calculateTrackDirection(World world, BlockPos pos, PlayerEntity player) {
        // 获取玩家朝向
        Direction playerFacing = player.getHorizontalFacing();
        
        // 查找周围已有的轨道方块
        for (Direction dir : Direction.values()) {
            if (dir.getAxis().isHorizontal() && canConnectToTrack(world, pos, dir)) {
                BlockPos neighborPos = pos.offset(dir);
                BlockState neighborState = world.getBlockState(neighborPos);
                
                // 根据相邻轨道的方向确定当前轨道的方向
                if (neighborState.getBlock() instanceof TrackBlock) {
                    return neighborState.get(TrackBlock.FACING).getOpposite();
                } else if (neighborState.getBlock() instanceof SwitchTrackBlock) {
                    Direction switchFacing = neighborState.get(SwitchTrackBlock.FACING);
                    boolean isMainRoute = neighborState.get(SwitchTrackBlock.IS_MAIN_ROUTE);
                    SwitchTrackBlock switchBlock = (SwitchTrackBlock) neighborState.getBlock();
                    
                    // 找到道岔可以连接的方向
                    for (Direction connDir : switchBlock.getConnectableDirections(switchFacing, isMainRoute)) {
                        if (connDir.getOpposite() == dir) {
                            return dir;
                        }
                    }
                }
            }
        }
        
        // 如果周围没有轨道，根据玩家朝向确定轨道方向
        return playerFacing;
    }

    /**
     * 检查线路坡度是否符合规定
     */
    public static boolean checkSlope合规性(World world, BlockPos startPos, BlockPos endPos) {
        int xDiff = Math.abs(startPos.getX() - endPos.getX());
        int zDiff = Math.abs(startPos.getZ() - endPos.getZ());
        int yDiff = Math.abs(startPos.getY() - endPos.getY());
        
        // 计算水平距离
        double horizontalDistance = Math.sqrt(xDiff * xDiff + zDiff * zDiff);
        
        // 如果水平距离为0，说明是垂直方向的坡度，需要特别处理
        if (horizontalDistance == 0) {
            return false; // 不允许垂直方向的轨道
        }
        
        // 计算坡度千分比
        double slope = (yDiff / horizontalDistance) * 1000;
        
        // 根据用户要求，正线最大坡度不宜大于30‰，困难35‰
        return slope <= 35.0; // 采用困难条件下的最大坡度
    }

    /**
     * 检查轨道转弯半径是否符合规定
     */
    public static boolean checkCurveRadius合规性(World world, List<BlockPos> trackPositions) {
        if (trackPositions.size() < 3) {
            return true; // 轨道太短，无法检查转弯半径
        }
        
        // 简化版转弯半径检查：计算相邻轨道方向的变化
        for (int i = 1; i < trackPositions.size() - 1; i++) {
            BlockPos prevPos = trackPositions.get(i - 1);
            BlockPos currentPos = trackPositions.get(i);
            BlockPos nextPos = trackPositions.get(i + 1);
            
            // 计算前一段和后一段的方向
            Direction prevDir = Direction.fromVector(
                    currentPos.getX() - prevPos.getX(),
                    currentPos.getY() - prevPos.getY(),
                    currentPos.getZ() - prevPos.getZ());
            
            Direction nextDir = Direction.fromVector(
                    nextPos.getX() - currentPos.getX(),
                    nextPos.getY() - currentPos.getY(),
                    nextPos.getZ() - currentPos.getZ());
            
            // 检查是否有急转弯（方向变化超过90度）
            if (prevDir.getAxis() != nextDir.getAxis() && !prevDir.equals(nextDir.getOpposite())) {
                // 计算转弯半径（简化版）
                int radius = calculateApproximateRadius(prevPos, currentPos, nextPos);
                
                // 根据用户要求，城市轨道交通地铁的曲线半径不小于300米（即300格）
                if (radius < 300) {
                    return false;
                }
            }
        }
        
        return true;
    }

    /**
     * 计算近似转弯半径
     */
    private static int calculateApproximateRadius(BlockPos p1, BlockPos p2, BlockPos p3) {
        // 简化版：计算三点形成的三角形的外接圆半径
        double a = distance(p2, p3);
        double b = distance(p1, p3);
        double c = distance(p1, p2);
        
        double s = (a + b + c) / 2;
        double area = Math.sqrt(s * (s - a) * (s - b) * (s - c));
        
        if (area == 0) {
            return Integer.MAX_VALUE; // 三点共线
        }
        
        // 外接圆半径公式
        double radius = (a * b * c) / (4 * area);
        
        return (int) Math.round(radius);
    }

    /**
     * 计算两点之间的距离
     */
    private static double distance(BlockPos p1, BlockPos p2) {
        int dx = p1.getX() - p2.getX();
        int dy = p1.getY() - p2.getY();
        int dz = p1.getZ() - p2.getZ();
        
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    /**
     * 检查线路是否完整连接
     */
    public static boolean checkLineConnectivity(World world, BlockPos startPos, BlockPos endPos) {
        List<BlockPos> visited = new ArrayList<>();
        return dfsCheckConnectivity(world, startPos, endPos, visited);
    }

    /**
     * 使用深度优先搜索检查连接性
     */
    private static boolean dfsCheckConnectivity(World world, BlockPos currentPos, BlockPos endPos, List<BlockPos> visited) {
        if (currentPos.equals(endPos)) {
            return true;
        }
        
        if (visited.contains(currentPos)) {
            return false;
        }
        
        visited.add(currentPos);
        BlockState currentState = world.getBlockState(currentPos);
        
        if (!isTrackBlock(currentState) && !isSwitchTrackBlock(currentState)) {
            return false;
        }
        
        // 检查所有可能的连接方向
        for (Direction dir : Direction.values()) {
            if (dir.getAxis().isHorizontal()) {
                BlockPos neighborPos = currentPos.offset(dir);
                BlockState neighborState = world.getBlockState(neighborPos);
                
                if ((isTrackBlock(neighborState) || isSwitchTrackBlock(neighborState)) && 
                    isValidTrackConnection(world, currentPos, dir, dir.getOpposite())) {
                    if (dfsCheckConnectivity(world, neighborPos, endPos, visited)) {
                        return true;
                    }
                }
            }
        }
        
        return false;
    }

    /**
     * 获取线路的完整轨道位置列表
     */
    public static List<BlockPos> getCompleteTrackLine(World world, BlockPos startPos) {
        List<BlockPos> trackPositions = new ArrayList<>();
        List<BlockPos> visited = new ArrayList<>();
        collectTrackLine(world, startPos, trackPositions, visited);
        return trackPositions;
    }

    /**
     * 收集轨道线路位置
     */
    private static void collectTrackLine(World world, BlockPos currentPos, List<BlockPos> trackPositions, List<BlockPos> visited) {
        if (visited.contains(currentPos)) {
            return;
        }
        
        visited.add(currentPos);
        BlockState currentState = world.getBlockState(currentPos);
        
        if (isTrackBlock(currentState) || isSwitchTrackBlock(currentState)) {
            trackPositions.add(currentPos);
            
            // 继续收集所有连接的轨道
            for (Direction dir : Direction.values()) {
                if (dir.getAxis().isHorizontal() && canConnectToTrack(world, currentPos, dir)) {
                    BlockPos neighborPos = currentPos.offset(dir);
                    if (isValidTrackConnection(world, currentPos, dir, dir.getOpposite())) {
                        collectTrackLine(world, neighborPos, trackPositions, visited);
                    }
                }
            }
        }
    }

    /**
     * 生成轨道铺设建议（用于辅助玩家铺设轨道）
     */
    public static List<BlockPos> generateTrackPlacementSuggestions(World world, BlockPos startPos, Direction direction, int length) {
        List<BlockPos> suggestions = new ArrayList<>();
        BlockPos currentPos = startPos;
        
        for (int i = 0; i < length; i++) {
            // 检查当前位置是否可以放置轨道
            if (canPlaceTrack(world, currentPos)) {
                suggestions.add(currentPos);
            } else {
                // 如果不能放置，尝试寻找替代位置
                BlockPos alternativePos = findAlternativeTrackPosition(world, currentPos, direction);
                if (alternativePos != null && canPlaceTrack(world, alternativePos)) {
                    suggestions.add(alternativePos);
                    currentPos = alternativePos;
                } else {
                    // 如果找不到替代位置，结束生成
                    break;
                }
            }
            
            // 移动到下一个位置
            currentPos = currentPos.offset(direction);
        }
        
        return suggestions;
    }

    /**
     * 检查是否可以在指定位置放置轨道
     */
    public static boolean canPlaceTrack(World world, BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        BlockState belowState = world.getBlockState(pos.down());
        
        // 检查是否有足够的空间放置轨道
        if (!state.isAir() && !state.getMaterial().isReplaceable()) {
            return false;
        }
        
        // 检查下方是否有支撑方块
        return belowState.isSolidBlock(world, pos.down());
    }

    /**
     * 寻找替代的轨道位置
     */
    private static BlockPos findAlternativeTrackPosition(World world, BlockPos originalPos, Direction direction) {
        // 尝试向上或向下寻找替代位置
        for (int offset = 1; offset <= 3; offset++) {
            // 向上
            BlockPos upPos = originalPos.up(offset);
            if (canPlaceTrack(world, upPos)) {
                return upPos;
            }
            
            // 向下
            BlockPos downPos = originalPos.down(offset);
            if (canPlaceTrack(world, downPos)) {
                return downPos;
            }
        }
        
        return null;
    }
}