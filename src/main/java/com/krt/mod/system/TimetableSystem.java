package com.krt.mod.system;

import net.minecraft.world.World;
import com.krt.mod.KRTMod;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 时刻表系统
 * 负责管理列车时刻表，支持按高峰期/平峰期调整运营参数
 */
public class TimetableSystem {
    private static final Map<World, TimetableSystem> INSTANCES = new HashMap<>();
    private final Map<String, Timetable> timetables = new ConcurrentHashMap<>();
    private final Map<TemporaryKey, Timetable> temporaryTimetables = new ConcurrentHashMap<>();
    private final World world;
    
    // 时段类型枚举
    public enum TimeSlotType {
        PEAK_HOUR,     // 高峰期
        OFF_PEAK_HOUR, // 平峰期
        NIGHT_HOUR     // 夜间
    }
    
    private TimetableSystem(World world) {
        this.world = world;
    }
    
    public static TimetableSystem getInstance(World world) {
        return INSTANCES.computeIfAbsent(world, TimetableSystem::new);
    }
    
    // 创建新时刻表
    public void createTimetable(String lineId) {
        if (!timetables.containsKey(lineId)) {
            timetables.put(lineId, new Timetable(lineId));
            KRTMod.LOGGER.info("Created new timetable for line: {}", lineId);
        }
    }
    
    // 添加时段到时刻表（带优先级）
    public void addTimeSlot(String lineId, String startTime, String endTime, TimeSlotType type, 
                          double speedFactor, int dwellTimeSeconds, int priority) {
        Timetable timetable = timetables.get(lineId);
        if (timetable != null) {
            timetable.addTimeSlot(startTime, endTime, type, speedFactor, dwellTimeSeconds, priority);
            KRTMod.LOGGER.info("Added time slot to line {}: {} - {} ({}) with priority {}", 
                             lineId, startTime, endTime, type, priority);
        }
    }
    
    // 兼容旧版本的添加时段方法（默认优先级5）
    public void addTimeSlot(String lineId, String startTime, String endTime, TimeSlotType type, 
                          double speedFactor, int dwellTimeSeconds) {
        addTimeSlot(lineId, startTime, endTime, type, speedFactor, dwellTimeSeconds, 5);
    }
    
    // 添加临时时刻表
    public void addTemporaryTimetable(String lineId, LocalDate startDate, LocalDate endDate, Timetable tempTimetable) {
        TemporaryKey key = new TemporaryKey(lineId, startDate, endDate);
        temporaryTimetables.put(key, tempTimetable);
        KRTMod.LOGGER.info("Added temporary timetable for line {} from {} to {}", 
                         lineId, startDate, endDate);
    }
    
    // 移除临时时刻表
    public void removeTemporaryTimetable(String lineId, LocalDate startDate, LocalDate endDate) {
        TemporaryKey key = new TemporaryKey(lineId, startDate, endDate);
        temporaryTimetables.remove(key);
        KRTMod.LOGGER.info("Removed temporary timetable for line {} from {} to {}", 
                         lineId, startDate, endDate);
    }
    
    // 获取当前生效的时刻表（优先临时时刻表）
    private Timetable getEffectiveTimetable(String lineId) {
        LocalDate today = LocalDate.now();
        // 查找覆盖当前日期的临时时刻表
        for (Map.Entry<TemporaryKey, Timetable> entry : temporaryTimetables.entrySet()) {
            TemporaryKey key = entry.getKey();
            if (key.lineId.equals(lineId) && !today.isBefore(key.startDate) && !today.isAfter(key.endDate)) {
                return entry.getValue();
            }
        }
        // 无临时时刻表则返回默认
        return timetables.get(lineId);
    }
    
    // 获取游戏内时间转换为LocalTime
    private LocalTime getGameTimeAsLocalTime() {
        // 获取游戏总刻数（20刻=1秒，游戏日=24000刻）
        long gameTime = world.getTimeOfDay();
        // 转换为游戏内小时（0-23）
        int gameHour = (int)((gameTime % 24000) / 1000);
        // 转换为游戏内分钟（0-59）
        int gameMinute = (int)(((gameTime % 1000) / 1000.0) * 60);
        
        // 游戏内6000刻=黎明（约现实5分钟），映射为早上6点
        // 调整游戏时间到现实时间的映射
        int adjustedHour = (gameHour + 6) % 24;
        return LocalTime.of(adjustedHour, gameMinute);
    }
    
    // 获取当前时段的运营参数
    public TimeSlot getCurrentTimeSlot(String lineId) {
        Timetable timetable = getEffectiveTimetable(lineId);
        if (timetable != null) {
            return timetable.getCurrentTimeSlot(getGameTimeAsLocalTime());
        }
        return null;
    }
    
    // 获取调整后的速度
    public double getAdjustedSpeed(String lineId, double baseSpeed) {
        TimeSlot slot = getCurrentTimeSlot(lineId);
        if (slot != null) {
            return baseSpeed * slot.getSpeedFactor();
        }
        return baseSpeed; // 默认不调整
    }
    
    // 获取当前停站时间（秒）
    public int getCurrentDwellTime(String lineId) {
        TimeSlot slot = getCurrentTimeSlot(lineId);
        if (slot != null) {
            return slot.getDwellTimeSeconds();
        }
        return 30; // 默认停站时间30秒
    }
    
    // 获取当前时段信息文本（用于站台显示）
    public String getCurrentTimeSlotDisplayText(String lineId) {
        TimeSlot slot = getCurrentTimeSlot(lineId);
        if (slot != null) {
            return String.format("时段：%s  停站：%ds", slot.getType().name(), slot.getDwellTimeSeconds());
        }
        return "时段：未知  停站：30s";
    }
    
