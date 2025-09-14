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
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.Packet;
import net.minecraft.network.packet.s2c.play.EntitySpawnS2CPacket;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.text.Text;
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
import com.krt.mod.system.TrainControlSystem;
import java.util.ArrayList;
import java.util.List;

public class TrainEntity extends Entity {
    // 数据跟踪器字段
    private static final TrackedData<Boolean> ATO_ENABLED = DataTracker.registerData(TrainEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
    private static final TrackedData<Double> CURRENT_SPEED = DataTracker.registerData(TrainEntity.class, TrackedDataHandlerRegistry.DOUBLE);
    private static final TrackedData<String> DESTINATION = DataTracker.registerData(TrainEntity.class, TrackedDataHandlerRegistry.STRING);
    private static final TrackedData<String> NEXT_STATION = DataTracker.registerData(TrainEntity.class, TrackedDataHandlerRegistry.STRING);
    private static final TrackedData<Integer> HEALTH = DataTracker.registerData(TrainEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Boolean> EMERGENCY_BRAKE = DataTracker.registerData(TrainEntity.class, TrackedDataHandlerRegistry.BOOLEAN);

    // 列车控制系统
    private TrainControlSystem controlSystem;
    // 司机
    private PlayerEntity driver;
    // 自检系统
    private TrainSelfCheckSystem selfCheckSystem;
    // 历史位置记录，用于计算速度和方向
    private List<Vec3d> positionHistory = new ArrayList<>();

    public TrainEntity(EntityType<?> type, World world) {
        super(type, world);
        this.controlSystem = new TrainControlSystem(this);
        this.selfCheckSystem = new TrainSelfCheckSystem(this);
    }

    @Override
    protected void initDataTracker() {
        this.dataTracker.startTracking(ATO_ENABLED, true); // 默认启用ATO
        this.dataTracker.startTracking(CURRENT_SPEED, 0.0);
        this.dataTracker.startTracking(DESTINATION, "终点站");
        this.dataTracker.startTracking(NEXT_STATION, "下一站");
        this.dataTracker.startTracking(HEALTH, 100);
        this.dataTracker.startTracking(EMERGENCY_BRAKE, false);
    }

    @Override
    protected void readCustomDataFromNbt(NbtCompound nbt) {
        this.dataTracker.set(ATO_ENABLED, nbt.getBoolean("ATOEnabled"));
        this.dataTracker.set(CURRENT_SPEED, nbt.getDouble("CurrentSpeed"));
        this.dataTracker.set(DESTINATION, nbt.getString("Destination"));
        this.dataTracker.set(NEXT_STATION, nbt.getString("NextStation"));
        this.dataTracker.set(HEALTH, nbt.getInt("Health"));
        this.dataTracker.set(EMERGENCY_BRAKE, nbt.getBoolean("EmergencyBrake"));
    }

    @Override
    protected void writeCustomDataToNbt(NbtCompound nbt) {
        nbt.putBoolean("ATOEnabled", this.dataTracker.get(ATO_ENABLED));
        nbt.putDouble("CurrentSpeed", this.dataTracker.get(CURRENT_SPEED));
        nbt.putString("Destination", this.dataTracker.get(DESTINATION));
        nbt.putString("NextStation", this.dataTracker.get(NEXT_STATION));
        nbt.putInt("Health", this.dataTracker.get(HEALTH));
        nbt.putBoolean("EmergencyBrake", this.dataTracker.get(EMERGENCY_BRAKE));
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
            this.dataTracker.set(CURRENT_SPEED, speed);
        }

        // 运行自检系统
        this.selfCheckSystem.tick();

        // 检查健康状态，如果健康值低于50%，禁止运营
        if (this.dataTracker.get(HEALTH) < 50) {
            this.applyEmergencyBrake();
            return;
        }

        // 如果启用了ATO，让ATO控制列车
        if (this.dataTracker.get(ATO_ENABLED) && !this.dataTracker.get(EMERGENCY_BRAKE)) {
            this.controlSystem.runATO();
        }

        // 检查信号机状态，如果前方有红灯，触发紧急制动
        if (!this.dataTracker.get(EMERGENCY_BRAKE)) {
            this.checkSignalStatus();
        }

        // 播放列车走行音
        if (this.dataTracker.get(CURRENT_SPEED) > 0.1 && !this.world.isClient) {
            this.world.playSound(null, this.getBlockPos(), KRTMod.TRAIN_MOVING_SOUND, SoundCategory.NEUTRAL, 0.5F, 1.0F);
        }
    }

    @Override
    public ActionResult interact(PlayerEntity player, Hand hand) {
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
        } else if (this.driver == player) {
            // 司机可以打开列车驾驶控制面板
            if (!this.world.isClient) {
                player.openHandledScreen(new TrainControlScreenHandler(this));
            }
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
                this.world.playSound(null, this.getBlockPos(), KRTMod.EMERGENCY_BRAKE_SOUND, SoundCategory.NEUTRAL, 1.0F, 1.0F);
            }
            // 通知司机
            if (this.driver != null) {
                this.driver.sendMessage(Text.literal("紧急制动已触发！"), false);
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
        }
    }

    // 手动控制列车
    public void manualControl(double acceleration, double direction) {
        if (!this.dataTracker.get(ATO_ENABLED) && !this.dataTracker.get(EMERGENCY_BRAKE)) {
            // 根据方向和加速度调整列车的速度和方向
            Vec3d velocity = this.getVelocity();
            Vec3d newVelocity = this.getRotationVector().multiply(acceleration * direction);
            this.setVelocity(newVelocity);
        }
    }

    // 获取列车控制系统
    public TrainControlSystem getControlSystem() {
        return this.controlSystem;
    }

    // 设置ATO状态
    public void setATOEnabled(boolean enabled) {
        this.dataTracker.set(ATO_ENABLED, enabled);
    }

    // 获取ATO状态
    public boolean isATOEnabled() {
        return this.dataTracker.get(ATO_ENABLED);
    }

    // 获取当前速度
    public double getCurrentSpeed() {
        return this.dataTracker.get(CURRENT_SPEED);
    }

    // 设置目的地
    public void setDestination(String destination) {
        this.dataTracker.set(DESTINATION, destination);
    }

    // 获取目的地
    public String getDestination() {
        return this.dataTracker.get(DESTINATION);
    }

    // 设置下一站
    public void setNextStation(String nextStation) {
        this.dataTracker.set(NEXT_STATION, nextStation);
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

    // 获取司机
    public PlayerEntity getDriver() {
        return this.driver;
    }
}