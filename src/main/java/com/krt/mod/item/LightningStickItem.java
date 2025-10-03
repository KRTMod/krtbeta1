package com.krt.mod.item;

import net.fabricmc.fabric.api.item.v1.FabricItemSettings;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LightningEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

import java.util.List;

public class LightningStickItem extends Item {
    public LightningStickItem(Settings settings) {
        super(settings);
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, net.minecraft.entity.player.PlayerEntity user, Hand hand) {
        // 确保只在服务端生成闪电，避免客户端与服务端不同步
        if (world.isClient) {
            return TypedActionResult.pass(user.getStackInHand(hand));
        }

        // 获取玩家前方10格的位置
        BlockPos frontOfPlayer = user.getBlockPos().offset(user.getHorizontalFacing(), 10);

        // 生成闪电实体
        LightningEntity lightningBolt = new LightningEntity(EntityType.LIGHTNING_BOLT, world);
        // Minecraft 1.19.2中没有toCenterPos()方法，使用替代方法计算中心位置
        double centerX = frontOfPlayer.getX() + 0.5;
        double centerY = frontOfPlayer.getY() + 0.5;
        double centerZ = frontOfPlayer.getZ() + 0.5;
        lightningBolt.setPosition(centerX, centerY, centerZ);
        world.spawnEntity(lightningBolt);

        // 使用后减少物品耐久或数量
        ItemStack heldStack = user.getStackInHand(hand);
        heldStack.damage(1, user, player -> player.sendToolBreakStatus(hand));

        return TypedActionResult.success(heldStack);
    }

    @Override
    public void appendTooltip(ItemStack stack, World world, List<Text> tooltip, TooltipContext context) {
        tooltip.add(Text.literal("右键点击在前方召唤闪电"));
        tooltip.add(Text.literal("小心使用，可能会引发火灾"));
    }

    @Override
    public boolean isDamageable() {
        return true;
    }
    
    // 在Minecraft 1.19.2中，getMaxDamage()方法是final的，不能被覆盖
    // 物品的最大耐久度应该在构造时通过settings设置
    // 我们已经在Init.java中设置了maxDamage(32)
}