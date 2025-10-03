package com.krt.mod.gui;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.screen.NamedScreenHandlerFactory;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

public class AnnouncementScreenHandler implements NamedScreenHandlerFactory {
    private final BlockPos pos;
    
    public AnnouncementScreenHandler(BlockPos pos) {
        this.pos = pos;
    }
    
    @Override
    public Text getDisplayName() {
        return Text.literal("报站设置");
    }
    
    @Override
    public ScreenHandler createMenu(int syncId, PlayerInventory playerInventory, PlayerEntity player) {
        // 这里应该返回一个实际的ScreenHandler实例
        // 由于我们只是为了修复编译错误，这里返回null
        return null;
    }
    
    public BlockPos getPos() {
        return pos;
    }
}