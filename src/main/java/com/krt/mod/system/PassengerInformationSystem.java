package com.krt.mod.system;

import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import com.krt.mod.entity.TrainEntity;
import com.krt.mod.system.TrainDisplaySystem.DisplayType;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;

public class PassengerInformationSystem {
    // PIS系统单例
    private static PassengerInformationSystem instance;
    // 存储所有PIS显示设备
    private final Map<BlockPos, PISDisplayDevice> displayDevices = new ConcurrentHashMap<>();
    // 存储每列车的PIS信息
    private final Map<String, TrainPISInfo> trainPISInfoMap = new ConcurrentHashMap<>();
    // 存储线路信息
    private final Map<String, LineInfo> lineInfoMap = new ConcurrentHashMap<>();
    // 多媒体内容缓存
    private final Map<String, MediaContent> mediaCache = new ConcurrentHashMap<>();
    // 紧急信息队列
    private final Queue<EmergencyMessage> emergencyMessages = new ConcurrentLinkedQueue<>();
    // 当前活动的紧急信息
    private EmergencyMessage currentEmergencyMessage = null;

    // 私有化构造函数
    private PassengerInformationSystem() {
    }

    // 获取单例实例
    public static synchronized PassengerInformationSystem getInstance() {
        if (instance == null) {
            instance = new PassengerInformationSystem();
        }
        return instance;
    }

    /**
     * 注册一个PIS显示设备
     */
    public void registerDisplayDevice(World world, BlockPos pos, PISDisplayType type) {
        if (!displayDevices.containsKey(pos)) {
            PISDisplayDevice device = new PISDisplayDevice(world, pos, type);
            displayDevices.put(pos, device);
            LogSystem.systemLog("PIS显示设备已注册: " + pos + ", 类型: " + type);
        }
    }

    /**
     * 更新所有PIS显示设备
     */
    public void updateAllDisplays() {
        // 处理紧急信息
        processEmergencyMessages();

        // 更新所有显示设备
        for (PISDisplayDevice device : displayDevices.values()) {
            if (!device.isValid()) {
                displayDevices.remove(device.getPos());
                continue;
            }
            updateDisplay(device);
        }
    }

    /**
     * 更新单个PIS显示设备
     */
    private void updateDisplay(PISDisplayDevice device) {
        World world = device.getWorld();
        BlockPos pos = device.getPos();
        PISDisplayType type = device.getType();

        // 检查是否有紧急信息需要显示
        if (currentEmergencyMessage != null) {
            showEmergencyMessage(device);
            return;
        }

        switch (type) {
            case TRAIN_INTERIOR_DISPLAY:
                updateTrainInteriorDisplay(device);
                break;
            case STATION_PLATFORM_DISPLAY:
                updateStationPlatformDisplay(device);
                break;
            case STATION_HALL_DISPLAY:
                updateStationHallDisplay(device);
                break;
            case DYNAMIC_MAP_DISPLAY:
                updateDynamicMapDisplay(device);
                break;
            case MEDIA_DISPLAY:
                updateMediaDisplay(device);
                break;
            case CROWD_LEVEL_DISPLAY:
                updateCrowdLevelDisplay(device);
                break;
        }
    }

    /**
     * 更新列车内部显示
     */
    private void updateTrainInteriorDisplay(PISDisplayDevice device) {
        World world = device.getWorld();
        BlockPos pos = device.getPos();
        
        // 获取显示设备所在的列车
        TrainEntity train = getTrainByDisplayPos(world, pos);
        if (train == null) {
            setDisplayContent(world, pos, "未连接到列车系统");
            return;
        }
        
        String trainId = train.getTrainId();
        
        // 获取或创建列车PIS信息
        TrainPISInfo trainPISInfo = getOrCreateTrainPISInfo(trainId);
        
        // 更新列车PIS信息
        updateTrainPISInfo(train, trainPISInfo);
        
        // 构建显示内容
        StringBuilder content = new StringBuilder();
        content.append("当前线路: " + train.getCurrentLine() + "\n");
        content.append("下一站: " + (train.getNextStation() != null ? train.getNextStation() : "未知") + "\n");
        content.append("终点站: " + train.getDestination() + "\n");
        content.append("开门方向: " + trainPISInfo.getDoorOpeningSide() + "\n");
        content.append("预计到站: " + trainPISInfo.getEstimatedArrivalTime() + "\n");
        
        setDisplayContent(world, pos, content.toString());
    }

