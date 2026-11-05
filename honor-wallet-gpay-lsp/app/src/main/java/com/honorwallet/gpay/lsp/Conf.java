package com.honorwallet.gpay.lsp;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * 常量 + 可选的「设备端配置覆盖」。
 *
 * <p>为什么要配置文件：本机没有荣耀手机可以实测，荣耀钱包里负责「双击电源键刷卡」的
 * Activity 类名无法从公开资料确认，NFC 默认支付组件的写法也可能随版本变化。
 * 与其把猜测烧进 APK 再让用户重装，不如让用户（或下一轮排查）改一个文本文件即可：
 *
 * <pre>
 * # /data/local/tmp/honor_wallet_gpay.conf   （也可以放 /sdcard/honor_wallet_gpay.conf）
 * activity=com.hihonor.wallet.xxx.QuickPayActivity     # 精确指定刷卡页
 * inject_all=1                                          # 调试：钱包里所有页面都注入
 * button_margin_bottom_dp=120
 * honor_nfc_component=com.hihonor.wallet/xxx.HceService
 * use_root=1
 * </pre>
 *
 * 读不到就用内置默认值，不影响正常使用。
 */
public final class Conf {

    public static final String MODULE_PKG = "com.honorwallet.gpay.lsp";
    public static final String AUTHORITY = MODULE_PKG + ".root";

    /** 荣耀钱包（Honor Wallet）包名 */
    public static final String DEFAULT_WALLET_PKG = "com.hihonor.wallet";
    /** Google Wallet（Google Pay）包名 */
    public static final String DEFAULT_GATEWAY_PKG = "com.google.android.apps.walletnfcrel";

    /** Settings.Secure 里「默认 NFC 支付应用」的键 */
    public static final String KEY_NFC_PAYMENT = "nfc_payment_default_component";
    /** Google Wallet 的 HCE 服务（NFC 默认支付组件） */
    public static final String GOOGLE_HCE =
            "com.google.android.gms/com.google.android.gms.tapandpay.hce.service.TpHceService";

    /** Google Wallet 快捷刷卡页 */
    public static final String GW_QUICKDRAW_ACTION = "com.google.android.apps.wallet.main.QUICKDRAW";
    public static final String GW_QUICKDRAW_ACTIVITY =
            "com.google.android.apps.wallet.pmode.PresentationModeActivity";
    /** NEW_TASK | MULTIPLE_TASK | EXCLUDE_FROM_RECENTS：独立 task 且不进最近任务 */
    public static final int GW_QUICKDRAW_FLAGS = 0x18800000;

    /** 荣耀钱包的 HCE 服务（实机 11.0.2.321 取到，作为回切兜底值）。 */
    public static final String DEFAULT_HONOR_HCE =
            "com.hihonor.wallet/com.hihonor.wallet.swipecard.NFCOffHostApduService";

    /** 日志（第一个能写的路径生效；都写不了就只进 logcat） */
    public static final String[] LOG_PATHS = {
            "/sdcard/Documents/honor_wallet_gpay.log",
            "/sdcard/honor_wallet_gpay.log",
            "/data/local/tmp/honor_wallet_gpay.log",
    };

    private static final String[] CONF_PATHS = {
            "/data/local/tmp/honor_wallet_gpay.conf",
            "/sdcard/honor_wallet_gpay.conf",
            "/storage/emulated/0/Documents/honor_wallet_gpay.conf",
    };

    private static volatile Map<String, String> sMap;
    private static volatile String sSource = "";

    private Conf() {
    }

    private static Map<String, String> map() {
        Map<String, String> m = sMap;
        if (m != null) {
            return m;
        }
        synchronized (Conf.class) {
            if (sMap != null) {
                return sMap;
            }
            m = new HashMap<>();
            for (String path : CONF_PATHS) {
                try {
                    File f = new File(path);
                    if (!f.canRead()) {
                        continue;
                    }
                    try (BufferedReader r = new BufferedReader(new InputStreamReader(
                            new FileInputStream(f), StandardCharsets.UTF_8))) {
                        String line;
                        while ((line = r.readLine()) != null) {
                            line = line.trim();
                            if (line.isEmpty() || line.startsWith("#")) {
                                continue;
                            }
                            int eq = line.indexOf('=');
                            if (eq <= 0) {
                                continue;
                            }
                            m.put(line.substring(0, eq).trim().toLowerCase(),
                                    line.substring(eq + 1).trim());
                        }
                    }
                    sSource = path;
                    break;
                } catch (Throwable ignored) {
                }
            }
            sMap = m;
            return m;
        }
    }

    public static String get(String key, String def) {
        String v = map().get(key.toLowerCase());
        return (v == null || v.isEmpty()) ? def : v;
    }

    public static int getInt(String key, int def) {
        try {
            return Integer.parseInt(get(key, String.valueOf(def)).trim());
        } catch (Throwable t) {
            return def;
        }
    }

    public static boolean getBool(String key, boolean def) {
        String v = get(key, def ? "1" : "0");
        return "1".equals(v) || "true".equalsIgnoreCase(v) || "yes".equalsIgnoreCase(v);
    }

    /** 配置文件实际生效的路径；空串表示没有读到任何配置文件。 */
    public static String sourcePath() {
        map();
        return sSource;
    }

    public static String walletPackage() {
        return get("wallet_package", DEFAULT_WALLET_PKG);
    }

    public static String gatewayPackage() {
        return get("gateway_package", DEFAULT_GATEWAY_PKG);
    }

    /** 精确指定的刷卡页类名；空串表示用启发式识别。 */
    public static String pinnedActivity() {
        return get("activity", "");
    }

    public static boolean injectAll() {
        return getBool("inject_all", false);
    }

    public static boolean useRoot() {
        return getBool("use_root", true);
    }

    /** 「荣耀自己的 NFC 支付组件」的兜底值（读不到当前值时用）。 */
    public static String honorComponentFallback() {
        return get("honor_nfc_component", DEFAULT_HONOR_HCE);
    }
}
