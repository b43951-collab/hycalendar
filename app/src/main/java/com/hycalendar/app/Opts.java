package com.hycalendar.app;

import android.content.Context;
import android.content.res.Configuration;

final class Opts {
    int mode;           // Renderer.SINGLE / COUPLE / FOUR
    Theme theme;
    int me = 1, mate = 4;
    float dateSize = 1f, shiftSize = 1f, mateScale = 0.8f, memoSize = 1f, gapDS = 0.1f, gapSM = 0.1f;
    int lunar = 1;      // 0 끔, 1 초하루·보름, 2 전체
    int shape = 0;      // 0 원, 1 둥근 사각, 2 사각
    int meTint, mateTint;
    boolean monStart, fixed6, lines = true, showMemo = true, holiday = true, drawBg = true, widgetStyle = false;

    static boolean systemDark(Context c) {
        return (c.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
    }

    static float f(String pfx, String k, float d) { return Store.getF(pfx + k, Store.getF(k, d)); }
    static int i(String pfx, String k, int d) { return Store.getI(pfx + k, Store.getI(k, d)); }
    static boolean b(String pfx, String k, boolean d) { return Store.getB(pfx + k, Store.getB(k, d)); }

    /** pfx: 앱 화면은 "", 위젯은 "w<id>_" */
    static Opts load(Context c, String pfx, int mode) {
        Store.init(c);
        Opts o = new Opts();
        o.mode = mode;
        o.widgetStyle = !pfx.isEmpty();
        o.me = Store.getI("grp_me", 1);
        o.mate = Store.getI("grp_mate", 4);
        o.dateSize = f(pfx, "s_date", 1f);
        o.shiftSize = f(pfx, "s_shift", 1f);
        o.mateScale = f("", "s_mate", 0.8f);
        o.memoSize = f(pfx, "s_memo", 1f);
        o.gapDS = f("", "s_gapds", 0.1f);
        o.gapSM = f("", "s_gapsm", 0.1f);
        o.lunar = i(pfx, "lunar", 1);
        o.shape = Store.getI("shape", 0);
        o.monStart = Store.getB("monstart", false);
        o.holiday = Store.getB("holiday", true);
        o.lines = pfx.isEmpty() && Store.getB("lines", true);
        o.showMemo = b(pfx, "showmemo", true);
        o.fixed6 = b(pfx, "fixed6", false);
        String tn = Store.getS("theme", "auto");
        int style = Store.getI(pfx + "bgstyle", 0);
        if (!pfx.isEmpty()) {
            if (style == 1) tn = "light";
            else if (style == 2) tn = "dark";
        }
        o.theme = Theme.get(tn, systemDark(c));
        o.meTint = Theme.tint(o.theme, Store.getI("c_me", 0));
        o.mateTint = Sync.partner().isEmpty() ? 0 : Theme.tint(o.theme, Store.getI("c_mate", 1));
        return o;
    }
}
