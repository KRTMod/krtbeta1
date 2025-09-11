package com.krt.api.atc;

import com.krt.api.data.Line;
import com.krt.api.data.Train;
import com.krt.api.data.Station;
import com.krt.api.data.Signal;
import com.krt.api.dispatch.KDispatcher;
import java.util.List;
import java.util.Map;

/**
 * 列车自动驾驶系统(ATO)
 * 自动完成列车启动、加速、减速等操作，优化运行效率
 */
public class ATO {
    // 进站减速距离（米）
    private static final int STATION_DECELERATION_DISTANCE = 500;
    // 站台定位精度（米）
    private static final double STATION_POSITIONING_PRECISION = 0.5;
    // 启动加速度（m/s²）
    private static final double START_ACCELERATION = 0.8;
    // 常用减速度（m/s²）
    private static final double NORMAL_DECELERATION = 1.0;
    // 紧急减速度（m/s²）
    private static final double EMERGENCY_DECELERATION = 1.2;
    // 巡航速度系数（相对于线路最大速度）
    private static final double CRUISE_SPEED_FACTOR = 0.9;
    
    private final ATP atpSystem;
    
    public ATO(ATP atp) {
        this.atpSystem = atp;
    }
    
    /**
     * 自动驾驶控制逻辑
     * @param train 列车对象
     * @param line 线路对象
     * @param signalMap 信号机映射表
     * @param otherTrains 其他列车列表
     * @param dispatcher 调度系统
     */
    public void controlTrain(Train train, Line line, Map<String, Signal> signalMap, List<Train> otherTrains, KDispatcher dispatcher) {
        if (!train.isOperational() || !line.isAtcEnabled()) {
            return;
        }
        
        // 获取ATP允许的最大速度
        int permittedSpeed = atpSystem.getPermittedSpeed(train, signalMap, line, otherTrains);
        
        // 检查是否需要紧急制动
        if (atpSystem.shouldApplyEmergencyBrake(train, signalMap, line, otherTrains)) {
            applyEmergencyBrake(train);
            return;
        }
        
        // 检查是否接近车站
        Station nextStation = getNextStation(train, line);
        if (nextStation != null) {
            double distanceToStation = calculateDistanceToStation(train, nextStation, line);
            
            // 接近车站时减速
            if (distanceToStation <= STATION_DECELERATION_DISTANCE) {
                handleApproachingStation(train, distanceToStation, permittedSpeed);
                return;
            }
        }
        
        // 正常运行时的速度控制
        handleNormalOperation(train, permittedSpeed, line);
    }
    
    /**
     * 获取列车前方的下一站
     * @param train 列车对象
     * @param line 线路对象
     * @return 下一站车站对象
     */
    private Station getNextStation(Train train, Line line) {
        List<Station> stations = line.getStationsInOrder();
        int currentStationOrder = train.getCurrentStationOrder();
        
        if (currentStationOrder + 1 < stations.size()) {
            return stations.get(currentStationOrder + 1);
        }
        
        return null;
    }
    
    /**
     * 计算列车到下一站的距离
     * @param train 列车对象
     * @param station 车站对象
     * @param line 线路对象
     * @return 距离（米）
     */
    private double calculateDistanceToStation(Train train, Station station, Line line) {
        // 简化实现，实际应用中需要根据轨道位置点精确计算
        // 这里根据车站顺序和站间距估算距离
        int stationDiff = station.getOrder() - train.getCurrentStationOrder();
        // 假设平均站间距为1000米
        return stationDiff * 1000;
    }
    
