package com.hycalendar.app;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** 로컬 저장소. 업데이트해도 SharedPreferences는 그대로 유지된다. */
final class Store {
    private Store() {}

    static final class Use {
        String id, key; LocalDate date; int who; boolean shared = true, remote = false; // who: 0 나, 1 배우자
    }

    private static SharedPreferences sp;
    private static List<Use> usesCache;

    static synchronized void init(Context c) {
        if (sp == null) sp = c.getApplicationContext().getSharedPreferences("hy", Context.MODE_PRIVATE);
    }

    static SharedPreferences prefs() { return sp; }

    static int getI(String k, int d) { return sp.getInt(k, d); }
    static float getF(String k, float d) { return sp.getFloat(k, d); }
    static boolean getB(String k, boolean d) { return sp.getBoolean(k, d); }
    static String getS(String k, String d) { return sp.getString(k, d); }
    static void putI(String k, int v) { sp.edit().putInt(k, v).apply(); }
    static void putF(String k, float v) { sp.edit().putFloat(k, v).apply(); }
    static void putB(String k, boolean v) { sp.edit().putBoolean(k, v).apply(); }
    static void putS(String k, String v) { sp.edit().putString(k, v).apply(); }

    // ---- 근태 ----
    private static synchronized List<Use> loadUses() {
        if (usesCache != null) return usesCache;
        List<Use> l = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(sp.getString("uses", "[]"));
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                Use u = new Use();
                u.id = o.getString("id"); u.key = o.getString("k"); u.date = LocalDate.parse(o.getString("d")); u.who = o.optInt("w", 0); u.shared = o.optBoolean("sh", true); u.remote = o.optBoolean("rm", false);
                l.add(u);
            }
        } catch (Exception ignored) { }
        usesCache = l;
        return l;
    }

    private static synchronized void saveUses() {
        JSONArray a = new JSONArray();
        try {
            for (Use u : usesCache) {
                JSONObject o = new JSONObject();
                o.put("id", u.id); o.put("k", u.key); o.put("d", u.date.toString()); o.put("w", u.who); o.put("sh", u.shared); o.put("rm", u.remote);
                a.put(o);
            }
        } catch (Exception ignored) { }
        sp.edit().putString("uses", a.toString()).apply();
    }

    static synchronized List<Use> allUses() { return new ArrayList<>(loadUses()); }

    static List<Use> usesOn(LocalDate d) { return usesOn(d, 0); }

    static synchronized List<Use> usesOn(LocalDate d, int who) {
        List<Use> r = new ArrayList<>();
        for (Use u : loadUses()) if (u.who == who && u.date.equals(d)) r.add(u);
        return r;
    }

    static List<String> useKeysOn(LocalDate d) { return useKeysOn(d, 0); }

    static synchronized List<String> useKeysOn(LocalDate d, int who) {
        List<String> r = new ArrayList<>();
        for (Use u : loadUses()) if (u.who == who && u.date.equals(d)) r.add(u.key);
        return r;
    }

    private static String balKey(String pool, int y, int who) { return (who == 0 ? "bal_" : "mbal_") + pool + "_" + y; }
    static double bal(String pool, int y, int who) { return sp.getFloat(balKey(pool, y, who), 0f); }
    static void setBal(String pool, int y, int who, double v) {
        sp.edit().putFloat(balKey(pool, y, who), (float) v).apply();
        Sync.pushBal(pool, y, who, v);
    }

    // ---- 서버 동기화용 (서버로 다시 올리지 않는다, 잔여는 건드리지 않는다) ----
    static void putBalLocal(String key, float v) { sp.edit().putFloat(key, v).apply(); }

    static synchronized void applyRemoteUse(String id, String key, LocalDate date, int who) {
        Use hit = null;
        for (Use x : loadUses()) if (x.id.equals(id)) hit = x;
        if (hit == null) { hit = new Use(); hit.id = id; loadUses().add(hit); }
        hit.key = key; hit.date = date; hit.who = who; hit.remote = true;
        saveUses();
    }

    static synchronized void removeRemoteUse(String id) {
        Use hit = null;
        for (Use x : loadUses()) if (x.id.equals(id)) hit = x;
        if (hit == null) return;
        loadUses().remove(hit);
        saveUses();
    }

    /** 반환: 차감 후 잔여(차감 안 하면 NaN) */
    static synchronized double addUse(LocalDate d, LeaveCore.Type t, int who) { return addUse(d, t, who, true); }

    static synchronized double addUse(LocalDate d, LeaveCore.Type t, int who, boolean shared) {
        Use u = new Use();
        u.shared = shared;
        u.who = who;
        u.id = Long.toString(System.currentTimeMillis(), 36) + Integer.toString((int) (Math.random() * 1000), 36);
        u.key = t.key; u.date = d;
        loadUses().add(u);
        saveUses();
        Sync.pushUse(u);
        if (t.pool != null) {
            int py = LeaveCore.pyear(t.pool, d);
            if (py >= LeaveCore.START) {
                double nb = bal(t.pool, py, who) - t.amt;
                setBal(t.pool, py, who, nb);
                return nb;
            }
        }
        return Double.NaN;
    }

    static synchronized void removeUse(String id) {
        Use hit = null;
        for (Use u : loadUses()) if (u.id.equals(id)) hit = u;
        if (hit == null) return;
        loadUses().remove(hit);
        saveUses();
        Sync.delUse(id);
        LeaveCore.Type t = LeaveCore.type(hit.key);
        if (t != null && t.pool != null) {
            int py = LeaveCore.pyear(t.pool, hit.date);
            if (py >= LeaveCore.START) setBal(t.pool, py, hit.who, bal(t.pool, py, hit.who) + t.amt);
        }
    }

    static synchronized double used(String pool, int y, int who) {
        double s = 0;
        for (Use u : loadUses()) {
            LeaveCore.Type t = LeaveCore.type(u.key);
            if (u.who == who && t != null && pool.equals(t.pool) && LeaveCore.pyear(pool, u.date) == y) s += t.amt;
        }
        return s;
    }

    // ---- 백업 ----
    static String exportJson() {
        JSONObject root = new JSONObject();
        try {
            for (Map.Entry<String, ?> e : sp.getAll().entrySet()) {
                Object v = e.getValue();
                JSONArray a = new JSONArray();
                if (v instanceof Boolean) { a.put("b"); a.put(v); }
                else if (v instanceof Integer) { a.put("i"); a.put(v); }
                else if (v instanceof Float) { a.put("f"); a.put(((Float) v).doubleValue()); }
                else if (v instanceof Long) { a.put("l"); a.put(v); }
                else { a.put("s"); a.put(String.valueOf(v)); }
                root.put(e.getKey(), a);
            }
        } catch (Exception ignored) { }
        return root.toString();
    }

    static synchronized boolean importJson(String json) {
        try {
            JSONObject root = new JSONObject(json);
            SharedPreferences.Editor ed = sp.edit();
            ed.clear();
            java.util.Iterator<String> it = root.keys();
            while (it.hasNext()) {
                String k = it.next();
                JSONArray a = root.getJSONArray(k);
                String t = a.getString(0);
                switch (t) {
                    case "b": ed.putBoolean(k, a.getBoolean(1)); break;
                    case "i": ed.putInt(k, a.getInt(1)); break;
                    case "f": ed.putFloat(k, (float) a.getDouble(1)); break;
                    case "l": ed.putLong(k, a.getLong(1)); break;
                    default: ed.putString(k, a.getString(1)); break;
                }
            }
            ed.commit();
            usesCache = null;
            Memos.reset();
            Alarms.reset();
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
