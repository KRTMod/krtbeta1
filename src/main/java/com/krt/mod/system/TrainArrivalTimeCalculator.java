package com.krt.mod.system;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import com.krt.mod.entity.TrainEntity;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 列车到站时间计算器
 * 负责根据列车自动监控系统(ATS)数据精确计算列车到站时间
 */
public class TrainArrivalTimeCalculator {
    private static final Map<World, TrainArrivalTimeCalculator> INSTANCES = new HashMap<>();
    private final World world;
    private final ATS ats;
    private final TrackSectionManager trackSectionManager;
    private final Map<String, TrainPositionTracker> trainPositionTrackers = new ConcurrentHashMap<>();
    private final Map<String, List<ScheduleItem>> stationSchedules = new ConcurrentHashMap<>();
    private long lastClockSyncTime = 0;
    private final int CLOCK_SYNC_INTERVAL = 60000; // 时钟同步间隔(毫秒)

    private TrainArrivalTimeCalculator(World world) {
        this.world = world;
        this.ats = ATS.getInstance(world);
        this.trackSectionManager = TrackSectionManager.getInstance(world);
        initialize();
    }

    /**
     * 获取实例（单例模式）
     */
    public static synchronized TrainArrivalTimeCalculator getInstance(World world) {
        return INSTANCES.computeIfAbsent(world, TrainArrivalTimeCalculator::new);
    }

    /**
     * 初始化计算器
     */
    private void initialize() {
        // 加载时刻表数据
        loadSchedules();
        // 同步系统时钟
        syncSystemClock();
        LogSystem.systemLog("列车到站时间计算器初始化完成");
    }

    /**
     * 加载时刻表数据
     */
    private void loadSchedules() {
        // 从ATS获取所有时刻表
        for (ATS.TrainSchedule schedule : ats.getTrainSchedules()) {
            for (ATS.ScheduleItem item : schedule.getItems()) {
                stationSchedules.computeIfAbsent(item.getStationId(), 
                        k -> new ArrayList<>()).add(new ScheduleItem(
                                schedule.getLineId(),
                                item.getStationId(),
                                item.getArrivalTime(),
                                item.getDepartureTime()
                        ));
            }
        }
    }

    /**
     * 同步系统时钟
     * 从GPS或北斗系统获取标准时钟信息
     */
    private void syncSystemClock() {
        // 这里应该实现与外部时钟源的同步
        // 北斗卫星导航系统时间同步
        // https://baike.baidu.com/item/%E5%8C%97%E6%96%97%E5%8D%AB%E6%98%9F%E5%AF%BC%E8%88%AA%E7%B3%BB%E7%BB%9F/10390403
        
        // 简化实现，使用系统当前时间
        lastClockSyncTime = System.currentTimeMillis();
    }

