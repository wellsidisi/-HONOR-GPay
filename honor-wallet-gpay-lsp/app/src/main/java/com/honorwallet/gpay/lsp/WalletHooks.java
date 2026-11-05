package com.honorwallet.gpay.lsp;

import android.app.Activity;
import android.content.Context;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

/**
 * 荣耀钱包进程里的注入逻辑。
 *
 * <p>和 MiPay 版的区别：MiPay 可以直接 hook 写死的 {@code DoubleClickActivity}；
 * 荣耀钱包的刷卡页类名没法从公开资料确认，所以这里 hook 的是 {@code android.app.Activity}
 * 的 onCreate/onResume（子类调 super 就会命中），再用 {@link ActivityProbe} 打分判断
 * 「这一页是不是刷卡页」。判断错了也不会崩，最多是不注入/多注入一个按钮。
 */
public final class WalletHooks {

    private static final String TAG = "honor_wallet_gpay_btn";

    private static volatile String sLastClass;
    private static volatile long sLastAt;

    private WalletHooks() {
    }

    public static void install(ClassLoader cl, String pkg) {
        DevLog.i("wallet: installing hooks for " + pkg
                + " (conf=" + (Conf.sourcePath().isEmpty() ? "default" : Conf.sourcePath()) + ")");

        Class<?> activity = XposedHelpers.findClassIfExists("android.app.Activity", cl);
        if (activity == null) {
            DevLog.e("wallet: android.app.Activity not found, abort");
            return;
        }
        XC_MethodHook generic = new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                Object self = param.thisObject;
                if (self instanceof Activity) {
                    onActivity((Activity) self, param.method.getName());
                }
            }
        };
        try {
            XposedBridge.hookAllMethods(activity, "onCreate", generic);
            XposedBridge.hookAllMethods(activity, "onResume", generic);
            DevLog.i("wallet: Activity.onCreate/onResume hooked");
        } catch (Throwable t) {
            DevLog.e("wallet: generic hook failed", t);
        }

        // 白名单里的页面直接挂（即使它不调 super.onResume 也能命中）
        for (String pinned : ActivityProbe.pinnedList()) {
            Class<?> target = XposedHelpers.findClassIfExists(pinned, cl);
            if (target == null) {
                DevLog.i("wallet: pinned activity not found: " + pinned);
                continue;
            }
            try {
                XposedBridge.hookAllMethods(target, "onResume", generic);
                DevLog.i("wallet: pinned activity hooked: " + pinned);
            } catch (Throwable t) {
                DevLog.e("wallet: pinned hook failed: " + pinned, t);
            }
        }
        logCandidates();
    }

    /** 把钱包里所有 Activity 按「像不像刷卡页」打分写进日志，一次实机就能锁定类名。 */
    private static void logCandidates() {
        try {
            Context ctx = appContext();
            if (ctx == null) {
                DevLog.i("wallet: no app context yet, skip candidate list");
                return;
            }
            java.util.List<ActivityProbe.Entry> list = ActivityProbe.list(ctx);
            DevLog.i("wallet: " + list.size() + " activities in " + Conf.walletPackage());
            int shown = 0;
            for (ActivityProbe.Entry e : list) {
                if (shown++ >= 12) {
                    break;
                }
                DevLog.i("wallet:   [" + e.score + "] " + e.name
                        + (e.autoInject() ? "  <= inject" : ""));
            }
        } catch (Throwable t) {
            DevLog.e("wallet: candidate list failed", t);
        }
    }

    private static Context appContext() {
        try {
            Class<?> helper = Class.forName("de.robv.android.xposed.AndroidAppHelper");
            Object app = helper.getMethod("currentApplication").invoke(null);
            return app instanceof Context ? (Context) app : null;
        } catch (Throwable t) {
            return null;
        }
    }

    private static void onActivity(Activity activity, String where) {
        try {
            String cls = activity.getClass().getName();
            if (!Conf.walletPackage().equals(activity.getPackageName())) {
                return;
            }
            long now = SystemClock.elapsedRealtime();
            if (cls.equals(sLastClass) && now - sLastAt < 500) {
                return;
            }
            sLastClass = cls;
            sLastAt = now;

            boolean inject = ActivityProbe.isPayScreen(cls);
            DevLog.i("wallet: " + where + " " + cls + " inject=" + inject);
            if (!inject) {
                return;
            }
            // 刷卡页亮着的时候，NFC 默认支付应用应该是荣耀钱包；如果还停在 Google 就切回来。
            // 读/写 Secure 设置要走 su，不能占主线程。
            final Context app = activity.getApplicationContext();
            new Thread(new Runnable() {
                @Override
                public void run() {
                    NfcControl.restoreHonor(app);
                }
            }, "hwg-restore").start();
            injectButton(activity, cls);
        } catch (Throwable t) {
            DevLog.e("wallet: onActivity failed", t);
        }
    }

    private static void injectButton(final Activity activity, final String cls) {
        try {
            View decor = activity.getWindow() == null ? null
                    : activity.getWindow().getDecorView();
            if (!(decor instanceof ViewGroup)) {
                DevLog.e("wallet: decorView is not a ViewGroup, skip inject");
                return;
            }
            final ViewGroup root = (ViewGroup) decor;
            if (root.findViewWithTag(TAG) != null) {
                return;
            }
            final GpayButton button = new GpayButton(activity);
            button.setTag(TAG);

            float density = activity.getResources().getDisplayMetrics().density;
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.BOTTOM | Gravity.END);
            lp.bottomMargin = Math.round(Conf.getInt("button_margin_bottom_dp", 120) * density);
            lp.rightMargin = Math.round(Conf.getInt("button_margin_right_dp", 16) * density);

            button.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    onButtonClick(activity, cls);
                }
            });
            button.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    // 诊断入口只在 debug=1 时存在，正式版长按无动作
                    if (!Conf.getBool("debug", false)) {
                        return false;
                    }
                    copyDiagnostics(activity, cls);
                    return true;
                }
            });

            button.setAlpha(0f);
            button.setScaleX(0.85f);
            button.setScaleY(0.85f);
            root.addView(button, lp);
            button.animate().alpha(1f).scaleX(1f).scaleY(1f)
                    .setDuration(180)
                    .setInterpolator(new DecelerateInterpolator())
                    .start();
            DevLog.i("wallet: button injected into " + cls);
        } catch (Throwable t) {
            DevLog.e("wallet: inject failed", t);
        }
    }

    private static void onButtonClick(final Activity activity, String cls) {
        DevLog.i("wallet: button clicked on " + cls);
        final Context ctx = activity.getApplicationContext();
        new Thread(new Runnable() {
            @Override
            public void run() {
                boolean nfc = NfcControl.switchToGoogle(ctx);
                DevLog.i("wallet: nfc->google " + nfc);
                boolean launched = WalletLauncher.launchSync(ctx);
                if (Conf.getBool("debug", false)) {
                    // 调试模式：把两步结果都弹出来，便于定位
                    WalletLauncher.toast(ctx, (nfc ? "NFC → Google ✓" : "NFC 切换失败 ✗") + "\n"
                            + (launched ? "已拉起 Google Wallet ✓" : "拉起 Wallet 失败 ✗"));
                } else if (!nfc) {
                    // 正式版：成功时完全静默，只在失败时说一句
                    WalletLauncher.toast(ctx, "NFC 未切换：给「荣耀钱包 GPay」授权 root 后重试");
                } else if (!launched) {
                    WalletLauncher.toast(ctx, "打不开 Google Wallet");
                }
            }
        }, "hwg-click").start();
    }

    /** 长按按钮：把「类名 + 当前 NFC 组件」复制到剪贴板，方便直接贴到聊天里。 */
    private static void copyDiagnostics(final Activity activity, final String cls) {
        final Context app = activity.getApplicationContext();
        new Thread(new Runnable() {
            @Override
            public void run() {
                // 读当前组件可能要 su，放后台
                final String text = "activity=" + cls + "\n"
                        + "nfc=" + NfcControl.current(app) + "\n"
                        + "honor=" + NfcControl.honorComponent(app) + "\n"
                        + "module=" + BuildInfo.VERSION_NAME;
                activity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            Object svc = activity.getSystemService(Context.CLIPBOARD_SERVICE);
                            if (svc instanceof android.content.ClipboardManager) {
                                ((android.content.ClipboardManager) svc).setPrimaryClip(
                                        android.content.ClipData.newPlainText(
                                                "honor-wallet-gpay", text));
                            }
                            WalletLauncher.toast(activity, "已复制到剪贴板\n" + cls);
                        } catch (Throwable t) {
                            DevLog.e("copy failed", t);
                            WalletLauncher.toast(activity, text);
                        }
                    }
                });
            }
        }, "hwg-diag").start();
    }
}
