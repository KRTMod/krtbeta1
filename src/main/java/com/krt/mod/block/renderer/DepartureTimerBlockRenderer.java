package com.krt.mod.block.renderer;

import com.krt.mod.KRTMod;
import com.krt.mod.block.DepartureTimerBlockEntity;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import net.minecraft.client.render.*;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.texture.SpriteAtlasTexture;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Quaternion;
import net.minecraft.util.math.Vec3f;

/**
 * 端门计时器方块渲染器
 * 负责渲染地铁端门内侧的发车时间计时器显示内容
 */
@Environment(EnvType.CLIENT)
public class DepartureTimerBlockRenderer implements BlockEntityRenderer<DepartureTimerBlockEntity> {
    private final TextRenderer textRenderer;
    private static final Identifier TIMER_TEXTURE = new Identifier(KRTMod.MOD_ID, "textures/block/departure_timer.png");
    private static final float TEXTURE_WIDTH = 16.0f;
    private static final float TEXTURE_HEIGHT = 16.0f;
    
    public DepartureTimerBlockRenderer(BlockEntityRendererFactory.Context context) {
        this.textRenderer = context.getTextRenderer();
    }
    
    @Override
    public void render(DepartureTimerBlockEntity blockEntity, float tickDelta, MatrixStack matrices, 
                     VertexConsumerProvider vertexConsumers, int light, int overlay) {
        // 检查方块实体是否有效
        if (blockEntity == null) {
            return;
        }
        
        // 获取方块朝向
        Direction facing = Direction.NORTH;
        if (blockEntity.getWorld() != null) {
            facing = blockEntity.getWorld().getBlockState(blockEntity.getPos()).get(DepartureTimerBlock.FACING);
        }
        
        // 设置渲染位置和旋转
        matrices.push();
        
        // 移动到方块中心
        matrices.translate(0.5, 0.5, 0.5);
        
        // 根据方块朝向旋转
        float rotation = getRotationDegrees(facing);
        matrices.multiply(Quaternion.fromEulerXyz(0.0f, rotation * MathHelper.RADIANS_PER_DEGREE, 0.0f));
        
        // 向方块前面移动一点，使其显示在方块表面
        matrices.translate(0.0, 0.0, -0.51);
        
        // 缩放文本
        matrices.scale(0.01f, 0.01f, 0.01f);
        
        // 获取显示文本
        Text displayText = blockEntity.getDisplayText();
        String timeText = blockEntity.getFormattedTime();
        String statusText = blockEntity.getStatusMessage();
        
        // 渲染背景（简单的矩形）
        renderBackground(matrices, vertexConsumers, blockEntity.isPowered());
        
        // 渲染时间文本（绿色或红色）
        int textColor = blockEntity.getTimeTextColor();
        
        // 如果不通电，降低文本亮度
        if (!blockEntity.isPowered()) {
            textColor = 0x777777; // 灰色文本表示未激活状态
        }
        
        // 计算文本位置，使其居中显示
        int textWidth = textRenderer.getWidth(timeText);
        int x = -textWidth / 2;
        int y = -textRenderer.fontHeight / 2;
        
        // 渲染时间文本
        textRenderer.draw(timeText, x, y, textColor, false, matrices.peek().getModel(), 
                          vertexConsumers, TextRenderer.TextLayerType.NORMAL, 0, light);
        
        // 如果显示详细信息，渲染状态消息
        if (blockEntity.isShowDetails()) {
            int statusWidth = textRenderer.getWidth(statusText);
            int statusX = -statusWidth / 2;
            int statusY = y + 15;
            
            textRenderer.draw(statusText, statusX, statusY, 0xFFFFFF, false, matrices.peek().getModel(),
                             vertexConsumers, TextRenderer.TextLayerType.NORMAL, 0, light);
        }
        
        matrices.pop();
    }
    
    /**
     * 获取指定朝向的旋转角度（度）
     */
    private float getRotationDegrees(Direction facing) {
        switch (facing) {
            case NORTH:
                return 0;
            case EAST:
                return -90;
            case SOUTH:
                return 180;
            case WEST:
                return 90;
            default:
                return 0;
        }
    }
    
    /**
     * 渲染计时器的背景
     */
    private void renderBackground(MatrixStack matrices, VertexConsumerProvider vertexConsumers, boolean powered) {
        // 获取纹理
        MinecraftClient client = MinecraftClient.getInstance();
        SpriteAtlasTexture textureAtlas = client.getSpriteAtlas(SpriteAtlasTexture.BLOCK_ATLAS_TEXTURE);
        Sprite sprite = textureAtlas.getSprite(TIMER_TEXTURE);
        
        // 渲染背景
        float scale = 1.0f / TEXTURE_WIDTH; // 缩放因子
        float width = 16.0f * scale;
        float height = 16.0f * scale;
        
        // 保存当前矩阵状态
        matrices.push();
        
        // 缩放以适应方块表面
        matrices.scale(1.0f, 1.0f, 0.01f);
        
        // 计算背景位置，使其居中
        float x = -width / 2;
        float y = -height / 2;
        
        // 创建顶点消费者
        VertexConsumer vertexConsumer = vertexConsumers.getBuffer(RenderLayer.getEntitySolid(TIMER_TEXTURE));
        
        // 渲染背景四边形
        // 左上角
        vertexConsumer.vertex(matrices.peek().getModel(), x, y + height, 0.0f)
                .color(255, 255, 255, powered ? 255 : 128)
                .texture(sprite.getMinU(), sprite.getMaxV())
                .overlay(OverlayTexture.DEFAULT_UV)
                .light(15728880) // 最大亮度
                .normal(0.0f, 0.0f, 1.0f)
                .next();
        
        // 左下角
        vertexConsumer.vertex(matrices.peek().getModel(), x, y, 0.0f)
                .color(255, 255, 255, powered ? 255 : 128)
                .texture(sprite.getMinU(), sprite.getMinV())
                .overlay(OverlayTexture.DEFAULT_UV)
                .light(15728880)
                .normal(0.0f, 0.0f, 1.0f)
                .next();
        
        // 右下角
        vertexConsumer.vertex(matrices.peek().getModel(), x + width, y, 0.0f)
                .color(255, 255, 255, powered ? 255 : 128)
                .texture(sprite.getMaxU(), sprite.getMinV())
                .overlay(OverlayTexture.DEFAULT_UV)
                .light(15728880)
                .normal(0.0f, 0.0f, 1.0f)
                .next();
        
        // 右上角
        vertexConsumer.vertex(matrices.peek().getModel(), x + width, y + height, 0.0f)
                .color(255, 255, 255, powered ? 255 : 128)
                .texture(sprite.getMaxU(), sprite.getMaxV())
                .overlay(OverlayTexture.DEFAULT_UV)
                .light(15728880)
                .normal(0.0f, 0.0f, 1.0f)
                .next();
        
        // 恢复矩阵状态
        matrices.pop();
    }
}