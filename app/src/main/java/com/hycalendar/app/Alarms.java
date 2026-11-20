package com.hycalendar.app;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.RingtoneManager;
import android.net.Uri;

import org.json.JSONArray;
import org.json.JSONObject;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** 알람 여러 개. 근무(A/B/C/휴/휴가)·요일 반복, 전날/당일/다음날, 다시 울림, 자동 끄기, 진동, 알람음. 메모 알림도 같이 예약한다. */
public class Alarms extends BroadcastReceiver {
    static final String ACTION_RING = "com.hycalendar.app.RING";
    static final String ACTION_STOP = "com.hycalendar.app.STOP";
    static final int DAYS = 21;
    private static final String[] DOW = {"일", "월", "화", "수", "목", "금", "토"};

    static final class Item {
        int id;
        boolean on = true;
        int min = 330;
        String shifts = "";   // A B C O(휴) L(휴가)
        int dow;              // bit0=일 … bit6=토
        int dayOff;           // -1 전날, 0 당일, 1 다음날
        int autoOff = 5;      // 분, 0이면 안 함
        int snoozeMin;        // 0이면 다시 울림 없음
        int snoozeCnt = 1;
        String cheer = "";
        boolean vib = true;
        String sound = "";    // "" 기본 알람음, "none" 무음, 그 외 uri
    }

    private static List<Item> cache;

    static synchronized void reset() { cache = null; }

    static synchronized List<Item> items() {
        if (cache == null) load();
        List<Item> l = new ArrayList<>(cache);
        Collections.sort(l, (a, b) -> a.min - b.min);
        return l;
    }

