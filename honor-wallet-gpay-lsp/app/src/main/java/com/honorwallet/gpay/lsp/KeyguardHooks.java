package com.honorwallet.gpay.lsp;

import android.content.ComponentName;
import android.content.Intent;
import android.os.IBinder;

import java.lang.reflect.Method;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

/**
 * system_server 侧：跳过锁屏。
 *
 * <p>场景是「手机在锁屏上，双击电源键 → 荣耀钱包刷卡页 → 点 GPay 按钮」。
 * 起 Google Wallet 的 QUICKDRAW 页时会走 dismissKeyguard；默认行为是弹密码/指纹，
 * 那就完全谈不上「快捷」了。这里只对「Google Wallet 的 QUICKDRAW 页」这一种情况
 * 直接回 onDismissSucceeded，其它一律放行原逻辑。
 *
 * <p>与原版差异：不写死方法签名（Android 14/15、MagicOS 8/9 上 dismissKeyguard 的
 * 重载可能变），而是把目标类里所有带 IBinder 参数的 dismissKeyguard 全部挂上，
 * 运行时再判断参数。
 */
public final class KeyguardHooks {

    private static final String[] TARGETS = {
            "com.android.server.wm.KeyguardController",
            "com.android.server.wm.ActivityTaskManagerService",
    };

    private static final XC_MethodHook HOOK = new XC_MethodHook() {
        @Override
        protected void beforeHookedMethod(MethodHookParam param) {
            try {
                IBinder token = null;
                Object callback = null;
                for (Object arg : param.args) {
                    if (arg instanceof IBinder && token == null) {
                        token = (IBinder) arg;
                    } else if (arg != null && callback == null
                            && arg.getClass().getName().contains("IKeyguardDismissCallback")) {
                        callback = arg;
                    }
                }
                if (token == null || callback == null) {
                    return;
                }
                ClassLoader loader = param.method.getDeclaringClass().getClassLoader();
                if (!isGatewayQuickDraw(token, loader)) {
                    return;
                }
                DevLog.i("keyguard: suppress bouncer for Gateway QUICKDRAW");
                XposedHelpers.callMethod(callback, "onDismissSucceeded");
                param.setResult(null);
            } catch (Throwable t) {
                DevLog.e("keyguard hook error (allow original)", t);
            }
        }
    };

    private KeyguardHooks() {
    }

    public static void install(ClassLoader cl) {
        DevLog.i("keyguard: installing in system_server");
        int hooked = 0;
        for (String name : TARGETS) {
            Class<?> cls = XposedHelpers.findClassIfExists(name, cl);
            if (cls == null) {
                DevLog.i("keyguard: " + name + " not found");
                continue;
            }
            for (Method m : cls.getDeclaredMethods()) {
                if (!"dismissKeyguard".equals(m.getName())) {
                    continue;
                }
                boolean hasBinder = false;
                for (Class<?> p : m.getParameterTypes()) {
                    if (IBinder.class.isAssignableFrom(p)) {
                        hasBinder = true;
                        break;
                    }
                }
                if (!hasBinder) {
                    continue;
                }
                try {
                    XposedBridge.hookMethod(m, HOOK);
                    hooked++;
                    DevLog.i("keyguard: hooked " + name + "#" + m.getName() + signature(m));
                } catch (Throwable t) {
                    DevLog.e("keyguard: hook failed " + name, t);
                }
            }
        }
        DevLog.i("keyguard: installed " + hooked + " hook(s)");
    }

    private static String signature(Method m) {
        StringBuilder sb = new StringBuilder("(");
        Class<?>[] ps = m.getParameterTypes();
        for (int i = 0; i < ps.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(ps[i].getSimpleName());
        }
        return sb.append(')').toString();
    }

    private static boolean isGatewayQuickDraw(IBinder token, ClassLoader loader) {
        Class<?> record = XposedHelpers.findClassIfExists(
                "com.android.server.wm.ActivityRecord", loader);
        if (record == null) {
            return false;
        }
        Object rec = callStatic(record, "forTokenLocked", token);
        if (rec == null) {
            rec = callStatic(record, "forToken", token);
        }
        if (rec == null) {
            return false;
        }
        ComponentName cn = null;
        try {
            Object v = XposedHelpers.getObjectField(rec, "mActivityComponent");
            cn = v instanceof ComponentName ? (ComponentName) v : null;
        } catch (Throwable ignored) {
        }
        if (cn == null) {
            try {
                Object v = XposedHelpers.getObjectField(rec, "activityComponent");
                cn = v instanceof ComponentName ? (ComponentName) v : null;
            } catch (Throwable ignored) {
            }
        }
        if (cn == null || !Conf.gatewayPackage().equals(cn.getPackageName())) {
            return false;
        }
        Intent intent = null;
        try {
            Object v = XposedHelpers.getObjectField(rec, "intent");
            intent = v instanceof Intent ? (Intent) v : null;
        } catch (Throwable ignored) {
        }
        boolean classOk = Conf.GW_QUICKDRAW_ACTIVITY.equals(cn.getClassName());
        boolean actionOk = intent != null && Conf.GW_QUICKDRAW_ACTION.equals(intent.getAction());
        if (!classOk && !actionOk) {
            return false;
        }
        DevLog.i("keyguard: token=" + cn.flattenToShortString()
                + " action=" + (intent == null ? null : intent.getAction()));
        return true;
    }

    private static Object callStatic(Class<?> cls, String name, Object... args) {
        try {
            return XposedHelpers.callStaticMethod(cls, name, args);
        } catch (Throwable t) {
            return null;
        }
    }
}
