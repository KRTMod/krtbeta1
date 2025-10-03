package com.krt.mod.blockentity;

import com.krt.mod.KRTMod;
import com.krt.mod.block.ElevatorBlock;
import com.krt.mod.block.PlatformDoorBlock;
import com.krt.mod.block.PlatformLightBlock;
import com.krt.mod.block.power.OverheadWireBlock;
import com.krt.mod.block.power.OverheadWirePoleBlock;
import com.krt.mod.block.power.ThirdRailBlock;
import com.krt.mod.blockentity.power.OverheadWireBlockEntity;
import com.krt.mod.blockentity.power.OverheadWirePoleBlockEntity;
import com.krt.mod.blockentity.power.PowerGeneratorBlockEntity;
import com.krt.mod.blockentity.power.PowerStorageBlockEntity;
import com.krt.mod.blockentity.power.RailPowerConnectorBlockEntity;
import com.krt.mod.blockentity.power.ThirdRailBlockEntity;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.util.Identifier;
import net.minecraft.util.registry.Registry;

/**
 * 注册所有模组的方块实体类型
 */
public class ModBlockEntities {
    // 站台系统相关方块实体
    public static final BlockEntityType<PlatformDoorBlockEntity> PLATFORM_DOOR_BLOCK_ENTITY = 
            FabricBlockEntityTypeBuilder.create(PlatformDoorBlockEntity::new, 
                    com.krt.mod.block.PlatformDoorBlock.INSTANCE).build(null);
    
    public static final BlockEntityType<PlatformLightBlockEntity> PLATFORM_LIGHT_BLOCK_ENTITY = 
            FabricBlockEntityTypeBuilder.create(PlatformLightBlockEntity::new, 
                    com.krt.mod.block.PlatformLightBlock.INSTANCE).build(null);
                    
    // 能源系统相关方块实体
    public static final BlockEntityType<PowerGeneratorBlockEntity> POWER_GENERATOR = 
            FabricBlockEntityTypeBuilder.create(PowerGeneratorBlockEntity::new, 
                    com.krt.mod.block.power.PowerGeneratorBlock.INSTANCE).build(null);
    
    public static final BlockEntityType<RailPowerConnectorBlockEntity> RAIL_POWER_CONNECTOR = 
            FabricBlockEntityTypeBuilder.create(RailPowerConnectorBlockEntity::new, 
                    com.krt.mod.block.power.RailPowerConnectorBlock.INSTANCE).build(null);
    
    public static final BlockEntityType<PowerStorageBlockEntity> POWER_STORAGE = 
            FabricBlockEntityTypeBuilder.create(PowerStorageBlockEntity::new, 
                    com.krt.mod.block.power.PowerStorageBlock.INSTANCE).build(null);
    
    // 接触网系统方块实体
    public static final BlockEntityType<OverheadWirePoleBlockEntity> OVERHEAD_WIRE_POLE = 
            FabricBlockEntityTypeBuilder.create(OverheadWirePoleBlockEntity::new, 
                    OverheadWirePoleBlock.OVERHEAD_WIRE_POLE).build(null);
    
    public static final BlockEntityType<OverheadWireBlockEntity> OVERHEAD_WIRE = 
            FabricBlockEntityTypeBuilder.create(OverheadWireBlockEntity::new, 
                    OverheadWireBlock.OVERHEAD_WIRE).build(null);
    
    // 第三轨系统方块实体
    public static final BlockEntityType<ThirdRailBlockEntity> THIRD_RAIL = 
            FabricBlockEntityTypeBuilder.create(ThirdRailBlockEntity::new, 
                    ThirdRailBlock.THIRD_RAIL).build(null);
    
    // 电梯系统方块实体
    public static final BlockEntityType<ElevatorBlockEntity> ELEVATOR_BLOCK_ENTITY = 
            Registry.register(Registry.BLOCK_ENTITY_TYPE,
                    new Identifier(KRTMod.MOD_ID, "elevator_block_entity"),
                    BlockEntityType.Builder.create(ElevatorBlockEntity::new, ElevatorBlock.INSTANCE).build(null));
    
    /**
     * 注册所有方块实体类型
     */
    public static void registerAllBlockEntities() {
        // 注册站台系统相关方块实体
        Registry.register(Registry.BLOCK_ENTITY_TYPE, 
                new Identifier(KRTMod.MOD_ID, "platform_door"), PLATFORM_DOOR_BLOCK_ENTITY);
        
        Registry.register(Registry.BLOCK_ENTITY_TYPE, 
                new Identifier(KRTMod.MOD_ID, "platform_light"), PLATFORM_LIGHT_BLOCK_ENTITY);
                
        // 注册能源系统相关方块实体
        Registry.register(Registry.BLOCK_ENTITY_TYPE, 
                new Identifier(KRTMod.MOD_ID, "power_generator"), POWER_GENERATOR);
        
        Registry.register(Registry.BLOCK_ENTITY_TYPE, 
                new Identifier(KRTMod.MOD_ID, "rail_power_connector"), RAIL_POWER_CONNECTOR);
        
        Registry.register(Registry.BLOCK_ENTITY_TYPE, 
                new Identifier(KRTMod.MOD_ID, "power_storage"), POWER_STORAGE);
        
        // 注册接触网系统方块实体
        Registry.register(Registry.BLOCK_ENTITY_TYPE, 
                new Identifier(KRTMod.MOD_ID, "overhead_wire_pole"), OVERHEAD_WIRE_POLE);
        
        Registry.register(Registry.BLOCK_ENTITY_TYPE, 
                new Identifier(KRTMod.MOD_ID, "overhead_wire"), OVERHEAD_WIRE);
        
        // 注册第三轨系统方块实体
        Registry.register(Registry.BLOCK_ENTITY_TYPE, 
                new Identifier(KRTMod.MOD_ID, "third_rail"), THIRD_RAIL);
        
        KRTMod.LOGGER.info("站台系统、照明系统、能源系统、电梯系统和供电系统方块实体注册完成");
    }
}