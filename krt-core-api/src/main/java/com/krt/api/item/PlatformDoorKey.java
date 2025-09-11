package com.krt.api.item;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * 屏蔽门钥匙类
 * 用于手动开关屏蔽门
 */
public class PlatformDoorKey extends Item {
    // 钥匙类型
    private String keyType = "universal";
    // 钥匙ID
    private String keyId;
    
    public PlatformDoorKey(Settings settings) {
        super(settings.maxCount(1).rarity(Rarity.UNCOMMON));
        this.keyId = generateKeyId();
    }
    
    /**
     * 生成唯一的钥匙ID
     */
    private String generateKeyId() {
        return "key_" + System.currentTimeMillis() + "_" + (int)(Math.random() * 1000);
    }
    
    /**
     * 获取钥匙ID
     */
    public String getKeyId() {
        return keyId;
    }
    
    /**
     * 设置钥匙类型
     */
    public void setKeyType(String type) {
        this.keyType = type;
    }
    
    /**
     * 获取钥匙类型
     */
    public String getKeyType() {
        return keyType;
    }
    
    /**
     * 检查钥匙是否可以打开指定屏蔽门
     */
    public boolean canOpenDoor(String doorId) {
        // 通用钥匙可以打开所有门
        if (keyType.equals("universal")) {
            return true;
        }
        
        // 专用钥匙只能打开特定的门
        return keyType.equals(doorId);
    }
    
    @Override
    public Text getName(ItemStack stack) {
        return Text.literal(Formatting.GOLD + "屏蔽门钥匙" + Formatting.RESET + " (" + keyType + ")");
    }
}