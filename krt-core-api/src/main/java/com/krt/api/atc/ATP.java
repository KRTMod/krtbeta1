package com.krt.api.atc;

import com.krt.api.data.Line;
import com.krt.api.data.Train;
import com.krt.api.data.Signal;
import com.krt.api.data.Station;
import java.util.List;
import java.util.Map;

/**
 * 列车自动防护系统(ATP)
 * 负责实时监测列车位置与速度，防止超速、追尾等风险，支持自动停车功能
 */
public class ATP {
    // 紧急制动触发距离（米）
    private static final int EMERGENCY_BRAKE_DISTANCE = 100;
    // 预警距离（米）
    private static final int WARNING_DISTANCE = 200;
    // 最大允许超速百分比
    private static final double MAX_SPEED_OVERRUN_PERCENTAGE = 0.1;
    
    /**
     * 检查列车是否需要触发紧急制动
     * @param train 列车对象
     * @param signalMap 信号机映射表
     * @param line 线路对象
     * @param otherTrains 其他列车列表
     * @return 是否需要紧急制动
     */
    public boolean shouldApplyEmergencyBrake(Train train, Map<String, Signal> signalMap, Line line, List<Train> otherTrains) {
        // 检查信号机状态
        if (checkSignalViolation(train, signalMap)) {
            return true;
        }
        
        // 检查与前方列车的距离
        if (checkTrainDistance(train, otherTrains)) {
            return true;
        }
        
        // 检查是否超速
        if (checkOverSpeed(train, line)) {
            return true;
        }
        
        // 检查线路安全状态
        if (!line.isValid()) {
            return true;
        }
        
        return false;
    }
    
    /**
     * 检查列车是否违反信号机指令
     * @param train 列车对象
     * @param signalMap 信号机映射表
     * @return 是否违反信号
     */
    private boolean checkSignalViolation(Train train, Map<String, Signal> signalMap) {
        // 查找列车前方最近的红色信号机
        for (Signal signal : signalMap.values()) {
            if (signal.getState() == Signal.State.RED) {
                double distance = calculateDistance(train, signal);
                if (distance <= EMERGENCY_BRAKE_DISTANCE) {
                    return true;
                }
            }
        }
        return false;
    }
    
    /**
     * 检查与前方列车的距离是否安全
     * @param train 列车对象
     * @param otherTrains 其他列车列表
     * @return 距离是否不安全
     */
    private boolean checkTrainDistance(Train train, List<Train> otherTrains) {
        // 查找同线路上的前方列车
        for (Train otherTrain : otherTrains) {
            if (otherTrain.getLineId().equals(train.getLineId()) && 
                otherTrain.getCurrentStationOrder() > train.getCurrentStationOrder()) {
                
                double distance = calculateDistance(train, otherTrain);
                if (distance <= EMERGENCY_BRAKE_DISTANCE) {
                    return true;
                }
            }
        }
        return false;
    }
    
    /**
     * 检查列车是否超速
     * @param train 列车对象
     * @param line 线路对象
     * @return 是否超速
     */
    private boolean checkOverSpeed(Train train, Line line) {
        int currentSpeed = train.getCurrentSpeed();
        int maxSpeed = line.getMaxSpeed();
        
        // 允许一定比例的超速，但超过这个比例就触发紧急制动
        return currentSpeed > maxSpeed * (1 + MAX_SPEED_OVERRUN_PERCENTAGE);
    }
    
    /**
     * 计算列车与信号机之间的距离
     * @param train 列车对象
     * @param signal 信号机对象
     * @return 距离（米）
     */
    private double calculateDistance(Train train, Signal signal) {
        // 简化实现，实际应用中需要根据轨道位置点精确计算
        // 这里假设train和signal都有x, y, z坐标属性
        int tx = 0, ty = 0, tz = 0;
        int sx = signal.getX(), sy = signal.getY(), sz = signal.getZ();
        
        return Math.sqrt(Math.pow(tx - sx, 2) + Math.pow(ty - sy, 2) + Math.pow(tz - sz, 2));
    }
    
    /**
     * 计算两列列车之间的距离
     * @param train1 第一列列车
     * @param train2 第二列列车
     * @return 距离（米）
     */
    private double calculateDistance(Train train1, Train train2) {
        // 简化实现，实际应用中需要根据轨道位置点精确计算
        // 这里根据车站顺序和站间距估算距离
        int stationDiff = train2.getCurrentStationOrder() - train1.getCurrentStationOrder();
        // 假设平均站间距为1000米
        return stationDiff * 1000;
    }
    
    /**
     * 获取当前允许的最大速度
     * @param train 列车对象
     * @param signalMap 信号机映射表
     * @param line 线路对象
     * @param otherTrains 其他列车列表
     * @return 允许的最大速度
     */
    public int getPermittedSpeed(Train train, Map<String, Signal> signalMap, Line line, List<Train> otherTrains) {
        // 基础最大速度为线路最大速度
        int maxAllowedSpeed = line.getMaxSpeed();
        
        // 根据前方信号机状态调整速度
        for (Signal signal : signalMap.values()) {
            double distance = calculateDistance(train, signal);
            if (signal.getState() == Signal.State.YELLOW && distance <= WARNING_DISTANCE) {
                maxAllowedSpeed = Math.min(maxAllowedSpeed, line.getMaxSpeed() / 2);
            }
            if (signal.getState() == Signal.State.RED && distance <= EMERGENCY_BRAKE_DISTANCE) {
                maxAllowedSpeed = 0;
            }
        }
        
        // 根据与前方列车的距离调整速度
        for (Train otherTrain : otherTrains) {
            if (otherTrain.getLineId().equals(train.getLineId()) && 
                otherTrain.getCurrentStationOrder() > train.getCurrentStationOrder()) {
                
                double distance = calculateDistance(train, otherTrain);
                if (distance <= WARNING_DISTANCE * 2) {
                    maxAllowedSpeed = Math.min(maxAllowedSpeed, line.getMaxSpeed() / 3);
                }
                if (distance <= WARNING_DISTANCE) {
                    maxAllowedSpeed = Math.min(maxAllowedSpeed, line.getMaxSpeed() / 5);
                }
            }
        }
        
        // 确保速度不为负数
        return Math.max(0, maxAllowedSpeed);
    }
    
    /**
     * 获取列车的安全停车距离
     * @param speed 当前速度
     * @return 安全停车距离
     */
    public double getSafeStoppingDistance(int speed) {
        // 简化计算，实际应用中需要考虑列车重量、轨道摩擦系数等因素
        // 假设停车距离与速度的平方成正比
        return (speed * speed) / 20;
    }
    
    /**
     * 检查列车是否可以启动
     * @param train 列车对象
     * @param signalMap 信号机映射表
     * @return 是否可以启动
     */
    public boolean canStartTrain(Train train, Map<String, Signal> signalMap) {
        // 检查前方信号机是否允许启动
        for (Signal signal : signalMap.values()) {
            if (signal.getState() == Signal.State.GREEN) {
                double distance = calculateDistance(train, signal);
                if (distance <= WARNING_DISTANCE) {
                    return true;
                }
            }
        }
        return false;
    }
}