package com.krt.api;

import com.krt.api.data.*;
import com.krt.api.data.json.KRTJsonData;
import com.krt.api.atc.ATCSystem;
import java.util.List;
import java.util.Map;

/**
 * KRT轨道交通模组核心接口
 * 定义所有功能模块的访问方法
 */
public interface KRTAPI {
    // 车站管理
    Station createStation(String id, String name, String lineId, int x, int y, int z);
    Station getStation(String id);
    List<Station> getAllStations();
    boolean updateStation(String id, String name, String lineId, int x, int y, int z);
    boolean deleteStation(String id);

    // 车厂管理
    Depot createDepot(String id, String name, int x, int y, int z);
    Depot getDepot(String id);
    List<Depot> getAllDepots();
    boolean updateDepot(String id, String name, int x, int y, int z);
    boolean deleteDepot(String id);
    boolean addDepotFacility(String depotId, String facilityType, int startX, int startY, int startZ, int endX, int endY, int endZ);

    // 线路管理
    Line createLine(String id, String name, String color, int maxSpeed, String depotId);
    Line getLine(String id);
    List<Line> getAllLines();
    boolean updateLine(String id, String name, String color, int maxSpeed, String depotId);
    boolean deleteLine(String id);
    boolean addStationToLine(String lineId, String stationId, int order);
    boolean removeStationFromLine(String lineId, String stationId);
    boolean checkLineValidity(String lineId);

    // 列车管理
    Train createTrain(String id, String name, String model, String lineId, String startStationId);
    Train getTrain(String id);
    List<Train> getAllTrains();
    List<Train> getTrainsByLine(String lineId);
    boolean updateTrain(String id, String name, String model, String lineId);
    boolean deleteTrain(String id);
    boolean trainSelfCheck(String trainId);
    boolean startTrain(String trainId);
    boolean stopTrain(String trainId);
    boolean setTrainSpeed(String trainId, int speed);

    // 信号系统管理
    Signal createSignal(String id, int x, int y, int z, String type, String lineId);
    Signal getSignal(String id);
    List<Signal> getAllSignals();
    boolean updateSignalState(String id, String state);
    boolean deleteSignal(String id);
    boolean updateSignalsByTrackSwitch(String trackSwitchId, String state);

    // 调度系统
    boolean startAIDispatch();
    boolean stopAIDispatch();
    boolean isAIDispatchRunning();
    String getAIDispatchSuggestion(String lineId);
    boolean manualDispatchCommand(String command);

    // 数据管理
    boolean loadData();
    boolean saveData();
    KRTJsonData exportData();
    boolean importData(KRTJsonData data);
    boolean validateLine(String lineId);
    Map<String, String> getLineValidationResult(String lineId);

    // 音频管理
    boolean registerAudio(String id, String soundPath);
    boolean playAudio(String id, int x, int y, int z);
    boolean replaceAudio(String id, String newSoundPath);
    
    // ATC系统管理
    boolean enableATC();
    boolean disableATC();
    boolean isATCEnabled();
    ATCSystem getATCSystem();
}