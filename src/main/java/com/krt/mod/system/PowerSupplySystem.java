package com.krt.mod.system;

import com.krt.mod.blockentity.power.PowerGeneratorBlockEntity;
import com.krt.mod.blockentity.power.PowerStorageBlockEntity;
import com.krt.mod.blockentity.power.RailPowerConnectorBlockEntity;
import com.krt.mod.entity.TrainConsist;
import java.util.*;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;

/**
 * 供电系统类，负责为列车及设备提供电力
 */
public class PowerSupplySystem {
    // 供电状态
    public enum PowerStatus {
        NORMAL,
        WARNING,
        ERROR,
        OUTAGE
    }
    
    // 供电类型
    public enum PowerType {
        OVERHEAD_WIRE(1500, 1.15, "接触网系统", "高架高压供电"),  // 1500V, 效率+15%
        THIRD_RAIL(750, 0.90, "第三轨系统", "地面低压供电"),      // 750V, 效率-10%
        BATTERY(600, 0.95, "电池系统", "应急供电");            // 600V, 效率-5%
        
        private final int voltage;      // 电压(V)
        private final double efficiency; // 效率系数
        private final String name;      // 系统名称
        private final String description; // 系统描述
        
        PowerType(int voltage, double efficiency, String name, String description) {
            this.voltage = voltage;
            this.efficiency = efficiency;
            this.name = name;
            this.description = description;
        }
        
        public int getVoltage() {
            return voltage;
        }
        
        public double getEfficiency() {
            return efficiency;
        }
        
        public String getName() {
            return name;
        }
        
        public String getDescription() {
            return description;
        }
    }
    
    private final World world;
    private final Map<BlockPos, PowerSource> powerSources = new HashMap<>();
    private final List<PowerGeneratorBlockEntity> generators = new ArrayList<>();
    private final List<PowerStorageBlockEntity> storageUnits = new ArrayList<>();
    private final List<RailPowerConnectorBlockEntity> railConnectors = new ArrayList<>();
    private PowerStatus systemStatus = PowerStatus.NORMAL;
    private int totalPower = 0;
    private int generatedPower = 0;
    private int consumedPower = 0;
    private int storedPower = 0;
    private int voltage = 1500; // 默认1500V直流电
    
    /**
     * 创建供电系统
     */
    public PowerSupplySystem(World world) {
        this.world = world;
    }
    
    /**
     * 添加电源
     */
    public boolean addPowerSource(BlockPos pos, PowerType type, int capacity) {
        if (powerSources.containsKey(pos)) {
            return false;
        }
        
        PowerSource source = new PowerSource(type, capacity);
        powerSources.put(pos, source);
        totalPower += capacity;
        return true;
    }
    
    /**
     * 移除电源
     */
    public boolean removePowerSource(BlockPos pos) {
        PowerSource source = powerSources.remove(pos);
        if (source != null) {
            totalPower -= source.capacity;
            return true;
        }
        return false;
    }
    
    /**
     * 为列车提供牵引电力
     */
    public int provideTractionPower(TrainConsist consist, int requestedPower, PowerType powerType) {
        // 检查系统状态
        if (systemStatus == PowerStatus.OUTAGE || systemStatus == PowerStatus.ERROR) {
            return 0; // 无法供电
        }
        
        // 计算可用电力
        int availablePower = calculateAvailablePower();
        
        // 考虑供电类型的效率差异
        double efficiency = powerType.getEfficiency();
        int adjustedRequest = (int)(requestedPower / efficiency); // 反向计算所需原始电力
        
        int actualPower = Math.min(adjustedRequest, availablePower);
        
        // 更新消耗的电力
        consumedPower += actualPower;
        
        // 应用效率系数并转换为交流电
        int efficientPower = (int)(actualPower * efficiency);
        return invertDcToAc(efficientPower);
    }
    
    // 兼容旧版本的方法
    public int provideTractionPower(TrainConsist consist, int requestedPower) {
        // 默认使用接触网供电
        return provideTractionPower(consist, requestedPower, PowerType.OVERHEAD_WIRE);
    }
    
    /**
     * 为设备提供动力照明电力
     */
    public int provideUtilityPower(BlockPos devicePos, int requestedPower) {
        // 检查系统状态
        if (systemStatus == PowerStatus.OUTAGE || systemStatus == PowerStatus.ERROR) {
            return 0;
        }
        
        // 检查设备是否在供电范围内
        if (!isDeviceInPowerRange(devicePos)) {
            return 0;
        }
        
        // 计算可用电力
        int availablePower = calculateAvailablePower();
        int actualPower = Math.min(requestedPower, availablePower);
        
        // 更新消耗的电力
        consumedPower += actualPower;
        
        // 提供电力（通常不需要转换，直接使用直流电）
        return actualPower;
    }
    
