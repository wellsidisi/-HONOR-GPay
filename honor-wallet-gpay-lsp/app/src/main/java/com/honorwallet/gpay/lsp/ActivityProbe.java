package com.honorwallet.gpay.lsp;

import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * 「哪一页才是双击电源键出来的刷卡页」。
 *
 * <p>已经在荣耀钱包 11.0.2.321 上拿到真实清单，因此默认用**白名单**固定到滑动刷卡那一族
 * Activity（见 {@link #DEFAULT_PINNED}），不再靠猜；配置里写 {@code activity=a,b} 可以整体覆盖，
 * 写 {@code inject_all=1} 或 {@code activity_keywords=…} 则回到启发式打分。
 */
public final class ActivityProbe {

    /**
     * 实机（荣耀钱包 11.0.2.321）长按按钮取到的真实类名：
     * {@code com.hihonor.wallet.cardmanager.activity.SwipeCardActivity}。
     * 第二个是同族的「快速刷卡」页，从钱包内部入口进来时可能用它。
     */
    public static final String[] DEFAULT_PINNED = {
            "com.hihonor.wallet.cardmanager.activity.SwipeCardActivity",
            "com.hihonor.wallet.cardmanager.activity.QuickSwipeCardActivity",
    };


    /** 类名里出现这些词就加分，越大越像刷卡页。 */
    private static final String[][] WEIGHTS = {
            {"quickpay", "120"}, {"quick_pay", "120"}, {"doubleclick", "110"},
            {"double_click", "110"}, {"swipelist", "95"}, {"swipe", "90"},
            {"quick", "60"}, {"nfc", "40"}, {"tap", "30"}, {"card", "20"},
            {"pay", "15"},
    };

    /** 出现这些词反而扣分（钱包首页、设置页、H5 页不注入）。 */
    private static final String[] NEGATIVE = {
            "main", "home", "splash", "guide", "setting", "about", "login",
            "webview", "web", "h5", "debug", "test", "permission", "protocol",
    };

    public static final class Entry {
        public final String name;
        public final int score;

        Entry(String name, int score) {
            this.name = name;
            this.score = score;
        }

        public boolean autoInject() {
            return ActivityProbe.isPayScreen(name);
        }
    }

    private ActivityProbe() {
    }

    public static int score(String className) {
        String s = className.toLowerCase(Locale.ROOT);
        int sc = 0;
        for (String[] w : WEIGHTS) {
            if (s.contains(w[0])) {
                sc += Integer.parseInt(w[1]);
            }
        }
        for (String n : NEGATIVE) {
            if (s.contains(n)) {
                sc -= 40;
            }
        }
        // 配置文件里追加的关键词（逗号分隔）
        String extra = Conf.get("activity_keywords", "");
        for (String kw : extra.split(",")) {
            kw = kw.trim().toLowerCase(Locale.ROOT);
            if (!kw.isEmpty() && s.contains(kw)) {
                sc += 100;
            }
        }
        return sc;
    }

    /** 当前要注入的类名集合：配置的 activity= 优先，否则用实机确认过的白名单。 */
    public static List<String> pinnedList() {
        String custom = Conf.get("activity", "");
        if (!custom.isEmpty()) {
            List<String> out = new ArrayList<>();
            for (String s : custom.split(",")) {
                String t = s.trim();
                if (!t.isEmpty()) {
                    out.add(t);
                }
            }
            if (!out.isEmpty()) {
                return out;
            }
        }
        List<String> list = new ArrayList<>();
        Collections.addAll(list, DEFAULT_PINNED);
        return list;
    }

    /** 当前这个 Activity 要不要注入按钮。 */
    public static boolean isPayScreen(String className) {
        if (className == null) {
            return false;
        }
        for (String n : pinnedList()) {
            if (n.equals(className)) {
                return true;
            }
        }
        if (Conf.injectAll()) {
            return true;
        }
        // 只有用户主动写了关键词才回退到打分，避免误伤向导页/设置页
        return !Conf.get("activity_keywords", "").isEmpty() && score(className) >= 60;
    }

    /** 列出钱包里所有 Activity 并打分（模块信息页 / 日志用）。 */
    public static List<Entry> list(Context ctx) {
        List<Entry> out = new ArrayList<>();
        String pkg = Conf.walletPackage();
        try {
            PackageManager pm = ctx.getPackageManager();
            PackageInfo pi = pm.getPackageInfo(pkg, PackageManager.GET_ACTIVITIES);
            ActivityInfo[] acts = pi.activities;
            if (acts == null) {
                return out;
            }
            for (ActivityInfo a : acts) {
                if (a.name != null) {
                    out.add(new Entry(a.name, score(a.name)));
                }
            }
        } catch (Throwable t) {
            DevLog.e("list activities failed: " + t.getMessage());
        }
        Collections.sort(out, new Comparator<Entry>() {
            @Override
            public int compare(Entry x, Entry y) {
                return Integer.compare(y.score, x.score);
            }
        });
        return out;
    }

    public static String appVersion(Context ctx, String pkg) {
        try {
            PackageInfo pi = ctx.getPackageManager().getPackageInfo(pkg, 0);
            String v = pi.versionName == null ? "?" : pi.versionName;
            return v + " (" + pi.getLongVersionCode() + ")";
        } catch (Throwable t) {
            return null;
        }
    }
}
