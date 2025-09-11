package com.krt.api.dispatch;

import com.krt.api.KRTAPI;
import com.krt.api.data.Line;
import com.krt.api.data.Train;
import com.krt.api.data.Signal;
import com.krt.api.KRTConfig;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * KRT轨道交通模组调度系统
 * 负责列车调度、信号控制和AI调度建议
 */
public class KDispatcher {
    // 调度状态枚举
    public enum DispatchState {
        NORMAL, // 正常状态
        CONGESTION, // 拥挤状态
        SEVERE_CONGESTION, // 严重拥挤状态
        EMERGENCY // 紧急状态
    }
    
    // 调度模式枚举
    public enum DispatchMode {
        MANUAL, // 手动调度
        AI_CONTROLLED // AI自动调度
    }
    
    // 单例实例
    private static KDispatcher instance;
    
    // 当前调度模式
    private DispatchMode dispatchMode = DispatchMode.MANUAL;
    
    // 线路调度状态映射
    private Map<String, DispatchState> lineStates = new HashMap<>();
    
    // AI调度建议列表
    private List<String> aiSuggestions = new ArrayList<>();
    
    // 随机数生成器
    private Random random = new Random();
    
    // 配置引用
    private KRTConfig config;
    
    // API引用
    private KRTAPI api;
    
    // 线路紧急状态映射
    private Map<String, Boolean> lineEmergencyStatus = new HashMap<>();
    
    // 当前紧急情况列表
    private List<EmergencyInfo> currentEmergencies = new ArrayList<>();
    
    // 线路AI建议映射
    private Map<String, List<String>> lineAISuggestions = new HashMap<>();
    
    /**
     * 获取调度器单例实例
     */
    public static synchronized KDispatcher getInstance() {
        if (instance == null) {
            instance = new KDispatcher();
        }
        return instance;
    }
    
    /**
     * 紧急情况信息类
     */
    public static class EmergencyInfo {
        private String id;
        private String type;
        private String lineId;
        private long timestamp;
        private boolean resolved;
        
        public EmergencyInfo(String type, String lineId) {
            this.id = UUID.randomUUID().toString();
            this.type = type;
            this.lineId = lineId;
            this.timestamp = System.currentTimeMillis();
            this.resolved = false;
        }
        
        // Getter和Setter方法
        public String getId() { return id; }
        public String getType() { return type; }
        public String getLineId() { return lineId; }
        public long getTimestamp() { return timestamp; }
        public boolean isResolved() { return resolved; }
        public void setResolved(boolean resolved) { this.resolved = resolved; }
    }
    
    /**
     * 初始化调度器
     */
    public void initialize(KRTAPI api, KRTConfig config) {
        this.api = api;
        this.config = config;
    }
    
    /**
     * 激活备用信号系统
     */
    public void activateBackupSignalSystem(String lineId) {
        System.out.println("激活备用信号系统: " + lineId);
        // 实现备用信号系统激活逻辑
    }
    
    /**
     * 处理故障列车
     */
    public void handleFaultyTrain(String trainId) {
        System.out.println("处理故障列车: " + trainId);
        // 实现故障列车处理逻辑
    }
    
    /**
     * 设置线路紧急状态
     */
    public void setLineEmergencyStatus(String lineId, boolean isEmergency) {
        lineEmergencyStatus.put(lineId, isEmergency);
        System.out.println("设置线路紧急状态: " + lineId + " -> " + isEmergency);
    }
    
    /**
     * 获取线路紧急状态
     */
    public boolean getLineEmergencyStatus(String lineId) {
        return lineEmergencyStatus.getOrDefault(lineId, false);
    }
    
    /**
     * 添加紧急情况信息
     */
    public void addEmergencyInfo(String type, String lineId) {
        EmergencyInfo emergency = new EmergencyInfo(type, lineId);
        currentEmergencies.add(emergency);
        System.out.println("添加紧急情况: " + type + " 到线路: " + lineId);
    }
    
    /**
     * 获取当前紧急情况列表
     */
    public List<EmergencyInfo> getCurrentEmergencies() {
        return new ArrayList<>(currentEmergencies);
    }
    
    /**
     * 获取线路AI建议
     */
    public List<String> getLineAISuggestions(String lineId) {
        return lineAISuggestions.getOrDefault(lineId, new ArrayList<>());
    }
    
    /**
     * 设置调度模式
     */
    public void setDispatchMode(DispatchMode mode) {
        this.dispatchMode = mode;
    }
    
    /**
     * 获取当前调度模式
     */
    public DispatchMode getDispatchMode() {
        return dispatchMode;
    }
    
