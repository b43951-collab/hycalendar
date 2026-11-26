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

    static final int MAX_MEMBERS = 6;

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

    private static String errText(Exception e) {
        if (e instanceof FirebaseNetworkException) return "인터넷에 연결할 수 없어요";
        String m = e == null ? "" : String.valueOf(e.getMessage());
        if (m.contains("PERMISSION_DENIED")) return "권한이 없어요 (서버 규칙을 확인하세요)";
        return "실패했어요: " + (m.length() > 80 ? m.substring(0, 80) : m);
    }

    // ---------- 로그인 / 가입 ----------
    private static String authErr(Exception e) {
        if (e instanceof com.google.firebase.auth.FirebaseAuthWeakPasswordException) return "비밀번호는 6자 이상이어야 해요";
        if (e instanceof FirebaseAuthUserCollisionException) return "이미 가입된 이메일이에요. 로그인해 주세요";
        if (e instanceof com.google.firebase.auth.FirebaseAuthInvalidUserException) return "가입되지 않은 이메일이에요";
        if (e instanceof com.google.firebase.auth.FirebaseAuthInvalidCredentialsException) return "이메일 또는 비밀번호가 틀렸어요";
        return errText(e);
    }

    static void signIn(String email, String pw, Done cb) {
        if (!configured()) { cb.run("서버가 아직 설정되지 않았어요"); return; }
        FirebaseAuth.getInstance().signInWithEmailAndPassword(email, pw)
                .addOnSuccessListener(r -> afterAuth(email, cb))
                .addOnFailureListener(e -> cb.run(authErr(e)));
    }

    static void signUp(String email, String pw, Done cb) {
        if (!configured()) { cb.run("서버가 아직 설정되지 않았어요"); return; }
        FirebaseAuth.getInstance().createUserWithEmailAndPassword(email, pw)
                .addOnSuccessListener(r -> afterAuth(email, cb))
                .addOnFailureListener(e -> cb.run(authErr(e)));
    }

    static void resetPassword(String email, Done cb) {
        if (!configured()) { cb.run("서버가 아직 설정되지 않았어요"); return; }
        FirebaseAuth.getInstance().sendPasswordResetEmail(email)
                .addOnSuccessListener(r -> cb.run(null))
                .addOnFailureListener(e -> cb.run(authErr(e)));
    }

    private static void afterAuth(final String email, final Done cb) {
        final String uid = uid();
        Store.putS("email", email);
        userDoc(uid).get().addOnSuccessListener(doc -> {
            if (doc.exists()) {
                String c = doc.getString("cid");
                Store.putS("cid", c == null ? "" : c);
            } else {
                Map<String, Object> m = new HashMap<>();
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
        Store.putS("email", "");
        Store.putS("code", "");
        Store.putS("synced_cid", "");
        Store.putS("synced_pt", "");
    }

    // ---------- 부부 연결 ----------
    static void createCouple(boolean spouse, Done cb) {
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
        List<String> sps = new ArrayList<>();
        if (spouse) sps.add(uid);
        cm.put("spouses", sps);   // 배우자(사내 부부)로 등록된 사람들 (최대 2명). 나머지는 가족
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

    static void joinCouple(String codeIn, boolean spouse, Done cb) {
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
                if (mem.size() >= MAX_MEMBERS && !mem.contains(uid)) { cb.run("가족 인원이 가득 찼어요 (최대 " + MAX_MEMBERS + "명)"); return; }
                List<String> sp = spousesOf(cd.get("spouses"), mem);
                if (spouse && !sp.contains(uid)) {
                    if (sp.size() >= 2) { cb.run("이미 배우자 2명이 연결돼 있어요. 가족으로 연결해 주세요"); return; }
                    if (sp.isEmpty() && !mem.isEmpty() && !mem.get(0).equals(uid)) sp.add(mem.get(0));  // 코드를 만든 사람도 함께 부부로
                    sp.add(uid);
                } else if (!spouse) sp.remove(uid);
                WriteBatch b = db().batch();
                b.update(db().collection("couples").document(cid), "members", FieldValue.arrayUnion(uid), "spouses", sp);
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

    /** 배우자(사내 부부)로 등록된 사람 목록. 예전 방식(spouses 없음)이면 먼저 들어온 두 명이 부부. */
    @SuppressWarnings("unchecked")
    static List<String> spousesOf(Object raw, List<String> members) {
        List<String> sp = new ArrayList<>();
        if (raw instanceof List) { for (Object o : (List<Object>) raw) if (o instanceof String) sp.add((String) o); }
        else if (members != null) sp.addAll(members.subList(0, Math.min(2, members.size())));
        return sp;
    }

    /** 이미 연결된 뒤에 내 연결 방식을 배우자 ↔ 가족으로 바꾼다. */
    static void setRole(boolean spouse, Done cb) {
        if (!active()) { cb.run("먼저 연결하세요"); return; }
        final String uid = uid();
        final DocumentReference ref = couple();
        ref.get().addOnSuccessListener(cd -> {
            @SuppressWarnings("unchecked")
            List<String> mem = (List<String>) cd.get("members");
            List<String> sp = spousesOf(cd.get("spouses"), mem);
            if (spouse && !sp.contains(uid)) {
                if (sp.size() >= 2) { cb.run("이미 배우자 2명이 연결돼 있어요"); return; }
                sp.add(uid);
            } else if (!spouse) sp.remove(uid);
            ref.update("spouses", sp).addOnSuccessListener(v -> cb.run(null)).addOnFailureListener(e -> cb.run(errText(e)));
        }).addOnFailureListener(e -> cb.run(errText(e)));
    }

    // ---------- 서버 백업 ----------
    interface RestoreCb { void run(String json, long ts, String err); }

    static void backupToServer(String json, Done cb) {
        try {
            if (!loggedIn()) { cb.run("먼저 로그인하세요"); return; }
            if (json.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 950000) { cb.run("백업 용량이 너무 커서 서버에 올릴 수 없어요"); return; }
            Map<String, Object> d = new HashMap<>();
            d.put("j", json);
            d.put("ts", System.currentTimeMillis());
            db().collection("backups").document(uid()).set(d)
                    .addOnSuccessListener(v -> cb.run(null))
                    .addOnFailureListener(e -> cb.run(errText(e)));
        } catch (Throwable t) { cb.run("실패했어요"); }
    }

    static void restoreFromServer(RestoreCb cb) {
        try {
            if (!loggedIn()) { cb.run(null, 0, "먼저 로그인하세요"); return; }
            db().collection("backups").document(uid()).get().addOnSuccessListener(d -> {
                if (!d.exists() || d.getString("j") == null) { cb.run(null, 0, "서버에 저장된 백업이 없어요"); return; }
                Long ts = d.getLong("ts");
                cb.run(d.getString("j"), ts == null ? 0 : ts, null);
            }).addOnFailureListener(e -> cb.run(null, 0, errText(e)));
        } catch (Throwable t) { cb.run(null, 0, "실패했어요"); }
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
                if (mem != null) {
                    Store.putI("mcount", mem.size());
                    List<String> sp = spousesOf(snap.get("spouses"), mem);   // 배우자로 등록된 사람들, 나머지는 가족
                    Store.putI("scount", sp.size());
                    Store.putB("is_spouse", sp.contains(me));
                    if (sp.contains(me) && sp.size() >= 2) pt = sp.get(0).equals(me) ? sp.get(1) : sp.get(0);
                }
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
                        if (!me.equals(owner) && (owner == null || !owner.equals(partner()))) continue;
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
                    if (!d.getId().equals(me) && !d.getId().equals(partner())) continue;
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
            Long g = d.getLong("grp");
            if (g != null && g >= 1 && g <= 5) Store.putI("grp_mate", g.intValue());
            String pn = d.getString("name");
            if (pn != null) Store.putS("mate_name", pn);   // 배우자 이름 (메모 작성자 안내용)
            fire();
        });
    }

    /** 처음 연결됐을 때 이 기기의 기존 데이터를 서버로 올린다 (한 번만) */
    private static void uploadAll() {
        if (!active()) return;
        final String me = uid(), pt = partner();
        if (!cid().equals(Store.getS("synced_cid", ""))) {
            Store.putS("synced_cid", cid());
            for (Memos.Memo m : Memos.all()) if (m.shared || m.family) { if (m.owner.isEmpty()) Memos.save(m); else { Memos.stamp(m); pushMemo(m); } }
            for (Store.Use u : Store.allUses()) if (u.who == 0) pushUse(u);
            Map<String, Object> bal = new HashMap<>();
            for (Map.Entry<String, ?> en : Store.prefs().getAll().entrySet()) {
                if (en.getKey().startsWith("bal_") && en.getValue() instanceof Float) bal.put(en.getKey().substring(4), ((Float) en.getValue()).doubleValue());
            }
            if (!bal.isEmpty()) couple().collection("bals").document(me).set(bal, SetOptions.merge());
            pushProfile();
        }
        if (!pt.isEmpty() && !Store.getB("purged_mate_uses", false)) {
            Store.putB("purged_mate_uses", true);
            for (Store.Use u : new java.util.ArrayList<>(Store.allUses())) if (u.who == 1 && !u.remote) Store.removeRemoteUse(u.id);
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
            if (!active() || !u.shared) return;
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

    // ---------- 공유 범위 / 표시 이름 ----------
    /** 서버에서 받은 메모를 이 기기에서 볼 수 있는지 */
    static boolean canSee(String owner, boolean toSpouse, boolean toFamily) {
        String me = uid();
        if (owner == null || owner.isEmpty() || owner.equals(me)) return true;
        if (toFamily) return true;
        return toSpouse && owner.equals(partner());
    }

    static String displayName() {
        String n = Store.getS("dname", "");
        if (!n.isEmpty()) return n;
        String e = Store.getS("email", "");
        int at = e.indexOf('@');
        return at > 0 ? e.substring(0, at) : "나";
    }

    // ---------- 게시판 ----------
    /** 공개 게시판 작성자명: 설정한 이름만 쓰고, 없으면 이메일 대신 "익명". */
    static String boardName() {
        String n = Store.getS("dname", "");
        return n.isEmpty() ? "익명" : n;
    }

    static final class Post {
        String id = "", o = "", n = "", t = "";
        long ts;
        int pc;                              // 별도 보관된 사진 장수 (새 글)
        List<String> ph = new ArrayList<>();   // 글 안에 들어 있는 사진 (예전 글)
    }

    /** 관리자 여부: Firestore의 admins/{내 uid} 문서가 있으면 true (콘솔에서만 만들 수 있음). */
    static volatile boolean isAdmin = false;

    static void checkAdmin() {
        try {
            if (!loggedIn()) { isAdmin = false; return; }
            db().collection("admins").document(uid()).get()
                    .addOnSuccessListener(d -> isAdmin = d != null && d.exists())
                    .addOnFailureListener(e -> isAdmin = false);
        } catch (Throwable t) { isAdmin = false; }
    }

    interface PostsCb { void run(List<Post> posts); }

    /** 게시글을 실시간으로 받는다. 반환값을 실행하면 구독이 끝난다. */
    static Runnable listenPosts(PostsCb cb) {
        try {
            if (!loggedIn()) return () -> { };
            checkAdmin();
            final ListenerRegistration r = db().collection("posts")
                    .orderBy("ts", com.google.firebase.firestore.Query.Direction.DESCENDING).limit(100)
                    .addSnapshotListener((qs, e) -> {
                        if (e != null || qs == null) return;
                        List<Post> l = new ArrayList<>();
                        for (DocumentSnapshot d : qs.getDocuments()) {
                            Post p = new Post();
                            p.id = d.getId();
                            p.o = String.valueOf(d.getString("o") == null ? "" : d.getString("o"));
                            p.n = d.getString("n") == null ? "" : d.getString("n");
                            p.t = d.getString("t") == null ? "" : d.getString("t");
                            Long ts = d.getLong("ts");
                            p.ts = ts == null ? 0 : ts;
                            @SuppressWarnings("unchecked")
                            List<String> ph = (List<String>) d.get("ph");
                            if (ph != null) p.ph = ph;
                            Long pc = d.getLong("pc");
                            p.pc = pc == null ? 0 : pc.intValue();
                            l.add(p);
                        }
                        cb.run(l);
                    });
            return r::remove;
        } catch (Throwable t) { return () -> { }; }
    }

    /** 새 글: 사진은 글과 따로(pphotos) 저장해서, 게시판 목록을 받을 때는 글자만 내려오게 한다 */
    static void addPost(String text, List<String> photos, Done cb) {
        try {
            if (!loggedIn()) { cb.run("로그인이 필요해요"); return; }
            com.google.firebase.firestore.DocumentReference ref = db().collection("posts").document();
            com.google.firebase.firestore.WriteBatch wb = db().batch();
            Map<String, Object> d = new HashMap<>();
            d.put("o", uid());
            d.put("n", boardName());
            d.put("t", text);
            d.put("ts", System.currentTimeMillis());
            d.put("pc", (long) photos.size());
            wb.set(ref, d);
            for (int i = 0; i < photos.size(); i++) {
                Map<String, Object> ph = new HashMap<>();
                ph.put("o", uid());
                ph.put("d", photos.get(i));
                wb.set(db().collection("pphotos").document(ref.getId() + "_" + i), ph);
            }
            wb.commit()
                    .addOnSuccessListener(r -> cb.run(null))
                    .addOnFailureListener(e -> cb.run(errText(e)));
        } catch (Throwable t) { cb.run("실패했어요"); }
    }

    interface PhotosCb { void run(List<String> photos); }

    /** 글에 딸린 사진을 눌렀을 때만 받아온다 */
    static void fetchPhotos(String postId, int count, PhotosCb cb) {
        final String[] got = new String[count];
        final int[] left = {count};
        if (count <= 0) { cb.run(new ArrayList<>()); return; }
        for (int i = 0; i < count; i++) {
            final int idx = i;
            db().collection("pphotos").document(postId + "_" + i).get().addOnCompleteListener(t -> {
                try {
                    if (t.isSuccessful() && t.getResult() != null && t.getResult().exists()) got[idx] = t.getResult().getString("d");
                } catch (Throwable ignored) { }
                if (--left[0] == 0) {
                    List<String> out = new ArrayList<>();
                    for (String g : got) if (g != null) out.add(g);
                    cb.run(out);
                }
            });
        }
    }

    static void updatePost(String id, String text, Done cb) {
        try {
            db().collection("posts").document(id).update("t", text)
                    .addOnSuccessListener(r -> cb.run(null))
                    .addOnFailureListener(e -> cb.run(errText(e)));
        } catch (Throwable t) { cb.run("실패했어요"); }
    }

    static void deletePost(Post p, Done cb) {
        try {
            com.google.firebase.firestore.WriteBatch wb = db().batch();
            wb.delete(db().collection("posts").document(p.id));
            for (int i = 0; i < p.pc; i++) wb.delete(db().collection("pphotos").document(p.id + "_" + i));
            wb.commit()
                    .addOnSuccessListener(r -> cb.run(null))
                    .addOnFailureListener(e -> cb.run(errText(e)));
        } catch (Throwable t) { cb.run("실패했어요"); }
    }

    static void pushProfile() {
        try {
            if (!loggedIn()) return;
            Map<String, Object> m = new HashMap<>();
            m.put("grp", (long) Store.getI("grp_me", 1));
            m.put("name", displayName());
            if (!cid().isEmpty()) m.put("cid", cid());
            userDoc(uid()).set(m, SetOptions.merge());
        } catch (Throwable ignored) { }
    }
}
