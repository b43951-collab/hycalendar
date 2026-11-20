package com.hycalendar.app;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.SweepGradient;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/** 오늘근무 방식 색 선택: 기본 색상 20색 + 사용자 색상(색상환) */
final class ColorUi {
    private ColorUi() {}

    interface Cb { void run(int color); }

    /** 진한 기본 5색 (원래 값). 0 = 기본/없음 칸은 따로 붙는다 */
    static final int[] BASE5 = {0xFF212121, 0xFFE53935, 0xFF1E6FEB, 0xFF2E9E4F, 0xFFFF4F9A};

    /** 길게 눌러 바꾼 기본색을 반영한 현재 5색 */
    static int[] base(Activity unused) {
        int[] r = BASE5.clone();
        String s = Store.getS("base_ov", "");
        if (!s.isEmpty()) {
            String[] p = s.split(",");
            for (int i = 0; i < p.length && i < 5; i++) { try { int v = Integer.parseInt(p[i]); if (v != 0) r[i] = v; } catch (Exception ignored) { } }
        }
        return r;
    }

    private static void setBase(int i, int color) {
        String[] p = new String[5];
        String s = Store.getS("base_ov", "");
        String[] o = s.isEmpty() ? new String[0] : s.split(",");
        for (int k = 0; k < 5; k++) p[k] = k < o.length ? o[k] : "0";
        p[i] = Integer.toString(color);
        Store.putS("base_ov", String.join(",", p));
    }

    /** 색 한 개를 보여주는 동그라미. color==0이면 빗금 원(없음) 또는 기본색 */
    static final class Dot extends View {
        private int color;
        private final int defColor;   // color==0일 때 채울 색 (0이면 빗금 원)
        private final int lineColor;
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

        Dot(Context c, Theme th, int color, int defColor) {
            super(c);
            this.color = color; this.defColor = defColor; this.lineColor = th.line;
        }

        void set(int c) { color = c; invalidate(); }

        @Override protected void onDraw(Canvas cv) {
            float cx = getWidth() / 2f, cy = getHeight() / 2f, r = Math.min(cx, cy) - Ui.dp(1);
            int fill = color != 0 ? color : defColor;
            p.setStyle(Paint.Style.FILL);
            if (fill != 0) {
                p.setColor(fill);
                cv.drawCircle(cx, cy, r, p);
            }
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(Ui.dp(1.5f));
            p.setColor(lineColor);
            if (fill == 0 || Color.red(fill) > 235 && Color.green(fill) > 235 && Color.blue(fill) > 235) cv.drawCircle(cx, cy, r, p);
            if (fill == 0) {
                p.setStrokeCap(Paint.Cap.ROUND);
                float d = r * 0.7071f;
                cv.drawLine(cx - d, cy + d, cx + d, cy - d, p);
            }
        }
    }

    static Dot dot(Context c, Theme th, int color, boolean forText) {
        return new Dot(c, th, color, forText ? th.fg : 0);
    }

    // ---------- 저장된 사용자 색 ----------
    private static List<Integer> customs() {
        List<Integer> l = new ArrayList<>();
        String s = Store.getS("custom_colors", "");
        if (!s.isEmpty()) for (String t : s.split(",")) { try { l.add(Integer.parseInt(t)); } catch (Exception ignored) { } }
        return l;
    }

    private static void saveCustoms(List<Integer> l) {
        StringBuilder sb = new StringBuilder();
        for (int c : l) { if (sb.length() > 0) sb.append(','); sb.append(c); }
        Store.putS("custom_colors", sb.toString());
    }

