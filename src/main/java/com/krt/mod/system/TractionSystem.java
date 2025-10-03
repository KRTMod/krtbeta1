package com.krt.mod.system;

import com.krt.mod.entity.TrainCar;
import net.minecraft.text.Text;

/**
 * 牵引系统类，列车的动力源，通过逆变器将直流电转换为交流电驱动牵引电机
 */
public class TractionSystem {
    // 牵引系统状态
    public enum TractionStatus {
        NORMAL,
        WARNING,
        ERROR,
        INACTIVE
    }
    
    private final TrainCar car;
    private TractionStatus status = TractionStatus.NORMAL;
    private int motorCount = 4; // 默认每辆车4个牵引电机
    private int[] motorHealth; // 每个电机的健康值
    private double powerInput; // 输入功率（直流电）
    private double powerOutput; // 输出功率（交流电）
    private double efficiency = 0.95; // 逆变器效率
    private int operatingTemperature = 40; // 当前温度
    private int maxTemperature = 120; // 最高温度
    private boolean powerRegenerationEnabled = true; // 能量回收功能
    private double health = 100.0;
    
    /**
     * 创建牵引系统
     */
    public TractionSystem(TrainCar car) {
        this.car = car;
        this.motorHealth = new int[motorCount];
        for (int i = 0; i < motorCount; i++) {
            motorHealth[i] = 100;
        }
    }
    
    /**
     * 应用电力到牵引系统
     */
    public void applyPower(double power, PowerSupplySystem powerSystem) {
        if (status == TractionStatus.ERROR || status == TractionStatus.INACTIVE) {
            return;
        }
        
        // 获取列车的供电类型
        PowerSupplySystem.PowerType powerType = car.getPowerType();
        
        // 从供电系统获取直流电，考虑不同供电类型的效率
        int dcPower;
        if (powerType != null) {
            // 使用带供电类型参数的方法，自动考虑效率差异
            dcPower = powerSystem.provideTractionPower(car.getConsist(), (int)power, powerType);
        } else {
            // 兼容旧版本，默认使用接触网供电
            dcPower = powerSystem.provideTractionPower(car.getConsist(), (int)power);
        }
        
        this.powerInput = dcPower;
        
        // 通过逆变器转换为交流电
        this.powerOutput = invertDcToAc(dcPower);
        
        // 更新温度
        updateTemperature();
        
        // 计算实际速度增量
        double speedIncrement = calculateSpeedIncrement(powerOutput);
        car.setSpeed(car.getSpeed() + speedIncrement);
    }
    
    /**
     * 将直流电转换为交流电
     */
    private double invertDcToAc(double dcPower) {
        // 考虑效率、温度等因素的逆变器转换
        double tempFactor = 1.0 - Math.max(0, (operatingTemperature - 80) / 100.0);
        return dcPower * efficiency * tempFactor;
    }
    
    /**
     * 计算速度增量
     */
    private double calculateSpeedIncrement(double acPower) {
        // 根据功率和车辆重量计算速度增量
        double weightFactor = 1.0 / (car.getWeight() / 1000.0); // 重量越轻，速度增量越大
        double healthFactor = health / 100.0;
        
        // 简化的速度增量计算
        return (acPower * 0.01) * weightFactor * healthFactor;
    }
    
    /**
     * 进行能量回收（制动时）
     */
    public double regenerateEnergy(double brakePower) {
        if (!powerRegenerationEnabled || status == TractionStatus.ERROR || status == TractionStatus.INACTIVE) {
            return 0;
        }
        
        // 能量回收率（制动能量的30-50%可以被回收）
        double recoveryRate = 0.4;
        return brakePower * recoveryRate;
    }
    
    /**
     * 更新系统
     */
    public void update() {
        // 检查电机状态
        checkMotorStatus();
        
        // 检查温度
        checkTemperature();
        
        // 更新健康值
        updateHealth();
        
        // 更新系统状态
        updateStatus();
    }
    
    /**
     * 检查电机状态
     */
    private void checkMotorStatus() {
        int avgMotorHealth = 0;
        int errorMotors = 0;
        int warningMotors = 0;
        
        for (int i = 0; i < motorCount; i++) {
            avgMotorHealth += motorHealth[i];
            
            // 模拟电机老化
            if (car.getOperatingHours() % 500 == 0) {
                motorHealth[i] = Math.max(0, motorHealth[i] - 1);
            }
            
            if (motorHealth[i] <= 30) {
                errorMotors++;
            } else if (motorHealth[i] <= 60) {
                warningMotors++;
            }
        }
        
        avgMotorHealth /= motorCount;
        
        // 根据电机状态影响系统健康
        if (errorMotors > 0) {
            health = Math.min(health, avgMotorHealth);
        } else if (warningMotors > 0) {
            health = Math.min(health, avgMotorHealth + 10);
        }
    }
    
