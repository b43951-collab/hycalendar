package com.txtviewer.app;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.widget.RemoteViews;

import org.json.JSONArray;

import java.util.ArrayList;

/** 홈 화면 명언 위젯: 저장한 명언을 하나씩 돌아가며 보여준다 (탭하면 다음 명언) */
public class QuoteWidget extends AppWidgetProvider {
    static final String ACTION_NEXT = "com.txtviewer.app.QUOTE_NEXT";

    static SharedPreferences prefs(Context c) { return c.getSharedPreferences("txt", Context.MODE_PRIVATE); }

    static ArrayList<String> load(Context c) {
        ArrayList<String> r = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(prefs(c).getString("quotes", "[]"));
            for (int i = 0; i < a.length(); i++) { String s = a.getString(i); if (!s.trim().isEmpty()) r.add(s); }
        } catch (Exception ignored) {}
        return r;
    }

    static void save(Context c, ArrayList<String> list) {
        JSONArray a = new JSONArray();
        for (String s : list) a.put(s);
        prefs(c).edit().putString("quotes", a.toString()).apply();
        refresh(c);
    }

    /** 다음 명언으로 넘긴다 */
    static void advance(Context c) {
        int n = load(c).size();
        SharedPreferences sp = prefs(c);
        int idx = n == 0 ? 0 : (sp.getInt("qidx", 0) + 1) % n;
        sp.edit().putInt("qidx", idx).putLong("qlast", System.currentTimeMillis()).apply();
    }

    static void refresh(Context c) {
        AppWidgetManager m = AppWidgetManager.getInstance(c);
        int[] ids = m.getAppWidgetIds(new ComponentName(c, QuoteWidget.class));
        if (ids != null && ids.length > 0) render(c, m, ids);
    }

    static void render(Context c, AppWidgetManager m, int[] ids) {
        ArrayList<String> q = load(c);
        SharedPreferences sp = prefs(c);
        int idx = q.isEmpty() ? 0 : Math.min(sp.getInt("qidx", 0), q.size() - 1);
        RemoteViews rv = new RemoteViews(c.getPackageName(), R.layout.widget_quote);
        rv.setTextViewText(R.id.q_text, q.isEmpty() ? "나의소설 앱 → 설정 → 명언 위젯에서\n자주 읽을 명언을 추가해 주세요" : q.get(idx));
        rv.setTextViewText(R.id.q_idx, q.isEmpty() ? "" : (idx + 1) + " / " + q.size());
        Intent i = new Intent(c, QuoteWidget.class).setAction(ACTION_NEXT);
        rv.setOnClickPendingIntent(R.id.q_root, PendingIntent.getBroadcast(c, 0, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
        m.updateAppWidget(ids, rv);
    }

    @Override
    public void onUpdate(Context c, AppWidgetManager m, int[] ids) {
        // 주기 갱신: 정해 둔 간격이 지났으면 다음 명언으로
        SharedPreferences sp = prefs(c);
        long gap = sp.getInt("qint", 30) * 60_000L;
        long last = sp.getLong("qlast", 0);
        if (last == 0) sp.edit().putLong("qlast", System.currentTimeMillis()).apply();
        else if (System.currentTimeMillis() - last >= gap - 60_000L) advance(c);
        render(c, m, ids);
    }

    @Override
    public void onReceive(Context c, Intent intent) {
        if (ACTION_NEXT.equals(intent.getAction())) { advance(c); refresh(c); return; }
        super.onReceive(c, intent);
    }
}
