package com.krt.api.entity;

import com.krt.api.data.Train;
import com.krt.api.data.Line;
import com.krt.api.data.TrackPoint;
import com.krt.api.KRTKeyBindings;
import com.krt.api.audio.KAudioManager;
import com.krt.api.audio.KAudioManager.AudioType;
import com.krt.api.utils.KUtils;
import com.krt.api.power.KPowerSystem;
import com.krt.api.power.KPowerSystem.PowerType;
import java.util.List;
import java.util.ArrayList;

/**
 * KRT轨道交通模组列车实体类
 * 负责列车的物理运动、状态管理和控制系统
 */
public class KTrainEntity {
    // 列车基础数据
    private Train trainData;
    
    // 列车当前所在线路
    private Line currentLine;
    
    // 列车当前在轨道上的位置索引
    private int currentTrackPointIndex = 0;
    
    // 列车当前实际坐标
    private double x, y, z;
    
    // 列车当前速度（km/h）
    private double currentSpeed = 0.0;
    
    // 列车目标速度（km/h）
    private double targetSpeed = 0.0;
    
    // 列车加速度（km/h/s）
    private double acceleration = 0.5;
    
    // 列车减速度（km/h/s）
    private double deceleration = 1.0;
    
    // 列车方向（1: 前进, -1: 后退）
    private int direction = 1;
    
    // 列车档位（1-前进, 0-空挡, -1-后退）
    private int gear = 0;
    
    // 车门状态（true: 开启, false: 关闭）
    private boolean doorsOpen = false;
    
    // 列车是否静止
    private boolean isStationary = true;
    
    // 列车是否在车站
    private boolean isAtStation = false;
    
    // 列车所在车站ID
    private String currentStationId = null;
    
    // 列车健康值
    private int health;
    
    // 上次更新时间
    private long lastUpdateTime = System.currentTimeMillis();
    
    // 音频管理器引用
    private KAudioManager audioManager;
    
    // 电力系统引用
    private KPowerSystem powerSystem;
    
    /**
     * 构造函数
     */
    public KTrainEntity(Train trainData, Line line) {
        this.trainData = trainData;
        this.currentLine = line;
        this.health = trainData.getHealth();
        
        // 初始化位置为线路第一个轨道点
        if (line != null && !line.getTrackPoints().isEmpty()) {
            TrackPoint firstPoint = line.getTrackPoints().get(0);
            this.x = firstPoint.getX();
            this.y = firstPoint.getY();
            this.z = firstPoint.getZ();
        }
        
        // 获取音频管理器和电力系统实例
        this.audioManager = KAudioManager.getInstance();
        this.powerSystem = KPowerSystem.getInstance();
    }
    
    /**
     * 更新列车状态
     */
    public void update() {
        long currentTime = System.currentTimeMillis();
        double deltaTime = (currentTime - lastUpdateTime) / 1000.0; // 转换为秒
        lastUpdateTime = currentTime;
        
        // 检查电力供应
        if (!powerSystem.canTrainReceivePower(trainData.getId())) {
            // 没有电力，停车
            targetSpeed = 0;
            // 这里可以添加电力不足的警告音效
        }
        
        // 检查列车健康状态
        if (health < 50) {
            // 健康状态不佳，限制速度
            if (currentSpeed > 20) {
                targetSpeed = 20;
            }
        }
        
        // 更新速度
        updateSpeed(deltaTime);
        
        // 如果列车在移动，更新位置
        if (Math.abs(currentSpeed) > 0.1) {
            updatePosition(deltaTime);
            isStationary = false;
        } else {
            currentSpeed = 0;
            isStationary = true;
        }
        
        // 播放列车走行音
        if (!isStationary && Math.abs(currentSpeed) > 5) {
            audioManager.playTrainSound(trainData.getId(), AudioType.TRAIN_MOVEMENT, x, y, z);
        }
        
        // 检查是否到达车站
        checkStationArrival();
        
        // 更新列车数据
        updateTrainData();
    }
    
    /**
     * 更新列车速度
     */
    private void updateSpeed(double deltaTime) {
        // 根据档位和方向更新目标速度
        switch (gear) {
            case 1: // 前进档
                targetSpeed = Math.min(trainData.getMaxSpeed(), targetSpeed + acceleration * deltaTime * direction);
                break;
            case -1: // 后退档
                targetSpeed = Math.max(-trainData.getMaxSpeed() / 2, targetSpeed - acceleration * deltaTime * direction);
                break;
            case 0: // 空挡
                if (currentSpeed > 0) {
                    targetSpeed = Math.max(0, targetSpeed - deceleration * deltaTime);
                } else if (currentSpeed < 0) {
                    targetSpeed = Math.min(0, targetSpeed + deceleration * deltaTime);
                }
                break;
        }
        
        // 平滑过渡到目标速度
        double speedDifference = targetSpeed - currentSpeed;
        double maxChange = deceleration * deltaTime;
        
        if (Math.abs(speedDifference) > maxChange) {
            currentSpeed += Math.signum(speedDifference) * maxChange;
        } else {
            currentSpeed = targetSpeed;
        }
    }
    
