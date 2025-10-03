package com.krt.mod.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import com.krt.mod.system.PerformanceMonitor;

import java.util.concurrent.TimeUnit;

import static net.minecraft.server.command.CommandManager.*;

/**
 * 性能分析命令，用于查看模组性能指标
 */
public class PerformanceCommand {
    
    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(literal("krt")
            .then(literal("performance")
                .requires(source -> source.hasPermissionLevel(2))
                .executes(context -> showPerformanceReport(context.getSource()))
                .then(literal("reset")
                    .executes(context -> resetPerformanceStats(context.getSource()))
                )
                .then(literal("warning")
                    .then(argument("threshold", IntegerArgumentType.integer(1, 200))
                        .executes(context -> setWarningThreshold(context.getSource(), 
                            IntegerArgumentType.getInteger(context, "threshold")))
                    )
                )
            )
        );
    }
    
    private static int showPerformanceReport(ServerCommandSource source) {
        PerformanceMonitor monitor = PerformanceMonitor.getInstance();
        
        // 获取当前性能数据
        double avgTickTime = monitor.getAverageTickTime();
        int currentFPS = monitor.getCurrentFPS();
        
        // 发送性能报告
        Text report = Text.literal("===== KRT模组性能报告 =====\n")
            .formatted(Formatting.GOLD)
            .append(Text.literal("平均tick时间: " + String.format("%.2f", avgTickTime) + "ms\n")
                .formatted(getColorForTickTime(avgTickTime)))
            .append(Text.literal("当前FPS: " + currentFPS + "\n")
                .formatted(getColorForFPS(currentFPS)))
            .append(Text.literal("系统执行时间:\n")
                .formatted(Formatting.YELLOW))
            .append(Text.literal(monitor.getSystemExecutionReport())
                .formatted(Formatting.WHITE));
        
        source.sendFeedback(report, false);
        
        return 1;
    }
    
    private static int resetPerformanceStats(ServerCommandSource source) {
        PerformanceMonitor.getInstance().resetStats();
        source.sendFeedback(Text.literal("性能统计数据已重置").formatted(Formatting.GREEN), false);
        return 1;
    }
    
    private static int setWarningThreshold(ServerCommandSource source, int threshold) {
        // 这里可以实现设置警告阈值的逻辑
        // PerformanceMonitor.setTickWarningThreshold(threshold);
        source.sendFeedback(Text.literal("性能警告阈值已设置为: " + threshold + "ms").formatted(Formatting.YELLOW), false);
        return 1;
    }
    
    private static Formatting getColorForTickTime(double tickTime) {
        if (tickTime < 10) return Formatting.GREEN;
        if (tickTime < 30) return Formatting.YELLOW;
        return Formatting.RED;
    }
    
    private static Formatting getColorForFPS(int fps) {
        if (fps >= 60) return Formatting.GREEN;
        if (fps >= 30) return Formatting.YELLOW;
        return Formatting.RED;
    }
}