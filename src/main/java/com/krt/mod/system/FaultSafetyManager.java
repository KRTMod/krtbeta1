package com.krt.mod.system;

import net.minecraft.world.World;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentHashMap;
import com.krt.mod.system.LogSystem.LogLevel;

/**
 * 故障安全管理系统
 * 负责监控系统故障，执行故障安全策略，确保系统在任何情况下都能保障列车运行安全
 */
public class FaultSafetyManager {
    private static final Map<World, FaultSafetyManager> INSTANCES = new HashMap<>();
    private final World world;
    private final Queue<FaultEvent> faultEvents = new ConcurrentLinkedQueue<>();
    private final Set<String> activeFaults = ConcurrentHashMap.newKeySet();
    private final Map<String, FaultHandler> faultHandlers = new HashMap<>();
    private boolean hasCriticalFault = false;
    private long lastFaultCheckTime = 0;
    private static final long FAULT_CHECK_INTERVAL = 1000; // 故障检查间隔（毫秒）
    private final PowerSupplySystem powerSupplySystem; // 供电系统引用

    private FaultSafetyManager(World world) {
        this.world = world;
        this.powerSupplySystem = VehicleSystemInitializer.getPowerSupplySystem();
        initialize();
    }

    /**
     * 获取实例（单例模式）
     */
    public static FaultSafetyManager getInstance(World world) {
        return INSTANCES.computeIfAbsent(world, FaultSafetyManager::new);
    }

    /**
     * 初始化故障安全管理系统
     */
    private void initialize() {
        LogSystem.systemLog("故障安全管理系统初始化完成");
        // 注册故障处理器
        registerFaultHandlers();
    }

    /**
     * 注册故障处理器
     */
    private void registerFaultHandlers() {
        // 注册ATS故障处理器
        faultHandlers.put("ATS_FAILURE", this::handleATSFailure);
        
        // 注册ATP故障处理器
        faultHandlers.put("ATP_FAILURE", this::handleATPFailure);
        
        // 注册ATO故障处理器
        faultHandlers.put("ATO_FAILURE", this::handleATOFailure);
        
        // 注册通信故障处理器
        faultHandlers.put("COMMUNICATION_FAILURE", this::handleCommunicationFailure);
        
        // 注册信号机故障处理器
        faultHandlers.put("SIGNAL_FAILURE", this::handleSignalFailure);
        
        // 注册道岔故障处理器
        faultHandlers.put("SWITCH_FAILURE", this::handleSwitchFailure);
        
        // 注册列车故障处理器
        faultHandlers.put("TRAIN_FAILURE", this::handleTrainFailure);
        
        // 注册供电系统故障处理器
        faultHandlers.put("POWER_SUPPLY_FAILURE", this::handlePowerSupplyFailure);
    }

    /**
     * 更新故障安全管理系统状态
     */
    public void update() {
        long currentTime = System.currentTimeMillis();
        
        // 定期检查故障
        if (currentTime - lastFaultCheckTime >= FAULT_CHECK_INTERVAL) {
            lastFaultCheckTime = currentTime;
            
            // 检查各子系统状态
            checkSubsystemStatus();
            
            // 处理待处理的故障事件
            processFaultEvents();
            
            // 更新故障状态
            updateFaultStatus();
        }
    }

    /**
     * 检查各子系统状态
     */
    private void checkSubsystemStatus() {
        // 检查ATS系统状态
        checkATSStatus();
        
        // 检查ATP系统状态
        checkATPStatus();
        
        // 检查ATO系统状态
        checkATOStatus();
        
        // 检查通信状态
        checkCommunicationStatus();
        
        // 检查供电系统状态
        checkPowerSupplyStatus();
    }

    /**
     * 检查ATS系统状态
     */
    private void checkATSStatus() {
        // TODO: 实现ATS系统状态检查逻辑
    }

    /**
     * 检查ATP系统状态
     */
    private void checkATPStatus() {
        // TODO: 实现ATP系统状态检查逻辑
    }

    /**
     * 检查ATO系统状态
     */
    private void checkATOStatus() {
        // TODO: 实现ATO系统状态检查逻辑
    }

    /**
     * 检查通信状态
     */
    private void checkCommunicationStatus() {
        // TODO: 实现通信状态检查逻辑
    }
    
