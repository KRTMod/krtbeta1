package com.krt.api;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * KRT模组配置类
 * 管理模组的配置选项
 */
public class KRTConfig {
    // 配置规范
    public static final ForgeConfigSpec COMMON_CONFIG;
    public static final ForgeConfigSpec CLIENT_CONFIG;
    
    // 通用配置
    private static final ForgeConfigSpec.BooleanValue ENABLE_SIGNAL_SYSTEM;
    private static final ForgeConfigSpec.BooleanValue ENABLE_ATC;
    private static final ForgeConfigSpec.BooleanValue ENABLE_AI_SUGGESTIONS;
    private static final ForgeConfigSpec.BooleanValue GENERATE_EXAMPLE_DATA;
    private static final ForgeConfigSpec.IntValue MAX_TRAINS_PER_LINE;
    private static final ForgeConfigSpec.IntValue INITIAL_TRAIN_HEALTH;
    private static final ForgeConfigSpec.IntValue DERAILMENT_THRESHOLD;
    
    // 音频配置
    private static final ForgeConfigSpec.BooleanValue ENABLE_CUSTOM_AUDIO;
    private static final ForgeConfigSpec.DoubleValue TRAIN_SOUND_VOLUME;
    private static final ForgeConfigSpec.DoubleValue ENVIRONMENT_SOUND_VOLUME;
    
    // 调试配置
    private static final ForgeConfigSpec.BooleanValue DEBUG_MODE;
    private static final ForgeConfigSpec.IntValue DEBUG_LEVEL;

    // 静态初始化块
    static {
        ForgeConfigSpec.Builder commonBuilder = new ForgeConfigSpec.Builder();
        ForgeConfigSpec.Builder clientBuilder = new ForgeConfigSpec.Builder();
        
        // 配置通用选项
        commonBuilder.comment("KRT轨道交通模组通用配置");
        commonBuilder.push("common");
        
        ENABLE_SIGNAL_SYSTEM = commonBuilder
                .comment("是否启用信号系统")
                .define("enableSignalSystem", true);
        
        ENABLE_ATC = commonBuilder
                .comment("是否启用列车自动控制系统")
                .define("enableATC", true);
        
        ENABLE_AI_SUGGESTIONS = commonBuilder
                .comment("是否启用AI调度建议")
                .define("enableAISuggestions", true);
        
        GENERATE_EXAMPLE_DATA = commonBuilder
                .comment("是否生成示例数据")
                .define("generateExampleData", true);
        
        MAX_TRAINS_PER_LINE = commonBuilder
                .comment("每条线路的最大列车数量")
                .defineInRange("maxTrainsPerLine", 10, 1, 100);
        
        INITIAL_TRAIN_HEALTH = commonBuilder
                .comment("列车初始健康值")
                .defineInRange("initialTrainHealth", 100, 1, 100);
        
        DERAILMENT_THRESHOLD = commonBuilder
                .comment("列车脱轨阈值")
                .defineInRange("derailmentThreshold", 30, 1, 100);
        
        commonBuilder.pop();
        
        // 配置音频选项
        clientBuilder.comment("KRT轨道交通模组音频配置");
        clientBuilder.push("audio");
        
        ENABLE_CUSTOM_AUDIO = clientBuilder
                .comment("是否启用自定义音频")
                .define("enableCustomAudio", true);
        
        TRAIN_SOUND_VOLUME = clientBuilder
                .comment("列车音效音量")
                .defineInRange("trainSoundVolume", 0.8, 0.0, 1.0);
        
        ENVIRONMENT_SOUND_VOLUME = clientBuilder
                .comment("环境音效音量")
                .defineInRange("environmentSoundVolume", 0.5, 0.0, 1.0);
        
        clientBuilder.pop();
        
        // 配置调试选项
        clientBuilder.comment("KRT轨道交通模组调试配置");
        clientBuilder.push("debug");
        
        DEBUG_MODE = clientBuilder
                .comment("是否启用调试模式")
                .define("debugMode", false);
        
        DEBUG_LEVEL = clientBuilder
                .comment("调试级别（0-3）")
                .defineInRange("debugLevel", 0, 0, 3);
        
        clientBuilder.pop();
        
        // 构建配置
        COMMON_CONFIG = commonBuilder.build();
        CLIENT_CONFIG = clientBuilder.build();
    }

    /**
     * 构造函数
     */
    public KRTConfig() {
        // 初始化配置
    }

    /**
     * 是否启用信号系统
     */
    public boolean isSignalSystemEnabled() {
        return ENABLE_SIGNAL_SYSTEM.get();
    }

    /**
     * 是否启用ATC系统
     */
    public boolean isATCEnabled() {
        return ENABLE_ATC.get();
    }

    /**
     * 是否启用AI调度建议
     */
    public boolean isAISuggestionsEnabled() {
        return ENABLE_AI_SUGGESTIONS.get();
    }

    /**
     * 是否生成示例数据
     */
    public boolean isGenerateExampleData() {
        return GENERATE_EXAMPLE_DATA.get();
    }

    /**
     * 获取每条线路的最大列车数量
     */
    public int getMaxTrainsPerLine() {
        return MAX_TRAINS_PER_LINE.get();
    }

    /**
     * 获取列车初始健康值
     */
    public int getInitialTrainHealth() {
        return INITIAL_TRAIN_HEALTH.get();
    }

    /**
     * 获取列车脱轨阈值
     */
    public int getDerailmentThreshold() {
        return DERAILMENT_THRESHOLD.get();
    }

    /**
     * 是否启用自定义音频
     */
    public boolean isCustomAudioEnabled() {
        return ENABLE_CUSTOM_AUDIO.get();
    }

    /**
     * 获取列车音效音量
     */
    public double getTrainSoundVolume() {
        return TRAIN_SOUND_VOLUME.get();
    }

    /**
     * 获取环境音效音量
     */
    public double getEnvironmentSoundVolume() {
        return ENVIRONMENT_SOUND_VOLUME.get();
    }

    /**
     * 是否启用调试模式
     */
    public boolean isDebugMode() {
        return DEBUG_MODE.get();
    }

    /**
     * 获取调试级别
     */
    public int getDebugLevel() {
        return DEBUG_LEVEL.get();
    }

    /**
     * 更新配置值
     */
    public void updateConfig() {
        // 配置更新逻辑（如果需要）
    }
}