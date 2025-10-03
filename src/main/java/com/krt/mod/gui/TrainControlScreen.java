package com.krt.mod.gui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import com.krt.mod.KRTMod;
import com.krt.mod.entity.TrainEntity;
import com.krt.mod.system.TrainControlSystem;

public class TrainControlScreen extends HandledScreen<TrainControlScreenHandler> {
    private static final Identifier TEXTURE = new Identifier(KRTMod.MOD_ID, "textures/gui/train_control_panel.png");
    private final TrainEntity train;
    private int gear = 0; // 0: 空挡, 1: 一档, 2: 二档, 3: 三档
    private int direction = 1; // 1: 前进, -1: 后退
    private boolean showControls = true; // 显示控制键位提示

    public TrainControlScreen(TrainControlScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
        this.train = handler.getTrain();
        this.backgroundHeight = 200;
        this.backgroundWidth = 300;
    }

    @Override
    protected void drawBackground(MatrixStack matrices, float delta, int mouseX, int mouseY) {
        // 绑定纹理
        client.getTextureManager().bindTexture(TEXTURE);
        // 绘制背景
        drawTexture(matrices, this.x, this.y, 0, 0, this.backgroundWidth, this.backgroundHeight);
    }

    @Override
    protected void drawForeground(MatrixStack matrices, int mouseX, int mouseY) {
        super.drawForeground(matrices, mouseX, mouseY);
        
        TextRenderer textRenderer = client.textRenderer;
        int centerX = this.backgroundWidth / 2;
        
        // 绘制标题 - 使用多语言支持
        Text titleText = Text.translatable("screen.krt_mod.train_control_panel.title");
        textRenderer.draw(matrices, titleText, centerX - textRenderer.getWidth(titleText) / 2, 10, 0x404040);
        
        // 绘制列车信息 - 使用多语言支持
        textRenderer.draw(matrices, Text.translatable("screen.krt_mod.train_control_panel.train_id", train.getTrainId()), 20, 30, 0x404040);
        textRenderer.draw(matrices, Text.translatable("screen.krt_mod.train_control_panel.destination", train.getDestination()), 20, 45, 0x404040);
        textRenderer.draw(matrices, Text.translatable("screen.krt_mod.train_control_panel.next_station", train.getNextStation()), 20, 60, 0x404040);
        
        // 绘制速度信息 - 使用多语言支持
        double currentSpeed = train.getCurrentSpeed();
        textRenderer.draw(matrices, Text.translatable("screen.krt_mod.train_control_panel.speed", String.format("%.1f", currentSpeed * 3.6)), 20, 80, 0x404040);
        
        // 绘制ATO状态 - 使用多语言支持
        Text atoStatus = Text.translatable(train.isATOEnabled() ? "screen.krt_mod.train_control_panel.ato_mode" : "screen.krt_mod.train_control_panel.manual_mode");
        textRenderer.draw(matrices, Text.translatable("screen.krt_mod.train_control_panel.control_mode", atoStatus), 20, 95, train.isATOEnabled() ? 0x00AA00 : 0xAA0000);
        
        // 绘制健康值 - 使用多语言支持
        int health = train.getHealth();
        textRenderer.draw(matrices, Text.translatable("screen.krt_mod.train_control_panel.health", health), 20, 110, getHealthColor(health));
        
        // 绘制档位和方向 - 使用多语言支持
        Text gearText = Text.translatable("screen.krt_mod.train_control_panel.gear." + gear);
        Text directionText = Text.translatable(direction > 0 ? "screen.krt_mod.train_control_panel.forward" : "screen.krt_mod.train_control_panel.backward");
        textRenderer.draw(matrices, Text.translatable("screen.krt_mod.train_control_panel.gear_label", gearText), 20, 125, 0x404040);
        textRenderer.draw(matrices, Text.translatable("screen.krt_mod.train_control_panel.direction_label", directionText), 20, 140, 0x404040);
        
        // 绘制紧急制动状态 - 使用多语言支持
        if (train.isEmergencyBraking()) {
            textRenderer.draw(matrices, Text.translatable("screen.krt_mod.train_control_panel.emergency_brake"), 20, 160, 0xFF0000);
        }
        
        // 绘制控制键位提示
        if (showControls) {
            drawControlTips(matrices, textRenderer);
        }
        
        // 绘制速度表
        drawSpeedometer(matrices, centerX + 50, 70, currentSpeed);
    }

