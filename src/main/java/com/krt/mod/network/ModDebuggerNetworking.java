package com.krt.mod.network;

import com.krt.mod.KRTMod;
import com.krt.mod.gui.ModDebuggerScreenHandler;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import java.util.ArrayList;
import java.util.List;
import io.netty.buffer.Unpooled;

public class ModDebuggerNetworking {
    // 网络数据包标识符
    public static final Identifier REQUEST_TRAINS_INFO = new Identifier(KRTMod.MOD_ID, "request_trains_info");
    public static final Identifier SEND_TRAINS_INFO = new Identifier(KRTMod.MOD_ID, "send_trains_info");
    public static final Identifier EXECUTE_DEBUG_COMMAND = new Identifier(KRTMod.MOD_ID, "execute_debug_command");
    public static final Identifier SEND_LOGS = new Identifier(KRTMod.MOD_ID, "send_logs");

    // 注册网络处理器
    public static void register() {
        // 服务器端处理列车信息请求
        ServerPlayNetworking.registerGlobalReceiver(REQUEST_TRAINS_INFO, (server, player, handler, buf, responseSender) -> {
            // 获取所有列车信息
            List<ModDebuggerScreenHandler.DebugInfo> trainsInfo = ModDebuggerScreenHandler.getAllTrainsInfo(player);
            
            // 创建响应缓冲区
            PacketByteBuf responseBuf = new PacketByteBuf(Unpooled.buffer());
            
            // 写入列车数量
            responseBuf.writeInt(trainsInfo.size());
            
            // 写入每个列车的信息
            for (ModDebuggerScreenHandler.DebugInfo info : trainsInfo) {
                responseBuf.writeString(info.trainId);
                responseBuf.writeFloat(info.speed);
                responseBuf.writeInt(info.health);
                responseBuf.writeBlockPos(info.position);
                responseBuf.writeBoolean(info.atoEnabled);
                responseBuf.writeBoolean(info.emergencyBrake);
                responseBuf.writeString(info.driverName);
                responseBuf.writeString(info.destination);
                responseBuf.writeString(info.nextStation);
                responseBuf.writeString(info.currentLine);
            }
            
            // 发送响应给客户端
            ServerPlayNetworking.send(player, SEND_TRAINS_INFO, responseBuf);
        });

        // 服务器端处理调试命令执行
        ServerPlayNetworking.registerGlobalReceiver(EXECUTE_DEBUG_COMMAND, (server, player, handler, buf, responseSender) -> {
            String command = buf.readString();
            String targetTrainId = buf.readString();
            String parameters = buf.readString();
            
            // 在主线程中执行命令
            server.execute(() -> {
                ModDebuggerScreenHandler.sendDebugCommand(player, command, targetTrainId, parameters);
                
                // 命令执行后，刷新缓存
                ModDebuggerScreenHandler.refreshCache();
            });
        });
    }

    // 注册客户端网络处理器
    public static void registerClient() {
        // 客户端处理服务器发送的列车信息
        ClientPlayNetworking.registerGlobalReceiver(SEND_TRAINS_INFO, (client, handler, buf, responseSender) -> {
            int count = buf.readInt();
            List<ModDebuggerScreenHandler.DebugInfo> trainsInfo = new ArrayList<>();
            
            for (int i = 0; i < count; i++) {
                ModDebuggerScreenHandler.DebugInfo info = new ModDebuggerScreenHandler.DebugInfo();
                info.trainId = buf.readString();
                info.speed = buf.readFloat();
                info.health = buf.readInt();
                info.position = buf.readBlockPos();
                info.atoEnabled = buf.readBoolean();
                info.emergencyBrake = buf.readBoolean();
                info.driverName = buf.readString();
                info.destination = buf.readString();
                info.nextStation = buf.readString();
                info.currentLine = buf.readString();
                trainsInfo.add(info);
            }
            
            // 存储列车信息供调试器界面使用
            ModDebuggerScreenHandler.setTrainsInfo(trainsInfo);
        });
    }

    // 从客户端请求列车信息
    public static void requestTrainsInfoFromClient() {
        ClientPlayNetworking.send(REQUEST_TRAINS_INFO, new PacketByteBuf(Unpooled.buffer()));
    }

    // 从客户端发送调试命令
    public static void sendDebugCommandFromClient(String command, String targetTrainId, String parameters) {
        PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
        buf.writeString(command);
        buf.writeString(targetTrainId);
        buf.writeString(parameters);
        ClientPlayNetworking.send(EXECUTE_DEBUG_COMMAND, buf);
    }
}