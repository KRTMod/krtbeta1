package com.krt.mod.entity;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import com.krt.mod.system.TractionSystem;
import com.krt.mod.system.BrakeSystem;
import com.krt.mod.system.LogSystem;
import com.krt.mod.system.PowerSupplySystem;
import com.krt.mod.system.PowerSupplySystemManager;

/**
 * 列车编组类，表示由多个车辆组成的列车
 */
public class TrainConsist {
    private final List<TrainCar> cars = new ArrayList<>();
    private final String consistId;
    private TrainEntity trainEntity;
    private String lineId;
    private String destination;
    private String nextStation;
    private double maxSpeed;
    private boolean atpEnabled;
    private boolean atoEnabled;
    private int totalPassengers;
    // 列车电力相关
    private int powerLevel = 100; // 0-100
    private int maxPowerLevel = 100;
    private boolean usingExternalPower = false; // 是否使用外部电力
    private int externalPowerLevel = 0; // 外部电力等级
    
    /**
     * 创建列车编组
     */
    public TrainConsist(String consistId) {
        this.consistId = consistId;
        this.maxSpeed = 80.0; // 默认最大速度80km/h
        this.atpEnabled = true;
        this.atoEnabled = true;
        this.totalPassengers = 0;
    }
    
    /**
     * 添加车辆到编组
     */
    public boolean addCar(TrainCar car) {
        if (car.getConsist() != null) {
            return false; // 车辆已经属于另一个编组
        }
        
        cars.add(car);
        car.setConsist(this);
        return true;
    }
    
    /**
     * 从编组中移除车辆
     */
    public boolean removeCar(String carId) {
        for (int i = 0; i < cars.size(); i++) {
            if (cars.get(i).getCarId().equals(carId)) {
                TrainCar car = cars.remove(i);
                car.setConsist(null);
                updateTotalPassengers();
                return true;
            }
        }
        return false;
    }
    
    /**
     * 更新列车编组
     */
    public void update() {
        // 更新所有车辆
        for (TrainCar car : cars) {
            car.update();
        }
        
        // 更新总乘客数
        updateTotalPassengers();
        
        // 更新最大速度（根据所有车辆的状态）
        updateMaxSpeed();
        
        // 检查电力供应
        checkPowerSupply();
        
        // 如果有任何车辆处于故障状态，触发警告
        checkCarStatus();
    }
    
    /**
     * 应用动力到所有车辆
     */
    public void applyPower(double powerLevel) {
        // 计算可用的动力
        int availablePower = this.powerLevel;
        
        // 如果使用外部电力，优先使用外部电力
        if (usingExternalPower && externalPowerLevel > 0) {
            availablePower = Math.min(availablePower + externalPowerLevel * 10, maxPowerLevel);
        }
        
        // 检查是否有足够的动力
        int requiredPower = calculateRequiredPower(powerLevel);
        if (availablePower >= requiredPower) {
            // 消耗动力
            consumePower(requiredPower);
            
            for (TrainCar car : cars) {
                // 只有动力车（车头和车尾）才能接收动力
                if (car.getCarType() == TrainCar.CarType.HEAD_CAR || 
                    car.getCarType() == TrainCar.CarType.TAIL_CAR) {
                    car.applyPower(powerLevel);
                }
            }
        } else {
            // 动力不足，减少动力输出
            double reducedPowerLevel = powerLevel * (double) availablePower / requiredPower;
            for (TrainCar car : cars) {
                // 只有动力车（车头和车尾）才能接收动力
                if (car.getCarType() == TrainCar.CarType.HEAD_CAR || 
                    car.getCarType() == TrainCar.CarType.TAIL_CAR) {
                    car.applyPower(reducedPowerLevel);
                }
            }
            consumePower(availablePower);
        }
    }
    
    /**
     * 计算需要的动力
     */
    private int calculateRequiredPower(double powerLevel) {
        // 根据动力等级和车辆数量计算所需电力
        int basePower = (int)(powerLevel * 2);
        int carFactor = Math.max(1, cars.size() / 2); // 每2辆车增加一个单位的电力需求
        return basePower * carFactor;
    }
    
