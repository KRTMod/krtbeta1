package com.krt.mod.system;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.resource.language.I18n;
import com.krt.mod.KRTMod;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

public class LanguageSystem {
    private static final Map<String, Map<String, String>> translations = new HashMap<>();
    private static String currentLanguage = "zh_cn"; // 默认简体中文

    // 支持的语言列表
    public static final String[] SUPPORTED_LANGUAGES = {
            "zh_cn", // 简体中文
            "zh_tw", // 繁体中文
            "en_us", // 英文
            "ja_jp", // 日文
            "th_th"  // 泰文
    };

    // 初始化语言系统
    public static void init() {
        // 加载默认的语言文件
        loadDefaultTranslations();
        // 尝试加载用户自定义的语言文件
        loadUserTranslations();
    }

    // 加载默认的语言文件
    private static void loadDefaultTranslations() {
        // 简体中文
        Map<String, String> zhCnTranslations = new HashMap<>();
        zhCnTranslations.put("krt.mod.name", "KRT 昆明轨道交通模组");
        zhCnTranslations.put("krt.mod.description", "昆明轨道交通仿真模组，支持地铁列车运行、信号系统、调度管理等功能");
        zhCnTranslations.put("krt.block.track", "轨道");
        zhCnTranslations.put("krt.block.switch_track", "道岔轨道");
        zhCnTranslations.put("krt.block.announcement", "报站方块");
        zhCnTranslations.put("krt.block.signal", "信号机");
        zhCnTranslations.put("krt.block.platform_door", "屏蔽门");
        zhCnTranslations.put("krt.block.atp_signal", "ATP信号机");
        zhCnTranslations.put("krt.item.key", "屏蔽门钥匙");
        zhCnTranslations.put("krt.train.driving_panel", "列车驾驶控制面板");
        zhCnTranslations.put("krt.line.control_panel", "线路控制面板");
        zhCnTranslations.put("krt.dispatcher.panel", "调度系统控制面板");
        zhCnTranslations.put("krt.message.train_arriving", "乘客们，列车即将进站，本次列车终点站%s站，请乘客们按照地面指示标志排队候车，列车到站时，请先下后上，注意站台间隙");
        zhCnTranslations.put("krt.message.train_announcement", "乘客们，本次列车终点站%s站，下一站%s站，上车的乘客请往里走，请勿在车厢内乞讨卖艺散发小广告");
        zhCnTranslations.put("krt.message.train_arrived", "乘客们，%s站，到了，请带好你的随身物品，从列车前进方向的%s方向下车，开门请当心");
        zhCnTranslations.put("krt.control.forward", "前进");
        zhCnTranslations.put("krt.control.backward", "后退");
        zhCnTranslations.put("krt.control.shift_gear", "切换档位");
        zhCnTranslations.put("krt.signal.green", "绿灯 - 运行正常");
        zhCnTranslations.put("krt.signal.yellow", "黄灯 - 注意减速");
        zhCnTranslations.put("krt.signal.red", "红灯 - 停车等待");
        zhCnTranslations.put("krt.door.open", "开门");
        zhCnTranslations.put("krt.door.close", "关门");
        zhCnTranslations.put("krt.door.manual_open", "手动开门");
        zhCnTranslations.put("krt.door.manual_close", "手动关门");
        zhCnTranslations.put("krt.train.health.good", "列车状态良好");
        zhCnTranslations.put("krt.train.health.poor", "列车状态不佳，需要检修");
        zhCnTranslations.put("krt.train.health.critical", "列车状态严重异常，禁止运营");
        translations.put("zh_cn", zhCnTranslations);

        // 繁体中文
        Map<String, String> zhTwTranslations = new HashMap<>();
        zhTwTranslations.put("krt.mod.name", "KRT 昆明軌道交通模組");
        zhTwTranslations.put("krt.mod.description", "昆明軌道交通仿真模組，支援地鐵列車運行、信號系統、調度管理等功能");
        zhTwTranslations.put("krt.block.track", "軌道");
        zhTwTranslations.put("krt.block.switch_track", "道岔軌道");
        zhTwTranslations.put("krt.block.announcement", "報站方塊");
        zhTwTranslations.put("krt.block.signal", "信號機");
        zhTwTranslations.put("krt.block.platform_door", "屏蔽門");
        zhTwTranslations.put("krt.block.atp_signal", "ATP信號機");
        zhTwTranslations.put("krt.item.key", "屏蔽門鑰匙");
        zhTwTranslations.put("krt.train.driving_panel", "列車駕駛控制面板");
        zhTwTranslations.put("krt.line.control_panel", "線路控制面板");
        zhTwTranslations.put("krt.dispatcher.panel", "調度系統控制面板");
        zhTwTranslations.put("krt.message.train_arriving", "乘客們，列車即將進站，本次列車終點站%s站，請乘客們按照地面指示標誌排隊候車，列車到站時，請先下後上，注意站台間隙");
        zhTwTranslations.put("krt.message.train_announcement", "乘客們，本次列車終點站%s站，下一站%s站，上車的乘客請往裡走，請勿在車廂內乞討賣藝散發小廣告");
        zhTwTranslations.put("krt.message.train_arrived", "乘客們，%s站，到了，請帶好你的隨身物品，從列車前進方向的%s方向下車，開門請當心");
        translations.put("zh_tw", zhTwTranslations);

        // 英文
        Map<String, String> enUsTranslations = new HashMap<>();
        enUsTranslations.put("krt.mod.name", "KRT Kunming Rail Transit Mod");
        enUsTranslations.put("krt.mod.description", "Kunming Rail Transit simulation mod, supporting subway train operation, signal system, dispatch management and other functions");
        enUsTranslations.put("krt.block.track", "Track");
        enUsTranslations.put("krt.block.switch_track", "Switch Track");
        enUsTranslations.put("krt.block.announcement", "Announcement Block");
        enUsTranslations.put("krt.block.signal", "Signal Machine");
        enUsTranslations.put("krt.block.platform_door", "Platform Door");
        enUsTranslations.put("krt.block.atp_signal", "ATP Signal");
        enUsTranslations.put("krt.item.key", "Platform Door Key");
        enUsTranslations.put("krt.train.driving_panel", "Train Driving Panel");
        enUsTranslations.put("krt.line.control_panel", "Line Control Panel");
        enUsTranslations.put("krt.dispatcher.panel", "Dispatch Control Panel");
        enUsTranslations.put("krt.message.train_arriving", "Passengers, the train is arriving. This train terminates at %s Station. Please line up according to the ground indicators. When the train arrives, please let passengers exit first before boarding. Watch the gap.");
        enUsTranslations.put("krt.message.train_announcement", "Passengers, this train terminates at %s Station. Next stop: %s Station. Please move inside. Begging, performing, and distributing flyers in the carriage are prohibited.");
        enUsTranslations.put("krt.message.train_arrived", "Passengers, we have arrived at %s Station. Please take all your belongings and exit the train in the %s direction. Mind the door.");
        translations.put("en_us", enUsTranslations);

        // 日文
        Map<String, String> jaJpTranslations = new HashMap<>();
        jaJpTranslations.put("krt.mod.name", "KRT 昆明軌道交通モッド");
        jaJpTranslations.put("krt.mod.description", "昆明軌道交通シミュレーションモッドで、地下鉄の運行、信号システム、ディスパッチ管理などの機能をサポートします");
        jaJpTranslations.put("krt.block.track", "線路");
        jaJpTranslations.put("krt.block.switch_track", "分岐器");
        jaJpTranslations.put("krt.block.announcement", "アナウンスブロック");
        jaJpTranslations.put("krt.block.signal", "信号機");
        jaJpTranslations.put("krt.block.platform_door", "ホームドア");
        jaJpTranslations.put("krt.block.atp_signal", "ATP信号機");
        jaJpTranslations.put("krt.item.key", "ホームドアキー");
        jaJpTranslations.put("krt.train.driving_panel", "運転台");
        jaJpTranslations.put("krt.line.control_panel", "路線制御パネル");
        jaJpTranslations.put("krt.dispatcher.panel", "ディスパッチャーコントロールパネル");
        translations.put("ja_jp", jaJpTranslations);

        // 泰文
        Map<String, String> thThTranslations = new HashMap<>();
        thThTranslations.put("krt.mod.name", "โมด KRT รถไฟฟ้าระบบเมืองกุนมิง");
        thThTranslations.put("krt.mod.description", "โมดจำลองรถไฟฟ้าระบบเมืองกุนมิง รองรับการทำงานของรถไฟฟ้า สายสัญญาณ ระบบควบคุมการขับเคลื่อน และฟังก์ชั่นอื่นๆ");
        thThTranslations.put("krt.block.track", "ราง");
        thThTranslations.put("krt.block.switch_track", "รางสลับเส้นทาง");
        thThTranslations.put("krt.block.announcement", "บล็อกประกาศ");
        thThTranslations.put("krt.block.signal", "เครื่องสัญญาณ");
        thThTranslations.put("krt.block.platform_door", "ประตูแพลตฟอร์ม");
        thThTranslations.put("krt.block.atp_signal", "เครื่องสัญญาณ ATP");
        thThTranslations.put("krt.item.key", "กุญแจประตูแพลตฟอร์ม");
        thThTranslations.put("krt.train.driving_panel", "แผงควบคุมการขับรถไฟ");
        thThTranslations.put("krt.line.control_panel", "แผงควบคุมเส้นทาง");
        thThTranslations.put("krt.dispatcher.panel", "แผงควบคุมระบบการจัดตาราง");
        translations.put("th_th", thThTranslations);
    }

