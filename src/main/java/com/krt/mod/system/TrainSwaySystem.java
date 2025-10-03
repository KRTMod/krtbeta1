package com.krt.mod.system;

import com.krt.mod.entity.TrainEntity;
import com.krt.mod.entity.TrainCar;
import net.minecraft.util.math.Vec3d;

/**
 * 列车摇摆效果系统
 * 实现列车在行驶过程中的摇摆、倾斜效果，增强真实感和沉浸感
 */
public class TrainSwaySystem {
    private final TrainEntity train;
    private double swayAmplitude = 0.05; // 摇摆幅度
    private double swayFrequency = 0.2; // 摇摆频率
    private double currentSwayPhase = 0; // 当前摇摆相位
    private double lateralSwayFactor = 0.0; // 横向摇摆因子
    private double verticalBounceFactor = 0.0; // 垂直弹跳因子
    private double lastSpeed = 0.0; // 上次速度，用于计算加速度
    private long lastUpdateTime = System.currentTimeMillis();

    public TrainSwaySystem(TrainEntity train) {
        this.train = train;
    }

    /**
     * 更新列车摇摆效果
     */
    public void update() {
        long currentTime = System.currentTimeMillis();
        double deltaTime = (currentTime - lastUpdateTime) / 1000.0;
        lastUpdateTime = currentTime;

        // 获取当前列车速度（转换为m/s）
        double currentSpeed = train.getCurrentSpeed() / 3.6; // km/h -> m/s
        double acceleration = (currentSpeed - lastSpeed) / deltaTime;
        lastSpeed = currentSpeed;

        // 根据速度和加速度更新摇摆参数
        updateSwayParameters(currentSpeed, acceleration);

        // 更新摇摆相位
        updateSwayPhase(deltaTime, currentSpeed);

        // 应用摇摆效果到每个车厢
        applySwayToTrain();
    }

    /**
     * 根据速度和加速度更新摇摆参数
     */
    private void updateSwayParameters(double speed, double acceleration) {
        // 速度越快，摇摆幅度越大，但有上限
        double speedFactor = Math.min(speed / 30.0, 1.0); // 30m/s是约108km/h
        swayAmplitude = 0.02 + 0.08 * speedFactor; // 基础摇摆幅度0.02，最大0.1

        // 速度越快，摇摆频率越高
        swayFrequency = 0.1 + 0.4 * speedFactor; // 基础频率0.1，最大0.5

        // 横向摇摆因子受加速度影响
        lateralSwayFactor = Math.max(-0.1, Math.min(0.1, acceleration * 0.05));

        // 垂直弹跳因子（简单模拟轨道不平）
        verticalBounceFactor = calculateVerticalBounce(speed);
    }

    /**
     * 更新摇摆相位
     */
    private void updateSwayPhase(double deltaTime, double speed) {
        // 相位更新与速度成正比
        currentSwayPhase += swayFrequency * deltaTime * (0.5 + speed / 60.0);
        if (currentSwayPhase > Math.PI * 2) {
            currentSwayPhase -= Math.PI * 2;
        }
    }

    /**
     * 计算垂直弹跳效果
     */
    private double calculateVerticalBounce(double speed) {
        // 简单的噪声算法模拟轨道不平
        double bounce = 0.0;
        double time = System.currentTimeMillis() / 1000.0;
        
        // 多种频率的噪声叠加
        bounce += Math.sin(time * 2.0) * 0.01;
        bounce += Math.sin(time * 4.3) * 0.005;
        bounce += Math.sin(time * 1.5) * 0.008;
        
        // 速度越快，弹跳效果越明显
        return bounce * (0.5 + speed / 60.0);
    }

    /**
     * 应用摇摆效果到整列火车
     */
    private void applySwayToTrain() {
        if (train.getConsist() == null) {
            return;
        }

        // 对每个车厢应用摇摆效果
        int carIndex = 0;
        for (TrainCar car : train.getConsist().getCars()) {
            // 计算每个车厢的摇摆偏移量
            double carSwayPhase = currentSwayPhase + (carIndex * 0.2); // 不同车厢有相位差
            
            // 水平摇摆（左右）
            double horizontalSway = Math.sin(carSwayPhase) * swayAmplitude + lateralSwayFactor;
            
            // 垂直摇摆（上下）
            double verticalSway = Math.cos(carSwayPhase * 0.8) * (swayAmplitude * 0.5) + verticalBounceFactor;
            
            // 倾斜角度（与速度和转弯相关）
            float tiltAngle = (float)(Math.sin(carSwayPhase) * 2.0 * Math.min(lastSpeed / 30.0, 1.0));
            
            // 设置车厢摇摆数据（实际的渲染效果需要在客户端实现）
            car.setSwayOffset(new Vec3d(horizontalSway, verticalSway, 0));
            car.setTiltAngle(tiltAngle);
            
            carIndex++;
        }
    }

    /**
     * 设置摇摆幅度
     */
    public void setSwayAmplitude(double amplitude) {
        this.swayAmplitude = Math.max(0.0, Math.min(0.5, amplitude));
    }

    /**
     * 设置摇摆频率
     */
    public void setSwayFrequency(double frequency) {
        this.swayFrequency = Math.max(0.01, Math.min(2.0, frequency));
    }
}