package com.hycalendar.app;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;

/** 화면 구성용 도우미 (둥근 카드, 분할 버튼, 스위치, 슬라이더, 탭 아이콘). */
final class Ui {
    private Ui() {}

    interface IntCb { void run(int i); }
    interface BoolCb { void run(boolean b); }
    interface FloatCb { void run(float v); }

    static float density = 3f;
    static void init(Context c) { density = c.getResources().getDisplayMetrics().density; }
    static int dp(float v) { return (int) (v * density + 0.5f); }

    static GradientDrawable round(int color, float rDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(rDp));
        return g;
    }

    static GradientDrawable stroke(int fill, int strokeColor, float rDp, float wDp) {
        GradientDrawable g = round(fill, rDp);
        g.setStroke(Math.max(1, dp(wDp)), strokeColor);
        return g;
    }

    static TextView text(Context c, String s, float sp, int color, boolean bold) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(null, android.graphics.Typeface.BOLD);
        return t;
    }

    static LinearLayout card(Context c, Theme th) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setPadding(dp(16), dp(14), dp(16), dp(14));
        l.setBackground(round(th.card, 16));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, 0, dp(12));
        l.setLayoutParams(lp);
        return l;
    }

    static TextView sectionTitle(Context c, Theme th, String s) {
        TextView t = text(c, s, 13, th.sub, true);
        t.setPadding(dp(6), dp(10), 0, dp(6));
        return t;
    }

    static TextView btn(Context c, Theme th, String s, boolean primary, View.OnClickListener l) {
        TextView t = text(c, s, 14, primary ? Color.WHITE : th.fg, true);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(14), dp(9), dp(14), dp(9));
        t.setBackground(round(primary ? th.todayStroke : th.soft, 10));
        t.setOnClickListener(l);
        return t;
    }

    static View segmented(Context c, Theme th, String[] names, int cur, IntCb cb) {
        LinearLayout row = new LinearLayout(c);
        row.setPadding(dp(3), dp(3), dp(3), dp(3));
        row.setBackground(round(th.soft, 12));
        TextView[] ts = new TextView[names.length];
        for (int i = 0; i < names.length; i++) {
            TextView t = text(c, names[i], 13, i == cur ? th.fg : th.sub, i == cur);
            t.setGravity(Gravity.CENTER);
            t.setPadding(0, dp(9), 0, dp(9));
            if (i == cur) t.setBackground(round(th.card, 9));
            final int ix = i;
            ts[i] = t;
            t.setOnClickListener(v -> {
                for (int j = 0; j < ts.length; j++) {
                    ts[j].setBackground(j == ix ? round(th.card, 9) : null);
                    ts[j].setTextColor(j == ix ? th.fg : th.sub);
                    ts[j].setTypeface(null, j == ix ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
                }
                cb.run(ix);
            });
            row.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        }
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, dp(4), 0, dp(6));
        row.setLayoutParams(lp);
        return row;
    }

    /** 크고 선명한 스위치 */
    static final class Sw extends View {
        boolean on;
        private final BoolCb cb;
        private final int onC, offC;
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

        Sw(Context c, Theme th, boolean cur, BoolCb cb) {
            super(c);
            this.on = cur; this.cb = cb;
            this.onC = th.todayStroke;
            this.offC = th.dark ? 0xFF5A5A60 : 0xFFB4B4BA;
            setOnClickListener(v -> { on = !on; invalidate(); if (this.cb != null) this.cb.run(on); });
        }

        void set(boolean v) { on = v; invalidate(); }

        @Override protected void onMeasure(int w, int h) { setMeasuredDimension(dp(58), dp(34)); }

        @Override protected void onDraw(Canvas cv) {
            float w = getWidth(), h = getHeight();
            p.setStyle(Paint.Style.FILL);
            p.setColor(on ? onC : offC);
            cv.drawRoundRect(new RectF(0, 0, w, h), h / 2, h / 2, p);
            float r = h / 2f - dp(3.5f);
            float cx = on ? w - h / 2f : h / 2f;
            p.setColor(0x33000000);
            cv.drawCircle(cx, h / 2f + dp(1), r, p);
            p.setColor(0xFFFFFFFF);
            cv.drawCircle(cx, h / 2f, r, p);
        }
    }

    static Sw switchView(Context c, Theme th, boolean cur, BoolCb cb) { return new Sw(c, th, cur, cb); }

    static View toggle(Context c, Theme th, String label, boolean cur, BoolCb cb) {
        LinearLayout row = new LinearLayout(c);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(9), 0, dp(9));
        row.addView(text(c, label, 16, th.fg, false), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        row.addView(new Sw(c, th, cur, cb));
        return row;
    }

    /** 여러 개 고르는 칩 줄 (sel 배열을 직접 바꾼다) */
    static View chips(Context c, Theme th, String[] labels, boolean[] sel, Runnable changed) {
        LinearLayout row = new LinearLayout(c);
        row.setPadding(0, dp(4), 0, dp(4));
        for (int i = 0; i < labels.length; i++) {
            final int ix = i;
            TextView t = text(c, labels[i], 13, sel[i] ? Color.WHITE : th.fg, true);
            t.setGravity(Gravity.CENTER);
            t.setPadding(0, dp(9), 0, dp(9));
            t.setBackground(round(sel[i] ? th.todayStroke : th.soft, 18));
            t.setOnClickListener(v -> {
                sel[ix] = !sel[ix];
                t.setTextColor(sel[ix] ? Color.WHITE : th.fg);
                t.setBackground(round(sel[ix] ? th.todayStroke : th.soft, 18));
                if (changed != null) changed.run();
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
            lp.setMargins(dp(2), 0, dp(2), 0);
            row.addView(t, lp);
        }
        return row;
    }

    /** 전체 화면 창 */
    static android.app.Dialog fullDialog(android.app.Activity a, Theme th, View content) {
        android.app.Dialog d = new android.app.Dialog(a, android.R.style.Theme_DeviceDefault_NoActionBar);
        d.setContentView(content);
        android.view.Window w = d.getWindow();
        if (w != null) {
            w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            w.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(th.page));
            w.setStatusBarColor(th.page);
            w.setNavigationBarColor(th.page);
            int f = w.getDecorView().getSystemUiVisibility();
            if (!th.dark) f |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR; else f &= ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            w.getDecorView().setSystemUiVisibility(f);
            w.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }
        return d;
    }

    /** 아래에서 올라오는 시트 (높이는 화면 비율) */
    static android.app.Dialog sheetDialog(android.app.Activity a, Theme th, View content, float frac) {
        android.app.Dialog d = new android.app.Dialog(a, android.R.style.Theme_DeviceDefault_NoActionBar);
        d.setContentView(content);
        android.view.Window w = d.getWindow();
        if (w != null) {
            int h = (int) (a.getResources().getDisplayMetrics().heightPixels * frac);
            w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, h);
            w.setGravity(Gravity.BOTTOM);
            w.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
            w.setNavigationBarColor(th.card);
            w.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }
        return d;
    }

    /** 색 고르기 (Theme.PALETTE 번호) */
    static View swatches(Context c, Theme th, int cur, IntCb cb) {
        LinearLayout row = new LinearLayout(c);
        row.setPadding(0, dp(4), 0, dp(8));
        View[] vs = new View[Theme.PALETTE.length];
        for (int i = 0; i < Theme.PALETTE.length; i++) {
            TextView v = new TextView(c);
            v.setGravity(Gravity.CENTER);
            v.setTextSize(11);
            v.setTextColor(th.sub);
            final int ix = i;
            vs[i] = v;
            paintSwatch(th, v, i, i == cur);
            v.setOnClickListener(x -> {
                for (int j = 0; j < vs.length; j++) paintSwatch(th, (TextView) vs[j], j, j == ix);
                cb.run(ix);
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(32), 1);
            lp.setMargins(dp(2), 0, dp(2), 0);
            row.addView(v, lp);
        }
        return row;
    }

    private static void paintSwatch(Theme th, TextView v, int i, boolean sel) {
        int base = Theme.PALETTE[i];
        int fill = base == 0 ? th.card : Theme.tint(th, i);
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(fill);
        g.setStroke(dp(sel ? 2.5f : 1f), sel ? th.todayStroke : th.line);
        v.setBackground(g);
        v.setText(base == 0 ? "없음" : "");
    }

    /** 슬라이더: 오른쪽에 현재 값을 보여준다. */
    static View slider(Context c, Theme th, String label, float min, float max, float cur, FloatCb cb) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setPadding(0, dp(4), 0, dp(2));
        LinearLayout top = new LinearLayout(c);
        top.addView(text(c, label, 14, th.fg, false), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        TextView val = text(c, String.format("%.2f", cur), 13, th.sub, false);
        top.addView(val);
        l.addView(top);
        SeekBar sb = new SeekBar(c);
        sb.setMax(100);
        sb.setProgress(Math.round((cur - min) / (max - min) * 100));
        sb.setProgressTintList(ColorStateList.valueOf(th.todayStroke));
        sb.setThumbTintList(ColorStateList.valueOf(th.todayStroke));
        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar x, int p, boolean u) {
                float v = min + (max - min) * p / 100f;
                val.setText(String.format("%.2f", v));
                if (u) cb.run(v);
            }
            public void onStartTrackingTouch(SeekBar x) { }
            public void onStopTrackingTouch(SeekBar x) { }
        });
        l.addView(sb);
        return l;
    }

    /** 하단 탭 아이콘 */
    static final class TabIcon extends View {
        private final int kind;
        private int color;
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

        TabIcon(Context c, int kind) { super(c); this.kind = kind; }

        void setColor(int c) { color = c; invalidate(); }

        @Override protected void onDraw(Canvas cv) {
            float s = Math.min(getWidth(), getHeight()) / 24f;
            cv.save();
            cv.translate((getWidth() - 24 * s) / 2f, (getHeight() - 24 * s) / 2f);
            cv.scale(s, s);
            p.setColor(color);
            p.setStyle(Paint.Style.FILL);
            switch (kind) {
                case 0: // 달력
                    cv.drawRoundRect(new RectF(3, 4.5f, 21, 21), 3, 3, p);
                    p.setColor(0xFFFFFFFF);
                    for (int r = 0; r < 2; r++) for (int k = 0; k < 3; k++) cv.drawCircle(8 + k * 4, 11.5f + r * 4.2f, 1.1f, p);
                    p.setColor(color);
                    cv.drawRect(7, 2, 8.6f, 6, p);
                    cv.drawRect(15.4f, 2, 17, 6, p);
                    break;
                case 1: // 부부
                    cv.drawCircle(8.5f, 8, 3.4f, p);
                    cv.drawCircle(16.5f, 9, 2.8f, p);
                    cv.drawRoundRect(new RectF(2.5f, 13, 14.5f, 20.5f), 4, 4, p);
                    cv.drawRoundRect(new RectF(12, 14, 21.5f, 20.5f), 3.5f, 3.5f, p);
                    break;
                case 2: // 4개조
                    cv.drawRoundRect(new RectF(3, 3, 11, 11), 2, 2, p);
                    cv.drawRoundRect(new RectF(13, 3, 21, 11), 2, 2, p);
                    cv.drawRoundRect(new RectF(3, 13, 11, 21), 2, 2, p);
                    cv.drawRoundRect(new RectF(13, 13, 21, 21), 2, 2, p);
                    break;
                case 3: // 근태 (시계)
                    cv.drawCircle(12, 12.5f, 9, p);
                    p.setColor(0xFFFFFFFF);
                    p.setStyle(Paint.Style.STROKE);
                    p.setStrokeWidth(1.8f);
                    p.setStrokeCap(Paint.Cap.ROUND);
                    cv.drawLine(12, 7.5f, 12, 12.5f, p);
                    cv.drawLine(12, 12.5f, 15.5f, 14.5f, p);
                    break;
                case 5: // 근태 (서류판)
                    cv.drawRoundRect(new RectF(4.5f, 4, 19.5f, 21.5f), 2.5f, 2.5f, p);
                    cv.drawRoundRect(new RectF(8.5f, 2, 15.5f, 6.5f), 1.5f, 1.5f, p);
                    p.setColor(0xFFFFFFFF);
                    cv.drawRect(8, 10.5f, 16, 11.8f, p);
                    cv.drawRect(8, 14, 16, 15.3f, p);
                    cv.drawRect(8, 17.5f, 13, 18.8f, p);
                    break;
                case 6: // 게시판 (말풍선)
                    cv.drawRoundRect(new RectF(3, 3.5f, 21, 17), 3.5f, 3.5f, p);
                    android.graphics.Path tail = new android.graphics.Path();
                    tail.moveTo(8, 16.5f); tail.lineTo(8, 21.5f); tail.lineTo(13, 16.5f); tail.close();
                    cv.drawPath(tail, p);
                    p.setColor(0xFFFFFFFF);
                    cv.drawRect(7, 7.5f, 17, 8.8f, p);
                    cv.drawRect(7, 11, 14, 12.3f, p);
                    break;
                default: // 설정 (톱니)
                    p.setStyle(Paint.Style.STROKE);
                    p.setStrokeWidth(2.6f);
                    cv.drawCircle(12, 12, 6.2f, p);
                    p.setStrokeWidth(3.2f);
                    for (int i = 0; i < 8; i++) {
                        double a = Math.PI / 4 * i;
                        cv.drawLine(12 + (float) Math.cos(a) * 7.6f, 12 + (float) Math.sin(a) * 7.6f,
                                12 + (float) Math.cos(a) * 10f, 12 + (float) Math.sin(a) * 10f, p);
                    }
                    p.setStyle(Paint.Style.FILL);
                    cv.drawCircle(12, 12, 2.4f, p);
                    break;
            }
            cv.restore();
        }
    }
}
