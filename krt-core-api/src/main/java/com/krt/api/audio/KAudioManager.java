package com.krt.api.audio;

import com.krt.api.KRTConfig;
import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * KRT轨道交通模组音频管理系统
 * 负责管理列车音效、环境音效和自定义音频
 */
public class KAudioManager {
    // 音频类型枚举
    public enum AudioType {
        TRAIN_MOVEMENT, // 列车走行音
        TRAIN_HORN, // 列车鸣笛
        TRAIN_BELL, // 列车铃音
        DOOR_OPEN, // 车门开启
        DOOR_CLOSE, // 车门关闭
        SIGNAL_BEEP, // 信号提示音
        ENVIRONMENT_AMBIENT, // 环境背景音
        STATION_ANNOUNCEMENT // 车站广播
    }
    
    // 单例实例
    private static KAudioManager instance;
    
    // 音频资源映射
    private Map<String, AudioResource> audioResources = new HashMap<>();
    
    // 自定义音频目录
    private static final String CUSTOM_AUDIO_DIR = "krt/audio";
    
    // 配置引用
    private KRTConfig config;
    
    /**
     * 音频资源类
     */
    public static class AudioResource {
        private String id;
        private String name;
        private String filePath;
        private AudioType type;
        private boolean isCustom;
        
        public AudioResource(String id, String name, String filePath, AudioType type, boolean isCustom) {
            this.id = id;
            this.name = name;
            this.filePath = filePath;
            this.type = type;
            this.isCustom = isCustom;
        }
        
        public String getId() { return id; }
        public String getName() { return name; }
        public String getFilePath() { return filePath; }
        public AudioType getType() { return type; }
        public boolean isCustom() { return isCustom; }
    }
    
    /**
     * 获取音频管理器单例实例
     */
    public static synchronized KAudioManager getInstance() {
        if (instance == null) {
            instance = new KAudioManager();
        }
        return instance;
    }
    
    /**
     * 初始化音频管理器
     */
    public void initialize(KRTConfig config) {
        this.config = config;
        
        // 初始化默认音频资源
        initializeDefaultAudioResources();
        
        // 扫描自定义音频文件
        if (config.isCustomAudioEnabled()) {
            scanCustomAudioFiles();
        }
    }
    
    /**
     * 初始化默认音频资源
     */
    private void initializeDefaultAudioResources() {
        // 注册默认列车走行音
        registerAudioResource("train_movement_default", "默认列车走行音", "assets/krt/audio/train/movement.ogg", AudioType.TRAIN_MOVEMENT, false);
        
        // 注册默认列车鸣笛
        registerAudioResource("train_horn_default", "默认列车鸣笛", "assets/krt/audio/train/horn.ogg", AudioType.TRAIN_HORN, false);
        
        // 注册默认列车铃音
        registerAudioResource("train_bell_default", "默认列车铃音", "assets/krt/audio/train/bell.ogg", AudioType.TRAIN_BELL, false);
        
        // 注册默认车门开启音效
        registerAudioResource("door_open_default", "默认车门开启", "assets/krt/audio/door/open.ogg", AudioType.DOOR_OPEN, false);
        
        // 注册默认车门关闭音效
        registerAudioResource("door_close_default", "默认车门关闭", "assets/krt/audio/door/close.ogg", AudioType.DOOR_CLOSE, false);
        
        // 注册默认信号提示音
        registerAudioResource("signal_beep_default", "默认信号提示音", "assets/krt/audio/signal/beep.ogg", AudioType.SIGNAL_BEEP, false);
        
        // 注册默认环境背景音
        registerAudioResource("environment_ambient_default", "默认环境背景音", "assets/krt/audio/environment/ambient.ogg", AudioType.ENVIRONMENT_AMBIENT, false);
        
        // 注册默认车站广播
        registerAudioResource("station_announcement_default", "默认车站广播", "assets/krt/audio/announcement/default.ogg", AudioType.STATION_ANNOUNCEMENT, false);
    }
    
