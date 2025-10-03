package com.krt.mod.system;

import com.krt.mod.KRTMod;
import com.krt.mod.entity.TrainCar;
import com.krt.mod.entity.TrainConsist;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import net.minecraft.text.Text;
import net.minecraft.util.WorldSavePath;
import net.minecraft.world.World;
import java.io.File;
import java.nio.file.Path;
// import com.krt.mod.util.PerformanceMonitor;
// import com.krt.mod.util.LogSystem;

/**
 * 车辆系统初始化类，负责在模组启动时初始化所有车辆系统组件
 */
public class VehicleSystemInitializer implements ModInitializer {
    // 系统实例
    private static PowerSupplySystem powerSupplySystem;
    private static VehicleManagementSystem vehicleManagementSystem;
    private static DepotSystem depotSystem;
    
    @Override
    public void onInitialize() {
        // 注册服务器生命周期事件
        ServerLifecycleEvents.SERVER_STARTING.register(this::onServerStarting);
        ServerLifecycleEvents.SERVER_STOPPING.register(this::onServerStopping);
        
        // 注册服务器tick事件
        ServerTickEvents.START_SERVER_TICK.register(this::onServerTick);
        
        KRTMod.LOGGER.info("KRT轨道交通模组 - 车辆系统初始化中...");
    }
    
    /**
     * 服务器启动时的处理
     */
    private void onServerStarting(MinecraftServer server) {
        // 获取主世界
        World world = server.getWorld(World.OVERWORLD);
        if (world == null) {
            KRTMod.LOGGER.error("KRT轨道交通模组 - 无法获取主世界，车辆系统初始化失败！");
            return;
        }
        
        // 初始化供电系统
        powerSupplySystem = new PowerSupplySystem(world);
        
        // 初始化车辆管理系统
        vehicleManagementSystem = VehicleManagementSystem.getInstance(world);
        
        // 初始化车辆段系统
        depotSystem = new DepotSystem(world);
        
        // 加载系统配置
        loadSystemConfigurations(server);
        
        // 注册默认组件
        registerDefaultComponents();
        
        KRTMod.LOGGER.info("KRT轨道交通模组 - 车辆系统初始化完成！");
        KRTMod.LOGGER.info("KRT轨道交通模组 - 车辆管理系统: " + vehicleManagementSystem.getSystemStatusReport().getString());
        KRTMod.LOGGER.info("KRT轨道交通模组 - 车辆段系统: " + depotSystem.getSystemStatusReport().getString());
    }
    
    /**
     * 服务器停止时的处理
     */
    private void onServerStopping(MinecraftServer server) {
        if (vehicleManagementSystem != null) {
            // 保存车辆和编组数据
            saveSystemData(server);
        }
        
        KRTMod.LOGGER.info("KRT轨道交通模组 - 车辆系统已关闭！");
    }
    
    /**
     * 服务器tick时的处理
     */
    private void onServerTick(MinecraftServer server) {
        // 开始tick性能监控
        PerformanceMonitor.getInstance().startTick();
        
        // 优化更新频率
        long ticks = server.getTicks();
        
        // 供电系统 - 每2tick更新一次
        if (powerSupplySystem != null && ticks % 2 == 0) {
            PerformanceMonitor.getInstance().startSystemExecution("PowerSupplySystem");
            powerSupplySystem.update();
            PerformanceMonitor.getInstance().endSystemExecution("PowerSupplySystem");
        }
        
        // 车辆管理系统 - 每tick更新（关键系统）
        if (vehicleManagementSystem != null) {
            PerformanceMonitor.getInstance().startSystemExecution("VehicleManagementSystem");
            vehicleManagementSystem.update();
            PerformanceMonitor.getInstance().endSystemExecution("VehicleManagementSystem");
        }
        
        // 车辆段系统 - 每3tick更新一次
        if (depotSystem != null && ticks % 3 == 0) {
            PerformanceMonitor.getInstance().startSystemExecution("DepotSystem");
            depotSystem.update();
            PerformanceMonitor.getInstance().endSystemExecution("DepotSystem");
        }
        
        // 每600tick（30秒）输出一次系统状态
        if (ticks % 600 == 0) {
            logSystemStatus(server);
            // 输出性能报告
            LogSystem.systemLog(PerformanceMonitor.getInstance().getSystemExecutionReport());
        }
        
        // 结束tick性能监控
        PerformanceMonitor.getInstance().endTick();
    }
    
    /**
     * 加载系统配置
     */
    private void loadSystemConfigurations(MinecraftServer server) {
        // 获取保存目录
        Path saveDir = server.getSavePath(WorldSavePath.ROOT);
        File configDir = new File(saveDir.toFile(), "krt_mod");
        
        if (!configDir.exists()) {
            configDir.mkdirs();
        }
        
        // 这里可以添加加载配置文件的逻辑
        KRTMod.LOGGER.info("KRT轨道交通模组 - 加载车辆系统配置: " + configDir.getAbsolutePath());
    }
    
    /**
     * 保存系统数据
     */
    private void saveSystemData(MinecraftServer server) {
        // 获取保存目录
        Path saveDir = server.getSavePath(WorldSavePath.ROOT);
        File saveDirFile = new File(saveDir.toFile(), "krt_mod");
        
        if (!saveDirFile.exists()) {
            saveDirFile.mkdirs();
        }
        
        // 这里可以添加保存车辆和编组数据的逻辑
        KRTMod.LOGGER.info("KRT轨道交通模组 - 保存车辆系统数据到: " + saveDirFile.getAbsolutePath());
    }
    
