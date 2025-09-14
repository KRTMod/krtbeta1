package com.krt.mod;

import com.krt.mod.block.*;
import com.krt.mod.entity.*;
import com.krt.mod.item.*;
import net.fabricmc.fabric.api.item.v1.FabricItemSettings;
import net.fabricmc.fabric.api.object.builder.v1.block.FabricBlockSettings;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricEntityTypeBuilder;
import net.minecraft.block.Block;
import net.minecraft.block.Material;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroup;
import net.minecraft.util.Identifier;
import net.minecraft.util.registry.Registry;

public class Init {
    // 创建一个物品组
    public static final ItemGroup KRT_GROUP = new ItemGroup("krt_group") {
        @Override
        public Item.Settings getIconSettings() {
            return new Item.Settings();
        }
        
        @Override
        public Item getIconItem() {
            return trackItem;
        }
    };

    // 轨道相关方块
    public static final Block TRACK = new TrackBlock(FabricBlockSettings.of(Material.METAL).strength(2.0f).requiresTool());
    public static final Item trackItem = new BlockItem(TRACK, new FabricItemSettings().group(KRT_GROUP));

    public static final Block SWITCH_TRACK = new SwitchTrackBlock(FabricBlockSettings.of(Material.METAL).strength(2.0f).requiresTool());
    public static final Item switchTrackItem = new BlockItem(SWITCH_TRACK, new FabricItemSettings().group(KRT_GROUP));

    // 报站方块
    public static final Block ANNOUNCEMENT_BLOCK = new AnnouncementBlock(FabricBlockSettings.of(Material.STONE).strength(2.0f).requiresTool());
    public static final Item announcementItem = new BlockItem(ANNOUNCEMENT_BLOCK, new FabricItemSettings().group(KRT_GROUP));

    // 信号机方块
    public static final Block SIGNAL_BLOCK = new SignalBlock(FabricBlockSettings.of(Material.METAL).strength(2.0f).requiresTool());
    public static final Item signalItem = new BlockItem(SIGNAL_BLOCK, new FabricItemSettings().group(KRT_GROUP));

    // 屏蔽门方块
    public static final Block PLATFORM_DOOR = new PlatformDoorBlock(FabricBlockSettings.of(Material.METAL).strength(3.0f).requiresTool());
    public static final Item platformDoorItem = new BlockItem(PLATFORM_DOOR, new FabricItemSettings().group(KRT_GROUP));

    // 钥匙物品
    public static final Item DOOR_KEY = new KeyItem(new FabricItemSettings().group(KRT_GROUP).maxCount(1));

    // 信号系统相关方块
    public static final Block ATP_BLOCK = new ATPSignalBlock(FabricBlockSettings.of(Material.METAL).strength(2.0f).requiresTool());
    public static final Item atpItem = new BlockItem(ATP_BLOCK, new FabricItemSettings().group(KRT_GROUP));

    // 列车实体
    public static final EntityType<TrainEntity> TRAIN = Registry.register(
            Registry.ENTITY_TYPE,
            new Identifier(KRTMod.MOD_ID, "train"),
            FabricEntityTypeBuilder.create(SpawnGroup.MISC, TrainEntity::new)
                    .dimensions(EntityDimensions.fixed(3.0f, 3.0f))
                    .trackedUpdateRate(20)
                    .build()
    );

    // 初始化所有方块、物品和实体
    public static void init() {
        // 注册轨道相关
        Registry.register(Registry.BLOCK, new Identifier(KRTMod.MOD_ID, "track"), TRACK);
        Registry.register(Registry.ITEM, new Identifier(KRTMod.MOD_ID, "track"), trackItem);

        Registry.register(Registry.BLOCK, new Identifier(KRTMod.MOD_ID, "switch_track"), SWITCH_TRACK);
        Registry.register(Registry.ITEM, new Identifier(KRTMod.MOD_ID, "switch_track"), switchTrackItem);

        // 注册报站方块
        Registry.register(Registry.BLOCK, new Identifier(KRTMod.MOD_ID, "announcement_block"), ANNOUNCEMENT_BLOCK);
        Registry.register(Registry.ITEM, new Identifier(KRTMod.MOD_ID, "announcement_block"), announcementItem);

        // 注册信号机方块
        Registry.register(Registry.BLOCK, new Identifier(KRTMod.MOD_ID, "signal_block"), SIGNAL_BLOCK);
        Registry.register(Registry.ITEM, new Identifier(KRTMod.MOD_ID, "signal_block"), signalItem);

        // 注册屏蔽门方块
        Registry.register(Registry.BLOCK, new Identifier(KRTMod.MOD_ID, "platform_door"), PLATFORM_DOOR);
        Registry.register(Registry.ITEM, new Identifier(KRTMod.MOD_ID, "platform_door"), platformDoorItem);

        // 注册钥匙物品
        Registry.register(Registry.ITEM, new Identifier(KRTMod.MOD_ID, "door_key"), DOOR_KEY);

        // 注册信号系统相关方块
        Registry.register(Registry.BLOCK, new Identifier(KRTMod.MOD_ID, "atp_block"), ATP_BLOCK);
        Registry.register(Registry.ITEM, new Identifier(KRTMod.MOD_ID, "atp_block"), atpItem);

        KRTMod.LOGGER.info("KRT 模组初始化完成!");
    }
}