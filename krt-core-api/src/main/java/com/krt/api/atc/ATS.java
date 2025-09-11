package com.krt.api.atc;

import com.krt.api.data.Line;
import com.krt.api.data.Train;
import com.krt.api.data.Signal;
import com.krt.api.data.Station;
import com.krt.api.dispatch.KDispatcher;
import java.util.*;

/**
 * 列车自动监控系统(ATS)
 * 整合全线信号数据，实现列车运行状态实时监控与调度指挥
 */
public class ATS {
    // 拥挤状态阈值（线路容量百分比）
    private static final double CONGESTION_THRESHOLD = 0.7;
    // 严重拥挤状态阈值（线路容量百分比）
    private static final double SEVERE_CONGESTION_THRESHOLD = 0.9;
    // 列车晚点阈值（秒）
    private static final int DELAY_THRESHOLD = 300;
    // 严重晚点阈值（秒）
    private static final int SEVERE_DELAY_THRESHOLD = 600;
    
    private final Map<String, Line> lines; // 线路映射表
    private final Map<String, Train> trains; // 列车映射表
    private final Map<String, Signal> signals; // 信号机映射表
    private final Map<String, Station> stations; // 车站映射表
    private final KDispatcher dispatcher; // 调度系统
    
    public ATS(Map<String, Line> lines, Map<String, Train> trains, 
               Map<String, Signal> signals, Map<String, Station> stations, 
               KDispatcher dispatcher) {
        this.lines = lines;
        this.trains = trains;
        this.signals = signals;
        this.stations = stations;
        this.dispatcher = dispatcher;
    }
    
    /**
     * 更新系统状态
     * 定期调用以刷新所有列车、信号和车站的状态
     */
    public void updateSystemStatus() {
        // 更新所有线路状态
        for (Line line : lines.values()) {
            updateLineStatus(line);
        }
        
        // 更新所有信号机状态
        updateAllSignals();
        
        // 监控列车运行状态
        monitorTrainOperations();
        
        // 生成调度建议
        generateDispatchSuggestions();
    }
    
    /**
     * 更新线路状态
     * @param line 线路对象
     */
    private void updateLineStatus(Line line) {
        // 计算线路上的列车数量
        int trainCount = getTrainCountOnLine(line.getId());
        
        // 计算线路容量利用率
        double capacityUtilization = (double) trainCount / line.getMaxSpeed();
        
        // 更新线路运行状态
        Line.Status status;
        if (capacityUtilization >= SEVERE_CONGESTION_THRESHOLD) {
            status = Line.Status.SEVERELY_CONGESTED;
        } else if (capacityUtilization >= CONGESTION_THRESHOLD) {
            status = Line.Status.CONGESTED;
        } else {
            status = Line.Status.NORMAL;
        }
        
        line.setStatus(status);
    }
    
    /**
     * 获取线路上的列车数量
     * @param lineId 线路ID
     * @return 列车数量
     */
    private int getTrainCountOnLine(String lineId) {
        int count = 0;
        for (Train train : trains.values()) {
            if (train.getLineId().equals(lineId)) {
                count++;
            }
        }
        return count;
    }
    
    /**
     * 更新所有信号机状态
     */
    private void updateAllSignals() {
        // 在实际实现中，这里应该根据列车位置、线路状态等信息更新信号机状态
        // 这里调用调度系统的信号更新方法
        dispatcher.updateAllSignals(trains, lines, signals);
    }
    
    /**
     * 监控列车运行状态
     */
    private void monitorTrainOperations() {
        for (Train train : trains.values()) {
            // 检查列车是否晚点
            checkTrainDelay(train);
            
            // 检查列车健康状态
            checkTrainHealth(train);
            
            // 检查列车是否处于异常状态
            checkTrainAbnormalStatus(train);
        }
    }
    