    private int getHealthColor(int health) {
        if (health >= 70) return 0x00AA00; // 绿色
        if (health >= 30) return 0xAAAA00; // 黄色
        return 0xFF0000; // 红色
    }

    private void drawControlTips(MatrixStack matrices, TextRenderer textRenderer) {
        textRenderer.draw(matrices, Text.translatable("screen.krt_mod.train_control_panel.controls.w"), 220, 30, 0x404040);
        textRenderer.draw(matrices, Text.translatable("screen.krt_mod.train_control_panel.controls.s"), 220, 45, 0x404040);
        textRenderer.draw(matrices, Text.translatable("screen.krt_mod.train_control_panel.controls.a"), 220, 60, 0x404040);
        textRenderer.draw(matrices, Text.translatable("screen.krt_mod.train_control_panel.controls.e"), 220, 75, 0x404040);
        textRenderer.draw(matrices, Text.translatable("screen.krt_mod.train_control_panel.controls.esc"), 220, 90, 0x404040);
    }

    private void drawSpeedometer(MatrixStack matrices, int centerX, int centerY, double currentSpeed) {
        // 绘制速度表外圈
        drawCenteredCircle(matrices, centerX, centerY, 40, 0x404040);
        
        // 绘制刻度
        TextRenderer textRenderer = client.textRenderer;
        for (int i = 0; i <= 10; i++) {
            double angle = Math.toRadians(135 - i * 27); // 从135度到-135度
            int x1 = (int)(centerX + Math.cos(angle) * 35);
            int y1 = (int)(centerY - Math.sin(angle) * 35);
            int x2 = (int)(centerX + Math.cos(angle) * 40);
            int y2 = (int)(centerY - Math.sin(angle) * 40);
            drawLine(matrices, x1, y1, x2, y2, 0x404040);
            
            // 绘制数字
            if (i % 2 == 0) {
                int speedValue = i * 20;
                String speedText = String.valueOf(speedValue);
                int textX = (int)(centerX + Math.cos(angle) * 50) - textRenderer.getWidth(speedText) / 2;
                int textY = (int)(centerY - Math.sin(angle) * 50) - 4;
                textRenderer.draw(matrices, speedText, textX, textY, 0x404040);
            }
        }
        
        // 绘制当前速度指针
        double speedRatio = Math.min(currentSpeed * 3.6 / 200, 1.0); // 最大速度200km/h
        double pointerAngle = Math.toRadians(135 - speedRatio * 270);
        int pointerX = (int)(centerX + Math.cos(pointerAngle) * 30);
        int pointerY = (int)(centerY - Math.sin(pointerAngle) * 30);
        drawLine(matrices, centerX, centerY, pointerX, pointerY, 0xFF0000);
        
        // 绘制当前速度白色三角形
        drawTriangle(matrices, centerX, centerY, pointerAngle, 0xFFFFFF);
        
        // 绘制最大允许速度黄色三角形（假设当前最大允许速度为120km/h）
        double maxAllowedSpeed = 120;
        double maxSpeedRatio = Math.min(maxAllowedSpeed / 200, 1.0);
        double maxSpeedAngle = Math.toRadians(135 - maxSpeedRatio * 270);
        drawTriangle(matrices, centerX, centerY, maxSpeedAngle, 0xFFFF00);
    }

