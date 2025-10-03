package com.krt.mod.system;

import com.krt.mod.KRTMod;
import net.fabricmc.loader.api.FabricLoader;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/**
 * 追加包系统
 * 负责加载和管理模组的追加内容包，支持自定义列车模型、线路主题、音效包等
 */
public class AppendPackageSystem {
    
    // 追加包目录
    private static final String APPEND_PACKAGE_DIR = "krt_append_packages";
    
    // 已加载的追加包列表
    private static final List<AppendPackage> loadedPackages = new ArrayList<>();
    
    // 内容类型枚举
    public enum ContentType {
        TRAIN_MODEL,      // 自定义列车模型
        LINE_THEME,       // 线路主题
        SOUND_PACK,       // 音效包
        STATION_ASSET,    // 车站资产
        SIGNAL_SYSTEM,    // 信号系统
        SCENERY_PACK,     // 场景包
        TEXTURE_PACK,     // 纹理包
        LANGUAGE_PACK,    // 语言包
        SCRIPT_PACK       // 脚本包
    }
    
    // 单例实例
    private static final AppendPackageSystem instance = new AppendPackageSystem();
    
    /**
     * 私有构造函数
     */
    private AppendPackageSystem() {
    }
    
    /**
     * 获取单例实例
     * @return AppendPackageSystem实例
     */
    public static AppendPackageSystem getInstance() {
        return instance;
    }
    
    /**
     * 初始化追加包系统
     */
    public static void initialize() {
        LogSystem.systemLog("初始化追加包系统...");
        
        try {
            // 创建追加包目录（如果不存在）
            createAppendPackageDirectory();
            
            // 扫描并加载所有追加包
            scanAndLoadPackages();
            
            LogSystem.systemLog("追加包系统初始化完成，已加载 " + loadedPackages.size() + " 个追加包");
        } catch (Exception e) {
            LogSystem.error("追加包系统初始化失败: " + e.getMessage());
            KRTMod.LOGGER.error("追加包系统初始化失败", e);
        }
    }
    
    /**
     * 创建追加包目录
     */
    private static void createAppendPackageDirectory() {
        try {
            Path packageDir = FabricLoader.getInstance().getGameDir().resolve(APPEND_PACKAGE_DIR);
            if (!Files.exists(packageDir)) {
                Files.createDirectories(packageDir);
                LogSystem.debugLog("已创建追加包目录: " + packageDir);
            }
        } catch (IOException e) {
            LogSystem.warningLog("创建追加包目录失败: " + e.getMessage());
        }
    }
    
    /**
     * 扫描并加载所有追加包
     */
    private static void scanAndLoadPackages() {
        try {
            Path packageDir = FabricLoader.getInstance().getGameDir().resolve(APPEND_PACKAGE_DIR);
            File[] packageFiles = packageDir.toFile().listFiles((dir, name) -> {
                return name.toLowerCase().endsWith(".zip") || new File(dir, name).isDirectory();
            });
            
            if (packageFiles == null || packageFiles.length == 0) {
                LogSystem.debugLog("未找到追加包");
                return;
            }
            
            LogSystem.systemLog("找到 " + packageFiles.length + " 个可能的追加包文件");
            
            for (File file : packageFiles) {
                try {
                    loadAppendPackage(file);
                } catch (Exception e) {
                    LogSystem.error("加载追加包失败: " + file.getName() + ", 错误: " + e.getMessage());
                }
            }
        } catch (Exception e) {
            LogSystem.error("扫描追加包时发生错误: " + e.getMessage());
        }
    }
    
    /**
     * 加载单个追加包
     * @param file 追加包文件或目录
     */
    /**
     * 检查包冲突
     * @param packageToCheck 要检查的包
     * @return 是否存在冲突
     */
    private static boolean checkPackageConflicts(AppendPackage packageToCheck) {
        for (AppendPackage loadedPackage : loadedPackages) {
            if (packageToCheck.getConflicts().contains(loadedPackage.getName()) || 
                loadedPackage.getConflicts().contains(packageToCheck.getName())) {
                LogSystem.warningLog("发现包冲突: " + packageToCheck.getName() + " 与 " + loadedPackage.getName());
                return true;
            }
        }
        return false;
    }
    
    /**
     * 检查包依赖
     * @param packageToCheck 要检查的包
     * @return 是否满足所有依赖
     */
    private static boolean checkPackageDependencies(AppendPackage packageToCheck) {
        for (Map.Entry<String, String> dependency : packageToCheck.getDependencies().entrySet()) {
            String depId = dependency.getKey();
            boolean found = false;
            
            for (AppendPackage loadedPackage : loadedPackages) {
                if (loadedPackage.getName().equals(depId)) {
                    // 这里可以添加版本检查逻辑
                    found = true;
                    break;
                }
            }
            
            if (!found) {
                LogSystem.warningLog("依赖缺失: " + packageToCheck.getName() + " 需要 " + depId);
                return false;
            }
        }
        return true;
    }
    
    private static void loadAppendPackage(File file) {
        try {
            // 创建追加包对象
            AppendPackage appendPackage = new AppendPackage(file.getName(), file.getPath());
            
            // 尝试读取包配置文件（如果存在）
            File configFile = new File(file, "package.json");
            if (file.isDirectory() && configFile.exists()) {
                // 在实际实现中，这里应该解析package.json文件
                // 添加版本、作者、内容类型等信息
                // 这里仅作示例
                appendPackage.addContentType(ContentType.TEXTURE_PACK);
                if (file.getName().contains("train")) {
                    appendPackage.addContentType(ContentType.TRAIN_MODEL);
                }
                if (file.getName().contains("sound")) {
                    appendPackage.addContentType(ContentType.SOUND_PACK);
                }
            }
            
            // 检查冲突
            if (checkPackageConflicts(appendPackage)) {
                LogSystem.warningLog("由于冲突，跳过加载追加包: " + file.getName());
                return;
            }
            
            // 检查依赖
            if (!checkPackageDependencies(appendPackage)) {
                LogSystem.warningLog("由于依赖缺失，跳过加载追加包: " + file.getName());
                return;
            }
            
            // 加载包内容
            loadedPackages.add(appendPackage);
            
            LogSystem.systemLog("成功加载追加包: " + file.getName() + " 类型: " + appendPackage.getContentTypes());
        } catch (Exception e) {
            throw new RuntimeException("加载追加包失败: " + file.getName(), e);
        }
    }
    
