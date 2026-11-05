package com.honorwallet.gpay.lsp;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.util.TypedValue;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.List;

/**
 * 模块信息页（点图标打开）。
 *
 * <p>它跑在模块自己的进程里，没有 root、也没有 hook，但正好能回答「实机上到底是什么情况」：
 * 荣耀钱包装没装、版本多少、当前 NFC 默认支付组件是谁、钱包里有哪些 Activity、
 * 哪些会被判定成刷卡页。把这一页的报告复制出来，就能直接定位需要固定的类名。
 */
public final class InfoActivity extends Activity {

    private LinearLayout column;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ScrollView scroll = new ScrollView(this);
        column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(20);
        column.setPadding(pad, pad, pad, pad);
        scroll.addView(column);
        setContentView(scroll);
        render();
    }

    private void render() {
        column.removeAllViews();
        addTitle("荣耀钱包 · GPay 快捷按钮");
        addBody("模块版本 v" + BuildInfo.VERSION_NAME + "（" + BuildInfo.VERSION_CODE + "）");
        addBody(report());
        addButton("刷新", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                render();
            }
        });
        addButton("复制诊断报告", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                copy();
            }
        });
    }

    private String report() {
        StringBuilder sb = new StringBuilder();
        String wallet = Conf.walletPackage();
        String gateway = Conf.gatewayPackage();

        sb.append("目标应用  ").append(wallet).append('\n');
        String walletVer = ActivityProbe.appVersion(this, wallet);
        sb.append("          ").append(walletVer == null ? "未安装" : "v" + walletVer).append('\n');

        sb.append("Google Wallet  ").append(gateway).append('\n');
        String gwVer = ActivityProbe.appVersion(this, gateway);
        sb.append("          ").append(gwVer == null ? "未安装" : "v" + gwVer).append('\n');

        sb.append("NFC 默认支付组件\n          ").append(safeNfc()).append('\n');

        String conf = Conf.sourcePath();
        sb.append("配置文件\n          ")
                .append(conf.isEmpty() ? "未找到（使用内置默认值）" : conf).append('\n');

        sb.append("调试提示\n          ")
                .append(Conf.getBool("debug", false)
                        ? "开：点击弹结果、长按复制诊断"
                        : "关（要开就在配置文件写 debug=1）")
                .append('\n');

        sb.append("日志\n          ").append(Conf.LOG_PATHS[0]).append('\n');
        sb.append("LSPosed 日志过滤：logcat -s ").append(DevLog.TAG).append('\n');

        sb.append("\n钱包里的 Activity（分数 ≥ 60 才会自动注入）\n");
        List<ActivityProbe.Entry> list = ActivityProbe.list(this);
        if (list.isEmpty()) {
            sb.append("          （读不到：应用未安装或权限不足）\n");
        } else {
            int shown = 0;
            for (ActivityProbe.Entry e : list) {
                if (shown++ >= 15) {
                    sb.append("          … 共 ").append(list.size()).append(" 个\n");
                    break;
                }
                sb.append(e.autoInject() ? " ✓ " : "   ")
                        .append('[').append(e.score).append("] ")
                        .append(e.name).append('\n');
            }
        }

        sb.append("\n✓ = 会注入按钮的页面（默认是实机确认过的白名单）。\n")
                .append("要换成别的页面，把下面一行写进配置文件（多个用逗号分隔）：\n")
                .append("  activity=com.hihonor.wallet.xxx.A,com.hihonor.wallet.xxx.B\n")
                .append("想全量注入：inject_all=1；改按钮位置：button_margin_bottom_dp=120");
        return sb.toString();
    }

    private String safeNfc() {
        try {
            // 钱包进程直读会 SecurityException，这里同样走「直读 → root 代读」的兜底
            String v = NfcControl.current(this);
            if (v == null || v.isEmpty()) {
                return "读不到（给本模块授权 root 后重开本页）";
            }
            return v;
        } catch (Throwable t) {
            return "读取失败：" + t.getClass().getSimpleName();
        }
    }

    private void copy() {
        try {
            String text = "包名 " + getPackageName() + "\n"
                    + "版本 " + BuildInfo.VERSION_NAME + "\n\n" + report();
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null) {
                cm.setPrimaryClip(ClipData.newPlainText("honor-wallet-gpay", text));
                WalletLauncher.toast(this, "已复制");
            }
        } catch (Throwable t) {
            WalletLauncher.toast(this, "复制失败：" + t.getClass().getSimpleName());
        }
    }

    private void addTitle(String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        tv.setPadding(0, 0, 0, dp(12));
        column.addView(tv);
    }

    private void addBody(String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        tv.setTextIsSelectable(true);
        tv.setTypeface(android.graphics.Typeface.MONOSPACE);
        tv.setInputType(InputType.TYPE_NULL);
        tv.setPadding(0, 0, 0, dp(8));
        column.addView(tv);
    }

    private void addButton(String text, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(text);
        b.setOnClickListener(l);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(8);
        column.addView(b, lp);
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
