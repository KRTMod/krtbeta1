package com.krt.mod.item;

import net.fabricmc.fabric.api.item.v1.FabricItemSettings;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;
import com.krt.mod.Init;

import java.util.List;

public class OperationManualItem extends Item {
    public OperationManualItem(Settings settings) {
        super(settings);
    }

    @Override
    public void appendTooltip(ItemStack stack, World world, List<Text> tooltip, TooltipContext context) {
        tooltip.add(Text.translatable("item.krt.operation_manual.tooltip"));
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity player, Hand hand) {
        if (!world.isClient) {
            // 打开操作手册界面
            player.openHandledScreen(new ExtendedScreenHandlerFactory() {
                @Override
                public void writeScreenOpeningData(ServerPlayerEntity player, PacketByteBuf buf) {
                    // 可以在这里传递额外的数据给客户端
                }

                @Override
                public Text getDisplayName() {
                    return Text.translatable("item.krt.operation_manual.name");
                }

                @Override
                public ScreenHandler createMenu(int syncId, PlayerInventory playerInventory, PlayerEntity player) {
                    // 创建并返回操作手册的ScreenHandler
                    return new ScreenHandler(Init.OPERATION_MANUAL_SCREEN_HANDLER, syncId) {
                        @Override
                        public boolean canUse(PlayerEntity player) {
                            return true;
                        }

                        @Override
                        public ItemStack transferSlot(PlayerEntity player, int index) {
                            return ItemStack.EMPTY;
                        }
                    };
                }
            });
        }
        return TypedActionResult.success(player.getStackInHand(hand));
    }
}