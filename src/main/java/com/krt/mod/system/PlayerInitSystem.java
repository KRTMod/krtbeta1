package com.krt.mod.system;

import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import com.krt.mod.Init;
import com.krt.mod.KRTMod;

/**
 * 玩家初始化系统
 * 负责处理玩家首次加入游戏时的初始物品设置
 */
public class PlayerInitSystem {
    private static final PlayerInitSystem INSTANCE = new PlayerInitSystem();
    
    private PlayerInitSystem() {
        // 私有构造函数，防止外部实例化
    }
    
    /**
     * 获取单例实例
     */
    public static PlayerInitSystem getInstance() {
        return INSTANCE;
    }
    
    /**
     * 初始化玩家系统
     */
    public void initialize() {
        // 注册玩家连接事件监听器
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            PlayerEntity player = handler.player;
            // 检查玩家是否是首次加入（通过检查是否有经验等级）
            if (player.experienceLevel == 0 && player.totalExperience == 0) {
                // 给玩家初始物品
                giveInitialItems(player);
            }
        });
        
        KRTMod.LOGGER.info("玩家初始化系统已启动");
    }
    
    /**
     * 给玩家初始物品
     */
    private void giveInitialItems(PlayerEntity player) {
        // 清空第一个物品栏位置
        player.getInventory().setStack(0, ItemStack.EMPTY);
        
        // 在第一个物品栏位置放入轨道铺设器
        ItemStack trackPlacer = new ItemStack(Init.TRACK_PLACER_ITEM);
        player.getInventory().setStack(0, trackPlacer);
        
        // 在第一个物品栏位置之后放入轨道移除器
        ItemStack trackRemover = new ItemStack(Init.TRACK_REMOVER_ITEM);
        player.getInventory().insertStack(trackRemover);
        
        KRTMod.LOGGER.info("已为玩家 {} 分配初始物品", player.getName().getString());
    }
}