    private void drawTriangle(MatrixStack matrices, int centerX, int centerY, double angle, int color) {
        int radius = 45;
        int x1 = (int)(centerX + Math.cos(angle) * radius);
        int y1 = (int)(centerY - Math.sin(angle) * radius);
        int x2 = (int)(centerX + Math.cos(angle + Math.toRadians(10)) * (radius - 5));
        int y2 = (int)(centerY - Math.sin(angle + Math.toRadians(10)) * (radius - 5));
        int x3 = (int)(centerX + Math.cos(angle - Math.toRadians(10)) * (radius - 5));
        int y3 = (int)(centerY - Math.sin(angle - Math.toRadians(10)) * (radius - 5));
        
        // 绘制三角形
        fillTriangle(matrices, x1, y1, x2, y2, x3, y3, color);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256) { // ESC键
            this.client.player.closeHandledScreen();
            return true;
        } else if (keyCode == 87) { // W键
            // 前进
            this.direction = 1;
            applyManualControl();
            return true;
        } else if (keyCode == 83) { // S键
            // 后退
            this.direction = -1;
            applyManualControl();
            return true;
        } else if (keyCode == 65) { // A键
            // 切换档位
            this.gear = (this.gear + 1) % 4;
            applyManualControl();
            return true;
        } else if (keyCode == 69) { // E键
            // 切换ATO/手动模式
            boolean currentATO = train.isATOEnabled();
            train.setATOEnabled(!currentATO);
            return true;
        } else if (keyCode == 32) { // 空格键
            // 切换控制提示显示
            this.showControls = !this.showControls;
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void applyManualControl() {
        if (!train.isATOEnabled() && !train.isEmergencyBraking()) {
            // 根据档位计算加速度
            double acceleration = 0;
            switch (this.gear) {
                case 1: acceleration = 0.1;
                    break;
                case 2: acceleration = 0.2;
                    break;
                case 3: acceleration = 0.3;
                    break;
                default: acceleration = 0;
            }
            
            train.manualControl(acceleration, this.direction);
        }
    }

    @Override
    public void render(MatrixStack matrices, int mouseX, int mouseY, float delta) {
        renderBackground(matrices);
        super.render(matrices, mouseX, mouseY, delta);
        drawMouseoverTooltip(matrices, mouseX, mouseY);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
    
    // 绘制线条
    private void drawLine(MatrixStack matrices, int x1, int y1, int x2, int y2, int color) {
        // 简化版线条绘制，使用填充像素的方式
        // 确保起点坐标小于终点坐标
        if (x1 > x2) {
            int temp = x1;
            x1 = x2;
            x2 = temp;
            temp = y1;
            y1 = y2;
            y2 = temp;
        }
        
        int dx = x2 - x1;
        int dy = y2 - y1;
        
        // 处理水平线
        if (dy == 0) {
            fill(matrices, x1, y1, x2 + 1, y1 + 1, color);
            return;
        }
        
        // 处理垂直线
        if (dx == 0) {
            if (y1 > y2) {
                int temp = y1;
                y1 = y2;
                y2 = temp;
            }
            fill(matrices, x1, y1, x1 + 1, y2 + 1, color);
            return;
        }
        
        // 处理斜线（简化版，只绘制起点到终点的直线）
        fill(matrices, x1, y1, x1 + 1, y1 + 1, color);
        fill(matrices, x2, y2, x2 + 1, y2 + 1, color);
    }
    
    // 绘制填充三角形
    private void fillTriangle(MatrixStack matrices, int x1, int y1, int x2, int y2, int x3, int y3, int color) {
        // 简化版三角形绘制，使用线条连接三个顶点
        drawLine(matrices, x1, y1, x2, y2, color);
        drawLine(matrices, x2, y2, x3, y3, color);
        drawLine(matrices, x3, y3, x1, y1, color);
    }
    
    // 绘制中心圆
    private void drawCenteredCircle(MatrixStack matrices, int centerX, int centerY, int radius, int color) {
        // 使用20个点来近似圆形
        int points = 20;
        for (int i = 0; i < points; i++) {
            double angle1 = Math.toRadians((i * 360) / points);
            double angle2 = Math.toRadians(((i + 1) * 360) / points);
            int x1 = (int)(centerX + Math.cos(angle1) * radius);
            int y1 = (int)(centerY - Math.sin(angle1) * radius);
            int x2 = (int)(centerX + Math.cos(angle2) * radius);
            int y2 = (int)(centerY - Math.sin(angle2) * radius);
            drawLine(matrices, x1, y1, x2, y2, color);
        }
    }
}