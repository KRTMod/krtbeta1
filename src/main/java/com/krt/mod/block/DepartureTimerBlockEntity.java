package com.krt.mod.block;

import com.krt.mod.system.TrainDepartureTimerLogic;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

/**
 * 端门计时器方块实体类
 * 负责处理地铁端门内侧的发车时间计时器的实际逻辑和显示内容
 */
public class DepartureTimerBlockEntity extends BlockEntity {
    private int tickCounter = 0;
    private boolean showDetails = false;
    private boolean powered = false;
    private String statusMessage = "未激活";
    private String formattedTime = "00:00";
    private boolean isGreen = true;
    
    public DepartureTimerBlockEntity(BlockPos pos, BlockState state) {
        super(KRTBlockEntities.DEPARTURE_TIMER, pos, state);
        // 初始化属性
        if (state.contains(DepartureTimerBlock.SHOW_DETAILS)) {
            this.showDetails = state.get(DepartureTimerBlock.SHOW_DETAILS);
        }
        if (state.contains(DepartureTimerBlock.POWERED)) {
            this.powered = state.get(DepartureTimerBlock.POWERED);
        }
    }
    
    @Override
    protected void writeNbt(NbtCompound nbt) {
        // 保存方块实体数据到NBT
        nbt.putInt("tickCounter", tickCounter);
        nbt.putBoolean("showDetails", showDetails);
        nbt.putBoolean("powered", powered);
        nbt.putString("statusMessage", statusMessage);
        nbt.putString("formattedTime", formattedTime);
        nbt.putBoolean("isGreen", isGreen);
        super.writeNbt(nbt);
    }
    
    @Override
    public void readNbt(NbtCompound nbt) {
        // 从NBT读取方块实体数据
        super.readNbt(nbt);
        if (nbt.contains("tickCounter")) {
            tickCounter = nbt.getInt("tickCounter");
        }
        if (nbt.contains("showDetails")) {
            showDetails = nbt.getBoolean("showDetails");
        }
        if (nbt.contains("powered")) {
            powered = nbt.getBoolean("powered");
        }
        if (nbt.contains("statusMessage")) {
            statusMessage = nbt.getString("statusMessage");
        }
        if (nbt.contains("formattedTime")) {
            formattedTime = nbt.getString("formattedTime");
        }
        if (nbt.contains("isGreen")) {
            isGreen = nbt.getBoolean("isGreen");
        }
    }
    
    /**
     * 方块实体的tick方法，每秒更新一次计时器显示
     */
    public void tick() {
        tickCounter++;
        if (tickCounter >= 20) { // 每秒更新一次
            tickCounter = 0;
            
            // 只有在通电状态下才更新计时器信息
            if (powered && world != null && !world.isClient()) {
                updateTimerInfo();
            }
        }
    }
    
    /**
     * 更新计时器信息
     */
    private void updateTimerInfo() {
        if (world == null || pos == null) {
            return;
        }
        
        try {
            // 从逻辑处理器获取计时器信息
            TrainDepartureTimerLogic timerLogic = TrainDepartureTimerLogic.getInstance(world);
            TrainDepartureTimerLogic.TimerInfo timerInfo = timerLogic.getTimerInfo(pos);
            
            // 更新显示数据
            formattedTime = timerInfo.getFormattedTime();
            statusMessage = timerInfo.getStatusMessage();
            isGreen = timerInfo.isGreen();
            
            // 标记方块实体数据已更改
            markDirty();
        } catch (Exception e) {
            // 处理可能的异常
            statusMessage = "系统错误";
            formattedTime = "--:--";
            isGreen = false;
        }
    }
    
    /**
     * 当电源状态改变时调用
     */
    public void onPowerStateChanged(boolean powered) {
        this.powered = powered;
        markDirty();
    }
    
    /**
     * 设置是否显示详细信息
     */
    public void setShowDetails(boolean showDetails) {
        this.showDetails = showDetails;
        markDirty();
    }
    
    /**
     * 获取显示文本
     */
    public Text getDisplayText() {
        // 如果未通电，显示关闭状态
        if (!powered) {
            return Text.of("关闭状态");
        }
        
        // 根据是否显示详细信息返回不同的文本
        if (showDetails) {
            // 详细模式显示时间和状态信息
            return Text.of(String.format("%s\n%s", formattedTime, statusMessage));
        } else {
            // 简洁模式只显示时间
            return Text.of(formattedTime);
        }
    }
    
    /**
     * 获取时间文本颜色
     */
    public int getTimeTextColor() {
        // 绿色表示倒计时（早于计划时间），红色表示正计时（晚于计划时间）
        return isGreen ? 0x00FF00 : 0xFF0000;
    }
    
    /**
     * 获取状态信息
     */
    public String getStatusMessage() {
        return statusMessage;
    }
    
    /**
     * 获取格式化的时间字符串
     */
    public String getFormattedTime() {
        return formattedTime;
    }
    
    /**
     * 检查计时器是否处于绿色状态（倒计时）
     */
    public boolean isGreen() {
        return isGreen;
    }
    
    /**
     * 检查是否显示详细信息
     */
    public boolean isShowDetails() {
        return showDetails;
    }
    
    /**
     * 检查方块是否通电
     */
    public boolean isPowered() {
        return powered;
    }
}