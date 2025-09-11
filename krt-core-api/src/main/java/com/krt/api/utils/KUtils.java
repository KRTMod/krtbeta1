package com.krt.api.utils;

/**
 * KRT轨道交通模组通用工具类
 * 提供坐标计算、距离计算和角度转换等功能
 */
public class KUtils {
    
    /**
     * 计算两点之间的欧几里得距离
     */
    public static double calculateDistance(double x1, double y1, double z1, double x2, double y2, double z2) {
        double dx = x2 - x1;
        double dy = y2 - y1;
        double dz = z2 - z1;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }
    
    /**
     * 计算两点在XZ平面上的二维距离
     */
    public static double calculateDistance2D(double x1, double z1, double x2, double z2) {
        double dx = x2 - x1;
        double dz = z2 - z1;
        return Math.sqrt(dx * dx + dz * dz);
    }
    
    /**
     * 计算两点之间的方向角度（在XZ平面上）
     * 返回角度范围：0-360度，正北为0度，顺时针方向递增
     */
    public static double calculateDirectionAngle(double x1, double z1, double x2, double z2) {
        double dx = x2 - x1;
        double dz = z1 - z2; // 注意这里z轴方向相反
        
        double angle = Math.atan2(dx, dz) * 180.0 / Math.PI;
        if (angle < 0) {
            angle += 360.0;
        }
        
        return angle;
    }
    
    /**
     * 检查两个浮点数是否相等（考虑精度误差）
     */
    public static boolean equalsWithTolerance(double a, double b, double tolerance) {
        return Math.abs(a - b) <= tolerance;
    }
    
    /**
     * 将角度转换为弧度
     */
    public static double toRadians(double degrees) {
        return degrees * Math.PI / 180.0;
    }
    
    /**
     * 将弧度转换为角度
     */
    public static double toDegrees(double radians) {
        return radians * 180.0 / Math.PI;
    }
    
    /**
     * 计算线路坡度（千分比）
     * @param startY 起点Y坐标
     * @param endY 终点Y坐标
     * @param horizontalDistance 水平距离
     * @return 坡度千分比
     */
    public static double calculateGradient(double startY, double endY, double horizontalDistance) {
        if (horizontalDistance == 0) {
            return 0;
        }
        
        double verticalChange = endY - startY;
        return (verticalChange / horizontalDistance) * 1000.0;
    }
    
    /**
     * 检查坡度是否在允许范围内
     * @param gradient 坡度千分比
     * @param isMainLine 是否为正线
     * @param isStation 是否为车站区域
     * @param isDepot 是否为车厂区域
     * @return 是否在允许范围内
     */
    public static boolean isGradientValid(double gradient, boolean isMainLine, boolean isStation, boolean isDepot) {
        double absoluteGradient = Math.abs(gradient);
        
        if (isStation) {
            // 车站区域：地下站站台计算长度段线路坡度宜采用2‰，困难条件下可设在不大于3‰的坡道上
            // 地面和高架车站一般设在平坡段上，困难时可设在不大于3‰的坡道上
            return absoluteGradient <= 3.0;
        } else if (isDepot) {
            // 车厂线：宜设在平坡道上，条件困难时库外线可设在不大于1.5‰的坡道上
            return absoluteGradient <= 1.5;
        } else {
            // 区间正线：最大坡度不宜大于30‰，困难35‰
            // 联络线、出入线：最大坡度不宜大于35‰
            return absoluteGradient <= 35.0;
        }
    }
    
    /**
     * 检查转弯半径是否在允许范围内
     * @param radius 转弯半径（米）
     * @return 是否在允许范围内
     */
    public static boolean isCurveRadiusValid(double radius) {
        // 根据《城市轨道交通设施技术规范》（GB/T 14886-2017）规定，城市轨道交通地铁的曲线半径不小于300米
        return radius >= 300.0;
    }
    
    /**
     * 计算两个点与中心点形成的夹角（用于计算转弯半径）
     * @param x1 点1的X坐标
     * @param z1 点1的Z坐标
     * @param xCenter 中心点的X坐标
     * @param zCenter 中心点的Z坐标
     * @param x2 点2的X坐标
     * @param z2 点2的Z坐标
     * @return 夹角（弧度）
     */
    public static double calculateAngleBetweenPoints(double x1, double z1, double xCenter, double zCenter, double x2, double z2) {
        double dx1 = x1 - xCenter;
        double dz1 = z1 - zCenter;
        double dx2 = x2 - xCenter;
        double dz2 = z2 - zCenter;
        
        double dotProduct = dx1 * dx2 + dz1 * dz2;
        double magnitude1 = Math.sqrt(dx1 * dx1 + dz1 * dz1);
        double magnitude2 = Math.sqrt(dx2 * dx2 + dz2 * dz2);
        
        if (magnitude1 == 0 || magnitude2 == 0) {
            return 0;
        }
        
        double cosine = Math.max(-1.0, Math.min(1.0, dotProduct / (magnitude1 * magnitude2)));
        return Math.acos(cosine);
    }
    
