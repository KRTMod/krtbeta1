package com.krt.api;

import com.krt.api.data.json.KRTJsonData;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * KRTAddonAPI接口的具体实现类
 * 提供扩展包API的具体功能实现
 */
public class KRTAddonAPIImpl implements KRTAddonAPI {
    
    // 扩展包存储Map
    private Map<String, AddonInfo> addons = new HashMap<>();
    
    // 列车模型存储Map
    private Map<String, String> trainModels = new HashMap<>();
    
    // 车站类型存储Map
    private Map<String, StationTypeInfo> stationTypes = new HashMap<>();
    
    // 轨道材质存储Map
    private Map<String, String> trackMaterials = new HashMap<>();
    
    // GSON实例用于JSON处理
    private Gson gson = new Gson();
    
    // 扩展包目录
    private static final String ADDON_DIR = "krt_addons";
    
    @Override
    public boolean registerAddon(String addonId, String addonName, String version, String author) {
        if (addons.containsKey(addonId)) {
            return false;
        }
        
        AddonInfo addonInfo = new AddonInfo(addonId, addonName, version, author);
        addons.put(addonId, addonInfo);
        
        // 创建扩展包目录
        File addonDir = new File(ADDON_DIR + File.separator + addonId);
        if (!addonDir.exists()) {
            addonDir.mkdirs();
        }
        
        return true;
    }
    
    @Override
    public boolean loadAddonData(String addonId, KRTJsonData data) {
        if (!addons.containsKey(addonId)) {
            return false;
        }
        
        // 保存数据到扩展包目录
        File dataFile = new File(ADDON_DIR + File.separator + addonId + File.separator + "data.json");
        try (FileWriter writer = new FileWriter(dataFile)) {
            gson.toJson(data, writer);
            return true;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }
    
    @Override
    public boolean unloadAddon(String addonId) {
        if (!addons.containsKey(addonId)) {
            return false;
        }
        
        addons.remove(addonId);
        
        // 可选：删除扩展包目录中的数据文件
        File dataFile = new File(ADDON_DIR + File.separator + addonId + File.separator + "data.json");
        if (dataFile.exists()) {
            dataFile.delete();
        }
        
        return true;
    }
    
    @Override
    public boolean isAddonLoaded(String addonId) {
        return addons.containsKey(addonId);
    }
    
    @Override
    public boolean registerTrainModel(String modelId, String modelData) {
        trainModels.put(modelId, modelData);
        return true;
    }
    
    @Override
    public boolean registerStationType(String typeId, String typeName, String typeData) {
        stationTypes.put(typeId, new StationTypeInfo(typeId, typeName, typeData));
        return true;
    }
    
    @Override
    public boolean registerTrackMaterial(String materialId, String texturePath) {
        trackMaterials.put(materialId, texturePath);
        return true;
    }
    
    @Override
    public String getAddonConfig(String addonId) {
        if (!addons.containsKey(addonId)) {
            return null;
        }
        
        File configFile = new File(ADDON_DIR + File.separator + addonId + File.separator + "config.json");
        if (!configFile.exists()) {
            return "{}";
        }
        
        try (FileReader reader = new FileReader(configFile)) {
            return gson.fromJson(reader, JsonObject.class).toString();
        } catch (IOException e) {
            e.printStackTrace();
            return "{}";
        }
    }
    
    @Override
    public boolean saveAddonConfig(String addonId, String config) {
        if (!addons.containsKey(addonId)) {
            return false;
        }
        
        File addonDir = new File(ADDON_DIR + File.separator + addonId);
        if (!addonDir.exists()) {
            addonDir.mkdirs();
        }
        
        File configFile = new File(addonDir + File.separator + "config.json");
        try (FileWriter writer = new FileWriter(configFile)) {
            writer.write(config);
            return true;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }
    
    // 扩展包信息内部类
    private static class AddonInfo {
        private String id;
        private String name;
        private String version;
        private String author;
        
        public AddonInfo(String id, String name, String version, String author) {
            this.id = id;
            this.name = name;
            this.version = version;
            this.author = author;
        }
        
        // Getters
        public String getId() { return id; }
        public String getName() { return name; }
        public String getVersion() { return version; }
        public String getAuthor() { return author; }
    }
    
    // 车站类型信息内部类
    private static class StationTypeInfo {
        private String id;
        private String name;
        private String data;
        
        public StationTypeInfo(String id, String name, String data) {
            this.id = id;
            this.name = name;
            this.data = data;
        }
        
        // Getters
        public String getId() { return id; }
        public String getName() { return name; }
        public String getData() { return data; }
    }
}