    /**
     * 更新车站站台显示
     */
    private void updateStationPlatformDisplay(PISDisplayDevice device) {
        World world = device.getWorld();
        BlockPos pos = device.getPos();
        
        // 获取最近的车站
        String stationName = getNearestStation(world, pos);
        if (stationName == null) {
            setDisplayContent(world, pos, "未检测到车站");
            return;
        }
        
        // 获取即将到达该车站的列车信息
        List<TrainEntity> arrivingTrains = getArrivingTrainsAtStation(world, stationName);
        
        StringBuilder content = new StringBuilder();
        content.append("车站: " + stationName + "\n\n");
        
        if (arrivingTrains.isEmpty()) {
            content.append("暂无列车即将到达");
        } else {
            for (TrainEntity train : arrivingTrains) {
                TrainPISInfo trainPISInfo = getOrCreateTrainPISInfo(train.getTrainId());
                updateTrainPISInfo(train, trainPISInfo);
                
                content.append("列车: " + train.getTrainId() + "\n");
                content.append("终点站: " + train.getDestination() + "\n");
                content.append("预计到达: " + trainPISInfo.getEstimatedArrivalTime() + "\n");
                content.append("\n");
            }
        }
        
        setDisplayContent(world, pos, content.toString());
    }

    /**
     * 更新车站大厅显示
     */
    private void updateStationHallDisplay(PISDisplayDevice device) {
        World world = device.getWorld();
        BlockPos pos = device.getPos();
        
        // 获取最近的车站
        String stationName = getNearestStation(world, pos);
        if (stationName == null) {
            setDisplayContent(world, pos, "未检测到车站");
            return;
        }
        
        // 获取线路信息
        String lineId = getLineByStation(stationName);
        LineInfo lineInfo = lineInfoMap.get(lineId);
        
        StringBuilder content = new StringBuilder();
        content.append("车站: " + stationName + "\n");
        content.append("线路: " + (lineId != null ? lineId : "未知") + "\n\n");
        
        // 显示首末班车信息
        if (lineInfo != null) {
            content.append("首班车: " + lineInfo.getFirstTrainTime() + "\n");
            content.append("末班车: " + lineInfo.getLastTrainTime() + "\n\n");
            
            // 显示换乘信息
            if (!lineInfo.getTransferStations().isEmpty()) {
                content.append("换乘站: " + String.join(", ", lineInfo.getTransferStations()) + "\n");
            }
        }
        
        setDisplayContent(world, pos, content.toString());
    }

    /**
     * 更新动态地图显示
     */
    private void updateDynamicMapDisplay(PISDisplayDevice device) {
        World world = device.getWorld();
        BlockPos pos = device.getPos();
        
        // 获取显示设备所在的列车
        TrainEntity train = getTrainByDisplayPos(world, pos);
        if (train == null) {
            return;
        }
        
        String trainId = train.getTrainId();
        String currentLine = train.getCurrentLine();
        LineInfo lineInfo = lineInfoMap.get(currentLine);
        
        if (lineInfo == null || lineInfo.getStations().isEmpty()) {
            return;
        }
        
        // 创建动态地图显示内容
        StringBuilder mapContent = new StringBuilder();
        List<String> stations = lineInfo.getStations();
        String nextStation = train.getNextStation();
        
        for (String station : stations) {
            if (station.equals(train.getDestination())) {
                // 终点站显示为红色
                mapContent.append("[红]" + station + "[/红] ");
            } else if (station.equals(nextStation)) {
                // 下一站显示为橙色
                mapContent.append("[橙]" + station + "[/橙] ");
            } else if (isStationPassed(train, station)) {
                // 已过站显示为灰色
                mapContent.append("[灰]" + station + "[/灰] ");
            } else {
                // 前方站点显示为绿色
                mapContent.append("[绿]" + station + "[/绿] ");
            }
        }
        
        setDisplayContent(world, pos, mapContent.toString());
        
        // 同时显示列车位置指示器
        setMapTrainIndicator(world, pos, getTrainPositionOnMap(train, lineInfo));
    }

