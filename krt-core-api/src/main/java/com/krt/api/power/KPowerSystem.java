package com.krt.api.power;

import com.krt.api.KRTAPI;
import com.krt.api.data.Line;
import com.krt.api.data.Station;
import com.krt.api.data.Train;
import java.util.HashMap;
import java.util.Map;
import java.util.List;

/**
 * KRT轨道交通模组电力系统
 * 负责管理列车供电、区间供电、车站电力等
 */
public class KPowerSystem {
    // 电力类型枚举
    public enum PowerType {
        THIRD_RAIL, // 第三轨供电
        OVERHEAD_CONTACT_LINE, // 接触网供电
        BATTERY // 电池供电
    }
    
    // 电力状态枚举
    public enum PowerStatus {
        NORMAL, // 正常
        LOW, // 低电量
        CRITICAL, // 临界状态
        OUTAGE // 断电
    }
    
    // 单例实例
    private static KPowerSystem instance;
    
    // 线路电力状态映射
    private Map<String, PowerStatus> linePowerStatus = new HashMap<>();
    
    // 车站电力状态映射
    private Map<String, PowerStatus> stationPowerStatus = new HashMap<>();
    
    // 列车电力类型映射
    private Map<String, PowerType> trainPowerTypes = new HashMap<>();
    
    // API引用
    private KRTAPI api;
    
    // 电力系统总负载
    private double totalLoad = 0.0;
    
    // 电力系统容量
    private double systemCapacity = 10000.0; // 假设的系统容量
    
    /**
     * 获取电力系统单例实例
     */
    public static synchronized KPowerSystem getInstance() {
        if (instance == null) {
            instance = new KPowerSystem();
        }
        return instance;
    }
    
    /**
     * 初始化电力系统
     */
    public void initialize(KRTAPI api) {
        this.api = api;
        initializePowerStatus();
    }
    
    /**
     * 初始化所有电力状态
     */
    private void initializePowerStatus() {
        if (api == null) return;
        
        // 初始化所有线路的电力状态为正常
        List<Line> lines = api.getAllLines();
        for (Line line : lines) {
            linePowerStatus.put(line.getId(), PowerStatus.NORMAL);
        }
        
        // 初始化所有车站的电力状态为正常
        List<Station> stations = api.getAllStations();
        for (Station station : stations) {
            stationPowerStatus.put(station.getId(), PowerStatus.NORMAL);
        }
        
        // 初始化所有列车的电力类型
        List<Train> trains = api.getAllTrains();
        for (Train train : trains) {
            // 默认使用第三轨供电
            trainPowerTypes.put(train.getId(), PowerType.THIRD_RAIL);
        }
    }
    
    /**
     * 更新电力系统状态
     */
    public void updatePowerSystem() {
        if (api == null) return;
        
        // 计算总负载
        calculateTotalLoad();
        
        // 更新线路电力状态
        updateLinePowerStatus();
        
        // 更新车站电力状态
        updateStationPowerStatus();
        
        // 更新列车电力
        updateTrainPower();
        
        // 检查电力系统健康状态
        checkPowerSystemHealth();
    }
    
    /**
     * 计算电力系统总负载
     */
    private void calculateTotalLoad() {
        if (api == null) return;
        
        double load = 0.0;
        
        // 计算线路负载（接触网、第三轨等）
        List<Line> lines = api.getAllLines();
        for (Line line : lines) {
            // 每条线路基础负载
            load += 100.0;
            
            // 根据线路长度增加负载
            load += line.getTrackPoints().size() * 0.5;
        }
        
        // 计算车站负载
        List<Station> stations = api.getAllStations();
        for (Station station : stations) {
            // 每个车站基础负载
            load += 200.0;
            
            // 根据车站规模增加负载
            load += station.getPlatformCount() * 50.0;
        }
        
        // 计算列车负载
        List<Train> trains = api.getAllTrains();
        for (Train train : trains) {
            // 每辆列车的负载
            load += calculateTrainLoad(train);
        }
        
        totalLoad = load;
    }
    
