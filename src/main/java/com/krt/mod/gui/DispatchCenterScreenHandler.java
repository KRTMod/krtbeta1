package com.krt.mod.gui;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.text.Text;
import com.krt.mod.KRTMod;

public class DispatchCenterScreenHandler extends ScreenHandler {
    
    public static final ScreenHandlerType<DispatchCenterScreenHandler> TYPE = 
        new ScreenHandlerType<>(DispatchCenterScreenHandler::new);
    
    static {
        // 注册屏幕处理器类型
        KRTMod.registerScreenHandlerType("dispatch_center", TYPE);
    }
    
    private final PlayerEntity player;
    
    public DispatchCenterScreenHandler(int syncId, PlayerInventory playerInventory) {
        super(TYPE, syncId);
        this.player = playerInventory.player;
        
        // 初始化槽位等（如果需要）
        // 调度中心主要用于显示信息和控制，可能不需要槽位
    }
    
    @Override
    public boolean canUse(PlayerEntity player) {
        // 可以添加权限检查，例如只有拥有特定权限的玩家才能使用调度中心
        return true;
    }
    
    // 可以添加自定义的数据包处理方法，用于服务器和客户端之间的通信
    
    public PlayerEntity getPlayer() {
        return player;
    }
    
    // 可以添加获取调度信息的方法，供GUI使用
    
    public Text getTitle() {
        return Text.translatable("gui.krt_mod.dispatch_center.title");
    }
}