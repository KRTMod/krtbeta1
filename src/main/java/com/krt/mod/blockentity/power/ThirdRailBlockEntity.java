package com.krt.mod.blockentity.power;

import com.krt.mod.system.PowerSupplySystem;
import com.krt.mod.system.PowerSupplySystemManager;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * 第三轨方块实体 - 管理第三轨电力传输和绝缘状态
 */
public class ThirdRailBlockEntity extends BlockEntity {
    private int powerLevel = 0;
    private boolean powered = false;
    private int insulationLevel = 100; // 绝缘等级(0-100)
    private boolean shortCircuitRisk = false; // 短路风险
    private int waterDamage = 0; // 水损坏等级
    
    public ThirdRailBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.THIRD_RAIL, pos, state);
    }
    
    @Override
    public void readNbt(NbtCompound nbt) {
        super.readNbt(nbt);
        powerLevel = nbt.getInt("PowerLevel");
        powered = nbt.getBoolean("Powered");
        insulationLevel = nbt.getInt("InsulationLevel");
        shortCircuitRisk = nbt.getBoolean("ShortCircuitRisk");
        waterDamage = nbt.getInt("WaterDamage");
    }
    
    @Override
    public void writeNbt(NbtCompound nbt) {
        super.writeNbt(nbt);
        nbt.putInt("PowerLevel", powerLevel);
        nbt.putBoolean("Powered", powered);
        nbt.putInt("InsulationLevel", insulationLevel);
        nbt.putBoolean("ShortCircuitRisk", shortCircuitRisk);
        nbt.putInt("WaterDamage", waterDamage);
    }
    
    /**
     * 获取状态文本
     */
    public Text getStatusText() {
        String powerStatus = powered ? "已通电" : "未通电";
        String insulationStatus = getInsulationStatus();
        String riskStatus = shortCircuitRisk ? "有短路风险" : "无短路风险";
        String waterStatus = waterDamage > 0 ? "进水损坏: " + waterDamage + "%" : "无水损坏";
        
        return Text.translatable("krt.gui.third_rail.status", 
                powerStatus, insulationLevel + "%", insulationStatus, riskStatus, waterStatus);
    }
    
    /**
     * 获取绝缘状态描述
     */
    private String getInsulationStatus() {
        if (insulationLevel < 20) return "严重破损";
        if (insulationLevel < 50) return "中度破损";
        if (insulationLevel < 80) return "轻微破损";
        return "完好";
    }
    
    /**
     * 设置供电状态
     */
    public void setPowered(boolean powered) {
        this.powered = powered;
        this.powerLevel = powered ? 100 : 0;
        markDirty();
        updateBlockState();
    }
    
    /**
     * 获取是否通电
     */
    public boolean isPowered() {
        return powered;
    }
    
    /**
     * 获取电力等级
     */
    public int getPowerLevel() {
        return powerLevel;
    }
    
    /**
     * 获取绝缘等级
     */
    public int getInsulationLevel() {
        return insulationLevel;
    }
    
    /**
     * 是否有绝缘
     */
    public boolean hasInsulation() {
        return insulationLevel > 30;
    }
    
    /**
     * 修复绝缘
     */
    public void repairInsulation(int amount) {
        insulationLevel = Math.min(100, insulationLevel + amount);
        markDirty();
    }
    
    /**
     * 更新方块状态
     */
    private void updateBlockState() {
        if (world != null && world.getBlockState(pos).getBlock() instanceof BlockWithEntity) {
            BlockState state = world.getBlockState(pos);
            world.setBlockState(pos, state.with(net.minecraft.state.property.Properties.POWERED, powered));
        }
    }
    
    /**
     * 增加水损坏
     */
    public void increaseWaterDamage(int amount) {
        waterDamage = Math.min(100, waterDamage + amount);
        markDirty();
    }
    
    /**
     * 清理水损坏
     */
    public void cleanWaterDamage() {
        waterDamage = 0;
        markDirty();
    }
    
    /**
     * 检查短路风险
     */
    public void checkShortCircuitRisk() {
        shortCircuitRisk = false;
        
        // 绝缘损坏会增加短路风险
        if (insulationLevel < 50) {
            shortCircuitRisk = true;
        }
        
        // 有水损坏时短路风险极高
        if (waterDamage > 30) {
            shortCircuitRisk = true;
        }
    }
    
    /**
     * 服务器端tick更新
     */
    public static void tick(World world, BlockPos pos, BlockState state, ThirdRailBlockEntity entity) {
        if (world.isClient) {
            return;
        }
        
        // 检查水损坏
        entity.checkWaterDamage();
        
        // 检查短路风险
        entity.checkShortCircuitRisk();
        
        // 检查电力连接
        boolean wasPowered = entity.powered;
        entity.checkPowerConnection();
        
        // 更新绝缘老化
        entity.updateInsulationAging();
        
        if (wasPowered != entity.powered) {
            entity.updateBlockState();
        }
    }
    
    /**
     * 检查水损坏
     */
    private void checkWaterDamage() {
        // 检查周围是否有水
        boolean hasWater = false;
        for (int x = -1; x <= 1; x++) {
            for (int y = -1; y <= 1; y++) {
                for (int z = -1; z <= 1; z++) {
                    if (x == 0 && y == 0 && z == 0) continue;
                    
                    BlockPos neighborPos = pos.add(x, y, z);
                    BlockState neighborState = world.getBlockState(neighborPos);
                    
                    if (neighborState.getMaterial() == net.minecraft.block.Material.WATER) {
                        hasWater = true;
                        break;
                    }
                }
                if (hasWater) break;
            }
            if (hasWater) break;
        }
        
        // 如果有水，增加损坏
        if (hasWater && world.getTime() % 20 == 0) { // 每秒增加
            increaseWaterDamage(1);
            // 水会加速绝缘损坏
            if (insulationLevel > 0) {
                insulationLevel--;
                markDirty();
            }
        } else if (waterDamage > 0 && world.getTime() % 100 == 0) { // 干燥时缓慢恢复
            waterDamage = Math.max(0, waterDamage - 1);
            markDirty();
        }
    }
    
    /**
     * 检查电力连接
     */
    private void checkPowerConnection() {
        // 检查相邻的第三轨或电力连接器
        for (int x = -1; x <= 1; x++) {
            for (int y = -1; y <= 1; y++) {
                for (int z = -1; z <= 1; z++) {
                    if (x == 0 && y == 0 && z == 0) continue;
                    
                    BlockPos neighborPos = pos.add(x, y, z);
                    BlockEntity neighborEntity = world.getBlockEntity(neighborPos);
                    
                    // 连接到其他第三轨
                    if (neighborEntity instanceof ThirdRailBlockEntity railEntity && railEntity.isPowered()) {
                        if (!shortCircuitRisk || insulationLevel > 0) { // 短路风险高且无绝缘时不传输电力
                            setPowered(true);
                            return;
                        }
                    }
                    
                    // 连接到电力连接器
                    if (neighborEntity instanceof RailPowerConnectorBlockEntity connector) {
                        if (connector.isConnectedToPowerGrid() && 
                            connector.getPowerType() == PowerSupplySystem.PowerType.THIRD_RAIL) {
                            setPowered(true);
                            return;
                        }
                    }
                }
            }
        }
        
        setPowered(false);
    }
    
    /**
     * 更新绝缘老化
     */
    private void updateInsulationAging() {
        // 每200tick老化一次
        if (world.getTime() % 200 == 0) {
            int agingAmount = 1;
            
            // 通电状态下老化更快
            if (powered) {
                agingAmount += 1;
            }
            
            // 水损坏加速老化
            if (waterDamage > 0) {
                agingAmount += waterDamage / 20;
            }
            
            insulationLevel = Math.max(0, insulationLevel - agingAmount);
            markDirty();
        }
    }
}