package com.krt.mod.command;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.MinecraftServer;
// import net.fabricmc.fabric.api.command.v1.CommandRegistrationCallback;

/**
 * 命令注册器，负责注册所有KRT模组的命令
 */
public class CommandRegistry {
    
    /**
     * 初始化并注册所有命令
     */
    public static void initialize() {
        // 暂时注释命令注册，API路径可能不正确
        // CommandRegistrationCallback.EVENT.register((dispatcher, dedicated) -> {
        //     registerCommands(dispatcher);
        // });
    }
    
    /**
     * 注册所有命令
     */
    private static void registerCommands(CommandDispatcher<ServerCommandSource> dispatcher) {
        // 注册性能分析命令
        PerformanceCommand.register(dispatcher);
        
        // 注册供电系统测试命令
        PowerTestCommand.register(dispatcher);
    }
}