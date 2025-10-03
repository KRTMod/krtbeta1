package com.krt.mod.api;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.world.World;
import com.krt.mod.entity.TrainEntity;
import com.krt.mod.entity.TrainCar;
import com.krt.mod.system.DispatchSystem;
import com.krt.mod.system.LogSystem;
import com.krt.mod.system.LineControlSystem;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * KRT数据同步API的具体实现类
 * 提供跨模组数据交互的功能实现
 */
public class KRTDataSyncAPIImpl implements KRTDataSyncAPI {
    
    // 单例实例
    public static final KRTDataSyncAPIImpl INSTANCE = new KRTDataSyncAPIImpl();
    
    // 监听器存储
    private final Map<UUID, ListenerInfo<?>> listeners = new ConcurrentHashMap<>();
    
    // 监听器信息类
    private static class ListenerInfo<T> {
        private final Consumer<T> listener;
        private final Object key; // 用于标识监听器的键（如位置、列车ID等）
        
        public ListenerInfo(Consumer<T> listener, Object key) {
            this.listener = listener;
            this.key = key;
        }
        
        public Consumer<T> getListener() {
            return listener;
        }
        
        public Object getKey() {
            return key;
        }
    }
    
    // 私有构造函数，防止外部实例化
    private KRTDataSyncAPIImpl() {
        LogSystem.systemLog("KRT数据同步API初始化完成");
    }
    
    // ========= 乘客数据相关实现 =========
    
    @Override
    public int getPassengerCountInArea(World world, BlockPos centerPos, double radius) {
        if (world == null || centerPos == null) {
            return 0;
        }
        
        // 检测范围内的玩家实体
        List<PlayerEntity> players = world.getEntitiesByClass(PlayerEntity.class, 
                new Box(centerPos).expand(radius), player -> player.isAlive());
        
        // 统计列车内的乘客
        List<TrainEntity> trains = world.getEntitiesByClass(TrainEntity.class, 
                new Box(centerPos).expand(radius), train -> train.isAlive());
        
        int trainPassengers = 0;
        for (TrainEntity train : trains) {
            // 通过列车编组获取乘客数量
            if (train.getConsist() != null) {
                for (TrainCar car : train.getConsist().getCars()) {
                    trainPassengers += car.getPassengers();
                }
            }
        }
        
        return players.size() + trainPassengers;
    }
    
    @Override
    public UUID registerPassengerCountListener(World world, BlockPos pos, Consumer<Integer> listener) {
        UUID id = UUID.randomUUID();
        listeners.put(id, new ListenerInfo<>(listener, new WorldPosKey(world, pos)));
        // LogSystem.debugLog("注册乘客数量监听器: " + id + " 位置: " + pos);
        return id;
    }
    
    @Override
    public void removePassengerCountListener(UUID listenerId) {
        if (listeners.containsKey(listenerId)) {
            listeners.remove(listenerId);
            // LogSystem.debugLog("移除乘客数量监听器: " + listenerId);
        }
    }
    
    // ========= 能源消耗相关实现 =========
    
    @Override
    public double getFacilityEnergyConsumption(World world, BlockPos facilityPos) {
        if (world == null || facilityPos == null) {
            return 0.0;
        }
        
        // 这里需要与能源系统集成，获取实际的能源消耗数据
        // 目前返回模拟数据
        double baseConsumption = 100.0; // 基础能耗
        
        // 根据设施类型调整能耗
        // 车站照明系统
        if (world.getBlockState(facilityPos).getBlock() instanceof com.krt.mod.block.PlatformLightBlock) {
            // 检查是否有乘客，有乘客时能耗更高
            int passengerCount = getPassengerCountInArea(world, facilityPos, 10);
            return baseConsumption * (1 + Math.min(passengerCount * 0.1, 1.0));
        }
        
        // 屏蔽门系统
        if (world.getBlockState(facilityPos).getBlock() instanceof com.krt.mod.block.PlatformDoorBlock) {
            // 根据门的状态调整能耗
            return baseConsumption * 0.8; // 略低的能耗
        }
        
        // ATP信号系统
        if (world.getBlockState(facilityPos).getBlock() instanceof com.krt.mod.block.ATPSignalBlock) {
            return baseConsumption * 0.5; // 较低的能耗
        }
        
        return baseConsumption;
    }
    
    @Override
    public UUID registerEnergyConsumptionListener(World world, BlockPos pos, Consumer<Double> listener) {
        UUID id = UUID.randomUUID();
        listeners.put(id, new ListenerInfo<>(listener, new WorldPosKey(world, pos)));
        // LogSystem.debugLog("注册能源消耗监控器: " + id + " 位置: " + pos);
        return id;
    }
    
