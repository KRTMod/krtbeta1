package com.krt.mod.compat;

import com.krt.mod.system.LogSystem;
import net.fabricmc.loader.api.FabricLoader;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 兼容性注册表
 * 负责发现和注册所有兼容模组的实现
 */
public class CompatibilityRegistry {
    
    // 已注册的兼容性提供者列表
    private static final Map<String, CompatibilityProvider> providers = new ConcurrentHashMap<>();
    
    // 单例实例
    private static final CompatibilityRegistry instance = new CompatibilityRegistry();
    
    /**
     * 私有构造函数
     */
    private CompatibilityRegistry() {
    }
    
    /**
     * 获取单例实例
     * @return CompatibilityRegistry实例
     */
    public static CompatibilityRegistry getInstance() {
        return instance;
    }
    
    /**
     * 注册一个兼容性提供者
     * @param provider 兼容性提供者
     */
    public void registerProvider(CompatibilityProvider provider) {
        if (provider != null && provider.getModId() != null) {
            providers.put(provider.getModId(), provider);
            LogSystem.debugLog("已注册兼容性提供者: " + provider.getModName() + " (" + provider.getModId() + ")");
        }
    }
    
    /**
     * 发现并注册所有可用的兼容性提供者
     * 此方法会扫描常见的模组并尝试创建对应的兼容性实现
     */
    public void discoverAndRegisterProviders() {
        LogSystem.systemLog("开始发现并注册兼容性提供者...");
        
        // 注册常见的铁路和交通类模组
        registerCommonRailwayMods();
        
        // 注册常见的建筑和装饰类模组
        registerCommonBuildingMods();
        
        // 注册常见的技术和工具类模组
        registerCommonTechMods();
        
        // 注册其他类型的常见模组
        registerOtherCommonMods();
        
        LogSystem.systemLog("兼容性提供者发现完成，共注册 " + providers.size() + " 个提供者");
    }
    
    /**
     * 注册常见的铁路和交通类模组
     */
    private void registerCommonRailwayMods() {
        // 这里注册常见的铁路和交通类模组的兼容性提供者
        
        // 1. Minecraft Transit Railway (MTR)
        registerProvider(new CompatibilityProvider("mtr", "Minecraft Transit Railway", "4.0.0+") {
            @Override
            public IModCompatibility createCompatibility() {
                return new NewMTRIntegration();
            }
        });
        
        // 2. Real Train Mod (RTM)
        registerProvider(new CompatibilityProvider("rtm", "Real Train Mod", "1.0.0+") {
            @Override
            public IModCompatibility createCompatibility() {
                // 检查是否已经存在RTM集成类
                try {
                    Class<?> rtmIntegrationClass = Class.forName("com.krt.mod.compat.RTMIntegration");
                    return (IModCompatibility) rtmIntegrationClass.getDeclaredConstructor().newInstance();
                } catch (Exception e) {
                    LogSystem.warningLog("未找到RTM兼容性集成类，使用基础实现");
                    return new BasicModCompatibility("rtm", "Real Train Mod", "1.0.0+");
                }
            }
        });
        
        // 3. Better Railroads
        registerProvider(new CompatibilityProvider("betterrailroads", "Better Railroads", "1.0.0+") {
            @Override
            public IModCompatibility createCompatibility() {
                return new BasicModCompatibility("betterrailroads", "Better Railroads", "1.0.0+");
            }
        });
        
        // 4. Immersive Railroading
        registerProvider(new CompatibilityProvider("immersiverailroading", "Immersive Railroading", "1.8.0+") {
            @Override
            public IModCompatibility createCompatibility() {
                return new BasicModCompatibility("immersiverailroading", "Immersive Railroading", "1.8.0+");
            }
        });
        
        LogSystem.debugLog("已注册常见铁路和交通类模组的兼容性提供者");
    }
    
    /**
     * 注册常见的建筑和装饰类模组
     */
    private void registerCommonBuildingMods() {
        // 这里注册常见的建筑和装饰类模组的兼容性提供者
        
        // 1. Create
        registerProvider(new CompatibilityProvider("create", "Create", "0.5.1+") {
            @Override
            public IModCompatibility createCompatibility() {
                return new BasicModCompatibility("create", "Create", "0.5.1+");
            }
        });
        
        // 2. Macaw's Bridges
        registerProvider(new CompatibilityProvider("macawsbridges", "Macaw's Bridges", "1.0.0+") {
            @Override
            public IModCompatibility createCompatibility() {
                return new BasicModCompatibility("macawsbridges", "Macaw's Bridges", "1.0.0+");
            }
        });
        
        // 3. Chisel & Bits
        registerProvider(new CompatibilityProvider("chisel", "Chisel & Bits", "1.0.0+") {
            @Override
            public IModCompatibility createCompatibility() {
                return new BasicModCompatibility("chisel", "Chisel & Bits", "1.0.0+");
            }
        });
        
        LogSystem.debugLog("已注册常见建筑和装饰类模组的兼容性提供者");
    }
    
