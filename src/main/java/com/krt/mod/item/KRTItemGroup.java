package com.krt.mod.item;

import com.krt.mod.KRTMod;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/**
 * 模组物品分组类
 * 用于在游戏中组织和显示模组物品
 */
public class KRTItemGroup {
    
    /**
     * 模组主物品分组
     */
    public static final ItemGroup KRT_GROUP = new ItemGroup("krt.main") {
        @Override
        public ItemStack createIcon() {
            // 使用发电机作为图标（如果ModItems.POWER_GENERATOR_ITEM存在）
            try {
                return new ItemStack(ModItems.POWER_GENERATOR_ITEM);
            } catch (Exception e) {
                // 如果无法创建图标，返回一个空的ItemStack作为替代
                return ItemStack.EMPTY;
            }
        }
        
        @Override
        public Text getDisplayName() {
            return Text.translatable("itemGroup." + KRTMod.MOD_ID + ".main");
        }
    };
    
    /**
     * 注册所有物品到对应分组
     */
    public static void registerItemGroups() {
        // 简化的实现，不依赖外部API
    }
}