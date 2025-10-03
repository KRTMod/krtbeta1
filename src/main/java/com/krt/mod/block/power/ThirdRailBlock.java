package com.krt.mod.block.power;

import com.krt.mod.blockentity.power.ThirdRailBlockEntity;
import net.minecraft.block.*;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
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
 * 第三轨方块 - 地下供电系统的低压轨道
 */
public class ThirdRailBlock extends BlockWithEntity {
    public static final DirectionProperty FACING = Properties.HORIZONTAL_FACING;
    public static final BooleanProperty POWERED = Properties.POWERED;
    public static final String ID = "third_rail";
    
    public ThirdRailBlock(Settings settings) {
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
        // 自动检测轨道方向
        Direction direction = detectRailDirection(ctx);
        if (direction == null) {
            direction = ctx.getPlayerFacing().getOpposite();
        }
        return this.getDefaultState().with(FACING, direction);
    }
    
    /**
     * 检测轨道方向
     */
    private Direction detectRailDirection(ItemPlacementContext ctx) {
        for (Direction dir : Direction.Type.HORIZONTAL) {
            BlockPos neighborPos = ctx.getBlockPos().offset(dir);
            BlockState neighborState = ctx.getWorld().getBlockState(neighborPos);
            
            if (neighborState.getBlock() instanceof RailwayBlock || 
                neighborState.getBlock() instanceof ThirdRailBlock) {
                return dir;
            }
        }
        return null;
    }
    
    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new ThirdRailBlockEntity(pos, state);
    }
    
    @Override
    public BlockRenderType getRenderType(BlockState state) {
        return BlockRenderType.MODEL;
    }
    
    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
        if (!world.isClient) {
            BlockEntity blockEntity = world.getBlockEntity(pos);
            if (blockEntity instanceof ThirdRailBlockEntity railEntity) {
                // 显示第三轨状态
                player.sendMessage(railEntity.getStatusText(), true);
                
                // 如果使用特殊工具可以检查绝缘
                if (player.getStackInHand(hand).getItem() instanceof ThirdRailTool) {
                    ThirdRailTool tool = (ThirdRailTool) player.getStackInHand(hand).getItem();
                    tool.interactWithRail(player, hand, railEntity);
                    return ActionResult.SUCCESS;
                }
            }
        }
        return ActionResult.SUCCESS;
    }
    
    @Override
    public void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.isOf(newState.getBlock())) {
            // 断开与电力系统的连接
            super.onStateReplaced(state, world, pos, newState, moved);
        }
    }
    
    @Override
    public void onSteppedOn(World world, BlockPos pos, BlockState state, Entity entity) {
        if (world.isClient) return;
        
        // 未绝缘的第三轨对生物造成伤害
        if (state.get(POWERED) && entity instanceof LivingEntity && !(entity instanceof PlayerEntity && ((PlayerEntity)entity).isCreative())) {
            BlockEntity blockEntity = world.getBlockEntity(pos);
            if (blockEntity instanceof ThirdRailBlockEntity railEntity && !railEntity.hasInsulation()) {
                // 750V低压电击伤害
                entity.damage(world.getDamageSources().lightningBolt(), 3.0F);
            }
        }
        
        super.onSteppedOn(world, pos, state, entity);
    }
    
    @Override
    public boolean canPlaceAt(BlockState state, World world, BlockPos pos) {
        // 检查下方方块是否为固体
        BlockPos downPos = pos.down();
        return world.getBlockState(downPos).isSolidBlock(world, downPos);
    }
}