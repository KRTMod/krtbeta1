package com.krt.mod.block;

import net.fabricmc.fabric.api.object.builder.v1.block.FabricBlockSettings;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.HorizontalFacingBlock;
import net.minecraft.entity.Entity;
import net.minecraft.entity.vehicle.AbstractMinecartEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.state.property.DirectionProperty;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import com.krt.mod.KRTMod;

public class SwitchTrackBlock extends Block {
    public static final DirectionProperty FACING = HorizontalFacingBlock.FACING;
    public static final BooleanProperty SWITCHED = BooleanProperty.of("switched");

    public SwitchTrackBlock(Settings settings) {
        super(settings);
        this.setDefaultState(this.stateManager.getDefaultState().with(FACING, Direction.NORTH).with(SWITCHED, false));
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(FACING, SWITCHED);
    }

    @Override
    public BlockState getPlacementState(ItemPlacementContext ctx) {
        return this.getDefaultState().with(FACING, ctx.getPlayerFacing());
    }

    // 切换道岔状态
    public static void toggleSwitch(World world, BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        if (state.getBlock() instanceof SwitchTrackBlock) {
            boolean switched = !state.get(SWITCHED);
            world.setBlockState(pos, state.with(SWITCHED, switched));
            // 可以在这里添加道岔切换的声音效果
        }
    }

    @Override
    public void onEntityCollision(BlockState state, World world, BlockPos pos, Entity entity) {
        if (entity instanceof AbstractMinecartEntity && !world.isClient) {
            // 列车经过道岔时发出撞轨的声音
            // 可以根据道岔的状态播放不同的声音效果
            if (state.get(SWITCHED)) {
                // 道岔处于切换状态时播放的声音
                world.playSound(null, pos, KRTMod.SWITCH_COLLISION_SOUND, SoundCategory.BLOCKS, 1.0F, 1.0F);
            } else {
                // 道岔处于正常状态时播放的声音
                world.playSound(null, pos, KRTMod.TRACK_COLLISION_SOUND, SoundCategory.BLOCKS, 0.5F, 1.0F);
            }
        }
    }
}