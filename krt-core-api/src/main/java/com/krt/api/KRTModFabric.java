package com.krt.api;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;

/**
 * KRT轨道交通模组Fabric版本主类
 */
public class KRTModFabric implements ModInitializer {
    public static final String MOD_ID = "krt";
    public static final String MOD_NAME = "KRT轨道交通";
    public static final String MOD_VERSION = "1.0.0";
    
    private static KRTAPIImpl apiInstance;
    private static KRTAddonAPIImpl addonApiInstance;
    private static KRTKeyBindings keyBindings;
    private static KRTConfigFabric config;

    @Override
    public void onInitialize() {
        // 初始化API实例
        apiInstance = new KRTAPIImpl();
        
        // 初始化扩展包API实例
        addonApiInstance = new KRTAddonAPIImpl();
        
        // 初始化键位绑定
        keyBindings = new KRTKeyBindings();
        
        // 初始化配置
        config = new KRTConfigFabric();
        config.loadConfig();
        
        // 注册生命周期事件
        registerLifecycleEvents();
        
        // 注册命令
        registerCommands();
        
        // 注册音频文件
        registerAudioFiles();
        
        // 加载已安装的扩展包
        loadInstalledAddons();
        
        // 如果启用了示例数据生成，则生成示例数据
        if (config.isGenerateExampleData()) {
            generateExampleData();
        }
    }
    
    /**
     * 加载已安装的扩展包
     */
    private void loadInstalledAddons() {
        // 这里应该实现加载已安装扩展包的逻辑
        // 实际应用中需要扫描扩展包目录并加载每个扩展包
    }
    
    /**
     * 获取KRT API实例
     */
    public static KRTAPIImpl getApiInstance() {
        return apiInstance;
    }
    
    /**
     * 获取KRT Addon API实例
     */
    public static KRTAddonAPIImpl getAddonApiInstance() {
        return addonApiInstance;
    }

    /**
     * 注册生命周期事件
     */
    private void registerLifecycleEvents() {
        // 服务器启动事件
        ServerLifecycleEvents.SERVER_STARTING.register(server -> {
            // 服务器启动时的初始化逻辑
        });
        
        // 服务器停止事件
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            // 服务器停止时的清理逻辑
            apiInstance.saveData();
        });
        
        // 客户端启动事件
        ClientLifecycleEvents.CLIENT_STARTED.register(client -> {
            // 客户端启动时的初始化逻辑
            registerClientComponents();
        });
    }

    /**
     * 注册命令
     */
    private void registerCommands() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            // 注册KRT相关命令
            // 实际应用中需要实现具体的命令注册逻辑
        });
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
        apiInstance.createDepot("depot1", "昆明车辆段", 0, 60, 0);
        if (apiInstance.getDepot("depot1") != null) {
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
        apiInstance.createLine("line1", "1号线", "#FF0000", 80, "depot1");
        if (apiInstance.getLine("line1") != null) {
            // 添加车站到线路
            apiInstance.addStationToLine("line1", "station1", 0);
            apiInstance.addStationToLine("line1", "station2", 1);
            apiInstance.addStationToLine("line1", "station3", 2);
        }
        
        // 创建示例列车
        apiInstance.createTrain("train1", "昆明地铁1号线列车", "DKZ5", "line1", "station1");
        
        // 创建示例信号机
        for (int i = 200; i <= 400; i += 50) {
            apiInstance.createSignal("signal" + i, i, 61, 0, "主信号机", "line1");
        }
        
        // 保存示例数据
        apiInstance.saveData();
    }

    /**
     * 注册客户端组件
     */
    private void registerClientComponents() {
        // 注册键位绑定
        registerKeyBindings();
        
        // 注册渲染器
        registerRenderers();
        
        // 注册GUI
        registerGUIs();
    }

    /**
     * 注册键位绑定
     */
    private void registerKeyBindings() {
        // 在Fabric中注册键位绑定
        // 实际应用中需要实现具体的键位注册逻辑
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
    public static KRTConfigFabric getConfig() {
        return config;
    }
}