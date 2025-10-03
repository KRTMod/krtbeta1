package com.krt.mod.entity.renderer;

import com.krt.mod.entity.TrainEntity;
import com.krt.mod.entity.TrainCar;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import com.krt.mod.KRTMod;

/**
 * 列车实体渲染器，负责渲染列车实体并应用摇摆效果
 */
@Environment(EnvType.CLIENT)
public class TrainEntityRenderer extends EntityRenderer<TrainEntity> {
    private static final float SCALE = 1.0F;
    private static final float WIDTH = 3.0F;
    private static final float HEIGHT = 3.0F;
    private static final float LENGTH = 7.0F;
    private static final int TRAIN_COLOR = 0xFF0000; // 红色列车

    public TrainEntityRenderer(EntityRendererFactory.Context context) {
        super(context);
        this.shadowRadius = 1.5F;
    }

    @Override
    public Identifier getTexture(TrainEntity entity) {
        // 返回一个默认的纹理标识符，实际渲染使用纯色
        return new Identifier(KRTMod.MOD_ID, "textures/entity/train.png");
    }

    @Override
    public void render(TrainEntity entity, float yaw, float tickDelta, MatrixStack matrices, 
                      VertexConsumerProvider vertexConsumers, int light) {
        super.render(entity, yaw, tickDelta, matrices, vertexConsumers, light);
        
        // 保存当前矩阵状态
        matrices.push();
        
        // 应用旋转（使列车朝向正确的方向）
        matrices.multiply(entity.getRotationQuaternion(tickDelta));
        
        // 应用缩放
        matrices.scale(SCALE, SCALE, SCALE);
        
        // 如果有列车编组，对每个车辆应用摇摆效果
        if (entity.getConsist() != null) {
            for (TrainCar car : entity.getConsist().getCars()) {
                // 为每个车厢创建一个新的矩阵堆栈，以应用独立的摇摆效果
                MatrixStack carMatrices = new MatrixStack();
                carMatrices.copyFrom(matrices);
                
                // 计算车厢在列车中的位置偏移
                int carIndex = entity.getConsist().getCars().indexOf(car);
                carMatrices.translate(0.0, 0.0, carIndex * LENGTH);
                
                // 应用摇摆效果
                applySwayEffect(carMatrices, car);
                
                // 渲染车厢
                renderCar(carMatrices, vertexConsumers, light);
            }
        } else {
            // 如果没有编组，渲染基本的列车模型
            renderCar(matrices, vertexConsumers, light);
        }
        
        // 恢复矩阵状态
        matrices.pop();
    }
    
    /**
     * 应用摇摆效果到矩阵堆栈
     */
    private void applySwayEffect(MatrixStack matrices, TrainCar car) {
        // 获取摇摆偏移量和倾斜角度
        Vec3d swayOffset = car.getSwayOffset();
        float tiltAngle = car.getTiltAngle();
        
        // 应用摇摆偏移
        matrices.translate(swayOffset.x, swayOffset.y, swayOffset.z);
        
        // 应用倾斜角度（绕X轴旋转）
        matrices.multiply(Vec3d.POSITIVE_X.getRadialQuaternion(tiltAngle));
    }
    
    /**
     * 渲染单个车厢
     */
    private void renderCar(MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light) {
        // 从矩阵获取位置矩阵
        MatrixStack.Entry entry = matrices.peek();
        
        // 创建顶点消费者，使用无纹理的渲染层
        VertexConsumer vertexConsumer = vertexConsumers.getBuffer(RenderLayer.getSolid());
        
        // 渲染一个简单的长方体作为车厢模型
        // 使用纯色渲染，不依赖纹理文件
        renderBox(entry, vertexConsumer, -WIDTH/2, -HEIGHT/2, -LENGTH/2, WIDTH/2, HEIGHT/2, LENGTH/2, light);
    }
    