    /**
     * 更新多媒体显示
     */
    private void updateMediaDisplay(PISDisplayDevice device) {
        World world = device.getWorld();
        BlockPos pos = device.getPos();
        
        // 检查是否配置了媒体内容
        String mediaId = getConfiguredMedia(world, pos);
        if (mediaId == null || !mediaCache.containsKey(mediaId)) {
            // 如果没有配置媒体或媒体不存在，显示默认安全宣传内容
            playSafetyAnnouncement(world, pos);
            return;
        }
        
        // 获取媒体数据
        MediaContent mediaContent = mediaCache.get(mediaId);
        
        // 根据媒体类型进行不同处理
        switch (mediaContent.getType()) {
            case VIDEO:
                displayVideoFrame(world, pos, (VideoContent) mediaContent);
                break;
            case IMAGE:
                displayImage(world, pos, (ImageContent) mediaContent);
                break;
            case TEXT:
                displayTextMedia(world, pos, (TextContent) mediaContent);
                break;
        }
    }

    /**
     * 更新拥挤度显示
     */
    private void updateCrowdLevelDisplay(PISDisplayDevice device) {
        World world = device.getWorld();
        BlockPos pos = device.getPos();
        
        // 获取显示设备所在的列车
        TrainEntity train = getTrainByDisplayPos(world, pos);
        if (train == null || train.getConsist() == null) {
            setDisplayContent(world, pos, "拥挤度: 未知");
            return;
        }
        
        // 计算并显示拥挤度
        int carIndex = getCarIndexByDisplayPos(world, pos);
        if (carIndex >= 0) {
            CrowdLevel crowdLevel = calculateCarCrowdLevel(train, carIndex);
            String crowdLevelText = "车厢 " + (carIndex + 1) + ": " + getCrowdLevelText(crowdLevel);
            setDisplayContent(world, pos, crowdLevelText);
            // 设置显示颜色，拥挤时显示红色，舒适时显示绿色
            setDisplayColor(world, pos, crowdLevel.getColor());
        }
    }

    /**
     * 显示紧急信息
     */
    private void showEmergencyMessage(PISDisplayDevice device) {
        World world = device.getWorld();
        BlockPos pos = device.getPos();
        
        if (currentEmergencyMessage != null) {
            // 红底黄字显示紧急信息
            setDisplayColor(world, pos, "RED");
            setTextColor(world, pos, "YELLOW");
            setDisplayContent(world, pos, "紧急通知\n" + currentEmergencyMessage.getMessage());
            
            // 播放紧急语音提示
            if (currentEmergencyMessage.hasAudio()) {
                playEmergencyAudio(world, pos, currentEmergencyMessage.getAudioId());
            }
        }
    }

    /**
     * 处理紧急信息队列
     */
    private void processEmergencyMessages() {
        if (currentEmergencyMessage != null) {
            // 检查当前紧急信息是否已过期
            if (System.currentTimeMillis() > currentEmergencyMessage.getExpiryTime()) {
                currentEmergencyMessage = null;
            }
        } else if (!emergencyMessages.isEmpty()) {
            // 如果当前没有活动的紧急信息，从队列中取出一个
            currentEmergencyMessage = emergencyMessages.poll();
        }
    }

    /**
     * 添加紧急信息
     */
    public void addEmergencyMessage(String message, long durationMillis, boolean hasAudio, String audioId) {
        long expiryTime = System.currentTimeMillis() + durationMillis;
        EmergencyMessage emergencyMessage = new EmergencyMessage(message, expiryTime, hasAudio, audioId);
        emergencyMessages.offer(emergencyMessage);
        LogSystem.systemLog("添加紧急信息: " + message);
    }

