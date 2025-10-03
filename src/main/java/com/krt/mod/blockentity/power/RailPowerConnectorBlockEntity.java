package com.krt.mod.blockentity.power;

import com.krt.mod.system.PowerSupplySystem;
import com.krt.mod.system.PowerSupplySystemManager;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import com.krt.mod.blockentity.ModBlockEntities;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.text.Text;
import com.krt.mod.block.power.PowerTransmissionLineBlock;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * 轨道电力连接器方块实体，管理轨道与电力系统的连接
 */
public class RailPowerConnectorBlockEntity extends BlockEntity {
    private int powerLevel = 0;
    private boolean connectedToPowerGrid = false;
    private PowerSupplySystem.PowerType powerType = PowerSupplySystem.PowerType.OVERHEAD_WIRE;
    private int coverageRadius = 16; // 覆盖半径（方块数）
    
    public RailPowerConnectorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.RAIL_POWER_CONNECTOR, pos, state);
    }
    
    @Override
    public void readNbt(NbtCompound nbt) {
        super.readNbt(nbt);
        powerLevel = nbt.getInt("PowerLevel");
        connectedToPowerGrid = nbt.getBoolean("ConnectedToPowerGrid");
        powerType = PowerSupplySystem.PowerType.valueOf(nbt.getString("PowerType"));
        coverageRadius = nbt.getInt("CoverageRadius");
    }
    
    @Override
    public void writeNbt(NbtCompound nbt) {
        super.writeNbt(nbt);
        nbt.putInt("PowerLevel", powerLevel);
        nbt.putBoolean("ConnectedToPowerGrid", connectedToPowerGrid);
        nbt.putString("PowerType", powerType.name());
        nbt.putInt("CoverageRadius", coverageRadius);
    }
    
    /**
     * 获取状态文本
     */
    public Text getStatusText() {
        String connectionStatus = connectedToPowerGrid ? "已连接" : "未连接";
        String powerInfo = "电力等级: " + powerLevel;
        String coverageInfo = "覆盖范围: " + coverageRadius + "格";
        return Text.translatable("krt.gui.rail_power_connector.status", connectionStatus, powerInfo, coverageInfo);
    }
    
    /**
     * 获取是否已连接到电网
     */
    public boolean isConnectedToPowerGrid() {
        return connectedToPowerGrid;
    }
    
    /**
     * 获取电力覆盖半径
     */
    public int getCoverageRadius() {
        return coverageRadius;
    }
    
    /**
     * 获取电力类型
     */
    public PowerSupplySystem.PowerType getPowerType() {
        return powerType;
    }
    
    /**
     * 设置电力类型
     */
    public void setPowerType(PowerSupplySystem.PowerType type) {
        this.powerType = type;
        this.markDirty();
    }
    
    /**
     * 设置电力等级
     */
    public void setPowerLevel(int powerLevel) {
        this.powerLevel = powerLevel;
        this.markDirty();
    }
    
    /**
     * 获取电力等级
     */
    public int getPowerLevel() {
        return powerLevel;
    }
    
    /**
     * 检查周围是否有电力传输线或发电机
     */
    private boolean checkNearbyPowerSources(World world) {
        // 检查相邻的方块是否有电源
        for (int x = -1; x <= 1; x++) {
            for (int y = -1; y <= 1; y++) {
                for (int z = -1; z <= 1; z++) {
                    if (x == 0 && y == 0 && z == 0) continue; // 跳过自身
                    
                    BlockPos neighborPos = pos.add(x, y, z);
                    BlockEntity neighborEntity = world.getBlockEntity(neighborPos);
                    
                    if (neighborEntity instanceof PowerGeneratorBlockEntity || 
                        neighborEntity instanceof RailPowerConnectorBlockEntity) {
                        // 找到电源或其他连接器
                        return true;
                    }
                    
                    // 检查是否为电力传输线
                    if (world.getBlockState(neighborPos).getBlock() instanceof PowerTransmissionLineBlock) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
    
    /**
     * 服务器端tick更新
     */
    public static void tick(World world, BlockPos pos, BlockState state, RailPowerConnectorBlockEntity entity) {
        if (world.isClient) {
            return;
        }
        
        // 检查连接状态
        boolean wasConnected = entity.connectedToPowerGrid;
        entity.connectedToPowerGrid = entity.checkNearbyPowerSources(world);
        
        // 更新电力等级
        if (entity.connectedToPowerGrid) {
            entity.powerLevel = 100; // 完全电力
            
            // 向供电系统注册此连接器
            PowerSupplySystem powerSystem = PowerSupplySystemManager.getForWorld(world);
            if (powerSystem != null) {
                powerSystem.addPowerSource(pos, entity.powerType, entity.coverageRadius * 50);
            }
        } else {
            entity.powerLevel = 0;
        }
        
        // 如果连接状态改变，通知客户端
        if (wasConnected != entity.connectedToPowerGrid) {
            world.updateListeners(pos, state, state, 3);
        }
    }
}