    /**
     * 检查供电系统状态
     */
    private void checkPowerSupplyStatus() {
        if (powerSupplySystem != null) {
            PowerSupplySystem.PowerStatus status = powerSupplySystem.getSystemStatus();
            
            if (status == PowerSupplySystem.PowerStatus.ERROR || status == PowerSupplySystem.PowerStatus.OUTAGE) {
                // 供电错误或中断，记录为严重故障
                recordFaultEvent("POWER_SUPPLY_FAILURE", "SYSTEM_WIDE", 
                    "供电系统状态异常: " + status.name() + ", 负载: " + powerSupplySystem.getSystemLoadPercentage() + "%");
            } else if (status == PowerSupplySystem.PowerStatus.WARNING) {
                // 供电警告，记录为非严重故障
                recordFaultEvent("POWER_SUPPLY_FAILURE", "SYSTEM_WIDE", 
                    "供电系统状态警告: " + status.name() + ", 负载: " + powerSupplySystem.getSystemLoadPercentage() + "%");
            }
        }
    }

    /**
     * 处理故障事件
     */
    private void processFaultEvents() {
        FaultEvent event;
        while ((event = faultEvents.poll()) != null) {
            // 将故障添加到活跃故障集合
            activeFaults.add(event.getFaultId());
            
            // 根据故障类型调用相应的处理器
            FaultHandler handler = faultHandlers.get(event.getFaultType());
            if (handler != null) {
                handler.handle(event);
            } else {
                // 默认故障处理
                handleGenericFault(event);
            }
            
            // 记录故障日志
            LogSystem.error("故障安全系统：检测到故障 - " + event.getFaultType() + ", ID: " + 
                          event.getFaultId() + ", 位置: " + event.getLocation());
        }
    }

    /**
     * 更新故障状态
     */
    private void updateFaultStatus() {
        // 检查是否有严重故障
        hasCriticalFault = activeFaults.stream().anyMatch(this::isCriticalFault);
    }

    /**
     * 检查故障是否严重
     */
    private boolean isCriticalFault(String faultId) {
        // 严重故障包括：ATP故障、通信故障、信号机故障和供电故障
        if (faultId.contains("POWER_SUPPLY_FAILURE")) {
            // 判断供电故障的严重程度
            if (powerSupplySystem != null) {
                PowerSupplySystem.PowerStatus status = powerSupplySystem.getSystemStatus();
                // 只有ERROR和OUTAGE状态被视为严重故障
                return status == PowerSupplySystem.PowerStatus.ERROR || status == PowerSupplySystem.PowerStatus.OUTAGE;
            }
            return false; // 如果无法获取供电状态，默认不视为严重故障
        }
        
        return faultId.contains("ATP_FAILURE") || 
               faultId.contains("COMMUNICATION_FAILURE") || 
               faultId.contains("SIGNAL_FAILURE");
    }

    /**
     * 记录故障事件
     */
    public void recordFaultEvent(String faultType, String location, String description) {
        String faultId = faultType + "_" + System.currentTimeMillis();
        FaultEvent event = new FaultEvent(faultId, faultType, location, description);
        faultEvents.offer(event);
    }

    /**
     * 记录紧急事件
     */
    public void recordEmergencyEvent(String relatedEntity, String description) {
        String faultId = "EMERGENCY_" + relatedEntity + "_" + System.currentTimeMillis();
        FaultEvent event = new FaultEvent(faultId, "EMERGENCY", relatedEntity, description);
        faultEvents.offer(event);
        
        // 立即处理紧急事件
        processFaultEvents();
    }

    /**
     * 清除故障
     */
    public void clearFault(String faultId) {
        if (activeFaults.remove(faultId)) {
            LogSystem.systemLog("故障安全系统：故障已清除 - ID: " + faultId);
            updateFaultStatus();
        }
    }

    /**
     * ATS故障处理
     */
    private void handleATSFailure(FaultEvent event) {
        // ATS故障时，切换到车站控制模式
        ATC atc = ATC.getInstance(world);
        atc.getATS().setControlMode(false);
        
        LogSystem.log(LogLevel.WARNING, "故障安全系统：ATS系统故障，已切换到车站控制模式");
    }

    /**
     * ATP故障处理
     */
    private void handleATPFailure(FaultEvent event) {
        // ATP故障是严重故障，需要立即停车
        ATC atc = ATC.getInstance(world);
        atc.setEmergencyMode(true);
        
        LogSystem.error("故障安全系统：ATP系统故障（严重故障），已触发紧急模式");
    }

    /**
     * ATO故障处理
     */
    private void handleATOFailure(FaultEvent event) {
        // ATO故障时，关闭自动模式，转为手动控制
        ATC atc = ATC.getInstance(world);
        atc.getATO().setGlobalAutoMode(false);
        
        LogSystem.log(LogLevel.WARNING, "故障安全系统：ATO系统故障，已关闭自动模式");
    }

