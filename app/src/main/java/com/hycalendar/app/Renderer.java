package com.hycalendar.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

/** 월 달력을 Canvas에 그린다. 앱 화면과 위젯이 같이 쓴다. (오늘근무 스타일) */
final class Renderer {
    private Renderer() {}

    static final int SINGLE = 0, COUPLE = 1, FOUR = 2;
    private static final String[] DOW = {"일", "월", "화", "수", "목", "금", "토"};

    static final class Grid {
        float hdr, cw, ch; int rows, offset; YearMonth ym; boolean monStart;

        LocalDate dateAt(int row, int col) { return ym.atDay(1).plusDays((long) row * 7 + col - offset); }

        LocalDate cellAt(float x, float y) {
            if (y < hdr || cw <= 0 || ch <= 0) return null;
            int col = (int) (x / cw), row = (int) ((y - hdr) / ch);
            if (col < 0 || col > 6 || row < 0 || row >= rows) return null;
            return dateAt(row, col);
        }
    }

    static Grid grid(int w, int h, YearMonth ym, Opts o) {
        Grid g = new Grid();
        g.ym = ym; g.monStart = o.monStart;
        int dow = ym.atDay(1).getDayOfWeek().getValue();
        g.offset = o.monStart ? dow - 1 : dow % 7;
        g.rows = o.fixed6 ? 6 : (g.offset + ym.lengthOfMonth() + 6) / 7;
        g.cw = w / 7f;
        g.hdr = Math.min(h * 0.055f, g.cw * 0.42f);
        g.ch = (h - g.hdr) / g.rows;
        return g;
    }

    private static final class Line { String t; int color; Line(String t, int c) { this.t = t; this.color = c; } }

    static void draw(Context ctx, Canvas c, int w, int h, YearMonth ym, Opts o) {
        Theme th = o.theme;
        Grid g = grid(w, h, ym, o);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        if (o.drawBg) c.drawColor(th.bg);

        p.setTextAlign(Paint.Align.CENTER);
        p.setTextSize(g.hdr * 0.58f);
        for (int i = 0; i < 7; i++) {
            int d = o.monStart ? (i + 1) % 7 : i;
            p.setColor(d == 0 ? th.sun : d == 6 ? th.sat : th.fg);
            c.drawText(DOW[d], g.cw * i + g.cw / 2, g.hdr * 0.74f, p);
        }

        LocalDate today = LocalDate.now();
        for (int r = 0; r < g.rows; r++) {
            for (int col = 0; col < 7; col++) {
                LocalDate date = g.dateAt(r, col);
                boolean inMonth = date.getMonthValue() == ym.getMonthValue();
                float cx = col * g.cw, cy = g.hdr + r * g.ch;
                int save = -1;
                if (!inMonth) {
                    if (!o.widgetStyle) {
                        p.setStyle(Paint.Style.FILL);
                        p.setColor(Theme.blend(th.bg, th.fg, th.dark ? 0.07f : 0.04f));
                        c.drawRect(cx, cy, cx + g.cw, cy + g.ch, p);
                    }
                    save = c.saveLayerAlpha(cx, cy, cx + g.cw, cy + g.ch, 85);
                }
                drawCell(c, p, o, th, g, date, cx, cy, date.equals(today));
                if (save >= 0) c.restoreToCount(save);
            }
        }

        if (o.lines && !o.widgetStyle) {
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(Math.max(1f, g.cw * 0.008f));
            p.setColor(th.line);
            for (int r = 0; r <= g.rows; r++) c.drawLine(0, g.hdr + r * g.ch, w, g.hdr + r * g.ch, p);
            for (int k = 1; k < 7; k++) c.drawLine(k * g.cw, g.hdr, k * g.cw, h, p);
            p.setStyle(Paint.Style.FILL);
        }

        // 오늘 칸: 영역 전체를 파란 테두리로
        for (int r = 0; r < g.rows; r++) {
            for (int col = 0; col < 7; col++) {
                if (!g.dateAt(r, col).equals(today)) continue;
                float cx = col * g.cw, cy = g.hdr + r * g.ch;
                p.setStyle(Paint.Style.FILL);
                p.setColor(th.todayFill);
                c.drawRect(cx, cy, cx + g.cw, cy + g.ch, p);
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(Math.max(3f, g.cw * 0.03f));
                p.setColor(th.todayStroke);
                c.drawRect(cx + 1.5f, cy + 1.5f, cx + g.cw - 1.5f, cy + g.ch - 1.5f, p);
                p.setStyle(Paint.Style.FILL);
            }
        }
    }

