package com.hycalendar.app;

import android.app.Activity;
import android.app.AlarmManager;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.media.AudioManager;
import android.os.Build;
import android.os.PowerManager;
import android.provider.Settings;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** 알람 진단: 알람이 왜 안 울리는지 볼 수 있는 정보를 한 화면에 모은다 (복사해서 게시판에 올릴 수 있다) */
final class DiagUi {
    private DiagUi() {}

    static String report(Activity a) {
        StringBuilder sb = new StringBuilder();
        SimpleDateFormat f = new SimpleDateFormat("M/d a h:mm:ss", Locale.KOREA);
        sb.append("앱 v").append(Updater.currentName(a)).append(" (code ").append(Updater.currentCode(a)).append(")")
                .append(Updater.beta() ? " · 시험 버전 받는 중" : "").append("\n");
        sb.append("기기 ").append(Build.MANUFACTURER).append(" ").append(Build.MODEL).append(" · 안드로이드 ").append(Build.VERSION.RELEASE)
                .append(" (API ").append(Build.VERSION.SDK_INT).append(")\n");
        sb.append("저장소: ").append(Store.storageMode()).append("\n\n");

        sb.append("[알람 상태]\n");
        try {
            Alarms.Next n = Alarms.next(a);
            sb.append("다음 알람: ").append(n == null ? "없음" : new SimpleDateFormat("M월 d일(E) a h:mm", Locale.KOREA).format(new Date(n.at))).append("\n");
        } catch (Throwable t) { sb.append("다음 알람: 확인 실패\n"); }
        int cnt = 0;
        String sc = Store.getS("sched_codes", "");
        if (!sc.isEmpty()) cnt = sc.split(",").length;
        sb.append("걸어 둔 예약: ").append(cnt).append("개\n");
        long exp = 0;
        try { exp = Long.parseLong(Store.getS("exp_at", "0")); } catch (Exception ignored) { }
        sb.append("가장 가까운 예약: ").append(exp > 0 ? f.format(new Date(exp)) : "없음").append("\n");
        long last = 0;
        try { last = Long.parseLong(Store.getS("last_ring_ms", "0")); } catch (Exception ignored) { }
        sb.append("마지막으로 울린 때: ").append(last > 0 ? f.format(new Date(last)) : "기록 없음").append("\n\n");

        sb.append("[권한·기기 설정]\n");
        try {
            for (Perms.Row r : Perms.rows(a)) sb.append(r.ok ? "✔ " : "✖ ").append(r.title).append("\n");
        } catch (Throwable ignored) { }
        try {
            AlarmManager am = (AlarmManager) a.getSystemService(Context.ALARM_SERVICE);
            if (Build.VERSION.SDK_INT >= 31 && am != null) sb.append(am.canScheduleExactAlarms() ? "✔ " : "✖ ").append("정확한 알람\n");
        } catch (Throwable ignored) { }
        try {
            PowerManager pm = (PowerManager) a.getSystemService(Context.POWER_SERVICE);
            if (pm != null) sb.append(pm.isIgnoringBatteryOptimizations(a.getPackageName()) ? "✔ " : "✖ ").append("배터리 사용 제한 없음\n");
        } catch (Throwable ignored) { }
        try { sb.append(Settings.canDrawOverlays(a) ? "✔ " : "✖ ").append("다른 앱 위에 표시\n"); } catch (Throwable ignored) { }
        try {
            AudioManager au = (AudioManager) a.getSystemService(Context.AUDIO_SERVICE);
            if (au != null) {
                int v = au.getStreamVolume(AudioManager.STREAM_ALARM), mx = au.getStreamMaxVolume(AudioManager.STREAM_ALARM);
                sb.append(v > 0 ? "✔ " : "✖ ").append("알람 볼륨 ").append(v).append("/").append(mx).append(v == 0 ? " (소리가 안 나요)" : "").append("\n");
            }
        } catch (Throwable ignored) { }

        sb.append("\n[최근 기록]\n");
        try {
            JSONArray log = new JSONArray(Store.getS("diag_log", "[]"));
            if (log.length() == 0) sb.append("아직 없어요\n");
            for (int i = log.length() - 1; i >= Math.max(0, log.length() - 10); i--) {
                JSONObject o = log.getJSONObject(i);
                sb.append(f.format(new Date(o.getLong("t")))).append("  ").append(o.getString("e")).append("\n");
            }
        } catch (Throwable ignored) { }
        return sb.toString();
    }

    static void show(Activity a, Theme th) {
        final String text = report(a);
        TextView tv = new TextView(a);
        tv.setText(text);
        tv.setTextSize(13);
        tv.setTextColor(th.fg);
        tv.setTextIsSelectable(true);
        tv.setPadding(Ui.dp(20), Ui.dp(12), Ui.dp(20), Ui.dp(4));
        ScrollView sv = new ScrollView(a);
        sv.addView(tv);
        new AlertDialog.Builder(a).setTitle("알람 진단").setView(sv)
                .setPositiveButton("복사", (d, w) -> {
                    try {
                        ((ClipboardManager) a.getSystemService(Context.CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("알람 진단", text));
                        Toast.makeText(a, "복사했어요. 게시판에 붙여넣어 제보해 주세요", Toast.LENGTH_LONG).show();
                    } catch (Throwable ignored) { }
                })
                .setNegativeButton("닫기", null).show();
    }
}