    /**
     * 渲染一个简单的长方体
     */
    private void renderBox(MatrixStack.Entry entry, VertexConsumer vertexConsumer, 
                           float minX, float minY, float minZ, float maxX, float maxY, float maxZ, 
                           int light) {
        // 渲染前面（Z+）
        vertex(vertexConsumer, entry, maxX, minY, maxZ, 1.0F, 0.0F, light);
        vertex(vertexConsumer, entry, maxX, maxY, maxZ, 1.0F, 1.0F, light);
        vertex(vertexConsumer, entry, minX, maxY, maxZ, 0.0F, 1.0F, light);
        vertex(vertexConsumer, entry, minX, minY, maxZ, 0.0F, 0.0F, light);
        
        // 渲染后面（Z-）
        vertex(vertexConsumer, entry, minX, minY, minZ, 0.0F, 0.0F, light);
        vertex(vertexConsumer, entry, minX, maxY, minZ, 0.0F, 1.0F, light);
        vertex(vertexConsumer, entry, maxX, maxY, minZ, 1.0F, 1.0F, light);
        vertex(vertexConsumer, entry, maxX, minY, minZ, 1.0F, 0.0F, light);
        
        // 渲染顶部（Y+）
        vertex(vertexConsumer, entry, maxX, maxY, minZ, 1.0F, 1.0F, light);
        vertex(vertexConsumer, entry, minX, maxY, minZ, 0.0F, 1.0F, light);
        vertex(vertexConsumer, entry, minX, maxY, maxZ, 0.0F, 0.0F, light);
        vertex(vertexConsumer, entry, maxX, maxY, maxZ, 1.0F, 0.0F, light);
        
        // 渲染底部（Y-）
        vertex(vertexConsumer, entry, maxX, minY, maxZ, 1.0F, 0.0F, light);
        vertex(vertexConsumer, entry, minX, minY, maxZ, 0.0F, 0.0F, light);
        vertex(vertexConsumer, entry, minX, minY, minZ, 0.0F, 1.0F, light);
        vertex(vertexConsumer, entry, maxX, minY, minZ, 1.0F, 1.0F, light);
        
        // 渲染右侧（X+）
        vertex(vertexConsumer, entry, maxX, minY, minZ, 0.0F, 0.0F, light);
        vertex(vertexConsumer, entry, maxX, maxY, minZ, 0.0F, 1.0F, light);
        vertex(vertexConsumer, entry, maxX, maxY, maxZ, 1.0F, 1.0F, light);
        vertex(vertexConsumer, entry, maxX, minY, maxZ, 1.0F, 0.0F, light);
        
        // 渲染左侧（X-）
        vertex(vertexConsumer, entry, minX, minY, maxZ, 1.0F, 0.0F, light);
        vertex(vertexConsumer, entry, minX, maxY, maxZ, 1.0F, 1.0F, light);
        vertex(vertexConsumer, entry, minX, maxY, minZ, 0.0F, 1.0F, light);
        vertex(vertexConsumer, entry, minX, minY, minZ, 0.0F, 0.0F, light);
    }
    
    /**
     * 添加一个顶点到顶点消费者
     */
    private void vertex(VertexConsumer vertexConsumer, MatrixStack.Entry entry, 
                        float x, float y, float z, float u, float v, int light) {
        // 从RGB颜色中提取各个分量
        int red = (TRAIN_COLOR >> 16) & 0xFF;
        int green = (TRAIN_COLOR >> 8) & 0xFF;
        int blue = TRAIN_COLOR & 0xFF;
        
        vertexConsumer.vertex(entry.getPositionMatrix(), x, y, z)
            .color(red, green, blue, 255) // 使用列车的颜色
            .texture(u, v) // 纹理坐标仍然需要，但实际上不会使用
            .overlay(OverlayTexture.DEFAULT_UV)
            .light(light)
            .normal(entry.getNormalMatrix(), 0.0F, 1.0F, 0.0F)
            .next();
    }
}