    /**
     * 计算指定列车到指定车站的预计到站时间
     */
    public ArrivalTimeInfo calculateArrivalTime(TrainEntity train, String stationName) {
        // 定期同步系统时钟
        if (System.currentTimeMillis() - lastClockSyncTime > CLOCK_SYNC_INTERVAL) {
            syncSystemClock();
        }
        
        String trainId = train.getTrainId();
        String lineId = train.getCurrentLine();
        
        // 获取列车位置跟踪器
        TrainPositionTracker tracker = trainPositionTrackers.computeIfAbsent(trainId, 
                k -> new TrainPositionTracker(trainId));
        
        // 更新列车位置信息
        updateTrainPosition(tracker, train);
        
        // 获取车站位置
        BlockPos stationPos = getStationPosition(stationName);
        if (stationPos == null) {
            return new ArrivalTimeInfo(-1, -1, false, "未知车站位置");
        }
        
        // 计算到车站的距离
        double distanceToStation = calculateDistance(tracker.getPosition(), stationPos);
        
        // 获取当前速度
        double currentSpeed = train.getCurrentSpeed();
        
        // 正常运行情况下的计算
        int estimatedMinutes = 0;
        int estimatedSeconds = 0;
        boolean isOnSchedule = true;
        String statusMessage = "正常运行";
        
        // 基于当前速度和距离估算时间
        if (currentSpeed > 0) {
            double estimatedTimeSeconds = (distanceToStation / currentSpeed) * 3.6; // 转换为秒
            estimatedMinutes = (int) (estimatedTimeSeconds / 60);
            estimatedSeconds = (int) (estimatedTimeSeconds % 60);
        }
        
        // 检查特殊情况
        if (isTrainDelayed(train, stationName)) {
            isOnSchedule = false;
            statusMessage = "列车晚点";
            // 根据晚点情况调整估算时间
            estimatedMinutes += getDelayMinutes(train, stationName);
        }
        
        if (isLineBlocked(lineId, train.getNextStation(), stationName)) {
            isOnSchedule = false;
            statusMessage = "前方线路阻塞";
            // 线路阻塞时增加额外等待时间
            estimatedMinutes += 3; // 假设额外等待3分钟
        }
        
        // 检查时刻表
        ScheduleItem scheduleItem = getScheduleForTrain(train, stationName);
        if (scheduleItem != null) {
            // 计算计划时间与实际估算时间的差异
            long now = System.currentTimeMillis();
            long scheduledArrivalTime = scheduleItem.getArrivalTime();
            long timeDifference = scheduledArrivalTime - now;
            
            if (timeDifference > 0) {
                int scheduleMinutes = (int) (timeDifference / 60000);
                int scheduleSeconds = (int) ((timeDifference % 60000) / 1000);
                
                // 综合考虑实际运行情况和时刻表
                // 根据权重计算最终的预计时间
                double realtimeWeight = 0.7; // 实时数据权重
                double scheduleWeight = 0.3; // 时刻表权重
                
                int finalMinutes = (int) (estimatedMinutes * realtimeWeight + scheduleMinutes * scheduleWeight);
                int finalSeconds = (int) (estimatedSeconds * realtimeWeight + scheduleSeconds * scheduleWeight);
                
                // 确保时间不为负
                finalMinutes = Math.max(0, finalMinutes);
                finalSeconds = Math.max(0, finalSeconds);
                
                return new ArrivalTimeInfo(finalMinutes, finalSeconds, isOnSchedule, statusMessage);
            }
        }
        
        // 确保时间不为负
        estimatedMinutes = Math.max(0, estimatedMinutes);
        estimatedSeconds = Math.max(0, estimatedSeconds);
        
        return new ArrivalTimeInfo(estimatedMinutes, estimatedSeconds, isOnSchedule, statusMessage);
    }

    /**
     * 更新列车位置信息
     */
    private void updateTrainPosition(TrainPositionTracker tracker, TrainEntity train) {
        BlockPos currentPos = train.getBlockPos();
        double currentSpeed = train.getCurrentSpeed();
        
        // 从ATS获取轨道区段信息
        TrackSectionManager.TrackSection section = trackSectionManager.getSectionAt(currentPos);
        String sectionId = section != null ? section.getSectionId() : "";
        
        // 更新位置跟踪器
        tracker.updatePosition(currentPos, currentSpeed, sectionId);
        
        // 记录里程信息（模拟编码里程计功能）
        double distanceTraveled = calculateDistance(tracker.getLastPosition(), currentPos);
        tracker.addDistanceTraveled(distanceTraveled);
    }

    /**
     * 计算两点之间的距离
     */
    private double calculateDistance(BlockPos pos1, BlockPos pos2) {
        return Math.sqrt(
                Math.pow(pos1.getX() - pos2.getX(), 2) +
                Math.pow(pos1.getY() - pos2.getY(), 2) +
                Math.pow(pos1.getZ() - pos2.getZ(), 2)
        );
    }

    /**
     * 获取车站位置
     */
    private BlockPos getStationPosition(String stationName) {
        // 从调度系统获取车站位置
        return DispatchSystem.getInstance(world).getStationPosition(stationName);
    }

    /**
     * 检查列车是否晚点
     */
    private boolean isTrainDelayed(TrainEntity train, String stationName) {
        // 获取列车当前时刻表
        ScheduleItem scheduleItem = getScheduleForTrain(train, stationName);
        if (scheduleItem == null) {
            return false;
        }
        
        // 检查当前时间是否已经超过计划到站时间
        long now = System.currentTimeMillis();
        return now > scheduleItem.getArrivalTime();
    }

    /**
     * 获取列车晚点分钟数
     */
    private int getDelayMinutes(TrainEntity train, String stationName) {
        ScheduleItem scheduleItem = getScheduleForTrain(train, stationName);
        if (scheduleItem == null) {
            return 0;
        }
        
        long now = System.currentTimeMillis();
        long delayMilliseconds = now - scheduleItem.getArrivalTime();
        return (int) (delayMilliseconds / 60000); // 转换为分钟
    }

