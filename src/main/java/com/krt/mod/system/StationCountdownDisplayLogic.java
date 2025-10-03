package com.krt.mod.system;

import com.krt.mod.block.TrainArrivalCountdownBlock;
// import com.krt.mod.blockentity.StationCountdownDisplayBlockEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 车站倒计时显示屏逻辑处理类
 * 负责处理倒计时显示屏的显示逻辑和用户交互功能
 */
public class StationCountdownDisplayLogic {
    // 单例实例
    private static StationCountdownDisplayLogic instance;
    
    // 显示屏缓存，减少重复计算
    private Map<String, DisplayCacheData> displayCache = new HashMap<>();
    
    // 缓存有效期（毫秒）
    private static final long CACHE_EXPIRY = 500; // 0.5秒
    
    /**
     * 获取单例实例
     */
    public static synchronized StationCountdownDisplayLogic getInstance() {
        if (instance == null) {
            instance = new StationCountdownDisplayLogic();
        }
        return instance;
    }
    
    /**
     * 计算并获取倒计时显示屏的显示文本
     * 
     * @param world 世界对象
     * @param pos 方块位置
     * @param showDetails 是否显示详细信息
     * @return 显示文本
     */
    public String getDisplayText(World world, BlockPos pos, boolean showDetails) {
        // 检查方块实体是否为StationCountdownDisplayBlockEntity
        BlockEntity blockEntity = world.getBlockEntity(pos);
        if (!(blockEntity instanceof StationCountdownDisplayBlockEntity)) {
            return "无效设备";
        }
        
        StationCountdownDisplayBlockEntity countdownEntity = (StationCountdownDisplayBlockEntity) blockEntity;
        
        // 获取车站ID
        String stationId = getStationIdForDisplay(world, pos);
        if (stationId == null) {
            return "未连接车站";
        }
        
        // 构建缓存键
        String cacheKey = stationId + ":" + showDetails;
        
        // 检查缓存是否有效
        DisplayCacheData cacheData = displayCache.get(cacheKey);
        if (cacheData != null && System.currentTimeMillis() - cacheData.timestamp < CACHE_EXPIRY) {
            return cacheData.displayText;
        }
        
        // 获取TrainArrivalTimeCalculator实例
        TrainArrivalTimeCalculator calculator = TrainArrivalTimeCalculator.getInstance();
        
        // 获取最近到站列车信息
        Optional<TrainArrivalTimeCalculator.ArrivalTimeInfo> arrivalInfoOpt = calculator.getNextTrainArrivalInfo(world, stationId);
        
        String displayText = "暂无列车信息";
        
        if (arrivalInfoOpt.isPresent()) {
            TrainArrivalTimeCalculator.ArrivalTimeInfo arrivalInfo = arrivalInfoOpt.get();
            
            // 格式化显示文本
            displayText = formatDisplayText(arrivalInfo, showDetails);
            
            // 更新缓存
            cacheData = new DisplayCacheData(displayText, System.currentTimeMillis());
            displayCache.put(cacheKey, cacheData);
        }
        
        // 更新方块实体的显示文本
        countdownEntity.setDisplayText(displayText);
        
        return displayText;
    }
    
    /**
     * 格式化显示文本
     */
    private String formatDisplayText(TrainArrivalTimeCalculator.ArrivalTimeInfo arrivalInfo, boolean showDetails) {
        StringBuilder sb = new StringBuilder();
        
        // 添加列车号
        sb.append("列车号: " + arrivalInfo.getTrainId() + "\n");
        
        // 添加方向信息
        sb.append("方向: " + arrivalInfo.getDirection() + "\n");
        
        // 添加到站倒计时
        int arrivalTimeSeconds = arrivalInfo.getArrivalTimeSeconds();
        if (arrivalTimeSeconds < 60) {
            sb.append("即将进站\n");
        } else {
            int minutes = arrivalTimeSeconds / 60;
            int seconds = arrivalTimeSeconds % 60;
            sb.append(String.format("到站时间: %d分%d秒\n", minutes, seconds));
        }
        
        // 如果显示详细信息，添加更多内容
        if (showDetails) {
            // 添加当前位置
            sb.append("当前位置: " + arrivalInfo.getCurrentLocation() + "\n");
            
            // 添加状态消息
            String statusMessage = arrivalInfo.getStatusMessage();
            if (statusMessage != null && !statusMessage.isEmpty()) {
                sb.append("状态: " + statusMessage + "\n");
            }
            
            // 添加预计停留时间
            if (arrivalInfo.getDwellTimeSeconds() > 0) {
                sb.append("停留时间: " + arrivalInfo.getDwellTimeSeconds() + "秒\n");
            }
        }
        
        return sb.toString();
    }
    
    /**
     * 获取显示屏所属的车站ID
     */
    private String getStationIdForDisplay(World world, BlockPos pos) {
        // 这里实现获取显示屏所属车站的逻辑
        // 可以通过检查周围方块或配置信息来确定
        // 简化实现：返回固定车站ID作为示例
        return "station_main";
    }
    
    /**
     * 刷新所有倒计时显示屏
     */
    public void refreshAllDisplays(World world) {
        // 清除缓存
        displayCache.clear();
        
        // 这里可以实现遍历所有显示屏并刷新的逻辑
        // 由于Minecraft的限制，可能需要通过其他方式实现
    }
    
    /**
     * 缓存数据类
     */
    private static class DisplayCacheData {
        private final String displayText;
        private final long timestamp;
        
        public DisplayCacheData(String displayText, long timestamp) {
            this.displayText = displayText;
            this.timestamp = timestamp;
        }
    }
}