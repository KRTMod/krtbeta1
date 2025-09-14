package com.krt.mod;

import net.fabricmc.api.ModInitializer;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;
import net.minecraft.util.registry.Registry;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.krt.mod.block.KRTBlockEntities;
import com.krt.mod.system.LogSystem;
import com.krt.mod.system.LanguageSystem;
import com.krt.mod.api.ModInterface;

public class KRTMod implements ModInitializer {
    public static final String MOD_ID = "krt";
    public static final Logger LOGGER = LogManager.getLogger(MOD_ID);

    // 声音事件注册
    public static final SoundEvent TRACK_COLLISION_SOUND = registerSoundEvent("track_collision");
    public static final SoundEvent SWITCH_COLLISION_SOUND = registerSoundEvent("switch_collision");
    public static final SoundEvent ANNOUNCEMENT_STATION_SOUND = registerSoundEvent("announcement_station");
    public static final SoundEvent ANNOUNCEMENT_ARRIVED_SOUND = registerSoundEvent("announcement_arrived");
    public static final SoundEvent EMERGENCY_BRAKE_SOUND = registerSoundEvent("emergency_brake");

    private static SoundEvent registerSoundEvent(String id) {
        Identifier identifier = new Identifier(MOD_ID, id);
        return Registry.register(Registry.SOUND_EVENT, identifier, new SoundEvent(identifier));
    }

    @Override
    public void onInitialize() {
        LOGGER.info("KRT 昆明轨道交通模组已加载!");
        
        // 初始化日志系统
        LogSystem.init();
        LogSystem.systemLog("KRT 昆明轨道交通模组初始化开始");
        
        // 初始化语言系统
        LanguageSystem.init();
        
        // 初始化方块实体
        KRTBlockEntities.registerBlockEntities();
        
        // 初始化追加包系统
        ModInterface.getInstance().initInterfaceSystem();
        
        // 初始化其他组件
        Init.init();
        
        LogSystem.systemLog("KRT 昆明轨道交通模组初始化完成");
    }
}