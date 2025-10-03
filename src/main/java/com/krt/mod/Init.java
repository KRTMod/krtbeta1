package com.krt.mod;

import com.krt.mod.block.*;
import com.krt.mod.entity.*;
import com.krt.mod.gui.OperationManualScreenHandler;
import com.krt.mod.gui.LineControlScreenHandler;
import com.krt.mod.gui.ModDebuggerScreenHandler;
import com.krt.mod.item.*;
import com.krt.mod.item.LightningStickItem;
import net.fabricmc.fabric.api.item.v1.FabricItemSettings;
import net.fabricmc.fabric.api.object.builder.v1.block.FabricBlockSettings;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricEntityTypeBuilder;
import net.fabricmc.fabric.api.screenhandler.v1.ScreenHandlerRegistry;
import net.minecraft.block.Block;
import net.minecraft.block.Material;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.util.Identifier;
import net.minecraft.util.registry.Registry;

public class Init {
    // 创建物品组 - 轨道与控制
    public static final ItemGroup TRACK_CONTROL_GROUP = new ItemGroup(0, "krt_mod_track_control_group") {
        @Override
        public ItemStack createIcon() {
            return new ItemStack(trackItem);
        }
    };
    
    // 创建物品组 - 车站建筑
    public static final ItemGroup STATION_BUILDING_GROUP = new ItemGroup(1, "krt_mod_station_building_group") {
        @Override
        public ItemStack createIcon() {
            return new ItemStack(platformBlockItem);
        }
    };

    // 轨道相关方块 (轨道与控制物品栏)
    public static final Block TRACK = new TrackBlock(FabricBlockSettings.of(Material.METAL).strength(2.0f).requiresTool());
    public static final Item trackItem = new BlockItem(TRACK, new FabricItemSettings().group(TRACK_CONTROL_GROUP));

    public static final Block SWITCH_TRACK = new SwitchTrackBlock(FabricBlockSettings.of(Material.METAL).strength(2.0f).requiresTool());
    public static final Item switchTrackItem = new BlockItem(SWITCH_TRACK, new FabricItemSettings().group(TRACK_CONTROL_GROUP));

    // 报站方块 (轨道与控制物品栏)
    public static final Block ANNOUNCEMENT_BLOCK = new AnnouncementBlock(FabricBlockSettings.of(Material.STONE).strength(2.0f).requiresTool());
    public static final Item announcementItem = new BlockItem(ANNOUNCEMENT_BLOCK, new FabricItemSettings().group(TRACK_CONTROL_GROUP));

    // 信号机方块 (轨道与控制物品栏)
    public static final Block SIGNAL_BLOCK = new SignalBlock(FabricBlockSettings.of(Material.METAL).strength(2.0f).requiresTool());
    public static final Item signalItem = new BlockItem(SIGNAL_BLOCK, new FabricItemSettings().group(TRACK_CONTROL_GROUP));

    // 信号系统相关方块 (轨道与控制物品栏)
    public static final Block ATP_BLOCK = new ATPSignalBlock(FabricBlockSettings.of(Material.METAL).strength(2.0f).requiresTool());
    public static final Item atpItem = new BlockItem(ATP_BLOCK, new FabricItemSettings().group(TRACK_CONTROL_GROUP));

    // 屏蔽门方块 (车站建筑物品栏)
    public static final Block PLATFORM_DOOR = new PlatformDoorBlock(FabricBlockSettings.of(Material.METAL).strength(3.0f).requiresTool());
    public static final Item platformDoorItem = new BlockItem(PLATFORM_DOOR, new FabricItemSettings().group(STATION_BUILDING_GROUP));

    // 钥匙物品 (轨道与控制物品栏)
    public static final Item DOOR_KEY = new KeyItem(new FabricItemSettings().group(TRACK_CONTROL_GROUP).maxCount(1));
    public static final Item DRIVER_KEY = new KeyItem(new FabricItemSettings().group(TRACK_CONTROL_GROUP).maxCount(1));

    // 新添加的方块 - 站台方块 (车站建筑物品栏)
    public static final Block PLATFORM_BLOCK = new PlatformBlock(FabricBlockSettings.of(Material.STONE).strength(1.5f).requiresTool());
    public static final Item platformBlockItem = new BlockItem(PLATFORM_BLOCK, new FabricItemSettings().group(STATION_BUILDING_GROUP));

    // 线路设置面板物品 (轨道与控制物品栏，手持打开)
    public static final Item LINE_SETTING_PANEL_ITEM = new LineSettingPanelItem(new FabricItemSettings().group(TRACK_CONTROL_GROUP).maxCount(1));