    /**
     * 根据内容类型获取追加包
     * @param contentType 内容类型
     * @return 匹配的追加包列表
     */
    public List<AppendPackage> getPackagesByContentType(ContentType contentType) {
        List<AppendPackage> result = new ArrayList<>();
        for (AppendPackage pkg : loadedPackages) {
            if (pkg.isEnabled() && pkg.getContentTypes().contains(contentType)) {
                result.add(pkg);
            }
        }
        return result;
    }
    
    /**
     * 启用或禁用追加包
     * @param packageName 包名
     * @param enabled 是否启用
     * @return 操作是否成功
     */
    public boolean setPackageEnabled(String packageName, boolean enabled) {
        for (AppendPackage pkg : loadedPackages) {
            if (pkg.getName().equals(packageName)) {
                pkg.setEnabled(enabled);
                LogSystem.systemLog((enabled ? "启用" : "禁用") + "追加包: " + packageName);
                return true;
            }
        }
        return false;
    }
    
    /**
     * 获取所有已加载的追加包
     * @return 已加载的追加包列表
     */
    public List<AppendPackage> getLoadedPackages() {
        return Collections.unmodifiableList(loadedPackages);
    }
    
    /**
     * 追加包类
     * 表示一个已加载的追加包，包含版本控制和内容类型信息
     */
    public static class AppendPackage {
        private final String name;
        private final String path;
        private final String version;
        private final String author;
        private final String description;
        private final List<ContentType> contentTypes = new ArrayList<>();
        private final Map<String, Object> content = new HashMap<>();
        private final Map<String, String> dependencies = new HashMap<>(); // 依赖包ID -> 版本要求
        private final Set<String> conflicts = new HashSet<>(); // 冲突包ID列表
        private boolean enabled = true;
        
        /**
         * 构造函数
         * @param name 追加包名称
         * @param path 追加包路径
         */
        public AppendPackage(String name, String path) {
            this.name = name;
            this.path = path;
            this.version = "1.0.0";
            this.author = "Unknown";
            this.description = "";
        }
        
        /**
         * 高级构造函数，包含版本和作者信息
         * @param name 追加包名称
         * @param path 追加包路径
         * @param version 包版本
         * @param author 作者
         * @param description 描述
         */
        public AppendPackage(String name, String path, String version, String author, String description) {
            this.name = name;
            this.path = path;
            this.version = version;
            this.author = author;
            this.description = description;
        }
        
        /**
         * 获取追加包名称
         * @return 追加包名称
         */
        public String getName() {
            return name;
        }
        
        /**
         * 获取追加包路径
         * @return 追加包路径
         */
        public String getPath() {
            return path;
        }
        
        /**
         * 获取追加包版本
         * @return 追加包版本
         */
        public String getVersion() {
            return version;
        }
        
        /**
         * 获取作者信息
         * @return 作者名称
         */
        public String getAuthor() {
            return author;
        }
        
        /**
         * 获取包描述
         * @return 包描述文本
         */
        public String getDescription() {
            return description;
        }
        
        /**
         * 添加内容类型
         * @param contentType 内容类型
         */
        public void addContentType(ContentType contentType) {
            if (!contentTypes.contains(contentType)) {
                contentTypes.add(contentType);
            }
        }
        
        /**
         * 获取所有内容类型
         * @return 内容类型列表
         */
        public List<ContentType> getContentTypes() {
            return Collections.unmodifiableList(contentTypes);
        }
        
        /**
         * 添加依赖
         * @param packageId 依赖包ID
         * @param versionRequirement 版本要求
         */
        public void addDependency(String packageId, String versionRequirement) {
            dependencies.put(packageId, versionRequirement);
        }
        
        /**
         * 获取依赖列表
         * @return 依赖包映射
         */
        public Map<String, String> getDependencies() {
            return Collections.unmodifiableMap(dependencies);
        }
        
        /**
         * 添加冲突包
         * @param packageId 冲突包ID
         */
        public void addConflict(String packageId) {
            conflicts.add(packageId);
        }
        
        /**
         * 获取冲突包列表
         * @return 冲突包集合
         */
        public Set<String> getConflicts() {
            return Collections.unmodifiableSet(conflicts);
        }
        
        /**
         * 设置包启用状态
         * @param enabled 是否启用
         */
        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
        
        /**
         * 获取包启用状态
         * @return 是否已启用
         */
        public boolean isEnabled() {
            return enabled;
        }
        
        /**
         * 添加内容到追加包
         * @param key 内容键
         * @param value 内容值
         */
        public void addContent(String key, Object value) {
            content.put(key, value);
        }
        
        /**
         * 获取追加包中的内容
         * @param key 内容键
         * @return 内容值，如果不存在则返回null
         */
        public Object getContent(String key) {
            return content.get(key);
        }
        
        /**
         * 获取追加包中的所有内容
         * @return 内容映射
         */
        public Map<String, Object> getAllContent() {
            return Collections.unmodifiableMap(content);
        }
    }
}