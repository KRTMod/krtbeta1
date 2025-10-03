package com.krt.mod.block;

import net.fabricmc.fabric.api.object.builder.v1.block.FabricBlockSettings;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.vehicle.AbstractMinecartEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import java.util.List;
import net.minecraft.sound.SoundCategory;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.block.Block;
// 使用项目自定义的TrackBlock（同一个包中无需导入）
import net.minecraft.state.property.DirectionProperty;
import net.minecraft.state.property.EnumProperty;
import net.minecraft.state.property.BooleanProperty;
import com.krt.mod.KRTMod;
import com.krt.mod.system.LogSystem;

public class SwitchTrackBlock extends TrackBlock {
    // 道岔特有属性
    public static final BooleanProperty SWITCHED = BooleanProperty.of("switched");

    public SwitchTrackBlock(Settings settings) {
        this(settings, TrackType.NORMAL, ElectrificationType.NONE);
    }
    
    public SwitchTrackBlock(Settings settings, TrackType trackType, ElectrificationType electrification) {
        super(settings, trackType, electrification);
        this.setDefaultState(this.stateManager.getDefaultState()
                .with(FACING, Direction.NORTH)
                .with(TRACK_TYPE, trackType)
                .with(ELECTRIFICATION, electrification)
                .with(HAS_SLOPE, false)
                .with(IN_TUNNEL, false)
                .with(SWITCHED, false));
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        super.appendProperties(builder);
        builder.add(SWITCHED);
    }

    @Override
    public BlockState getPlacementState(ItemPlacementContext ctx) {
        return super.getPlacementState(ctx).with(SWITCHED, false);
    }

    // 切换道岔状态
    public static void toggleSwitch(World world, BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        if (state.getBlock() instanceof SwitchTrackBlock) {
            boolean switched = !state.get(SWITCHED);
            world.setBlockState(pos, state.with(SWITCHED, switched));
            // 播放道岔切换的声音
            world.playSound(null, pos, KRTMod.SWITCH_TOGGLE_SOUND, SoundCategory.BLOCKS, 1.0F, 1.0F);
            LogSystem.debugLog("道岔切换状态: " + pos + " -> " + (switched ? "切换" : "正常"));
        }
    }
    
    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
        if (!world.isClient) {
            // 切换道岔状态
            toggleSwitch(world, pos);
        }
        return ActionResult.SUCCESS;
    }
    
    // 获取道岔的当前连接方向
    public List<Direction> getConnectionDirections(BlockState state) {
        List<Direction> connections = new ArrayList<>();
        Direction facing = state.get(FACING);
        connections.add(facing.getOpposite()); // 始终连接后面
        
        if (state.get(SWITCHED)) {
            // 切换状态连接到右侧
            connections.add(facing.rotateYClockwise());
        } else {
            // 正常状态连接到正面
            connections.add(facing);
        }
        
        return connections;
    }
    
    // 检查指定方向是否可以连接到道岔
    public boolean canConnectToDirection(BlockState state, Direction direction) {
        return getConnectionDirections(state).contains(direction);
    }

    @Override
    public void onEntityCollision(BlockState state, World world, BlockPos pos, Entity entity) {
        if (entity instanceof AbstractMinecartEntity && !world.isClient) {
            TrackType trackType = state.get(TRACK_TYPE);
            boolean isSwitched = state.get(SWITCHED);
            
            // 根据轨道类型和道岔状态播放不同的声音
            if (isSwitched) {
                // 道岔处于切换状态时播放特殊声音
                switch (trackType) {
                    case HIGH_SPEED:
                        world.playSound(null, pos, KRTMod.HIGH_SPEED_SWITCH_SOUND, SoundCategory.BLOCKS, 1.0F, 1.0F);
                        break;
                    case MAGLEV:
                        world.playSound(null, pos, KRTMod.MAGLEV_SWITCH_SOUND, SoundCategory.BLOCKS, 0.8F, 1.2F);
                        break;
                    case MONORAIL:
                        world.playSound(null, pos, KRTMod.MONORAIL_SWITCH_SOUND, SoundCategory.BLOCKS, 0.9F, 1.0F);
                        break;
                    default:
                        world.playSound(null, pos, KRTMod.SWITCH_COLLISION_SOUND, SoundCategory.BLOCKS, 1.0F, 1.0F);
                }
            } else {
                // 道岔处于正常状态时，使用轨道类型对应的声音
                super.onEntityCollision(state, world, pos, entity);
            }
            
            // 如果是电气化轨道，可以添加电力传输逻辑
            if (state.get(ELECTRIFICATION).isElectrified()) {
                LogSystem.debugLog("列车经过电气化道岔: " + pos);
                // 这里可以实现电力传输到列车的逻辑
            }
        }
    }
    
    // 重写坡度计算，考虑道岔的特殊情况
    @Override
    private double calculateSlope(World world, BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        Direction facing = state.get(FACING);
        boolean isSwitched = state.get(SWITCHED);
        
        // 对于道岔，需要考虑两个连接方向的坡度
        List<BlockPos> checkPositions = new ArrayList<>();
        checkPositions.add(pos.offset(facing.getOpposite())); // 后面
        
        if (isSwitched) {
            checkPositions.add(pos.offset(facing.rotateYClockwise())); // 右侧
        } else {
            checkPositions.add(pos.offset(facing)); // 正面
        }
        
        // 计算平均坡度
        double totalSlope = 0.0;
        int validChecks = 0;
        
        for (BlockPos checkPos : checkPositions) {
            int checkHeight = getTrackHeight(world, checkPos);
            if (checkHeight != -1) {
                totalSlope += (checkHeight - pos.getY());
                validChecks++;
            }
        }
        
        return validChecks > 0 ? totalSlope / validChecks : 0.0;
    }
}