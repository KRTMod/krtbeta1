package com.krt.mod.system;

import java.io.*;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.logging.*;
import com.krt.mod.KRTMod;

public class LogSystem {
    private static final Logger LOGGER = Logger.getLogger("KRT Mod");
    private static FileHandler fileHandler;
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    private static boolean initialized = false;

    // 日志级别枚举
    public enum LogLevel {
        INFO,
        WARNING,
        ERROR,
        DEBUG
    }

    // 初始化日志系统
    public static synchronized void init() {
        if (initialized) return;

        try {
            // 创建日志目录
            File logDir = new File("logs/krt");
            if (!logDir.exists()) {
                logDir.mkdirs();
            }

            // 创建日志文件（每天一个新文件）
            String logFileName = "krt-" + new SimpleDateFormat("yyyy-MM-dd").format(new Date()) + ".log";
            File logFile = new File(logDir, logFileName);

            // 设置文件处理器，追加模式
            fileHandler = new FileHandler(logFile.getAbsolutePath(), true);
            fileHandler.setFormatter(new LogFormatter());

            // 添加处理器到Logger
            LOGGER.addHandler(fileHandler);
            LOGGER.setLevel(Level.ALL);
            LOGGER.setUseParentHandlers(false); // 不使用父处理器（避免控制台重复输出）

            initialized = true;
            log(LogLevel.INFO, "日志系统初始化成功");
        } catch (IOException e) {
            KRTMod.LOGGER.error("初始化日志系统失败", e);
        }
    }

    // 记录日志
    public static void log(LogLevel level, String message) {
        if (!initialized) {
            init();
        }

        String formattedMessage = "[" + DATE_FORMAT.format(new Date()) + "] [" + level + "] " + message;
        System.out.println(formattedMessage); // 输出到控制台

        // 写入文件
        switch (level) {
            case INFO:
                LOGGER.info(message);
                break;
            case WARNING:
                LOGGER.warning(message);
                break;
            case ERROR:
                LOGGER.severe(message);
                break;
            case DEBUG:
                LOGGER.fine(message);
                break;
        }
    }

    // 记录信息日志
    public static void info(String message) {
        log(LogLevel.INFO, message);
    }

    // 记录警告日志
    public static void warning(String message) {
        log(LogLevel.WARNING, message);
    }

    // 记录错误日志
    public static void error(String message) {
        log(LogLevel.ERROR, message);
    }

    // 记录调试日志
    public static void debug(String message) {
        log(LogLevel.DEBUG, message);
    }

    // 记录系统日志（与info类似，但用于系统级消息）
    public static void systemLog(String message) {
        log(LogLevel.INFO, "[系统] " + message);
    }

    // 记录列车相关日志
    public static void trainLog(String trainId, String message) {
        log(LogLevel.INFO, "[列车 " + trainId + "] " + message);
    }

    // 记录线路相关日志
    public static void lineLog(String lineId, String message) {
        log(LogLevel.INFO, "[线路 " + lineId + "] " + message);
    }

    // 记录调度相关日志
    public static void dispatchLog(String message) {
        log(LogLevel.INFO, "[调度] " + message);
    }

    // 记录车厂相关日志
    public static void depotLog(String depotId, String message) {
        log(LogLevel.INFO, "[车厂 " + depotId + "] " + message);
    }

    // 自定义日志格式化器
    private static class LogFormatter extends Formatter {
        @Override
        public String format(LogRecord record) {
            String level = record.getLevel().getName();
            String message = formatMessage(record);
            String timestamp = DATE_FORMAT.format(new Date(record.getMillis()));
            String threadName = Thread.currentThread().getName();
            String className = record.getSourceClassName();
            String methodName = record.getSourceMethodName();

            return "[" + timestamp + "] [" + level + "] [" + threadName + "] [" + className + "." + methodName + "] " + message + "\n";
        }
    }

    // 关闭日志系统
    public static synchronized void close() {
        if (initialized && fileHandler != null) {
            log(LogLevel.INFO, "日志系统已关闭");
            fileHandler.close();
            initialized = false;
        }
    }

    // 获取日志文件路径
    public static String getLogFilePath() {
        if (!initialized) {
            init();
        }

        if (fileHandler != null) {
            for (File f : fileHandler.getFiles()) {
                return f.getAbsolutePath();
            }
        }

        return "logs/krt/krt-" + new SimpleDateFormat("yyyy-MM-dd").format(new Date()) + ".log";
    }

    // 清理旧日志文件（保留最近7天的日志）
    public static void cleanOldLogs() {
        File logDir = new File("logs/krt");
        if (!logDir.exists()) {
            return;
        }

        long sevenDaysAgo = System.currentTimeMillis() - (7L * 24 * 60 * 60 * 1000);
        File[] logFiles = logDir.listFiles();
        if (logFiles != null) {
            for (File file : logFiles) {
                if (file.lastModified() < sevenDaysAgo) {
                    if (file.delete()) {
                        info("已删除旧日志文件: " + file.getName());
                    } else {
                        warning("无法删除旧日志文件: " + file.getName());
                    }
                }
            }
        }
    }

    // 导出日志文件（用于调试和问题报告）
    public static File exportLogs() {
        try {
            File exportDir = new File("exports");
            if (!exportDir.exists()) {
                exportDir.mkdirs();
            }

            String exportFileName = "krt-logs-" + new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss").format(new Date()) + ".zip";
            File exportFile = new File(exportDir, exportFileName);

            // 这里可以实现日志文件的压缩导出逻辑
            // 简化版：直接返回今天的日志文件
            String todayLogFileName = "krt-" + new SimpleDateFormat("yyyy-MM-dd").format(new Date()) + ".log";
            File todayLogFile = new File("logs/krt", todayLogFileName);
            if (todayLogFile.exists()) {
                return todayLogFile;
            }

            return null;
        } catch (Exception e) {
            error("导出日志文件失败: " + e.getMessage());
            return null;
        }
    }
}