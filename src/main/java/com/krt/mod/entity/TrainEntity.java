package com.krt.mod.entity;

import net.fabricmc.fabric.api.entity.event.v1.ServerEntityCombatEvents;
import net.fabricmc.fabric.api.networking.v1.EntityTrackingEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.Packet;
import net.minecraft.network.packet.s2c.play.EntitySpawnS2CPacket;
import net.minecraft.screen.NamedScreenHandlerFactory;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import com.krt.mod.sound.ModSounds;
import net.minecraft.sound.SoundEvent;
import net.minecraft.text.Text;
import com.krt.mod.system.TrainControlSystem;
import com.krt.mod.system.TrainSelfCheckSystem;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import com.krt.mod.KRTMod;
import com.krt.mod.block.SignalBlock;
import com.krt.mod.block.ATPSignalBlock;
import com.krt.mod.gui.TrainControlScreenHandler;
import java.util.ArrayList;
import java.util.List;
import com.krt.mod.entity.TrainConsist;
import com.krt.mod.entity.TrainCar;
import com.krt.mod.system.PowerSupplySystem;
import com.krt.mod.system.TractionSystem;
import com.krt.mod.system.BrakeSystem;
import com.krt.mod.system.VehicleManagementSystem;
import com.krt.mod.system.TrainSwaySystem;

