package com.krt.mod.entity;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.krt.mod.KRTMod;
import com.krt.mod.sound.ModSounds;
import com.krt.mod.system.BogieSystem;
import com.krt.mod.system.BrakeSystem;
import com.krt.mod.system.PowerSupplySystem;
import com.krt.mod.system.TractionSystem;

/**
 * 车辆类，表示地铁系统中的单个车辆
 */
public class TrainCar {
    // 车辆类型枚举
    public enum CarType {
        HEAD_CAR("车头"),
        MIDDLE_CAR("中间车"),
        TAIL_CAR("车尾"),
        DOUBLE_HEADED_CAR("重联车头"),
        SPECIAL_PURPOSE_CAR("特殊用途车");
        
        private final String displayName;
        
        CarType(String displayName) {
            this.displayName = displayName;
        }
        
        public String getDisplayName() {
            return displayName;
        }
    }
    
    // 车辆状态枚举
    public enum CarStatus {
        OK("正常"),
        WARNING("警告"),
        ERROR("故障"),
        MAINTENANCE("维护中");
        
        private final String displayName;
        
        CarStatus(String displayName) {
            this.displayName = displayName;
        }
        
        public String getDisplayName() {
            return displayName;
        }
    }
    
    private final String carId;
    private final CarType carType;
    private int health; // 车辆健康值
    private CarStatus status;
    private double speed;
    private boolean powered;
    private boolean doorsOpen;
    private int passengers;
    private final int maxPassengers;
    private TrainConsist consist;
    
    // 车辆子系统
    private TractionSystem tractionSystem;
    private BrakeSystem brakeSystem;
    private List<BogieSystem> bogieSystems = new ArrayList<>();
    
    // 车辆基本属性
    private double weight; // 车辆重量（千克）
    private double length; // 车辆长度（米）
    private double width; // 车辆宽度（米）
    private double height; // 车辆高度（米）
    
    // 性能属性
    private double maxAcceleration; // 最大加速度（m/s²）
    private double maxDeceleration; // 最大减速度（m/s²）
    private double maxComfortableSpeed; // 最大舒适速度（km/h）
    
    // 运营属性
    private int operatingHours = 0; // 运行小时数
    private double cleanliness = 100.0; // 清洁度 (0-100)
    private long lastMaintenanceTime; // 上次维护时间戳
    private int passengerCapacityFactor = 100; // 乘客容量系数（%）
    
    // 物理属性
    private double dragCoefficient; // 空气阻力系数
    private double rollingResistanceCoefficient; // 滚动阻力系数
    private double frictionCoefficient; // 摩擦系数
    
    // 电力属性
    private boolean hasPantograph = false; // 是否安装受电弓
    private boolean hasPowerCollectorShoe = false; // 是否安装受电靴
    private PowerSupplySystem.PowerType powerType = null; // 供电类型
    
    // 车辆状态监控
    private Map<String, Double> customProperties = new HashMap<>(); // 自定义属性
    private List<String> activeWarnings = new ArrayList<>(); // 活跃警告列表
    private List<String> activeErrors = new ArrayList<>(); // 活跃错误列表
    
    // 摇摆效果相关字段
    private Vec3d swayOffset = Vec3d.ZERO; // 摇摆偏移量
    private double currentSwayPhase = 0.0; // 当前摇摆相位
    private double swayFrequency = 2.0; // 摇摆频率
    private double swayAmplitude = 0.05; // 摇摆幅度
    private float tiltAngle = 0.0f; // 倾斜角度 (度)
    private double lateralForce; // 横向力
    private double verticalForce; // 垂直力
    
