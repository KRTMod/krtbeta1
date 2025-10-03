package com.krt.mod.block.power;

import net.minecraft.block.*;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.enums.Attachment;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.*;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

/**
 * 电力传输线方块，用于传输电力
 */
public class PowerTransmissionLineBlock extends BlockWithEntity {
    public static final BooleanProperty POWERED = Properties.POWERED;
    public static final DirectionProperty FACING = Properties.FACING;
    public static final String ID = "power_transmission_line";
    
    public PowerTransmissionLineBlock(Settings settings) {
        super(settings);
        this.setDefaultState(this.stateManager.getDefaultState()
                .with(FACING, Direction.UP)
                .with(POWERED, false));
    }
    
    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(FACING, POWERED);
    }
    
    @Override
    public BlockState getPlacementState(ItemPlacementContext ctx) {
        // 尝试放置在朝向最近的表面
        for (Direction direction : ctx.getPlacementDirections()) {
            BlockState blockState = this.getDefaultState().with(FACING, direction);
            if (blockState.canPlaceAt(ctx.getWorld(), ctx.getBlockPos())) {
                return blockState;
            }
        }
        return this.getDefaultState().with(FACING, Direction.UP);
    }
    
    @Override
    public boolean canPlaceAt(BlockState state, World world, BlockPos pos) {
        Direction direction = state.get(FACING);
        BlockPos attachedPos = pos.offset(direction.getOpposite());
        BlockState attachedState = world.getBlockState(attachedPos);
        return attachedState.isSideSolidFullSquare(world, attachedPos, direction);
    }
    
    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return null; // 暂时不需要方块实体，后续可以扩展为需要方块实体的更复杂传输线
    }
    
    @Override
    public BlockRenderType getRenderType(BlockState state) {
        return BlockRenderType.MODEL;
    }
    
    /**
     * 设置传输线的通电状态
     */
    public void setPowered(World world, BlockPos pos, boolean powered) {
        BlockState state = world.getBlockState(pos);
        if (state.get(POWERED) != powered) {
            world.setBlockState(pos, state.with(POWERED, powered));
            // 通知相邻传输线更新
            updateConnectedLines(world, pos);
        }
    }
    
    /**
     * 更新相连的传输线
     */
    private void updateConnectedLines(World world, BlockPos pos) {
        for (Direction direction : Direction.values()) {
            BlockPos neighborPos = pos.offset(direction);
            BlockState neighborState = world.getBlockState(neighborPos);
            if (neighborState.getBlock() instanceof PowerTransmissionLineBlock) {
                // 触发相邻传输线更新
                // 这里可以添加电力传导逻辑
            }
        }
    }
}