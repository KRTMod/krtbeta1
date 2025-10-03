package com.krt.mod.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import com.krt.mod.KRTMod;
import com.krt.mod.system.LineControlSystem;
import com.krt.mod.system.DepotManagementSystem;
import com.krt.mod.system.LanguageSystem;
import com.krt.mod.system.LogSystem;
import com.krt.mod.system.SaveDataManager;
import com.krt.mod.map.MapExporter;
import java.util.*;

public class LineControlPanel extends Screen {
    private static final int WIDTH = 400;
    private static final int HEIGHT = 300;
    private static final int MAP_WIDTH = 250;
    private static final int MAP_HEIGHT = 250;
    private TextFieldWidget lineIdField;
    private TextFieldWidget lineNameField;
    private TextFieldWidget stationIdField;
    private TextFieldWidget stationNameField;
    private ButtonWidget createLineButton;
    private ButtonWidget addStationButton;
    private ButtonWidget checkLineButton;
    private ButtonWidget createDepotButton;
    private ButtonWidget backButton;
    private ButtonWidget nextButton;
    private ButtonWidget zoomInButton;
    private ButtonWidget zoomOutButton;
    private int currentPage = 0;
    private final World world;
    private final LineControlSystem lineControlSystem;
    private final DepotManagementSystem depotManagementSystem;
    private MapRenderer mapRenderer;
    private int mapX, mapY;
    private int currentZoomLevel = 1; // 初始缩放级别

    public LineControlPanel() {
        super(Text.literal(LanguageSystem.translate("krt.line.control_panel")));
        this.world = client.world;
        this.lineControlSystem = LineControlSystem.getInstance(world);
        this.depotManagementSystem = DepotManagementSystem.getInstance(world);
        this.mapRenderer = new MapRenderer(MAP_WIDTH, MAP_HEIGHT);
    }