    /**
     * 创建新车
     */
    public TrainCar(String carId, CarType carType, int maxPassengers) {
        this.carId = carId;
        this.carType = carType;
        this.maxPassengers = maxPassengers;
        this.health = 100;
        this.status = CarStatus.OK;
        this.speed = 0;
        this.powered = false;
        this.doorsOpen = false;
        this.passengers = 0;
        this.lastMaintenanceTime = System.currentTimeMillis();
        
        // 初始化电力属性
        this.hasPantograph = false;
        this.hasPowerCollectorShoe = false;
        this.powerType = null;
        
        // 根据车辆类型设置默认值
        switch (carType) {
            case HEAD_CAR -> {
                weight = 40000; // 车头更重，40吨
                length = 20.0; // 米
                width = 3.0; // 米
                height = 3.8; // 米
                maxAcceleration = 1.2; // m/s²
                maxDeceleration = 1.3; // m/s²
                maxComfortableSpeed = 80.0; // km/h
                dragCoefficient = 0.8;
                rollingResistanceCoefficient = 0.001;
                frictionCoefficient = 0.3;
                
                // 车头有牵引系统
                tractionSystem = new TractionSystem(this);
                // 车头有两个动力转向架
                bogieSystems.add(new BogieSystem(this, BogieSystem.BogieType.POWERED));
                bogieSystems.add(new BogieSystem(this, BogieSystem.BogieType.POWERED));
            }
            case TAIL_CAR -> {
                weight = 38000; // 尾车38吨
                length = 20.0; // 米
                width = 3.0; // 米
                height = 3.8; // 米
                maxAcceleration = 1.2; // m/s²
                maxDeceleration = 1.3; // m/s²
                maxComfortableSpeed = 80.0; // km/h
                dragCoefficient = 0.8;
                rollingResistanceCoefficient = 0.001;
                frictionCoefficient = 0.3;
                
                // 尾车有牵引系统
                tractionSystem = new TractionSystem(this);
                // 尾车有两个动力转向架
                bogieSystems.add(new BogieSystem(this, BogieSystem.BogieType.POWERED));
                bogieSystems.add(new BogieSystem(this, BogieSystem.BogieType.POWERED));
            }
            case MIDDLE_CAR -> {
                weight = 35000; // 中间车35吨
                length = 19.5; // 米
                width = 3.0; // 米
                height = 3.8; // 米
                maxAcceleration = 1.0; // m/s²
                maxDeceleration = 1.3; // m/s²
                maxComfortableSpeed = 80.0; // km/h
                dragCoefficient = 0.7;
                rollingResistanceCoefficient = 0.001;
                frictionCoefficient = 0.3;
                
                // 中间车没有牵引系统
                // 中间车有两个非动力转向架
                bogieSystems.add(new BogieSystem(this, BogieSystem.BogieType.UNPOWERED));
                bogieSystems.add(new BogieSystem(this, BogieSystem.BogieType.UNPOWERED));
            }
            case DOUBLE_HEADED_CAR -> {
                weight = 42000; // 重联车头42吨
                length = 21.0; // 米
                width = 3.0; // 米
                height = 3.8; // 米
                maxAcceleration = 1.3; // m/s²
                maxDeceleration = 1.4; // m/s²
                maxComfortableSpeed = 90.0; // km/h
                dragCoefficient = 0.85;
                rollingResistanceCoefficient = 0.001;
                frictionCoefficient = 0.3;
                
                // 重联车头有更强大的牵引系统
                tractionSystem = new TractionSystem(this);
                // 重联车头有两个增强型动力转向架
                bogieSystems.add(new BogieSystem(this, BogieSystem.BogieType.POWERED));
                bogieSystems.add(new BogieSystem(this, BogieSystem.BogieType.POWERED));
            }
            case SPECIAL_PURPOSE_CAR -> {
                weight = 38000; // 特殊用途车38吨
                length = 19.5; // 米
                width = 3.0; // 米
                height = 4.0; // 米
                maxAcceleration = 0.9; // m/s²
                maxDeceleration = 1.2; // m/s²
                maxComfortableSpeed = 70.0; // km/h
                dragCoefficient = 0.75;
                rollingResistanceCoefficient = 0.001;
                frictionCoefficient = 0.3;
                
                // 特殊用途车有一个动力转向架和一个非动力转向架
                tractionSystem = new TractionSystem(this);
                bogieSystems.add(new BogieSystem(this, BogieSystem.BogieType.POWERED));
                bogieSystems.add(new BogieSystem(this, BogieSystem.BogieType.UNPOWERED));
            }
        }
        
        // 所有车辆都有制动系统
        brakeSystem = new BrakeSystem(this);
        
        // 初始化自定义属性
        initializeCustomProperties();
    }
    