    @Override
    public void removeEnergyConsumptionListener(UUID listenerId) {
        if (listeners.containsKey(listenerId)) {
            listeners.remove(listenerId);
            // LogSystem.debugLog("移除能源消耗监听器: " + listenerId);
        }
    }
    
    // ========= 列车状态相关实现 =========
    
    @Override
    public List<TrainInfo> getTrainsInArea(World world, BlockPos centerPos, double radius) {
        if (world == null || centerPos == null) {
            return Collections.emptyList();
        }
        
        List<TrainEntity> trains = world.getEntitiesByClass(TrainEntity.class, 
                new Box(centerPos).expand(radius), train -> train.isAlive());
        
        List<TrainInfo> trainInfos = new ArrayList<>(trains.size());
        for (TrainEntity train : trains) {
            TrainStatus status = convertTrainStatus(train);
            // 计算乘客数量
            int passengerCount = 0;
            if (train.getConsist() != null) {
                for (TrainCar car : train.getConsist().getCars()) {
                    passengerCount += car.getPassengers();
                }
            }
            
            trainInfos.add(new TrainInfo(
                train.getUuid(),
                train.getBlockPos(),
                train.getCurrentSpeed(),
                status,
                passengerCount,
                train.getCurrentLine()
            ));
        }
        
        return trainInfos;
    }
    
    @Override
    public TrainInfo getTrainInfo(World world, UUID trainId) {
        if (world == null || trainId == null) {
            return null;
        }
        
        // 查找指定ID的列车
        for (TrainEntity train : world.getEntitiesByClass(TrainEntity.class, 
                new Box(-30000000, -30000000, -30000000, 30000000, 30000000, 30000000),
                entity -> entity.getUuid().equals(trainId))) {
            
            TrainStatus status = convertTrainStatus(train);
            int passengerCount = 0;
            if (train.getConsist() != null) {
                for (TrainCar car : train.getConsist().getCars()) {
                    passengerCount += car.getPassengers();
                }
            }
        
            return new TrainInfo(
                train.getUuid(),
                train.getBlockPos(),
                train.getCurrentSpeed(),
                status,
                passengerCount,
                train.getCurrentLine()
            );
        }
        
        return null;
    }
    
    @Override
    public UUID registerTrainStatusListener(World world, UUID trainId, Consumer<TrainInfo> listener) {
        UUID id = UUID.randomUUID();
        listeners.put(id, new ListenerInfo<>(listener, trainId));
        // LogSystem.debugLog("注册列车状态监听器: " + id + " 列车ID: " + trainId);
        return id;
    }
    
    @Override
    public void removeTrainStatusListener(UUID listenerId) {
        if (listeners.containsKey(listenerId)) {
            listeners.remove(listenerId);
            // LogSystem.debugLog("移除列车状态监听器: " + listenerId);
        }
    }
    
    // 辅助方法：将TrainEntity状态转换为API枚举
    private TrainStatus convertTrainStatus(TrainEntity train) {
        if (train.isEmergencyBraking()) {
            return TrainStatus.EMERGENCY_STOP;
        }
        
        double speed = train.getCurrentSpeed();
        
        // 由于TrainEntity没有直接提供加速度方法，我们简化状态判断
        if (speed < 0.1) {
            return TrainStatus.STOPPED;
        } else {
            // 默认将运行中的列车状态设为CRUISING
            // 要准确区分加速/减速/匀速需要额外的速度历史数据
            return TrainStatus.CRUISING;
        }
    }
    
    // ========= 车站相关实现 =========
    
    @Override
    public StationInfo getStationInfo(World world, BlockPos pos) {
        if (world == null || pos == null) {
            return null;
        }
        
        // 检查指定位置是否在车站范围内
        // 这里简化实现，实际应该与车站系统集成
        
        // 模拟车站数据
        String stationName = "未知车站";
        int platformCount = 2;
        List<String> connectedLines = Arrays.asList("1号线", "2号线");
        int passengerCount = getPassengerCountInArea(world, pos, 30); // 30格范围内的乘客数量
        
        // 尝试从线路控制系统获取车站信息
        try {
            LineControlSystem lineSystem = LineControlSystem.getInstance(world);
            // 检查是否在任何车站附近
            for (LineControlSystem.LineInfo lineInfo : lineSystem.getAllLines()) {
                for (LineControlSystem.StationInfo station : lineInfo.getStations()) {
                    double distance = pos.getSquaredDistance(station.getPosition());
                    // 如果在车站50米范围内，认为是在车站内
                    if (Math.sqrt(distance) < 50) {
                        stationName = station.getStationName();
                        // 简化处理：假设所有车站都有2个站台
                        platformCount = 2;
                        // 简化处理：返回当前线路名称
                        connectedLines = Collections.singletonList(lineInfo.getLineName());
                        break;
                    }
                }
            }
        } catch (Exception e) {
            // 忽略日志系统错误，使用默认值
        }
        
        return new StationInfo(stationName, pos, passengerCount, platformCount, connectedLines);
    }
    