    /**
     * 计算单辆列车的电力负载
     */
    private double calculateTrainLoad(Train train) {
        // 根据列车速度计算负载
        double speed = train.getCurrentSpeed();
        double baseLoad = 50.0;
        
        // 速度越高，负载越大
        baseLoad += speed * 2.0;
        
        // 根据电力类型调整
        PowerType powerType = trainPowerTypes.getOrDefault(train.getId(), PowerType.THIRD_RAIL);
        switch (powerType) {
            case OVERHEAD_CONTACT_LINE:
                baseLoad *= 1.1; // 接触网效率稍低
                break;
            case BATTERY:
                baseLoad *= 1.2; // 电池效率更低
                break;
            default:
                // 第三轨默认
                break;
        }
        
        return baseLoad;
    }
    
    /**
     * 更新线路电力状态
     */
    private void updateLinePowerStatus() {
        if (api == null) return;
        
        List<Line> lines = api.getAllLines();
        
        for (Line line : lines) {
            String lineId = line.getId();
            
            // 检查线路上的列车数量
            int trainCount = api.getTrainsByLineId(lineId).size();
            
            // 检查线路长度
            int lineLength = line.getTrackPoints().size();
            
            // 计算线路负载比例
            double lineLoadRatio = (lineLength * 0.5 + trainCount * 100.0) / (systemCapacity / 2);
            
            // 根据负载比例更新电力状态
            PowerStatus status;
            if (lineLoadRatio > 0.9) {
                status = PowerStatus.CRITICAL;
            } else if (lineLoadRatio > 0.7) {
                status = PowerStatus.LOW;
            } else {
                status = PowerStatus.NORMAL;
            }
            
            linePowerStatus.put(lineId, status);
        }
    }
    
    /**
     * 更新车站电力状态
     */
    private void updateStationPowerStatus() {
        if (api == null) return;
        
        List<Station> stations = api.getAllStations();
        
        for (Station station : stations) {
            String stationId = station.getId();
            
            // 检查车站规模和设施
            int platformCount = station.getPlatformCount();
            
            // 计算车站负载比例
            double stationLoadRatio = (platformCount * 50.0 + 200.0) / (systemCapacity / 5);
            
            // 根据负载比例更新电力状态
            PowerStatus status;
            if (stationLoadRatio > 0.9) {
                status = PowerStatus.CRITICAL;
            } else if (stationLoadRatio > 0.7) {
                status = PowerStatus.LOW;
            } else {
                status = PowerStatus.NORMAL;
            }
            
            stationPowerStatus.put(stationId, status);
        }
    }
    
    /**
     * 更新列车电力
     */
    private void updateTrainPower() {
        if (api == null) return;
        
        List<Train> trains = api.getAllTrains();
        
        for (Train train : trains) {
            String trainId = train.getId();
            
            // 获取列车所在线路
            String lineId = train.getLineId();
            
            // 检查线路电力状态
            PowerStatus lineStatus = linePowerStatus.getOrDefault(lineId, PowerStatus.NORMAL);
            
            // 根据线路电力状态影响列车运行
            switch (lineStatus) {
                case OUTAGE:
                    // 线路断电，列车无法运行
                    api.stopTrain(trainId);
                    break;
                case CRITICAL:
                    // 线路电力严重不足，限制列车速度
                    if (train.getCurrentSpeed() > 20) {
                        api.setTrainSpeed(trainId, 20);
                    }
                    break;
                case LOW:
                    // 线路电力不足，稍微限制列车速度
                    if (train.getCurrentSpeed() > 30) {
                        api.setTrainSpeed(trainId, 30);
                    }
                    break;
                default:
                    // 电力正常，不限制
                    break;
            }
        }
    }
    
    /**
     * 检查电力系统健康状态
     */
    private void checkPowerSystemHealth() {
        // 计算系统负载率
        double loadRatio = totalLoad / systemCapacity;
        
        // 如果负载率超过90%，可能需要触发警报
        if (loadRatio > 0.9) {
            // 这里可以添加警报逻辑
        }
    }
    
    /**
     * 设置线路电力状态
     */
    public void setLinePowerStatus(String lineId, PowerStatus status) {
        linePowerStatus.put(lineId, status);
    }
    
    /**
     * 获取线路电力状态
     */
    public PowerStatus getLinePowerStatus(String lineId) {
        return linePowerStatus.getOrDefault(lineId, PowerStatus.NORMAL);
    }
    
