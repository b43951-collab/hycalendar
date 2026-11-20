package com.hycalendar.app;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.widget.RemoteViews;

import java.time.YearMonth;

/** 월 달력 위젯 공통 구현. 크기별로 하위 클래스만 다르다. */
public abstract class WidgetBase extends AppWidgetProvider {
    static final String ACTION_NAV = "com.hycalendar.app.WIDGET_NAV";

    @Override
    public void onUpdate(Context c, AppWidgetManager m, int[] ids) {
        Store.init(c);
        for (int id : ids) update(c, m, id);
        Alarms.schedule(c);
    }

    @Override
    public void onAppWidgetOptionsChanged(Context c, AppWidgetManager m, int id, Bundle o) {
        Store.init(c);
        update(c, m, id);
    }

    @Override
    public void onDeleted(Context c, int[] ids) {
        Store.init(c);
        android.content.SharedPreferences.Editor ed = Store.prefs().edit();
        for (int id : ids) {
            for (String k : Store.prefs().getAll().keySet()) if (k.startsWith("w" + id + "_")) ed.remove(k);
        }
        ed.apply();
    }

    @Override
    public void onReceive(Context c, Intent i) {
        Store.init(c);
        String a = i.getAction();
        if (ACTION_NAV.equals(a)) {
            int id = i.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, 0);
            int delta = i.getIntExtra("delta", 0);
            String k = "w" + id + "_off";
            Store.putI(k, delta == 0 ? 0 : Store.getI(k, 0) + delta);
            update(c, AppWidgetManager.getInstance(c), id);
            return;
        }
        if (Intent.ACTION_DATE_CHANGED.equals(a) || Intent.ACTION_TIME_CHANGED.equals(a) || Intent.ACTION_TIMEZONE_CHANGED.equals(a)) {
            updateAll(c);
            Alarms.schedule(c);
            return;
        }
        super.onReceive(c, i);
    }

    static void updateAll(Context c) {
        Store.init(c);
        AppWidgetManager m = AppWidgetManager.getInstance(c);
        Class<?>[] cls = {WidgetSmall.class, WidgetMedium.class, WidgetLarge.class};
        for (Class<?> k : cls) {
            for (int id : m.getAppWidgetIds(new ComponentName(c, k))) update(c, m, id);
        }
    }

    static Class<?> providerOf(Context c, AppWidgetManager m, int id) {
        android.appwidget.AppWidgetProviderInfo info = m.getAppWidgetInfo(id);
        if (info != null && info.provider != null) {
            String n = info.provider.getClassName();
            if (n.endsWith("WidgetSmall")) return WidgetSmall.class;
            if (n.endsWith("WidgetLarge")) return WidgetLarge.class;
        }
        return WidgetMedium.class;
    }

    static void update(Context c, AppWidgetManager m, int id) {
        try {
            doUpdate(c, m, id);
        } catch (Throwable t) {
            // 위젯 한 개 실패가 전체를 막지 않도록
        }
    }

    private static void doUpdate(Context c, AppWidgetManager m, int id) {
        String pfx = "w" + id + "_";
        int mode = Store.getI(pfx + "mode", Renderer.SINGLE);
        Opts o = Opts.load(c, pfx, mode);
        o.drawBg = false;
        int offset = Store.getI(pfx + "off", 0);
        YearMonth ym = YearMonth.now().plusMonths(offset);

        DisplayMetrics dm = c.getResources().getDisplayMetrics();
        Bundle opt = m.getAppWidgetOptions(id);
        int wDp = opt.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 250);
        int hDp = opt.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 250);
        if (wDp <= 0) wDp = 250;
        if (hDp <= 0) hDp = 250;
        float d = dm.density;
        int bw = Math.max(100, (int) ((wDp - 8) * d));
        int bh = Math.max(100, (int) ((hDp - 8 - 32) * d));
        // 비트맵 메모리 제한 대응
        double area = (double) bw * bh;
        double cap = 1_300_000d;
        if (area > cap) {
            double s = Math.sqrt(cap / area);
            bw = (int) (bw * s);
            bh = (int) (bh * s);
        }
        Bitmap bmp = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(bmp);
        Renderer.draw(c, cv, bw, bh, ym, o);

        RemoteViews rv = new RemoteViews(c.getPackageName(), R.layout.widget_month);
        rv.setImageViewBitmap(R.id.img, bmp);

        int alphaPct = Store.getI(pfx + "alpha", 100);
        int a = Math.max(0, Math.min(255, alphaPct * 255 / 100));
        rv.setInt(R.id.bg, "setColorFilter", o.theme.bg | 0xFF000000);
        rv.setInt(R.id.bg, "setImageAlpha", a);
        // 투명도가 낮을 때 글자색은 배경이 아니라 테마 기준
        rv.setTextViewText(R.id.title, ym.getYear() + ". " + ym.getMonthValue());
        rv.setTextColor(R.id.title, o.theme.fg);
        rv.setInt(R.id.prev, "setColorFilter", o.theme.fg);
        rv.setInt(R.id.next, "setColorFilter", o.theme.fg);
        rv.setInt(R.id.gear, "setColorFilter", o.theme.fg);
        rv.setInt(R.id.refresh, "setColorFilter", o.theme.fg);
        Intent cfg = new Intent(c, WidgetConfigActivity.class).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                .setData(android.net.Uri.parse("hycal://widget/" + id)).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        rv.setOnClickPendingIntent(R.id.gear, PendingIntent.getActivity(c, id + 5000, cfg, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
        rv.setOnClickPendingIntent(R.id.refresh, nav(c, m, id, 0));

        Intent open = new Intent(c, MainActivity.class).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP)
        open.putExtra("open_y", ym.getYear()).putExtra("open_m", ym.getMonthValue());  // 위젯에 보이던 달로 열기
        PendingIntent po = PendingIntent.getActivity(c, id, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        rv.setOnClickPendingIntent(R.id.img, po);
        rv.setOnClickPendingIntent(R.id.title, nav(c, m, id, 0));
        rv.setOnClickPendingIntent(R.id.prev, nav(c, m, id, -1));
        rv.setOnClickPendingIntent(R.id.next, nav(c, m, id, 1));
        m.updateAppWidget(id, rv);
    }

    private static PendingIntent nav(Context c, AppWidgetManager m, int id, int delta) {
        Intent i = new Intent(c, providerOf(c, m, id)).setAction(ACTION_NAV)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id).putExtra("delta", delta);
        return PendingIntent.getBroadcast(c, id * 10 + (delta + 1), i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
}
