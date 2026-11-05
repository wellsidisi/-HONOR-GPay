package com.honorwallet.gpay.lsp;

import android.content.ContentResolver;
import android.content.Context;
import android.nfc.NfcAdapter;
import android.provider.Settings;

import java.lang.reflect.Method;

/**
 * 切 NFC 默认支付组件。
 *
 * <p>实机（荣耀钱包 11.0.2.321）反馈：钱包进程读这个 key 直接抛 SecurityException，
 * 写更不可能。所以链路是「先试直写 → 不行走模块 uid 的 root 通道」，并且把读到的值
 * 缓存下来，避免每次进刷卡页都起一次 su。
 *
 * <p>回切目标（荣耀自己的组件）不再硬编码：第一次读到的非 Google 值即视为它。
 */
public final class NfcControl {

    /** 荣耀自己的默认支付组件（回切目标）。 */
    private static volatile String sHonor;
    /** 我们认知的当前值，用于避免重复 su。 */
    private static volatile String sCurrent;

    private NfcControl() {
    }

    public static String current(Context ctx) {
        String c = sCurrent;
        if (c != null) {
            return c;
        }
        c = readNow(ctx);
        if (c != null) {
            sCurrent = c;
        }
        return c;
    }

    /** 直接读；被拒就用 root 读（模块进程）。 */
    private static String readNow(Context ctx) {
        try {
            String v = Settings.Secure.getString(ctx.getContentResolver(),
                    Conf.KEY_NFC_PAYMENT);
            if (v != null && !v.isEmpty()) {
                return v;
            }
        } catch (Throwable t) {
            DevLog.i("direct nfc read denied: " + t.getClass().getSimpleName());
        }
        if (Conf.useRoot()) {
            String v = RootBridge.getNfc(ctx);
            if (v != null && !v.isEmpty()) {
                return v;
            }
        }
        return null;
    }

    public static boolean isGoogle(String component) {
        if (component == null) {
            return false;
        }
        String c = component.toLowerCase();
        return c.startsWith("com.google.android.gms/") || c.contains("tapandpay")
                || c.equalsIgnoreCase(Conf.GOOGLE_HCE);
    }

    public static String honorComponent(Context ctx) {
        String h = sHonor;
        if (h != null) {
            return h;
        }
        String cur = current(ctx);
        if (cur != null && !isGoogle(cur)) {
            sHonor = cur;
            DevLog.i("honor nfc component captured: " + cur);
            return cur;
        }
        String fallback = Conf.honorComponentFallback();
        return fallback.isEmpty() ? null : fallback;
    }

    public static boolean switchToGoogle(Context ctx) {
        return set(ctx, Conf.GOOGLE_HCE);
    }

    /** 刷卡页亮着时把默认支付应用切回荣耀钱包（已经是就不动，零 IPC）。 */
    public static boolean restoreHonor(Context ctx) {
        String honor = honorComponent(ctx);
        if (honor == null) {
            DevLog.i("honor component unknown, skip restore");
            return false;
        }
        String cur = current(ctx);
        if (honor.equals(cur)) {
            return true;
        }
        return set(ctx, honor);
    }

    public static boolean set(Context ctx, String component) {
        if (component == null || component.isEmpty()) {
            return false;
        }
        if (component.equals(sCurrent)) {
            return true;
        }
        if (directWrite(ctx, component)) {
            sCurrent = component;
            DevLog.i("nfc set (direct) -> " + component);
            refresh(ctx);
            return true;
        }
        if (!Conf.useRoot()) {
            DevLog.e("nfc direct write denied, root disabled");
            return false;
        }
        if (RootBridge.setNfc(ctx, component)) {
            sCurrent = component;
            DevLog.i("nfc set (root) -> " + component);
            refresh(ctx);
            return true;
        }
        DevLog.e("nfc set failed for " + component);
        return false;
    }

    private static boolean directWrite(Context ctx, String component) {
        try {
            ContentResolver cr = ctx.getContentResolver();
            Class<?> secure = Class.forName("android.provider.Settings$Secure");
            Method m = secure.getDeclaredMethod("putStringForUser",
                    ContentResolver.class, String.class, String.class, int.class);
            m.setAccessible(true);
            Object r = m.invoke(null, cr, Conf.KEY_NFC_PAYMENT, component, -2);
            if (Boolean.TRUE.equals(r)) {
                return true;
            }
        } catch (Throwable t) {
            DevLog.i("putStringForUser unavailable: " + t.getClass().getSimpleName());
        }
        try {
            return Settings.Secure.putString(ctx.getContentResolver(),
                    Conf.KEY_NFC_PAYMENT, component);
        } catch (Throwable t) {
            return false;
        }
    }

    /** 让 NFC 立刻按新的默认支付组件重建路由（不是所有 ROM 都有，失败无所谓）。 */
    public static void refresh(Context ctx) {
        try {
            NfcAdapter adapter = NfcAdapter.getDefaultAdapter(ctx);
            if (adapter == null) {
                return;
            }
            Method m = adapter.getClass().getDeclaredMethod("maybeUpdateCardEmulationRoute");
            m.setAccessible(true);
            m.invoke(adapter);
            DevLog.i("nfc route refresh requested");
        } catch (Throwable ignored) {
        }
    }
}
