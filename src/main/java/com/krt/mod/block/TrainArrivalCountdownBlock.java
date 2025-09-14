package com.krt.mod.block;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.HorizontalFacingBlock;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.state.property.DirectionProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.BlockMirror;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import com.krt.mod.system.LanguageSystem;
import com.krt.mod.system.LogSystem;
import com.krt.mod.system.TrainDisplaySystem;

import java.util.Random;

public class TrainArrivalCountdownBlock extends Block implements TrainDisplaySystem.TrainDisplayBlock {
    // 方向属性
    public static final DirectionProperty FACING = HorizontalFacingBlock.FACING;
    // 电源状态属性
    public static final BooleanProperty POWERED = Properties.POWERED;
    // 是否显示详细信息属性
    public static final BooleanProperty SHOW_DETAILS = BooleanProperty.of("show_details");

    public TrainArrivalCountdownBlock(Settings settings) {
        super(settings);
        this.setDefaultState(this.stateManager.getDefaultState()
                .with(FACING, Direction.NORTH)
                .with(POWERED, false)
                .with(SHOW_DETAILS, false));
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(FACING, POWERED, SHOW_DETAILS);
    }

    @Override
    public BlockState getPlacementState(ItemPlacementContext ctx) {
        // 设置方块朝向为玩家朝向
        Direction facing = ctx.getPlayerFacing().getOpposite();
        World world = ctx.getWorld();
        BlockPos pos = ctx.getBlockPos();
        
        // 检查是否有红石信号
        boolean powered = world.isReceivingRedstonePower(pos);
        
        // 注册到显示系统
        if (!world.isClient()) {
            TrainDisplaySystem.getInstance().registerDisplayDevice(world, pos, TrainDisplaySystem.DisplayType.ARRIVAL_COUNTDOWN);
            LogSystem.systemLog("列车到站倒计时方块已放置: " + pos);
        }
        
        return this.getDefaultState().with(FACING, facing).with(POWERED, powered);
    }

    @Override
    public BlockState rotate(BlockState state, BlockRotation rotation) {
        return state.with(FACING, rotation.rotate(state.get(FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, BlockMirror mirror) {
        return state.rotate(mirror.getRotation(state.get(FACING)));
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
            
            if (isPowered) {
                // 通电时开始显示倒计时
                LogSystem.systemLog("列车到站倒计时方块已激活: " + pos);
            } else {
                // 断电时停止显示倒计时
                LogSystem.systemLog("列车到站倒计时方块已关闭: " + pos);
            }
        }
    }

    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
        if (world.isClient) {
            // 客户端只处理UI交互
            return ActionResult.SUCCESS;
        }
        
        // 切换详细信息显示
        boolean showDetails = !state.get(SHOW_DETAILS);
        world.setBlockState(pos, state.with(SHOW_DETAILS, showDetails));
        
        // 发送消息给玩家
        String message = showDetails ? 
                LanguageSystem.translate("krt.display.showing_details") : 
                LanguageSystem.translate("krt.display.hiding_details");
        player.sendMessage(Text.literal(message), false);
        
        LogSystem.systemLog("玩家 " + player.getEntityName() + " 切换了倒计时方块显示模式: " + pos + ", 显示详细信息: " + showDetails);
        
        return ActionResult.CONSUME;
    }

    @Override
    public void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.isOf(newState.getBlock())) {
            // 方块被移除，从显示系统中取消注册
            if (!world.isClient()) {
                LogSystem.systemLog("列车到站倒计时方块已移除: " + pos);
            }
            super.onStateReplaced(state, world, pos, newState, moved);
        }
    }

    /**
     * 获取当前显示的倒计时文本
     */
    public String getCountdownText(World world, BlockPos pos) {
        // 在实际实现中，这里应该从TrainDisplaySystem获取倒计时信息
        // 这里简化处理，返回一个示例文本
        return "02:30";
    }

    /**
     * 获取当前显示的列车信息
     */
    public String getTrainInfoText(World world, BlockPos pos) {
        // 在实际实现中，这里应该从TrainDisplaySystem获取列车信息
        // 这里简化处理，返回一个示例文本
        return "K1线 终点站: 大学城南站";
    }

    /**
     * 检查是否应该显示详细信息
     */
    public boolean shouldShowDetails(World world, BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        return state.get(SHOW_DETAILS);
    }

    /**
     * 检查方块是否处于激活状态
     */
    public boolean isActive(World world, BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        return state.get(POWERED);
    }

    /**
     * 获取方块朝向
     */
    public Direction getFacing(World world, BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        return state.get(FACING);
    }
}