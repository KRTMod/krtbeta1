package com.krt.mod.screen;

import com.krt.mod.system.LanguageSystem;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

@Environment(EnvType.CLIENT)
public class KRTConfigScreen extends Screen {
    private final Screen parent;
    private TextFieldWidget trainSpeedField;
    private ButtonWidget enableATOButton;
    private ButtonWidget showHudButton;
    private boolean atoEnabled = true;
    private boolean hudEnabled = true;
    private int scrollOffset = 0;

    public KRTConfigScreen(Screen parent) {
        super(Text.literal(LanguageSystem.translate("krt.config.title")));
        this.parent = parent;
    }

    @Override
    protected void init() {
        // 确保在添加新组件前清除所有现有组件
        clearChildren();
        
        int centerX = this.width / 2;
        int y = 40;
        int buttonWidth = 200;
        int buttonHeight = 20;
        int spacing = 25;

        // 添加标题
        addDrawableChild(new ButtonWidget(centerX - 100, y, 200, 20, 
                Text.literal(LanguageSystem.translate("krt.config.title")), button -> {}));
        y += spacing;

        // 添加分隔线
        addDrawableChild(new ButtonWidget(centerX - 150, y, 300, 2, 
                Text.empty(), button -> {}));
        y += spacing;

        // 最大列车速度设置
        addDrawableChild(new ButtonWidget(centerX - 150, y, 120, 20, 
                Text.literal(LanguageSystem.translate("krt.config.max_speed")), button -> {}));
        
        trainSpeedField = new TextFieldWidget(this.textRenderer, centerX - 20, y, 70, 20, 
                Text.literal("120"));
        trainSpeedField.setText("120"); // 默认值
        trainSpeedField.setMaxLength(3);
        addDrawableChild(trainSpeedField);
        
        addDrawableChild(new ButtonWidget(centerX + 55, y, 50, 20, 
                Text.literal("km/h"), button -> {}));
        y += spacing;

        // 启用ATO功能
        // 使用ButtonWidget替代CheckboxWidget以避免版本不兼容问题
        updateATOButtonText(centerX - 150, y);
        y += spacing;

        // 显示HUD信息
        // 使用ButtonWidget替代CheckboxWidget以避免版本不兼容问题
        updateHUDButtonText(centerX - 150, y);
        y += spacing;

        // 添加另一个分隔线
        addDrawableChild(new ButtonWidget(centerX - 150, y, 300, 2, 
                Text.empty(), button -> {}));
        y += spacing * 2;

        // 添加保存按钮
        addDrawableChild(new ButtonWidget(centerX - 105, y, 100, 20, 
                Text.literal(LanguageSystem.translate("krt.config.save")), button -> saveSettings()));

        // 添加重置按钮
        addDrawableChild(new ButtonWidget(centerX + 5, y, 100, 20, 
                Text.literal(LanguageSystem.translate("krt.config.reset")), button -> resetSettings()));
        y += spacing * 2;

        // 添加关闭按钮
        addDrawableChild(new ButtonWidget(centerX - 100, y, 200, 20, 
                ScreenTexts.CANCEL, button -> close()));
    }

    private void saveSettings() {
        // 获取设置值
        String maxSpeed = trainSpeedField.getText();

        // 这里应该保存设置到配置文件
        // 为了演示，我们只是打印到控制台
        System.out.println(LanguageSystem.translate("krt.config.saving"));
        System.out.println(LanguageSystem.translate("krt.config.max_speed") + " " + maxSpeed + " km/h");
        System.out.println(LanguageSystem.translate("krt.config.enable_ato") + ": " + atoEnabled);
        System.out.println(LanguageSystem.translate("krt.config.show_hud") + ": " + hudEnabled);

        // 显示保存成功消息
        if (this.client != null && this.client.player != null) {
            this.client.player.sendMessage(Text.literal(LanguageSystem.translate("krt.config.saved")).formatted(Formatting.GREEN), false);
        }
    }

    private void resetSettings() {
        // 重置为默认值
        trainSpeedField.setText("120");
        atoEnabled = true;
        hudEnabled = true;
        
        // 重新创建按钮而不是更新现有按钮的文本
        // 这样可以避免使用可能不存在的getY()方法
        if (enableATOButton != null && showHudButton != null) {
            // 获取按钮的y坐标（使用之前设置的间距）
            int atoButtonY = 60 + 25 * 2; // 根据createWidgets方法中的位置计算
            int hudButtonY = atoButtonY + 25;
            
            updateATOButtonText(this.width / 2 - 150, atoButtonY);
            updateHUDButtonText(this.width / 2 - 150, hudButtonY);
        }
    }
    
    private void updateATOButtonText(int x, int y) {
        // 移除旧按钮
        if (enableATOButton != null) {
            remove(enableATOButton);
        }
        
        // 创建新按钮
        String text = LanguageSystem.translate("krt.config.enable_ato") + ": " + 
                     (atoEnabled ? LanguageSystem.translate("krt.config.enabled") : 
                                  LanguageSystem.translate("krt.config.disabled"));
        
        enableATOButton = new ButtonWidget(x, y, 300, 20, Text.literal(text), button -> {
            atoEnabled = !atoEnabled;
            updateATOButtonText(x, y);
        });
        addDrawableChild(enableATOButton);
    }
    
    private void updateHUDButtonText(int x, int y) {
        // 移除旧按钮
        if (showHudButton != null) {
            remove(showHudButton);
        }
        
        // 创建新按钮
        String text = LanguageSystem.translate("krt.config.show_hud") + ": " + 
                     (hudEnabled ? LanguageSystem.translate("krt.config.enabled") : 
                                  LanguageSystem.translate("krt.config.disabled"));
        
        showHudButton = new ButtonWidget(x, y, 300, 20, Text.literal(text), button -> {
            hudEnabled = !hudEnabled;
            updateHUDButtonText(x, y);
        });
        addDrawableChild(showHudButton);
    }

    @Override
    public void render(MatrixStack matrices, int mouseX, int mouseY, float delta) {
        // 渲染背景
        this.renderBackground(matrices);
        
        // 调用父类的渲染方法渲染所有小部件
        super.render(matrices, mouseX, mouseY, delta);

        // 绘制标题
        drawCenteredText(matrices, this.textRenderer, this.title, this.width / 2, 15, 0xFFFFFF);

        // 绘制提示文本
        drawTextWithShadow(matrices, this.textRenderer, 
                Text.literal(LanguageSystem.translate("krt.config.description")), 
                this.width / 2 - 70, 30, 0xAAAAAA);
    }

    @Override
    public void close() {
        // 返回到上一个屏幕
        this.client.setScreen(this.parent);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // 当按下ESC键时关闭屏幕
        if (keyCode == 256) { // ESC键的键码
            this.close();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean shouldPause() {
        // 设置为false，这样打开此屏幕时游戏不会暂停
        return false;
    }

    // 移除不兼容的旁白支持方法（Minecraft 1.19.2版本不支持）
}