    /**
     * 将直流电转换为交流电
     */
    private int invertDcToAc(int dcPower) {
        // 简化的转换逻辑，实际应用中需要更复杂的计算
        // 假设逆变器效率为95%
        return (int)(dcPower * 0.95);
    }
    
    /**
     * 计算可用电力
     */
    private int calculateAvailablePower() {
        // 考虑负载和系统状态
        if (systemStatus == PowerStatus.WARNING) {
            // 警告状态下，限制功率输出为70%
            return (int)(totalPower * 0.7 - consumedPower);
        }
        return totalPower - consumedPower;
    }
    
    /**
     * 检查设备是否在供电范围内
     */
    private boolean isDeviceInPowerRange(BlockPos devicePos) {
        // 检查设备是否在任意电源的供电范围内（32格）
        for (BlockPos sourcePos : powerSources.keySet()) {
            if (sourcePos.getSquaredDistance(devicePos) <= 32 * 32) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * 更新系统
     */
    public void update() {
        // 重置消耗的电力
        consumedPower = 0;
        generatedPower = 0;
        storedPower = 0;
        
        // 更新发电机状态和发电量
        updateGenerators();
        
        // 更新存储单元状态
        updateStorageUnits();
        
        // 检查传统电源状态
        checkPowerSources();
        
        // 更新轨道连接器
        updateRailConnectors();
        
        // 分配电力
        distributePower();
        
        // 更新系统状态
        updateSystemStatus();
    }
    
    /**
     * 更新发电机状态
     */
    private void updateGenerators() {
        for (PowerGeneratorBlockEntity generator : generators) {
            if (generator.isActive()) {
                generatedPower += generator.getPowerGeneration();
            }
        }
    }
    
    /**
     * 更新存储单元状态
     */
    private void updateStorageUnits() {
        int totalStored = 0;
        for (PowerStorageBlockEntity storage : storageUnits) {
            totalStored += storage.getPower();
        }
        storedPower = totalStored;
    }
    
    /**
     * 更新轨道连接器
     */
    private void updateRailConnectors() {
        for (RailPowerConnectorBlockEntity connector : railConnectors) {
            // 设置连接器的电力等级基于系统状态
            int powerLevel = 0;
            switch (systemStatus) {
                case NORMAL -> powerLevel = 10;
                case WARNING -> powerLevel = 7;
                case ERROR -> powerLevel = 3;
                case OUTAGE -> powerLevel = 0;
            }
            connector.setPowerLevel(powerLevel);
        }
    }
    
    /**
     * 分配电力到存储单元和消耗设备
     */
    private void distributePower() {
        // 计算净电力（发电量减去消耗）
        int netPower = generatedPower - consumedPower;
        
        if (netPower > 0) {
            // 有多余电力，存入存储单元
            storeExcessPower(netPower);
        } else if (netPower < 0) {
            // 电力不足，从存储单元补充
            int deficit = Math.abs(netPower);
            int retrieved = retrievePowerFromStorage(deficit);
            consumedPower -= retrieved; // 减少实际消耗
        }
    }
    
    /**
     * 存储多余电力到存储单元
     */
    private void storeExcessPower(int excessPower) {
        if (storageUnits.isEmpty()) return;
        
        int powerPerStorage = excessPower / storageUnits.size();
        int remainder = excessPower % storageUnits.size();
        
        for (int i = 0; i < storageUnits.size(); i++) {
            PowerStorageBlockEntity storage = storageUnits.get(i);
            int powerToAdd = powerPerStorage + (i < remainder ? 1 : 0);
            storage.storePower(powerToAdd);
        }
    }
    
    /**
     * 从存储单元检索电力
     */
    private int retrievePowerFromStorage(int requiredPower) {
        if (storageUnits.isEmpty() || requiredPower <= 0) return 0;
        
        int retrievedPower = 0;
        int remainingPower = requiredPower;
        
        // 按存储量降序排序，优先从存储量大的单元获取
        storageUnits.sort((a, b) -> Integer.compare(b.getPower(), a.getPower()));
        
        for (PowerStorageBlockEntity storage : storageUnits) {
            if (remainingPower <= 0) break;
            
            int availablePower = storage.getPower();
            int powerToTake = Math.min(availablePower, remainingPower);
            storage.releasePower(powerToTake);
            remainingPower -= powerToTake;
            retrievedPower += powerToTake;
        }
        
        return retrievedPower;
    }
    
    /**
     * 检查电源状态
     */
    private void checkPowerSources() {
        int activeSources = 0;
        int warningSources = 0;
        int errorSources = 0;
        
        for (PowerSource source : powerSources.values()) {
            source.update();
            
            if (source.status == PowerStatus.NORMAL) {
                activeSources++;
            } else if (source.status == PowerStatus.WARNING) {
                warningSources++;
            } else if (source.status == PowerStatus.ERROR || source.status == PowerStatus.OUTAGE) {
                errorSources++;
            }
        }
        
        // 记录各状态电源数量
        if (errorSources > powerSources.size() / 2) {
            systemStatus = PowerStatus.ERROR;
        } else if (warningSources > powerSources.size() / 2) {
            systemStatus = PowerStatus.WARNING;
        } else if (activeSources == 0) {
            systemStatus = PowerStatus.OUTAGE;
        } else {
            systemStatus = PowerStatus.NORMAL;
        }
    }
    
    /**
     * 更新系统状态
     */
    private void updateSystemStatus() {
        // 计算总可用电力（传统电源 + 发电机 + 存储）
        int totalAvailablePower = totalPower + generatedPower + storedPower;
        double loadPercentage = totalAvailablePower > 0 ? (double)consumedPower / totalAvailablePower : 0;
        
        // 根据负载百分比更新状态
        if (loadPercentage > 0.9) {
            systemStatus = PowerStatus.ERROR;
        } else if (loadPercentage > 0.7) {
            systemStatus = PowerStatus.WARNING;
        } else {
            // 检查是否有活跃的电源或发电机
            boolean hasActivePower = !powerSources.isEmpty() || !generators.isEmpty() || storedPower > 0;
            systemStatus = hasActivePower ? PowerStatus.NORMAL : PowerStatus.OUTAGE;
        }
    }
    

    
    /**
     * 获取系统状态信息
     */
    public Text getStatusText() {
        String statusText;
        switch (systemStatus) {
            case NORMAL -> statusText = "正常";
            case WARNING -> statusText = "警告";
            case ERROR -> statusText = "故障";
            case OUTAGE -> statusText = "断电";
            default -> statusText = "未知";
        }
        
        // 计算总可用电力
        int totalAvailablePower = totalPower + generatedPower + storedPower;
        double loadPercentage = totalAvailablePower > 0 ? (double)consumedPower / totalAvailablePower * 100 : 0;
        
        return Text.literal("供电系统状态: " + statusText + 
                ", 传统电源: " + totalPower + 
                ", 发电机输出: " + generatedPower + 
                ", 存储电量: " + storedPower + 
                ", 负载: " + String.format("%.1f%%", loadPercentage) + 
                ", 电压: " + voltage + "V");
    }
    
    // Getters and setters
    public PowerStatus getSystemStatus() {
        return systemStatus;
    }
    
    public int getTotalPower() {
        return totalPower;
    }
    
    public int getGeneratedPower() {
        return generatedPower;
    }
    
    public int getStoredPower() {
        return storedPower;
    }
    
    public int getConsumedPower() {
        return consumedPower;
    }
    
    public int getVoltage() {
        return voltage;
    }
    
    public void setVoltage(int voltage) {
        this.voltage = voltage;
    }
    
    /**
     * 添加发电机到系统
     */
    public void addGenerator(PowerGeneratorBlockEntity generator) {
        if (!generators.contains(generator)) {
            generators.add(generator);
        }
    }
    
    /**
     * 从系统移除发电机
     */
    public void removeGenerator(PowerGeneratorBlockEntity generator) {
        generators.remove(generator);
    }
    
    /**
     * 添加存储单元到系统
     */
    public void addStorageUnit(PowerStorageBlockEntity storage) {
        if (!storageUnits.contains(storage)) {
            storageUnits.add(storage);
        }
    }
    
    /**
     * 从系统移除存储单元
     */
    public void removeStorageUnit(PowerStorageBlockEntity storage) {
        storageUnits.remove(storage);
    }
    
    /**
     * 添加轨道连接器到系统
     */
    public void addRailConnector(RailPowerConnectorBlockEntity connector) {
        if (!railConnectors.contains(connector)) {
            railConnectors.add(connector);
        }
    }
    
    /**
     * 从系统移除轨道连接器
     */
    public void removeRailConnector(RailPowerConnectorBlockEntity connector) {
        railConnectors.remove(connector);
    }
    
    /**
     * 获取指定位置的轨道电力等级
     */
    public int getRailPowerLevel(BlockPos pos) {
        // 查找最近的轨道连接器
        RailPowerConnectorBlockEntity closestConnector = null;
        double closestDistance = Double.MAX_VALUE;
        
        for (RailPowerConnectorBlockEntity connector : railConnectors) {
            double distance = connector.getPos().getSquaredDistance(pos);
            if (distance < closestDistance && distance <= connector.getCoverageRadius() * connector.getCoverageRadius()) {
                closestDistance = distance;
                closestConnector = connector;
            }
        }
        
        return closestConnector != null ? closestConnector.getPowerLevel() : 0;
    }
    
    /**
     * 检查位置是否在供电范围内
     */
    public boolean isPositionPowered(BlockPos pos, int range) {
        // 检查是否有轨道连接器在范围内
        for (RailPowerConnectorBlockEntity connector : railConnectors) {
            if (connector.getPos().getSquaredDistance(pos) <= range * range && connector.getPowerLevel() > 0) {
                return true;
            }
        }
        
        // 检查传统电源
        return superIsPositionPowered(pos, range);
    }
    
    /**
     * 原始的位置供电检查方法
     */
    private boolean superIsPositionPowered(BlockPos pos, int range) {
        // 检查指定位置范围内是否有可用的电源
        for (BlockPos sourcePos : powerSources.keySet()) {
            if (sourcePos.getSquaredDistance(pos) <= range * range) {
                PowerSource source = powerSources.get(sourcePos);
                if (source.status == PowerStatus.NORMAL) {
                    return true;
                }
            }
        }
        return false;
    }
    
    /**
     * 将系统状态保存到NBT数据中
     */
    public NbtCompound toNbt() {
        NbtCompound nbt = new NbtCompound();
        
        // 保存系统基本状态
        nbt.putString("systemStatus", systemStatus.name());
        nbt.putInt("totalPower", totalPower);
        nbt.putInt("consumedPower", consumedPower);
        nbt.putInt("voltage", voltage);
        
        // 保存电源信息 - 创建电源位置和属性的列表
        NbtList powerSourcesList = new NbtList();
        for (Map.Entry<BlockPos, PowerSource> entry : powerSources.entrySet()) {
            BlockPos pos = entry.getKey();
            PowerSource source = entry.getValue();
            
            NbtCompound sourceNbt = new NbtCompound();
            // 保存位置信息
            sourceNbt.putInt("posX", pos.getX());
            sourceNbt.putInt("posY", pos.getY());
            sourceNbt.putInt("posZ", pos.getZ());
            // 保存电源属性
            sourceNbt.putString("type", source.type.name());
            sourceNbt.putInt("capacity", source.capacity);
            sourceNbt.putString("sourceStatus", source.status.name());
            sourceNbt.putInt("health", source.health);
            sourceNbt.putInt("operatingHours", source.operatingHours);
            
            powerSourcesList.add(sourceNbt);
        }
        nbt.put("powerSources", powerSourcesList);
        
        return nbt;
    }
    
    /**
     * 从NBT数据中恢复系统状态
     */
    public void fromNbt(NbtCompound nbt) {
        // 恢复系统基本状态
        this.systemStatus = PowerStatus.valueOf(nbt.getString("systemStatus"));
        this.totalPower = nbt.getInt("totalPower");
        this.consumedPower = nbt.getInt("consumedPower");
        this.voltage = nbt.getInt("voltage");
        
        // 清除现有电源并从NBT中恢复
        this.powerSources.clear();
        
        if (nbt.contains("powerSources")) {
            NbtList powerSourcesList = nbt.getList("powerSources", NbtElement.COMPOUND_TYPE);
            for (int i = 0; i < powerSourcesList.size(); i++) {
                NbtCompound sourceNbt = powerSourcesList.getCompound(i);
                
                // 恢复位置
                BlockPos pos = new BlockPos(
                    sourceNbt.getInt("posX"),
                    sourceNbt.getInt("posY"),
                    sourceNbt.getInt("posZ")
                );
                
                // 创建新的PowerSource
                PowerType type = PowerType.valueOf(sourceNbt.getString("type"));
                int capacity = sourceNbt.getInt("capacity");
                
                PowerSource source = new PowerSource(type, capacity);
                source.status = PowerStatus.valueOf(sourceNbt.getString("sourceStatus"));
                source.health = sourceNbt.getInt("health");
                source.operatingHours = sourceNbt.getInt("operatingHours");
                
                // 添加到电源列表
                this.powerSources.put(pos, source);
            }
        }
    }
    
    /**
     * 电源类，表示单个电源
     */
    private static class PowerSource {
        private final PowerType type;
        private final int capacity;
        private PowerStatus status = PowerStatus.NORMAL;
        private int health = 100;
        private int operatingHours = 0;
        
        public PowerSource(PowerType type, int capacity) {
            this.type = type;
            this.capacity = capacity;
        }
        
        public void update() {
            // 更新运行时间
            operatingHours++;
            
            // 根据运行时间和健康值更新状态
            if (health <= 30) {
                status = PowerStatus.ERROR;
            } else if (health <= 60 || operatingHours > 10000) {
                status = PowerStatus.WARNING;
            } else {
                status = PowerStatus.NORMAL;
            }
            
            // 模拟老化
            if (operatingHours % 1000 == 0) {
                health -= 5;
                if (health < 0) health = 0;
            }
        }
        
        // 可以添加维修方法
        public void repair() {
            health = 100;
            status = PowerStatus.NORMAL;
        }
    }
}