    @Override
    public UUID registerStationPassengerListener(World world, BlockPos pos, Consumer<Integer> listener) {
        UUID id = UUID.randomUUID();
        listeners.put(id, new ListenerInfo<>(listener, new WorldPosKey(world, pos)));
        // LogSystem.debugLog("注册车站乘客监听器: " + id + " 位置: " + pos);
        return id;
    }
    
    @Override
    public void removeStationPassengerListener(UUID listenerId) {
        if (listeners.containsKey(listenerId)) {
            listeners.remove(listenerId);
            // LogSystem.debugLog("移除车站乘客监听器: " + listenerId);
        }
    }
    
    // ========= 通用事件总线实现 =========
    
    // 事件监听器映射
    private final Map<KRTEventType, List<Pair<UUID, Consumer<KRTEventData>>>> eventListeners = new ConcurrentHashMap<>();
    
    private static class Pair<K, V> {
        private final K first;
        private final V second;
        
        public Pair(K first, V second) {
            this.first = first;
            this.second = second;
        }
        
        public K getFirst() { return first; }
        public V getSecond() { return second; }
    }
    
    @Override
    public UUID registerKRTEventListener(KRTEventType eventType, Consumer<KRTEventData> listener) {
        UUID id = UUID.randomUUID();
        
        eventListeners.computeIfAbsent(eventType, k -> new ArrayList<>())
                      .add(new Pair<>(id, listener));
        
        // LogSystem.debugLog("注册KRT事件监听器: " + id + " 事件类型: " + eventType);
        return id;
    }
    
    @Override
    public void removeKRTEventListener(UUID listenerId) {
        boolean found = false;
        
        for (List<Pair<UUID, Consumer<KRTEventData>>> eventListenerList : eventListeners.values()) {
            Iterator<Pair<UUID, Consumer<KRTEventData>>> iterator = eventListenerList.iterator();
            while (iterator.hasNext()) {
                Pair<UUID, Consumer<KRTEventData>> pair = iterator.next();
                if (pair.getFirst().equals(listenerId)) {
                    iterator.remove();
                    found = true;
                    break;
                }
            }
            if (found) break;
        }
        
        if (found) {
            // LogSystem.debugLog("移除KRT事件监听器: " + listenerId);
        }
    }
    
    // 触发事件的方法（内部使用）
    public void triggerEvent(KRTEventType eventType, Object data) {
        KRTEventData eventData = new KRTEventData(eventType, data);
        
        List<Pair<UUID, Consumer<KRTEventData>>> listenerList = eventListeners.get(eventType);
        if (listenerList != null) {
            for (Pair<UUID, Consumer<KRTEventData>> pair : listenerList) {
                try {
                    pair.getSecond().accept(eventData);
                } catch (Exception e) {
                    // LogSystem.errorLog("触发KRT事件时监听器出错: " + e.getMessage());
                }
            }
        }
        
        // LogSystem.debugLog("触发KRT事件: " + eventType + " 数据: " + data);
    }
    
    // ========= 接口版本信息实现 =========
    
    @Override
    public String getAPIVersion() {
        return "1.0.0";
    }
    
    @Override
    public boolean isCompatibleWithVersion(String requiredVersion) {
        try {
            // 简单的版本兼容性检查
            String currentVersion = getAPIVersion();
            
            // 比较主版本号
            int currentMajor = Integer.parseInt(currentVersion.split("\\.")[0]);
            int requiredMajor = Integer.parseInt(requiredVersion.split("\\.")[0]);
            
            return currentMajor >= requiredMajor;
        } catch (Exception e) {
            // LogSystem.warningLog("版本兼容性检查失败: " + e.getMessage());
            return false;
        }
    }
    
    // 辅助类：用于标识世界位置的键
    private static class WorldPosKey {
        private final World world;
        private final BlockPos pos;
        
        public WorldPosKey(World world, BlockPos pos) {
            this.world = world;
            this.pos = pos;
        }
        
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            WorldPosKey that = (WorldPosKey) o;
            return world == that.world && pos.equals(that.pos);
        }
        
        @Override
        public int hashCode() {
            return Objects.hash(System.identityHashCode(world), pos);
        }
    }
}