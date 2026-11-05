package com.honorwallet.gpay.lsp;

import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;
import android.os.Bundle;

/**
 * 钱包进程 → 模块进程的 root 通道。
 *
 * <p>实机反馈：钱包进程读 {@code nfc_payment_default_component} 会抛 SecurityException，
 * 写更不可能。所以「读/写默认支付组件」和「am start」一样，都转给模块自己的 uid 以 root 执行，
 * 而不是让荣耀钱包（系统应用）去申请 root。
 */
public final class RootBridge {

    private RootBridge() {
    }

    public static Bundle call(Context ctx, String method, String arg) {
        try {
            ContentResolver cr = ctx.getContentResolver();
            return cr.call(Uri.parse("content://" + Conf.AUTHORITY), method, arg, null);
        } catch (Throwable t) {
            DevLog.e("root bridge call failed: " + method, t);
            return null;
        }
    }

    /** 读当前默认 NFC 支付组件（模块 uid + root）。 */
    public static String getNfc(Context ctx) {
        Bundle b = call(ctx, RootCommandProvider.METHOD_GET_NFC, null);
        if (b == null) {
            return null;
        }
        String out = b.getString(RootCommandProvider.KEY_OUTPUT);
        return out == null ? null : out.trim();
    }

    public static boolean setNfc(Context ctx, String component) {
        Bundle b = call(ctx, RootCommandProvider.METHOD_SET_NFC, component);
        return b != null && b.getBoolean(RootCommandProvider.KEY_SUCCESS);
    }

    public static boolean launchWallet(Context ctx) {
        Bundle b = call(ctx, RootCommandProvider.METHOD_LAUNCH, null);
        return b != null && b.getBoolean(RootCommandProvider.KEY_SUCCESS);
    }
}
