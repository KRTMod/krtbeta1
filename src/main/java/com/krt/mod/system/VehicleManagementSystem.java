package com.krt.mod.system;

import com.krt.mod.entity.TrainCar;
import com.krt.mod.entity.TrainConsist;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.world.World;

/**
 * 车辆管理系统类，负责管理所有车辆和列车编组
 */
public class VehicleManagementSystem {
    // 系统单例
    private static VehicleManagementSystem instance;
    
    private final Map<String, TrainCar> cars = new HashMap<>();
    private final Map<String, TrainConsist> consists = new HashMap<>();
    private final World world;
    private int nextCarId = 1;
    private int nextConsistId = 1;
    private int maxActiveCars = 100;
    private int maxActiveConsists = 20;
    
    /**
     * 创建车辆管理系统
     */
    private VehicleManagementSystem(World world) {
        this.world = world;
    }
    
    /**
     * 获取系统单例
     */
    public static synchronized VehicleManagementSystem getInstance(World world) {
        if (instance == null) {
            instance = new VehicleManagementSystem(world);
        }
        return instance;
    }
    
    /**
     * 创建新车
     */
    public TrainCar createCar(TrainCar.CarType type, String model) {
        // 检查是否达到最大车辆数量
        if (cars.size() >= maxActiveCars) {
            return null;
        }
        
        // 生成车辆ID
        String carId = "car_" + nextCarId++;
        
        // 创建新车 - 使用正确的构造函数参数
        // 注意：原代码中的model和world参数被移除，根据车辆类型设置最大乘客数
        int maxPassengers = switch (type) {
            case HEAD_CAR, TAIL_CAR -> 50;
            case MIDDLE_CAR -> 60;
            default -> 55;
        };
        TrainCar car = new TrainCar(carId, type, maxPassengers);
        
        // 添加到管理列表
        cars.put(carId, car);
        
        return car;
    }
    
    /**
     * 移除车辆
     */
    public boolean removeCar(String carId) {
        TrainCar car = cars.get(carId);
        if (car == null) {
            return false;
        }
        
        // 检查车辆是否在某个编组中
        TrainConsist consist = car.getConsist();
        if (consist != null) {
            consist.removeCar(carId);
        }
        
        // 从管理列表中移除
        cars.remove(carId);
        
        return true;
    }
    
    /**
     * 创建新的列车编组
     */
    public TrainConsist createConsist() {
        // 检查是否达到最大编组数量
        if (consists.size() >= maxActiveConsists) {
            return null;
        }
        
        // 生成编组ID
        String consistId = "consist_" + nextConsistId++;
        
        // 创建新编组
        TrainConsist consist = new TrainConsist(consistId);
        
        // 添加到管理列表
        consists.put(consistId, consist);
        
        return consist;
    }
    
    /**
     * 移除列车编组
     */
    public boolean removeConsist(String consistId) {
        TrainConsist consist = consists.get(consistId);
        if (consist == null) {
            return false;
        }
        
        // 移除编组中的所有车辆
        for (TrainCar car : consist.getCars()) {
            car.setConsist(null);
        }
        
        // 从管理列表中移除
        consists.remove(consistId);
        
        return true;
    }
    
    /**
     * 将车辆添加到编组
     */
    public boolean addCarToConsist(String carId, String consistId) {
        TrainCar car = cars.get(carId);
        TrainConsist consist = consists.get(consistId);
        
        if (car == null || consist == null) {
            return false;
        }
        
        return consist.addCar(car);
    }
    
    /**
     * 从编组中移除车辆
     */
    public boolean removeCarFromConsist(String carId, String consistId) {
        TrainConsist consist = consists.get(consistId);
        if (consist == null) {
            return false;
        }
        
        return consist.removeCar(carId);
    }
    
    /**
     * 查找车辆
     */
    public TrainCar getCar(String carId) {
        return cars.get(carId);
    }
    
    /**
     * 查找编组
     */
    public TrainConsist getConsist(String consistId) {
        return consists.get(consistId);
    }
    
    /**
     * 更新系统
     */
    public void update() {
        // 更新所有车辆
        for (TrainCar car : cars.values()) {
            car.update();
        }
        
        // 更新所有编组
        for (TrainConsist consist : consists.values()) {
            consist.update();
        }
        
        // 清理无效车辆和编组
        cleanupInvalidEntities();
    }
    
