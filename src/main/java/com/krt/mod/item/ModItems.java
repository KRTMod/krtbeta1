package com.krt.mod.item;

import com.krt.mod.KRTMod;
import com.krt.mod.block.ModBlocks;
import com.krt.mod.item.material.CeramicItem;
import com.krt.mod.item.tool.OverheadWireTool;
import com.krt.mod.item.tool.ThirdRailTool;
import com.krt.mod.item.train.PantographItem;
import com.krt.mod.item.train.PowerCollectorShoeItem;
import com.krt.mod.system.LogSystem;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.Item.Settings;
import net.minecraft.util.Identifier;
import net.minecraft.util.registry.Registry;

/**
 * 物品注册类
 * 负责注册模组中的所有物品
 */
public class ModItems {
    
    // 能源系统物品
    public static Item POWER_GENERATOR_ITEM;
    public static Item POWER_TRANSMISSION_LINE_ITEM;
    public static Item RAIL_POWER_CONNECTOR_ITEM;
    public static Item POWER_STORAGE_ITEM;
    
    // 供电系统方块物品
    public static Item OVERHEAD_WIRE_POLE_ITEM;
    public static Item OVERHEAD_WIRE_ITEM;
    public static Item THIRD_RAIL_ITEM;
    
    // 供电系统工具和材料
    public static Item OVERHEAD_WIRE_TOOL;
    public static Item THIRD_RAIL_TOOL;
    public static Item CERAMIC_ITEM;
    
    // 列车电力装置
    public static Item PANTOGRAPH_ITEM;
    public static Item POWER_COLLECTOR_SHOE_ITEM;
    
    /**
     * 注册所有物品
     */
    public static void registerAllItems() {
        LogSystem.systemLog("开始注册物品...");
        
        // 注册轨道相关物品
        registerTrackItems();
        
        // 注册道岔相关物品
        registerSwitchItems();
        
        // 注册信号机相关物品
        registerSignalItems();
        
        // 注册车站相关物品
        registerStationItems();
        
        // 注册能源系统相关物品
        registerPowerItems();
        
        // 注册供电系统相关物品
        registerPowerSystemItems();
        
        // 注册工具类物品
        registerToolItems();
        
        // 注册其他物品
        registerOtherItems();
        
        LogSystem.systemLog("物品注册完成");
    }
    
    /**
     * 注册轨道相关物品
     */
    private static void registerTrackItems() {
        // 这里将注册各种轨道物品
        LogSystem.debugLog("注册轨道相关物品");
    }
    
    /**
     * 注册道岔相关物品
     */
    private static void registerSwitchItems() {
        // 这里将注册各种道岔物品
        LogSystem.debugLog("注册道岔相关物品");
    }
    
    /**
     * 注册信号机相关物品
     */
    private static void registerSignalItems() {
        // 这里将注册各种信号机物品
        LogSystem.debugLog("注册信号机相关物品");
    }
    
    /**
     * 注册车站相关物品
     */
    private static void registerStationItems() {
        // 这里将注册各种车站设备物品
        LogSystem.debugLog("注册车站相关物品");
    }
    
    /**
     * 注册能源系统相关物品
     */
    private static void registerPowerItems() {
        LogSystem.debugLog("注册能源系统相关物品");
        
        // 为每个能源系统方块创建对应的BlockItem并注册
        POWER_GENERATOR_ITEM = register(new BlockItem(ModBlocks.POWER_GENERATOR, new Settings().group(KRTItemGroup.KRT_GROUP)), 
                com.krt.mod.block.power.PowerGeneratorBlock.ID);
        
        POWER_TRANSMISSION_LINE_ITEM = register(new BlockItem(ModBlocks.POWER_TRANSMISSION_LINE, new Settings().group(KRTItemGroup.KRT_GROUP)), 
                com.krt.mod.block.power.PowerTransmissionLineBlock.ID);
        
        RAIL_POWER_CONNECTOR_ITEM = register(new BlockItem(ModBlocks.RAIL_POWER_CONNECTOR, new Settings().group(KRTItemGroup.KRT_GROUP)), 
                com.krt.mod.block.power.RailPowerConnectorBlock.ID);
        
        POWER_STORAGE_ITEM = register(new BlockItem(ModBlocks.POWER_STORAGE, new Settings().group(KRTItemGroup.KRT_GROUP)), 
                com.krt.mod.block.power.PowerStorageBlock.ID);
        
        // 注册供电系统方块物品
        OVERHEAD_WIRE_POLE_ITEM = register(new BlockItem(ModBlocks.OVERHEAD_WIRE_POLE, new Settings().group(KRTItemGroup.KRT_GROUP)), 
                "overhead_wire_pole");
        
        OVERHEAD_WIRE_ITEM = register(new BlockItem(ModBlocks.OVERHEAD_WIRE, new Settings().group(KRTItemGroup.KRT_GROUP)), 
                "overhead_wire");
        
        THIRD_RAIL_ITEM = register(new BlockItem(ModBlocks.THIRD_RAIL, new Settings().group(KRTItemGroup.KRT_GROUP)), 
                "third_rail");
    }
    
    /**
     * 注册供电系统工具和材料物品
     */
    private static void registerPowerSystemItems() {
        LogSystem.debugLog("注册供电系统工具和材料物品");
        
        // 注册工具物品
        OVERHEAD_WIRE_TOOL = register(new OverheadWireTool(new Settings().group(KRTItemGroup.KRT_GROUP).maxDamage(100)), 
                "overhead_wire_tool");
        
        THIRD_RAIL_TOOL = register(new ThirdRailTool(new Settings().group(KRTItemGroup.KRT_GROUP).maxDamage(100)), 
                "third_rail_tool");
        
        // 注册材料物品
        CERAMIC_ITEM = register(new CeramicItem(new Settings().group(KRTItemGroup.KRT_GROUP)), 
                "ceramic");
        
        // 注册列车电力装置
        PANTOGRAPH_ITEM = register(new PantographItem(new Settings().group(KRTItemGroup.KRT_GROUP)), 
                "pantograph");
        
        POWER_COLLECTOR_SHOE_ITEM = register(new PowerCollectorShoeItem(new Settings().group(KRTItemGroup.KRT_GROUP)), 
                "power_collector_shoe");
    }
    
    /**
     * 注册工具类物品
     */
    private static void registerToolItems() {
        // 这里将注册各种工具类物品
        LogSystem.debugLog("注册工具类物品");
    }
    
    /**
     * 注册其他物品
     */
    private static void registerOtherItems() {
        // 这里将注册其他物品
        LogSystem.debugLog("注册其他物品");
    }
    
    /**
     * 注册单个物品的辅助方法
     * @param item 要注册的物品
     * @param name 物品名称
     * @return 注册后的物品
     */
    public static <T extends Item> T register(Item item, String name) {
        Identifier id = new Identifier(KRTMod.MOD_ID, name);
        LogSystem.debugLog("注册物品: " + name + " (ID: " + id + ")");
        return (T) Registry.register(Registry.ITEM, id, item);
    }
}