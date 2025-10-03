package com.krt.mod.system;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.Box;
import net.minecraft.world.World;
import com.krt.mod.entity.TrainCar;
import com.krt.mod.entity.TrainConsist;
import com.krt.mod.entity.TrainEntity;
import com.krt.mod.util.EntityObjectPool;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 列车内饰和乘客系统
 * 负责管理列车内部设施和乘客模型
 */
public class TrainInteriorSystem {
    private static final Map<World, TrainInteriorSystem> INSTANCES = new HashMap<>();
    private final World world;
    
    // 存储每个车厢的内饰配置
    private final Map<String, InteriorConfig> carInteriors = new ConcurrentHashMap<>();
    
    // 存储乘客信息
    private final Map<String, List<PassengerInfo>> carPassengers = new ConcurrentHashMap<>();
    
    // 最大乘客数量乘数（基于车厢类型）
    private static final double HEAD_CAR_MULTIPLIER = 0.8;
    private static final double MIDDLE_CAR_MULTIPLIER = 1.0;
    private static final double TAIL_CAR_MULTIPLIER = 0.8;
    
    // 乘客生成概率（每刻）
    private static final double PASSENGER_SPAWN_PROBABILITY = 0.01;
    
    private TrainInteriorSystem(World world) {
        this.world = world;
    }
    
    public static TrainInteriorSystem getInstance(World world) {
        return INSTANCES.computeIfAbsent(world, TrainInteriorSystem::new);
    }
    
    // 初始化车厢内饰
    public void initializeCarInterior(TrainCar car) {
        String carId = car.getCarId();
        
        // 根据车厢类型设置默认内饰
        InteriorType interiorType;
        switch (car.getCarType()) {
            case HEAD_CAR:
            case TAIL_CAR:
                interiorType = InteriorType.DRIVER_CAB;
                break;
            case MIDDLE_CAR:
                interiorType = InteriorType.STANDARD_PASSENGER;
                break;
            default:
                interiorType = InteriorType.STANDARD_PASSENGER;
                break;
        }
        
        carInteriors.put(carId, new InteriorConfig(interiorType));
        carPassengers.put(carId, new ArrayList<>());
        
        // 设置最大乘客数量
        int baseMaxPassengers = 60; // 基础最大乘客数
        double multiplier = getPassengerMultiplier(car.getCarType());
        car.setMaxPassengers((int)(baseMaxPassengers * multiplier));
    }
    
    // 获取乘客乘数
    private double getPassengerMultiplier(TrainCar.CarType carType) {
        switch (carType) {
            case HEAD_CAR:
                return HEAD_CAR_MULTIPLIER;
            case MIDDLE_CAR:
                return MIDDLE_CAR_MULTIPLIER;
            case TAIL_CAR:
                return TAIL_CAR_MULTIPLIER;
            default:
                return MIDDLE_CAR_MULTIPLIER;
        }
    }
    
    // 更新内饰系统
    public void update() {
        // 更新所有车厢的乘客
        for (Map.Entry<String, List<PassengerInfo>> entry : carPassengers.entrySet()) {
            String carId = entry.getKey();
            List<PassengerInfo> passengers = entry.getValue();
            
            // 更新乘客状态
            updatePassengers(carId, passengers);
        }
    }
    
    // 更新乘客状态
    private void updatePassengers(String carId, List<PassengerInfo> passengers) {
        // 清理已经下车的乘客
        passengers.removeIf(passenger -> !isPassengerInWorld(passenger));
        
        // 查找对应的车厢
        TrainCar car = findCarById(carId);
        if (car == null) {
            return;
        }
        
        // 如果车门打开，有概率生成新乘客或让乘客下车
        if (car.areDoorsOpen() && world.isClient) {
            // 生成新乘客
            if (passengers.size() < car.getMaxPassengers() && Math.random() < PASSENGER_SPAWN_PROBABILITY) {
                spawnPassenger(carId);
            }
            
            // 乘客下车
            if (!passengers.isEmpty() && Math.random() < PASSENGER_SPAWN_PROBABILITY) {
                removePassenger(carId);
            }
        }
        
        // 更新车厢的乘客数量
        car.setPassengers(passengers.size());
    }
    
