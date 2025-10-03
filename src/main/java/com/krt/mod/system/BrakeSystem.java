package com.krt.mod.system;

import com.krt.mod.entity.TrainCar;
import net.minecraft.text.Text;

/**
 * 制动系统类，负责列车的制动功能
 */
public class BrakeSystem {
    // 制动类型
    public enum BrakeType {
        SERVICE_BRAKE, // 常用制动
        EMERGENCY_BRAKE, // 紧急制动
        HOLDING_BRAKE, // 停放制动
        REGENERATIVE_BRAKE // 再生制动
    }
    
    // 制动系统状态
    public enum BrakeStatus {
        NORMAL,
        WARNING,
        ERROR,
        APPLIED,
        RELEASED
    }
    
    private final TrainCar car;
    private BrakeStatus status = BrakeStatus.NORMAL;
    private double serviceBrakeLevel = 0.0; // 常用制动级别（0.0-1.0）
    private boolean emergencyBrakeApplied = false;
    private boolean holdingBrakeApplied = false;
    private double regenerativeBrakeLevel = 0.0; // 再生制动级别（0.0-1.0）
    private int health = 100;
    private int brakePadHealth = 100; // 刹车片健康值
    private double maxDeceleration = 1.0; // 最大减速度（m/s²）
    private boolean isAntilockEnabled = true; // 防抱死功能
    private boolean isBrakeForceDistributionEnabled = true; // 制动力分配功能
    
    /**
     * 创建制动系统
     */
    public BrakeSystem(TrainCar car) {
        this.car = car;
    }
    
    /**
     * 应用常用制动
     */
    public void applyServiceBrake(double level) {
        if (status == BrakeStatus.ERROR) {
            return;
        }
        
        // 限制制动级别范围
        this.serviceBrakeLevel = Math.max(0.0, Math.min(1.0, level));
        
        // 计算制动力
        double brakeForce = calculateBrakeForce(serviceBrakeLevel, BrakeType.SERVICE_BRAKE);
        
        // 应用制动力
        applyBrakeForce(brakeForce);
        
        status = BrakeStatus.APPLIED;
    }
    
    /**
     * 应用紧急制动
     */
    public void applyEmergencyBrake() {
        if (status == BrakeStatus.ERROR) {
            return;
        }
        
        this.emergencyBrakeApplied = true;
        
        // 计算紧急制动力（比常用制动更大）
        double brakeForce = calculateBrakeForce(1.0, BrakeType.EMERGENCY_BRAKE);
        
        // 应用制动力
        applyBrakeForce(brakeForce);
        
        status = BrakeStatus.APPLIED;
    }
    
    /**
     * 释放紧急制动
     */
    public void releaseEmergencyBrake() {
        this.emergencyBrakeApplied = false;
        
        // 如果没有应用其他制动，释放制动状态
        if (serviceBrakeLevel == 0.0 && regenerativeBrakeLevel == 0.0 && !holdingBrakeApplied) {
            status = BrakeStatus.RELEASED;
        }
    }
    
    /**
     * 应用停放制动
     */
    public void applyHoldingBrake() {
        if (status == BrakeStatus.ERROR) {
            return;
        }
        
        this.holdingBrakeApplied = true;
        
        // 停放制动通常在列车静止时使用
        if (car.getSpeed() < 0.1) {
            // 应用停放制动力
            applyBrakeForce(calculateBrakeForce(1.0, BrakeType.HOLDING_BRAKE));
            status = BrakeStatus.APPLIED;
        }
    }
    
    /**
     * 释放停放制动
     */
    public void releaseHoldingBrake() {
        this.holdingBrakeApplied = false;
        
        // 如果没有应用其他制动，释放制动状态
        if (serviceBrakeLevel == 0.0 && regenerativeBrakeLevel == 0.0 && !emergencyBrakeApplied) {
            status = BrakeStatus.RELEASED;
        }
    }
    