    /**
     * 检查列车是否晚点
     * @param train 列车对象
     */
    private void checkTrainDelay(Train train) {
        // 简化实现，实际应用中需要根据时刻表计算
        long currentTime = System.currentTimeMillis() / 1000;
        long scheduledArrivalTime = train.getScheduledArrivalTime();
        
        if (scheduledArrivalTime > 0) {
            long delay = currentTime - scheduledArrivalTime;
            
            if (delay >= SEVERE_DELAY_THRESHOLD) {
                train.setDelayStatus(Train.DelayStatus.SEVERELY_DELAYED);
                // 触发严重晚点警报
                triggerAlarm(train, "severe_delay");
            } else if (delay >= DELAY_THRESHOLD) {
                train.setDelayStatus(Train.DelayStatus.DELAYED);
                // 触发晚点警报
                triggerAlarm(train, "delay");
            } else {
                train.setDelayStatus(Train.DelayStatus.ON_TIME);
            }
        }
    }
    
    /**
     * 检查列车健康状态
     * @param train 列车对象
     */
    private void checkTrainHealth(Train train) {
        int health = train.getHealth();
        
        if (health < 30) {
            // 健康状态严重恶化，建议立即回厂检修
            train.setHealthStatus(Train.HealthStatus.CRITICAL);
            triggerAlarm(train, "critical_health");
        } else if (health < 60) {
            // 健康状态不佳，建议尽快回厂检修
            train.setHealthStatus(Train.HealthStatus.POOR);
            triggerAlarm(train, "poor_health");
        } else {
            // 健康状态良好
            train.setHealthStatus(Train.HealthStatus.GOOD);
        }
    }
    
    /**
     * 检查列车是否处于异常状态
     * @param train 列车对象
     */
    private void checkTrainAbnormalStatus(Train train) {
        // 检查列车是否停在区间
        if (train.isStopped() && !train.isAtStation()) {
            // 列车停在区间，可能发生故障
            triggerAlarm(train, "stopped_in_tunnel");
        }
        
        // 检查列车是否超速
        Line line = lines.get(train.getLineId());
        if (line != null && train.getCurrentSpeed() > line.getMaxSpeed() * 1.1) {
            // 列车严重超速
            triggerAlarm(train, "over_speed");
        }
    }
    
    /**
     * 触发警报
     * @param train 相关列车
     * @param alarmType 警报类型
     */
    private void triggerAlarm(Train train, String alarmType) {
        // 在实际实现中，这里应该向调度系统发送警报信息
        // 例如：dispatcher.triggerAlarm(train.getId(), alarmType);
    }
    
    /**
     * 生成调度建议
     */
    private void generateDispatchSuggestions() {
        // 为每条线路生成调度建议
        for (Line line : lines.values()) {
            List<String> suggestions = new ArrayList<>();
            
            // 根据线路状态生成建议
            switch (line.getStatus()) {
                case SEVERELY_CONGESTED:
                    suggestions.add("建议增加列车发车间隔");
                    suggestions.add("建议部分列车跳站运行");
                    suggestions.add("考虑启动备用列车分流");
                    break;
                case CONGESTED:
                    suggestions.add("建议适当调整列车发车间隔");
                    suggestions.add("加强站台疏导");
                    break;
                case NORMAL:
                    suggestions.add("线路运行正常，继续保持");
                    break;
            }
            
            // 将建议保存到调度系统
            dispatcher.saveAISuggestions(line.getId(), suggestions);
        }
    }
    
    /**
     * 获取列车实时位置信息
     * @param trainId 列车ID
     * @return 列车位置信息
     */
    public Map<String, Object> getTrainRealTimePosition(String trainId) {
        Train train = trains.get(trainId);
        Map<String, Object> positionInfo = new HashMap<>();
        
        if (train != null) {
            positionInfo.put("trainId", trainId);
            positionInfo.put("lineId", train.getLineId());
            positionInfo.put("currentStation", train.getCurrentStationId());
            positionInfo.put("nextStation", getNextStation(train));
            positionInfo.put("currentSpeed", train.getCurrentSpeed());
            positionInfo.put("status", train.getStatus());
            // 在实际实现中，这里应该添加更详细的位置信息
        }
        
        return positionInfo;
    }
    
