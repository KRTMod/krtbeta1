package com.krt.mod.system;

import net.minecraft.world.World;
import net.minecraft.util.math.BlockPos;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * 列车发车计时器逻辑处理类
 * 负责处理地铁端门计时器的核心逻辑，包括发车时间计算、倒计时/正计时显示等功能
 */
public class TrainDepartureTimerLogic {
    // 单例实例
    private static final Map<World, TrainDepartureTimerLogic> INSTANCES = new HashMap<>();
    
    // 世界对象
    private final World world;
    
    // ATS系统引用
    private final ATS ats;
    
    // 计时器数据缓存
    private final Map<String, TimerData> timerDataCache = new HashMap<>();
    
    // 缓存有效期（毫秒）
    private static final long CACHE_EXPIRY = 500;
    
    // 随机数生成器，用于模拟数据
    private final Random random = new Random();
    
    /**
     * 获取指定世界的单例实例
     */
    public static synchronized TrainDepartureTimerLogic getInstance(World world) {
        return INSTANCES.computeIfAbsent(world, k -> new TrainDepartureTimerLogic(world));
    }
    
    /**
     * 私有构造函数
     */
    private TrainDepartureTimerLogic(World world) {
        this.world = world;
        this.ats = ATS.getInstance(world);
    }
    
    /**
     * 获取指定位置列车的发车计时器信息
     * 
     * @param pos 计时器位置
     * @return 计时器信息
     */
    public TimerInfo getTimerInfo(BlockPos pos) {
        String cacheKey = pos.toString();
        TimerData cachedData = timerDataCache.get(cacheKey);
        
        // 检查缓存是否有效
        if (cachedData != null && System.currentTimeMillis() - cachedData.timestamp < CACHE_EXPIRY) {
            return cachedData.timerInfo;
        }
        
        try {
            // 计算发车时间差异（模拟数据）
            long now = System.currentTimeMillis();
            
            // 模拟一个随机的计划发车时间，在当前时间前后120秒内
            long scheduledDepartureTime = now + (random.nextInt(241) - 120) * 1000;
            long timeDifference = scheduledDepartureTime - now;
            
            TimerMode mode;
            int minutes;
            int seconds;
            String statusMessage;
            
            // 判断计时器模式（倒计时或正计时）
            if (timeDifference > 0) {
                // 倒计时模式（绿色）：当前时间早于计划发车时间
                mode = TimerMode.COUNTDOWN;
                minutes = (int) (timeDifference / 60000);
                seconds = (int) ((timeDifference % 60000) / 1000);
                statusMessage = "正常运行";
            } else {
                // 正计时模式（红色）：当前时间晚于计划发车时间
                mode = TimerMode.COUNTUP;
                minutes = (int) (-timeDifference / 60000);
                seconds = (int) ((-timeDifference % 60000) / 1000);
                statusMessage = "列车晚点";
            }
            
            // 创建计时器信息
            TimerInfo timerInfo = new TimerInfo(minutes, seconds, mode, statusMessage);
            
            // 更新缓存
            timerDataCache.put(cacheKey, new TimerData(timerInfo, System.currentTimeMillis()));
            
            return timerInfo;
        } catch (Exception e) {
            // 处理可能的异常，返回默认状态
            return new TimerInfo(0, 0, TimerMode.INACTIVE, "系统错误");
        }
    }
    
    /**
     * 计时器模式枚举
     */
    public enum TimerMode {
        COUNTDOWN, // 倒计时（绿色）
        COUNTUP,   // 正计时（红色）
        INACTIVE   // 非活动状态
    }
    
    /**
     * 计时器信息类
     */
    public static class TimerInfo {
        private final int minutes;
        private final int seconds;
        private final TimerMode mode;
        private final String statusMessage;
        
        public TimerInfo(int minutes, int seconds, TimerMode mode, String statusMessage) {
            this.minutes = minutes;
            this.seconds = seconds;
            this.mode = mode;
            this.statusMessage = statusMessage;
        }
        
        public int getMinutes() { return minutes; }
        public int getSeconds() { return seconds; }
        public TimerMode getMode() { return mode; }
        public String getStatusMessage() { return statusMessage; }
        
        /**
         * 获取格式化的时间字符串
         */
        public String getFormattedTime() {
            return String.format("%02d:%02d", minutes, seconds);
        }
        
        /**
         * 检查是否应该显示绿色
         */
        public boolean isGreen() {
            return mode == TimerMode.COUNTDOWN;
        }
        
        /**
         * 检查是否应该显示红色
         */
        public boolean isRed() {
            return mode == TimerMode.COUNTUP;
        }
    }
    
    /**
     * 计时器数据缓存类
     */
    private static class TimerData {
        private final TimerInfo timerInfo;
        private final long timestamp;
        
        public TimerData(TimerInfo timerInfo, long timestamp) {
            this.timerInfo = timerInfo;
            this.timestamp = timestamp;
        }
    }
}