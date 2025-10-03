package com.krt.mod.block;

import net.fabricmc.fabric.api.object.builder.v1.block.FabricBlockSettings;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.HorizontalFacingBlock;
import net.minecraft.entity.Entity;
import net.minecraft.entity.vehicle.AbstractMinecartEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.state.property.DirectionProperty;
import net.minecraft.state.property.EnumProperty;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import com.krt.mod.system.LogSystem;
import com.krt.mod.KRTMod;
import net.minecraft.sound.SoundCategory;

public class TrackBlock extends Block {
    // 轨道类型枚举
    public enum TrackType {
        NORMAL(1.0f, 0.95f, "普通轨道"),
        HIGH_SPEED(1.5f, 0.98f, "高速轨道"),
        LOW_SPEED(0.75f, 0.90f, "低速轨道"),
        MAGLEV(2.0f, 0.99f, "磁悬浮轨道"),
        MONORAIL(1.2f, 0.96f, "单轨轨道");
        
        private final float speedMultiplier; // 速度倍数
        private final float efficiency; // 效率因子
        private final String displayName;
        
        TrackType(float speedMultiplier, float efficiency, String displayName) {
            this.speedMultiplier = speedMultiplier;
            this.efficiency = efficiency;
            this.displayName = displayName;
        }
        
        public float getSpeedMultiplier() {
            return speedMultiplier;
        }
        
        public float getEfficiency() {
            return efficiency;
        }
        
        public String getDisplayName() {
            return displayName;
        }
    }
    
    // 电气化类型枚举
    public enum ElectrificationType {
        NONE(false, 0, "非电气化"),
        OVERHEAD_CONTACT(true, 1, "接触网"),
        THIRD_RAIL(true, 2, "第三轨"),
        MAGNETIC(true, 3, "磁悬浮供电");
        
        private final boolean isElectrified;
        private final int powerLevel;
        private final String displayName;
        
        ElectrificationType(boolean isElectrified, int powerLevel, String displayName) {
            this.isElectrified = isElectrified;
            this.powerLevel = powerLevel;
            this.displayName = displayName;
        }
        
        public boolean isElectrified() {
            return isElectrified;
        }
        
        public int getPowerLevel() {
            return powerLevel;
        }
        
        public String getDisplayName() {
            return displayName;
        }
    }
    
    // 轨道属性类
    public static class TrackProperties {
        public TrackType trackType;
        public Direction direction;
        public ElectrificationType electrificationType;
        
        public TrackProperties() {
            this.trackType = TrackType.NORMAL;
            this.direction = Direction.NORTH;
            this.electrificationType = ElectrificationType.NONE;
        }
        
        public void setTrackType(TrackType trackType) {
            this.trackType = trackType;
        }
        
        public void setElectrificationType(ElectrificationType electrificationType) {
            this.electrificationType = electrificationType;
        }
    }
    
    // 轨道状态属性
    public static final DirectionProperty FACING = HorizontalFacingBlock.FACING;
    public static final EnumProperty<TrackType> TRACK_TYPE = EnumProperty.of("track_type", TrackType.class);
    public static final EnumProperty<ElectrificationType> ELECTRIFICATION = EnumProperty.of("electrification", ElectrificationType.class);
    public static final BooleanProperty HAS_SLOPE = BooleanProperty.of("has_slope");
    public static final BooleanProperty IN_TUNNEL = BooleanProperty.of("in_tunnel");
    
    private final TrackType defaultTrackType;
    private final ElectrificationType defaultElectrification;
    
    public TrackBlock(Settings settings) {
        this(settings, TrackType.NORMAL, ElectrificationType.NONE);
    }
    
