package com.krt.mod.util;

import com.krt.mod.KRTMod;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.Identifier;
import net.minecraft.util.registry.Registry;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

public class TextureReferenceFixer {
    private static final Logger LOGGER = LogManager.getLogger("krt_mod");
    private static final Map<String, String> FIXED_TEXTURE_REFERENCES = new HashMap<>();

    /**
     * 初始化纹理引用修复器
     */
    public static void init() {
        LOGGER.info("初始化纹理引用修复器...");
        
        // 在实际项目中，这里可以加载配置文件来指定需要修复的纹理引用
        // 但在这个简单实现中，我们将动态处理
    }

    /**
     * 修复纹理引用，将.png.svg改为.png
     */
    @Environment(EnvType.CLIENT)
    public static Identifier fixTextureReference(Identifier originalId) {
        if (originalId.getNamespace().equals(KRTMod.MOD_ID)) {
            String path = originalId.getPath();
            if (path.endsWith(".png.svg")) {
                String fixedPath = path.replace(".png.svg", ".png");
                Identifier fixedId = new Identifier(KRTMod.MOD_ID, fixedPath);
                
                // 检查修复后的纹理是否存在
                if (checkTextureExists(fixedId)) {
                    return fixedId;
                } else {
                    // 如果修复后的纹理不存在，尝试创建一个简单的占位符纹理
                    LOGGER.warn("修复后的纹理不存在: " + fixedId + "，尝试创建占位符...");
                    createPlaceholderTexture(fixedId);
                    return fixedId;
                }
            }
        }
        return originalId;
    }

    /**
     * 检查纹理是否存在
     */
    @Environment(EnvType.CLIENT)
    private static boolean checkTextureExists(Identifier textureId) {
        try {
            MinecraftClient.getInstance().getResourceManager().getResource(textureId);
            return true;
        } catch (Exception e) {
            // 使用更通用的Exception捕获，因为getResource()可能不会直接抛出IOException
            return false;
        }
    }

    /**
     * 创建占位符纹理
     */
    @Environment(EnvType.CLIENT)
    private static void createPlaceholderTexture(Identifier textureId) {
        try {
            // 在实际项目中，这里应该创建一个真正的PNG文件
            // 但在这个简单实现中，我们只是记录日志
            LOGGER.info("创建占位符纹理: " + textureId);
            
            // 在开发环境中，可以尝试将SVG文件转换为PNG
            String svgPath = MinecraftClient.getInstance().runDirectory + "/mods/" + 
                             textureId.getNamespace() + "/textures/" + 
                             textureId.getPath().replace(".png", ".png.svg");
            
            File svgFile = new File(svgPath);
            if (svgFile.exists()) {
                LOGGER.info("找到对应的SVG文件，尝试转换: " + svgFile.getAbsolutePath());
                // 调用SVG转换工具
                SvgToPngConverter.convertSvgToPng(Paths.get(svgPath));
            }
        } catch (Exception e) {
            LOGGER.error("创建占位符纹理时出错: " + e.getMessage());
        }
    }
}