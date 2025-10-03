package com.krt.mod.gui;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.text.Text;
import com.krt.mod.entity.TrainEntity;

public class TrainControlScreenHandler extends ScreenHandler {
    private final TrainEntity train;
    
    public TrainControlScreenHandler(TrainEntity train) {
        super(null, -1);
        this.train = train;
    }
    
    @Override
    public boolean canUse(PlayerEntity player) {
        // 只有列车司机可以使用这个界面
        return player == train.getDriver() && player.squaredDistanceTo(train) < 10.0;
    }
    
    public TrainEntity getTrain() {
        return train;
    }
    
    @Override
    public ItemStack transferSlot(PlayerEntity player, int index) {
        // 由于这是一个控制面板而不是物品栏界面，我们不需要处理物品转移
        return ItemStack.EMPTY;
    }
}