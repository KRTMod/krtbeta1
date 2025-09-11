package com.krt.api.block;

import net.minecraft.block.BlockState;
import net.minecraft.block.Material;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * 高架屏蔽门类
 * 适用于高架车站的屏蔽门
 */
public class ElevatedPlatformDoor extends PlatformDoor {
    // 高架屏蔽门特有属性
    private boolean hasWeatherProtection = true; // 是否有天气防护
    
    public ElevatedPlatformDoor() {
        super(Settings.of(Material.METAL)
                .strength(3.0f, 6.0f)
                .sounds(BlockSoundGroup.METAL)
                .nonOpaque()
                .requiresTool());
    }
    
    /**
     * 高架屏蔽门特有方法：检查天气影响
     */
    public void checkWeatherEffects(World world, BlockPos pos) {
        // 检查天气状态并应用相应的效果
        // 例如：雨天可能导致屏蔽门表面湿润等
    }
    
    /**
     * 设置是否有天气防护
     */
    public void setWeatherProtection(boolean hasProtection) {
        this.hasWeatherProtection = hasProtection;
    }
    
    /**
     * 检查是否有天气防护
     */
    public boolean hasWeatherProtection() {
        return hasWeatherProtection;
    }
    
    @Override
    protected void playOpenSound(World world, BlockPos pos) {
        // 高架屏蔽门特有的开门音效逻辑
        super.playOpenSound(world, pos);
    }
    
    @Override
    protected void playCloseSound(World world, BlockPos pos) {
        // 高架屏蔽门特有的关门音效逻辑
        super.playCloseSound(world, pos);
    }
}