    /**
     * 设置车站电力状态
     */
    public void setStationPowerStatus(String stationId, PowerStatus status) {
        stationPowerStatus.put(stationId, status);
    }
    
    /**
     * 获取车站电力状态
     */
    public PowerStatus getStationPowerStatus(String stationId) {
        return stationPowerStatus.getOrDefault(stationId, PowerStatus.NORMAL);
    }
    
    /**
     * 设置列车电力类型
     */
    public void setTrainPowerType(String trainId, PowerType powerType) {
        trainPowerTypes.put(trainId, powerType);
    }
    
    /**
     * 获取列车电力类型
     */
    public PowerType getTrainPowerType(String trainId) {
        return trainPowerTypes.getOrDefault(trainId, PowerType.THIRD_RAIL);
    }
    
    /**
     * 获取电力系统总负载
     */
    public double getTotalLoad() {
        return totalLoad;
    }
    
    /**
     * 获取电力系统容量
     */
    public double getSystemCapacity() {
        return systemCapacity;
    }
    
    /**
     * 设置电力系统容量
     */
    public void setSystemCapacity(double capacity) {
        this.systemCapacity = Math.max(0, capacity);
    }
    
    /**
     * 检查列车是否能获取足够的电力
     */
    public boolean canTrainReceivePower(String trainId) {
        if (api == null) return true;
        
        Train train = api.getTrainById(trainId);
        if (train == null) return false;
        
        // 获取列车所在线路
        String lineId = train.getLineId();
        
        // 检查线路电力状态
        PowerStatus lineStatus = linePowerStatus.getOrDefault(lineId, PowerStatus.NORMAL);
        
        // 如果线路断电，列车无法获取电力（除非是电池供电）
        if (lineStatus == PowerStatus.OUTAGE) {
            PowerType powerType = trainPowerTypes.getOrDefault(trainId, PowerType.THIRD_RAIL);
            return powerType == PowerType.BATTERY;
        }
        
        return true;
    }
    
    /**
     * 模拟电力故障
     */
    public void simulatePowerFailure(String lineId) {
        linePowerStatus.put(lineId, PowerStatus.OUTAGE);
        
        // 获取该线路上的所有车站
        List<Station> stations = api.getStationsByLineId(lineId);
        for (Station station : stations) {
            stationPowerStatus.put(station.getId(), PowerStatus.OUTAGE);
        }
    }
    
    /**
     * 恢复电力供应
     */
    public void restorePower(String lineId) {
        linePowerStatus.put(lineId, PowerStatus.NORMAL);
        
        // 获取该线路上的所有车站
        List<Station> stations = api.getStationsByLineId(lineId);
        for (Station station : stations) {
            stationPowerStatus.put(station.getId(), PowerStatus.NORMAL);
        }
    }
    
    /**
     * 获取电力系统健康报告
     */
    public String getPowerSystemHealthReport() {
        StringBuilder report = new StringBuilder();
        
        // 系统总览
        double loadRatio = totalLoad / systemCapacity;
        report.append("电力系统总览：\n");
        report.append("- 总负载: " + String.format("%.2f", totalLoad) + " / " + String.format("%.2f", systemCapacity) + " (" + String.format("%.1f%%", loadRatio * 100) + ")\n");
        
        // 线路电力状态
        report.append("\n线路电力状态：\n");
        for (Map.Entry<String, PowerStatus> entry : linePowerStatus.entrySet()) {
            String lineId = entry.getKey();
            PowerStatus status = entry.getValue();
            report.append("- 线路" + lineId + ": " + status.name() + "\n");
        }
        
        // 车站电力状态
        report.append("\n车站电力状态：\n");
        int problematicStations = 0;
        for (Map.Entry<String, PowerStatus> entry : stationPowerStatus.entrySet()) {
            PowerStatus status = entry.getValue();
            if (status == PowerStatus.CRITICAL || status == PowerStatus.OUTAGE) {
                String stationId = entry.getKey();
                report.append("- 车站" + stationId + ": " + status.name() + "\n");
                problematicStations++;
            }
        }
        
        if (problematicStations == 0) {
            report.append("- 所有车站电力状态正常\n");
        }
        
        return report.toString();
    }
}