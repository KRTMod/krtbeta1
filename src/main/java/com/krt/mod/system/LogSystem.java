package com.krt.mod.system;

import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.logging.*;
import com.krt.mod.KRTMod;

public class LogSystem {
    private static final Logger LOGGER = Logger.getLogger("KRT Mod");
    private static FileHandler fileHandler;
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    private static boolean initialized = false;
    private static final Queue<LogEntry> logQueue = new ConcurrentLinkedQueue<>();
    private static final int MAX_LOG_ENTRIES = 10000; // 内存中保留的最大日志条目数
    private static Thread logWriterThread = null;
    private static final Object LOCK = new Object();

    // 日志级别枚举
    public enum LogLevel {
        INFO,
        WARNING,
        ERROR,
        DEBUG
    }

    // 日志条目类
    public static class LogEntry {
        private final String message;
        private final LogLevel level;
        private final long timestamp;
        private final String source;

        public LogEntry(String message, LogLevel level, String source) {
            this.message = message;
            this.level = level;
            this.timestamp = System.currentTimeMillis();
            this.source = source;
        }

        public String getMessage() { return message; }
        public LogLevel getLevel() { return level; }
        public long getTimestamp() { return timestamp; }
        public String getSource() { return source; }
        
        @Override
        public String toString() {
            return "[" + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").format(new Date(timestamp)) + "] [" + level + "] [" + source + "] " + message;
        }
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

            // 启动日志写入线程
            startLogWriterThread();

