package com.krt.api;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Properties;

/**
 * KRT模组Fabric版本配置类
 * 管理模组的配置选项
 */
public class KRTConfigFabric {
    // 配置文件路径
    private static final String CONFIG_FILE_PATH = "config/krt.properties";
    
    // 配置属性
    private Properties properties = new Properties();
    
    // 默认配置值
    private static final boolean DEFAULT_ENABLE_SIGNAL_SYSTEM = true;
    private static final boolean DEFAULT_ENABLE_ATC = true;
    private static final boolean DEFAULT_ENABLE_AI_SUGGESTIONS = true;
    private static final boolean DEFAULT_GENERATE_EXAMPLE_DATA = true;
    private static final int DEFAULT_MAX_TRAINS_PER_LINE = 10;
    private static final int DEFAULT_INITIAL_TRAIN_HEALTH = 100;
    private static final int DEFAULT_DERAILMENT_THRESHOLD = 30;
    private static final boolean DEFAULT_ENABLE_CUSTOM_AUDIO = true;
    private static final double DEFAULT_TRAIN_SOUND_VOLUME = 0.8;
    private static final double DEFAULT_ENVIRONMENT_SOUND_VOLUME = 0.5;
    private static final boolean DEFAULT_DEBUG_MODE = false;
    private static final int DEFAULT_DEBUG_LEVEL = 0;

    /**
     * 构造函数
     */
    public KRTConfigFabric() {
        // 初始化默认配置
        setDefaultProperties();
    }

    /**
     * 设置默认配置属性
     */
    private void setDefaultProperties() {
        properties.setProperty("enableSignalSystem", String.valueOf(DEFAULT_ENABLE_SIGNAL_SYSTEM));
        properties.setProperty("enableATC", String.valueOf(DEFAULT_ENABLE_ATC));
        properties.setProperty("enableAISuggestions", String.valueOf(DEFAULT_ENABLE_AI_SUGGESTIONS));
        properties.setProperty("generateExampleData", String.valueOf(DEFAULT_GENERATE_EXAMPLE_DATA));
        properties.setProperty("maxTrainsPerLine", String.valueOf(DEFAULT_MAX_TRAINS_PER_LINE));
        properties.setProperty("initialTrainHealth", String.valueOf(DEFAULT_INITIAL_TRAIN_HEALTH));
        properties.setProperty("derailmentThreshold", String.valueOf(DEFAULT_DERAILMENT_THRESHOLD));
        properties.setProperty("enableCustomAudio", String.valueOf(DEFAULT_ENABLE_CUSTOM_AUDIO));
        properties.setProperty("trainSoundVolume", String.valueOf(DEFAULT_TRAIN_SOUND_VOLUME));
        properties.setProperty("environmentSoundVolume", String.valueOf(DEFAULT_ENVIRONMENT_SOUND_VOLUME));
        properties.setProperty("debugMode", String.valueOf(DEFAULT_DEBUG_MODE));
        properties.setProperty("debugLevel", String.valueOf(DEFAULT_DEBUG_LEVEL));
    }

    /**
     * 加载配置文件
     */
    public void loadConfig() {
        File configFile = new File(CONFIG_FILE_PATH);
        if (configFile.exists()) {
            try (FileReader reader = new FileReader(configFile)) {
                properties.load(reader);
            } catch (IOException e) {
                e.printStackTrace();
                // 加载失败时使用默认配置
                setDefaultProperties();
            }
        } else {
            // 配置文件不存在，创建新文件并保存默认配置
            saveConfig();
        }
    }