    /**
     * 检查温度
     */
    private void checkTemperature() {
        // 如果系统正在工作，温度会上升
        if (powerOutput > 0) {
            operatingTemperature += (powerOutput / 100);
        } else {
            // 否则温度会下降
            operatingTemperature = Math.max(40, operatingTemperature - 1);
        }
        
        // 如果温度过高，降低效率和健康
        if (operatingTemperature > maxTemperature * 0.8) {
            efficiency = 0.95 - (operatingTemperature - maxTemperature * 0.8) / 1000.0;
        } else {
            efficiency = 0.95;
        }
        
        if (operatingTemperature > maxTemperature) {
            health = Math.max(0, health - 5);
        }
    }
    
    /**
     * 更新温度
     */
    private void updateTemperature() {
        // 根据功率更新温度
        operatingTemperature += (powerOutput / 200);
        // 确保温度不超过最大值
        operatingTemperature = Math.min(operatingTemperature, maxTemperature);
    }
    
    /**
     * 更新健康值
     */
    private void updateHealth() {
        // 根据各种因素更新健康值
        if (status == TractionStatus.ERROR) {
            health = Math.max(0, health - 1);
        } else if (status == TractionStatus.WARNING) {
            health = Math.max(0, health - 0.5);
        }
        
        // 温度对健康的影响
        if (operatingTemperature > maxTemperature * 0.9) {
            health = Math.max(0, health - 2);
        }
    }
    
    /**
     * 更新系统状态
     */
    private void updateStatus() {
        if (health <= 30) {
            status = TractionStatus.ERROR;
        } else if (health <= 60 || operatingTemperature > maxTemperature * 0.8) {
            status = TractionStatus.WARNING;
        } else if (car.getStatus() == TrainCar.CarStatus.MAINTENANCE) {
            status = TractionStatus.INACTIVE;
        } else {
            status = TractionStatus.NORMAL;
        }
    }
    
    /**
     * 维修牵引系统
     */
    public void repair() {
        health = 100;
        operatingTemperature = 40;
        efficiency = 0.95;
        
        // 维修所有电机
        for (int i = 0; i < motorCount; i++) {
            motorHealth[i] = 100;
        }
        
        status = TractionStatus.NORMAL;
    }
    
    /**
     * 获取牵引系统信息
     */
    public Text getInfoText() {
        String statusText;
        switch (status) {
            case NORMAL -> statusText = "正常";
            case WARNING -> statusText = "警告";
            case ERROR -> statusText = "故障";
            case INACTIVE -> statusText = "停用";
            default -> statusText = "未知";
        }
        
        int avgMotorHealth = 0;
        for (int health : motorHealth) {
            avgMotorHealth += health;
        }
        avgMotorHealth /= motorCount;
        
        return Text.literal(
            "牵引系统状态: " + statusText + ", 健康: " + health + "%" +
            ", 电机健康: " + avgMotorHealth + "%" +
            ", 温度: " + operatingTemperature + "°C" +
            ", 效率: " + String.format("%.1f%%", efficiency * 100) +
            ", 输入功率: " + (int)powerInput + "W" +
            ", 输出功率: " + (int)powerOutput + "W"
        );
    }
    
    // Getters and setters
    public TractionStatus getStatus() {
        return status;
    }
    
    public double getHealth() {
        return health;
    }
    
    public int getMotorCount() {
        return motorCount;
    }
    
    public void setMotorCount(int motorCount) {
        this.motorCount = motorCount;
        this.motorHealth = new int[motorCount];
        for (int i = 0; i < motorCount; i++) {
            motorHealth[i] = 100;
        }
    }
    
    public int getOperatingTemperature() {
        return operatingTemperature;
    }
    
    public boolean isPowerRegenerationEnabled() {
        return powerRegenerationEnabled;
    }
    
    public void setPowerRegenerationEnabled(boolean powerRegenerationEnabled) {
        this.powerRegenerationEnabled = powerRegenerationEnabled;
    }
    
    public double getEfficiency() {
        return efficiency;
    }
    
    public TrainCar getCar() {
        return car;
    }
}