package com.hycalendar.app;

import android.app.Activity;
import android.app.Dialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Color;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

/** 설정의 "서버·부부 연결" 카드와 로그인/코드 입력 창 */
final class SyncUi {
    private SyncUi() {}

    private static int dp(float v) { return Ui.dp(v); }

    private static EditText field(Activity a, Theme th, String hint, boolean password) {
        EditText e = new EditText(a);
        e.setHint(hint);
        e.setHintTextColor(th.sub);
        e.setTextColor(th.fg);
        e.setTextSize(16);
        e.setSingleLine(true);
        e.setInputType(password ? InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD : InputType.TYPE_CLASS_TEXT);
        e.setBackground(Ui.round(th.soft, 12));
        e.setPadding(dp(14), dp(12), dp(14), dp(12));
        return e;
    }

    private static LinearLayout.LayoutParams gap() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, 0, dp(10));
        return lp;
    }

    private static Dialog cardDialog(Activity a, Theme th, LinearLayout box, boolean cancelable) {
        box.setBackground(Ui.round(th.card, 20));
        box.setPadding(dp(22), dp(20), dp(22), dp(16));
        final Dialog dlg = new Dialog(a, android.R.style.Theme_DeviceDefault_NoActionBar);
        dlg.setContentView(box);
        dlg.setCancelable(cancelable);
        dlg.setCanceledOnTouchOutside(false);
        if (dlg.getWindow() != null) {
            dlg.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
            dlg.getWindow().setLayout((int) (a.getResources().getDisplayMetrics().widthPixels * 0.9f), ViewGroup.LayoutParams.WRAP_CONTENT);
            dlg.getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }
        return dlg;
    }

    /** 이메일 + 비밀번호로 로그인 / 가입 */
    static void loginDialog(Activity a, Theme th, boolean startup, Runnable done) {
        LinearLayout box = new LinearLayout(a);
        box.setOrientation(LinearLayout.VERTICAL);
        box.addView(Ui.text(a, "로그인 / 가입", 20, th.fg, true));
        TextView ex = Ui.text(a, "이메일과 비밀번호(6자 이상)로 로그인해요. 처음이면 '가입'을 누르세요.", 13, th.sub, false);
        ex.setPadding(0, dp(6), 0, dp(12));
        box.addView(ex);
        final EditText mail = field(a, th, "이메일", false);
        mail.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        mail.setText(Store.getS("email", ""));
        box.addView(mail, gap());
        final EditText pw = field(a, th, "비밀번호 (6자 이상)", true);
        box.addView(pw, gap());
        final TextView err = Ui.text(a, " ", 12, th.sun, false);
        err.setPadding(dp(4), 0, 0, dp(8));
        box.addView(err);
        final Dialog dlg = cardDialog(a, th, box, true);
        LinearLayout row = new LinearLayout(a);
        final TextView login = Ui.btn(a, th, "로그인", true, null);
        final TextView signup = Ui.btn(a, th, "가입", false, null);
        View.OnClickListener go = v -> {
            boolean isLogin = v == login;
            String m = mail.getText().toString().trim(), p = pw.getText().toString();
            if (!m.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) { err.setText("이메일 형식을 확인해 주세요"); return; }
            if (p.length() < 6) { err.setText("비밀번호는 6자 이상이에요"); return; }
            err.setText("처리 중…");
            login.setEnabled(false); signup.setEnabled(false);
            Sync.Done cb = e -> {
                login.setEnabled(true); signup.setEnabled(true);
                if (e != null) { err.setText(e); return; }
                dlg.dismiss();
                Toast.makeText(a, isLogin ? "로그인했어요" : "가입했어요", Toast.LENGTH_SHORT).show();
                if (done != null) done.run();
            };
            if (isLogin) Sync.signIn(m, p, cb); else Sync.signUp(m, p, cb);
        };
        login.setOnClickListener(go);
        signup.setOnClickListener(go);
        LinearLayout.LayoutParams l1 = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        l1.setMargins(0, 0, dp(6), 0);
        LinearLayout.LayoutParams l2 = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        l2.setMargins(dp(6), 0, 0, 0);
        row.addView(login, l1);
        row.addView(signup, l2);
        box.addView(row);
        TextView reset = Ui.text(a, "비밀번호를 잊었어요", 13, th.todayStroke, true);
        reset.setGravity(Gravity.CENTER);
        reset.setPadding(0, dp(14), 0, dp(6));
        reset.setOnClickListener(v -> {
            String m = mail.getText().toString().trim();
            if (m.isEmpty()) { err.setText("위에 이메일을 먼저 입력해 주세요"); return; }
            err.setText("메일을 보내는 중…");
            Sync.resetPassword(m, e -> err.setText(e != null ? e : "재설정 메일을 보냈어요. 메일함(스팸함도)을 확인하세요"));
        });
        box.addView(reset);
        if (startup) {
            TextView later = Ui.text(a, "나중에 할게요", 13, th.sub, false);
            later.setGravity(Gravity.CENTER);
            later.setPadding(0, dp(6), 0, dp(4));
            later.setOnClickListener(v -> { Store.putB("login_later", true); dlg.dismiss(); });
            box.addView(later);
        }
        dlg.show();
    }

    private static void codeDialog(Activity a, Theme th, Runnable done) {
        LinearLayout box = new LinearLayout(a);
        box.setOrientation(LinearLayout.VERTICAL);
        box.addView(Ui.text(a, "초대 코드 입력", 20, th.fg, true));
        TextView ex = Ui.text(a, "배우자가 알려준 6자리 코드를 입력하세요.", 13, th.sub, false);
        ex.setPadding(0, dp(6), 0, dp(12));
        box.addView(ex);
        final EditText code = field(a, th, "예) K7M2QX", false);
        code.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
        box.addView(code, gap());
        final TextView err = Ui.text(a, " ", 12, th.sun, false);
        err.setPadding(dp(4), 0, 0, dp(8));
        box.addView(err);
        LinearLayout row = new LinearLayout(a);
        final Dialog dlg = cardDialog(a, th, box, true);
        LinearLayout.LayoutParams l1 = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        l1.setMargins(0, 0, dp(6), 0);
        row.addView(Ui.btn(a, th, "취소", false, v -> dlg.dismiss()), l1);
        final TextView ok = Ui.btn(a, th, "연결", true, null);
        ok.setOnClickListener(v -> {
            String c = code.getText().toString().trim();
            if (c.length() != 6) { err.setText("6자리 코드를 입력해 주세요"); return; }
            err.setText("처리 중…");
            ok.setEnabled(false);
            Sync.joinCouple(c, e -> {
                ok.setEnabled(true);
                if (e != null) { err.setText(e); return; }
                dlg.dismiss();
                Toast.makeText(a, "배우자와 연결했어요", Toast.LENGTH_SHORT).show();
                if (done != null) done.run();
            });
        });
        LinearLayout.LayoutParams l2 = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        l2.setMargins(dp(6), 0, 0, 0);
        row.addView(ok, l2);
        box.addView(row);
        dlg.show();
    }

    /** 설정 화면에 넣는 카드 */
    static LinearLayout card(Activity a, Theme th, Runnable refresh) {
        LinearLayout c = Ui.card(a, th);
        if (!Sync.configured()) {
            c.addView(Ui.text(a, "서버 설정 전이에요", 15, th.fg, true));
            c.addView(Ui.text(a, "지금은 이 폰에서만 저장돼요. 서버를 연결하면 배우자와 메모·근태가 서로 보여요.", 12, th.sub, false));
            return c;
        }
        if (!Sync.loggedIn()) {
            c.addView(Ui.text(a, "로그인이 필요해요", 15, th.fg, true));
            TextView ex = Ui.text(a, "로그인하면 배우자와 메모·근태를 함께 보고 고칠 수 있어요.", 12, th.sub, false);
            ex.setPadding(0, dp(2), 0, dp(10));
            c.addView(ex);
            c.addView(Ui.btn(a, th, "로그인 / 가입", true, v -> loginDialog(a, th, false, refresh)));
            return c;
        }
        c.addView(Ui.text(a, "로그인: " + Store.getS("email", ""), 15, th.fg, true));
        String cid = Sync.cid(), partner = Sync.partner();
        if (cid.isEmpty()) {
            TextView ex = Ui.text(a, "배우자와 연결해 보세요. 한 명이 코드를 만들고, 다른 한 명이 그 코드를 입력해요.", 12, th.sub, false);
            ex.setPadding(0, dp(2), 0, dp(10));
            c.addView(ex);
            LinearLayout row = new LinearLayout(a);
            LinearLayout.LayoutParams l1 = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
            l1.setMargins(0, 0, dp(6), 0);
            LinearLayout.LayoutParams l2 = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
            l2.setMargins(dp(6), 0, 0, 0);
            row.addView(Ui.btn(a, th, "초대 코드 만들기", true, v -> Sync.createCouple(e -> {
                if (e != null) Toast.makeText(a, e, Toast.LENGTH_LONG).show();
                refresh.run();
            })), l1);
            row.addView(Ui.btn(a, th, "코드 입력", false, v -> codeDialog(a, th, refresh)), l2);
            c.addView(row);
        } else if (partner.isEmpty()) {
            TextView ex = Ui.text(a, "내 초대 코드 (배우자가 '코드 입력'에 넣어요)", 12, th.sub, false);
            ex.setPadding(0, dp(2), 0, dp(6));
            c.addView(ex);
            TextView code = Ui.text(a, Store.getS("code", ""), 30, th.todayStroke, true);
            code.setLetterSpacing(0.2f);
            c.addView(code);
            c.addView(Ui.btn(a, th, "코드 복사", false, v -> {
                ClipboardManager cm = (ClipboardManager) a.getSystemService(Context.CLIPBOARD_SERVICE);
                if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("code", Store.getS("code", "")));
                Toast.makeText(a, "복사했어요", Toast.LENGTH_SHORT).show();
            }));
            TextView w = Ui.text(a, "연결을 기다리는 중이에요", 12, th.sub, false);
            w.setPadding(0, dp(8), 0, 0);
            c.addView(w);
        } else {
            c.addView(Ui.text(a, "배우자 연결됨", 14, th.todayStroke, true));
            TextView ex = Ui.text(a, "메모·근태·잔여가 서로 실시간으로 반영돼요.", 12, th.sub, false);
            ex.setPadding(0, dp(2), 0, 0);
            c.addView(ex);
        }
        TextView out = Ui.btn(a, th, "로그아웃", false, v -> new android.app.AlertDialog.Builder(a)
                .setTitle("로그아웃")
                .setMessage("로그아웃하면 이 폰은 서버와 연결이 끊겨요. 폰에 있는 데이터는 그대로 남아요.")
                .setPositiveButton("로그아웃", (x, w) -> { Sync.signOut(); refresh.run(); })
                .setNegativeButton("취소", null).show());
        LinearLayout.LayoutParams op = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        op.setMargins(0, dp(12), 0, 0);
        c.addView(out, op);
        return c;
    }
}
