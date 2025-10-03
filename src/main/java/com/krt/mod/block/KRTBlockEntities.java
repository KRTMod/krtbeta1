package com.krt.mod.block;

import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.util.Identifier;
import net.minecraft.util.registry.Registry;

import com.krt.mod.KRTMod;
import com.krt.mod.Init;

public class KRTBlockEntities {
    // 注册ATP信号方块实体
    public static final BlockEntityType<ATPSignalBlockEntity> ATP_SIGNAL = Registry.register(
            Registry.BLOCK_ENTITY_TYPE,
            new Identifier(KRTMod.MOD_ID, "atp_signal"),
            FabricBlockEntityTypeBuilder.create(ATPSignalBlockEntity::new, Init.ATP_BLOCK).build(null)
    );

    // 注册车站列车到站倒计时显示屏方块实体
    public static final BlockEntityType<StationCountdownDisplayBlockEntity> STATION_COUNTDOWN_DISPLAY = Registry.register(
            Registry.BLOCK_ENTITY_TYPE,
            new Identifier(KRTMod.MOD_ID, "station_countdown_display"),
            FabricBlockEntityTypeBuilder.create(StationCountdownDisplayBlockEntity::new, Init.STATION_COUNTDOWN_DISPLAY).build(null)
    );
    
    // 注册端门发车计时器方块实体
    public static final BlockEntityType<DepartureTimerBlockEntity> DEPARTURE_TIMER = Registry.register(
            Registry.BLOCK_ENTITY_TYPE,
            new Identifier(KRTMod.MOD_ID, "departure_timer"),
            FabricBlockEntityTypeBuilder.create(DepartureTimerBlockEntity::new, Init.DEPARTURE_TIMER).build(null)
    );
    
    public static void registerBlockEntities() {
        // 方块实体已经通过静态字段注册，这里可以添加其他初始化逻辑
        KRTMod.LOGGER.info("KRT方块实体已注册");
    }
}