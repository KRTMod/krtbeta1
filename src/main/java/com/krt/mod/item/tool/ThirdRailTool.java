package com.krt.mod.item.tool;

import com.krt.mod.blockentity.power.ThirdRailBlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import com.krt.mod.item.material.CeramicItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

/**
 * 第三轨工具 - 用于维护和检查第三轨绝缘状态
 */
public class ThirdRailTool extends Item {
    
    public ThirdRailTool(Settings settings) {
        super(settings);
    }
    
    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity player, Hand hand) {
        if (world.isClient) {
            player.sendMessage(Text.translatable("krt.item.third_rail_tool.tooltip"), true);
        }
        return TypedActionResult.success(player.getStackInHand(hand));
    }
    
    /**
     * 与第三轨交互
     */
    public void interactWithRail(PlayerEntity player, Hand hand, ThirdRailBlockEntity railEntity) {
        // 检查是否有维修材料（陶瓷片）
        if (player.isSneaking()) {
            // 清理水损坏
            if (railEntity.getWaterDamage() > 0) {
                railEntity.cleanWaterDamage();
                player.sendMessage(Text.translatable("krt.item.third_rail_tool.clean_water"), true);
            } else {
                player.sendMessage(Text.translatable("krt.item.third_rail_tool.no_water_damage"), true);
            }
        } else {
            // 修复绝缘
            // 检查玩家是否有陶瓷片
            boolean hasCeramic = false;
            for (int i = 0; i < player.getInventory().size(); i++) {
                ItemStack stack = player.getInventory().getStack(i);
                if (stack.getItem() instanceof CeramicItem && stack.getCount() > 0) {
                    // 消耗一个陶瓷片
                    stack.decrement(1);
                    hasCeramic = true;
                    break;
                }
            }
            
            if (hasCeramic) {
                // 修复25点绝缘
                railEntity.repairInsulation(25);
                player.sendMessage(Text.translatable("krt.item.third_rail_tool.repair_insulation", 
                        railEntity.getInsulationLevel()), true);
            } else {
                player.sendMessage(Text.translatable("krt.item.third_rail_tool.need_ceramic"), true);
            }
        }
        
        // 消耗耐久
        ItemStack stack = player.getStackInHand(hand);
        stack.damage(1, player, (p) -> p.sendToolBreakStatus(hand));
    }
}