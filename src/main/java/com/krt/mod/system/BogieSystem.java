package com.krt.mod.system;

import com.krt.mod.entity.TrainCar;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;

/**
 * 转向架系统类，负责支撑车体并传递牵引力
 */
public class BogieSystem {
    // 转向架类型
    public enum BogieType {
        POWERED, // 动力转向架
        UNPOWERED // 非动力转向架
    }
    
    // 转向架状态
    public enum BogieStatus {
        NORMAL,
        WARNING,
        ERROR,
        MAINTENANCE
    }
    
    private final TrainCar car;
    private final BogieType type;
    private BogieStatus status = BogieStatus.NORMAL;
    private int health = 100;
    private double wheelHealth = 100; // 轮对健康值
    private double suspensionHealth = 100; // 悬挂系统健康值
    private double lateralAcceleration = 0; // 横向加速度
    private double verticalAcceleration = 0; // 垂向加速度
    private double yawRate = 0; // 偏航率
    private double pitchRate = 0; // 俯仰率
    private double rollRate = 0; // 侧滚率
    private double wheelFlangeWear = 0; // 轮缘磨损
    private double wheelTreadWear = 0; // 踏面磨损
    private boolean isPowered;
    
    /**
     * 创建转向架系统
     */
    public BogieSystem(TrainCar car, BogieType type) {
        this.car = car;
        this.type = type;
        this.isPowered = type == BogieType.POWERED;
    }
    
    /**
     * 传递牵引力
     */
    public void transmitTractionForce(double force, TractionSystem tractionSystem) {
        if (!isPowered || status == BogieStatus.ERROR || status == BogieStatus.MAINTENANCE) {
            return;
        }
        
        // 考虑转向架效率和健康状态
        double efficiencyFactor = calculateEfficiencyFactor();
        double transmittedForce = force * efficiencyFactor;
        
        // 应用牵引力到车辆
        applyTractionForce(transmittedForce);
        
        // 更新轮对磨损
        updateWheelWear(transmittedForce);
    }
    
    /**
     * 传递制动力
     */
    public void transmitBrakeForce(double force, BrakeSystem brakeSystem) {
        if (status == BogieStatus.ERROR || status == BogieStatus.MAINTENANCE) {
            return;
        }
        
        // 考虑转向架效率和健康状态
        double efficiencyFactor = calculateEfficiencyFactor();
        double transmittedForce = force * efficiencyFactor;
        
        // 应用制动力到车辆
        applyBrakeForce(transmittedForce);
        
        // 更新轮对磨损
        updateWheelWear(transmittedForce);
    }
    
    /**
     * 计算转向架效率因子
     */
    private double calculateEfficiencyFactor() {
        double wheelFactor = wheelHealth / 100.0;
        double suspensionFactor = suspensionHealth / 100.0;
        double bogieFactor = health / 100.0;
        
        // 综合效率因子
        return wheelFactor * suspensionFactor * bogieFactor;
    }
    
    /**
     * 应用牵引力
     */
    private void applyTractionForce(double force) {
        // 计算加速度增量
        double acceleration = force / car.getWeight();
        
        // 考虑轨道条件
        double trackConditionFactor = car.getTrackConditionFactor();
        acceleration *= trackConditionFactor;
        
        // 考虑坡度影响
        double grade = car.getCurrentTrackGrade();
        acceleration -= grade * 9.8; // 减去重力沿轨道方向的分量
        
        // 考虑空气阻力
        double airResistanceForce = 0.5 * 1.225 * Math.pow(car.getSpeed(), 2) * car.getFrontalArea() * car.getDragCoefficient();
        double airResistanceAcceleration = airResistanceForce / car.getWeight();
        acceleration -= airResistanceAcceleration;
        
        // 考虑滚动阻力
        double rollingResistanceForce = 0.01 * car.getWeight() * 9.8; // 滚动阻力系数约为0.01
        double rollingResistanceAcceleration = rollingResistanceForce / car.getWeight();
        acceleration -= rollingResistanceAcceleration;
        
        // 计算时间步长（假设每10毫秒更新一次）
        double deltaTime = 0.01; // 10毫秒
        
        // 更新车辆速度
        double newSpeed = car.getSpeed() + acceleration * deltaTime;
        car.setSpeed(newSpeed);
        
        // 更新动力学参数
        updateDynamicsParameters();
    }
    
