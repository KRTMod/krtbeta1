package com.krt.mod.compat;

import com.krt.mod.system.LogSystem;
import net.fabricmc.loader.api.FabricLoader;

/**
 * 基础兼容性实现类
 * 为没有专门集成类的模组提供基础的兼容性支持
 */
public class BasicModCompatibility extends AbstractModCompatibility {
    
    /**
     * 构造函数
     * @param modId 模组ID
     * @param modName 模组名称
     * @param minimumVersion 最低兼容版本
     */
    public BasicModCompatibility(String modId, String modName, String minimumVersion) {
        super(modId, modName, minimumVersion);
    }
    
    @Override
    protected void doInitialize() {
        LogSystem.systemLog("初始化" + getModName() + "基础兼容性支持...");
        
        // 检查模组是否已加载
        if (!isModLoaded()) {
            LogSystem.warningLog("模组" + getModName() + "未加载，无法初始化兼容性支持");
            return;
        }
        
        // 检查版本兼容性
        if (!checkVersion()) {
            LogSystem.warningLog("模组" + getModName() + "版本不兼容，当前版本可能低于要求的最低版本: " + getMinimumVersion());
        }
        
        // 执行基础兼容性检测
        detectBasicCompatibility();
        
        // 尝试进行简单的集成
        attemptBasicIntegration();
        
        LogSystem.systemLog("" + getModName() + "基础兼容性支持初始化完成");
    }
    
    /**
     * 执行基础兼容性检测
     */
    private void detectBasicCompatibility() {
        try {
            // 尝试加载模组的一些核心类，以验证兼容性
            String[] coreClasses = getCoreClasses();
            if (coreClasses != null && coreClasses.length > 0) {
                for (String className : coreClasses) {
                    try {
                        safeLoadClass(className);
                        LogSystem.debug("成功加载" + getModName() + "核心类: " + className);
                    } catch (ClassNotFoundException e) {
                        LogSystem.warningLog("无法加载" + getModName() + "核心类: " + className + ", 可能是版本不兼容");
                    }
                }
            }
        } catch (Exception e) {
            LogSystem.error("检测" + getModName() + "兼容性时发生错误: " + e.getMessage());
        }
    }
    
    /**
     * 尝试进行简单的集成
     */
    private void attemptBasicIntegration() {
        try {
            // 这里可以实现一些通用的集成逻辑，例如事件监听、物品注册等
            LogSystem.debug("尝试与" + getModName() + "进行基础集成");
            
            // 示例: 注册一些基本的事件监听器
            registerBasicEventListeners();
            
            // 示例: 添加一些基本的物品兼容性
            addBasicItemCompatibility();
            
            // 示例: 添加一些基本的方块兼容性
            addBasicBlockCompatibility();
        } catch (Exception e) {
            LogSystem.error("与" + getModName() + "进行基础集成时发生错误: " + e.getMessage());
        }
    }
    
    /**
     * 获取模组的核心类列表
     * 子类可以覆盖此方法以提供特定模组的核心类
     * @return 核心类列表
     */
    protected String[] getCoreClasses() {
        // 默认实现返回空数组，子类可以根据需要覆盖
        return new String[0];
    }
    
    /**
     * 注册基本的事件监听器
     */
    protected void registerBasicEventListeners() {
        // 默认实现为空，子类可以根据需要覆盖
        LogSystem.debug("注册" + getModName() + "基本事件监听器");
    }
    
    /**
     * 添加基本的物品兼容性
     */
    protected void addBasicItemCompatibility() {
        // 默认实现为空，子类可以根据需要覆盖
        LogSystem.debug("添加" + getModName() + "基本物品兼容性");
    }
    
    /**
     * 添加基本的方块兼容性
     */
    protected void addBasicBlockCompatibility() {
        // 默认实现为空，子类可以根据需要覆盖
        LogSystem.debug("添加" + getModName() + "基本方块兼容性");
    }
}