    // 获取线路时刻表
    public Timetable getTimetable(String lineId) {
        return timetables.get(lineId);
    }
    
    /**
     * 临时时刻表键类
     */
    private static class TemporaryKey {
        public final String lineId;
        public final LocalDate startDate;
        public final LocalDate endDate;
        
        public TemporaryKey(String lineId, LocalDate startDate, LocalDate endDate) {
            this.lineId = lineId;
            this.startDate = startDate;
            this.endDate = endDate;
        }
        
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            TemporaryKey that = (TemporaryKey) o;
            return lineId.equals(that.lineId) && 
                   startDate.equals(that.startDate) && 
                   endDate.equals(that.endDate);
        }
        
        @Override
        public int hashCode() {
            return Objects.hash(lineId, startDate, endDate);
        }
    }
    
    /**
     * 时刻表类
     * 管理特定线路的时刻表信息
     */
    public static class Timetable {
        private final String lineId;
        private final List<TimeSlot> timeSlots = new ArrayList<>();
        private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");
        private static final TimeSlot DEFAULT_SLOT = new TimeSlot(LocalTime.MIN, LocalTime.MAX, 
                                                                TimeSlotType.OFF_PEAK_HOUR, 1.0, 30, 0);
        
        public Timetable(String lineId) {
            this.lineId = lineId;
            // 默认时段配置（可通过外部配置覆盖）
            addDefaultTimeSlots();
        }
        
        private void addDefaultTimeSlots() {
            // 默认高峰期：早7:00-9:00，晚17:00-19:00
            addTimeSlot("07:00", "09:00", TimeSlotType.PEAK_HOUR, 0.8, 45, 5); // 优先级5
            addTimeSlot("17:00", "19:00", TimeSlotType.PEAK_HOUR, 0.8, 45, 5);
            
            // 默认平峰期：9:00-17:00
            addTimeSlot("09:00", "17:00", TimeSlotType.OFF_PEAK_HOUR, 1.0, 30, 5);
            
            // 默认夜间：19:00-次日7:00
            addTimeSlot("19:00", "24:00", TimeSlotType.NIGHT_HOUR, 0.7, 40, 5);
            addTimeSlot("00:00", "07:00", TimeSlotType.NIGHT_HOUR, 0.7, 40, 5);
        }
        
        public void addTimeSlot(String startTime, String endTime, TimeSlotType type, 
                              double speedFactor, int dwellTimeSeconds, int priority) {
            try {
                LocalTime start = LocalTime.parse(startTime, TIME_FORMATTER);
                LocalTime end = LocalTime.parse(endTime, TIME_FORMATTER);
                timeSlots.add(new TimeSlot(start, end, type, speedFactor, dwellTimeSeconds, priority));
            } catch (Exception e) {
                KRTMod.LOGGER.error("Failed to add time slot: {}", e.getMessage());
            }
        }
        
        // 使用传入的时间获取当前时段（支持测试和外部调用）
        public TimeSlot getCurrentTimeSlot(LocalTime currentTime) {
            return timeSlots.stream()
                .filter(slot -> slot.isWithinTimeRange(currentTime))
                .max(Comparator.comparingInt(TimeSlot::getPriority))
                .orElse(DEFAULT_SLOT);
        }
        
        // 兼容旧版本的获取方法（使用系统时间）
        public TimeSlot getCurrentTimeSlot() {
            return getCurrentTimeSlot(LocalTime.now());
        }
        
        public List<TimeSlot> getTimeSlots() {
            return Collections.unmodifiableList(timeSlots);
        }
        
        public String getLineId() {
            return lineId;
        }
    }
    
    /**
     * 时段类
     * 表示一个时间段及其对应的运营参数
     */
    public static class TimeSlot {
        private final LocalTime startTime;
        private final LocalTime endTime;
        private final TimeSlotType type;
        private final double speedFactor;
        private final int dwellTimeSeconds;
        private final int priority; // 优先级，值越高优先级越高
        
        public TimeSlot(LocalTime startTime, LocalTime endTime, TimeSlotType type, 
                      double speedFactor, int dwellTimeSeconds, int priority) {
            this.startTime = startTime;
            this.endTime = endTime;
            this.type = type;
            this.speedFactor = speedFactor;
            this.dwellTimeSeconds = dwellTimeSeconds;
            this.priority = priority;
        }
        
        // 兼容旧版本的构造方法
        public TimeSlot(LocalTime startTime, LocalTime endTime, TimeSlotType type, 
                      double speedFactor, int dwellTimeSeconds) {
            this(startTime, endTime, type, speedFactor, dwellTimeSeconds, 5); // 默认优先级5
        }
        
        // 检查给定时间是否在当前时段内
        public boolean isWithinTimeRange(LocalTime time) {
            // 处理跨午夜的情况
            if (startTime.isAfter(endTime)) {
                return time.isAfter(startTime) || time.isBefore(endTime);
            }
            return !time.isBefore(startTime) && !time.isAfter(endTime);
        }
        
        public LocalTime getStartTime() {
            return startTime;
        }
        
        public LocalTime getEndTime() {
            return endTime;
        }
        
        public TimeSlotType getType() {
            return type;
        }
        
        public double getSpeedFactor() {
            return speedFactor;
        }
        
        public int getDwellTimeSeconds() {
            return dwellTimeSeconds;
        }
        
        public int getPriority() {
            return priority;
        }
        
        @Override
        public String toString() {
            return String.format("TimeSlot{%s-%s, %s, speed:%.1f, dwell:%ds, priority:%d}",
                               startTime, endTime, type, speedFactor, dwellTimeSeconds, priority);
        }
    }
}