    // 加载用户自定义的语言文件
    private static void loadUserTranslations() {
        try {
            File langDir = new File("config/krt/lang");
            if (!langDir.exists()) {
                langDir.mkdirs();
            }

            for (String langCode : SUPPORTED_LANGUAGES) {
                File langFile = new File(langDir, langCode + ".json");
                if (langFile.exists()) {
                    try (Reader reader = new InputStreamReader(new FileInputStream(langFile), StandardCharsets.UTF_8)) {
                        JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                        Map<String, String> userTranslations = translations.getOrDefault(langCode, new HashMap<>());

                        for (Map.Entry<String, com.google.gson.JsonElement> entry : json.entrySet()) {
                            userTranslations.put(entry.getKey(), entry.getValue().getAsString());
                        }

                        translations.put(langCode, userTranslations);
                        KRTMod.LOGGER.info("加载用户自定义语言文件: {}", langFile.getAbsolutePath());
                    }
                }
            }
        } catch (Exception e) {
            KRTMod.LOGGER.error("加载用户自定义语言文件失败", e);
        }
    }

    // 设置当前语言
    public static void setCurrentLanguage(String languageCode) {
        if (translations.containsKey(languageCode)) {
            currentLanguage = languageCode;
            KRTMod.LOGGER.info("语言已切换至: {}", languageCode);
        } else {
            KRTMod.LOGGER.warn("不支持的语言代码: {}", languageCode);
        }
    }

    // 获取当前语言
    public static String getCurrentLanguage() {
        return currentLanguage;
    }

    // 翻译文本
    public static String translate(String key, Object... args) {
        Map<String, String> langTranslations = translations.get(currentLanguage);
        if (langTranslations != null && langTranslations.containsKey(key)) {
            String translation = langTranslations.get(key);
            return String.format(translation, args);
        }

        // 如果当前语言没有翻译，尝试使用英文
        if (!currentLanguage.equals("en_us")) {
            langTranslations = translations.get("en_us");
            if (langTranslations != null && langTranslations.containsKey(key)) {
                String translation = langTranslations.get(key);
                return String.format(translation, args);
            }
        }

        // 如果都没有翻译，返回原始键
        return key;
    }

    // 获取支持的语言列表
    public static String[] getSupportedLanguages() {
        return SUPPORTED_LANGUAGES;
    }

    // 获取语言显示名称
    public static String getLanguageDisplayName(String languageCode) {
        switch (languageCode) {
            case "zh_cn":
                return "简体中文";
            case "zh_tw":
                return "繁體中文";
            case "en_us":
                return "English";
            case "ja_jp":
                return "日本語";
            case "th_th":
                return "ไทย";
            default:
                return languageCode;
        }
    }
}