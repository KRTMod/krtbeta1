package com.krt.mod.item;

import net.fabricmc.fabric.api.item.v1.FabricItemSettings;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;
import com.krt.mod.Init;
import com.krt.mod.block.TrackBlock;
import com.krt.mod.block.TrackPlacementHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;

public class TrackRemoverItem extends Item {
    public TrackRemoverItem(Settings settings) {
        super(settings);
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity player, Hand hand) {
        ItemStack stack = player.getStackInHand(hand);
        
        // 执行射线检测，获取点击的方块
        BlockHitResult hitResult = raycast(world, player, RaycastContext.FluidHandling.NONE);
        if (hitResult.getType() != HitResult.Type.BLOCK) {
            return TypedActionResult.pass(stack);
        }
        
        BlockPos hitPos = hitResult.getBlockPos();
        
        // 检查点击的方块是否为轨道
        BlockState state = world.getBlockState(hitPos);
        if (TrackPlacementHelper.isTrackBlock(state) || TrackPlacementHelper.isSwitchTrackBlock(state)) {
            if (!world.isClient) {
                // 移除轨道方块
                world.breakBlock(hitPos, false);
                
                // 显示坐标信息
                player.sendMessage(Text.literal("已移除轨道于 X: " + hitPos.getX() + ", Y: " + hitPos.getY() + ", Z: " + hitPos.getZ()), false);
                
                // 消耗物品耐久（如果有）
                if (stack.isDamageable()) {
                    stack.damage(1, player, p -> p.sendToolBreakStatus(hand));
                }
            }
            return TypedActionResult.success(stack);
        }
        
        return TypedActionResult.pass(stack);
    }
    
    // 移除postRender方法，因为Item类中没有这个方法
}