    /**
     * 根据三点计算圆的半径
     * @param x1 点1的X坐标
     * @param z1 点1的Z坐标
     * @param x2 点2的X坐标
     * @param z2 点2的Z坐标
     * @param x3 点3的X坐标
     * @param z3 点3的Z坐标
     * @return 圆的半径
     */
    public static double calculateCircleRadius(double x1, double z1, double x2, double z2, double x3, double z3) {
        // 计算三角形的边长
        double a = calculateDistance2D(x1, z1, x2, z2);
        double b = calculateDistance2D(x2, z2, x3, z3);
        double c = calculateDistance2D(x3, z3, x1, z1);
        
        // 计算三角形的半周长
        double s = (a + b + c) / 2;
        
        // 计算三角形的面积
        double area = Math.sqrt(s * (s - a) * (s - b) * (s - c));
        
        // 如果面积为0，说明三点共线，返回无穷大
        if (area == 0) {
            return Double.MAX_VALUE;
        }
        
        // 计算外接圆半径
        return (a * b * c) / (4 * area);
    }
    
    /**
     * 限制值在指定范围内
     */
    public static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
    
    /**
     * 将值线性映射到新的范围
     */
    public static double map(double value, double fromMin, double fromMax, double toMin, double toMax) {
        if (fromMax - fromMin == 0) {
            return toMin;
        }
        return ((value - fromMin) / (fromMax - fromMin)) * (toMax - toMin) + toMin;
    }
    
    /**
     * 格式化时间（秒）为分:秒格式
     */
    public static String formatTime(int seconds) {
        int minutes = seconds / 60;
        int remainingSeconds = seconds % 60;
        return String.format("%d:%02d", minutes, remainingSeconds);
    }
    
    /**
     * 生成唯一ID
     */
    public static String generateUniqueId(String prefix) {
        return prefix + "_" + System.currentTimeMillis() + "_" + (int)(Math.random() * 1000);
    }
    
    /**
     * 检查字符串是否为空或仅包含空白字符
     */
    public static boolean isEmpty(String str) {
        return str == null || str.trim().isEmpty();
    }
    
    /**
     * 安全地解析整数，失败时返回默认值
     */
    public static int parseInteger(String str, int defaultValue) {
        try {
            return Integer.parseInt(str);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
    
    /**
     * 安全地解析浮点数，失败时返回默认值
     */
    public static double parseDouble(String str, double defaultValue) {
        try {
            return Double.parseDouble(str);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
    
    /**
     * 安全地解析布尔值，失败时返回默认值
     */
    public static boolean parseBoolean(String str, boolean defaultValue) {
        if (str == null) {
            return defaultValue;
        }
        str = str.trim().toLowerCase();
        if ("true".equals(str) || "yes".equals(str) || "1".equals(str)) {
            return true;
        } else if ("false".equals(str) || "no".equals(str) || "0".equals(str)) {
            return false;
        } else {
            return defaultValue;
        }
    }
    
    /**
     * 获取文件扩展名
     */
    public static String getFileExtension(String fileName) {
        if (fileName == null || !fileName.contains(".")) {
            return "";
        }
        return fileName.substring(fileName.lastIndexOf(".") + 1).toLowerCase();
    }
    
    /**
     * 计算两点之间的中点坐标
     */
    public static double[] calculateMidpoint(double x1, double y1, double z1, double x2, double y2, double z2) {
        double midX = (x1 + x2) / 2;
        double midY = (y1 + y2) / 2;
        double midZ = (z1 + z2) / 2;
        return new double[]{midX, midY, midZ};
    }
    
    /**
     * 根据方向角度和距离计算目标点坐标
     * @param startX 起点X坐标
     * @param startZ 起点Z坐标
     * @param angle 方向角度（度）
     * @param distance 距离
     * @return 目标点坐标 [x, z]
     */
    public static double[] calculateTargetPoint(double startX, double startZ, double angle, double distance) {
        double radians = toRadians(angle);
        double targetX = startX + Math.sin(radians) * distance;
        double targetZ = startZ - Math.cos(radians) * distance;
        return new double[]{targetX, targetZ};
    }
    
    /**
     * 计算两个角度之间的最小差值（考虑360度环绕）
     */
    public static double calculateAngleDifference(double angle1, double angle2) {
        double difference = angle2 - angle1;
        while (difference < -180) difference += 360;
        while (difference > 180) difference -= 360;
        return difference;
    }
    
    /**
     * 检查两个矩形区域是否相交
     */
    public static boolean doRectanglesIntersect(
            double x1, double z1, double width1, double height1,
            double x2, double z2, double width2, double height2) {
        return !(x1 + width1 < x2 || x1 > x2 + width2 || z1 + height1 < z2 || z1 > z2 + height2);
    }
}