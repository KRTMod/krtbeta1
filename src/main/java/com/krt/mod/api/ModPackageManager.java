package com.krt.mod.api;

import com.google.gson.*;
import com.krt.mod.system.LogSystem;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Identifier;
import net.minecraft.util.WorldSavePath;
import net.minecraft.world.World;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ModPackageManager {
    // 单例实例
    private static ModPackageManager instance;
    // Minecraft服务器实例
    private MinecraftServer server;
    // 已加载的追加包列表
    private final Map<String, PackageInfo> loadedPackages = new ConcurrentHashMap<>();
    // 包事件监听器列表
    private final List<PackageEventListener> eventListeners = new ArrayList<>();
    // JSON解析器
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    // 私有化构造函数
    private ModPackageManager() {
    }

    // 获取单例实例
    public static synchronized ModPackageManager getInstance() {
        if (instance == null) {
            instance = new ModPackageManager();
        }
        return instance;
    }

    /**
     * 初始化包管理器
     */
    public void initialize(MinecraftServer server) {
        this.server = server;
        LogSystem.systemLog("正在初始化KRT追加包管理器...");
        
        // 初始化包目录
        initPackageDirectories();
        
        // 加载所有包
        loadAllPackages();
        
        LogSystem.systemLog("KRT追加包管理器初始化完成，已加载 " + loadedPackages.size() + " 个追加包");
    }

    /**
     * 初始化包目录
     */
    private void initPackageDirectories() {
        try {
            // 获取全局包目录
            Path globalPackageDir = getGlobalPackageDirectory();
            if (!Files.exists(globalPackageDir)) {
                Files.createDirectories(globalPackageDir);
                LogSystem.systemLog("已创建全局追加包目录: " + globalPackageDir.toAbsolutePath());
            }
            
            // 获取存档包目录
            Path worldPackageDir = getWorldPackageDirectory();
            if (!Files.exists(worldPackageDir)) {
                Files.createDirectories(worldPackageDir);
                LogSystem.systemLog("已创建存档追加包目录: " + worldPackageDir.toAbsolutePath());
            }
            
            // 创建示例包
            createExamplePackage();
        } catch (IOException e) {
            LogSystem.error("初始化追加包目录失败: " + e.getMessage());
        }
    }

    /**
     * 创建示例包
     */
    private void createExamplePackage() {
        try {
            Path examplePackageDir = getGlobalPackageDirectory().resolve("krt_example_package");
            if (!Files.exists(examplePackageDir)) {
                Files.createDirectories(examplePackageDir);
                
                // 创建package.json
                JsonObject packageJson = new JsonObject();
                packageJson.addProperty("id", "krt_example_package");
                packageJson.addProperty("version", "1.0.0");
                packageJson.addProperty("name", "示例追加包");
                packageJson.addProperty("author", "KRT Team");
                packageJson.addProperty("description", "KRT模组的示例追加包，展示如何创建自定义内容");
                
                JsonArray dependencies = new JsonArray();
                dependencies.add("krt_core");
                packageJson.add("dependencies", dependencies);
                
                JsonObject content = new JsonObject();
                content.addProperty("type", "example");
                packageJson.add("content", content);
                
                Path packageJsonPath = examplePackageDir.resolve("package.json");
                try (Writer writer = new FileWriter(packageJsonPath.toFile(), StandardCharsets.UTF_8)) {
                    gson.toJson(packageJson, writer);
                }
                
                // 创建README文件
                Path readmePath = examplePackageDir.resolve("README.md");
                try (Writer writer = new FileWriter(readmePath.toFile(), StandardCharsets.UTF_8)) {
                    writer.write("# KRT示例追加包\n\n");
                    writer.write("这是一个示例追加包，用于展示如何为KRT模组创建自定义内容。\n\n");
                    writer.write("## 包结构\n");
                    writer.write("- package.json - 包的元数据和配置\n");
                    writer.write("- audio/ - 音频文件（.ogg, .mp3, .wav）\n");
                    writer.write("- textures/ - 纹理文件（.png, .jpg）\n");
                    writer.write("- videos/ - 视频文件（.mp4）\n");
                    writer.write("- lang/ - 语言文件（.json）\n");
                    writer.write("- models/ - 模型文件\n");
                    writer.write("\n## 如何创建自己的追加包\n");
                    writer.write("1. 复制此目录并重命名\n");
                    writer.write("2. 修改package.json中的信息\n");
                    writer.write("3. 添加你的资源文件\n");
                    writer.write("4. 将整个目录放入krt_packages文件夹\n");
                }
                
                LogSystem.systemLog("已创建示例追加包: " + examplePackageDir.getFileName());
            }
        } catch (IOException e) {
            LogSystem.error("创建示例追加包失败: " + e.getMessage());
        }
    }

    /**
     * 加载所有包
     */
    private void loadAllPackages() {
        try {
            // 首先加载全局包
            loadPackagesFromDirectory(getGlobalPackageDirectory());
            
            // 然后加载存档特定的包
            loadPackagesFromDirectory(getWorldPackageDirectory());
        } catch (Exception e) {
            LogSystem.error("加载追加包失败: " + e.getMessage());
        }
    }

    /**
     * 从指定目录加载包
     */
    private void loadPackagesFromDirectory(Path directory) {
        try {
            if (!Files.exists(directory) || !Files.isDirectory(directory)) {
                return;
            }
            
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory)) {
                for (Path packagePath : stream) {
                    if (Files.isDirectory(packagePath)) {
                        loadPackage(packagePath);
                    }
                }
            }
        } catch (Exception e) {
            LogSystem.error("从目录加载追加包失败: " + directory + ", 错误: " + e.getMessage());
        }
    }

    /**
     * 加载单个包
     */
    public boolean loadPackage(Path packagePath) {
        try {
            // 检查包是否包含package.json文件
            Path packageJsonPath = packagePath.resolve("package.json");
            if (!Files.exists(packageJsonPath)) {
                LogSystem.warning("追加包不包含package.json文件: " + packagePath.getFileName());
                return false;
            }
            
            // 读取package.json文件
            JsonObject packageJson;
            try (Reader reader = new FileReader(packageJsonPath.toFile(), StandardCharsets.UTF_8)) {
                packageJson = JsonParser.parseReader(reader).getAsJsonObject();
            }
            
            // 验证包的基本信息
            if (!packageJson.has("id") || !packageJson.has("version") || !packageJson.has("name")) {
                LogSystem.warning("追加包缺少必要信息: " + packagePath.getFileName());
                return false;
            }
            
            String packageId = packageJson.get("id").getAsString();
            
            // 检查包是否已经加载
            if (loadedPackages.containsKey(packageId)) {
                LogSystem.warning("追加包ID重复，跳过加载: " + packageId);
                return false;
            }
            
            // 创建包信息对象
            PackageInfo packageInfo = new PackageInfo(packageJson, packagePath);
            
            // 触发包加载前事件
            firePackagePreLoadEvent(packageInfo);
            
            // 加载包的资源
            boolean loadSuccess = loadPackageResources(packageInfo);
            
            if (loadSuccess) {
                // 将包添加到已加载列表
                loadedPackages.put(packageId, packageInfo);
                
                // 触发包加载后事件
                firePackagePostLoadEvent(packageInfo);
                
                LogSystem.systemLog("成功加载追加包: " + packageInfo.getName() + " (" + packageInfo.getId() + ", v" + packageInfo.getVersion() + ")");
                return true;
            } else {
                LogSystem.warning("加载追加包资源失败: " + packageId);
                return false;
            }
        } catch (Exception e) {
            LogSystem.error("加载追加包失败: " + packagePath.getFileName() + ", 错误: " + e.getMessage());
            return false;
        }
    }

    /**
     * 加载包的资源
     */
    private boolean loadPackageResources(PackageInfo packageInfo) {
        try {
            Path packagePath = packageInfo.getPath();
            
            // 加载音频资源
            loadAudioResources(packageInfo, packagePath.resolve("audio"));
            
            // 加载纹理资源
            loadTextureResources(packageInfo, packagePath.resolve("textures"));
            
            // 加载视频资源
            loadVideoResources(packageInfo, packagePath.resolve("videos"));
            
            // 加载语言资源
            loadLanguageResources(packageInfo, packagePath.resolve("lang"));
            
            // 加载模型资源
            loadModelResources(packageInfo, packagePath.resolve("models"));
            
            // 加载自定义配置
            loadPackageConfig(packageInfo, packagePath.resolve("config.json"));
            
            return true;
        } catch (Exception e) {
            LogSystem.error("加载追加包资源失败: " + packageInfo.getName() + ", 错误: " + e.getMessage());
            return false;
        }
    }

    /**
     * 加载音频资源
     */
    private void loadAudioResources(PackageInfo packageInfo, Path audioDir) {
        try {
            if (!Files.exists(audioDir) || !Files.isDirectory(audioDir)) {
                return;
            }
            
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(audioDir, "*.{ogg,mp3,wav}")) {
                for (Path audioPath : stream) {
                    String resourceId = packageInfo.getId() + ":" + audioPath.getFileName().toString();
                    // 在实际实现中，这里应该将音频资源注册到游戏中
                    packageInfo.addResource(ResourceType.AUDIO, resourceId, audioPath);
                    LogSystem.systemLog("加载音频资源: " + resourceId);
                }
            }
        } catch (Exception e) {
            LogSystem.error("加载音频资源失败: " + e.getMessage());
        }
    }

    /**
     * 加载纹理资源
     */
    private void loadTextureResources(PackageInfo packageInfo, Path textureDir) {
        try {
            if (!Files.exists(textureDir) || !Files.isDirectory(textureDir)) {
                return;
            }
            
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(textureDir, "*.{png,jpg}")) {
                for (Path texturePath : stream) {
                    String resourceId = packageInfo.getId() + ":" + texturePath.getFileName().toString();
                    // 在实际实现中，这里应该将纹理资源注册到游戏中
                    packageInfo.addResource(ResourceType.TEXTURE, resourceId, texturePath);
                    LogSystem.systemLog("加载纹理资源: " + resourceId);
                }
            }
        } catch (Exception e) {
            LogSystem.error("加载纹理资源失败: " + e.getMessage());
        }
    }

    /**
     * 加载视频资源
     */
    private void loadVideoResources(PackageInfo packageInfo, Path videoDir) {
        try {
            if (!Files.exists(videoDir) || !Files.isDirectory(videoDir)) {
                return;
            }
            
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(videoDir, "*.{mp4}")) {
                for (Path videoPath : stream) {
                    String resourceId = packageInfo.getId() + ":" + videoPath.getFileName().toString();
                    // 在实际实现中，这里应该将视频资源注册到游戏中
                    packageInfo.addResource(ResourceType.VIDEO, resourceId, videoPath);
                    LogSystem.systemLog("加载视频资源: " + resourceId);
                }
            }
        } catch (Exception e) {
            LogSystem.error("加载视频资源失败: " + e.getMessage());
        }
    }

    /**
     * 加载语言资源
     */
    private void loadLanguageResources(PackageInfo packageInfo, Path langDir) {
        try {
            if (!Files.exists(langDir) || !Files.isDirectory(langDir)) {
                return;
            }
            
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(langDir, "*.json")) {
                for (Path langPath : stream) {
                    String langCode = langPath.getFileName().toString().replace(".json", "");
                    // 在实际实现中，这里应该将语言资源注册到游戏中
                    packageInfo.addResource(ResourceType.LANGUAGE, langCode, langPath);
                    LogSystem.systemLog("加载语言资源: " + langCode);
                }
            }
        } catch (Exception e) {
            LogSystem.error("加载语言资源失败: " + e.getMessage());
        }
    }

    /**
     * 加载模型资源
     */
    private void loadModelResources(PackageInfo packageInfo, Path modelDir) {
        try {
            if (!Files.exists(modelDir) || !Files.isDirectory(modelDir)) {
                return;
            }
            
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(modelDir, "*.json")) {
                for (Path modelPath : stream) {
                    String modelId = packageInfo.getId() + ":" + modelPath.getFileName().toString();
                    // 在实际实现中，这里应该将模型资源注册到游戏中
                    packageInfo.addResource(ResourceType.MODEL, modelId, modelPath);
                    LogSystem.systemLog("加载模型资源: " + modelId);
                }
            }
        } catch (Exception e) {
            LogSystem.error("加载模型资源失败: " + e.getMessage());
        }
    }

    /**
     * 加载包配置
     */
    private void loadPackageConfig(PackageInfo packageInfo, Path configPath) {
        try {
            if (!Files.exists(configPath) || !Files.isRegularFile(configPath)) {
                return;
            }
            
            try (Reader reader = new FileReader(configPath.toFile(), StandardCharsets.UTF_8)) {
                JsonObject config = JsonParser.parseReader(reader).getAsJsonObject();
                packageInfo.setConfig(config);
                LogSystem.systemLog("加载包配置: " + packageInfo.getId());
            }
        } catch (Exception e) {
            LogSystem.error("加载配置失败: " + packageInfo.getId() + ", 错误: " + e.getMessage());
        }
    }

    /**
     * 卸载包
     */
    public boolean unloadPackage(String packageId) {
        PackageInfo packageInfo = loadedPackages.get(packageId);
        if (packageInfo == null) {
            LogSystem.warning("要卸载的追加包未加载: " + packageId);
            return false;
        }
        
        // 触发包卸载前事件
        firePackagePreUnloadEvent(packageInfo);
        
        try {
            // 在实际实现中，这里应该清理包加载的所有资源
            
            // 从已加载列表中移除
            loadedPackages.remove(packageId);
            
            // 触发包卸载后事件
            firePackagePostUnloadEvent(packageInfo);
            
            LogSystem.systemLog("成功卸载追加包: " + packageInfo.getName());
            return true;
        } catch (Exception e) {
            LogSystem.error("卸载追加包失败: " + packageId + ", 错误: " + e.getMessage());
            return false;
        }
    }

    /**
     * 重新加载所有包
     */
    public void reloadAllPackages() {
        LogSystem.systemLog("开始重新加载所有追加包...");
        
        // 卸载所有包
        List<String> packageIds = new ArrayList<>(loadedPackages.keySet());
        for (String packageId : packageIds) {
            unloadPackage(packageId);
        }
        
        // 重新加载所有包
        loadAllPackages();
        
        LogSystem.systemLog("重新加载追加包完成，当前加载: " + loadedPackages.size() + " 个追加包");
    }

    /**
     * 获取全局包目录
     */
    private Path getGlobalPackageDirectory() {
        return Paths.get("krt_packages");
    }

    /**
     * 获取存档包目录
     */
    private Path getWorldPackageDirectory() {
        if (server == null) {
            return Paths.get("krt_packages", "world");
        }
        return server.getSavePath(WorldSavePath.ROOT).resolve("krt_packages");
    }

    /**
     * 获取已加载的包列表
     */
    public Collection<PackageInfo> getLoadedPackages() {
        return Collections.unmodifiableCollection(loadedPackages.values());
    }

    /**
     * 根据ID获取包
     */
    public PackageInfo getPackageById(String packageId) {
        return loadedPackages.get(packageId);
    }

    /**
     * 检查包是否已加载
     */
    public boolean isPackageLoaded(String packageId) {
        return loadedPackages.containsKey(packageId);
    }

    /**
     * 注册包事件监听器
     */
    public void registerEventListener(PackageEventListener listener) {
        if (!eventListeners.contains(listener)) {
            eventListeners.add(listener);
        }
    }

    /**
     * 注销包事件监听器
     */
    public void unregisterEventListener(PackageEventListener listener) {
        eventListeners.remove(listener);
    }

    /**
     * 触发包加载前事件
     */
    private void firePackagePreLoadEvent(PackageInfo packageInfo) {
        for (PackageEventListener listener : eventListeners) {
            try {
                listener.onPackagePreLoad(packageInfo);
            } catch (Exception e) {
                LogSystem.error("包加载前事件监听器执行失败: " + e.getMessage());
            }
        }
    }

    /**
     * 触发包加载后事件
     */
    private void firePackagePostLoadEvent(PackageInfo packageInfo) {
        for (PackageEventListener listener : eventListeners) {
            try {
                listener.onPackagePostLoad(packageInfo);
            } catch (Exception e) {
                LogSystem.error("包加载后事件监听器执行失败: " + e.getMessage());
            }
        }
    }

    /**
     * 触发包卸载前事件
     */
    private void firePackagePreUnloadEvent(PackageInfo packageInfo) {
        for (PackageEventListener listener : eventListeners) {
            try {
                listener.onPackagePreUnload(packageInfo);
            } catch (Exception e) {
                LogSystem.error("包卸载前事件监听器执行失败: " + e.getMessage());
            }
        }
    }

    /**
     * 触发包卸载后事件
     */
    private void firePackagePostUnloadEvent(PackageInfo packageInfo) {
        for (PackageEventListener listener : eventListeners) {
            try {
                listener.onPackagePostUnload(packageInfo);
            } catch (Exception e) {
                LogSystem.error("包卸载后事件监听器执行失败: " + e.getMessage());
            }
        }
    }

    /**
     * 资源类型枚举
     */
    public enum ResourceType {
        AUDIO,
        TEXTURE,
        VIDEO,
        LANGUAGE,
        MODEL,
        CONFIG,
        OTHER
    }

    /**
     * 包信息类
     */
    public static class PackageInfo {
        private final String id;
        private final String version;
        private final String name;
        private final String author;
        private final String description;
        private final List<String> dependencies;
        private final JsonObject content;
        private final Path path;
        private final Map<ResourceType, List<PackageResource>> resources;
        private JsonObject config;
        private long loadTime;

        public PackageInfo(JsonObject packageJson, Path path) {
            this.id = packageJson.get("id").getAsString();
            this.version = packageJson.get("version").getAsString();
            this.name = packageJson.get("name").getAsString();
            this.author = packageJson.has("author") ? packageJson.get("author").getAsString() : "Unknown";
            this.description = packageJson.has("description") ? packageJson.get("description").getAsString() : "";
            
            this.dependencies = new ArrayList<>();
            if (packageJson.has("dependencies") && packageJson.get("dependencies").isJsonArray()) {
                JsonArray dependenciesArray = packageJson.getAsJsonArray("dependencies");
                for (JsonElement element : dependenciesArray) {
                    dependencies.add(element.getAsString());
                }
            }
            
            this.content = packageJson.has("content") ? packageJson.getAsJsonObject("content") : new JsonObject();
            this.path = path;
            this.resources = new EnumMap<>(ResourceType.class);
            this.loadTime = System.currentTimeMillis();
        }

        public String getId() {
            return id;
        }

        public String getVersion() {
            return version;
        }

        public String getName() {
            return name;
        }

        public String getAuthor() {
            return author;
        }

        public String getDescription() {
            return description;
        }

        public List<String> getDependencies() {
            return Collections.unmodifiableList(dependencies);
        }

        public JsonObject getContent() {
            return content;
        }

        public Path getPath() {
            return path;
        }

        public JsonObject getConfig() {
            return config;
        }

        public void setConfig(JsonObject config) {
            this.config = config;
        }

        public long getLoadTime() {
            return loadTime;
        }

        public void addResource(ResourceType type, String id, Path path) {
            resources.computeIfAbsent(type, k -> new ArrayList<>()).add(new PackageResource(id, path));
        }

        public List<PackageResource> getResourcesByType(ResourceType type) {
            return resources.getOrDefault(type, Collections.emptyList());
        }

        public PackageResource getResourceById(String id) {
            for (List<PackageResource> resourceList : resources.values()) {
                for (PackageResource resource : resourceList) {
                    if (resource.getId().equals(id)) {
                        return resource;
                    }
                }
            }
            return null;
        }

        public boolean hasResource(String id) {
            return getResourceById(id) != null;
        }
    }

    /**
     * 包资源类
     */
    public static class PackageResource {
        private final String id;
        private final Path path;

        public PackageResource(String id, Path path) {
            this.id = id;
            this.path = path;
        }

        public String getId() {
            return id;
        }

        public Path getPath() {
            return path;
        }
    }

    /**
     * 包事件监听器接口
     */
    public interface PackageEventListener {
        void onPackagePreLoad(PackageInfo packageInfo);
        void onPackagePostLoad(PackageInfo packageInfo);
        void onPackagePreUnload(PackageInfo packageInfo);
        void onPackagePostUnload(PackageInfo packageInfo);
    }
}