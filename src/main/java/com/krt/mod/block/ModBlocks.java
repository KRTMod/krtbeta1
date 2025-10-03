package com.krt.mod.block;

import com.krt.mod.KRTMod;
import com.krt.mod.block.power.*;
import com.krt.mod.system.LogSystem;
import net.minecraft.block.Block;
import net.minecraft.block.Material;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.util.Identifier;
import net.minecraft.util.registry.Registry;

/**
 * 方块注册类
 * 负责注册模组中的所有方块
 */
public class ModBlocks {
    
    // 能源系统相关方块实例
    public static final PowerGeneratorBlock POWER_GENERATOR = new PowerGeneratorBlock(Block.Settings.of(Material.METAL).strength(3.0f, 6.0f).sounds(BlockSoundGroup.METAL));
    public static final PowerTransmissionLineBlock POWER_TRANSMISSION_LINE = new PowerTransmissionLineBlock(Block.Settings.of(Material.METAL).strength(0.5f).sounds(BlockSoundGroup.METAL));
    public static final RailPowerConnectorBlock RAIL_POWER_CONNECTOR = new RailPowerConnectorBlock(Block.Settings.of(Material.METAL).strength(2.0f, 4.0f).sounds(BlockSoundGroup.METAL));
    public static final PowerStorageBlock POWER_STORAGE = new PowerStorageBlock(Block.Settings.of(Material.METAL).strength(3.0f, 6.0f).sounds(BlockSoundGroup.METAL));
    
    // 供电系统方块实例
    public static final OverheadWirePoleBlock OVERHEAD_WIRE_POLE = new OverheadWirePoleBlock(Block.Settings.of(Material.METAL).strength(2.0f, 4.0f).sounds(BlockSoundGroup.METAL));
    public static final OverheadWireBlock OVERHEAD_WIRE = new OverheadWireBlock(Block.Settings.of(Material.METAL).strength(0.5f).sounds(BlockSoundGroup.METAL));
    public static final ThirdRailBlock THIRD_RAIL = new ThirdRailBlock(Block.Settings.of(Material.METAL).strength(1.5f, 3.0f).sounds(BlockSoundGroup.METAL));
    
    /**
     * 注册所有方块
     */
    public static void registerAllBlocks() {
        LogSystem.systemLog("开始注册方块...");
        
        // 注册轨道相关方块
        registerTrackBlocks();
        
        // 注册道岔相关方块
        registerSwitchBlocks();
        
        // 注册信号机相关方块
        registerSignalBlocks();
        
        // 注册车站相关方块
        registerStationBlocks();
        
        // 注册能源系统相关方块
        registerPowerBlocks();
        
        // 注册其他功能方块
        registerFunctionalBlocks();
        
        LogSystem.systemLog("方块注册完成");
    }
    
    /**
     * 注册轨道相关方块
     */
    private static void registerTrackBlocks() {
        // 这里将注册各种轨道方块
        LogSystem.debugLog("注册轨道相关方块");
    }
    
    /**
     * 注册道岔相关方块
     */
    private static void registerSwitchBlocks() {
        // 这里将注册各种道岔方块
        LogSystem.debugLog("注册道岔相关方块");
    }
    
    /**
     * 注册信号机相关方块
     */
    private static void registerSignalBlocks() {
        // 这里将注册各种信号机方块
        LogSystem.debugLog("注册信号机相关方块");
    }
    
    /**
     * 注册车站相关方块
     */
    private static void registerStationBlocks() {
        // 这里将注册各种车站设备方块
        LogSystem.debugLog("注册车站相关方块");
    }
    
    /**
     * 注册能源系统相关方块
     */
    private static void registerPowerBlocks() {
        LogSystem.debugLog("注册能源系统相关方块");
        
        // 注册发电机
        register(POWER_GENERATOR, PowerGeneratorBlock.ID);
        
        // 注册电力传输线
        register(POWER_TRANSMISSION_LINE, PowerTransmissionLineBlock.ID);
        
        // 注册轨道电力连接器
        register(RAIL_POWER_CONNECTOR, RailPowerConnectorBlock.ID);
        
        // 注册电力存储块
        register(POWER_STORAGE, PowerStorageBlock.ID);
        
        // 注册接触网系统方块
        register(OVERHEAD_WIRE_POLE, OverheadWirePoleBlock.ID);
        register(OVERHEAD_WIRE, OverheadWireBlock.ID);
        
        // 注册第三轨系统方块
        register(THIRD_RAIL, ThirdRailBlock.ID);
    }
    
    /**
     * 注册其他功能方块
     */
    private static void registerFunctionalBlocks() {
        // 这里将注册其他功能性方块
        LogSystem.debugLog("注册其他功能方块");
    }
    
    /**
     * 注册单个方块的辅助方法
     * @param block 要注册的方块
     * @param name 方块名称
     * @return 注册后的方块
     */
    public static <T extends Block> T register(Block block, String name) {
        Identifier id = new Identifier(KRTMod.MOD_ID, name);
        LogSystem.debugLog("注册方块: " + name + " (ID: " + id + ")");
        return (T) Registry.register(Registry.BLOCK, id, block);
    }
}