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
        // 报站方块相关翻译
        zhCnTranslations.put("announcement.arriving", "乘客们，列车即将进站，本次列车终点站%s站，请乘客们按照地面指示标志排队候车，列车到站时，请先下后上，注意站台间隙");
        zhCnTranslations.put("announcement.station", "乘客们，本次列车终点站%s站，下一站%s站，上车的乘客请往里走，请勿在车厢内乞讨卖艺散发小广告");
        zhCnTranslations.put("announcement.arrived", "乘客们，%s站，到了，请带好你的随身物品，从列车前进方向的%s方向下车，开门请当心");
        zhCnTranslations.put("announcement.mode.auto", "自动模式");
        zhCnTranslations.put("announcement.mode.manual", "手动模式");
        zhCnTranslations.put("announcement.mode.advanced", "高级模式");
        zhCnTranslations.put("announcement.mode.unknown", "未知模式");
        zhCnTranslations.put("announcement.mode_switched", "已切换连接模式至: %s");
        zhCnTranslations.put("announcement.no_permission", "你没有权限操作此设备");
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
        zhCnTranslations.put("krt.map.title", "小地图");
        zhCnTranslations.put("krt.map.zoom_in", "放大");
        zhCnTranslations.put("krt.map.zoom_out", "缩小");
        zhCnTranslations.put("krt.gui.line_map", "线路地图");
        zhCnTranslations.put("krt.gui.dispatch_center", "调度中心");
        zhCnTranslations.put("krt.map.export_image", "导出为图片");
        zhCnTranslations.put("krt.map.download_file", "下载文件");
        zhCnTranslations.put("krt.map.select_download_location", "选择下载位置");
        zhCnTranslations.put("krt.map.download_default_location", "默认位置（模组文件夹）");
        zhCnTranslations.put("krt.gui.confirm", "确认");
        zhCnTranslations.put("krt.gui.cancel", "取消");
        zhCnTranslations.put("krt.config.title", "昆明轨道交通模组配置");
        zhCnTranslations.put("krt.config.max_speed", "最大列车速度: ");
        zhCnTranslations.put("krt.config.enable_ato", "启用列车自动驾驶 (ATO)");
        zhCnTranslations.put("krt.config.show_hud", "显示游戏内HUD信息");
        zhCnTranslations.put("krt.config.save", "保存设置");
        zhCnTranslations.put("krt.config.reset", "重置");
        zhCnTranslations.put("krt.config.saving", "保存设置:");
        zhCnTranslations.put("krt.config.saved", "设置已保存");
        zhCnTranslations.put("krt.config.description", "调整昆明轨道交通模组的各项设置");
        zhCnTranslations.put("narration.krt.config.usage", "使用鼠标选择设置项，按Tab键在设置项之间切换，按Enter键确认选择，按Esc键返回");
        zhCnTranslations.put("narration.krt.config.hint", "点击保存按钮以应用更改");
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
        zhTwTranslations.put("krt.message.train_arriving", "乘客們，列車即將進站，本次列車終點站%s站，请乘客們按照地面指示標誌排隊候車，列車到站時，请先下後上，注意站台間隙");
        // 報站方塊相關翻譯
        zhTwTranslations.put("announcement.arriving", "乘客們，列車即將進站，本次列車終點站%s站，請乘客們按照地面指示標誌排隊候車，列車到站時，請先下後上，注意站台間隙");
        zhTwTranslations.put("announcement.station", "乘客們，本次列車終點站%s站，下一站%s站，上車的乘客請往裡走，請勿在車廂內乞討賣藝散發小廣告");
        zhTwTranslations.put("announcement.arrived", "乘客們，%s站，到了，請帶好你的隨身物品，從列車前進方向的%s方向下車，開門請當心");
        zhTwTranslations.put("announcement.mode.auto", "自動模式");
        zhTwTranslations.put("announcement.mode.manual", "手動模式");
        zhTwTranslations.put("announcement.mode.advanced", "高級模式");
        zhTwTranslations.put("announcement.mode.unknown", "未知模式");
        zhTwTranslations.put("announcement.mode_switched", "已切換連接模式至: %s");
        zhTwTranslations.put("announcement.no_permission", "你沒有權限操作此設備");
        zhTwTranslations.put("krt.message.train_announcement", "乘客們，本次列車終點站%s站，下一站%s站，上車的乘客請往裡走，請勿在車廂內乞討賣藝散發小廣告");
        zhTwTranslations.put("krt.message.train_arrived", "乘客們，%s站，到了，請帶好你的隨身物品，從列車前進方向的%s方向下車，開門請當心");
        zhTwTranslations.put("krt.map.title", "小地圖");
        zhTwTranslations.put("krt.map.zoom_in", "放大");
        zhTwTranslations.put("krt.map.zoom_out", "縮小");
        zhTwTranslations.put("krt.gui.line_map", "路線地圖");
        zhTwTranslations.put("krt.gui.dispatch_center", "調度中心");
        zhTwTranslations.put("krt.map.export_image", "匯出為圖片");
        zhTwTranslations.put("krt.map.download_file", "下載檔案");
        zhTwTranslations.put("krt.map.select_download_location", "選擇下載位置");
        zhTwTranslations.put("krt.map.download_default_location", "預設位置（模組資料夾）");
        zhTwTranslations.put("krt.gui.confirm", "確認");
        zhTwTranslations.put("krt.gui.cancel", "取消");
        zhTwTranslations.put("krt.config.title", "昆明軌道交通模組設定");
        zhTwTranslations.put("krt.config.max_speed", "最大列車速度: ");
        zhTwTranslations.put("krt.config.enable_ato", "啟用列車自動駕駛 (ATO)");
        zhTwTranslations.put("krt.config.show_hud", "顯示遊戲內HUD資訊");
        zhTwTranslations.put("krt.config.save", "儲存設定");
        zhTwTranslations.put("krt.config.reset", "重設");
        zhTwTranslations.put("krt.config.saving", "儲存設定:");
        zhTwTranslations.put("krt.config.saved", "設定已儲存");
        zhTwTranslations.put("krt.config.description", "調整昆明軌道交通模組的各項設定");
        zhTwTranslations.put("narration.krt.config.usage", "使用滑鼠選擇設定項，按Tab鍵在設定項之間切換，按Enter鍵確認選擇，按Esc鍵返回");
        zhTwTranslations.put("narration.krt.config.hint", "點擊儲存按鈕以應用變更");
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
        // Announcement block related translations
        enUsTranslations.put("announcement.arriving", "Passengers, the train is arriving. This train terminates at %s Station. Please line up according to the ground indicators. When the train arrives, please let passengers exit first before boarding. Watch the gap.");
        enUsTranslations.put("announcement.station", "Passengers, this train terminates at %s Station. Next stop: %s Station. Please move inside. Begging, performing, and distributing flyers in the carriage are prohibited.");
        enUsTranslations.put("announcement.arrived", "Passengers, we have arrived at %s Station. Please take all your belongings and exit the train in the %s direction. Mind the door.");
        enUsTranslations.put("announcement.mode.auto", "Auto Mode");
        enUsTranslations.put("announcement.mode.manual", "Manual Mode");
        enUsTranslations.put("announcement.mode.advanced", "Advanced Mode");
        enUsTranslations.put("announcement.mode.unknown", "Unknown Mode");
        enUsTranslations.put("announcement.mode_switched", "Connection mode switched to: %s");
        enUsTranslations.put("announcement.no_permission", "You don't have permission to operate this device");
        enUsTranslations.put("krt.message.train_announcement", "Passengers, this train terminates at %s Station. Next stop: %s Station. Please move inside. Begging, performing, and distributing flyers in the carriage are prohibited.");
        enUsTranslations.put("krt.message.train_arrived", "Passengers, we have arrived at %s Station. Please take all your belongings and exit the train in the %s direction. Mind the door.");
        enUsTranslations.put("krt.map.title", "Mini Map");
        enUsTranslations.put("krt.map.zoom_in", "Zoom In");
        enUsTranslations.put("krt.map.zoom_out", "Zoom Out");
        enUsTranslations.put("krt.gui.line_map", "Line Map");
        enUsTranslations.put("krt.gui.dispatch_center", "Dispatch Center");
        enUsTranslations.put("krt.map.export_image", "Export as Image");
        enUsTranslations.put("krt.map.download_file", "Download File");
        enUsTranslations.put("krt.map.select_download_location", "Select Download Location");
        enUsTranslations.put("krt.map.download_default_location", "Default Location (Mod Folder)");
        enUsTranslations.put("krt.gui.confirm", "Confirm");
        enUsTranslations.put("krt.gui.cancel", "Cancel");
        enUsTranslations.put("krt.config.title", "KRT Kunming Rail Transit Mod Configuration");
        enUsTranslations.put("krt.config.max_speed", "Maximum Train Speed: ");
        enUsTranslations.put("krt.config.enable_ato", "Enable Train Auto-Driving (ATO)");
        enUsTranslations.put("krt.config.show_hud", "Show In-Game HUD Information");
        enUsTranslations.put("krt.config.save", "Save Settings");
        enUsTranslations.put("krt.config.reset", "Reset");
        enUsTranslations.put("krt.config.saving", "Saving Settings:");
        enUsTranslations.put("krt.config.saved", "Settings Saved");
        enUsTranslations.put("krt.config.description", "Adjust various settings of the KRT Kunming Rail Transit Mod");
        enUsTranslations.put("narration.krt.config.usage", "Use mouse to select settings, press Tab to switch between settings, press Enter to confirm selection, press Esc to return");
        enUsTranslations.put("narration.krt.config.hint", "Click Save button to apply changes");
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
        // アナウンスブロック関連翻訳
        jaJpTranslations.put("announcement.arriving", "乗客の皆様、電車が到着します。この電車の終点は%s駅です。地上の表示に従って列を作ってお待ちください。電車が到着したら、先に降りる乗客をお待ちください。隙間にご注意ください。");
        jaJpTranslations.put("announcement.station", "乗客の皆様、この電車の終点は%s駅です。次の駅は%s駅です。中にお入りください。車内での物乞い、芸能人、チラシ配布は禁止されています。");
        jaJpTranslations.put("announcement.arrived", "乗客の皆様、%s駅に到着しました。お荷物をお忘れなく。電車の進行方向%s側から降りてください。ドアにご注意ください。");
        jaJpTranslations.put("announcement.mode.auto", "自動モード");
        jaJpTranslations.put("announcement.mode.manual", "手動モード");
        jaJpTranslations.put("announcement.mode.advanced", "高度なモード");
        jaJpTranslations.put("announcement.mode.unknown", "不明なモード");
        jaJpTranslations.put("announcement.mode_switched", "接続モードを%sに切り替えました");
        jaJpTranslations.put("announcement.no_permission", "この機器を操作する権限がありません");
        jaJpTranslations.put("krt.map.title", "ミニマップ");
        jaJpTranslations.put("krt.map.zoom_in", "拡大");
        jaJpTranslations.put("krt.map.zoom_out", "縮小");
        jaJpTranslations.put("krt.gui.line_map", "路線図");
        jaJpTranslations.put("krt.gui.dispatch_center", "運行管理センター");
        jaJpTranslations.put("krt.map.export_image", "画像としてエクスポート");
        jaJpTranslations.put("krt.map.download_file", "ファイルをダウンロード");
        jaJpTranslations.put("krt.map.select_download_location", "ダウンロード場所を選択");
        jaJpTranslations.put("krt.map.download_default_location", "デフォルト位置（モッドフォルダ）");
        jaJpTranslations.put("krt.gui.confirm", "確認");
        jaJpTranslations.put("krt.gui.cancel", "キャンセル");
        jaJpTranslations.put("krt.config.title", "KRT 昆明軌道交通モッド設定");
        jaJpTranslations.put("krt.config.max_speed", "最大列車速度: ");
        jaJpTranslations.put("krt.config.enable_ato", "列車自動運転 (ATO) を有効にする");
        jaJpTranslations.put("krt.config.show_hud", "ゲーム内HUD情報を表示");
        jaJpTranslations.put("krt.config.save", "設定を保存");
        jaJpTranslations.put("krt.config.reset", "リセット");
        jaJpTranslations.put("krt.config.saving", "設定を保存中:");
        jaJpTranslations.put("krt.config.saved", "設定が保存されました");
        jaJpTranslations.put("krt.config.description", "KRT 昆明軌道交通モッドの各種設定を調整");
        jaJpTranslations.put("narration.krt.config.usage", "マウスで設定を選択し、Tabキーで設定間を切り替え、Enterキーで選択を確定、Escキーで戻る");
        jaJpTranslations.put("narration.krt.config.hint", "保存ボタンをクリックして変更を適用");
        translations.put("ja_jp", jaJpTranslations);

        // 泰文
        Map<String, String> thThTranslations = new HashMap<>();
        thThTranslations.put("krt.mod.name", "KRT โมดทางรถไฟ高架กรุงขุมงง");
        thThTranslations.put("krt.mod.description", "โมดจำลองทางรถไฟ高架กรุงขุมงง รองรับการทำงานของรถไฟใต้ดิน ระบบสัญญาณ จัดการการเดินรถ และฟังก์ชั่นอื่นๆ");
        thThTranslations.put("krt.block.track", "ทางรถไฟ");
        thThTranslations.put("krt.block.switch_track", "ทางแยก");
        thThTranslations.put("krt.block.announcement", "บล็อกประกาศ");
        thThTranslations.put("krt.block.signal", "เครื่องสัญญาณ");
        thThTranslations.put("krt.block.platform_door", "ประตูแพลตฟอร์ม");
        thThTranslations.put("krt.block.atp_signal", "เครื่องสัญญาณ ATP");
        thThTranslations.put("krt.item.key", "กุญแจประตูแพลตฟอร์ม");
        thThTranslations.put("krt.train.driving_panel", "แผงควบคุมการขับรถไฟ");
        thThTranslations.put("krt.line.control_panel", "แผงควบคุมเส้นทาง");
        thThTranslations.put("krt.dispatcher.panel", "แผงควบคุมระบบการจัดตาราง");
        // แปลเกี่ยวกับบล็อกประกาศ
        thThTranslations.put("announcement.arriving", "ผู้โดยสาร ขบวนรถกำลังมาถึง ขบวนรถครั้งนี้สิ้นสุดที่สถานี%s กรุณาจัดตัวเป็นแถวตามเครื่องหมายบนพื้น เมื่อรถมาถึง กรุณาให้ผู้โดยสารออกก่อนลงรถ เที่ยวเตียงบริเวณช่องว่าง");
        thThTranslations.put("announcement.station", "ผู้โดยสาร ขบวนรถครั้งนี้สิ้นสุดที่สถานี%s สถานีถัดไปคือสถานี%s ผู้โดยสารที่ขึ้นรถโปรดเดินเข้าไปข้างใน กรุณาอย่าขอเงิน ขับขี่กล้องถ่ายรูป หรือแจกแฟลायเออร์ในรถ");
        thThTranslations.put("announcement.arrived", "ผู้โดยสาร ถึงสถานี%s แล้ว กรุณาเก็บของตัวเองไว้ และออกจากรถทางด้าน%s ของทิศทางการเคลื่อนที่ของรถ เที่ยวเตียงประตู");
        thThTranslations.put("announcement.mode.auto", "โหมดอัตโนมัติ");
        thThTranslations.put("announcement.mode.manual", "โหมดมือถือ");
        thThTranslations.put("announcement.mode.advanced", "โหมดขั้นสูง");
        thThTranslations.put("announcement.mode.unknown", "โหมดที่ไม่รู้จัก");
        thThTranslations.put("announcement.mode_switched", "เปลี่ยนโหมดการเชื่อมต่อเป็น: %s");
        thThTranslations.put("announcement.no_permission", "คุณไม่มีสิทธิ์操作此設備");
        thThTranslations.put("krt.map.title", "แผนที่เล็ก");
        thThTranslations.put("krt.map.zoom_in", "ขยาย");
        thThTranslations.put("krt.map.zoom_out", "ย่อ");
        thThTranslations.put("krt.gui.line_map", "แผนที่เส้นทาง");
        thThTranslations.put("krt.gui.dispatch_center", "ศูนย์ควบคุมการเดินรถ");
        thThTranslations.put("krt.map.export_image", "ส่งออกเป็นรูปภาพ");
        thThTranslations.put("krt.map.download_file", "ดาวน์โหลดไฟล์");
        thThTranslations.put("krt.map.select_download_location", "เลือกตำแหน่งดาวน์โหลด");
        thThTranslations.put("krt.map.download_default_location", "ตำแหน่งเริ่มต้น (โฟลเดอร์โมด)");
        thThTranslations.put("krt.gui.confirm", "ยืนยัน");
        thThTranslations.put("krt.gui.cancel", "ยกเลิก");
        thThTranslations.put("krt.config.title", "การตั้งค่าโมด KRT รถไฟฟ้าระบบเมืองกุนมิง");
        thThTranslations.put("krt.config.max_speed", "ความเร็วสูงสุดของรถไฟ: ");
        thThTranslations.put("krt.config.enable_ato", "เปิดใช้งานการขับขี่อัตโนมัติของรถไฟ (ATO)");
        thThTranslations.put("krt.config.show_hud", "แสดงข้อมูล HUD ในเกม");
        thThTranslations.put("krt.config.save", "บันทึกการตั้งค่า");
        thThTranslations.put("krt.config.reset", "รีเซ็ต");
        thThTranslations.put("krt.config.saving", "กำลังบันทึกการตั้งค่า:");
        thThTranslations.put("krt.config.saved", "การตั้งค่าถูกบันทึกแล้ว");
        thThTranslations.put("krt.config.description", "ปรับแต่งการตั้งค่าต่างๆ ของโมด KRT รถไฟฟ้าระบบเมืองกุนมิง");
        thThTranslations.put("narration.krt.config.usage", "ใช้เมาส์เลือกการตั้งค่า กด Tab สลับระหว่างการตั้งค่า  กด Enter ยืนยันการเลือก  กด Esc กลับ");
        thThTranslations.put("narration.krt.config.hint", "คลิกปุ่มบันทึกเพื่อประยุกต์ใช้การเปลี่ยนแปลง");
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