package com.krt.mod.blockentity;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.Entity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.world.World;
import com.krt.mod.block.PlatformDoorBlock;
import com.krt.mod.entity.TrainEntity;
import java.util.List;

public class PlatformDoorBlockEntity extends BlockEntity {
    // 门状态枚举
    public enum DoorStatus {
        OPEN,
        CLOSED,
        OPENING,
        CLOSING
    }
    
    private DoorStatus doorStatus = DoorStatus.CLOSED;
    private int delayTimer = 0; // 延迟关门计时器
    private static final int DEFAULT_CLOSE_DELAY = 60; // 默认关闭延迟（3秒，60刻）
    private int passengerCount = 0; // 乘客计数
    
    public PlatformDoorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.PLATFORM_DOOR_BLOCK_ENTITY, pos, state);
    }
    
    @Override
    public void tick() {
        if (world == null || world.isClient) {
            return;
        }
        
        // 检查5格范围内是否有停靠的列车（速度小于0.1视为停止）
        List<TrainEntity> nearbyTrains = world.getEntitiesByClass(TrainEntity.class,
                new Box(pos).expand(5), train -> train.getCurrentSpeed() < 0.1);
        
        if (!nearbyTrains.isEmpty()) {
            // 有列车停靠时自动开门
            if (doorStatus != DoorStatus.OPEN && doorStatus != DoorStatus.OPENING) {
                openDoors();
            }
            // 重置延迟计时器
            delayTimer = 0;
        } else if (doorStatus == DoorStatus.OPEN) {
            // 无列车且门开着，检查是否需要关门
            if (passengerCount == 0) {
                // 如果计时器未启动，启动计时器
                if (delayTimer == 0) {
                    delayTimer = DEFAULT_CLOSE_DELAY;
                }
                
                // 计时器倒计时
                if (delayTimer > 0) {
                    delayTimer--;
                } else {
                    // 计时结束，关闭门
                    closeDoors();
                }
            } else {
                // 有乘客时重置计时器
                delayTimer = 0;
            }
        }
        
        // 检测乘客数量
        updatePassengerCount();
    }
    
    // 打开门
    public void openDoors() {
        if (world == null) return;
        
        doorStatus = DoorStatus.OPENING;
        PlatformDoorBlock.autoOpenDoor(world, pos);
        doorStatus = DoorStatus.OPEN;
        markDirty();
    }
    
    // 关闭门
    public void closeDoors() {
        if (world == null) return;
        
        doorStatus = DoorStatus.CLOSING;
        PlatformDoorBlock.autoCloseDoor(world, pos);
        doorStatus = DoorStatus.CLOSED;
        markDirty();
    }
    
    // 延迟关闭门
    public void closeDoorsAfterDelay(int seconds) {
        this.delayTimer = seconds * 20; // 转换为游戏刻
    }
    
    // 更新乘客数量
    private void updatePassengerCount() {
        if (world == null) return;
        
        // 检测门前方1格范围内的实体数量
        Box detectionBox = new Box(pos).offset(
                getCachedState().get(PlatformDoorBlock.FACING).getVector()).expand(0.5);
        
        List<Entity> entities = world.getEntitiesByClass(Entity.class, detectionBox, 
                entity -> entity.isAlive());
        
        // 排除列车实体，只计算乘客
        passengerCount = (int) entities.stream()
                .filter(entity -> !(entity instanceof TrainEntity))
                .count();
    }
    
    // 获取门状态
    public DoorStatus getDoorStatus() {
        return doorStatus;
    }
    
    // 获取乘客数量
    public int getPassengerCount() {
        return passengerCount;
    }
    
    @Override
    protected void writeNbt(NbtCompound nbt) {
        super.writeNbt(nbt);
        nbt.putInt("doorStatus", doorStatus.ordinal());
        nbt.putInt("delayTimer", delayTimer);
        nbt.putInt("passengerCount", passengerCount);
    }
    
    @Override
    public void readNbt(NbtCompound nbt) {
        super.readNbt(nbt);
        doorStatus = DoorStatus.values()[nbt.getInt("doorStatus")];
        delayTimer = nbt.getInt("delayTimer");
        passengerCount = nbt.getInt("passengerCount");
    }
}