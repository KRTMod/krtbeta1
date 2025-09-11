package com.krt.api;

import com.krt.api.data.*;
import com.krt.api.data.json.KRTJsonData;
import com.krt.api.dispatch.KDispatcher;
import com.krt.api.atc.ATCSystem;
import com.google.gson.Gson;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;

/**
 * KRTAPI接口的具体实现类
 * 包含所有功能模块的具体实现
 */
public class KRTAPIImpl implements KRTAPI {
    // 存储各实体的Map
    private Map<String, Station> stations = new HashMap<>();
    private Map<String, Depot> depots = new HashMap<>();
    private Map<String, Line> lines = new HashMap<>();
    private Map<String, Train> trains = new HashMap<>();
    private Map<String, Signal> signals = new HashMap<>();
    
    // 调度系统相关
    private boolean aiDispatchRunning = false;
    private Map<String, String> audioRegistry = new HashMap<>();
    
    // ATC系统
    private ATCSystem atcSystem;
    private KDispatcher dispatcher;
    private boolean atcEnabled = false;
    
    // 数据文件路径
    private static final String DATA_FILE_PATH = "krt_data.json";

    @Override
    public Station createStation(String id, String name, String lineId, int x, int y, int z) {
        if (stations.containsKey(id)) {
            return null;
        }
        
        Station station = new Station(id, name, lineId, x, y, z);
        stations.put(id, station);
        return station;
    }

    @Override
    public Station getStation(String id) {
        return stations.get(id);
    }

    @Override
    public List<Station> getAllStations() {
        return new ArrayList<>(stations.values());
    }

    @Override
    public boolean updateStation(String id, String name, String lineId, int x, int y, int z) {
        Station station = stations.get(id);
        if (station == null) {
            return false;
        }
        
        station.setName(name);
        station.setLineId(lineId);
        station.setX(x);
        station.setY(y);
        station.setZ(z);
        return true;
    }

    @Override
    public boolean deleteStation(String id) {
        if (!stations.containsKey(id)) {
            return false;
        }
        
        stations.remove(id);
        // 同时从线路中移除该车站
        for (Line line : lines.values()) {
            line.removeStation(id);
        }
        return true;
    }

    @Override
    public Depot createDepot(String id, String name, int x, int y, int z) {
        if (depots.containsKey(id)) {
            return null;
        }
        
        Depot depot = new Depot(id, name, x, y, z);
        depots.put(id, depot);
        return depot;
    }

    @Override
    public Depot getDepot(String id) {
        return depots.get(id);
    }

    @Override
    public List<Depot> getAllDepots() {
        return new ArrayList<>(depots.values());
    }

    @Override
    public boolean updateDepot(String id, String name, int x, int y, int z) {
        Depot depot = depots.get(id);
        if (depot == null) {
            return false;
        }
        
        depot.setName(name);
        depot.setX(x);
        depot.setY(y);
        depot.setZ(z);
        return true;
    }

    @Override
    public boolean deleteDepot(String id) {
        if (!depots.containsKey(id)) {
            return false;
        }
        
        depots.remove(id);
        return true;
    }

    @Override
    public boolean addDepotFacility(String depotId, String facilityType, int startX, int startY, int startZ, int endX, int endY, int endZ) {
        Depot depot = depots.get(depotId);
        if (depot == null) {
            return false;
        }
        
        return depot.addFacility(facilityType, startX, startY, startZ, endX, endY, endZ);
    }

    @Override
    public Line createLine(String id, String name, String color, int maxSpeed, String depotId) {
        if (lines.containsKey(id)) {
            return null;
        }
        
        // 检查车厂是否存在
        if (depotId != null && !depotId.isEmpty() && !depots.containsKey(depotId)) {
            return null;
        }
        
        Line line = new Line(id, name, color, maxSpeed, depotId);
        lines.put(id, line);
        return line;
    }

    @Override
    public Line getLine(String id) {
        return lines.get(id);
    }

    @Override
    public List<Line> getAllLines() {
        return new ArrayList<>(lines.values());
    }

    @Override
    public boolean updateLine(String id, String name, String color, int maxSpeed, String depotId) {
        Line line = lines.get(id);
        if (line == null) {
            return false;
        }
        
        line.setName(name);
        line.setColor(color);
        line.setMaxSpeed(maxSpeed);
        line.setDepotId(depotId);
        return true;
    }

