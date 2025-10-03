package com.krt.mod.screen;

import com.krt.mod.KRTMod;
import com.krt.mod.system.LogSystem;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.util.Identifier;
import net.minecraft.util.registry.Registry;

/**
 * 屏幕处理器注册类
 * 负责注册模组中的所有屏幕处理器类型
 */
public class ModScreens {
    
    /**
     * 注册所有屏幕处理器类型
     */
    public static void registerAllScreenHandlers() {
        LogSystem.systemLog("开始注册屏幕处理器...");
        
        // 注册操作手册屏幕
        registerManualScreenHandlers();
        
        // 注册线路控制面板
        registerLineControlScreens();
        
        // 注册信号控制系统
        registerSignalControlScreens();
        
        // 注册列车监控系统
        registerTrainMonitorScreens();
        
        // 注册车站管理系统
        registerStationManagementScreens();
        
        LogSystem.systemLog("屏幕处理器注册完成");
    }
    
    /**
     * 注册操作手册屏幕
     */
    private static void registerManualScreenHandlers() {
        // 这里将注册操作手册相关的屏幕处理器
        LogSystem.debugLog("注册操作手册屏幕处理器");
    }
    
    /**
     * 注册线路控制面板
     */
    private static void registerLineControlScreens() {
        // 这里将注册线路控制相关的屏幕处理器
        LogSystem.debugLog("注册线路控制面板屏幕处理器");
    }
    
    /**
     * 注册信号控制系统
     */
    private static void registerSignalControlScreens() {
        // 这里将注册信号控制相关的屏幕处理器
        LogSystem.debugLog("注册信号控制系统屏幕处理器");
    }
    
    /**
     * 注册列车监控系统
     */
    private static void registerTrainMonitorScreens() {
        // 这里将注册列车监控相关的屏幕处理器
        LogSystem.debugLog("注册列车监控系统屏幕处理器");
    }
    
    /**
     * 注册车站管理系统
     */
    private static void registerStationManagementScreens() {
        // 这里将注册车站管理相关的屏幕处理器
        LogSystem.debugLog("注册车站管理系统屏幕处理器");
    }
    
    /**
     * 注册单个屏幕处理器的辅助方法
     * @param screenHandlerType 要注册的屏幕处理器类型
     * @param name 屏幕处理器名称
     * @return 注册后的屏幕处理器类型
     */
    public static <T extends ScreenHandlerType<?>> T register(ScreenHandlerType<?> screenHandlerType, String name) {
        Identifier id = new Identifier(KRTMod.MOD_ID, name);
        LogSystem.debugLog("注册屏幕处理器: " + name + " (ID: " + id + ")");
        return (T) Registry.register(Registry.SCREEN_HANDLER, id, screenHandlerType);
    }
}