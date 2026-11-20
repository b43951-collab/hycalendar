package com.hycalendar.app;

import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/** 메모: 하루에 여러 개, 기간(시작~종료), 반복(근무/요일/매월), 글자색·배경색, 할 일, 알림. */
final class Memos {
    private Memos() {}

    static final class Memo {
        String id = "", text = "", url = "", label = "";
        int color = -1, bg = -1;           // Theme.PALETTE 번호, -1 기본
        boolean todo, done, allDay = true;
        LocalDate start = LocalDate.now(), end = null; // end==null: 반복 메모는 종료 없음, 일반 메모는 하루
        int sMin = 540, eMin = 600;        // 하루 종일이 아닐 때 시간
        int notify = -1;                   // 알림 시각(하루 중 분), -1 없음
        String repShift = "";              // 근무에 반복: A B C O(휴) L(휴가)
        int repDow;                        // 요일에 반복: bit0=일 … bit6=토
        boolean repMonth;                  // 날짜에 반복: 매월 같은 날

        boolean repeats() { return !repShift.isEmpty() || repDow != 0 || repMonth; }
    }

    private static List<Memo> cache;

    static synchronized void reset() { cache = null; }

    static synchronized List<Memo> all() {
        if (cache == null) load();
        return new ArrayList<>(cache);
    }

    private static void load() {
        SharedPreferences sp = Store.prefs();
        List<Memo> l = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(sp.getString("memos", "[]"));
            for (int i = 0; i < a.length(); i++) l.add(fromJson(a.getJSONObject(i)));
        } catch (Exception ignored) { }
        cache = l;
        migrateOld(sp);
    }

    /** 예전 방식(날짜당 메모 한 덩어리, 키 m_날짜)을 새 구조로 옮긴다. */
    private static void migrateOld(SharedPreferences sp) {
        boolean changed = false;
        SharedPreferences.Editor ed = sp.edit();
        for (Map.Entry<String, ?> e : new ArrayList<>(sp.getAll().entrySet())) {
            String k = e.getKey();
            if (!k.startsWith("m_") || !(e.getValue() instanceof String)) continue;
            LocalDate d;
            try { d = LocalDate.parse(k.substring(2)); } catch (Exception ex) { continue; }
            for (String line : ((String) e.getValue()).split("\n")) {
                if (line.trim().isEmpty()) continue;
                Memo m = new Memo();
                m.id = newId();
                m.text = line.trim();
                m.start = d;
                cache.add(m);
            }
            ed.remove(k);
            changed = true;
        }
        if (changed) { ed.apply(); persist(); }
    }

    private static String newId() { return Long.toString(System.currentTimeMillis(), 36) + Integer.toString((int) (Math.random() * 100000), 36); }

    static synchronized void save(Memo m) {
        if (cache == null) load();
        if (m.id.isEmpty()) m.id = newId();
        boolean found = false;
        for (int i = 0; i < cache.size(); i++) if (cache.get(i).id.equals(m.id)) { cache.set(i, m); found = true; }
        if (!found) cache.add(m);
        persist();
    }

    static synchronized void delete(String id) {
        if (cache == null) load();
        for (int i = 0; i < cache.size(); i++) if (cache.get(i).id.equals(id)) { cache.remove(i); break; }
        persist();
    }

    private static void persist() {
        JSONArray a = new JSONArray();
        try { for (Memo m : cache) a.put(toJson(m)); } catch (Exception ignored) { }
        Store.prefs().edit().putString("memos", a.toString()).apply();
    }

    private static JSONObject toJson(Memo m) throws Exception {
        JSONObject o = new JSONObject();
        o.put("id", m.id); o.put("t", m.text); o.put("u", m.url); o.put("l", m.label);
        o.put("c", m.color); o.put("bg", m.bg); o.put("td", m.todo); o.put("dn", m.done); o.put("ad", m.allDay);
        o.put("s", m.start.toString()); o.put("e", m.end == null ? "" : m.end.toString());
        o.put("sm", m.sMin); o.put("em", m.eMin); o.put("n", m.notify);
        o.put("rs", m.repShift); o.put("rd", m.repDow); o.put("rm", m.repMonth);
        return o;
    }

    private static Memo fromJson(JSONObject o) throws Exception {
        Memo m = new Memo();
        m.id = o.getString("id"); m.text = o.optString("t"); m.url = o.optString("u"); m.label = o.optString("l");
        m.color = o.optInt("c", -1); m.bg = o.optInt("bg", -1); m.todo = o.optBoolean("td"); m.done = o.optBoolean("dn");
        m.allDay = o.optBoolean("ad", true);
        m.start = LocalDate.parse(o.getString("s"));
        String e = o.optString("e");
        m.end = e.isEmpty() ? null : LocalDate.parse(e);
        m.sMin = o.optInt("sm", 540); m.eMin = o.optInt("em", 600); m.notify = o.optInt("n", -1);
        m.repShift = o.optString("rs"); m.repDow = o.optInt("rd"); m.repMonth = o.optBoolean("rm");
        return m;
    }

    /** 내 근무 기준 분류: A B C 휴(O) 휴가(L) */
    static char cat(LocalDate d) {
        if (LeaveCore.isOff(Store.useKeysOn(d))) return 'L';
        return ShiftCore.typeFor(Store.getI("grp_me", 1), d);
    }

    static boolean applies(Memo m, LocalDate d) {
        if (!m.repeats()) {
            LocalDate e = m.end == null ? m.start : m.end;
            return !d.isBefore(m.start) && !d.isAfter(e);
        }
        if (d.isBefore(m.start)) return false;
        if (m.end != null && d.isAfter(m.end)) return false;
        if (d.equals(m.start)) return true;
        if (m.repDow != 0 && (m.repDow & (1 << (d.getDayOfWeek().getValue() % 7))) != 0) return true;
        if (m.repMonth && d.getDayOfMonth() == m.start.getDayOfMonth()) return true;
        if (!m.repShift.isEmpty() && m.repShift.indexOf(cat(d)) >= 0) return true;
        return false;
    }

    static List<Memo> forDate(LocalDate d) {
        List<Memo> r = new ArrayList<>();
        for (Memo m : all()) if (applies(m, d)) r.add(m);
        return r;
    }

    /** 전체보기용: 시작일 순 */
    static List<Memo> sorted() {
        List<Memo> l = all();
        Collections.sort(l, (a, b) -> a.start.compareTo(b.start));
        return l;
    }
}