    /**
     * 扫描自定义音频文件
     */
    private void scanCustomAudioFiles() {
        // 创建自定义音频目录（如果不存在）
        File customAudioDir = new File(CUSTOM_AUDIO_DIR);
        if (!customAudioDir.exists()) {
            customAudioDir.mkdirs();
        }
        
        // 扫描子目录
        scanAudioDirectory(new File(customAudioDir, "train"), AudioType.TRAIN_MOVEMENT);
        scanAudioDirectory(new File(customAudioDir, "door"), AudioType.DOOR_OPEN);
        scanAudioDirectory(new File(customAudioDir, "signal"), AudioType.SIGNAL_BEEP);
        scanAudioDirectory(new File(customAudioDir, "environment"), AudioType.ENVIRONMENT_AMBIENT);
        scanAudioDirectory(new File(customAudioDir, "announcement"), AudioType.STATION_ANNOUNCEMENT);
    }
    
    /**
     * 扫描指定目录中的音频文件
     */
    private void scanAudioDirectory(File directory, AudioType defaultType) {
        if (!directory.exists() || !directory.isDirectory()) {
            return;
        }
        
        File[] files = directory.listFiles();
        if (files == null) {
            return;
        }
        
        for (File file : files) {
            if (file.isFile() && (file.getName().endsWith(".ogg") || file.getName().endsWith(".wav") || file.getName().endsWith(".mp3"))) {
                // 根据文件名和目录推断音频类型
                AudioType type = defaultType;
                String fileName = file.getName();
                String baseName = fileName.substring(0, fileName.lastIndexOf('.'));
                
                // 根据文件名调整类型
                if (fileName.contains("horn")) {
                    type = AudioType.TRAIN_HORN;
                } else if (fileName.contains("bell")) {
                    type = AudioType.TRAIN_BELL;
                } else if (fileName.contains("close")) {
                    type = AudioType.DOOR_CLOSE;
                }
                
                // 注册自定义音频
                String id = "custom_" + baseName.toLowerCase().replace(" ", "_");
                registerAudioResource(id, baseName, file.getAbsolutePath(), type, true);
            }
        }
    }
    
    /**
     * 注册音频资源
     */
    public void registerAudioResource(String id, String name, String filePath, AudioType type, boolean isCustom) {
        audioResources.put(id, new AudioResource(id, name, filePath, type, isCustom));
    }
    
    /**
     * 获取音频资源
     */
    public AudioResource getAudioResource(String id) {
        return audioResources.get(id);
    }
    
    /**
     * 获取指定类型的所有音频资源
     */
    public List<AudioResource> getAudioResourcesByType(AudioType type) {
        List<AudioResource> result = new ArrayList<>();
        
        for (AudioResource resource : audioResources.values()) {
            if (resource.getType() == type) {
                result.add(resource);
            }
        }
        
        return result;
    }
    
    /**
     * 播放音频
     */
    public void playAudio(String audioId, double volume, double x, double y, double z) {
        if (!config.isCustomAudioEnabled()) {
            return;
        }
        
        AudioResource resource = audioResources.get(audioId);
        if (resource == null) {
            return;
        }
        
        // 这里应该是实际播放音频的逻辑
        // 在实际实现中，需要调用Minecraft的音频API
        // 这里仅作占位符
        System.out.println("Playing audio: " + resource.getName() + " at volume: " + volume + " at position: (" + x + ", " + y + ", " + z + ")");
        
        // 应用音量设置
        double finalVolume = volume;
        if (resource.getType() == AudioType.TRAIN_MOVEMENT || resource.getType() == AudioType.TRAIN_HORN || resource.getType() == AudioType.TRAIN_BELL) {
            finalVolume *= config.getTrainSoundVolume();
        } else {
            finalVolume *= config.getEnvironmentSoundVolume();
        }
        
        // 实际播放音频的代码应该在这里
    }
    
    /**
     * 播放列车音效
     */
    public void playTrainSound(String trainId, AudioType type, double x, double y, double z) {
        // 默认使用类型对应的默认音频
        String defaultAudioId;
        
        switch (type) {
            case TRAIN_MOVEMENT:
                defaultAudioId = "train_movement_default";
                break;
            case TRAIN_HORN:
                defaultAudioId = "train_horn_default";
                break;
            case TRAIN_BELL:
                defaultAudioId = "train_bell_default";
                break;
            default:
                return;
        }
        
        // 播放音频
        playAudio(defaultAudioId, 1.0, x, y, z);
    }
    
