package com.krt.mod.map;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Matrix4f;
import net.minecraft.world.World;
import com.krt.mod.gui.MapRenderer;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.UUID;

@Environment(EnvType.CLIENT)
public class MapExporter {
    private static final MinecraftClient client = MinecraftClient.getInstance();
    private static final String DEFAULT_EXPORT_DIR = "config/krt/maps";

    /**
     * 将小地图导出为PNG图片
     * @param mapRenderer 地图渲染器实例
     * @param width 图片宽度
     * @param height 图片高度
     * @param world 世界对象
     * @param customPath 自定义保存路径，如果为null则使用默认路径
     * @return 保存的文件路径，如果保存失败则返回null
     */
    public static String exportMapAsImage(MapRenderer mapRenderer, int width, int height, World world, String customPath) {
        try {
            // 创建图像缓冲区
            NativeImage image = new NativeImage(width, height, false);
            
            // 渲染地图到图像
            mapRenderer.renderToImage(image, width, height, world);
            
            // 生成文件名
            String fileName = generateFileName("png");
            
            // 确定保存路径
            File saveFile = getSaveFile(fileName, customPath);
            
            // 确保目录存在
            saveFile.getParentFile().mkdirs();
            
            // 保存图像
            try {
                // 转换NativeImage到BufferedImage然后保存
                java.awt.image.BufferedImage bufferedImage = new java.awt.image.BufferedImage(
                    width, height, java.awt.image.BufferedImage.TYPE_INT_ARGB
                );
                
                // 使用数组批量复制像素数据，比单个像素循环更高效
                int[] pixelArray = new int[width * height];
                
                // 将像素数据复制到数组
                for (int y = 0; y < height; y++) {
                    for (int x = 0; x < width; x++) {
                        int color = image.getColor(x, y);
                        pixelArray[y * width + x] = color;
                    }
                }
                
                // 使用setRGB方法一次性设置整行或整个图像的像素
                bufferedImage.setRGB(0, 0, width, height, pixelArray, 0, width);
                
                // 保存BufferedImage到文件
                javax.imageio.ImageIO.write(bufferedImage, "png", saveFile);
            } finally {
                image.close();
            }
            
            return saveFile.getAbsolutePath();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
    
    /**
     * 将小地图导出为PNG图片（使用默认大小）
     * @param mapRenderer 地图渲染器实例
     * @param world 世界对象
     * @param customPath 自定义保存路径，如果为null则使用默认路径
     * @return 保存的文件路径，如果保存失败则返回null
     */
    public static String exportMapAsImage(MapRenderer mapRenderer, World world, String customPath) {
        // 使用默认大小 500x500
        return exportMapAsImage(mapRenderer, 500, 500, world, customPath);
    }

    /**
     * 下载小地图数据文件
     * @param mapData 地图数据字符串
     * @param customPath 自定义保存路径，如果为null则使用默认路径
     * @return 保存的文件路径，如果保存失败则返回null
     */
    public static String downloadMapFile(String mapData, String customPath) {
        try {
            // 生成文件名
            String fileName = generateFileName("json");
            
            // 确定保存路径
            File saveFile = getSaveFile(fileName, customPath);
            
            // 确保目录存在
            saveFile.getParentFile().mkdirs();
            
            // 保存文件
            try (FileOutputStream fos = new FileOutputStream(saveFile)) {
                fos.write(mapData.getBytes());
            }
            
            return saveFile.getAbsolutePath();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * 生成带有时间戳的文件名
     */
    private static String generateFileName(String extension) {
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMdd_HHmmss");
        String timestamp = dateFormat.format(new Date());
        String uniqueId = UUID.randomUUID().toString().substring(0, 8);
        return "krt_map_" + timestamp + "_" + uniqueId + "." + extension;
    }

    /**
     * 获取保存文件的完整路径
     */
    private static File getSaveFile(String fileName, String customPath) {
        File gameDir = client.runDirectory;
        
        if (customPath != null && !customPath.isEmpty()) {
            return new File(customPath, fileName);
        } else {
            return new File(gameDir, DEFAULT_EXPORT_DIR + File.separator + fileName);
        }
    }

    /**
     * 获取默认导出目录
     */
    public static String getDefaultExportDirectory() {
        File gameDir = client.runDirectory;
        return new File(gameDir, DEFAULT_EXPORT_DIR).getAbsolutePath();
    }
}