    /**
     * 更新所有线路的调度状态
     */
    public void updateAllLineStates() {
        if (api == null) return;
        
        // 获取所有线路
        List<Line> lines = api.getAllLines();
        
        for (Line line : lines) {
            String lineId = line.getId();
            
            // 检查线路状态
            DispatchState state = evaluateLineState(line);
            lineStates.put(lineId, state);
            
            // 根据线路状态调整列车运行
            adjustTrainsByLineState(line, state);
            
            // 更新信号系统
            updateSignalsForLine(line, state);
        }
        
        // 如果启用AI调度且当前为AI模式，生成调度建议
        if (config.isAISuggestionsEnabled() && dispatchMode == DispatchMode.AI_CONTROLLED) {
            generateAISuggestions();
        }
    }
    
    /**
     * 评估线路状态
     */
    private DispatchState evaluateLineState(Line line) {
        if (api == null) return DispatchState.NORMAL;
        
        // 获取线路上的所有列车
        List<Train> trains = api.getTrainsByLineId(line.getId());
        
        // 列车数量评估
        int maxTrains = config.getMaxTrainsPerLine();
        int currentTrains = trains.size();
        
        // 计算平均间隔
        double avgInterval = calculateAverageTrainInterval(line, trains);
        
        // 检查是否有列车延误
        int delayedTrains = countDelayedTrains(trains);
        
        // 根据评估结果确定线路状态
        if (delayedTrains > trains.size() * 0.5 || currentTrains > maxTrains * 1.2) {
            return DispatchState.SEVERE_CONGESTION;
        } else if (delayedTrains > 0 || currentTrains > maxTrains * 0.8 || avgInterval < 2 * 60) {
            return DispatchState.CONGESTION;
        } else {
            return DispatchState.NORMAL;
        }
    }
    
    /**
     * 计算列车平均间隔（秒）
     */
    private double calculateAverageTrainInterval(Line line, List<Train> trains) {
        if (trains.size() < 2) return Double.MAX_VALUE;
        
        // 这里简化实现，实际应该根据列车位置和运行方向计算
        // 假设线路长度和列车平均速度
        double lineLength = line.getTrackPoints().size(); // 简化为轨道点数量
        double avgSpeed = 30.0; // 平均速度 km/h
        
        // 计算理论间隔
        return (lineLength * 3.6) / avgSpeed; // 转换为秒
    }
    
    /**
     * 统计延误列车数量
     */
    private int countDelayedTrains(List<Train> trains) {
        int count = 0;
        for (Train train : trains) {
            // 这里简化实现，实际应该有更复杂的延误判断逻辑
            if (train.getCurrentSpeed() < 0.5 && !train.isStationary()) {
                count++;
            }
        }
        return count;
    }
    
    /**
     * 根据线路状态调整列车运行
     */
    private void adjustTrainsByLineState(Line line, DispatchState state) {
        if (api == null || dispatchMode != DispatchMode.AI_CONTROLLED) return;
        
        List<Train> trains = api.getTrainsByLineId(line.getId());
        
        switch (state) {
            case SEVERE_CONGESTION:
                // 严重拥挤时，减速所有列车并考虑暂时减少列车数量
                for (Train train : trains) {
                    double currentSpeed = train.getCurrentSpeed();
                    if (currentSpeed > 0) {
                        api.setTrainSpeed(train.getId(), currentSpeed * 0.7);
                    }
                }
                // 可以考虑让部分列车返回车厂
                if (trains.size() > 3) {
                    int trainsToReturn = Math.min(2, trains.size() / 3);
                    for (int i = 0; i < trainsToReturn; i++) {
                        Train train = trains.get(i);
                        api.sendTrainToDepot(train.getId());
                    }
                }
                break;
            case CONGESTION:
                // 拥挤时，适当减速列车
                for (Train train : trains) {
                    double currentSpeed = train.getCurrentSpeed();
                    if (currentSpeed > 0) {
                        api.setTrainSpeed(train.getId(), currentSpeed * 0.9);
                    }
                }
                break;
            case NORMAL:
                // 正常状态，保持正常速度
                break;
        }
    }
    
    /**
     * 更新线路信号系统
     */
    private void updateSignalsForLine(Line line, DispatchState state) {
        if (api == null || !config.isSignalSystemEnabled()) return;
        
        List<Signal> signals = api.getSignalsByLineId(line.getId());
        
        for (Signal signal : signals) {
            // 根据信号位置和前方列车情况更新信号状态
            updateSignalState(signal);
        }
    }
    
    /**
     * 更新单个信号状态
     */
    private void updateSignalState(Signal signal) {
        if (api == null) return;
        
        // 获取信号前方一定范围内的列车
        List<Train> nearbyTrains = api.getTrainsNearPosition(
                signal.getX(), signal.getY(), signal.getZ(), 200);
        
        // 根据列车距离更新信号状态
        if (!nearbyTrains.isEmpty()) {
            Train closestTrain = findClosestTrain(signal, nearbyTrains);
            double distance = calculateDistance(signal, closestTrain);
            
            if (distance < 100) {
                // 100米内有列车，显示红色
                signal.setState(Signal.SignalState.RED);
            } else if (distance < 200) {
                // 100-200米内有列车，显示黄色
                signal.setState(Signal.SignalState.YELLOW);
            } else {
                // 200米外，显示绿色
                signal.setState(Signal.SignalState.GREEN);
            }
        } else {
            // 没有列车，显示绿色
            signal.setState(Signal.SignalState.GREEN);
        }
        
        // 更新信号
        api.updateSignal(signal);
    }
    