    private static void drawCell(Canvas c, Paint p, Opts o, Theme th, Grid g, LocalDate date, float cx, float cy, boolean isToday) {
        float cw = g.cw, ch = g.ch, pad = cw * 0.07f;
        boolean ws = o.widgetStyle;
        String hol = o.holiday ? Holidays.name(date) : "";
        int realDow = date.getDayOfWeek().getValue() % 7;
        int dateColor = (!hol.isEmpty() || realDow == 0) ? th.sun : realDow == 6 ? th.sat : th.fg;

        float dateSz = cw * (ws ? 0.17f : 0.19f) * o.dateSize;
        p.setStyle(Paint.Style.FILL);
        p.setTypeface(Typeface.DEFAULT);
        float baseline = cy + pad * 0.7f + dateSz * 0.92f;

        if (!ws) {
            // 공휴일: 날짜 줄에 분홍 띠 + 이름
            if (!hol.isEmpty()) {
                p.setColor(th.band);
                c.drawRect(cx + 1, cy + 1, cx + cw - 1, baseline + dateSz * 0.28f, p);
            }
            p.setTextAlign(Paint.Align.LEFT);
            p.setTextSize(dateSz);
            p.setColor(dateColor);
            String ds = String.valueOf(date.getDayOfMonth());
            c.drawText(ds, cx + pad, baseline, p);
            if (!hol.isEmpty()) {
                float x0 = cx + pad + p.measureText(ds) + cw * 0.05f;
                float avail = cx + cw - pad * 0.6f - x0;
                float sz = dateSz * 0.72f;
                p.setTextSize(sz);
                float wd = p.measureText(hol);
                if (wd > avail && avail > 0) { sz = Math.max(dateSz * 0.4f, sz * avail / wd); p.setTextSize(sz); }
                p.setColor(th.sun);
                c.drawText(hol, x0, baseline, p);
            } else if (o.lunar > 0) {
                Lunar.Res lr = Lunar.of(date);
                if (o.lunar == 2 || lr.d == 1 || lr.d == 15) {
                    p.setTextAlign(Paint.Align.RIGHT);
                    p.setTextSize(dateSz * 0.62f);
                    p.setColor(th.sub);
                    c.drawText((lr.leap ? "윤" : "") + lr.m + "." + lr.d, cx + cw - pad * 0.8f, baseline - dateSz * 0.05f, p);
                }
            }
        } else {
            // 위젯: 날짜 가운데, 공휴일은 같은 줄에 작게
            String ds = String.format("%02d", date.getDayOfMonth());
            if (!hol.isEmpty()) ds = ds + " " + hol;
            else if (o.lunar > 0) {
                Lunar.Res lr = Lunar.of(date);
                if (o.lunar == 2 || lr.d == 1 || lr.d == 15) ds = ds + " " + lr.m + "." + lr.d;
            }
            p.setTextAlign(Paint.Align.CENTER);
            p.setTypeface(Typeface.DEFAULT_BOLD);
            float sz = dateSz;
            p.setTextSize(sz);
            float wd = p.measureText(ds);
            float avail = cw - pad;
            if (wd > avail) { sz = Math.max(dateSz * 0.45f, sz * avail / wd); p.setTextSize(sz); }
            p.setColor(dateColor);
            c.drawText(ds, cx + cw / 2, baseline, p);
            p.setTypeface(Typeface.DEFAULT);
        }

        float cursor = baseline + dateSz * 0.3f + o.gapDS * cw * 0.5f;
        float bottom = cy + ch - pad * 0.6f;

        // 근태
        List<Store.Use> uses = Store.usesOn(date);
        List<String> keys = new ArrayList<>();
        for (Store.Use u : uses) keys.add(u.key);
        boolean off = LeaveCore.isOff(keys);
        String offLabel = "휴가";
        for (String k : keys) {
            LeaveCore.Type t = LeaveCore.type(k);
            if (t != null && t.off && t.pool == null) { offLabel = t.label; break; }
        }

        List<int[]> rowsDef = new ArrayList<>();
        float baseChip;
        if (o.mode == SINGLE) { rowsDef.add(new int[]{o.me, 1}); baseChip = cw * 0.40f * o.shiftSize; }
        else if (o.mode == COUPLE) { rowsDef.add(new int[]{o.me, 1}); rowsDef.add(new int[]{o.mate, 2}); baseChip = cw * 0.36f * o.shiftSize; }
        else { for (int gr = 1; gr <= 4; gr++) rowsDef.add(new int[]{gr, gr == o.me ? 1 : gr == o.mate ? 2 : 0}); baseChip = cw * 0.25f * o.shiftSize; }

        float memoSz = cw * 0.165f * o.memoSize;
        float lineH = memoSz * 1.22f;
        float reserve = (o.showMemo && o.mode != FOUR) ? lineH * 2 : 0f;
        float rowGap = cw * 0.02f;
        float[] hs = new float[rowsDef.size()];
        float total = 0;
        for (int i = 0; i < hs.length; i++) {
            hs[i] = baseChip * ((o.mode == COUPLE && rowsDef.get(i)[1] == 2) ? o.mateScale : 1f);
            total += hs[i] + rowGap;
        }
        float avail = bottom - cursor - reserve;
        float scale = total > avail && avail > 0 ? Math.max(0.35f, avail / total) : 1f;
        for (int i = 0; i < hs.length; i++) {
            float chipH = hs[i] * scale;
            int[] rd = rowsDef.get(i);
            char type = ShiftCore.typeFor(rd[0], date);
            boolean leaveOff = rd[1] == 1 && off;
            int ring = o.mode != SINGLE ? (rd[1] == 1 ? th.meRing : rd[1] == 2 ? th.mateRing : 0) : 0;
            drawChip(c, p, th, cx + cw / 2, cursor, chipH, cw - 2 * pad, type, leaveOff, offLabel, ring, o.shape);
            cursor += chipH + rowGap;
        }

        if (!o.showMemo) return;
        cursor += o.gapSM * cw * 0.5f;
        float remaining = bottom - cursor;
        int maxLines = (int) Math.floor(remaining / lineH);
        if (maxLines <= 0) return;
        List<Line> items = new ArrayList<>();
        for (Store.Use u : uses) {
            LeaveCore.Type t = LeaveCore.type(u.key);
            if (t != null && !(t.off && t.pool != null && off)) items.add(new Line(t.label, th.orange));
        }
        String memo = Store.memo(date);
        if (!memo.isEmpty()) for (String s : memo.split("\n")) if (!s.trim().isEmpty()) items.add(new Line(s.trim(), th.fg));

        p.setTypeface(Typeface.DEFAULT);
        p.setTextSize(memoSz);
        p.setTextAlign(ws ? Paint.Align.CENTER : Paint.Align.LEFT);
        float maxW = cw - 2 * pad;
        List<String> texts = new ArrayList<>();
        List<Integer> cols = new ArrayList<>();
        for (Line ln : items) {
            for (String piece : wrap(p, ln.t, maxW)) { texts.add(piece); cols.add(ln.color); }
        }
        float tx = ws ? cx + cw / 2 : cx + pad;
        for (int i = 0; i < texts.size() && i < maxLines; i++) {
            String s = texts.get(i);
            if (i == maxLines - 1 && texts.size() > maxLines) s = cut(p, s + "…", maxW);
            p.setColor(cols.get(i));
            c.drawText(s, tx, cursor + (i + 1) * lineH - memoSz * 0.25f, p);
        }
    }

