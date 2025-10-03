package com.krt.mod.item.tool;

import com.krt.mod.blockentity.power.OverheadWirePoleBlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

/**
 * 接触网工具 - 用于维护和调整接触网张力
 */
public class OverheadWireTool extends Item {
    
    public OverheadWireTool(Settings settings) {
        super(settings);
    }
    
    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity player, Hand hand) {
        if (world.isClient) {
            player.sendMessage(Text.translatable("krt.item.overhead_wire_tool.tooltip"), true);
        }
        return TypedActionResult.success(player.getStackInHand(hand));
    }
    
    /**
     * 与接触网支柱交互
     */
    public void interactWithPole(PlayerEntity player, Hand hand, OverheadWirePoleBlockEntity poleEntity) {
        // 根据玩家按键调整张力
        if (player.isSneaking()) {
            // 降低张力
            poleEntity.adjustTension(-5);
            player.sendMessage(Text.translatable("krt.item.overhead_wire_tool.decrease_tension", 
                    poleEntity.getTensionLevel()), true);
        } else {
            // 增加张力
            poleEntity.adjustTension(5);
            player.sendMessage(Text.translatable("krt.item.overhead_wire_tool.increase_tension", 
                    poleEntity.getTensionLevel()), true);
        }
        
        // 消耗耐久
        ItemStack stack = player.getStackInHand(hand);
        stack.damage(1, player, (p) -> p.sendToolBreakStatus(hand));
    }
}