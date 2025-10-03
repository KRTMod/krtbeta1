package com.krt.mod.blockentity.power;

import com.krt.mod.system.PowerSupplySystem;
import com.krt.mod.system.PowerSupplySystemManager;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import com.krt.mod.blockentity.ModBlockEntities;
import net.minecraft.inventory.Inventories;
import net.minecraft.item.ItemStack;
import com.krt.mod.block.power.PowerTransmissionLineBlock;
import com.krt.mod.block.power.PowerStorageBlock;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.text.Text;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * 电力存储块方块实体，管理电力的存储和释放
 */
public class PowerStorageBlockEntity extends BlockEntity {
    private static final int MAX_CAPACITY = 100000;
    private int storedPower = 0;
    private boolean isCharging = false;
    private boolean isDischarging = false;
    private boolean isContributingPower = false;
    
    public PowerStorageBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.POWER_STORAGE, pos, state);
    }
    
    @Override
    public void readNbt(NbtCompound nbt) {
        super.readNbt(nbt);
        storedPower = nbt.getInt("StoredPower");
        isCharging = nbt.getBoolean("IsCharging");
        isDischarging = nbt.getBoolean("IsDischarging");
        isContributingPower = nbt.getBoolean("IsContributingPower");
    }
    
    @Override
    public void writeNbt(NbtCompound nbt) {
        super.writeNbt(nbt);
        nbt.putInt("StoredPower", storedPower);
        nbt.putBoolean("IsCharging", isCharging);
        nbt.putBoolean("IsDischarging", isDischarging);
        nbt.putBoolean("IsContributingPower", isContributingPower);
    }
    
    /**
     * 获取当前存储的电力百分比
     */
    public int getPowerPercentage() {
        return (int) ((double) storedPower / MAX_CAPACITY * 100);
    }
    
    /**
     * 获取当前存储的电力值
     */
    public int getPower() {
        return storedPower;
    }
    
    /**
     * 获取状态文本
     */
    public Text getStatusText() {
        String status;
        if (isCharging && isDischarging) {
            status = "异常状态";
        } else if (isCharging) {
            status = "充电中";
        } else if (isDischarging) {
            status = "放电中";
        } else {
            status = "待机";
        }
        
        return Text.translatable("krt.gui.power_storage.status", 
                status, 
                storedPower + "/" + MAX_CAPACITY, 
                getPowerPercentage() + "%");
    }
    
    /**
     * 存储电力
     */
    public int storePower(int amount) {
        int before = storedPower;
        storedPower = Math.min(storedPower + amount, MAX_CAPACITY);
        isCharging = storedPower < MAX_CAPACITY;
        isDischarging = false;
        return storedPower - before;
    }
    
    /**
     * 释放电力
     */
    public int releasePower(int amount) {
        int before = storedPower;
        storedPower = Math.max(storedPower - amount, 0);
        isDischarging = storedPower > 0;
        isCharging = false;
        return before - storedPower;
    }
    
    /**
     * 检查是否连接到电源
     */
    private boolean isConnectedToPowerSource(World world) {
        for (int x = -1; x <= 1; x++) {
            for (int y = -1; y <= 1; y++) {
                for (int z = -1; z <= 1; z++) {
                    if (x == 0 && y == 0 && z == 0) continue;
                    
                    BlockPos neighborPos = pos.add(x, y, z);
                    BlockEntity neighborEntity = world.getBlockEntity(neighborPos);
                    
                    if (neighborEntity instanceof PowerGeneratorBlockEntity) {
                        return true;
                    }
                    
                    if (world.getBlockState(neighborPos).getBlock() instanceof PowerTransmissionLineBlock) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
    
    /**
     * 获取是否正在向系统贡献电力
     */
    public boolean isContributingPower() {
        return isContributingPower;
    }
    
    /**
     * 服务器端tick更新
     */
    public static void tick(World world, BlockPos pos, BlockState state, PowerStorageBlockEntity entity) {
        if (world.isClient) {
            return;
        }
        
        boolean wasContributing = entity.isContributingPower;
        entity.isContributingPower = false;
        
        // 检查是否连接到电源
        boolean connectedToPower = entity.isConnectedToPowerSource(world);
        
        // 处理充电/放电逻辑
        if (connectedToPower && entity.storedPower < entity.MAX_CAPACITY) {
            // 充电状态
            entity.isCharging = true;
            entity.isDischarging = false;
            // 模拟从电源获取电力（实际应从发电系统获取）
            int chargeAmount = 50; // 每tick充电量
            entity.storePower(chargeAmount);
        } else if (!connectedToPower && entity.storedPower > 0) {
            // 放电状态，向供电系统提供电力
            entity.isCharging = false;
            entity.isDischarging = true;
            entity.isContributingPower = true;
            
            PowerSupplySystem powerSystem = PowerSupplySystemManager.getForWorld(world);
            if (powerSystem != null) {
                // 计算可提供的电力
                int powerToSupply = Math.min(entity.storedPower, 100);
                powerSystem.addPowerSource(pos, PowerSupplySystem.PowerType.OVERHEAD_WIRE, powerToSupply);
                entity.releasePower(powerToSupply);
            }
        } else {
            // 待机状态
            entity.isCharging = false;
            entity.isDischarging = false;
        }
        
        // 如果状态改变，通知客户端
        if (wasContributing != entity.isContributingPower || 
            state.get(PowerStorageBlock.POWERED) != (entity.isCharging || entity.isDischarging)) {
            world.setBlockState(pos, state.with(PowerStorageBlock.POWERED, 
                    entity.isCharging || entity.isDischarging));
            world.updateListeners(pos, state, state, 3);
        }
        
        entity.markDirty();
    }
    
    /**
     * 掉落物品栏内容
     */
    public void dropInventory(World world, BlockPos pos) {
        // 此方块没有物品栏，但保留方法以便将来扩展
    }
}