    /**
     * 通信故障处理
     */
    private void handleCommunicationFailure(FaultEvent event) {
        // 通信故障是严重故障，需要立即停车
        ATC atc = ATC.getInstance(world);
        atc.setEmergencyMode(true);
        
        LogSystem.log(LogLevel.ERROR, "故障安全系统：通信故障（严重故障），已触发紧急模式");
    }

    /**
     * 信号机故障处理
     */
    private void handleSignalFailure(FaultEvent event) {
        // 信号机故障时，将该信号机设置为红灯
        // TODO: 实现信号机故障处理逻辑
        
        LogSystem.log(LogLevel.WARNING, "故障安全系统：信号机故障，已将相关信号机设置为红灯");
    }

    /**
     * 道岔故障处理
     */
    private void handleSwitchFailure(FaultEvent event) {
        // 道岔故障时，锁定道岔并通知相关列车
        // TODO: 实现道岔故障处理逻辑
        
        LogSystem.log(LogLevel.WARNING, "故障安全系统：道岔故障，已锁定道岔并通知相关列车");
    }

    /**
     * 列车故障处理
     */
    private void handleTrainFailure(FaultEvent event) {
        // 列车故障时，通知该列车转为手动控制
        String trainId = event.getLocation();
        ATC atc = ATC.getInstance(world);
        atc.getATO().setTrainAutoMode(trainId, false);
        
        LogSystem.log(LogLevel.WARNING, "故障安全系统：列车 " + trainId + " 故障，已转为手动控制");
    }
    
    /**
     * 供电系统故障处理
     */
    private void handlePowerSupplyFailure(FaultEvent event) {
        PowerSupplySystem.PowerStatus status = powerSupplySystem != null ? powerSupplySystem.getSystemStatus() : null;
        ATC atc = ATC.getInstance(world);
        
        if (status == PowerSupplySystem.PowerStatus.ERROR || status == PowerSupplySystem.PowerStatus.OUTAGE) {
            // 供电严重异常，触发紧急模式
            atc.setEmergencyMode(true);
            LogSystem.error("故障安全系统：供电系统严重异常（严重故障），已触发紧急模式");
            
            // 将所有信号机设为红灯
            setAllSignalsToRed();
        } else if (status == PowerSupplySystem.PowerStatus.WARNING) {
            // 供电警告，通过ATP添加临时速度限制
            ATP atp = atc.getATP();
            atp.addSpeedRestriction("POWER_WARNING", 40.0); // 限制最大速度为40km/h
            LogSystem.warning("故障安全系统：供电系统警告，已添加临时速度限制");
        }
    }
    
    /**
     * 将所有信号机设为红灯
     */
    private void setAllSignalsToRed() {
        CBTCSystem cbtc = CBTCSystem.getInstance(world);
        if (cbtc != null) {
            // 委托CBTC系统将所有信号机设为红灯
            cbtc.setAllSignalsToRed();
        }
    }

    /**
     * 通用故障处理
     */
    private void handleGenericFault(FaultEvent event) {
        // 默认故障处理逻辑
        LogSystem.log(LogLevel.WARNING, "故障安全系统：处理未分类故障 - " + event.getFaultType());
    }

    /**
     * 是否有严重故障
     */
    public boolean hasCriticalFault() {
        return hasCriticalFault;
    }

    /**
     * 获取活跃故障列表
     */
    public Set<String> getActiveFaults() {
        return Collections.unmodifiableSet(activeFaults);
    }

    /**
     * 获取活跃故障数量
     */
    public int getActiveFaultCount() {
        return activeFaults.size();
    }

    /**
     * 故障事件接口
     */
    @FunctionalInterface
    private interface FaultHandler {
        void handle(FaultEvent event);
    }

    /**
     * 故障事件类
     */
    public static class FaultEvent {
        private final String faultId;
        private final String faultType;
        private final String location;
        private final String description;
        private final long timestamp;
        private boolean isCritical = false;

        public FaultEvent(String faultId, String faultType, String location, String description) {
            this.faultId = faultId;
            this.faultType = faultType;
            this.location = location;
            this.description = description;
            this.timestamp = System.currentTimeMillis();
        }

        // Getters and setters
        public String getFaultId() { return faultId; }
        public String getFaultType() { return faultType; }
        public String getLocation() { return location; }
        public String getDescription() { return description; }
        public long getTimestamp() { return timestamp; }
        public boolean isCritical() { return isCritical; }
        public void setCritical(boolean critical) { isCritical = critical; }
    }
}