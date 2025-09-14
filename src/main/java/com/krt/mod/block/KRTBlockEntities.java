package com.krt.mod.block;

import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.util.Identifier;
import net.minecraft.util.registry.Registry;

import com.krt.mod.KRTMod;

public class KRTBlockEntities {
    // 注册ATP信号方块实体
    public static final BlockEntityType<ATPSignalBlockEntity> ATP_SIGNAL = Registry.register(
            Registry.BLOCK_ENTITY_TYPE,
            new Identifier(KRTMod.MOD_ID, "atp_signal"),
            FabricBlockEntityTypeBuilder.create(ATPSignalBlockEntity::new, Init.ATP_BLOCK).build(null)
    );

    // 可以在这里添加其他方块实体的注册
    
    public static void registerBlockEntities() {
        // 方块实体已经通过静态字段注册，这里可以添加其他初始化逻辑
        KRTMod.LOGGER.info("KRT方块实体已注册");
    }
}