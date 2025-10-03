package com.krt.mod.gui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import com.krt.mod.KRTMod;

import java.util.ArrayList;
import java.util.List;

public class OperationManualScreen extends HandledScreen<OperationManualScreenHandler> {
    private static final Identifier TEXTURE = new Identifier(KRTMod.MOD_ID, "textures/gui/operation_manual.png");
    private int scrollOffset = 0;
    private static final int MAX_LINES = 20; // 一页显示的最大行数
    private List<Text> manualContent = new ArrayList<>();

    public OperationManualScreen(OperationManualScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
        this.backgroundHeight = 256;
        this.backgroundWidth = 384;
        
        // 初始化操作手册内容
        initManualContent();
    }

    private void initManualContent() {
        // 添加操作手册的完整内容
        manualContent.add(Text.literal("# KRT Beta 昆明轨道交通模组"));
        manualContent.add(Text.literal(""));
        manualContent.add(Text.literal("## 模组介绍"));
        manualContent.add(Text.literal("这是一个基于Minecraft Fabric平台开发的昆明轨道交通主题模组，包含地铁轨道、信号系统、车站建筑等元素，让玩家可以在游戏中体验和构建自己的地铁系统。"));
        manualContent.add(Text.literal(""));
        manualContent.add(Text.literal("## 物品栏说明"));
        manualContent.add(Text.literal("模组包含两个主要物品栏："));
        manualContent.add(Text.literal(""));
        manualContent.add(Text.literal("### 轨道与控制物品栏"));
        manualContent.add(Text.literal("- 地铁轨道：基础轨道方块，用于铺设地铁线路"));
        manualContent.add(Text.literal("- 道岔轨道：可切换方向的轨道方块"));
        manualContent.add(Text.literal("- 信号机：控制列车运行的信号设备"));
        manualContent.add(Text.literal("- ATP信号机：高级列车保护系统信号设备"));
        manualContent.add(Text.literal("- 报站器：播放车站广播的设备"));
        manualContent.add(Text.literal("- 线路设置面板：设置地铁线路信息的面板"));
        manualContent.add(Text.literal("- 列车操控面板：控制列车运行的面板"));
        manualContent.add(Text.literal("- 司机钥匙：开启列车操控面板的钥匙"));
        manualContent.add(Text.literal("- 屏蔽门钥匙：开启屏蔽门的钥匙"));
        manualContent.add(Text.literal(""));
        manualContent.add(Text.literal("### 车站建筑物品栏"));
        manualContent.add(Text.literal("- 屏蔽门：车站月台上的安全门"));
        manualContent.add(Text.literal("- 站台方块：车站站台的基础方块"));
        manualContent.add(Text.literal("- 车站列车到站倒计时显示屏：显示下一班列车到站时间的显示屏"));
        manualContent.add(Text.literal("- 小电视：显示各种信息的小型显示屏"));
        manualContent.add(Text.literal(""));
        manualContent.add(Text.literal("## 方块功能说明"));
        manualContent.add(Text.literal(""));
        manualContent.add(Text.literal("### 轨道系统"));
        manualContent.add(Text.literal("- **地铁轨道**：基础的轨道方块，支持矿车和列车在上面行驶"));
        manualContent.add(Text.literal("- **道岔轨道**：可以切换轨道方向，实现列车变道"));
        manualContent.add(Text.literal(""));
        manualContent.add(Text.literal("### 信号系统"));
        manualContent.add(Text.literal("- **信号机**：显示红、黄、绿三种信号状态，控制列车通行"));
        manualContent.add(Text.literal("- **ATP信号机**：更高级的信号系统，提供额外的列车保护功能"));
        manualContent.add(Text.literal(""));
        manualContent.add(Text.literal("### 车站设备"));
        manualContent.add(Text.literal("- **屏蔽门**：在车站月台上生成安全门，需要屏蔽门钥匙开启"));
        manualContent.add(Text.literal("- **报站器**：播放车站到站信息的广播设备"));
        manualContent.add(Text.literal("- **站台方块**：车站站台的基础建材"));
        manualContent.add(Text.literal(""));
        manualContent.add(Text.literal("### 控制设备"));
        manualContent.add(Text.literal("- **线路设置面板**：设置地铁线路的名称、车站列表和目的地等信息"));
        manualContent.add(Text.literal("- **列车操控面板**：控制列车的启动、停止、加速、减速等操作，需要司机钥匙开启"));
        manualContent.add(Text.literal("- **车站列车到站倒计时显示屏**：显示下一班列车的预计到站时间"));
        manualContent.add(Text.literal("- **小电视**：显示列车时刻表、公告等信息的小型显示屏"));
        manualContent.add(Text.literal(""));
        manualContent.add(Text.literal("## 使用方法"));
        manualContent.add(Text.literal(""));
        manualContent.add(Text.literal("### 搭建地铁线路"));
        manualContent.add(Text.literal("1. 使用地铁轨道和道岔轨道铺设线路"));
        manualContent.add(Text.literal("2. 在关键位置放置信号机以控制列车运行"));
        manualContent.add(Text.literal("3. 在线路两端或中间设置车站区域"));
        manualContent.add(Text.literal(""));
        manualContent.add(Text.literal("### 建造车站"));
        manualContent.add(Text.literal("1. 使用站台方块建造车站月台"));
        manualContent.add(Text.literal("2. 安装屏蔽门以提高安全性"));
        manualContent.add(Text.literal("3. 放置报站器、倒计时显示屏和小电视等设备"));
        manualContent.add(Text.literal("4. 设置线路设置面板以配置线路信息"));
        manualContent.add(Text.literal(""));
        manualContent.add(Text.literal("### 控制列车"));
        manualContent.add(Text.literal("1. 手持司机钥匙右击列车操控面板进入驾驶模式"));
        manualContent.add(Text.literal("2. 使用W键前进，S键后退"));
        manualContent.add(Text.literal("3. 使用A键切换档位"));
        manualContent.add(Text.literal("4. 使用E键切换自动驾驶(ATO)和手动控制模式"));
        manualContent.add(Text.literal("5. 使用ESC键退出驾驶模式"));
        manualContent.add(Text.literal(""));
        manualContent.add(Text.literal("## 注意事项"));
        manualContent.add(Text.literal(""));
        manualContent.add(Text.literal("## 快捷键说明"));
        manualContent.add(Text.literal(""));
        manualContent.add(Text.literal("### 列车控制相关快捷键"));
        manualContent.add(Text.literal("在列车控制面板界面（TrainControlScreen）中："));
        manualContent.add(Text.literal(""));
        manualContent.add(Text.literal("- ESC键 ：关闭界面"));
        manualContent.add(Text.literal("- W键 ：控制列车前进"));
        manualContent.add(Text.literal("- S键 ：控制列车后退"));
        manualContent.add(Text.literal("- A键 ：切换列车档位"));
        manualContent.add(Text.literal("- E键 ：切换ATO（自动运行）和手动模式"));
        manualContent.add(Text.literal("- 空格键 ：切换控制提示显示"));
        manualContent.add(Text.literal(""));
        manualContent.add(Text.literal("在列车驾驶面板界面（TrainDrivingPanel）中："));
        manualContent.add(Text.literal(""));
        manualContent.add(Text.literal("- ESC键 ：关闭界面"));
        manualContent.add(Text.literal("- W键 ：控制列车前进"));
        manualContent.add(Text.literal("- S键 ：控制列车后退"));
        manualContent.add(Text.literal("- A键 ：切换列车档位"));
        manualContent.add(Text.literal(""));
        manualContent.add(Text.literal("### 其他相关信息"));
        manualContent.add(Text.literal("模组中还包含一个\"快捷键手册\"物品，但目前这个物品只是注册了，其对应的界面实现（ShortcutManualScreen）似乎尚未完成。右键点击该物品会尝试打开快捷键手册界面，但由于没有对应的实现，可能无法正常显示。"));
        manualContent.add(Text.literal("- 列车需要在轨道上行驶，无法在普通方块上移动"));
        manualContent.add(Text.literal("- 信号机和ATP信号机需要正确连接才能发挥作用"));
        manualContent.add(Text.literal("- 屏蔽门和列车操控面板需要对应的钥匙才能开启"));
        manualContent.add(Text.literal("- 部分高级功能需要在服务器模式下才能完全实现"));
        manualContent.add(Text.literal(""));
        manualContent.add(Text.literal("## 版本信息"));
        manualContent.add(Text.literal("- Minecraft版本：1.19.2"));
        manualContent.add(Text.literal("- Fabric API版本：需要安装最新版"));
        manualContent.add(Text.literal("- Java版本：17或更高版本"));
        manualContent.add(Text.literal(""));
        manualContent.add(Text.literal("## 更新日志"));
        manualContent.add(Text.literal("### v1.0.0"));
        manualContent.add(Text.literal("- 初始版本发布"));
        manualContent.add(Text.literal("- 包含基础轨道系统、信号系统、车站建筑和控制设备"));
        manualContent.add(Text.literal(""));
        manualContent.add(Text.literal("## 常见问题"));
        manualContent.add(Text.literal("Q: 为什么我的列车无法启动？"));
        manualContent.add(Text.literal("A: 请确保您手持司机钥匙，并正确点击了列车操控面板"));
        manualContent.add(Text.literal(""));
        manualContent.add(Text.literal("Q: 屏蔽门无法打开怎么办？"));
        manualContent.add(Text.literal("A: 请使用屏蔽门钥匙右击屏蔽门"));
        manualContent.add(Text.literal(""));
        manualContent.add(Text.literal("Q: 信号机一直显示红色怎么办？"));
        manualContent.add(Text.literal("A: 检查轨道连接是否正确，确保前方轨道没有被阻挡"));
        manualContent.add(Text.literal(""));
        manualContent.add(Text.literal("## 开发团队"));
        manualContent.add(Text.literal("- 开发者：布莱克多格"));
    }

