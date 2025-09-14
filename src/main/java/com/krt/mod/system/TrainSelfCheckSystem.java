package com.krt.mod.system;

import net.minecraft.text.Text;
import net.minecraft.world.World;
import com.krt.mod.KRTMod;
import com.krt.mod.entity.TrainEntity;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

public class TrainSelfCheckSystem {
    private final TrainEntity train;
    private final Random random = new Random();
    // 系统组件状态
    private Map<SystemComponent, ComponentStatus> componentStatuses = new HashMap<>();
    // 自检间隔（刻）
    private static final int CHECK_INTERVAL = 20 * 60; // 每分钟检查一次
    private int checkTimer = 0;

    public TrainSelfCheckSystem(TrainEntity train) {
        this.train = train;
        // 初始化所有系统组件的状态
        for (SystemComponent component : SystemComponent.values()) {
            componentStatuses.put(component, ComponentStatus.NORMAL);
        }
    }

    public void tick() {
        checkTimer++;
        if (checkTimer >= CHECK_INTERVAL) {
            checkTimer = 0;
            performSelfCheck();
        }

        // 随机生成一些故障（用于测试）
        if (random.nextDouble() < 0.0001) { // 很小的概率生成故障
            SystemComponent[] components = SystemComponent.values();
            SystemComponent randomComponent = components[random.nextInt(components.length)];
            setComponentStatus(randomComponent, ComponentStatus.FAULTY);
        }

        // 根据组件状态更新列车健康值
        updateTrainHealth();
    }

    // 执行自检
    private void performSelfCheck() {
        // 检查所有系统组件
        for (SystemComponent component : SystemComponent.values()) {
            // 这里可以实现实际的检查逻辑，现在只是随机模拟
            double healthProbability = calculateComponentHealthProbability(component);
            if (random.nextDouble() < healthProbability) {
                setComponentStatus(component, ComponentStatus.NORMAL);
            } else {
                setComponentStatus(component, ComponentStatus.FAULTY);
            }
        }
    }

    // 计算组件健康的概率
    private double calculateComponentHealthProbability(SystemComponent component) {
        // 不同组件有不同的健康概率
        switch (component) {
            case ENGINE:
                return 0.99; // 引擎99%的概率正常
            case BRAKES:
                return 0.98; // 制动系统98%的概率正常
            case SIGNAL:
                return 0.97; // 信号系统97%的概率正常
            case DOORS:
                return 0.96; // 车门系统96%的概率正常
            case POWER:
                return 0.95; // 动力系统95%的概率正常
            default:
                return 0.98;
        }
    }

    // 设置组件状态
    private void setComponentStatus(SystemComponent component, ComponentStatus status) {
        ComponentStatus oldStatus = componentStatuses.get(component);
        if (oldStatus != status) {
            componentStatuses.put(component, status);
            // 如果状态变为故障，通知司机
            if (status == ComponentStatus.FAULTY && train.getDriver() != null) {
                train.getDriver().sendMessage(Text.literal("系统警告: " + component.getDisplayName() + " 出现故障！"), false);
            }
        }
    }

    // 根据组件状态更新列车健康值
    private void updateTrainHealth() {
        int faultyComponents = 0;
        int totalComponents = componentStatuses.size();

        for (ComponentStatus status : componentStatuses.values()) {
            if (status == ComponentStatus.FAULTY) {
                faultyComponents++;
            }
        }

        // 计算健康值（0-100）
        int health = 100 - (faultyComponents * 100 / totalComponents);
        train.setHealth(health);

        // 如果健康值低于50%，触发警告
        if (health < 50 && train.getDriver() != null) {
            train.getDriver().sendMessage(Text.literal("严重警告: 列车健康值过低（" + health + "%），请立即返回车辆段进行检修！"), false);
        }
    }

    // 获取组件状态
    public ComponentStatus getComponentStatus(SystemComponent component) {
        return componentStatuses.getOrDefault(component, ComponentStatus.NORMAL);
    }

    // 修复所有组件
    public void repairAllComponents() {
        for (SystemComponent component : SystemComponent.values()) {
            setComponentStatus(component, ComponentStatus.NORMAL);
        }
        if (train.getDriver() != null) {
            train.getDriver().sendMessage(Text.literal("所有系统组件已修复"), false);
        }
    }

    // 系统组件枚举
    public enum SystemComponent {
        ENGINE("引擎系统"),
        BRAKES("制动系统"),
        SIGNAL("信号系统"),
        DOORS("车门系统"),
        POWER("动力系统"),
        ATC("列车自动控制系统"),
        ATS("自动监控系统");

        private final String displayName;

        SystemComponent(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    // 组件状态枚举
    public enum ComponentStatus {
        NORMAL,
        FAULTY
    }
}