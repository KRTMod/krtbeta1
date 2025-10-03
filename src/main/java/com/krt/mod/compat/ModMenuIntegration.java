package com.krt.mod.compat;

import com.krt.mod.screen.KRTConfigScreen;
import com.krt.mod.KRTMod;
import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public class ModMenuIntegration implements ModMenuApi {

    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        // 返回一个函数，该函数接收父屏幕并返回我们的配置屏幕实例
        return parent -> new KRTConfigScreen(parent);
    }
}