    @Override
    protected void init() {
        super.init();
        // 添加滚动按钮
        addDrawableChild(new ButtonWidget(
            this.x + this.backgroundWidth - 20, this.y + 20, 16, 16, 
            Text.literal("▲"), (button) -> scrollUp()
        ));
        addDrawableChild(new ButtonWidget(
            this.x + this.backgroundWidth - 20, this.y + this.backgroundHeight - 40, 16, 16, 
            Text.literal("▼"), (button) -> scrollDown()
        ));
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
        
        // 绘制标题
        Text titleText = Text.translatable("item.krt.operation_manual.name");
        textRenderer.draw(matrices, titleText, centerX - textRenderer.getWidth(titleText) / 2, 10, 0x404040);
        
        // 绘制内容，实现滚动功能
        int lineHeight = textRenderer.fontHeight + 2; // 行高
        int contentX = 20;
        int contentY = 30;
        
        for (int i = 0; i < MAX_LINES && i + scrollOffset < manualContent.size(); i++) {
            Text line = manualContent.get(i + scrollOffset);
            // 根据文本前缀设置不同的颜色
            int color = getTextColor(line.getString());
            textRenderer.draw(matrices, line, contentX, contentY + (i * lineHeight), color);
        }
        
        // 绘制页码信息
        int totalPages = (int) Math.ceil((float) manualContent.size() / MAX_LINES);
        int currentPage = scrollOffset / MAX_LINES + 1;
        Text pageText = Text.literal("第 " + currentPage + " 页，共 " + totalPages + " 页");
        textRenderer.draw(matrices, pageText, centerX - textRenderer.getWidth(pageText) / 2, this.backgroundHeight - 16, 0x404040);
    }

