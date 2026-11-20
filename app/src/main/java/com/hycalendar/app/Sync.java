package com.hycalendar.app;

import android.content.Context;

import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseNetworkException;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthUserCollisionException;
import com.google.firebase.firestore.DocumentChange;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.firestore.WriteBatch;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 서버(Firebase) 동기화. google-services.json 없이 빌드되면 configured()가 false이고 모든 호출이 아무 일도 하지 않는다.
 * 로컬(SharedPreferences)이 항상 화면의 기준이고, 서버 변경은 로컬로 반영(apply*)하며 로컬 변경은 즉시 서버로 올린다.
 */
final class Sync {
    private Sync() {}

    interface Done { void run(String err); }   // err == null 이면 성공
    interface Changed { void run(); }

    private static Context app;
    private static Changed changed;
    private static boolean started;
    private static final List<ListenerRegistration> regs = new ArrayList<>();
    private static ListenerRegistration partnerReg;

    static void init(Context c, Changed ch) {
        app = c.getApplicationContext();
        changed = ch;
    }

    static boolean configured() {
        try { return app != null && !FirebaseApp.getApps(app).isEmpty(); } catch (Throwable t) { return false; }
    }

    static String uid() {
        try {
            if (!configured()) return "";
            com.google.firebase.auth.FirebaseUser u = FirebaseAuth.getInstance().getCurrentUser();
            return u == null ? "" : u.getUid();
        } catch (Throwable t) { return ""; }
    }

    static boolean loggedIn() { return !uid().isEmpty(); }
    static String cid() { return Store.getS("cid", ""); }
    static String partner() { return Store.getS("partner", ""); }
    static boolean active() { return loggedIn() && !cid().isEmpty(); }

    private static FirebaseFirestore db() { return FirebaseFirestore.getInstance(); }
    private static DocumentReference couple() { return db().collection("couples").document(cid()); }
    private static DocumentReference userDoc(String uid) { return db().collection("users").document(uid); }

    private static void fire() { if (changed != null) { try { changed.run(); } catch (Throwable ignored) { } } }

    static String emailOf(String nick) {
        StringBuilder sb = new StringBuilder();
        for (byte b : nick.getBytes(StandardCharsets.UTF_8)) sb.append(String.format("%02x", b));
        return sb.append("@hycal.app").toString();
    }

    private static String errText(Exception e) {
        if (e instanceof FirebaseNetworkException) return "인터넷에 연결할 수 없어요";
        String m = e == null ? "" : String.valueOf(e.getMessage());
        if (m.contains("PERMISSION_DENIED")) return "권한이 없어요 (서버 규칙을 확인하세요)";
        return "실패했어요: " + (m.length() > 80 ? m.substring(0, 80) : m);
    }

    // ---------- 로그인 / 가입 ----------
    static void signIn(String nick, String pw, Done cb) {
        if (!configured()) { cb.run("서버가 아직 설정되지 않았어요"); return; }
        final String em = emailOf(nick);
        final FirebaseAuth auth = FirebaseAuth.getInstance();
        auth.signInWithEmailAndPassword(em, pw)
                .addOnSuccessListener(r -> afterAuth(nick, cb))
                .addOnFailureListener(e -> auth.createUserWithEmailAndPassword(em, pw)
                        .addOnSuccessListener(r2 -> afterAuth(nick, cb))
                        .addOnFailureListener(e2 -> cb.run(e2 instanceof FirebaseAuthUserCollisionException
                                ? "이미 사용 중인 닉네임이거나 비밀번호가 달라요" : errText(e2))));
    }

    private static void afterAuth(final String nick, final Done cb) {
        final String uid = uid();
        userDoc(uid).get().addOnSuccessListener(doc -> {
            if (doc.exists()) {
                String n = doc.getString("nick");
                Store.putS("nick", n == null || n.isEmpty() ? nick : n);
                String c = doc.getString("cid");
                Store.putS("cid", c == null ? "" : c);
            } else {
                Store.putS("nick", nick);
                Map<String, Object> m = new HashMap<>();
                m.put("nick", nick);
                m.put("grp", (long) Store.getI("grp_me", 1));
                userDoc(uid).set(m, SetOptions.merge());
            }
            Store.putS("uid", uid);
            if (!cid().isEmpty()) {
                couple().get().addOnSuccessListener(cd -> {
                    String code = cd.getString("code");
                    Store.putS("code", code == null ? "" : code);
                    start();
                    cb.run(null);
                }).addOnFailureListener(e -> { start(); cb.run(null); });
            } else {
                cb.run(null);
            }
        }).addOnFailureListener(e -> cb.run(errText(e)));
    }

