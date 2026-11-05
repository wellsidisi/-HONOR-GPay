package com.honorwallet.gpay.lsp;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

/**
 * 拉起 Google Wallet 的快捷刷卡页。
 *
 * <p>为什么优先走 root：从别的 App（这里是钱包进程）直接 startActivity 一个后台 Activity，
 * 会被后台启动限制拦掉；而用 {@code am start} 起出来的 Activity 归属仍然是 Google Wallet，
 * 我们挂在 system_server 的 dismissKeyguard hook 才能认出「这是 Wallet 的 QUICKDRAW」并跳过锁屏。
 * 直连方式作为兜底保留。
 */
public final class WalletLauncher {

    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private WalletLauncher() {
    }

    /**
     * 同步拉起，返回「有没有成功发出去」。跑在后台线程，供按钮点击后直接 toast 结果，
     * 不接 adb 也能在手机屏幕上看到成功/失败。
     */
    public static boolean launchSync(Context ctx) {
        Context app = ctx.getApplicationContext();
        if (!installed(app)) {
            toast(app, "未安装 Google Wallet");
            return false;
        }
        if (Conf.useRoot() && RootBridge.launchWallet(app)) {
            DevLog.i("launch via root ok");
            return true;
        }
        return direct(app);
    }

    public static void launch(Context ctx) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                launchSync(ctx);
            }
        }, "hwg-launch").start();
    }

    /** 直接 startActivity（无 root 兜底）。 */
    public static boolean direct(Context ctx) {
        Intent i = new Intent(Conf.GW_QUICKDRAW_ACTION);
        i.setComponent(new ComponentName(Conf.gatewayPackage(), Conf.GW_QUICKDRAW_ACTIVITY));
        i.addFlags(Conf.GW_QUICKDRAW_FLAGS);
        try {
            ctx.startActivity(i);
            DevLog.i("direct startActivity sent");
            return true;
        } catch (Throwable t) {
            DevLog.e("direct startActivity failed", t);
            toast(ctx, "打不开 Google Wallet：" + t.getClass().getSimpleName());
            return false;
        }
    }

    public static boolean installed(Context ctx) {
        try {
            ctx.getPackageManager().getPackageInfo(Conf.gatewayPackage(), 0);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    public static void toast(Context ctx, String msg) {
        MAIN.post(new Runnable() {
            @Override
            public void run() {
                try {
                    Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show();
                } catch (Throwable ignored) {
                }
            }
        });
    }
}
