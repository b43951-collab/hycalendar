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

/** 월 달력을 Canvas에 그린다. 앱 화면과 위젯이 같이 쓴다. */
final class Renderer {
    private Renderer() {}

    static final int SINGLE = 0, COUPLE = 1, FOUR = 2;
    private static final String[] DOW = {"일", "월", "화", "수", "목", "금", "토"};

    static final class Grid {
        float hdr, cw, ch; int rows, offset; YearMonth ym; boolean monStart;

        LocalDate cellAt(float x, float y) {
            if (y < hdr || cw <= 0 || ch <= 0) return null;
            int col = (int) (x / cw), row = (int) ((y - hdr) / ch);
            if (col < 0 || col > 6 || row < 0 || row >= rows) return null;
            int day = row * 7 + col - offset + 1;
            if (day < 1 || day > ym.lengthOfMonth()) return null;
            return ym.atDay(day);
        }
    }

    static Grid grid(int w, int h, YearMonth ym, Opts o) {
        Grid g = new Grid();
        g.ym = ym; g.monStart = o.monStart;
        int dow = ym.atDay(1).getDayOfWeek().getValue(); // 월=1 .. 일=7
        g.offset = o.monStart ? dow - 1 : dow % 7;
        g.rows = o.fixed6 ? 6 : (g.offset + ym.lengthOfMonth() + 6) / 7;
        g.cw = w / 7f;
        g.hdr = Math.min(h * 0.06f, g.cw * 0.45f);
        g.ch = (h - g.hdr) / g.rows;
        return g;
    }

    private static final class Line { String t; int color; Line(String t, int c) { this.t = t; this.color = c; } }

    static void draw(Context ctx, Canvas c, int w, int h, YearMonth ym, Opts o) {
        Theme th = o.theme;
        Grid g = grid(w, h, ym, o);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        if (o.drawBg) c.drawColor(th.bg);

        // 요일 헤더
        p.setTextAlign(Paint.Align.CENTER);
        p.setTextSize(g.hdr * 0.62f);
        for (int i = 0; i < 7; i++) {
            int d = o.monStart ? (i + 1) % 7 : i;
            p.setColor(d == 0 ? th.sun : d == 6 ? th.sat : th.sub);
            c.drawText(DOW[d], g.cw * i + g.cw / 2, g.hdr * 0.75f, p);
        }

        LocalDate today = LocalDate.now();
        for (int day = 1; day <= ym.lengthOfMonth(); day++) {
            LocalDate date = ym.atDay(day);
            int idx = g.offset + day - 1;
            float cx = (idx % 7) * g.cw, cy = g.hdr + (idx / 7) * g.ch;
            drawCell(ctx, c, p, o, th, g, date, cx, cy, date.equals(today), idx % 7);
        }

        if (o.lines) {
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(Math.max(1f, g.cw * 0.01f));
            p.setColor(th.line);
            for (int r = 0; r <= g.rows; r++) c.drawLine(0, g.hdr + r * g.ch, w, g.hdr + r * g.ch, p);
            p.setStyle(Paint.Style.FILL);
        }
    }

    private static void drawCell(Context ctx, Canvas c, Paint p, Opts o, Theme th, Grid g, LocalDate date,
                                 float cx, float cy, boolean isToday, int col) {
        float cw = g.cw, ch = g.ch, pad = cw * 0.05f;
        String hol = o.holiday ? Holidays.name(date) : "";
        int realDow = date.getDayOfWeek().getValue() % 7;
        int dateColor = (!hol.isEmpty() || realDow == 0) ? th.sun : realDow == 6 ? th.sat : th.fg;

        if (isToday) {
            p.setStyle(Paint.Style.FILL);
            p.setColor(th.todayFill);
            c.drawRect(cx + 1, cy + 1, cx + cw - 1, cy + ch - 1, p);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(Math.max(3f, cw * 0.035f));
            p.setColor(th.todayStroke);
            c.drawRect(cx + 2, cy + 2, cx + cw - 2, cy + ch - 2, p);
            p.setStyle(Paint.Style.FILL);
        }

        // 날짜 숫자
        float dateSz = cw * 0.2f * o.dateSize;
        p.setStyle(Paint.Style.FILL);
        p.setTypeface(Typeface.DEFAULT);
        p.setTextAlign(Paint.Align.CENTER);
        p.setTextSize(dateSz);
        p.setColor(dateColor);
        float baseline = cy + pad + dateSz * 0.9f;
        c.drawText(String.valueOf(date.getDayOfMonth()), cx + cw / 2, baseline, p);

        // 음력
        if (o.lunar > 0) {
            Lunar.Res lr = Lunar.of(date);
            if (o.lunar == 2 || lr.d == 1 || lr.d == 15) {
                p.setTextAlign(Paint.Align.LEFT);
                p.setTextSize(dateSz * 0.45f);
                p.setColor(th.sub);
                String s = (lr.leap ? "윤" : "") + lr.m + "." + lr.d;
                c.drawText(s, cx + pad, baseline - dateSz * 0.35f, p);
                p.setTextAlign(Paint.Align.CENTER);
            }
        }

        float cursor = baseline + dateSz * 0.12f + o.gapDS * cw * 0.5f;
        float bottom = cy + ch - pad;

        // 근태 반영
        List<Store.Use> uses = Store.usesOn(date);
        List<String> keys = new ArrayList<>();
        for (Store.Use u : uses) keys.add(u.key);
        boolean off = LeaveCore.isOff(keys);
        String offLabel = null;
        for (String k : keys) {
            LeaveCore.Type t = LeaveCore.type(k);
            if (t != null && t.off) { offLabel = t.shortLabel; break; }
        }
        if (off && offLabel == null) offLabel = "연";

        // 근무 칩
        List<int[]> rowsDef = new ArrayList<>(); // {group, isMe(1)/isMate(2)/0}
        float baseChip;
        if (o.mode == SINGLE) { rowsDef.add(new int[]{o.me, 1}); baseChip = cw * 0.42f * o.shiftSize; }
        else if (o.mode == COUPLE) { rowsDef.add(new int[]{o.me, 1}); rowsDef.add(new int[]{o.mate, 2}); baseChip = cw * 0.38f * o.shiftSize; }
        else { for (int gr = 1; gr <= 4; gr++) rowsDef.add(new int[]{gr, gr == o.me ? 1 : gr == o.mate ? 2 : 0}); baseChip = cw * 0.26f * o.shiftSize; }

        float memoSz = cw * 0.15f * o.memoSize;
        float lineH = memoSz * 1.18f;
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
            String label = type == 'O' ? "휴" : String.valueOf(type);
            int color = th.shiftColor(type);
            if (rd[1] == 1 && off) { label = offLabel; color = th.off; }
            drawChip(c, p, o, th, cx + cw / 2, cursor, chipH, cw - 2 * pad, label, color, rd[1] == 1 ? th.meRing : rd[1] == 2 ? th.mateRing : 0, o.mode != SINGLE && rd[1] != 0);
            cursor += chipH + rowGap;
        }

