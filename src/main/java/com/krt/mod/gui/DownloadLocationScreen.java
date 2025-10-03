package com.krt.mod.gui;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import com.krt.mod.system.LanguageSystem;
import com.krt.mod.map.MapExporter;

public class DownloadLocationScreen extends Screen {
    private final Screen parent;
    private final String defaultPath;
    private final DownloadCallback callback;
    private TextFieldWidget pathTextField;
    
    public interface DownloadCallback {
        void onDownloadPathSelected(String path);
    }

    public DownloadLocationScreen(Screen parent, DownloadCallback callback) {
        super(Text.literal(LanguageSystem.translate("krt.map.select_download_location")));
        this.parent = parent;
        this.callback = callback;
        this.defaultPath = MapExporter.getDefaultExportDirectory();
    }

    @Override
    protected void init() {
        int x = (width - 300) / 2;
        int y = (height - 150) / 2;
        
        // 路径输入框
        pathTextField = new TextFieldWidget(textRenderer, x, y + 40, 300, 20, Text.literal("Download Path"));
        pathTextField.setText(defaultPath);
        addDrawableChild(pathTextField);
        
        // 使用默认路径按钮
        ButtonWidget defaultPathButton = new ButtonWidget(x, y + 70, 145, 20, 
                Text.literal(LanguageSystem.translate("krt.map.download_default_location")), 
                button -> {
                    pathTextField.setText(defaultPath);
                });
        addDrawableChild(defaultPathButton);
        
        // 确认按钮
        ButtonWidget confirmButton = new ButtonWidget(x + 155, y + 70, 145, 20, 
                Text.literal(LanguageSystem.translate("krt.gui.confirm")), 
                button -> {
                    String path = pathTextField.getText();
                    callback.onDownloadPathSelected(path);
                    client.setScreen(parent);
                });
        addDrawableChild(confirmButton);
        
        // 取消按钮
        ButtonWidget cancelButton = new ButtonWidget(x + 100, y + 100, 100, 20, 
                Text.literal(LanguageSystem.translate("krt.gui.cancel")), 
                button -> {
                    client.setScreen(parent);
                });
        addDrawableChild(cancelButton);
    }

    @Override
    public void render(MatrixStack matrices, int mouseX, int mouseY, float delta) {
        renderBackground(matrices);
        super.render(matrices, mouseX, mouseY, delta);
        
        int x = (width - 300) / 2;
        int y = (height - 150) / 2;
        
        // 绘制标题
        drawCenteredText(matrices, textRenderer, title, width / 2, y + 10, 0xFFFFFF);
        
        // 绘制提示文本
        textRenderer.draw("Download Path:", x, y + 30, 0xA0A0A0, false, matrices.peek().getPositionMatrix(), null, false, 0, 15728880);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256) { // ESC键
            client.setScreen(parent);
            return true;
        } else if (keyCode == 257 || keyCode == 335) { // Enter键或小键盘Enter键
            String path = pathTextField.getText();
            callback.onDownloadPathSelected(path);
            client.setScreen(parent);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}