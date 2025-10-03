package com.krt.mod.blockentity.power;

import com.krt.mod.block.power.OverheadWireBlock;
import com.krt.mod.block.power.OverheadWirePoleBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

/**
 * 接触网方块实体 - 管理接触网电力传输和张力
 */
public class OverheadWireBlockEntity extends BlockEntity {
    private int powerLevel = 0;
    private boolean powered = false;
    private int tensionLevel = 50; // 张力等级(0-100)
    private int wearLevel = 0; // 磨损等级(0-100)
    
    public OverheadWireBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.OVERHEAD_WIRE, pos, state);
    }
    
    @Override
    public void readNbt(NbtCompound nbt) {
        super.readNbt(nbt);
        powerLevel = nbt.getInt("PowerLevel");
        powered = nbt.getBoolean("Powered");
        tensionLevel = nbt.getInt("TensionLevel");
        wearLevel = nbt.getInt("WearLevel");
    }
    
    @Override
    public void writeNbt(NbtCompound nbt) {
        super.writeNbt(nbt);
        nbt.putInt("PowerLevel", powerLevel);
        nbt.putBoolean("Powered", powered);
        nbt.putInt("TensionLevel", tensionLevel);
        nbt.putInt("WearLevel", wearLevel);
    }
    
    /**
     * 获取状态文本
     */
    public Text getStatusText() {
        String powerStatus = powered ? "已通电" : "未通电";
        String tensionStatus = getTensionStatus();
        String wearStatus = getWearStatus();
        
        return Text.translatable("krt.gui.overhead_wire.status", 
                powerStatus, tensionLevel + "%", tensionStatus, wearLevel + "%", wearStatus);
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
     * 获取磨损状态描述
     */
    private String getWearStatus() {
        if (wearLevel > 80) return "严重磨损";
        if (wearLevel > 50) return "中度磨损";
        if (wearLevel > 20) return "轻微磨损";
        return "良好";
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
     * 获取张力等级
     */
    public int getTensionLevel() {
        return tensionLevel;
    }
    
    /**
     * 设置张力等级
     */
    public void setTensionLevel(int level) {
        this.tensionLevel = Math.max(0, Math.min(100, level));
        markDirty();
    }
    
    /**
     * 更新方块状态
     */
    private void updateBlockState() {
        if (world != null && world.getBlockState(pos).getBlock() instanceof OverheadWireBlock) {
            BlockState state = world.getBlockState(pos);
            world.setBlockState(pos, state.with(net.minecraft.state.property.Properties.POWERED, powered));
        }
    }
    
    /**
     * 通知相连的支柱
     */
    public void notifyPoles() {
        if (world == null) return;
        
        // 检查连接的支柱
        Direction facing = world.getBlockState(pos).get(FACING);
        checkPole(pos.offset(facing));
        checkPole(pos.offset(facing.getOpposite()));
    }
    
    /**
     * 检查并通知支柱
     */
    private void checkPole(BlockPos polePos) {
        BlockEntity entity = world.getBlockEntity(polePos);
        if (entity instanceof OverheadWirePoleBlockEntity poleEntity) {
            poleEntity.removeConnectedWire(pos);
        }
    }
    
    /**
     * 更新张力（基于支柱）
     */
    public void updateTensionFromPoles() {
        if (world == null) return;
        
        int totalTension = 0;
        int poleCount = 0;
        
        // 检查连接的支柱
        Direction facing = world.getBlockState(pos).get(FACING);
        BlockEntity entity1 = world.getBlockEntity(pos.offset(facing));
        BlockEntity entity2 = world.getBlockEntity(pos.offset(facing.getOpposite()));
        
        if (entity1 instanceof OverheadWirePoleBlockEntity poleEntity1) {
            totalTension += poleEntity1.getTensionLevel();
            poleCount++;
        }
        if (entity2 instanceof OverheadWirePoleBlockEntity poleEntity2) {
            totalTension += poleEntity2.getTensionLevel();
            poleCount++;
        }
        
        // 计算平均张力
        if (poleCount > 0) {
            setTensionLevel(totalTension / poleCount);
        }
    }
    
    /**
     * 增加磨损
     */
    public void increaseWear(int amount) {
        wearLevel = Math.min(100, wearLevel + amount);
        markDirty();
    }
    
    /**
     * 修复磨损
     */
    public void repair() {
        wearLevel = 0;
        markDirty();
    }
    
    /**
     * 服务器端tick更新
     */
    public static void tick(World world, BlockPos pos, BlockState state, OverheadWireBlockEntity entity) {
        if (world.isClient) {
            return;
        }
        
        // 更新张力（基于支柱）
        entity.updateTensionFromPoles();
        
        // 检查电力传输
        boolean wasPowered = entity.powered;
        entity.checkPowerTransfer();
        
        // 更新磨损（基于张力和电力负载）
        entity.updateWear();
        
        if (wasPowered != entity.powered) {
            entity.updateBlockState();
        }
    }
    
    /**
     * 检查电力传输
     */
    private void checkPowerTransfer() {
        if (world == null) return;
        
        // 检查是否连接到通电的支柱或其他接触网
        Direction facing = world.getBlockState(pos).get(FACING);
        
        // 检查相邻的支柱
        BlockEntity entity1 = world.getBlockEntity(pos.offset(facing));
        BlockEntity entity2 = world.getBlockEntity(pos.offset(facing.getOpposite()));
        
        if (entity1 instanceof OverheadWirePoleBlockEntity poleEntity1 && poleEntity1.isPowered()) {
            setPowered(true);
            return;
        }
        if (entity2 instanceof OverheadWirePoleBlockEntity poleEntity2 && poleEntity2.isPowered()) {
            setPowered(true);
            return;
        }
        
        // 检查相邻的接触网
        if (entity1 instanceof OverheadWireBlockEntity wireEntity1 && wireEntity1.isPowered()) {
            setPowered(true);
            return;
        }
        if (entity2 instanceof OverheadWireBlockEntity wireEntity2 && wireEntity2.isPowered()) {
            setPowered(true);
            return;
        }
        
        setPowered(false);
    }
    
    /**
     * 更新磨损
     */
    private void updateWear() {
        // 每100tick增加一点磨损
        if (world.getTime() % 100 == 0) {
            int wearIncrease = 1;
            
            // 张力异常会加速磨损
            if (tensionLevel < 30 || tensionLevel > 70) {
                wearIncrease += 1;
            }
            
            // 通电状态下磨损增加
            if (powered) {
                wearIncrease += 1;
            }
            
            increaseWear(wearIncrease);
        }
    }
}