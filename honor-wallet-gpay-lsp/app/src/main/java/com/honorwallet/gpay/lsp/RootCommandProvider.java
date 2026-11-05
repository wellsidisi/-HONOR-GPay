package com.honorwallet.gpay.lsp;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;
import android.os.Process;

/**
 * 模块自己的「root 通道」。
 *
 * <p>hook 跑在荣耀钱包进程里，但 root 只授予模块自己的 uid（而不是整个钱包），
 * 所以需要 root 的动作（am start / settings put）都转到这里执行。钱包进程通过
 * {@code content://com.honorwallet.gpay.lsp.root} 调进来，调用方 uid 必须属于荣耀钱包。
 */
public class RootCommandProvider extends ContentProvider {

    public static final String METHOD_LAUNCH = "launch_wallet";
    public static final String METHOD_SET_NFC = "set_nfc";
    public static final String METHOD_GET_NFC = "get_nfc";
    public static final String METHOD_STATE = "state";
    public static final String KEY_SUCCESS = "success";
    public static final String KEY_OUTPUT = "output";

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public Bundle call(String method, String arg, Bundle extras) {
        checkCaller();
        if (METHOD_LAUNCH.equals(method)) {
            return launchWallet();
        }
        if (METHOD_SET_NFC.equals(method)) {
            return setNfc(arg);
        }
        if (METHOD_GET_NFC.equals(method)) {
            return getNfc();
        }
        if (METHOD_STATE.equals(method)) {
            return state();
        }
        return null;
    }

    private Bundle launchWallet() {
        String cmd = "/system/bin/am start --user current"
                + " -f 0x" + Integer.toHexString(Conf.GW_QUICKDRAW_FLAGS)
                + " -a " + Conf.GW_QUICKDRAW_ACTION
                + " -n " + Conf.gatewayPackage() + "/" + Conf.GW_QUICKDRAW_ACTIVITY;
        Shell.Result r = Shell.su(cmd);
        DevLog.i("am start: " + r);
        return reply(r.ok, r.output + (r.ok ? "" : " (exit=" + r.code + ")"));
    }

    private Bundle setNfc(String component) {
        String cmd = "settings --user current put secure "
                + Conf.KEY_NFC_PAYMENT + " '" + component + "'";
        Shell.Result r = Shell.su(cmd);
        DevLog.i("settings put: " + r);
        return reply(r.ok, r.output);
    }

    /**
     * 钱包进程没有读取 nfc_payment_default_component 的权限（实机报 SecurityException），
     * 所以「抓当前默认支付组件」这一步也得由模块 uid 以 root 身份代读。
     */
    private Bundle getNfc() {
        Shell.Result r = Shell.su("settings --user current get secure " + Conf.KEY_NFC_PAYMENT);
        DevLog.i("settings get: " + r);
        return reply(r.ok, r.output);
    }

    private Bundle state() {
        Context ctx = getContext();
        String nfc = ctx == null ? "" : NfcControl.current(ctx);
        return reply(true, "nfc=" + nfc + " conf=" + Conf.sourcePath());
    }

    private static Bundle reply(boolean ok, String output) {
        Bundle b = new Bundle();
        b.putBoolean(KEY_SUCCESS, ok);
        b.putString(KEY_OUTPUT, output == null ? "" : output);
        return b;
    }

    private void checkCaller() {
        int uid = Binder.getCallingUid();
        if (uid == Process.myUid()) {
            return;
        }
        Context ctx = getContext();
        PackageManager pm = ctx == null ? null : ctx.getPackageManager();
        String[] pkgs = pm == null ? null : pm.getPackagesForUid(uid);
        if (pkgs != null) {
            for (String p : pkgs) {
                if (Conf.walletPackage().equals(p) || Conf.MODULE_PKG.equals(p)) {
                    return;
                }
            }
        }
        throw new SecurityException("untrusted caller uid=" + uid);
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection,
                        String[] selectionArgs, String sortOrder) {
        return null;
    }

    @Override
    public String getType(Uri uri) {
        return null;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        return null;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        return 0;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        return 0;
    }
}
