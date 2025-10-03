package com.krt.mod.system;

import net.minecraft.server.MinecraftServer;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import com.krt.mod.system.LogSystem;

/**
 * 性能监控工具类，用于监控模组各系统的性能表现
 */
public class PerformanceMonitor {
    private static final PerformanceMonitor INSTANCE = new PerformanceMonitor();
    
    // 性能监控配置
    private static final long TICK_WARNING_THRESHOLD_MS = 50; // 单tick超过此值发出警告
    private static final int FPS_WARNING_THRESHOLD = 20; // FPS低于此值发出警告
    
    // tick时间统计
    private long lastTickTime = 0;
    private long tickCounter = 0;
    private long totalTickTime = 0;
    private Map<String, Long> systemExecutionTimes = new HashMap<>();
    private Map<String, Long> systemLastUpdateTime = new HashMap<>();
    
    // 内存监控
    private long lastMemoryCheckTime = 0;
    private final long MEMORY_CHECK_INTERVAL = 30000; // 30秒检查一次
    
    // FPS监控
    private int fpsCounter = 0;
    private long fpsStartTime = 0;
    private int currentFPS = 0;
    
    private PerformanceMonitor() {
        // 私有构造函数
    }
    
    public static PerformanceMonitor getInstance() {
        return INSTANCE;
    }
    
    /**
     * 开始tick性能监控
     */
    public void startTick() {
        lastTickTime = System.nanoTime();
        fpsCounter++;
        
        // 计算FPS
        if (fpsStartTime == 0) {
            fpsStartTime = System.nanoTime();
        } else if (System.nanoTime() - fpsStartTime >= TimeUnit.SECONDS.toNanos(1)) {
            currentFPS = fpsCounter;
            fpsCounter = 0;
            fpsStartTime = System.nanoTime();
            
            // FPS警告
            if (currentFPS < FPS_WARNING_THRESHOLD) {
                LogSystem.warning("性能警告 - 当前FPS: " + currentFPS + ", 低于警告阈值: " + FPS_WARNING_THRESHOLD);
            }
        }
    }
    
    /**
     * 结束tick性能监控
     */
    public void endTick() {
        long tickTimeNs = System.nanoTime() - lastTickTime;
        long tickTimeMs = TimeUnit.NANOSECONDS.toMillis(tickTimeNs);
        totalTickTime += tickTimeMs;
        tickCounter++;
        
        // tick时间警告
        if (tickTimeMs > TICK_WARNING_THRESHOLD_MS) {
            LogSystem.warning("性能警告 - 当前tick耗时: " + tickTimeMs + "ms, 超过警告阈值: " + TICK_WARNING_THRESHOLD_MS + "ms");
        }
        
        // 定期内存检查
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastMemoryCheckTime >= MEMORY_CHECK_INTERVAL) {
            checkMemoryUsage();
            lastMemoryCheckTime = currentTime;
        }
    }
    
    /**
     * 开始系统执行时间测量
     * @param systemName 系统名称
     */
    public void startSystemExecution(String systemName) {
        systemLastUpdateTime.put(systemName, System.nanoTime());
    }
    
    /**
     * 结束系统执行时间测量
     * @param systemName 系统名称
     */
    public void endSystemExecution(String systemName) {
        if (systemLastUpdateTime.containsKey(systemName)) {
            long executionTimeNs = System.nanoTime() - systemLastUpdateTime.get(systemName);
            long executionTimeMs = TimeUnit.NANOSECONDS.toMillis(executionTimeNs);
            systemExecutionTimes.put(systemName, executionTimeMs);
        }
    }
    
    /**
     * 检查内存使用情况
     */
    private void checkMemoryUsage() {
        Runtime runtime = Runtime.getRuntime();
        long usedMemoryMB = (runtime.totalMemory() - runtime.freeMemory()) / 1024 / 1024;
        long maxMemoryMB = runtime.maxMemory() / 1024 / 1024;
        
        // 记录内存使用情况
        LogSystem.systemLog("性能监控 - 内存使用: " + usedMemoryMB + "MB / " + maxMemoryMB + "MB (" + 
                           (usedMemoryMB * 100 / maxMemoryMB) + "%)");
    }
    
    /**
     * 获取平均tick时间
     */
    public double getAverageTickTime() {
        if (tickCounter == 0) return 0;
        return (double) totalTickTime / tickCounter;
    }
    
    /**
     * 获取当前FPS
     */
    public int getCurrentFPS() {
        return currentFPS;
    }
    
    /**
     * 获取系统执行时间报告
     */
    public String getSystemExecutionReport() {
        StringBuilder report = new StringBuilder("性能报告 - 系统执行时间:\n");
        systemExecutionTimes.forEach((system, time) -> {
            report.append("  - ").append(system).append(": ").append(time).append("ms\n");
        });
        report.append("平均tick时间: ").append(String.format("%.2f", getAverageTickTime())).append("ms\n");
        report.append("当前FPS: ").append(currentFPS);
        return report.toString();
    }
    
    /**
     * 重置性能统计数据
     */
    public void resetStats() {
        tickCounter = 0;
        totalTickTime = 0;
        systemExecutionTimes.clear();
        fpsCounter = 0;
        fpsStartTime = 0;
        currentFPS = 0;
    }
}