    /**
     * 清除所有紧急信息
     */
    public void clearEmergencyMessages() {
        emergencyMessages.clear();
        currentEmergencyMessage = null;
        LogSystem.systemLog("已清除所有紧急信息");
    }

    /**
     * 添加视频内容到缓存
     */
    public void addVideoContent(String videoId, byte[] videoData, int frameCount, int fps, String title) {
        VideoContent videoContent = new VideoContent(videoId, videoData, frameCount, 1000 / fps, title);
        mediaCache.put(videoId, videoContent);
        LogSystem.systemLog("视频内容已添加到缓存: " + videoId + ", 标题: " + title);
    }

    /**
     * 添加图片内容到缓存
     */
    public void addImageContent(String imageId, byte[] imageData, String title) {
        ImageContent imageContent = new ImageContent(imageId, imageData, title);
        mediaCache.put(imageId, imageContent);
        LogSystem.systemLog("图片内容已添加到缓存: " + imageId + ", 标题: " + title);
    }

    /**
     * 添加文本内容到缓存
     */
    public void addTextContent(String textId, String content, String title) {
        TextContent textContent = new TextContent(textId, content, title);
        mediaCache.put(textId, textContent);
        LogSystem.systemLog("文本内容已添加到缓存: " + textId + ", 标题: " + title);
    }

    /**
     * 添加线路信息
     */
    public void addLineInfo(String lineId, List<String> stations, String firstTrainTime, String lastTrainTime, List<String> transferStations) {
        LineInfo lineInfo = new LineInfo(lineId, stations, firstTrainTime, lastTrainTime, transferStations);
        lineInfoMap.put(lineId, lineInfo);
        LogSystem.systemLog("线路信息已添加: " + lineId);
    }

    /**
     * 获取或创建列车PIS信息
     */
    private TrainPISInfo getOrCreateTrainPISInfo(String trainId) {
        return trainPISInfoMap.computeIfAbsent(trainId, k -> new TrainPISInfo(trainId));
    }

    /**
     * 更新列车PIS信息
     */
    private void updateTrainPISInfo(TrainEntity train, TrainPISInfo info) {
        // 更新开门方向
        info.setDoorOpeningSide(determineDoorOpeningSide(train));
        
        // 计算预计到站时间
        String estimatedTime = calculateEstimatedArrivalTime(train);
        info.setEstimatedArrivalTime(estimatedTime);
        
        // 更新列车位置信息
        info.setCurrentPosition(train.getX(), train.getY(), train.getZ());
    }

    /**
     * 确定车门开启方向
     */
    private String determineDoorOpeningSide(TrainEntity train) {
        // 这里应该根据车站布局和列车位置来确定开门方向
        // 简化处理，返回"左侧"或"右侧"
        return train.getId() % 2 == 0 ? "左侧" : "右侧";
    }

    /**
     * 计算预计到站时间
     */
    private String calculateEstimatedArrivalTime(TrainEntity train) {
        // 实际实现中应该基于列车当前位置、速度和下一站距离来计算
        // 简化处理，返回一个示例时间
        if (train.getCurrentSpeed() > 0) {
            return "约3分钟";
        } else {
            return "即将到达";
        }
    }

    /**
     * 计算车厢拥挤度
     */
    private CrowdLevel calculateCarCrowdLevel(TrainEntity train, int carIndex) {
        // 获取车厢乘客数
        int passengersInCar = getPassengersInCar(train, carIndex);
        
        // 结合空气弹簧压力数据修正拥挤度计算
        double springPressure = getCarSpringPressure(train, carIndex);
        
        // 根据乘客数量和弹簧压力确定拥挤度
        double crowdFactor = passengersInCar * 0.8 + springPressure * 0.2;
        
        if (crowdFactor > 80) {
            return CrowdLevel.EXCESSIVE;
        } else if (crowdFactor > 60) {
            return CrowdLevel.CROWDED;
        } else if (crowdFactor > 30) {
            return CrowdLevel.MODERATE;
        } else {
            return CrowdLevel.COMFORTABLE;
        }
    }