    /**
     * 获取列车前方的下一站
     * @param train 列车对象
     * @return 下一站ID
     */
    private String getNextStation(Train train) {
        Line line = lines.get(train.getLineId());
        if (line != null) {
            List<Station> stations = line.getStationsInOrder();
            int currentStationOrder = train.getCurrentStationOrder();
            
            if (currentStationOrder + 1 < stations.size()) {
                return stations.get(currentStationOrder + 1).getId();
            }
        }
        return null;
    }
    
    /**
     * 获取线路运行状态报告
     * @param lineId 线路ID
     * @return 运行状态报告
     */
    public Map<String, Object> getLineStatusReport(String lineId) {
        Line line = lines.get(lineId);
        Map<String, Object> report = new HashMap<>();
        
        if (line != null) {
            report.put("lineId", lineId);
            report.put("lineName", line.getName());
            report.put("status", line.getStatus());
            report.put("trainCount", getTrainCountOnLine(lineId));
            report.put("signalCount", getSignalCountOnLine(lineId));
            report.put("stationCount", line.getStationsInOrder().size());
            report.put("delayedTrains", getDelayedTrainCount(lineId));
            // 在实际实现中，这里应该添加更多报告内容
        }
        
        return report;
    }
    
    /**
     * 获取线路上的信号机数量
     * @param lineId 线路ID
     * @return 信号机数量
     */
    private int getSignalCountOnLine(String lineId) {
        int count = 0;
        // 在实际实现中，需要根据信号机与线路的关联关系计算
        // 这里简化实现
        return count;
    }
    
    /**
     * 获取线路上晚点的列车数量
     * @param lineId 线路ID
     * @return 晚点列车数量
     */
    private int getDelayedTrainCount(String lineId) {
        int count = 0;
        for (Train train : trains.values()) {
            if (train.getLineId().equals(lineId) && 
                (train.getDelayStatus() == Train.DelayStatus.DELAYED || 
                 train.getDelayStatus() == Train.DelayStatus.SEVERELY_DELAYED)) {
                count++;
            }
        }
        return count;
    }
    
    /**
     * 获取系统整体运行状态
     * @return 系统运行状态
     */
    public Map<String, Object> getSystemOverallStatus() {
        Map<String, Object> status = new HashMap<>();
        
        int totalLines = lines.size();
        int totalTrains = trains.size();
        int totalSignals = signals.size();
        int totalStations = stations.size();
        
        // 计算各类状态的数量
        int normalLines = 0, congestedLines = 0, severelyCongestedLines = 0;
        int onTimeTrains = 0, delayedTrains = 0, severelyDelayedTrains = 0;
        int goodHealthTrains = 0, poorHealthTrains = 0, criticalHealthTrains = 0;
        
        for (Line line : lines.values()) {
            switch (line.getStatus()) {
                case NORMAL: normalLines++;
                    break;
                case CONGESTED: congestedLines++;
                    break;
                case SEVERELY_CONGESTED: severelyCongestedLines++;
                    break;
            }
        }
        
        for (Train train : trains.values()) {
            switch (train.getDelayStatus()) {
                case ON_TIME: onTimeTrains++;
                    break;
                case DELAYED: delayedTrains++;
                    break;
                case SEVERELY_DELAYED: severelyDelayedTrains++;
                    break;
            }
            
            switch (train.getHealthStatus()) {
                case GOOD: goodHealthTrains++;
                    break;
                case POOR: poorHealthTrains++;
                    break;
                case CRITICAL: criticalHealthTrains++;
                    break;
            }
        }
        
        // 填充状态信息
        status.put("totalLines", totalLines);
        status.put("normalLines", normalLines);
        status.put("congestedLines", congestedLines);
        status.put("severelyCongestedLines", severelyCongestedLines);
        status.put("totalTrains", totalTrains);
        status.put("onTimeTrains", onTimeTrains);
        status.put("delayedTrains", delayedTrains);
        status.put("severelyDelayedTrains", severelyDelayedTrains);
        status.put("goodHealthTrains", goodHealthTrains);
        status.put("poorHealthTrains", poorHealthTrains);
        status.put("criticalHealthTrains", criticalHealthTrains);
        status.put("totalSignals", totalSignals);
        status.put("totalStations", totalStations);
        
        return status;
    }
}