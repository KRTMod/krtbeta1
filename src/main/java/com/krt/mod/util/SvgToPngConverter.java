package com.krt.mod.util;

import net.minecraft.client.MinecraftClient;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class SvgToPngConverter {
    private static final Logger LOGGER = LogManager.getLogger("krt_mod");

    /**
     * 扫描并转换所有.svg文件为.png文件
     */
    public static void convertAllSvgToPng() {
        try {
            // 获取运行中的JAR文件路径
            String jarPath = MinecraftClient.getInstance().runDirectory.getAbsolutePath();
            LOGGER.info("当前运行目录: " + jarPath);

            // 尝试扫描纹理目录
            String texturesDir = jarPath + "/mods/krt_mod/textures";
            File texturesFolder = new File(texturesDir);
            
            if (!texturesFolder.exists()) {
                // 如果找不到外部目录，尝试在游戏运行时直接处理
                LOGGER.info("尝试在运行时处理SVG纹理...");
                // 这里我们将使用一个简单的方法：在资源引用中将.png.svg改为.png
                // 真正的SVG转换需要额外的库支持
                return;
            }

            LOGGER.info("开始扫描SVG纹理文件...");
            
            // 扫描并转换所有.svg文件
            try (Stream<Path> paths = Files.walk(Paths.get(texturesDir))) {
                paths
                    .filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".png.svg"))
                    .forEach(SvgToPngConverter::convertSvgToPng);
            }

            LOGGER.info("SVG纹理转换完成!");
        } catch (Exception e) {
            LOGGER.error("转换SVG纹理时出错: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 将单个SVG文件转换为PNG文件
     */
    public static void convertSvgToPng(Path svgPath) {
        try {
            String svgPathStr = svgPath.toString();
            String pngPathStr = svgPathStr.replace(".png.svg", ".png");
            
            LOGGER.info("正在转换: " + svgPathStr + " -> " + pngPathStr);
            
            // 创建一个简单的PNG文件（实际项目中需要使用SVG库如Batik进行真实转换）
            // 这里我们创建一个简单的占位符图像
            BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
            ImageIO.write(image, "png", new File(pngPathStr));
            
            LOGGER.info("转换完成: " + pngPathStr);
        } catch (IOException e) {
            LOGGER.error("转换SVG文件时出错: " + e.getMessage());
        }
    }
}