    /**
     * 获取显示拥挤度文本
     */
    private String getCrowdLevelText(CrowdLevel level) {
        switch (level) {
            case EXCESSIVE:
                return "严重拥挤";
            case CROWDED:
                return "拥挤";
            case MODERATE:
                return "一般";
            case COMFORTABLE:
                return "舒适";
            default:
                return "未知";
        }
    }

    /**
     * 播放安全宣传内容
     */
    private void playSafetyAnnouncement(World world, BlockPos pos) {
        // 在实际实现中，这里应该播放安全宣传视频或显示安全提示信息
        setDisplayContent(world, pos, "安全提示: 请站稳扶好，保管好随身物品，不要倚靠车门。");
    }

    /**
     * 显示视频帧
     */
    private void displayVideoFrame(World world, BlockPos pos, VideoContent videoContent) {
        // 检查视频是否需要更新帧
        if (System.currentTimeMillis() - videoContent.getLastFrameUpdate() > videoContent.getFrameDelay()) {
            videoContent.updateCurrentFrameIndex();
            videoContent.updateLastFrameUpdate();
        }
        
        // 显示当前帧
        // 在实际实现中，这里应该根据视频数据和帧索引显示对应的视频帧
        setDisplayContent(world, pos, videoContent.getTitle() + " (帧: " + videoContent.getCurrentFrameIndex() + ")");
    }

    /**
     * 显示图片
     */
    private void displayImage(World world, BlockPos pos, ImageContent imageContent) {
        // 在实际实现中，这里应该显示图片内容
        setDisplayContent(world, pos, "图片: " + imageContent.getTitle());
    }

    /**
     * 显示文本媒体
     */
    private void displayTextMedia(World world, BlockPos pos, TextContent textContent) {
        setDisplayContent(world, pos, textContent.getTitle() + "\n" + textContent.getContent());
    }

    /**
     * 播放紧急音频
     */
    private void playEmergencyAudio(World world, BlockPos pos, String audioId) {
        // 在实际实现中，这里应该播放紧急音频
        LogSystem.systemLog("播放紧急音频: " + audioId + " 在位置: " + pos);
    }

    /**
     * 获取配置的媒体ID
     */
    private String getConfiguredMedia(World world, BlockPos pos) {
        // 在实际实现中，这里应该从方块的NBT数据中获取配置的媒体ID
        // 这里简化处理，返回一个默认的媒体ID
        return "default_media";
    }

    /**
     * 设置显示内容
     */
    private void setDisplayContent(World world, BlockPos pos, String content) {
        // 在实际实现中，这里应该更新方块的状态或NBT数据来显示内容
        LogSystem.systemLog("更新PIS显示内容: " + pos + ", 内容: " + content);
    }

    /**
     * 设置显示颜色
     */
    private void setDisplayColor(World world, BlockPos pos, String color) {
        // 在实际实现中，这里应该更新方块的状态或NBT数据来设置显示颜色
        LogSystem.systemLog("设置PIS显示颜色: " + pos + ", 颜色: " + color);
    }

    /**
     * 设置文本颜色
     */
    private void setTextColor(World world, BlockPos pos, String color) {
        // 在实际实现中，这里应该更新方块的状态或NBT数据来设置文本颜色
        LogSystem.systemLog("设置PIS文本颜色: " + pos + ", 颜色: " + color);
    }

    /**
     * 设置地图列车指示器
     */
    private void setMapTrainIndicator(World world, BlockPos pos, int position) {
        // 在实际实现中，这里应该更新动态地图上的列车位置指示器
        LogSystem.systemLog("设置地图列车指示器位置: " + pos + ", 位置: " + position);
    }

    /**
     * 根据显示设备位置获取列车
     */
    private TrainEntity getTrainByDisplayPos(World world, BlockPos pos) {
        // 在实际实现中，这里应该根据显示设备的位置找到对应的列车
        // 简化处理，返回null
        return null;
    }

    /**
     * 获取最近的车站
     */
    private String getNearestStation(World world, BlockPos pos) {
        // 在实际实现中，这里应该从调度系统获取所有车站，并计算距离
        // 简化处理，返回一个示例车站名
        return "中央车站";
    }