    // 列车操控面板物品 (轨道与控制物品栏，手持打开)
    public static final Item TRAIN_CONTROL_PANEL_ITEM = new TrainControlPanelItem(new FabricItemSettings().group(TRACK_CONTROL_GROUP).maxCount(1));
    
    // 轨道铺设器物品 (轨道与控制物品栏)
    public static final Item TRACK_PLACER_ITEM = new TrackPlacerItem(new FabricItemSettings().group(TRACK_CONTROL_GROUP).maxCount(1));
    
    // 轨道移除器物品 (轨道与控制物品栏)
    public static final Item TRACK_REMOVER_ITEM = new TrackRemoverItem(new FabricItemSettings().group(TRACK_CONTROL_GROUP).maxCount(1));

    // 操作手册物品 (车站建筑物品栏)
    public static final Item OPERATION_MANUAL_ITEM = new OperationManualItem(new FabricItemSettings().group(STATION_BUILDING_GROUP).maxCount(1));
    
    // 操作手册屏幕处理器类型
    public static final ScreenHandlerType<OperationManualScreenHandler> OPERATION_MANUAL_SCREEN_HANDLER = 
            ScreenHandlerRegistry.registerSimple(new Identifier(KRTMod.MOD_ID, "operation_manual"), 
                    OperationManualScreenHandler::new);
    
    // 线路设置面板屏幕处理器类型
    public static final ScreenHandlerType<LineControlScreenHandler> LINE_CONTROL_SCREEN_HANDLER = 
            ScreenHandlerRegistry.registerSimple(new Identifier(KRTMod.MOD_ID, "line_control"), 
                    LineControlScreenHandler::new);
                     
    // 调试器屏幕处理器类型
    public static final ScreenHandlerType<ModDebuggerScreenHandler> MOD_DEBUGGER_SCREEN_HANDLER = 
            ScreenHandlerRegistry.registerSimple(new Identifier(KRTMod.MOD_ID, "mod_debugger"), 
                    ModDebuggerScreenHandler::new);

    // 快捷键手册物品 (车站建筑物品栏)
    public static final Item SHORTCUT_MANUAL_ITEM = new ShortcutManualItem(new FabricItemSettings().group(STATION_BUILDING_GROUP).maxCount(1));
    
    // 模组调试器物品 (轨道与控制物品栏)
    public static final Item MOD_DEBUGGER_ITEM = new ModDebuggerItem(new FabricItemSettings().group(TRACK_CONTROL_GROUP).maxCount(1));
    
    // 闪电法杖物品 (轨道与控制物品栏)
    public static final Item LIGHTNING_STICK_ITEM = new LightningStickItem(new FabricItemSettings().group(TRACK_CONTROL_GROUP).maxCount(1).maxDamage(32));

    // 车站列车到站倒计时显示屏方块 (车站建筑物品栏)
    public static final Block STATION_COUNTDOWN_DISPLAY = new StationCountdownDisplayBlock(FabricBlockSettings.of(Material.METAL).strength(2.0f).requiresTool().nonOpaque().luminance(state -> 15));
    public static final Item stationCountdownDisplayItem = new BlockItem(STATION_COUNTDOWN_DISPLAY, new FabricItemSettings().group(STATION_BUILDING_GROUP));

    // 小电视方块 (车站建筑物品栏)
    public static final Block SMALL_TV = new SmallTVBlock(FabricBlockSettings.of(Material.METAL).strength(2.0f).requiresTool().nonOpaque().luminance(state -> 10));
    public static final Item smallTVItem = new BlockItem(SMALL_TV, new FabricItemSettings().group(STATION_BUILDING_GROUP));
    
    // 端门发车计时器方块 (车站建筑物品栏)
    public static final Block DEPARTURE_TIMER = new DepartureTimerBlock(FabricBlockSettings.of(Material.METAL).strength(2.0f).requiresTool().nonOpaque().luminance(state -> 15));
    public static final Item departureTimerItem = new BlockItem(DEPARTURE_TIMER, new FabricItemSettings().group(STATION_BUILDING_GROUP));

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
        // 注册轨道相关 (轨道与控制物品栏)
        Registry.register(Registry.BLOCK, new Identifier(KRTMod.MOD_ID, "track"), TRACK);
        Registry.register(Registry.ITEM, new Identifier(KRTMod.MOD_ID, "track"), trackItem);

        Registry.register(Registry.BLOCK, new Identifier(KRTMod.MOD_ID, "switch_track"), SWITCH_TRACK);
        Registry.register(Registry.ITEM, new Identifier(KRTMod.MOD_ID, "switch_track"), switchTrackItem);

        // 注册报站方块 (轨道与控制物品栏)
        Registry.register(Registry.BLOCK, new Identifier(KRTMod.MOD_ID, "announcement_block"), ANNOUNCEMENT_BLOCK);
        Registry.register(Registry.ITEM, new Identifier(KRTMod.MOD_ID, "announcement_block"), announcementItem);

