package com.krt.mod.api;

import com.google.gson.*;
import com.krt.mod.KRTMod;
import com.krt.mod.system.LanguageSystem;
import com.krt.mod.system.LogSystem;
import net.minecraft.util.Identifier;
import net.minecraft.util.WorldSavePath;
import net.minecraft.world.World;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ModInterface {
    // 单例实例
    private static ModInterface instance;
    // 追加包列表
    private final List<ModPackage> packages = new ArrayList<>();
    // 已加载的追加包缓存
    private final Map<String, ModPackage> loadedPackages = new ConcurrentHashMap<>();
    // JSON解析器
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    // 私有化构造函数
    private ModInterface() {
    }

    // 获取单例实例
    public static synchronized ModInterface getInstance() {
        if (instance == null) {
            instance = new ModInterface();
        }
        return instance;
    }

    /**
     * 初始化接口系统
     */
    public void initialize() {
        LogSystem.systemLog("正在初始化KRT模组接口系统...");
        // 初始化追加包目录
        initPackageDirectories();
        // 加载所有已安装的追加包
        loadAllPackages();
        LogSystem.systemLog("KRT模组接口系统初始化完成，已加载 " + loadedPackages.size() + " 个追加包");
    }

    /**
     * 初始化追加包目录
     */
    private void initPackageDirectories() {
        try {
            // 创建全局追加包目录
            Path globalPackageDir = Paths.get("krt_packages");
            if (!Files.exists(globalPackageDir)) {
                Files.createDirectories(globalPackageDir);
                LogSystem.systemLog("已创建全局追加包目录: " + globalPackageDir.toAbsolutePath());
            }
            
            // 创建配置文件
            Path configFile = globalPackageDir.resolve("config.json");
            if (!Files.exists(configFile)) {
                JsonObject config = new JsonObject();
                config.addProperty("version", "1.0");
                config.addProperty("auto_load_packages", true);
                config.addProperty("max_package_count", 100);
                
                try (Writer writer = new FileWriter(configFile.toFile(), StandardCharsets.UTF_8)) {
                    gson.toJson(config, writer);
                }
                LogSystem.systemLog("已创建追加包配置文件: " + configFile.toAbsolutePath());
            }
        } catch (IOException e) {
            LogSystem.error("初始化追加包目录失败: " + e.getMessage());
        }
    }

    /**
     * 加载所有已安装的追加包
     */
    private void loadAllPackages() {
        try {
            Path globalPackageDir = Paths.get("krt_packages");
            
            // 扫描全局追加包目录
            if (Files.exists(globalPackageDir) && Files.isDirectory(globalPackageDir)) {
                try (DirectoryStream<Path> stream = Files.newDirectoryStream(globalPackageDir)) {
                    for (Path packagePath : stream) {
                        if (Files.isDirectory(packagePath)) {
                            loadPackage(packagePath);
                        }
                    }
                }
            }
        } catch (IOException e) {
            LogSystem.error("加载追加包失败: " + e.getMessage());
        }
    }

    /**
     * 加载单个追加包
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
            try (Reader reader = new FileReader(packageJsonPath.toFile(), StandardCharsets.UTF_8)) {
                JsonObject packageJson = JsonParser.parseReader(reader).getAsJsonObject();
                
                // 验证包的基本信息
                if (!packageJson.has("id") || !packageJson.has("version") || !packageJson.has("name")) {
                    LogSystem.warning("追加包缺少必要信息: " + packagePath.getFileName());
                    return false;
                }
                
                String packageId = packageJson.get("id").getAsString();
                String version = packageJson.get("version").getAsString();
                String name = packageJson.get("name").getAsString();
                
                // 检查包是否已经加载
                if (loadedPackages.containsKey(packageId)) {
                    LogSystem.warning("追加包ID重复，跳过加载: " + packageId);
                    return false;
                }
                
                // 创建追加包实例
                ModPackage modPackage = new ModPackage(packageId, version, name, packagePath);
                
                // 读取包的内容
                if (packageJson.has("content")) {
                    JsonObject contentJson = packageJson.getAsJsonObject("content");
                    modPackage.setContent(contentJson);
                }
                
                // 读取包的依赖
                if (packageJson.has("dependencies")) {
                    JsonArray dependenciesArray = packageJson.getAsJsonArray("dependencies");
                    List<String> dependencies = new ArrayList<>();
                    for (JsonElement element : dependenciesArray) {
                        dependencies.add(element.getAsString());
                    }
                    modPackage.setDependencies(dependencies);
                }
                
                // 验证依赖
                if (!verifyDependencies(modPackage)) {
                    LogSystem.warning("追加包依赖验证失败，跳过加载: " + packageId);
                    return false;
                }
                
                // 加载包的资源
                loadPackageResources(modPackage);
                
                // 注册包
                loadedPackages.put(packageId, modPackage);
                packages.add(modPackage);
                
                LogSystem.systemLog("成功加载追加包: " + name + " (" + packageId + ", v" + version + ")");
                
                return true;
            }
        } catch (Exception e) {
            LogSystem.error("加载追加包失败: " + packagePath.getFileName() + ", 错误: " + e.getMessage());
            return false;
        }
    }

    /**
     * 验证追加包的依赖
     */
    private boolean verifyDependencies(ModPackage modPackage) {
        for (String dependencyId : modPackage.getDependencies()) {
            if (!loadedPackages.containsKey(dependencyId)) {
                // 检查是否为核心模组依赖
                if (!dependencyId.equals("krt_core")) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * 加载追加包的资源
     */
    private void loadPackageResources(ModPackage modPackage) {
        try {
            Path packagePath = modPackage.getPath();
            
            // 加载音频资源
            Path audioDir = packagePath.resolve("audio");
            if (Files.exists(audioDir) && Files.isDirectory(audioDir)) {
                try (DirectoryStream<Path> stream = Files.newDirectoryStream(audioDir)) {
                    for (Path audioPath : stream) {
                        if (audioPath.toString().endsWith(".ogg") || audioPath.toString().endsWith(".mp3") || audioPath.toString().endsWith(".wav")) {
                            String audioId = modPackage.getId() + ":" + audioPath.getFileName().toString();
                            // 在实际实现中，这里应该加载音频文件到游戏中
                            LogSystem.systemLog("加载音频资源: " + audioId);
                        }
                    }
                }
            }
            
            // 加载纹理资源
            Path textureDir = packagePath.resolve("textures");
            if (Files.exists(textureDir) && Files.isDirectory(textureDir)) {
                try (DirectoryStream<Path> stream = Files.newDirectoryStream(textureDir)) {
                    for (Path texturePath : stream) {
                        if (texturePath.toString().endsWith(".png") || texturePath.toString().endsWith(".jpg") || texturePath.toString().endsWith(".gif")) {
                            String textureId = modPackage.getId() + ":" + texturePath.getFileName().toString();
                            // 在实际实现中，这里应该加载纹理文件到游戏中
                            LogSystem.systemLog("加载纹理资源: " + textureId);
                        }
                    }
                }
            }
            
            // 加载视频资源
            Path videoDir = packagePath.resolve("videos");
            if (Files.exists(videoDir) && Files.isDirectory(videoDir)) {
                try (DirectoryStream<Path> stream = Files.newDirectoryStream(videoDir)) {
                    for (Path videoPath : stream) {
                        if (videoPath.toString().endsWith(".mp4") || videoPath.toString().endsWith(".mov")) {
                            String videoId = modPackage.getId() + ":" + videoPath.getFileName().toString();
                            // 在实际实现中，这里应该加载视频文件到游戏中
                            LogSystem.systemLog("加载视频资源: " + videoId);
                        }
                    }
                }
            }
            
            // 加载语言文件
            Path langDir = packagePath.resolve("lang");
            if (Files.exists(langDir) && Files.isDirectory(langDir)) {
                try (DirectoryStream<Path> stream = Files.newDirectoryStream(langDir)) {
                    for (Path langPath : stream) {
                        if (langPath.toString().endsWith(".json")) {
                            String langCode = langPath.getFileName().toString().replace(".json", "");
                            // 在实际实现中，这里应该加载语言文件到游戏中
                            LogSystem.systemLog("加载语言资源: " + langCode);
                        }
                    }
                }
            }
        } catch (Exception e) {
            LogSystem.error("加载追加包资源失败: " + modPackage.getName() + ", 错误: " + e.getMessage());
        }
    }

    /**
     * 卸载追加包
     */
    public boolean unloadPackage(String packageId) {
        if (!loadedPackages.containsKey(packageId)) {
            LogSystem.warning("要卸载的追加包未加载: " + packageId);
            return false;
        }
        
        ModPackage modPackage = loadedPackages.remove(packageId);
        packages.remove(modPackage);
        
        // 在实际实现中，这里应该清理包加载的资源
        LogSystem.systemLog("成功卸载追加包: " + modPackage.getName() + " (" + packageId + ")");
        
        return true;
    }

    /**
     * 获取已加载的追加包列表
     */
    public List<ModPackage> getLoadedPackages() {
        return new ArrayList<>(packages);
    }

    /**
     * 根据ID获取追加包
     */
    public ModPackage getPackageById(String packageId) {
        return loadedPackages.get(packageId);
    }

    /**
     * 检查追加包是否已加载
     */
    public boolean isPackageLoaded(String packageId) {
        return loadedPackages.containsKey(packageId);
    }

    /**
     * 创建新的追加包
     */
    public ModPackage createNewPackage(String packageId, String version, String name) {
        try {
            // 检查包ID是否已存在
            if (loadedPackages.containsKey(packageId)) {
                LogSystem.warning("追加包ID已存在: " + packageId);
                return null;
            }
            
            // 创建包目录
            Path packageDir = Paths.get("krt_packages").resolve(packageId);
            if (Files.exists(packageDir)) {
                LogSystem.warning("追加包目录已存在: " + packageId);
                return null;
            }
            Files.createDirectories(packageDir);
            
            // 创建必要的子目录
            Files.createDirectories(packageDir.resolve("audio"));
            Files.createDirectories(packageDir.resolve("textures"));
            Files.createDirectories(packageDir.resolve("videos"));
            Files.createDirectories(packageDir.resolve("lang"));
            
            // 创建package.json文件
            JsonObject packageJson = new JsonObject();
            packageJson.addProperty("id", packageId);
            packageJson.addProperty("version", version);
            packageJson.addProperty("name", name);
            packageJson.addProperty("author", "Unknown");
            packageJson.addProperty("description", "");
            packageJson.add("dependencies", new JsonArray());
            packageJson.add("content", new JsonObject());
            
            Path packageJsonPath = packageDir.resolve("package.json");
            try (Writer writer = new FileWriter(packageJsonPath.toFile(), StandardCharsets.UTF_8)) {
                gson.toJson(packageJson, writer);
            }
            
            // 创建示例语言文件
            createExampleLangFile(packageDir);
            
            // 创建示例音频文件（占位符）
            createExampleAudioFile(packageDir);
            
            LogSystem.systemLog("成功创建新追加包: " + name + " (" + packageId + ")");
            
            // 加载新创建的包
            return loadPackage(packageDir) ? loadedPackages.get(packageId) : null;
        } catch (Exception e) {
            LogSystem.error("创建新追加包失败: " + e.getMessage());
            return null;
        }
    }

    /**
     * 创建示例语言文件
     */
    private void createExampleLangFile(Path packageDir) throws IOException {
        Path langDir = packageDir.resolve("lang");
        
        // 创建中文语言文件
        JsonObject zhCnJson = new JsonObject();
        zhCnJson.addProperty("krt.package.example.title", "示例标题");
        zhCnJson.addProperty("krt.package.example.description", "这是一个示例描述");
        
        Path zhCnPath = langDir.resolve("zh_cn.json");
        try (Writer writer = new FileWriter(zhCnPath.toFile(), StandardCharsets.UTF_8)) {
            gson.toJson(zhCnJson, writer);
        }
        
        // 创建英文语言文件
        JsonObject enUsJson = new JsonObject();
        enUsJson.addProperty("krt.package.example.title", "Example Title");
        enUsJson.addProperty("krt.package.example.description", "This is an example description");
        
        Path enUsPath = langDir.resolve("en_us.json");
        try (Writer writer = new FileWriter(enUsPath.toFile(), StandardCharsets.UTF_8)) {
            gson.toJson(enUsJson, writer);
        }
    }

    /**
     * 创建示例音频文件（占位符）
     */
    private void createExampleAudioFile(Path packageDir) throws IOException {
        Path audioDir = packageDir.resolve("audio");
        Path exampleAudioPath = audioDir.resolve("example.ogg");
        
        // 创建一个空的音频文件作为占位符
        Files.createFile(exampleAudioPath);
        try (Writer writer = new FileWriter(exampleAudioPath.toFile(), StandardCharsets.UTF_8)) {
            writer.write("// 这是一个音频文件占位符\n// 请替换为实际的OGG或MP3音频文件");
        }
    }

    /**
     * 导出追加包
     */
    public boolean exportPackage(String packageId, Path exportPath) {
        try {
            ModPackage modPackage = loadedPackages.get(packageId);
            if (modPackage == null) {
                LogSystem.warning("要导出的追加包未加载: " + packageId);
                return false;
            }
            
            Path packagePath = modPackage.getPath();
            
            // 在实际实现中，这里应该将包的内容打包成ZIP文件
            LogSystem.systemLog("导出追加包: " + modPackage.getName() + " 到: " + exportPath);
            
            return true;
        } catch (Exception e) {
            LogSystem.error("导出追加包失败: " + packageId + ", 错误: " + e.getMessage());
            return false;
        }
    }

    /**
     * 获取包的内容
     */
    public JsonObject getPackageContent(String packageId) {
        ModPackage modPackage = loadedPackages.get(packageId);
        if (modPackage == null) {
            return null;
        }
        return modPackage.getContent();
    }

    /**
     * 获取指定类型的所有包资源
     */
    public List<String> getPackageResourcesByType(String packageId, ResourceType type) {
        List<String> resources = new ArrayList<>();
        
        ModPackage modPackage = loadedPackages.get(packageId);
        if (modPackage == null) {
            return resources;
        }
        
        // 在实际实现中，这里应该返回包中指定类型的资源列表
        return resources;
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
        OTHER
    }

    /**
     * 追加包类
     */
    public static class ModPackage {
        private final String id;
        private final String version;
        private final String name;
        private String author = "Unknown";
        private String description = "";
        private final Path path;
        private List<String> dependencies = new ArrayList<>();
        private JsonObject content = new JsonObject();
        private long loadTime;

        public ModPackage(String id, String version, String name, Path path) {
            this.id = id;
            this.version = version;
            this.name = name;
            this.path = path;
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

        public void setAuthor(String author) {
            this.author = author;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public Path getPath() {
            return path;
        }

        public List<String> getDependencies() {
            return dependencies;
        }

        public void setDependencies(List<String> dependencies) {
            this.dependencies = dependencies;
        }

        public JsonObject getContent() {
            return content;
        }

        public void setContent(JsonObject content) {
            this.content = content;
        }

        public long getLoadTime() {
            return loadTime;
        }

        public boolean isLoaded() {
            return loadTime > 0;
        }

        @Override
        public String toString() {
            return "ModPackage{" +
                    "id='" + id + '\'' +
                    ", version='" + version + '\'' +
                    ", name='" + name + '\'' +
                    '}';
        }
    }
}