    // ---------- 선택 시트 ----------
    static void pick(Activity a, Theme th, int cur, boolean forText, Cb cb) {
        final int[] sel = {cur};
        LinearLayout root = new LinearLayout(a);
        root.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(th.card);
        float rr = Ui.dp(24);
        bg.setCornerRadii(new float[]{rr, rr, rr, rr, 0, 0, 0, 0});
        root.setBackground(bg);
        root.setPadding(Ui.dp(22), Ui.dp(20), Ui.dp(22), Ui.dp(14));
        final Dialog dlg = Ui.sheetDialog(a, th, root, 0.72f);

        ScrollView sv = new ScrollView(a);
        final LinearLayout box = new LinearLayout(a);
        box.setOrientation(LinearLayout.VERTICAL);
        sv.addView(box);
        root.addView(sv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        TextView close = Ui.text(a, "닫기", 17, th.todayStroke, true);
        close.setGravity(Gravity.END);
        close.setPadding(Ui.dp(10), Ui.dp(12), Ui.dp(10), Ui.dp(6));
        close.setOnClickListener(v -> dlg.dismiss());
        root.addView(close, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        final Runnable[] render = new Runnable[1];
        render[0] = () -> {
            box.removeAllViews();
            TextView t1 = Ui.text(a, "기본 색상", 16, th.sub, false);
            t1.setGravity(Gravity.CENTER);
            t1.setPadding(0, 0, 0, Ui.dp(14));
            box.addView(t1);
            final int[] bs = base(a);
            LinearLayout brow = new LinearLayout(a);
            for (int j = 0; j < 6; j++) {
                final int ix = j;
                int col = j < 5 ? bs[j] : 0;
                brow.addView(swatch(a, th, col, sel[0], forText, () -> { }, c -> { sel[0] = c; cb.run(c); render[0].run(); },
                        j < 5 ? () -> wheel(a, th, bs[ix], BASE5[ix], c -> {
                            setBase(ix, c);
                            sel[0] = c;
                            cb.run(c);
                            render[0].run();
                        }) : null), cell());
            }
            box.addView(brow);
            View ln = new View(a);
            ln.setBackgroundColor(th.line);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1);
            lp.setMargins(0, Ui.dp(12), 0, Ui.dp(12));
            box.addView(ln, lp);
            TextView t2 = Ui.text(a, "사용자 색상 (길게 누르면 편집)", 16, th.sub, false);
            t2.setGravity(Gravity.CENTER);
            t2.setPadding(0, 0, 0, Ui.dp(12));
            box.addView(t2);
            List<Integer> cs = customs();
            List<View> cells = new ArrayList<>();
            for (int k = 0; k < cs.size(); k++) {
                final int ix = k;
                cells.add(swatch(a, th, cs.get(k), sel[0], forText, () -> { }, c -> { sel[0] = c; cb.run(c); render[0].run(); },
                        () -> wheel(a, th, cs.get(ix), 0, c -> {
                            List<Integer> l2 = customs();
                            if (ix < l2.size()) l2.set(ix, c);
                            saveCustoms(l2);
                            sel[0] = c;
                            cb.run(c);
                            render[0].run();
                        })));
            }
            TextView plus = Ui.text(a, "+", 26, th.sub, false);
            plus.setGravity(Gravity.CENTER);
            GradientDrawable g = new GradientDrawable();
            g.setShape(GradientDrawable.OVAL);
            g.setColor(0);
            g.setStroke(Ui.dp(1.5f), th.line);
            plus.setBackground(g);
            plus.setOnClickListener(v -> wheel(a, th, 0xFFFFE600, 0, c -> {
                List<Integer> l2 = customs();
                l2.add(c);
                saveCustoms(l2);
                sel[0] = c;
                cb.run(c);
                render[0].run();
            }));
            cells.add(plus);
            for (int i = 0; i < cells.size(); i += 5) {
                LinearLayout row = new LinearLayout(a);
                for (int j = i; j < i + 5; j++) {
                    if (j < cells.size()) row.addView(cells.get(j), cell());
                    else row.addView(new View(a), cell());
                }
                box.addView(row);
            }
        };
        render[0].run();
        dlg.show();
    }

    private static LinearLayout.LayoutParams cell() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, Ui.dp(52), 1);
        lp.setMargins(Ui.dp(6), Ui.dp(6), Ui.dp(6), Ui.dp(6));
        return lp;
    }

    private static View swatch(Context c, Theme th, int color, int cur, boolean forText, Runnable unused, Cb pick, Runnable longPress) {
        Dot d = new Dot(c, th, color, 0);
        LinearLayout w = new LinearLayout(c);
        w.setGravity(Gravity.CENTER);
        boolean on = color == cur;
        w.setPadding(Ui.dp(on ? 0 : 3), Ui.dp(on ? 0 : 3), Ui.dp(on ? 0 : 3), Ui.dp(on ? 0 : 3));
        if (on) {
            GradientDrawable g = new GradientDrawable();
            g.setShape(GradientDrawable.OVAL);
            g.setColor(0);
            g.setStroke(Ui.dp(3), th.todayStroke);
            w.setBackground(g);
            w.setPadding(Ui.dp(4), Ui.dp(4), Ui.dp(4), Ui.dp(4));
        }
        w.addView(d, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        w.setOnClickListener(v -> pick.run(color));
        if (longPress != null) w.setOnLongClickListener(v -> { longPress.run(); return true; });
        return w;
    }

    // ---------- 색상환 ----------
    /** orig != 0이면 "원래 색" 버튼으로 되돌릴 수 있다 */
    static void wheel(Activity a, Theme th, int start, int orig, Cb done) {
        LinearLayout box = new LinearLayout(a);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setBackground(Ui.round(0xFF1E1E1E, 22));
        box.setPadding(Ui.dp(8), Ui.dp(8), Ui.dp(8), Ui.dp(10));
        final ColorPickView pv = new ColorPickView(a, start);
        box.addView(pv, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout bar = new LinearLayout(a);
        bar.setGravity(Gravity.END);
        final Dialog dlg = new Dialog(a, android.R.style.Theme_DeviceDefault_NoActionBar);
        TextView cancel = Ui.text(a, "취소", 17, 0xFF3D8BFF, true);
        cancel.setPadding(Ui.dp(22), Ui.dp(10), Ui.dp(22), Ui.dp(10));
        cancel.setOnClickListener(v -> dlg.dismiss());
        TextView ok = Ui.text(a, "확인", 17, 0xFF3D8BFF, true);
        ok.setPadding(Ui.dp(22), Ui.dp(10), Ui.dp(22), Ui.dp(10));
        ok.setOnClickListener(v -> { dlg.dismiss(); done.run(pv.color()); });
        if (orig != 0) {
            TextView reset = Ui.text(a, "원래 색", 17, 0xFF9E9E9E, true);
            reset.setPadding(Ui.dp(22), Ui.dp(10), Ui.dp(22), Ui.dp(10));
            reset.setOnClickListener(v -> { dlg.dismiss(); done.run(orig); });
            bar.addView(reset);
        }
        bar.addView(cancel);
        bar.addView(ok);
        box.addView(bar);
        dlg.setContentView(box);
        if (dlg.getWindow() != null) {
            dlg.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
            dlg.getWindow().setLayout((int) (a.getResources().getDisplayMetrics().widthPixels * 0.94f), ViewGroup.LayoutParams.WRAP_CONTENT);
        }
        dlg.show();
    }

    static final class ColorPickView extends View {
        private float h, s, v;
        private int target; // 0 없음, 1 색상환, 2 채도, 3 명도
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private float cx, cy, R, ringW, bx0, bx1, yS, yV;

        ColorPickView(Context c, int color) {
            super(c);
            float[] hsv = new float[3];
            Color.colorToHSV(color, hsv);
            h = hsv[0]; s = hsv[1]; v = hsv[2];
        }

        int color() { return Color.HSVToColor(new float[]{h, s, v}) | 0xFF000000; }

        @Override protected void onMeasure(int wSpec, int hSpec) {
            int w = MeasureSpec.getSize(wSpec);
            float ring = Math.min(w / 2f - Ui.dp(26), Ui.dp(130));
            setMeasuredDimension(w, (int) (Ui.dp(14) + ring * 2 + Ui.dp(100)));
        }

        private void layoutCalc() {
            float w = getWidth();
            R = Math.min(w / 2f - Ui.dp(26), Ui.dp(130));
            ringW = Ui.dp(26);
            cx = w / 2f;
            cy = Ui.dp(14) + R;
            bx0 = Ui.dp(26);
            bx1 = w - Ui.dp(26);
            yS = cy + R + Ui.dp(40);
            yV = yS + Ui.dp(46);
        }

        @Override protected void onDraw(Canvas cv) {
            layoutCalc();
            // 색상환
            p.setShader(new SweepGradient(cx, cy, new int[]{0xFFFF0000, 0xFFFFFF00, 0xFF00FF00, 0xFF00FFFF, 0xFF0000FF, 0xFFFF00FF, 0xFFFF0000}, null));
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(ringW);
            cv.drawCircle(cx, cy, R, p);
            p.setShader(null);
            // 가운데 미리보기
            p.setStyle(Paint.Style.FILL);
            p.setColor(color());
            cv.drawCircle(cx, cy, R * 0.45f, p);
            // 색상 손잡이
            double ang = Math.toRadians(h);
            float tx = cx + (float) Math.cos(ang) * R, ty = cy + (float) Math.sin(ang) * R;
            p.setColor(0xFF1E1E1E);
            cv.drawCircle(tx, ty, ringW * 0.78f, p);
            p.setColor(Color.HSVToColor(new float[]{h, 1f, 1f}));
            cv.drawCircle(tx, ty, ringW * 0.62f, p);
            // 채도 막대 (흰색 → 색)
            int full = Color.HSVToColor(new float[]{h, 1f, 1f});
            bar(cv, yS, 0xFFFFFFFF, full, s);
            // 명도 막대 (색 → 검정)
            int mid = Color.HSVToColor(new float[]{h, s, 1f});
            bar(cv, yV, mid, 0xFF000000, 1f - v);
        }

        private void bar(Canvas cv, float y, int c0, int c1, float pos) {
            p.setShader(new LinearGradient(bx0, y, bx1, y, c0, c1, Shader.TileMode.CLAMP));
            p.setStyle(Paint.Style.FILL);
            cv.drawRoundRect(new RectF(bx0, y - Ui.dp(3), bx1, y + Ui.dp(3)), Ui.dp(3), Ui.dp(3), p);
            p.setShader(null);
            float x = bx0 + (bx1 - bx0) * pos;
            p.setColor(0x55000000);
            cv.drawCircle(x, y, Ui.dp(17), p);
            p.setColor(color());
            cv.drawCircle(x, y, Ui.dp(12), p);
        }

        @Override public boolean onTouchEvent(MotionEvent e) {
            layoutCalc();
            float x = e.getX(), y = e.getY();
            if (e.getAction() == MotionEvent.ACTION_DOWN) {
                float dist = (float) Math.hypot(x - cx, y - cy);
                if (Math.abs(y - yS) < Ui.dp(26)) target = 2;
                else if (Math.abs(y - yV) < Ui.dp(26)) target = 3;
                else if (dist > R - ringW * 1.6f && dist < R + ringW * 1.6f) target = 1;
                else target = 0;
                getParent().requestDisallowInterceptTouchEvent(true);
            }
            if (target == 1) {
                double a = Math.toDegrees(Math.atan2(y - cy, x - cx));
                h = (float) ((a + 360) % 360);
            } else if (target == 2) {
                s = Math.max(0f, Math.min(1f, (x - bx0) / (bx1 - bx0)));
            } else if (target == 3) {
                v = 1f - Math.max(0f, Math.min(1f, (x - bx0) / (bx1 - bx0)));
            }
            invalidate();
            return true;
        }
    }
}
