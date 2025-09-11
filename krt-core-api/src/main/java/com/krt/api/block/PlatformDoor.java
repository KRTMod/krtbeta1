package com.krt.api.block;

import com.krt.api.audio.KAudioManager;
import com.krt.api.data.Station;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
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
 * 屏蔽门基类
 * 实现屏蔽门的基本功能
 */
public abstract class PlatformDoor extends Block {
    public static final BooleanProperty OPEN = Properties.OPEN;
    public static final DirectionProperty FACING = Properties.HORIZONTAL_FACING;
    
    // 屏蔽门状态
    private boolean isAutomaticMode = true; // 默认自动模式
    private String stationId = null; // 所属车站ID
    
    // 音频ID
    protected String openSoundId = "door.open";
    protected String closeSoundId = "door.close";
    
    public PlatformDoor(Settings settings) {
        super(settings);
        this.setDefaultState(this.stateManager.getDefaultState()
                .with(OPEN, false)
                .with(FACING, Direction.NORTH));
    }
    
    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(OPEN, FACING);
    }
    
    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
        if (world.isClient) {
            return ActionResult.SUCCESS;
        }
        
        ItemStack stack = player.getStackInHand(hand);
        
        // 检查是否手持屏蔽门钥匙
        if (isPlatformKey(stack)) {
            // 使用钥匙手动开关门
            toggleDoor(world, pos, state);
            return ActionResult.CONSUME;
        }
        
        // 非自动模式下，允许管理员或有权限的玩家手动开关门
        if (!isAutomaticMode && hasPermission(player)) {
            toggleDoor(world, pos, state);
            return ActionResult.CONSUME;
        }
        
        return ActionResult.PASS;
    }
    
    /**
     * 切换门的开关状态
     */
    protected void toggleDoor(World world, BlockPos pos, BlockState state) {
        boolean isOpen = state.get(OPEN);
        BlockState newState = state.with(OPEN, !isOpen);
        world.setBlockState(pos, newState, NOTIFY_LISTENERS);
        
        // 播放开关门声音
        if (isOpen) {
            playCloseSound(world, pos);
        } else {
            playOpenSound(world, pos);
        }
    }
    
    /**
     * 自动开门（当列车到站时调用）
     */
    public void autoOpen(World world, BlockPos pos) {
        if (isAutomaticMode) {
            BlockState state = world.getBlockState(pos);
            if (!state.get(OPEN)) {
                world.setBlockState(pos, state.with(OPEN, true), NOTIFY_LISTENERS);
                playOpenSound(world, pos);
            }
        }
    }
    
    /**
     * 自动关门（当列车离站时调用）
     */
    public void autoClose(World world, BlockPos pos) {
        if (isAutomaticMode) {
            BlockState state = world.getBlockState(pos);
            if (state.get(OPEN)) {
                world.setBlockState(pos, state.with(OPEN, false), NOTIFY_LISTENERS);
                playCloseSound(world, pos);
            }
        }
    }
    
    /**
     * 播放开门声音
     */
    protected void playOpenSound(World world, BlockPos pos) {
        KAudioManager.getInstance().playAudio(openSoundId, pos.getX(), pos.getY(), pos.getZ());
    }
    
    /**
     * 播放关门声音
     */
    protected void playCloseSound(World world, BlockPos pos) {
        KAudioManager.getInstance().playAudio(closeSoundId, pos.getX(), pos.getY(), pos.getZ());
    }
    
    /**
     * 检查物品是否为屏蔽门钥匙
     */
    protected boolean isPlatformKey(ItemStack stack) {
        // 这里应该实现检查物品是否为屏蔽门钥匙的逻辑
        return false;
    }
    
    /**
     * 检查玩家是否有权限操作屏蔽门
     */
    protected boolean hasPermission(PlayerEntity player) {
        // 这里应该实现检查玩家权限的逻辑
        return player.isCreative();
    }
    
    /**
     * 设置自动模式
     */
    public void setAutomaticMode(boolean automaticMode) {
        this.isAutomaticMode = automaticMode;
    }
    
    /**
     * 设置所属车站
     */
    public void setStationId(String stationId) {
        this.stationId = stationId;
    }
    
    /**
     * 获取所属车站
     */
    public String getStationId() {
        return stationId;
    }
}