    /**
     * 获取即将到达指定车站的列车列表
     */
    private List<TrainEntity> getArrivingTrainsAtStation(World world, String stationName) {
        // 在实际实现中，这里应该从调度系统获取即将到达该车站的列车列表
        // 简化处理，返回空列表
        return new ArrayList<>();
    }

    /**
     * 获取车站所属的线路
     */
    private String getLineByStation(String stationName) {
        // 在实际实现中，这里应该根据车站名找到所属的线路
        // 简化处理，返回一个示例线路ID
        return "红线";
    }

    /**
     * 检查车站是否已经通过
     */
    private boolean isStationPassed(TrainEntity train, String stationName) {
        // 在实际实现中，这里应该根据列车的行驶方向和当前位置判断车站是否已经通过
        // 简化处理，返回false
        return false;
    }

    /**
     * 获取列车在地图上的位置
     */
    private int getTrainPositionOnMap(TrainEntity train, LineInfo lineInfo) {
        // 在实际实现中，这里应该根据列车的实际位置计算在地图上的相对位置
        // 简化处理，返回0
        return 0;
    }

    /**
     * 根据显示设备位置获取车厢索引
     */
    private int getCarIndexByDisplayPos(World world, BlockPos pos) {
        // 在实际实现中，这里应该根据显示设备的位置找到对应的车厢索引
        // 简化处理，返回0
        return 0;
    }

    /**
     * 获取车厢内的乘客数
     */
    private int getPassengersInCar(TrainEntity train, int carIndex) {
        // 在实际实现中，这里应该获取指定车厢内的乘客数量
        // 简化处理，返回一个随机数
        return new Random().nextInt(100);
    }

    /**
     * 获取车厢空气弹簧压力
     */
    private double getCarSpringPressure(TrainEntity train, int carIndex) {
        // 在实际实现中，这里应该从列车系统获取空气弹簧压力数据
        // 简化处理，返回一个随机值
        return new Random().nextDouble() * 100;
    }

    /**
     * PIS显示设备类
     */
    private static class PISDisplayDevice {
        private final World world;
        private final BlockPos pos;
        private final PISDisplayType type;

        public PISDisplayDevice(World world, BlockPos pos, PISDisplayType type) {
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

        public PISDisplayType getType() {
            return type;
        }

        public boolean isValid() {
            // 检查方块是否仍然存在且类型正确
            return world != null && world.isChunkLoaded(pos);
        }
    }

    /**
     * 列车PIS信息类
     */
    private static class TrainPISInfo {
        private final String trainId;
        private String doorOpeningSide;
        private String estimatedArrivalTime;
        private double x, y, z;
        private long lastUpdateTime;

        public TrainPISInfo(String trainId) {
            this.trainId = trainId;
            this.lastUpdateTime = System.currentTimeMillis();
        }

        public String getTrainId() {
            return trainId;
        }

        public String getDoorOpeningSide() {
            return doorOpeningSide;
        }

        public void setDoorOpeningSide(String doorOpeningSide) {
            this.doorOpeningSide = doorOpeningSide;
        }

        public String getEstimatedArrivalTime() {
            return estimatedArrivalTime;
        }

        public void setEstimatedArrivalTime(String estimatedArrivalTime) {
            this.estimatedArrivalTime = estimatedArrivalTime;
        }

        public void setCurrentPosition(double x, double y, double z) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.lastUpdateTime = System.currentTimeMillis();
        }

        public long getLastUpdateTime() {
            return lastUpdateTime;
        }
    }

    /**
     * 线路信息类
     */
    private static class LineInfo {
        private final String lineId;
        private final List<String> stations;
        private final String firstTrainTime;
        private final String lastTrainTime;
        private final List<String> transferStations;

        public LineInfo(String lineId, List<String> stations, String firstTrainTime, String lastTrainTime, List<String> transferStations) {
            this.lineId = lineId;
            this.stations = new ArrayList<>(stations);
            this.firstTrainTime = firstTrainTime;
            this.lastTrainTime = lastTrainTime;
            this.transferStations = new ArrayList<>(transferStations);
        }

