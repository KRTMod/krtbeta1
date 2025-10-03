package com.krt.mod.system;

import net.minecraft.world.World;
import net.minecraft.util.math.Box;
import com.krt.mod.entity.TrainEntity;
import com.krt.mod.block.ATPSignalBlockEntity;
import com.krt.mod.system.PerformanceMonitor;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 列车自动控制系统（ATC - Automatic Train Control）
 * 作为地铁信号系统的总协调中心，整合ATS、ATP和ATO三个子系统
 */
public class ATC {
    private static final Map<World, ATC> INSTANCES = new HashMap<>();
    private final World world;
    private final ATS ats;
    private final ATP atp;
    private final ATO ato;
    private final TrackSectionManager trackSectionManager;
    private final FaultSafetyManager faultSafetyManager;
    private boolean systemEnabled = true;
    private long lastUpdateTime = 0;

    private ATC(World world) {
        this.world = world;
        this.trackSectionManager = TrackSectionManager.getInstance(world);
        this.ats = ATS.getInstance(world);
        this.atp = ATP.getInstance(world);
        this.ato = ATO.getInstance(world);
        this.faultSafetyManager = FaultSafetyManager.getInstance(world);
        initialize();
    }

    /**
     * 获取实例（单例模式）
     */
    public static ATC getInstance(World world) {
        return INSTANCES.computeIfAbsent(world, ATC::new);
    }

    /**
     * 初始化ATC系统
     */
    private void initialize() {
        LogSystem.systemLog("ATC系统初始化完成，整合了ATS、ATP和ATO子系统");
        // 设置各子系统的初始状态
        setDefaultSystemConfiguration();
    }

    /**
     * 设置默认系统配置
     */
    private void setDefaultSystemConfiguration() {
        // 设置ATS为中央控制模式
        ats.setControlMode(true);
        
        // 设置ATO全局自动模式为启用
        ato.setGlobalAutoMode(true);
        
        // 加载默认速度限制
        loadDefaultSpeedRestrictions();
    }

    /**
     * 加载默认速度限制
     */
    private void loadDefaultSpeedRestrictions() {
        atp.addSpeedRestriction("default", 80.0);     // 默认最大速度80km/h
        atp.addSpeedRestriction("station_area", 30.0); // 车站区域30km/h
        atp.addSpeedRestriction("curve", 50.0);       // 弯道50km/h
        atp.addSpeedRestriction("switch", 40.0);      // 道岔区域40km/h
        atp.addSpeedRestriction("slippery", 60.0);    // 湿滑轨道60km/h
    }

    /**
     * 更新ATC系统状态
     */
    public void update() {
        if (!systemEnabled) {
            return; // 系统未启用
        }

        // 获取当前时间
        long currentTime = System.currentTimeMillis();
        
        // 限制更新频率（每50ms更新一次，约20Hz）
        if (currentTime - lastUpdateTime < 50) {
            return;
        }
        
        lastUpdateTime = currentTime;
        
        PerformanceMonitor.getInstance().startSystemExecution("ATC");
        
        // 1. 先更新故障安全系统，检查是否有故障
        faultSafetyManager.update();
        
        // 如果有严重故障，进入降级模式
        if (faultSafetyManager.hasCriticalFault()) {
            enterDegradedMode();
            return;
        }
        
        // 2. 更新轨道区段管理系统 - 可以适当降低频率
        if (currentTime % 150 < 50) { // 约每3次更新一次
            trackSectionManager.update();
        }
        
        // 3. 更新ATS系统 - 每次都更新（最关键）
        ats.update();
        
        // 4. 更新ATP系统 - 每次都更新（安全关键）
        atp.update();
        
        // 5. 更新ATO系统 - 可以适当降低频率
        if (currentTime % 100 < 50) { // 约每2次更新一次
            ato.update();
        }
        
        // 6. 执行系统协调逻辑
        coordinateSubsystems();
        
        // 7. 记录系统状态日志
        logSystemStatus();
    }

    /**
     * 协调各子系统
     */
    private void coordinateSubsystems() {
        // 获取所有列车
        List<TrainEntity> trains = world.getEntitiesByClass(TrainEntity.class, 
                new Box(0, 0, 0, 6000000, 6000000, 6000000), 
                entity -> true);

        for (TrainEntity train : trains) {
            String trainId = train.getTrainId();
            
            // 获取各子系统的列车数据
            ATS.TrainInfo atsTrainInfo = ats.getTrainInfo(trainId);
            ATP.ATPTrainData atpData = atp.getTrainATPData(trainId);
            ATO.ATOTrainData atoData = ato.getTrainATOData(trainId);
            
            // 确保数据一致性
            ensureDataConsistency(trainId, atsTrainInfo, atpData, atoData);
            
            // 检查是否有紧急情况需要干预
            if (hasEmergency(trainId, atpData)) {
                handleEmergency(trainId);
            }
        }
    }

    /**
     * 确保数据一致性
     */
    private void ensureDataConsistency(String trainId, ATS.TrainInfo atsTrainInfo, 
                                      ATP.ATPTrainData atpData, ATO.ATOTrainData atoData) {
        if (atsTrainInfo != null && atpData != null && atoData != null) {
            // 确保速度数据一致
            atoData.setAllowedSpeed(atpData.get允许速度());
            atoData.setSignalStatus(atpData.get前方信号机状态());
            
            // 确保位置数据一致
            atpData.setPosition(atsTrainInfo.getPosition());
            atoData.setPosition(atsTrainInfo.getPosition());
        }
    }

