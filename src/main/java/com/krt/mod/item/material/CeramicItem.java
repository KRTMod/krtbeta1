package com.krt.mod.item.material;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * 陶瓷片 - 用于第三轨绝缘维修的材料
 */
public class CeramicItem extends Item {
    
    public CeramicItem(Settings settings) {
        super(settings);
    }
    
    @Override
    public Text getName(ItemStack stack) {
        return Text.translatable(this.getTranslationKey(stack)).formatted(Formatting.BLUE);
    }
}