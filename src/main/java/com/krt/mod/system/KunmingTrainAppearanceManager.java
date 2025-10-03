package com.krt.mod.system;

import net.minecraft.util.Identifier;
import com.krt.mod.KRTMod;
import com.krt.mod.entity.TrainCar;

import java.util.HashMap;
import java.util.Map;

/**
 * 昆明地铁特色列车外观管理器
 * 负责管理不同线路列车的外观、涂装和特色
 */
public class KunmingTrainAppearanceManager {
    private static final KunmingTrainAppearanceManager INSTANCE = new KunmingTrainAppearanceManager();
    
    // 存储不同线路的列车外观配置
    private final Map<String, TrainAppearanceConfig> lineAppearances = new HashMap<>();
    
    private KunmingTrainAppearanceManager() {
        initializeDefaultAppearances();
    }
    
    public static KunmingTrainAppearanceManager getInstance() {
        return INSTANCE;
    }
    
    // 初始化默认的昆明地铁线路外观
    private void initializeDefaultAppearances() {
        // 1号线 - 蓝色主题
        lineAppearances.put("line1", new TrainAppearanceConfig(
            new Identifier(KRTMod.MOD_ID, "textures/entity/train/line1_head.png"),
            new Identifier(KRTMod.MOD_ID, "textures/entity/train/line1_middle.png"),
            new Identifier(KRTMod.MOD_ID, "textures/entity/train/line1_tail.png"),
            "昆明地铁1号线",
            "蓝色科技"
        ));
        
        // 2号线 - 红色主题
        lineAppearances.put("line2", new TrainAppearanceConfig(
            new Identifier(KRTMod.MOD_ID, "textures/entity/train/line2_head.png"),
            new Identifier(KRTMod.MOD_ID, "textures/entity/train/line2_middle.png"),
            new Identifier(KRTMod.MOD_ID, "textures/entity/train/line2_tail.png"),
            "昆明地铁2号线",
            "红色文化"
        ));
        
        // 3号线 - 橙色主题
        lineAppearances.put("line3", new TrainAppearanceConfig(
            new Identifier(KRTMod.MOD_ID, "textures/entity/train/line3_head.png"),
            new Identifier(KRTMod.MOD_ID, "textures/entity/train/line3_middle.png"),
            new Identifier(KRTMod.MOD_ID, "textures/entity/train/line3_tail.png"),
            "昆明地铁3号线",
            "橙色活力"
        ));
        
        // 4号线 - 绿色主题
        lineAppearances.put("line4", new TrainAppearanceConfig(
            new Identifier(KRTMod.MOD_ID, "textures/entity/train/line4_head.png"),
            new Identifier(KRTMod.MOD_ID, "textures/entity/train/line4_middle.png"),
            new Identifier(KRTMod.MOD_ID, "textures/entity/train/line4_tail.png"),
            "昆明地铁4号线",
            "绿色生态"
        ));
        
        // 5号线 - 紫色主题
        lineAppearances.put("line5", new TrainAppearanceConfig(
            new Identifier(KRTMod.MOD_ID, "textures/entity/train/line5_head.png"),
            new Identifier(KRTMod.MOD_ID, "textures/entity/train/line5_middle.png"),
            new Identifier(KRTMod.MOD_ID, "textures/entity/train/line5_tail.png"),
            "昆明地铁5号线",
            "紫色人文"
        ));
        
        // 特色主题列车
        lineAppearances.put("flower_festival", new TrainAppearanceConfig(
            new Identifier(KRTMod.MOD_ID, "textures/entity/train/flower_head.png"),
            new Identifier(KRTMod.MOD_ID, "textures/entity/train/flower_middle.png"),
            new Identifier(KRTMod.MOD_ID, "textures/entity/train/flower_tail.png"),
            "昆明花卉节主题列车",
            "四季花开"
        ));
        
        lineAppearances.put("ethnic_culture", new TrainAppearanceConfig(
            new Identifier(KRTMod.MOD_ID, "textures/entity/train/ethnic_head.png"),
            new Identifier(KRTMod.MOD_ID, "textures/entity/train/ethnic_middle.png"),
            new Identifier(KRTMod.MOD_ID, "textures/entity/train/ethnic_tail.png"),
            "云南民族文化主题列车",
            "多彩民族"
        ));
    }
    
    // 获取指定线路的列车外观配置
    public TrainAppearanceConfig getAppearanceForLine(String lineId) {
        return lineAppearances.getOrDefault(lineId, lineAppearances.get("line1")); // 默认使用1号线外观
    }
    
    // 为特定列车设置外观
    public void setTrainAppearance(TrainCar car, String lineId) {
        TrainAppearanceConfig config = getAppearanceForLine(lineId);
        if (config != null) {
            Identifier texture;
            
            switch (car.getCarType()) {
                case HEAD_CAR:
                    texture = config.headTexture;
                    break;
                case MIDDLE_CAR:
                    texture = config.middleTexture;
                    break;
                case TAIL_CAR:
                    texture = config.tailTexture;
                    break;
                default:
                    texture = config.middleTexture;
                    break;
            }
            
            // 应用纹理到车辆
            car.setTexture(texture);
            car.setLineName(config.lineName);
            car.setThemeDescription(config.themeDescription);
        }
    }
    
    // 列车外观配置类
    public static class TrainAppearanceConfig {
        private final Identifier headTexture;
        private final Identifier middleTexture;
        private final Identifier tailTexture;
        private final String lineName;
        private final String themeDescription;
        
        public TrainAppearanceConfig(Identifier headTexture, Identifier middleTexture, 
                                   Identifier tailTexture, String lineName, String themeDescription) {
            this.headTexture = headTexture;
            this.middleTexture = middleTexture;
            this.tailTexture = tailTexture;
            this.lineName = lineName;
            this.themeDescription = themeDescription;
        }
        
        public Identifier getHeadTexture() {
            return headTexture;
        }
        
        public Identifier getMiddleTexture() {
            return middleTexture;
        }
        
        public Identifier getTailTexture() {
            return tailTexture;
        }
        
        public String getLineName() {
            return lineName;
        }
        
        public String getThemeDescription() {
            return themeDescription;
        }
    }
}