    /**
     * 消耗电力
     */
    private void consumePower(int amount) {
        // 优先使用外部电力
        if (usingExternalPower && externalPowerLevel > 0) {
            int externalPowerAvailable = externalPowerLevel * 10;
            if (externalPowerAvailable >= amount) {
                // 完全使用外部电力
                externalPowerLevel = Math.max(0, externalPowerLevel - (amount / 10));
                return;
            } else {
                // 部分使用外部电力，剩余使用内部电力
                amount -= externalPowerAvailable;
                externalPowerLevel = 0;
            }
        }
        
        // 使用内部电力
        this.powerLevel = Math.max(0, this.powerLevel - amount);
        
        // 如果电力不足，发出警告
        if (this.powerLevel < 20 && !usingExternalPower) {
            notifyDriver("电力不足，请尽快充电或减速!");
        }
    }
    
    /**
     * 检查电力供应
     */
    private void checkPowerSupply() {
        // 重置外部电力状态
        usingExternalPower = false;
        externalPowerLevel = 0;
        
        // 如果没有列车实体，无法获取位置
        if (trainEntity == null) {
            return;
        }
        
        // 获取列车位置
        BlockPos trainPos = new BlockPos(trainEntity.getX(), trainEntity.getY(), trainEntity.getZ());
        
        // 获取供电系统
        PowerSupplySystem powerSystem = com.krt.mod.system.VehicleSystemInitializer.getPowerSupplySystem();
        if (powerSystem != null) {
            // 检查轨道电力等级
            int railPowerLevel = powerSystem.getRailPowerLevel(trainPos);
            
            if (railPowerLevel > 0) {
                // 列车在电气化轨道上
                usingExternalPower = true;
                externalPowerLevel = railPowerLevel;
                
                // 尝试充电
                rechargeFromExternal(railPowerLevel);
            }
        }
    }
    
    /**
     * 从外部电力系统充电
     */
    private void rechargeFromExternal(int powerLevel) {
        if (powerLevel > 0 && this.powerLevel < this.maxPowerLevel) {
            // 根据外部电力等级计算充电量
            int chargeAmount = powerLevel / 2;
            
            // 充电，不超过最大电量
            this.powerLevel = Math.min(this.powerLevel + chargeAmount, this.maxPowerLevel);
        }
    }
    
    /**
     * 应用紧急制动到所有车辆
     */
    public void applyEmergencyBrake() {
        for (TrainCar car : cars) {
            // 使用最大制动力来实现紧急制动效果
            car.applyBrake(100.0);
        }
    }
    
    /**
     * 释放所有制动
     */
    public void releaseBrakes() {
        for (TrainCar car : cars) {
            // 应用0级制动力来模拟释放制动的效果
            car.applyBrake(0.0);
        }
    }
    
    /**
     * 应用制动到所有车辆
     */
    public void applyBrake(double brakeLevel) {
        for (TrainCar car : cars) {
            car.applyBrake(brakeLevel);
        }
    }
    
    /**
     * 应用停放制动到所有车辆
     */
    public void applyParkingBrake() {
        for (TrainCar car : cars) {
            // 通过制动系统应用停放制动
            if (car.getBrakeSystem() != null) {
                car.getBrakeSystem().applyHoldingBrake();
            }
        }
    }
    
    /**
     * 释放停放制动
     */
    public void releaseParkingBrake() {
        for (TrainCar car : cars) {
            // 通过制动系统释放停放制动
            if (car.getBrakeSystem() != null) {
                car.getBrakeSystem().releaseHoldingBrake();
            }
        }
    }
    
    /**
     * 打开所有车辆的车门
     */
    public void openAllDoors() {
        for (TrainCar car : cars) {
            car.openDoors();
        }
    }
    
    /**
     * 关闭所有车辆的车门
     */
    public void closeAllDoors() {
        for (TrainCar car : cars) {
            car.closeDoors();
        }
    }
    
    /**
     * 检查是否所有车门都已关闭
     */
    public boolean areAllDoorsClosed() {
        for (TrainCar car : cars) {
            if (car.areDoorsOpen()) {
                return false;
            }
        }
        return true;
    }
    
    /**
     * 开始整个编组的维护
     */
    public void startMaintenance() {
        for (TrainCar car : cars) {
            car.startMaintenance();
        }
    }
    
    /**
     * 结束整个编组的维护
     */
    public void endMaintenance() {
        for (TrainCar car : cars) {
            car.endMaintenance();
        }
    }
    