    private int getTextColor(String text) {
        if (text.startsWith("#")) {
            return 0xFF5555; // 一级标题红色
        } else if (text.startsWith("##")) {
            return 0xFF7733; // 二级标题橙色
        } else if (text.startsWith("###")) {
            return 0xFFAA00; // 三级标题黄色
        } else if (text.startsWith("- **")) {
            return 0x0055FF; // 重点项蓝色
        } else if (text.startsWith("- ")) {
            return 0x000000; // 普通列表项黑色
        } else if (text.startsWith("Q: ")) {
            return 0x5500AA; // 问题紫色
        } else if (text.startsWith("A: ")) {
            return 0x00AA55; // 回答绿色
        } else {
            return 0x333333; // 普通文本深灰色
        }
    }

    private void scrollUp() {
        if (scrollOffset > 0) {
            scrollOffset--;
        }
    }

    private void scrollDown() {
        if (scrollOffset < manualContent.size() - MAX_LINES) {
            scrollOffset++;
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        if (amount < 0) {
            scrollDown();
        } else {
            scrollUp();
        }
        return true;
    }

    @Override
    public void render(MatrixStack matrices, int mouseX, int mouseY, float delta) {
        super.renderBackground(matrices);
        super.render(matrices, mouseX, mouseY, delta);
        drawMouseoverTooltip(matrices, mouseX, mouseY);
    }
}