    /**
     * 更新列车位置
     */
    private void updatePosition(double deltaTime) {
        if (currentLine == null || currentLine.getTrackPoints().isEmpty()) {
            return;
        }
        
        List<TrackPoint> trackPoints = currentLine.getTrackPoints();
        int pointCount = trackPoints.size();
        
        if (pointCount < 2) {
            return;
        }
        
        // 计算移动距离（米）
        double speedInMetersPerSecond = currentSpeed / 3.6;
        double distanceToMove = speedInMetersPerSecond * deltaTime;
        
        // 循环移动直到用完所有距离
        while (distanceToMove > 0) {
            // 获取当前和下一个轨道点
            TrackPoint currentPoint = trackPoints.get(currentTrackPointIndex);
            int nextPointIndex = (currentTrackPointIndex + direction + pointCount) % pointCount;
            TrackPoint nextPoint = trackPoints.get(nextPointIndex);
            
            // 计算当前点到下一个点的距离
            double segmentDistance = KUtils.calculateDistance(
                    currentPoint.getX(), currentPoint.getY(), currentPoint.getZ(),
                    nextPoint.getX(), nextPoint.getY(), nextPoint.getZ()
            );
            
            if (segmentDistance <= 0) {
                // 两个点重合，直接移动到下一个点
                currentTrackPointIndex = nextPointIndex;
                x = nextPoint.getX();
                y = nextPoint.getY();
                z = nextPoint.getZ();
                continue;
            }
            
            if (distanceToMove <= segmentDistance) {
                // 移动距离小于等于当前段距离，在当前段内移动
                double ratio = distanceToMove / segmentDistance;
                x = currentPoint.getX() + (nextPoint.getX() - currentPoint.getX()) * ratio;
                y = currentPoint.getY() + (nextPoint.getY() - currentPoint.getY()) * ratio;
                z = currentPoint.getZ() + (nextPoint.getZ() - currentPoint.getZ()) * ratio;
                distanceToMove = 0;
            } else {
                // 移动距离大于当前段距离，移动到下一个点并继续
                x = nextPoint.getX();
                y = nextPoint.getY();
                z = nextPoint.getZ();
                currentTrackPointIndex = nextPointIndex;
                distanceToMove -= segmentDistance;
            }
        }
    }
    
    /**
     * 检查是否到达车站
     */
    private void checkStationArrival() {
        // 这里简化实现，实际应该有更复杂的车站到达检测逻辑
        if (currentLine != null && isStationary && !isAtStation) {
            // 检查附近是否有车站
            // 这里应该调用API来获取附近的车站
        }
    }
    
    /**
     * 更新列车数据
     */
    private void updateTrainData() {
        trainData.setCurrentSpeed(currentSpeed);
        trainData.setHealth(health);
        trainData.setX(x);
        trainData.setY(y);
        trainData.setZ(z);
        trainData.setDirection(direction);
        trainData.setDoorsOpen(doorsOpen);
        trainData.setStationary(isStationary);
    }
    
    /**
     * 处理用户输入
     */
    public void handleInput() {
        // 这里应该从KRTKeyBindings获取按键状态并处理
        // 简化实现，实际应该调用KRTKeyBindings的方法
        
        // 例如：如果按下前进键
        if (KRTKeyBindings.isKeyPressed(KRTKeyBindings.KEY_FORWARD)) {
            gear = 1;
            direction = 1;
        }
        
        // 如果按下后退键
        if (KRTKeyBindings.isKeyPressed(KRTKeyBindings.KEY_BACKWARD)) {
            gear = -1;
            direction = -1;
        }
        
        // 如果按下切换档位键
        if (KRTKeyBindings.isKeyPressed(KRTKeyBindings.KEY_CHANGE_GEAR)) {
            changeGear();
        }
        
        // 如果按下开门键
        if (KRTKeyBindings.isKeyPressed(KRTKeyBindings.KEY_OPEN_DOORS)) {
            openDoors();
        }
        
        // 如果按下关门键
        if (KRTKeyBindings.isKeyPressed(KRTKeyBindings.KEY_CLOSE_DOORS)) {
            closeDoors();
        }
    }
    
    /**
     * 切换档位
     */
    public void changeGear() {
        // 循环切换档位：空挡 -> 前进 -> 后退 -> 空挡
        switch (gear) {
            case 0:
                gear = 1;
                break;
            case 1:
                gear = -1;
                break;
            case -1:
                gear = 0;
                break;
        }
    }
    
    /**
     * 开启车门
     */
    public void openDoors() {
        if (isStationary && !doorsOpen) {
            doorsOpen = true;
            // 播放车门开启音效
            audioManager.playAudio("door_open_default", 1.0, x, y, z);
        }
    }
    
    /**
     * 关闭车门
     */
    public void closeDoors() {
        if (doorsOpen) {
            doorsOpen = false;
            // 播放车门关闭音效
            audioManager.playAudio("door_close_default", 1.0, x, y, z);
        }
    }
    