    /**
     * 计算信号与列车之间的距离
     */
    private double calculateDistance(Signal signal, Train train) {
        double dx = signal.getX() - train.getX();
        double dy = signal.getY() - train.getY();
        double dz = signal.getZ() - train.getZ();
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }
    
    /**
     * 找到最近的列车
     */
    private Train findClosestTrain(Signal signal, List<Train> trains) {
        Train closestTrain = null;
        double minDistance = Double.MAX_VALUE;
        
        for (Train train : trains) {
            double distance = calculateDistance(signal, train);
            if (distance < minDistance) {
                minDistance = distance;
                closestTrain = train;
            }
        }
        
        return closestTrain;
    }
    
    /**
     * 生成AI调度建议
     */
    private void generateAISuggestions() {
        aiSuggestions.clear();
        
        if (api == null) return;
        
        List<Line> lines = api.getAllLines();
        
        for (Line line : lines) {
            DispatchState state = lineStates.getOrDefault(line.getId(), DispatchState.NORMAL);
            
            switch (state) {
                case SEVERE_CONGESTION:
                    aiSuggestions.add("线路" + line.getName() + "严重拥挤，建议增加发车间隔并安排备用列车。");
                    aiSuggestions.add("检查线路" + line.getName() + "的信号系统是否正常工作。");
                    break;
                case CONGESTION:
                    aiSuggestions.add("线路" + line.getName() + "出现拥挤，建议稍微增加发车间隔。");
                    break;
                case NORMAL:
                    // 随机生成一些优化建议
                    if (random.nextDouble() < 0.3) {
                        aiSuggestions.add("线路" + line.getName() + "运行正常，可考虑调整部分列车的停站时间。");
                    }
                    break;
            }
        }
        
        // 添加系统级建议
        checkSystemHealth();
    }
    
    /**
     * 检查系统健康状态
     */
    private void checkSystemHealth() {
        if (api == null) return;
        
        // 检查故障列车
        List<Train> allTrains = api.getAllTrains();
        int faultyTrains = 0;
        
        for (Train train : allTrains) {
            if (train.getHealth() < 50) {
                faultyTrains++;
            }
        }
        
        if (faultyTrains > 0) {
            aiSuggestions.add("检测到" + faultyTrains + "辆列车需要维护，请及时安排检修。");
        }
        
        // 检查电力系统
        // 这里简化实现，实际应该有更复杂的电力系统检查
    }
    
    /**
     * 获取所有AI调度建议
     */
    public List<String> getAISuggestions() {
        return new ArrayList<>(aiSuggestions);
    }
    
    /**
     * 获取线路调度状态
     */
    public DispatchState getLineState(String lineId) {
        return lineStates.getOrDefault(lineId, DispatchState.NORMAL);
    }
    
    /**
     * 手动调度列车
     */
    public boolean manuallyDispatchTrain(String trainId, String command) {
        if (api == null || dispatchMode != DispatchMode.MANUAL) {
            return false;
        }
        
        Train train = api.getTrainById(trainId);
        if (train == null) {
            return false;
        }
        
        // 处理手动调度命令
        switch (command.toLowerCase()) {
            case "start":
                return api.startTrain(trainId);
            case "stop":
                return api.stopTrain(trainId);
            case "speed_up":
                double currentSpeed = train.getCurrentSpeed();
                return api.setTrainSpeed(trainId, currentSpeed * 1.2);
            case "slow_down":
                currentSpeed = train.getCurrentSpeed();
                return api.setTrainSpeed(trainId, Math.max(0, currentSpeed * 0.8));
            case "send_to_depot":
                return api.sendTrainToDepot(trainId);
            default:
                return false;
        }
    }
    
    /**
     * 紧急情况下的处理
     */
    public void handleEmergency(String lineId) {
        if (api == null) return;
        
        // 将线路状态设置为紧急
        lineStates.put(lineId, DispatchState.EMERGENCY);
        
        // 获取线路上的所有列车
        List<Train> trains = api.getTrainsByLineId(lineId);
        
        // 让所有列车减速并停靠最近的车站
        for (Train train : trains) {
            api.stopTrain(train.getId());
            // 这里应该有让列车停靠最近车站的逻辑
        }
        
        // 添加紧急建议
        aiSuggestions.add("线路" + lineId + "发生紧急情况，请立即处理！");
    }
}