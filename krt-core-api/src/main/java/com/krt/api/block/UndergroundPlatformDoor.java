package com.krt.api.block;

import net.minecraft.block.BlockState;
import net.minecraft.block.Material;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * 地下屏蔽门类
 * 适用于地下车站的屏蔽门
 */
public class UndergroundPlatformDoor extends PlatformDoor {
    // 地下屏蔽门特有属性
    private boolean hasFireResistance = true; // 是否有防火功能
    
    public UndergroundPlatformDoor() {
        super(Settings.of(Material.METAL)
                .strength(4.0f, 8.0f)
                .sounds(BlockSoundGroup.METAL)
                .nonOpaque()
                .requiresTool());
    }
    
    /**
     * 地下屏蔽门特有方法：检查防火状态
     */
    public void checkFireSafety(World world, BlockPos pos) {
        // 检查周围环境的防火状态
        // 例如：检测附近是否有火灾，并采取相应措施
    }
    
    /**
     * 设置是否有防火功能
     */
    public void setFireResistance(boolean hasResistance) {
        this.hasFireResistance = hasResistance;
    }
    
    /**
     * 检查是否有防火功能
     */
    public boolean hasFireResistance() {
        return hasFireResistance;
    }
    
    @Override
    protected void playOpenSound(World world, BlockPos pos) {
        // 地下屏蔽门特有的开门音效逻辑（可能带有回声效果）
        super.playOpenSound(world, pos);
    }
    
    @Override
    protected void playCloseSound(World world, BlockPos pos) {
        // 地下屏蔽门特有的关门音效逻辑（可能带有回声效果）
        super.playCloseSound(world, pos);
    }
}