    /**
     * 处理接近车站的情况
     * @param train 列车对象
     * @param distanceToStation 到车站的距离
     * @param permittedSpeed ATP允许的最大速度
     */
    private void handleApproachingStation(Train train, double distanceToStation, int permittedSpeed) {
        int currentSpeed = train.getCurrentSpeed();
        
        // 根据距离计算目标速度
        int targetSpeed;
        if (distanceToStation <= 100) {
            // 非常接近车站，减速至停止
            targetSpeed = 0;
            applyServiceBrake(train, EMERGENCY_DECELERATION);
        } else if (distanceToStation <= 300) {
            // 较接近车站，减速至低速
            targetSpeed = Math.min(permittedSpeed, 30);
            applyServiceBrake(train, NORMAL_DECELERATION);
        } else {
            // 开始进站减速
            targetSpeed = Math.min(permittedSpeed, 60);
            applyServiceBrake(train, NORMAL_DECELERATION * 0.8);
        }
        
        // 调整列车速度至目标速度
        adjustSpeed(train, targetSpeed);
        
        // 如果距离非常近且速度已经很低，准备停车
        if (distanceToStation <= STATION_POSITIONING_PRECISION && currentSpeed <= 5) {
            train.stop();
            openDoors(train);
        }
    }
    
    /**
     * 处理正常运行情况
     * @param train 列车对象
     * @param permittedSpeed ATP允许的最大速度
     * @param line 线路对象
     */
    private void handleNormalOperation(Train train, int permittedSpeed, Line line) {
        int currentSpeed = train.getCurrentSpeed();
        int cruiseSpeed = (int) (line.getMaxSpeed() * CRUISE_SPEED_FACTOR);
        int targetSpeed = Math.min(permittedSpeed, cruiseSpeed);
        
        // 调整列车速度至目标速度
        adjustSpeed(train, targetSpeed);
    }
    
    /**
     * 调整列车速度
     * @param train 列车对象
     * @param targetSpeed 目标速度
     */
    private void adjustSpeed(Train train, int targetSpeed) {
        int currentSpeed = train.getCurrentSpeed();
        
        if (currentSpeed < targetSpeed) {
            // 需要加速
            int speedDiff = targetSpeed - currentSpeed;
            if (speedDiff > 10) {
                train.accelerate(START_ACCELERATION);
            } else {
                // 平稳加速
                train.accelerate(START_ACCELERATION * 0.5);
            }
        } else if (currentSpeed > targetSpeed) {
            // 需要减速
            int speedDiff = currentSpeed - targetSpeed;
            if (speedDiff > 15) {
                applyServiceBrake(train, NORMAL_DECELERATION);
            } else {
                // 平稳减速
                applyServiceBrake(train, NORMAL_DECELERATION * 0.5);
            }
        }
        // 速度已经合适，保持当前速度
    }
    
    /**
     * 应用常用制动
     * @param train 列车对象
     * @param deceleration 减速度
     */
    private void applyServiceBrake(Train train, double deceleration) {
        train.decelerate(deceleration);
    }
    
    /**
     * 应用紧急制动
     * @param train 列车对象
     */
    private void applyEmergencyBrake(Train train) {
        train.emergencyStop();
    }
    
    /**
     * 打开列车车门
     * @param train 列车对象
     */
    private void openDoors(Train train) {
        train.openDoors();
        // 触发报站系统
        triggerAnnouncement(train, "station_arrived");
    }
    
    /**
     * 关闭列车车门
     * @param train 列车对象
     */
    private void closeDoors(Train train) {
        train.closeDoors();
    }
    
    /**
     * 触发广播系统
     * @param train 列车对象
     * @param announcementType 广播类型
     */
    private void triggerAnnouncement(Train train, String announcementType) {
        // 在实际实现中，这里应该调用音频管理系统播放相应的广播
        // 例如：KAudioManager.getInstance().playAnnouncement(train.getCurrentStationId(), announcementType);
    }
    
    /**
     * 检查列车是否可以从车站出发
     * @param train 列车对象
     * @param signalMap 信号机映射表
     * @return 是否可以出发
     */
    public boolean canDepartFromStation(Train train, Map<String, Signal> signalMap) {
        // 检查车门是否关闭
        if (train.areDoorsOpen()) {
            return false;
        }
        
        // 检查ATP是否允许启动
        return atpSystem.canStartTrain(train, signalMap);
    }
    
    /**
     * 控制列车从车站出发
     * @param train 列车对象
     */
    public void departFromStation(Train train) {
        if (canDepartFromStation(train, null)) { // 实际应该传入signalMap
            // 触发出发广播
            triggerAnnouncement(train, "departure");
            // 开始加速
            train.accelerate(START_ACCELERATION);
        }
    }
}