public class TrainEntity extends Entity {
    // 数据跟踪器字段
    private static final TrackedData<Boolean> ATO_ENABLED = DataTracker.registerData(TrainEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
    private static final TrackedData<Float> CURRENT_SPEED = DataTracker.registerData(TrainEntity.class, TrackedDataHandlerRegistry.FLOAT);
    private static final TrackedData<String> DESTINATION = DataTracker.registerData(TrainEntity.class, TrackedDataHandlerRegistry.STRING);
    private static final TrackedData<String> NEXT_STATION = DataTracker.registerData(TrainEntity.class, TrackedDataHandlerRegistry.STRING);
    private static final TrackedData<Integer> HEALTH = DataTracker.registerData(TrainEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Boolean> EMERGENCY_BRAKE = DataTracker.registerData(TrainEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
    private static final TrackedData<String> CURRENT_DEPOT = DataTracker.registerData(TrainEntity.class, TrackedDataHandlerRegistry.STRING);
    private static final TrackedData<String> CURRENT_LINE = DataTracker.registerData(TrainEntity.class, TrackedDataHandlerRegistry.STRING);
    private static final TrackedData<String> CONSIST_ID = DataTracker.registerData(TrainEntity.class, TrackedDataHandlerRegistry.STRING);

    // 列车控制系统
    private TrainControlSystem controlSystem;
    // 司机
    private PlayerEntity driver;
    // 自检系统
    private TrainSelfCheckSystem selfCheckSystem;
    // 历史位置记录，用于计算速度和方向
    private List<Vec3d> positionHistory = new ArrayList<>();
    // 列车编组
    private TrainConsist consist;
    // 供电系统
    private PowerSupplySystem powerSupplySystem;
    // 车辆管理系统引用
    private VehicleManagementSystem vehicleManagementSystem;
    // 列车摇摆系统
    private TrainSwaySystem swaySystem;

    public TrainEntity(EntityType<?> type, World world) {
        super(type, world);
        this.controlSystem = new TrainControlSystem(this);
        this.selfCheckSystem = new TrainSelfCheckSystem(this);
        this.vehicleManagementSystem = VehicleManagementSystem.getInstance(world);
        this.powerSupplySystem = new PowerSupplySystem(world);
        this.swaySystem = new TrainSwaySystem(this);
    }

    @Override
    protected void initDataTracker() {
        this.dataTracker.startTracking(ATO_ENABLED, true); // 默认启用ATO
        this.dataTracker.startTracking(CURRENT_SPEED, 0.0F);
        this.dataTracker.startTracking(DESTINATION, "终点站");
        this.dataTracker.startTracking(NEXT_STATION, "下一站");
        this.dataTracker.startTracking(HEALTH, 100);
        this.dataTracker.startTracking(EMERGENCY_BRAKE, false);
        this.dataTracker.startTracking(CURRENT_DEPOT, "");
        this.dataTracker.startTracking(CURRENT_LINE, "");
        this.dataTracker.startTracking(CONSIST_ID, "");
    }

    @Override
    protected void readCustomDataFromNbt(NbtCompound nbt) {
        this.dataTracker.set(ATO_ENABLED, nbt.getBoolean("ATOEnabled"));
        this.dataTracker.set(CURRENT_SPEED, (float)nbt.getDouble("CurrentSpeed"));
        this.dataTracker.set(DESTINATION, nbt.getString("Destination"));
        this.dataTracker.set(NEXT_STATION, nbt.getString("NextStation"));
        this.dataTracker.set(HEALTH, nbt.getInt("Health"));
        this.dataTracker.set(EMERGENCY_BRAKE, nbt.getBoolean("EmergencyBrake"));
        this.dataTracker.set(CURRENT_DEPOT, nbt.getString("CurrentDepot"));
        this.dataTracker.set(CURRENT_LINE, nbt.getString("CurrentLine"));
        this.dataTracker.set(CONSIST_ID, nbt.getString("ConsistId"));
        
        // 从车辆管理系统获取列车编组
        String consistId = this.dataTracker.get(CONSIST_ID);
        if (!consistId.isEmpty()) {
            this.consist = vehicleManagementSystem.getConsist(consistId);
            if (this.consist != null) {
                this.consist.setTrainEntity(this);
            }
        }
        
        // 加载供电系统状态
        if (nbt.contains("PowerSupplySystem")) {
            this.powerSupplySystem.fromNbt(nbt.getCompound("PowerSupplySystem"));
        }
    }

    @Override
    protected void writeCustomDataToNbt(NbtCompound nbt) {
        nbt.putBoolean("ATOEnabled", this.dataTracker.get(ATO_ENABLED));
        nbt.putDouble("CurrentSpeed", (double)this.dataTracker.get(CURRENT_SPEED));
        nbt.putString("Destination", this.dataTracker.get(DESTINATION));
        nbt.putString("NextStation", this.dataTracker.get(NEXT_STATION));
        nbt.putInt("Health", this.dataTracker.get(HEALTH));
        nbt.putBoolean("EmergencyBrake", this.dataTracker.get(EMERGENCY_BRAKE));
        nbt.putString("CurrentDepot", this.dataTracker.get(CURRENT_DEPOT));
        nbt.putString("CurrentLine", this.dataTracker.get(CURRENT_LINE));
        nbt.putString("ConsistId", this.dataTracker.get(CONSIST_ID));
        
        // 保存编组数据
        if (this.consist != null) {
            nbt.put("Consist", this.consist.toNbt());
        }
        
        // 保存供电系统状态
        nbt.put("PowerSupplySystem", this.powerSupplySystem.toNbt());
    }

    @Override
    public Packet<?> createSpawnPacket() {
        return new EntitySpawnS2CPacket(this);
    }

    @Override
    public void tick() {
        super.tick();

        // 记录位置历史，用于计算速度
        this.positionHistory.add(this.getPos());
        if (this.positionHistory.size() > 20) {
            this.positionHistory.remove(0);
        }

        // 计算当前速度
        if (this.positionHistory.size() >= 2) {
            Vec3d pos1 = this.positionHistory.get(0);
            Vec3d pos2 = this.positionHistory.get(this.positionHistory.size() - 1);
            double distance = pos1.squaredDistanceTo(pos2);
            double speed = Math.sqrt(distance) * 20; // 转换为每秒的距离
            this.dataTracker.set(CURRENT_SPEED, (float)speed);
        }

        // 更新供电系统
        this.powerSupplySystem.update();

        // 运行自检系统
        this.selfCheckSystem.tick();

        // 更新列车编组
        if (this.consist != null) {
            this.consist.update();
            
            // 从编组更新健康值
            this.setHealth(this.consist.getTotalHealth());
        }

        // 检查健康状态，如果健康值低于50%，禁止运营
        if (this.dataTracker.get(HEALTH) < 50) {
            this.applyEmergencyBrake();
            return;
        }

        // 如果启用了紧急制动，保持制动状态
        if (this.dataTracker.get(EMERGENCY_BRAKE)) {
            this.setVelocity(Vec3d.ZERO);
            if (this.consist != null) {
                this.consist.applyEmergencyBrake();
            }
            return;
        }

        // 根据控制模式处理列车移动
        if (this.dataTracker.get(ATO_ENABLED)) {
            // ATO模式
            this.controlSystem.runATO();
        } else {
            // 手动模式
            this.controlSystem.adjustSpeed();
        }

        // 检查信号机状态，如果前方有红灯，触发紧急制动
        this.checkSignalStatus();

        // 播放列车走行音，根据速度调整音量和音高
        if (this.dataTracker.get(CURRENT_SPEED) > 0.1 && !this.world.isClient) {
            float volume = Math.min(0.5F + (float)this.dataTracker.get(CURRENT_SPEED) / 200.0F, 1.0F);
            float pitch = 0.8F + (float)this.dataTracker.get(CURRENT_SPEED) / 400.0F;
            this.world.playSound(null, this.getBlockPos(), ModSounds.TRAIN_MOVING_SOUND, SoundCategory.NEUTRAL, volume, pitch);
        }
        
        // 更新列车摇摆效果
        if (this.swaySystem != null) {
            this.swaySystem.update();
        }
    }

    @Override
    public ActionResult interact(PlayerEntity player, Hand hand) {
        // 检查玩家是否手持司机钥匙
        boolean hasKey = player.getStackInHand(hand).getItem() == com.krt.mod.Init.DOOR_KEY;
        
        if (player.isSneaking()) {
            // 玩家潜行时可以进入列车
            if (this.driver == null) {
                this.driver = player;
                player.sendMessage(Text.literal("您已进入驾驶位"), false);
                return ActionResult.SUCCESS;
            } else if (this.driver == player) {
                this.driver = null;
                player.sendMessage(Text.literal("您已离开驾驶位"), false);
                return ActionResult.SUCCESS;
            }
        } else if (this.driver == player && hasKey) {
            // 司机手持钥匙时可以打开列车驾驶控制面板
            if (!this.world.isClient) {
                player.openHandledScreen(new NamedScreenHandlerFactory() {
                    @Override
                    public ScreenHandler createMenu(int syncId, PlayerInventory inventory, PlayerEntity player) {
                        return new TrainControlScreenHandler(TrainEntity.this);
                    }
                    
                    @Override
                    public Text getDisplayName() {
                        return Text.literal("列车控制面板");
                    }
                });
            }
            return ActionResult.SUCCESS;
        } else if (!hasKey && this.driver == player) {
            player.sendMessage(Text.literal("您需要手持司机钥匙才能打开控制面板"), false);
            return ActionResult.SUCCESS;
        }
        return ActionResult.PASS;
    }

    // 检查前方信号机状态
    private void checkSignalStatus() {
        // 获取列车前方的位置
        Vec3d forwardPos = this.getPos().add(this.getRotationVector().multiply(20));
        BlockPos checkPos = new BlockPos(forwardPos);

        // 检查周围20格内的信号机
        for (BlockPos pos : BlockPos.iterateOutwards(checkPos, 20, 5, 20)) {
            if (this.world.getBlockState(pos).getBlock() instanceof SignalBlock) {
                SignalBlock.SignalState signalState = this.world.getBlockState(pos).get(SignalBlock.SIGNAL_STATE);
                double distance = this.getPos().squaredDistanceTo(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
                
                if (signalState == SignalBlock.SignalState.RED && distance < 100 * 100) {
                    // 前方100米内有红灯，触发紧急制动
                    this.applyEmergencyBrake();
                    break;
                } else if (signalState == SignalBlock.SignalState.YELLOW && distance < 200 * 200) {
                    // 前方200米内有黄灯，提示减速
                    if (this.driver != null) {
                        this.driver.sendMessage(Text.literal("前方信号机显示黄色，请注意减速！"), false);
                    }
                    // ATO系统会自动减速
                    if (this.dataTracker.get(ATO_ENABLED)) {
                        this.controlSystem.slowDown();
                    }
                }
            }
        }
    }

    // 应用紧急制动
    public void applyEmergencyBrake() {
        if (!this.dataTracker.get(EMERGENCY_BRAKE)) {
            this.dataTracker.set(EMERGENCY_BRAKE, true);
            // 播放紧急制动声音
            if (!this.world.isClient) {
                this.world.playSound(null, this.getBlockPos(), ModSounds.EMERGENCY_BRAKE_SOUND, SoundCategory.NEUTRAL, 1.0F, 1.0F);
            }
            // 通知司机
            if (this.driver != null) {
                this.driver.sendMessage(Text.literal("紧急制动已触发！"), false);
            }
            // 对编组应用紧急制动
            if (this.consist != null) {
                this.consist.applyEmergencyBrake();
            }
        }
    }

    // 释放紧急制动
    public void releaseEmergencyBrake() {
        if (this.dataTracker.get(EMERGENCY_BRAKE)) {
            this.dataTracker.set(EMERGENCY_BRAKE, false);
            // 通知司机
            if (this.driver != null) {
                this.driver.sendMessage(Text.literal("紧急制动已解除"), false);
            }
            // 释放编组的制动
            if (this.consist != null) {
                this.consist.releaseBrakes();
            }
        }
    }

    // 手动控制列车
    public void manualControl(double acceleration, double direction) {
        if (!this.dataTracker.get(ATO_ENABLED) && !this.dataTracker.get(EMERGENCY_BRAKE)) {
            // 根据方向和加速度调整列车的速度和方向
            Vec3d velocity = this.getVelocity();
            Vec3d newVelocity = this.getRotationVector().multiply(acceleration * direction);
            this.setVelocity(newVelocity);
            
            // 对编组应用动力或制动
            if (this.consist != null) {
                if (acceleration > 0) {
                    // 应用动力
                    this.consist.applyPower(acceleration);
                } else if (acceleration < 0) {
                    // 应用制动
                    this.consist.applyBrake(Math.abs(acceleration));
                }
            }
        }
    }

    // 获取列车控制系统
    public TrainControlSystem getControlSystem() {
        return this.controlSystem;
    }
    
    // 设置列车编组
    public void setConsist(TrainConsist consist) {
        this.consist = consist;
        if (consist != null) {
            this.dataTracker.set(CONSIST_ID, consist.getConsistId());
            consist.setTrainEntity(this);
            // 同步信息
            this.setDestination(consist.getDestination());
            this.setNextStation(consist.getNextStation());
            this.setCurrentLine(consist.getLineId());
        } else {
            this.dataTracker.set(CONSIST_ID, "");
        }
    }
    
    // 获取列车编组
    public TrainConsist getConsist() {
        return this.consist;
    }

    // 设置ATO状态
    public void setATOEnabled(boolean enabled) {
        this.dataTracker.set(ATO_ENABLED, enabled);
        if (this.consist != null) {
            this.consist.setAtoEnabled(enabled);
        }
    }

    // 获取ATO状态
    public boolean isATOEnabled() {
        return this.dataTracker.get(ATO_ENABLED);
    }
    
    // 设置ATP状态
    public void setATPEnabled(boolean enabled) {
        if (this.consist != null) {
            this.consist.setAtpEnabled(enabled);
        }
    }
    
    // 获取ATP状态
    public boolean isATPEnabled() {
        return this.consist != null && this.consist.isAtpEnabled();
    }

    // 获取当前速度
    public float getCurrentSpeed() {
        return this.dataTracker.get(CURRENT_SPEED);
    }

    // 设置目的地
    public void setDestination(String destination) {
        this.dataTracker.set(DESTINATION, destination);
        if (this.consist != null) {
            this.consist.setDestination(destination);
        }
    }

    // 获取目的地
    public String getDestination() {
        return this.dataTracker.get(DESTINATION);
    }

    // 设置下一站
    public void setNextStation(String nextStation) {
        this.dataTracker.set(NEXT_STATION, nextStation);
        if (this.consist != null) {
            this.consist.setNextStation(nextStation);
        }
    }

    // 获取下一站
    public String getNextStation() {
        return this.dataTracker.get(NEXT_STATION);
    }

    // 设置健康值
    public void setHealth(int health) {
        this.dataTracker.set(HEALTH, MathHelper.clamp(health, 0, 100));
    }

    // 获取健康值
    public int getHealth() {
        return this.dataTracker.get(HEALTH);
    }
    
    // 获取总乘客数
    public int getTotalPassengers() {
        if (this.consist != null) {
            return this.consist.getTotalPassengers();
        }
        return 0;
    }
    
    // 添加乘客
    public boolean addPassenger() {
        if (this.consist != null) {
            return this.consist.addPassenger();
        }
        return false;
    }
    
    // 移除乘客
    public boolean removePassenger() {
        if (this.consist != null) {
            return this.consist.removePassenger();
        }
        return false;
    }

    // 获取司机
    public PlayerEntity getDriver() {
        return this.driver;
    }
    
    // 设置司机
    public void setDriver(PlayerEntity driver) {
        this.driver = driver;
    }
    
    // 检查是否正在紧急制动
    public boolean isEmergencyBraking() {
        return this.dataTracker.get(EMERGENCY_BRAKE);
    }
    
    // 获取列车ID
    public String getTrainId() {
        return "Train-" + this.getId();
    }
    
    // 设置当前车厂
    public void setCurrentDepot(String depotId) {
        this.dataTracker.set(CURRENT_DEPOT, depotId);
    }

    // 获取当前车厂
    public String getCurrentDepot() {
        return this.dataTracker.get(CURRENT_DEPOT);
    }
    
    // 打开所有车门
    public void openAllDoors() {
        if (this.consist != null) {
            this.consist.openAllDoors();
        }
    }
    
    // 关闭所有车门
    public void closeAllDoors() {
        if (this.consist != null) {
            this.consist.closeAllDoors();
        }
    }
    
    // 检查是否所有车门都已关闭
    public boolean areAllDoorsClosed() {
        if (this.consist != null) {
            return this.consist.areAllDoorsClosed();
        }
        return true;
    }
    
    // 开始维护模式
    public void startMaintenance() {
        if (this.consist != null) {
            this.consist.startMaintenance();
        }
    }
    
    // 结束维护模式
    public void endMaintenance() {
        if (this.consist != null) {
            this.consist.endMaintenance();
        }
    }
    
    // 检查是否处于维护模式
    public boolean isInMaintenance() {
        if (this.consist != null) {
            return this.consist.isInMaintenance();
        }
        return false;
    }
    
    // 获取供电系统
    public PowerSupplySystem getPowerSupplySystem() {
        return this.powerSupplySystem;
    }
    
    // 获取列车编组信息
    public Text getConsistInfo() {
        if (this.consist != null) {
            return this.consist.getInfoText();
        }
        return Text.literal("无列车编组");
    }
    
    // 获取当前线路
    public String getCurrentLine() {
        return this.dataTracker.get(CURRENT_LINE);
    }
    
    // 设置当前线路
    public void setCurrentLine(String lineId) {
        this.dataTracker.set(CURRENT_LINE, lineId);
        if (this.consist != null) {
            this.consist.setLineId(lineId);
        }
    }
    
    // 应用停放制动
    public void applyParkingBrake() {
        if (this.consist != null) {
            this.consist.applyParkingBrake();
        }
    }
    
    // 释放停放制动
    public void releaseParkingBrake() {
        if (this.consist != null) {
            this.consist.releaseParkingBrake();
        }
    }
    
    // 对所有车辆进行清洁
    public void cleanAllCars() {
        if (this.consist != null) {
            this.consist.cleanAllCars();
        }
    }
    
    // 获取故障车辆列表
    public List<TrainCar> getErrorCars() {
        if (this.consist != null) {
            return this.consist.getErrorCars();
        }
        return new ArrayList<>();
    }
    
    // 获取需要维护的车辆列表
    public List<TrainCar> getMaintenanceRequiredCars() {
        if (this.consist != null) {
            return this.consist.getMaintenanceRequiredCars();
        }
        return new ArrayList<>();
    }
    
    // 获取列车总重量
    public double getTotalWeight() {
        if (this.consist != null) {
            return this.consist.getTotalWeight();
        }
        return 0;
    }
    
    // 获取列车总运行小时数
    public double getTotalRunningHours() {
        if (this.consist != null) {
            return this.consist.getTotalRunningHours();
        }
        return 0;
    }
    
    // 获取平均清洁度
    public int getAverageCleanliness() {
        if (this.consist != null) {
            return this.consist.getAverageCleanliness();
        }
        return 100;
    }
    
    // 重置所有车辆的运行小时数（大修后）
    public void resetRunningHours() {
        if (this.consist != null) {
            this.consist.resetRunningHours();
        }
    }
}