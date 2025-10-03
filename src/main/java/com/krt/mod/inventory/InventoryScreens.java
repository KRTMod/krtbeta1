package com.krt.mod.inventory;

import com.krt.mod.KRTMod;
import com.krt.mod.inventory.screen.PowerGeneratorScreenHandler;
import net.fabricmc.fabric.api.screenhandler.v1.ScreenHandlerRegistry;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.util.Identifier;

/**
 * 注册所有模组的屏幕处理器类型
 */
public class InventoryScreens {
    // 能源系统相关屏幕处理器
    public static final ScreenHandlerType<PowerGeneratorScreenHandler> POWER_GENERATOR_SCREEN_HANDLER = 
            ScreenHandlerRegistry.registerSimple(
                    new Identifier(KRTMod.MOD_ID, "power_generator"),
                    PowerGeneratorScreenHandler::new);
    
    /**
     * 注册所有屏幕处理器
     */
    public static void registerAllScreenHandlers() {
        KRTMod.LOGGER.info("能源系统屏幕处理器注册完成");
    }
}