package com.krt.mod.screen;

import com.krt.mod.gui.ModDebuggerScreenHandler;
import com.krt.mod.network.ModDebuggerNetworking;
import com.krt.mod.system.LanguageSystem;
import com.krt.mod.system.LogSystem;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class ModDebuggerScreen extends Screen {
    private static final int WIDTH = 800;
    private static final int HEIGHT = 500;
    private TextFieldWidget searchField;
    private ButtonWidget refreshButton;
    private ButtonWidget executeButton;
    private ButtonWidget logButton;
    private TextFieldWidget commandField;
    private TextFieldWidget targetField;
    private TextFieldWidget paramField;
    private boolean showLogs = false;
    private int scrollOffset = 0;
    private List<ModDebuggerScreenHandler.DebugInfo> filteredTrains = List.of();

    public ModDebuggerScreen() {
        super(Text.literal(LanguageSystem.translate("krt.debugger.title")));
    }

    @Override
    protected void init() {
        int x = (width - WIDTH) / 2;
        int y = (height - HEIGHT) / 2;

        // 搜索框
        searchField = new TextFieldWidget(textRenderer, x + 20, y + 20, 200, 20, Text.literal("搜索列车..."));
        searchField.setChangedListener(text -> filterTrains());
        addDrawableChild(searchField);

        // 刷新按钮
        refreshButton = new ButtonWidget(x + 230, y + 20, 80, 20, Text.literal("刷新"), button -> refreshData());
        addDrawableChild(refreshButton);

        // 日志按钮
        logButton = new ButtonWidget(x + 320, y + 20, 80, 20, Text.literal("查看日志"), button -> toggleLogs());
        addDrawableChild(logButton);

        // 关闭按钮
        addDrawableChild(new ButtonWidget(x + WIDTH - 100, y + 20, 80, 20, Text.literal("关闭"), button -> client.setScreen(null)));

        // 调试命令区域
        addDrawableChild(new ButtonWidget(x + 20, y + HEIGHT - 100, 150, 20, Text.literal("调试命令区域"), button -> {}));

        // 目标列车
        addDrawableChild(new ButtonWidget(x + 20, y + HEIGHT - 75, 150, 20, Text.literal("目标列车 ID: (all表示全部)"), button -> {}));
        targetField = new TextFieldWidget(textRenderer, x + 180, y + HEIGHT - 75, 150, 20, Text.literal("all"));
        addDrawableChild(targetField);

        // 命令
        addDrawableChild(new ButtonWidget(x + 20, y + HEIGHT - 50, 60, 20, Text.literal("命令:"), button -> {}));
        commandField = new TextFieldWidget(textRenderer, x + 90, y + HEIGHT - 50, 150, 20, Text.literal("set_speed"));
        addDrawableChild(commandField);

        // 参数
        addDrawableChild(new ButtonWidget(x + 250, y + HEIGHT - 50, 60, 20, Text.literal("参数:"), button -> {}));
        paramField = new TextFieldWidget(textRenderer, x + 320, y + HEIGHT - 50, 150, 20, Text.literal("40"));
        addDrawableChild(paramField);

        // 执行按钮
        executeButton = new ButtonWidget(x + 480, y + HEIGHT - 50, 80, 20, Text.literal("执行"), button -> executeCommand());
        addDrawableChild(executeButton);

        // 命令说明
        addDrawableChild(new ButtonWidget(x + 20, y + HEIGHT - 25, 550, 20, Text.literal("可用命令: set_speed, toggle_ato, release_brake, apply_brake, set_health, clear_cache"), button -> {}));

        // 初始加载数据
        refreshData();
    }

    private void refreshData() {
        // 通过网络请求从服务器获取数据
        ModDebuggerNetworking.requestTrainsInfoFromClient();
        
        // 重置滚动位置
        scrollOffset = 0;
        
        // 延迟一小段时间后再过滤，确保数据已经收到
        client.execute(() -> {
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            filterTrains();
        });
    }

    private void filterTrains() {
        // 使用从服务器获取并存储在客户端的数据
        String searchText = searchField.getText().toLowerCase();
        filteredTrains = ModDebuggerScreenHandler.getClientTrainsInfo().stream()
                .filter(train -> searchText.isEmpty() || 
                        train.trainId.toLowerCase().contains(searchText) ||
                        train.driverName.toLowerCase().contains(searchText) ||
                        train.destination.toLowerCase().contains(searchText) ||
                        train.nextStation.toLowerCase().contains(searchText) ||
                        train.currentLine.toLowerCase().contains(searchText))
                .collect(Collectors.toList());
    }

    private void toggleLogs() {
        showLogs = !showLogs;
        logButton.setMessage(Text.literal(showLogs ? "隐藏日志" : "查看日志"));
    }

    private void executeCommand() {
        String command = commandField.getText();
        String target = targetField.getText();
        String params = paramField.getText();
        
        // 通过网络将命令发送到服务器执行
        ModDebuggerNetworking.sendDebugCommandFromClient(command, target, params);
        
        // 记录日志
        LogSystem.debug("调试命令已发送: " + command + " 目标: " + target + " 参数: " + params);
        
        // 刷新数据以显示最新状态
        refreshData();
    }

    @Override
    public void render(MatrixStack matrices, int mouseX, int mouseY, float delta) {
        renderBackground(matrices);
        super.render(matrices, mouseX, mouseY, delta);

        int x = (width - WIDTH) / 2;
        int y = (height - HEIGHT) / 2;

        // 绘制标题
        drawCenteredText(matrices, textRenderer, title, width / 2, y + 10, 0xFFFFFF);

        // 绘制列车信息表格标题
        drawTextWithShadow(matrices, textRenderer, Text.literal("ID"), x + 30, y + 50, 0xFFFF00);
        drawTextWithShadow(matrices, textRenderer, Text.literal("速度"), x + 150, y + 50, 0xFFFF00);
        drawTextWithShadow(matrices, textRenderer, Text.literal("健康值"), x + 220, y + 50, 0xFFFF00);
        drawTextWithShadow(matrices, textRenderer, Text.literal("ATO"), x + 280, y + 50, 0xFFFF00);
        drawTextWithShadow(matrices, textRenderer, Text.literal("紧急制动"), x + 320, y + 50, 0xFFFF00);
        drawTextWithShadow(matrices, textRenderer, Text.literal("司机"), x + 400, y + 50, 0xFFFF00);
        drawTextWithShadow(matrices, textRenderer, Text.literal("线路"), x + 480, y + 50, 0xFFFF00);
        drawTextWithShadow(matrices, textRenderer, Text.literal("目的地"), x + 550, y + 50, 0xFFFF00);

        // 绘制分隔线
        drawHorizontalLine(matrices, x + 20, x + WIDTH - 20, y + 60, 0xFFFFFF);

        // 绘制列车信息
        int rowHeight = 25;
        int rowsPerPage = (HEIGHT - 170) / rowHeight;
        int displayCount = Math.min(rowsPerPage, filteredTrains.size() - scrollOffset);

        for (int i = 0; i < displayCount; i++) {
            int index = i + scrollOffset;
            if (index >= filteredTrains.size()) break;

            ModDebuggerScreenHandler.DebugInfo train = filteredTrains.get(index);
            int rowY = y + 70 + i * rowHeight;

            // 绘制行背景（交替颜色）
            if (i % 2 == 0) {
                fill(matrices, x + 20, rowY, x + WIDTH - 20, rowY + rowHeight - 2, 0x40404040);
            }

            // 绘制列车信息
            drawTextWithShadow(matrices, textRenderer, Text.literal(train.trainId), x + 30, rowY + 5, 0xFFFFFF);
            drawTextWithShadow(matrices, textRenderer, Text.literal(String.format("%.1f", train.speed) + " km/h"), x + 150, rowY + 5, 0xFFFFFF);
            drawTextWithShadow(matrices, textRenderer, Text.literal(train.health + "%"), x + 220, rowY + 5, getHealthColor(train.health));
            drawTextWithShadow(matrices, textRenderer, Text.literal(train.atoEnabled ? "✓" : "✗"), x + 290, rowY + 5, train.atoEnabled ? 0x00FF00 : 0xFF0000);
            drawTextWithShadow(matrices, textRenderer, Text.literal(train.emergencyBrake ? "✓" : "✗"), x + 340, rowY + 5, train.emergencyBrake ? 0xFF0000 : 0x00FF00);
            drawTextWithShadow(matrices, textRenderer, Text.literal(train.driverName), x + 400, rowY + 5, 0xFFFFFF);
            drawTextWithShadow(matrices, textRenderer, Text.literal(train.currentLine), x + 480, rowY + 5, 0xFFFFFF);
            drawTextWithShadow(matrices, textRenderer, Text.literal(train.destination), x + 550, rowY + 5, 0xFFFFFF);
        }

        // 绘制滚动条
        if (filteredTrains.size() > rowsPerPage) {
            int scrollBarHeight = Math.max(20, (HEIGHT - 170) * rowsPerPage / filteredTrains.size());
            int scrollBarY = y + 70 + scrollOffset * (HEIGHT - 170 - scrollBarHeight) / (filteredTrains.size() - rowsPerPage);
            fill(matrices, x + WIDTH - 15, scrollBarY, x + WIDTH - 10, scrollBarY + scrollBarHeight, 0x80FFFFFF);
        }

        // 绘制日志区域
        if (showLogs) {
            int logHeight = 200;
            fill(matrices, x + 20, y + HEIGHT - logHeight - 130, x + WIDTH - 20, y + HEIGHT - 130, 0x80000000);
            drawTextWithShadow(matrices, textRenderer, Text.literal("调试日志"), x + 30, y + HEIGHT - logHeight - 120, 0xFFFF00);
            
            // 在实际应用中，这里应该显示真实的日志
            // 使用临时演示日志
            List<String> logEntries = new ArrayList<>();
            logEntries.add("调试器已初始化");
            logEntries.add("等待服务器数据...");
            logEntries.add("提示：点击刷新按钮获取最新数据");
            
            for (int i = 0; i < logEntries.size(); i++) {
                drawTextWithShadow(matrices, textRenderer, Text.literal(logEntries.get(i)), 
                        x + 30, y + HEIGHT - logHeight - 100 + i * 15, 0xFFFFFF);
            }
        }
    }

    private int getHealthColor(int health) {
        if (health > 70) return 0x00FF00;  // 绿色
        if (health > 30) return 0xFFFF00;  // 黄色
        return 0xFF0000;  // 红色
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // 处理滚动条点击
        int x = (width - WIDTH) / 2;
        int y = (height - HEIGHT) / 2;
        int rowsPerPage = (HEIGHT - 170) / 25;

        if (filteredTrains.size() > rowsPerPage && 
                mouseX >= x + WIDTH - 15 && mouseX <= x + WIDTH - 10 && 
                mouseY >= y + 70 && mouseY <= y + HEIGHT - 100) {
            int scrollBarHeight = Math.max(20, (HEIGHT - 170) * rowsPerPage / filteredTrains.size());
            int scrollableArea = HEIGHT - 170 - scrollBarHeight;
            float scrollPercent = (float)(mouseY - y - 70) / scrollableArea;
            scrollOffset = Math.max(0, Math.min(filteredTrains.size() - rowsPerPage, 
                    (int)(scrollPercent * (filteredTrains.size() - rowsPerPage))));
            return true;
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        int rowsPerPage = (HEIGHT - 170) / 25;
        if (filteredTrains.size() > rowsPerPage) {
            scrollOffset = Math.max(0, Math.min(filteredTrains.size() - rowsPerPage, 
                    scrollOffset - (int)amount));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, amount);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}