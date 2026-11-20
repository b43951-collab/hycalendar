package com.hycalendar.app;

import android.app.AlarmManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.RingtoneManager;
import android.net.Uri;
import android.app.Notification;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

/** 출근 알람. 휴무(검진·교육·장기근속·연차 종일 등 포함)인 날은 울리지 않는다. */
public class Alarms extends BroadcastReceiver {
    static final String ACTION_RING = "com.hycalendar.app.RING";
    static final String CHANNEL = "shift_alarm";
    static final int DAYS = 21;
    static final char[] TYPES = {'A', 'B', 'C'};

    static int minutes(char t) {
        int def = t == 'A' ? 330 : t == 'B' ? 810 : 1290;
        return Store.getI("al_" + t + "_min", def);
    }

    static boolean enabled(char t) { return Store.getB("al_" + t + "_on", false); }

    /** 앞으로 울릴 알람 목록 (설정 화면 표시용) */
    static java.util.List<String> upcoming(Context c, int n) {
        Store.init(c);
        java.util.List<String> r = new java.util.ArrayList<>();
        int me = Store.getI("grp_me", 1);
        LocalDate today = LocalDate.now();
        String[] dow = {"일", "월", "화", "수", "목", "금", "토"};
        long now = System.currentTimeMillis();
        for (int off = 0; off < 60 && r.size() < n; off++) {
            LocalDate d = today.plusDays(off);
            char t = ShiftCore.typeFor(me, d);
            if (t == 'O' || !enabled(t) || LeaveCore.isOff(Store.useKeysOn(d))) continue;
            int mins = minutes(t);
            long at = LocalDateTime.of(d, java.time.LocalTime.of(mins / 60, mins % 60)).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
            if (at <= now) continue;
            r.add(d.getMonthValue() + "/" + d.getDayOfMonth() + " (" + dow[d.getDayOfWeek().getValue() % 7] + ")  " + t + "근무  " + String.format("%02d:%02d", mins / 60, mins % 60));
        }
        return r;
    }

    private static PendingIntent pi(Context c, int code, String text) {
        Intent i = new Intent(c, Alarms.class).setAction(ACTION_RING).putExtra("text", text).putExtra("code", code);
        return PendingIntent.getBroadcast(c, code, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    static void schedule(Context c) {
        try {
            doSchedule(c);
        } catch (Throwable t) {
            // 알람 예약 실패가 앱을 멈추게 하지 않도록
        }
    }

    private static void doSchedule(Context c) {
        Store.init(c);
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        int me = Store.getI("grp_me", 1);
        LocalDate today = LocalDate.now();
        long now = System.currentTimeMillis();
        PendingIntent show = PendingIntent.getActivity(c, 0, new Intent(c, MainActivity.class), PendingIntent.FLAG_IMMUTABLE);
        for (int off = 0; off < DAYS; off++) {
            LocalDate d = today.plusDays(off);
            PendingIntent p = pi(c, 1000 + off, "");
            am.cancel(p);
            char t = ShiftCore.typeFor(me, d);
            if (t == 'O' || !enabled(t)) continue;
            if (LeaveCore.isOff(Store.useKeysOn(d))) continue;
            int mins = minutes(t);
            long at = LocalDateTime.of(d, java.time.LocalTime.of(mins / 60, mins % 60)).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
            if (at <= now) continue;
            String name = t == 'A' ? "A조 근무" : t == 'B' ? "B조 근무" : "C조 근무";
            PendingIntent p2 = pi(c, 1000 + off, "오늘 " + name + " (" + String.format("%02d:%02d", mins / 60, mins % 60) + ")");
            try {
                am.setAlarmClock(new AlarmManager.AlarmClockInfo(at, show), p2);
            } catch (Throwable e) {
                try { am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, p2); } catch (Throwable ignored) { }
            }
        }
    }

    @Override
    public void onReceive(Context c, Intent i) {
        Store.init(c);
        if (ACTION_RING.equals(i.getAction())) {
            ring(c, i.getStringExtra("text"));
        } else {
            // 부팅·시간 변경 시 다시 예약
            schedule(c);
            WidgetBase.updateAll(c);
        }
    }

    private static void ring(Context c, String text) {
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;
        Uri snd = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
        if (snd == null) snd = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
        NotificationChannel ch = new NotificationChannel(CHANNEL, "출근 알람", NotificationManager.IMPORTANCE_HIGH);
        ch.setSound(snd, new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build());
        ch.enableVibration(true);
        ch.setVibrationPattern(new long[]{0, 600, 300, 600, 300, 600});
        nm.createNotificationChannel(ch);
        PendingIntent open = PendingIntent.getActivity(c, 0, new Intent(c, MainActivity.class), PendingIntent.FLAG_IMMUTABLE);
        Notification n = new Notification.Builder(c, CHANNEL)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle("우리 근무달력")
                .setContentText(text == null || text.isEmpty() ? "출근 시간입니다" : text)
                .setContentIntent(open)
                .setAutoCancel(true)
                .build();
        nm.notify((int) (System.currentTimeMillis() % 100000), n);
    }
}
