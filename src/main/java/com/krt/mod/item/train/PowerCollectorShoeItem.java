package com.krt.mod.item.train;

import com.krt.mod.entity.TrainCar;
import com.krt.mod.system.PowerSupplySystem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * 受电靴 - 安装在列车底部，从第三轨获取电力
 */
public class PowerCollectorShoeItem extends Item {
    
    public PowerCollectorShoeItem(Settings settings) {
        super(settings);
    }
    
    @Override
    public Text getName(ItemStack stack) {
        return Text.translatable(this.getTranslationKey(stack))
                .formatted(Formatting.GREEN);
    }
    
    /**
     * 安装到列车
     */
    public boolean installToTrain(TrainCar trainCar) {
        if (trainCar.hasPowerCollectorShoe()) {
            return false; // 已有受电靴
        }
        
        trainCar.setPowerType(PowerSupplySystem.PowerType.THIRD_RAIL);
        trainCar.setHasPowerCollectorShoe(true);
        return true;
    }
    
    /**
     * 从列车移除
     */
    public boolean removeFromTrain(TrainCar trainCar) {
        if (!trainCar.hasPowerCollectorShoe()) {
            return false; // 没有受电靴
        }
        
        trainCar.setHasPowerCollectorShoe(false);
        if (trainCar.getPowerType() == PowerSupplySystem.PowerType.THIRD_RAIL) {
            trainCar.setPowerType(null);
        }
        return true;
    }
}