    /**
     * 应用再生制动
     */
    public void applyRegenerativeBrake(double level, TractionSystem tractionSystem) {
        if (status == BrakeStatus.ERROR || !car.hasTractionSystem()) {
            return;
        }
        
        // 限制再生制动级别范围
        this.regenerativeBrakeLevel = Math.max(0.0, Math.min(1.0, level));
        
        // 计算再生制动力
        double brakeForce = calculateBrakeForce(regenerativeBrakeLevel, BrakeType.REGENERATIVE_BRAKE);
        
        // 通过牵引系统进行能量回收
        if (tractionSystem != null && tractionSystem.isPowerRegenerationEnabled()) {
            double recoveredEnergy = tractionSystem.regenerateEnergy(brakeForce);
            // 可以在这里添加能量回收的处理逻辑
        }
        
        // 应用制动力
        applyBrakeForce(brakeForce);
        
        status = BrakeStatus.APPLIED;
    }
    
    /**
     * 释放所有制动
     */
    public void releaseAllBrakes() {
        this.serviceBrakeLevel = 0.0;
        this.emergencyBrakeApplied = false;
        this.holdingBrakeApplied = false;
        this.regenerativeBrakeLevel = 0.0;
        
        status = BrakeStatus.RELEASED;
    }
    
    /**
     * 计算制动力
     */
    private double calculateBrakeForce(double level, BrakeType type) {
        double baseForce = 0;
        
        switch (type) {
            case SERVICE_BRAKE -> baseForce = 8000 * level; // 常用制动基础力
            case EMERGENCY_BRAKE -> baseForce = 12000; // 紧急制动最大力
            case HOLDING_BRAKE -> baseForce = 5000; // 停放制动力
            case REGENERATIVE_BRAKE -> baseForce = 6000 * level; // 再生制动基础力
        }
        
        // 考虑健康值和刹车片状态
        double healthFactor = health / 100.0;
        double brakePadFactor = brakePadHealth / 100.0;
        
        // 根据列车速度调整制动力
        double speedFactor = 1.0;
        if (type == BrakeType.REGENERATIVE_BRAKE && car.getSpeed() < 5.0) {
            // 速度过低时，再生制动效率降低
            speedFactor = car.getSpeed() / 5.0;
        }
        
        return baseForce * healthFactor * brakePadFactor * speedFactor;
    }
    
    /**
     * 应用制动力
     */
    private void applyBrakeForce(double force) {
        // 计算减速度
        double deceleration = force / car.getWeight();
        
        // 限制最大减速度
        deceleration = Math.min(deceleration, maxDeceleration);
        
        // 应用防抱死系统
        if (isAntilockEnabled && car.getSpeed() > 0) {
            deceleration = applyAntilock(deceleration);
        }
        
        // 应用制动力分配
        if (isBrakeForceDistributionEnabled) {
            deceleration = applyBrakeForceDistribution(deceleration);
        }
        
        // 更新列车速度
        double newSpeed = Math.max(0, car.getSpeed() - deceleration * 0.1); // 简化计算
        car.setSpeed(newSpeed);
        
        // 更新刹车片磨损
        updateBrakePadWear(force);
    }
    
    /**
     * 应用防抱死系统
     */
    private double applyAntilock(double deceleration) {
        // 简化的防抱死逻辑
        if (deceleration > maxDeceleration * 0.8) {
            // 如果减速度过大，降低制动力防止抱死
            return maxDeceleration * 0.8;
        }
        return deceleration;
    }
    
    /**
     * 应用制动力分配
     */
    private double applyBrakeForceDistribution(double deceleration) {
        // 简化的制动力分配逻辑
        // 根据车辆负载调整前后轮制动力分配
        double loadFactor = car.getCurrentLoadFactor();
        
        // 负载越大，制动力分配越向前轮倾斜
        return deceleration * (0.4 + loadFactor * 0.2);
    }
    
    /**
     * 更新刹车片磨损
     */
    private void updateBrakePadWear(double force) {
        // 根据制动力和使用频率更新刹车片磨损
        if (force > 0) {
            double wear = force * 0.0001;
            brakePadHealth = Math.max(0, brakePadHealth - (int)wear);
        }
    }
    
    /**
     * 更新系统
     */
    public void update() {
        // 检查系统状态
        checkSystemStatus();
        
        // 更新健康值
        updateHealth();
        
        // 检查刹车片状态
        checkBrakePadStatus();
        
        // 如果列车静止，自动应用停放制动
        if (car.getSpeed() < 0.1 && !holdingBrakeApplied && !emergencyBrakeApplied) {
            // 可以在这里添加自动停放制动的逻辑
        }
    }
    