    private static void load() {
        List<Item> l = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(Store.getS("alarms", "[]"));
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                Item it = new Item();
                it.id = o.getInt("id"); it.on = o.optBoolean("on", true); it.min = o.optInt("min", 330);
                it.shifts = o.optString("sh"); it.dow = o.optInt("dw"); it.dayOff = o.optInt("do");
                it.autoOff = o.optInt("ao", 5); it.snoozeMin = o.optInt("sn"); it.snoozeCnt = o.optInt("sc", 1);
                it.cheer = o.optString("ch"); it.vib = o.optBoolean("vb", true); it.sound = o.optString("so");
                l.add(it);
            }
        } catch (Exception ignored) { }
        cache = l;
        // 예전 A/B/C 알람 설정 이전
        if (!Store.getB("alarm_mig", false)) {
            Store.putB("alarm_mig", true);
            for (char t : new char[]{'A', 'B', 'C'}) {
                if (!Store.getB("al_" + t + "_on", false)) continue;
                Item it = new Item();
                it.id = nextId();
                it.min = Store.getI("al_" + t + "_min", t == 'A' ? 330 : t == 'B' ? 810 : 1290);
                it.shifts = String.valueOf(t);
                cache.add(it);
            }
            persist();
        }
    }

    private static int nextId() {
        int n = Store.getI("alarm_seq", 0) + 1;
        Store.putI("alarm_seq", n);
        return n;
    }

    static Item create() {
        Item it = new Item();
        it.id = 0;
        it.shifts = "A";
        return it;
    }

    static synchronized void save(Item it) {
        if (cache == null) load();
        if (it.id == 0) { it.id = nextId(); cache.add(it); }
        else {
            boolean found = false;
            for (int i = 0; i < cache.size(); i++) if (cache.get(i).id == it.id) { cache.set(i, it); found = true; }
            if (!found) cache.add(it);
        }
        persist();
    }

    static synchronized void delete(int id) {
        if (cache == null) load();
        for (int i = 0; i < cache.size(); i++) if (cache.get(i).id == id) { cache.remove(i); break; }
        persist();
    }

    static synchronized void setAll(boolean on) {
        if (cache == null) load();
        for (Item it : cache) it.on = on;
        persist();
    }

    private static void persist() {
        JSONArray a = new JSONArray();
        try {
            for (Item it : cache) {
                JSONObject o = new JSONObject();
                o.put("id", it.id); o.put("on", it.on); o.put("min", it.min); o.put("sh", it.shifts); o.put("dw", it.dow);
                o.put("do", it.dayOff); o.put("ao", it.autoOff); o.put("sn", it.snoozeMin); o.put("sc", it.snoozeCnt);
                o.put("ch", it.cheer); o.put("vb", it.vib); o.put("so", it.sound);
                a.put(o);
            }
        } catch (Exception ignored) { }
        Store.putS("alarms", a.toString());
    }

    // ---- 계산 ----
    /** d가 이 알람이 울릴 "근무 날짜"인가 */
    static boolean applies(Item it, LocalDate d) {
        if (it.dow != 0 && (it.dow & (1 << (d.getDayOfWeek().getValue() % 7))) != 0) return true;
        return !it.shifts.isEmpty() && it.shifts.indexOf(Memos.cat(d)) >= 0;
    }

    static long fireAt(Item it, LocalDate d) {
        return LocalDateTime.of(d.plusDays(it.dayOff), LocalTime.of(it.min / 60, it.min % 60))
                .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    static final class Next { long at; Item item; LocalDate day; }

    /** 켜져 있는 알람 중 가장 가까운 것 */
    static Next next(Context c) {
        Store.init(c);
        Next best = null;
        long now = System.currentTimeMillis();
        LocalDate today = LocalDate.now();
        for (Item it : items()) {
            if (!it.on) continue;
            Next n = nextOf(it, today, now);
            if (n != null && (best == null || n.at < best.at)) best = n;
        }
        return best;
    }

    static Next nextOf(Item it, LocalDate today, long now) {
        for (int off = -1; off < 60; off++) {
            LocalDate d = today.plusDays(off);
            if (!applies(it, d)) continue;
            long at = fireAt(it, d);
            if (at <= now) continue;
            Next n = new Next();
            n.at = at; n.item = it; n.day = d;
            return n;
        }
        return null;
    }

    static String clockShort(int min) { return String.format("%02d:%02d", min / 60, min % 60); }

    static String dowText(int mask) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 7; i++) if ((mask & (1 << i)) != 0) sb.append(DOW[i]);
        return sb.toString();
    }

    static String title(Item it) {
        List<String> p = new ArrayList<>();
        if (!it.shifts.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (char ch : it.shifts.toCharArray()) {
                if (sb.length() > 0) sb.append('·');
                sb.append(ch == 'O' ? "휴" : ch == 'L' ? "휴가" : String.valueOf(ch));
            }
            p.add(sb + " 근무");
        }
        if (it.dow != 0) p.add("매주 " + dowText(it.dow));
        return p.isEmpty() ? "반복 없음" : String.join("  ·  ", p);
    }

    // ---- 예약 ----
    private static PendingIntent pi(Context c, int code, Intent extra) {
        Intent i = extra != null ? extra : new Intent();
        i.setClass(c, Alarms.class).setAction(ACTION_RING);
        return PendingIntent.getBroadcast(c, code, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    static void schedule(Context c) {
        try {
            doSchedule(c);
        } catch (Throwable t) {
            // 알람 예약 실패가 앱을 멈추게 하지 않도록
        }
    }

    private static void setExact(AlarmManager am, long at, PendingIntent show, PendingIntent p) {
        try {
            am.setAlarmClock(new AlarmManager.AlarmClockInfo(at, show), p);
        } catch (Throwable e) {
            try { am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, p); } catch (Throwable ignored) { }
        }
    }

    private static void doSchedule(Context c) {
        Store.init(c);
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        // 이전에 예약한 것 전부 취소
        String old = Store.getS("sched_codes", "");
        if (!old.isEmpty()) {
            for (String s : old.split(",")) {
                try { am.cancel(pi(c, Integer.parseInt(s), null)); } catch (Exception ignored) { }
            }
        }
        StringBuilder codes = new StringBuilder();
        LocalDate today = LocalDate.now();
        long now = System.currentTimeMillis();
        PendingIntent show = PendingIntent.getActivity(c, 0, new Intent(c, MainActivity.class), PendingIntent.FLAG_IMMUTABLE);
        String[] dowN = DOW;
        for (Item it : items()) {
            if (!it.on) continue;
            for (int off = -1; off <= DAYS; off++) {
                LocalDate d = today.plusDays(off);
                if (!applies(it, d)) continue;
                int base = it.id * 1000 + (off + 1) * 10;
                int times = 1 + (it.snoozeMin > 0 ? Math.min(it.snoozeCnt, 8) : 0);
                for (int k = 0; k < times; k++) {
                    long at = fireAt(it, d) + (long) k * it.snoozeMin * 60000L;
                    if (at <= now) continue;
                    Intent ex = new Intent()
                            .putExtra("kind", "alarm").putExtra("nid", base + k).putExtra("base", base)
                            .putExtra("title", title(it) + " 알람")
                            .putExtra("text", it.cheer.isEmpty() ? d.getMonthValue() + "/" + d.getDayOfMonth() + "(" + dowN[d.getDayOfWeek().getValue() % 7] + ") " + shiftWord(d) + " · 출근 준비하세요" : it.cheer)
                            .putExtra("vib", it.vib).putExtra("sound", it.sound).putExtra("auto", it.autoOff);
                    setExact(am, at, show, pi(c, base + k, ex));
                    codes.append(base + k).append(',');
                }
            }
        }
        // 메모 알림
        int n = 0;
        for (Memos.Memo m : Memos.all()) {
            if (m.notify < 0) continue;
            for (int off = 0; off <= DAYS && n < 300; off++) {
                LocalDate d = today.plusDays(off);
                if (!Memos.applies(m, d)) continue;
                long at = LocalDateTime.of(d, LocalTime.of(m.notify / 60, m.notify % 60)).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
                if (at <= now) continue;
                int code = 900000 + n++;
                String body = m.text.replace('\n', ' ');
                Intent ex = new Intent().putExtra("kind", "memo").putExtra("nid", code)
                        .putExtra("title", m.label.isEmpty() ? "메모 알림" : m.label).putExtra("text", body);
                setExact(am, at, show, pi(c, code, ex));
                codes.append(code).append(',');
            }
        }
        Store.putS("sched_codes", codes.length() > 0 ? codes.substring(0, codes.length() - 1) : "");
    }

    private static String shiftWord(LocalDate d) {
        char t = Memos.cat(d);
        return t == 'O' ? "휴무" : t == 'L' ? "휴가" : t + "근무";
    }

    @Override
    public void onReceive(Context c, Intent i) {
        Store.init(c);
        String a = i.getAction();
        if (ACTION_RING.equals(a)) {
            ring(c, i);
        } else if (ACTION_STOP.equals(a)) {
            stop(c, i);
        } else {
            // 부팅·앱 업데이트·시간 변경 시 다시 예약
            schedule(c);
            WidgetBase.updateAll(c);
        }
    }

    private static void stop(Context c, Intent i) {
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        int base = i.getIntExtra("base", -1), nid = i.getIntExtra("nid", -1);
        if (nm != null && nid >= 0) nm.cancel(nid);
        if (am != null && base >= 0) for (int k = 1; k <= 9; k++) am.cancel(pi(c, base + k, null));
    }

    private static void ring(Context c, Intent i) {
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;
        boolean alarm = "alarm".equals(i.getStringExtra("kind"));
        boolean vib = i.getBooleanExtra("vib", true);
        String sound = i.getStringExtra("sound");
        if (sound == null) sound = "";
        String chId = alarm ? "al_" + (vib ? "v" : "n") + Integer.toHexString(sound.hashCode()) : "memo_notice";
        if (nm.getNotificationChannel(chId) == null) {
            NotificationChannel ch = new NotificationChannel(chId, alarm ? "출근 알람" : "메모 알림", NotificationManager.IMPORTANCE_HIGH);
            if (alarm) {
                if ("none".equals(sound)) ch.setSound(null, null);
                else {
                    Uri snd = sound.isEmpty() ? RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM) : Uri.parse(sound);
                    if (snd == null) snd = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
                    ch.setSound(snd, new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build());
                }
                ch.enableVibration(vib);
                if (vib) ch.setVibrationPattern(new long[]{0, 600, 300, 600, 300, 600});
            }
            nm.createNotificationChannel(ch);
        }
        int nid = i.getIntExtra("nid", (int) (System.currentTimeMillis() % 100000));
        PendingIntent open = PendingIntent.getActivity(c, 0, new Intent(c, MainActivity.class), PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder b = new Notification.Builder(c, chId)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle(i.getStringExtra("title") == null ? "우리 근무달력" : i.getStringExtra("title"))
                .setContentText(i.getStringExtra("text"))
                .setStyle(new Notification.BigTextStyle().bigText(i.getStringExtra("text")))
                .setContentIntent(open)
                .setAutoCancel(true);
        int auto = i.getIntExtra("auto", 0);
        if (alarm) {
            if (auto > 0) b.setTimeoutAfter(auto * 60000L);
            Intent st = new Intent(c, Alarms.class).setAction(ACTION_STOP)
                    .putExtra("nid", nid).putExtra("base", i.getIntExtra("base", -1));
            b.addAction(new Notification.Action.Builder(0, "끄기",
                    PendingIntent.getBroadcast(c, 800000 + (nid % 100000), st, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE)).build());
            b.setCategory(Notification.CATEGORY_ALARM);
        }
        nm.notify(nid, b.build());
    }
}