        public String getLineId() {
            return lineId;
        }

        public List<String> getStations() {
            return stations;
        }

        public String getFirstTrainTime() {
            return firstTrainTime;
        }

        public String getLastTrainTime() {
            return lastTrainTime;
        }

        public List<String> getTransferStations() {
            return transferStations;
        }
    }

    /**
     * 媒体内容基类
     */
    private static abstract class MediaContent {
        private final String id;
        private final String title;
        private final MediaType type;

        public MediaContent(String id, String title, MediaType type) {
            this.id = id;
            this.title = title;
            this.type = type;
        }

        public String getId() {
            return id;
        }

        public String getTitle() {
            return title;
        }

        public MediaType getType() {
            return type;
        }
    }

    /**
     * 视频内容类
     */
    private static class VideoContent extends MediaContent {
        private final byte[] data;
        private final int frameCount;
        private final int frameDelay;
        private int currentFrameIndex = 0;
        private long lastFrameUpdate = System.currentTimeMillis();

        public VideoContent(String id, byte[] data, int frameCount, int frameDelay, String title) {
            super(id, title, MediaType.VIDEO);
            this.data = data;
            this.frameCount = frameCount;
            this.frameDelay = frameDelay;
        }

        public byte[] getData() {
            return data;
        }

        public int getFrameCount() {
            return frameCount;
        }

        public int getFrameDelay() {
            return frameDelay;
        }

        public int getCurrentFrameIndex() {
            return currentFrameIndex;
        }

        public void updateCurrentFrameIndex() {
            this.currentFrameIndex = (this.currentFrameIndex + 1) % this.frameCount;
        }

        public long getLastFrameUpdate() {
            return lastFrameUpdate;
        }

        public void updateLastFrameUpdate() {
            this.lastFrameUpdate = System.currentTimeMillis();
        }
    }

    /**
     * 图片内容类
     */
    private static class ImageContent extends MediaContent {
        private final byte[] data;

        public ImageContent(String id, byte[] data, String title) {
            super(id, title, MediaType.IMAGE);
            this.data = data;
        }

        public byte[] getData() {
            return data;
        }
    }

    /**
     * 文本内容类
     */
    private static class TextContent extends MediaContent {
        private final String content;

        public TextContent(String id, String content, String title) {
            super(id, title, MediaType.TEXT);
            this.content = content;
        }

        public String getContent() {
            return content;
        }
    }

    /**
     * 紧急信息类
     */
    private static class EmergencyMessage {
        private final String message;
        private final long expiryTime;
        private final boolean hasAudio;
        private final String audioId;

        public EmergencyMessage(String message, long expiryTime, boolean hasAudio, String audioId) {
            this.message = message;
            this.expiryTime = expiryTime;
            this.hasAudio = hasAudio;
            this.audioId = audioId;
        }

        public String getMessage() {
            return message;
        }

        public long getExpiryTime() {
            return expiryTime;
        }

        public boolean hasAudio() {
            return hasAudio;
        }

        public String getAudioId() {
            return audioId;
        }
    }

    /**
     * PIS显示类型枚举
     */
    public enum PISDisplayType {
        TRAIN_INTERIOR_DISPLAY,    // 列车内部显示屏
        STATION_PLATFORM_DISPLAY,  // 车站站台显示屏
        STATION_HALL_DISPLAY,      // 车站大厅显示屏
        DYNAMIC_MAP_DISPLAY,       // 动态地图显示屏
        MEDIA_DISPLAY,             // 媒体显示屏
        CROWD_LEVEL_DISPLAY        // 拥挤度显示屏
    }

    /**
     * 媒体类型枚举
     */
    private enum MediaType {
        VIDEO,
        IMAGE,
        TEXT
    }

    /**
     * 拥挤度枚举
     */
    private enum CrowdLevel {
        EXCESSIVE("RED"),
        CROWDED("ORANGE"),
        MODERATE("YELLOW"),
        COMFORTABLE("GREEN");

        private final String color;

        CrowdLevel(String color) {
            this.color = color;
        }

        public String getColor() {
            return color;
        }
    }
}