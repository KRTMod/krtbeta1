package com.krt.mod.util;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.world.World;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 实体对象池，用于优化实体创建和重用
 */
public class EntityObjectPool {
    private static final Map<World, Map<Class<? extends Entity>, EntityObjectPool>> POOLS = new HashMap<>();
    
    private final Class<? extends Entity> entityClass;
    private final World world;
    private final List<Entity> pool;
    private final int maxPoolSize;
    
    private EntityObjectPool(World world, Class<? extends Entity> entityClass, int maxPoolSize) {
        this.world = world;
        this.entityClass = entityClass;
        this.pool = new ArrayList<>();
        this.maxPoolSize = maxPoolSize;
    }
    
    /**
     * 获取指定类型和世界的对象池实例
     */
    @SuppressWarnings("unchecked")
    public static <T extends Entity> EntityObjectPool getInstance(World world, Class<T> entityClass) {
        return POOLS.computeIfAbsent(world, w -> new HashMap<>())
                   .computeIfAbsent(entityClass, c -> new EntityObjectPool(world, c, 50));
    }
    
    /**
     * 从对象池中获取实体
     */
    @SuppressWarnings("unchecked")
    public <T extends Entity> T get() {
        if (!pool.isEmpty()) {
            // 从池中获取一个实体
            return (T) pool.remove(pool.size() - 1);
        }
        
        // 如果池为空，创建新实体
        return createNew();
    }
    
    /**
     * 回收实体到对象池
     */
    public void recycle(Entity entity) {
        if (entity == null || !entity.isAlive() || pool.size() >= maxPoolSize) {
            return;
        }
        
        // 重置实体状态
        resetEntity(entity);
        
        // 从世界中移除但保留实体对象
        entity.remove(Entity.RemovalReason.DISCARDED);
        
        // 添加到池中
        pool.add(entity);
    }
    
    /**
     * 重置实体状态
     */
    private void resetEntity(Entity entity) {
        if (entity instanceof VillagerEntity) {
            // 重置村民实体的特定属性
            VillagerEntity villager = (VillagerEntity) entity;
            // 可以在这里添加其他重置逻辑
        }
        
        // 重置通用属性
        entity.setPosition(0, -1000, 0); // 移动到远离玩家的位置
        entity.setSilent(true); // 暂时静音
    }
    
    /**
     * 创建新实体
     */
    @SuppressWarnings("unchecked")
    private <T extends Entity> T createNew() {
        if (entityClass == VillagerEntity.class) {
            return (T) new VillagerEntity(EntityType.VILLAGER, world);
        }
        
        // 可以根据需要添加更多类型的实体创建
        return null;
    }
    
    /**
     * 获取当前池大小
     */
    public int size() {
        return pool.size();
    }
    
    /**
     * 清理对象池
     */
    public void clear() {
        for (Entity entity : pool) {
            entity.discard();
        }
        pool.clear();
    }
}