    /**
     * 注册常见的技术和工具类模组
     */
    private void registerCommonTechMods() {
        // 这里注册常见的技术和工具类模组的兼容性提供者
        
        // 1. Immersive Engineering
        registerProvider(new CompatibilityProvider("immersiveengineering", "Immersive Engineering", "1.19.2-7.2.0+") {
            @Override
            public IModCompatibility createCompatibility() {
                return new BasicModCompatibility("immersiveengineering", "Immersive Engineering", "1.19.2-7.2.0+");
            }
        });
        
        // 2. IndustrialCraft 2
        registerProvider(new CompatibilityProvider("ic2", "IndustrialCraft 2", "2.0.0+") {
            @Override
            public IModCompatibility createCompatibility() {
                return new BasicModCompatibility("ic2", "IndustrialCraft 2", "2.0.0+");
            }
        });
        
        // 3. Refined Storage
        registerProvider(new CompatibilityProvider("refinedstorage", "Refined Storage", "1.11.0+") {
            @Override
            public IModCompatibility createCompatibility() {
                return new BasicModCompatibility("refinedstorage", "Refined Storage", "1.11.0+");
            }
        });
        
        LogSystem.debugLog("已注册常见技术和工具类模组的兼容性提供者");
    }
    
    /**
     * 注册其他类型的常见模组
     */
    private void registerOtherCommonMods() {
        // 这里注册其他类型的常见模组的兼容性提供者
        
        // 1. ModMenu
        registerProvider(new CompatibilityProvider("modmenu", "ModMenu", "4.0.0+") {
            @Override
            public IModCompatibility createCompatibility() {
                return new NewModMenuIntegration();
            }
        });
        
        // 2. Cloth Config
        registerProvider(new CompatibilityProvider("cloth-config2", "Cloth Config", "6.0.0+") {
            @Override
            public IModCompatibility createCompatibility() {
                return new BasicModCompatibility("cloth-config2", "Cloth Config", "6.0.0+");
            }
        });
        
        // 3. JEI (Just Enough Items)
        registerProvider(new CompatibilityProvider("jei", "Just Enough Items", "11.0.0+") {
            @Override
            public IModCompatibility createCompatibility() {
                return new BasicModCompatibility("jei", "Just Enough Items", "11.0.0+");
            }
        });
        
        // 4. WAILA (What Am I Looking At)
        registerProvider(new CompatibilityProvider("waila", "WAILA", "1.19.2-1.0.0+") {
            @Override
            public IModCompatibility createCompatibility() {
                return new BasicModCompatibility("waila", "WAILA", "1.19.2-1.0.0+");
            }
        });
        
        LogSystem.debugLog("已注册其他类型的常见模组的兼容性提供者");
    }
    
    /**
     * 为所有已加载的模组创建并注册兼容性实现
     * @param manager 兼容性管理器
     */
    public void createAndRegisterCompatibilities(CompatibilityManager manager) {
        LogSystem.systemLog("开始为已加载的模组创建兼容性实现...");
        
        int count = 0;
        
        for (Map.Entry<String, CompatibilityProvider> entry : providers.entrySet()) {
            String modId = entry.getKey();
            CompatibilityProvider provider = entry.getValue();
            
            // 检查模组是否已加载
            if (FabricLoader.getInstance().isModLoaded(modId)) {
                try {
                    // 创建兼容性实现
                    IModCompatibility compatibility = provider.createCompatibility();
                    
                    // 注册到兼容性管理器
                    manager.registerCompatibility(compatibility);
                    
                    count++;
                    LogSystem.systemLog("已为模组创建兼容性实现: " + provider.getModName() + " (" + modId + ")");
                } catch (Exception e) {
                    LogSystem.error("创建" + provider.getModName() + "兼容性实现失败: " + e.getMessage());
                }
            }
        }
        
        LogSystem.systemLog("已为 " + count + " 个已加载的模组创建兼容性实现");
    }
    
    /**
     * 兼容性提供者接口
     * 负责为特定模组创建兼容性实现
     */
    public abstract static class CompatibilityProvider {
        private final String modId;
        private final String modName;
        private final String versionRange;
        
        /**
         * 构造函数
         * @param modId 模组ID
         * @param modName 模组名称
         * @param versionRange 版本范围
         */
        public CompatibilityProvider(String modId, String modName, String versionRange) {
            this.modId = modId;
            this.modName = modName;
            this.versionRange = versionRange;
        }
        
        /**
         * 获取模组ID
         */
        public String getModId() {
            return modId;
        }
        
        /**
         * 获取模组名称
         */
        public String getModName() {
            return modName;
        }
        
        /**
         * 获取版本范围
         */
        public String getVersionRange() {
            return versionRange;
        }
        
        /**
         * 创建兼容性实现
         * @return 兼容性实现对象
         */
        public abstract IModCompatibility createCompatibility();
    }
}