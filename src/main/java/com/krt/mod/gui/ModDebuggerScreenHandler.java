package com.krt.mod.gui;

import com.krt.mod.entity.TrainEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.ScreenHandlerType;
import com.krt.mod.Init;
import com.krt.mod.system.LogSystem;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import java.util.ArrayList;
import java.util.List;

public class ModDebuggerScreenHandler extends ScreenHandler {

    private static final List<DebugInfo> debugInfoCache = new ArrayList<>();
    private static long lastCacheUpdate = 0;
    private static final long CACHE_DURATION = 200; // 缓存有效期（毫秒）
    private static List<DebugInfo> clientTrainsInfo = new ArrayList<>(); // 客户端存储的列车信息

    public ModDebuggerScreenHandler(int syncId) {
        super(Init.MOD_DEBUGGER_SCREEN_HANDLER, syncId);
    }
    
    public ModDebuggerScreenHandler(int syncId, PlayerInventory playerInventory) {
        this(syncId);
    }

    // 必须覆盖的抽象方法
    @Override
    public ItemStack transferSlot(PlayerEntity player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean canUse(PlayerEntity player) {
        return true; // 允许任何玩家使用调试器
    }

    // 服务器端获取所有列车信息
    public static List<DebugInfo> getAllTrainsInfo(ServerPlayerEntity player) {
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastCacheUpdate < CACHE_DURATION) {
            return debugInfoCache;
        }

        debugInfoCache.clear();
        lastCacheUpdate = currentTime;

        ServerWorld world = player.getServer().getWorld(player.getWorld().getRegistryKey());
        if (world == null) return debugInfoCache;
        
        for (Entity entity : world.iterateEntities()) {
            if (entity instanceof TrainEntity) {
                TrainEntity train = (TrainEntity) entity;
                DebugInfo info = new DebugInfo();
                info.trainId = train.getTrainId();
                info.speed = train.getCurrentSpeed();
                info.health = train.getHealth();
                info.position = train.getBlockPos();
                info.atoEnabled = train.isATOEnabled();
                info.emergencyBrake = train.isEmergencyBraking();
                info.driverName = train.getDriver() != null ? train.getDriver().getDisplayName().getString() : "无";
                info.destination = train.getDestination();
                info.nextStation = train.getNextStation();
                info.currentLine = train.getCurrentLine();
                debugInfoCache.add(info);
            }
        }

        return debugInfoCache;
    }

    // 立即刷新缓存
    public static void refreshCache() {
        lastCacheUpdate = 0;
    }

    // 存储客户端列车信息（从服务器接收）
    public static void setTrainsInfo(List<DebugInfo> trainsInfo) {
        clientTrainsInfo.clear();
        clientTrainsInfo.addAll(trainsInfo);
    }

    // 获取客户端列车信息
    public static List<DebugInfo> getClientTrainsInfo() {
        return new ArrayList<>(clientTrainsInfo);
    }

    // 调试信息数据类
    public static class DebugInfo {
        public String trainId;
        public float speed;
        public int health;
        public BlockPos position;
        public boolean atoEnabled;
        public boolean emergencyBrake;
        public String driverName;
        public String destination;
        public String nextStation;
        public String currentLine;
    }

    // 发送调试命令到服务器
    public static void sendDebugCommand(ServerPlayerEntity player, String command, String targetTrainId, String parameters) {
        LogSystem.debug("Debug command from " + player.getDisplayName().getString() + ": " + command + " target: " + targetTrainId + " params: " + parameters);

        // 根据命令类型执行相应操作
        ServerWorld world = player.getServer().getWorld(player.getWorld().getRegistryKey());
        if (world == null) return;
        
        for (Entity entity : world.iterateEntities()) {
            if (entity instanceof TrainEntity && (targetTrainId.equals("all") || ((TrainEntity)entity).getTrainId().equals(targetTrainId))) {
                TrainEntity train = (TrainEntity) entity;
                executeCommand(train, command, parameters);
            }
        }
    }

    private static void executeCommand(TrainEntity train, String command, String parameters) {
        switch (command) {
            case "set_speed":
                try {
                    float speed = Float.parseFloat(parameters);
                    train.getControlSystem().setTargetSpeed(speed);
                    LogSystem.trainLog(train.getTrainId(), "调试命令: 设置目标速度为 " + speed + " km/h");
                } catch (NumberFormatException e) {
                    LogSystem.error("Invalid speed parameter: " + parameters);
                }
                break;
            case "toggle_ato":
                boolean atoEnabled = !train.isATOEnabled();
                train.setATOEnabled(atoEnabled);
                LogSystem.trainLog(train.getTrainId(), "调试命令: " + (atoEnabled ? "启用" : "禁用") + " ATO模式");
                break;
            case "release_brake":
                train.releaseEmergencyBrake();
                LogSystem.trainLog(train.getTrainId(), "调试命令: 释放紧急制动");
                break;
            case "apply_brake":
                train.applyEmergencyBrake();
                LogSystem.trainLog(train.getTrainId(), "调试命令: 触发紧急制动");
                break;
            case "set_health":
                try {
                    int health = Integer.parseInt(parameters);
                    train.setHealth(health);
                    LogSystem.trainLog(train.getTrainId(), "调试命令: 设置健康值为 " + health + "%");
                } catch (NumberFormatException e) {
                    LogSystem.error("Invalid health parameter: " + parameters);
                }
                break;
            case "clear_cache":
                refreshCache();
                LogSystem.debug("Cache cleared");
                break;
        }
    }
}