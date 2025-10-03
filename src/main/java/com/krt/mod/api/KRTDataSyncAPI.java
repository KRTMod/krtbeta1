package com.krt.mod.api;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * KRT数据同步API接口
 * 为跨模组数据交互提供标准化接口，允许其他模组（如Create、能源模组）与KRT地铁系统交互
 */
public interface KRTDataSyncAPI {
    
    // 单例实例获取方法
    static KRTDataSyncAPI getInstance() {
        return KRTDataSyncAPIImpl.INSTANCE;
    }
    
    // ========= 乘客数据相关API =========
    
    /**
     * 获取指定区域内的乘客数量
     * @param world 世界对象
     * @param centerPos 中心点坐标
     * @param radius 搜索半径
     * @return 乘客数量
     */
    int getPassengerCountInArea(World world, BlockPos centerPos, double radius);
    
    /**
     * 注册乘客数量变化监听器
     * @param world 世界对象
     * @param pos 检测位置
     * @param listener 乘客数量变化监听器
     * @return 监听器ID，用于后续移除
     */
    UUID registerPassengerCountListener(World world, BlockPos pos, Consumer<Integer> listener);
    
    /**
     * 移除乘客数量变化监听器
     * @param listenerId 监听器ID
     */
    void removePassengerCountListener(UUID listenerId);
    
    // ========= 能源消耗相关API =========
    
    /**
     * 获取指定车站/设施的能源消耗数据
     * @param world 世界对象
     * @param facilityPos 设施位置
     * @return 能源消耗数据（焦耳/秒）
     */
    double getFacilityEnergyConsumption(World world, BlockPos facilityPos);
    
    /**
     * 注册能源消耗变化监听器
     * @param world 世界对象
     * @param pos 设施位置
     * @param listener 能源消耗变化监听器
     * @return 监听器ID，用于后续移除
     */
    UUID registerEnergyConsumptionListener(World world, BlockPos pos, Consumer<Double> listener);
    
    /**
     * 移除能源消耗变化监听器
     * @param listenerId 监听器ID
     */
    void removeEnergyConsumptionListener(UUID listenerId);
    
    // ========= 列车状态相关API =========
    
    /**
     * 列车状态枚举
     */
    enum TrainStatus {
        STOPPED(0), // 停止
        ACCELERATING(1), // 加速
        CRUISING(2), // 巡航
        DECELERATING(3), // 减速
        EMERGENCY_STOP(4); // 紧急制动
        
        private final int value;
        
        TrainStatus(int value) {
            this.value = value;
        }
        
        public int getValue() {
            return value;
        }
    }
    
    /**
     * 列车信息类
     */
    class TrainInfo {
        private final UUID trainId;
        private final BlockPos position;
        private final double speed;
        private final TrainStatus status;
        private final int passengerCount;
        private final String lineId;
        
        public TrainInfo(UUID trainId, BlockPos position, double speed, TrainStatus status, int passengerCount, String lineId) {
            this.trainId = trainId;
            this.position = position;
            this.speed = speed;
            this.status = status;
            this.passengerCount = passengerCount;
            this.lineId = lineId;
        }
        
        public UUID getTrainId() { return trainId; }
        public BlockPos getPosition() { return position; }
        public double getSpeed() { return speed; }
        public TrainStatus getStatus() { return status; }
        public int getPassengerCount() { return passengerCount; }
        public String getLineId() { return lineId; }
    }
    
    /**
     * 获取指定区域内的列车信息
     * @param world 世界对象
     * @param centerPos 中心点坐标
     * @param radius 搜索半径
     * @return 列车信息列表
     */
    List<TrainInfo> getTrainsInArea(World world, BlockPos centerPos, double radius);
    
    /**
     * 获取特定列车的信息
     * @param world 世界对象
     * @param trainId 列车ID
     * @return 列车信息，如果不存在返回null
     */
    TrainInfo getTrainInfo(World world, UUID trainId);
    
    /**
     * 注册列车状态变化监听器
     * @param world 世界对象
     * @param trainId 列车ID
     * @param listener 列车状态变化监听器
     * @return 监听器ID，用于后续移除
     */
    UUID registerTrainStatusListener(World world, UUID trainId, Consumer<TrainInfo> listener);
    
    /**
     * 移除列车状态变化监听器
     * @param listenerId 监听器ID
     */
    void removeTrainStatusListener(UUID listenerId);
    
    // ========= 车站相关API =========
    
    /**
     * 车站信息类
     */
    class StationInfo {
        private final String stationName;
        private final BlockPos position;
        private final int passengerCount;
        private final int platformCount;
        private final List<String> connectedLines;
        
        public StationInfo(String stationName, BlockPos position, int passengerCount, int platformCount, List<String> connectedLines) {
            this.stationName = stationName;
            this.position = position;
            this.passengerCount = passengerCount;
            this.platformCount = platformCount;
            this.connectedLines = connectedLines;
        }
        
        public String getStationName() { return stationName; }
        public BlockPos getPosition() { return position; }
        public int getPassengerCount() { return passengerCount; }
        public int getPlatformCount() { return platformCount; }
        public List<String> getConnectedLines() { return connectedLines; }
    }
    
    /**
     * 获取指定位置的车站信息
     * @param world 世界对象
     * @param pos 检测位置
     * @return 车站信息，如果不是车站返回null
     */
    StationInfo getStationInfo(World world, BlockPos pos);
    
    /**
     * 注册车站乘客数量变化监听器
     * @param world 世界对象
     * @param pos 车站位置
     * @param listener 乘客数量变化监听器
     * @return 监听器ID，用于后续移除
     */
    UUID registerStationPassengerListener(World world, BlockPos pos, Consumer<Integer> listener);
    
    /**
     * 移除车站乘客数量变化监听器
     * @param listenerId 监听器ID
     */
    void removeStationPassengerListener(UUID listenerId);
    
    // ========= 通用事件总线 =========
    
    /**
     * KRT事件类型枚举
     */
    enum KRTEventType {
        TRAIN_ARRIVED,     // 列车到站
        TRAIN_DEPARTED,    // 列车离站
        STATION_OPENED,    // 车站开放
        STATION_CLOSED,    // 车站关闭
        PASSENGER_BOARDED, // 乘客上车
        PASSENGER_EXITED   // 乘客下车
    }
    
    /**
     * KRT事件数据类
     */
    class KRTEventData {
        private final KRTEventType eventType;
        private final Object data;
        private final long timestamp;
        
        public KRTEventData(KRTEventType eventType, Object data) {
            this.eventType = eventType;
            this.data = data;
            this.timestamp = System.currentTimeMillis();
        }
        
        public KRTEventType getEventType() { return eventType; }
        public Object getData() { return data; }
        public long getTimestamp() { return timestamp; }
    }
    
    /**
     * 注册KRT事件监听器
     * @param eventType 事件类型
     * @param listener 事件监听器
     * @return 监听器ID，用于后续移除
     */
    UUID registerKRTEventListener(KRTEventType eventType, Consumer<KRTEventData> listener);
    
    /**
     * 移除KRT事件监听器
     * @param listenerId 监听器ID
     */
    void removeKRTEventListener(UUID listenerId);
    
    // ========= 接口版本信息 =========
    
    /**
     * 获取API版本号
     * @return API版本号字符串
     */
    String getAPIVersion();
    
    /**
     * 检查API兼容性
     * @param requiredVersion 所需的最低版本号
     * @return 是否兼容
     */
    boolean isCompatibleWithVersion(String requiredVersion);
}