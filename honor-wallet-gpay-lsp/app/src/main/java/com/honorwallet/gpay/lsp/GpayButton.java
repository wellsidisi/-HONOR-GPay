package com.honorwallet.gpay.lsp;

import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.RippleDrawable;
import android.view.View;
import android.view.ViewOutlineProvider;

/**
 * Google Wallet 快捷按钮：药丸底 + Google Pay 彩色字标，深浅色自适应，带水波纹和按压反馈。
 *
 * <p>注意一个 Xposed 模块的坑：{@code R.drawable.*} 是「模块 APK」里的资源 id，
 * 如果直接拿钱包的 Context 去 {@code getDrawable(id)}，解析的是钱包的资源表——id 对不上，
 * 结果要么抛异常、要么取到别的图。所以这里必须用模块自己的 {@link Context} 取图。
 */
public final class GpayButton extends View {

    /** 模块自己的 Resources（跨进程复用一份就够）。 */
    private static Context sModuleContext;

    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final boolean dark;
    private Drawable logo;
    private boolean logoTried;

    public GpayButton(Context context) {
        super(context);
        setClickable(true);
        setFocusable(true);
        setContentDescription("Google Wallet");
        setElevation(dp(6));

        dark = useDarkStyle(context);
        fillPaint.setColor(dark ? 0xFF202124 : 0xFFFFFFFF);
        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setStrokeWidth(dp(1));
        strokePaint.setColor(dark ? 0xFF5F6368 : 0xFFDADCE0);
        textPaint.setColor(dark ? 0xFFE8EAED : 0xFF5F6368);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));

        setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(),
                        view.getHeight() / 2f);
            }
        });
        setClipToOutline(true);
        setForeground(new RippleDrawable(
                ColorStateList.valueOf(dark ? 0x33FFFFFF : 0x1F000000), null, null));
    }

    private float dp(float v) {
        return v * getResources().getDisplayMetrics().density;
    }

    /**
     * 默认白底：荣耀钱包的刷卡页本身是深色底（实机截图），白药丸对比度最好；
     * 需要黑底可以写 {@code button_theme=dark}，或者 {@code auto} 跟随系统深色。
     */
    private static boolean useDarkStyle(Context context) {
        String theme = Conf.get("button_theme", "light").toLowerCase();
        if ("dark".equals(theme)) {
            return true;
        }
        if ("auto".equals(theme)) {
            return (context.getResources().getConfiguration().uiMode
                    & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        }
        return false;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        setMeasuredDimension(dp(Conf.getInt("button_width_dp", 108)),
                dp(Conf.getInt("button_height_dp", 56)));
    }

    @Override
    public void setPressed(boolean pressed) {
        super.setPressed(pressed);
        animate().cancel();
        animate().scaleX(pressed ? 0.96f : 1f).scaleY(pressed ? 0.96f : 1f)
                .setDuration(pressed ? 80 : 120).start();
    }

    private void ensureLogo() {
        if (logoTried) {
            return;
        }
        logoTried = true;
        try {
            Context ctx = moduleContext(getContext());
            logo = ctx.getDrawable(dark
                    ? R.drawable.ic_gpay_wordmark_dark
                    : R.drawable.ic_gpay_wordmark);
        } catch (Throwable t) {
            DevLog.e("logo load failed", t);
            logo = null;
        }
    }

    private static Context moduleContext(Context any) {
        if (sModuleContext == null) {
            try {
                sModuleContext = any.createPackageContext(Conf.MODULE_PKG,
                        Context.CONTEXT_IGNORE_SECURITY);
            } catch (Throwable t) {
                DevLog.e("module context failed", t);
            }
        }
        return sModuleContext == null ? any : sModuleContext;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float w = getWidth();
        float h = getHeight();
        float inset = dp(1);
        rect.set(inset, inset, w - inset, h - inset);
        float radius = (h - 2 * inset) / 2f;
        canvas.drawRoundRect(rect, radius, radius, fillPaint);
        canvas.drawRoundRect(rect, radius, radius, strokePaint);

        ensureLogo();
        if (logo != null) {
            int iw = Math.max(1, logo.getIntrinsicWidth());
            int ih = Math.max(1, logo.getIntrinsicHeight());
            float th = h * 0.58f;
            float tw = th * iw / ih;
            float left = (w - tw) / 2f;
            float top = (h - th) / 2f;
            logo.setBounds(Math.round(left), Math.round(top),
                    Math.round(left + tw), Math.round(top + th));
            logo.draw(canvas);
        } else {
            // 资源万一取不到也别留一颗空白药丸
            textPaint.setTextSize(h * 0.30f);
            float baseline = h / 2f - (textPaint.descent() + textPaint.ascent()) / 2f;
            canvas.drawText("Google Wallet", w / 2f, baseline, textPaint);
        }
    }
}
