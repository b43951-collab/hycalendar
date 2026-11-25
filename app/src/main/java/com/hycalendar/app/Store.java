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

    private static boolean lockedMode;   // 잠금 해제 전(재부팅 직후)에 임시로 연 상태

    /**
     * 저장소를 '기기 보호 저장소'에 둔다. 재부팅 후 잠금을 풀기 전에도 알람을 다시 예약하고 울리려면 앱 데이터를 읽을 수 있어야 하기 때문이다.
     * 예전 저장소(잠금 해제 후에만 열림)의 데이터는 잠금이 풀린 첫 실행 때 한 번 통째로 옮긴다. 옮기지 못하면 예전 방식 그대로 쓴다.
     */
    static synchronized void init(Context c) {
        Context app = c.getApplicationContext();
        if (sp != null && !lockedMode) { migrateGrant(); return; }
        try {
            Context dp = app.createDeviceProtectedStorageContext();
            android.os.UserManager um = (android.os.UserManager) app.getSystemService(Context.USER_SERVICE);
            boolean unlocked = um == null || um.isUserUnlocked();
            SharedPreferences meta = dp.getSharedPreferences("hy_meta", Context.MODE_PRIVATE);
            boolean done = meta.getBoolean("mig_done", false);
            if (!done && unlocked) {
                if (dp.moveSharedPreferencesFrom(app, "hy")) { meta.edit().putBoolean("mig_done", true).commit(); done = true; }
            }
            if (done) { sp = dp.getSharedPreferences("hy", Context.MODE_PRIVATE); lockedMode = false; }
            else if (unlocked) { sp = app.getSharedPreferences("hy", Context.MODE_PRIVATE); lockedMode = false; }   // 이전 실패: 예전 방식
            else { sp = dp.getSharedPreferences("hy_locked", Context.MODE_PRIVATE); lockedMode = true; }          // 아직 못 옮긴 상태로 잠금 해제 전: 임시
        } catch (Throwable t) {
            if (sp == null || lockedMode) { sp = app.getSharedPreferences("hy", Context.MODE_PRIVATE); lockedMode = false; }
        }
        if (!lockedMode) migrateGrant();
    }

    /**
     * 예전에는 입력칸 값이 사용 때마다 줄어드는 '잔여'였다. 이제 입력칸은 그해 '발생' 개수로 고정이고
     * 잔여 = 발생 - 사용으로 계산한다. 예전 값(잔여)에 사용량을 더해 발생으로 한 번만 바꾼다. (내 값만, 배우자 값은 배우자 폰이 올려 준다)
     */
    static synchronized void migrateGrant() {
        try {
            if (sp.getBoolean("grant_mig1", false)) return;
            List<String> keys = new ArrayList<>();
            for (String k : sp.getAll().keySet()) if (k.startsWith("bal_")) keys.add(k);
            for (String k : keys) {
                int us = k.lastIndexOf('_');
                if (us <= 4) continue;
                String pool = k.substring(4, us);
                int y;
                try { y = Integer.parseInt(k.substring(us + 1)); } catch (Exception e) { continue; }
                if (y < LeaveCore.START) continue;
                double grant = sp.getFloat(k, 0f) + used(pool, y, 0);
                setBal(pool, y, 0, grant);
            }
            sp.edit().putBoolean("grant_mig1", true).apply();
        } catch (Throwable ignored) { }
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
    /** 잔여 = 발생(입력값) - 사용 */
    static double remain(String pool, int y, int who) { return bal(pool, y, who) - used(pool, y, who); }
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
            if (py >= LeaveCore.START) return remain(t.pool, py, who);
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
    /** 이 폰에만 의미 있는 상태(로그인·연결·권한 안내·위젯·알람 진행 상태 등). 백업에 넣지 않고, 복원해도 덮어쓰지 않는다. */
    private static boolean deviceKey(String k) {
        switch (k) {
            case "uid": case "email": case "cid": case "code": case "partner": case "is_spouse":
            case "synced_cid": case "synced_pt": case "mcount": case "scount": case "grp_mate":
            case "perm_next": case "perm_guide_ver": case "notif_asked": case "login_later":
            case "srv_bk": case "srv_bk_ms": case "auto_bk": case "app_ver": case "purged_mate_uses":
            case "sched_codes": case "al_state": case "beta_upd": case "upd_chk_ms":
                return true;
            default:
                return k.matches("w\\d+_.*");
        }
    }

    static String exportJson() {
        JSONObject root = new JSONObject();
        try {
            for (Map.Entry<String, ?> e : sp.getAll().entrySet()) {
                if (deviceKey(e.getKey())) continue;
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
            if (root.length() == 0) return false;   // 내용이 없는 파일로 현재 데이터를 지워 버리지 않는다
            SharedPreferences.Editor ed = sp.edit();
            ed.remove("al_state");   // 알람의 '오늘 종료/다시 울림' 기록은 복원한 알람과 맞지 않으니 비운다
            for (String ek : new java.util.ArrayList<>(sp.getAll().keySet())) if (!deviceKey(ek)) ed.remove(ek);   // 기기 상태(로그인·연결 등)는 지우지 않는다
            java.util.Iterator<String> it = root.keys();
            while (it.hasNext()) {
                String k = it.next();
                if (deviceKey(k)) continue;   // 예전 백업에 들어 있던 기기 상태도 무시
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
            migrateGrant();
            Memos.reset();
            Alarms.reset();
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
