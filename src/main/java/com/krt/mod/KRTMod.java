package com.krt.mod;

import com.krt.mod.block.ModBlocks;
import com.krt.mod.compat.CompatibilityManager;
import com.krt.mod.compat.NewMTRIntegration;
import com.krt.mod.compat.NewModMenuIntegration;
import com.krt.mod.entity.ModEntities;
import com.krt.mod.item.KRTItemGroup;
import com.krt.mod.item.ModItems;
import com.krt.mod.screen.ModScreens;
import com.krt.mod.sound.ModSounds;
import com.krt.mod.system.AppendPackageSystem;
import com.krt.mod.system.LanguageSystem;
import com.krt.mod.system.LogSystem;
import com.krt.mod.system.PlayerSystem;
import com.krt.mod.texture.SVGTextureLoader;
import com.krt.mod.command.CommandRegistry;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public class KRTMod implements ModInitializer {
    public static final String MOD_ID = "krt";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        // 初始化日志系统
        LogSystem.initialize();
        
        // 初始化命令系统
        LOGGER.info("注册命令系统...");
        CommandRegistry.initialize();

        // 初始化声音事件
        ModSounds.registerAllSounds();

        // 初始化SVG纹理加载器
        SVGTextureLoader.initialize();

        // 初始化纹理引用修复器
        TextureReferenceFixer.fixAllReferences();

        // 初始化语言系统
        LanguageSystem.initialize();

        // 初始化方块
        ModBlocks.registerAllBlocks();

        // 初始化物品
        ModItems.registerAllItems();

        // 初始化物品分组
        KRTItemGroup.registerItemGroups();

        // 初始化方块实体
        ModEntities.registerAllBlockEntities();

        // 初始化实体
        ModEntities.registerAllEntities();

        // 初始化屏幕处理器类型
        ModScreens.registerAllScreenHandlers();

        // 初始化追加包系统
        AppendPackageSystem.initialize();

        // 初始化玩家系统
        PlayerSystem.initialize();

        // 初始化兼容性管理器
        initializeCompatibilityManager();

        // 初始化ModMenu集成（如果安装了ModMenu）
        initializeModMenuIntegration();

        // 日志输出
        LOGGER.info("昆明轨道交通模组 (KRT) 已成功加载!");
    }

    /**
     * 初始化兼容性管理器并注册所有兼容模组
     */
    private void initializeCompatibilityManager() {
        try {
            // 获取兼容性管理器实例
            CompatibilityManager manager = CompatibilityManager.getInstance();
            
            // 获取兼容性注册表实例
            CompatibilityRegistry registry = CompatibilityRegistry.getInstance();
            
            // 发现并注册所有兼容性提供者
            registry.discoverAndRegisterProviders();
            
            // 为所有已加载的模组创建并注册兼容性实现
            registry.createAndRegisterCompatibilities(manager);
            
            // 初始化所有兼容性
            manager.initializeAll();
            
            LOGGER.info("兼容性管理器初始化完成，已支持 " + manager.getInitializedCount() + " 个模组");
        } catch (Exception e) {
            LOGGER.error("兼容性管理器初始化失败", e);
        }
    }

    // 移除了未使用的registerCommonModCompatibilities方法，
    // 兼容性注册功能现在由CompatibilityRegistry类统一管理

    /**
     * 初始化ModMenu集成（通过反射避免直接依赖）
     */
    private void initializeModMenuIntegration() {
        try {
            if (FabricLoader.getInstance().isModLoaded("modmenu")) {
                // 获取ModMenuApi类
                Class<?> modMenuApiClass = Class.forName("com.terraformersmc.modmenu.api.ModMenuApi");
                Class<?> modMenuImplementationClass = Class.forName("com.terraformersmc.modmenu.api.ModMenuApi$Impl");
                
                // 获取register方法
                Method registerMethod = modMenuImplementationClass.getMethod("register", modMenuApiClass);
                
                // 创建一个实现了ModMenuApi接口的对象
                Object modMenuApiInstance = java.lang.reflect.Proxy.newProxyInstance(
                        getClass().getClassLoader(),
                        new Class<?>[]{modMenuApiClass},
                        (proxy, method, args) -> {
                            if ("getModConfigScreenFactory".equals(method.getName())) {
                                // 获取ModMenu兼容性实现
                                NewModMenuIntegration modMenuIntegration = new NewModMenuIntegration();
                                return modMenuIntegration.getModConfigScreenFactory();
                            }
                            return null;
                        }
                );
                
                // 注册ModMenuApi实现
                registerMethod.invoke(null, modMenuApiInstance);
                
                LOGGER.info("ModMenu集成初始化成功");
            }
        } catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException | 
                 InvocationTargetException e) {
            LOGGER.warn("ModMenu集成初始化失败", e);
        }
    }
}