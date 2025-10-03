package com.krt.mod.sound;

import com.krt.mod.KRTMod;
import com.krt.mod.system.LogSystem;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;
import net.minecraft.util.registry.Registry;

/**
 * 声音事件注册类
 * 负责注册模组中的所有声音事件
 */
public class ModSounds {
    
    // 轨道相关声音
    public static SoundEvent TRACK_COLLISION_SOUND;
    public static SoundEvent TRACK_MOVING_SOUND;
    public static SoundEvent SWITCH_COLLISION_SOUND;
    public static SoundEvent SWITCH_TOGGLE_SOUND;
    
    // 列车相关声音
    public static SoundEvent TRAIN_MOVING_SOUND;
    public static SoundEvent TRAIN_HORN_SOUND;
    public static SoundEvent TRAIN_BRAKE_SOUND;
    public static SoundEvent EMERGENCY_BRAKE_SOUND;
    
    // 门相关声音
    public static SoundEvent DOOR_OPEN_SOUND;
    public static SoundEvent DOOR_CLOSE_SOUND;
    public static SoundEvent SHIELD_DOOR_OPEN_SOUND;
    public static SoundEvent SHIELD_DOOR_CLOSE_SOUND;
    
    // 车站广播声音
    public static SoundEvent ANNOUNCEMENT_STATION_SOUND;
    public static SoundEvent ANNOUNCEMENT_ARRIVED_SOUND;
    public static SoundEvent ANNOUNCEMENT_ARRIVING_SOUND;
    public static SoundEvent ANNOUNCEMENT_DEPARTING_SOUND;
    
    // 其他声音
    public static SoundEvent SIGNAL_CHANGE_SOUND;
    public static SoundEvent CONTROL_PANEL_BUTTON_SOUND;
    public static SoundEvent ALARM_SOUND;
    public static SoundEvent BUTTON_CLICK_SOUND;
    public static SoundEvent POWER_ON_SOUND;
    public static SoundEvent POWER_OFF_SOUND;
    public static SoundEvent ALIGN_SOUND;
    
    /**
     * 注册所有声音事件
     */
    public static void registerAllSounds() {
        LogSystem.systemLog("开始注册声音事件...");
        
        // 注册轨道相关声音
        registerTrackSounds();
        
        // 注册列车相关声音
        registerTrainSounds();
        
        // 注册门相关声音
        registerDoorSounds();
        
        // 注册车站广播声音
        registerAnnouncementSounds();
        
        // 注册其他声音
        registerOtherSounds();
        
        LogSystem.systemLog("声音事件注册完成");
    }
    
    /**
     * 注册轨道相关声音
     */
    private static void registerTrackSounds() {
        TRACK_COLLISION_SOUND = registerSoundEvent("track_collision");
        TRACK_MOVING_SOUND = registerSoundEvent("track_moving");
        SWITCH_COLLISION_SOUND = registerSoundEvent("switch_collision");
        SWITCH_TOGGLE_SOUND = registerSoundEvent("switch_toggle");
        LogSystem.debugLog("注册轨道相关声音");
    }
    
    /**
     * 注册列车相关声音
     */
    private static void registerTrainSounds() {
        TRAIN_MOVING_SOUND = registerSoundEvent("train_moving");
        TRAIN_HORN_SOUND = registerSoundEvent("train_horn");
        TRAIN_BRAKE_SOUND = registerSoundEvent("train_brake");
        EMERGENCY_BRAKE_SOUND = registerSoundEvent("emergency_brake");
        LogSystem.debugLog("注册列车相关声音");
    }
    
    /**
     * 注册门相关声音
     */
    private static void registerDoorSounds() {
        DOOR_OPEN_SOUND = registerSoundEvent("door_open");
        DOOR_CLOSE_SOUND = registerSoundEvent("door_close");
        SHIELD_DOOR_OPEN_SOUND = registerSoundEvent("shield_door_open");
        SHIELD_DOOR_CLOSE_SOUND = registerSoundEvent("shield_door_close");
        LogSystem.debugLog("注册门相关声音");
    }
    
    /**
     * 注册车站广播声音
     */
    private static void registerAnnouncementSounds() {
        ANNOUNCEMENT_STATION_SOUND = registerSoundEvent("announcement_station");
        ANNOUNCEMENT_ARRIVED_SOUND = registerSoundEvent("announcement_arrived");
        ANNOUNCEMENT_ARRIVING_SOUND = registerSoundEvent("announcement_arriving");
        ANNOUNCEMENT_DEPARTING_SOUND = registerSoundEvent("announcement_departing");
        LogSystem.debugLog("注册车站广播声音");
    }
    
    /**
     * 注册其他声音
     */
    private static void registerOtherSounds() {
        SIGNAL_CHANGE_SOUND = registerSoundEvent("signal_change");
        CONTROL_PANEL_BUTTON_SOUND = registerSoundEvent("control_panel_button");
        ALARM_SOUND = registerSoundEvent("alarm");
        BUTTON_CLICK_SOUND = registerSoundEvent("button_click");
        POWER_ON_SOUND = registerSoundEvent("power_on");
        POWER_OFF_SOUND = registerSoundEvent("power_off");
        ALIGN_SOUND = registerSoundEvent("align");
        LogSystem.debugLog("注册其他声音");
    }
    
    /**
     * 注册单个声音事件的辅助方法
     * @param id 声音事件ID
     * @return 注册后的声音事件
     */
    private static SoundEvent registerSoundEvent(String id) {
        Identifier identifier = new Identifier(KRTMod.MOD_ID, id);
        SoundEvent soundEvent = new SoundEvent(identifier);
        Registry.register(Registry.SOUND_EVENT, identifier, soundEvent);
        LogSystem.debugLog("注册声音事件: " + id + " (ID: " + identifier + ")");
        return soundEvent;
    }
}