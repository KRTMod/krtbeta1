package com.krt.mod.compat;

/**
 * 模组兼容性接口
 * 所有特定模组的兼容类都应实现此接口
 */
public interface IModCompatibility {
    
    /**
     * 获取模组ID
     * @return 模组的唯一标识符
     */
    String getModId();
    
    /**
     * 获取模组名称
     * @return 模组的显示名称
     */
    String getModName();
    
    /**
     * 初始化兼容性功能
     * 实现与该模组的联动逻辑
     */
    void initialize();
    
    /**
     * 检查该模组是否已加载
     * @return 模组加载状态
     */
    boolean isModLoaded();
    
    /**
     * 获取兼容的最低版本
     * @return 最低兼容版本字符串
     */
    String getMinimumVersion();
    
    /**
     * 获取兼容性状态
     * @return 兼容性状态枚举
     */
    CompatibilityStatus getStatus();
    
    /**
     * 兼容性状态枚举
     */
    enum CompatibilityStatus {
        NOT_LOADED,
        LOADED,
        INITIALIZED,
        FAILED
    }
}