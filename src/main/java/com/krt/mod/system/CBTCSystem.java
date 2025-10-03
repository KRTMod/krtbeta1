package com.krt.mod.system;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import com.krt.mod.KRTMod;
import com.krt.mod.block.TrackBlock;
import com.krt.mod.block.SignalBlock;
import com.krt.mod.block.ATPSignalBlock;
// import com.krt.mod.block.SignalBlockEntity;
import com.krt.mod.block.ATPSignalBlockEntity;
import com.krt.mod.entity.TrainEntity;
import com.krt.mod.entity.TrainConsist;
import com.krt.mod.entity.TrainCar;
import com.krt.mod.system.LogSystem;
import com.krt.mod.system.PowerSupplySystem;
import com.krt.mod.system.TimetableSystem;

import java.util.*;
import java.util.concurrent.*;
import java.util.HashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 基于通信的列车控制系统(CBTC)
 * 负责列车之间、列车与地面设备之间的通信和控制
 */
public class CBTCSystem {
    private static final Map<World, CBTCSystem> INSTANCES = new HashMap<>();
    private final World world;
    
    // 闭塞区间管理
    private final Map<BlockPos, BlockSection> sections = new ConcurrentHashMap<>();
    // 列车位置跟踪
    private final Map<String, TrainPositionInfo> trainPositions = new ConcurrentHashMap<>();
    // 列车调度队列
    // 修改为有界队列，防止任务堆积导致内存占用过大
    // 队列容量设置为1000，足够大多数场景使用
    private final BlockingQueue<TrainDispatchInfo> dispatchQueue = new LinkedBlockingQueue<>(1000);
    // 信号机管理
    private final Map<BlockPos, SignalInfo> signals = new ConcurrentHashMap<>();
    // 警报系统管理
    private final ConcurrentHashMap<String, List<AlertInfo>> activeAlerts = new ConcurrentHashMap<>(); // 按列车ID分组的活跃警报
    private final ConcurrentHashMap<String, TemporarySpeedLimit> temporarySpeedLimits = new ConcurrentHashMap<>(); // 临时限速信息
    private final ExecutorService alertProcessingPool = Executors.newFixedThreadPool(2); // 警报处理线程池
    // 供电系统引用
    private final PowerSupplySystem powerSupplySystem;
    
    // 线程池配置
    private final ExecutorService computationThreadPool;
    private final ExecutorService trainUpdateThreadPool;
    private final ScheduledExecutorService systemScheduler;
    
    // 批处理大小
    private static final int TRAIN_BATCH_SIZE = 5;
    // 系统更新频率（ms）
    private static final int SYSTEM_UPDATE_INTERVAL = 50;
    
    // 固定闭塞区间长度（方块数）
    private static final int BLOCK_SECTION_LENGTH = 50;
    // 安全距离（方块数）
    private static final int SAFE_DISTANCE = 20;
    // 移动闭塞安全包络距离（方块数）
    private static final int MOVING_BLOCK_SAFETY_DISTANCE = 30;
    // 最大同时允许的列车数量
    private static final int MAX_TRAINS_PER_LINE = 10;
    // 是否启用移动闭塞
    private boolean movingBlockEnabled = true;
    