    public TrackBlock(Settings settings, TrackType trackType, ElectrificationType electrification) {
        super(settings);
        this.defaultTrackType = trackType;
        this.defaultElectrification = electrification;
        this.setDefaultState(this.stateManager.getDefaultState()
                .with(FACING, Direction.NORTH)
                .with(TRACK_TYPE, trackType)
                .with(ELECTRIFICATION, electrification)
                .with(HAS_SLOPE, false)
                .with(IN_TUNNEL, false));
    }
    
    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(FACING, TRACK_TYPE, ELECTRIFICATION, HAS_SLOPE, IN_TUNNEL);
    }
    
    @Override
    public BlockState getPlacementState(ItemPlacementContext ctx) {
        return this.getDefaultState().with(FACING, ctx.getPlayerFacing());
    }
    
    // 获取轨道类型
    public TrackType getTrackType(BlockState state) {
        return state.get(TRACK_TYPE);
    }
    
    // 获取电气化类型
    public ElectrificationType getElectrificationType(BlockState state) {
        return state.get(ELECTRIFICATION);
    }
    
    // 检查轨道是否电气化
    public boolean isElectrified(BlockState state) {
        return state.get(ELECTRIFICATION).isElectrified();
    }
    
    // 获取轨道速度限制
    public float getSpeedLimit(BlockState state) {
        return state.get(TRACK_TYPE).getSpeedMultiplier();
    }
    
    // 获取轨道效率
    public float getEfficiency(BlockState state) {
        return state.get(TRACK_TYPE).getEfficiency();
    }
    
    // 更新轨道坡度状态
    public void updateSlopeState(World world, BlockPos pos, BlockState state) {
        boolean hasSlope = calculateSlope(world, pos) != 0.0;
        if (state.get(HAS_SLOPE) != hasSlope) {
            world.setBlockState(pos, state.with(HAS_SLOPE, hasSlope));
        }
    }
    
    // 更新隧道状态
    public void updateTunnelState(World world, BlockPos pos, BlockState state) {
        boolean inTunnel = isInTunnel(world, pos);
        if (state.get(IN_TUNNEL) != inTunnel) {
            world.setBlockState(pos, state.with(IN_TUNNEL, inTunnel));
        }
    }
    
    // 计算轨道坡度
    private double calculateSlope(World world, BlockPos pos) {
        // 检查周围轨道的高度差异
        BlockPos frontPos = pos.offset(state.get(FACING));
        BlockPos backPos = pos.offset(state.get(FACING).getOpposite());
        
        int frontHeight = getTrackHeight(world, frontPos);
        int backHeight = getTrackHeight(world, backPos);
        
        if (frontHeight == -1 || backHeight == -1) {
            return 0.0; // 如果无法获取相邻轨道高度，返回0
        }
        
        return (frontHeight - backHeight) / 2.0; // 简化计算，实际应考虑水平距离
    }
    
    // 获取轨道高度
    private int getTrackHeight(World world, BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        if (state.getBlock() instanceof TrackBlock || state.getBlock() instanceof SwitchTrackBlock) {
            return pos.getY();
        }
        return -1; // 非轨道方块返回-1
    }
    
    // 检查轨道是否在隧道内
    private boolean isInTunnel(World world, BlockPos pos) {
        // 简单判断：检查上方和四周是否有方块覆盖
        int coveredSides = 0;
        
        // 检查上方
        for (int y = pos.getY() + 1; y <= pos.getY() + 3; y++) {
            BlockPos checkPos = new BlockPos(pos.getX(), y, pos.getZ());
            if (!world.isAir(checkPos)) {
                coveredSides++;
                break;
            }
        }
        
        // 检查四个方向
        for (Direction dir : Direction.Type.HORIZONTAL) {
            BlockPos checkPos = pos.offset(dir);
            if (!world.isAir(checkPos)) {
                coveredSides++;
            }
        }
        
        return coveredSides >= 3; // 如果至少3个方向被覆盖，认为在隧道内
    }
    
    @Override
    public void onEntityCollision(BlockState state, World world, BlockPos pos, Entity entity) {
        // 处理列车在轨道上行驶的逻辑
        if (entity instanceof AbstractMinecartEntity && !world.isClient) {
            TrackType trackType = state.get(TRACK_TYPE);
            
            // 根据轨道类型播放不同的声音
            switch (trackType) {
                case HIGH_SPEED:
                    world.playSound(null, pos, KRTMod.HIGH_SPEED_TRACK_SOUND, SoundCategory.BLOCKS, 0.8F, 1.0F);
                    break;
                case MAGLEV:
                    world.playSound(null, pos, KRTMod.MAGLEV_TRACK_SOUND, SoundCategory.BLOCKS, 0.5F, 1.2F);
                    break;
                case MONORAIL:
                    world.playSound(null, pos, KRTMod.MONORAIL_TRACK_SOUND, SoundCategory.BLOCKS, 0.7F, 1.0F);
                    break;
                case LOW_SPEED:
                    world.playSound(null, pos, KRTMod.LOW_SPEED_TRACK_SOUND, SoundCategory.BLOCKS, 0.6F, 0.9F);
                    break;
                default:
                    world.playSound(null, pos, KRTMod.TRACK_COLLISION_SOUND, SoundCategory.BLOCKS, 0.5F, 1.0F);
            }
            
            // 如果是电气化轨道，可以添加电力传输逻辑
            if (state.get(ELECTRIFICATION).isElectrified()) {
                // 这里可以实现电力传输到列车的逻辑
                LogSystem.debugLog("列车经过电气化轨道: " + pos);
            }
            
            // 如果在隧道内，可以添加隧道音效和视觉效果
            if (state.get(IN_TUNNEL)) {
                // 添加隧道回声效果等
            }
        }
    }
}