    /**
     * 检查系统状态
     */
    private void checkSystemStatus() {
        if (emergencyBrakeApplied) {
            status = BrakeStatus.APPLIED;
        } else if (serviceBrakeLevel > 0 || regenerativeBrakeLevel > 0 || holdingBrakeApplied) {
            status = BrakeStatus.APPLIED;
        } else {
            status = BrakeStatus.RELEASED;
        }
        
        // 根据健康值和刹车片状态更新状态
        if (health <= 30 || brakePadHealth <= 20) {
            status = BrakeStatus.ERROR;
        } else if (health <= 60 || brakePadHealth <= 50) {
            if (status != BrakeStatus.APPLIED) {
                status = BrakeStatus.WARNING;
            }
        }
    }
    
    /**
     * 更新健康值
     */
    private void updateHealth() {
        // 模拟系统老化
        if (car.getOperatingHours() % 1000 == 0) {
            health = Math.max(0, health - 2);
        }
        
        // 刹车片状态影响系统健康
        if (brakePadHealth <= 30) {
            health = Math.max(0, health - 1);
        }
    }
    
    /**
     * 检查刹车片状态
     */
    private void checkBrakePadStatus() {
        // 刹车片健康过低时，降低制动效率
        if (brakePadHealth <= 20) {
            maxDeceleration = 0.7;
        } else if (brakePadHealth <= 50) {
            maxDeceleration = 0.9;
        } else {
            maxDeceleration = 1.0;
        }
    }
    
    /**
     * 维修制动系统
     */
    public void repair() {
        health = 100;
        brakePadHealth = 100;
        maxDeceleration = 1.0;
        status = BrakeStatus.NORMAL;
    }
    
    /**
     * 更换刹车片
     */
    public void replaceBrakePads() {
        brakePadHealth = 100;
        maxDeceleration = 1.0;
    }
    
    /**
     * 获取制动系统信息
     */
    public Text getInfoText() {
        String statusText;
        switch (status) {
            case NORMAL -> statusText = "正常";
            case WARNING -> statusText = "警告";
            case ERROR -> statusText = "故障";
            case APPLIED -> statusText = "已施加";
            case RELEASED -> statusText = "已释放";
            default -> statusText = "未知";
        }
        
        return Text.literal(
            "制动系统状态: " + statusText + ", 健康: " + health + "%" +
            ", 刹车片健康: " + brakePadHealth + "%" +
            ", 常用制动: " + String.format("%.0f%%", serviceBrakeLevel * 100) +
            ", 再生制动: " + String.format("%.0f%%", regenerativeBrakeLevel * 100) +
            ", 紧急制动: " + (emergencyBrakeApplied ? "已施加" : "未施加") +
            ", 停放制动: " + (holdingBrakeApplied ? "已施加" : "未施加") +
            ", 最大减速度: " + maxDeceleration + "m/s²"
        );
    }
    
    // Getters and setters
    public BrakeStatus getStatus() {
        return status;
    }
    
    public int getHealth() {
        return health;
    }
    
    public int getBrakePadHealth() {
        return brakePadHealth;
    }
    
    public boolean isEmergencyBrakeApplied() {
        return emergencyBrakeApplied;
    }
    
    public boolean isHoldingBrakeApplied() {
        return holdingBrakeApplied;
    }
    
    public double getServiceBrakeLevel() {
        return serviceBrakeLevel;
    }
    
    public boolean isAntilockEnabled() {
        return isAntilockEnabled;
    }
    
    public void setAntilockEnabled(boolean antilockEnabled) {
        isAntilockEnabled = antilockEnabled;
    }
    
    public boolean isBrakeForceDistributionEnabled() {
        return isBrakeForceDistributionEnabled;
    }
    
    public void setBrakeForceDistributionEnabled(boolean brakeForceDistributionEnabled) {
        isBrakeForceDistributionEnabled = brakeForceDistributionEnabled;
    }
    
    public TrainCar getCar() {
        return car;
    }
}