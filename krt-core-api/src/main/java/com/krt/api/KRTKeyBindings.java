package com.krt.api;

import java.util.HashMap;
import java.util.Map;

/**
 * KRT模组按键绑定管理类
 * 管理列车驾驶系统的控制键位
 */
public class KRTKeyBindings {
    // 默认控制键位
    public static final String KEY_FORWARD = "key.forward";
    public static final String KEY_BACKWARD = "key.backward";
    public static final String KEY_CHANGE_GEAR = "key.change_gear";
    public static final String KEY_TOGGLE_DOORS = "key.toggle_doors";
    public static final String KEY_HORN = "key.horn";
    public static final String KEY_EMERGENCY_BRAKE = "key.emergency_brake";
    
    // 键位映射
    private Map<String, String> keyBindings = new HashMap<>();
    
    // 键位状态
    private Map<String, Boolean> keyStates = new HashMap<>();

    /**
     * 构造函数，初始化默认键位
     */
    public KRTKeyBindings() {
        // 初始化默认键位
        keyBindings.put(KEY_FORWARD, "W");
        keyBindings.put(KEY_BACKWARD, "S");
        keyBindings.put(KEY_CHANGE_GEAR, "A");
        keyBindings.put(KEY_TOGGLE_DOORS, "D");
        keyBindings.put(KEY_HORN, "SPACE");
        keyBindings.put(KEY_EMERGENCY_BRAKE, "E");
        
        // 初始化键位状态
        for (String key : keyBindings.keySet()) {
            keyStates.put(key, false);
        }
    }

    /**
     * 获取指定功能的键位
     * @param function 功能名称
     * @return 键位字符
     */
    public String getKeyBinding(String function) {
        return keyBindings.get(function);
    }

    /**
     * 设置指定功能的键位
     * @param function 功能名称
     * @param key 新的键位字符
     * @return 是否设置成功
     */
    public boolean setKeyBinding(String function, String key) {
        if (keyBindings.containsKey(function) && key != null && !key.isEmpty()) {
            keyBindings.put(function, key);
            return true;
        }
        return false;
    }

    /**
     * 获取键位状态
     * @param function 功能名称
     * @return 键位是否被按下
     */
    public boolean isKeyPressed(String function) {
        return keyStates.getOrDefault(function, false);
    }

    /**
     * 设置键位状态
     * @param function 功能名称
     * @param pressed 键位是否被按下
     */
    public void setKeyState(String function, boolean pressed) {
        if (keyStates.containsKey(function)) {
            keyStates.put(function, pressed);
        }
    }

    /**
     * 获取所有键位映射
     * @return 键位映射表
     */
    public Map<String, String> getAllKeyBindings() {
        return new HashMap<>(keyBindings);
    }

    /**
     * 重置所有键位到默认设置
     */
    public void resetKeyBindings() {
        keyBindings.put(KEY_FORWARD, "W");
        keyBindings.put(KEY_BACKWARD, "S");
        keyBindings.put(KEY_CHANGE_GEAR, "A");
        keyBindings.put(KEY_TOGGLE_DOORS, "D");
        keyBindings.put(KEY_HORN, "SPACE");
        keyBindings.put(KEY_EMERGENCY_BRAKE, "E");
    }

    /**
     * 检查按键是否有效
     * @param key 按键字符
     * @return 是否有效
     */
    public boolean isValidKey(String key) {
        // 简单的键位有效性检查
        return key != null && !key.isEmpty() && key.length() <= 10;
    }

    /**
     * 获取按键的显示名称
     * @param key 按键字符
     * @return 显示名称
     */
    public static String getKeyDisplayName(String key) {
        switch (key) {
            case "SPACE":
                return "空格";
            case "LEFT":
                return "左箭头";
            case "RIGHT":
                return "右箭头";
            case "UP":
                return "上箭头";
            case "DOWN":
                return "下箭头";
            default:
                return key;
        }
    }
}