    @Override
    public boolean deleteLine(String id) {
        if (!lines.containsKey(id)) {
            return false;
        }
        
        lines.remove(id);
        // 同时移除该线路上的所有列车
        List<String> trainIdsToRemove = new ArrayList<>();
        for (Map.Entry<String, Train> entry : trains.entrySet()) {
            if (entry.getValue().getLineId().equals(id)) {
                trainIdsToRemove.add(entry.getKey());
            }
        }
        for (String trainId : trainIdsToRemove) {
            trains.remove(trainId);
        }
        return true;
    }

    @Override
    public boolean addStationToLine(String lineId, String stationId, int order) {
        Line line = lines.get(lineId);
        Station station = stations.get(stationId);
        
        if (line == null || station == null) {
            return false;
        }
        
        return line.addStation(station, order);
    }

    @Override
    public boolean removeStationFromLine(String lineId, String stationId) {
        Line line = lines.get(lineId);
        if (line == null) {
            return false;
        }
        
        return line.removeStation(stationId);
    }

    @Override
    public boolean checkLineValidity(String lineId) {
        Line line = lines.get(lineId);
        if (line == null) {
            return false;
        }
        
        return line.isValid();
    }

    @Override
    public Train createTrain(String id, String name, String model, String lineId, String startStationId) {
        if (trains.containsKey(id)) {
            return null;
        }
        
        // 检查线路和车站是否存在
        if (!lines.containsKey(lineId) || !stations.containsKey(startStationId)) {
            return null;
        }
        
        Train train = new Train(id, name, model, lineId, startStationId);
        trains.put(id, train);
        return train;
    }

    @Override
    public Train getTrain(String id) {
        return trains.get(id);
    }

    @Override
    public List<Train> getAllTrains() {
        return new ArrayList<>(trains.values());
    }

    @Override
    public List<Train> getTrainsByLine(String lineId) {
        List<Train> result = new ArrayList<>();
        for (Train train : trains.values()) {
            if (train.getLineId().equals(lineId)) {
                result.add(train);
            }
        }
        return result;
    }

    @Override
    public boolean updateTrain(String id, String name, String model, String lineId) {
        Train train = trains.get(id);
        if (train == null) {
            return false;
        }
        
        train.setName(name);
        train.setModel(model);
        train.setLineId(lineId);
        return true;
    }

    @Override
    public boolean deleteTrain(String id) {
        if (!trains.containsKey(id)) {
            return false;
        }
        
        trains.remove(id);
        return true;
    }

    @Override
    public boolean trainSelfCheck(String trainId) {
        Train train = trains.get(trainId);
        if (train == null) {
            return false;
        }
        
        return train.selfCheck();
    }

    @Override
    public boolean startTrain(String trainId) {
        Train train = trains.get(trainId);
        if (train == null) {
            return false;
        }
        
        return train.start();
    }

    @Override
    public boolean stopTrain(String trainId) {
        Train train = trains.get(trainId);
        if (train == null) {
            return false;
        }
        
        return train.stop();
    }

    @Override
    public boolean setTrainSpeed(String trainId, int speed) {
        Train train = trains.get(trainId);
        if (train == null) {
            return false;
        }
        
        return train.setSpeed(speed);
    }

    @Override
    public Signal createSignal(String id, int x, int y, int z, String type, String lineId) {
        if (signals.containsKey(id)) {
            return null;
        }
        
        Signal signal = new Signal(id, x, y, z, type, lineId);
        signals.put(id, signal);
        return signal;
    }

    @Override
    public Signal getSignal(String id) {
        return signals.get(id);
    }

    @Override
    public List<Signal> getAllSignals() {
        return new ArrayList<>(signals.values());
    }

    @Override
    public boolean updateSignalState(String id, String state) {
        Signal signal = signals.get(id);
        if (signal == null) {
            return false;
        }
        
        signal.setState(state);
        return true;
    }

    @Override
    public boolean deleteSignal(String id) {
        if (!signals.containsKey(id)) {
            return false;
        }
        
        signals.remove(id);
        return true;
    }

    @Override
    public boolean updateSignalsByTrackSwitch(String trackSwitchId, String state) {
        // 查找关联该道岔的所有信号机
        boolean updated = false;
        for (Signal signal : signals.values()) {
            if (trackSwitchId.equals(signal.getTrackSwitchId())) {
                // 假设距离为100格（实际应用中需要计算真实距离）
                signal.updateStateByTrackSwitch(state, 100);
                updated = true;
            }
        }
        return updated;
    }

