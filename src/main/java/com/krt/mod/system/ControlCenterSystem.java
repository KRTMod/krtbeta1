package com.krt.mod.system;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import com.krt.mod.KRTMod;
import java.util.*;
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

/**
 * 控制中心系统
 * 提供列车运行状态、信号状态和警报信息的实时监控界面
 */
public class ControlCenterSystem {
    private static final Map<World, ControlCenterSystem> INSTANCES = new HashMap<>();
    private final World world;
    private final CBTCSystem cbtcSystem;
    private final Map<String, TrainDisplayInfo> trainDisplayCache = new ConcurrentHashMap<>();
    private final List<AlertDisplayInfo> alertDisplayCache = new CopyOnWriteArrayList<>();
    private static final int CACHE_UPDATE_INTERVAL = 1000; // 缓存更新间隔（毫秒）
    private long lastCacheUpdateTime = 0;
    
    // 数据格式化器接口和实现，符合开闭原则
    public interface DataFormatter<T> {
        String format(T data);
    }
    
    // 列车信息格式化器
    private static class TrainFormatter implements DataFormatter<TrainDisplayInfo> {
        @Override
        public String format(TrainDisplayInfo info) {
            return String.format("%s | %s | %.0f,%.0f,%.0f | %.1f | %s | %s | %s",
                    info.getTrainId(),
                    info.getTrainName(),
                    info.getX(), info.getY(), info.getZ(),
                    info.getSpeed() * 3.6, // m/s 转 km/h
                    info.getLineId(),
                    info.getCurrentStation(),
                    info.getNextStation());
        }
    }
    
    // 警报信息格式化器
    private static class AlertFormatter implements DataFormatter<AlertDisplayInfo> {
        @Override
        public String format(AlertDisplayInfo info) {
            String timeStr = new Date(info.getTimestamp()).toString();
            return String.format("%s | %s | %d | %s | %s",
                    info.getTrainId(),
                    info.getAlertType(),
                    info.getSeverity(),
                    info.getDescription(),
                    timeStr);
        }
    }
    
    // 道岔信息格式化器
    private static class SwitchFormatter implements DataFormatter<Map.Entry<String, String>> {
        @Override
        public String format(Map.Entry<String, String> entry) {
            return entry.getKey() + " | " + entry.getValue();
        }
    }
    
    // 格式化器实例
    private final DataFormatter<TrainDisplayInfo> trainFormatter = new TrainFormatter();
    private final DataFormatter<AlertDisplayInfo> alertFormatter = new AlertFormatter();
    private final DataFormatter<Map.Entry<String, String>> switchFormatter = new SwitchFormatter();
    
    private ControlCenterSystem(World world) {
        this.world = world;
        this.cbtcSystem = CBTCSystem.getInstance(world);
    }
    
    public static ControlCenterSystem getInstance(World world) {
        return INSTANCES.computeIfAbsent(world, ControlCenterSystem::new);
    }
    
    // 更新显示缓存
    private void updateCache() {
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastCacheUpdateTime < CACHE_UPDATE_INTERVAL) {
            return;
        }
        
        // 更新列车显示信息
        Map<String, TrainPositionInfo> trainPositions = cbtcSystem.getTrainPositions();
        if (trainPositions != null) {
            trainPositions.forEach((trainId, positionInfo) -> {
                trainDisplayCache.put(trainId, new TrainDisplayInfo(trainId, positionInfo));
            });
        }
        
        // 更新警报显示信息
        Map<String, List<AlertInfo>> activeAlerts = cbtcSystem.getActiveAlerts();
        if (activeAlerts != null) {
            alertDisplayCache.clear();
            activeAlerts.forEach((trainId, alerts) -> {
                alerts.forEach(alert -> {
                    alertDisplayCache.add(new AlertDisplayInfo(trainId, alert));
                });
            });
            // 按严重程度排序
            alertDisplayCache.sort(Comparator.comparingInt(AlertDisplayInfo::getSeverity).reversed());
        }
        