    /**
     * 更新车辆状态
     */
    public void update() {
        // 更新运行时间
        operatingHours++;
        
        // 更新车辆健康状态
        if (health < 30) {
            status = CarStatus.ERROR;
        } else if (health < 70) {
            status = CarStatus.WARNING;
        } else {
            status = CarStatus.OK;
        }
        
        // 如果车辆处于维护中，设置状态
        if (isInMaintenance()) {
            status = CarStatus.MAINTENANCE;
        }
        
        // 更新子系统
        updateSubsystems();
        
        // 检查子系统状态是否影响车辆状态
        checkSubsystemStatus();
        
        // 更新清洁度
        updateCleanliness();
    }
    
    /**
     * 更新子系统
     */
    private void updateSubsystems() {
        if (tractionSystem != null) {
            tractionSystem.update();
        }
        
        if (brakeSystem != null) {
            brakeSystem.update();
        }
        
        for (BogieSystem bogie : bogieSystems) {
            bogie.update();
        }
    }
    
    /**
     * 检查子系统状态
     */
    private void checkSubsystemStatus() {
        // 如果车辆已经在维护中，不需要检查
        if (status == CarStatus.MAINTENANCE) {
            return;
        }
        
        // 如果任何关键子系统故障，车辆也故障
        if (tractionSystem != null && tractionSystem.getStatus() == TractionSystem.TractionStatus.ERROR) {
            status = CarStatus.ERROR;
            return;
        }
        
        if (brakeSystem != null && brakeSystem.getStatus() == BrakeSystem.BrakeStatus.ERROR) {
            status = CarStatus.ERROR;
            return;
        }
        
        // 检查转向架系统
        for (BogieSystem bogie : bogieSystems) {
            if (bogie.getStatus() == BogieSystem.BogieStatus.ERROR) {
                status = CarStatus.ERROR;
                return;
            }
        }
        
        // 如果车辆当前状态不是ERROR，但有子系统警告，车辆也警告
        if (status == CarStatus.OK) {
            if (tractionSystem != null && tractionSystem.getStatus() == TractionSystem.TractionStatus.WARNING) {
                status = CarStatus.WARNING;
                return;
            }
            
            if (brakeSystem != null && brakeSystem.getStatus() == BrakeSystem.BrakeStatus.WARNING) {
                status = CarStatus.WARNING;
                return;
            }
            
            // 检查转向架系统
            for (BogieSystem bogie : bogieSystems) {
                if (bogie.getStatus() == BogieSystem.BogieStatus.WARNING) {
                    status = CarStatus.WARNING;
                    return;
                }
            }
        }
    }
    
    /**
     * 更新清洁度
     */
    private void updateCleanliness() {
        // 模拟清洁度下降
        if (operatingHours % 500 == 0) {
            cleanliness -= 2;
            if (cleanliness < 0) cleanliness = 0;
        }
        
        // 乘客数量对清洁度的影响
        double passengerFactor = (double)passengers / maxPassengers;
        if (passengerFactor > 0.8) {
            if (operatingHours % 300 == 0) {
                cleanliness -= 1;
                if (cleanliness < 0) cleanliness = 0;
            }
        }
    }
    
    /**
     * 应用动力
     */
    public void applyPower(double powerLevel) {
        if (status != CarStatus.OK && status != CarStatus.WARNING) {
            return; // 如果车辆状态异常，不响应动力请求
        }
        
        this.powered = true;
        
        // 如果有牵引系统，通过牵引系统应用动力
        if (tractionSystem != null && tractionSystem.getStatus() != TractionSystem.TractionStatus.ERROR) {
            // 简化处理，实际应该从供电系统获取电力
            // 注意：这里暂时不调用applyPower方法，因为缺少PowerSupplySystem参数
            // tractionSystem.applyPower(powerLevel);
            // 直接更新速度
            double powerOutput = powerLevel * 0.95; // 模拟逆变器效率
            // 简化的速度增量计算
            double weightFactor = 1.0 / (getWeight() / 1000.0);
            double speedIncrement = (powerOutput * 0.01) * weightFactor;
            setSpeed(getSpeed() + speedIncrement);
        }
        
        // 通过转向架系统传递动力
        if (hasPoweredBogies()) {
            for (BogieSystem bogie : bogieSystems) {
                if (bogie.isPowered()) {
                    // 简化处理，实际应该从牵引系统获取力
                    bogie.transmitTractionForce(powerLevel * 0.5, tractionSystem);
                }
            }
        }
    }
    
