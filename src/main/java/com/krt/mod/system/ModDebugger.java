package com.krt.mod.system;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import com.krt.mod.KRTMod;
import com.krt.mod.block.TrackBlock;
import com.krt.mod.block.ATPSignalBlock;
import com.krt.mod.entity.TrainEntity;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ModDebugger {
    
    private static final ModDebugger INSTANCE = new ModDebugger();
    
    // 调试模式标志
    private boolean isDebugModeEnabled = false;
    
    // 显示选项
    private boolean showTrackSegments = false;
    private boolean showTrainPaths = false;
    private boolean showSignalStates = false;
    private boolean showTrainInfo = false;
    
    // 玩家调试会话
    private final Map<UUID, DebugSession> playerSessions = new HashMap<>();
    
    // 调试范围（区块）
    private static final int DEBUG_RANGE = 10;
    
    private ModDebugger() {}
    
    public static ModDebugger getInstance() {
        return INSTANCE;
    }
    
    // 切换调试模式
    public void toggleDebugMode(PlayerEntity player) {
        isDebugModeEnabled = !isDebugModeEnabled;
        
        if (isDebugModeEnabled) {
            // 创建调试会话
            playerSessions.put(player.getUuid(), new DebugSession(player));
            player.sendMessage(Text.literal("调试模式已启用"), false);
        } else {
            // 清理调试会话
            playerSessions.remove(player.getUuid());
            player.sendMessage(Text.literal("调试模式已禁用"), false);
        }
    }
    
    // 切换轨道区段显示
    public void toggleTrackSegments(PlayerEntity player) {
        DebugSession session = getOrCreateSession(player);
        session.showTrackSegments = !session.showTrackSegments;
        player.sendMessage(Text.literal("轨道区段显示: " + (session.showTrackSegments ? "已启用" : "已禁用")), false);
    }
    
    // 切换列车路径显示
    public void toggleTrainPaths(PlayerEntity player) {
        DebugSession session = getOrCreateSession(player);
        session.showTrainPaths = !session.showTrainPaths;
        player.sendMessage(Text.literal("列车路径显示: " + (session.showTrainPaths ? "已启用" : "已禁用")), false);
    }
    
    // 切换信号状态显示
    public void toggleSignalStates(PlayerEntity player) {
        DebugSession session = getOrCreateSession(player);
        session.showSignalStates = !session.showSignalStates;
        player.sendMessage(Text.literal("信号状态显示: " + (session.showSignalStates ? "已启用" : "已禁用")), false);
    }
    
    // 切换列车信息显示
    public void toggleTrainInfo(PlayerEntity player) {
        DebugSession session = getOrCreateSession(player);
        session.showTrainInfo = !session.showTrainInfo;
        player.sendMessage(Text.literal("列车信息显示: " + (session.showTrainInfo ? "已启用" : "已禁用")), false);
    }
    
    // 获取或创建调试会话
    private DebugSession getOrCreateSession(PlayerEntity player) {
        return playerSessions.computeIfAbsent(player.getUuid(), uuid -> new DebugSession(player));
    }
    
    // 客户端渲染调试信息
    public void renderDebugInfo(MatrixStack matrices, VertexConsumerProvider vertexConsumers, float tickDelta) {
        if (!isDebugModeEnabled) return;
        
        MinecraftClient client = MinecraftClient.getInstance();
        PlayerEntity player = client.player;
        if (player == null) return;
        
        DebugSession session = playerSessions.get(player.getUuid());
        if (session == null) return;
        
        // 渲染轨道区段
        if (session.showTrackSegments) {
            renderTrackSegments(matrices, vertexConsumers, player);
        }
        
        // 渲染列车路径
        if (session.showTrainPaths) {
            renderTrainPaths(matrices, vertexConsumers);
        }
        
        // 渲染信号状态
        if (session.showSignalStates) {
            renderSignalStates(matrices, vertexConsumers, player);
        }
        
        // 渲染列车信息
        if (session.showTrainInfo) {
            renderTrainInfo(matrices, player);
        }
    }
    
    // 渲染轨道区段
    private void renderTrackSegments(MatrixStack matrices, VertexConsumerProvider vertexConsumers, PlayerEntity player) {
        World world = player.world;
        BlockPos playerPos = player.getBlockPos();
        
        // 在玩家周围一定范围内查找轨道方块
        for (int x = -DEBUG_RANGE; x <= DEBUG_RANGE; x++) {
            for (int z = -DEBUG_RANGE; z <= DEBUG_RANGE; z++) {
                for (int y = -3; y <= 3; y++) {
                    BlockPos pos = playerPos.add(x, y, z);
                    
                    if (world.getBlockState(pos).getBlock() instanceof TrackBlock) {
                        // 渲染轨道信息
                        TrackBlock.TrackProperties properties = ((TrackBlock)world.getBlockState(pos).getBlock()).getTrackProperties(world, pos);
                        renderTrackInfo(matrices, vertexConsumers, pos, properties);
                    }
                }
            }
        }
    }
    
    // 渲染轨道信息
    private void renderTrackInfo(MatrixStack matrices, VertexConsumerProvider vertexConsumers, BlockPos pos, TrackBlock.TrackProperties properties) {
        // 这里可以实现轨道信息的可视化渲染
        // 例如：渲染轨道类型、方向、电气化状态等
        MinecraftClient client = MinecraftClient.getInstance();
        String info = "轨道: " + properties.trackType.name() + ", 方向: " + properties.direction;
        client.textRenderer.draw(matrices, info, pos.getX() + 0.5f, pos.getY() + 1.5f, pos.getZ() + 0.5f, 0xFFFFFF);
    }
    
    // 渲染列车路径
    private void renderTrainPaths(MatrixStack matrices, VertexConsumerProvider vertexConsumers) {
        // 这里可以实现列车路径的可视化渲染
        // 例如：显示列车的运行轨迹、未来路径预测等
    }
    
    // 渲染信号状态
    private void renderSignalStates(MatrixStack matrices, VertexConsumerProvider vertexConsumers, PlayerEntity player) {
        World world = player.world;
        BlockPos playerPos = player.getBlockPos();
        
        // 在玩家周围一定范围内查找信号机方块
        for (int x = -DEBUG_RANGE; x <= DEBUG_RANGE; x++) {
            for (int z = -DEBUG_RANGE; z <= DEBUG_RANGE; z++) {
                for (int y = -3; y <= 3; y++) {
                    BlockPos pos = playerPos.add(x, y, z);
                    
                    if (world.getBlockState(pos).getBlock() instanceof ATPSignalBlock) {
                        // 渲染信号机状态
                        renderSignalInfo(matrices, vertexConsumers, pos);
                    }
                }
            }
        }
    }
    
    // 渲染信号机信息
    private void renderSignalInfo(MatrixStack matrices, VertexConsumerProvider vertexConsumers, BlockPos pos) {
        // 这里可以实现信号机信息的可视化渲染
        // 例如：渲染信号机状态、允许速度等
    }
    
    // 渲染列车信息
    private void renderTrainInfo(MatrixStack matrices, PlayerEntity player) {
        World world = player.world;
        
        // 显示附近列车的详细信息
        world.getEntitiesByClass(TrainEntity.class, player.getBoundingBox().expand(DEBUG_RANGE * 16), train -> true)
            .forEach(train -> renderTrainDetails(matrices, train));
    }
    
    // 渲染列车详细信息
    private void renderTrainDetails(MatrixStack matrices, TrainEntity train) {
        // 这里可以实现列车详细信息的可视化渲染
        // 例如：显示列车ID、速度、状态、目的地等
        MinecraftClient client = MinecraftClient.getInstance();
        Vec3d pos = train.getPos().add(0, 2, 0);
        String info = "列车: " + train.getDisplayName().getString() + ", 速度: " + Math.round(train.getVelocity().length() * 3.6) + " km/h";
        client.textRenderer.draw(matrices, info, (float)pos.x, (float)pos.y, (float)pos.z, 0xFFFF00);
    }
    
    // 调试会话类
    private static class DebugSession {
        private final PlayerEntity player;
        private boolean showTrackSegments = false;
        private boolean showTrainPaths = false;
        private boolean showSignalStates = false;
        private boolean showTrainInfo = false;
        
        public DebugSession(PlayerEntity player) {
            this.player = player;
        }
    }
    
    // 获取调试模式状态
    public boolean isDebugModeEnabled() {
        return isDebugModeEnabled;
    }
    
    // 清理玩家会话
    public void cleanup(PlayerEntity player) {
        playerSessions.remove(player.getUuid());
    }
}