    // 生成乘客
    private void spawnPassenger(String carId) {
        TrainCar car = findCarById(carId);
        if (car == null) {
            return;
        }
        
        // 在车厢附近生成乘客实体
        Vec3d carPos = getCarPosition(car);
        if (carPos != null) {
            // 使用对象池获取乘客实体，避免频繁创建新对象
            EntityObjectPool pool = EntityObjectPool.getInstance(world, VillagerEntity.class);
            VillagerEntity passenger = pool.get();
            
            if (passenger != null) {
                // 随机生成位置偏移
                double offsetX = (Math.random() - 0.5) * 3;
                double offsetZ = (Math.random() - 0.5) * 8;
                
                passenger.setPosition(carPos.x + offsetX, carPos.y + 1, carPos.z + offsetZ);
                passenger.setSilent(false); // 恢复声音
                
                // 设置随机职业和外貌
                int professionId = (int)(Math.random() * 5); // 0-4种不同职业
                passenger.setVillagerData(passenger.getVillagerData().setProfession(
                    net.minecraft.entity.passive.VillagerProfession.values()[professionId]
                ));
                
                // 将实体添加到世界
                if (!world.isClient) {
                    world.spawnEntity(passenger);
                }
                
                // 记录乘客信息
                PassengerInfo passengerInfo = new PassengerInfo(
                    passenger.getUuidAsString(),
                    "乘客" + UUID.randomUUID().toString().substring(0, 4),
                    generateRandomDestination()
                );
                
                carPassengers.computeIfAbsent(carId, k -> new ArrayList<>()).add(passengerInfo);
            }
        }
    }
    
    // 移除乘客
    private void removePassenger(String carId) {
        List<PassengerInfo> passengers = carPassengers.get(carId);
        if (passengers != null && !passengers.isEmpty()) {
            // 随机选择一个乘客下车
            PassengerInfo passenger = passengers.remove((int)(Math.random() * passengers.size()));
            
            // 回收实体到对象池而不是直接丢弃
            Entity entity = world.getEntity(UUID.fromString(passenger.passengerId));
            if (entity != null && entity instanceof VillagerEntity) {
                EntityObjectPool pool = EntityObjectPool.getInstance(world, VillagerEntity.class);
                pool.recycle(entity);
            }
        }
    }
    
    // 检查乘客是否在世界中
    private boolean isPassengerInWorld(PassengerInfo passenger) {
        Entity entity = world.getEntity(UUID.fromString(passenger.passengerId));
        return entity != null && entity.isAlive();
    }
    
    // 查找车厢
    private TrainCar findCarById(String carId) {
        // 遍历世界中的所有列车，查找对应的车厢
        for (Entity entity : world.getEntitiesByClass(Entity.class, new Box(-30000000, -64, -30000000, 30000000, 320, 30000000), 
                entity -> entity instanceof TrainEntity)) {
            TrainEntity train = (TrainEntity) entity;
            TrainConsist consist = train.getConsist();
            if (consist != null) {
                for (TrainCar car : consist.getCars()) {
                    if (car.getCarId().equals(carId)) {
                        return car;
                    }
                }
            }
        }
        return null;
    }
    
    // 获取车厢位置
    private Vec3d getCarPosition(TrainCar car) {
        TrainConsist consist = car.getConsist();
        if (consist != null) {
            TrainEntity trainEntity = consist.getTrainEntity();
            if (trainEntity != null) {
                return trainEntity.getPos();
            }
        }
        return null;
    }
    
    // 生成随机目的地
    private String generateRandomDestination() {
        // 昆明地铁常见站点
        String[] stations = {
            "昆明火车站", "东风广场", "南屏街", "翠湖", "云南大学", 
            "呈贡大学城", "昆明南站", "长水机场", "海埂大坝", "滇池路"
        };
        
        return stations[(int)(Math.random() * stations.length)];
    }
    