    /**
     * 鸣笛
     */
    public void horn() {
        audioManager.playTrainSound(trainData.getId(), AudioType.TRAIN_HORN, x, y, z);
    }
    
    /**
     * 按铃
     */
    public void bell() {
        audioManager.playTrainSound(trainData.getId(), AudioType.TRAIN_BELL, x, y, z);
    }
    
    /**
     * 启动列车
     */
    public boolean start() {
        if (!doorsOpen) {
            gear = 1;
            direction = 1;
            return true;
        }
        return false;
    }
    
    /**
     * 停止列车
     */
    public void stop() {
        targetSpeed = 0;
        gear = 0;
    }
    
    /**
     * 加速
     */
    public void speedUp() {
        if (gear == 1) {
            targetSpeed = Math.min(trainData.getMaxSpeed(), targetSpeed + 5);
        } else if (gear == -1) {
            targetSpeed = Math.max(-trainData.getMaxSpeed() / 2, targetSpeed - 5);
        }
    }
    
    /**
     * 减速
     */
    public void slowDown() {
        if (currentSpeed > 0) {
            targetSpeed = Math.max(0, targetSpeed - 5);
        } else if (currentSpeed < 0) {
            targetSpeed = Math.min(0, targetSpeed + 5);
        }
    }
    
    /**
     * 列车自检
     */
    public List<String> performSelfCheck() {
        List<String> issues = new ArrayList<>();
        
        // 检查健康状态
        if (health < 50) {
            issues.add("列车健康状态不佳，需要维护。");
        }
        
        // 检查车门状态
        if (doorsOpen) {
            issues.add("车门未关闭。");
        }
        
        // 检查电力供应
        if (!powerSystem.canTrainReceivePower(trainData.getId())) {
            issues.add("电力供应不足。");
        }
        
        // 检查所在线路
        if (currentLine == null) {
            issues.add("列车不在任何线路上。");
        }
        
        return issues;
    }
    
    /**
     * 应用损伤
     */
    public void applyDamage(int amount) {
        health = Math.max(0, health - amount);
        
        // 如果健康值过低，自动停车
        if (health < 20 && !isStationary) {
            stop();
        }
    }
    
    /**
     * 修复列车
     */
    public void repair(int amount) {
        health = Math.min(100, health + amount);
    }
    
    /**
     * 设置目标速度
     */
    public void setTargetSpeed(double speed) {
        this.targetSpeed = KUtils.clamp(speed, -trainData.getMaxSpeed() / 2, trainData.getMaxSpeed());
    }
    
    /**
     * 获取列车数据
     */
    public Train getTrainData() {
        return trainData;
    }
    
    /**
     * 设置列车数据
     */
    public void setTrainData(Train trainData) {
        this.trainData = trainData;
    }
    
    /**
     * 获取当前线路
     */
    public Line getCurrentLine() {
        return currentLine;
    }
    
    /**
     * 设置当前线路
     */
    public void setCurrentLine(Line line) {
        this.currentLine = line;
        this.currentTrackPointIndex = 0;
        
        // 重置位置到线路起点
        if (line != null && !line.getTrackPoints().isEmpty()) {
            TrackPoint firstPoint = line.getTrackPoints().get(0);
            this.x = firstPoint.getX();
            this.y = firstPoint.getY();
            this.z = firstPoint.getZ();
        }
    }
    
    /**
     * 获取当前坐标
     */
    public double getX() { return x; }
    public double getY() { return y; }
    public double getZ() { return z; }
    
    /**
     * 设置坐标
     */
    public void setPosition(double x, double y, double z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }
    
    /**
     * 获取当前速度
     */
    public double getCurrentSpeed() { return currentSpeed; }
    
    /**
     * 获取档位
     */
    public int getGear() { return gear; }
    
    /**
     * 设置档位
     */
    public void setGear(int gear) {
        this.gear = gear;
    }
    
    /**
     * 获取方向
     */
    public int getDirection() { return direction; }
    
    /**
     * 设置方向
     */
    public void setDirection(int direction) {
        this.direction = direction;
    }
    
    /**
     * 检查车门是否开启
     */
    public boolean areDoorsOpen() { return doorsOpen; }
    
    /**
     * 检查列车是否静止
     */
    public boolean isStationary() { return isStationary; }
    
    /**
     * 检查列车是否在车站
     */
    public boolean isAtStation() { return isAtStation; }
    
    /**
     * 设置列车是否在车站
     */
    public void setAtStation(boolean atStation, String stationId) {
        this.isAtStation = atStation;
        this.currentStationId = stationId;
    }
    
    /**
     * 获取当前车站ID
     */
    public String getCurrentStationId() { return currentStationId; }
    
    /**
     * 获取健康值
     */
    public int getHealth() { return health; }
    
    /**
     * 设置健康值
     */
    public void setHealth(int health) {
        this.health = KUtils.clamp(health, 0, 100);
    }
}