    static void signOut() {
        stop();
        try { if (configured()) FirebaseAuth.getInstance().signOut(); } catch (Throwable ignored) { }
        Store.putS("uid", "");
        Store.putS("cid", "");
        Store.putS("partner", "");
        Store.putS("partner_nick", "");
        Store.putS("code", "");
        Store.putS("synced_cid", "");
        Store.putS("synced_pt", "");
    }

    // ---------- 부부 연결 ----------
    static void createCouple(Done cb) {
        if (!loggedIn()) { cb.run("먼저 로그인하세요"); return; }
        final String uid = uid();
        String chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 6; i++) sb.append(chars.charAt((int) (Math.random() * chars.length())));
        final String code = sb.toString();
        final DocumentReference cd = db().collection("couples").document();
        WriteBatch b = db().batch();
        Map<String, Object> cm = new HashMap<>();
        List<String> mem = new ArrayList<>();
        mem.add(uid);
        cm.put("members", mem);
        cm.put("code", code);
        b.set(cd, cm);
        Map<String, Object> km = new HashMap<>();
        km.put("cid", cd.getId());
        b.set(db().collection("codes").document(code), km);
        Map<String, Object> um = new HashMap<>();
        um.put("cid", cd.getId());
        b.set(userDoc(uid), um, SetOptions.merge());
        b.commit().addOnSuccessListener(v -> {
            Store.putS("cid", cd.getId());
            Store.putS("code", code);
            Store.putS("partner", "");
            start();
            cb.run(null);
        }).addOnFailureListener(e -> cb.run(errText(e)));
    }

    static void joinCouple(String codeIn, Done cb) {
        if (!loggedIn()) { cb.run("먼저 로그인하세요"); return; }
        final String uid = uid();
        final String code = codeIn.trim().toUpperCase();
        db().collection("codes").document(code).get().addOnSuccessListener(kd -> {
            if (!kd.exists() || kd.getString("cid") == null) { cb.run("코드를 찾을 수 없어요"); return; }
            final String cid = kd.getString("cid");
            db().collection("couples").document(cid).get().addOnSuccessListener(cd -> {
                @SuppressWarnings("unchecked")
                List<String> mem = (List<String>) cd.get("members");
                if (mem == null) { cb.run("코드를 찾을 수 없어요"); return; }
                if (mem.size() >= 2 && !mem.contains(uid)) { cb.run("이미 두 사람이 연결된 코드예요"); return; }
                WriteBatch b = db().batch();
                b.update(db().collection("couples").document(cid), "members", FieldValue.arrayUnion(uid));
                Map<String, Object> um = new HashMap<>();
                um.put("cid", cid);
                b.set(userDoc(uid), um, SetOptions.merge());
                b.commit().addOnSuccessListener(v -> {
                    Store.putS("cid", cid);
                    Store.putS("code", code);
                    start();
                    cb.run(null);
                }).addOnFailureListener(e -> cb.run(errText(e)));
            }).addOnFailureListener(e -> cb.run(errText(e)));
        }).addOnFailureListener(e -> cb.run(errText(e)));
    }

    // ---------- 실시간 반영 ----------
    static synchronized void start() {
        if (started || !active()) return;
        started = true;
        try {
            final DocumentReference cd = couple();
            regs.add(cd.addSnapshotListener((snap, e) -> {
                if (e != null || snap == null || !snap.exists()) return;
                @SuppressWarnings("unchecked")
                List<String> mem = (List<String>) snap.get("members");
                String code = snap.getString("code");
                if (code != null) Store.putS("code", code);
                String me = uid(), pt = "";
                if (mem != null) for (String m : mem) if (!m.equals(me)) pt = m;
                if (!pt.equals(partner())) {
                    Store.putS("partner", pt);
                    watchPartner(pt);
                    fire();
                } else if (partnerReg == null && !pt.isEmpty()) {
                    watchPartner(pt);
                }
                uploadAll();
            }));
            regs.add(cd.collection("memos").addSnapshotListener((qs, e) -> {
                if (e != null || qs == null) return;
                for (DocumentChange dc : qs.getDocumentChanges()) {
                    if (dc.getType() == DocumentChange.Type.REMOVED) Memos.removeRemote(dc.getDocument().getId());
                    else Memos.applyRemote(dc.getDocument().getString("j"));
                }
                fire();
            }));
            regs.add(cd.collection("uses").addSnapshotListener((qs, e) -> {
                if (e != null || qs == null) return;
                String me = uid();
                for (DocumentChange dc : qs.getDocumentChanges()) {
                    DocumentSnapshot d = dc.getDocument();
                    if (dc.getType() == DocumentChange.Type.REMOVED) { Store.removeRemoteUse(d.getId()); continue; }
                    try {
                        String owner = d.getString("o");
                        Store.applyRemoteUse(d.getId(), d.getString("k"), LocalDate.parse(d.getString("d")), me.equals(owner) ? 0 : 1);
                    } catch (Exception ignored) { }
                }
                fire();
            }));
            regs.add(cd.collection("bals").addSnapshotListener((qs, e) -> {
                if (e != null || qs == null) return;
                String me = uid();
                for (DocumentChange dc : qs.getDocumentChanges()) {
                    DocumentSnapshot d = dc.getDocument();
                    if (dc.getType() == DocumentChange.Type.REMOVED || d.getData() == null) continue;
                    String prefix = d.getId().equals(me) ? "bal_" : "mbal_";
                    for (Map.Entry<String, Object> en : d.getData().entrySet()) {
                        if (en.getValue() instanceof Number) Store.putBalLocal(prefix + en.getKey(), ((Number) en.getValue()).floatValue());
                    }
                }
                fire();
            }));
            if (!partner().isEmpty()) watchPartner(partner());
            uploadAll();
        } catch (Throwable t) {
            started = false;
        }
    }

    static synchronized void stop() {
        for (ListenerRegistration r : regs) { try { r.remove(); } catch (Throwable ignored) { } }
        regs.clear();
        if (partnerReg != null) { try { partnerReg.remove(); } catch (Throwable ignored) { } partnerReg = null; }
        started = false;
    }

    private static void watchPartner(String pt) {
        if (partnerReg != null) { try { partnerReg.remove(); } catch (Throwable ignored) { } partnerReg = null; }
        if (pt.isEmpty()) return;
        partnerReg = userDoc(pt).addSnapshotListener((d, e) -> {
            if (e != null || d == null || !d.exists()) return;
            String n = d.getString("nick");
            if (n != null) Store.putS("partner_nick", n);
            Long g = d.getLong("grp");
            if (g != null && g >= 1 && g <= 5) Store.putI("grp_mate", g.intValue());
            fire();
        });
    }

    /** 처음 연결됐을 때 이 기기의 기존 데이터를 서버로 올린다 (한 번만) */
    private static void uploadAll() {
        if (!active()) return;
        final String me = uid(), pt = partner();
        if (!cid().equals(Store.getS("synced_cid", ""))) {
            Store.putS("synced_cid", cid());
            for (Memos.Memo m : Memos.all()) pushMemo(m);
            for (Store.Use u : Store.allUses()) if (u.who == 0) pushUse(u);
            Map<String, Object> bal = new HashMap<>();
            for (Map.Entry<String, ?> en : Store.prefs().getAll().entrySet()) {
                if (en.getKey().startsWith("bal_") && en.getValue() instanceof Float) bal.put(en.getKey().substring(4), ((Float) en.getValue()).doubleValue());
            }
            if (!bal.isEmpty()) couple().collection("bals").document(me).set(bal, SetOptions.merge());
            pushProfile();
        }
        if (!pt.isEmpty() && !pt.equals(Store.getS("synced_pt", ""))) {
            Store.putS("synced_pt", pt);
            for (Store.Use u : Store.allUses()) if (u.who == 1) pushUse(u);
        }
    }

    // ---------- 로컬 변경 → 서버 ----------
    static void pushMemo(Memos.Memo m) {
        try {
            if (!active()) return;
            Map<String, Object> d = new HashMap<>();
            d.put("j", Memos.toJsonString(m));
            couple().collection("memos").document(m.id).set(d);
        } catch (Throwable ignored) { }
    }

    static void delMemo(String id) {
        try { if (active()) couple().collection("memos").document(id).delete(); } catch (Throwable ignored) { }
    }

    private static String ownerOf(int who) { return who == 0 ? uid() : partner(); }

    static void pushUse(Store.Use u) {
        try {
            if (!active()) return;
            String owner = ownerOf(u.who);
            if (owner.isEmpty()) return;
            Map<String, Object> d = new HashMap<>();
            d.put("k", u.key);
            d.put("d", u.date.toString());
            d.put("o", owner);
            couple().collection("uses").document(u.id).set(d);
        } catch (Throwable ignored) { }
    }

    static void delUse(String id) {
        try { if (active()) couple().collection("uses").document(id).delete(); } catch (Throwable ignored) { }
    }

    static void pushBal(String pool, int y, int who, double v) {
        try {
            if (!active()) return;
            String owner = ownerOf(who);
            if (owner.isEmpty()) return;
            Map<String, Object> d = new HashMap<>();
            d.put(pool + "_" + y, v);
            couple().collection("bals").document(owner).set(d, SetOptions.merge());
        } catch (Throwable ignored) { }
    }

    static void pushProfile() {
        try {
            if (!loggedIn()) return;
            Map<String, Object> m = new HashMap<>();
            m.put("nick", Store.getS("nick", ""));
            m.put("grp", (long) Store.getI("grp_me", 1));
            if (!cid().isEmpty()) m.put("cid", cid());
            userDoc(uid()).set(m, SetOptions.merge());
        } catch (Throwable ignored) { }
    }
}