    /**
     * 检查是否有任何车辆处于维护状态
     */
    public boolean isInMaintenance() {
        for (TrainCar car : cars) {
            if (car.isInMaintenance()) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * 获取列车总健康值
     */
    public int getTotalHealth() {
        if (cars.isEmpty()) {
            return 100;
        }
        
        int totalHealth = 0;
        for (TrainCar car : cars) {
            totalHealth += car.getHealth();
        }
        
        return totalHealth / cars.size();
    }
    
    /**
     * 获取总重量
     */
    public double getTotalWeight() {
        if (cars.isEmpty()) {
            return 0;
        }
        
        double totalWeight = 0;
        for (TrainCar car : cars) {
            totalWeight += car.getWeight();
        }
        
        return totalWeight;
    }
    
    /**
     * 获取总运行小时数
     */
    public double getTotalRunningHours() {
        if (cars.isEmpty()) {
            return 0;
        }
        
        double totalHours = 0;
        for (TrainCar car : cars) {
            // 使用getOperatingHours()方法代替不存在的getRunningHours()
            totalHours += car.getOperatingHours();
        }
        
        return totalHours / cars.size();
    }
    
    /**
     * 获取平均清洁度
     */
    public int getAverageCleanliness() {
        if (cars.isEmpty()) {
            return 100;
        }
        
        int totalCleanliness = 0;
        for (TrainCar car : cars) {
            totalCleanliness += car.getCleanliness();
        }
        
        return totalCleanliness / cars.size();
    }
    
    /**
     * 添加乘客到任意可用的车辆
     */
    public boolean addPassenger() {
        for (TrainCar car : cars) {
            if (car.addPassenger()) {
                updateTotalPassengers();
                return true;
            }
        }
        return false;
    }
    
    /**
     * 移除一名乘客
     */
    public boolean removePassenger() {
        for (int i = cars.size() - 1; i >= 0; i--) {
            if (cars.get(i).removePassenger()) {
                updateTotalPassengers();
                return true;
            }
        }
        return false;
    }
    
    /**
     * 更新总乘客数
     */
    private void updateTotalPassengers() {
        int total = 0;
        for (TrainCar car : cars) {
            total += car.getPassengers();
        }
        this.totalPassengers = total;
    }
    
    /**
     * 更新最大速度
     */
    private void updateMaxSpeed() {
        if (cars.isEmpty()) {
            return;
        }
        
        // 默认最大速度
        double baseMaxSpeed = 80.0;
        
        // 根据车辆状态调整最大速度
        boolean hasErrorCar = false;
        boolean hasWarningCar = false;
        int tractionSystemErrors = 0;
        int brakeSystemErrors = 0;
        
        for (TrainCar car : cars) {
            if (car.getStatus() == TrainCar.CarStatus.ERROR) {
                hasErrorCar = true;
                break;
            } else if (car.getStatus() == TrainCar.CarStatus.WARNING) {
                hasWarningCar = true;
            }
            
            // 检查关键子系统状态
            if (car.getTractionSystem() != null && 
                car.getTractionSystem().getStatus() == TractionSystem.TractionStatus.ERROR) {
                tractionSystemErrors++;
            }
            
            if (car.getBrakeSystem() != null && 
                car.getBrakeSystem().getStatus() == BrakeSystem.BrakeStatus.ERROR) {
                brakeSystemErrors++;
            }
        }
        
        // 应用状态调整因子
        double statusFactor = 1.0;
        if (hasErrorCar) {
            statusFactor = 0.5; // 有故障车辆时，速度减半
        } else if (hasWarningCar) {
            statusFactor = 0.75; // 有警告车辆时，速度为正常的75%
        } else if (tractionSystemErrors > 0) {
            statusFactor = 0.8; // 有牵引系统问题时
        } else if (brakeSystemErrors > 0) {
            statusFactor = 0.7; // 有制动系统问题时
        }
        
        // 根据乘客数量调整
        double passengerFactor = 1.0;
        double passengerRatio = (double) totalPassengers / (getCarCount() * 100); // 假设每车最大100人
        if (passengerRatio > 1.5) {
            passengerFactor = 0.9; // 超载150%以上时，速度降低10%
        }
        
        // 计算最终最大速度
        this.maxSpeed = baseMaxSpeed * statusFactor * passengerFactor;
    }
    
    /**
     * 检查车辆状态
     */
    private void checkCarStatus() {
        for (TrainCar car : cars) {
            if (car.getStatus() == TrainCar.CarStatus.ERROR) {
                // 有车辆发生故障，通知司机
                if (trainEntity != null && trainEntity.getDriver() != null) {
                    trainEntity.getDriver().sendMessage(
                        Text.literal("警告：车辆 " + car.getCarId() + " 发生故障！"), false);
                }
            } else if (car.getStatus() == TrainCar.CarStatus.WARNING) {
                // 有车辆需要注意，通知司机
                if (trainEntity != null && trainEntity.getDriver() != null) {
                    trainEntity.getDriver().sendMessage(
                        Text.literal("注意：车辆 " + car.getCarId() + " 需要检查！"), false);
                }
            }
            
            // 检查关键子系统
            checkSubSystems(car);
        }
    }
    
    /**
     * 检查车辆子系统状态
     */
    private void checkSubSystems(TrainCar car) {
        if (car.getTractionSystem() != null && 
            car.getTractionSystem().getStatus() == TractionSystem.TractionStatus.ERROR) {
            // 牵引系统故障
            notifyDriver("紧急：车辆 " + car.getCarId() + " 牵引系统故障！请立即停车。");
        }
        
        if (car.getBrakeSystem() != null && 
            car.getBrakeSystem().getStatus() == BrakeSystem.BrakeStatus.ERROR) {
            // 制动系统故障
            notifyDriver("紧急：车辆 " + car.getCarId() + " 制动系统故障！请谨慎驾驶。");
        }
    }
    
    /**
     * 通知司机消息
     */
    private void notifyDriver(String message) {
        if (trainEntity != null && trainEntity.getDriver() != null) {
            trainEntity.getDriver().sendMessage(Text.literal(message), false);
        }
    }
    
    /**
     * 保存到NBT
     */
    public NbtCompound toNbt() {
        NbtCompound nbt = new NbtCompound();
        nbt.putString("consistId", consistId);
        nbt.putString("lineId", lineId != null ? lineId : "");
        nbt.putString("destination", destination != null ? destination : "");
        nbt.putString("nextStation", nextStation != null ? nextStation : "");
        nbt.putDouble("maxSpeed", maxSpeed);
        nbt.putBoolean("atpEnabled", atpEnabled);
        nbt.putBoolean("atoEnabled", atoEnabled);
        nbt.putInt("totalPassengers", totalPassengers);
        nbt.putInt("powerLevel", powerLevel);
        nbt.putInt("maxPowerLevel", maxPowerLevel);
        nbt.putBoolean("usingExternalPower", usingExternalPower);
        nbt.putInt("externalPowerLevel", externalPowerLevel);
        
        // 保存车辆列表
        NbtList carList = new NbtList();
        for (TrainCar car : cars) {
            carList.add(car.toNbt());
        }
        nbt.put("cars", carList);
        
        return nbt;
    }
    
    /**
     * 从NBT加载
     */
    public static TrainConsist fromNbt(NbtCompound nbt) {
        String consistId = nbt.getString("consistId");
        TrainConsist consist = new TrainConsist(consistId);
        
        consist.lineId = nbt.getString("lineId");
        consist.destination = nbt.getString("destination");
        consist.nextStation = nbt.getString("nextStation");
        consist.maxSpeed = nbt.getDouble("maxSpeed");
        consist.atpEnabled = nbt.getBoolean("atpEnabled");
        consist.atoEnabled = nbt.getBoolean("atoEnabled");
        if (nbt.contains("totalPassengers")) {
            consist.totalPassengers = nbt.getInt("totalPassengers");
        }
        
        // 加载电力相关状态
        if (nbt.contains("powerLevel")) {
            consist.powerLevel = nbt.getInt("powerLevel");
        }
        if (nbt.contains("maxPowerLevel")) {
            consist.maxPowerLevel = nbt.getInt("maxPowerLevel");
        }
        if (nbt.contains("usingExternalPower")) {
            consist.usingExternalPower = nbt.getBoolean("usingExternalPower");
        }
        if (nbt.contains("externalPowerLevel")) {
            consist.externalPowerLevel = nbt.getInt("externalPowerLevel");
        }
        
        // 加载车辆列表
        NbtList carList = nbt.getList("cars", 10); // 10表示COMPOUND类型
        for (int i = 0; i < carList.size(); i++) {
            NbtCompound carNbt = carList.getCompound(i);
            TrainCar car = TrainCar.fromNbt(carNbt);
            consist.addCar(car);
        }
        
        consist.updateTotalPassengers();
        return consist;
    }
    
    // Getters and setters
    public List<TrainCar> getCars() {
        return new ArrayList<>(cars);
    }
    
    public String getConsistId() {
        return consistId;
    }
    
    public TrainEntity getTrainEntity() {
        return trainEntity;
    }
    
    public void setTrainEntity(TrainEntity trainEntity) {
        this.trainEntity = trainEntity;
        if (trainEntity != null) {
            // 同步信息到TrainEntity
            trainEntity.setDestination(this.destination);
            trainEntity.setNextStation(this.nextStation);
            trainEntity.setCurrentLine(this.lineId);
        }
    }
    
    public String getLineId() {
        return lineId;
    }
    
    public void setLineId(String lineId) {
        this.lineId = lineId;
        if (trainEntity != null) {
            trainEntity.setCurrentLine(lineId);
        }
    }
    
    public String getDestination() {
        return destination;
    }
    
    public void setDestination(String destination) {
        this.destination = destination;
        if (trainEntity != null) {
            trainEntity.setDestination(destination);
        }
    }
    
    public String getNextStation() {
        return nextStation;
    }
    
    public void setNextStation(String nextStation) {
        this.nextStation = nextStation;
        if (trainEntity != null) {
            trainEntity.setNextStation(nextStation);
        }
    }
    
    public double getMaxSpeed() {
        return maxSpeed;
    }
    
    public boolean isAtpEnabled() {
        return atpEnabled;
    }
    
    public void setAtpEnabled(boolean atpEnabled) {
        this.atpEnabled = atpEnabled;
    }
    
    public boolean isAtoEnabled() {
        return atoEnabled;
    }
    
    public void setAtoEnabled(boolean atoEnabled) {
        this.atoEnabled = atoEnabled;
    }
    
    public int getTotalPassengers() {
        return totalPassengers;
    }
    
    public int getCarCount() {
        return cars.size();
    }
    
    /**
     * 获取编组信息文本
     */
    public Text getInfoText() {
        String powerSource = usingExternalPower ? "外部电力" : "车载电力";
        String powerDetail = usingExternalPower ? 
            ", 电力: " + powerLevel + "% (外部供电: " + externalPowerLevel + ")" : 
            ", 电力: " + powerLevel + "%";
        
        return Text.literal("列车编组: " + consistId + ", 车辆数: " + cars.size() + ", 健康: " + getTotalHealth() + "%" + ", 乘客: " + totalPassengers + "人" + ", 总重: " + Math.round(getTotalWeight()) + "吨" + ", 清洁度: " + getAverageCleanliness() + "%" + powerDetail + ", 电力来源: " + powerSource);
    }
    
    // Getters and setters for power-related properties
    public int getPowerLevel() {
        return powerLevel;
    }
    
    public void setPowerLevel(int powerLevel) {
        this.powerLevel = Math.max(0, Math.min(maxPowerLevel, powerLevel));
    }
    
    public int getMaxPowerLevel() {
        return maxPowerLevel;
    }
    
    public void setMaxPowerLevel(int maxPowerLevel) {
        this.maxPowerLevel = Math.max(0, maxPowerLevel);
        // 确保当前电力不超过新的最大值
        if (this.powerLevel > this.maxPowerLevel) {
            this.powerLevel = this.maxPowerLevel;
        }
    }
    
    public boolean isUsingExternalPower() {
        return usingExternalPower;
    }
    
    public int getExternalPowerLevel() {
        return externalPowerLevel;
    }
    
    /**
     * 获取故障车辆列表
     */
    public List<TrainCar> getErrorCars() {
        return cars.stream()
            .filter(car -> car.getStatus() == TrainCar.CarStatus.ERROR)
            .collect(Collectors.toList());
    }
    
    /**
     * 获取需要维护的车辆列表
     */
    public List<TrainCar> getMaintenanceRequiredCars() {
        return cars.stream()
            .filter(car -> car.getStatus() == TrainCar.CarStatus.WARNING || 
                           car.getHealth() < 50 ||
                           car.getCleanliness() < 30 ||
                           car.getOperatingHours() > 1000)
            .collect(Collectors.toList());
    }
    
    /**
     * 对所有车辆进行清洁
     */
    public void cleanAllCars() {
        for (TrainCar car : cars) {
            // 使用setCleanliness方法替代不存在的clean方法
            car.setCleanliness(100.0);
        }
    }
    
    /**
     * 重置所有车辆的运行小时数（大修后）
     * 注意：TrainCar类目前没有resetRunningHours方法
     * 这个方法暂时不执行任何操作
     */
    public void resetRunningHours() {
        // TrainCar类没有提供重置运行小时数的方法
        // 暂时不执行任何操作，因为我们不能修改TrainCar类
        System.out.println("无法重置运行小时数：TrainCar类没有提供相应方法");
    }
}