    @Override
    protected void init() {
        int x = (width - WIDTH) / 2;
        int y = (height - HEIGHT) / 2;
        
        // 计算小地图位置（右侧面板外部）
        this.mapX = x + WIDTH + 20;
        this.mapY = y;
        
        // 加载存档数据
        if (world != null) {
            SaveDataManager.loadRailwayData(world);
        }
        
        // 添加地图缩放按钮
        this.zoomInButton = new ButtonWidget(mapX + MAP_WIDTH - 20, mapY + 5, 15, 15, Text.literal(LanguageSystem.translate("krt.map.zoom_in")), button -> {
            this.zoomIn();
        });
        addDrawableChild(zoomInButton);
        
        this.zoomOutButton = new ButtonWidget(mapX + MAP_WIDTH - 40, mapY + 5, 15, 15, Text.literal(LanguageSystem.translate("krt.map.zoom_out")), button -> {
            this.zoomOut();
        });
        addDrawableChild(zoomOutButton);
        
        // 添加导出图片按钮
        ButtonWidget exportImageButton = new ButtonWidget(mapX, mapY + MAP_HEIGHT + 10, 120, 20, Text.literal(LanguageSystem.translate("krt.map.export_image")), button -> {
            if (world != null) {
                // 使用默认路径导出图片
                String filePath = MapExporter.exportMapAsImage(mapRenderer, world, null);
                if (filePath != null) {
                    client.player.sendMessage(Text.literal("地图已导出为图片: " + filePath), false);
                } else {
                    client.player.sendMessage(Text.literal("图片导出失败！"), false);
                }
            }
        });
        addDrawableChild(exportImageButton);
        
        // 添加下载文件按钮
        ButtonWidget downloadFileButton = new ButtonWidget(mapX + 125, mapY + MAP_HEIGHT + 10, 120, 20, Text.literal(LanguageSystem.translate("krt.map.download_file")), button -> {
            if (world != null) {
                // 打开下载位置选择界面
                client.setScreen(new DownloadLocationScreen(this, path -> {
                    // 生成地图数据（简化版）
                    String mapData = "{\"map_data\": \"This is a sample map data\"}";
                    
                    // 使用用户选择的路径下载文件
                    String filePath = MapExporter.downloadMapFile(mapData, path);
                    if (filePath != null) {
                        client.player.sendMessage(Text.literal("地图文件已下载: " + filePath), false);
                    } else {
                        client.player.sendMessage(Text.literal("文件下载失败！"), false);
                    }
                }));
            }
        });
        addDrawableChild(downloadFileButton);

        // 第一页：线路创建
        if (currentPage == 0) {
            // 线路ID输入框
            lineIdField = new TextFieldWidget(textRenderer, x + 20, y + 40, 150, 20, Text.literal("Line ID"));
            lineIdField.setText("line_1");
            addDrawableChild(lineIdField);

            // 线路名称输入框
            lineNameField = new TextFieldWidget(textRenderer, x + 20, y + 80, 150, 20, Text.literal("Line Name"));
            lineNameField.setText("Line 1");
            addDrawableChild(lineNameField);

            // 创建线路按钮
            createLineButton = new ButtonWidget(x + 20, y + 120, 150, 20, Text.literal(LanguageSystem.translate("krt.gui.create")), button -> {
                String lineId = lineIdField.getText();
                String lineName = lineNameField.getText();
                if (!lineId.isEmpty() && !lineName.isEmpty()) {
                    lineControlSystem.createLine(lineId, lineName);
                    LogSystem.lineLog(lineId, "线路已创建: " + lineName);
                    
                    // 保存数据到存档
                    if (world != null) {
                        SaveDataManager.saveRailwayData(world);
                    }
                }
            });
            addDrawableChild(createLineButton);

            // 检查线路按钮
            checkLineButton = new ButtonWidget(x + 20, y + 160, 150, 20, Text.literal(LanguageSystem.translate("krt.gui.check_line")), button -> {
                String lineId = lineIdField.getText();
                if (!lineId.isEmpty()) {
                    List<String> issues = lineControlSystem.checkLineIntegrity(lineId);
                    if (issues.isEmpty()) {
                        client.player.sendMessage(Text.literal("线路检查通过！"), false);
                    } else {
                        for (String issue : issues) {
                            client.player.sendMessage(Text.literal("线路问题: " + issue), false);
                        }
                    }
                }
            });
            addDrawableChild(checkLineButton);

            // 创建车厂按钮
            createDepotButton = new ButtonWidget(x + 20, y + 200, 150, 20, Text.literal(LanguageSystem.translate("krt.gui.create_depot")), button -> {
                currentPage = 1;
                init();
            });
            addDrawableChild(createDepotButton);
            
            // 调度中心按钮
            ButtonWidget dispatchCenterButton = new ButtonWidget(x + 20, y + 230, 150, 20, Text.literal(LanguageSystem.translate("krt.gui.dispatch_center")), button -> {
                if (client.player != null) {
                    // 打开调度中心界面
                    client.setScreen(new DispatchCenterScreen());
                }
            });
            addDrawableChild(dispatchCenterButton);
        }
        // 第二页：车厂创建
        else if (currentPage == 1) {
            // 这里可以添加车厂创建相关的UI元素
            // 简化版：只有返回按钮
        }

        // 下一页按钮
        if (currentPage < 1) {
            nextButton = new ButtonWidget(x + WIDTH - 100, y + HEIGHT - 40, 80, 20, Text.literal(LanguageSystem.translate("krt.gui.next")), button -> {
                currentPage++;
                init();
            });
            addDrawableChild(nextButton);
        }

        // 上一页按钮
        if (currentPage > 0) {
            backButton = new ButtonWidget(x + 20, y + HEIGHT - 40, 80, 20, Text.literal(LanguageSystem.translate("krt.gui.back")), button -> {
                currentPage--;
                init();
            });
            addDrawableChild(backButton);
        }

        // 关闭按钮
        ButtonWidget closeButton = new ButtonWidget(x + WIDTH - 100, y + 20, 80, 20, Text.literal(LanguageSystem.translate("krt.gui.close")), button -> {
            client.setScreen(null);
        });
        addDrawableChild(closeButton);
    }

