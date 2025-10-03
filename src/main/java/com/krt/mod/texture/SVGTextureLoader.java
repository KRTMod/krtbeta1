package com.krt.mod.texture;

import com.krt.mod.system.LogSystem;
// import org.apache.batik.transcoder.TranscoderException;
// import org.apache.batik.transcoder.TranscoderInput;
// import org.apache.batik.transcoder.TranscoderOutput;
// import org.apache.batik.transcoder.image.PNGTranscoder;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * SVG纹理加载器
 * 负责加载和处理SVG格式的纹理文件
 */
public class SVGTextureLoader {
    
    // SVG纹理目录
    private static final String SVG_TEXTURE_DIR = "assets/krt/textures/svg";
    // 输出PNG纹理目录
    private static final String PNG_TEXTURE_DIR = "assets/krt/textures";
    
    /**
     * 初始化SVG纹理加载器
     * 此方法在KRTMod初始化时调用
     */
    public static void initialize() {
        try {
            LogSystem.systemLog("初始化SVG纹理加载器...");
            
            // 创建输出目录（如果不存在）
            createOutputDirectory();
            
            // 转换所有SVG文件为PNG格式
            convertAllSvgToPng();
            
            LogSystem.systemLog("SVG纹理加载器初始化完成");
        } catch (Exception e) {
            LogSystem.error("SVG纹理加载器初始化失败: " + e.getMessage());
        }
    }
    
    /**
     * 创建输出目录
     */
    private static void createOutputDirectory() {
        try {
            Path pngDir = Paths.get(PNG_TEXTURE_DIR);
            if (!Files.exists(pngDir)) {
                Files.createDirectories(pngDir);
                LogSystem.debugLog("已创建PNG纹理输出目录: " + PNG_TEXTURE_DIR);
            }
        } catch (IOException e) {
            LogSystem.warningLog("创建PNG纹理输出目录失败: " + e.getMessage());
        }
    }
    
    /**
     * 转换所有SVG文件为PNG格式
     */
    private static void convertAllSvgToPng() {
        try {
            File svgDir = new File(SVG_TEXTURE_DIR);
            if (!svgDir.exists() || !svgDir.isDirectory()) {
                LogSystem.warningLog("SVG纹理目录不存在: " + SVG_TEXTURE_DIR);
                return;
            }
            
            File[] svgFiles = svgDir.listFiles((dir, name) -> name.toLowerCase().endsWith(".svg"));
            if (svgFiles == null || svgFiles.length == 0) {
                LogSystem.debugLog("未找到SVG文件需要转换");
                return;
            }
            
            LogSystem.systemLog("找到 " + svgFiles.length + " 个SVG文件需要转换为PNG格式");
            
            for (File svgFile : svgFiles) {
                convertSvgToPng(svgFile);
            }
        } catch (Exception e) {
            LogSystem.error("转换SVG文件时发生错误: " + e.getMessage());
        }
    }
    
    /**
     * 将单个SVG文件转换为PNG格式
     * @param svgFile SVG文件
     */
    private static void convertSvgToPng(File svgFile) {
        try {
            String fileName = svgFile.getName();
            String pngFileName = fileName.substring(0, fileName.lastIndexOf('.')) + ".png";
            File pngFile = new File(PNG_TEXTURE_DIR + File.separator + pngFileName);
            
            // 检查输出文件是否已存在且是最新的
            if (pngFile.exists() && pngFile.lastModified() >= svgFile.lastModified()) {
                LogSystem.debugLog("PNG文件已存在且是最新的，跳过转换: " + pngFileName);
                return;
            }
            
            LogSystem.debugLog("正在转换SVG文件: " + fileName);
            
            // 使用Batik库进行转换
            PNGTranscoder transcoder = new PNGTranscoder();
            TranscoderInput input = new TranscoderInput(new FileInputStream(svgFile));
            TranscoderOutput output = new TranscoderOutput(new FileOutputStream(pngFile));
            
            transcoder.transcode(input, output);
            
            LogSystem.debugLog("SVG文件转换成功: " + fileName + " -> " + pngFileName);
        } catch (IOException | TranscoderException e) {
            LogSystem.error("转换SVG文件失败: " + svgFile.getName() + ", 错误: " + e.getMessage());
        }
    }
}