    private CBTCSystem(World world) {
        this.world = world;
        this.powerSupplySystem = VehicleSystemInitializer.getPowerSupplySystem();
        initializeSections();
        
        // 初始化线程池 - 根据处理器核心数动态调整
        int processorCount = Runtime.getRuntime().availableProcessors();
        this.computationThreadPool = Executors.newFixedThreadPool(processorCount, r -> {
            Thread thread = new Thread(r, "CBTCComputationThread");
            thread.setPriority(Thread.NORM_PRIORITY);
            thread.setDaemon(true);
            return thread;
        });
        
        this.trainUpdateThreadPool = Executors.newFixedThreadPool(Math.max(2, processorCount / 2), r -> {
            Thread thread = new Thread(r, "TrainUpdateThread");
            thread.setPriority(Thread.NORM_PRIORITY);
            thread.setDaemon(true);
            return thread;
        });
        
        this.systemScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "CBTCSystemScheduler");
            thread.setPriority(Thread.MAX_PRIORITY);
            thread.setDaemon(true);
            return thread;
        });
        
        // 启动异步处理
        startAsyncProcessing();
    }
    
    // 启动异步处理
    private void startAsyncProcessing() {
        // 使用scheduleWithFixedDelay代替scheduleAtFixedRate，确保前一个任务完成后再执行下一个任务
        // 这样可以避免长时间运行的任务导致后续任务堆积和线程阻塞
        systemScheduler.scheduleWithFixedDelay(this::asyncSystemUpdate, 0, SYSTEM_UPDATE_INTERVAL, TimeUnit.MILLISECONDS);
    }
    
    // 安全关闭线程池
    public void shutdown() {
        int totalPendingTasks = 0;
        
        try {
            // 关闭警报处理线程池 - 优化为包含任务完成度日志和未完成任务保存
            if (alertProcessingPool != null && !alertProcessingPool.isTerminated()) {
                alertProcessingPool.shutdown();
                if (!alertProcessingPool.awaitTermination(2, TimeUnit.SECONDS)) {
                    List<Runnable> pendingTasks = alertProcessingPool.shutdownNow();
                    int count = pendingTasks.size();
                    totalPendingTasks += count;
                    if (count > 0) {
                        // 增加详细的任务完成度日志
                        LogSystem.error("未完成警报任务数: " + count + "，任务详情: " + pendingTasks);
                        // 保存未完成任务，以便系统重启后恢复
                        try {
                            savePendingAlerts(pendingTasks);
                            LogSystem.log("已保存未完成警报任务，系统重启后将尝试恢复处理");
                        } catch (Exception e) {
                            LogSystem.error("保存未完成警报任务失败: " + e.getMessage());
                        }
                    }
                }
            }
            // 关闭系统调度器
            if (systemScheduler != null && !systemScheduler.isTerminated()) {
                systemScheduler.shutdown();
                if (!systemScheduler.awaitTermination(3, TimeUnit.SECONDS)) {
                    List<Runnable> pendingTasks = systemScheduler.shutdownNow();
                    int count = pendingTasks.size();
                    totalPendingTasks += count;
                    if (count > 0) {
                        LogSystem.warning("CBTC系统调度器有 " + count + " 个任务未完成，已强制关闭");
                    }
                }
            }
            
            // 关闭计算线程池
            if (computationThreadPool != null && !computationThreadPool.isTerminated()) {
                computationThreadPool.shutdown();
                if (!computationThreadPool.awaitTermination(5, TimeUnit.SECONDS)) {
                    List<Runnable> pendingTasks = computationThreadPool.shutdownNow();
                    int count = pendingTasks.size();
                    totalPendingTasks += count;
                    if (count > 0) {
                        LogSystem.warning("CBTC系统计算线程池有 " + count + " 个任务未完成，已强制关闭");
                    }
                }
            }
            
            // 关闭列车更新线程池
            if (trainUpdateThreadPool != null && !trainUpdateThreadPool.isTerminated()) {
                trainUpdateThreadPool.shutdown();
                if (!trainUpdateThreadPool.awaitTermination(5, TimeUnit.SECONDS)) {
                    List<Runnable> pendingTasks = trainUpdateThreadPool.shutdownNow();
                    int count = pendingTasks.size();
                    totalPendingTasks += count;
                    if (count > 0) {
                        LogSystem.warning("CBTC系统列车更新线程池有 " + count + " 个任务未完成，已强制关闭");
                    }
                }
            }
            
            if (totalPendingTasks > 0) {
                LogSystem.warning("CBTC系统关闭时共有 " + totalPendingTasks + " 个任务未完成");
            }
            LogSystem.info("CBTC系统线程池已安全关闭");
            
        } catch (InterruptedException e) {
            LogSystem.error("CBTC系统关闭时被中断: " + e.getMessage());
            Thread.currentThread().interrupt();
            
            // 被中断时强制关闭所有线程池
            if (systemScheduler != null) {
                systemScheduler.shutdownNow();
            }
            if (computationThreadPool != null) {
                computationThreadPool.shutdownNow();
            }
            if (trainUpdateThreadPool != null) {
                trainUpdateThreadPool.shutdownNow();
            }
        }
    }
    
    /**
     * 保存未完成的警报任务，以便系统重启后恢复处理
     */
    private void savePendingAlerts(List<Runnable> pendingAlerts) {
        // 实际实现可能需要序列化任务信息并保存到文件
        // 这里提供一个基础框架，需要根据具体任务类型进行扩展
        Map<String, Object> pendingAlertsInfo = new HashMap<>();
        pendingAlertsInfo.put("timestamp", System.currentTimeMillis());
        pendingAlertsInfo.put("count", pendingAlerts.size());
        pendingAlertsInfo.put("world", world.getRegistryKey().getValue().toString());
        
        // 记录任务类型统计信息（如果需要更详细的信息，需要对任务类型进行定制）
        pendingAlertsInfo.put("task_types", pendingAlerts.stream()
                .map(task -> task.getClass().getSimpleName())
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting())));
        
        // TODO: 实现序列化保存到文件的逻辑
    }
    
    public static CBTCSystem getInstance(World world) {
        return INSTANCES.computeIfAbsent(world, CBTCSystem::new);
    }
    
    /**
     * 触发列车警报
     * @param trainId 列车ID
     * @param alertType 警报类型
     * @param alertMessage 警报消息
     * @param additionalInfo 附加信息（如故障代码、位置信息等）
     */
    public synchronized String triggerAlert(String trainId, AlertType alertType, String alertMessage, Map<String, Object> additionalInfo) {
        // 生成唯一警报ID
        String alertId = UUID.randomUUID().toString();
        AlertInfo alert = new AlertInfo(alertId, trainId, alertType, alertMessage, additionalInfo);
        
        // 将警报添加到活跃警报列表
        activeAlerts.computeIfAbsent(trainId, k -> new CopyOnWriteArrayList<>()).add(alert);
        
        // 异步处理警报（发出声光提示、通知控制中心等）
        alertProcessingPool.submit(() -> processAlert(alert));
        
        LogSystem.logInfo("Alert triggered: " + alertId + " - " + alertType + " - " + alertMessage);
        return alertId;
    }
    
    /**
     * 简化版本的触发警报方法
     */
    public String triggerAlert(String trainId, AlertType alertType, String alertMessage) {
        return triggerAlert(trainId, alertType, alertMessage, new HashMap<>());
    }
    
    /**
     * 触发包含玩家信息的警报（用于追溯误触责任）
     */
    public synchronized String triggerAlertWithPlayer(String trainId, AlertType alertType, String alertMessage, 
                                     Map<String, Object> additionalInfo, PlayerEntity player) {
        // 如果additionalInfo为null，创建新的map
        if (additionalInfo == null) {
            additionalInfo = new HashMap<>();
        }
        
        // 添加玩家信息
        additionalInfo.put("player_name", player.getName().getString());
        additionalInfo.put("player_uuid", player.getUuidAsString());
        
        // 调用主触发方法
        return triggerAlert(trainId, alertType, alertMessage, additionalInfo);
    }
    
    /**
     * 用于紧急解锁时记录触发玩家
     */
    public String triggerEmergencyUnlock(String trainId, BlockPos pos, PlayerEntity player) {
        String playerName = player.getName().getString();
        // 触发警报时携带玩家信息
        Map<String, Object> additionalInfo = new HashMap<>();
        additionalInfo.put("player", playerName);
        additionalInfo.put("player_uuid", player.getUuidAsString());
        additionalInfo.put("pos", pos.toString());
        additionalInfo.put("timestamp", System.currentTimeMillis());
        
        return triggerAlert(trainId, AlertType.EMERGENCY_UNLOCK, 
                   "玩家" + playerName + "触发紧急解锁", additionalInfo);
    }
    
    /**
     * 处理警报（异步执行）
     */
    private void processAlert(AlertInfo alert) {
        try {
            // 根据警报优先级执行不同级别的响应
            switch (alert.getAlertType().getPriority()) {
                case HIGH:
                    // 安全类警报 - 最高优先级处理
                    performHighPriorityResponse(alert);
                    break;
                case MEDIUM:
                    // 运营类警报 - 中等优先级处理
                    performMediumPriorityResponse(alert);
                    break;
                case LOW:
                    // 维护类警报 - 低优先级处理
                    performLowPriorityResponse(alert);
                    break;
            }
            
            // 向控制中心上报警报信息
            reportAlertToControlCenter(alert);
            
        } catch (Exception e) {
            LogSystem.logError("Failed to process alert: " + alert.getAlertId(), e);
        }
    }
    
    /**
     * 执行高优先级警报响应（安全类）
     */
    private void performHighPriorityResponse(AlertInfo alert) {
        TrainEntity train = getTrainById(alert.getTrainId());
        if (train != null) {
            // 1. 司机室声光预警 - 红色快速闪烁、高频蜂鸣
            triggerDriverAlert(train, AlertPriority.HIGH, alert.getAlertMessage());
            
            // 2. 自动锁闭牵引系统（防止误操作）
            if (alert.getAlertType() == AlertType.BRAKE_FAILURE || alert.getAlertType() == AlertType.TRACTION_SYSTEM_FAILURE) {
                train.emergencyDeceleration();
            }
            
            // 3. 车厢乘客广播通知
            broadcastToPassengers(train, "紧急情况：" + alert.getAlertMessage() + "。请乘客保持冷静，扶稳坐好。");
        }
    }
    
    /**
     * 执行中优先级警报响应（运营类）
     */
    private void performMediumPriorityResponse(AlertInfo alert) {
        TrainEntity train = getTrainById(alert.getTrainId());
        if (train != null) {
            // 1. 司机室提示 - 黄色指示灯、中等频率提示音
            triggerDriverAlert(train, AlertPriority.MEDIUM, alert.getAlertMessage());
            
            // 2. 必要时通知乘客
            if (alert.getAlertType() == AlertType.PASSENGER_EMERGENCY_CALL || alert.getAlertType() == AlertType.AIR_CONDITIONING_FAILURE) {
                broadcastToPassengers(train, "列车提示：" + alert.getAlertMessage());
            }
        }
    }
    
    /**
     * 执行低优先级警报响应（维护类）
     */
    private void performLowPriorityResponse(AlertInfo alert) {
        TrainEntity train = getTrainById(alert.getTrainId());
        if (train != null) {
            // 1. 司机室提示 - 绿色指示灯或仅显示信息
            triggerDriverAlert(train, AlertPriority.LOW, alert.getAlertMessage());
            
            // 低优先级警报通常不需要通知乘客
        }
    }
    
    /**
     * 触发司机室警报提示
     */
    private void triggerDriverAlert(TrainEntity train, AlertPriority priority, String message) {
        // 这里需要与列车实体的方法对接，实际实现需要根据TrainEntity类的具体方法来定
        // 模拟实现：显示警报信息到司机显示屏
        if (train != null) {
            train.displayAlertToDriver(message, priority);
        }
    }
    
    /**
     * 向车厢乘客广播信息
     */
    private void broadcastToPassengers(TrainEntity train, String message) {
        // 这里需要与列车实体的方法对接，实际实现需要根据TrainEntity类的具体方法来定
        // 模拟实现：播放广播给所有车厢
        if (train != null) {
            train.broadcastMessageToPassengers(message);
        }
    }
    
    /**
     * 向控制中心上报警报信息
     */
    private void reportAlertToControlCenter(AlertInfo alert) {
        // 模拟实现：记录日志，实际应用中可能需要通过网络通信上报
        LogSystem.logInfo("Reporting alert to control center: " + alert);
        
        // 这里可以添加与控制中心通信的代码
    }
    
    /**
     * 解除特定警报
     * @param alertId 警报ID
     * @return 是否成功解除
     */
    public synchronized boolean resolveAlert(String alertId) {
        for (List<AlertInfo> alerts : activeAlerts.values()) {
            for (AlertInfo alert : alerts) {
                if (alert.getAlertId().equals(alertId)) {
                    alert.setStatus(AlertStatus.RESOLVED);
                    alert.setResolveTime(new Date());
                    
                    // 从活跃警报列表中移除
                    alerts.remove(alert);
                    
                    // 如果该列车没有活跃警报了，从map中移除
                    if (alerts.isEmpty()) {
                        activeAlerts.remove(alert.getTrainId());
                    }
                    
                    LogSystem.logInfo("Alert resolved: " + alertId);
                    return true;
                }
            }
        }
        return false;
    }
    
    /**
     * 解除列车的所有警报
     * @param trainId 列车ID
     */
    public synchronized void resolveAllAlertsForTrain(String trainId) {
        List<AlertInfo> alerts = activeAlerts.remove(trainId);
        if (alerts != null) {
            for (AlertInfo alert : alerts) {
                alert.setStatus(AlertStatus.RESOLVED);
                alert.setResolveTime(new Date());
            }
            LogSystem.logInfo("Resolved all alerts for train: " + trainId);
        }
    }
    
    /**
     * 获取特定列车的所有活跃警报
     * @param trainId 列车ID
     * @return 活跃警报列表
     */
    public List<AlertInfo> getActiveAlertsForTrain(String trainId) {
        return activeAlerts.getOrDefault(trainId, Collections.emptyList());
    }
    
    /**
     * 获取所有列车的所有活跃警报
     * @return 所有活跃警报
     */
    public Map<String, List<AlertInfo>> getAllActiveAlerts() {
        return Collections.unmodifiableMap(new HashMap<>(activeAlerts));
    }
    
    /**
     * 根据ID获取列车实体
     * 这里是一个辅助方法，需要根据实际代码结构实现
     */
    private TrainEntity getTrainById(String trainId) {
        // 模拟实现，实际需要根据项目代码结构来查找列车实体
        // 在实际实现中，这里应该通过trainPositions或其他方式查找对应的列车实体
        return null; // 临时返回null，需要根据实际代码结构实现
    }
    
    // 初始化闭塞区间和信号机
    private void initializeSections() {
        // 扩大扫描范围，从(0,0,0)向外扫描更大区域
        final int SCAN_RANGE = 5000; // 扩大到5000格范围
        final int SCAN_HEIGHT = 30;  // 扩大垂直扫描范围到30格
        
        int newSectionsCreated = 0;
        int newSignalsFound = 0;
        
        // 扫描更大范围内的轨道，创建闭塞区间
        for (BlockPos pos : BlockPos.iterateOutwards(new BlockPos(0, 0, 0), SCAN_RANGE, SCAN_HEIGHT, SCAN_RANGE)) {
            // 检查轨道方块
            if (world.getBlockState(pos).getBlock() instanceof TrackBlock) {
                BlockPos sectionKey = new BlockPos(
                    (pos.getX() / BLOCK_SECTION_LENGTH) * BLOCK_SECTION_LENGTH,
                    pos.getY(),
                    (pos.getZ() / BLOCK_SECTION_LENGTH) * BLOCK_SECTION_LENGTH
                );
                
                if (!sections.containsKey(sectionKey)) {
                    sections.put(sectionKey, new BlockSection(sectionKey));
                    newSectionsCreated++;
                }
            } 
            // 扫描信号机
            else if (world.getBlockState(pos).getBlock() instanceof SignalBlock || 
                     world.getBlockState(pos).getBlock() instanceof ATPSignalBlock) {
                if (!signals.containsKey(pos)) {
                    signals.put(pos, new SignalInfo(pos));
                    newSignalsFound++;
                }
            }
        }
        
        LogSystem.info("CBTC系统初始化完成: 新增 " + newSectionsCreated + " 个闭塞区间, " + 
                      newSignalsFound + " 个信号机 (扫描范围: " + SCAN_RANGE + "x" + SCAN_HEIGHT + "x" + SCAN_RANGE + ")");
    }
    
    // 动态扫描并添加新铺设的轨道和信号机
    private void dynamicScanForNewTracks() {
        // 定义动态扫描的范围（可以根据需要调整）
        final int DYNAMIC_SCAN_RANGE = 1000;
        final int DYNAMIC_SCAN_HEIGHT = 30;
        
        int newSectionsCreated = 0;
        int newSignalsFound = 0;
        
        // 从所有列车位置向外扫描，这样可以动态发现新铺设的轨道
        Set<BlockPos> scannedPositions = new HashSet<>();
        
        // 首先扫描所有已发现列车周围的区域
        for (TrainPositionInfo posInfo : trainPositions.values()) {
            BlockPos trainBlockPos = new BlockPos(posInfo.position);
            for (BlockPos pos : BlockPos.iterateOutwards(trainBlockPos, DYNAMIC_SCAN_RANGE, DYNAMIC_SCAN_HEIGHT, DYNAMIC_SCAN_RANGE)) {
                if (scannedPositions.add(pos)) {
                    // 直接处理方块并更新计数
                    if (world.getBlockState(pos).getBlock() instanceof TrackBlock) {
                        BlockPos sectionKey = new BlockPos(
                            (pos.getX() / BLOCK_SECTION_LENGTH) * BLOCK_SECTION_LENGTH,
                            pos.getY(),
                            (pos.getZ() / BLOCK_SECTION_LENGTH) * BLOCK_SECTION_LENGTH
                        );
                        
                        if (!sections.containsKey(sectionKey)) {
                            sections.put(sectionKey, new BlockSection(sectionKey));
                            newSectionsCreated++;
                        }
                    } else if (world.getBlockState(pos).getBlock() instanceof SignalBlock || 
                              world.getBlockState(pos).getBlock() instanceof ATPSignalBlock) {
                        if (!signals.containsKey(pos)) {
                            signals.put(pos, new SignalInfo(pos));
                            newSignalsFound++;
                        }
                    }
                }
            }
        }
        
        // 然后扫描所有已发现信号机周围的区域
        for (SignalInfo signal : signals.values()) {
            for (BlockPos pos : BlockPos.iterateOutwards(signal.pos, DYNAMIC_SCAN_RANGE, DYNAMIC_SCAN_HEIGHT, DYNAMIC_SCAN_RANGE)) {
                if (scannedPositions.add(pos)) {
                    // 直接处理方块并更新计数
                    if (world.getBlockState(pos).getBlock() instanceof TrackBlock) {
                        BlockPos sectionKey = new BlockPos(
                            (pos.getX() / BLOCK_SECTION_LENGTH) * BLOCK_SECTION_LENGTH,
                            pos.getY(),
                            (pos.getZ() / BLOCK_SECTION_LENGTH) * BLOCK_SECTION_LENGTH
                        );
                        
                        if (!sections.containsKey(sectionKey)) {
                            sections.put(sectionKey, new BlockSection(sectionKey));
                            newSectionsCreated++;
                        }
                    } else if (world.getBlockState(pos).getBlock() instanceof SignalBlock || 
                              world.getBlockState(pos).getBlock() instanceof ATPSignalBlock) {
                        if (!signals.containsKey(pos)) {
                            signals.put(pos, new SignalInfo(pos));
                            newSignalsFound++;
                        }
                    }
                }
            }
        }
        
        // 记录动态发现的新元素
        if (newSectionsCreated > 0 || newSignalsFound > 0) {
            LogSystem.debug("CBTC系统动态扫描: 新增 " + newSectionsCreated + " 个闭塞区间, " + 
                          newSignalsFound + " 个信号机");
        }
    }
    
    // 更新CBTC系统 - 主线程调用的接口
    public void update() {
        // 在异步模式下，这里只做基本检查
        // 核心更新逻辑已移至异步线程
        if (!world.isClient() && world instanceof net.minecraft.server.world.ServerWorld) {
            // 确保异步处理已启动
            if (systemScheduler.isShutdown()) {
                startAsyncProcessing();
            }
        }
    }
    
    // 异步系统更新入口
    private void asyncSystemUpdate() {
        if (!world.isClient() && world instanceof net.minecraft.server.world.ServerWorld) {
            try {
                // 检查供电系统状态
                boolean powerNormal = checkPowerSupplyStatus();
                
                if (powerNormal) {
                    // 1. 异步更新列车位置
                    CompletableFuture.runAsync(this::asyncUpdateTrainPositions, trainUpdateThreadPool)
                        .thenRunAsync(this::asyncUpdateSections, computationThreadPool)
                        .thenRunAsync(this::asyncUpdateSignalStatus, computationThreadPool)
                        .thenRunAsync(this::asyncProcessDispatchQueue, computationThreadPool)
                        .thenRunAsync(this::asyncUpdateTrainATPData, computationThreadPool)
                        .thenRunAsync(this::asyncSendControlCommands, computationThreadPool)
                        .thenRunAsync(() -> {
                            // 定期清理过期数据
                            if (System.currentTimeMillis() % 30000 < SYSTEM_UPDATE_INTERVAL) { // 每30秒清理一次
                                cleanupExpiredData();
                            }
                            
                            // 定期执行动态扫描，发现新铺设的轨道和信号机
                            // 每10秒执行一次动态扫描，减少性能消耗
                            if (System.currentTimeMillis() % 10000 < SYSTEM_UPDATE_INTERVAL) {
                                dynamicScanForNewTracks();
                            }
                        }, computationThreadPool)
                        .exceptionally(ex -> {
                            LogSystem.error("CBTC系统异步更新失败: " + ex.getMessage());
                            return null;
                        });
                }
            } catch (Exception e) {
                LogSystem.error("CBTC系统调度失败: " + e.getMessage());
            }
        }
    }
    
    /**
     * 检查供电系统状态
     * @return 如果供电正常返回true，否则返回false
     */
    private boolean checkPowerSupplyStatus() {
        if (powerSupplySystem != null) {
            PowerSupplySystem.PowerStatus status = powerSupplySystem.getSystemStatus();
            
            if (status == PowerSupplySystem.PowerStatus.ERROR || status == PowerSupplySystem.PowerStatus.OUTAGE) {
                LogSystem.warning("CBTC系统检测到供电异常: " + status.name());
                // 供电异常时，将所有信号机设为红灯并触发紧急制动
                emergencyStopAllTrains();
                setAllSignalsToRed();
                return false;
            } else if (status == PowerSupplySystem.PowerStatus.WARNING) {
                LogSystem.warning("CBTC系统检测到供电警告: " + status.name());
                // 供电警告时，限制列车速度
                limitTrainSpeeds(0.5); // 降低到50%速度
            } else if (status == PowerSupplySystem.PowerStatus.NORMAL) {
                // 检查是否需要恢复正常运行
                checkAndRestoreNormalOperation();
            }
        }
        return true;
    }
    
    /**
     * 检查并恢复系统正常运行状态
     */
    private void checkAndRestoreNormalOperation() {
        // 检查ATP系统是否处于紧急模式
        ATP atp = ATC.getInstance(world).getATP();
        
        // 如果ATP之前因为供电问题进入紧急模式，现在可以恢复
        // 注意：这里我们不能直接恢复，而是通过ATC系统的正常流程来恢复
        LogSystem.debug("CBTC系统：供电恢复正常，系统正在评估恢复运行条件");
        
        // 通知故障安全系统检查是否可以恢复正常模式
        ATC.getInstance(world).restoreNormalMode();
    }
    
    /**
     * 紧急停止所有列车
     */
    private void emergencyStopAllTrains() {
        for (String trainId : trainPositions.keySet()) {
            try {
                Entity entity = world.getEntity(UUID.fromString(trainId));
                if (entity instanceof TrainEntity) {
                    TrainEntity train = (TrainEntity) entity;
                    train.applyEmergencyBrake();
                    LogSystem.warning("对列车 " + trainId + " 实施紧急制动");
                }
            } catch (Exception e) {
                LogSystem.error("对列车 " + trainId + " 实施紧急制动失败: " + e.getMessage());
            }
        }
    }
    
    /**
     * 将所有信号机设为红灯
     */
    private void setAllSignalsToRed() {
        for (BlockPos pos : signals.keySet()) {
            try {
                BlockState state = world.getBlockState(pos);
                if (state.getBlock() instanceof SignalBlock) {
                    SignalBlock.updateSignalState(world, pos, SignalBlock.SignalState.RED);
                } else if (state.getBlock() instanceof ATPSignalBlock) {
                    ATPSignalBlock.updateSignalState(world, pos, SignalBlock.SignalState.RED);
                }
            } catch (Exception e) {
                LogSystem.error("设置信号机 " + pos + " 为红灯失败: " + e.getMessage());
            }
        }
    }
    
    /**
     * 限制所有列车速度
     * @param speedFactor 速度系数 (0.1-1.0)
     */
    private void limitTrainSpeeds(double speedFactor) {
        for (String trainId : trainPositions.keySet()) {
            try {
                Entity entity = world.getEntity(UUID.fromString(trainId));
                if (entity instanceof TrainEntity) {
                    TrainEntity train = (TrainEntity) entity;
                    double currentMaxSpeed = train.getMaxSpeed();
                    double newMaxSpeed = currentMaxSpeed * speedFactor;
                    train.setMaxSpeed(newMaxSpeed);
                    LogSystem.debug("限制列车 " + trainId + " 速度至: " + newMaxSpeed + " m/s");
                }
            } catch (Exception e) {
                LogSystem.error("限制列车 " + trainId + " 速度失败: " + e.getMessage());
            }
        }
    }
    
    // 清理过期数据
    private void cleanupExpiredData() {
        try {
            // 移除不存在的信号机
            List<BlockPos> toRemove = new ArrayList<>();
            for (BlockPos pos : signals.keySet()) {
                if (world.isClient || !(world.getBlockState(pos).getBlock() instanceof SignalBlock || 
                    world.getBlockState(pos).getBlock() instanceof ATPSignalBlock)) {
                    toRemove.add(pos);
                }
            }
            
            for (BlockPos pos : toRemove) {
                signals.remove(pos);
            }
            
            // 移除不存在的列车位置信息
            trainPositions.entrySet().removeIf(entry -> {
                String trainId = entry.getKey();
                return world.getEntity(UUID.fromString(trainId)) == null;
            });
        } catch (Exception e) {
            LogSystem.error("清理过期数据失败: " + e.getMessage());
        }
    }
    
    // 异步更新列车位置信息
    private void asyncUpdateTrainPositions() {
        try {
            List<TrainEntity> trains = world.getEntitiesByClass(TrainEntity.class, 
                    new net.minecraft.util.math.Box(-30000000, -64, -30000000, 30000000, 320, 30000000), 
                    entity -> entity instanceof TrainEntity);
            
            // 批量处理列车更新，减少线程调度开销
            for (int i = 0; i < trains.size(); i += TRAIN_BATCH_SIZE) {
                final int startIndex = i;
                final int endIndex = Math.min(i + TRAIN_BATCH_SIZE, trains.size());
                
                CompletableFuture.runAsync(() -> {
                    for (int j = startIndex; j < endIndex; j++) {
                        TrainEntity train = trains.get(j);
                        String trainId = train.getUuidAsString();
                        
                        TrainPositionInfo posInfo = trainPositions.computeIfAbsent(trainId, 
                                id -> new TrainPositionInfo(trainId));
                        
                        // 更新列车位置信息
                        posInfo.update(train);
                    }
                }, trainUpdateThreadPool);
            }
            
            // 清理不存在的列车
            trainPositions.entrySet().removeIf(entry -> {
                String trainId = entry.getKey();
                return world.getEntity(UUID.fromString(trainId)) == null;
            });
        } catch (Exception e) {
            KRTMod.LOGGER.error("异步更新列车位置失败: " + e.getMessage());
        }
    }
    
    // 异步更新闭塞区间状态
    private void asyncUpdateSections() {
        try {
            // 并行更新所有闭塞区间
            List<CompletableFuture<Void>> futures = sections.values().stream()
                .map(section -> CompletableFuture.runAsync(() -> {
                    try {
                        section.clearTrainIds();
                    } catch (Exception e) {
                        // 单个区间更新失败不影响整体
                    }
                }, computationThreadPool))
                .collect(Collectors.toList());
            
            // 等待所有区间清空完成
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
            
            // 为每个区间分配列车
            for (TrainPositionInfo posInfo : trainPositions.values()) {
                try {
                    BlockPos sectionKey = new BlockPos(
                        (int)(posInfo.position.getX() / BLOCK_SECTION_LENGTH) * BLOCK_SECTION_LENGTH,
                        posInfo.position.getY(),
                        (int)(posInfo.position.getZ() / BLOCK_SECTION_LENGTH) * BLOCK_SECTION_LENGTH
                    );
                    
                    BlockSection section = sections.get(sectionKey);
                    if (section != null) {
                        section.addTrainId(posInfo.trainId);
                    }
                } catch (Exception e) {
                    // 单个列车区间分配失败不影响整体
                }
            }
        } catch (Exception e) {
            KRTMod.LOGGER.error("异步更新闭塞区间失败: " + e.getMessage());
        }
    }
    
    // 异步更新信号机状态
    private void asyncUpdateSignalStatus() {
        try {
            // 批量处理信号机更新
            List<SignalInfo> signalList = new ArrayList<>(signals.values());
            for (int i = 0; i < signalList.size(); i += 10) {
                final int startIndex = i;
                final int endIndex = Math.min(i + 10, signalList.size());
                
                CompletableFuture.runAsync(() -> {
                    for (int j = startIndex; j < endIndex; j++) {
                        try {
                            SignalInfo signal = signalList.get(j);
                            Block block = world.getBlockState(signal.pos).getBlock();
                            
                            // 精确计算前方空闲闭塞分区数量
                            int freeSections = calculateFreeSectionsAhead(signal.pos);
                            
                            // 根据信号机类型调整信号显示策略
                            SignalDisplay display = determineSignalDisplay(signal, freeSections);
                            signal.setDisplay(display);
                            
                            // 更新信号机方块实体状态
                            if (block instanceof SignalBlock || block instanceof ATPSignalBlock) {
                                BlockEntity blockEntity = world.getBlockEntity(signal.pos);
                                if (blockEntity instanceof SignalBlockEntity) {
                                    SignalBlockEntity entity = (SignalBlockEntity) blockEntity;
                                    // 设置四显示信号状态
                                    SignalBlock.SignalState state = convertToSignalBlockState(display);
                                    entity.updateSignalState(state);
                                } else if (blockEntity instanceof ATPSignalBlockEntity) {
                                    ATPSignalBlockEntity entity = (ATPSignalBlockEntity) blockEntity;
                                    // 更新ATP信号机状态和ATP数据
                                    updateATPSignalBlockEntity(entity, display, freeSections);
                                }
                            }
                            
                            // 记录信号状态变化
                            LogSystem.debug("CBTC: 信号机 " + signal.pos + " 更新为 " + display + ", 前方空闲分区: " + freeSections);
                        } catch (Exception e) {
                            LogSystem.error("更新信号机 " + signalList.get(j).pos + " 失败: " + e.getMessage());
                        }
                    }
                }, computationThreadPool);
            }
        } catch (Exception e) {
            LogSystem.error("异步更新信号机状态失败: " + e.getMessage());
        }
    }
    
    // 计算前方空闲闭塞分区数量
    /**
     * 获取轨道方块的走向
     * @param pos 轨道方块位置
     * @return 轨道走向（X轴或Z轴方向）
     */
    private Direction getTrackDirection(BlockPos pos) {
        // 检查轨道方块类型，这里简化实现
        BlockState state = world.getBlockState(pos);
        Block block = state.getBlock();
        
        // 实际实现中，需要根据轨道方块的具体状态获取方向
        // 例如，对于标准轨道可能返回X轴或Z轴方向
        // 这里提供一个基本实现，可以根据实际情况扩展
        if (block instanceof TrackBlock) {
            TrackBlock trackBlock = (TrackBlock) block;
            // 假设轨道方块有获取方向的方法
            // 这里需要根据实际的轨道方块实现进行调整
            return trackBlock.getFacing(state);
        }
        
        // 默认返回X轴正方向
        return Direction.EAST;
    }
    
    /**
     * 沿轨道走向计算前方空闲闭塞区间数量
     * @param signalPos 信号机位置
     * @return 前方空闲区间数量
     */
    private int calculateFreeSectionsAhead(BlockPos signalPos) {
        // 如果启用了移动闭塞，使用移动闭塞算法
        if (MOBILE_BLOCK_ENABLED) {
            return calculateMobileBlockFreeSections(signalPos);
        }
        
        // 传统固定闭塞算法
        int freeCount = 0;
        
        // 获取信号机对应的轨道走向
        Direction trackDir = getTrackDirection(signalPos);
        
        // 计算信号机前方相邻的闭塞区间
        BlockPos currentPos = signalPos.offset(trackDir, BLOCK_SECTION_LENGTH);
        
        // 最多检查3个前方区间（对应四显示信号的需求）
        while (freeCount < 3) {
            // 转换为区间位置（取区段中心点）
            BlockPos sectionPos = new BlockPos(
                (currentPos.getX() / BLOCK_SECTION_LENGTH) * BLOCK_SECTION_LENGTH + BLOCK_SECTION_LENGTH / 2,
                currentPos.getY(),
                (currentPos.getZ() / BLOCK_SECTION_LENGTH) * BLOCK_SECTION_LENGTH + BLOCK_SECTION_LENGTH / 2
            );
            
            BlockSection section = sections.get(sectionPos);
            // 如果区间存在且空闲，则计数增加
            if (section != null && section.getStatus() == BlockSectionStatus.FREE) {
                freeCount++;
            } else {
                // 一旦遇到非空闲区间，停止计数
                break;
            }
            
            // 沿轨道走向移动到下一个区间位置
            currentPos = currentPos.offset(trackDir, BLOCK_SECTION_LENGTH);
        }
        
        return freeCount;
    }
    
    // 移动闭塞算法：计算前方空闲区间
    private int calculateMobileBlockFreeSections(BlockPos signalPos) {
        Direction trackDir = getTrackDirection(signalPos);
        Vec3d signalVec = new Vec3d(signalPos.getX() + 0.5, signalPos.getY(), signalPos.getZ() + 0.5);
        
        // 模拟前方最大可检测距离（3个传统闭塞分区）
        double maxDetectionDistance = BLOCK_SECTION_LENGTH * 3;
        
        // 检查前方是否有列车的移动闭塞区域
        for (Map.Entry<String, MobileBlockInfo> entry : mobileBlockMap.entrySet()) {
            MobileBlockInfo blockInfo = entry.getValue();
            if (blockInfo.isActive && blockInfo.frontPosition != null) {
                Vec3d trainFrontVec = new Vec3d(blockInfo.frontPosition.getX() + 0.5, 
                                              blockInfo.frontPosition.getY(), 
                                              blockInfo.frontPosition.getZ() + 0.5);
                
                // 计算沿轨道方向的距离
                Vec3d toTrain = trainFrontVec.subtract(signalVec);
                double distanceAlongTrack = toTrain.dotProduct(new Vec3d(trackDir.getX(), 0, trackDir.getZ()).normalize());
                
                // 如果列车在信号机前方且在检测范围内
                if (distanceAlongTrack > 0 && distanceAlongTrack < maxDetectionDistance) {
                    // 根据距离计算空闲分区数量
                    double availableDistance = distanceAlongTrack - SAFETY_DISTANCE;
                    if (availableDistance <= 0) return 0; // 无空闲区间
                    
                    // 将可用距离转换为空闲分区数
                    return (int)Math.min(3, Math.floor(availableDistance / BLOCK_SECTION_LENGTH));
                }
            }
        }
        
        // 前方没有列车的移动闭塞区域，返回最大空闲分区数
        return 3;
    }
    
    // 更新列车的移动闭塞信息
    public void updateTrainMobileBlock(String trainId, BlockPos frontPos, double trainLength, double currentSpeed) {
        if (!MOBILE_BLOCK_ENABLED) return;
        
        MobileBlockInfo blockInfo = mobileBlockMap.computeIfAbsent(trainId, MobileBlockInfo::new);
        blockInfo.update(frontPos, trainLength, currentSpeed);
        
        // 更新闭塞区间状态（预留移动闭塞区域）
        updateBlockSectionsForMobileBlock(trainId, blockInfo);
    }
    
    // 根据移动闭塞更新闭塞区间状态
    private void updateBlockSectionsForMobileBlock(String trainId, MobileBlockInfo blockInfo) {
        if (!blockInfo.isActive || blockInfo.frontPosition == null) return;
        
        // 获取列车行驶方向（简化版）
        Direction trackDir = Direction.NORTH; // 需要根据实际轨道走向计算
        
        // 计算移动闭塞区域的起点（列车前方）
        BlockPos startPos = blockInfo.frontPosition.offset(trackDir.getOpposite(), 
                                                          (int)(blockInfo.requiredBlockLength / 2));
        BlockPos endPos = blockInfo.frontPosition.offset(trackDir, 
                                                        (int)(blockInfo.requiredBlockLength / 2));
        
        // 更新该区域内的闭塞区间状态为预留
        for (BlockPos pos : BlockPos.iterate(startPos, endPos)) {
            BlockPos sectionKey = new BlockPos(
                (pos.getX() / BLOCK_SECTION_LENGTH) * BLOCK_SECTION_LENGTH,
                pos.getY(),
                (pos.getZ() / BLOCK_SECTION_LENGTH) * BLOCK_SECTION_LENGTH
            );
            
            BlockSection section = sections.get(sectionKey);
            if (section != null && section.getStatus() == BlockSectionStatus.FREE) {
                // 标记为预留状态（通过特殊的列车ID格式）
                section.addTrainId("RESERVED_" + trainId);
            }
        }
    }
    
    // 根据信号机类型和前方空闲分区确定信号显示
    private SignalDisplay determineSignalDisplay(SignalInfo signal, int freeSections) {
        // 获取相关的额外信息
        boolean hasTrainApproaching = signal.isApproaching();
        String lineId = signal.getAssociatedLineId();
        boolean isExpressLine = isExpressLine(lineId);
        
        // 根据信号机类型应用不同的显示策略
        switch (signal.getType()) {
            case ENTRY:
                // 进站信号机增强逻辑
                if (freeSections >= 3) {
                    // 如果前方多个站台空闲且是快速线路，显示绿灯
                    return SignalDisplay.GREEN;
                } else if (freeSections == 2) {
                    // 前方两个闭塞分区空闲，显示双黄灯
                    return SignalDisplay.DOUBLE_YELLOW;
                } else if (freeSections == 1) {
                    // 前方一个闭塞分区空闲，显示黄灯
                    return SignalDisplay.YELLOW;
                } else {
                    // 前方无空闲分区，显示红灯
                    return SignalDisplay.RED;
                }
            case EXIT:
                // 出站信号机增强逻辑
                if (freeSections >= 4 && isExpressLine) {
                    // 快速线路且前方多个空闲分区，显示闪绿灯
                    return SignalDisplay.FLASHING_GREEN;
                } else if (freeSections >= 3) {
                    // 前方三个及以上空闲分区，显示绿灯
                    return SignalDisplay.GREEN;
                } else if (freeSections == 2) {
                    // 前方两个空闲分区，显示绿黄灯
                    return SignalDisplay.YELLOW_GREEN;
                } else if (freeSections == 1) {
                    // 前方一个空闲分区，显示黄灯
                    return SignalDisplay.YELLOW;
                } else {
                    // 前方无空闲分区，显示红灯
                    return SignalDisplay.RED;
                }
            case SHUNTING:
                // 调车信号机使用专用显示
                if (freeSections >= 1) {
                    // 前方有空闲区域，显示白灯
                    return SignalDisplay.WHITE;
                } else {
                    // 前方无空闲区域，显示蓝灯
                    return SignalDisplay.BLUE;
                }
            case BLOCK:
                // 闭塞信号机精细化显示
                if (freeSections >= 3) {
                    return SignalDisplay.GREEN;
                } else if (freeSections == 2) {
                    return SignalDisplay.YELLOW_GREEN;
                } else if (freeSections == 1) {
                    return SignalDisplay.YELLOW;
                } else {
                    return SignalDisplay.RED;
                }
            case MAIN:
                // 主信号机综合考虑多种因素
                if (freeSections >= 4) {
                    // 前方多个分区空闲且无特殊情况，显示绿灯
                    return SignalDisplay.GREEN;
                } else if (freeSections == 3) {
                    return SignalDisplay.YELLOW_GREEN;
                } else if (freeSections == 2) {
                    return SignalDisplay.YELLOW;
                } else if (freeSections == 1) {
                    // 检查前方是否有道岔或特殊区段
                    if (hasSwitchOrSpecialSectionAhead(signal.getPos())) {
                        return SignalDisplay.FLASHING_YELLOW;
                    } else {
                        return SignalDisplay.YELLOW;
                    }
                } else {
                    return SignalDisplay.RED;
                }
            case REPEATING:
                // 复示信号机反映其复示的主体信号机状态
                SignalInfo mainSignal = findMainSignalForRepeating(signal);
                if (mainSignal != null) {
                    int mainFreeSections = calculateFreeSectionsAhead(mainSignal.getPos());
                    return determineSignalDisplayForMain(mainFreeSections);
                }
                return SignalDisplay.RED;
            case DISTANT:
                // 预告信号机提前预告进站信号机状态
                SignalInfo entrySignal = findNearestEntrySignalAhead(signal.getPos());
                if (entrySignal != null) {
                    int entryFreeSections = calculateFreeSectionsAhead(entrySignal.getPos());
                    // 预告信号机的显示比进站信号机提前一级
                    if (entryFreeSections >= 2) {
                        return SignalDisplay.GREEN;
                    } else if (entryFreeSections == 1) {
                        return SignalDisplay.YELLOW;
                    } else {
                        return SignalDisplay.DOUBLE_YELLOW;
                    }
                }
                return SignalDisplay.GREEN;
            case DIVERGING:
                // 分歧信号机专用逻辑
                if (freeSections >= 2) {
                    // 分歧线路允许通过，显示闪黄灯
                    return SignalDisplay.FLASHING_YELLOW;
                } else {
                    return SignalDisplay.RED;
                }
            case COMBINED:
                // 组合信号机，同时具备主体和调车功能
                if (isShuntingOperationActive()) {
                    // 调车作业时，使用调车信号逻辑
                    return freeSections >= 1 ? SignalDisplay.YELLOW_FLASHING_YELLOW : SignalDisplay.BLUE;
                } else {
                    // 正常运行时，使用主体信号逻辑
                    return determineSignalDisplayForMain(freeSections);
                }
            case EXPRESS_PASSING:
                // 快速通过信号机
                if (freeSections >= 4 && isExpressLine) {
                    return SignalDisplay.FLASHING_GREEN;
                } else if (freeSections >= 2) {
                    return SignalDisplay.GREEN;
                } else {
                    return SignalDisplay.RED;
                }
            case EMERGENCY_STOP:
                // 紧急停车信号机始终显示红灯
                return SignalDisplay.RED;
            default:
                // 默认使用标准四显示逻辑
                return determineSignalDisplayForMain(freeSections);
        }
    }
    
    // 辅助方法：为主信号机确定显示状态
    private SignalDisplay determineSignalDisplayForMain(int freeSections) {
        if (freeSections >= 3) {
            return SignalDisplay.GREEN;
        } else if (freeSections == 2) {
            return SignalDisplay.YELLOW_GREEN;
        } else if (freeSections == 1) {
            return SignalDisplay.YELLOW;
        } else {
            return SignalDisplay.RED;
        }
    }
    
    // 检查是否为快速线路
    private boolean isExpressLine(String lineId) {
        if (lineId == null || lineId.isEmpty()) {
            return false;
        }
        // 可以从线路数据库或配置中获取线路类型信息
        // 这里简化处理，假设包含"express"的线路ID为快速线路
        return lineId.toLowerCase().contains("express") || lineId.toLowerCase().contains("fast");
    }
    
    // 检查前方是否有道岔或特殊区段
    private boolean hasSwitchOrSpecialSectionAhead(BlockPos pos) {
        // 搜索前方一定范围内的道岔和特殊区段
        for (int i = 1; i <= 20; i++) { // 搜索前方20个方块
            BlockPos checkPos = pos.east(i); // 简化为东方，实际应考虑信号机朝向
            if (world.getBlockState(checkPos).getBlock() instanceof SwitchBlock) {
                return true;
            }
        }
        return false;
    }
    
    // 查找复示信号机对应的主信号机
    private SignalInfo findMainSignalForRepeating(SignalInfo repeatingSignal) {
        // 简化实现：查找复示信号机后方的主信号机
        BlockPos pos = repeatingSignal.getPos();
        
        for (SignalInfo signal : signals.values()) {
            if (signal.getType() == SignalType.MAIN && 
                signal.getPos().getX() < pos.getX() && // 简化判断，假设信号机朝向东方
                signal.getPos().distanceSq(pos) < 200 * 200) { // 200米范围内
                return signal;
            }
        }
        
        return null;
    }
    
    // 查找最近的进站信号机
    private SignalInfo findNearestEntrySignalAhead(BlockPos pos) {
        SignalInfo nearestEntry = null;
        double nearestDistance = Double.MAX_VALUE;
        
        for (SignalInfo signal : signals.values()) {
            if (signal.getType() == SignalType.ENTRY) {
                double distance = signal.getPos().distanceSq(pos);
                // 简化判断，只考虑东方的信号机
                if (signal.getPos().getX() > pos.getX() && distance < nearestDistance) {
                    nearestDistance = distance;
                    nearestEntry = signal;
                }
            }
        }
        
        return nearestEntry;
    }
    
    // 检查是否有调车作业正在进行
    private boolean isShuntingOperationActive() {
        // 检查是否有列车处于调车模式
        for (TrainEntity train : trains.values()) {
            if (train.getControlSystem().getControlMode() == TrainControlSystem.TrainControlMode.RM ||
                train.getControlSystem().getControlMode() == TrainControlSystem.TrainControlMode.URM) {
                return true;
            }
        }
        return false;
    }
        }
    }
    
    // 转换CBTC信号显示到SignalBlock状态
    private SignalBlock.SignalState convertToSignalBlockState(SignalDisplay display) {
        switch (display) {
            case RED:
                return SignalBlock.SignalState.RED;
            case YELLOW:
                return SignalBlock.SignalState.YELLOW;
            case YELLOW_GREEN:
                return SignalBlock.SignalState.YELLOW_GREEN;
            case GREEN:
                return SignalBlock.SignalState.GREEN;
            default:
                return SignalBlock.SignalState.RED;
        }
    }
    
    // 更新ATPSignalBlockEntity状态和ATP数据
    private void updateATPSignalBlockEntity(ATPSignalBlockEntity entity, SignalDisplay display, int freeSections) {
        // 更新信号状态
        ATPSignalBlockEntity.SignalStatus signalStatus = convertToATPSignalStatus(display);
        entity.updateSignalState(signalStatus);
        
        // 更新空闲闭塞分区数量
        entity.updateFreeBlockSections(freeSections);
        
        // 根据信号显示计算并设置最大速度
        double maxSpeed = calculateMaxSpeedForSignal(display);
        entity.setMaxSpeed(maxSpeed);
        
        // 设置进路状态（简化逻辑）
        entity.updateRouteClear(freeSections > 0);
    }
    
    // 转换CBTC信号显示到ATPSignalBlockEntity信号状态
    private ATPSignalBlockEntity.SignalStatus convertToATPSignalStatus(SignalDisplay display) {
        switch (display) {
            case RED:
                return ATPSignalBlockEntity.SignalStatus.RED;
            case YELLOW:
                return ATPSignalBlockEntity.SignalStatus.YELLOW;
            case YELLOW_GREEN:
                return ATPSignalBlockEntity.SignalStatus.YELLOW_GREEN;
            case GREEN:
                return ATPSignalBlockEntity.SignalStatus.GREEN;
            default:
                return ATPSignalBlockEntity.SignalStatus.RED;
        }
    }
    
    // 根据信号显示计算最大允许速度
    private double calculateMaxSpeedForSignal(SignalDisplay display) {
        switch (display) {
            case GREEN:
                return 80.0;  // 绿灯全速80km/h
            case YELLOW_GREEN:
                return 50.0;  // 绿黄灯减速至50km/h
            case YELLOW:
                return 20.0;  // 黄灯减速至20km/h
            case RED:
                return 0.0;   // 红灯停车
            default:
                return 0.0;
        }
    }
    
    // 异步处理调度队列
    private void asyncProcessDispatchQueue() {
        try {
            // 处理调度队列中的任务
            TrainDispatchInfo dispatchInfo;
            while ((dispatchInfo = dispatchQueue.poll()) != null) {
                try {
                    String trainId = dispatchInfo.trainId;
                    
                    // 检查是否可以安全调度
                    if (canDispatchTrain(trainId, dispatchInfo.targetPosition)) {
                        // 向列车发送调度指令
                        Entity entity = world.getEntity(UUID.fromString(trainId));
                        if (entity instanceof TrainEntity) {
                            TrainEntity train = (TrainEntity) entity;
                            train.setDestination(dispatchInfo.targetStation);
                            train.setNextStation(dispatchInfo.nextStation);
                            train.getControlSystem().setTargetSpeed(dispatchInfo.targetSpeed);
                        }
                    }
                } catch (Exception e) {
                    // 单个调度任务失败不影响整体
                }
            }
        } catch (Exception e) {
            KRTMod.LOGGER.error("异步处理调度队列失败: " + e.getMessage());
        }
    }
    
    // 异步向列车发送控制指令
    private void asyncSendControlCommands() {
        try {
            // 分批发送指令，减少网络阻塞
            List<TrainPositionInfo> positionInfoList = new ArrayList<>(trainPositions.values());
            
            for (int i = 0; i < positionInfoList.size(); i += TRAIN_BATCH_SIZE) {
                final int startIndex = i;
                final int endIndex = Math.min(i + TRAIN_BATCH_SIZE, positionInfoList.size());
                
                CompletableFuture.runAsync(() -> {
                    for (int j = startIndex; j < endIndex; j++) {
                        try {
                            TrainPositionInfo posInfo = positionInfoList.get(j);
                            Entity entity = world.getEntity(UUID.fromString(posInfo.trainId));
                            if (entity instanceof TrainEntity) {
                                TrainEntity train = (TrainEntity) entity;
                                
                                // 获取最近的信号机
                                BlockPos nearestSignalPos = findNearestSignalAhead(train);
                                SignalInfo nearestSignal = nearestSignalPos != null ? signals.get(nearestSignalPos) : null;
                                
                                // 计算安全速度（结合信号状态）
                                double safeSpeed = calculateSafeSpeed(posInfo, nearestSignal);
                                
                                // 获取当前列车速度
                                double currentSpeed = train.getCurrentSpeed();
                                
                                if (train.isATPEnabled()) {
                                    // 根据信号状态和速度差异采取不同级别的制动
                                    if (nearestSignal != null) {
                                        switch (nearestSignal.getDisplay()) {
                                            case RED:
                                                // 红灯：紧急制动
                                                if (currentSpeed > 0) {
                                                    train.getControlSystem().applyEmergencyBrake();
                                                    if (train.getDriver() != null) {
                                                        train.getDriver().sendMessage(Text.literal("ATP紧急制动: 前方红灯，请停车！"), false);
                                                    }
                                                    LogSystem.warning("ATP紧急制动: 列车 " + posInfo.trainId + " 前方红灯");
                                                }
                                                break;
                                            case YELLOW:
                                                // 黄灯：减速至20km/h以下
                                                if (currentSpeed > 20) {
                                                    train.getControlSystem().applyServiceBrake();
                                                    if (train.getDriver() != null) {
                                                        train.getDriver().sendMessage(Text.literal("ATP减速: 前方黄灯，限速20km/h"), false);
                                                    }
                                                }
                                                break;
                                            case YELLOW_GREEN:
                                                // 绿黄灯：减速至50km/h以下
                                                if (currentSpeed > 50) {
                                                    train.getControlSystem().applyLightBrake();
                                                    if (train.getDriver() != null) {
                                                        train.getDriver().sendMessage(Text.literal("ATP减速: 前方绿黄灯，限速50km/h"), false);
                                                    }
                                                }
                                                break;
                                            case GREEN:
                                                // 绿灯：可全速运行，但仍需保持安全距离
                                                if (currentSpeed > safeSpeed) {
                                                    train.getControlSystem().applyLightBrake();
                                                    if (train.getDriver() != null) {
                                                        train.getDriver().sendMessage(Text.literal("ATP减速: 前方列车接近，请注意控制速度！"), false);
                                                    }
                                                }
                                                break;
                                        }
                                    } else {
                                        // 无信号机时，仅根据安全距离控制
                                        if (currentSpeed > safeSpeed) {
                                            train.getControlSystem().applyServiceBrake();
                                            if (train.getDriver() != null) {
                                                train.getDriver().sendMessage(Text.literal("ATP减速: 前方有障碍物，请减速！"), false);
                                            }
                                        }
                                    }
                                }
                            }
                        } catch (Exception e) {
                            LogSystem.error("向列车发送控制指令失败: " + e.getMessage());
                        }
                    }
                }, computationThreadPool);
            }
        } catch (Exception e) {
            LogSystem.error("异步发送控制指令失败: " + e.getMessage());
        }
    }
    
    // 计算安全速度（考虑信号状态）
    private double calculateSafeSpeed(TrainPositionInfo posInfo, SignalInfo nearestSignal) {
        // 计算与前方列车的最小距离
        double minDistance = calculateMinimumDistanceToOtherTrains(posInfo);
        
        // 如果有前方信号机，优先考虑信号机状态
        if (nearestSignal != null) {
            double signalSpeedLimit = calculateMaxSpeedForSignal(nearestSignal.getDisplay());
            
            // 根据信号状态和距离综合计算安全速度
            if (nearestSignal.getDisplay() == SignalDisplay.RED) {
                return 0; // 红灯必须停车
            } else if (nearestSignal.getDisplay() == SignalDisplay.YELLOW) {
                // 黄灯时，确保有足够的制动距离
                return Math.min(20.0, calculateSpeedBasedOnDistance(minDistance));
            } else if (nearestSignal.getDisplay() == SignalDisplay.YELLOW_GREEN) {
                // 绿黄灯时，考虑前方有两个空闲区间
                return Math.min(50.0, calculateSpeedBasedOnDistance(minDistance));
            }
        }
        
        // 传统固定闭塞的安全速度计算
        return calculateSpeedBasedOnDistance(minDistance);
    }
    
    // 根据距离计算安全速度
    private double calculateSpeedBasedOnDistance(double distance) {
        // 根据距离线性计算安全速度（更精确的计算应考虑制动曲线）
        if (distance < SAFE_DISTANCE * 2) {
            return 0; // 需要停车
        } else if (distance < SAFE_DISTANCE * 4) {
            return 20; // 低速
        } else if (distance < SAFE_DISTANCE * 6) {
            return 40; // 中速
        } else if (distance < SAFE_DISTANCE * 8) {
            return 60; // 高速
        }
        
        return 80; // 正常速度
    }
    
    // 计算到其他列车的最小距离
    private double calculateMinimumDistanceToOtherTrains(TrainPositionInfo posInfo) {
        double minDistance = Double.MAX_VALUE;
        
        for (Map.Entry<String, TrainPositionInfo> entry : trainPositions.entrySet()) {
            if (!entry.getKey().equals(posInfo.trainId)) {
                TrainPositionInfo otherPos = entry.getValue();
                
                // 计算相对位置和距离
                Vec3d relativePos = otherPos.position.subtract(posInfo.position);
                double distance = relativePos.length();
                
                if (isSameDirection(posInfo.direction, otherPos.direction)) {
                    // 同向列车：只考虑前方列车
                    double dotProduct = relativePos.dotProduct(posInfo.direction);
                    
                    // 如果前方有列车
                    if (dotProduct > 0) {
                        if (distance < minDistance) {
                            minDistance = distance;
                        }
                    }
                } else if (isOppositeDirection(posInfo.direction, otherPos.direction)) {
                    // 反向列车：考虑所有接近的列车，因为它们可能会相遇
                    // 反向列车总是需要考虑作为潜在危险
                    if (distance < minDistance) {
                        minDistance = distance;
                    }
                    
                    // 反向列车需要特殊处理，提前预警
                    if (distance < SAFE_DISTANCE * 2) {
                        LogSystem.warn("CBTC系统预警: 列车 " + posInfo.trainId + " 与反向列车 " + 
                                     otherPos.trainId + " 距离过近: " + distance + " 米");
                    }
                }
                // 注意：对于交叉方向的列车（既不是同向也不是反向），暂时不纳入最小距离计算
                // 这种情况需要更复杂的轨道布局分析
            }
        }
        
        // 如果没有找到其他列车，返回一个大值
        return minDistance == Double.MAX_VALUE ? SAFE_DISTANCE * 10 : minDistance;
    }
    
    // 异步更新列车ATP数据
    private void asyncUpdateTrainATPData() {
        try {
            List<TrainPositionInfo> positionInfoList = new ArrayList<>(trainPositions.values());
            
            for (int i = 0; i < positionInfoList.size(); i += TRAIN_BATCH_SIZE) {
                final int startIndex = i;
                final int endIndex = Math.min(i + TRAIN_BATCH_SIZE, positionInfoList.size());
                
                CompletableFuture.runAsync(() -> {
                    for (int j = startIndex; j < endIndex; j++) {
                        try {
                            TrainPositionInfo posInfo = positionInfoList.get(j);
                            Entity entity = world.getEntity(UUID.fromString(posInfo.trainId));
                            
                            if (entity instanceof TrainEntity) {
                                TrainEntity train = (TrainEntity) entity;
                                
                                // 获取最近的信号机信息
                                BlockPos nearestSignalPos = findNearestSignalAhead(train);
                                SignalInfo nearestSignal = nearestSignalPos != null ? signals.get(nearestSignalPos) : null;
                                
                                // 计算列车前方空闲闭塞分区数量
                                int freeSectionsAhead = nearestSignal != null ? 
                                    calculateFreeSectionsAhead(nearestSignal.pos) : 0;
                                
                                // 创建ATP数据并发送给列车
                                if (train.isATPEnabled() && train.getATPController() != null) {
                                    ATPSignalBlockEntity.ATPData atpData = createATPDataForTrain(train, nearestSignal, freeSectionsAhead);
                                    train.getATPController().updateATPSignalData(nearestSignalPos, atpData);
                                }
                            }
                        } catch (Exception e) {
                            LogSystem.error("更新列车ATP数据失败: " + e.getMessage());
                        }
                    }
                }, computationThreadPool);
            }
        } catch (Exception e) {
            LogSystem.error("异步更新列车ATP数据失败: " + e.getMessage());
        }
    }
    
    // 为列车创建ATP数据
    private ATPSignalBlockEntity.ATPData createATPDataForTrain(TrainEntity train, SignalInfo nearestSignal, int freeSectionsAhead) {
        // 计算最大速度
        double maxSpeed;
        if (nearestSignal == null) {
            maxSpeed = 80.0; // 默认最大速度
        } else {
            maxSpeed = calculateMaxSpeedForSignal(nearestSignal.getDisplay());
        }
        
        // 检查是否需要紧急制动（前方无空闲区间）
        boolean emergencyBrake = freeSectionsAhead == 0 && nearestSignal != null && 
                                 nearestSignal.getDisplay() == SignalDisplay.RED;
        
        // 计算障碍物距离（简化为空闲分区长度 * 分区数）
        double obstacleDistance = freeSectionsAhead * BLOCK_SECTION_LENGTH * 1.0; // 方块转换为米
        
        // 获取信号状态
        int signalState = nearestSignal != null ? 
            convertSignalDisplayToInt(nearestSignal.getDisplay()) : 0;
        
        // 创建ATP数据
        return new ATPSignalBlockEntity.ATPData(
            maxSpeed,
            emergencyBrake,
            obstacleDistance,
            signalState,
            freeSectionsAhead,
            freeSectionsAhead > 0,  // 进路状态
            maxSpeed * 0.9,         // 建议速度（略低于最大速度）
            0                       // ATP系统类型：CBTC
        );
    }
    
    // 查找列车前方最近的信号机
    private BlockPos findNearestSignalAhead(TrainEntity train) {
        // 简化实现：查找列车前方一定范围内的信号机
        Vec3d trainPos = train.getPos();
        BlockPos nearestSignal = null;
        double nearestDistance = Double.MAX_VALUE;
        
        for (SignalInfo signal : signals.values()) {
            double distance = signal.pos.getSquaredDistance(trainPos);
            // 只考虑前方200米内的信号机
            if (distance < 200 * 200 && distance < nearestDistance) {
                // 判断是否为前方信号机（考虑列车行驶方向）
                Vec3d signalVec = new Vec3d(signal.pos.getX() - trainPos.x, 0, signal.pos.getZ() - trainPos.z);
                Vec3d trainDir = train.getRotationVector();
                
                // 如果信号机在列车行驶方向的前方（夹角小于90度）
                if (signalVec.dotProduct(trainDir) > 0) {
                    nearestDistance = distance;
                    nearestSignal = signal.pos;
                }
            }
        }
        
        return nearestSignal;
    }
    
    // 转换信号显示为整数值
    private int convertSignalDisplayToInt(SignalDisplay display) {
        switch (display) {
            case RED:
                return 0;
            case YELLOW:
                return 1;
            case YELLOW_GREEN:
                return 3;
            case GREEN:
                return 2;
            case FLASHING_GREEN:
                return 4;
            case FLASHING_YELLOW:
                return 5;
            case DOUBLE_YELLOW:
                return 6;
            case YELLOW_FLASHING_YELLOW:
                return 7;
            case WHITE:
                return 8;
            case BLUE:
                return 9;
            default:
                return 0;
        }
    }
    
    // 根据信号显示获取推荐速度
    private double getRecommendedSpeedForSignal(SignalDisplay display) {
        switch (display) {
            case RED:
                return 0.0; // 停车
            case FLASHING_GREEN:
                return 80.0; // 快速通过
            case GREEN:
                return 60.0; // 正常速度
            case YELLOW_GREEN:
                return 45.0; // 准备减速
            case YELLOW:
                return 30.0; // 减速
            case FLASHING_YELLOW:
                return 25.0; // 道岔通过速度
            case DOUBLE_YELLOW:
                return 20.0; // 准备进站停车
            case YELLOW_FLASHING_YELLOW:
                return 15.0; // 调车作业速度
            case WHITE:
                return 15.0; // 调车作业速度
            case BLUE:
                return 0.0; // 禁止调车
            default:
                return 0.0;
        }
    }
    
    // 更新列车位置信息 - 优化为线程安全的原子操作并减少静止列车更新频率
    private void updateTrainPositions() {
        for (Entity entity : world.getEntitiesByClass(Entity.class, new net.minecraft.util.math.Box(-30000000, -64, -30000000, 30000000, 320, 30000000), 
                entity -> entity instanceof TrainEntity)) {
            TrainEntity train = (TrainEntity) entity;
            String trainId = train.getUuidAsString();
            
            // 检查列车是否静止且静止时间超过1秒
            if (train.isStopped() && train.getStoppedTime() > 1000) {
                // 静止列车每1秒只更新一次位置信息，减少资源占用
                if (System.currentTimeMillis() % 1000 >= SYSTEM_UPDATE_INTERVAL) {
                    continue; // 跳过本次更新
                }
            }
            
            // 使用computeIfAbsent确保原子操作（获取或创建）
            trainPositions.computeIfAbsent(trainId, id -> new TrainPositionInfo(trainId));
            
            // 使用computeIfPresent确保更新操作的原子性
            trainPositions.computeIfPresent(trainId, (id, posInfo) -> {
                posInfo.update(train);
                return posInfo;
            });
        }
        
        // 清理不存在的列车
        trainPositions.entrySet().removeIf(entry -> {
            String trainId = entry.getKey();
            return world.getEntity(UUID.fromString(trainId)) == null;
        });
    }
    
    // 更新闭塞区间状态
    private void updateSections() {
        for (BlockSection section : sections.values()) {
            section.clearTrainIds();
        }
        
        // 为每个区间分配列车
        for (TrainPositionInfo posInfo : trainPositions.values()) {
            BlockPos sectionKey = new BlockPos(
                (posInfo.position.getX() / BLOCK_SECTION_LENGTH) * BLOCK_SECTION_LENGTH,
                posInfo.position.getY(),
                (posInfo.position.getZ() / BLOCK_SECTION_LENGTH) * BLOCK_SECTION_LENGTH
            );
            
            BlockSection section = sections.get(sectionKey);
            if (section != null) {
                section.addTrainId(posInfo.trainId);
            }
        }
    }
    
    // 处理调度队列
    private void processDispatchQueue() {
        while (!dispatchQueue.isEmpty()) {
            TrainDispatchInfo dispatchInfo = dispatchQueue.poll();
            String trainId = dispatchInfo.trainId;
            
            // 检查是否可以安全调度
            if (canDispatchTrain(trainId, dispatchInfo.targetPosition)) {
                // 向列车发送调度指令
                Entity entity = world.getEntity(UUID.fromString(trainId));
                if (entity instanceof TrainEntity) {
                    TrainEntity train = (TrainEntity) entity;
                    train.setDestination(dispatchInfo.targetStation);
                    train.setNextStation(dispatchInfo.nextStation);
                    train.getControlSystem().setTargetSpeed(dispatchInfo.targetSpeed);
                }
            }
        }
    }
    
    // 检查是否可以安全调度列车
    private boolean canDispatchTrain(String trainId, Vec3d targetPos) {
        // 检查目标位置附近是否有其他列车
        for (Map.Entry<String, TrainPositionInfo> entry : trainPositions.entrySet()) {
            if (!entry.getKey().equals(trainId)) {
                double distance = entry.getValue().position.distanceTo(targetPos);
                if (distance < SAFE_DISTANCE) {
                    return false;
                }
            }
        }
        return true;
    }
    
    // 更新信号机状态（同步版本）
    private void updateSignalStatus() {
        for (SignalInfo signal : signals.values()) {
            try {
                // 精确计算前方空闲闭塞分区数量
                int freeSections = calculateFreeSectionsAhead(signal.pos);
                
                // 根据信号机类型和空闲分区确定信号显示
                SignalDisplay display = determineSignalDisplay(signal, freeSections);
                signal.setDisplay(display);
                
                // 更新方块实体状态
                Block block = world.getBlockState(signal.pos).getBlock();
                BlockEntity blockEntity = world.getBlockEntity(signal.pos);
                
                if (block instanceof SignalBlock && blockEntity instanceof SignalBlockEntity) {
                    SignalBlockEntity entity = (SignalBlockEntity) blockEntity;
                    entity.updateSignalState(convertToSignalBlockState(display));
                } else if (block instanceof ATPSignalBlock && blockEntity instanceof ATPSignalBlockEntity) {
                    ATPSignalBlockEntity entity = (ATPSignalBlockEntity) blockEntity;
                    updateATPSignalBlockEntity(entity, display, freeSections);
                }
            } catch (Exception e) {
                LogSystem.error("同步更新信号机 " + signal.pos + " 失败: " + e.getMessage());
            }
        }
    }
    
    // 向列车发送控制指令
    private void sendControlCommands() {
        for (TrainPositionInfo posInfo : trainPositions.values()) {
            Entity entity = world.getEntity(UUID.fromString(posInfo.trainId));
            if (entity instanceof TrainEntity) {
                TrainEntity train = (TrainEntity) entity;
                
                // 计算安全速度
                double safeSpeed = calculateSafeSpeed(posInfo);
                
                // 如果列车速度超过安全速度，通知列车减速
                if (train.getCurrentSpeed() > safeSpeed && train.isATPEnabled()) {
                    train.getControlSystem().applyServiceBrake();
                    if (train.getDriver() != null) {
                        train.getDriver().sendMessage(Text.literal("ATP: 前方区间有列车，请注意减速！"), false);
                    }
                }
            }
        }
    }
    
    // 计算安全速度（兼容旧方法）
    /**
     * 获取指定位置的临时限速值
     * @param position 位置信息
     * @return 临时限速值，单位：m/s；如果没有临时限速则返回-1
     */
    private double getTemporarySpeedLimit(Vec3d position) {
        // 检查列车当前位置是否在任何临时限速区间内
        for (TemporarySpeedLimit limit : temporarySpeedLimits.values()) {
            if (limit.isWithinRange(position) && limit.isActive()) {
                return limit.getSpeedLimit();
            }
        }
        return -1; // 无临时限速
    }
    
    private double calculateSafeSpeed(TrainPositionInfo posInfo) {
        // 读取临时限速
        double tempLimit = getTemporarySpeedLimit(posInfo.position);
        if (tempLimit > 0) {
            LogSystem.logInfo("临时限速生效: " + tempLimit + "m/s，位置: " + posInfo.position);
            // 临时限速已考虑安全余量，直接返回
            return tempLimit;
        }
        
        // 正常计算基础安全速度
        return calculateBaseSafeSpeed(posInfo);
    }
    
    /**
     * 计算基础安全速度（不考虑临时限速）
     */
    private double calculateBaseSafeSpeed(TrainPositionInfo posInfo) {
        // 查找最近的信号机
        Entity entity = world.getEntity(UUID.fromString(posInfo.trainId));
        BlockPos nearestSignalPos = null;
        SignalInfo nearestSignal = null;
        
        if (entity instanceof TrainEntity) {
            nearestSignalPos = findNearestSignalAhead((TrainEntity) entity);
            nearestSignal = nearestSignalPos != null ? signals.get(nearestSignalPos) : null;
        }
        
        // 计算基础安全速度
        double baseSpeed = calculateSafeSpeed(posInfo, nearestSignal);
        
        // 集成时刻表限速逻辑
        try {
            // 获取线路ID
            String lineId = posInfo.lineId;
            if (lineId != null && !lineId.isEmpty()) {
                // 获取时刻表限速系数
                double timetableFactor = TimetableSystem.getInstance(world).getAdjustedSpeed(lineId, 1.0);
                LogSystem.logInfo("列车 " + posInfo.trainId + " 在线路 " + lineId + " 的时刻表限速系数: " + timetableFactor);
                // 应用时刻表限速
                return baseSpeed * timetableFactor;
            }
        } catch (Exception e) {
            LogSystem.logError("应用时刻表限速失败: " + e.getMessage());
        }
        
        return baseSpeed;
    }
    
    // 计算移动闭塞安全速度
    private double calculateMovingBlockSafeSpeed(TrainPositionInfo currentTrain, TrainPositionInfo frontTrain, double distance) {
        // 计算相对速度
        double relativeSpeed = currentTrain.speed - frontTrain.speed;
        
        // 确保相对速度为正数（当前车比前车快）
        relativeSpeed = Math.max(0, relativeSpeed);
        
        // 计算所需的制动距离
        double brakingDistance = calculateBrakingDistance(relativeSpeed);
        
        // 计算安全余量
        double safeMargin = distance - brakingDistance - MOVING_BLOCK_SAFETY_DISTANCE;
        
        // 如果安全余量不足，需要降低速度
        if (safeMargin <= 0) {
            return frontTrain.speed; // 与前车保持相同速度
        } else if (safeMargin < MOVING_BLOCK_SAFETY_DISTANCE) {
            // 安全余量较小，保持较低速度
            return Math.min(40, frontTrain.speed + 10);
        }
        
        // 安全余量充足，可以保持较高速度
        return Math.min(80, frontTrain.speed + 20);
    }
    
    // 计算制动距离
    private double calculateBrakingDistance(double speed) {
        // 简化的制动距离计算公式：距离 = 速度² / (2 * 减速度)
        // 假设减速度为2.5 m/s²
        double deceleration = 2.5;
        return (speed * speed) / (2 * deceleration);
    }
    
    // 检查是否同向
    private boolean isSameDirection(Vec3d dir1, Vec3d dir2) {
        // 向量归一化以确保点积计算准确
        Vec3d normalized1 = dir1.normalize();
        Vec3d normalized2 = dir2.normalize();
        
        // 更严格的同向判断：夹角小于25度
        // 使用0.9的阈值（cos(25°)≈0.9063）
        return normalized1.dotProduct(normalized2) > 0.9;
    }
    
    // 检查是否反向
    private boolean isOppositeDirection(Vec3d dir1, Vec3d dir2) {
        // 向量归一化以确保点积计算准确
        Vec3d normalized1 = dir1.normalize();
        Vec3d normalized2 = dir2.normalize();
        
        // 反向判断：夹角大于155度
        // 使用-0.9的阈值（cos(155°)≈-0.9063）
        return normalized1.dotProduct(normalized2) < -0.9;
    }
    
    // 添加列车到调度队列
    public void addToDispatchQueue(String trainId, Vec3d targetPos, String targetStation, String nextStation, double targetSpeed) {
        TrainDispatchInfo dispatchInfo = new TrainDispatchInfo(trainId, targetPos, targetStation, nextStation, targetSpeed);
        
        // 使用offer方法而不是add方法，避免队列满时抛出异常
        if (!dispatchQueue.offer(dispatchInfo)) {
            // 队列已满，记录警告日志并返回
            LogSystem.warn("CBTC系统调度队列已满: 无法添加列车 " + trainId + " 的调度任务。队列当前大小: " + 
                          dispatchQueue.size() + "/1000");
            
            // 可以选择尝试从队列头部移除最旧的任务，为新任务腾出空间
            // 但这需要谨慎使用，因为可能会导致任务丢失
            // dispatchQueue.poll();
            // dispatchQueue.add(dispatchInfo);
        }
    }
    
    // 获取区间状态
    public BlockSectionStatus getSectionStatus(BlockPos pos) {
        BlockPos sectionKey = new BlockPos(
            (pos.getX() / BLOCK_SECTION_LENGTH) * BLOCK_SECTION_LENGTH,
            pos.getY(),
            (pos.getZ() / BLOCK_SECTION_LENGTH) * BLOCK_SECTION_LENGTH
        );
        
        BlockSection section = sections.get(sectionKey);
        if (section != null) {
            return section.getStatus();
        }
        return BlockSectionStatus.FREE;
    }
    
    // 闭塞区间类
    private static class BlockSection {
        private final BlockPos sectionPos;
        private final Set<String> trainIds = ConcurrentHashMap.newKeySet();
        private final Set<String> reservedTrainIds = ConcurrentHashMap.newKeySet();
        private long lastUpdateTime;
        private double occupancyRate;
        
        public BlockSection(BlockPos sectionPos) {
            this.sectionPos = sectionPos;
            this.lastUpdateTime = System.currentTimeMillis();
            this.occupancyRate = 0.0;
        }
        
        public void addTrainId(String trainId) {
            // 检查是否为预留标记
            if (trainId.startsWith("RESERVED_")) {
                String actualTrainId = trainId.substring("RESERVED_".length());
                reservedTrainIds.add(actualTrainId);
            } else {
                trainIds.add(trainId);
                this.occupancyRate = 1.0;
            }
            this.lastUpdateTime = System.currentTimeMillis();
        }
        
        public void clearTrainIds() {
            trainIds.clear();
            reservedTrainIds.clear();
            this.occupancyRate = 0.0;
        }
        
        public BlockSectionStatus getStatus() {
            if (!trainIds.isEmpty()) {
                return trainIds.size() > 1 ? BlockSectionStatus.CONGESTED : BlockSectionStatus.OCCUPIED;
            } else if (!reservedTrainIds.isEmpty()) {
                return BlockSectionStatus.RESERVED;
            } else {
                return BlockSectionStatus.FREE;
            }
        }
        
        public Set<String> getTrainIds() {
            return new HashSet<>(trainIds);
        }
        
        public Set<String> getReservedTrainIds() {
            return new HashSet<>(reservedTrainIds);
        }
        
        public void update() {
            // 如果超过5秒没有更新，自动释放占用和预留
            if (System.currentTimeMillis() - lastUpdateTime > 5000) {
                clearTrainIds();
            }
        }
        
        public void updateOccupancyRate(double rate) {
            this.occupancyRate = Math.max(0, Math.min(1.0, rate));
            this.lastUpdateTime = System.currentTimeMillis();
        }
    }
    
    // 列车位置信息类
    private static class TrainPositionInfo {
        private final String trainId;
        private Vec3d position;
        private Vec3d direction;
        private double speed;
        private double acceleration;
        private String lineId;
        private String currentStation;
        private String nextStation;
        private boolean isStopped;
        private double stoppingDistance;
        
        public TrainPositionInfo(String trainId) {
            this.trainId = trainId;
            this.position = Vec3d.ZERO;
            this.direction = Vec3d.ZERO;
            this.speed = 0;
            this.acceleration = 0;
            this.lineId = "";
            this.currentStation = "";
            this.nextStation = "";
            this.isStopped = false;
            this.stoppingDistance = 0;
        }
        
        public void update(TrainEntity train) {
            // 保存旧的位置和速度用于计算加速度
            Vec3d oldPosition = this.position;
            double oldSpeed = this.speed;
            
            // 更新当前状态
            this.position = train.getPos();
            this.direction = train.getRotationVector();
            this.speed = train.getCurrentSpeed();
            
            // 计算加速度（简化计算）
            if (oldSpeed >= 0 && speed >= 0) {
                this.acceleration = speed - oldSpeed;
            }
            
            // 检查是否停止
            this.isStopped = Math.abs(speed) < 0.1;
            
            // 获取线路和车站信息
            TrainConsist consist = train.getConsist();
            if (consist != null) {
                this.lineId = consist.getLineId();
                this.currentStation = consist.getCurrentStation();
                this.nextStation = consist.getNextStation();
            }
            
            // 估算停车距离
            if (!isStopped && speed > 0) {
                this.stoppingDistance = calculateBrakingDistance(speed);
            } else {
                this.stoppingDistance = 0;
            }
        }
        
        // 获取移动闭塞安全包络的末端位置
        public Vec3d getSafetyEnvelopeEnd() {
            // 安全包络 = 当前位置 + 行驶方向 * (当前速度下的制动距离 + 安全距离)
            double envelopeDistance = stoppingDistance + MOVING_BLOCK_SAFETY_DISTANCE;
            return position.add(direction.normalize().multiply(envelopeDistance));
        }
    }
    
    // 列车调度信息类
    private static class TrainDispatchInfo {
        private final String trainId;
        private final Vec3d targetPosition;
        private final String targetStation;
        private final String nextStation;
        private final double targetSpeed;
        
        public TrainDispatchInfo(String trainId, Vec3d targetPosition, String targetStation, String nextStation, double targetSpeed) {
            this.trainId = trainId;
            this.targetPosition = targetPosition;
            this.targetStation = targetStation;
            this.nextStation = nextStation;
            this.targetSpeed = targetSpeed;
        }
    }
    
    // 警报优先级枚举
    public enum AlertPriority {
        HIGHEST, // 最高优先级（安全类警报）
        HIGH,    // 高优先级
        MEDIUM,  // 中等优先级（运营类警报）
        LOW,     // 低优先级（维护类警报）
        LOWEST   // 最低优先级
    }
    
    // 警报类型枚举
    public enum AlertType {
        // 安全类警报
        BRAKE_FAILURE("制动失效", AlertPriority.HIGHEST),
        TRACTION_FAILURE("牵引系统故障", AlertPriority.HIGHEST),
        DOOR_NOT_LOCKED("车门未锁闭", AlertPriority.HIGHEST),
        SPEED_OVERLIMIT("速度超限", AlertPriority.HIGH),
        SIGNAL_CONFLICT("信号冲突", AlertPriority.HIGH),
        
        // 运营类警报
        AIR_CONDITIONING_FAILURE("空调故障", AlertPriority.MEDIUM),
        PASSENGER_EMERGENCY_CALL("乘客紧急呼叫", AlertPriority.MEDIUM),
        FIRE_ALERT("火灾警报", AlertPriority.HIGH),
        
        // 维护类警报
        BEARING_OVERHEAT("轴承温度过高", AlertPriority.LOW),
        WHEEL_WEAR("轮胎磨损超标", AlertPriority.LOW),
        
        // 安全类警报 - 新增紧急解锁类型
        EMERGENCY_UNLOCK("紧急解锁", AlertPriority.HIGH),
        
        // 其他警报
        SPEED_SENSOR_FAILURE("速度传感器故障", AlertPriority.HIGH),
        EMERGENCY_BRAKE_ACTIVATED("紧急制动已激活", AlertPriority.HIGHEST);
        
        private final String description;
        private final AlertPriority priority;
        
        AlertType(String description, AlertPriority priority) {
            this.description = description;
            this.priority = priority;
        }
        
        public String getDescription() {
            return description;
        }
        
        public AlertPriority getPriority() {
            return priority;
        }
    }
    
    // 警报状态枚举
    public enum AlertStatus {
        ACTIVE,      // 活跃状态
        ACKNOWLEDGED, // 已确认
        RESOLVED     // 已解决
    }
    
    // 警报信息类
    /**
     * 临时限速信息类
     */
    public class TemporarySpeedLimit {
        private final String id;
        private final Vec3d startPos;
        private final Vec3d endPos;
        private final double speedLimit; // 单位：m/s
        private final long startTime; // 生效时间
        private final long endTime; // 结束时间
        private final String reason; // 设置原因
        private final String operator; // 操作员
        
        public TemporarySpeedLimit(String id, Vec3d startPos, Vec3d endPos, double speedLimit, 
                                 long startTime, long endTime, String reason, String operator) {
            this.id = id;
            this.startPos = startPos;
            this.endPos = endPos;
            this.speedLimit = speedLimit;
            this.startTime = startTime;
            this.endTime = endTime;
            this.reason = reason;
            this.operator = operator;
        }
        
        /**
         * 检查位置是否在限速区间内
         */
        public boolean isWithinRange(Vec3d position) {
            // 简化实现：检查X和Z坐标是否在区间内
            double minX = Math.min(startPos.x, endPos.x);
            double maxX = Math.max(startPos.x, endPos.x);
            double minZ = Math.min(startPos.z, endPos.z);
            double maxZ = Math.max(startPos.z, endPos.z);
            
            return position.x >= minX && position.x <= maxX && 
                   position.z >= minZ && position.z <= maxZ;
        }
        
        /**
         * 检查限速是否当前生效
         */
        public boolean isActive() {
            long currentTime = System.currentTimeMillis();
            return currentTime >= startTime && currentTime <= endTime;
        }
        
        public double getSpeedLimit() { return speedLimit; }
        public String getId() { return id; }
        public Vec3d getStartPos() { return startPos; }
        public Vec3d getEndPos() { return endPos; }
        public long getStartTime() { return startTime; }
        public long getEndTime() { return endTime; }
        public String getReason() { return reason; }
        public String getOperator() { return operator; }
    }
    
    /**
     * 添加临时限速
     */
    public void addTemporarySpeedLimit(Vec3d startPos, Vec3d endPos, double speedLimit, 
                                     long startTime, long endTime, String reason, String operator) {
        String id = UUID.randomUUID().toString();
        TemporarySpeedLimit limit = new TemporarySpeedLimit(id, startPos, endPos, speedLimit, 
                                                         startTime, endTime, reason, operator);
        temporarySpeedLimits.put(id, limit);
        LogSystem.logInfo("添加临时限速: ID=" + id + ", 区间=" + startPos + "至" + endPos + ", 限速=" + speedLimit + "m/s");
    }
    
    /**
     * 移除临时限速
     */
    public void removeTemporarySpeedLimit(String id) {
        if (temporarySpeedLimits.remove(id) != null) {
            LogSystem.logInfo("移除临时限速: ID=" + id);
        }
    }
    
    /**
     * 获取所有有效的临时限速
     */
    public Collection<TemporarySpeedLimit> getActiveTemporarySpeedLimits() {
        return temporarySpeedLimits.values().stream()
                .filter(TemporarySpeedLimit::isActive)
                .collect(Collectors.toList());
    }
    
    public class AlertInfo {
        private final String alertId;
        private final String trainId;
        private final AlertType type;
        private final String message;
        private final long timestamp;
        private AlertStatus status;
        private String location;
        private Map<String, Object> additionalInfo;
        
        public AlertInfo(String trainId, AlertType type, String message) {
            this.alertId = "ALERT-" + UUID.randomUUID().toString().substring(0, 8);
            this.trainId = trainId;
            this.type = type;
            this.message = message;
            this.timestamp = System.currentTimeMillis();
            this.status = AlertStatus.ACTIVE;
            this.additionalInfo = new HashMap<>();
        }
        
        public String getAlertId() { return alertId; }
        public String getTrainId() { return trainId; }
        public AlertType getType() { return type; }
        public String getMessage() { return message; }
        public long getTimestamp() { return timestamp; }
        public AlertStatus getStatus() { return status; }
        public void setStatus(AlertStatus status) { this.status = status; }
        public String getLocation() { return location; }
        public void setLocation(String location) { this.location = location; }
        
        public void addAdditionalInfo(String key, Object value) {
            this.additionalInfo.put(key, value);
        }
        
        public Map<String, Object> getAdditionalInfo() {
            return additionalInfo;
        }
        
        @Override
        public String toString() {
            return String.format("[%s] 列车 %s: %s (类型: %s, 优先级: %s)", 
                new Date(timestamp), trainId, message, type.getDescription(), type.getPriority());
        }
    }
    
    // 信号显示枚举（扩展的多显示自动闭塞）
    public enum SignalDisplay {
        RED,            // 红灯：停车，禁止越过
        YELLOW,         // 黄灯：注意减速，前方有一个闭塞分区被占用
        YELLOW_GREEN,   // 绿黄灯：前方有两个闭塞分区空闲，准备减速
        GREEN,          // 绿灯：前方至少有三个闭塞分区空闲，可以按规定速度运行
        FLASHING_GREEN, // 闪绿灯：表示允许以更高速度通过，前方线路状态良好
        FLASHING_YELLOW, // 闪黄灯：表示允许以有限速度通过道岔或特殊区域
        DOUBLE_YELLOW,  // 双黄灯：表示接近进站信号机，准备停车
        YELLOW_FLASHING_YELLOW, // 黄闪黄灯：表示允许调车作业
        WHITE,          // 白灯：调车信号机，表示允许调车作业
        BLUE            // 蓝灯：调车信号机，表示禁止调车作业
    }
    
    // 信号机信息类
    private static class SignalInfo {
        private final BlockPos pos;
        private SignalDisplay display;
        private SignalType type;
        private boolean isApproaching;
        private String associatedLineId;
        
        public SignalInfo(BlockPos pos) {
            this.pos = pos;
            this.display = SignalDisplay.RED;
            this.type = SignalType.MAIN;
            this.isApproaching = false;
            this.associatedLineId = "";
        }
        
        public void setDisplay(SignalDisplay display) {
            this.display = display;
        }
        
        public SignalDisplay getDisplay() {
            return display;
        }
        
        public void setType(SignalType type) {
            this.type = type;
        }
        
        public SignalType getType() {
            return type;
        }
        
        public void setApproaching(boolean approaching) {
            this.isApproaching = approaching;
        }
        
        public boolean isApproaching() {
            return isApproaching;
        }
        
        public void setAssociatedLineId(String lineId) {
            this.associatedLineId = lineId;
        }
        
        public String getAssociatedLineId() {
            return associatedLineId;
        }
    }
    
    // 信号机类型枚举（扩展）
    public enum SignalType {
        MAIN,           // 主信号机
        ENTRY,          // 进站信号机
        EXIT,           // 出站信号机
        SHUNTING,       // 调车信号机
        BLOCK,          // 闭塞信号机
        REPEATING,      // 复示信号机
        DISTANT,        // 预告信号机
        DIVERGING,      // 分歧信号机
        COMBINED,       // 组合信号机（主体和调车功能）
        STATION_REPEATING, // 站内复示信号机
        EXPRESS_PASSING, // 快速通过信号机
        EMERGENCY_STOP  // 紧急停车信号机
    }
    
    // 添加信号机到系统
    public void registerSignal(BlockPos pos, SignalType type) {
        SignalInfo signalInfo = signals.computeIfAbsent(pos, SignalInfo::new);
        signalInfo.setType(type);
        LogSystem.debug("CBTC系统注册信号机: " + pos + " 类型: " + type);
    }
    
    // 更新信号机类型
    public void updateSignalType(BlockPos pos, SignalType type) {
        SignalInfo signalInfo = signals.get(pos);
        if (signalInfo != null) {
            signalInfo.setType(type);
            LogSystem.debug("CBTC系统更新信号机类型: " + pos + " 类型: " + type);
        }
    }
    
    // 移除信号机
    public void unregisterSignal(BlockPos pos) {
        signals.remove(pos);
        LogSystem.debug("CBTC系统移除信号机: " + pos);
    }
    
    // 设置信号机关联的线路ID
    public void setSignalAssociatedLine(BlockPos pos, String lineId) {
        SignalInfo signalInfo = signals.get(pos);
        if (signalInfo != null) {
            signalInfo.setAssociatedLineId(lineId);
            LogSystem.debug("CBTC系统设置信号机线路关联: " + pos + " 线路: " + lineId);
        }
    }
    
    // 闭塞区间状态枚举
    public enum BlockSectionStatus {
        FREE,       // 空闲
        OCCUPIED,   // 占用
        CONGESTED,  // 拥挤
        RESERVED    // 预留（移动闭塞专用）
    }
    
    // 移动闭塞相关配置
    private static final boolean MOBILE_BLOCK_ENABLED = true;          // 是否启用移动闭塞
    private static final double SAFETY_DISTANCE = 50.0;               // 安全距离（米）
    private static final double MINIMUM_BLOCK_LENGTH = 100.0;         // 最小闭塞分区长度（米）
    private static final double TRAIN_LENGTH_FACTOR = 1.5;            // 列车长度倍数（移动闭塞计算用）
    
    // 移动闭塞数据结构
    private static class MobileBlockInfo {
        private final String trainId;
        private BlockPos frontPosition;
        private double requiredBlockLength;
        private boolean isActive;
        
        public MobileBlockInfo(String trainId) {
            this.trainId = trainId;
            this.isActive = false;
        }
        
        public void update(BlockPos frontPos, double trainLength, double speed) {
            this.frontPosition = frontPos;
            // 计算所需的移动闭塞区间长度 = 列车长度×系数 + 速度×反应时间 + 安全距离
            double reactionDistance = speed * 2.0; // 2秒反应时间
            this.requiredBlockLength = Math.max(MINIMUM_BLOCK_LENGTH, 
                                               trainLength * TRAIN_LENGTH_FACTOR + reactionDistance + SAFETY_DISTANCE);
            this.isActive = true;
        }
        
        public boolean isPositionInBlock(BlockPos pos) {
            if (!isActive || frontPosition == null) return false;
            double distance = Math.sqrt(frontPosition.getSquaredDistance(pos));
            return distance <= requiredBlockLength;
        }
    }
    
    // 存储各列车的移动闭塞信息
    private final Map<String, MobileBlockInfo> mobileBlockMap = new ConcurrentHashMap<>();
}