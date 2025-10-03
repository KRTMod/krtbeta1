package com.krt.mod.compat;

import com.krt.mod.system.LogSystem;
import net.fabricmc.loader.api.FabricLoader;

/**
 * 抽象兼容性实现类
 * 提供通用功能，简化特定模组兼容类的实现
 */
public abstract class AbstractModCompatibility implements IModCompatibility {
    
    private final String modId;
    private final String modName;
    private final String minimumVersion;
    private CompatibilityStatus status = CompatibilityStatus.NOT_LOADED;
    
    /**
     * 构造函数
     * @param modId 模组ID
     * @param modName 模组名称
     * @param minimumVersion 最低兼容版本
     */
    protected AbstractModCompatibility(String modId, String modName, String minimumVersion) {
        this.modId = modId;
        this.modName = modName;
        this.minimumVersion = minimumVersion;
    }
    
    @Override
    public String getModId() {
        return modId;
    }
    
    @Override
    public String getModName() {
        return modName;
    }
    
    @Override
    public String getMinimumVersion() {
        return minimumVersion;
    }
    
    @Override
    public CompatibilityStatus getStatus() {
        return status;
    }
    
    @Override
    public boolean isModLoaded() {
        boolean loaded = FabricLoader.getInstance().isModLoaded(modId);
        if (loaded) {
            status = CompatibilityStatus.LOADED;
        }
        return loaded;
    }
    
    @Override
    public final void initialize() {
        try {
            LogSystem.systemLog("开始初始化" + modName + "兼容性集成...");
            preInitialize();
            doInitialize();
            postInitialize();
            status = CompatibilityStatus.INITIALIZED;
            LogSystem.systemLog(modName + "兼容性集成初始化成功");
        } catch (Exception e) {
            status = CompatibilityStatus.FAILED;
            LogSystem.error(modName + "兼容性集成初始化失败: " + e.getMessage());
            throw e;
        }
    }
    
    /**
     * 初始化前的准备工作
     * 子类可以覆盖此方法以执行初始化前的任务
     */
    protected void preInitialize() {
        // 默认实现为空
    }
    
    /**
     * 执行实际的初始化工作
     * 子类必须实现此方法
     */
    protected abstract void doInitialize();
    
    /**
     * 初始化后的清理工作
     * 子类可以覆盖此方法以执行初始化后的任务
     */
    protected void postInitialize() {
        // 默认实现为空
    }
    
    /**
     * 安全地使用反射加载类
     * @param className 类名
     * @return 加载的类
     * @throws ClassNotFoundException 如果类不存在
     */
    protected Class<?> safeLoadClass(String className) throws ClassNotFoundException {
        try {
            return Class.forName(className);
        } catch (ClassNotFoundException e) {
            LogSystem.warningLog("无法加载类: " + className + ", 可能是模组版本不兼容");
            throw e;
        }
    }
    
    /**
     * 检查当前加载的模组版本是否满足最低要求
     * @return 是否满足最低版本要求
     */
    protected boolean checkVersion() {
        try {
            String currentVersion = FabricLoader.getInstance().getModContainer(modId)
                    .orElseThrow(() -> new RuntimeException("模组" + modId + "未加载"))
                    .getMetadata().getVersion().getFriendlyString();
            
            // 简单的版本比较逻辑，实际应用中可能需要更复杂的比较
            return true; // 暂时返回true，表示版本兼容
        } catch (Exception e) {
            LogSystem.warningLog("无法检查" + modName + "版本: " + e.getMessage());
            return false;
        }
    }
}