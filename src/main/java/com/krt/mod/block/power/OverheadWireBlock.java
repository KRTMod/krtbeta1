package com.krt.mod.block.power;

import com.krt.mod.blockentity.power.OverheadWireBlockEntity;
import net.minecraft.block.*;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.state.property.DirectionProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

/**
 * 接触网方块 - 连接接触网支柱的供电线路
 */
public class OverheadWireBlock extends BlockWithEntity {
    public static final DirectionProperty FACING = Properties.HORIZONTAL_FACING;
    public static final BooleanProperty POWERED = Properties.POWERED;
    public static final String ID = "overhead_wire";
    
    public OverheadWireBlock(Settings settings) {
        super(settings);
        this.setDefaultState(this.stateManager.getDefaultState()
                .with(FACING, Direction.NORTH)
                .with(POWERED, false));
    }
    
    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(FACING, POWERED);
    }
    
    @Override
    public BlockState getPlacementState(ItemPlacementContext ctx) {
        // 尝试检测放置方向，基于玩家朝向或相邻支柱
        Direction direction = ctx.getPlayerFacing().getOpposite();
        
        // 检查相邻的支柱
        for (Direction dir : Direction.Type.HORIZONTAL) {
            BlockPos neighborPos = ctx.getBlockPos().offset(dir);
            if (ctx.getWorld().getBlockState(neighborPos).getBlock() instanceof OverheadWirePoleBlock) {
                direction = dir;
                break;
            }
        }
        
        return this.getDefaultState().with(FACING, direction);
    }
    
    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new OverheadWireBlockEntity(pos, state);
    }
    
    @Override
    public BlockRenderType getRenderType(BlockState state) {
        return BlockRenderType.MODEL;
    }
    
    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
        if (!world.isClient) {
            BlockEntity blockEntity = world.getBlockEntity(pos);
            if (blockEntity instanceof OverheadWireBlockEntity wireEntity) {
                // 显示接触网状态
                player.sendMessage(wireEntity.getStatusText(), true);
            }
        }
        return ActionResult.SUCCESS;
    }
    
    @Override
    public void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.isOf(newState.getBlock())) {
            BlockEntity blockEntity = world.getBlockEntity(pos);
            if (blockEntity instanceof OverheadWireBlockEntity wireEntity) {
                // 通知相连的支柱移除该接触网
                wireEntity.notifyPoles();
            }
            super.onStateReplaced(state, world, pos, newState, moved);
        }
    }
    
    @Override
    public boolean canPlaceAt(BlockState state, World world, BlockPos pos) {
        // 检查是否连接到支柱或其他接触网
        Direction facing = state.get(FACING);
        BlockPos polePos1 = pos.offset(facing);
        BlockPos polePos2 = pos.offset(facing.getOpposite());
        
        boolean hasPole1 = world.getBlockState(polePos1).getBlock() instanceof OverheadWirePoleBlock;
        boolean hasPole2 = world.getBlockState(polePos2).getBlock() instanceof OverheadWirePoleBlock;
        boolean hasWire1 = world.getBlockState(polePos1).getBlock() instanceof OverheadWireBlock;
        boolean hasWire2 = world.getBlockState(polePos2).getBlock() instanceof OverheadWireBlock;
        
        return hasPole1 || hasPole2 || hasWire1 || hasWire2;
    }
}