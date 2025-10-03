package com.krt.mod.gui;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.text.Text;

public class LineControlScreenHandler extends ScreenHandler {
    
    // 用于ScreenHandlerRegistry.registerSimple的构造函数
    public LineControlScreenHandler(int syncId, PlayerInventory playerInventory) {
        super(null, syncId);
    }
    
    // 用于LineSettingPanelItem的构造函数
    public LineControlScreenHandler(int syncId) {
        super(null, syncId);
    }
    
    @Override
    public boolean canUse(PlayerEntity player) {
        // 任何人都可以使用这个界面
        return true;
    }
    
    @Override
    public ItemStack transferSlot(PlayerEntity player, int index) {
        // 由于这是一个设置面板而不是物品栏界面，我们不需要处理物品转移
        return ItemStack.EMPTY;
    }
}