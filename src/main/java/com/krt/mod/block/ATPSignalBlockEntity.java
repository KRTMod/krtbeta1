package com.krt.mod.block;

import com.krt.mod.system.LogSystem;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.WorldView;

public class ATPSignalBlockEntity extends BlockEntity {
    // 四显示信号状态枚举（兼容SignalBlock.SignalState）
    public enum SignalStatus {
        RED(0),      // 红灯
        YELLOW(1),   // 黄灯
        YELLOW_GREEN(3), // 绿黄灯
        GREEN(2);    // 绿灯
        
        private final int value;
        
        SignalStatus(int value) {
            this.value = value;
        }
        
        public int getValue() {
            return value;
        }
        
        public static SignalStatus fromValue(int value) {
            for (SignalStatus status : values()) {
                if (status.value == value) {
                    return status;
                }
            }
            return RED; // 默认返回红灯
        }
        
        // 从前方空闲闭塞分区数量转换为信号状态
        public static SignalStatus fromBlockSections(int freeSections) {
            if (freeSections >= 3) {
                return GREEN;
            } else if (freeSections == 2) {
                return YELLOW_GREEN;
            } else if (freeSections == 1) {
                return YELLOW;
            } else {
                return RED;
            }
        }
        
        // 转换为SignalBlock.SignalState
        public SignalBlock.SignalState toSignalBlockState() {
            switch (this) {
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
        
        // 从SignalBlock.SignalState转换
        public static SignalStatus fromSignalBlockState(SignalBlock.SignalState state) {
            switch (state) {
                case RED:
                    return RED;
                case YELLOW:
                    return YELLOW;
                case YELLOW_GREEN:
                    return YELLOW_GREEN;
                case GREEN:
                    return GREEN;
                default:
                    return RED;
            }
        }
    }
    
    // 信号状态 (0=红灯, 1=黄灯, 2=绿灯, 3=绿黄灯)
    private int signalState = 0;
    // 最大允许速度
    private double maxSpeed = 0.0;
    // 紧急制动状态
    private boolean emergencyBrake = false;
    // 障碍物距离
    private double obstacleDistance = 0.0;
    // 前方信号机状态
    private int nextSignalState = 0;
    // 前方空闲闭塞分区数量
    private int freeBlockSections = 0;
    // 进路是否已建立
    private boolean routeClear = false;
    // 闭塞分区占用情况
    private int blockOccupancy = 0;
    // 建议速度
    private double suggestedSpeed = 0.0;
    // 轨道占用计数
    private int trackOccupancyCount = 0;
    // 最后更新时间
    private long lastUpdateTime = 0;
    // ATP系统类型
    private int atpSystemType = 0; // 0=CBTC, 1=ATC, 2=CTCS-2, 3=ETCS
    // 信号亮度相关
    private int signalBrightness = 10; // 默认亮度值
    private boolean weatherTimeLinkEnabled = true; // 是否启用天气/时间联动

    public ATPSignalBlockEntity(BlockPos pos, BlockState state) {
        super(KRTBlockEntities.ATP_SIGNAL, pos, state);
        this.suggestedSpeed = calculateSuggestedSpeed();
    }

    @Override
    protected void writeNbt(NbtCompound nbt) {
        super.writeNbt(nbt);
        nbt.putInt("SignalState", signalState);
        nbt.putDouble("MaxSpeed", maxSpeed);
        nbt.putBoolean("EmergencyBrake", emergencyBrake);
        nbt.putDouble("ObstacleDistance", obstacleDistance);
        nbt.putInt("NextSignalState", nextSignalState);
        nbt.putInt("FreeBlockSections", freeBlockSections);
        nbt.putBoolean("RouteClear", routeClear);
        nbt.putInt("BlockOccupancy", blockOccupancy);
        nbt.putDouble("SuggestedSpeed", suggestedSpeed);
        nbt.putInt("TrackOccupancyCount", trackOccupancyCount);
        nbt.putLong("LastUpdateTime", lastUpdateTime);
        nbt.putInt("ATPSystemType", atpSystemType);
        nbt.putInt("SignalBrightness", signalBrightness);
        nbt.putBoolean("WeatherTimeLinkEnabled", weatherTimeLinkEnabled);
    }

    @Override
    public void readNbt(NbtCompound nbt) {
        super.readNbt(nbt);
        signalState = nbt.getInt("SignalState");
        maxSpeed = nbt.getDouble("MaxSpeed");
        emergencyBrake = nbt.getBoolean("EmergencyBrake");
        obstacleDistance = nbt.getDouble("ObstacleDistance");
        nextSignalState = nbt.getInt("NextSignalState");
        
        // 读取新增的字段（向后兼容）
        if (nbt.contains("FreeBlockSections")) {
            freeBlockSections = nbt.getInt("FreeBlockSections");
        }
        if (nbt.contains("RouteClear")) {
            routeClear = nbt.getBoolean("RouteClear");
        }
        if (nbt.contains("BlockOccupancy")) {
            blockOccupancy = nbt.getInt("BlockOccupancy");
        }
        if (nbt.contains("SuggestedSpeed")) {
            suggestedSpeed = nbt.getDouble("SuggestedSpeed");
        }
        if (nbt.contains("TrackOccupancyCount")) {
            trackOccupancyCount = nbt.getInt("TrackOccupancyCount");
        }
        if (nbt.contains("LastUpdateTime")) {
            lastUpdateTime = nbt.getLong("LastUpdateTime");
        }
        if (nbt.contains("ATPSystemType")) {
            atpSystemType = nbt.getInt("ATPSystemType");
        }
        // 亮度和天气联动设置
        if (nbt.contains("SignalBrightness")) {
            signalBrightness = nbt.getInt("SignalBrightness");
        }
        if (nbt.contains("WeatherTimeLinkEnabled")) {
            weatherTimeLinkEnabled = nbt.getBoolean("WeatherTimeLinkEnabled");
        } else {
            weatherTimeLinkEnabled = true; // 默认启用
        }
        // 如果NBT中没有亮度数据，应用天气/时间联动逻辑
        if (!nbt.contains("SignalBrightness") && weatherTimeLinkEnabled && world != null) {
            updateBrightnessBasedOnWeatherAndTime();
        }
        
        // 重新计算建议速度
        this.suggestedSpeed = calculateSuggestedSpeed();
    }

    /**
     * 计算建议速度
     */
    private double calculateSuggestedSpeed() {
        if (emergencyBrake) return 0;
        
        // 根据信号状态计算建议速度
        switch (signalState) {
            case 0: // 红灯
                return 0.0;
            case 1: // 黄灯
                return 20.0; // 减速至20km/h
            case 3: // 绿黄灯
                return 50.0; // 减速至50km/h
            case 2: // 绿灯
                return 80.0; // 全速80km/h
            default:
                return 0.0;
        }
    }

    /**
     * 更新信号状态
     */
    public void updateSignalState(int newState) {
        // 确保状态值有效
        if (newState < 0 || newState > 3) {
            LogSystem.error("ATP信号机无效的状态值: " + newState);
            return;
        }
        
        this.signalState = newState;
        
        // 根据信号状态更新最大允许速度
        updateMaxSpeed();
        
        // 标记为脏，触发数据同步
        markDirty();
        
        if (world != null && !world.isClient) {
            world.updateListeners(pos, getCachedState(), getCachedState(), 3);
        }
        
        LogSystem.systemLog("ATP信号机 " + pos + " 状态更新为: " + getSignalStatusString(newState));
    }

    /**
     * 使用四显示信号状态枚举更新信号
     */
    public void updateSignalState(SignalStatus status) {
        updateSignalState(status.getValue());
    }

    /**
     * 同步SignalBlock.SignalState状态
     */
    public void updateFromSignalBlockState(SignalBlock.SignalState state) {
        updateSignalState(SignalStatus.fromSignalBlockState(state).getValue());
    }

    /**
     * 根据信号状态更新最大允许速度
     */
    private void updateMaxSpeed() {
        switch (signalState) {
            case 0: // 红灯
                maxSpeed = 0.0;
                emergencyBrake = true;
                break;
            case 1: // 黄灯
                maxSpeed = 20.0;
                emergencyBrake = false;
                break;
            case 3: // 绿黄灯
                maxSpeed = 50.0;
                emergencyBrake = false;
                break;
            case 2: // 绿灯
                maxSpeed = 80.0;
                emergencyBrake = false;
                break;
        }
        // 更新建议速度
        this.suggestedSpeed = calculateSuggestedSpeed();
    }

    /**
     * 设置最大速度
     */
    public void setMaxSpeed(double newMaxSpeed) {
        this.maxSpeed = Math.max(0, newMaxSpeed); // 确保速度不为负数
        this.suggestedSpeed = calculateSuggestedSpeed();
        markDirty();
        
        if (world != null && !world.isClient) {
            world.updateListeners(pos, getCachedState(), getCachedState(), 3);
        }
        
        LogSystem.debug("ATP信号机 " + pos + " 更新最大速度: " + newMaxSpeed + " km/h");
    }

    /**
     * 设置障碍物距离
     */
    public void setObstacleDistance(double distance) {
        this.obstacleDistance = Math.max(0, distance); // 确保距离不为负数
        markDirty();
        
        // 如果障碍物太近，触发紧急制动
        if (distance < 100 && distance > 0) {
            setEmergencyBrake(true);
            LogSystem.warningLog("ATP信号机 " + pos + " 检测到近距离障碍物: " + distance + " 米");
        }
    }

    /**
     * 设置前方信号机状态
     */
    public void setNextSignalState(int state) {
        this.nextSignalState = state;
        markDirty();
        
        // 根据前方信号状态调整当前信号状态（如果连接了轨道传感器）
        BlockState blockState = getCachedState();
        if (blockState.contains(ATPSignalBlock.HAS_TRACK_SENSOR) && blockState.get(ATPSignalBlock.HAS_TRACK_SENSOR)) {
            // 这里可以实现更复杂的信号连锁逻辑
        }
    }

    /**
     * 更新空闲闭塞分区数量
     */
    public void updateFreeBlockSections(int sections) {
        this.freeBlockSections = Math.max(0, sections); // 确保数量不为负数
        
        // 根据空闲分区数量自动更新信号状态（如果接入了轨道传感器）
        BlockState blockState = getCachedState();
        if (blockState.contains(ATPSignalBlock.HAS_TRACK_SENSOR) && blockState.get(ATPSignalBlock.HAS_TRACK_SENSOR)) {
            SignalStatus newStatus = SignalStatus.fromBlockSections(sections);
            if (getSignalStatus() != newStatus) {
                updateSignalState(newStatus);
                LogSystem.systemLog("ATP信号机 " + pos + " 根据前方空闲分区数量(" + sections + ")自动调整状态为: " + newStatus);
            }
        }
        
        markDirty();
        
        if (world != null && !world.isClient) {
            world.updateListeners(pos, getCachedState(), getCachedState(), 3);
        }
    }

    /**
     * 更新进路状态
     */
    public void updateRouteClear(boolean clear) {
        this.routeClear = clear;
        markDirty();
        
        if (world != null && !world.isClient) {
            world.updateListeners(pos, getCachedState(), getCachedState(), 3);
        }
        
        LogSystem.debug("ATP信号机 " + pos + " 进路状态更新为: " + (clear ? "已建立" : "未建立"));
    }

    /**
     * 设置紧急制动
     */
    public void setEmergencyBrake(boolean emergencyBrake) {
        this.emergencyBrake = emergencyBrake;
        
        if (emergencyBrake) {
            this.maxSpeed = 0.0;
            this.suggestedSpeed = 0.0;
            LogSystem.warningLog("ATP信号机 " + pos + " 触发紧急制动！");
        } else {
            // 解除紧急制动后重新计算速度
            updateMaxSpeed();
            LogSystem.debug("ATP信号机 " + pos + " 解除紧急制动");
        }
        
        markDirty();
        
        if (world != null && !world.isClient) {
            world.updateListeners(pos, getCachedState(), getCachedState(), 3);
        }
    }

    /**
     * 更新轨道占用计数
     */
    public void updateTrackOccupancy(boolean occupied) {
        if (occupied) {
            trackOccupancyCount++;
        } else {
            trackOccupancyCount = Math.max(0, trackOccupancyCount - 1);
        }
        
        markDirty();
        
        if (world != null && !world.isClient) {
            world.updateListeners(pos, getCachedState(), getCachedState(), 3);
        }
        
        // 轨道占用逻辑
        if (occupied) {
            LogSystem.debug("ATP信号机 " + pos + " 检测到轨道占用");
        }
    }

    /**
     * 更新ATP系统类型
     */
    public void setATPSystemType(int type) {
        this.atpSystemType = type;
        markDirty();
        
        if (world != null && !world.isClient) {
            world.updateListeners(pos, getCachedState(), getCachedState(), 3);
        }
    }
    
    /**
     * 获取信号亮度
     */
    public int getSignalBrightness() {
        return signalBrightness;
    }
    
    /**
     * 设置信号亮度
     */
    public void setSignalBrightness(int brightness) {
        // 限制亮度范围在0-15之间
        this.signalBrightness = Math.max(0, Math.min(15, brightness));
        markDirty();
        
        if (world != null && !world.isClient) {
            world.updateListeners(pos, getCachedState(), getCachedState(), 3);
        }
    }
    
    /**
     * 是否启用天气/时间联动
     */
    public boolean isWeatherTimeLinkEnabled() {
        return weatherTimeLinkEnabled;
    }
    
    /**
     * 设置是否启用天气/时间联动
     */
    public void setWeatherTimeLinkEnabled(boolean enabled) {
        this.weatherTimeLinkEnabled = enabled;
        markDirty();
        
        // 如果启用联动，立即更新亮度
        if (enabled && world != null) {
            updateBrightnessBasedOnWeatherAndTime();
        }
    }
    
    /**
     * 根据天气和时间更新信号亮度
     */
    private void updateBrightnessBasedOnWeatherAndTime() {
        if (world == null) return;
        
        boolean isNight = world.isNight(); // 检测是否为夜间
        boolean isThunder = world.isThundering(); // 检测是否为雷暴天气
        boolean isRaining = world.isRaining(); // 检测是否为雨天
        
        int newBrightness = 10; // 默认为中等亮度
        
        // 雷暴天气时亮度最高
        if (isThunder) {
            newBrightness = 15;
        } 
        // 夜间或雨天时亮度较高
        else if (isNight || isRaining) {
            newBrightness = 13;
        } 
        // 白天正常亮度
        else {
            newBrightness = 10;
        }
        
        if (signalBrightness != newBrightness) {
            setSignalBrightness(newBrightness);
            LogSystem.debug("ATP信号机 " + pos + " 根据天气/时间调整亮度: " + newBrightness);
        }
    }

    /**
     * 获取信号状态
     */
    public int getSignalState() {
        return signalState;
    }
    
    /**
     * 获取信号状态枚举
     */
    public SignalStatus getSignalStatus() {
        return SignalStatus.fromValue(signalState);
    }
    
    /**
     * 设置信号状态枚举
     */
    public void setSignalStatus(SignalStatus status) {
        updateSignalState(status);
    }

    /**
     * 获取最大允许速度
     */
    public double getMaxSpeed() {
        return maxSpeed;
    }

    /**
     * 获取建议速度
     */
    public double getSuggestedSpeed() {
        return suggestedSpeed;
    }

    /**
     * 获取紧急制动状态
     */
    public boolean isEmergencyBrake() {
        return emergencyBrake;
    }

    /**
     * 获取障碍物距离
     */
    public double getObstacleDistance() {
        return obstacleDistance;
    }

    /**
     * 获取前方信号机状态
     */
    public int getNextSignalState() {
        return nextSignalState;
    }

    /**
     * 获取空闲闭塞分区数量
     */
    public int getFreeBlockSections() {
        return freeBlockSections;
    }

    /**
     * 获取进路状态
     */
    public boolean isRouteClear() {
        return routeClear;
    }

    /**
     * 获取轨道占用计数
     */
    public int getTrackOccupancyCount() {
        return trackOccupancyCount;
    }

    /**
     * 获取ATP系统类型
     */
    public int getATPSystemType() {
        return atpSystemType;
    }

    /**
     * 获取ATP信号数据
     */
    public ATPData getATPData() {
        return new ATPData(maxSpeed, emergencyBrake, obstacleDistance, signalState, 
                          freeBlockSections, routeClear, suggestedSpeed, atpSystemType);
    }

    /**
     * 获取当前信号显示对应的ATP最大速度限制
     */
    public double getSpeedLimitFromSignal() {
        switch (signalState) {
            case 2: // 绿灯
                return maxSpeed;          // 全速
            case 3: // 绿黄灯
                return maxSpeed * 0.7;    // 70%最大速度
            case 1: // 黄灯
                return maxSpeed * 0.3;    // 30%最大速度
            case 0: // 红灯
                return 0;                 // 停车
            default:
                return 0;
        }
    }

    /**
     * ATP信号数据类 - 扩展支持四显示自动闭塞
     */
    public static class ATPData {
        public double maxSpeed = 80.0;           // 最大允许速度
        public boolean emergencyStop = false;    // 是否需要紧急制动
        public double distanceToObstacle = Double.MAX_VALUE; // 到前方障碍物的距离
        public boolean isSignalGreen = true;     // 前方信号机是否为绿灯
        public int signalState = 0;              // 详细信号状态(0=红灯,1=黄灯,2=绿灯,3=绿黄灯)
        public int freeBlockSections = 0;        // 前方空闲闭塞分区数量
        public boolean routeClear = false;       // 进路是否已建立
        public double suggestedSpeed = 0.0;      // 建议速度
        public int atpSystemType = 0;            // ATP系统类型
        
        public ATPData() {
            // 默认构造函数
        }
        
        public ATPData(double maxSpeed, boolean emergencyStop, double distanceToObstacle, int signalState) {
            this.maxSpeed = maxSpeed;
            this.emergencyStop = emergencyStop;
            this.distanceToObstacle = distanceToObstacle;
            this.signalState = signalState;
            this.isSignalGreen = signalState == 2; // 绿灯为通行状态
        }
        
        public ATPData(double maxSpeed, boolean emergencyStop, double distanceToObstacle, int signalState, 
                      int freeBlockSections, boolean routeClear, double suggestedSpeed, int atpSystemType) {
            this.maxSpeed = maxSpeed;
            this.emergencyStop = emergencyStop;
            this.distanceToObstacle = distanceToObstacle;
            this.signalState = signalState;
            this.isSignalGreen = signalState == 2;
            this.freeBlockSections = freeBlockSections;
            this.routeClear = routeClear;
            this.suggestedSpeed = suggestedSpeed;
            this.atpSystemType = atpSystemType;
        }
        
        // 获取信号状态字符串
        public String getSignalStateString() {
            switch (signalState) {
                case 0: return "红灯 (停车)";
                case 1: return "黄灯 (减速)";
                case 2: return "绿灯 (通行)";
                case 3: return "绿黄灯 (减速准备)";
                default: return "未知状态";
            }
        }
    }

    /**
     * 获取信号状态字符串
     */
    public static String getSignalStatusString(int state) {
        switch (state) {
            case 0: return "红灯 (停车)";
            case 1: return "黄灯 (减速)";
            case 2: return "绿灯 (通行)";
            case 3: return "绿黄灯 (减速准备)";
            default: return "未知状态";
        }
    }

    /**
     * 每刻更新逻辑
     */
    public static void tick(World world, BlockPos pos, BlockState state, ATPSignalBlockEntity blockEntity) {
        if (world.isClient) {
            return;
        }
        
        // 更新最后更新时间
        blockEntity.lastUpdateTime = world.getTime();
        
        // 检查供电状态
        boolean isPowered = false;
        if (state.contains(ATPSignalBlock.POWERED)) {
            isPowered = state.get(ATPSignalBlock.POWERED);
        }
        
        if (!isPowered) {
            // 无电状态下触发紧急制动
            if (!blockEntity.emergencyBrake) {
                blockEntity.setEmergencyBrake(true);
                LogSystem.warningLog("ATP信号机 " + pos + " 断电，触发紧急制动保护！");
            }
        }
        
        // 检查DCS连接状态
        boolean dcsConnected = false;
        if (state.contains(ATPSignalBlock.DCS_CONNECTED)) {
            dcsConnected = state.get(ATPSignalBlock.DCS_CONNECTED);
        }
        
        // DCS连接断开时触发安全措施
        if (!dcsConnected) {
            // 通信中断时，将信号设为红灯并触发紧急制动
            blockEntity.signalState = 0; // 设置为红灯
            if (!blockEntity.emergencyBrake) {
                blockEntity.setEmergencyBrake(true);
                LogSystem.warning("ATP信号机 " + pos + " DCS连接断开，触发紧急制动保护！");
            }
        }
        
        // 每5秒检查一次天气和时间，更新信号亮度
        if (world.getTime() % 100 == 0) { // 每5秒
            if (blockEntity.weatherTimeLinkEnabled) {
                blockEntity.updateBrightnessBasedOnWeatherAndTime();
            }
        }
        
        // 定期清理脏标记
        if (world.getTime() % 20 == 0) { // 每1秒
            blockEntity.markDirty();
            
            // 调试日志
            if (LogSystem.isDebugEnabled()) {
                LogSystem.debug("ATP信号机 " + pos + " 状态: "+ 
                                  getSignalStatusString(blockEntity.signalState) + 
                                  ", 供电: " + isPowered + ", DCS连接: " + dcsConnected + 
                                  ", 空闲分区: " + blockEntity.freeBlockSections + 
                                  ", 亮度: " + blockEntity.signalBrightness + 
                                  ", 天气联动: " + blockEntity.weatherTimeLinkEnabled);
            }
        }
    }
}