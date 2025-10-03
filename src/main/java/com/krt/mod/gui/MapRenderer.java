package com.krt.mod.gui;

import com.krt.mod.system.LineControlSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.TextureManager;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Matrix4f;
import net.minecraft.util.math.Vec3f;
import net.minecraft.world.World;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class MapRenderer {
    // 基础配置
    private static final int DEFAULT_MAP_WIDTH = 200;
    private static final int DEFAULT_MAP_HEIGHT = 200;
    private static final int BORDER_SIZE = 2;
    private static final int TILE_SIZE = 4; // 每个方块在地图上的像素大小
    private static final int CHUNK_SIZE = 16; // 区块大小，与Minecraft保持一致
    
    // 优化配置
    private static final int MAX_RENDER_DISTANCE = 5; // 最大渲染距离（区块）
    private static final int MAX_CACHED_CHUNKS = 100; // 最大缓存区块数
    private static final int MAX_TEXTURE_CACHE_SIZE = 50; // 最大纹理缓存数
    private static final int MIN_RENDER_TILE_SIZE = 2; // 最小渲染瓦片大小，小于此值不渲染细节
    private static final int BATCH_RENDER_THRESHOLD = 10; // 批量渲染阈值
    
    // 层级化渲染配置
    private static final int LAYER_TERRAIN = 0; // 地形层
    private static final int LAYER_TRACKS = 1; // 轨道层
    private static final int LAYER_STATIONS = 2; // 车站层
    private static final int LAYER_MARKERS = 3; // 标记层
    private static final int LAYER_UI = 4; // UI层
    
    // 层级配置表 - 用于控制不同层级的可见性和细节级别
    private final Map<Integer, LayerConfig> layerConfigs = new HashMap<>();

    private final int mapWidth;
    private final int mapHeight;
    private BlockPos centerPos;
    private int zoomLevel = 1;
    private final Map<Long, MapChunk> chunkCache = new ConcurrentHashMap<>();
    private final ExecutorService chunkLoader = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "MapChunkLoader");
        thread.setDaemon(true);
        return thread;
    });
    private final ExecutorService renderThreadPool = Executors.newFixedThreadPool(2, r -> {
        Thread thread = new Thread(r, "MapRenderThread");
        thread.setDaemon(true);
        return thread;
    });
    private long seed = 0; // 用于噪声生成的种子
    
    // 轻量级资源缓存
    private final Map<String, LightweightTexture> textureCache = new LinkedHashMap<String, LightweightTexture>(MAX_TEXTURE_CACHE_SIZE, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, LightweightTexture> eldest) {
            return size() > MAX_TEXTURE_CACHE_SIZE;
        }
    };
    
    // 帧计数器，用于控制更新频率
    private int frameCounter = 0;
    private static final int UPDATE_EVERY_N_FRAMES = 3; // 每N帧更新一次非关键数据
    
    // 批量渲染缓存
    private final Map<Integer, List<RenderBatch>> renderBatches = new ConcurrentHashMap<>();
    private long lastBatchUpdateTime = 0;
    private static final long BATCH_UPDATE_INTERVAL = 100; // 批量更新间隔（ms）

    // 噪声参数
    private static final double NOISE_SCALE = 0.01;
    private static final int NOISE_OCTAVES = 3;
    private static final double NOISE_PERSISTENCE = 0.5;
    private static final double NOISE_LACUNARITY = 2.0;

    // 层级配置类
    private static class LayerConfig {
        private boolean visible; // 是否可见
        private int minZoomLevel; // 最小缩放级别
        private int detailLevel; // 细节级别（0-100）
        private boolean useBatchRendering; // 是否使用批量渲染
        
        public LayerConfig(boolean visible, int minZoomLevel, int detailLevel, boolean useBatchRendering) {
            this.visible = visible;
            this.minZoomLevel = minZoomLevel;
            this.detailLevel = detailLevel;
            this.useBatchRendering = useBatchRendering;
        }
        
        public boolean isVisible(int currentZoomLevel) {
            return visible && currentZoomLevel >= minZoomLevel;
        }
        
        public int getDetailLevel() {
            return detailLevel;
        }
        
        public boolean useBatchRendering() {
            return useBatchRendering;
        }
    }
    
    // 轻量级纹理类
    private static class LightweightTexture {
        private final int width;
        private final int height;
        private final int[] pixels; // 存储压缩的像素数据
        private long lastUsedTime;
        
        public LightweightTexture(int width, int height, int[] pixels) {
            this.width = width;
            this.height = height;
            // 对像素数据进行压缩存储
            this.pixels = compressPixelData(pixels);
            this.lastUsedTime = System.currentTimeMillis();
        }
        
        public int getWidth() { return width; }
        public int getHeight() { return height; }
        public int[] getPixels() { 
            updateLastUsedTime();
            // 返回解压后的像素数据
            return decompressPixelData(pixels);
        }
    }
    
    // 轻量级区块数据类
    private static class LightweightChunkData {
        private final int chunkX;
        private final int chunkZ;
        private long lastAccessedTime;
        
        public LightweightChunkData(int chunkX, int chunkZ) {
            this.chunkX = chunkX;
            this.chunkZ = chunkZ;
            this.lastAccessedTime = System.currentTimeMillis();
        }
        
        public long getLastAccessedTime() {
            return lastAccessedTime;
        }
        
        public void updateLastAccessedTime() {
            this.lastAccessedTime = System.currentTimeMillis();
        }
        
        public int getChunkX() {
            return chunkX;
        }
        
        public int getChunkZ() {
            return chunkZ;
        }
        
        private void updateLastUsedTime() {
            this.lastUsedTime = System.currentTimeMillis();
        }
        
        private int[] compressPixelData(int[] original) {
            // 简单的颜色索引压缩，将相同颜色的像素进行编码
            if (original == null || original.length == 0) return new int[0];
            
            List<Integer> compressed = new ArrayList<>();
            int currentColor = original[0];
            int count = 1;
            
            for (int i = 1; i < original.length; i++) {
                if (original[i] == currentColor && count < 255) {
                    count++;
                } else {
                    compressed.add(currentColor);
                    compressed.add(count);
                    currentColor = original[i];
                    count = 1;
                }
            }
            
            // 添加最后一组数据
            compressed.add(currentColor);
            compressed.add(count);
            
            // 转换为数组
            return compressed.stream().mapToInt(Integer::intValue).toArray();
        }
        
        private int[] decompressPixelData(int[] compressed) {
            // 解压像素数据
            if (compressed == null || compressed.length == 0) return new int[0];
            
            List<Integer> decompressed = new ArrayList<>();
            
            for (int i = 0; i < compressed.length; i += 2) {
                int color = compressed[i];
                int count = compressed[i + 1];
                
                for (int j = 0; j < count; j++) {
                    decompressed.add(color);
                }
            }
            
            return decompressed.stream().mapToInt(Integer::intValue).toArray();
        }
    }
    
    // 渲染批次类
    private static class RenderBatch {
        private final int layer; // 所属层级
        private final List<RenderElement> elements; // 渲染元素列表
        private final BlockPos batchOrigin; // 批次原点坐标
        private long lastUpdated; // 最后更新时间
        
        public RenderBatch(int layer, BlockPos origin) {
            this.layer = layer;
            this.elements = new ArrayList<>();
            this.batchOrigin = origin;
            this.lastUpdated = System.currentTimeMillis();
        }
        
        public void addElement(RenderElement element) {
            elements.add(element);
        }
        
        public List<RenderElement> getElements() {
            return elements;
        }
        
        public int getLayer() {
            return layer;
        }
        
        public BlockPos getBatchOrigin() {
            return batchOrigin;
        }
        
        public long getLastUpdated() {
            return lastUpdated;
        }
        
        public void setLastUpdated(long time) {
            this.lastUpdated = time;
        }
    }
    
    // 渲染元素类
    private static class RenderElement {
        private final BlockPos pos; // 世界坐标
        private final int type; // 元素类型（0=方块，1=线条，2=文本等）
        private final int color; // 颜色
        private final float size; // 大小
        private final Object data; // 附加数据
        
        public RenderElement(BlockPos pos, int type, int color, float size, Object data) {
            this.pos = pos;
            this.type = type;
            this.color = color;
            this.size = size;
            this.data = data;
        }
        
        public BlockPos getPos() { return pos; }
        public int getType() { return type; }
        public int getColor() { return color; }
        public float getSize() { return size; }
        public Object getData() { return data; }
    }
    
    // 区块类定义（优化版）
    public class MapChunk {
        private final int chunkX;
        private final int chunkZ;
        private final Map<BlockPos, Integer> blocks = new HashMap<>();
        private boolean loaded = false;
        private long lastAccessedTime = System.currentTimeMillis();
        private int[][] simplifiedBlockData; // 简化的区块数据，用于快速渲染
        private boolean simplifiedDataGenerated = false;

        public MapChunk(int chunkX, int chunkZ) {
            this.chunkX = chunkX;
            this.chunkZ = chunkZ;
            // 预分配简化数据数组
            this.simplifiedBlockData = new int[CHUNK_SIZE][CHUNK_SIZE];
        }

        public int getChunkX() {
            return chunkX;
        }

        public int getChunkZ() {
            return chunkZ;
        }

        public void addBlock(BlockPos pos, int color) {
            blocks.put(pos, color);
            // 更新简化数据
            int relX = pos.getX() - (chunkX * CHUNK_SIZE);
            int relZ = pos.getZ() - (chunkZ * CHUNK_SIZE);
            if (relX >= 0 && relX < CHUNK_SIZE && relZ >= 0 && relZ < CHUNK_SIZE) {
                simplifiedBlockData[relX][relZ] = color;
            }
        }

        public Map<BlockPos, Integer> getBlocks() {
            updateLastAccessed();
            return blocks;
        }

        public boolean isLoaded() {
            return loaded;
        }

        public void setLoaded(boolean loaded) {
            this.loaded = loaded;
            if (loaded) {
                updateLastAccessed();
                // 生成简化数据用于快速渲染
                generateSimplifiedData();
            }
        }

        public long getLastAccessedTime() {
            return lastAccessedTime;
        }

        private void updateLastAccessed() {
            this.lastAccessedTime = System.currentTimeMillis();
        }

        // 获取区块中的区块坐标（相对于区块原点）
        public BlockPos getChunkBlockPos(int relX, int relZ) {
            int worldX = chunkX * CHUNK_SIZE + relX;
            int worldZ = chunkZ * CHUNK_SIZE + relZ;
            return new BlockPos(worldX, 0, worldZ);
        }
        
        // 生成简化的区块数据，用于低细节级别渲染
        private void generateSimplifiedData() {
            if (simplifiedDataGenerated) return;
            
            // 这里可以实现更复杂的简化算法，例如将多个小方块合并为更大的块
            simplifiedDataGenerated = true;
        }
        
        // 获取简化的区块数据
        public int[][] getSimplifiedBlockData() {
            updateLastAccessed();
            return simplifiedBlockData;
        }
    }

    public MapRenderer() {
        this(DEFAULT_MAP_WIDTH, DEFAULT_MAP_HEIGHT);
    }

    public MapRenderer(int width, int height) {
        this.mapWidth = width;
        this.mapHeight = height;
        // 初始化随机种子，基于当前系统时间
        this.seed = System.currentTimeMillis();
        
        // 初始化层级配置
        initializeLayerConfigs();
        
        // 初始化渲染批次存储
        for (int i = 0; i <= LAYER_UI; i++) {
            renderBatches.put(i, new ArrayList<>());
        }
    }
    
    // 初始化层级配置
    private void initializeLayerConfigs() {
        // 地形层：始终可见，低细节级别，使用批量渲染
        layerConfigs.put(LAYER_TERRAIN, new LayerConfig(true, 1, 40, true));
        // 轨道层：中等缩放级别可见，中等细节级别，使用批量渲染
        layerConfigs.put(LAYER_TRACKS, new LayerConfig(true, 1, 60, true));
        // 车站层：始终可见，高细节级别，使用批量渲染
        layerConfigs.put(LAYER_STATIONS, new LayerConfig(true, 1, 80, true));
        // 标记层：始终可见，最高细节级别，不使用批量渲染（动态元素）
        layerConfigs.put(LAYER_MARKERS, new LayerConfig(true, 1, 100, false));
        // UI层：始终可见，最高细节级别，不使用批量渲染
        layerConfigs.put(LAYER_UI, new LayerConfig(true, 1, 100, false));
    }
    
    // 设置层级可见性
    public void setLayerVisibility(int layer, boolean visible) {
        LayerConfig config = layerConfigs.get(layer);
        if (config != null) {
            config.visible = visible;
        }
    }
    
    // 设置层级最小缩放级别
    public void setLayerMinZoomLevel(int layer, int minZoomLevel) {
        LayerConfig config = layerConfigs.get(layer);
        if (config != null) {
            config.minZoomLevel = minZoomLevel;
        }
    }

    public void setCenterPosition(BlockPos centerPos) {
        this.centerPos = centerPos;
    }

    public void setZoomLevel(int level) {
        this.zoomLevel = Math.max(1, Math.min(5, level)); // 限制缩放级别在1-5之间
    }

    public void render(MatrixStack matrices, int x, int y, World world) {
        if (centerPos == null) {
            // 如果没有中心位置，使用玩家位置
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.player != null) {
                centerPos = client.player.getBlockPos();
            } else {
                return;
            }
        }

        // 清空当前批次
        for (int i = 0; i <= LAYER_UI; i++) {
            renderBatches.get(i).clear();
        }
        
        // 根据缩放级别确定可见层级
        prepareRenderBatches(world);
        
        // 按顺序渲染各层级
        renderLayers(matrices, x + BORDER_SIZE, y + BORDER_SIZE);
        
        // 绘制UI层
        if (layerConfigs.get(LAYER_UI).isVisible(zoomLevel)) {
            drawMapBorder(matrices, x, y);
        }
    }
    
    // 准备渲染批次
    private void prepareRenderBatches(World world) {
        // 地形层渲染准备
        if (shouldRenderLayer(LAYER_TERRAIN)) {
            prepareTerrainBatch();
        }
        
        // 轨道层渲染准备
        if (shouldRenderLayer(LAYER_TRACKS)) {
            prepareTracksBatch(world);
        }
        
        // 车站层渲染准备
        if (shouldRenderLayer(LAYER_STATIONS)) {
            prepareStationsBatch(world);
        }
    }
    
    // 渲染各层级
    private void renderLayers(MatrixStack matrices, int x, int y) {
        // 地形层
        if (shouldRenderLayer(LAYER_TERRAIN)) {
            renderLayer(matrices, x, y, LAYER_TERRAIN);
        }
        
        // 轨道层
        if (shouldRenderLayer(LAYER_TRACKS)) {
            renderLayer(matrices, x, y, LAYER_TRACKS);
        }
        
        // 车站层
        if (shouldRenderLayer(LAYER_STATIONS)) {
            renderLayer(matrices, x, y, LAYER_STATIONS);
        }
        
        // 标记层 - 始终单独渲染（动态元素）
        if (shouldRenderLayer(LAYER_MARKERS)) {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.world != null) {
                drawPlayerMarker(matrices, x, y, client.player.getBlockPos(), TILE_SIZE / zoomLevel, mapWidth / 2 / (TILE_SIZE / zoomLevel), mapHeight / 2 / (TILE_SIZE / zoomLevel));
            }
        }
    }
    
    // 判断层级是否应该渲染
    private boolean shouldRenderLayer(int layer) {
        LayerConfig config = layerConfigs.get(layer);
        if (config == null) {
            return false;
        }
        
        // 根据缩放级别判断是否渲染
        return config.isVisible(zoomLevel);
    }
    
    // 批量渲染指定层级
    private void renderLayer(MatrixStack matrices, int x, int y, int layer) {
        List<RenderBatch> batches = renderBatches.get(layer);
        LayerConfig config = layerConfigs.get(layer);
        
        if (batches.isEmpty() || config == null) {
            return;
        }
        
        int effectiveTileSize = TILE_SIZE / zoomLevel;
        if (effectiveTileSize < 1) effectiveTileSize = 1;
        
        int halfWidth = mapWidth / 2 / effectiveTileSize;
        int halfHeight = mapHeight / 2 / effectiveTileSize;
        
        // 如果启用批量渲染，则一次性绘制所有批次
        if (config.useBatchRendering()) {
            // 批量渲染实现
            for (RenderBatch batch : batches) {
                for (RenderElement element : batch.getElements()) {
                    drawElement(matrices, x, y, element, effectiveTileSize, halfWidth, halfHeight);
                }
            }
        }
    }
    
    // 绘制渲染元素
    private void drawElement(MatrixStack matrices, int x, int y, RenderElement element, int tileSize, int halfWidth, int halfHeight) {
        BlockPos pos = element.getPos();
        int color = element.getColor();
        
        // 检查是否在可见范围内
        if (Math.abs(pos.getX() - centerPos.getX()) <= halfWidth && Math.abs(pos.getZ() - centerPos.getZ()) <= halfHeight) {
            int screenX = x + (pos.getX() - centerPos.getX() + halfWidth) * tileSize;
            int screenY = y + (pos.getZ() - centerPos.getZ() + halfHeight) * tileSize;
            
            // 根据元素类型绘制
            if (element.getType() == 0) { // 方块
                fill(matrices, screenX, screenY, screenX + tileSize - 1, screenY + tileSize - 1, color);
            } else if (element.getType() == 1) { // 线条
                BlockPos endPos = (BlockPos)element.getData();
                int endScreenX = x + (endPos.getX() - centerPos.getX() + halfWidth) * tileSize;
                int endScreenY = y + (endPos.getZ() - centerPos.getZ() + halfHeight) * tileSize;
                drawLine(matrices, screenX, screenY, endScreenX, endScreenY, color, (int)element.getSize());
            }
        }
    }
    
    // 准备地形渲染批次
    private void prepareTerrainBatch() {
        // 地形层使用轻量级资源和简化绘制
        int effectiveTileSize = TILE_SIZE / zoomLevel;
        if (effectiveTileSize < 1) effectiveTileSize = 1;
        
        int halfWidth = mapWidth / 2 / effectiveTileSize;
        int halfHeight = mapHeight / 2 / effectiveTileSize;
        
        // 计算当前中心区块坐标
        int centerChunkX = centerPos.getX() >> 4;
        int centerChunkZ = centerPos.getZ() >> 4;
        
        // 确定要渲染的区块范围
        int renderDistance = Math.min(MAX_RENDER_DISTANCE, calculateRenderDistance());
        
        // 异步加载区块
        loadNearbyChunks(centerChunkX, centerChunkZ, renderDistance);
        
        // 为可见区块创建渲染批次
        for (int chunkX = centerChunkX - renderDistance; chunkX <= centerChunkX + renderDistance; chunkX++) {
            for (int chunkZ = centerChunkZ - renderDistance; chunkZ <= centerChunkZ + renderDistance; chunkZ++) {
                MapChunk chunk = getChunk(chunkX, chunkZ);
                if (chunk != null && chunk.isLoaded()) {
                    // 创建地形渲染批次
                    RenderBatch terrainBatch = new RenderBatch(LAYER_TERRAIN, new BlockPos(chunkX * CHUNK_SIZE, 0, chunkZ * CHUNK_SIZE));
                    
                    // 添加区块中的方块到批次
                    int[][] simplifiedData = chunk.getSimplifiedBlockData();
                    for (int relX = 0; relX < CHUNK_SIZE; relX++) {
                        for (int relZ = 0; relZ < CHUNK_SIZE; relZ++) {
                            int color = simplifiedData[relX][relZ];
                            if (color != 0) { // 跳过透明方块
                                BlockPos pos = chunk.getChunkBlockPos(relX, relZ);
                                terrainBatch.addElement(new RenderElement(pos, 0, color, 1.0f, null));
                            }
                        }
                    }
                    
                    if (!terrainBatch.getElements().isEmpty()) {
                        renderBatches.get(LAYER_TERRAIN).add(terrainBatch);
                    }
                }
            }
        }
    }
    
    // 准备轨道渲染批次
    private void prepareTracksBatch(World world) {
        if (world == null) return;
        
        // 轨道层使用中等细节级别
        LineControlSystem lineControlSystem = LineControlSystem.getInstance(world);
        Collection<LineControlSystem.LineInfo> lines = lineControlSystem.getAllLines();
        
        for (LineControlSystem.LineInfo line : lines) {
            RenderBatch tracksBatch = new RenderBatch(LAYER_TRACKS, centerPos);
            
            // 将轨道数据添加到批次
            for (BlockPos trackPos : line.getTracks()) {
                tracksBatch.addElement(new RenderElement(trackPos, 0, 0xFFAAAAAA, 1.0f, null));
            }
            
            if (!tracksBatch.getElements().isEmpty()) {
                renderBatches.get(LAYER_TRACKS).add(tracksBatch);
            }
        }
    }
    
    // 准备车站渲染批次
    private void prepareStationsBatch(World world) {
        if (world == null) return;
        
        // 车站层使用高细节级别
        LineControlSystem lineControlSystem = LineControlSystem.getInstance(world);
        Collection<LineControlSystem.LineInfo> lines = lineControlSystem.getAllLines();
        
        for (LineControlSystem.LineInfo line : lines) {
            RenderBatch stationsBatch = new RenderBatch(LAYER_STATIONS, centerPos);
            
            // 将车站数据添加到批次
            for (LineControlSystem.StationInfo station : line.getStations()) {
                BlockPos pos = station.getPosition();
                stationsBatch.addElement(new RenderElement(pos, 0, 0xFFFF0000, 1.0f, station.getStationName()));
            }
            
            if (!stationsBatch.getElements().isEmpty()) {
                renderBatches.get(LAYER_STATIONS).add(stationsBatch);
            }
        }
    }

    private void drawMapBorder(MatrixStack matrices, int x, int y) {
        // 绘制边框背景
        fill(matrices, x, y, x + mapWidth + BORDER_SIZE * 2, y + mapHeight + BORDER_SIZE * 2, 0xFF000000);
        // 绘制内边框
        fill(matrices, x + BORDER_SIZE, y + BORDER_SIZE, x + mapWidth, y + mapHeight, 0xFF222222);
    }

    private void drawMapContent(MatrixStack matrices, int x, int y, World world) {
        // 绘制基本地形（简化版，仅绘制区块轮廓）
        int effectiveTileSize = TILE_SIZE / zoomLevel;
        if (effectiveTileSize < 1) effectiveTileSize = 1;
        
        int halfWidth = mapWidth / 2 / effectiveTileSize;
        int halfHeight = mapHeight / 2 / effectiveTileSize;

        // 只在低缩放级别绘制地形，减少渲染开销
        if (zoomLevel <= 2 && world != null) {
            drawTerrain(matrices, x, y, effectiveTileSize, halfWidth, halfHeight);
        }
        
        // 绘制网格线
        drawGridLines(matrices, x, y, effectiveTileSize, halfWidth, halfHeight);
    }

    private void drawGridLines(MatrixStack matrices, int x, int y, int tileSize, int halfWidth, int halfHeight) {
        // 绘制网格线（简化版）
        for (int i = 0; i <= mapWidth; i += tileSize * 4) {
            drawLine(matrices, x + i, y, x + i, y + mapHeight, 0xFF444444, 1);
        }
        for (int i = 0; i <= mapHeight; i += tileSize * 4) {
            drawLine(matrices, x, y + i, x + mapWidth, y + i, 0xFF444444, 1);
        }
    }

    private void drawTerrain(MatrixStack matrices, int x, int y, int tileSize, int halfWidth, int halfHeight) {
        // 计算当前中心区块坐标
        int centerChunkX = centerPos.getX() >> 4;
        int centerChunkZ = centerPos.getZ() >> 4;
        
        // 确定要渲染的区块范围
        int renderDistance = Math.min(MAX_RENDER_DISTANCE, calculateRenderDistance());
        
        // 异步加载区块
        loadNearbyChunks(centerChunkX, centerChunkZ, renderDistance);
        
        // 绘制可见范围内的区块
        for (int chunkX = centerChunkX - renderDistance; chunkX <= centerChunkX + renderDistance; chunkX++) {
            for (int chunkZ = centerChunkZ - renderDistance; chunkZ <= centerChunkZ + renderDistance; chunkZ++) {
                MapChunk chunk = getChunk(chunkX, chunkZ);
                if (chunk != null && chunk.isLoaded()) {
                    drawChunk(matrices, x, y, chunk, tileSize, halfWidth, halfHeight);
                }
            }
        }
        
        // 清理过期区块缓存
        cleanupChunkCache();
    }

    // 绘制区块 - 已优化为使用批量渲染
    private void drawChunk(MatrixStack matrices, int x, int y, MapChunk chunk, int tileSize, int halfWidth, int halfHeight) {
        // 计算区块边界，快速判断是否完全在可视区域外
        int chunkMinX = chunk.getChunkX() * 16;
        int chunkMaxX = chunkMinX + 15;
        int chunkMinZ = chunk.getChunkZ() * 16;
        int chunkMaxZ = chunkMinZ + 15;
        
        // 快速视锥剔除：如果区块完全在可视区域外，直接跳过
        if (chunkMaxX < centerPos.getX() - halfWidth || 
            chunkMinX > centerPos.getX() + halfWidth || 
            chunkMaxZ < centerPos.getZ() - halfHeight || 
            chunkMinZ > centerPos.getZ() + halfHeight) {
            return;
        }
        
        // 使用简化的区块数据进行快速渲染
        int[][] simplifiedBlockData = chunk.getSimplifiedBlockData();
        
        // 根据当前缩放级别和性能设置，决定渲染粒度
        int renderStep = calculateRenderStep(zoomLevel);
        
        // 预计算一些值，减少循环中的计算
        int centerX = centerPos.getX();
        int centerZ = centerPos.getZ();
        int chunkX = chunk.getChunkX() * CHUNK_SIZE;
        int chunkZ = chunk.getChunkZ() * CHUNK_SIZE;
        
        for (int relX = 0; relX < CHUNK_SIZE; relX += renderStep) {
            for (int relZ = 0; relZ < CHUNK_SIZE; relZ += renderStep) {
                // 使用采样点的颜色代表整个区域
                int sampleX = Math.min(relX, CHUNK_SIZE - 1);
                int sampleZ = Math.min(relZ, CHUNK_SIZE - 1);
                int color = simplifiedBlockData[sampleX][sampleZ];
                
                if (color == 0) continue; // 跳过透明方块
                
                int worldX = chunkX + relX;
                int worldZ = chunkZ + relZ;
                
                // 检查是否在可见范围内
                if (Math.abs(worldX - centerX) > halfWidth || Math.abs(worldZ - centerZ) > halfHeight) {
                    continue;
                }
                
                // 转换为屏幕坐标
                int screenX = x + (worldX - centerX + halfWidth) * tileSize;
                int screenY = y + (worldZ - centerZ + halfHeight) * tileSize;
                
                // 绘制方块，根据渲染步长调整大小
                int drawSize = Math.min(renderStep * tileSize, tileSize * 2);
                fill(matrices, screenX, screenY, screenX + drawSize - 1, screenY + drawSize - 1, color);
            }
        }
    }
    
    // 计算渲染步长（根据缩放级别动态调整）
    private int calculateRenderStep(int zoom) {
        // 当缩放级别较低时，增加渲染步长以减少绘制量
        if (zoom <= 1) return 4;
        if (zoom <= 2) return 2;
        return 1;
    }

    private void loadNearbyChunks(int centerChunkX, int centerChunkZ, int renderDistance) {
        // 计算区块加载任务的优先级队列
        List<ChunkLoadTask> tasks = new ArrayList<>();
        
        for (int chunkX = centerChunkX - renderDistance; chunkX <= centerChunkX + renderDistance; chunkX++) {
            for (int chunkZ = centerChunkZ - renderDistance; chunkZ <= centerChunkZ + renderDistance; chunkZ++) {
                // 只添加未加载的区块
                if (!chunkCache.containsKey(getChunkKey(chunkX, chunkZ)) || !getChunk(chunkX, chunkZ).isLoaded()) {
                    // 计算到中心区块的距离，用于优先级排序
                    int distance = Math.abs(chunkX - centerChunkX) + Math.abs(chunkZ - centerChunkZ);
                    tasks.add(new ChunkLoadTask(chunkX, chunkZ, distance));
                }
            }
        }
        
        // 按距离排序，优先加载近的区块
        tasks.sort(Comparator.comparingInt(ChunkLoadTask::getDistance));
        
        // 限制并发加载的区块数量，避免线程池过载
        int maxConcurrentLoads = 4;
        int submitted = 0;
        
        for (ChunkLoadTask task : tasks) {
            if (submitted < maxConcurrentLoads && !chunkLoader.isShutdown()) {
                final int cx = task.getChunkX();
                final int cz = task.getChunkZ();
                
                chunkLoader.submit(() -> {
                    loadChunk(cx, cz);
                });
                submitted++;
            } else {
                // 对于超出限制的任务，稍后再加载
                break;
            }
        }
    }
    
    // 区块加载任务类，用于优先级排序
    private static class ChunkLoadTask {
        private final int chunkX;
        private final int chunkZ;
        private final int distance;
        
        public ChunkLoadTask(int chunkX, int chunkZ, int distance) {
            this.chunkX = chunkX;
            this.chunkZ = chunkZ;
            this.distance = distance;
        }
        
        public int getChunkX() {
            return chunkX;
        }
        
        public int getChunkZ() {
            return chunkZ;
        }
        
        public int getDistance() {
            return distance;
        }
    }

    // 加载区块 - 已优化为使用噪声缓存和简化计算
    private void loadChunk(int chunkX, int chunkZ) {
        MapChunk chunk = getChunk(chunkX, chunkZ);
        
        if (chunk != null && !chunk.isLoaded()) {
            // 简化的区块数据生成
            int[][] simplifiedBlockData = new int[CHUNK_SIZE][CHUNK_SIZE];
            
            // 批量计算噪声值
            generateSimplifiedChunkData(chunkX, chunkZ, simplifiedBlockData);
            
            // 设置简化的区块数据并添加方块
            for (int relX = 0; relX < CHUNK_SIZE; relX++) {
                for (int relZ = 0; relZ < CHUNK_SIZE; relZ++) {
                    BlockPos pos = chunk.getChunkBlockPos(relX, relZ);
                    int color = simplifiedBlockData[relX][relZ];
                    chunk.addBlock(pos, color);
                }
            }
            
            chunk.setLoaded(true);
        }
    }
    
    // 生成简化的区块数据
    private void generateSimplifiedChunkData(int chunkX, int chunkZ, int[][] blockData) {
        // 根据性能设置决定噪声计算精度
        int noisePrecision = 2; // 使用较低精度以提高性能，可根据需要调整
        
        // 预计算噪声值并缓存
        double[][] noiseValues = new double[CHUNK_SIZE / noisePrecision + 1][CHUNK_SIZE / noisePrecision + 1];
        
        int worldX = chunkX * CHUNK_SIZE;
        int worldZ = chunkZ * CHUNK_SIZE;
        
        // 只计算关键采样点的噪声值
        for (int x = 0; x < CHUNK_SIZE; x += noisePrecision) {
            for (int z = 0; z < CHUNK_SIZE; z += noisePrecision) {
                noiseValues[x / noisePrecision][z / noisePrecision] = generateNoise(worldX + x, worldZ + z);
            }
        }
        
        // 使用双线性插值填充其余点
        for (int x = 0; x < CHUNK_SIZE; x++) {
            for (int z = 0; z < CHUNK_SIZE; z++) {
                double noiseValue;
                
                if (x % noisePrecision != 0 || z % noisePrecision != 0) {
                    // 双线性插值
                    int x0 = (x / noisePrecision) * noisePrecision;
                    int z0 = (z / noisePrecision) * noisePrecision;
                    int x1 = Math.min(x0 + noisePrecision, CHUNK_SIZE - 1);
                    int z1 = Math.min(z0 + noisePrecision, CHUNK_SIZE - 1);
                    
                    double v00 = noiseValues[x0 / noisePrecision][z0 / noisePrecision];
                    double v01 = noiseValues[x0 / noisePrecision][z1 / noisePrecision];
                    double v10 = noiseValues[x1 / noisePrecision][z0 / noisePrecision];
                    double v11 = noiseValues[x1 / noisePrecision][z1 / noisePrecision];
                    
                    double tx = (double)(x - x0) / (x1 - x0);
                    double tz = (double)(z - z0) / (z1 - z0);
                    
                    noiseValue = lerp(lerp(v00, v10, tx), lerp(v01, v11, tx), tz);
                } else {
                    // 直接使用采样点值
                    noiseValue = noiseValues[x / noisePrecision][z / noisePrecision];
                }
                
                // 根据噪声值设置方块颜色
                blockData[x][z] = getColorFromNoise(noiseValue);
            }
        }
    }

    private int getColorFromNoise(double noiseValue) {
        // 将噪声值映射到不同的地形颜色
        if (noiseValue < -0.3) {
            return 0xFF0000FF; // 深蓝色（深海）
        } else if (noiseValue < 0) {
            return 0xFF0080FF; // 浅蓝色（浅海）
        } else if (noiseValue < 0.2) {
            return 0xFF00FF00; // 绿色（平原）
        } else if (noiseValue < 0.4) {
            return 0xFF808000; // 棕色（山地）
        } else if (noiseValue < 0.6) {
            return 0xFFA0A0A0; // 灰色（岩石）
        } else {
            return 0xFFFFFFFF; // 白色（雪山）
        }
    }

    private double generateNoise(double x, double z) {
        // 使用多层Perlin噪声生成地形
        double value = 0;
        double amplitude = 1;
        double frequency = NOISE_SCALE;
        
        for (int i = 0; i < NOISE_OCTAVES; i++) {
            value += amplitude * perlinNoise(x * frequency + seed, z * frequency + seed);
            amplitude *= NOISE_PERSISTENCE;
            frequency *= NOISE_LACUNARITY;
        }
        
        // 归一化到[-1, 1]范围
        return Math.max(-1, Math.min(1, value));
    }

    private double perlinNoise(double x, double z) {
        // 简化版的Perlin噪声实现
        int xi = (int)Math.floor(x) & 255;
        int yi = (int)Math.floor(z) & 255;
        
        double xf = x - Math.floor(x);
        double yf = z - Math.floor(z);
        
        double u = fade(xf);
        double v = fade(yf);
        
        int a = p[xi] + yi;
        int b = p[xi + 1] + yi;
        
        return lerp(v, lerp(u, grad(p[a], xf, yf), grad(p[b], xf - 1, yf)), 
                   lerp(u, grad(p[a + 1], xf, yf - 1), grad(p[b + 1], xf - 1, yf - 1)));
    }

    // Perlin噪声辅助函数
    private double fade(double t) {
        return t * t * t * (t * (t * 6 - 15) + 10);
    }
    
    private double lerp(double t, double a, double b) {
        return a + t * (b - a);
    }
    
    private double grad(int hash, double x, double z) {
        int h = hash & 15;
        double u = h < 8 ? x : z;
        double v = h < 4 ? z : h == 12 || h == 14 ? x : 0;
        return ((h & 1) == 0 ? u : -u) + ((h & 2) == 0 ? v : -v);
    }
    
    // Perlin噪声的置换表
    private final int[] p = new int[512];
    { 
        // 初始化置换表
        int[] permutation = {151, 160, 137, 91, 90, 15, 131, 13, 201, 95, 96, 53, 194, 233, 7, 225, 140, 36, 103, 30, 69, 142, 8, 99, 37, 240, 21, 10, 23, 190, 6, 148, 247, 120, 234, 75, 0, 26, 197, 62, 94, 252, 219, 203, 117, 35, 11, 32, 57, 177, 33, 88, 237, 149, 56, 87, 174, 20, 125, 136, 171, 168, 68, 175, 74, 165, 71, 134, 139, 48, 27, 166, 77, 146, 158, 231, 83, 111, 229, 122, 60, 211, 133, 230, 220, 105, 92, 41, 55, 46, 245, 40, 244, 102, 143, 54, 65, 25, 63, 161, 1, 216, 80, 73, 209, 76, 132, 187, 208, 89, 18, 169, 200, 196, 135, 130, 116, 188, 159, 86, 164, 100, 109, 198, 173, 186, 3, 64, 52, 217, 226, 250, 124, 123, 5, 202, 38, 147, 118, 126, 255, 82, 85, 212, 207, 206, 59, 227, 47, 16, 58, 17, 182, 189, 28, 42, 223, 183, 170, 213, 119, 248, 152, 2, 44, 154, 163, 70, 221, 153, 101, 155, 167, 43, 172, 9, 129, 22, 39, 253, 19, 98, 108, 110, 79, 113, 224, 232, 178, 185, 112, 104, 218, 246, 97, 228, 251, 34, 242, 193, 238, 210, 144, 12, 191, 179, 162, 241, 81, 51, 145, 235, 249, 14, 239, 107, 49, 192, 214, 31, 181, 199, 106, 157, 184, 84, 204, 176, 115, 121, 50, 45, 127, 4, 150, 254, 138, 236, 205, 93, 222, 114, 67, 29, 24, 72, 243, 141, 128, 195, 78, 66, 215, 61, 156, 180};
        
        // 复制置换表以避免边界检查
        for (int i = 0; i < 256; i++) {
            p[256 + i] = p[i] = permutation[i];
        }
    }

    private int calculateRenderDistance() {
        // 根据缩放级别动态调整渲染距离
        int baseDistance = 3;
        
        // 缩放级别越高（视角越小），渲染距离越小
        if (zoomLevel >= 4) {
            return 1;
        } else if (zoomLevel >= 2) {
            return 2;
        }
        
        return baseDistance;
    }

    private MapChunk getChunk(int chunkX, int chunkZ) {
        long key = getChunkKey(chunkX, chunkZ);
        return chunkCache.computeIfAbsent(key, k -> new MapChunk(chunkX, chunkZ));
    }

    private long getChunkKey(int chunkX, int chunkZ) {
        // 将区块坐标转换为唯一键
        return ((long)chunkX << 32) | (chunkZ & 0xFFFFFFFFL);
    }

    private void cleanupChunkCache() {
        // 当缓存的区块数量超过上限时，清理最久未使用的区块
        if (chunkCache.size() > MAX_CACHED_CHUNKS) {
            // 只在需要清理时才创建列表并排序
            List<MapChunk> chunks = new ArrayList<>(chunkCache.values());
            chunks.sort(Comparator.comparingLong(MapChunk::getLastAccessedTime));
            
            // 清理到最大缓存数量的75%，避免频繁清理
            int targetSize = (int)(MAX_CACHED_CHUNKS * 0.75);
            int chunksToRemove = chunkCache.size() - targetSize;
            
            for (int i = 0; i < chunksToRemove && i < chunks.size(); i++) {
                MapChunk chunk = chunks.get(i);
                chunkCache.remove(getChunkKey(chunk.getChunkX(), chunk.getChunkZ()));
            }
        }
        
        // 同时清理轻量级区块缓存
        cleanupLightweightChunkCache();
    }
    
    // 清理轻量级区块缓存
    private void cleanupLightweightChunkCache() {
        if (lightweightChunkCache.size() > MAX_LIGHTWEIGHT_CHUNK_CACHE_SIZE) {
            // 移除一定比例的最久未使用的轻量级数据
            int targetSize = (int)(MAX_LIGHTWEIGHT_CHUNK_CACHE_SIZE * 0.75);
            int chunksToRemove = lightweightChunkCache.size() - targetSize;
            
            // 使用流式操作进行高效过滤
            List<Map.Entry<Long, LightweightChunkData>> entries = new ArrayList<>(lightweightChunkCache.entrySet());
            entries.sort(Map.Entry.comparingByValue(Comparator.comparingLong(LightweightChunkData::getLastAccessedTime)));
            
            for (int i = 0; i < chunksToRemove && i < entries.size(); i++) {
                lightweightChunkCache.remove(entries.get(i).getKey());
            }
        }
    }
    
    // 获取轻量级区块数据
    private LightweightChunkData getLightweightChunkData(int chunkX, int chunkZ) {
        long chunkKey = getChunkKey(chunkX, chunkZ);
        return lightweightChunkCache.get(chunkKey);
    }
    
    // 初始化轻量级纹理管理器
    private void initializeLightweightTextures(MinecraftClient client) {
        // 获取Minecraft的纹理管理器
        textureManager = client.getTextureManager();
        
        // 预加载常用纹理
        registerLightweightTexture("track", "textures/block/rail.png");
        registerLightweightTexture("station", "textures/entity/villager/villager.png");
        registerLightweightTexture("player_marker", "textures/entity/player/wizard.png");
    }
    
    // 注册轻量级纹理
    private void registerLightweightTexture(String name, String texturePath) {
        Identifier id = new Identifier(texturePath);
        lightweightTextures.put(name, new LightweightTexture(textureManager, id));
    }
    
    // 获取轻量级纹理
    public LightweightTexture getLightweightTexture(String name) {
        return lightweightTextures.get(name);
    }

    private void drawMapMarkers(MatrixStack matrices, int x, int y, World world) {
        if (world == null || centerPos == null) {
            return;
        }
        
        // 获取线路控制系统实例
        LineControlSystem lineControlSystem = LineControlSystem.getInstance(world);
        Collection<LineControlSystem.LineInfo> lines = lineControlSystem.getAllLines();

        int effectiveTileSize = TILE_SIZE / zoomLevel;
        if (effectiveTileSize < 1) effectiveTileSize = 1;

        int halfWidth = mapWidth / 2 / effectiveTileSize;
        int halfHeight = mapHeight / 2 / effectiveTileSize;

        // 绘制所有线路的轨道和车站
        for (LineControlSystem.LineInfo line : lines) {
            // 只在有效缩放级别下绘制轨道
            if (effectiveTileSize <= 2) {
                drawTracks(matrices, x, y, line.getTracks(), effectiveTileSize, halfWidth, halfHeight);
            }
            
            // 绘制车站
            for (LineControlSystem.StationInfo station : line.getStations()) {
                drawStation(matrices, x, y, station, effectiveTileSize, halfWidth, halfHeight);
            }
        }

        // 绘制玩家位置标记
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null) {
            drawPlayerMarker(matrices, x, y, client.player.getBlockPos(), effectiveTileSize, halfWidth, halfHeight);
        }
    }

    private void drawTracks(MatrixStack matrices, int x, int y, Set<BlockPos> tracks, int tileSize, int halfWidth, int halfHeight) {
        for (BlockPos pos : tracks) {
            // 检查轨道是否在可见范围内
            if (Math.abs(pos.getX() - centerPos.getX()) <= halfWidth && Math.abs(pos.getZ() - centerPos.getZ()) <= halfHeight) {
                int screenX = x + (pos.getX() - centerPos.getX() + halfWidth) * tileSize;
                int screenY = y + (pos.getZ() - centerPos.getZ() + halfHeight) * tileSize;
                
                // 绘制轨道方块
                fill(matrices, screenX, screenY, screenX + tileSize - 1, screenY + tileSize - 1, 0xFFAAAAAA);
            }
        }
    }

    private void drawStation(MatrixStack matrices, int x, int y, LineControlSystem.StationInfo station, int tileSize, int halfWidth, int halfHeight) {
        BlockPos pos = station.getPosition();
        
        // 检查车站是否在可见范围内
        if (Math.abs(pos.getX() - centerPos.getX()) <= halfWidth && Math.abs(pos.getZ() - centerPos.getZ()) <= halfHeight) {
            int screenX = x + (pos.getX() - centerPos.getX() + halfWidth) * tileSize;
            int screenY = y + (pos.getZ() - centerPos.getZ() + halfHeight) * tileSize;
            
            // 绘制车站标记（红色方块）
            fill(matrices, screenX, screenY, screenX + tileSize - 1, screenY + tileSize - 1, 0xFFFF0000);
            
            // 绘制车站名称（简化版）
            if (tileSize >= 4) { // 只有在足够大的缩放级别下才显示名称
                TextRenderer textRenderer = MinecraftClient.getInstance().textRenderer;
                textRenderer.draw(station.getStationName(), screenX + tileSize + 2, screenY, 0xFFFFFFFF, false, matrices.peek().getPositionMatrix(), null, false, 0, 15728880);
            }
        }
    }

    private void drawPlayerMarker(MatrixStack matrices, int x, int y, BlockPos pos, int tileSize, int halfWidth, int halfHeight) {
        // 检查玩家是否在可见范围内
        if (Math.abs(pos.getX() - centerPos.getX()) <= halfWidth && Math.abs(pos.getZ() - centerPos.getZ()) <= halfHeight) {
            int screenX = x + (pos.getX() - centerPos.getX() + halfWidth) * tileSize;
            int screenY = y + (pos.getZ() - centerPos.getZ() + halfHeight) * tileSize;
            
            // 绘制玩家标记（绿色十字）
            int crossSize = Math.max(2, tileSize / 2);
            drawLine(matrices, screenX, screenY - crossSize, screenX, screenY + crossSize, 0xFF00FF00, 2);
            drawLine(matrices, screenX - crossSize, screenY, screenX + crossSize, screenY, 0xFF00FF00, 2);
        }
    }
    
    // 坐标转换方法：世界坐标转屏幕坐标
    public BlockPos screenToWorld(int screenX, int screenY, int mapX, int mapY) {
        int effectiveTileSize = TILE_SIZE / zoomLevel;
        if (effectiveTileSize < 1) effectiveTileSize = 1;
        
        int halfWidth = mapWidth / 2 / effectiveTileSize;
        int halfHeight = mapHeight / 2 / effectiveTileSize;
        
        int relX = (screenX - mapX) / effectiveTileSize - halfWidth;
        int relZ = (screenY - mapY) / effectiveTileSize - halfHeight;
        
        return new BlockPos(centerPos.getX() + relX, centerPos.getY(), centerPos.getZ() + relZ);
    }
    
    // 自定义绘制方法
    private void fill(MatrixStack matrices, int x1, int y1, int x2, int y2, int color) {
        int minX = Math.min(x1, x2);
        int minY = Math.min(y1, y2);
        int maxX = Math.max(x1, x2);
        int maxY = Math.max(y1, y2);
        
        float a = (float)(color >> 24 & 255) / 255.0F;
        float r = (float)(color >> 16 & 255) / 255.0F;
        float g = (float)(color >> 8 & 255) / 255.0F;
        float b = (float)(color & 255) / 255.0F;
        
        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder bufferBuilder = tessellator.getBuffer();
        bufferBuilder.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
        Matrix4f matrix4f = matrices.peek().getPositionMatrix();
        
        bufferBuilder.vertex(matrix4f, (float)minX, (float)maxY, 0.0F).color(r, g, b, a).next();
        bufferBuilder.vertex(matrix4f, (float)maxX, (float)maxY, 0.0F).color(r, g, b, a).next();
        bufferBuilder.vertex(matrix4f, (float)maxX, (float)minY, 0.0F).color(r, g, b, a).next();
        bufferBuilder.vertex(matrix4f, (float)minX, (float)minY, 0.0F).color(r, g, b, a).next();
        
        tessellator.draw();
    }
    
    private void drawLine(MatrixStack matrices, int x1, int y1, int x2, int y2, int color, int lineWidth) {
        float a = (float)(color >> 24 & 255) / 255.0F;
        float r = (float)(color >> 16 & 255) / 255.0F;
        float g = (float)(color >> 8 & 255) / 255.0F;
        float b = (float)(color & 255) / 255.0F;
        
        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder bufferBuilder = tessellator.getBuffer();
        Matrix4f matrix4f = matrices.peek().getPositionMatrix();
        
        // 简单的线条绘制，使用线段代替线条
        bufferBuilder.begin(VertexFormat.DrawMode.LINES, VertexFormats.POSITION_COLOR);
        bufferBuilder.vertex(matrix4f, (float)x1, (float)y1, 0.0F).color(r, g, b, a).next();
        bufferBuilder.vertex(matrix4f, (float)x2, (float)y2, 0.0F).color(r, g, b, a).next();
        tessellator.draw();
    }
    
    /**
     * 将地图渲染到NativeImage对象上，用于导出为图片
     */
    public void renderToImage(NativeImage image, int width, int height, World world) {
        if (centerPos == null) {
            // 如果没有中心位置，使用玩家位置
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.player != null) {
                setCenterPosition(client.player.getBlockPos());
            } else {
                return;
            }
        }
        
        // 创建一个矩阵堆栈用于渲染
        MatrixStack matrices = new MatrixStack();
        
        // 绘制地图到缓冲区（这里我们不直接渲染到NativeImage，而是通过现有的渲染逻辑）
        // 在实际实现中，完整的解决方案需要使用Framebuffer来捕获渲染结果
        // 然后将Framebuffer的内容复制到NativeImage
        
        // 这里我们提供一个简化的实现，直接使用现有的渲染方法
        // 但由于Minecraft的渲染系统限制，这种方法可能无法直接工作
        // 完整的实现需要更复杂的渲染管道设置
        
        // 注意：在实际项目中，你可能需要使用专门的渲染上下文和Framebuffer来正确实现这个功能
        render(matrices, 0, 0, world);
    }
    
    // 清理资源
    public void cleanup() {
        // 关闭区块加载线程池
        chunkLoader.shutdown();
        try {
            if (!chunkLoader.awaitTermination(500, TimeUnit.MILLISECONDS)) {
                chunkLoader.shutdownNow();
            }
        } catch (InterruptedException e) {
            chunkLoader.shutdownNow();
            Thread.currentThread().interrupt();
        }
        
        // 清理所有缓存
        chunkCache.clear();
        lightweightChunkCache.clear();
        
        // 释放轻量级纹理资源
        for (LightweightTexture texture : lightweightTextures.values()) {
            texture.close();
        }
        lightweightTextures.clear();
        
        // 清理渲染批次
        for (List<RenderBatch> batches : renderBatches.values()) {
            batches.clear();
        }
        
        // 释放其他资源
        renderThreadPool.shutdown();
        try {
            if (!renderThreadPool.awaitTermination(500, TimeUnit.MILLISECONDS)) {
                renderThreadPool.shutdownNow();
            }
        } catch (InterruptedException e) {
            renderThreadPool.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}