        // 메모 / 공휴일 / 근태 줄
        if (!o.showMemo && hol.isEmpty()) return;
        cursor += o.gapSM * cw * 0.5f;
        float remaining = bottom - cursor;
        int maxLines = (int) Math.floor(remaining / lineH);
        if (maxLines <= 0) return;
        List<Line> lines = new ArrayList<>();
        if (!hol.isEmpty()) lines.add(new Line(hol, th.sun));
        if (o.showMemo) {
            for (Store.Use u : uses) {
                LeaveCore.Type t = LeaveCore.type(u.key);
                if (t != null) lines.add(new Line(t.label, th.leave));
            }
            String memo = Store.memo(date);
            if (!memo.isEmpty()) for (String s : memo.split("\n")) if (!s.trim().isEmpty()) lines.add(new Line(s.trim(), th.fg));
        }
        p.setTextAlign(Paint.Align.LEFT);
        p.setTextSize(memoSz);
        p.setTypeface(Typeface.DEFAULT);
        float maxW = cw - 2 * pad;
        for (int i = 0; i < lines.size() && i < maxLines; i++) {
            Line ln = lines.get(i);
            p.setColor(ln.color);
            c.drawText(ellipsize(p, ln.t, maxW), cx + pad, cursor + (i + 1) * lineH - memoSz * 0.22f, p);
        }
    }

    private static void drawChip(Canvas c, Paint p, Opts o, Theme th, float mx, float top, float h, float maxW,
                                 String label, int color, int ring, boolean showRing) {
        float w = o.shape == 0 ? h : Math.min(maxW, h * 1.6f);
        RectF r = new RectF(mx - w / 2, top, mx + w / 2, top + h);
        p.setStyle(Paint.Style.FILL);
        p.setColor(color);
        if (o.shape == 0) c.drawOval(r, p);
        else if (o.shape == 1) c.drawRoundRect(r, h * 0.3f, h * 0.3f, p);
        else c.drawRect(r, p);
        if (showRing && ring != 0) {
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(Math.max(2f, h * 0.1f));
            p.setColor(ring);
            RectF r2 = new RectF(r.left - 1, r.top - 1, r.right + 1, r.bottom + 1);
            if (o.shape == 0) c.drawOval(r2, p);
            else if (o.shape == 1) c.drawRoundRect(r2, h * 0.3f, h * 0.3f, p);
            else c.drawRect(r2, p);
            p.setStyle(Paint.Style.FILL);
        }
        p.setColor(Theme.onColor(color));
        p.setTypeface(Typeface.DEFAULT_BOLD);
        p.setTextAlign(Paint.Align.CENTER);
        p.setTextSize(h * 0.6f);
        c.drawText(label, mx, top + h * 0.5f + h * 0.21f, p);
        p.setTypeface(Typeface.DEFAULT);
    }

    private static String ellipsize(Paint p, String s, float maxW) {
        if (p.measureText(s) <= maxW) return s;
        int n = p.breakText(s, true, maxW, null);
        if (n <= 1) return s.substring(0, Math.min(1, s.length()));
        int cut = Math.max(1, n - 1);
        return s.substring(0, cut) + "…";
    }
}
