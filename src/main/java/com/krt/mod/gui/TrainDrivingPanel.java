package com.krt.mod.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import com.krt.mod.KRTMod;
import com.krt.mod.entity.TrainEntity;
import com.krt.mod.system.TrainControlSystem;
import com.krt.mod.system.LanguageSystem;
import com.krt.mod.system.LogSystem;

public class TrainDrivingPanel extends Screen {
    private static final int WIDTH = 400;
    private static final int HEIGHT = 300;
    private final TrainEntity train;
    private ButtonWidget forwardButton;
    private ButtonWidget backwardButton;
    private ButtonWidget shiftGearButton;
    private ButtonWidget controlModeButton;
    private ButtonWidget emergencyBrakeButton;
    private ButtonWidget closeButton;
    private int gear = 0; // 0: 空挡, 1: 前进1档, 2: 前进2档, -1: 后退1档

    public TrainDrivingPanel(TrainEntity train) {
        super(Text.literal(LanguageSystem.translate("krt.train.driving_panel")));
        this.train = train;
    }

    @Override
    protected void init() {
        int x = (width - WIDTH) / 2;
        int y = (height - HEIGHT) / 2;

        // 前进按钮
        forwardButton = ButtonWidget.builder(Text.literal(LanguageSystem.translate("krt.control.forward")), button -> {
            if (gear >= 0) {
                gear = Math.min(gear + 1, 2); // 最多2档
            } else {
                gear = 1; // 从后退切换到前进1档
            }
            updateTrainControl();
        }).dimensions(x + 150, y + 100, 100, 30).build();
        addDrawableChild(forwardButton);

        // 后退按钮
        backwardButton = ButtonWidget.builder(Text.literal(LanguageSystem.translate("krt.control.backward")), button -> {
            if (gear <= 0) {
                gear = Math.max(gear - 1, -1); // 最多后退1档
            } else {
                gear = -1; // 从前进切换到后退1档
            }
            updateTrainControl();
        }).dimensions(x + 50, y + 160, 100, 30).build();
        addDrawableChild(backwardButton);

        // 切换档位按钮
        shiftGearButton = ButtonWidget.builder(Text.literal(LanguageSystem.translate("krt.control.shift_gear")), button -> {
            cycleGear();
            updateTrainControl();
        }).dimensions(x + 150, y + 160, 100, 30).build();
        addDrawableChild(shiftGearButton);

        // 控制模式切换按钮
        String currentMode = train.getControlSystem().getControlMode() == TrainControlSystem.TrainControlMode.ATO 
                ? LanguageSystem.translate("krt.train.ato") 
                : LanguageSystem.translate("krt.train.manual");
        controlModeButton = ButtonWidget.builder(Text.literal("模式: " + currentMode), button -> {
            if (train.getControlSystem().getControlMode() == TrainControlSystem.TrainControlMode.ATO) {
                train.getControlSystem().setControlMode(TrainControlSystem.TrainControlMode.MANUAL);
            } else {
                train.getControlSystem().setControlMode(TrainControlSystem.TrainControlMode.ATO);
            }
            // 更新按钮文本
            String newMode = train.getControlSystem().getControlMode() == TrainControlSystem.TrainControlMode.ATO 
                    ? LanguageSystem.translate("krt.train.ato") 
                    : LanguageSystem.translate("krt.train.manual");
            controlModeButton.setMessage(Text.literal("模式: " + newMode));
        }).dimensions(x + 270, y + 100, 100, 30).build();
        addDrawableChild(controlModeButton);

        // 紧急制动按钮
        emergencyBrakeButton = ButtonWidget.builder(Text.literal(LanguageSystem.translate("krt.train.emergency_brake")), button -> {
            train.applyEmergencyBrake();
            LogSystem.trainLog(train.getTrainId(), "紧急制动已触发");
        }).dimensions(x + 270, y + 160, 100, 30).build();
        addDrawableChild(emergencyBrakeButton);
        emergencyBrakeButton.active = !train.isEmergencyBraking();

        // 关闭按钮
        closeButton = ButtonWidget.builder(Text.literal(LanguageSystem.translate("krt.gui.close")), button -> {
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

        // 绘制列车信息
        drawTextWithShadow(matrices, textRenderer, "列车ID: " + train.getTrainId(), x + 20, y + 50, 0xFFFFFF);
        drawTextWithShadow(matrices, textRenderer, "当前速度: " + String.format("%.1f", train.getCurrentSpeed()) + " km/h", x + 20, y + 70, 0xFFFFFF);
        drawTextWithShadow(matrices, textRenderer, "目标速度: " + String.format("%.1f", train.getControlSystem().getMaxSpeed()) + " km/h", x + 20, y + 90, 0xFFFFFF);
        drawTextWithShadow(matrices, textRenderer, "当前档位: " + getGearDisplayName(), x + 20, y + 110, 0xFFFFFF);
        drawTextWithShadow(matrices, textRenderer, "健康值: " + train.getHealth() + "%", x + 20, y + 130, 0xFFFFFF);

        // 绘制速度表
        drawSpeedometer(matrices, x + 150, y + 50);

        // 绘制下一站信息
        if (train.getNextStation() != null) {
            drawTextWithShadow(matrices, textRenderer, "下一站: " + train.getNextStation(), x + 20, y + 150, 0xFFFFFF);
        }

        // 绘制终点站信息
        if (train.getDestination() != null) {
            drawTextWithShadow(matrices, textRenderer, "终点站: " + train.getDestination(), x + 20, y + 170, 0xFFFFFF);
        }

        // 绘制紧急制动状态
        if (train.isEmergencyBraking()) {
            drawTextWithShadow(matrices, textRenderer, "紧急制动已触发！", x + 20, y + 190, 0xFF0000);
        }

        // 绘制操作提示
        drawTextWithShadow(matrices, textRenderer, "操作提示: W-前进, S-后退, A-切换档位", x + 20, y + HEIGHT - 30, 0xA0A0A0);
    }

    // 绘制速度表
    private void drawSpeedometer(MatrixStack matrices, int x, int y) {
        // 绘制速度表外圆
        fill(matrices, x, y, x + 80, y + 80, 0x404040);
        fill(matrices, x + 5, y + 5, x + 75, y + 75, 0x202020);

        // 绘制速度刻度
        for (int i = 0; i <= 100; i += 10) {
            double angle = Math.toRadians(-135 + (i / 100.0) * 270);
            int x1 = x + 40 + (int)(Math.cos(angle) * 30);
            int y1 = y + 40 - (int)(Math.sin(angle) * 30);
            int x2 = x + 40 + (int)(Math.cos(angle) * 35);
            int y2 = y + 40 - (int)(Math.sin(angle) * 35);
            drawLine(matrices, x1, y1, x2, y2, 0xFFFFFF);
        }

        // 绘制当前速度指针
        double speedRatio = Math.min(train.getCurrentSpeed() / 100.0, 1.0);
        double currentAngle = Math.toRadians(-135 + speedRatio * 270);
        int cx = x + 40;
        int cy = y + 40;
        int px = cx + (int)(Math.cos(currentAngle) * 30);
        int py = cy - (int)(Math.sin(currentAngle) * 30);
        drawLine(matrices, cx, cy, px, py, 0xFF0000);

        // 绘制最大速度指示器（黄色三角形）
        double maxSpeedRatio = Math.min(train.getControlSystem().getMaxSpeed() / 100.0, 1.0);
        double maxAngle = Math.toRadians(-135 + maxSpeedRatio * 270);
        int mx = cx + (int)(Math.cos(maxAngle) * 38);
        int my = cy - (int)(Math.sin(maxAngle) * 38);
        // 绘制黄色三角形
        fillTriangle(matrices, mx, my, 
                mx - 3 + (int)(Math.cos(maxAngle + Math.toRadians(90)) * 5), 
                my + 3 - (int)(Math.sin(maxAngle + Math.toRadians(90)) * 5), 
                mx - 3 - (int)(Math.cos(maxAngle + Math.toRadians(90)) * 5), 
                my + 3 + (int)(Math.sin(maxAngle + Math.toRadians(90)) * 5), 
                0xFFFF00);
    }

    // 绘制三角形
    private void fillTriangle(MatrixStack matrices, int x1, int y1, int x2, int y2, int x3, int y3, int color) {
        // 简化版：使用线条连接三个点形成三角形
        drawLine(matrices, x1, y1, x2, y2, color);
        drawLine(matrices, x2, y2, x3, y3, color);
        drawLine(matrices, x3, y3, x1, y1, color);
    }

    // 获取档位显示名称
    private String getGearDisplayName() {
        switch (gear) {
            case 0:
                return "空挡";
            case 1:
                return "前进1档";
            case 2:
                return "前进2档";
            case -1:
                return "后退1档";
            default:
                return "未知";
        }
    }

    // 循环切换档位
    private void cycleGear() {
        if (gear == 2) {
            gear = -1; // 从前进2档切换到后退1档
        } else if (gear == -1) {
            gear = 0; // 从后退1档切换到空挡
        } else {
            gear++; // 其他情况加1
        }
    }

    // 更新列车控制
    private void updateTrainControl() {
        // 只有在手动控制模式下才应用档位
        if (train.getControlSystem().getControlMode() == TrainControlSystem.TrainControlMode.MANUAL) {
            switch (gear) {
                case 1:
                    train.getControlSystem().setTargetSpeed(30.0); // 前进1档：30km/h
                    break;
                case 2:
                    train.getControlSystem().setTargetSpeed(60.0); // 前进2档：60km/h
                    break;
                case -1:
                    train.getControlSystem().setTargetSpeed(-20.0); // 后退1档：20km/h（负数表示后退）
                    break;
                case 0:
                default:
                    train.getControlSystem().setTargetSpeed(0.0); // 空挡：停止
                    break;
            }
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256) { // ESC键
            this.client.setScreen(null);
            return true;
        } else if (keyCode == 87) { // W键：前进
            if (gear >= 0) {
                gear = Math.min(gear + 1, 2);
            } else {
                gear = 1;
            }
            updateTrainControl();
            return true;
        } else if (keyCode == 83) { // S键：后退
            if (gear <= 0) {
                gear = Math.max(gear - 1, -1);
            } else {
                gear = -1;
            }
            updateTrainControl();
            return true;
        } else if (keyCode == 65) { // A键：切换档位
            cycleGear();
            updateTrainControl();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}