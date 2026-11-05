package com.honorwallet.gpay.lsp;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/** 按包名分发：荣耀钱包 → 注入按钮；android（system_server）→ 跳过锁屏。 */
public final class MainHook implements IXposedHookLoadPackage {

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lp) {
        String pkg = lp == null ? null : lp.packageName;
        if (pkg == null) {
            return;
        }
        try {
            if (Conf.MODULE_PKG.equals(pkg)) {
                return;
            }
            if ("android".equals(pkg)) {
                KeyguardHooks.install(lp.classLoader);
                return;
            }
            if (Conf.walletPackage().equals(pkg)) {
                WalletHooks.install(lp.classLoader, pkg);
                return;
            }
            DevLog.i("skip package: " + pkg);
        } catch (Throwable t) {
            DevLog.e("handleLoadPackage failed for " + pkg, t);
        }
    }
}
