package com.krt.mod.entity;

import com.krt.mod.KRTMod;
import com.krt.mod.system.LogSystem;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.EntityType;
import net.minecraft.util.Identifier;
import net.minecraft.util.registry.Registry;

/**
 * 实体注册类
 * 负责注册模组中的所有实体和方块实体
 */
public class ModEntities {
    
    /**
     * 注册所有方块实体
     */
    public static void registerAllBlockEntities() {
        LogSystem.systemLog("开始注册方块实体...");
        
        // 注册信号机方块实体
        registerSignalBlockEntities();
        
        // 注册轨道控制器方块实体
        registerTrackControllerBlockEntities();
        
        // 注册车站设备方块实体
        registerStationDeviceBlockEntities();
        
        LogSystem.systemLog("方块实体注册完成");
    }
    
    /**
     * 注册所有实体
     */
    public static void registerAllEntities() {
        LogSystem.systemLog("开始注册实体...");
        
        // 注册列车实体
        registerTrainEntities();
        
        // 注册轨道相关实体
        registerTrackEntities();
        
        // 注册其他功能实体
        registerFunctionalEntities();
        
        LogSystem.systemLog("实体注册完成");
    }
    
    /**
     * 注册信号机方块实体
     */
    private static void registerSignalBlockEntities() {
        // 这里将注册各种信号机方块实体
        LogSystem.debugLog("注册信号机方块实体");
    }
    
    /**
     * 注册轨道控制器方块实体
     */
    private static void registerTrackControllerBlockEntities() {
        // 这里将注册各种轨道控制器方块实体
        LogSystem.debugLog("注册轨道控制器方块实体");
    }
    
    /**
     * 注册车站设备方块实体
     */
    private static void registerStationDeviceBlockEntities() {
        // 这里将注册各种车站设备方块实体
        LogSystem.debugLog("注册车站设备方块实体");
    }
    
    /**
     * 注册列车实体
     */
    private static void registerTrainEntities() {
        // 这里将注册各种列车实体
        LogSystem.debugLog("注册列车实体");
    }
    
    /**
     * 注册轨道相关实体
     */
    private static void registerTrackEntities() {
        // 这里将注册各种轨道相关实体
        LogSystem.debugLog("注册轨道相关实体");
    }
    
    /**
     * 注册其他功能实体
     */
    private static void registerFunctionalEntities() {
        // 这里将注册其他功能性实体
        LogSystem.debugLog("注册其他功能实体");
    }
    
    /**
     * 注册单个方块实体的辅助方法
     * @param blockEntityType 要注册的方块实体类型
     * @param name 方块实体名称
     * @return 注册后的方块实体类型
     */
    public static <T extends BlockEntityType<?>> T registerBlockEntity(BlockEntityType<?> blockEntityType, String name) {
        Identifier id = new Identifier(KRTMod.MOD_ID, name);
        LogSystem.debugLog("注册方块实体: " + name + " (ID: " + id + ")");
        return (T) Registry.register(Registry.BLOCK_ENTITY_TYPE, id, blockEntityType);
    }
    
    /**
     * 注册单个实体的辅助方法
     * @param entityType 要注册的实体类型
     * @param name 实体名称
     * @return 注册后的实体类型
     */
    public static <T extends EntityType<?>> T registerEntity(EntityType<?> entityType, String name) {
        Identifier id = new Identifier(KRTMod.MOD_ID, name);
        LogSystem.debugLog("注册实体: " + name + " (ID: " + id + ")");
        return (T) Registry.register(Registry.ENTITY_TYPE, id, entityType);
    }
}