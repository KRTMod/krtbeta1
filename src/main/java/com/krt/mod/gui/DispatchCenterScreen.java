package com.krt.mod.gui;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.gui.DrawableHelper;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import com.krt.mod.KRTMod;
import com.krt.mod.system.DispatchSystem;
import com.krt.mod.system.LineControlSystem;
import java.util.Map;

public class DispatchCenterScreen extends Screen {
    
    private static final Identifier BACKGROUND_TEXTURE = new Identifier(KRTMod.MOD_ID, "textures/gui/dispatch_center.png");
    private final int backgroundWidth = 854;
    private final int backgroundHeight = 480;
    private int x, y;
    
    private DispatchSystem dispatchSystem;
    private LineControlSystem lineSystem;
    
    public DispatchCenterScreen() {
        super(Text.translatable("gui.krt_mod.dispatch_center.title"));
    }
    
    @Override
    protected void init() {
        super.init();
        
        this.x = (this.width - this.backgroundWidth) / 2;
        this.y = (this.height - this.backgroundHeight) / 2;
        
        // 获取系统实例
        this.dispatchSystem = DispatchSystem.getInstance(client.world);
        this.lineSystem = LineControlSystem.getInstance(client.world);
        
        // 添加线路选择按钮
        addLineButtons();
        
        // 添加功能按钮
        addFunctionButtons();
    }
    
    private void addLineButtons() {
        // 在线路面板添加线路选择按钮
        Map<String, LineControlSystem.LineInfo> lines = lineSystem.getLines();
        int buttonX = x + 20;
        int buttonY = y + 50;
        int buttonWidth = 120;
        int buttonHeight = 25;
        
        for (Map.Entry<String, LineControlSystem.LineInfo> entry : lines.entrySet()) {
            String lineId = entry.getKey();
            LineControlSystem.LineInfo lineInfo = entry.getValue();
            
            this.addDrawableChild(ButtonWidget.builder(
                Text.literal(lineInfo.getLineName()), 
                button -> onLineSelect(lineId)
            ).position(buttonX, buttonY).size(buttonWidth, buttonHeight).build());
            
            buttonY += 30;
            // 限制按钮数量，防止超出屏幕
            if (buttonY > y + backgroundHeight - 100) {
                break;
            }
        }
    }
    
    private void addFunctionButtons() {
        // 添加功能按钮
        int buttonWidth = 100;
        int buttonHeight = 30;
        int buttonX = x + backgroundWidth - 120;
        int buttonY = y + 50;
        
        // 高峰期调度按钮
        this.addDrawableChild(ButtonWidget.builder(
            Text.translatable("gui.krt_mod.dispatch_center.peak_hour"),
            button -> togglePeakHourMode()
        ).position(buttonX, buttonY).size(buttonWidth, buttonHeight).build());
        
        buttonY += 40;
        
        // 紧急调度按钮
        this.addDrawableChild(ButtonWidget.builder(
            Text.translatable("gui.krt_mod.dispatch_center.emergency"),
            button -> activateEmergencyMode()
        ).position(buttonX, buttonY).size(buttonWidth, buttonHeight).build());
        
        buttonY += 40;
        
        // 刷新按钮
        this.addDrawableChild(ButtonWidget.builder(
            Text.translatable("gui.krt_mod.dispatch_center.refresh"),
            button -> refreshData()
        ).position(buttonX, buttonY).size(buttonWidth, buttonHeight).build());
        
        buttonY += 40;
        
        // 返回按钮
        this.addDrawableChild(ButtonWidget.builder(
            Text.translatable("gui.krt_mod.dispatch_center.back"),
            button -> close()
        ).position(buttonX, buttonY).size(buttonWidth, buttonHeight).build());
    }
    
    private void onLineSelect(String lineId) {
        // 处理线路选择
        LineControlSystem.LineInfo lineInfo = lineSystem.getLine(lineId);
        if (lineInfo != null) {
            // 这里可以显示该线路的详细信息
            KRTMod.LOGGER.info("选择线路: {}", lineInfo.getLineName());
        }
    }
    
    private void togglePeakHourMode() {
        // 切换高峰期模式
        // 这里可以实现高峰期模式的手动控制
        KRTMod.LOGGER.info("切换高峰期调度模式");
    }
    
    private void activateEmergencyMode() {
        // 激活紧急调度模式
        KRTMod.LOGGER.info("激活紧急调度模式");
    }
    
    private void refreshData() {
        // 刷新数据
        this.clearChildren();
        this.init();
        KRTMod.LOGGER.info("刷新调度中心数据");
    }
    
    @Override
    public void render(MatrixStack matrices, int mouseX, int mouseY, float delta) {
        super.render(matrices, mouseX, mouseY, delta);
        
        // 绘制背景
        client.getTextureManager().bindTexture(BACKGROUND_TEXTURE);
        DrawableHelper.drawTexture(matrices, x, y, 0, 0, 0, backgroundWidth, backgroundHeight, backgroundWidth, backgroundHeight);
        
        // 绘制标题
        DrawableHelper.drawCenteredTextWithShadow(matrices, textRenderer, title, width / 2, y + 20, 0xFFFFFF);
        
        // 绘制线路信息
        drawLineInfo(matrices);
        
        // 绘制列车状态
        drawTrainStatus(matrices);
    }
    
    private void drawLineInfo(MatrixStack matrices) {
        // 绘制线路信息
        int infoX = x + 160;
        int infoY = y + 50;
        
        textRenderer.draw(matrices, Text.translatable("gui.krt_mod.dispatch_center.line_info"), 
            infoX, infoY, 0xFFFFFF);
        
        // 这里可以添加更多线路信息的绘制
    }
    
    private void drawTrainStatus(MatrixStack matrices) {
        // 绘制列车状态信息
        int statusX = x + 400;
        int statusY = y + 50;
        
        textRenderer.draw(matrices, Text.translatable("gui.krt_mod.dispatch_center.train_status"), 
            statusX, statusY, 0xFFFFFF);
        
        // 这里可以添加更多列车状态的绘制
    }
    
    @Override
    public boolean shouldCloseOnEsc() {
        return true;
    }
    
    @Override
    public void close() {
        super.close();
    }
}