    /**
     * 注册默认组件
     */
    private void registerDefaultComponents() {
        // 创建默认的车辆段（如果不存在）
        createDefaultDepots();
        
        // 注册一些默认的电源
        registerDefaultPowerSources();
    }
    
    /**
     * 创建默认的车辆段
     */
    private void createDefaultDepots() {
        if (depotSystem == null) {
            return;
        }
        
        // 创建主车辆段
        DepotSystem.Depot mainDepot = depotSystem.createDepot(
            "主车辆段",
            DepotSystem.DepotType.MAJOR_DEPOT,
            new net.minecraft.util.math.BlockPos(0, 64, 0)
        );
        
        if (mainDepot != null) {
            // 设置工作人员数量
            mainDepot.setStaffCount(15);
            
            // 添加各种区域
            mainDepot.addArea(
                DepotSystem.DepotAreaType.PARKING_AREA,
                new net.minecraft.util.math.BlockPos(-50, 64, -50),
                new net.minecraft.util.math.BlockPos(50, 64, 50)
            );
            
            mainDepot.addArea(
                DepotSystem.DepotAreaType.WASHING_BAY,
                new net.minecraft.util.math.BlockPos(50, 64, -30),
                new net.minecraft.util.math.BlockPos(70, 64, -10)
            );
            
            mainDepot.addArea(
                DepotSystem.DepotAreaType.MAINTENANCE_BAY,
                new net.minecraft.util.math.BlockPos(50, 64, 10),
                new net.minecraft.util.math.BlockPos(70, 64, 30)
            );
            
            mainDepot.addArea(
                DepotSystem.DepotAreaType.TEST_TRACK,
                new net.minecraft.util.math.BlockPos(-70, 64, -20),
                new net.minecraft.util.math.BlockPos(-50, 64, 20)
            );
        }
        
        // 创建存车段
        DepotSystem.Depot storageDepot = depotSystem.createDepot(
            "存车段",
            DepotSystem.DepotType.STORAGE_DEPOT,
            new net.minecraft.util.math.BlockPos(0, 64, 100)
        );
        
        if (storageDepot != null) {
            // 设置工作人员数量
            storageDepot.setStaffCount(5);
            
            // 添加停车区域
            storageDepot.addArea(
                DepotSystem.DepotAreaType.PARKING_AREA,
                new net.minecraft.util.math.BlockPos(-50, 64, 100),
                new net.minecraft.util.math.BlockPos(50, 64, 200)
            );
        }
    }
    
    /**
     * 注册默认的电源
     */
    private void registerDefaultPowerSources() {
        if (powerSupplySystem == null) {
            return;
        }
        
        // 在车辆段附近添加电源
        powerSupplySystem.addPowerSource(
            new net.minecraft.util.math.BlockPos(0, 65, 0),
            PowerSupplySystem.PowerType.OVERHEAD_WIRE,
            10000
        );
        
        powerSupplySystem.addPowerSource(
            new net.minecraft.util.math.BlockPos(0, 65, 100),
            PowerSupplySystem.PowerType.OVERHEAD_WIRE,
            8000
        );
    }
    
    /**
     * 记录系统状态
     */
    private void logSystemStatus(MinecraftServer server) {
        if (server == null || server.isDedicated()) {
            // 仅在专用服务器上输出详细日志
            if (powerSupplySystem != null) {
                KRTMod.LOGGER.info("KRT轨道交通模组 - " + powerSupplySystem.getStatusText().getString());
            }
            
            if (vehicleManagementSystem != null) {
                KRTMod.LOGGER.info("KRT轨道交通模组 - " + vehicleManagementSystem.getSystemStatusReport().getString());
            }
            
            if (depotSystem != null) {
                KRTMod.LOGGER.info("KRT轨道交通模组 - " + depotSystem.getSystemStatusReport().getString());
            }
        }
    }
    
    // 获取系统实例的静态方法
    public static PowerSupplySystem getPowerSupplySystem() {
        return powerSupplySystem;
    }
    
    public static VehicleManagementSystem getVehicleManagementSystem() {
        return vehicleManagementSystem;
    }
    
    public static DepotSystem getDepotSystem() {
        return depotSystem;
    }
    
    /**
     * 创建示例列车
     */
    public static void createExampleTrain() {
        if (vehicleManagementSystem == null) {
            return;
        }
        
        // 创建一个简单的列车编组示例
        TrainConsist consist = vehicleManagementSystem.createConsist();
        if (consist == null) {
            return;
        }
        
        // 创建车头
        TrainCar headCar = vehicleManagementSystem.createCar(
            com.krt.mod.entity.TrainCar.CarType.HEAD_CAR,
            "A型地铁车头"
        );
        
        // 创建中间车
        TrainCar middleCar = vehicleManagementSystem.createCar(
            com.krt.mod.entity.TrainCar.CarType.MIDDLE_CAR,
            "A型地铁中间车"
        );
        
        // 创建尾车
        TrainCar tailCar = vehicleManagementSystem.createCar(
            com.krt.mod.entity.TrainCar.CarType.TAIL_CAR,
            "A型地铁尾车"
        );
        
        // 将车辆添加到编组
        if (headCar != null) {
            consist.addCar(headCar);
        }
        
        if (middleCar != null) {
            consist.addCar(middleCar);
        }
        
        if (tailCar != null) {
            consist.addCar(tailCar);
        }
        
        // 如果有车辆段系统，将编组分配到车辆段
        if (depotSystem != null) {
            for (DepotSystem.Depot depot : depotSystem.getDepots().values()) {
                if (depot.hasAvailableParking()) {
                    depotSystem.assignTrainToDepot(consist, depot.getId());
                    break;
                }
            }
        }
    }
}