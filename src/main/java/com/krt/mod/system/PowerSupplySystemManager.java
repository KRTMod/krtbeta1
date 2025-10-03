package com.krt.mod.system;

import net.minecraft.world.World;
import java.util.HashMap;
import java.util.Map;

/**
 * 供电系统管理器，负责为每个世界维护一个供电系统实例
 */
public class PowerSupplySystemManager {
    private static final Map<World, PowerSupplySystem> POWER_SYSTEMS = new HashMap<>();
    
    /**
     * 获取指定世界的供电系统
     */
    public static PowerSupplySystem getForWorld(World world) {
        if (world == null) {
            return null;
        }
        
        // 为不同维度创建独立的供电系统
        return POWER_SYSTEMS.computeIfAbsent(world, PowerSupplySystem::new);
    }
    
    /**
     * 移除指定世界的供电系统
     */
    public static void removeForWorld(World world) {
        POWER_SYSTEMS.remove(world);
    }
    
    /**
     * 清理所有供电系统（用于模组卸载）
     */
    public static void clearAll() {
        POWER_SYSTEMS.clear();
    }
}