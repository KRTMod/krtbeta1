package com.krt.mod.block;

import com.krt.mod.system.LogSystem;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class ATPSignalBlockEntity extends BlockEntity {
    // 信号状态 (0=红灯, 1=黄灯, 2=绿灯)
    private int signalState = 0;
    // 最大允许速度
    private double maxSpeed = 0.0;
    // 紧急制动状态
    private boolean emergencyBrake = false;
    // 障碍物距离
    private double obstacleDistance = 0.0;
    // 前方信号机状态
    private int nextSignalState = 0;

    public ATPSignalBlockEntity(BlockPos pos, BlockState state) {
        super(KRTBlockEntities.ATP_SIGNAL, pos, state);
    }

    @Override
    protected void writeNbt(NbtCompound nbt) {
        super.writeNbt(nbt);
        nbt.putInt("SignalState", signalState);
        nbt.putDouble("MaxSpeed", maxSpeed);
        nbt.putBoolean("EmergencyBrake", emergencyBrake);
        nbt.putDouble("ObstacleDistance", obstacleDistance);
        nbt.putInt("NextSignalState", nextSignalState);
    }

    @Override
    public void readNbt(NbtCompound nbt) {
        super.readNbt(nbt);
        signalState = nbt.getInt("SignalState");
        maxSpeed = nbt.getDouble("MaxSpeed");
        emergencyBrake = nbt.getBoolean("EmergencyBrake");
        obstacleDistance = nbt.getDouble("ObstacleDistance");
        nextSignalState = nbt.getInt("NextSignalState");
    }

    /**
     * 更新信号状态
     */
    public void updateSignalState(int newState) {
        this.signalState = newState;
        
        // 根据信号状态更新最大允许速度
        updateMaxSpeed();
        
        // 标记为脏，触发数据同步
        markDirty();
        
        if (world != null && !world.isClient) {
            world.updateListeners(pos, getCachedState(), getCachedState(), 3);
        }
        
        LogSystem.systemLog("ATP信号机状态更新为: " + getSignalStatusString(newState));
    }

    /**
     * 根据信号状态更新最大允许速度
     */
    private void updateMaxSpeed() {
        switch (signalState) {
            case 0: // 红灯
                maxSpeed = 0.0;
                emergencyBrake = true;
                break;
            case 1: // 黄灯
                maxSpeed = 20.0;
                emergencyBrake = false;
                break;
            case 2: // 绿灯
                maxSpeed = 80.0;
                emergencyBrake = false;
                break;
        }
    }

    /**
     * 设置障碍物距离
     */
    public void setObstacleDistance(double distance) {
        this.obstacleDistance = distance;
        markDirty();
    }

    /**
     * 设置前方信号机状态
     */
    public void setNextSignalState(int state) {
        this.nextSignalState = state;
        markDirty();
    }

    /**
     * 获取信号状态
     */
    public int getSignalState() {
        return signalState;
    }

    /**
     * 获取最大允许速度
     */
    public double getMaxSpeed() {
        return maxSpeed;
    }

    /**
     * 获取紧急制动状态
     */
    public boolean isEmergencyBrake() {
        return emergencyBrake;
    }

    /**
     * 获取障碍物距离
     */
    public double getObstacleDistance() {
        return obstacleDistance;
    }

    /**
     * 获取前方信号机状态
     */
    public int getNextSignalState() {
        return nextSignalState;
    }

    /**
     * 获取ATP信号数据
     */
    public ATPData getATPData() {
        return new ATPData(maxSpeed, emergencyBrake, obstacleDistance, signalState);
    }

    /**
     * ATP信号数据类
     */
    public static class ATPData {
        public double maxSpeed = 80.0; // 默认最大速度80km/h
        public boolean emergencyStop = false; // 是否需要紧急制动
        public double distanceToObstacle = Double.MAX_VALUE; // 到前方障碍物的距离
        public boolean isSignalGreen = true; // 前方信号机是否为绿灯
        
        public ATPData() {
            // 默认构造函数
        }
        
        public ATPData(double maxSpeed, boolean emergencyStop, double distanceToObstacle, int signalState) {
            this.maxSpeed = maxSpeed;
            this.emergencyStop = emergencyStop;
            this.distanceToObstacle = distanceToObstacle;
            this.isSignalGreen = signalState == 2; // 绿灯为通行状态
        }
    }

    /**
     * 获取信号状态字符串
     */
    public static String getSignalStatusString(int state) {
        switch (state) {
            case 0: return "红灯 (停车)";
            case 1: return "黄灯 (减速)";
            case 2: return "绿灯 (通行)";
            default: return "未知状态";
        }
    }

    /**
     * 每刻更新逻辑
     */
    public static void tick(World world, BlockPos pos, BlockState state, ATPSignalBlockEntity blockEntity) {
        if (world.isClient) {
            return;
        }
        
        // 在实际实现中，这里应该根据列车位置、轨道状态等更新信号
        // 可以从调度系统获取相关信息
        
        // 定期清理脏标记
        if (world.getTime() % 20 == 0) { // 每1秒
            blockEntity.markDirty();
        }
    }
}