            initialized = true;
            log(LogLevel.INFO, "日志系统初始化成功");
        } catch (IOException e) {
            KRTMod.LOGGER.error("初始化日志系统失败", e);
        }
    }

    // 启动日志写入线程
    private static void startLogWriterThread() {
        if (logWriterThread != null && logWriterThread.isAlive()) {
            return;
        }

        logWriterThread = new Thread(() -> {
            while (initialized) {
                try {
                    // 处理队列中的日志条目
                    processLogQueue();
                    Thread.sleep(100); // 每100ms检查一次队列
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }, "KRTLogThread");

        logWriterThread.setDaemon(true); // 设置为守护线程，程序退出时自动结束
        logWriterThread.start();
    }

    // 处理日志队列
    private static void processLogQueue() {
        while (!logQueue.isEmpty()) {
            LogEntry entry = logQueue.poll();
            if (entry != null) {
                // 写入文件
                switch (entry.getLevel()) {
                    case INFO:
                        LOGGER.info(entry.getMessage());
                        break;
                    case WARNING:
                        LOGGER.warning(entry.getMessage());
                        break;
                    case ERROR:
                        LOGGER.severe(entry.getMessage());
                        break;
                    case DEBUG:
                        LOGGER.fine(entry.getMessage());
                        break;
                }
            }
        }
    }

    // 记录日志
    public static void log(LogLevel level, String message) {
        log(level, message, "SYSTEM");
    }

    // 记录日志（带来源）
    public static void log(LogLevel level, String message, String source) {
        if (!initialized) {
            init();
        }

        // 创建日志条目
        LogEntry entry = new LogEntry(message, level, source);
        
        // 添加到日志队列
        logQueue.offer(entry);
        
        // 控制队列大小
        while (logQueue.size() > MAX_LOG_ENTRIES) {
            logQueue.poll();
        }
        
        // 输出到控制台
        System.out.println(entry.toString());
    }

    // 记录信息日志
    public static void info(String message) {
        log(LogLevel.INFO, message, "SYSTEM");
    }

    // 记录警告日志
    public static void warning(String message) {
        log(LogLevel.WARNING, message, "SYSTEM");
    }

    // 记录错误日志
    public static void error(String message) {
        log(LogLevel.ERROR, message, "SYSTEM");
    }

    // 记录调试日志
    public static void debug(String message) {
        log(LogLevel.DEBUG, message, "SYSTEM");
    }

    // 记录系统日志（与info类似，但用于系统级消息）
    public static void systemLog(String message) {
        log(LogLevel.INFO, message, "SYSTEM");
    }

    // 记录列车相关日志
    public static void trainLog(String trainId, String message) {
        log(LogLevel.INFO, message, "TRAIN-" + trainId);
    }

    // 记录线路相关日志
    public static void lineLog(String lineId, String message) {
        log(LogLevel.INFO, message, "LINE-" + lineId);
    }

    // 记录调度相关日志
    public static void dispatchLog(String message) {
        log(LogLevel.INFO, message, "DISPATCH");
    }

    // 记录车厂相关日志
    public static void depotLog(String depotId, String message) {
        log(LogLevel.INFO, message, "DEPOT-" + depotId);
    }

    // ATC系统日志
    public static void atcLog(String message) {
        log(LogLevel.INFO, message, "ATC");
    }

    public static void atcWarning(String message) {
        log(LogLevel.WARNING, message, "ATC");
    }

    public static void atcError(String message) {
        log(LogLevel.ERROR, message, "ATC");
    }

    public static void atcDebug(String message) {
        log(LogLevel.DEBUG, message, "ATC");
    }

    // ATS系统日志
    public static void atsLog(String message) {
        log(LogLevel.INFO, message, "ATS");
    }

    public static void atsWarning(String message) {
        log(LogLevel.WARNING, message, "ATS");
    }

    public static void atsError(String message) {
        log(LogLevel.ERROR, message, "ATS");
    }

    public static void atsDebug(String message) {
        log(LogLevel.DEBUG, message, "ATS");
    }

    // ATP系统日志
    public static void atpLog(String message) {
        log(LogLevel.INFO, message, "ATP");
    }

    public static void atpWarning(String message) {
        log(LogLevel.WARNING, message, "ATP");
    }

    public static void atpError(String message) {
        log(LogLevel.ERROR, message, "ATP");
    }

    public static void atpDebug(String message) {
        log(LogLevel.DEBUG, message, "ATP");
    }

    // ATO系统日志
    public static void atoLog(String message) {
        log(LogLevel.INFO, message, "ATO");
    }

    public static void atoWarning(String message) {
        log(LogLevel.WARNING, message, "ATO");
    }

    public static void atoError(String message) {
        log(LogLevel.ERROR, message, "ATO");
    }

    public static void atoDebug(String message) {
        log(LogLevel.DEBUG, message, "ATO");
    }

    // 故障安全系统日志
    public static void faultLog(String message) {
        log(LogLevel.INFO, message, "FAULT");
    }

    public static void faultWarning(String message) {
        log(LogLevel.WARNING, message, "FAULT");
    }

    public static void faultError(String message) {
        log(LogLevel.ERROR, message, "FAULT");
    }

    public static void faultDebug(String message) {
        log(LogLevel.DEBUG, message, "FAULT");
    }

    // 轨道区段管理系统日志
    public static void trackLog(String message) {
        log(LogLevel.INFO, message, "TRACK");
    }

    public static void trackWarning(String message) {
        log(LogLevel.WARNING, message, "TRACK");
    }

    public static void trackError(String message) {
        log(LogLevel.ERROR, message, "TRACK");
    }

    public static void trackDebug(String message) {
        log(LogLevel.DEBUG, message, "TRACK");
    }

    // 自定义日志格式化器
    private static class LogFormatter extends java.util.logging.Formatter {
        @Override
        public String format(LogRecord record) {
            String level = record.getLevel().getName();
            String message = formatMessage(record);
            String timestamp = DATE_FORMAT.format(new Date(record.getMillis()));
            String threadName = Thread.currentThread().getName();
            String className = record.getSourceClassName();
            String methodName = record.getSourceMethodName();

            // 尝试从消息中提取来源信息（如果消息格式符合我们的日志格式）
            String source = "UNKNOWN";
            if (message.contains("] [")) {
                int firstBracketPos = message.indexOf("] [");
                if (firstBracketPos > 0 && message.charAt(0) == '[') {
                    source = message.substring(1, firstBracketPos);
                    // 移除消息开头的来源信息
                    message = message.substring(firstBracketPos + 3);
                }
            }

            return "[" + timestamp + "] [" + level + "] [" + source + "] [" + threadName + "] " + message + "\n";
        }
    }

    // 关闭日志系统
    public static synchronized void close() {
        if (initialized && fileHandler != null) {
            log(LogLevel.INFO, "日志系统关闭中...");
            
            // 处理队列中剩余的所有日志条目
            processLogQueue();
            
            fileHandler.close();
            initialized = false;
            
            // 中断日志写入线程
            if (logWriterThread != null && logWriterThread.isAlive()) {
                logWriterThread.interrupt();
            }
            
            log(LogLevel.INFO, "日志系统已关闭");
        }
    }
    
    // 获取最近的日志条目
    public static List<LogEntry> getRecentLogEntries(int count) {
        List<LogEntry> entries = new ArrayList<>(logQueue);
        int size = entries.size();
        int startIndex = Math.max(0, size - count);
        return entries.subList(startIndex, size);
    }
    
    // 按来源获取最近的日志条目
    public static List<LogEntry> getRecentLogEntriesBySource(String source, int count) {
        List<LogEntry> result = new ArrayList<>();
        for (LogEntry entry : logQueue) {
            if (entry.getSource().equals(source)) {
                result.add(entry);
            }
        }
        
        int size = result.size();
        int startIndex = Math.max(0, size - count);
        return result.subList(startIndex, size);
    }
    
    // 获取所有日志条目的数量
    public static int getLogEntryCount() {
        return logQueue.size();
    }

    // 获取日志文件路径
    public static String getLatestLogFile() {
        if (!initialized) {
            init();
        }

        // 直接返回当前日志文件的路径
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