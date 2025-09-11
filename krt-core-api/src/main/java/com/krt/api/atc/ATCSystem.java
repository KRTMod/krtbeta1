package com.krt.api.atc;

import com.krt.api.data.Line;
import com.krt.api.data.Train;
import com.krt.api.data.Signal;
import com.krt.api.data.Station;
import com.krt.api.dispatch.KDispatcher;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map;

/**
 * 列车自动控制系统(ATC)
 * 集成ATP、ATO和ATS三个子系统，实现列车运行的全面自动化控制
 */
public class ATCSystem {
    private final ATP atpSystem;
    private final ATO atoSystem;
    private final ATS atsSystem;
    private final KDispatcher dispatcher;
    private boolean isEnabled;
    
    public ATCSystem(Map<String, Line> lines, Map<String, Train> trains, 
                     Map<String, Signal> signals, Map<String, Station> stations, 
                     KDispatcher dispatcher) {
        this.dispatcher = dispatcher;
        this.atpSystem = new ATP();
        this.atoSystem = new ATO(atpSystem);
        this.atsSystem = new ATS(lines, trains, signals, stations, dispatcher);
        this.isEnabled = true;
    }
    
    /**
     * 更新ATC系统状态
     * 定期调用以执行所有ATC功能
     */
    public void update() {
        if (!isEnabled) {
            return;
        }
        
        // 更新ATS系统状态
        atsSystem.updateSystemStatus();
        
        // 对每条启用了ATC的线路上的自动运行列车进行控制
        for (Line line : lines.values()) {
            if (line.isAtcEnabled()) {
                updateLineTrains(line);
            }
        }
    }
    
    /**
     * 更新线路上的所有列车
     * @param line 线路对象
     */
    private void updateLineTrains(Line line) {
        for (Train train : trains.values()) {
            if (train.getLineId().equals(line.getId()) && train.isAutoMode()) {
                // 对处于自动驾驶模式的列车进行控制
                atoSystem.controlTrain(train, line, signals, getTrainsOnLine(line.getId()), dispatcher);
            }
        }
    }
    
    /**
     * 获取线路上的所有列车
     * @param lineId 线路ID
     * @return 列车列表
     */
    private List<Train> getTrainsOnLine(String lineId) {
        List<Train> lineTrains = new ArrayList<>();
        for (Train train : trains.values()) {
            if (train.getLineId().equals(lineId)) {
                lineTrains.add(train);
            }
        }
        return lineTrains;
    }
    
    /**
     * 切换ATC系统状态
     * @param enabled 是否启用
     */
    public void setEnabled(boolean enabled) {
        this.isEnabled = enabled;
    }
    
    /**
     * 检查ATC系统是否启用
     * @return 是否启用
     */
    public boolean isEnabled() {
        return isEnabled;
    }
    
    /**
     * 获取ATP子系统
     * @return ATP子系统
     */
    public ATP getATPSystem() {
        return atpSystem;
    }
    
    /**
     * 获取ATO子系统
     * @return ATO子系统
     */
    public ATO getATOSystem() {
        return atoSystem;
    }
    
    /**
     * 获取ATS子系统
     * @return ATS子系统
     */
    public ATS getATSSystem() {
        return atsSystem;
    }
    
    /**
     * 处理紧急情况
     * @param emergencyType 紧急情况类型
     * @param relatedEntities 相关实体ID列表
     */
    public void handleEmergency(String emergencyType, List<String> relatedEntities) {
        // 根据紧急情况类型执行不同的处理逻辑
        switch (emergencyType) {
            case "track_obstruction":
                // 轨道障碍物处理
                handleTrackObstruction(relatedEntities);
                break;
            case "train_failure":
                // 列车故障处理
                handleTrainFailure(relatedEntities);
                break;
            case "power_failure":
                // 电力故障处理
                handlePowerFailure(relatedEntities);
                break;
            case "signal_failure":
                // 信号系统故障处理
                handleSignalFailure(relatedEntities);
                break;
        }
    }
    
    /**
     * 处理轨道障碍物
     * @param lineIds 受影响的线路ID列表
     */
    private void handleTrackObstruction(List<String> lineIds) {
        for (String lineId : lineIds) {
            Line line = lines.get(lineId);
            if (line != null) {
                // 通知调度系统设置线路紧急状态
                dispatcher.setLineEmergencyStatus(lineId, true);
                
                // 对线路上的所有列车实施紧急制动
                for (Train train : trains.values()) {
                    if (train.getLineId().equals(lineId)) {
                        train.emergencyStop();
                    }
                }
            }
        }
    }
    
    /**
     * 处理列车故障
     * @param trainIds 故障列车ID列表
     */
    private void handleTrainFailure(List<String> trainIds) {
        for (String trainId : trainIds) {
            Train train = trains.get(trainId);
            if (train != null) {
                // 对故障列车实施紧急制动
                train.emergencyStop();
                
                // 通知调度系统处理故障列车
                dispatcher.handleFaultyTrain(trainId);
            }
        }
    }
    
    /**
     * 处理电力故障
     * @param powerSectionIds 受影响的电力区段ID列表
     */
    private void handlePowerFailure(List<String> powerSectionIds) {
        // 在实际实现中，这里需要根据电力区段影响的线路和列车进行处理
        // 简化实现：对所有列车实施紧急制动
        for (Train train : trains.values()) {
            train.emergencyStop();
        }
        
        // 通知调度系统设置所有线路为紧急状态
        for (Line line : lines.values()) {
            dispatcher.setLineEmergencyStatus(line.getId(), true);
        }
    }
    
    /**
     * 处理信号系统故障
     * @param signalIds 故障信号机ID列表
     */
    private void handleSignalFailure(List<String> signalIds) {
        // 在实际实现中，这里需要根据信号机影响的区域进行处理
        // 简化实现：启用备用信号系统或降级运行模式
        if (signals != null && !signals.isEmpty() && lines != null && !lines.isEmpty()) {
            // 对于第一个信号机，假设它影响所有线路的运行
            // 在实际实现中，应该确定哪些线路受到影响
            for (Line line : lines.values()) {
                dispatcher.activateBackupSignalSystem(line.getId());
            }
        }
    }
    
    /**
     * 获取系统运行状态报告
     * @return 运行状态报告
     */
    public Map<String, Object> getSystemStatusReport() {
        Map<String, Object> report = new HashMap<>();
        report.put("isEnabled", isEnabled);
        report.put("overallStatus", atsSystem.getSystemOverallStatus());
        
        // 添加各子系统的状态信息
        report.put("atpStatus", "active"); // 简化实现
        report.put("atoStatus", "active"); // 简化实现
        report.put("atsStatus", "active"); // 简化实现
        
        // 添加紧急情况信息
        report.put("emergencies", dispatcher.getCurrentEmergencies());
        
        return report;
    }
    
    // 需要在ATCSystem类中添加这些字段的定义
    private Map<String, Line> lines; // 线路映射表
    private Map<String, Train> trains; // 列车映射表
    private Map<String, Signal> signals; // 信号机映射表
    
    /**
     * 设置线路映射表
     * @param lines 线路映射表
     */
    public void setLines(Map<String, Line> lines) {
        this.lines = lines;
    }
    
    /**
     * 设置列车映射表
     * @param trains 列车映射表
     */
    public void setTrains(Map<String, Train> trains) {
        this.trains = trains;
    }
    
    /**
     * 设置信号机映射表
     * @param signals 信号机映射表
     */
    public void setSignals(Map<String, Signal> signals) {
        this.signals = signals;
    }
}