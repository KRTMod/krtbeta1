package com.krt.api;

import com.krt.api.data.json.KRTJsonData;

/**
 * KRT轨道交通模组扩展包API接口
 * 用于支持追加包制作
 */
public interface KRTAddonAPI {
    
    /**
     * 注册扩展包
     * @param addonId 扩展包ID
     * @param addonName 扩展包名称
     * @param version 扩展包版本
     * @param author 扩展包作者
     * @return 是否注册成功
     */
    boolean registerAddon(String addonId, String addonName, String version, String author);
    
    /**
     * 加载扩展包数据
     * @param addonId 扩展包ID
     * @param data 扩展包数据
     * @return 是否加载成功
     */
    boolean loadAddonData(String addonId, KRTJsonData data);
    
    /**
     * 卸载扩展包
     * @param addonId 扩展包ID
     * @return 是否卸载成功
     */
    boolean unloadAddon(String addonId);
    
    /**
     * 检查扩展包是否已加载
     * @param addonId 扩展包ID
     * @return 是否已加载
     */
    boolean isAddonLoaded(String addonId);
    
    /**
     * 注册新的列车模型
     * @param modelId 模型ID
     * @param modelData 模型数据（JSON格式）
     * @return 是否注册成功
     */
    boolean registerTrainModel(String modelId, String modelData);
    
    /**
     * 注册新的车站类型
     * @param typeId 类型ID
     * @param typeName 类型名称
     * @param typeData 类型数据（JSON格式）
     * @return 是否注册成功
     */
    boolean registerStationType(String typeId, String typeName, String typeData);
    
    /**
     * 注册新的轨道材质
     * @param materialId 材质ID
     * @param texturePath 贴图路径
     * @return 是否注册成功
     */
    boolean registerTrackMaterial(String materialId, String texturePath);
    
    /**
     * 获取扩展包配置
     * @param addonId 扩展包ID
     * @return 配置数据（JSON格式）
     */
    String getAddonConfig(String addonId);
    
    /**
     * 保存扩展包配置
     * @param addonId 扩展包ID
     * @param config 配置数据（JSON格式）
     * @return 是否保存成功
     */
    boolean saveAddonConfig(String addonId, String config);
}