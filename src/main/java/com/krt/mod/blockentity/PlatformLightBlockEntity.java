package com.krt.mod.blockentity;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.world.World;
import com.krt.mod.block.PlatformLightBlock;
import com.krt.mod.entity.TrainEntity;
import java.util.List;

public class PlatformLightBlockEntity extends BlockEntity {
    private static final int PASSENGER_CHECK_RADIUS = 10; // 乘客检测半径
    private int passengerCount = 0; // 乘客数量
    private boolean automaticControl = true; // 是否启用自动控制
    private static final int CHECK_INTERVAL = 10; // 检查间隔（游戏刻）
    private int checkTimer = 0;
    
    public PlatformLightBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.PLATFORM_LIGHT_BLOCK_ENTITY, pos, state);
    }
    
    @Override
    public void tick() {
        if (world == null || world.isClient) {
            return;
        }
        
        // 使用定时器减少检测频率，提高性能
        if (checkTimer > 0) {
            checkTimer--;
            return;
        }
        
        checkTimer = CHECK_INTERVAL;
        
        if (automaticControl) {
            // 检测乘客数量
            updatePassengerCount();
            
            // 检测是否有列车停靠
            boolean hasTrainStopped = checkTrainStopped();
            
            // 根据乘客数量和列车状态控制灯光
            boolean shouldBeLit = passengerCount > 0 || hasTrainStopped;
            BlockState currentState = getCachedState();
            
            if (currentState.get(PlatformLightBlock.LIT) != shouldBeLit) {
                PlatformLightBlock.setLightState(world, pos, shouldBeLit);
                markDirty();
            }
        }
    }
    
    // 更新乘客数量
    private void updatePassengerCount() {
        if (world == null) return;
        
        // 检测范围内的所有实体
        List<Entity> entities = world.getEntitiesByClass(Entity.class, 
                new Box(pos).expand(PASSENGER_CHECK_RADIUS), entity -> entity.isAlive());
        
        // 过滤出玩家实体（作为乘客）
        passengerCount = (int) entities.stream()
                .filter(entity -> entity instanceof PlayerEntity || entity.hasPassengers())
                .count();
    }
    
    // 检查是否有列车停靠
    private boolean checkTrainStopped() {
        if (world == null) return false;
        
        // 检测范围内是否有停止的列车（速度小于0.1视为停止）
        List<TrainEntity> nearbyTrains = world.getEntitiesByClass(TrainEntity.class, 
                new Box(pos).expand(PASSENGER_CHECK_RADIUS * 2), 
                train -> train.getCurrentSpeed() < 0.1);
        
        return !nearbyTrains.isEmpty();
    }
    
    // 切换自动控制模式
    public void toggleAutomaticControl() {
        automaticControl = !automaticControl;
        markDirty();
    }
    
    // 设置自动控制模式
    public void setAutomaticControl(boolean automatic) {
        this.automaticControl = automatic;
        markDirty();
    }
    
    // 获取自动控制状态
    public boolean isAutomaticControl() {
        return automaticControl;
    }
    
    // 获取乘客数量
    public int getPassengerCount() {
        return passengerCount;
    }
    
    @Override
    protected void writeNbt(NbtCompound nbt) {
        super.writeNbt(nbt);
        nbt.putBoolean("automaticControl", automaticControl);
        nbt.putInt("checkTimer", checkTimer);
    }
    
    @Override
    public void readNbt(NbtCompound nbt) {
        super.readNbt(nbt);
        automaticControl = nbt.getBoolean("automaticControl");
        checkTimer = nbt.getInt("checkTimer");
    }
}