    /**
     * 检查线路是否阻塞
     */
    private boolean isLineBlocked(String lineId, String fromStation, String toStation) {
        // 检查轨道区段占用情况
        List<String> sectionsBetweenStations = getSectionsBetweenStations(lineId, fromStation, toStation);
        
        for (String sectionId : sectionsBetweenStations) {
            // 检查区段是否被占用
            if (trackSectionManager.isSectionOccupied(sectionId)) {
                return true;
            }
        }
        
        return false;
    }

    /**
     * 获取两站之间的轨道区段
     */
    private List<String> getSectionsBetweenStations(String lineId, String fromStation, String toStation) {
        // 简化实现，实际应该根据线路图获取区段列表
        return new ArrayList<>();
    }

    /**
     * 获取列车在指定车站的时刻表项
     */
    private ScheduleItem getScheduleForTrain(TrainEntity train, String stationName) {
        String trainId = train.getTrainId();
        String lineId = train.getCurrentLine();
        
        // 从ATS获取列车信息
        ATS.TrainInfo trainInfo = ats.getTrainInfo(trainId);
        if (trainInfo == null) {
            return null;
        }
        
        // 查找对应车站的时刻表
        List<ScheduleItem> items = stationSchedules.getOrDefault(stationName, Collections.emptyList());
        
        // 找到对应线路的最新时刻表项
        ScheduleItem latestItem = null;
        long latestTime = 0;
        
        for (ScheduleItem item : items) {
            if (item.getLineId().equals(lineId) && item.getArrivalTime() > latestTime) {
                latestItem = item;
                latestTime = item.getArrivalTime();
            }
        }
        
        return latestItem;
    }

    /**
     * 列车位置跟踪器类
     * 模拟编码里程计功能
     */
    private static class TrainPositionTracker {
        private final String trainId;
        private BlockPos currentPosition = new BlockPos(0, 0, 0);
        private BlockPos lastPosition = new BlockPos(0, 0, 0);
        private double currentSpeed = 0;
        private String currentSectionId = "";
        private double totalDistanceTraveled = 0;
        private long lastUpdateTime = System.currentTimeMillis();

        public TrainPositionTracker(String trainId) {
            this.trainId = trainId;
        }

        public void updatePosition(BlockPos position, double speed, String sectionId) {
            this.lastPosition = this.currentPosition;
            this.currentPosition = position;
            this.currentSpeed = speed;
            this.currentSectionId = sectionId;
            this.lastUpdateTime = System.currentTimeMillis();
        }

        public void addDistanceTraveled(double distance) {
            this.totalDistanceTraveled += distance;
        }

        public BlockPos getPosition() { return currentPosition; }
        public BlockPos getLastPosition() { return lastPosition; }
        public double getCurrentSpeed() { return currentSpeed; }
        public String getCurrentSectionId() { return currentSectionId; }
        public double getTotalDistanceTraveled() { return totalDistanceTraveled; }
        public long getLastUpdateTime() { return lastUpdateTime; }
    }

    /**
     * 到站时间信息类
     */
    public static class ArrivalTimeInfo {
        private final int minutesLeft;
        private final int secondsLeft;
        private final boolean isOnSchedule;
        private final String statusMessage;

        public ArrivalTimeInfo(int minutesLeft, int secondsLeft, boolean isOnSchedule, String statusMessage) {
            this.minutesLeft = minutesLeft;
            this.secondsLeft = secondsLeft;
            this.isOnSchedule = isOnSchedule;
            this.statusMessage = statusMessage;
        }

        public int getMinutesLeft() { return minutesLeft; }
        public int getSecondsLeft() { return secondsLeft; }
        public boolean isOnSchedule() { return isOnSchedule; }
        public String getStatusMessage() { return statusMessage; }
    }

    /**
     * 时刻表项类
     */
    public static class ScheduleItem {
        private final String lineId;
        private final String stationId;
        private final long arrivalTime;
        private final long departureTime;

        public ScheduleItem(String lineId, String stationId, long arrivalTime, long departureTime) {
            this.lineId = lineId;
            this.stationId = stationId;
            this.arrivalTime = arrivalTime;
            this.departureTime = departureTime;
        }

        public String getLineId() { return lineId; }
        public String getStationId() { return stationId; }
        public long getArrivalTime() { return arrivalTime; }
        public long getDepartureTime() { return departureTime; }
    }
}