    private static void drawChip(Canvas c, Paint p, Theme th, float mx, float top, float h, float maxW,
                                 char type, boolean leaveOff, String offLabel, int ring, int shape) {
        p.setStyle(Paint.Style.FILL);
        p.setTextAlign(Paint.Align.CENTER);
        p.setTypeface(Typeface.DEFAULT_BOLD);
        float cy = top + h / 2f;
        if (leaveOff) {
            // 휴가 알약
            float pw = Math.min(maxW, h * 1.9f), ph = h * 0.82f;
            RectF r = new RectF(mx - pw / 2, cy - ph / 2, mx + pw / 2, cy + ph / 2);
            p.setColor(th.orange);
            c.drawRoundRect(r, ph / 2, ph / 2, p);
            p.setColor(0xFFFFFFFF);
            float sz = ph * 0.58f;
            p.setTextSize(sz);
            float wd = p.measureText(offLabel);
            if (wd > pw * 0.9f) { sz = sz * pw * 0.9f / wd; p.setTextSize(sz); }
            c.drawText(offLabel, mx, cy + sz * 0.35f, p);
        } else if (type == 'O') {
            p.setColor(th.offText);
            p.setTextSize(h * 0.62f);
            c.drawText("휴", mx, cy + h * 0.22f, p);
        } else {
            int color = th.shiftColor(type);
            float w = shape == 0 ? h : Math.min(maxW, h * 1.5f);
            RectF r = new RectF(mx - w / 2, top, mx + w / 2, top + h);
            p.setColor(color);
            if (shape == 0) c.drawOval(r, p);
            else if (shape == 1) c.drawRoundRect(r, h * 0.3f, h * 0.3f, p);
            else c.drawRect(r, p);
            p.setColor(Theme.onColor(color));
            p.setTextSize(h * 0.58f);
            c.drawText(String.valueOf(type), mx, cy + h * 0.2f, p);
        }
        if (ring != 0) {
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(Math.max(2f, h * 0.09f));
            p.setColor(ring);
            c.drawCircle(mx, cy, h * 0.5f + p.getStrokeWidth() * 0.2f, p);
            p.setStyle(Paint.Style.FILL);
        }
        p.setTypeface(Typeface.DEFAULT);
    }

    /** 글자 단위 줄바꿈 (한글은 띄어쓰기보다 글자 폭 기준이 읽기 좋다) */
    private static List<String> wrap(Paint p, String s, float maxW) {
        List<String> out = new ArrayList<>();
        int i = 0;
        while (i < s.length()) {
            int n = p.breakText(s, i, s.length(), true, maxW, null);
            if (n <= 0) n = 1;
            out.add(s.substring(i, i + n));
            i += n;
        }
        return out;
    }

    private static String cut(Paint p, String s, float maxW) {
        while (s.length() > 2 && p.measureText(s) > maxW) s = s.substring(0, s.length() - 2) + "…";
        return s;
    }
}