    /**
     * 应用制动
     */
    public void applyBrake(double brakeLevel) {
        if (status != CarStatus.OK && status != CarStatus.WARNING) {
            return; // 如果车辆状态异常，不响应制动请求
        }
        
        this.powered = false;
        
        // 如果有制动系统，通过制动系统应用制动
        if (brakeSystem != null && brakeSystem.getStatus() != BrakeSystem.BrakeStatus.ERROR) {
            // 根据制动级别应用常用制动
            double brakeLevelNormalized = Math.min(1.0, brakeLevel / 100.0);
            brakeSystem.applyServiceBrake(brakeLevelNormalized);
        }
        
        // 通过转向架系统传递制动力
        for (BogieSystem bogie : bogieSystems) {
            // 简化处理，实际应该从制动系统获取力
            bogie.transmitBrakeForce(brakeLevel * 0.5, brakeSystem);
        }
    }
    
    /**
     * 打开车门
     */
    public void openDoors() {
        if (speed > 0.1) {
            return; // 车辆移动中，不能打开车门
        }
        
        this.doorsOpen = true;
        // 播放开门声音
        if (consist != null && consist.getTrainEntity() != null && !consist.getTrainEntity().world.isClient) {
            consist.getTrainEntity().world.playSound(null, consist.getTrainEntity().getBlockPos(), 
                    ModSounds.DOOR_OPEN_SOUND, net.minecraft.sound.SoundCategory.NEUTRAL, 1.0F, 1.0F);
        }
    }
    
    /**
     * 关闭车门
     */
    public void closeDoors() {
        this.doorsOpen = false;
        // 播放关门声音
        if (consist != null && consist.getTrainEntity() != null && !consist.getTrainEntity().world.isClient) {
            consist.getTrainEntity().world.playSound(null, consist.getTrainEntity().getBlockPos(), 
                    ModSounds.DOOR_CLOSE_SOUND, net.minecraft.sound.SoundCategory.NEUTRAL, 1.0F, 1.0F);
        }
    }
    
    /**
     * 进入维护模式
     */
    public void startMaintenance() {
        this.status = CarStatus.MAINTENANCE;
        // 维护时应该停下列车并清空乘客
        this.speed = 0;
        this.doorsOpen = true;
        this.powered = false;
    }
    
    /**
     * 结束维护模式
     */
    public void endMaintenance() {
        // 维护后恢复满健康值
        repair();
        this.status = CarStatus.OK;
        this.doorsOpen = false;
    }
    
    /**
     * 检查是否处于维护模式
     */
    public boolean isInMaintenance() {
        return status == CarStatus.MAINTENANCE;
    }
    
    /**
     * 增加乘客
     */
    public boolean addPassenger() {
        if (passengers < maxPassengers) {
            passengers++;
            return true;
        }
        return false;
    }
    
    /**
     * 减少乘客
     */
    public boolean removePassenger() {
        if (passengers > 0) {
            passengers--;
            return true;
        }
        return false;
    }
    
    /**
     * 保存到NBT
     */
    public NbtCompound toNbt() {
        NbtCompound nbt = new NbtCompound();
        nbt.putString("carId", carId);
        nbt.putString("carType", carType.name());
        nbt.putInt("health", health);
        nbt.putString("status", status.name());
        nbt.putDouble("speed", speed);
        nbt.putBoolean("powered", powered);
        nbt.putBoolean("doorsOpen", doorsOpen);
        nbt.putInt("passengers", passengers);
        
        // 保存新增属性
        nbt.putDouble("weight", weight);
        nbt.putInt("operatingHours", operatingHours);
        nbt.putDouble("cleanliness", cleanliness);
        
        // 保存电力属性
        nbt.putBoolean("hasPantograph", hasPantograph);
        nbt.putBoolean("hasPowerCollectorShoe", hasPowerCollectorShoe);
        if (powerType != null) {
            nbt.putString("powerType", powerType.name());
        }
        
        // 保存子系统数据
        // 牵引系统
        if (tractionSystem != null) {
            nbt.putBoolean("hasTractionSystem", true);
            // 这里应该保存牵引系统的NBT数据
        }
        
        // 制动系统
        if (brakeSystem != null) {
            nbt.putBoolean("hasBrakeSystem", true);
            // 这里应该保存制动系统的NBT数据
        }
        
        // 转向架系统
        NbtList bogieList = new NbtList();
        for (int i = 0; i < bogieSystems.size(); i++) {
            BogieSystem bogie = bogieSystems.get(i);
            NbtCompound bogieNbt = new NbtCompound();
            bogieNbt.putString("type", bogie.getType().name());
            bogieNbt.putInt("health", bogie.getHealth());
            // 保存其他转向架数据
            bogieList.add(bogieNbt);
        }
        nbt.put("bogieSystems", bogieList);
        
        return nbt;
    }
    
