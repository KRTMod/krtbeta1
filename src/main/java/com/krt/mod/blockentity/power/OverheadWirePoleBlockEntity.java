package com.krt.mod.blockentity.power;

import com.krt.mod.block.power.OverheadWireBlock;
import com.krt.mod.system.PowerSupplySystem;
import com.krt.mod.system.PowerSupplySystemManager;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.List;

/**
 * 接触网支柱方块实体 - 管理接触网张力和连接
 */
public class OverheadWirePoleBlockEntity extends BlockEntity {
    private int tensionLevel = 50; // 张力等级(0-100)
    private int powerLevel = 0;    // 电力等级
    private boolean powered = false; // 是否通电
    private List<BlockPos> connectedWires = new ArrayList<>(); // 连接的接触网
    private int maintenanceDays = 0; // 维护天数
    
    public OverheadWirePoleBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.OVERHEAD_WIRE_POLE, pos, state);
    }
    
    @Override
    public void readNbt(NbtCompound nbt) {
        super.readNbt(nbt);
        tensionLevel = nbt.getInt("TensionLevel");
        powerLevel = nbt.getInt("PowerLevel");
        powered = nbt.getBoolean("Powered");
        maintenanceDays = nbt.getInt("MaintenanceDays");
        
        // 读取连接的接触网位置
        int count = nbt.getInt("WireCount");
        connectedWires.clear();
        for (int i = 0; i < count; i++) {
            int x = nbt.getInt("WireX" + i);
            int y = nbt.getInt("WireY" + i);
            int z = nbt.getInt("WireZ" + i);
            connectedWires.add(new BlockPos(x, y, z));
        }
    }
    
    @Override
    public void writeNbt(NbtCompound nbt) {
        super.writeNbt(nbt);
        nbt.putInt("TensionLevel", tensionLevel);
        nbt.putInt("PowerLevel", powerLevel);
        nbt.putBoolean("Powered", powered);
        nbt.putInt("MaintenanceDays", maintenanceDays);
        
        // 保存连接的接触网位置
        nbt.putInt("WireCount", connectedWires.size());
        for (int i = 0; i < connectedWires.size(); i++) {
            BlockPos wirePos = connectedWires.get(i);
            nbt.putInt("WireX" + i, wirePos.getX());
            nbt.putInt("WireY" + i, wirePos.getY());
            nbt.putInt("WireZ" + i, wirePos.getZ());
        }
    }
    
    /**
     * 获取状态文本
     */
    public Text getStatusText() {
        String powerStatus = powered ? "已通电" : "未通电";
        String tensionStatus = getTensionStatus();
        return Text.translatable("krt.gui.overhead_wire_pole.status", 
                powerStatus, tensionLevel + "%", tensionStatus, maintenanceDays);
    }
    
    /**
     * 获取张力状态描述
     */
    private String getTensionStatus() {
        if (tensionLevel < 30) return "过松";
        if (tensionLevel > 70) return "过紧";
        return "正常";
    }
    
    /**
     * 调整张力
     */
    public void adjustTension(int amount) {
        tensionLevel = Math.max(0, Math.min(100, tensionLevel + amount));
        markDirty();
        updateBlockState();
    }
    
    /**
     * 设置张力为特定值
     */
    public void setTension(int level) {
        tensionLevel = Math.max(0, Math.min(100, level));
        markDirty();
        updateBlockState();
    }
    
    /**
     * 添加连接的接触网
     */
    public void addConnectedWire(BlockPos wirePos) {
        if (!connectedWires.contains(wirePos)) {
            connectedWires.add(wirePos);
            markDirty();
        }
    }
    
    /**
     * 移除连接的接触网
     */
    public void removeConnectedWire(BlockPos wirePos) {
        connectedWires.remove(wirePos);
        markDirty();
    }
    
    /**
     * 移除所有连接的接触网
     */
    public void removeConnectedWires() {
        if (world != null && !world.isClient) {
            for (BlockPos wirePos : connectedWires) {
                BlockEntity entity = world.getBlockEntity(wirePos);
                if (entity instanceof OverheadWireBlockEntity) {
                    world.breakBlock(wirePos, true);
                }
            }
        }
        connectedWires.clear();
        markDirty();
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
     * 更新方块状态
     */
    private void updateBlockState() {
        if (world != null && world.getBlockState(pos).getBlock() instanceof BlockWithEntity) {
            BlockState state = world.getBlockState(pos);
            world.setBlockState(pos, state.with(net.minecraft.state.property.Properties.POWERED, powered));
        }
    }
    
    /**
     * 执行维护
     */
    public void performMaintenance() {
        maintenanceDays = 0;
        tensionLevel = 50; // 重置为正常张力
        markDirty();
        updateBlockState();
    }
    
    /**
     * 获取当前张力
     */
    public int getTensionLevel() {
        return tensionLevel;
    }
    
    /**
     * 获取电力等级
     */
    public int getPowerLevel() {
        return powerLevel;
    }
    
    /**
     * 是否通电
     */
    public boolean isPowered() {
        return powered;
    }
    
    /**
     * 服务器端tick更新
     */
    public static void tick(World world, BlockPos pos, BlockState state, OverheadWirePoleBlockEntity entity) {
        if (world.isClient) {
            return;
        }
        
        // 每24000tick(一天)更新维护天数
        if (world.getTime() % 24000 == 0) {
            entity.maintenanceDays++;
            
            // 维护天数过多会影响张力
            if (entity.maintenanceDays > 7) {
                entity.tensionLevel = Math.max(0, entity.tensionLevel - 5);
            }
            entity.markDirty();
        }
        
        // 检查电力连接
        boolean wasPowered = entity.powered;
        entity.checkPowerConnection();
        
        if (wasPowered != entity.powered) {
            entity.updateBlockState();
        }
    }
    
    /**
     * 检查电力连接
     */
    private void checkPowerConnection() {
        // 检查相邻的电力连接器或接触网
        for (BlockPos wirePos : connectedWires) {
            BlockEntity entity = world.getBlockEntity(wirePos);
            if (entity instanceof OverheadWireBlockEntity wireEntity) {
                if (wireEntity.isPowered()) {
                    setPowered(true);
                    return;
                }
            }
        }
        
        // 检查相邻的电力连接器
        for (int x = -1; x <= 1; x++) {
            for (int y = -1; y <= 3; y++) { // 向上检查更多
                for (int z = -1; z <= 1; z++) {
                    if (x == 0 && y == 0 && z == 0) continue;
                    
                    BlockPos neighborPos = pos.add(x, y, z);
                    BlockEntity neighborEntity = world.getBlockEntity(neighborPos);
                    
                    if (neighborEntity instanceof RailPowerConnectorBlockEntity connector) {
                        if (connector.isConnectedToPowerGrid() && 
                            connector.getPowerType() == PowerSupplySystem.PowerType.OVERHEAD_WIRE) {
                            setPowered(true);
                            return;
                        }
                    }
                }
            }
        }
        
        setPowered(false);
    }
}