    /**
     * 保存配置文件
     */
    public void saveConfig() {
        try {
            // 确保配置目录存在
            File configDir = new File("config");
            if (!configDir.exists()) {
                configDir.mkdirs();
            }
            
            // 保存配置文件
            File configFile = new File(CONFIG_FILE_PATH);
            try (FileWriter writer = new FileWriter(configFile)) {
                properties.store(writer, "KRT轨道交通模组配置文件");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * 是否启用信号系统
     */
    public boolean isSignalSystemEnabled() {
        return Boolean.parseBoolean(properties.getProperty("enableSignalSystem"));
    }

    /**
     * 设置是否启用信号系统
     */
    public void setSignalSystemEnabled(boolean enabled) {
        properties.setProperty("enableSignalSystem", String.valueOf(enabled));
        saveConfig();
    }

    /**
     * 是否启用ATC系统
     */
    public boolean isATCEnabled() {
        return Boolean.parseBoolean(properties.getProperty("enableATC"));
    }

    /**
     * 设置是否启用ATC系统
     */
    public void setATCEnabled(boolean enabled) {
        properties.setProperty("enableATC", String.valueOf(enabled));
        saveConfig();
    }

    /**
     * 是否启用AI调度建议
     */
    public boolean isAISuggestionsEnabled() {
        return Boolean.parseBoolean(properties.getProperty("enableAISuggestions"));
    }

    /**
     * 设置是否启用AI调度建议
     */
    public void setAISuggestionsEnabled(boolean enabled) {
        properties.setProperty("enableAISuggestions", String.valueOf(enabled));
        saveConfig();
    }

    /**
     * 是否生成示例数据
     */
    public boolean isGenerateExampleData() {
        return Boolean.parseBoolean(properties.getProperty("generateExampleData"));
    }

    /**
     * 设置是否生成示例数据
     */
    public void setGenerateExampleData(boolean generate) {
        properties.setProperty("generateExampleData", String.valueOf(generate));
        saveConfig();
    }

    /**
     * 获取每条线路的最大列车数量
     */
    public int getMaxTrainsPerLine() {
        return Integer.parseInt(properties.getProperty("maxTrainsPerLine"));
    }

    /**
     * 设置每条线路的最大列车数量
     */
    public void setMaxTrainsPerLine(int maxTrains) {
        properties.setProperty("maxTrainsPerLine", String.valueOf(maxTrains));
        saveConfig();
    }

    /**
     * 获取列车初始健康值
     */
    public int getInitialTrainHealth() {
        return Integer.parseInt(properties.getProperty("initialTrainHealth"));
    }

    /**
     * 设置列车初始健康值
     */
    public void setInitialTrainHealth(int health) {
        properties.setProperty("initialTrainHealth", String.valueOf(health));
        saveConfig();
    }

    /**
     * 获取列车脱轨阈值
     */
    public int getDerailmentThreshold() {
        return Integer.parseInt(properties.getProperty("derailmentThreshold"));
    }

    /**
     * 设置列车脱轨阈值
     */
    public void setDerailmentThreshold(int threshold) {
        properties.setProperty("derailmentThreshold", String.valueOf(threshold));
        saveConfig();
    }

    /**
     * 是否启用自定义音频
     */
    public boolean isCustomAudioEnabled() {
        return Boolean.parseBoolean(properties.getProperty("enableCustomAudio"));
    }

    /**
     * 设置是否启用自定义音频
     */
    public void setCustomAudioEnabled(boolean enabled) {
        properties.setProperty("enableCustomAudio", String.valueOf(enabled));
        saveConfig();
    }

    /**
     * 获取列车音效音量
     */
    public double getTrainSoundVolume() {
        return Double.parseDouble(properties.getProperty("trainSoundVolume"));
    }

    /**
     * 设置列车音效音量
     */
    public void setTrainSoundVolume(double volume) {
        properties.setProperty("trainSoundVolume", String.valueOf(volume));
        saveConfig();
    }

    /**
     * 获取环境音效音量
     */
    public double getEnvironmentSoundVolume() {
        return Double.parseDouble(properties.getProperty("environmentSoundVolume"));
    }

    /**
     * 设置环境音效音量
     */
    public void setEnvironmentSoundVolume(double volume) {
        properties.setProperty("environmentSoundVolume", String.valueOf(volume));
        saveConfig();
    }

    /**
     * 是否启用调试模式
     */
    public boolean isDebugMode() {
        return Boolean.parseBoolean(properties.getProperty("debugMode"));
    }

    /**
     * 设置是否启用调试模式
     */
    public void setDebugMode(boolean debug) {
        properties.setProperty("debugMode", String.valueOf(debug));
        saveConfig();
    }

    /**
     * 获取调试级别
     */
    public int getDebugLevel() {
        return Integer.parseInt(properties.getProperty("debugLevel"));
    }

    /**
     * 设置调试级别
     */
    public void setDebugLevel(int level) {
        properties.setProperty("debugLevel", String.valueOf(level));
        saveConfig();
    }

    /**
     * 获取所有配置属性
     */
    public Properties getProperties() {
        return (Properties) properties.clone();
    }

    /**
     * 设置配置属性
     */
    public void setProperty(String key, String value) {
        properties.setProperty(key, value);
        saveConfig();
    }

    /**
     * 获取配置属性
     */
    public String getProperty(String key, String defaultValue) {
        return properties.getProperty(key, defaultValue);
    }
}