    /**
     * 应用制动力
     */
    private void applyBrakeForce(double force) {
        // 计算减速度增量
        double deceleration = force / car.getWeight();
        
        // 考虑轨道条件
        double trackConditionFactor = car.getTrackConditionFactor();
        deceleration *= trackConditionFactor;
        
        // 考虑坡度影响
        double grade = car.getCurrentTrackGrade();
        deceleration += grade * 9.8; // 加上重力沿轨道方向的分量
        
        // 考虑空气阻力（在制动过程中仍存在）
        double airResistanceForce = 0.5 * 1.225 * Math.pow(car.getSpeed(), 2) * car.getFrontalArea() * car.getDragCoefficient();
        double airResistanceDeceleration = airResistanceForce / car.getWeight();
        deceleration += airResistanceDeceleration;
        
        // 考虑滚动阻力（在制动过程中仍存在）
        double rollingResistanceForce = 0.01 * car.getWeight() * 9.8; // 滚动阻力系数约为0.01
        double rollingResistanceDeceleration = rollingResistanceForce / car.getWeight();
        deceleration += rollingResistanceDeceleration;
        
        // 计算时间步长（假设每10毫秒更新一次）
        double deltaTime = 0.01; // 10毫秒
        
        // 更新车辆速度
        double newSpeed = Math.max(0, car.getSpeed() - deceleration * deltaTime);
        car.setSpeed(newSpeed);
        
        // 更新动力学参数
        updateDynamicsParameters();
    }
    
    /**
     * 更新轮对磨损
     */
    private void updateWheelWear(double force) {
        // 根据力的大小更新轮对磨损
        double wearAmount = force * 0.00005;
        wheelTreadWear += wearAmount;
        
        // 高速行驶时增加轮缘磨损
        if (car.getSpeed() > 30.0) {
            wheelFlangeWear += wearAmount * 0.5;
        }
        
        // 限制磨损范围
        wheelTreadWear = Math.min(wheelTreadWear, 100.0);
        wheelFlangeWear = Math.min(wheelFlangeWear, 100.0);
        
        // 更新轮对健康值
        updateWheelHealth();
    }
    
    /**
     * 更新轮对健康值
     */
    private void updateWheelHealth() {
        // 轮对健康值受踏面磨损和轮缘磨损影响
        double maxWear = Math.max(wheelTreadWear, wheelFlangeWear);
        this.wheelHealth = 100.0 - maxWear;
        
        // 限制健康值范围
        wheelHealth = Math.max(0.0, wheelHealth);
    }
    
    /**
     * 更新动力学参数
     */
    private void updateDynamicsParameters() {
        double speed = car.getSpeed();
        
        // 1. 计算横向加速度
        // 横向加速度受曲线半径、速度和轨道超高影响
        double curveRadius = car.getCurrentTrackCurveRadius();
        if (curveRadius > 0) {
            // 横向加速度 = v² / r
            this.lateralAcceleration = (speed * speed) / curveRadius;
            
            // 考虑轨道超高的影响（超高可以部分抵消横向加速度）
            double superelevation = car.getCurrentTrackSuperelevation();
            double superelevationEffect = 9.8 * Math.tan(Math.toRadians(superelevation));
            this.lateralAcceleration -= superelevationEffect;
        } else {
            this.lateralAcceleration = 0;
        }
        
        // 2. 计算垂向加速度
        // 垂向加速度受悬挂系统、轨道不平顺和车辆振动影响
        this.verticalAcceleration = calculateVerticalAcceleration(speed);
        
        // 3. 计算偏航率
        // 偏航率表示车辆绕垂直轴旋转的速率，与曲线半径和速度相关
        if (curveRadius > 0) {
            this.yawRate = speed / curveRadius; // rad/s
        } else {
            this.yawRate = 0;
        }
        
        // 4. 计算俯仰率
        // 俯仰率表示车辆绕横向轴旋转的速率，与坡度变化率相关
        double grade = car.getCurrentTrackGrade();
        double gradeChangeRate = calculateGradeChangeRate(); // 坡度变化率
        this.pitchRate = gradeChangeRate * speed / 100.0;
        
        // 5. 计算侧滚率
        // 侧滚率表示车辆绕纵向轴旋转的速率，与横向加速度和悬挂特性相关
        this.rollRate = Math.abs(this.lateralAcceleration) * 0.5; // 简化关系，实际应基于悬挂特性
    }
    
    /**
     * 计算垂向加速度
     */
    private double calculateVerticalAcceleration(double speed) {
        // 这里应该基于悬挂系统、轨道不平顺和车辆振动模型计算
        // 目前返回简化的值，实际应用中需要实现
        double trackRoughness = 0.01; // 轨道不平顺度
        double suspensionDamping = 0.2; // 悬挂系统阻尼系数
        return trackRoughness * speed * suspensionDamping;
    }
    