    // 添加玩家到车厢
    public void addPlayerToCar(PlayerEntity player, TrainCar car) {
        String carId = car.getCarId();
        
        // 检查是否已经在车厢内
        for (PassengerInfo passenger : carPassengers.getOrDefault(carId, Collections.emptyList())) {
            if (passenger.passengerId.equals(player.getUuidAsString())) {
                return; // 已经在车厢内
            }
        }
        
        // 添加玩家信息
        PassengerInfo passengerInfo = new PassengerInfo(
            player.getUuidAsString(),
            player.getName().getString(),
            ""
        );
        
        carPassengers.computeIfAbsent(carId, k -> new ArrayList<>()).add(passengerInfo);
        
        // 更新车厢乘客数量
        car.setPassengers(carPassengers.get(carId).size());
        
        // 通知玩家
        player.sendMessage(Text.literal("您已进入 " + car.getLineName() + " 的列车车厢"), false);
    }
    
    // 从车厢移除玩家
    public void removePlayerFromCar(PlayerEntity player, TrainCar car) {
        String carId = car.getCarId();
        List<PassengerInfo> passengers = carPassengers.get(carId);
        
        if (passengers != null) {
            passengers.removeIf(passenger -> passenger.passengerId.equals(player.getUuidAsString()));
            
            // 更新车厢乘客数量
            car.setPassengers(passengers.size());
        }
    }
    
    // 获取车厢内饰配置
    public InteriorConfig getCarInterior(String carId) {
        return carInteriors.getOrDefault(carId, new InteriorConfig(InteriorType.STANDARD_PASSENGER));
    }
    
    // 设置车厢内饰类型
    public void setCarInteriorType(String carId, InteriorType interiorType) {
        carInteriors.put(carId, new InteriorConfig(interiorType));
    }
    
    // 内饰配置类
    public static class InteriorConfig {
        private final InteriorType type;
        private final Map<String, Object> customProperties = new HashMap<>();
        
        public InteriorConfig(InteriorType type) {
            this.type = type;
            initializeDefaultProperties();
        }
        
        private void initializeDefaultProperties() {
            switch (type) {
                case DRIVER_CAB:
                    customProperties.put("hasControlPanel", true);
                    customProperties.put("hasCommunicationSystem", true);
                    customProperties.put("hasSpeedDisplay", true);
                    break;
                case STANDARD_PASSENGER:
                    customProperties.put("hasSeats", true);
                    customProperties.put("hasHandrails", true);
                    customProperties.put("hasInfoDisplay", true);
                    break;
                case PREMIUM_PASSENGER:
                    customProperties.put("hasSeats", true);
                    customProperties.put("hasHandrails", true);
                    customProperties.put("hasInfoDisplay", true);
                    customProperties.put("hasChargingStations", true);
                    customProperties.put("hasWiFi", true);
                    break;
                case ACCESSIBILITY:
                    customProperties.put("hasWheelchairSpace", true);
                    customProperties.put("hasPrioritySeats", true);
                    customProperties.put("hasLowHandrails", true);
                    break;
            }
        }
        
        public InteriorType getType() {
            return type;
        }
        
        public boolean hasProperty(String key) {
            return customProperties.containsKey(key);
        }
        
        public Object getProperty(String key) {
            return customProperties.get(key);
        }
        
        public void setProperty(String key, Object value) {
            customProperties.put(key, value);
        }
    }
    
    // 乘客信息类
    private static class PassengerInfo {
        private final String passengerId;
        private final String name;
        private final String destination;
        
        public PassengerInfo(String passengerId, String name, String destination) {
            this.passengerId = passengerId;
            this.name = name;
            this.destination = destination;
        }
    }
    
    // 内饰类型枚举
    public enum InteriorType {
        DRIVER_CAB("驾驶室"),
        STANDARD_PASSENGER("标准乘客车厢"),
        PREMIUM_PASSENGER("高级乘客车厢"),
        ACCESSIBILITY("无障碍车厢");
        
        private final String displayName;
        
        InteriorType(String displayName) {
            this.displayName = displayName;
        }
        
        public String getDisplayName() {
            return displayName;
        }
    }
}