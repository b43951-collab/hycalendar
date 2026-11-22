package com.hycalendar.app;

import android.graphics.Color;

final class Theme {
    int bg, fg, sub, line, sun, sat, todayFill, todayStroke, meRing, mateRing, a, b, c, off, leave;
    int page, card, soft, band, offText, orange, rowMe, rowMate;
    boolean dark;

    static final String[] NAMES = {"auto", "light", "dark", "black", "midnight", "ocean", "forest", "rose", "sand"};
    static final String[] LABELS = {"자동", "라이트", "다크", "블랙", "미드나이트", "오션", "포레스트", "로즈", "샌드"};

    static Theme get(String name, boolean systemDark) {
        if ("auto".equals(name)) name = systemDark ? "dark" : "light";
        String[] p;
        switch (name) {
            case "dark": p = new String[]{"1E1E1E", "EEEEEE", "9A9A9A", "3A3A3A"}; break;
            case "black": p = new String[]{"000000", "F2F2F2", "8A8A8A", "2A2A2A"}; break;
            case "midnight": p = new String[]{"0F1830", "E6ECFF", "8FA0C8", "26335A"}; break;
            case "ocean": p = new String[]{"EAF6FB", "0B3A4F", "5F8FA3", "C5E1EC"}; break;
            case "forest": p = new String[]{"EFF6EC", "1F3A25", "6A8A70", "CFE2CA"}; break;
            case "rose": p = new String[]{"FFF1F3", "4A1F2A", "A06B76", "F3CDD4"}; break;
            case "sand": p = new String[]{"F7F1E3", "3E3322", "8F8467", "E2D8BC"}; break;
            default: p = new String[]{"FFFFFF", "111111", "777777", "E3E3E3"}; break;
        }
        Theme t = new Theme();
        t.bg = Color.parseColor("#" + p[0]);
        t.fg = Color.parseColor("#" + p[1]);
        t.sub = Color.parseColor("#" + p[2]);
        t.line = Color.parseColor("#" + p[3]);
        int lum = (Color.red(t.bg) * 299 + Color.green(t.bg) * 587 + Color.blue(t.bg) * 114) / 1000;
        t.dark = lum < 110;
        t.sun = Color.parseColor(t.dark ? "#FF6B6B" : "#D32F2F");
        t.sat = Color.parseColor(t.dark ? "#6FA8FF" : "#1565C0");
        t.todayStroke = Color.parseColor("#2979FF");
        t.todayFill = Color.argb(45, 41, 121, 255);
        t.meRing = Color.parseColor("#2979FF");
        t.mateRing = Color.parseColor("#FF7043");
        t.a = Color.parseColor("#FFD400");
        t.b = Color.parseColor("#5B87E8");
        t.c = Color.parseColor(t.dark ? "#8A8A8F" : "#3D3D3D");
        t.off = Color.parseColor("#8E8E93");
        t.offText = Color.parseColor(t.dark ? "#FF7A93" : "#E04A6A");
        t.orange = Color.parseColor("#E8600A");
        t.band = Color.parseColor(t.dark ? "#4A2A33" : "#FCE4E8");
        t.rowMe = Color.parseColor(t.dark ? "#3A3720" : "#FFF8D8");
        t.rowMate = Color.parseColor(t.dark ? "#40291F" : "#FFE9DB");
        t.card = t.bg;
        t.page = blend(t.bg, t.fg, t.dark ? 0.06f : 0.05f);
        t.soft = blend(t.bg, t.fg, t.dark ? 0.14f : 0.08f);
        t.leave = Color.parseColor(t.dark ? "#81C784" : "#2E7D32");
        return t;
    }

    int shiftColor(char type) {
        switch (type) {
            case 'A': return a;
            case 'B': return b;
            case 'C': return c;
            default: return off;
        }
    }

    static final int[] PALETTE = {0xFFFFD400, 0xFFFF8A3D, 0xFF4CAF50, 0xFF2979FF, 0xFFFF4F81, 0xFF8E5CF7, 0xFF9E9E9E, 0xFF00BCD4, 0};

    /** 나/배우자 줄 배경색 (idx: PALETTE 번호, 0이면 없음) */
    static int tint(Theme t, int idx) {
        if (idx < 0 || idx >= PALETTE.length || PALETTE[idx] == 0) return 0;
        return blend(t.bg, PALETTE[idx], t.dark ? 0.15f : 0.10f);
    }

    static int blend(int a, int b, float t) {
        return Color.rgb((int) (Color.red(a) * (1 - t) + Color.red(b) * t),
                (int) (Color.green(a) * (1 - t) + Color.green(b) * t),
                (int) (Color.blue(a) * (1 - t) + Color.blue(b) * t));
    }

    static int onColor(int bg) {
        int l = (Color.red(bg) * 299 + Color.green(bg) * 587 + Color.blue(bg) * 114) / 1000;
        return l > 150 ? Color.parseColor("#222222") : Color.WHITE;
    }
}
