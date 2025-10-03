package com.krt.mod.item.train;

import com.krt.mod.entity.TrainCar;
import com.krt.mod.system.PowerSupplySystem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * 受电弓 - 安装在列车顶部，从接触网获取电力
 */
public class PantographItem extends Item {
    
    public PantographItem(Settings settings) {
        super(settings);
    }
    
    @Override
    public Text getName(ItemStack stack) {
        return Text.translatable(this.getTranslationKey(stack))
                .formatted(Formatting.GOLD);
    }
    
    /**
     * 安装到列车
     */
    public boolean installToTrain(TrainCar trainCar) {
        if (trainCar.hasPantograph()) {
            return false; // 已有受电弓
        }
        
        trainCar.setPowerType(PowerSupplySystem.PowerType.OVERHEAD_WIRE);
        trainCar.setHasPantograph(true);
        return true;
    }
    
    /**
     * 从列车移除
     */
    public boolean removeFromTrain(TrainCar trainCar) {
        if (!trainCar.hasPantograph()) {
            return false; // 没有受电弓
        }
        
        trainCar.setHasPantograph(false);
        if (trainCar.getPowerType() == PowerSupplySystem.PowerType.OVERHEAD_WIRE) {
            trainCar.setPowerType(null);
        }
        return true;
    }
}