    @Override
    public void render(MatrixStack matrices, int mouseX, int mouseY, float delta) {
        renderBackground(matrices);
        super.render(matrices, mouseX, mouseY, delta);

        int x = (width - WIDTH) / 2;
        int y = (height - HEIGHT) / 2;

        // 绘制标题
        drawCenteredText(matrices, textRenderer, title, width / 2, y + 10, 0xFFFFFF);
        
        // 绘制小地图（如果世界存在）
        if (world != null) {
            // 在渲染其他UI元素之后渲染地图，确保它在最上层
            mapRenderer.render(matrices, mapX, mapY, world);
            drawTextWithShadow(matrices, textRenderer, Text.literal(LanguageSystem.translate("krt.gui.line_map")), 
                              mapX + (MAP_WIDTH - textRenderer.getWidth(LanguageSystem.translate("krt.gui.line_map"))) / 2, 
                              mapY - 20, 0xFFFFFFFF);
        }

        // 第一页：线路创建
        if (currentPage == 0) {
            drawTextWithShadow(matrices, textRenderer, Text.literal(LanguageSystem.translate("krt.gui.line_id")), x + 20, y + 30, 0xA0A0A0);
            drawTextWithShadow(matrices, textRenderer, Text.literal(LanguageSystem.translate("krt.gui.line_name")), x + 20, y + 70, 0xA0A0A0);

            // 显示已创建的线路
            int lineY = y + 240;
            drawTextWithShadow(matrices, textRenderer, Text.literal(LanguageSystem.translate("krt.gui.created_lines")), x + 20, lineY - 20, 0xA0A0A0);
            for (LineControlSystem.LineInfo line : lineControlSystem.getAllLines()) {
                drawTextWithShadow(matrices, textRenderer, Text.literal(line.getLineId() + ": " + line.getLineName()), x + 20, lineY, 0xFFFFFF);
                lineY += 12;
            }
        }
        // 第二页：车厂创建
        else if (currentPage == 1) {
            drawCenteredText(matrices, textRenderer, Text.literal(LanguageSystem.translate("krt.gui.depot_creation")), width / 2, y + 40, 0xFFFFFF);
            drawTextWithShadow(matrices, textRenderer, Text.literal(LanguageSystem.translate("krt.gui.depot_guide")), x + 20, y + 70, 0xA0A0A0);
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256) { // ESC键
            this.client.setScreen(null);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void removed() {
        super.removed();
        // 清理地图渲染器资源
        if (mapRenderer != null) {
            mapRenderer.cleanup();
        }
    }
    
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // 处理小地图点击事件
        if (world != null && mouseX >= mapX && mouseX <= mapX + MAP_WIDTH && 
            mouseY >= mapY && mouseY <= mapY + MAP_HEIGHT) {
            // 将地图中心点设置为点击位置对应的世界坐标
            BlockPos newCenterPos = this.mapRenderer.screenToWorld((int)mouseX, (int)mouseY, mapX, mapY);
            this.mapRenderer.setCenterPosition(newCenterPos);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }
    
    // 地图缩放功能
    private void zoomIn() {
        if (currentZoomLevel < 5) { // 最大缩放级别为5
            currentZoomLevel++;
            this.mapRenderer.setZoomLevel(currentZoomLevel);
            // 缩放级别改变时，中心区块可能需要重新计算
            updateMapCenter();
        }
    }
    
    private void zoomOut() {
        if (currentZoomLevel > 1) { // 最小缩放级别为1
            currentZoomLevel--;
            this.mapRenderer.setZoomLevel(currentZoomLevel);
            // 缩放级别改变时，中心区块可能需要重新计算
            updateMapCenter();
        }
    }
    
    // 获取当前缩放级别（供MapRenderer使用）
    public int getZoomLevel() {
        return currentZoomLevel;
    }

    private void updateMapCenter() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null) {
            mapRenderer.setCenterPosition(client.player.getBlockPos());
        }
    }
}