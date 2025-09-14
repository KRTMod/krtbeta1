package com.krt.mod.block;

import net.fabricmc.fabric.api.object.builder.v1.block.FabricBlockSettings;
import net.minecraft.block.Block;
import net.minecraft.block.BlockEntityProvider;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.HorizontalFacingBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.state.property.DirectionProperty;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import com.krt.mod.KRTMod;
import com.krt.mod.system.LogSystem;

public class ATPSignalBlock extends Block implements BlockEntityProvider {
    public static final DirectionProperty FACING = HorizontalFacingBlock.FACING;
    public static final BooleanProperty POWERED = BooleanProperty.of("powered");

    public ATPSignalBlock(Settings settings) {
        super(settings);
        this.setDefaultState(this.stateManager.getDefaultState().with(FACING, Direction.NORTH).with(POWERED, false));
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getPlacementState(ItemPlacementContext ctx) {
        return this.getDefaultState().with(FACING, ctx.getPlayerFacing());
    }

    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new ATPSignalBlockEntity(pos, state);
    }

    @Override
    public BlockRenderType getRenderType(BlockState state) {
        return BlockRenderType.MODEL;
    }

    @Override
    public void neighborUpdate(BlockState state, World world, BlockPos pos, Block sourceBlock, BlockPos sourcePos, boolean notify) {
        if (world.isClient) {
            return;
        }
        
        boolean wasPowered = state.get(POWERED);
        boolean isPowered = world.isReceivingRedstonePower(pos);
        
        if (wasPowered != isPowered) {
            world.setBlockState(pos, state.with(POWERED, isPowered));
            
            // 更新方块实体的状态
            BlockEntity blockEntity = world.getBlockEntity(pos);
            if (blockEntity instanceof ATPSignalBlockEntity) {
                ATPSignalBlockEntity atpEntity = (ATPSignalBlockEntity) blockEntity;
                if (isPowered) {
                    LogSystem.systemLog("ATP信号方块已激活: " + pos);
                } else {
                    LogSystem.systemLog("ATP信号方块已关闭: " + pos);
                }
            }
        }
    }

    // 获取ATP信号数据
    public static ATPSignalBlockEntity.ATPData getATPData(World world, BlockPos pos) {
        BlockEntity blockEntity = world.getBlockEntity(pos);
        if (blockEntity instanceof ATPSignalBlockEntity) {
            return ((ATPSignalBlockEntity) blockEntity).getATPData();
        }
        // 如果没有方块实体，返回默认数据
        return new ATPSignalBlockEntity.ATPData();
    }
}