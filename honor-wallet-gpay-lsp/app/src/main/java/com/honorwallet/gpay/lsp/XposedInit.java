package com.honorwallet.gpay.lsp;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/** LSPosed 入口，类名由 assets/xposed_init 指定。 */
public final class XposedInit implements IXposedHookLoadPackage {

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lp) {
        String pkg = lp == null ? null : lp.packageName;
        DevLog.i("loaded into " + pkg);
        try {
            XposedBridge.log(Conf.MODULE_PKG + " v" + BuildInfo.VERSION_NAME
                    + " loaded into " + pkg);
        } catch (Throwable ignored) {
        }
        try {
            new MainHook().handleLoadPackage(lp);
        } catch (Throwable t) {
            DevLog.e("MainHook crashed", t);
        }
    }
}