    @Override
    public boolean startAIDispatch() {
        if (aiDispatchRunning) {
            return false;
        }
        
        // 初始化调度器
        if (dispatcher == null) {
            dispatcher = KDispatcher.getInstance();
            dispatcher.initialize(this, null); // 暂时传递null作为配置
        }
        
        // 初始化ATC系统
        if (atcSystem == null) {
            atcSystem = new ATCSystem(lines, trains, signals, stations, dispatcher);
            atcSystem.setLines(lines);
            atcSystem.setTrains(trains);
            atcSystem.setSignals(signals);
        }
        
        aiDispatchRunning = true;
        // 启动AI调度线程
        new Thread(() -> {
            while (aiDispatchRunning) {
                try {
                    // 每10秒执行一次调度逻辑
                    Thread.sleep(10000);
                    dispatchTrains();
                    
                    // 如果ATC系统启用，也更新ATC系统
                    if (atcEnabled && atcSystem != null) {
                        atcSystem.update();
                    }
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }).start();
        return true;
    }

    @Override
    public boolean stopAIDispatch() {
        if (!aiDispatchRunning) {
            return false;
        }
        
        aiDispatchRunning = false;
        return true;
    }

    @Override
    public boolean isAIDispatchRunning() {
        return aiDispatchRunning;
    }
    
    @Override
    public boolean enableATC() {
        if (atcSystem == null) {
            // 如果ATC系统尚未初始化，先初始化它
            if (dispatcher == null) {
                dispatcher = KDispatcher.getInstance();
                dispatcher.initialize(this, null);
            }
            
            atcSystem = new ATCSystem(lines, trains, signals, stations, dispatcher);
            atcSystem.setLines(lines);
            atcSystem.setTrains(trains);
            atcSystem.setSignals(signals);
        }
        
        atcEnabled = true;
        atcSystem.setEnabled(true);
        return true;
    }
    
    @Override
    public boolean disableATC() {
        if (atcSystem != null) {
            atcSystem.setEnabled(false);
        }
        atcEnabled = false;
        return true;
    }
    
    @Override
    public boolean isATCEnabled() {
        return atcEnabled;
    }
    
    @Override
    public ATCSystem getATCSystem() {
        return atcSystem;
    }

    @Override
    public String getAIDispatchSuggestion(String lineId) {
        // 生成AI调度建议
        Line line = lines.get(lineId);
        if (line == null) {
            return "线路不存在";
        }
        
        List<Train> lineTrains = getTrainsByLine(lineId);
        int trainCount = lineTrains.size();
        int stationCount = line.getStationsInOrder().size();
        
        if (trainCount == 0) {
            return "建议增加至少一列列车运营";
        }
        
        if (trainCount < stationCount / 3) {
            return "建议增加列车数量以提高运营效率";
        }
        
        return "当前运营状态良好，无需调整";
    }

    @Override
    public boolean manualDispatchCommand(String command) {
        // 处理手动调度命令
        // 实际应用中需要更复杂的命令解析和执行逻辑
        System.out.println("执行手动调度命令: " + command);
        return true;
    }

    @Override
    public boolean loadData() {
        try {
            File file = new File(DATA_FILE_PATH);
            if (!file.exists()) {
                return false;
            }
            
            Gson gson = new Gson();
            KRTJsonData data = gson.fromJson(new FileReader(file), KRTJsonData.class);
            
            // 加载数据到内存
            if (data != null && data.validateData()) {
                // 清空现有数据
                stations.clear();
                depots.clear();
                lines.clear();
                trains.clear();
                signals.clear();
                
                // 加载车站
                for (Station station : data.getStations()) {
                    stations.put(station.getId(), station);
                }
                
                // 加载车厂
                for (Depot depot : data.getDepots()) {
                    depots.put(depot.getId(), depot);
                }
                
                // 加载线路
                for (Line line : data.getLines()) {
                    lines.put(line.getId(), line);
                }
                
                // 加载列车
                for (Train train : data.getTrains()) {
                    trains.put(train.getId(), train);
                }
                
                // 加载信号机
                for (Signal signal : data.getSignals()) {
                    signals.put(signal.getId(), signal);
                }
                
                return true;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    @Override
    public boolean saveData() {
        try {
            KRTJsonData data = new KRTJsonData();
            data.setStations(new ArrayList<>(stations.values()));
            data.setDepots(new ArrayList<>(depots.values()));
            data.setLines(new ArrayList<>(lines.values()));
            data.setTrains(new ArrayList<>(trains.values()));
            data.setSignals(new ArrayList<>(signals.values()));
            
            if (data.validateData()) {
                Gson gson = new Gson();
                try (FileWriter writer = new FileWriter(DATA_FILE_PATH)) {
                    gson.toJson(data, writer);
                }
                return true;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    @Override
    public KRTJsonData exportData() {
        KRTJsonData data = new KRTJsonData();
        data.setStations(new ArrayList<>(stations.values()));
        data.setDepots(new ArrayList<>(depots.values()));
        data.setLines(new ArrayList<>(lines.values()));
        data.setTrains(new ArrayList<>(trains.values()));
        data.setSignals(new ArrayList<>(signals.values()));
        return data;
    }

    @Override
    public boolean importData(KRTJsonData data) {
        if (data != null && data.validateData()) {
            // 清空现有数据
            stations.clear();
            depots.clear();
            lines.clear();
            trains.clear();
            signals.clear();
            
            // 导入数据
            for (Station station : data.getStations()) {
                stations.put(station.getId(), station);
            }
            
            for (Depot depot : data.getDepots()) {
                depots.put(depot.getId(), depot);
            }
            
            for (Line line : data.getLines()) {
                lines.put(line.getId(), line);
            }
            
            for (Train train : data.getTrains()) {
                trains.put(train.getId(), train);
            }
            
            for (Signal signal : data.getSignals()) {
                signals.put(signal.getId(), signal);
            }
            
            return true;
        }
        return false;
    }

    @Override
    public boolean validateLine(String lineId) {
        return checkLineValidity(lineId);
    }

    @Override
    public Map<String, String> getLineValidationResult(String lineId) {
        Map<String, String> result = new HashMap<>();
        Line line = lines.get(lineId);
        
        if (line == null) {
            result.put("status", "error");
            result.put("message", "线路不存在");
            return result;
        }
        
        if (line.getStationsInOrder().size() < 2) {
            result.put("status", "error");
            result.put("message", "车站数量不足，至少需要2个车站");
            return result;
        }
        
        if (line.getDepotId() == null || line.getDepotId().isEmpty() || !depots.containsKey(line.getDepotId())) {
            result.put("status", "error");
            result.put("message", "车厂不存在或未关联");
            return result;
        }
        
        Depot depot = depots.get(line.getDepotId());
        if (!depot.hasRequiredFacilities()) {
            result.put("status", "error");
            result.put("message", "关联的车厂缺少必要设施");
            return result;
        }
        
        if (!line.checkGradient()) {
            result.put("status", "error");
            result.put("message", "线路坡度不符合规范");
            return result;
        }
        
        if (!line.checkTurningRadius()) {
            result.put("status", "error");
            result.put("message", "线路转弯半径不符合规范");
            return result;
        }
        
        result.put("status", "success");
        result.put("message", "线路验证通过");
        return result;
    }

    @Override
    public boolean registerAudio(String id, String soundPath) {
        audioRegistry.put(id, soundPath);
        return true;
    }

    @Override
    public boolean playAudio(String id, int x, int y, int z) {
        // 实际应用中需要调用Minecraft的音频播放API
        String soundPath = audioRegistry.get(id);
        if (soundPath != null) {
            System.out.println("播放音频: " + soundPath + " 在位置: " + x + ", " + y + ", " + z);
            return true;
        }
        return false;
    }

    @Override
    public boolean replaceAudio(String id, String newSoundPath) {
        if (audioRegistry.containsKey(id)) {
            audioRegistry.put(id, newSoundPath);
            return true;
        }
        return false;
    }

    // 调度列车的内部方法
    private void dispatchTrains() {
        // 简化的调度逻辑
        for (Line line : lines.values()) {
            List<Train> lineTrains = getTrainsByLine(line.getId());
            // 检查列车健康状态
            for (Train train : lineTrains) {
                if (!train.isOperational()) {
                    System.out.println("列车 " + train.getName() + " 状态异常，需要检修");
                    if (train.isRunning()) {
                        train.stop();
                    }
                }
            }
            
            // 根据线路长度和车站数量调整列车数量
            int stationCount = line.getStationsInOrder().size();
            int idealTrainCount = Math.max(1, stationCount / 5);
            if (lineTrains.size() < idealTrainCount) {
                System.out.println("线路 " + line.getName() + " 建议增加列车数量");
            }
        }
    }
}