    /**
     * 从NBT加载
     */
    public static TrainCar fromNbt(NbtCompound nbt) {
        String carId = nbt.getString("carId");
        CarType carType = CarType.valueOf(nbt.getString("carType"));
        int maxPassengers = 10; // 默认值，实际应该从配置中获取
        
        TrainCar car = new TrainCar(carId, carType, maxPassengers);
        car.health = nbt.getInt("health");
        car.status = CarStatus.valueOf(nbt.getString("status"));
        car.speed = nbt.getDouble("speed");
        car.powered = nbt.getBoolean("powered");
        car.doorsOpen = nbt.getBoolean("doorsOpen");
        car.passengers = nbt.getInt("passengers");
        
        // 加载新增属性
        if (nbt.contains("weight")) {
            car.weight = nbt.getDouble("weight");
        }
        if (nbt.contains("operatingHours")) {
            car.operatingHours = nbt.getInt("operatingHours");
        }
        if (nbt.contains("cleanliness")) {
            car.cleanliness = nbt.getDouble("cleanliness");
        }
        
        // 加载电力属性
        if (nbt.contains("hasPantograph")) {
            car.hasPantograph = nbt.getBoolean("hasPantograph");
        }
        if (nbt.contains("hasPowerCollectorShoe")) {
            car.hasPowerCollectorShoe = nbt.getBoolean("hasPowerCollectorShoe");
        }
        if (nbt.contains("powerType")) {
            car.powerType = PowerSupplySystem.PowerType.valueOf(nbt.getString("powerType"));
        }
        
        // 加载子系统数据（简化版）
        // 转向架系统
        if (nbt.contains("bogieSystems")) {
            NbtList bogieList = nbt.getList("bogieSystems", 10);
            car.bogieSystems.clear();
            for (int i = 0; i < bogieList.size(); i++) {
                NbtCompound bogieNbt = bogieList.getCompound(i);
                BogieSystem.BogieType bogieType = BogieSystem.BogieType.valueOf(bogieNbt.getString("type"));
                BogieSystem bogie = new BogieSystem(car, bogieType);
                // 设置其他转向架数据
                car.bogieSystems.add(bogie);
            }
        }
        
        return car;
    }
    
    /**
     * 维修车辆
     */
    public void repair() {
        this.health = 100;
        this.cleanliness = 100;
        
        // 维修子系统
        if (tractionSystem != null) {
            tractionSystem.repair();
        }
        if (brakeSystem != null) {
            brakeSystem.repair();
        }
        for (BogieSystem bogie : bogieSystems) {
            bogie.repair();
        }
        
        this.status = CarStatus.OK;
    }
    
    /**
     * 检查车辆是否有牵引系统
     */
    public boolean hasTractionSystem() {
        return tractionSystem != null;
    }
    
    /**
     * 检查车辆是否有动力转向架
     */
    public boolean hasPoweredBogies() {
        for (BogieSystem bogie : bogieSystems) {
            if (bogie.isPowered()) {
                return true;
            }
        }
        return false;
    }
    
    // Getters and setters
    public String getCarId() {
        return carId;
    }
    
    public CarType getCarType() {
        return carType;
    }
    
    public int getHealth() {
        return health;
    }
    
    public void setHealth(int health) {
        this.health = Math.max(0, Math.min(100, health));
    }
    
    public CarStatus getStatus() {
        return status;
    }
    
    public void setStatus(CarStatus status) {
        this.status = status;
    }
    
    public double getSpeed() {
        return speed;
    }
    
    public void setSpeed(double speed) {
        this.speed = speed;
    }
    
    public boolean isPowered() {
        return powered;
    }
    