        // 注册信号机方块 (轨道与控制物品栏)
        Registry.register(Registry.BLOCK, new Identifier(KRTMod.MOD_ID, "signal_block"), SIGNAL_BLOCK);
        Registry.register(Registry.ITEM, new Identifier(KRTMod.MOD_ID, "signal_block"), signalItem);

        // 注册信号系统相关方块 (轨道与控制物品栏)
        Registry.register(Registry.BLOCK, new Identifier(KRTMod.MOD_ID, "atp_block"), ATP_BLOCK);
        Registry.register(Registry.ITEM, new Identifier(KRTMod.MOD_ID, "atp_block"), atpItem);

        // 注册屏蔽门方块 (车站建筑物品栏)
        Registry.register(Registry.BLOCK, new Identifier(KRTMod.MOD_ID, "platform_door"), PLATFORM_DOOR);
        Registry.register(Registry.ITEM, new Identifier(KRTMod.MOD_ID, "platform_door"), platformDoorItem);

        // 注册钥匙物品 (轨道与控制物品栏)
        Registry.register(Registry.ITEM, new Identifier(KRTMod.MOD_ID, "door_key"), DOOR_KEY);
        Registry.register(Registry.ITEM, new Identifier(KRTMod.MOD_ID, "driver_key"), DRIVER_KEY);

        // 注册新添加的方块
        // 站台方块 (车站建筑物品栏)
        Registry.register(Registry.BLOCK, new Identifier(KRTMod.MOD_ID, "platform_block"), PLATFORM_BLOCK);
        Registry.register(Registry.ITEM, new Identifier(KRTMod.MOD_ID, "platform_block"), platformBlockItem);

        // 线路设置面板物品 (轨道与控制物品栏，手持打开)
        Registry.register(Registry.ITEM, new Identifier(KRTMod.MOD_ID, "line_setting_panel"), LINE_SETTING_PANEL_ITEM);

        // 列车操控面板物品 (轨道与控制物品栏，手持打开)
        Registry.register(Registry.ITEM, new Identifier(KRTMod.MOD_ID, "train_control_panel"), TRAIN_CONTROL_PANEL_ITEM);
        
        // 轨道铺设器物品 (轨道与控制物品栏)
        Registry.register(Registry.ITEM, new Identifier(KRTMod.MOD_ID, "track_placer"), TRACK_PLACER_ITEM);
        
        // 轨道移除器物品 (轨道与控制物品栏)
        Registry.register(Registry.ITEM, new Identifier(KRTMod.MOD_ID, "track_remover"), TRACK_REMOVER_ITEM);

        // 操作手册物品 (车站建筑物品栏)
        Registry.register(Registry.ITEM, new Identifier(KRTMod.MOD_ID, "operation_manual"), OPERATION_MANUAL_ITEM);

        // 快捷键手册物品 (车站建筑物品栏)
        Registry.register(Registry.ITEM, new Identifier(KRTMod.MOD_ID, "shortcut_manual"), SHORTCUT_MANUAL_ITEM);

        // 注册模组调试器物品
        Registry.register(Registry.ITEM, new Identifier(KRTMod.MOD_ID, "mod_debugger"), MOD_DEBUGGER_ITEM);

        // 车站列车到站倒计时显示屏方块 (车站建筑物品栏)
        Registry.register(Registry.BLOCK, new Identifier(KRTMod.MOD_ID, "station_countdown_display"), STATION_COUNTDOWN_DISPLAY);
        Registry.register(Registry.ITEM, new Identifier(KRTMod.MOD_ID, "station_countdown_display"), stationCountdownDisplayItem);

        // 小电视方块 (车站建筑物品栏)
        Registry.register(Registry.BLOCK, new Identifier(KRTMod.MOD_ID, "small_tv"), SMALL_TV);
        Registry.register(Registry.ITEM, new Identifier(KRTMod.MOD_ID, "small_tv"), smallTVItem);
        
        // 端门发车计时器方块 (车站建筑物品栏)
        Registry.register(Registry.BLOCK, new Identifier(KRTMod.MOD_ID, "departure_timer"), DEPARTURE_TIMER);
        Registry.register(Registry.ITEM, new Identifier(KRTMod.MOD_ID, "departure_timer"), departureTimerItem);
        
        // 注册闪电法杖物品 (轨道与控制物品栏)
        Registry.register(Registry.ITEM, new Identifier(KRTMod.MOD_ID, "lightning_stick"), LIGHTNING_STICK_ITEM);
        
        KRTMod.LOGGER.info("KRT 模组初始化完成!");
    }
}