        lastCacheUpdateTime = currentTime;
    }
    
    // 获取列车显示信息
    public Collection<TrainDisplayInfo> getTrainDisplayInfo() {
        updateCache();
        return trainDisplayCache.values();
    }
    
    // 获取警报显示信息
    public List<AlertDisplayInfo> getAlertDisplayInfo() {
        updateCache();
        return new ArrayList<>(alertDisplayCache);
    }
    
    // 获取道岔状态信息
    public Map<String, String> getSwitchStatusInfo() {
        Map<String, String> switchStatus = new HashMap<>();
        SwitchControlSystem switchSystem = SwitchControlSystem.getInstance(world);
        
        // 使用SwitchControlSystem中已有的getAllSwitchControlStates方法获取所有道岔状态
        for (SwitchControlSystem.SwitchControlState controlState : switchSystem.getAllSwitchControlStates()) {
            BlockPos pos = controlState.getSwitchPos();
            String switchId = String.format("道岔_%d_%d_%d", pos.getX(), pos.getY(), pos.getZ());
            String stateStr = getSwitchStateString(controlState.getCurrentState());
            String modeStr = controlState.getMode() != null ? "/" + controlState.getMode() : "";
            switchStatus.put(switchId, stateStr + modeStr);
        }
        
        return switchStatus;
    }
    
    // 将道岔状态转换为可读字符串
    private String getSwitchStateString(SwitchControlSystem.SwitchState state) {
        switch (state) {
            case NORMAL: return "直股";  
            case REVERSE: return "弯股";  
            case MOVING: return "转换中";  
            case LOCKED: return "锁定";  
            case FAILED: return "故障";  
            default: return "未知";  
        }
    }
    
    // 打开控制中心界面
    public void openControlCenterScreen() {
        MinecraftClient.getInstance().send(() -> {
            MinecraftClient.getInstance().setScreen(new ControlCenterScreen());
        });
    }
    
    /**
     * 列车位置信息类
     */
    public static class TrainPositionInfo {
        private final String trainName;
        private final Position position;
        private final double speed;
        private final double direction;
        private final String lineId;
        private final String currentStation;
        private final String nextStation;
        
        public TrainPositionInfo(String trainName, double x, double y, double z, double speed, double direction) {
            this.trainName = trainName;
            this.position = new Position(x, y, z);
            this.speed = speed;
            this.direction = direction;
            this.lineId = null;
            this.currentStation = null;
            this.nextStation = null;
        }
        
        public String getTrainName() { return trainName; }
        public Position getPosition() { return position; }
        public double getSpeed() { return speed; }
        public double getDirection() { return direction; }
        public String getLineId() { return lineId; }
        public String getCurrentStation() { return currentStation; }
        public String getNextStation() { return nextStation; }
        
        /**
         * 位置坐标内部类
         */
        public static class Position {
            private final double x, y, z;
            
            public Position(double x, double y, double z) {
                this.x = x;
                this.y = y;
                this.z = z;
            }
            
            public double getX() { return x; }
            public double getY() { return y; }
            public double getZ() { return z; }
        }
    }
    
    /**
     * 列车显示信息类
     */
    public static class TrainDisplayInfo {
        private final String trainId;
        private final String trainName;
        private final double x, y, z;
        private final double speed;
        private final double direction;
        private final String lineId;
        private final String currentStation;
        private final String nextStation;
        
        public TrainDisplayInfo(String trainId, TrainPositionInfo positionInfo) {
            this.trainId = trainId;
            this.trainName = positionInfo.getTrainName() != null ? positionInfo.getTrainName() : trainId;
            this.x = positionInfo.getPosition().getX();
            this.y = positionInfo.getPosition().getY();
            this.z = positionInfo.getPosition().getZ();
            this.speed = positionInfo.getSpeed();
            this.direction = positionInfo.getDirection();
            this.lineId = positionInfo.getLineId() != null ? positionInfo.getLineId() : "未知";
            this.currentStation = positionInfo.getCurrentStation() != null ? positionInfo.getCurrentStation() : "运行中";
            this.nextStation = positionInfo.getNextStation() != null ? positionInfo.getNextStation() : "未知";
        }
        
        public String getTrainId() { return trainId; }
        public String getTrainName() { return trainName; }
        public double getX() { return x; }
        public double getY() { return y; }
        public double getZ() { return z; }
        public double getSpeed() { return speed; }
        public double getDirection() { return direction; }
        public String getLineId() { return lineId; }
        public String getCurrentStation() { return currentStation; }
        public String getNextStation() { return nextStation; }
    }
    
    /**
     * 警报显示信息类
     */
    public static class AlertDisplayInfo {
        private final String trainId;
        private final String alertId;
        private final String alertType;
        private final int severity;
        private final String description;
        private final long timestamp;
        private final Map<String, String> additionalInfo;
        
        // 定义缺失的AlertInfo类
        public static class AlertInfo {
            private final String id;
            private final AlertType type;
            private final int severity;
            private final String description;
            private final long timestamp;
            private final Map<String, String> additionalInfo;
            
            public AlertInfo(String id, AlertType type, int severity, String description) {
                this.id = id;
                this.type = type;
                this.severity = severity;
                this.description = description;
                this.timestamp = System.currentTimeMillis();
                this.additionalInfo = new HashMap<>();
            }
            
            public String getId() { return id; }
            public AlertType getType() { return type; }
            public int getSeverity() { return severity; }
            public String getDescription() { return description; }
            public long getTimestamp() { return timestamp; }
            public Map<String, String> getAdditionalInfo() { return additionalInfo; }
        }
        
        // AlertType枚举
        public enum AlertType {
            TRAIN, SIGNAL, TRACK, STATION, SYSTEM
        }
        
        public AlertDisplayInfo(String trainId, AlertInfo alertInfo) {
            this.trainId = trainId;
            this.alertId = alertInfo.getId();
            this.alertType = alertInfo.getType().toString();
            this.severity = alertInfo.getSeverity();
            this.description = alertInfo.getDescription();
            this.timestamp = alertInfo.getTimestamp();
            this.additionalInfo = alertInfo.getAdditionalInfo();
        }
        
        public String getTrainId() { return trainId; }
        public String getAlertId() { return alertId; }
        public String getAlertType() { return alertType; }
        public int getSeverity() { return severity; }
        public String getDescription() { return description; }
        public long getTimestamp() { return timestamp; }
        public Map<String, String> getAdditionalInfo() { return additionalInfo; }
    }
    
    /**
     * 控制中心界面
     */
    public static class ControlCenterScreen extends Screen {
        private int selectedTab = 0; // 0:列车监控, 1:警报信息, 2:道岔状态
        private TextFieldWidget searchField;
        private ScrollablePanel scrollablePanel;
        
        public ControlCenterScreen() {
            super(Text.literal("地铁控制中心"));
        }
        
        @Override
        protected void init() {
            super.init();
            
            // 搜索框
            searchField = new TextFieldWidget(textRenderer, width / 4, 40, width / 2, 20, Text.literal("搜索"));
            addDrawableChild(searchField);
            
            // 标签按钮
            int buttonWidth = 100;
            int buttonHeight = 20;
            int buttonY = 65;
            
            addDrawableChild(ButtonWidget.builder(Text.literal("列车监控"), button -> {
                selectedTab = 0;
                updateScrollablePanel();
            }).position(50, buttonY).size(buttonWidth, buttonHeight).build());
            
            addDrawableChild(ButtonWidget.builder(Text.literal("警报信息"), button -> {
                selectedTab = 1;
                updateScrollablePanel();
            }).position(160, buttonY).size(buttonWidth, buttonHeight).build());
            
            addDrawableChild(ButtonWidget.builder(Text.literal("道岔状态"), button -> {
                selectedTab = 2;
                updateScrollablePanel();
            }).position(270, buttonY).size(buttonWidth, buttonHeight).build());
            
            // 关闭按钮
            addDrawableChild(ButtonWidget.builder(Text.literal("关闭"), button -> {
                close();
            }).position(width - 80, 20).size(60, 20).build());
            
            // 初始化滚动面板
            scrollablePanel = new ScrollablePanel(client, width - 40, height - 120, 25, 100);
            addDrawableChild(scrollablePanel);
        }
        
        // 优化后的搜索方法，支持模糊匹配和大小写不敏感
        private boolean matchesSearch(String content, String searchText) {
            if (searchText.isEmpty()) return true;
            // 转换为小写进行不区分大小写的匹配
            String lowerContent = content.toLowerCase();
            String lowerSearchText = searchText.toLowerCase();
            return lowerContent.contains(lowerSearchText);
        }
        
        private void updateScrollablePanel() {
            scrollablePanel.setContentProvider(() -> {
                List<String> lines = new ArrayList<>();
                ControlCenterSystem controlSystem = ControlCenterSystem.getInstance(client.world);
                String searchText = searchField.getText();
                
                if (selectedTab == 0) {
                    // 列车监控
                    lines.add("=== 列车监控信息 ===");
                    lines.add("列车ID | 名称 | 位置 | 速度(km/h) | 线路 | 当前站 | 下一站");
                    lines.add("--------------------------------------------------------------------------------------------------------");
                    
                    for (TrainDisplayInfo info : controlSystem.getTrainDisplayInfo()) {
                        if (matchesSearch(info.getTrainId(), searchText) ||
                            matchesSearch(info.getTrainName(), searchText) ||
                            matchesSearch(info.getLineId(), searchText) ||
                            matchesSearch(info.getCurrentStation(), searchText) ||
                            matchesSearch(info.getNextStation(), searchText)) {
                            lines.add(controlSystem.trainFormatter.format(info));
                        }
                    }
                } else if (selectedTab == 1) {
                    // 警报信息
                    lines.add("=== 警报信息 ===");
                    lines.add("列车ID | 类型 | 严重程度 | 描述 | 时间");
                    lines.add("--------------------------------------------------------------------------------------------------------");
                    
                    for (AlertDisplayInfo info : controlSystem.getAlertDisplayInfo()) {
                        if (matchesSearch(info.getTrainId(), searchText) ||
                            matchesSearch(info.getAlertType(), searchText) ||
                            matchesSearch(info.getDescription(), searchText)) {
                            lines.add(controlSystem.alertFormatter.format(info));
                        }
                    }
                } else if (selectedTab == 2) {
                    // 道岔状态
                    lines.add("=== 道岔状态 ===");
                    lines.add("道岔ID | 状态");
                    lines.add("-------------------");
                    
                    Map<String, String> switchStatus = controlSystem.getSwitchStatusInfo();
                    for (Map.Entry<String, String> entry : switchStatus.entrySet()) {
                        if (matchesSearch(entry.getKey(), searchText) ||
                            matchesSearch(entry.getValue(), searchText)) {
                            lines.add(controlSystem.switchFormatter.format(entry));
                        }
                    }
                }
                
                return lines;
            });
        }
        
        private long lastPanelUpdateTime = 0;
        private int lastSelectedTab = -1;
        private String lastSearchText = "";
        
        @Override
        public void render(MatrixStack matrices, int mouseX, int mouseY, float delta) {
            renderBackground(matrices);
            super.render(matrices, mouseX, mouseY, delta);
            
            // 渲染标题
            drawCenteredText(matrices, textRenderer, title, width / 2, 10, 0xFFFFFF);
            
            // 优化：只有在标签页切换、搜索文本改变或超过1秒时才更新面板内容
            long currentTime = System.currentTimeMillis();
            String currentSearchText = searchField.getText();
            if (selectedTab != lastSelectedTab || 
                !currentSearchText.equals(lastSearchText) || 
                currentTime - lastPanelUpdateTime > 1000) {
                updateScrollablePanel();
                lastSelectedTab = selectedTab;
                lastSearchText = currentSearchText;
                lastPanelUpdateTime = currentTime;
            }
            
            // 显示最后更新时间
            String updateTime = "最后更新: " + new Date().toString();
            drawTextWithShadow(matrices, textRenderer, updateTime, width - textRenderer.getWidth(updateTime) - 10, height - 20, 0xAAAAAA);
        }
        
        @Override
        public boolean shouldCloseOnEsc() {
            return true;
        }
    }
    
    /**
     * 滚动面板组件
     */
    public static class ScrollablePanel extends ButtonWidget {
        private final int contentHeight;
        private int scrollPosition = 0;
        private final List<String> cachedLines = new ArrayList<>();
        private Supplier<List<String>> contentProvider;
        private long lastUpdateTime = 0;
        
        public ScrollablePanel(MinecraftClient client, int width, int height, int x, int y) {
            super(x, y, width, height, Text.empty(), button -> {}, DEFAULT_NARRATION_SUPPLIER);
            this.contentHeight = height;
        }
        
        public void setContentProvider(Supplier<List<String>> provider) {
            this.contentProvider = provider;
            // 立即更新内容，不需要等待时间间隔
            cachedLines.clear();
            if (contentProvider != null) {
                cachedLines.addAll(contentProvider.get());
            }
            lastUpdateTime = System.currentTimeMillis();
        }
        
        private void updateContent() {
            // 内容更新逻辑已经移至setContentProvider，这里不再需要
        }
        
        @Override
        public void render(MatrixStack matrices, int mouseX, int mouseY, float delta) {
            // 渲染面板背景
            fill(matrices, getX(), getY(), getX() + width, getY() + height, 0xFF1E1E1E);
            fill(matrices, getX(), getY(), getX() + width, getY() + 1, 0xFFAAAAAA);
            fill(matrices, getX(), getY() + height - 1, getX() + width, getY() + height, 0xFFAAAAAA);
            
            // 渲染文本内容
            TextRenderer textRenderer = MinecraftClient.getInstance().textRenderer;
            int lineHeight = textRenderer.fontHeight + 2;
            int visibleLines = contentHeight / lineHeight;
            int maxVisibleWidth = width - 15; // 留出右边距
            
            int startLine = scrollPosition;
            int endLine = Math.min(startLine + visibleLines, cachedLines.size());
            
            for (int i = startLine; i < endLine; i++) {
                String line = cachedLines.get(i);
                int yOffset = (i - startLine) * lineHeight;
                
                // 根据内容类型设置不同颜色
                int color = 0xFFFFFF;
                if (i == 0) {
                    color = 0xFFFFAA00; // 标题颜色
                } else if (i == 1) {
                    color = 0xFFAAAAAA; // 表头颜色
                } else if (i == 2) {
                    continue; // 跳过分隔线
                } else if (line.contains("ALERT") || line.contains("WARNING")) {
                    color = 0xFFFF0000; // 警报颜色
                }
                
                // 文本视口裁剪：如果文本太长，截断并添加省略号
                String displayText = line;
                if (textRenderer.getWidth(line) > maxVisibleWidth) {
                    displayText = textRenderer.trimToWidth(line, maxVisibleWidth - 10) + "...";
                }
                
                drawTextWithShadow(matrices, textRenderer, displayText, getX() + 5, getY() + 5 + yOffset, color);
            }
            
            // 渲染滚动条
            if (cachedLines.size() > visibleLines) {
                int scrollbarHeight = Math.max(20, (contentHeight * visibleLines) / cachedLines.size());
                int scrollbarY = getY() + 5 + (scrollPosition * (contentHeight - scrollbarHeight - 10)) / 
                                Math.max(1, cachedLines.size() - visibleLines);
                
                fill(matrices, getX() + width - 10, scrollbarY, getX() + width - 5, scrollbarY + scrollbarHeight, 0xFFAAAAAA);
            }
        }
        
        @Override
        public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
            if (isMouseOver(mouseX, mouseY)) {
                int lineHeight = MinecraftClient.getInstance().textRenderer.fontHeight + 2;
                int visibleLines = contentHeight / lineHeight;
                
                if (verticalAmount < 0) {
                    // 向下滚动
                    scrollPosition = Math.min(scrollPosition + 3, Math.max(0, cachedLines.size() - visibleLines));
                } else {
                    // 向上滚动
                    scrollPosition = Math.max(scrollPosition - 3, 0);
                }
                return true;
            }
            return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
        }
    }
    
    /**
     * 内容提供者接口
     */
    public interface Supplier<T> {
        T get();
    }
}