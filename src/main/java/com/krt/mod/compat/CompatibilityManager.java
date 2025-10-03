package com.krt.mod.compat;

import com.krt.mod.KRTMod;
import com.krt.mod.system.LogSystem;
import net.fabricmc.loader.api.FabricLoader;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 兼容性管理器
 * 统一管理所有模组的兼容性集成
 */
public class CompatibilityManager {
    // 单例实例
    private static CompatibilityManager instance;
    
    // 已注册的兼容模组列表
    private final Map<String, IModCompatibility> registeredCompatibilities = new ConcurrentHashMap<>();
    
    // 私有构造函数
    private CompatibilityManager() {
    }
    
    /**
     * 获取单例实例
     * @return CompatibilityManager实例
     */
    public static synchronized CompatibilityManager getInstance() {
        if (instance == null) {
            instance = new CompatibilityManager();
        }
        return instance;
    }
    
    /**
     * 注册一个兼容性实现
     * @param compatibility 兼容性实现对象
     */
    public void registerCompatibility(IModCompatibility compatibility) {
        if (compatibility != null && compatibility.getModId() != null) {
            registeredCompatibilities.put(compatibility.getModId(), compatibility);
            LogSystem.systemLog("已注册兼容性实现: " + compatibility.getModName() + " (" + compatibility.getModId() + ")");
        }
    }
    
    /**
     * 初始化所有已注册的兼容性实现
     */
    public void initializeAll() {
        LogSystem.systemLog("开始初始化所有兼容模组...");
        
        for (IModCompatibility compatibility : registeredCompatibilities.values()) {
            try {
                if (compatibility.isModLoaded()) {
                    LogSystem.systemLog("初始化" + compatibility.getModName() + "模组兼容性...");
                    compatibility.initialize();
                    LogSystem.systemLog(compatibility.getModName() + "模组兼容性初始化完成");
                } else {
                    LogSystem.systemLog("未检测到" + compatibility.getModName() + "模组，跳过兼容性初始化");
                }
            } catch (Exception e) {
                LogSystem.error(compatibility.getModName() + "模组兼容性初始化失败: " + e.getMessage());
                KRTMod.LOGGER.error(compatibility.getModName() + "模组兼容性初始化失败", e);
            }
        }
        
        LogSystem.systemLog("所有兼容模组初始化完成");
    }
    
    /**
     * 获取已注册的所有兼容性实现
     * @return 兼容性实现集合
     */
    public Collection<IModCompatibility> getAllRegisteredCompatibilities() {
        return new ArrayList<>(registeredCompatibilities.values());
    }
    
    /**
     * 根据模组ID获取兼容性实现
     * @param modId 模组ID
     * @return 兼容性实现对象，如果不存在则返回null
     */
    public IModCompatibility getCompatibility(String modId) {
        return registeredCompatibilities.get(modId);
    }
    
    /**
     * 检查指定模组是否已加载且初始化完成
     * @param modId 模组ID
     * @return 是否已加载且初始化完成
     */
    public boolean isModInitialized(String modId) {
        IModCompatibility compatibility = registeredCompatibilities.get(modId);
        return compatibility != null && compatibility.getStatus() == IModCompatibility.CompatibilityStatus.INITIALIZED;
    }
    
    /**
     * 获取已初始化的兼容模组数量
     * @return 已初始化的模组数量
     */
    public int getInitializedCount() {
        int count = 0;
        for (IModCompatibility compatibility : registeredCompatibilities.values()) {
            if (compatibility.getStatus() == IModCompatibility.CompatibilityStatus.INITIALIZED) {
                count++;
            }
        }
        return count;
    }
}