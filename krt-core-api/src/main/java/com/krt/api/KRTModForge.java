package com.krt.api;

import com.krt.api.data.*;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

/**
 * KRT轨道交通模组Forge版本主类
 */
@Mod(KRTModForge.MOD_ID)
public class KRTModForge {
    public static final String MOD_ID = "krt";
    public static final String MOD_NAME = "KRT轨道交通";
    public static final String MOD_VERSION = "1.0.0";
    
    private static KRTAPIImpl apiInstance;
    private static KRTKeyBindings keyBindings;
    private static KRTConfig config;

    public KRTModForge() {
        // 获取事件总线
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        
        // 注册生命周期事件
        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(this::clientSetup);
        
        // 注册到Forge事件总线
        MinecraftForge.EVENT_BUS.register(this);
        
        // 注册配置
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, KRTConfig.COMMON_CONFIG);
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, KRTConfig.CLIENT_CONFIG);
        
        // 初始化API实例
        apiInstance = new KRTAPIImpl();
        
        // 初始化键位绑定
        keyBindings = new KRTKeyBindings();
        
        // 初始化配置
        config = new KRTConfig();
        
        // 注册命令
        MinecraftForge.EVENT_BUS.addListener(this::registerCommands);
    }

    /**
     * 通用设置阶段
     */
    private void commonSetup(final FMLCommonSetupEvent event) {
        // 加载现有数据
        apiInstance.loadData();
        
        // 注册音频文件
        registerAudioFiles();
        
        // 如果启用了示例数据生成，则生成示例数据
        if (config.isGenerateExampleData()) {
            generateExampleData();
        }
    }

    /**
     * 客户端设置阶段
     */
    private void clientSetup(final FMLClientSetupEvent event) {
        // 注册客户端相关内容，如渲染器、GUI等
        registerRenderers();
        registerGUIs();
        registerKeyBindings();
    }

    /**
     * 注册命令
     */
    private void registerCommands(final RegisterCommandsEvent event) {
        // 注册KRT相关命令
        // 实际应用中需要实现具体的命令注册逻辑
    }

    /**
     * 注册音频文件
     */
    private void registerAudioFiles() {
        // 注册列车走行音
        apiInstance.registerAudio("train.run", "krt:sounds/train/run.ogg");
        
        // 注册屏蔽门开关门声音
        apiInstance.registerAudio("door.open", "krt:sounds/door/open.ogg");
        apiInstance.registerAudio("door.close", "krt:sounds/door/close.ogg");
        
        // 注册道岔撞轨声音
        apiInstance.registerAudio("track_switch.collision", "krt:sounds/track_switch/collision.ogg");
        
        // 注册鸣笛声音
        apiInstance.registerAudio("train.horn", "krt:sounds/train/horn.ogg");
        
        // 注册其他音频
        apiInstance.registerAudio("train.brake", "krt:sounds/train/brake.ogg");
    }

    /**
     * 生成示例数据
     */
    private void generateExampleData() {
        // 创建示例车厂
        Depot depot = apiInstance.createDepot("depot1", "昆明车辆段", 0, 60, 0);
        if (depot != null) {
            // 添加必要设施
            apiInstance.addDepotFacility("depot1", "停车库", 10, 60, 10, 40, 60, 10); // 长度30格
            apiInstance.addDepotFacility("depot1", "试车线", 50, 60, 0, 150, 60, 0); // 长度100格
            apiInstance.addDepotFacility("depot1", "洗车库", 0, 60, 20, 30, 60, 20); // 长度30格
            apiInstance.addDepotFacility("depot1", "检修库", 0, 60, 30, 40, 60, 30);
        }
        
        // 创建示例车站
        apiInstance.createStation("station1", "昆明火车站", "line1", 200, 60, 0);
        apiInstance.createStation("station2", "东风广场站", "line1", 300, 60, 0);
        apiInstance.createStation("station3", "北辰站", "line1", 400, 60, 0);
        
        // 创建示例线路
        Line line = apiInstance.createLine("line1", "1号线", "#FF0000", 80, "depot1");
        if (line != null) {
            // 添加车站到线路
            apiInstance.addStationToLine("line1", "station1", 0);
            apiInstance.addStationToLine("line1", "station2", 1);
            apiInstance.addStationToLine("line1", "station3", 2);
            
            // 添加轨道点
            for (int i = 0; i <= 400; i += 10) {
                line.addTrackPoint(i, 60, 0);
            }
        }
        
        // 创建示例列车
        apiInstance.createTrain("train1", "昆明地铁1号线列车", "DKZ5", "line1", "station1");
        
        // 创建示例信号机
        for (int i = 200; i <= 400; i += 50) {
            apiInstance.createSignal("signal" + i, i, 61, 0, Signal.TYPE_MAIN, "line1");
        }
        
        // 保存示例数据
        apiInstance.saveData();
    }

    /**
     * 注册渲染器
     */
    private void registerRenderers() {
        // 注册列车渲染器、轨道渲染器等
    }

    /**
     * 注册GUI
     */
    private void registerGUIs() {
        // 注册线路控制面板、列车驾驶面板等GUI
    }

    /**
     * 注册键位绑定
     */
    private void registerKeyBindings() {
        // 注册游戏内键位绑定
    }

    /**
     * 获取API实例
     */
    public static KRTAPI getAPI() {
        return apiInstance;
    }

    /**
     * 获取键位绑定实例
     */
    public static KRTKeyBindings getKeyBindings() {
        return keyBindings;
    }

    /**
     * 获取配置实例
     */
    public static KRTConfig getConfig() {
        return config;
    }
}