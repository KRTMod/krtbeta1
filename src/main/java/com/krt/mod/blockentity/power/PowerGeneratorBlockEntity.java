package com.krt.mod.blockentity.power;

import com.krt.mod.inventory.InventoryScreens;
import com.krt.mod.inventory.screen.PowerGeneratorScreenHandler;
import com.krt.mod.system.PowerSupplySystem;
import com.krt.mod.system.PowerSupplySystemManager;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import com.krt.mod.blockentity.ModBlockEntities;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventories;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.entity.ItemEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.screen.NamedScreenHandlerFactory;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.text.Text;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * 发电机方块实体，负责能源生产和管理
 */
public class PowerGeneratorBlockEntity extends BlockEntity implements Inventory, NamedScreenHandlerFactory {
    private static final int INVENTORY_SIZE = 2;
    private static final int FUEL_SLOT = 0;
    private static final int OUTPUT_SLOT = 1;
    
    private final DefaultedList<ItemStack> inventory = DefaultedList.ofSize(INVENTORY_SIZE, ItemStack.EMPTY);
    private int burnTime = 0;
    private int maxBurnTime = 0;
    private int powerGeneration = 200; // 每tick发电量
    private boolean isActive = false;
    
    public PowerGeneratorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.POWER_GENERATOR, pos, state);
    }
    
    @Override
    public void readNbt(NbtCompound nbt) {
        super.readNbt(nbt);
        Inventories.readNbt(nbt, inventory);
        burnTime = nbt.getInt("BurnTime");
        maxBurnTime = nbt.getInt("MaxBurnTime");
        isActive = nbt.getBoolean("IsActive");
        powerGeneration = nbt.getInt("PowerGeneration");
    }
    
    @Override
    public void writeNbt(NbtCompound nbt) {
        super.writeNbt(nbt);
        Inventories.writeNbt(nbt, inventory);
        nbt.putInt("BurnTime", burnTime);
        nbt.putInt("MaxBurnTime", maxBurnTime);
        nbt.putBoolean("IsActive", isActive);
        nbt.putInt("PowerGeneration", powerGeneration);
    }
    
    @Override
    public Text getDisplayName() {
        return Text.translatable("block.krt.power_generator");
    }
    
    @Override
    public ScreenHandler createMenu(int syncId, PlayerInventory playerInventory, PlayerEntity player) {
        return new PowerGeneratorScreenHandler(syncId, playerInventory, this);
    }
    
    @Override
    public int size() {
        return inventory.size();
    }
    
    @Override
    public boolean isEmpty() {
        for (ItemStack stack : inventory) {
            if (!stack.isEmpty()) {
                return false;
            }
        }
        return true;
    }
    
    @Override
    public ItemStack getStack(int slot) {
        return inventory.get(slot);
    }
    
    @Override
    public ItemStack removeStack(int slot, int amount) {
        return Inventories.splitStack(inventory, slot, amount);
    }
    
    @Override
    public ItemStack removeStack(int slot) {
        return Inventories.removeStack(inventory, slot);
    }
    
    @Override
    public void setStack(int slot, ItemStack stack) {
        inventory.set(slot, stack);
        if (stack.getCount() > stack.getMaxCount()) {
            stack.setCount(stack.getMaxCount());
        }
    }
    
    @Override
    public boolean canPlayerUse(PlayerEntity player) {
        return player.squaredDistanceTo(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 64.0;
    }
    
    @Override
    public void clear() {
        inventory.clear();
    }
    
    /**
     * 获取燃料燃烧进度
     */
    public int getFuelProgress() {
        if (maxBurnTime == 0) {
            return 0;
        }
        return (int) (burnTime * 100.0 / maxBurnTime);
    }
    
    /**
     * 获取状态文本
     */
    public Text getStatusText() {
        String status = isActive ? "运行中" : "未运行";
        String powerInfo = "发电量: " + powerGeneration + "/tick";
        String fuelInfo = "燃料: " + burnTime + "/" + maxBurnTime;
        return Text.translatable("krt.gui.power_generator.status", status, powerInfo, fuelInfo);
    }
    
    /**
     * 获取是否处于活动状态
     */
    public boolean isActive() {
        return isActive;
    }
    
    /**
     * 获取发电量
     */
    public int getPowerGeneration() {
        return powerGeneration;
    }
    
    /**
     * 掉落物品栏内容
     */
    public void dropInventory(World world, BlockPos pos) {
        for (ItemStack stack : inventory) {
            if (!stack.isEmpty()) {
                // 在Minecraft 1.19.2中使用ItemEntity生成物品
                ItemEntity itemEntity = new ItemEntity(world, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, stack);
                world.spawnEntity(itemEntity);
            }
        }
    }
    
    /**
     * 服务器端tick更新
     */
    public static void tick(World world, BlockPos pos, BlockState state, PowerGeneratorBlockEntity entity) {
        if (world.isClient) {
            return;
        }
        
        // 检查燃料
        if (entity.burnTime > 0) {
            entity.burnTime--;
            entity.isActive = true;
            
            // 向供电系统提供电力
            PowerSupplySystem powerSystem = PowerSupplySystemManager.getForWorld(world);
            if (powerSystem != null && powerSystem.addPowerSource(pos, PowerSupplySystem.PowerType.OVERHEAD_WIRE, entity.powerGeneration)) {
                // 电源已添加到系统
            }
        } else {
            entity.isActive = false;
            
            // 尝试消耗燃料
            ItemStack fuelStack = entity.getStack(FUEL_SLOT);
            if (!fuelStack.isEmpty()) {
                // 计算燃料值（示例实现）
                entity.maxBurnTime = calculateFuelValue(fuelStack.getItem());
                if (entity.maxBurnTime > 0) {
                    entity.burnTime = entity.maxBurnTime;
                    fuelStack.decrement(1);
                    entity.markDirty();
                }
            }
        }
        
        // 如果状态改变，通知客户端
        boolean wasActive = entity.isActive;
        boolean nowActive = entity.burnTime > 0;
        if (wasActive != nowActive) {
            world.updateListeners(pos, state, state, 3);
        }
    }
    
    /**
     * 计算燃料值
     */
    private static int calculateFuelValue(Item item) {
        // 示例燃料值计算，实际应根据不同燃料物品设置不同的值
        String itemId = item.toString();
        if (itemId.contains("coal")) {
            return 1600; // 煤炭可燃烧1600tick (80秒)
        } else if (itemId.contains("wood")) {
            return 300; // 木材可燃烧300tick (15秒)
        } else if (itemId.contains("lava")) {
            return 20000; // 熔岩桶可燃烧20000tick (1000秒)
        }
        return 0;
    }
}