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

public class SmallTVBlock extends Block implements TrainDisplaySystem.TrainDisplayBlock {
    // 方向属性
    public static final DirectionProperty FACING = HorizontalFacingBlock.FACING;
    // 电源状态属性
    public static final BooleanProperty POWERED = Properties.POWERED;
    // 音量属性（0-10）
    public static final BooleanProperty MUTED = BooleanProperty.of("muted");
    // 亮度属性（0-15）
    public static final BooleanProperty HIGH_BRIGHTNESS = BooleanProperty.of("high_brightness");

    public SmallTVBlock(Settings settings) {
        super(settings);
        this.setDefaultState(this.stateManager.getDefaultState()
                .with(FACING, Direction.NORTH)
                .with(POWERED, false)
                .with(MUTED, false)
                .with(HIGH_BRIGHTNESS, false));
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(FACING, POWERED, MUTED, HIGH_BRIGHTNESS);
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
            TrainDisplaySystem.getInstance().registerDisplayDevice(world, pos, TrainDisplaySystem.DisplayType.SMALL_TV);
            LogSystem.systemLog("小电视方块已放置: " + pos);
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
                // 通电时开始播放视频
                LogSystem.systemLog("小电视方块已激活: " + pos);
            } else {
                // 断电时停止播放视频
                LogSystem.systemLog("小电视方块已关闭: " + pos);
            }
        }
    }

    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
        if (world.isClient) {
            // 客户端只处理UI交互
            return ActionResult.SUCCESS;
        }
        
        // 检查是否使用了空手点击（用于切换功能）
        if (player.getStackInHand(hand).isEmpty()) {
            // 确定点击的面，以决定切换哪个功能
            Direction hitSide = hit.getSide();
            
            if (hitSide == state.get(FACING)) {
                // 点击屏幕，切换静音状态
                boolean muted = !state.get(MUTED);
                world.setBlockState(pos, state.with(MUTED, muted));
                
                String message = muted ? 
                        LanguageSystem.translate("krt.tv.muted") : 
                        LanguageSystem.translate("krt.tv.unmuted");
                player.sendMessage(Text.literal(message), false);
                LogSystem.systemLog("玩家 " + player.getEntityName() + " 切换了小电视静音状态: " + pos + ", 静音: " + muted);
            } else if (hitSide == Direction.UP || hitSide == Direction.DOWN) {
                // 点击顶部或底部，切换亮度
                boolean highBrightness = !state.get(HIGH_BRIGHTNESS);
                world.setBlockState(pos, state.with(HIGH_BRIGHTNESS, highBrightness));
                
                String message = highBrightness ? 
                        LanguageSystem.translate("krt.tv.high_brightness") : 
                        LanguageSystem.translate("krt.tv.low_brightness");
                player.sendMessage(Text.literal(message), false);
                LogSystem.systemLog("玩家 " + player.getEntityName() + " 切换了小电视亮度: " + pos + ", 高亮度: " + highBrightness);
            } else {
                // 点击侧面，打开视频选择界面（在实际实现中应该打开GUI）
                player.sendMessage(Text.literal(LanguageSystem.translate("krt.tv.select_video")), false);
                LogSystem.systemLog("玩家 " + player.getEntityName() + " 请求打开小电视视频选择界面: " + pos);
            }
        } else {
            // 如果手持物品，尝试加载视频文件
            // 在实际实现中，这里应该检查物品是否是视频文件，并加载视频
            player.sendMessage(Text.literal(LanguageSystem.translate("krt.tv.load_video_placeholder")), false);
            LogSystem.systemLog("玩家 " + player.getEntityName() + " 尝试加载视频到小电视: " + pos);
        }
        
        return ActionResult.CONSUME;
    }

    @Override
    public void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.isOf(newState.getBlock())) {
            // 方块被移除，从显示系统中取消注册
            if (!world.isClient()) {
                LogSystem.systemLog("小电视方块已移除: " + pos);
            }
            super.onStateReplaced(state, world, pos, newState, moved);
        }
    }

    /**
     * 检查方块是否处于激活状态
     */
    public boolean isActive(World world, BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        return state.get(POWERED);
    }

    /**
     * 检查是否静音
     */
    public boolean isMuted(World world, BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        return state.get(MUTED);
    }

    /**
     * 获取亮度级别
     */
    public int getBrightnessLevel(World world, BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        return state.get(HIGH_BRIGHTNESS) ? 15 : 8;
    }

    /**
     * 获取方块朝向
     */
    public Direction getFacing(World world, BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        return state.get(FACING);
    }

    /**
     * 获取当前播放的视频ID
     */
    public String getCurrentVideoId(World world, BlockPos pos) {
        // 在实际实现中，这里应该从方块的NBT数据中获取当前播放的视频ID
        // 这里简化处理，返回一个默认的视频ID
        return "default_video";
    }

    /**
     * 设置要播放的视频
     */
    public void setVideoToPlay(World world, BlockPos pos, String videoId) {
        // 在实际实现中，这里应该更新方块的NBT数据，设置要播放的视频ID
        // 并通知显示系统加载新视频
        if (!world.isClient()) {
            LogSystem.systemLog("小电视设置视频: " + pos + ", 视频ID: " + videoId);
        }
    }

    /**
     * 播放/暂停视频
     */
    public void togglePlayPause(World world, BlockPos pos) {
        // 在实际实现中，这里应该切换视频的播放状态
        if (!world.isClient()) {
            LogSystem.systemLog("小电视切换播放状态: " + pos);
        }
    }
}