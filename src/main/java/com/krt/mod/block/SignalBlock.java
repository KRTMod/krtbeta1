package com.krt.mod.block;

import net.fabricmc.fabric.api.object.builder.v1.block.FabricBlockSettings;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.HorizontalFacingBlock;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.DirectionProperty;
import net.minecraft.state.property.EnumProperty;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import com.krt.mod.KRTMod;

public class SignalBlock extends Block {
    public static final DirectionProperty FACING = HorizontalFacingBlock.FACING;
    public static final EnumProperty<SignalState> SIGNAL_STATE = EnumProperty.of("signal_state", SignalState.class);

    public SignalBlock(Settings settings) {
        super(settings);
        this.setDefaultState(this.stateManager.getDefaultState().with(FACING, Direction.NORTH).with(SIGNAL_STATE, SignalState.GREEN));
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(FACING, SIGNAL_STATE);
    }

    @Override
    public BlockState getPlacementState(ItemPlacementContext ctx) {
        return this.getDefaultState().with(FACING, ctx.getPlayerFacing());
    }

    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
        if (!world.isClient) {
            // 手动切换信号状态（仅用于测试）
            SignalState currentState = state.get(SIGNAL_STATE);
            SignalState nextState;
            switch (currentState) {
                case GREEN:
                    nextState = SignalState.YELLOW;
                    break;
                case YELLOW:
                    nextState = SignalState.RED;
                    break;
                default:
                    nextState = SignalState.GREEN;
                    break;
            }
            world.setBlockState(pos, state.with(SIGNAL_STATE, nextState));
        }
        return ActionResult.SUCCESS;
    }

    // 更新信号状态
    public static void updateSignalState(World world, BlockPos pos, SignalState newState) {
        if (world.isClient) return;
        BlockState state = world.getBlockState(pos);
        if (state.getBlock() instanceof SignalBlock) {
            world.setBlockState(pos, state.with(SIGNAL_STATE, newState));
        }
    }

    // 信号状态枚举
    public enum SignalState {
        GREEN,
        YELLOW,
        RED
    }
}