    /**
     * 清理无效实体
     */
    private void cleanupInvalidEntities() {
        // 清理无效车辆 - 使用health属性替代不存在的isRemoved()方法
        // 假设健康值为0的车辆被视为无效车辆
        cars.entrySet().removeIf(entry -> entry.getValue().getHealth() == 0);
        
        // 清理无效编组（没有车辆的编组）
        consists.entrySet().removeIf(entry -> entry.getValue().getCarCount() == 0);
    }
    
    /**
     * 开始车辆维护
     */
    public boolean startCarMaintenance(String carId) {
        TrainCar car = cars.get(carId);
        if (car == null) {
            return false;
        }
        
        car.startMaintenance();
        return true;
    }
    
    /**
     * 结束车辆维护
     */
    public boolean endCarMaintenance(String carId) {
        TrainCar car = cars.get(carId);
        if (car == null) {
            return false;
        }
        
        car.endMaintenance();
        return true;
    }
    
    /**
     * 维修车辆
     */
    public boolean repairCar(String carId) {
        TrainCar car = cars.get(carId);
        if (car == null) {
            return false;
        }
        
        car.repair();
        return true;
    }
    
    /**
     * 开始编组维护
     */
    public boolean startConsistMaintenance(String consistId) {
        TrainConsist consist = consists.get(consistId);
        if (consist == null) {
            return false;
        }
        
        consist.startMaintenance();
        return true;
    }
    
    /**
     * 结束编组维护
     */
    public boolean endConsistMaintenance(String consistId) {
        TrainConsist consist = consists.get(consistId);
        if (consist == null) {
            return false;
        }
        
        consist.endMaintenance();
        return true;
    }
    
    /**
     * 获取车辆状态报告
     */
    public Text getCarStatusReport(String carId) {
        TrainCar car = cars.get(carId);
        if (car == null) {
            return Text.literal("车辆不存在: " + carId);
        }
        
        return Text.literal(
            "车辆ID: " + carId +
            ", 类型: " + car.getCarType().name() +
            ", 状态: " + car.getStatus().name() +
            ", 健康: " + car.getHealth() + "%" +
            ", 速度: " + String.format("%.1f km/h", car.getSpeed()) +
            ", 乘客: " + car.getPassengers() + "人"
        );
    }
    
    /**
     * 获取编组状态报告
     */
    public Text getConsistStatusReport(String consistId) {
        TrainConsist consist = consists.get(consistId);
        if (consist == null) {
            return Text.literal("编组不存在: " + consistId);
        }
        
        return consist.getInfoText();
    }
    
    /**
     * 获取系统状态报告
     */
    public Text getSystemStatusReport() {
        // 计算各状态的车辆数量
        int normalCars = 0;
        int warningCars = 0;
        int errorCars = 0;
        int maintenanceCars = 0;
        
        for (TrainCar car : cars.values()) {
            switch (car.getStatus()) {
                case OK -> normalCars++; // 注意：在TrainCar类中，正常状态是OK而不是NORMAL
                case WARNING -> warningCars++;
                case ERROR -> errorCars++;
                case MAINTENANCE -> maintenanceCars++;
            }
        }
        
        // 计算各状态的编组数量
        int normalConsists = 0;
        int warningConsists = 0;
        int errorConsists = 0;
        int maintenanceConsists = 0;
        
        for (TrainConsist consist : consists.values()) {
            if (consist.isInMaintenance()) {
                maintenanceConsists++;
            } else {
                int health = consist.getTotalHealth();
                if (health > 60) {
                    normalConsists++;
                } else if (health > 30) {
                    warningConsists++;
                } else {
                    errorConsists++;
                }
            }
        }
        
        return Text.literal(
            "车辆管理系统状态: " +
            "车辆总数: " + cars.size() + "(正常: " + normalCars + ", 警告: " + warningCars + ", 故障: " + errorCars + ", 维护: " + maintenanceCars + ") " +
            "编组总数: " + consists.size() + "(正常: " + normalConsists + ", 警告: " + warningConsists + ", 故障: " + errorConsists + ", 维护: " + maintenanceConsists + ")"
        );
    }
    
    // Getters and setters
    public Map<String, TrainCar> getCars() {
        return new HashMap<>(cars);
    }
    
    public Map<String, TrainConsist> getConsists() {
        return new HashMap<>(consists);
    }
    
    public int getMaxActiveCars() {
        return maxActiveCars;
    }
    
    public void setMaxActiveCars(int maxActiveCars) {
        this.maxActiveCars = maxActiveCars;
    }
    
    public int getMaxActiveConsists() {
        return maxActiveConsists;
    }
    
    public void setMaxActiveConsists(int maxActiveConsists) {
        this.maxActiveConsists = maxActiveConsists;
    }
    
    public World getWorld() {
        return world;
    }
}