    /**
     * 检查是否有紧急情况
     */
    private boolean hasEmergency(String trainId, ATP.ATPTrainData atpData) {
        // 前方信号机红灯且距离小于制动距离
        if (atpData != null && 
            atpData.get前方信号机状态() == ATPSignalBlockEntity.SignalStatus.RED && 
            atpData.get前方信号机距离() < atpData.get制动距离()) {
            return true;
        }
        
        // 前方有障碍物
        if (atpData != null && atpData.is前方障碍物() && atpData.get前方障碍物距离() < 5) {
            return true;
        }
        
        return false;
    }

    /**
     * 处理紧急情况
     */
    private void handleEmergency(String trainId) {
        // 触发紧急制动
        TrainControlSystem.getInstance(world).applyEmergencyBrake(trainId);
        
        // 记录紧急事件
        LogSystem.error("ATC系统：列车 " + trainId + " 发生紧急情况，已触发紧急制动");
        
        // 通知故障安全系统记录故障
        faultSafetyManager.recordEmergencyEvent(trainId, "紧急制动触发");
    }

    /**
     * 进入降级模式
     */
    private void enterDegradedMode() {
        // 在降级模式下，只保留ATP的基本安全功能
        ats.setControlMode(false); // 关闭ATS中央控制
        ato.setGlobalAutoMode(false); // 关闭ATO自动模式
        
        // 发送降级模式通知
        LogSystem.warning("ATC系统：进入降级模式，仅保留ATP基本安全功能");
    }

    /**
     * 恢复正常模式
     */
    public void restoreNormalMode() {
        if (faultSafetyManager.hasCriticalFault()) {
            LogSystem.warning("ATC系统：无法恢复正常模式，系统仍存在严重故障");
            return;
        }
        
        // 恢复各子系统正常工作
        ats.setControlMode(true);
        ato.setGlobalAutoMode(true);
        
        LogSystem.systemLog("ATC系统：恢复正常模式");
    }

    /**
     * 记录系统状态日志
     */
    private void logSystemStatus() {
        // 每分钟记录一次系统状态
        long currentTime = System.currentTimeMillis();
        if (currentTime % 60000 < 50) { // 每分钟的前50ms内记录
            int activeTrains = ats.getAllTrainInfos().size();
            int occupiedSections = 0;
            
            for (TrackSectionManager.TrackSection section : trackSectionManager.getAllSections()) {
                if (section.isOccupied()) {
                    occupiedSections++;
                }
            }
            
            LogSystem.systemLog("ATC系统状态：活跃列车数=" + activeTrains + ", 占用区段数=" + 
                              occupiedSections + ", 系统模式=" + (isNormalMode() ? "正常" : "降级"));
            
            PerformanceMonitor.getInstance().endSystemExecution("ATC");
        }
    }

    /**
     * 检查是否为正常模式
     */
    public boolean isNormalMode() {
        return systemEnabled && !faultSafetyManager.hasCriticalFault() && 
               ats.isCentralMode() && ato.getAutoModeEnabled();
    }

    /**
     * 启用/禁用ATC系统
     */
    public void setSystemEnabled(boolean enabled) {
        this.systemEnabled = enabled;
        String status = enabled ? "启用" : "禁用";
        LogSystem.systemLog("ATC系统：全局" + status);
        
        if (!enabled) {
            // 禁用系统时，所有列车转为手动控制
            ato.setGlobalAutoMode(false);
            
            // 通知所有列车系统已禁用
            List<TrainEntity> trains = world.getEntitiesByClass(TrainEntity.class, 
                    new Box(0, 0, 0, 6000000, 6000000, 6000000), 
                    entity -> true);
            
            for (TrainEntity train : trains) {
                train.setATOEnabled(false); // 禁用ATO
                train.setATPEnabled(false); // 禁用ATP
            }
        }
    }

    /**
     * 获取ATS子系统
     */
    public ATS getATS() {
        return ats;
    }

    /**
     * 获取ATP子系统
     */
    public ATP getATP() {
        return atp;
    }

    /**
     * 获取ATO子系统
     */
    public ATO getATO() {
        return ato;
    }

    /**
     * 获取轨道区段管理系统
     */
    public TrackSectionManager getTrackSectionManager() {
        return trackSectionManager;
    }

    /**
     * 获取故障安全管理系统
     */
    public FaultSafetyManager getFaultSafetyManager() {
        return faultSafetyManager;
    }

    /**
     * 配置列车自动驾驶
     */
    public void configureTrainAutoOperation(String trainId, boolean enableATO, boolean enableATP) {
        if (enableATO) {
            ato.setTrainAutoMode(trainId, true);
        } else {
            ato.setTrainAutoMode(trainId, false);
        }
        
        // ATP始终启用，因为它是安全系统
        LogSystem.systemLog("ATC系统：配置列车 " + trainId + " 自动驾驶，ATO=" + enableATO + ", ATP=已启用");
    }

    /**
     * 创建新的线路区段划分
     */
    public void createTrackSections(String lineId, boolean autoDivide) {
        if (autoDivide) {
            trackSectionManager.autoDivideSections(lineId);
        }
        
        LogSystem.systemLog("ATC系统：为线路 " + lineId + " " + (autoDivide ? "自动" : "手动") + "创建了轨道区段");
    }

    /**
     * 设置紧急模式
     */
    public void setEmergencyMode(boolean emergencyMode) {
        atp.setEmergencyMode(emergencyMode);
        
        if (emergencyMode) {
            // 紧急模式下，所有列车转为手动控制
            ato.setGlobalAutoMode(false);
        }
    }
}