    public boolean areDoorsOpen() {
        return doorsOpen;
    }
    
    public int getPassengers() {
        return passengers;
    }
    
    public int getMaxPassengers() {
        return maxPassengers;
    }
    
    public TrainConsist getConsist() {
        return consist;
    }
    
    public void setConsist(TrainConsist consist) {
        this.consist = consist;
    }
    
    public TractionSystem getTractionSystem() {
        return tractionSystem;
    }
    
    public BrakeSystem getBrakeSystem() {
        return brakeSystem;
    }
    
    public List<BogieSystem> getBogieSystems() {
        return new ArrayList<>(bogieSystems);
    }
    
    /**
     * 获取摇摆偏移量
     */// 获取摇摆偏移量
    public Vec3d getSwayOffset() {
        return swayOffset;
    }
    
    /**
     * 初始化自定义属性
     */
    private void initializeCustomProperties() {
        customProperties.put("energyEfficiency", 0.95); // 能源效率
        customProperties.put("noiseLevel", 65.0); // 噪音水平（dB）
        customProperties.put("vibrationLevel", 0.1); // 振动水平
        customProperties.put("lightingBrightness", 1.0); // 照明亮度
        customProperties.put("airConditioningStatus", 1.0); // 空调状态
    }
    
    /**
     * 设置自定义属性
     */
    public void setCustomProperty(String key, double value) {
        customProperties.put(key, value);
    }
    
    /**
     * 获取自定义属性
     */
    public double getCustomProperty(String key) {
        return customProperties.getOrDefault(key, 0.0);
    }
    
    /**
     * 添加警告
     */
    public void addWarning(String warning) {
        if (!activeWarnings.contains(warning)) {
            activeWarnings.add(warning);
        }
        // 如果之前状态是OK，现在变为WARNING
        if (status == CarStatus.OK) {
            status = CarStatus.WARNING;
        }
    }
    
    /**
     * 移除警告
     */
    public void removeWarning(String warning) {
        activeWarnings.remove(warning);
        // 如果没有警告且没有错误，恢复到OK状态
        if (activeWarnings.isEmpty() && activeErrors.isEmpty()) {
            status = CarStatus.OK;
        }
    }
    
    /**
     * 添加错误
     */
    public void addError(String error) {
        if (!activeErrors.contains(error)) {
            activeErrors.add(error);
        }
        // 错误状态优先级最高
        status = CarStatus.ERROR;
    }
    
    /**
     * 移除错误
     */
    public void removeError(String error) {
        activeErrors.remove(error);
        // 如果没有错误但有警告，变为WARNING状态
        if (activeErrors.isEmpty()) {
            if (activeWarnings.isEmpty()) {
                status = CarStatus.OK;
            } else {
                status = CarStatus.WARNING;
            }
        }
    }
    
    /**
     * 计算车辆受到的总阻力
     */
    public double calculateTotalResistance() {
        // 空气阻力: 0.5 * ρ * v² * A * Cd
        double airDensity = 1.225; // 空气密度 kg/m³
        double frontalArea = width * height; // 迎风面积
        double airResistance = 0.5 * airDensity * Math.pow(speed, 2) * frontalArea * dragCoefficient;
        
        // 滚动阻力: μ * m * g
        double gravity = 9.81; // 重力加速度
        double rollingResistance = rollingResistanceCoefficient * weight * gravity;
        
        return airResistance + rollingResistance;
    }
    
    /**
     * 更新车辆物理状态
     */
    public void updatePhysicsState(double deltaTime) {
        // 更新摇摆效果
        updateSwayEffect(deltaTime);
        
        // 计算横向力
        lateralForce = calculateLateralForce();
        
        // 计算垂直力
        verticalForce = calculateVerticalForce();
        
        // 更新清洁度（随时间降低）
        if (speed > 0) {
            cleanliness -= 0.0001 * deltaTime;
            cleanliness = Math.max(0, Math.min(100, cleanliness));
        }
        
        // 更新运行小时数
        operatingHours += deltaTime / 3600000.0; // 转换为小时
    }
    
    /**
     * 计算横向力
     */
    private double calculateLateralForce() {
        // 简化的横向力计算，基于速度和轨道曲率
        return 0.1 * weight * speed * tiltAngle * Math.PI / 180;
    }
    
