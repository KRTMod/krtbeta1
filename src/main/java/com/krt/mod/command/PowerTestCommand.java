package com.krt.mod.command;

import com.krt.mod.entity.TrainCar;
import com.krt.mod.system.PowerSupplySystem;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.entity.Entity;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;

/**
 * 供电系统测试命令，用于测试和调试接触网和第三轨系统
 */
public class PowerTestCommand {
    
    /**
     * 注册供电系统测试命令
     */
    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(CommandManager.literal("krt")
            .then(CommandManager.literal("power")
                .requires(source -> source.hasPermissionLevel(2))
                .then(CommandManager.literal("test")
                    .executes(PowerTestCommand::testPowerSystem)
                )
                .then(CommandManager.literal("pantograph")
                    .then(CommandManager.argument("target", EntityArgumentType.entity())
                        .then(CommandManager.argument("enabled", BoolArgumentType.bool())
                            .executes(PowerTestCommand::setPantograph)
                        )
                    )
                )
                .then(CommandManager.literal("shoe")
                    .then(CommandManager.argument("target", EntityArgumentType.entity())
                        .then(CommandManager.argument("enabled", BoolArgumentType.bool())
                            .executes(PowerTestCommand::setPowerCollectorShoe)
                        )
                    )
                )
                .then(CommandManager.literal("type")
                    .then(CommandManager.argument("target", EntityArgumentType.entity())
                        .then(CommandManager.literal("overhead")
                            .executes(context -> setPowerType(context, PowerSupplySystem.PowerType.OVERHEAD_WIRE))
                        )
                        .then(CommandManager.literal("thirdrail")
                            .executes(context -> setPowerType(context, PowerSupplySystem.PowerType.THIRD_RAIL))
                        )
                        .then(CommandManager.literal("battery")
                            .executes(context -> setPowerType(context, PowerSupplySystem.PowerType.BATTERY))
                        )
                    )
                )
                .then(CommandManager.literal("efficiency")
                    .then(CommandManager.literal("overhead")
                        .executes(PowerTestCommand::showOverheadWireEfficiency)
                    )
                    .then(CommandManager.literal("thirdrail")
                        .executes(PowerTestCommand::showThirdRailEfficiency)
                    )
                    .then(CommandManager.literal("battery")
                        .executes(PowerTestCommand::showBatteryEfficiency)
                    )
                )
            )
        );
    }
    
    /**
     * 测试供电系统功能
     */
    private static int testPowerSystem(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        
        // 显示供电系统类型信息
        source.sendFeedback(() -> Text.literal("=== KRT供电系统测试 ==="), false);
        source.sendFeedback(() -> Text.literal("接触网(OVERHEAD_WIRE): 1500V, 效率+15%"), false);
        source.sendFeedback(() -> Text.literal("第三轨(THIRD_RAIL): 750V, 效率-10%"), false);
        source.sendFeedback(() -> Text.literal("电池(BATTERY): 600V, 效率-5%"), false);
        
        // 显示测试命令提示
        source.sendFeedback(() -> Text.literal("\n可用命令:"), false);
        source.sendFeedback(() -> Text.literal("- /krt power pantograph <实体> <true|false>: 设置受电弓"), false);
        source.sendFeedback(() -> Text.literal("- /krt power shoe <实体> <true|false>: 设置受电靴"), false);
        source.sendFeedback(() -> Text.literal("- /krt power type <实体> <overhead|thirdrail|battery>: 设置供电类型"), false);
        source.sendFeedback(() -> Text.literal("- /krt power efficiency <overhead|thirdrail|battery>: 查看效率数据"), false);
        
        return 1;
    }
    
    /**
     * 设置受电弓状态
     */
    private static int setPantograph(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        Entity entity = EntityArgumentType.getEntity(context, "target");
        boolean enabled = BoolArgumentType.getBool(context, "enabled");
        
        if (entity instanceof TrainCar) {
            TrainCar car = (TrainCar) entity;
            car.setHasPantograph(enabled);
            source.sendFeedback(() -> Text.literal("已将车辆 '" + entity.getName().getString() + "' 的受电弓设置为: " + (enabled ? "启用" : "禁用")), false);
            
            // 如果启用受电弓，自动设置供电类型为接触网
            if (enabled) {
                car.setPowerType(PowerSupplySystem.PowerType.OVERHEAD_WIRE);
                source.sendFeedback(() -> Text.literal("自动将供电类型设置为: 接触网"), false);
            }
        } else {
            source.sendFeedback(() -> Text.literal("目标实体不是列车车辆"), false);
        }
        
        return 1;
    }
    
    /**
     * 设置受电靴状态
     */
    private static int setPowerCollectorShoe(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        Entity entity = EntityArgumentType.getEntity(context, "target");
        boolean enabled = BoolArgumentType.getBool(context, "enabled");
        
        if (entity instanceof TrainCar) {
            TrainCar car = (TrainCar) entity;
            car.setHasPowerCollectorShoe(enabled);
            source.sendFeedback(() -> Text.literal("已将车辆 '" + entity.getName().getString() + "' 的受电靴设置为: " + (enabled ? "启用" : "禁用")), false);
            
            // 如果启用受电靴，自动设置供电类型为第三轨
            if (enabled) {
                car.setPowerType(PowerSupplySystem.PowerType.THIRD_RAIL);
                source.sendFeedback(() -> Text.literal("自动将供电类型设置为: 第三轨"), false);
            }
        } else {
            source.sendFeedback(() -> Text.literal("目标实体不是列车车辆"), false);
        }
        
        return 1;
    }
    
    /**
     * 设置供电类型
     */
    private static int setPowerType(CommandContext<ServerCommandSource> context, PowerSupplySystem.PowerType type) {
        ServerCommandSource source = context.getSource();
        Entity entity = EntityArgumentType.getEntity(context, "target");
        
        if (entity instanceof TrainCar) {
            TrainCar car = (TrainCar) entity;
            car.setPowerType(type);
            
            String typeName;
            switch (type) {
                case OVERHEAD_WIRE:
                    typeName = "接触网";
                    break;
                case THIRD_RAIL:
                    typeName = "第三轨";
                    break;
                case BATTERY:
                    typeName = "电池";
                    break;
                default:
                    typeName = "未知";
            }
            
            source.sendFeedback(() -> Text.literal("已将车辆 '" + entity.getName().getString() + "' 的供电类型设置为: " + typeName), false);
        } else {
            source.sendFeedback(() -> Text.literal("目标实体不是列车车辆"), false);
        }
        
        return 1;
    }
    
    /**
     * 显示接触网效率数据
     */
    private static int showOverheadWireEfficiency(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        PowerSupplySystem.PowerType type = PowerSupplySystem.PowerType.OVERHEAD_WIRE;
        
        source.sendFeedback(() -> Text.literal("=== 接触网效率数据 ==="), false);
        source.sendFeedback(() -> Text.literal("电压: " + type.getVoltage() + "V"), false);
        source.sendFeedback(() -> Text.literal("效率系数: " + type.getEfficiency() + " (" + ((type.getEfficiency() - 1.0) * 100) + "%)"), false);
        source.sendFeedback(() -> Text.literal("功率损耗: 低"), false);
        source.sendFeedback(() -> Text.literal("最大传输距离: 长"), false);
        source.sendFeedback(() -> Text.literal("维护需求: 需要定期调整张力"), false);
        
        return 1;
    }
    
    /**
     * 显示第三轨效率数据
     */
    private static int showThirdRailEfficiency(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        PowerSupplySystem.PowerType type = PowerSupplySystem.PowerType.THIRD_RAIL;
        
        source.sendFeedback(() -> Text.literal("=== 第三轨效率数据 ==="), false);
        source.sendFeedback(() -> Text.literal("电压: " + type.getVoltage() + "V"), false);
        source.sendFeedback(() -> Text.literal("效率系数: " + type.getEfficiency() + " (" + ((type.getEfficiency() - 1.0) * 100) + "%)"), false);
        source.sendFeedback(() -> Text.literal("功率损耗: 中"), false);
        source.sendFeedback(() -> Text.literal("最大传输距离: 中"), false);
        source.sendFeedback(() -> Text.literal("维护需求: 需要定期检查绝缘"), false);
        
        return 1;
    }
    
    /**
     * 显示电池效率数据
     */
    private static int showBatteryEfficiency(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        PowerSupplySystem.PowerType type = PowerSupplySystem.PowerType.BATTERY;
        
        source.sendFeedback(() -> Text.literal("=== 电池效率数据 ==="), false);
        source.sendFeedback(() -> Text.literal("电压: " + type.getVoltage() + "V"), false);
        source.sendFeedback(() -> Text.literal("效率系数: " + type.getEfficiency() + " (" + ((type.getEfficiency() - 1.0) * 100) + "%)"), false);
        source.sendFeedback(() -> Text.literal("功率损耗: 高"), false);
        source.sendFeedback(() -> Text.literal("最大传输距离: 短"), false);
        source.sendFeedback(() -> Text.literal("维护需求: 需要定期充电"), false);
        
        return 1;
    }
}