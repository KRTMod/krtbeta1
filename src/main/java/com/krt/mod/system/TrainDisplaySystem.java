package com.krt.mod.system;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import com.krt.mod.block.PlatformDoorBlock;
import com.krt.mod.entity.TrainEntity;
import com.krt.mod.system.DispatchSystem;
import com.krt.mod.system.LanguageSystem;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class TrainDisplaySystem {
    // 显示系统单例
    private static TrainDisplaySystem instance;
    // 存储所有显示设备
    private final Map<BlockPos, DisplayDevice> displayDevices = new ConcurrentHashMap<>();
    // 存储所有列车的到站信息
    private final Map<String, TrainArrivalInfo> trainArrivalInfos = new ConcurrentHashMap<>();
    // 视频文件缓存
    private final Map<String, VideoData> videoCache = new ConcurrentHashMap<>();

    // 私有化构造函数
    private TrainDisplaySystem() {
        // 初始化数据结构
    }

    // 获取单例实例
    public static synchronized TrainDisplaySystem getInstance() {
        if (instance == null) {
            instance = new TrainDisplaySystem();
        }
        return instance;
    }

    /**
     * 注册一个显示设备
     */
    public void registerDisplayDevice(World world, BlockPos pos, DisplayType type) {
        if (!displayDevices.containsKey(pos)) {
            DisplayDevice device = new DisplayDevice(world, pos, type);
            displayDevices.put(pos, device);
            LogSystem.systemLog("显示设备已注册: " + pos + ", 类型: " + type);
        }
    }

    /**
     * 更新所有显示设备
     */
    public void updateAllDisplays() {
        for (DisplayDevice device : displayDevices.values()) {
            if (!device.isValid()) {
                displayDevices.remove(device.getPos());
                continue;
            }
            updateDisplay(device);
        }
        
        // 清理过期的列车到站信息
        cleanExpiredTrainArrivalInfos();
    }

    /**
     * 更新单个显示设备
     */
    private void updateDisplay(DisplayDevice device) {
        World world = device.getWorld();
        BlockPos pos = device.getPos();
        DisplayType type = device.getType();
        
        switch (type) {
            case ARRIVAL_COUNTDOWN:
                updateArrivalCountdown(world, pos);
                break;
            case SMALL_TV:
                updateSmallTV(world, pos);
                break;
            case TRAIN_STATUS:
                updateTrainStatusDisplay(world, pos);
                break;
        }
    }

    /**
     * 更新到站倒计时显示
     */
    private void updateArrivalCountdown(World world, BlockPos pos) {
        // 获取最近的车站
        String stationName = getNearestStation(world, pos);
        if (stationName == null) {
            return;
        }
        
        // 获取即将到达该车站的列车信息
        List<TrainArrivalInfo> arrivingTrains = getArrivingTrainsAtStation(stationName);
        if (arrivingTrains.isEmpty()) {
            // 没有即将到达的列车
            setDisplayContent(world, pos, "--:--");
            return;
        }
        
        // 取最近的一列列车
        TrainArrivalInfo nearestTrain = arrivingTrains.get(0);
        int minutesLeft = nearestTrain.getMinutesLeft();
        int secondsLeft = nearestTrain.getSecondsLeft();
        
        // 格式化倒计时显示
        String countdownText = String.format("%02d:%02d", minutesLeft, secondsLeft);
        setDisplayContent(world, pos, countdownText);
        
        // 同时显示列车信息和状态
        String trainInfo = nearestTrain.getTrainId() + " 终点站: " + nearestTrain.getDestination();
        if (nearestTrain.hasStatusMessage()) {
            trainInfo += " (" + nearestTrain.getStatusMessage() + ")";
        }
        setDisplaySubContent(world, pos, trainInfo);
    }

    /**
     * 更新小电视显示
     */
    private void updateSmallTV(World world, BlockPos pos) {
        // 检查是否有配置的视频文件
        String videoId = getConfiguredVideo(world, pos);
        if (videoId == null || !videoCache.containsKey(videoId)) {
            // 如果没有配置视频或视频不存在，显示默认内容
            setTvContent(world, pos, "等待视频输入...");
            return;
        }
        
        // 获取视频数据
        VideoData videoData = videoCache.get(videoId);
        
        // 检查视频是否需要更新帧
        if (System.currentTimeMillis() - videoData.lastFrameUpdate > videoData.frameDelay) {
            videoData.currentFrameIndex = (videoData.currentFrameIndex + 1) % videoData.frameCount;
            videoData.lastFrameUpdate = System.currentTimeMillis();
        }
        
        // 显示当前帧
        displayVideoFrame(world, pos, videoData, videoData.currentFrameIndex);
    }

    /**
     * 更新列车状态显示
     */
    private void updateTrainStatusDisplay(World world, BlockPos pos) {
        // 获取附近的列车
        TrainEntity nearestTrain = getNearestTrain(world, pos, 100.0);
        if (nearestTrain == null) {
            setDisplayContent(world, pos, "无列车信息");
            return;
        }
        
        // 显示列车状态信息
        String statusInfo = String.format(
                "列车: %s\n状态: %s\n速度: %.1f km/h\n下一站: %s",
                nearestTrain.getTrainId(),
                getTrainStatusText(nearestTrain),
                nearestTrain.getCurrentSpeed(),
                nearestTrain.getNextStation() != null ? nearestTrain.getNextStation() : "未知"
        );
        
        setDisplayContent(world, pos, statusInfo);
    }

    /**
     * 更新列车到站信息
     */
    public void updateTrainArrivalInfo(TrainEntity train, String stationName, int minutes, int seconds) {
        String key = train.getTrainId() + ":" + stationName;
        TrainArrivalInfo info = new TrainArrivalInfo(train.getTrainId(), stationName, minutes, seconds, train.getDestination());
        trainArrivalInfos.put(key, info);
        
        // 通知相关的显示设备更新
        notifyDisplayDevices(stationName);
    }
    
    /**
     * 使用ATS数据自动更新所有列车到站信息
     */
    public void autoUpdateAllTrainArrivalInfo(World world) {
        // 获取所有列车
        List<TrainEntity> trains = DispatchSystem.getInstance(world).getAllTrains();
        
        for (TrainEntity train : trains) {
            String nextStation = train.getNextStation();
            if (nextStation != null && !nextStation.isEmpty()) {
                // 使用精确计算器计算到站时间
                TrainArrivalTimeCalculator calculator = TrainArrivalTimeCalculator.getInstance(world);
                TrainArrivalTimeCalculator.ArrivalTimeInfo arrivalInfo = calculator.calculateArrivalTime(train, nextStation);
                
                // 更新列车到站信息
                TrainArrivalInfo info = new TrainArrivalInfo(
                        train.getTrainId(),
                        nextStation,
                        arrivalInfo.getMinutesLeft(),
                        arrivalInfo.getSecondsLeft(),
                        train.getDestination(),
                        arrivalInfo.getStatusMessage()
                );
                
                String key = train.getTrainId() + ":" + nextStation;
                trainArrivalInfos.put(key, info);
            }
        }
        
        // 更新所有显示设备
        updateAllDisplays();
    }

    /**
     * 添加视频文件到缓存
     */
    public void addVideoToCache(String videoId, byte[] videoData, int frameCount, int fps) {
        VideoData data = new VideoData(videoId, videoData, frameCount, 1000 / fps);
        videoCache.put(videoId, data);
        LogSystem.systemLog("视频已添加到缓存: " + videoId + ", 帧数: " + frameCount + ", FPS: " + fps);
    }

    /**
     * 通知特定车站的显示设备更新
     */
    private void notifyDisplayDevices(String stationName) {
        // 由于没有实现车站位置查找和显示设备与车站的关联逻辑
        // 这里简单地记录日志并更新所有显示设备
        LogSystem.systemLog("通知车站显示设备更新: " + stationName);
        updateAllDisplays();
    }
    
    /**
     * 清理过期的列车到站信息
     */
    private void cleanExpiredTrainArrivalInfos() {
        long currentTime = System.currentTimeMillis();
        trainArrivalInfos.entrySet().removeIf(entry -> {
            TrainArrivalInfo info = entry.getValue();
            // 移除已到达车站的列车信息
            return info.getMinutesLeft() < 0 || (info.getMinutesLeft() == 0 && info.getSecondsLeft() <= 0);
        });
    }

    /**
     * 获取指定车站的即将到达的列车列表
     */
    private List<TrainArrivalInfo> getArrivingTrainsAtStation(String stationName) {
        List<TrainArrivalInfo> result = new ArrayList<>();
        
        for (TrainArrivalInfo info : trainArrivalInfos.values()) {
            if (info.getStationName().equals(stationName) && 
                info.getMinutesLeft() >= 0 && 
                (info.getMinutesLeft() > 0 || info.getSecondsLeft() > 0)) {
                result.add(info);
            }
        }
        
        // 按到达时间排序
        result.sort(Comparator.comparingInt(TrainArrivalInfo::getTotalSecondsLeft));
        
        return result;
    }

    /**
     * 获取指定位置最近的车站
     */
    private String getNearestStation(World world, BlockPos pos) {
        // 从调度系统获取所有车站，并计算距离
        Map<String, BlockPos> stations = DispatchSystem.getInstance(world).getAllStations();
        
        String nearestStation = null;
        double minDistance = Double.MAX_VALUE;
        
        for (Map.Entry<String, BlockPos> entry : stations.entrySet()) {
            double distance = Math.sqrt(
                    Math.pow(pos.getX() - entry.getValue().getX(), 2) +
                    Math.pow(pos.getY() - entry.getValue().getY(), 2) +
                    Math.pow(pos.getZ() - entry.getValue().getZ(), 2)
            );
            
            if (distance < minDistance) {
                minDistance = distance;
                nearestStation = entry.getKey();
            }
        }
        
        return nearestStation;
    }

    /**
     * 获取指定位置最近的列车
     */
    private TrainEntity getNearestTrain(World world, BlockPos pos, double maxDistance) {
        TrainEntity nearestTrain = null;
        double minDistance = Double.MAX_VALUE;
        
        // 获取所有列车
        List<TrainEntity> trains = DispatchSystem.getInstance(world).getAllTrains();
        
        for (TrainEntity train : trains) {
            double distance = Math.sqrt(
                    Math.pow(pos.getX() - train.getX(), 2) +
                    Math.pow(pos.getY() - train.getY(), 2) +
                    Math.pow(pos.getZ() - train.getZ(), 2)
            );
            
            if (distance < minDistance && distance <= maxDistance) {
                minDistance = distance;
                nearestTrain = train;
            }
        }
        
        return nearestTrain;
    }

    /**
     * 获取列车状态文本
     */
    private String getTrainStatusText(TrainEntity train) {
        if (train.isEmergencyBraking()) {
            return LanguageSystem.translate("krt.train.emergency_braking");
        } else if (train.getCurrentSpeed() == 0) {
            return LanguageSystem.translate("krt.train.stopped");
        } else if (train.getControlSystem().getControlMode() == TrainControlSystem.TrainControlMode.ATO) {
            return LanguageSystem.translate("krt.train.auto_running");
        } else {
            return LanguageSystem.translate("krt.train.manual_running");
        }
    }





    /**
       * 查找车站位置
       */
      private BlockPos findStationPosition(String stationName) {
          // 这应该从车站系统中获取车站位置
          // 目前返回null，实际应用中需要实现
          return null;
      }
      
      /**
       * 设置显示内容
       */
      private void setDisplayContent(World world, BlockPos pos, String content) {
          // 更新方块的状态或NBT数据来显示内容
          // 由于TrainDisplayBlockEntity不存在，这里只记录日志
          LogSystem.systemLog("设置显示内容: " + pos + ", 内容: " + content);
      }
      
      /**
       * 设置显示子内容
       */
      private void setDisplaySubContent(World world, BlockPos pos, String content) {
          // 更新方块的状态或NBT数据来显示子内容
          // 由于TrainDisplayBlockEntity不存在，这里只记录日志
          LogSystem.systemLog("设置显示子内容: " + pos + ", 内容: " + content);
      }
      
      /**
       * 设置电视内容
       */
      private void setTvContent(World world, BlockPos pos, String content) {
          // 更新方块的状态或NBT数据来显示电视内容
          // 由于SmallTVBlockEntity不存在，这里只记录日志
          LogSystem.systemLog("设置电视内容: " + pos + ", 内容: " + content);
      }
      
      /**
       * 显示视频帧
       */
      private void displayVideoFrame(World world, BlockPos pos, VideoData videoData, int frameIndex) {
          // 由于SmallTVBlockEntity不存在，这里只记录日志
          LogSystem.systemLog("显示视频帧: " + pos + ", 视频ID: " + videoData.videoId + ", 帧索引: " + frameIndex);
      }
      
      /**
       * 获取配置的视频ID
       */
      private String getConfiguredVideo(World world, BlockPos pos) {
          // 从方块的NBT数据中获取配置的视频ID
          // 由于TrainDisplayBlockEntity不存在，返回默认值
          return "default_video";
      }
      


    /**
     * 显示设备类
     */
    private static class DisplayDevice {
        private final World world;
        private final BlockPos pos;
        private final DisplayType type;

        public DisplayDevice(World world, BlockPos pos, DisplayType type) {
            this.world = world;
            this.pos = pos;
            this.type = type;
        }

        public World getWorld() {
            return world;
        }

        public BlockPos getPos() {
            return pos;
        }

        public DisplayType getType() {
            return type;
        }
        
        /**
         * 检查设备是否有效
         */
        public boolean isValid() {
            // 检查方块是否仍然存在且类型正确
            return world != null && world.isChunkLoaded(pos);
        }
    }

    /**
     * 列车到站信息类
     */
    private static class TrainArrivalInfo {
        private final String trainId;
        private final String stationName;
        private final int minutesLeft;
        private final int secondsLeft;
        private final String destination;
        private final String statusMessage;
        private final long timestamp;

        public TrainArrivalInfo(String trainId, String stationName, int minutesLeft, int secondsLeft, String destination) {
            this(trainId, stationName, minutesLeft, secondsLeft, destination, "");
        }
        
        public TrainArrivalInfo(String trainId, String stationName, int minutesLeft, int secondsLeft, String destination, String statusMessage) {
            this.trainId = trainId;
            this.stationName = stationName;
            this.minutesLeft = minutesLeft;
            this.secondsLeft = secondsLeft;
            this.destination = destination;
            this.statusMessage = statusMessage;
            this.timestamp = System.currentTimeMillis();
        }

        public String getTrainId() { return trainId; }

        public String getStationName() { return stationName; }

        public String getDestination() { return destination; }
        
        public String getStatusMessage() { return statusMessage; }
        
        public boolean hasStatusMessage() {
            return statusMessage != null && !statusMessage.isEmpty();
        }
        
        public int getMinutesLeft() {
            // 计算剩余分钟数
            long elapsedTime = (System.currentTimeMillis() - timestamp) / 1000;
            int totalSecondsLeft = (minutesLeft * 60 + secondsLeft) - (int) elapsedTime;
            
            if (totalSecondsLeft <= 0) {
                return -1;
            }
            
            return totalSecondsLeft / 60;
        }

        public int getSecondsLeft() {
            // 计算剩余秒数
            long elapsedTime = (System.currentTimeMillis() - timestamp) / 1000;
            int totalSecondsLeft = (minutesLeft * 60 + secondsLeft) - (int) elapsedTime;
            
            if (totalSecondsLeft <= 0) {
                return 0;
            }
            
            return totalSecondsLeft % 60;
        }

        public int getTotalSecondsLeft() {
            return getMinutesLeft() * 60 + getSecondsLeft();
        }
    }
    
    /**
     * 视频数据类
     */
    private static class VideoData {
        private final String videoId;
        private final byte[] data;
        private final int frameCount;
        private final int frameDelay; // 帧延迟（毫秒）
        private int currentFrameIndex = 0;
        private long lastFrameUpdate = System.currentTimeMillis();

        public VideoData(String videoId, byte[] data, int frameCount, int frameDelay) {
            this.videoId = videoId;
            this.data = data;
            this.frameCount = frameCount;
            this.frameDelay = frameDelay;
        }
    }

    /**
     * 显示类型枚举
     */
    public enum DisplayType {
        ARRIVAL_COUNTDOWN,
        SMALL_TV,
        TRAIN_STATUS
    }

    /**
     * 列车显示方块接口（用于标识所有显示设备方块）
     */
    public interface TrainDisplayBlock {
    }
}