    /**
     * 计算垂直力
     */
    private double calculateVerticalForce() {
        // 简化的垂直力计算，考虑重力和加速度
        double gravity = 9.81;
        return weight * gravity;
    }
    
    /**
     * 更新摇摆效果
     */
    public void updateSwayEffect(double deltaTime) {
        // 根据速度和加速度调整摇摆参数
        double baseAmplitude = 0.05;
        double speedFactor = Math.min(speed / 30.0, 1.0); // 归一化速度因子
        
        swayAmplitude = baseAmplitude * (1 + speedFactor);
        currentSwayPhase += swayFrequency * deltaTime / 1000.0;
        
        // 计算新的摇摆偏移
        double xOffset = Math.sin(currentSwayPhase) * swayAmplitude;
        double zOffset = Math.cos(currentSwayPhase) * swayAmplitude * 0.5;
        
        swayOffset = new Vec3d(xOffset, 0, zOffset);
        
        // 根据横向力调整倾斜角度
        tiltAngle = (float)(lateralForce / (weight * 1000));
    }
    
    /**
     * 执行维护操作
     */
    public void performMaintenance() {
        // 重置健康值
        health = 100;
        
        // 重置清洁度
        cleanliness = 100.0;
        
        // 清除警告和错误
        activeWarnings.clear();
        activeErrors.clear();
        
        // 更新状态
        status = CarStatus.OK;
        
        // 记录维护时间
        lastMaintenanceTime = System.currentTimeMillis();
        
        // 重置子系统状态
        if (tractionSystem != null) {
            tractionSystem.repair();
        }
        if (brakeSystem != null) {
            brakeSystem.repair();
        }
        for (BogieSystem bogie : bogieSystems) {
            bogie.repair();
        }
    }
    
    /**
     * 计算维护需求度
     */
    public double calculateMaintenanceRequirement() {
        // 基于运行小时数、健康值和清洁度计算维护需求
        double hourFactor = Math.min(operatingHours / 10000.0, 1.0); // 10000小时为满需求
        double healthFactor = (100 - health) / 100.0;
        double cleanlinessFactor = (100 - cleanliness) / 100.0;
        
        return (hourFactor * 0.4) + (healthFactor * 0.4) + (cleanlinessFactor * 0.2);
    }
    
    /**
     * 获取实际乘客容量
     */
    public int getEffectivePassengerCapacity() {
        return (int)(maxPassengers * passengerCapacityFactor / 100.0);
    }
    
    /**
     * 设置乘客容量系数
     */
    public void setPassengerCapacityFactor(int factor) {
        this.passengerCapacityFactor = Math.max(0, Math.min(150, factor));
    }
    
    // getter和setter方法
    public double getLength() { return length; }
    public double getWidth() { return width; }
    public double getHeight() { return height; }
    public double getMaxAcceleration() { return maxAcceleration; }
    public double getMaxDeceleration() { return maxDeceleration; }
    public double getMaxComfortableSpeed() { return maxComfortableSpeed; }
    public double getDragCoefficient() { return dragCoefficient; }
    public double getRollingResistanceCoefficient() { return rollingResistanceCoefficient; }
    public double getFrictionCoefficient() { return frictionCoefficient; }
    public long getLastMaintenanceTime() { return lastMaintenanceTime; }
    public List<String> getActiveWarnings() { return new ArrayList<>(activeWarnings); }
    public List<String> getActiveErrors() { return new ArrayList<>(activeErrors); }
    public double getLateralForce() { return lateralForce; }
    public double getVerticalForce() { return verticalForce; }
    
    public void setLength(double length) { this.length = length; }
    public void setWidth(double width) { this.width = width; }
    public void setHeight(double height) { this.height = height; }
    public void setMaxAcceleration(double maxAcceleration) { this.maxAcceleration = maxAcceleration; }
    public void setMaxDeceleration(double maxDeceleration) { this.maxDeceleration = maxDeceleration; }
    public void setMaxComfortableSpeed(double maxComfortableSpeed) { this.maxComfortableSpeed = maxComfortableSpeed; }
    public void setDragCoefficient(double dragCoefficient) {
        this.dragCoefficient = dragCoefficient;
    }
    
    // 电力相关方法
    public boolean hasPantograph() {
        return hasPantograph;
    }
    
