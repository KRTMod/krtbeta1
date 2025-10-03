package com.krt.mod.gui;

import com.krt.mod.Init;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.text.Text;

public class OperationManualScreenHandler extends ScreenHandler {
    
    public OperationManualScreenHandler(int syncId, PlayerInventory playerInventory) {
        super(Init.OPERATION_MANUAL_SCREEN_HANDLER, syncId);
    }
    
    @Override
    public boolean canUse(PlayerEntity player) {
        // 任何玩家都可以使用操作手册
        return true;
    }
    
    @Override
    public ItemStack transferSlot(PlayerEntity player, int index) {
        // 操作手册界面不涉及物品转移
        return ItemStack.EMPTY;
    }
}