    /**
     * 计算坡度变化率
     */
    private double calculateGradeChangeRate() {
        // 这里应该计算轨道坡度的变化率
        // 目前返回简化的值，实际应用中需要实现
        return 0.01;
    }
    
    /**
     * 检查轨道状态
     */
    public void checkTrackCondition() {
        // 获取轨道条件因子
        double trackCondition = car.getTrackConditionFactor();
        
        // 轨道条件差时，增加磨损和降低健康
        if (trackCondition < 0.7) {
            // 增加悬挂系统磨损
            suspensionHealth = Math.max(0.0, suspensionHealth - 0.5);
            
            // 增加轮对磨损
            wheelTreadWear = Math.min(100.0, wheelTreadWear + 0.1);
            updateWheelHealth();
        }
    }
    
    /**
     * 更新系统
     */
    public void update() {
        // 检查轨道状态
        checkTrackCondition();
        
        // 更新健康值
        updateHealth();
        
        // 更新悬挂系统状态
        updateSuspensionStatus();
        
        // 更新转向架状态
        updateBogieStatus();
    }
    
    /**
     * 更新健康值
     */
    private void updateHealth() {
        // 转向架健康值受轮对和悬挂系统健康影响
        double avgComponentHealth = (wheelHealth + suspensionHealth) / 2.0;
        this.health = (int)(avgComponentHealth * 0.9 + 10); // 基础健康值10
        
        // 限制健康值范围
        health = Math.max(0, Math.min(100, health));
    }
    
    /**
     * 更新悬挂系统状态
     */
    private void updateSuspensionStatus() {
        // 模拟悬挂系统老化
        if (car.getOperatingHours() % 1500 == 0) {
            suspensionHealth = Math.max(0.0, suspensionHealth - 1.0);
        }
        
        // 高速行驶时，悬挂系统磨损增加
        if (car.getSpeed() > 60.0) {
            suspensionHealth = Math.max(0.0, suspensionHealth - 0.05);
        }
    }
    
    /**
     * 更新转向架状态
     */
    private void updateBogieStatus() {
        if (health <= 30) {
            status = BogieStatus.ERROR;
        } else if (health <= 60) {
            status = BogieStatus.WARNING;
        } else if (car.getStatus() == TrainCar.CarStatus.MAINTENANCE) {
            status = BogieStatus.MAINTENANCE;
        } else {
            status = BogieStatus.NORMAL;
        }
    }
    
    /**
     * 维修转向架系统
     */
    public void repair() {
        health = 100;
        wheelHealth = 100;
        suspensionHealth = 100;
        wheelFlangeWear = 0;
        wheelTreadWear = 0;
        status = BogieStatus.NORMAL;
    }
    
    /**
     * 更换轮对
     */
    public void replaceWheels() {
        wheelHealth = 100;
        wheelFlangeWear = 0;
        wheelTreadWear = 0;
    }
    
    /**
     * 检修悬挂系统
     */
    public void serviceSuspension() {
        suspensionHealth = 100;
    }
    
    /**
     * 获取转向架系统信息
     */
    public Text getInfoText() {
        String statusText;
        switch (status) {
            case NORMAL -> statusText = "正常";
            case WARNING -> statusText = "警告";
            case ERROR -> statusText = "故障";
            case MAINTENANCE -> statusText = "维护中";
            default -> statusText = "未知";
        }
        
        String typeText = isPowered ? "动力转向架" : "非动力转向架";
        
        return Text.literal(
            "转向架状态: " + statusText + ", 类型: " + typeText +
            ", 健康: " + health + "%" +
            ", 轮对健康: " + (int)wheelHealth + "%" +
            ", 悬挂健康: " + (int)suspensionHealth + "%" +
            ", 踏面磨损: " + String.format("%.1f%%", wheelTreadWear) +
            ", 轮缘磨损: " + String.format("%.1f%%", wheelFlangeWear) +
            ", 横向加速度: " + String.format("%.2f m/s²", lateralAcceleration)
        );
    }
    
    // Getters and setters
    public BogieStatus getStatus() {
        return status;
    }
    
    public int getHealth() {
        return health;
    }
    
    public double getWheelHealth() {
        return wheelHealth;
    }
    
    public double getSuspensionHealth() {
        return suspensionHealth;
    }
    
    public boolean isPowered() {
        return isPowered;
    }
    
    public BogieType getType() {
        return type;
    }
    
    public TrainCar getCar() {
        return car;
    }
}