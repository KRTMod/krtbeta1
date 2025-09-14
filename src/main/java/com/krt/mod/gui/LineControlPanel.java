package com.krt.mod.gui;

import com.mojang.blaze3d.systems.RenderSystem;
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
import java.util.*;

public class LineControlPanel extends Screen {
    private static final int WIDTH = 400;
    private static final int HEIGHT = 300;
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
    private int currentPage = 0;
    private final World world;
    private final LineControlSystem lineControlSystem;
    private final DepotManagementSystem depotManagementSystem;

    public LineControlPanel() {
        super(Text.literal(LanguageSystem.translate("krt.line.control_panel")));
        this.world = client.world;
        this.lineControlSystem = LineControlSystem.getInstance(world);
        this.depotManagementSystem = DepotManagementSystem.getInstance(world);
    }

    @Override
    protected void init() {
        int x = (width - WIDTH) / 2;
        int y = (height - HEIGHT) / 2;

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
            createLineButton = ButtonWidget.builder(Text.literal(LanguageSystem.translate("krt.gui.create")), button -> {
                String lineId = lineIdField.getText();
                String lineName = lineNameField.getText();
                if (!lineId.isEmpty() && !lineName.isEmpty()) {
                    lineControlSystem.createLine(lineId, lineName);
                    LogSystem.lineLog(lineId, "线路已创建: " + lineName);
                }
            }).dimensions(x + 20, y + 120, 150, 20).build();
            addDrawableChild(createLineButton);

            // 检查线路按钮
            checkLineButton = ButtonWidget.builder(Text.literal(LanguageSystem.translate("krt.gui.check_line")), button -> {
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
            }).dimensions(x + 20, y + 160, 150, 20).build();
            addDrawableChild(checkLineButton);

            // 创建车厂按钮
            createDepotButton = ButtonWidget.builder(Text.literal(LanguageSystem.translate("krt.gui.create_depot")), button -> {
                currentPage = 1;
                init();
            }).dimensions(x + 20, y + 200, 150, 20).build();
            addDrawableChild(createDepotButton);
        }
        // 第二页：车厂创建
        else if (currentPage == 1) {
            // 这里可以添加车厂创建相关的UI元素
            // 简化版：只有返回按钮
        }

        // 下一页按钮
        if (currentPage < 1) {
            nextButton = ButtonWidget.builder(Text.literal(LanguageSystem.translate("krt.gui.next")), button -> {
                currentPage++;
                init();
            }).dimensions(x + WIDTH - 100, y + HEIGHT - 40, 80, 20).build();
            addDrawableChild(nextButton);
        }

        // 上一页按钮
        if (currentPage > 0) {
            backButton = ButtonWidget.builder(Text.literal(LanguageSystem.translate("krt.gui.back")), button -> {
                currentPage--;
                init();
            }).dimensions(x + 20, y + HEIGHT - 40, 80, 20).build();
            addDrawableChild(backButton);
        }

        // 关闭按钮
        ButtonWidget closeButton = ButtonWidget.builder(Text.literal(LanguageSystem.translate("krt.gui.close")), button -> {
            client.setScreen(null);
        }).dimensions(x + WIDTH - 100, y + 20, 80, 20).build();
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

        // 第一页：线路创建
        if (currentPage == 0) {
            drawTextWithShadow(matrices, textRenderer, LanguageSystem.translate("krt.gui.line_id"), x + 20, y + 30, 0xA0A0A0);
            drawTextWithShadow(matrices, textRenderer, LanguageSystem.translate("krt.gui.line_name"), x + 20, y + 70, 0xA0A0A0);

            // 显示已创建的线路
            int lineY = y + 240;
            drawTextWithShadow(matrices, textRenderer, LanguageSystem.translate("krt.gui.created_lines"), x + 20, lineY - 20, 0xA0A0A0);
            for (LineControlSystem.LineInfo line : lineControlSystem.getAllLines()) {
                drawTextWithShadow(matrices, textRenderer, line.getLineId() + ": " + line.getLineName(), x + 20, lineY, 0xFFFFFF);
                lineY += 12;
            }
        }
        // 第二页：车厂创建
        else if (currentPage == 1) {
            drawCenteredText(matrices, textRenderer, LanguageSystem.translate("krt.gui.depot_creation"), width / 2, y + 40, 0xFFFFFF);
            drawTextWithShadow(matrices, textRenderer, LanguageSystem.translate("krt.gui.depot_guide"), x + 20, y + 70, 0xA0A0A0);
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
}