    public void setHasPantograph(boolean hasPantograph) {
        this.hasPantograph = hasPantograph;
    }
    
    public boolean hasPowerCollectorShoe() {
        return hasPowerCollectorShoe;
    }
    
    public void setHasPowerCollectorShoe(boolean hasPowerCollectorShoe) {
        this.hasPowerCollectorShoe = hasPowerCollectorShoe;
    }
    
    public PowerSupplySystem.PowerType getPowerType() {
        return powerType;
    }
    
    public void setPowerType(PowerSupplySystem.PowerType powerType) {
        this.powerType = powerType;
    }
    
    /**
     * 检查列车是否可以从当前供电系统获取电力
     */
    public boolean canReceivePower(PowerSupplySystem.PowerType availablePowerType) {
        if (availablePowerType == null) return false;
        
        switch (availablePowerType) {
            case OVERHEAD_WIRE:
                return hasPantograph;
            case THIRD_RAIL:
                return hasPowerCollectorShoe;
            case BATTERY:
                return true; // 电池可以作为应急电源
            default:
                return false;
        }
    }
    public void setRollingResistanceCoefficient(double rollingResistanceCoefficient) { this.rollingResistanceCoefficient = rollingResistanceCoefficient; }
    public void setFrictionCoefficient(double frictionCoefficient) { this.frictionCoefficient = frictionCoefficient; }
    
    /**
     * 设置摇摆偏移量
     */
    public void setSwayOffset(Vec3d offset) {
        this.swayOffset = offset;
    }
    
    /**
     * 获取倾斜角度
     */
    public float getTiltAngle() {
        return tiltAngle;
    }
    
    /**
     * 设置倾斜角度
     */
    public void setTiltAngle(float angle) {
        this.tiltAngle = angle;
    }
    
    public double getWeight() {
        return weight;
    }
    
    public void setWeight(double weight) {
        this.weight = weight;
    }
    
    public int getOperatingHours() {
        return operatingHours;
    }
    
    public double getCleanliness() {
        return cleanliness;
    }
    
    /**
     * 获取当前负载因子
     */
    public double getCurrentLoadFactor() {
        if (maxPassengers == 0) {
            return 0.0;
        }
        return (double) passengers / maxPassengers;
    }
    
    /**
     * 获取轨道条件因子（表示轨道状况好坏的值，0-1之间）
     */
    public double getTrackConditionFactor() {
        // 简化实现，返回良好轨道条件的默认值
        return 1.0;
    }
    
    /**
     * 获取当前轨道坡度（以弧度表示）
     */
    public double getCurrentTrackGrade() {
        // 简化实现，返回平坦轨道的默认值
        return 0.0;
    }
    
    /**
     * 获取车辆迎风面积
     */
    public double getFrontalArea() {
        // 简化实现，返回一个合理的默认值（平方米）
        return 8.0;
    }
    

    
    /**
     * 获取当前轨道曲线半径（米）
     */
    public double getCurrentTrackCurveRadius() {
        // 简化实现，返回直线路段的默认值（很大的数值表示直线）
        return 100000.0;
    }
    
    /**
     * 获取当前轨道超高（度）
     */
    public double getCurrentTrackSuperelevation() {
        // 简化实现，返回无超高的默认值
        return 0.0;
    }
    
    public void setCleanliness(double cleanliness) {
        this.cleanliness = Math.max(0, Math.min(100, cleanliness));
    }
    
    /**
     * 获取车辆信息文本
     */
    public Text getInfoText() {
        String hasTraction = hasTractionSystem() ? "有" : "无";
        String bogieCount = String.valueOf(bogieSystems.size());
        String poweredBogies = hasPoweredBogies() ? "有" : "无";
        
        return Text.literal(
            carId + " - " + carType.getDisplayName() + ", 状态: " + status.getDisplayName() + ", 健康: " + health + "%" +
            ", 乘客: " + passengers + "/" + maxPassengers + ", 速度: " + String.format("%.1f km/h", speed) +
            ", 车门: " + (doorsOpen ? "打开" : "关闭") + ", 清洁度: " + String.format("%.1f%%", cleanliness) +
            ", 牵引系统: " + hasTraction + ", 转向架数: " + bogieCount + ", 动力转向架: " + poweredBogies
        );
    }
}