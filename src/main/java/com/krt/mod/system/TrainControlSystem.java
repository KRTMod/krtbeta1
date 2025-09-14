package com.krt.mod.system;

import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import com.krt.mod.KRTMod;
import com.krt.mod.block.SignalBlock;
import com.krt.mod.block.ATPSignalBlock;
import com.krt.mod.entity.TrainEntity;

import java.util.List;

public class TrainControlSystem {
    private final TrainEntity train;
    private final World world;
    private boolean isAccelerating = false;
    private boolean isBraking = false;
    private double targetSpeed = 0.0;
    private double maxSpeed = 80.0; // 默认最大速度80km/h
    private TrainControlMode controlMode = TrainControlMode.ATO;

    public TrainControlSystem(TrainEntity train) {
        this.train = train;
        this.world = train.world;
    }

    // 运行ATO系统（自动驾驶）
    public void runATO() {
        if (controlMode != TrainControlMode.ATO) return;

        // 获取ATP数据
        ATPSignalBlock.ATPData atpData = getATPSignalData();
        
        // 检查ATP数据，如果需要紧急制动，立即触发
        if (atpData.emergencyStop) {
            train.applyEmergencyBrake();
            return;
        }

        // 根据ATP数据设置最大速度
        this.maxSpeed = atpData.maxSpeed;

        // 检测前方的车站和信号机
        detectObstacles();

        // 根据当前状态调整速度
        adjustSpeed();
    }

    // 获取ATP信号数据
    private ATPSignalBlock.ATPData getATPSignalData() {
        // 查找最近的ATP信号块
        BlockPos trainPos = train.getBlockPos();
        for (BlockPos pos : BlockPos.iterateOutwards(trainPos, 20, 5, 20)) {
            if (world.getBlockState(pos).getBlock() instanceof ATPSignalBlock) {
                return ATPSignalBlock.getATPData(world, pos);
            }
        }
        // 如果没有找到ATP信号块，返回默认数据
        return new ATPSignalBlock.ATPData();
    }

    // 检测前方障碍物
    private void detectObstacles() {
        // 这里可以实现检测前方车站、信号机、其他列车等障碍物的逻辑
        // 简化版：只检测信号机状态
        Vec3d forwardPos = train.getPos().add(train.getRotationVector().multiply(20));
        BlockPos checkPos = new BlockPos(forwardPos);

        for (BlockPos pos : BlockPos.iterateOutwards(checkPos, 200, 5, 200)) {
            if (world.getBlockState(pos).getBlock() instanceof SignalBlock) {
                SignalBlock.SignalState signalState = world.getBlockState(pos).get(SignalBlock.SIGNAL_STATE);
                double distance = train.getPos().squaredDistanceTo(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
                
                if (signalState == SignalBlock.SignalState.RED) {
                    // 前方有红灯，减速停车
                    double brakingDistance = calculateBrakingDistance(train.getCurrentSpeed());
                    if (Math.sqrt(distance) < brakingDistance * 1.5) {
                        setTargetSpeed(0.0);
                        applyBrakes();
                        return;
                    }
                } else if (signalState == SignalBlock.SignalState.YELLOW) {
                    // 前方有黄灯，减速
                    double brakingDistance = calculateBrakingDistance(train.getCurrentSpeed());
                    if (Math.sqrt(distance) < brakingDistance * 2) {
                        setTargetSpeed(maxSpeed * 0.5);
                        return;
                    }
                }
            }
        }

        // 如果前方没有障碍物，加速到最大速度
        setTargetSpeed(maxSpeed);
    }

    // 计算制动距离
    private double calculateBrakingDistance(double speed) {
        // 简化的制动距离计算公式：距离 = 速度^2 / (2 * 减速度)
        double deceleration = 2.0; // 减速度
        return (speed * speed) / (2 * deceleration);
    }

    // 调整速度
    private void adjustSpeed() {
        double currentSpeed = train.getCurrentSpeed();
        double speedDifference = targetSpeed - currentSpeed;

        if (Math.abs(speedDifference) < 0.5) {
            // 速度已经接近目标速度，保持当前速度
            isAccelerating = false;
            isBraking = false;
        } else if (speedDifference > 0) {
            // 需要加速
            isAccelerating = true;
            isBraking = false;
            applyAcceleration();
        } else {
            // 需要减速
            isAccelerating = false;
            isBraking = true;
            applyBrakes();
        }
    }

    // 应用加速
    private void applyAcceleration() {
        double acceleration = 0.1;
        Vec3d currentVelocity = train.getVelocity();
        Vec3d forwardVector = train.getRotationVector().normalize();
        Vec3d newVelocity = forwardVector.multiply(train.getCurrentSpeed() + acceleration);
        train.setVelocity(newVelocity);
    }

    // 应用制动
    private void applyBrakes() {
        double deceleration = 0.2;
        double currentSpeed = train.getCurrentSpeed();
        if (currentSpeed > 0) {
            Vec3d forwardVector = train.getRotationVector().normalize();
            double newSpeed = Math.max(0, currentSpeed - deceleration);
            Vec3d newVelocity = forwardVector.multiply(newSpeed);
            train.setVelocity(newVelocity);
        }
    }

    // 减速（用于信号系统提示减速时）
    public void slowDown() {
        if (controlMode == TrainControlMode.ATO) {
            setTargetSpeed(maxSpeed * 0.7);
        }
    }

    // 设置目标速度
    public void setTargetSpeed(double speed) {
        this.targetSpeed = Math.min(speed, maxSpeed);
    }

    // 设置控制模式
    public void setControlMode(TrainControlMode mode) {
        this.controlMode = mode;
        if (train.getDriver() != null) {
            train.getDriver().sendMessage(Text.literal("控制模式已切换至: " + mode.getDisplayName()), false);
        }
    }

    // 获取控制模式
    public TrainControlMode getControlMode() {
        return controlMode;
    }

    // 获取最大速度
    public double getMaxSpeed() {
        return maxSpeed;
    }

    // 设置最大速度
    public void setMaxSpeed(double maxSpeed) {
        this.maxSpeed = maxSpeed;
        if (this.targetSpeed > maxSpeed) {
            this.targetSpeed = maxSpeed;
        }
    }

    // 控制模式枚举
    public enum TrainControlMode {
        MANUAL("手动控制"),
        ATO("自动驾驶");

        private final String displayName;

        TrainControlMode(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }
}