    /**
     * 播放道岔声音
     */
    public void playSwitchSound(double x, double y, double z) {
        // 检查是否启用自定义音频
        if (!config.isCustomAudioEnabled()) {
            return;
        }
        
        // 这里应该播放道岔声音
        // 可以使用一个专门的道岔音频，或者使用信号提示音作为替代
        String audioId = "signal_beep_default";
        playAudio(audioId, 1.0, x, y, z);
    }
    
    /**
     * 播放车站广播
     */
    public void playStationAnnouncement(String stationId, double x, double y, double z) {
        // 检查是否启用自定义音频
        if (!config.isCustomAudioEnabled()) {
            return;
        }
        
        // 默认使用车站广播音频
        String audioId = "station_announcement_default";
        
        // 查找是否有针对特定车站的自定义广播
        String stationSpecificId = "custom_announcement_station_" + stationId.toLowerCase();
        if (audioResources.containsKey(stationSpecificId)) {
            audioId = stationSpecificId;
        }
        
        playAudio(audioId, 1.0, x, y, z);
    }
    
    /**
     * 停止所有音频播放
     */
    public void stopAllAudio() {
        // 这里应该是停止所有音频的逻辑
        System.out.println("Stopping all audio");
    }
    
    /**
     * 刷新音频资源
     */
    public void refreshAudioResources() {
        // 清除现有资源
        audioResources.clear();
        
        // 重新初始化
        initializeDefaultAudioResources();
        
        if (config.isCustomAudioEnabled()) {
            scanCustomAudioFiles();
        }
    }
    
    /**
     * 检查音频文件是否存在
     */
    public boolean audioFileExists(String filePath) {
        return new File(filePath).exists();
    }
    
    /**
     * 获取音频统计信息
     */
    public String getAudioStatistics() {
        StringBuilder stats = new StringBuilder();
        
        // 总音频资源数
        stats.append("总音频资源数: " + audioResources.size() + "\n");
        
        // 按类型统计
        Map<AudioType, Integer> typeCount = new HashMap<>();
        for (AudioResource resource : audioResources.values()) {
            AudioType type = resource.getType();
            typeCount.put(type, typeCount.getOrDefault(type, 0) + 1);
        }
        
        for (Map.Entry<AudioType, Integer> entry : typeCount.entrySet()) {
            stats.append("- " + entry.getKey().name() + ": " + entry.getValue() + "\n");
        }
        
        // 自定义音频数量
        int customCount = 0;
        for (AudioResource resource : audioResources.values()) {
            if (resource.isCustom()) {
                customCount++;
            }
        }
        
        stats.append("自定义音频数量: " + customCount + "\n");
        
        // 自定义音频目录状态
        File customDir = new File(CUSTOM_AUDIO_DIR);
        stats.append("自定义音频目录存在: " + customDir.exists() + "\n");
        
        return stats.toString();
    }
    
    /**
     * 创建音频文件夹结构
     */
    public void createAudioDirectoryStructure() {
        try {
            // 创建主目录
            Path mainDir = Paths.get(CUSTOM_AUDIO_DIR);
            Files.createDirectories(mainDir);
            
            // 创建子目录
            Files.createDirectories(mainDir.resolve("train"));
            Files.createDirectories(mainDir.resolve("door"));
            Files.createDirectories(mainDir.resolve("signal"));
            Files.createDirectories(mainDir.resolve("environment"));
            Files.createDirectories(mainDir.resolve("announcement"));
            
            System.out.println("音频文件夹结构创建成功: " + CUSTOM_AUDIO_DIR);
        } catch (IOException e) {
            e.printStackTrace();
            System.err.println("创建音频文件夹结构失败: " + e.getMessage());
        }
    }
    
    /**
     * 获取自定义音频目录路径
     */
    public String getCustomAudioDirectory() {
        return CUSTOM_AUDIO_DIR;
    }
}